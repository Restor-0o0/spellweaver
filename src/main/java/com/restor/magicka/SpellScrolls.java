package com.restor.magicka;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

public final class SpellScrolls {
    private static final String TAG = "magicka_scrolls";
    private static volatile Set<String> client = Set.of();
    private static volatile boolean clientAll;

    public static boolean locked(SpellDefinition spell) {
        return spell.source().has("scroll") && spell.source().get("scroll").getAsBoolean();
    }

    public static Set<String> known(Player player) {
        String raw = player.getPersistentData().getString(TAG);
        return raw.isEmpty() ? Set.of() : Set.copyOf(Arrays.asList(raw.split(",")));
    }

    private static boolean open(Player player, SpellDefinition spell, Set<String> known, boolean all) {
        return all || !locked(spell) || player == null || player.isCreative() || known.contains(spell.id().toString());
    }

    public static boolean open(Player player, SpellDefinition spell) { return open(player, spell, known(player), MagicConfig.UNLOCK_ALL.get()); }
    public static boolean clientOpen(Player player, SpellDefinition spell) { return open(player, spell, client, clientAll); }
    public static boolean clientKnown(ResourceLocation id) { return client.contains(id.toString()); }

    public static boolean learn(ServerPlayer player, ResourceLocation id) {
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
        return open(player, spell, known, all) ? spell : RecipeRegistry.fusion(spell.formula());
    }

    public static SpellDefinition clientResolve(Player player, SpellFormula formula) {
        var spell = RecipeRegistry.clientResolve(formula);
        return clientOpen(player, spell) ? spell : RecipeRegistry.fusion(formula);
    }


    public static void acceptClient(boolean all, List<String> known) { clientAll = all; client = Set.copyOf(known); }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new MagicNetwork.Scrolls(MagicConfig.UNLOCK_ALL.get(), List.copyOf(known(player))));
    }

    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) { if (event.getEntity() instanceof ServerPlayer p) sync(p); }
    @SubscribeEvent public void clone(PlayerEvent.Clone event) {
        String raw = event.getOriginal().getPersistentData().getString(TAG);
        if (!raw.isEmpty()) event.getEntity().getPersistentData().putString(TAG, raw);
    }

    public SpellScrolls() {}
}
