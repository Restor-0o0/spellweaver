package com.restor.magicka;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

final class Fissure {
    static boolean handles(SpellDefinition s) { return s.delivery().has("fissure"); }

    static void open(ServerPlayer p, SpellDefinition s, Vec3 point) {
        ServerLevel level = p.level();
        Vec3 dir = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z);
        dir = dir.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, p.getYRot()) : dir.normalize();
        BlockPos center = surface(level, BlockPos.containing(point));
        int half = (int) Math.clamp(s.number("radius", 3), 1, 8), depth = (int) Math.clamp(s.number("fissure_depth", 2), 1, 4);
        List<BlockPos> line = new ArrayList<>();
        for (int i = 0; i <= half; i++) for (int sign : i == 0 ? new int[]{1} : new int[]{1, -1}) {
            BlockPos column = BlockPos.containing(center.getX() + .5 + dir.x * i * sign, center.getY(), center.getZ() + .5 + dir.z * i * sign);
            if (!line.contains(column)) line.add(surface(level, column));
        }
        level.playSound(null, center, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1f, .6f);
        level.playSound(null, center, SoundEvents.LAVA_POP, SoundSource.PLAYERS, 1.5f, .7f);
        int duration = Math.max(20, (int) s.number("duration_ticks", 120));
        SpellRuntime.repeat(p, duration, age -> {
            int opened = Math.min(line.size(), age + 1);
            if (age < line.size()) crack(p, line.get(age), depth);
            for (int i = 0; i < opened; i++) {
                if ((age + i) % 3 != 0) continue;
                BlockPos c = line.get(i);
                EpicMagic.fx(level, ParticleTypes.FLAME, c.getX() + .5, c.getY() + .2, c.getZ() + .5, 3, .25, .4, .25, .03);
                EpicMagic.fx(level, ParticleTypes.LARGE_SMOKE, c.getX() + .5, c.getY() + .6, c.getZ() + .5, 1, .2, .3, .2, .02);
                if (level.getRandom().nextInt(6) == 0) EpicMagic.fx(level, ParticleTypes.LAVA, c.getX() + .5, c.getY() + .2, c.getZ() + .5, 1, .1, .1, .1, 0);
            }
            if (age % 20 == 0) level.playSound(null, center, SoundEvents.LAVA_AMBIENT, SoundSource.PLAYERS, 1f, .8f);
        });
    }

    private static void crack(ServerPlayer p, BlockPos top, int depth) {
        ServerLevel level = p.level();
        BlockPos bottom = top;
        for (int d = 1; d <= depth; d++) {
            BlockPos at = top.below(d);
            if (!WorldMagic.breakBlock(p, at)) break;
            bottom = at;
        }
        if (bottom != top && MagicConfig.CONSTRUCTION.get() && !level.getBlockState(bottom.below()).isAir())
            WorldMagic.replace(p, bottom.below(), Blocks.MAGMA_BLOCK.defaultBlockState());
        EpicMagic.fx(level, ParticleTypes.LAVA, top.getX() + .5, top.getY(), top.getZ() + .5, 4, .2, .1, .2, 0);
    }

    private static BlockPos surface(ServerLevel level, BlockPos pos) {
        for (int i = 0; i < 4 && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty(); i++) pos = pos.above();
        for (int i = 0; i < 8 && level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty(); i++) pos = pos.below();
        return pos;
    }

    private Fissure() {}
}
