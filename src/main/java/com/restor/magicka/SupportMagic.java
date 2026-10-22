package com.restor.magicka;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import java.util.UUID;

public final class SupportMagic {
    public static void effect(ServerPlayer caster, LivingEntity target, String id, int ticks, int strength) {
        var key = Identifier.tryParse(id);
        var effect = key == null ? null : BuiltInRegistries.MOB_EFFECT.get(key).orElse(null);
        if (effect == null || !effect.value().isBeneficial() && !EffectGraph.canHarm(caster, target)) return;
        target.addEffect(new MobEffectInstance(effect, Math.clamp(ticks, 1, 72000), Math.clamp(strength - 1, 0, 4)), caster);
    }

    private static BlockPos open(ServerPlayer caster, Vec3 point) {
        Vec3 back = caster.getEyePosition().subtract(point);
        return BlockPos.containing(back.lengthSqr() < .01 ? point : point.add(back.normalize().scale(.45)));
    }

    private static boolean place(ServerPlayer caster, BlockPos pos, BlockState state) {
        int dy = 0;
        while (dy < 4 && !WorldPlacement.placeBlock(caster, pos.above(dy), state, UUID.randomUUID(), pos.above(dy), true, false, null)) dy++;
        if (dy == 4) return false;
        pos = pos.above(dy);
        caster.level().playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, .8f, 1.4f);
        return true;
    }

    private static BlockPos at(ServerPlayer caster, Vec3 point, double offsetY) {
        return offsetY != 0 ? BlockPos.containing(point.add(0, offsetY, 0)) : open(caster, point);
    }

    public static boolean light(ServerPlayer caster, Vec3 point, double offsetY) {
        return place(caster, at(caster, point, offsetY), MagicBlocks.LIGHT.get().defaultBlockState());
    }

    public static int path(ServerPlayer caster, Vec3 point, double spacing, int count) {
        Vec3 from = caster.getEyePosition(), direction = point.subtract(from);
        double length = direction.length();
        if (length < 1) return light(caster, point, 1) ? 1 : 0;
        direction = direction.scale(1 / length);
        spacing = Math.clamp(spacing, 2, 16);
        int placed = 0, limit = Math.clamp(count, 1, 16);
        for (double d = Math.min(length, spacing * .5); placed < limit; d = Math.min(length, d + spacing)) {
            boolean last = d >= length - .5;
            if (place(caster, last ? open(caster, point) : BlockPos.containing(from.add(direction.scale(d))), MagicBlocks.LIGHT.get().defaultBlockState())) placed++;
            if (last) break;
        }
        return placed;
    }

    public static int ring(ServerPlayer caster, Vec3 point, double radius, int count) {
        var level = caster.level();
        int placed = 0;
        count = Math.clamp(count, 1, 16);
        radius = Math.clamp(radius, 1, 32);
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            BlockPos column = BlockPos.containing(point.x + Math.cos(angle) * radius, point.y + 2, point.z + Math.sin(angle) * radius);
            for (int dy = 0; dy < 5; dy++) {
                BlockPos pos = column.below(dy);
                if (!level.getBlockState(pos).isAir() || level.getBlockState(pos.below()).isAir()) continue;
                if (place(caster, pos, MagicBlocks.LIGHT.get().defaultBlockState())) placed++;
                break;
            }
        }
        return placed;
    }

    public static boolean glowstone(ServerPlayer caster, Vec3 point, double offsetY) {
        return place(caster, at(caster, point, offsetY), MagicBlocks.GLOW.get().defaultBlockState());
    }

    public static int reveal(ServerPlayer caster, Vec3 point, double radius, int ticks) {
        double r = Math.clamp(radius, 1, 128);
        var found = caster.level().getEntitiesOfClass(LivingEntity.class, new AABB(point, point).inflate(r),
                e -> e != caster && e.isAlive() && !e.isSpectator() && e.distanceToSqr(point) <= r * r && EffectGraph.canHarm(caster, e));
        for (var entity : found) entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, Math.clamp(ticks, 20, 6000), 0, false, false), caster);
        caster.level().sendParticles(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH,-1), point.x, point.y + 1, point.z, 1, 0, 0, 0, 0);
        caster.level().playSound(null, BlockPos.containing(point), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, .6f, 1.8f);
        return found.size();
    }

    public static int extinguish(ServerPlayer caster, Vec3 point, double radius) {
        var level = caster.level();
        int r = Math.clamp((int) Math.ceil(radius), 1, 32), doused = 0;
        BlockPos center = BlockPos.containing(point);
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
            if (p.distToCenterSqr(point) > radius * radius || !level.hasChunkAt(p)) continue;
            BlockPos pos = p.immutable();
            BlockState state = level.getBlockState(pos);
            if (!state.is(BlockTags.FIRE) || !WorldPlacement.allowed(caster, pos)) continue;
            if (NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.block.BreakBlockEvent(level, pos, state, caster)).isCanceled()) continue;
            if (level.removeBlock(pos, false)) {
                doused++;
                level.sendParticles(ParticleTypes.CLOUD, pos.getX() + .5, pos.getY() + .3, pos.getZ() + .5, 3, .2, .1, .2, .01);
            }
        }
        for (var entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(point, point).inflate(radius), LivingEntity::isAlive)) {
            if (entity.isOnFire() || MagicStatuses.has(entity, MagicStatuses.Status.BURNING)) doused++;
            MagicStatuses.remove(entity, MagicStatuses.Status.BURNING);
            entity.clearFire();
        }
        if (doused > 0) level.playSound(null, center, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, .7f, 1.2f);
        return doused;
    }

    private SupportMagic() {}
}
