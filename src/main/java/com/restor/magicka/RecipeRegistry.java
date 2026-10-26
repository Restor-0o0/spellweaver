package com.restor.magicka;

import com.google.gson.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class RecipeRegistry {
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    public static final int CATALOG_SIZE = 346;
    private static final List<Identifier> INDEX = readIndex();
    private static final Map<Identifier, Integer> POSITIONS = positions();
    private static final Map<SpellFormula, SpellDefinition> BASE = loadBase();
    private static volatile Map<SpellFormula, SpellDefinition> server = BASE;
    private static volatile Map<SpellFormula, SpellDefinition> client = BASE;
    private static volatile Map<Identifier, SpellDefinition> serverIds = ids(BASE);
    private static volatile Map<Identifier, SpellDefinition> clientIds = serverIds;
    private static int clientRevision;
    public static int clientRevision(){return clientRevision;}
    public static int catalogPosition(Identifier id) { return POSITIONS.getOrDefault(id, Integer.MAX_VALUE); }
    public static Collection<SpellDefinition> baseRecipes() { return BASE.values(); }
    public static Collection<SpellDefinition> recipes() { return server.values(); }
    public static List<SpellDefinition> clientRecipes() { return client.values().stream().sorted(Comparator.comparingInt((SpellDefinition d) -> d.formula().size()).thenComparing(d -> d.id().toString())).toList(); }
    public static SpellDefinition resolve(SpellFormula formula) { var value=server.get(formula);return value==null?fallback(formula):value; }
    public static SpellDefinition clientResolve(SpellFormula formula) { var value=client.get(formula);return value==null?fallback(formula):value; }
    private static final Map<SpellFormula, SpellDefinition> FUSIONS = new java.util.concurrent.ConcurrentHashMap<>();
    /** What a set of elements casts when it misses every named sequence; see {@link FusionRules}. */
    public static SpellDefinition fusion(SpellFormula formula) { return FUSIONS.computeIfAbsent(formula, RecipeRegistry::fuse); }
    private static SpellDefinition fuse(SpellFormula formula) {
        var digits = new StringBuilder();
        for (Element e : Element.values()) digits.append(formula.count(e));
        return SpellDefinition.parse(Identifier.fromNamespaceAndPath(Magicka.ID, "fusion_" + digits), FusionRules.build(formula));
    }
    public static SpellDefinition byId(Identifier id) { return serverIds.get(id); }
    public static SpellDefinition clientById(Identifier id) { return clientIds.get(id); }
    private static Map<Identifier, SpellDefinition> ids(Map<SpellFormula, SpellDefinition> recipes) {
        var result = new HashMap<Identifier, SpellDefinition>();
        for (var def : recipes.values()) result.put(def.id(), def);
        return Map.copyOf(result);
    }
    public static void resetClient() { client = BASE;clientIds = ids(BASE);clientRevision++; }
    public static void acceptClient(List<String> definitions) {
        Map<SpellFormula, SpellDefinition> loaded = new HashMap<>();
        for (String text : definitions) {
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            var def = SpellDefinition.parse(Identifier.parse(json.get("id").getAsString()), json);
            if (loaded.put(def.formula(), def) != null) throw new IllegalArgumentException("Duplicate synced formula");
        }
        client = Map.copyOf(loaded);
        clientIds = ids(client);
        clientRevision++;
    }
    private static List<Identifier> readIndex() {
        try (var stream = RecipeRegistry.class.getResourceAsStream("/assets/magicka/spell_index.json")) {
            if (stream == null) throw new IllegalStateException("Missing spell catalog index");
            List<Identifier> ids = new ArrayList<>();
            for (JsonElement e : JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonArray()) ids.add(Identifier.parse(e.getAsString()));
            return List.copyOf(ids);
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    private static Map<Identifier, Integer> positions() {
        Map<Identifier, Integer> positions = new HashMap<>();
        for (int i = 0; i < INDEX.size(); i++) positions.put(INDEX.get(i), i);
        return Map.copyOf(positions);
    }
    private static Map<SpellFormula, SpellDefinition> loadBase() {
        Map<SpellFormula, SpellDefinition> loaded = new HashMap<>();
        for (Identifier id : INDEX) {
            try (var data = RecipeRegistry.class.getResourceAsStream("/data/" + id.getNamespace() + "/magicka_spell/" + id.getPath() + ".json")) {
                if (data == null) throw new IllegalStateException("Missing " + id);
                var def = SpellDefinition.parse(id, JsonParser.parseReader(new InputStreamReader(data,StandardCharsets.UTF_8)).getAsJsonObject());
                if (loaded.put(def.formula(),def) != null) throw new IllegalStateException("Duplicate " + id);
            } catch (IOException e) { throw new UncheckedIOException(e); }
        }
        if (loaded.size() != CATALOG_SIZE) throw new IllegalStateException("Expected " + CATALOG_SIZE + " base formulas, got " + loaded.size());
        return Map.copyOf(loaded);
    }
    public static SpellDefinition fallback(SpellFormula formula) {
        if (formula.size() == 0) throw new IllegalArgumentException("No spell for empty formula");
        SpellDefinition base = BASE == null ? null : BASE.get(formula);
        return base != null ? base : fusion(formula);
    }
    @SubscribeEvent public void reload(AddServerReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(Magicka.ID, "spells"), new SimpleJsonResourceReloadListener<JsonElement>(net.minecraft.util.ExtraCodecs.JSON, net.minecraft.resources.FileToIdConverter.json("magicka_spell")) {
            @Override protected void apply(Map<Identifier, JsonElement> entries, ResourceManager resources, ProfilerFiller profiler) {
                var loaded = new HashMap<SpellFormula, SpellDefinition>(BASE);
                var custom=new HashSet<SpellFormula>();
                // Base recipes are replaced by formula. This permits a custom namespace to override one base formula.
                entries.entrySet().stream().sorted(Comparator.<Map.Entry<Identifier,JsonElement>,Boolean>comparing(e -> !e.getKey().getNamespace().equals(Magicka.ID)).thenComparing(e->e.getKey().toString())).forEach(e -> {
                    SpellDefinition def = SpellDefinition.parse(e.getKey(), e.getValue().getAsJsonObject());
                    if(!e.getKey().getNamespace().equals(Magicka.ID) && !custom.add(def.formula()))throw new IllegalArgumentException("Multiple custom recipes for "+def.formula());
                    loaded.put(def.formula(),def);
                });
                server = Map.copyOf(loaded);
                serverIds = ids(server);
                org.slf4j.LoggerFactory.getLogger(Magicka.ID).info("Loaded {} data-driven spell recipes", loaded.size());
            }
        });
    }
    @SubscribeEvent public void sync(OnDatapackSyncEvent event) {
        var packets = server.values().stream().sorted(Comparator.comparing(d -> d.id().toString())).map(d -> {
            JsonObject json = d.source().deepCopy(); json.addProperty("id",d.id().toString()); return GSON.toJson(json);
        }).toList();
        // Small chunks stay far below Minecraft's 1 MiB clientbound limit.
        for (int i = 0; i < packets.size(); i += 12) {
            var packet = new MagicNetwork.Catalog(i == 0, i + 12 >= packets.size(), packets.subList(i,Math.min(i+12,packets.size())));
            if (event.getPlayer() == null) event.getPlayerList().getPlayers().forEach(p -> PacketDistributor.sendToPlayer(p,packet));
            else PacketDistributor.sendToPlayer(event.getPlayer(),packet);
        }
    }
}
