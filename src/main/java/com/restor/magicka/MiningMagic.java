package com.restor.magicka;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;

import java.util.*;

public final class MiningMagic {
    public static final TagKey<Block> MINEABLE = TagKey.create(Registries.BLOCK,ResourceLocation.fromNamespaceAndPath(Magicka.ID,"mineable"));
    public static final TagKey<Block> ORES = TagKey.create(Registries.BLOCK,ResourceLocation.fromNamespaceAndPath(Magicka.ID,"ores"));
    public static final TagKey<Item> SMELTABLE_DROPS = TagKey.create(Registries.ITEM,ResourceLocation.fromNamespaceAndPath(Magicka.ID,"smeltable_mining_drops"));

    public static void mine(ServerPlayer player, Vec3 point, String mode, int budget, int depth, double radius, int tier, boolean smelt, boolean bulkOnly) {
        mine(player,point,mode,budget,depth,radius,tier,smelt,bulkOnly,false);
    }

    public static void mine(ServerPlayer player, Vec3 point, String mode, int budget, int depth, double radius, int tier, boolean smelt, boolean bulkOnly, boolean universal) {
        int remaining = Mth.clamp(budget,1,256), safeTier = Mth.clamp(tier,0,3);
        BlockPos origin = BlockPos.containing(point);
        var hit = SpellAim.hit(player);
        if (hit.getType() == HitResult.Type.BLOCK && hit.getLocation().distanceToSqr(point) < 1.0e-4) origin = hit.getBlockPos();
        List<BlockPos> positions = switch (mode) {
            case "tunnel" -> tunnel(player,origin,Mth.clamp(depth,1,32),Mth.clamp((int)Math.floor(radius),0,3),safeTier,bulkOnly);
            case "plane" -> plane(player,origin,Mth.clamp((int)Math.ceil(radius),0,8),hit.getType()==HitResult.Type.BLOCK && hit.getLocation().distanceToSqr(point)<1.0e-4?hit.getDirection():null);
            case "vein" -> vein(player,origin,remaining);
            case "volume" -> volume(origin,Mth.clamp((int)Math.ceil(radius),0,8),radius);
            default -> List.of(origin);
        };
        for (BlockPos pos : positions) {
            if (remaining <= 0) break;
            BlockState state = player.serverLevel().getBlockState(pos);
            if (!eligible(player,pos,state,safeTier,bulkOnly,universal) || !WorldMagic.loose(player,pos)) continue;
            ItemStack tool = tool(safeTier,state);
            if (harvest(player,pos,state,tool,smelt)) remaining--;
        }
    }

    private static boolean eligible(ServerPlayer player, BlockPos pos, BlockState state, int tier, boolean bulkOnly, boolean universal) {
        boolean pickaxe = state.is(BlockTags.MINEABLE_WITH_PICKAXE), shovel = state.is(BlockTags.MINEABLE_WITH_SHOVEL);
        if ((!universal && !state.is(MINEABLE) && !pickaxe && !shovel) || bulkOnly && state.is(ORES) || state.hasBlockEntity() || !state.getFluidState().isEmpty()) return false;
        if (state.getDestroySpeed(player.serverLevel(),pos) < 0 || state.getDestroySpeed(player.serverLevel(),pos) > 50) return false;
        if (state.is(BlockTags.NEEDS_DIAMOND_TOOL)) return tier >= 3;
        if (state.is(BlockTags.NEEDS_IRON_TOOL)) return tier >= 2;
        if (state.is(BlockTags.NEEDS_STONE_TOOL)) return tier >= 1;
        return tier >= 0;
    }

    private static boolean harvest(ServerPlayer player, BlockPos pos, BlockState state, ItemStack tool, boolean smelt) {
        ServerLevel level = player.serverLevel();
        var blockEntity = level.getBlockEntity(pos);
        List<ItemStack> drops = Block.getDrops(state,level,pos,blockEntity,player,tool);
        if (!level.setBlock(pos,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL)) return false;
        state.spawnAfterBreak(level,pos,tool,true);
        for (ItemStack drop : drops) Block.popResource(level,pos,smelt ? smelt(level,drop) : drop);
        level.levelEvent(player,2001,pos,Block.getId(state));
        return true;
    }

