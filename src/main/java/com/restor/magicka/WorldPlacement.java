package com.restor.magicka;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class WorldPlacement {
    static boolean allowed(ServerPlayer player, BlockPos pos) {
        var world = player.serverLevel();
        return player.mayBuild() && world.hasChunkAt(pos) && !world.isOutsideBuildHeight(pos)
                && world.getWorldBorder().isWithinBounds(pos) && world.mayInteract(player, pos);
    }
    static boolean placeBlock(ServerPlayer player, BlockPos pos, BlockState state, java.util.UUID construction,
                              BlockPos root, boolean floating, boolean push, Object ignored) {
        var world = player.serverLevel();
        if (!allowed(player, pos)) return false;
        BlockState old = world.getBlockState(pos);
        if (!replaceable(old, !state.is(MagicBlocks.PUDDLE.get()))) return false;
        boolean supported = floating || old.is(MagicBlocks.PUDDLE.get());
        for (Direction face : Direction.values()) {
            BlockPos neighbor = pos.relative(face);
            if (world.hasChunkAt(neighbor)) {
                var neighborState = world.getBlockState(neighbor);
                if (neighborState.isFaceSturdy(world, neighbor, face.getOpposite()) ||
                        neighborState.getBlock() instanceof MagicBlocks.TemporaryBlock && !neighborState.is(MagicBlocks.PUDDLE.get())) supported = true;
            }
        }
        if (!supported) return false;
        var occupants = world.getEntitiesOfClass(LivingEntity.class, new AABB(pos), LivingEntity::isAlive);
        boolean solid = !state.is(MagicBlocks.PUDDLE.get());
        if (solid && !occupants.isEmpty()) {
            if (!push) return false;
            for (LivingEntity entity : occupants) {
                double lift = pos.getY() + 1.01 - entity.getY();
                if (lift <= 0 || lift > 1.6 || !world.noCollision(entity, entity.getBoundingBox().move(0, lift, 0))) return false;
            }
        }
        // Snapshot contains the replaced state, as it does for vanilla placement events.
        BlockSnapshot snapshot = BlockSnapshot.create(world.dimension(), world, pos);
        if (!world.setBlock(pos, state, Block.UPDATE_CLIENTS)) return false;
        var event = new BlockEvent.EntityPlaceEvent(snapshot, world.getBlockState(pos.below()), player);
        if (NeoForge.EVENT_BUS.post(event).isCanceled()) {
            snapshot.restore();
            return false;
        }
        if (solid) for (LivingEntity entity : occupants) {
            entity.teleportTo(entity.getX(), pos.getY() + 1.01, entity.getZ());
            entity.push(0, 0.2, 0); entity.hurtMarked = true;
            if (entity != player && EffectGraph.canHarm(player, entity)) entity.hurt(player.damageSources().indirectMagic(player, player), 1F);
        }
        if (world.getBlockEntity(pos) instanceof MagicBlocks.MagicBlockEntity data) {
            int lifetime = state.is(MagicBlocks.ICE.get()) ? MagicConfig.ICE_LIFE.get()
                    : state.is(MagicBlocks.ROOT.get()) ? MagicConfig.ROOT_LIFE.get()
                    : state.is(MagicBlocks.ARCANE.get()) ? MagicConfig.ARCANE_LIFE.get() : MagicConfig.EARTH_LIFE.get();
            if (state.is(MagicBlocks.PUDDLE.get())) lifetime = 300;
            data.initialize(player.getUUID(), construction, root,
                    MagicConfig.LIFETIME.get() ? world.getGameTime() + lifetime : Long.MAX_VALUE, floating);
        }
        world.updateNeighborsAt(pos, state.getBlock());
        world.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.03);
        return true;
    }
    static boolean replaceable(BlockState old, boolean solid) {
        return old.isAir() || old.is(MagicBlocks.PUDDLE.get()) || solid && old.canBeReplaced() && !(old.getBlock() instanceof MagicBlocks.TemporaryBlock)
                && !old.getFluidState().is(net.minecraft.tags.FluidTags.LAVA);
    }
    static boolean destroy(ServerPlayer player, BlockPos pos) {
        if (!MagicConfig.TERRAIN_DAMAGE.get() || !allowed(player, pos)) return false;
        var world = player.serverLevel();
        if (!(world.getBlockState(pos).getBlock() instanceof MagicBlocks.TemporaryBlock)) return false;
        if (NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(world, pos, world.getBlockState(pos), player)).isCanceled()) return false;
        return world.removeBlock(pos, false);
    }
}
