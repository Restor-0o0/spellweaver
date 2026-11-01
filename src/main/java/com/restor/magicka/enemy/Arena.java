package com.restor.magicka.enemy;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Endless waves around the player: each wave buys a mix of enemies from a growing budget, every fifth brings a boss. */
public final class Arena {
    public record Entry(String id, int cost, int from) {}
    public static final List<Entry> ROSTER = List.of(
            new Entry("fodder", 1, 1), new Entry("cave_skitter", 1, 1), new Entry("goblin_warrior", 2, 1),
            new Entry("goblin_archer", 2, 2), new Entry("goblin_mage", 3, 3), new Entry("shield_beetle", 3, 3),
            new Entry("goblin_frost_mage", 3, 4), new Entry("goblin_storm_mage", 3, 4), new Entry("mushroom_nurse", 3, 4),
            new Entry("goblin_bomber", 3, 5), new Entry("stone_crawler", 4, 5), new Entry("spark_imp", 2, 5),
            new Entry("flood_caster", 4, 6), new Entry("element_binder", 4, 6), new Entry("goblin_shaman", 4, 7),
            new Entry("burrower", 4, 7), new Entry("countermage", 5, 8), new Entry("armored_knight", 6, 8), new Entry("troll", 8, 10));
    public static final int CAP = 24, BREAK = 100, LEASH = 32;
    public static final String BEST = "magicka_arena_best";

    static final class Run {
        final ServerPlayer player; final ServerLevel level; final Vec3 center;
        final ServerBossEvent bar = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
        final List<Mob> mobs = new ArrayList<>();
        int wave, total, pause = 40;
        Run(ServerPlayer player) { this.player = player; this.level = player.getLevel(); this.center = player.position(); bar.addPlayer(player); }
    }
    private static final Map<UUID, Run> RUNS = new HashMap<>();

    public static int budget(int wave) { return 3 + 3 * wave; }
    public static List<String> wave(int n, net.minecraft.util.RandomSource random) {
        List<String> out = new ArrayList<>();
        int budget = budget(n);
        if (n % 5 == 0) { String boss = n >= 10 ? "troll" : "armored_knight"; out.add(boss); budget -= cost(boss); }
        List<Entry> open = ROSTER.stream().filter(e -> e.from <= n).toList();
        while (budget > 0 && out.size() < CAP) {
            int left = budget;
            List<Entry> fits = open.stream().filter(e -> e.cost <= left).toList();
            if (fits.isEmpty()) break;
            Entry pick = fits.get(random.nextInt(fits.size()));
            out.add(pick.id);
            budget -= pick.cost;
        }
        return out;
    }
    static int cost(String id) { return ROSTER.stream().filter(e -> e.id.equals(id)).findFirst().map(Entry::cost).orElse(1); }
    public static boolean running(ServerPlayer p) { return RUNS.containsKey(p.getUUID()); }

    public static boolean start(ServerPlayer p) {
        if (RUNS.containsKey(p.getUUID())) return false;
        RUNS.put(p.getUUID(), new Run(p));
        p.sendSystemMessage(Component.translatable("message.magicka.arena_start"));
        p.getLevel().playSound(null, p.blockPosition(), SoundEvents.RAID_HORN, SoundSource.HOSTILE, 1f, 1f);
        return true;
    }
    public static boolean stop(ServerPlayer p) {
        Run run = RUNS.remove(p.getUUID());
        if (run == null) return false;
        end(run);
        return true;
    }
    private static void end(Run run) {
        int cleared = run.pause > 0 ? run.wave : run.wave - 1;
        run.bar.removeAllPlayers();
        run.mobs.forEach(m -> { if (m.isAlive()) m.discard(); });
        var data = run.player.getPersistentData();
        int best = Math.max(data.getInt(BEST), cleared);
        data.putInt(BEST, best);
        run.player.sendSystemMessage(Component.translatable("message.magicka.arena_end", cleared, best));
    }
    private static void next(Run run) {
        run.wave++;
        run.mobs.clear();
        double a = run.level.random.nextDouble() * Math.PI * 2;
        Vec3 at = run.center.add(Math.cos(a) * 10, 0, Math.sin(a) * 10);
        run.mobs.addAll(MagicEnemies.spawn(run.level, at, wave(run.wave, run.level.random)));
        run.mobs.forEach(m -> { m.setTarget(run.player); m.setPersistenceRequired(); });
        run.total = Math.max(1, run.mobs.size());
        run.level.playSound(null, run.player.blockPosition(), run.wave % 5 == 0 ? SoundEvents.WITHER_SPAWN : SoundEvents.RAID_HORN, SoundSource.HOSTILE, .8f, 1.2f);
    }
    static void tick(Run run) {
        run.mobs.removeIf(m -> !m.isAlive());
        if (run.pause > 0) {
            if (--run.pause == 0) next(run);
            else if (run.pause % 10 == 0) {
                run.bar.setName(Component.translatable("hud.magicka.arena_break", run.wave + 1, (run.pause + 19) / 20));
                run.bar.setProgress(1 - run.pause / (float) BREAK);
            }
            return;
        }
        if (run.level.getGameTime() % 10 != 0) return;
        for (Mob m : run.mobs) {
            if (m.position().distanceTo(run.center) > LEASH) {
                Vec3 back = run.center.add(m.getRandom().nextGaussian() * 4, 0, m.getRandom().nextGaussian() * 4);
                m.teleportTo(back.x, run.center.y + 1, back.z);
            }
            if (m.getTarget() == null) m.setTarget(run.player);
        }
        run.bar.setName(Component.translatable("hud.magicka.arena_wave", run.wave, run.mobs.size()));
        run.bar.setProgress(run.mobs.size() / (float) run.total);
        if (run.mobs.isEmpty()) {
            run.pause = BREAK;
            run.player.heal(4);
            run.level.playSound(null, run.player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, .8f, 1f);
        }
    }

    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (Run run : List.copyOf(RUNS.values())) {
            if (!run.player.isAlive() || run.player.hasDisconnected() || run.player.level != run.level) { RUNS.remove(run.player.getUUID()); end(run); }
            else tick(run);
        }
    }
    @SubscribeEvent public void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) stop(p);
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) stop(p);
    }
    @SubscribeEvent public void stopped(ServerStoppedEvent event) { RUNS.clear(); }

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("arena")
                .then(Commands.literal("start").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    if (!start(p)) { c.getSource().sendFailure(Component.translatable("message.magicka.arena_busy")); return 0; }
                    return 1;
                }))
                .then(Commands.literal("stop").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    if (!stop(p)) { c.getSource().sendFailure(Component.translatable("message.magicka.arena_none")); return 0; }
                    return 1;
                }));
    }
}
