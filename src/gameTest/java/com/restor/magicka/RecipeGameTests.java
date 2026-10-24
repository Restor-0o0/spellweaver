package com.restor.magicka;

import net.minecraft.world.entity.EntityType;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import java.util.*;
import java.util.function.Consumer;
public final class RecipeGameTests {
    static SpellDefinition spell(String name){return RecipeRegistry.recipes().stream().filter(d->d.name().equals(name)).findFirst().orElseThrow();}
    static net.minecraft.server.level.ServerPlayer caster(GameTestHelper h){
        var p=FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"RecipeTester"));
        p.setPos(h.absoluteVec(new Vec3(1.5,2,1.5)));p.setYRot(0);p.setXRot(0);
        RecipeRegistry.recipes().stream().filter(SpellScrolls::locked).forEach(d->SpellScrolls.learn(p,d.id()));return p;
    }
    @MagicTest
    public static void catalogAndDataOverride(GameTestHelper h){
        int[] counts=new int[6];Set<SpellFormula> keys=new HashSet<>();
        for(var d:RecipeRegistry.recipes()){counts[d.formula().size()]++;keys.add(d.formula());}
        h.assertTrue(Arrays.equals(counts,new int[]{0,8,36,120,330,792}) && keys.size()==1286,"Complete canonical catalog");
        long signatures=RecipeRegistry.recipes().stream().filter(d->d.tier().equals("Signature")).count();
        h.assertTrue(signatures==204,"All signatures imported, got "+signatures);
        var base=spell("Ember");var json=base.source().deepCopy();json.addProperty("cast_ticks",33);json.addProperty("shape","beam");
        var changed=SpellDefinition.parse(Identifier.parse("test:custom_ember"),json);
        h.assertTrue(changed.shape()==SpellShape.BEAM && changed.castTicks()==33 && changed.formula().equals(base.formula()),"New delivery is defined by data alone");
        json.getAsJsonObject("formula").addProperty("fire",6);
        boolean rejected=false;try{SpellDefinition.parse(base.id(),json);}catch(IllegalArgumentException expected){rejected=true;}
        h.assertTrue(rejected,"Invalid datapack formula rejected");h.succeed();
    }
    @MagicTest
    public static void twoPlayersAndPrimarySelf(GameTestHelper h){
        var a=caster(h);var b=caster(h);
        CastingServer.input(a,7);CastingServer.input(a,4);
        CastingServer.input(b,7);CastingServer.input(b,1);CastingServer.input(b,2);
        var before=CastingServer.snapshot(a);a.setPos(a.position().add(3,0,0));
        h.assertTrue(CastingServer.snapshot(a).formula()==before.formula(),"Movement preserves prepared formula");
        CastingServer.input(a,12);h.assertTrue(CastingServer.snapshot(a).remaining()==0,"Release without a channel is ignored");
        CastingServer.input(a,9);
        h.assertTrue(CastingServer.snapshot(a).intent()==0 && CastingServer.snapshot(a).total()==spell("Pebble").castTicks(),"Earth Primary is a projectile cast, no build channel");
        h.assertTrue(Formula.unpack(CastingServer.snapshot(b).formula()).size()==2 && CastingServer.snapshot(b).remaining()==0,"Other player state is independent");
        h.succeed();
    }
    @MagicTest
    public static void terrainBlacklistAndHooks(GameTestHelper h){
        var p=caster(h);BlockPos local=new BlockPos(2,2,2),pos=h.absolutePos(local);
        h.setBlock(local,Blocks.BEDROCK);h.assertFalse(WorldMagic.breakBlock(p,pos),"Bedrock protected");
        h.setBlock(local,Blocks.CHEST);h.assertFalse(WorldMagic.breakBlock(p,pos),"Containers protected");
        h.setBlock(local,Blocks.STONE);
        Consumer<net.neoforged.neoforge.event.level.block.BreakBlockEvent> deny=e->{if(e.getPos().equals(pos))e.setCanceled(true);};NeoForge.EVENT_BUS.addListener(deny);
        try{h.assertFalse(WorldMagic.breakBlock(p,pos),"Claim hook cancels terrain destruction");h.assertBlockPresent(Blocks.STONE,local);}finally{NeoForge.EVENT_BUS.unregister(deny);}
        h.assertTrue(WorldMagic.breakBlock(p,pos),"Ordinary terrain can be destroyed");h.succeed();
    }
    @MagicTest(timeoutTicks=70)
    public static void projectileTravelsAndHits(GameTestHelper h){
        var p=caster(h);Vec3 start=h.absoluteVec(new Vec3(1.5,2.5,1.5));
        h.setBlock(new BlockPos(1,1,5),Blocks.STONE);
        var pig=h.spawnWithNoFreeWill(EntityType.PIG,new BlockPos(1,2,5));float health=pig.getHealth();
        pig.setNoGravity(true);
        SpellRuntime.projectile(p,spell("Ember"),start,new Vec3(0,0,1),false);
        h.assertTrue(pig.getHealth()==health,"Projectile does not hit instantly");
        h.runAfterDelay(12,()->{h.assertTrue(pig.getHealth()<health || MagicStatuses.has(pig,MagicStatuses.Status.BURNING),"Moving projectile collides with target");h.succeed();});
    }
    @MagicTest(timeoutTicks=70)
    public static void waterZoneAndFreeze(GameTestHelper h){
        var p=caster(h);BlockPos local=new BlockPos(2,2,2);h.setBlock(local.below(),Blocks.STONE);
        var pig=h.spawnWithNoFreeWill(EntityType.PIG,local);Vec3 point=Vec3.atCenterOf(h.absolutePos(local));
        SpellRuntime.wetZone(p,point,2,100);
        h.runAfterDelay(12,()->{
            h.assertTrue(MagicStatuses.has(pig,MagicStatuses.Status.WET),"Persistent zone wets occupants");
            MagicStatuses.temperature(pig,-40);h.assertTrue(MagicStatuses.has(pig,MagicStatuses.Status.FROZEN),"Wet plus cold freezes");
            pig.discard();SpellRuntime.freezeWetZones(p,point,2);
            h.assertBlockPresent(MagicBlocks.ICE.get(),local);
            WorldMagic.temperature(p,point,80,spell("Firebolt"));h.assertBlockPresent(Blocks.AIR,local);h.succeed();
        });
    }
    @MagicTest(timeoutTicks=100)
    public static void reachableStairAndFloatingBridge(GameTestHelper h){
        var p=caster(h);h.setBlock(new BlockPos(1,1,2),Blocks.STONE);
        var stair=spell("Stone Stair");var plan=new MagicConstructionManager.Plan(p,stair,false,null);
        h.assertTrue(plan.start.equals(p.blockPosition().south()),"Stair starts at feet, not aim hit");
        BlockPos first=plan.next(p,0),previous=first;
        for(int i=1;i<stair.budget();i++){BlockPos next=plan.next(p,i);h.assertTrue(next.distManhattan(previous)==1,"Stair segments remain connected and climbable");previous=next;}
        var path=new net.minecraft.world.phys.AABB(first).expandTowards(0,2,0).inflate(1);
        h.runAfterDelay(1,()->{
            h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,path).forEach(net.minecraft.world.entity.Entity::discard);
            SpellRuntime.cast(p,stair,false);
        });
        h.runAfterDelay(9,()->h.assertTrue(h.getLevel().getBlockState(first).is(MagicBlocks.EARTH.get()),"Reachable first stair step placed"));
        h.runAfterDelay(73,()->{
            h.assertTrue(MagicConstructionManager.get(h.getLevel()).structures().stream().anyMatch(c->c.owner.equals(p.getUUID()) && c.blocks.size()>2),"Manager tracks constructed stair");
            var bridge=spell("Skybridge");h.assertTrue(bridge.floating() && bridge.origin()==OriginMode.AIM_HIT,"Bridge supports aim and floating anchor");
            var tag=MagicConstructionManager.get(h.getLevel()).save();
            h.assertTrue(!tag.getListOrEmpty("Structures").isEmpty(),"Construction metadata persists");h.succeed();
        });
    }
    @MagicTest(timeoutTicks=80)
    public static void mineAndHealingObject(GameTestHelper h){
        var p=caster(h);var pig=h.spawnWithNoFreeWill(EntityType.PIG,new BlockPos(2,2,2));pig.setHealth(3);
        SpellRuntime.cast(p,spell("Healing Spring"),true);
        h.runAfterDelay(24,()->{
            h.assertTrue(pig.getHealth()>3,"Healing summon pulses");
            var objects=h.getLevel().getEntitiesOfClass(MagicSummon.class,pig.getBoundingBox().inflate(4));
            h.assertTrue(!objects.isEmpty(),"Healer has a damageable object");var object=objects.getFirst();
            float hp=object.getHealth();object.hurt(p.damageSources().magic(),3);
            h.assertTrue(object.getHealth()==hp-3,"Object loses actual HP");
            h.assertTrue(!object.shouldBeSaved() && object.interact(p,net.minecraft.world.InteractionHand.MAIN_HAND,Vec3.ZERO)==net.minecraft.world.InteractionResult.FAIL,"No persistent orphan or equipment farming");
            object.hurt(p.damageSources().magic(),100);h.assertTrue(object.isRemoved(),"Object destroyed at zero HP");h.succeed();
        });
    }
    @MagicTest(timeoutTicks=80)
    public static void lightningChainAndRevival(GameTestHelper h){
        var p=caster(h);var a=h.spawnWithNoFreeWill(EntityType.PIG,new BlockPos(1,2,2));var b=h.spawnWithNoFreeWill(EntityType.PIG,new BlockPos(3,2,2));
        MagicStatuses.apply(a,MagicStatuses.Status.WET,100,1);MagicStatuses.apply(b,MagicStatuses.Status.WET,100,1);
        SpellRuntime.chain(p,spell("Stormwater"),a.position(),2,5,1);
        h.assertTrue(a.getHealth()<10 && b.getHealth()<10,"Water/lightning reaches multiple targets");
        Vec3 corpse=a.position();a.hurt(p.damageSources().magic(),100);
        h.runAfterDelay(25,()->{h.assertTrue(RevivalManager.revive(p,corpse,2,.3f),"Soul marker revives a fallen mob");h.succeed();});
    }
    @MagicTest(timeoutTicks=40)
    public static void bridgeReallyCreatesAnchor(GameTestHelper h){
        var p=caster(h);p.setPos(h.absoluteVec(new Vec3(1.5,24,1.5)));p.setXRot(0);var bridge=spell("Skybridge");
        h.assertTrue(SpellAim.hit(p).getType()==net.minecraft.world.phys.HitResult.Type.MISS,"Bridge test must aim into empty sky");
        var plan=new MagicConstructionManager.Plan(p,bridge,false,null);
        // Keep the test's remote aim chunk loaded without forcing chunks in production code.
        h.getLevel().getChunkAt(plan.start);SpellRuntime.cast(p,bridge,false);
        h.runAfterDelay(8,()->{
            h.assertTrue(h.getLevel().getBlockState(plan.start).is(MagicBlocks.EARTH.get()),"Permanent floating bridge anchors with its own material at "+plan.start+", got "+h.getLevel().getBlockState(plan.start)+", structures="+MagicConstructionManager.get(h.getLevel()).structures().stream().filter(c->c.owner.equals(p.getUUID())).map(c->c.root.toString()).toList());
            var data=(MagicBlocks.MagicBlockEntity)h.getLevel().getBlockEntity(plan.start);
            h.assertTrue(data.floating && data.expires==Long.MAX_VALUE,"Earth anchor is permanent");h.succeed();
        });
    }
    @MagicTest(timeoutTicks=60)
    public static void projectileBlockedByWall(GameTestHelper h){
        var p=caster(h);var pig=h.spawnWithNoFreeWill(EntityType.PIG,new BlockPos(1,2,5));pig.setNoGravity(true);
        for(int y=1;y<=4;y++)h.setBlock(new BlockPos(1,y,3),Blocks.STONE);
        SpellRuntime.projectile(p,spell("Ember"),h.absoluteVec(new Vec3(1.5,2.5,1.5)),new Vec3(0,0,1),false);
        h.runAfterDelay(15,()->{h.assertTrue(pig.getHealth()==10 && !MagicStatuses.has(pig,MagicStatuses.Status.BURNING),"Wall intercepts projectile before target");h.succeed();});
    }
    @MagicTest(timeoutTicks=60)
    public static void delayedMine(GameTestHelper h){
        var p=caster(h);var point=h.absoluteVec(new Vec3(2,2,2));
        SpellRuntime.zone(p,spell("Steam Mine"),point,100,2,false,true);
        h.runAfterDelay(22,()->{
            var pig=h.spawnWithNoFreeWill(EntityType.PIG,new BlockPos(2,2,2));pig.setNoGravity(true);
            h.runAfterDelay(12,()->{h.assertTrue(pig.getHealth()<10,"Armed persistent mine triggers on arriving target");h.succeed();});
        });
    }
    @MagicTest
    public static void syncedCatalogAndGenericSelf(GameTestHelper h){
        var data=RecipeRegistry.recipes().stream().map(d->{var j=d.source().deepCopy();j.addProperty("id",d.id().toString());return RecipeRegistry.GSON.toJson(j);}).toList();
        RecipeRegistry.acceptClient(data);
        h.assertTrue(RecipeRegistry.clientRecipes().size()==1286,"Complete catalog synchronization");
        var json=spell("Pebble").source().deepCopy();json.remove("self");
        var recipe=SpellDefinition.parse(Identifier.parse("test:earth"),json);var p=caster(h);
        SpellRuntime.cast(p,recipe,true);h.assertTrue(MagicStatuses.has(p,MagicStatuses.Status.STONE_SHELL),"Omitting Self uses generic rules");
        SpellRuntime.cast(p,spell("Ice Step"),true);h.assertTrue(MagicStatuses.has(p,MagicStatuses.Status.CHILLED),"Authored construction Self also applies its slow");
        RecipeRegistry.resetClient();h.succeed();
    }
    @MagicTest
    public static void ultimatesShatterSpawners(GameTestHelper h){
        var p=caster(h);BlockPos local=new BlockPos(2,2,2);Vec3 point=h.absoluteVec(new Vec3(2.5,2.5,2.5));
        SpellDefinition quake=spell("Earthquake"),pebble=spell("Pebble");
        h.assertTrue(quake.ultimate() && !pebble.ultimate(),"Only ritual spells count as ultimates");
        var node=new com.google.gson.JsonObject();node.addProperty("type","destroy_blocks");node.addProperty("budget",8);node.addProperty("radius",1.5);
        h.setBlock(local,Blocks.SPAWNER);
        EffectGraph.world(p,pebble,List.of(node),point,false,false);
        h.assertBlockPresent(Blocks.SPAWNER,local);
        EffectGraph.world(p,quake,List.of(node),point,false,false);
        h.assertBlockPresent(Blocks.AIR,local);
        h.setBlock(local,Blocks.CHEST);
        EffectGraph.world(p,quake,List.of(node),point,false,false);
        h.assertBlockPresent(Blocks.CHEST,local);
        h.succeed();
    }
    @MagicTest
    public static void scrollsSealStrongSpells(GameTestHelper h){
        var p=caster(h);SpellScrolls.forget(p);
        var wave=spell("Spirit Wave");
        h.assertTrue(SpellScrolls.locked(wave),"Spirit Wave needs a scroll");
        var weaker=MagicStats.resolve(p,wave.formula());
        h.assertTrue(!weaker.id().equals(wave.id()) && weaker.formula().size()==wave.formula().size()-1 && SpellScrolls.open(p,weaker),"Sealed formula casts a shorter open spell, got "+weaker.id());
        for(var d:RecipeRegistry.recipes())if(SpellScrolls.locked(d)){
            var cast=SpellScrolls.resolve(p,d);
            h.assertTrue(SpellScrolls.open(p,cast) && !cast.ultimate() && cast.formula().size()<d.formula().size(),"Every sealed spell falls back to an open one: "+d.id());
        }
        var elementals=RecipeRegistry.recipes().stream().filter(d->"elemental".equals(SpellDefinition.string(d.delivery(),"object_kind",""))).toList();
        h.assertTrue(elementals.stream().filter(d->!SpellScrolls.locked(d)).count()==1,"Exactly one elemental is open from the start");
        h.assertTrue(RecipeRegistry.recipes().stream().filter(d->d.ultimate() && !SpellScrolls.locked(d)).count()==1,"Exactly one ultimate is open from the start");
        h.assertTrue(SpellScrolls.learn(p,wave.id()) && !SpellScrolls.learn(p,wave.id()),"A spell is learned once");
        h.assertTrue(MagicStats.resolve(p,wave.formula()).id().equals(wave.id()),"Learned scroll restores the spell");
        var other=caster(h);SpellScrolls.forget(other);
        h.assertTrue(!MagicStats.resolve(other,wave.formula()).id().equals(wave.id()),"Knowledge is per player");
        other.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        h.assertTrue(MagicStats.resolve(other,wave.formula()).id().equals(wave.id()),"Creative knows every spell");
        SpellScrolls.forget(p);
        h.succeed();
    }
    @MagicTest
    public static void enemiesDropUnlearnedScrolls(GameTestHelper h){
        var p=caster(h);SpellScrolls.forget(p);
        var level=h.getLevel();var random=net.minecraft.util.RandomSource.create(7);
        var locked=RecipeRegistry.recipes().stream().filter(SpellScrolls::locked).toList();
        var rest=locked.stream().filter(d->!d.name().equals("Fire Titan")).map(d->d.id().toString()).collect(java.util.stream.Collectors.toSet());
        for(int i=0;i<20;i++)h.assertTrue(ScrollDrops.pick(random,rest,1,1).name().equals("Fire Titan"),"Drops prefer unlearned spells");
        var all=locked.stream().map(d->d.id().toString()).collect(java.util.stream.Collectors.toSet());
        h.assertTrue(ScrollDrops.pick(random,all,1,1)!=null,"Known spells still drop once all are learned");
        for(int i=0;i<50;i++)h.assertTrue(!ScrollDrops.pick(random,Set.of(),0,1).ultimate(),"Specialists never drop ultimates");
        var mage=com.restor.magicka.enemy.MagicEnemies.COUNTERMAGE.get().create(level,net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        var imp=com.restor.magicka.enemy.MagicEnemies.SPARK_IMP.get().create(level,net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        var boss=EntityType.WITHER.create(level,net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        var zombie=EntityType.ZOMBIE.create(level,net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        h.assertTrue(ScrollDrops.chance(boss)==1 && ScrollDrops.chance(mage)==ScrollDrops.CASTER_CHANCE && ScrollDrops.chance(imp)==ScrollDrops.SPECIALIST_CHANCE && ScrollDrops.chance(zombie)==0,"Drop chances by enemy kind");
        h.succeed();
    }
    @MagicTest
    public static void scrollIsNotConsumed(GameTestHelper h){
        var p=caster(h);SpellScrolls.forget(p);
        var titan=spell("Fire Titan");var stack=SpellScrollItem.of(titan);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stack);
        stack.getItem().use(h.getLevel(),p,net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(p.getMainHandItem().getCount()==1 && p.getMainHandItem().is(MagicItems.SPELL_SCROLL.get()),"Scroll stays in hand");
        h.assertTrue(SpellScrolls.known(p).contains(titan.id().toString()) && SpellScrolls.open(p,titan),"Reading the scroll teaches the spell");
        SpellScrolls.acceptClient(false,List.of());
        h.assertTrue(!SpellScrolls.clientOpen(p,titan) && SpellScrolls.clientOpen(p,spell("Stone Elemental")),"Book hides sealed spells it was not told about");
        SpellScrolls.acceptClient(false,List.of(titan.id().toString()));
        h.assertTrue(SpellScrolls.clientOpen(p,titan),"Book shows a learned spell");
        SpellScrolls.acceptClient(false,List.of());SpellScrolls.forget(p);
        h.succeed();
    }
}
