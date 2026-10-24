package com.restor.magicka;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public final class SpellScrolls {
    private static final String TAG = "magicka_scrolls";
    private static volatile Set<String> client = Set.of();
    private static volatile boolean clientAll;

    public static boolean locked(SpellDefinition spell) {
        return spell.source().has("scroll") && spell.source().get("scroll").getAsBoolean();
    }

    public static Set<String> known(Player player) {
        String raw = player.getPersistentData().getStringOr(TAG, "");
        return raw.isEmpty() ? Set.of() : Set.copyOf(Arrays.asList(raw.split(",")));
    }

    private static boolean open(Player player, SpellDefinition spell, Set<String> known, boolean all) {
        return all || !locked(spell) || player == null || player.isCreative() || known.contains(spell.id().toString());
    }

    public static boolean open(Player player, SpellDefinition spell) { return open(player, spell, known(player), MagicConfig.UNLOCK_ALL.get()); }
    public static boolean clientOpen(Player player, SpellDefinition spell) { return open(player, spell, client, clientAll); }
    public static boolean clientKnown(Identifier id) { return client.contains(id.toString()); }

    public static boolean learn(ServerPlayer player, Identifier id) {
        var known = new TreeSet<>(known(player));
        if (!known.add(id.toString())) return false;
        player.getPersistentData().putString(TAG, String.join(",", known));
        sync(player);
        return true;
    }

    public static void forget(ServerPlayer player) {
        player.getPersistentData().remove(TAG);
        sync(player);
    }

    public static SpellDefinition resolve(Player player, SpellDefinition spell) {
        Set<String> known = known(player);
        boolean all = MagicConfig.UNLOCK_ALL.get();
        return substitute(spell, s -> open(player, s, known, all), RecipeRegistry::resolve);
    }

    public static SpellDefinition clientResolve(Player player, SpellFormula formula) {
        return substitute(RecipeRegistry.clientResolve(formula), s -> clientOpen(player, s), RecipeRegistry::clientResolve);
    }

    /** A locked recipe falls back to the recipe without its rarest element, the one generic fusions are built from. */
    private static SpellDefinition substitute(SpellDefinition spell, Predicate<SpellDefinition> open, Function<SpellFormula, SpellDefinition> lookup) {
        while (!open.test(spell) && spell.formula().size() > 1) {
            SpellFormula formula = spell.formula();
            Element rarest = Arrays.stream(Element.values()).filter(e -> formula.count(e) > 0).min(Comparator.comparingInt(formula::count)).orElseThrow();
            spell = lookup.apply(formula.without(rarest));
        }
        return spell;
    }

    public static void acceptClient(boolean all, List<String> known) { clientAll = all; client = Set.copyOf(known); }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new MagicNetwork.Scrolls(MagicConfig.UNLOCK_ALL.get(), List.copyOf(known(player))));
    }

    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) { if (event.getEntity() instanceof ServerPlayer p) sync(p); }
    @SubscribeEvent public void clone(PlayerEvent.Clone event) {
        String raw = event.getOriginal().getPersistentData().getStringOr(TAG, "");
        if (!raw.isEmpty()) event.getEntity().getPersistentData().putString(TAG, raw);
    }

    public SpellScrolls() {}
}
