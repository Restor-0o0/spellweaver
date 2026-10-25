package com.restor.magicka;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

final class Deluge {
    static boolean handles(SpellDefinition s) { return s.delivery().has("wave"); }

    static void cast(ServerPlayer p, SpellDefinition s) {
        ServerLevel level = p.serverLevel();
        Vec3 dir = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z);
        dir = dir.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, p.getYRot()) : dir.normalize();
        double range = Math.clamp(s.number("radius", 9), 3, 16), width = Math.clamp(s.number("burst_radius", 9), 3, 16);
        Vec3 aim = SpellAim.hit(p).getLocation();
        if (aim.distanceToSqr(p.getEyePosition()) > 64 * 64) aim = p.getEyePosition().add(p.getLookAngle().scale(64));
        Vec3 back = aim.subtract(dir.scale(range * .5)), side = new Vec3(-dir.z, 0, dir.x);
        Vec3 start = back.subtract(p.position()).dot(dir) < 1.2 ? p.position().add(dir.scale(1.2)) : back;
        int travel = Math.clamp((int) s.number("travel_ticks", 16), 6, 40), linger = Math.clamp((int) s.number("water_ticks", 60), 0, 200);
        boolean flood = MagicConfig.CONSTRUCTION.get() && linger > 0;
        var hitEffects = s.effects().stream().filter(n -> !SpellDefinition.string(n, "type", "").equals("extinguish")).toList();
        var worldEffects = s.effects().stream().filter(n -> SpellDefinition.string(n, "type", "").equals("extinguish")).toList();
        EffectGraph.world(p, s, worldEffects, aim, false, true);
        Set<UUID> struck = new HashSet<>();
        List<BlockPos> placed = new ArrayList<>();
        Vec3 direction = dir;
        level.playSound(null, BlockPos.containing(start), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 1.5f, .6f);
        level.playSound(null, BlockPos.containing(start), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.2f, .8f);
        SpellRuntime.timed(p, travel + linger, age -> {
            if (age >= travel) return;
            double t = (age + 1) / (double) travel, reach = range * t, half = 1.5 + (width / 2 - 1.5) * t;
            Vec3 front = start.add(direction.scale(reach));
            for (double o = -half; o <= half; o += .5) {
                Vec3 q = front.add(side.scale(o));
                double y = ground(level, q);
                EpicMagic.fx(level, ParticleTypes.SPLASH, q.x, y + .2, q.z, 3, .2, .4, .2, .1);
                EpicMagic.fx(level, ParticleTypes.FALLING_WATER, q.x, y + 1.6, q.z, 1, .2, .5, .2, 0);
                if (age % 2 == 0) EpicMagic.fx(level, ParticleTypes.BUBBLE_POP, q.x, y + 1, q.z, 1, .2, .4, .2, .05);
                if (flood && age % 2 == 0 && placed.size() < 96) water(p, BlockPos.containing(q.x, y, q.z), placed);
            }
            if (age % 4 == 0) level.playSound(null, BlockPos.containing(front), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 1f, .7f + (float) t * .3f);
            AABB box = new AABB(front, front).inflate(half + 1, 2.5, half + 1).expandTowards(direction.scale(-1.5));
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != p && e.isAlive() && !struck.contains(e.getUUID()))) {
                Vec3 rel = e.position().subtract(start);
                if (rel.dot(direction) < reach - 2.5 || Math.abs(rel.dot(side)) > half + .8) continue;
                struck.add(e.getUUID());
                EffectGraph.entity(p, e, s, hitEffects, e.position().subtract(direction.scale(2)), false);
                e.push(direction.x * .8, .25, direction.z * .8);
                e.hurtMarked = true;
            }
        }, () -> {
            for (BlockPos pos : placed) if (level.getFluidState(pos).is(Fluids.WATER) && level.getBlockState(pos).is(Blocks.WATER)) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        });
    }

    private static double ground(ServerLevel level, Vec3 q) {
        BlockPos pos = BlockPos.containing(q.x, q.y + 1, q.z);
        for (int i = 0; i < 5; i++) {
            if (!level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()) return pos.getY();
            pos = pos.below();
        }
        return q.y;
    }

    private static void water(ServerPlayer p, BlockPos pos, List<BlockPos> placed) {
        ServerLevel level = p.serverLevel();
        if (!level.getBlockState(pos).isAir() || level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()) return;
        if (WorldMagic.replace(p, pos, Blocks.WATER.defaultBlockState())) placed.add(pos.immutable());
    }

    private Deluge() {}
}
