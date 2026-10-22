package com.restor.magicka;

import net.minecraft.util.Mth;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.level.BlockEvent;
import java.util.*;

public final class WorldMagic {
    private record WeakRock(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,BlockPos pos) {}
    private static final Map<WeakRock,Long> BRITTLE=new HashMap<>();
    private static boolean brittle(ServerPlayer player,BlockPos pos){return BRITTLE.getOrDefault(new WeakRock(player.level().dimension(),pos),0L)>player.level().getGameTime();}
    public static final TagKey<Block> PROTECTED = TagKey.create(Registries.BLOCK,new ResourceLocation(Magicka.ID,"terrain_protected"));
    public static final TagKey<Block> SHATTERABLE = TagKey.create(Registries.BLOCK,new ResourceLocation(Magicka.ID,"ultimate_shatterable"));
    public static boolean breakBlock(ServerPlayer player, BlockPos pos) { return breakBlock(player,pos,false); }
    public static boolean breakBlock(ServerPlayer player, BlockPos pos, boolean ultimate) {
        if (!loose(player,pos,ultimate)) return false;
        return player.serverLevel().destroyBlock(pos,false,player);
    }
    public static boolean shatterWeak(ServerPlayer player, BlockPos pos, float hardness) {
        var world = player.serverLevel(); var state = world.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(world,pos) > hardness) return false;
        return breakBlock(player,pos);
    }
    private static boolean loose(ServerPlayer player, BlockPos pos) { return loose(player,pos,false); }
    private static boolean loose(ServerPlayer player, BlockPos pos, boolean ultimate) {
        var world = player.serverLevel();
        if (!MagicConfig.TERRAIN_DAMAGE.get() || !WorldPlacement.allowed(player,pos)) return false;
        var state = world.getBlockState(pos);
        if (ultimate && state.is(SHATTERABLE) && state.getDestroySpeed(world,pos) >= 0)
            return !MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(world,pos,state,player));
        boolean magic = state.getBlock() instanceof MagicBlocks.TemporaryBlock;
        if (state.isAir() || state.is(PROTECTED) || state.getDestroySpeed(world,pos) < 0 || state.getDestroySpeed(world,pos) > 50 || (!magic && (state.hasBlockEntity() || !state.getFluidState().isEmpty()))) return false;
        return !MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(world,pos,state,player));
    }
    public static void heave(ServerPlayer player, Vec3 point, int budget, double radius, double lift) {
        var world = player.serverLevel(); BlockPos base = BlockPos.containing(point);
        int r = Mth.clamp((int)Math.ceil(radius),0,16), remaining = Mth.clamp(budget,0,256);
        var columns = new ArrayList<BlockPos>();
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++)
            if (dx*dx + dz*dz <= radius*radius) columns.add(base.offset(dx,0,dz));
        Collections.shuffle(columns,new Random(world.getGameTime()));
        for (BlockPos column : columns) {
            if (remaining <= 0) break;
            BlockPos pos = crest(world,column,r);
            if (pos == null || !loose(player,pos)) continue;
            var clod = net.minecraft.world.entity.item.FallingBlockEntity.fall(world,pos,world.getBlockState(pos));
            clod.setHurtsEntities(2,6);
            clod.setDeltaMovement((pos.getX()+.5-point.x)*.05,lift*(.6+world.random.nextDouble()*.7),(pos.getZ()+.5-point.z)*.05);
            clod.time = 1;
            remaining--;
        }
    }
    private static BlockPos crest(net.minecraft.server.level.ServerLevel world, BlockPos column, int span) {
        for (int dy = span; dy >= -span; dy--) {
            BlockPos pos = column.offset(0,dy,0);
            if (!world.getBlockState(pos).isAir() && world.getBlockState(pos.above()).isAir()) return pos;
        }
        return null;
    }
    public static void destroy(ServerPlayer player, Vec3 center, int budget, double radius) { destroy(player,center,budget,radius,false); }
    public static void destroy(ServerPlayer player, Vec3 center, int budget, double radius, boolean ultimate) {
        int r = Mth.clamp((int)Math.ceil(radius),0,16); BlockPos base = BlockPos.containing(center);
        var positions = new ArrayList<BlockPos>();
        BlockPos.betweenClosed(base.offset(-r,-r,-r),base.offset(r,r,r)).forEach(p -> positions.add(p.immutable()));
        positions.sort(Comparator.comparingDouble(p -> p.distToCenterSqr(center)));
        double reach = (radius+.8)*(radius+.8);
        if (ultimate) for (BlockPos pos : positions)
            if (pos.distToCenterSqr(center) <= reach && player.serverLevel().getBlockState(pos).is(SHATTERABLE)) shatter(player,pos);
        int remaining = Mth.clamp(budget,0,4096)*2;
        for (BlockPos pos : positions) {
            if (remaining <= 0) break;
            int cost=brittle(player,pos)?1:2;
            if (pos.distToCenterSqr(center) <= reach && breakBlock(player,pos,ultimate)) remaining-=cost;
        }
    }
    private static void shatter(ServerPlayer player, BlockPos pos) {
        var world = player.serverLevel();
        if (!breakBlock(player,pos,true)) return;
        world.levelEvent(2001,pos,Block.getId(Blocks.SPAWNER.defaultBlockState()));
        world.sendParticles(ParticleTypes.SOUL,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,24,.4,.4,.4,.04);
        world.playSound(null,pos,net.minecraft.sounds.SoundEvents.RESPAWN_ANCHOR_DEPLETE.get(),net.minecraft.sounds.SoundSource.BLOCKS,1.2F,.7F);
    }
    public static void temperature(ServerPlayer player, Vec3 point, double amount, SpellDefinition spell) {
        var world = player.serverLevel(); BlockPos center = BlockPos.containing(point); int r = Mth.clamp(spell.formula().size()-1,1,2);
        int budget = 16;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-r,-r,-r),center.offset(r,r,r))) {
            BlockPos pos = p.immutable(); if (budget == 0 || !WorldPlacement.allowed(player,pos)) continue;
            BlockState state = world.getBlockState(pos);
            if (amount > 0) {
                if (state.is(MagicBlocks.ROOT.get())) { burnRoots(player,pos); budget--; }
                else if (state.is(MagicBlocks.ICE.get()) || state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)) {
                    if (breakBlock(player,pos)) { budget--; world.sendParticles(ParticleTypes.SPLASH,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,8,.3,.3,.3,.02); }
                } else if (world.getBlockEntity(pos) instanceof MagicBlocks.MagicBlockEntity magic) {
                    if(MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(world,pos,state,player)))continue;
                    magic.integrity = Math.max(1,magic.integrity-.5F); magic.heat = Math.min(200,magic.heat+(float)amount); magic.setChanged();
                } else if (MagicConfig.TERRAIN_DAMAGE.get() && state.isFlammable(world,pos,Direction.UP)) {
                    BlockPos free = kindling(world,pos);
                    if (free != null && replace(player,free,Blocks.FIRE.defaultBlockState())) budget--;
                }
            } else if (state.is(MagicBlocks.PUDDLE.get())) {
                if (WorldPlacement.placeBlock(player,pos,MagicBlocks.ICE.get().defaultBlockState(),UUID.randomUUID(),pos,false,false,null)) budget--;
            }
        }
        if (amount < 0) SpellRuntime.freezeWetZones(player,point,Math.max(2,spell.number("radius",2)));
    }
    private static BlockPos kindling(net.minecraft.server.level.ServerLevel world, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos side = pos.relative(direction);
            if (world.isEmptyBlock(side) && Blocks.FIRE.defaultBlockState().canSurvive(world,side)) return side;
        }
        return null;
    }
    private static void burnRoots(ServerPlayer caster, BlockPos start) {
        var queue = new ArrayDeque<BlockPos>(); var seen = new HashSet<BlockPos>(); queue.add(start); int budget = 32;
        while (!queue.isEmpty() && seen.size() < 192 && budget > 0) {
            BlockPos pos = queue.remove();
            if (!seen.add(pos) || !caster.serverLevel().hasChunkAt(pos) || !caster.serverLevel().getBlockState(pos).is(MagicBlocks.ROOT.get()) || !breakBlock(caster,pos)) continue;
            budget--; for (Direction direction : Direction.values()) queue.add(pos.relative(direction));
        }
    }
    public static void brittle(ServerPlayer player, Vec3 point, double radius) {
        BRITTLE.entrySet().removeIf(e->e.getValue()<=player.level().getGameTime());
        int r = Math.min(16,(int)Math.ceil(radius)); BlockPos base = BlockPos.containing(point);
        for (BlockPos pos : BlockPos.betweenClosed(base.offset(-r,-r,-r),base.offset(r,r,r))) {
            if(!WorldPlacement.allowed(player,pos))continue;
            var state=player.serverLevel().getBlockState(pos);
            if(state.is(PROTECTED) || state.isAir() || state.getDestroySpeed(player.level(),pos)<0 || state.hasBlockEntity() && !(player.serverLevel().getBlockEntity(pos) instanceof MagicBlocks.MagicBlockEntity))continue;
            if(MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(player.level(),pos,state,player)))continue;
            if(BRITTLE.size()<4096)BRITTLE.put(new WeakRock(player.level().dimension(),pos.immutable()),player.level().getGameTime()+160);
            if (player.serverLevel().getBlockEntity(pos) instanceof MagicBlocks.MagicBlockEntity data) { data.integrity = Math.max(.5F,data.integrity/2); data.setChanged(); }
        }
    }
    /** Growth uses cancellable state replacement rather than bypassing claims with a whole tree feature. */
    public static void grow(ServerPlayer player, Vec3 point, int budget) {
        var level = player.serverLevel(); BlockPos base = BlockPos.containing(point);
        for (BlockPos p : BlockPos.betweenClosed(base.offset(-2,-1,-2),base.offset(2,1,2))) {
            if (budget <= 0) break;
            BlockPos pos = p.immutable(); if (!WorldPlacement.allowed(player,pos)) continue;
            var state = level.getBlockState(pos);
            if (state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state)) {
                if (replace(player,pos,crop.getStateForAge(Math.min(crop.getMaxAge(),crop.getAge(state)+2)))) budget--;
            } else if (state.is(MagicBlocks.ROOT.get()) && level.isEmptyBlock(pos.above())) {
                if (WorldPlacement.placeBlock(player,pos.above(),state,UUID.randomUUID(),pos.above(),false,false,null)) budget--;
            } else if (state.is(Blocks.DIRT) && level.isEmptyBlock(pos.above())) {
                if (replace(player,pos,Blocks.GRASS_BLOCK.defaultBlockState())) budget--;
            }
        }
    }
    public static boolean replace(ServerPlayer player, BlockPos pos, BlockState state) {
        var world = player.serverLevel();
        if (!WorldPlacement.allowed(player,pos) || world.getBlockState(pos).hasBlockEntity() || world.getBlockState(pos).is(PROTECTED)) return false;
        var snapshot = BlockSnapshot.create(world.dimension(),world,pos);
        if (!world.setBlock(pos,state,Block.UPDATE_CLIENTS)) return false;
        if (MinecraftForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(snapshot,world.getBlockState(pos.below()),player))) { snapshot.restore(true, false); return false; }
        world.updateNeighborsAt(pos,state.getBlock()); return true;
    }
    private WorldMagic() {}
    static void reset(){BRITTLE.clear();}
}
