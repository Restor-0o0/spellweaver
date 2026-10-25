package com.restor.magicka;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.util.FakePlayer;
import java.util.Set;

public final class MagePath {
    public static void award(ServerPlayer player, String name) {
        if (player instanceof FakePlayer || player.level().getServer() == null) return;
        var holder = player.level().getServer().getAdvancements().get(ResourceLocation.fromNamespaceAndPath(Magicka.ID, "mage/" + name));
        if (holder != null) player.getAdvancements().award(holder, "done");
    }

    public static int total() { return (int) RecipeRegistry.recipes().stream().filter(SpellScrolls::locked).count(); }

    public static int read(Set<String> known) {
        return (int) RecipeRegistry.recipes().stream().filter(s -> SpellScrolls.locked(s) && known.contains(s.id().toString())).count();
    }
    public static int read(Player player) { return read(SpellScrolls.known(player)); }

    public static void learned(ServerPlayer player, SpellDefinition spell) {
        MageLevel.gain(player, spell.ultimate() ? 75 : 25);
        award(player, "first_scroll");
        if (spell.ultimate()) award(player, "ultimate");
        int read = read(player), total = total();
        if (read >= 10) award(player, "scrolls_10");
        if (read >= total / 2) award(player, "scrolls_half");
        if (read >= total) award(player, "scrolls_all");
    }

    private MagePath() {}
}
