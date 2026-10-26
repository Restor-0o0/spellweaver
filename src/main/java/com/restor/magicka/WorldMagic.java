package com.restor.magicka;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import java.util.*;

public final class WorldMagic {
    private record WeakRock(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,BlockPos pos) {}
    private static final Map<WeakRock,Long> BRITTLE=new HashMap<>();
    private static boolean brittle(ServerPlayer player,BlockPos pos){return BRITTLE.getOrDefault(new WeakRock(player.level().dimension(),pos),0L)>player.level().getGameTime();}
    public static final TagKey<Block> PROTECTED = TagKey.create(Registries.BLOCK,Identifier.fromNamespaceAndPath(Magicka.ID,"terrain_protected"));
    public static final TagKey<Block> SHATTERABLE = TagKey.create(Registries.BLOCK,Identifier.fromNamespaceAndPath(Magicka.ID,"ultimate_shatterable"));
    public static boolean breakBlock(ServerPlayer player, BlockPos pos) { return breakBlock(player,pos,false); }
    public static boolean breakBlock(ServerPlayer player, BlockPos pos, boolean ultimate) {
        if (!loose(player,pos,ultimate)) return false;
        return player.level().destroyBlock(pos,false,player);
    }
    public static boolean shatterWeak(ServerPlayer player, BlockPos pos, float hardness) {
        var world = player.level(); var state = world.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(world,pos) > hardness) return false;
        return breakBlock(player,pos);
    }
    private static boolean loose(ServerPlayer player, BlockPos pos) { return loose(player,pos,false); }
    private static boolean loose(ServerPlayer player, BlockPos pos, boolean ultimate) {
        var world = player.level();
        if (!MagicConfig.TERRAIN_DAMAGE.get() || !WorldPlacement.allowed(player,pos)) return false;
        var state = world.getBlockState(pos);
        if (ultimate && state.is(SHATTERABLE) && state.getDestroySpeed(world,pos) >= 0)
            return !NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.block.BreakBlockEvent(world,pos,state,player)).isCanceled();
        boolean magic = state.getBlock() instanceof MagicBlocks.TemporaryBlock;
        if (state.isAir() || state.is(PROTECTED) || state.getDestroySpeed(world,pos) < 0 || state.getDestroySpeed(world,pos) > 50 || (!magic && (state.hasBlockEntity() || !state.getFluidState().isEmpty()))) return false;
        return !NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.block.BreakBlockEvent(world,pos,state,player)).isCanceled();
    }
    public static void ignite(ServerPlayer player, Vec3 point, double radius) {
        var world = player.level(); BlockPos center = BlockPos.containing(point); int r = (int)Math.ceil(radius);
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-r,-2,-r),center.offset(r,2,r))) {
            if (p.distSqr(center) > radius*radius+4 || world.getRandom().nextFloat() > .6f) continue;
            BlockPos pos = p.immutable();
            if (world.getBlockState(pos).isAir() && world.getBlockState(pos.below()).isFaceSturdy(world,pos.below(),Direction.UP) && WorldPlacement.allowed(player,pos))
                world.setBlockAndUpdate(pos,net.minecraft.world.level.block.BaseFireBlock.getState(world,pos));
        }
    }
    public static void heave(ServerPlayer player, Vec3 point, int budget, double radius, double lift) {
        var world = player.level(); BlockPos base = BlockPos.containing(point);
        int r = Math.clamp((int)Math.ceil(radius),0,16), remaining = Math.clamp(budget,0,256);
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
            clod.setDeltaMovement((pos.getX()+.5-point.x)*.05,lift*(.6+world.getRandom().nextDouble()*.7),(pos.getZ()+.5-point.z)*.05);
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
        int r = Math.clamp((int)Math.ceil(radius),0,16); BlockPos base = BlockPos.containing(center);
        var positions = new ArrayList<BlockPos>();
        BlockPos.betweenClosed(base.offset(-r,-r,-r),base.offset(r,r,r)).forEach(p -> positions.add(p.immutable()));
        positions.sort(Comparator.comparingDouble(p -> bowl(p,center)));
        double reach = (radius+.8)*(radius+.8), core = Math.max(1,radius*.65), edge = Math.max(core+.3,radius+.8);
        if (ultimate) for (BlockPos pos : positions)
            if (pos.distToCenterSqr(center) <= reach && player.level().getBlockState(pos).is(SHATTERABLE)) shatter(player,pos);
        int remaining = Math.clamp(budget,0,4096)*2;
        long seed = player.level().getGameTime()*31+base.asLong();
        for (BlockPos pos : positions) {
            if (remaining <= 0) break;
            double d = Math.sqrt(bowl(pos,center));
            if (d > edge) continue;
            if (d > core && chance(seed,pos) > .75*(edge-d)/(edge-core)) continue;
            int cost=brittle(player,pos)?1:2;
            if (breakBlock(player,pos,ultimate)) remaining-=cost;
        }
    }
    private static double chance(long seed, BlockPos pos) {
        long h = seed ^ pos.asLong()*0x9E3779B97F4A7C15L;
        h = (h ^ h >>> 30)*0xBF58476D1CE4E5B9L; h = (h ^ h >>> 27)*0x94D049BB133111EBL; h ^= h >>> 31;
        return (h >>> 11)*0x1.0p-53;
    }
    /** Squared distance with depth counted double, so the hole is a shallow bowl rather than a sphere. */
    private static double bowl(BlockPos pos, Vec3 center) {
        double dx = pos.getX()+.5-center.x, dy = pos.getY()+.5-center.y, dz = pos.getZ()+.5-center.z;
        if (dy < -.6) dy = dy*2+.6;
        return dx*dx+dy*dy+dz*dz;
    }
    private static void shatter(ServerPlayer player, BlockPos pos) {
        var world = player.level();
        if (!breakBlock(player,pos,true)) return;
        world.levelEvent(2001,pos,Block.getId(Blocks.SPAWNER.defaultBlockState()));
        EpicMagic.fx(world,ParticleTypes.SOUL,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,24,.4,.4,.4,.04);
        world.playSound(null,pos,net.minecraft.sounds.SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(),net.minecraft.sounds.SoundSource.BLOCKS,1.2F,.7F);
    }
    public static void temperature(ServerPlayer player, Vec3 point, double amount, SpellDefinition spell) {
        var world = player.level(); BlockPos center = BlockPos.containing(point); int r = Math.clamp(spell.formula().size()-1,1,2);
        int budget = 16;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-r,-r,-r),center.offset(r,r,r))) {
            BlockPos pos = p.immutable(); if (budget == 0 || !WorldPlacement.allowed(player,pos)) continue;
            BlockState state = world.getBlockState(pos);
            if (amount > 0) {
                if (state.is(MagicBlocks.ROOT.get())) { burnRoots(player,pos); budget--; }
                else if (state.is(MagicBlocks.ICE.get()) || state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)) {
                    if (breakBlock(player,pos)) { budget--; EpicMagic.fx(world,ParticleTypes.SPLASH,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,8,.3,.3,.3,.02); }
                } else if (world.getBlockEntity(pos) instanceof MagicBlocks.MagicBlockEntity magic) {
                    if(NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.block.BreakBlockEvent(world,pos,state,player)).isCanceled())continue;
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
            if (!seen.add(pos) || !caster.level().hasChunkAt(pos) || !caster.level().getBlockState(pos).is(MagicBlocks.ROOT.get()) || !breakBlock(caster,pos)) continue;
            budget--; for (Direction direction : Direction.values()) queue.add(pos.relative(direction));
        }
    }
    public static void brittle(ServerPlayer player, Vec3 point, double radius) {
        BRITTLE.entrySet().removeIf(e->e.getValue()<=player.level().getGameTime());
        int r = Math.min(16,(int)Math.ceil(radius)); BlockPos base = BlockPos.containing(point);
        for (BlockPos pos : BlockPos.betweenClosed(base.offset(-r,-r,-r),base.offset(r,r,r))) {
            if(!WorldPlacement.allowed(player,pos))continue;
            var state=player.level().getBlockState(pos);
            if(state.is(PROTECTED) || state.isAir() || state.getDestroySpeed(player.level(),pos)<0 || state.hasBlockEntity() && !(player.level().getBlockEntity(pos) instanceof MagicBlocks.MagicBlockEntity))continue;
            if(NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.block.BreakBlockEvent(player.level(),pos,state,player)).isCanceled())continue;
            if(BRITTLE.size()<4096)BRITTLE.put(new WeakRock(player.level().dimension(),pos.immutable()),player.level().getGameTime()+160);
            if (player.level().getBlockEntity(pos) instanceof MagicBlocks.MagicBlockEntity data) { data.integrity = Math.max(.5F,data.integrity/2); data.setChanged(); }
        }
    }
    /** Growth uses cancellable state replacement rather than bypassing claims with a whole tree feature. */
    public static void grow(ServerPlayer player, Vec3 point, int budget) {
        var level = player.level(); BlockPos base = BlockPos.containing(point);
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
        var world = player.level();
        if (!WorldPlacement.allowed(player,pos) || world.getBlockState(pos).hasBlockEntity() || world.getBlockState(pos).is(PROTECTED)) return false;
        var snapshot = BlockSnapshot.create(world.dimension(),world,pos);
        if (!world.setBlock(pos,state,Block.UPDATE_CLIENTS)) return false;
        if (NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(snapshot,world.getBlockState(pos.below()),player)).isCanceled()) { snapshot.restore(); return false; }
        world.updateNeighborsAt(pos,state.getBlock()); return true;
    }
    private WorldMagic() {}
    static void reset(){BRITTLE.clear();}
}
