package com.restor.magicka;

import net.minecraft.world.entity.EntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
public final class AuditGameTests {
    @MagicTest
    public static void configCommandChangesSettings(GameTestHelper h){
        var server=h.getLevel().getServer();var source=server.createCommandSourceStack().withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source,"spellweaver config bossUltimateWeight 5");
        int changed=MagicConfig.BOSS_ULTIMATE_WEIGHT.get();
        server.getCommands().performPrefixedCommand(source,"spellweaver config bossUltimateWeight 999");
        int rejected=MagicConfig.BOSS_ULTIMATE_WEIGHT.get();
        server.getCommands().performPrefixedCommand(source,"spellweaver config bossUltimateWeight default");
        h.assertTrue(changed==5,"The command sets a value");
        h.assertTrue(rejected==5,"An out-of-range value is refused");
        h.succeedWhen(()->h.assertTrue(MagicConfig.BOSS_ULTIMATE_WEIGHT.get()==MagicConfig.BOSS_ULTIMATE_WEIGHT.getDefault(),"default restores the default"));
    }
    @MagicTest
    public static void fireIgnitesWood(GameTestHelper h){
        DeathGameTests.floor(h);var p=RecipeGameTests.caster(h);
        h.setBlock(new BlockPos(3,2,3),net.minecraft.world.level.block.Blocks.OAK_PLANKS);
        WorldMagic.temperature(p,h.absoluteVec(new Vec3(3.5,2.5,3.5)),40,RecipeGameTests.spell("Firebolt"));
        boolean planks=h.getLevel().getBlockState(h.absolutePos(new BlockPos(3,3,3))).is(net.minecraft.world.level.block.Blocks.FIRE);
        h.setBlock(new BlockPos(4,2,4),net.minecraft.world.level.block.Blocks.OAK_PLANKS);
        WorldMagic.temperature(p,h.absoluteVec(new Vec3(4.5,3.1,4.5)),40,RecipeGameTests.spell("Ember"));
        boolean surface=h.getLevel().getBlockState(h.absolutePos(new BlockPos(4,3,4))).is(net.minecraft.world.level.block.Blocks.FIRE);
        h.assertTrue(planks,"Firebolt sets wood alight");
        h.assertTrue(surface,"Ember sets wood alight from the surface");
        h.succeed();
    }
    @MagicTest
    public static void ritualOrderGatesUltimates(GameTestHelper h){
        DeathGameTests.floor(h);var spoken=RecipeGameTests.caster(h);var scrambled=RecipeGameTests.caster(h);
        CastingServer.input(spoken,7);for(int a:new int[]{4,4,0,2,4})CastingServer.input(spoken,a);CastingServer.input(spoken,9);
        CastingServer.input(scrambled,7);for(int a:new int[]{4,4,4,0,2})CastingServer.input(scrambled,a);CastingServer.input(scrambled,9);
        h.assertTrue(CastingServer.snapshot(spoken).total()==RecipeGameTests.spell("Earthquake").castTicks(),"The ritual order calls the ultimate");
        h.assertTrue(CastingServer.snapshot(scrambled).total()==RecipeGameTests.spell("Heavy Greater Magma").castTicks(),"A scrambled order collapses to the four-element spell");
        h.succeed();
    }
    @MagicTest
    public static void quakeHeavesGroundWithoutLevellingIt(GameTestHelper h){
        DeathGameTests.floor(h);var p=RecipeGameTests.caster(h);
        WorldMagic.heave(p,h.absoluteVec(new Vec3(2.5,1.5,2.5)),12,2.5,.75);
        var clods=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,
                new net.minecraft.world.phys.AABB(h.absolutePos(new BlockPos(2,2,2))).inflate(8));
        int standing=0;
        for(BlockPos pos:BlockPos.betweenClosed(new BlockPos(0,1,0),new BlockPos(4,1,4)))
            if(h.getLevel().getBlockState(h.absolutePos(pos)).is(net.minecraft.world.level.block.Blocks.STONE))standing++;
        h.assertTrue(clods.size()>=4,"The quake throws the ground into the air, got "+clods.size());
        h.assertTrue(clods.stream().allMatch(c->c.getDeltaMovement().y>0),"Every heaved block flies upward");
        h.assertTrue(standing>=8,"The quake heaves the ground instead of levelling it, "+standing+" blocks left");
        h.succeed();
    }
    @MagicTest
    public static void lightningLeavesHandAndChains(GameTestHelper h){
        DeathGameTests.floor(h);var p=RecipeGameTests.caster(h);
        var aimed=h.spawnWithNoFreeWill(EntityTypes.ZOMBIE,new BlockPos(1,2,4));var near=h.spawnWithNoFreeWill(EntityTypes.ZOMBIE,new BlockPos(3,2,4));
        var far=h.spawnWithNoFreeWill(EntityTypes.ZOMBIE,new BlockPos(1,2,14));
        SpellRuntime.cast(p,RecipeGameTests.spell("Bolt"),false);
        h.assertTrue(aimed.getHealth()<aimed.getMaxHealth(),"Aimed creature is struck");
        h.assertTrue(near.getHealth()<near.getMaxHealth(),"Lightning chains to a neighbour");
        h.assertTrue(far.getHealth()==far.getMaxHealth(),"Creatures out of reach are untouched");h.succeed();
    }
    @MagicTest
    public static void thunderLancePiercesLine(GameTestHelper h){
        DeathGameTests.floor(h);var p=RecipeGameTests.caster(h);
        var first=h.spawnWithNoFreeWill(EntityTypes.ZOMBIE,new BlockPos(1,2,3));var second=h.spawnWithNoFreeWill(EntityTypes.ZOMBIE,new BlockPos(1,2,5));
        h.setBlock(new BlockPos(1,2,8),net.minecraft.world.level.block.Blocks.STONE);h.setBlock(new BlockPos(1,3,8),net.minecraft.world.level.block.Blocks.STONE);
        SpellRuntime.cast(p,RecipeGameTests.spell("Thunder Lance"),false);
        h.assertTrue(first.getHealth()<first.getMaxHealth() && second.getHealth()<second.getMaxHealth(),"Every creature on the line is struck");h.succeed();
    }
    @MagicTest(timeoutTicks=240)
    public static void teslaSpireBuildsCoilAndZaps(GameTestHelper h){
        DeathGameTests.floor(h);var p=RecipeGameTests.caster(h);
        var enemy=h.spawnWithNoFreeWill(EntityTypes.ZOMBIE,new BlockPos(5,2,5));enemy.setNoGravity(true);
        SpellRuntime.cast(p,RecipeGameTests.spell("Tesla Spire"),true);
        h.succeedWhen(()->{
            var blocks=MagicConstructionManager.get(h.getLevel()).structures().stream().filter(c->c.owner.equals(p.getUUID())).flatMap(c->c.blocks.stream()).toList();
            h.assertTrue(blocks.size()==5,"Four copper blocks and a coil, got "+blocks.size());
            var top=blocks.stream().max(java.util.Comparator.comparingInt(BlockPos::getY)).orElseThrow();
            h.assertTrue(h.getLevel().getBlockState(top).is(MagicBlocks.COIL.get()),"Coil sits on top");
            h.assertTrue(blocks.stream().filter(b->!b.equals(top)).allMatch(b->h.getLevel().getBlockState(b).is(MagicBlocks.COPPER.get())),"Tower body is copper");
            var beside=net.minecraft.world.phys.Vec3.atBottomCenterOf(top).add(2.5,0,0);
            if(enemy.position().distanceToSqr(beside)>.01){enemy.teleportTo(beside.x,beside.y,beside.z);enemy.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);}
            h.assertTrue(enemy.getHealth()<enemy.getMaxHealth(),"Coil zaps an enemy beside it");
        });
    }
    @MagicTest(timeoutTicks=60)
    public static void structureSpellsFollowView(GameTestHelper h){
        DeathGameTests.floor(h);var p=RecipeGameTests.caster(h);p.setPos(h.absoluteVec(new Vec3(2.5,2,0.5)));BlockPos feet=p.blockPosition();
        var wall=RecipeGameTests.spell("Stone Wall");
        h.assertTrue(wall.formula().equals(new SpellFormula(0,0,0,0,1,0,2,0)),"Stone Wall uses earth + 2 arcane");
        Vec3 south=new Vec3(0,0,1),center=Vec3.atCenterOf(feet);
        p.setXRot(-80);var tent=MagicConstructionManager.Plan.template(p,"wall",center,south);
        h.assertTrue(tent.size()==80 && tent.contains(feet.above(4)) && !tent.contains(feet.above(2)),"Looking up raises a tent over the caster, got "+tent.size());
        p.setXRot(0);var slope=MagicConstructionManager.Plan.template(p,"slope",center,south);
        h.assertTrue(slope.size()==50 && slope.contains(feet.south(3)) && slope.contains(feet.above(3)) && !slope.contains(feet.above(2)) && !slope.contains(feet.south().above(1)),"Slanted wall rises at 45 degrees over the caster, got "+slope.size());
        p.setXRot(-80);var roof=MagicConstructionManager.Plan.template(p,"slab",center,south);
        h.assertTrue(roof.size()==25 && roof.stream().allMatch(b->b.getY()==feet.getY()+3) && roof.getFirst().equals(feet.above(3)),"Looking up turns the slab into a roof");
        p.setXRot(70);h.assertTrue(MagicConstructionManager.Plan.template(p,"slab",center,south).stream().allMatch(b->b.getY()==feet.getY()-1),"Looking down turns it into a floor");
        p.setXRot(0);var diagonal=new Vec3(1,0,1).normalize();
        for(String kind:new String[]{"wall","slope","redoubt"}){
            var cells=new java.util.HashSet<BlockPos>();for(var c:MagicConstructionManager.Plan.template(p,kind,center,diagonal))cells.add(kind.equals("slope") ? c : c.atY(0));
            var seen=new java.util.HashSet<BlockPos>();var queue=new java.util.ArrayDeque<BlockPos>();queue.add(cells.iterator().next());seen.add(queue.peek());
            while(!queue.isEmpty()){var c=queue.poll();for(var d:net.minecraft.core.Direction.values())if(cells.contains(c.relative(d)) && seen.add(c.relative(d)))queue.add(c.relative(d));}
            h.assertTrue(cells.size()>=6 && seen.size()==cells.size(),"Diagonal "+kind+" has no gaps: "+seen.size()+"/"+cells.size());
        }
        p.setXRot((float)Math.toDegrees(Math.atan2(p.getEyeY()-h.absoluteVec(new Vec3(0,2,0)).y,3)));SpellRuntime.cast(p,wall,false);
        h.runAfterDelay(30,()->{
            for(int x=0;x<5;x++)for(int y=2;y<5;y++)h.assertTrue(h.getBlockState(new BlockPos(x,y,3)).is(MagicBlocks.EARTH.get()),"Wall stands where the caster aims, cell "+x+","+y);
            h.assertTrue(h.getBlockState(new BlockPos(2,2,2)).isAir(),"Nothing is built in front of the aim point");h.succeed();
        });
    }
    @MagicTest(timeoutTicks=80,batch="slab")
    public static void stoneSlabBridgesGapAhead(GameTestHelper h){
        var p=RecipeGameTests.caster(h);p.setPos(h.absoluteVec(new Vec3(2.5,2,0.5)));h.setBlock(new BlockPos(2,1,0),net.minecraft.world.level.block.Blocks.STONE);
        SpellRuntime.cast(p,RecipeGameTests.spell("Stone Slab"),false);
        h.succeedWhen(()->{
            for(int x=0;x<5;x++)for(int z=1;z<=5;z++)h.assertTrue(h.getBlockState(new BlockPos(x,1,z)).is(MagicBlocks.EARTH.get()),"Deck cell "+x+","+z);
        });
    }
    @MagicTest(timeoutTicks=80)
    public static void floatingPlatformAppearsUnderFeet(GameTestHelper h){
        var p=RecipeGameTests.caster(h);p.setPos(h.absoluteVec(new Vec3(2.5,4,2.5)));BlockPos feet=p.blockPosition();
        SpellRuntime.cast(p,RecipeGameTests.spell("Floating Platform"),false);
        h.runAfterDelay(50,()->{
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)
                h.assertTrue(h.getLevel().getBlockState(feet.offset(x,-1,z)).is(MagicBlocks.EARTH.get()),"Platform cell under the caster at "+x+","+z);
            h.succeed();
        });
    }
    @MagicTest(timeoutTicks=40)
    public static void bubbleLiftRaisesEveryoneAround(GameTestHelper h){
        DeathGameTests.floor(h);var p=RecipeGameTests.caster(h);
        var a=h.spawnWithNoFreeWill(EntityTypes.PIG,new BlockPos(2,2,3));var b=h.spawnWithNoFreeWill(EntityTypes.PIG,new BlockPos(3,2,2));
        SpellRuntime.cast(p,RecipeGameTests.spell("Bubble Lift"),true);
        h.assertTrue(MagicStatuses.has(a,MagicStatuses.Status.LEVITATING) && MagicStatuses.has(b,MagicStatuses.Status.LEVITATING) && MagicStatuses.has(p,MagicStatuses.Status.LEVITATING),"Everyone nearby floats");h.succeed();
    }
    @MagicTest(timeoutTicks=40)
    public static void attributesScaleSpells(GameTestHelper h){
        DeathGameTests.floor(h);var p=RecipeGameTests.caster(h);
        var bomb=RecipeGameTests.spell("Firebomb");
        h.assertTrue(MagicStats.apply(p,bomb)==bomb,"Default attributes leave spells untouched");
        MagicStats.set(p,MagicStats.Stat.FIRE.ordinal(),400);MagicStats.set(p,MagicStats.Stat.DAMAGE.ordinal(),200);MagicStats.set(p,MagicStats.Stat.AREA.ordinal(),200);
        var strong=MagicStats.apply(p,bomb);
        h.assertTrue(strong.effects().getFirst().get("amount").getAsDouble()==28,"Fire damage scales with fire power and damage");
        h.assertTrue(strong.effects().get(2).get("amount").getAsDouble()==4,"Arcane damage ignores fire power");
        h.assertTrue(strong.number("burst_radius",0)>bomb.number("burst_radius",0)*3,"Burst radius grows with area and element power");
        MagicStats.set(p,MagicStats.Stat.CONSTRUCTION.ordinal(),500);
        h.assertTrue(MagicStats.apply(p,RecipeGameTests.spell("Stone Stair")).budget()>MagicConfig.MAX_BLOCKS.get(),"Construction lifts the block cap");
        MagicStats.reset(p);MagicStats.set(p,MagicStats.Stat.SUMMONS.ordinal(),300);
        SpellRuntime.cast(p,MagicStats.apply(p,RecipeGameTests.spell("Grave Call")),true);
        int raised=DeathGameTests.minions(h,p).size();
        MagicStats.reset(p);
        h.assertTrue(raised==6,"Summons triples servants, got "+raised);h.succeed();
    }
    @MagicTest(timeoutTicks=100)
    public static void stencilBuildsTheChosenShape(GameTestHelper h){
        var level=h.getLevel();var origin=h.absolutePos(BlockPos.ZERO);
        // The stencil is wider than the 6x6x6 arena, so clear a yard around it and floor it with stone.
        for(int x=-9;x<=9;x++)for(int y=0;y<=9;y++)for(int z=-9;z<=9;z++)
            level.setBlock(origin.offset(x,y,z),(y==1?net.minecraft.world.level.block.Blocks.STONE:net.minecraft.world.level.block.Blocks.AIR).defaultBlockState(),2);
        var p=RecipeGameTests.caster(h);p.setPos(h.absoluteVec(new Vec3(2.5,2,2.5)));p.setXRot(45);
        long[] bits=new long[2];
        for(int x=0;x<ShapePattern.SIZE;x++)for(int z=0;z<ShapePattern.SIZE;z++)ShapePattern.set(bits,x,0,z,true);
        ShapePattern.set(bits,2,3,2,true);
        ShapePattern.store(p,bits[0],bits[1]);
        h.assertTrue(ShapePattern.count(ShapePattern.of(p))==26,"Stencil keeps every painted cell");
        var spell=RecipeGameTests.spell("Stone Sculpture");
        h.assertTrue(spell.budget()==125 && spell.floating(),"Stencil fills the whole cube and may hang in the air");
        var plan=new MagicConstructionManager.Plan(p,spell,false,null);
        h.assertTrue(plan.template.size()==26,"Plan follows the stencil, got "+plan.template.size());
        int base=plan.template.getFirst().getY();
        var slab=plan.template.stream().filter(c->c.getY()==base).toList();
        h.assertTrue(slab.size()==25 && slab.stream().mapToInt(BlockPos::getX).distinct().count()==5
                && slab.stream().mapToInt(BlockPos::getZ).distinct().count()==5,"Bottom layer is a 5x5 square");
        h.assertTrue(plan.template.stream().anyMatch(c->c.getY()==base+3),"Painted cell three layers up hangs in the air");
        SpellRuntime.cast(p,spell,false);
        h.runAfterDelay(40,()->{
            var placed=MagicConstructionManager.get(level).structures().stream().filter(c->c.owner.equals(p.getUUID())).flatMap(c->c.blocks.stream()).toList();
            h.assertTrue(placed.size()==26,"Every stencil cell becomes a block, got "+placed.size());
            h.assertTrue(placed.stream().filter(c->!level.getBlockState(c).is(MagicBlocks.EARTH.get())).count()<=1,"Sculpture is magic earth apart from its anchor");
            h.succeed();
        });
    }
    @MagicTest(timeoutTicks=80)
    public static void groundSpellsGripTheirTargets(GameTestHelper h){
        DeathGameTests.floor(h);for(int z=0;z<8;z++)for(int x=0;x<5;x++)h.setBlock(new BlockPos(x,1,z),net.minecraft.world.level.block.Blocks.STONE);
        var p=RecipeGameTests.caster(h);
        var gripped=h.spawnWithNoFreeWill(net.minecraft.world.entity.EntityTypes.ZOMBIE,new BlockPos(1,2,5));
        SpellRuntime.cast(p,RecipeGameTests.spell("Grasping Dead"),false);
        h.runAfterDelay(25,()->{
            h.assertTrue(MagicStatuses.has(gripped,MagicStatuses.Status.ROOTED),"Grasping Dead roots the aimed zombie");
            h.assertTrue(gripped.getHealth()<gripped.getMaxHealth(),"Grasping Dead hurts the aimed zombie");
            gripped.discard();
            var muddy=h.spawnWithNoFreeWill(net.minecraft.world.entity.EntityTypes.ZOMBIE,new BlockPos(1,2,5));
            SpellRuntime.cast(p,RecipeGameTests.spell("Mud"),false);
            h.runAfterDelay(30,()->{
                h.assertTrue(MagicStatuses.has(muddy,MagicStatuses.Status.ROOTED) && MagicStatuses.has(muddy,MagicStatuses.Status.WET),"Mud soaks and roots the zombie it hits");
                h.succeed();
            });
        });
    }
    @MagicTest(timeoutTicks=80)
    public static void portalNeverDropsIntoAWall(GameTestHelper h){
        DeathGameTests.floor(h);for(int x=0;x<5;x++)for(int y=2;y<6;y++)h.setBlock(new BlockPos(x,y,7),net.minecraft.world.level.block.Blocks.STONE);
        var p=RecipeGameTests.caster(h);p.setPos(h.absoluteVec(new Vec3(1.5,2,.5)));
        SpellRuntime.cast(p,RecipeGameTests.spell("Spark Portal"),false);
        var walker=h.spawnWithNoFreeWill(net.minecraft.world.entity.EntityTypes.ZOMBIE,new BlockPos(1,2,2));
        h.runAfterDelay(15,()->{
            h.assertTrue(walker.position().distanceToSqr(h.absoluteVec(new Vec3(1.5,2,2.5)))>.5,"The zombie in the gate is carried through");
            h.assertTrue(h.getLevel().noCollision(walker,walker.getBoundingBox()),"The portal never leaves anyone inside blocks");
            h.succeed();
        });
    }
    @MagicTest(timeoutTicks=60)
    public static void portalOpensOnTheCeilingItIsAimedAt(GameTestHelper h){
        DeathGameTests.floor(h);for(int x=0;x<5;x++)for(int z=0;z<6;z++)h.setBlock(new BlockPos(x,7,z),net.minecraft.world.level.block.Blocks.STONE);
        var p=RecipeGameTests.caster(h);p.setPos(h.absoluteVec(new Vec3(1.5,2,.5)));p.setYRot(0);p.setXRot(-60);
        SpellRuntime.cast(p,RecipeGameTests.spell("Spark Portal"),false);
        var walker=h.spawnWithNoFreeWill(net.minecraft.world.entity.EntityTypes.ZOMBIE,new BlockPos(1,2,3));
        h.runAfterDelay(4,()->{
            h.assertTrue(walker.getY()>h.absoluteVec(new Vec3(0,3.5,0)).y,"The far gate hangs on the ceiling instead of dropping to the floor");
            h.succeed();
        });
    }
    @MagicTest(timeoutTicks=60)
    public static void raysAllLandAndVentCracksTheGround(GameTestHelper h){
        for(int z=0;z<9;z++)for(int x=0;x<5;x++)h.setBlock(new BlockPos(x,1,z),net.minecraft.world.level.block.Blocks.STONE);
        var p=RecipeGameTests.caster(h);
        var target=h.spawnWithNoFreeWill(net.minecraft.world.entity.EntityTypes.ZOMBIE,new BlockPos(1,2,5));
        SpellRuntime.cast(p,RecipeGameTests.spell("Piercing Rays"),false);
        Fissure.open(p,RecipeGameTests.spell("Volcanic Vent"),h.absoluteVec(new Vec3(3.5,2,2.5)));
        h.runAfterDelay(30,()->{
            h.assertTrue(!target.isAlive(),"Five rays of 2 hearts each kill a zombie, health left "+target.getHealth());
            h.assertTrue(h.getBlockState(new BlockPos(3,1,2)).isAir() && h.getBlockState(new BlockPos(3,1,0)).isAir(),"The vent cracks a line of ground open");
            h.assertTrue(h.getBlockState(new BlockPos(2,1,2)).is(net.minecraft.world.level.block.Blocks.STONE),"The fissure is one block wide");
            h.succeed();
        });
    }
    @MagicTest(timeoutTicks=60)
    public static void plantSpellsGrowOnlyTheirOwnPlants(GameTestHelper h){
        for(int x=0;x<5;x++)for(int z=0;z<5;z++)h.setBlock(new BlockPos(x,1,z),net.minecraft.world.level.block.Blocks.GRASS_BLOCK);
        var p=RecipeGameTests.caster(h);
        SpellRuntime.cast(p,RecipeGameTests.spell("Field of Flowers"),true);
        h.runAfterDelay(25,()->{
            int flowers=0,other=0;
            for(int x=0;x<5;x++)for(int z=0;z<5;z++){var s=h.getBlockState(new BlockPos(x,2,z));if(s.is(net.minecraft.tags.BlockTags.SMALL_FLOWERS))flowers++;else if(s.is(net.minecraft.world.level.block.Blocks.SHORT_GRASS)||s.is(net.minecraft.world.level.block.Blocks.FERN)||s.getBlock() instanceof net.minecraft.world.level.block.SaplingBlock)other++;}
            h.assertTrue(flowers>0 && other==0,"Field of Flowers grows only flowers, got "+flowers+" flowers and "+other+" other");
            for(int x=0;x<5;x++)for(int z=0;z<5;z++)h.setBlock(new BlockPos(x,2,z),net.minecraft.world.level.block.Blocks.AIR);
            SpellRuntime.cast(p,RecipeGameTests.spell("Meadow"),true);
            h.runAfterDelay(25,()->{
                int grass=0,rest=0;
                for(int x=0;x<5;x++)for(int z=0;z<5;z++){var s=h.getBlockState(new BlockPos(x,2,z));if(s.is(net.minecraft.world.level.block.Blocks.SHORT_GRASS)||s.is(net.minecraft.world.level.block.Blocks.FERN))grass++;else if(s.is(net.minecraft.tags.BlockTags.SMALL_FLOWERS)||s.getBlock() instanceof net.minecraft.world.level.block.SaplingBlock)rest++;}
                h.assertTrue(grass>0 && rest==0,"Meadow grows only grass and ferns, got "+grass+" and "+rest+" other");
                h.succeed();
            });
        });
    }
    @MagicTest(timeoutTicks=60)
    public static void groveGrowsTrees(GameTestHelper h){
        for(int x=0;x<5;x++)for(int z=0;z<5;z++)h.setBlock(new BlockPos(x,1,z),net.minecraft.world.level.block.Blocks.GRASS_BLOCK);
        var p=RecipeGameTests.caster(h);
        SpellRuntime.cast(p,RecipeGameTests.spell("Grove"),true);
        h.runAfterDelay(25,()->{
            boolean log=false;
            for(int x=0;x<5;x++)for(int z=0;z<5;z++)for(int y=2;y<6;y++)log|=h.getBlockState(new BlockPos(x,y,z)).is(net.minecraft.tags.BlockTags.LOGS)||h.getBlockState(new BlockPos(x,y,z)).getBlock() instanceof net.minecraft.world.level.block.SaplingBlock;
            h.assertTrue(log,"Grove plants a tree on grass");
            h.succeed();
        });
    }
    @MagicTest(timeoutTicks=60)
    public static void stagTurnsToRamFoesAside(GameTestHelper h){
        DeathGameTests.floor(h);
        var p=RecipeGameTests.caster(h);
        var side=h.spawnWithNoFreeWill(net.minecraft.world.entity.EntityTypes.ZOMBIE,new BlockPos(4,2,4));
        var start=side.position();
        SpellRuntime.cast(p,RecipeGameTests.spell("Silver Stag"),false);
        h.runAfterDelay(30,()->{
            h.assertTrue(side.getHealth()<side.getMaxHealth() || side.position().distanceTo(start)>1.5,"The stag turns aside to ram a foe off its path");
            h.succeed();
        });
    }
    @MagicTest(timeoutTicks=40)
    public static void defensiveCircleBlocksHitsFromTheFront(GameTestHelper h){
        DeathGameTests.floor(h);
        var p=RecipeGameTests.caster(h);
        var front=h.spawnWithNoFreeWill(net.minecraft.world.entity.EntityTypes.ZOMBIE,new BlockPos(1,2,4));
        var back=h.spawnWithNoFreeWill(net.minecraft.world.entity.EntityTypes.ZOMBIE,new BlockPos(1,2,0));
        SpellRuntime.cast(p,RecipeGameTests.spell("Piercing Rays"),true);
        h.runAfterDelay(5,()->{
            var hit=new net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent(p,new net.neoforged.neoforge.common.damagesource.DamageContainer(p.damageSources().mobAttack(front),4));
            LegendMagic.ward(hit);
            h.assertTrue(hit.isCanceled(),"The circle blocks a hit from the front");
            hit=new net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent(p,new net.neoforged.neoforge.common.damagesource.DamageContainer(p.damageSources().mobAttack(back),4));
            LegendMagic.ward(hit);
            h.assertFalse(hit.isCanceled(),"A hit from behind still lands");
            h.succeed();
        });
    }
}