    private static ItemStack smelt(ServerLevel level, ItemStack input) {
        if (!input.is(SMELTABLE_DROPS)) return input;
        var container = new SimpleContainer(input.copyWithCount(1));
        var recipe = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING,container,level);
        if (recipe.isEmpty()) return input;
        ItemStack result = recipe.get().assemble(container,level.registryAccess());
        if (result.isEmpty()) return input;
        result.setCount(result.getCount()*input.getCount());
        return result;
    }

    private static List<BlockPos> plane(ServerPlayer player, BlockPos origin, int radius, Direction face) {
        Direction.Axis axis = planeAxis(face,player.getLookAngle());
        var positions = new ArrayList<BlockPos>();
        for (int a=-radius;a<=radius;a++) for (int b=-radius;b<=radius;b++) {
            BlockPos pos = switch (axis) {
                case X -> origin.offset(0,a,b);
                case Y -> origin.offset(a,0,b);
                case Z -> origin.offset(a,b,0);
            };
            positions.add(pos);
        }
        positions.sort(Comparator.comparingInt(origin::distManhattan));
        return positions;
    }

    static Direction.Axis planeAxis(Direction face, Vec3 look) {
        return face == null ? dominant(look).getAxis() : face.getAxis();
    }

    private static List<BlockPos> tunnel(ServerPlayer player, BlockPos origin, int depth, int radius, int tier, boolean bulkOnly) {
        Vec3 look = player.getLookAngle().normalize();
        Direction.Axis axis = dominant(look).getAxis();
        var positions = new ArrayList<BlockPos>();
        var added = new HashSet<BlockPos>();
        var seenCenters = new HashSet<BlockPos>();
        double maxDistance = depth * Math.sqrt(3), increment = .2;
        for (double distance=0;distance<=maxDistance && seenCenters.size()<depth;distance+=increment) {
            BlockPos center = BlockPos.containing(origin.getCenter().add(look.scale(distance)));
            if (!seenCenters.add(center)) continue;
            BlockState centerState = player.serverLevel().getBlockState(center);
            if (!tunnelClear(player,center,centerState,tier,bulkOnly,false)) break;
            for (BlockPos pos : planeAt(center,axis,radius)) if (added.add(pos)) positions.add(pos);
        }
        return positions;
    }

    private static List<BlockPos> planeAt(BlockPos origin, Direction.Axis axis, int radius) {
        var positions = new ArrayList<BlockPos>();
        for (int a=-radius;a<=radius;a++) for (int b=-radius;b<=radius;b++) positions.add(switch (axis) {
            case X -> origin.offset(0,a,b);
            case Y -> origin.offset(a,0,b);
            case Z -> origin.offset(a,b,0);
        });
        positions.sort(Comparator.comparingInt(origin::distManhattan));
        return positions;
    }

    private static List<BlockPos> volume(BlockPos origin, int extent, double radius) {
        var positions = new ArrayList<BlockPos>(); double reach = radius*radius+.01;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-extent,-extent,-extent),origin.offset(extent,extent,extent)))
            if (pos.distSqr(origin) <= reach) positions.add(pos.immutable());
        positions.sort(Comparator.comparingDouble(origin::distSqr));
        return positions;
    }

    private static List<BlockPos> vein(ServerPlayer player, BlockPos origin, int budget) {
        BlockState start = player.serverLevel().getBlockState(origin);
        if (!start.is(ORES)) return List.of();
        var result = new ArrayList<BlockPos>(); var queue = new ArrayDeque<BlockPos>(); var seen = new HashSet<BlockPos>(); queue.add(origin);
        while (!queue.isEmpty() && result.size() < budget && seen.size() < 256) {
            BlockPos pos = queue.remove();
            if (!seen.add(pos) || !player.serverLevel().hasChunkAt(pos) || !player.serverLevel().getBlockState(pos).is(start.getBlock())) continue;
            result.add(pos);
            for (Direction direction : Direction.values()) queue.add(pos.relative(direction));
        }
        return result;
    }

    private static boolean tunnelClear(ServerPlayer player, BlockPos pos, BlockState state, int tier, boolean bulkOnly, boolean universal) {
        float hardness = state.getDestroySpeed(player.serverLevel(),pos);
        return MagicConfig.TERRAIN_DAMAGE.get() && WorldPlacement.allowed(player,pos) && eligible(player,pos,state,tier,bulkOnly,universal)
                && !state.is(WorldMagic.PROTECTED) && hardness <= 50;
    }

    private static Direction dominant(Vec3 look) {
        return Direction.getNearest(look.x,look.y,look.z);
    }

    private static ItemStack tool(int tier, BlockState state) {
        if(state.is(BlockTags.LEAVES))return new ItemStack(Items.SHEARS);
        boolean shovel = state.is(BlockTags.MINEABLE_WITH_SHOVEL) && !state.is(BlockTags.MINEABLE_WITH_PICKAXE);
        return new ItemStack(shovel ? switch(tier) {
            case 1 -> Items.STONE_SHOVEL;
            case 2 -> Items.IRON_SHOVEL;
            case 3 -> Items.DIAMOND_SHOVEL;
            default -> Items.WOODEN_SHOVEL;
        } : switch (tier) {
            case 1 -> Items.STONE_PICKAXE;
            case 2 -> Items.IRON_PICKAXE;
            case 3 -> Items.DIAMOND_PICKAXE;
            default -> Items.WOODEN_PICKAXE;
        });
    }

    private MiningMagic() {}
}
