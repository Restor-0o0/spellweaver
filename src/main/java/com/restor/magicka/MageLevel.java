package com.restor.magicka;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class MageLevel {
    private static final String TAG = "magicka_mage_xp";
    private static volatile int clientXp;

    public static int xpFor(int level) { return (int) Math.min(Integer.MAX_VALUE, 25L * level * level); }

    public static int level(int xp) {
        int max = MagicConfig.MAX_MAGE_LEVEL.get(), level = 0;
        while (level < max && xpFor(level + 1) <= xp) level++;
        return level;
    }

    public static int xp(Player player) { return player.getPersistentData().getInt(TAG); }
    public static int level(Player player) { return MagicConfig.MAGE_LEVELS.get() ? level(xp(player)) : 0; }
    public static int clientXp() { return clientXp; }
    public static void acceptClient(int xp) { clientXp = xp; }

    public static int maxElements(Player player, int level) {
        if (player.isCreative() || player instanceof FakePlayer || !MagicConfig.MAGE_LEVELS.get() || !MagicConfig.FORMULA_BY_LEVEL.get()) return Formula.MAX;
        return level >= MagicConfig.FIVE_ELEMENTS.get() ? 5 : level >= MagicConfig.FOUR_ELEMENTS.get() ? 4 : 3;
    }
    public static int maxElements(Player player) { return maxElements(player, level(player)); }
    public static int clientMaxElements(Player player) { return maxElements(player, level(clientXp)); }

    public static int levelFor(int elements) {
        return elements >= 5 ? MagicConfig.FIVE_ELEMENTS.get() : elements == 4 ? MagicConfig.FOUR_ELEMENTS.get() : 0;
    }

    public static void bonus(Player player, int[] deltas) {
        int level = level(player);
        deltas[MagicStats.Stat.CAST_SPEED.ordinal()] += level * MagicConfig.LEVEL_CAST_SPEED.get();
        deltas[MagicStats.Stat.DAMAGE.ordinal()] += level * MagicConfig.LEVEL_DAMAGE.get();
    }

    public static void gain(ServerPlayer player, double amount) {
        if (!MagicConfig.MAGE_LEVELS.get() || player instanceof FakePlayer) return;
        int gained = (int) Math.round(amount * MagicConfig.MAGE_XP.get());
        if (gained <= 0) return;
        int before = level(player), xp = (int) Math.min(Integer.MAX_VALUE, (long) xp(player) + gained);
        set(player, xp);
        int after = level(player);
        if (after > before) levelUp(player, before, after);
    }

    public static void set(ServerPlayer player, int xp) {
        int before = level(player);
        player.getPersistentData().putInt(TAG, Math.max(0, xp));
        if (level(player) != before) MagicStats.invalidate(player);
        sync(player);
    }

    private static void levelUp(ServerPlayer player, int before, int after) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, .8f, 1.3f);
        player.sendSystemMessage(Component.translatable("mage.magicka.level_up", after).withStyle(ChatFormatting.LIGHT_PURPLE));
        int elements = maxElements(player, after);
        if (elements > maxElements(player, before)) player.sendSystemMessage(Component.translatable("mage.magicka.more_elements", elements).withStyle(ChatFormatting.GOLD));
        if (after >= MagicConfig.FOUR_ELEMENTS.get()) MagePath.award(player, "four_elements");
        if (after >= MagicConfig.FIVE_ELEMENTS.get()) MagePath.award(player, "five_elements");
        if (after >= MagicConfig.MAX_MAGE_LEVEL.get()) MagePath.award(player, "archmage");
    }

    public static void cast(ServerPlayer player, SpellDefinition recipe, boolean self) {
        MagicGear.cast(player, recipe, self);
        if (recipe == null) return;
        MagePath.award(player, "root");
        gain(player, recipe.ultimate() ? 10 : recipe.formula().size());
    }

    public static void fizzle(ServerPlayer player) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, .6f, 1.4f);
    }

    public static void reject(ServerPlayer player, int elements) {
        fizzle(player);
        player.displayClientMessage(Component.translatable("mage.magicka.too_long", elements, levelFor(elements)).withStyle(ChatFormatting.RED), true);
    }

    public static void sync(ServerPlayer player) { PacketDistributor.sendToPlayer(player, new MagicNetwork.MageXp(xp(player))); }

    @SubscribeEvent public void death(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer) || !com.restor.magicka.enemy.MagicEnemies.bySpell || !(dead instanceof Enemy)) return;
        boolean boss = ScrollDrops.boss(dead);
        gain(killer, dead.getMaxHealth() / 4 * (boss ? 2 : 1));
        if (boss) MagePath.award(killer, "boss_slayer");
    }

    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        var data = p.getPersistentData();
        if (!data.contains(TAG)) { data.putInt(TAG, veteran(p) ? xpFor(MagicConfig.FIVE_ELEMENTS.get()) : 0); MagicStats.invalidate(p); }
        sync(p);
    }

    private static boolean veteran(Player player) {
        var data = player.getPersistentData();
        return data.contains("magicka_scrolls") || data.contains("magicka_stats") || data.contains("magicka_gear");
    }
    @SubscribeEvent public void clone(PlayerEvent.Clone event) {
        if (event.getOriginal().getPersistentData().contains(TAG)) event.getEntity().getPersistentData().putInt(TAG, xp(event.getOriginal()));
    }

    public MageLevel() {}
}
