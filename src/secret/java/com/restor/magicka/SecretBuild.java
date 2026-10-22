package com.restor.magicka;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import java.util.*;

public final class SecretBuild implements SecretSpell {
    static final String SEQUENCE = "5665";
    public static final int HEIGHT = 20;
    public static final boolean ENABLED = Boolean.getBoolean("magicka.secret") || SecretBuild.class.getResource("/magicka_secret.flag") != null;

    @Override public boolean matches(String typed) { return ENABLED && typed.equals(SEQUENCE); }

    public static Map<BlockPos, Block> shape(BlockPos base, Direction facing) {
        Direction right = facing.getClockWise();
        var cells = new TreeMap<BlockPos, Block>(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingLong(BlockPos::asLong));
        for (int dx = -7; dx <= 7; dx++) for (int dz = -4; dz <= 4; dz++) for (int y = 0; y < HEIGHT; y++) {
            BlockPos pos = base.relative(right, dx).relative(facing, dz).above(y);
            Block block = cell(dx, dz, y);
            if (block != null) cells.put(pos.immutable(), block);
        }
        return cells;
    }

    private static Block cell(int x, int z, int y) {
        double shaft = x * x + z * z;
        if (y == HEIGHT - 1) return shaft <= 1.2 ? (shaft == 0 ? Blocks.DYED_TERRACOTTA.magenta() : Blocks.CONCRETE.pink()) : null;
        double head = shaft / (3.3 * 3.3) + Math.pow((y - 16.5) / 3.2, 2);
        if (y >= 14 && head <= 1) return y == 14 && shaft > 5.5 ? Blocks.DYED_TERRACOTTA.magenta() : Blocks.CONCRETE.pink();
        if (y <= 15 && shaft <= 2.6 * 2.6) {
            boolean vein = shaft > 4 && (y + x * 3 + z * 5) % 9 == 0;
            return vein ? Blocks.DYED_TERRACOTTA.magenta() : Blocks.DYED_TERRACOTTA.pink();
        }
        for (int side : new int[]{-3, 3}) {
            double ball = Math.pow((x - side) / 3.3, 2) + Math.pow(z / 3.0, 2) + Math.pow((y - 2.5) / 3.0, 2);
            if (y <= 5 && ball <= 1) return Blocks.DYED_TERRACOTTA.pink();
        }
        return null;
    }

    public static BlockPos base(ServerPlayer player) {
        var hit = SpellAim.hit(player);
        Direction facing = player.getDirection();
        return hit.getType() == HitResult.Type.BLOCK
                ? hit.getBlockPos().relative(hit.getDirection() == Direction.DOWN ? Direction.DOWN : Direction.UP)
                : player.blockPosition().relative(facing, 8);
    }

    @Override public void cast(ServerPlayer player) {
        var level = player.level();
        Direction facing = player.getDirection();
        final BlockPos origin = base(player);
        var layers = new ArrayList<List<Map.Entry<BlockPos, Block>>>();
        for (int i = 0; i < HEIGHT; i++) layers.add(new ArrayList<>());
        shape(origin, facing).entrySet().forEach(e -> layers.get(e.getKey().getY() - origin.getY()).add(e));
        level.playSound(null, origin, SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.5f, 1.3f);
        SpellRuntime.repeat(player, HEIGHT * 2, tick -> {
            if (tick % 2 != 0) return;
            for (var cell : layers.get(tick / 2)) place(player, cell.getKey(), cell.getValue().defaultBlockState());
            var top = origin.above(tick / 2);
            level.sendParticles(ParticleTypes.HEART, top.getX() + .5, top.getY() + 1, top.getZ() + .5, 6, 2, .3, 2, 0);
            level.playSound(null, top, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1f, .6f + tick * .02f);
            if (tick / 2 == HEIGHT - 1) level.playSound(null, top, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.5f, .5f);
        });
    }

    private static void place(ServerPlayer player, BlockPos pos, BlockState state) {
        var level = player.level();
        if (!WorldPlacement.allowed(player, pos)) return;
        var old = level.getBlockState(pos);
        if (!old.isAir() && !old.canBeReplaced() && !WorldMagic.breakBlock(player, pos)) return;
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos), LivingEntity::isAlive)) {
            entity.teleportTo(entity.getX(), pos.getY() + 1.01, entity.getZ());
            entity.setDeltaMovement(entity.getDeltaMovement().add(0, .6, 0));
            entity.syncVelocity = true;
        }
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }

}
