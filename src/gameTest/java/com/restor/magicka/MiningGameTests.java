package com.restor.magicka;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.function.Consumer;

@GameTestHolder(Magicka.ID)
@PrefixGameTestTemplate(false)
public final class MiningGameTests {
    @GameTest(template="empty")
    public static void singleDropsNormalLoot(GameTestHelper h) {
        var player=RecipeGameTests.caster(h);BlockPos local=new BlockPos(2,2,2);h.setBlock(local,Blocks.STONE);
        MiningMagic.mine(player,h.absolutePos(local).getCenter(),"single",1,1,0,0,false,false);
        h.assertBlockPresent(Blocks.AIR,local);
        h.assertTrue(drops(h,local,Items.COBBLESTONE)==1,"Single mining drops normal pickaxe loot");h.succeed();
    }

    @GameTest(template="empty")
    public static void targetedCastMinesHitBlock(GameTestHelper h) {
        var player=RecipeGameTests.caster(h);BlockPos local=new BlockPos(1,3,5);h.setBlock(local,Blocks.STONE);
        player.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(1.5,2,1.5)));player.setYRot(0);player.setXRot(3);
        SpellRuntime.cast(player,RecipeGameTests.spell("Thermal Pick"),false);
        h.assertBlockPresent(Blocks.AIR,local);
        h.assertTrue(drops(h,local,Items.COBBLESTONE)==1,"Casting at a block mines the ray hit rather than the adjacent position");h.succeed();
    }

    @GameTest(template="empty")
    public static void dirtAndCobblestoneAreMineable(GameTestHelper h) {
        var player=RecipeGameTests.caster(h);BlockPos dirt=new BlockPos(2,2,2),cobble=new BlockPos(3,2,2);
        h.setBlock(dirt,Blocks.DIRT);h.setBlock(cobble,Blocks.COBBLESTONE);
        MiningMagic.mine(player,h.absolutePos(dirt).getCenter(),"single",1,1,0,1,false,false);
        MiningMagic.mine(player,h.absolutePos(cobble).getCenter(),"single",1,1,0,1,false,false);
        h.assertBlockPresent(Blocks.AIR,dirt);h.assertBlockPresent(Blocks.AIR,cobble);h.succeed();
    }

    @GameTest(template="empty")
    public static void hydraulicFractureRepeatsAlongBeam(GameTestHelper h) {
        var player=RecipeGameTests.caster(h);BlockPos first=new BlockPos(1,3,5),second=first.south();
        h.setBlock(first,Blocks.STONE);h.setBlock(second,Blocks.STONE);
        player.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(1.5,2,1.5)));player.setYRot(0);player.setXRot(3);
        var spell=RecipeRegistry.recipes().stream().filter(d->d.id().getPath().equals("hydraulic_fracture")).findFirst().orElseThrow();
        h.assertTrue(spell.channeled(),"Hydro Erosion is a held channel spell");
        SpellRuntime.pulse(player,spell);SpellRuntime.pulse(player,spell);
        h.assertBlockPresent(Blocks.AIR,first);h.assertBlockPresent(Blocks.AIR,second);h.succeed();
    }

    @GameTest(template="empty")
    public static void hydraulicFractureBreaksWoodAndLeaves(GameTestHelper h) {
        var player=RecipeGameTests.caster(h);BlockPos log=new BlockPos(1,3,5),leaves=log.south();
        h.setBlock(log,Blocks.OAK_LOG);h.setBlock(leaves,Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT,true));
        player.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(1.5,2,1.5)));player.setYRot(0);player.setXRot(3);
        var spell=RecipeRegistry.recipes().stream().filter(d->d.id().getPath().equals("hydraulic_fracture")).findFirst().orElseThrow();
        SpellRuntime.pulse(player,spell);SpellRuntime.pulse(player,spell);
        h.assertBlockPresent(Blocks.AIR,log);h.assertBlockPresent(Blocks.AIR,leaves);h.succeed();
    }

    @GameTest(template="empty")
    public static void worldbreakerDestroysBreakableMaterials(GameTestHelper h) {
        var player=RecipeGameTests.caster(h);BlockPos target=new BlockPos(1,3,5);
        for(int x=1;x<=2;x++)for(int y=3;y<=4;y++)for(int z=5;z<=6;z++)
            h.setBlock(new BlockPos(x,y,z),(y==3?Blocks.OAK_LOG:Blocks.OAK_LEAVES).defaultBlockState());
        player.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(1.5,2,1.5)));player.setYRot(0);player.setXRot(3);
        var spell=RecipeRegistry.recipes().stream().filter(d->d.id().getPath().equals("worldbreaker")).findFirst().orElseThrow();
        SpellRuntime.cast(player,spell,false);
        for(int x=1;x<=2;x++)for(int y=3;y<=4;y++)for(int z=5;z<=6;z++)h.assertBlockPresent(Blocks.AIR,new BlockPos(x,y,z));
        h.succeed();
    }

    @GameTest(template="empty")
    public static void driftBoreTracksAngledDescent(GameTestHelper h) {
        var player=RecipeGameTests.caster(h);BlockPos[] line={new BlockPos(2,4,2),new BlockPos(3,3,3),new BlockPos(4,2,4),new BlockPos(5,1,5)};
        for(BlockPos pos:line)h.setBlock(pos,Blocks.STONE);
        player.setYRot(-45);player.setXRot(35.264f);
        MiningMagic.mine(player,h.absolutePos(line[0]).getCenter(),"tunnel",16,4,0,1,false,true);
        for(BlockPos pos:line)h.assertBlockPresent(Blocks.AIR,pos);
        h.succeed();
    }

    @GameTest(template="empty")
    public static void genericMagmaLeavesBurningPoolAndCrustsWater(GameTestHelper h) {
        var player=RecipeGameTests.caster(h);
        var generic=RecipeRegistry.fusion(new SpellFormula(0,0,0,0,0,0,0,0,0,0,0,1));
        h.assertTrue(FusionRules.terrainReaction(generic.formula())==FusionRules.TerrainReaction.MAGMA,"Generic Magma leaves a hot pool");
        var named=RecipeRegistry.baseRecipes().stream().filter(d->d.formula().magma()>0 && !d.source().has("fusion")).findFirst().orElseThrow();
        BlockPos namedCenter=new BlockPos(3,2,8);
        for(int x=2;x<=4;x++)for(int z=7;z<=9;z++)h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
        FusionRules.world(player,named,h.absoluteVec(namedCenter.getCenter()),false);
        h.assertBlockPresent(Blocks.AIR,namedCenter);
        BlockPos center=new BlockPos(3,2,3);
        for(int x=2;x<=4;x++)for(int z=2;z<=4;z++)h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
        WorldMagic.magmaGround(player,h.absoluteVec(center.getCenter()),1.4,100);
        h.assertBlockPresent(MagicBlocks.MAGMA_PUDDLE.get(),center);
        SpellRuntime.cast(player,generic,false);
        ((MagicBlocks.TemporaryBlock)MagicBlocks.MAGMA_PUDDLE.get()).entityInside(h.getBlockState(center),h.getLevel(),h.absolutePos(center),player);
        h.assertTrue(MagicStatuses.has(player,MagicStatuses.Status.BURNING),"Magma puddles burn entities on contact");
        BlockPos waterCenter=new BlockPos(8,2,8);
        for(int x=7;x<=9;x++)for(int z=7;z<=9;z++)h.setBlock(new BlockPos(x,2,z),Blocks.WATER);
        WorldMagic.magmaCrust(player,h.absoluteVec(waterCenter.getCenter()),1.4);
        h.assertBlockPresent(Blocks.COBBLESTONE,waterCenter);h.assertBlockPresent(Blocks.COBBLESTONE,waterCenter.east());
        h.assertBlockPresent(Blocks.WATER,waterCenter.south().east());
        var steam=RecipeRegistry.resolve(new SpellFormula(0,1,0,0,0,0,0,0,0,0,0,1));
        var crust=RecipeRegistry.resolve(new SpellFormula(0,2,0,0,0,0,0,0,0,0,0,1));
        var frozenCrust=RecipeRegistry.resolve(new SpellFormula(0,0,0,0,0,0,0,0,0,1,0,1));
        var magmaSteam=RecipeRegistry.resolve(new SpellFormula(0,0,0,0,0,0,0,0,1,0,0,1));
        var earthSteam=RecipeRegistry.resolve(new SpellFormula(0,0,0,0,2,0,0,0,1,0,0,0));
        h.assertTrue(FusionRules.terrainReaction(steam.formula())==FusionRules.TerrainReaction.STEAM,"Generic Magma with a little water creates steam");
        h.assertTrue(FusionRules.terrainReaction(magmaSteam.formula())==FusionRules.TerrainReaction.STEAM,"Generic Steam and Magma create a steam cloud");
        h.assertTrue(FusionRules.terrainReaction(crust.formula())==FusionRules.TerrainReaction.CRUST,"Magma with abundant water crusts into cobblestone");
        h.assertTrue(FusionRules.terrainReaction(frozenCrust.formula())==FusionRules.TerrainReaction.CRUST,"Magma with ice creates a stone crust");
        h.assertTrue(FusionRules.terrainReaction(earthSteam.formula())==FusionRules.TerrainReaction.STEAM,"Steam remains a generic element reaction with earth");h.succeed();
    }

    @GameTest(template="empty")
    public static void miningSpellsHaveDistinctReachAndDelivery(GameTestHelper h) {
        var player=RecipeGameTests.caster(h);
        var pick=RecipeGameTests.spell("Thermal Pick");
        var quarry=RecipeGameTests.spell("Quarry Pulse");
        var ray=RecipeGameTests.spell("Smelting Ray");
        var water=RecipeGameTests.spell("Hydro Erosion");
        h.assertTrue(pick.shape()==SpellShape.DIRECT_TARGET && SpellRuntime.reach(player,pick)==4.5,"The basic pick stays within tool reach");
        h.assertTrue(quarry.shape()==SpellShape.BURST_PROJECTILE && SpellRuntime.reach(player,quarry)==36,"The quarry is a ranged impact projectile");
        player.setXRot(-90);
        var boundedAim=SpellAim.hit(player,36);
        h.assertTrue(boundedAim.getLocation().distanceTo(player.getEyePosition())<=36.01,"A capped aim ray stays within maximum range");
        h.assertTrue(ray.shape()==SpellShape.BEAM && SpellRuntime.reach(player,ray)==16,"The smelting ray mines at beam range");
        h.assertTrue(water.shape()==SpellShape.BEAM && SpellRuntime.reach(player,water)==14,"Hydro Erosion reaches 14 blocks");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void galleryCutterFollowsHitFace(GameTestHelper h) {
        h.assertTrue(MiningMagic.planeAxis(Direction.UP,new Vec3(1,0,0))==Direction.Axis.Y,"Top face selects a horizontal cut");
        h.assertTrue(MiningMagic.planeAxis(Direction.DOWN,new Vec3(1,0,0))==Direction.Axis.Y,"Bottom face selects a horizontal cut");
        h.assertTrue(MiningMagic.planeAxis(Direction.NORTH,new Vec3(0,1,0))==Direction.Axis.Z,"Side face selects a vertical cut");
        h.assertTrue(MiningMagic.planeAxis(Direction.EAST,new Vec3(0,1,0))==Direction.Axis.X,"Other side faces select a vertical cut");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void smeltTurnsRawIronIntoIngot(GameTestHelper h) {
        var player=RecipeGameTests.caster(h);BlockPos local=new BlockPos(2,2,2);h.setBlock(local,Blocks.IRON_ORE);
        MiningMagic.mine(player,h.absolutePos(local).getCenter(),"single",1,1,0,1,true,false);
        h.assertBlockPresent(Blocks.AIR,local);
        h.assertTrue(drops(h,local,Items.IRON_INGOT)==1 && drops(h,local,Items.RAW_IRON)==0,"Ore drop is smelted through its furnace recipe");h.succeed();
    }

    @GameTest(template="empty")
    public static void bulkOnlyLeavesOre(GameTestHelper h) {
        var player=RecipeGameTests.caster(h);BlockPos local=new BlockPos(2,2,2);h.setBlock(local,Blocks.IRON_ORE);
        MiningMagic.mine(player,h.absolutePos(local).getCenter(),"single",1,1,0,1,false,true);
        h.assertBlockPresent(Blocks.IRON_ORE,local);h.succeed();
    }

    @GameTest(template="empty")
    public static void veinMinesConnectedOreWithinBudget(GameTestHelper h) {
        var player=RecipeGameTests.caster(h);BlockPos start=new BlockPos(2,2,2);
        h.setBlock(start,Blocks.IRON_ORE);h.setBlock(start.north(),Blocks.IRON_ORE);h.setBlock(start.north(2),Blocks.IRON_ORE);h.setBlock(start.east(),Blocks.COPPER_ORE);
        MiningMagic.mine(player,h.absolutePos(start).getCenter(),"vein",2,1,0,1,false,false);
        int broken=0;for(int dz=0;dz>=-2;dz--)if(h.getBlockState(start.offset(0,0,dz)).isAir())broken++;
        h.assertTrue(broken==2,"Vein respects its block budget");h.assertBlockPresent(Blocks.COPPER_ORE,start.east());h.succeed();
    }

    @GameTest(template="empty")
    public static void protectionAndContainersStopMining(GameTestHelper h) {
        var player=RecipeGameTests.caster(h);BlockPos protectedLocal=new BlockPos(2,2,2),protectedPos=h.absolutePos(protectedLocal),chest=new BlockPos(3,2,2);
        h.setBlock(protectedLocal,Blocks.STONE);h.setBlock(chest,Blocks.CHEST);
        Consumer<BlockEvent.BreakEvent> deny=e->{if(e.getPos().equals(protectedPos))e.setCanceled(true);};MinecraftForge.EVENT_BUS.addListener(deny);
        try{MiningMagic.mine(player,protectedPos.getCenter(),"single",1,1,0,0,false,false);}finally{MinecraftForge.EVENT_BUS.unregister(deny);}
        MiningMagic.mine(player,h.absolutePos(chest).getCenter(),"single",1,1,0,3,false,false);
        h.assertBlockPresent(Blocks.STONE,protectedLocal);h.assertBlockPresent(Blocks.CHEST,chest);h.succeed();
    }

    private static int drops(GameTestHelper h, BlockPos local, net.minecraft.world.item.Item item) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(h.absolutePos(local)).inflate(2),entity->entity.getItem().is(item)).stream().mapToInt(entity->entity.getItem().getCount()).sum();
    }

    private MiningGameTests() {}
}
