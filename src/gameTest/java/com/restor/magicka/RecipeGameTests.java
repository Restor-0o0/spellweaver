package com.restor.magicka;

import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.*;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder(Magicka.ID)
@PrefixGameTestTemplate(false)
public final class RecipeGameTests {
    static SpellDefinition spell(String name){return RecipeRegistry.recipes().stream().filter(d->d.name().equals(name)).findFirst().orElseThrow();}
    static net.minecraft.server.level.ServerPlayer caster(GameTestHelper h){
        var p=FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"RecipeTester"));
        p.setPos(h.absoluteVec(new Vec3(1.5,2,1.5)));p.setYRot(0);p.setXRot(0);
        RecipeRegistry.recipes().stream().filter(SpellScrolls::locked).forEach(d->SpellScrolls.learn(p,d.id()));return p;
    }
    @GameTest(template="empty")
    public static void catalogAndDataOverride(GameTestHelper h){
        int[] counts=new int[6];Set<SpellFormula> keys=new HashSet<>();
        for(var d:RecipeRegistry.recipes()){counts[d.formula().size()]++;keys.add(d.formula());}
        Check.assertTrue(h,Arrays.equals(counts,new int[]{0,8,36,120,330,792}) && keys.size()==1286,"Complete canonical catalog");
        long signatures=RecipeRegistry.recipes().stream().filter(d->d.tier().equals("Signature")).count();
        Check.assertTrue(h,signatures==206,"All signatures imported, got "+signatures);
        var base=spell("Ember");var json=base.source().deepCopy();json.addProperty("cast_ticks",33);json.addProperty("shape","beam");
        var changed=SpellDefinition.parse(new ResourceLocation("test:custom_ember"),json);
        Check.assertTrue(h,changed.shape()==SpellShape.BEAM && changed.castTicks()==33 && changed.formula().equals(base.formula()),"New delivery is defined by data alone");
        json.getAsJsonObject("formula").addProperty("fire",6);
        boolean rejected=false;try{SpellDefinition.parse(base.id(),json);}catch(IllegalArgumentException expected){rejected=true;}
        Check.assertTrue(h,rejected,"Invalid datapack formula rejected");h.succeed();
    }
    @GameTest(template="empty")
    public static void twoPlayersAndPrimarySelf(GameTestHelper h){
        var a=caster(h);var b=caster(h);
        CastingServer.input(a,7);CastingServer.input(a,4);
        CastingServer.input(b,7);CastingServer.input(b,1);CastingServer.input(b,2);
        var before=CastingServer.snapshot(a);a.setPos(a.position().add(3,0,0));
        Check.assertTrue(h,CastingServer.snapshot(a).formula()==before.formula(),"Movement preserves prepared formula");
        CastingServer.input(a,12);Check.assertTrue(h,CastingServer.snapshot(a).remaining()==0,"Release without a channel is ignored");
        CastingServer.input(a,9);
        Check.assertTrue(h,CastingServer.snapshot(a).intent()==0 && CastingServer.snapshot(a).total()==spell("Pebble").castTicks(),"Earth Primary is a projectile cast, no build channel");
        Check.assertTrue(h,Formula.unpack(CastingServer.snapshot(b).formula()).size()==2 && CastingServer.snapshot(b).remaining()==0,"Other player state is independent");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void terrainBlacklistAndHooks(GameTestHelper h){
        var p=caster(h);BlockPos local=new BlockPos(2,2,2),pos=h.absolutePos(local);
        h.setBlock(local,Blocks.BEDROCK);Check.assertFalse(h,WorldMagic.breakBlock(p,pos),"Bedrock protected");
        h.setBlock(local,Blocks.CHEST);Check.assertFalse(h,WorldMagic.breakBlock(p,pos),"Containers protected");
        h.setBlock(local,Blocks.STONE);
        Consumer<BlockEvent.BreakEvent> deny=e->{if(e.getPos().equals(pos))e.setCanceled(true);};MinecraftForge.EVENT_BUS.addListener(deny);
        try{Check.assertFalse(h,WorldMagic.breakBlock(p,pos),"Claim hook cancels terrain destruction");h.assertBlockPresent(Blocks.STONE,local);}finally{MinecraftForge.EVENT_BUS.unregister(deny);}
        Check.assertTrue(h,WorldMagic.breakBlock(p,pos),"Ordinary terrain can be destroyed");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=70)
    public static void projectileTravelsAndHits(GameTestHelper h){
        var p=caster(h);Vec3 start=h.absoluteVec(new Vec3(1.5,2.5,1.5));
        h.setBlock(new BlockPos(1,1,5),Blocks.STONE);
        var pig=h.spawnWithNoFreeWill(EntityType.PIG,new BlockPos(1,2,5));float health=pig.getHealth();
        pig.setNoGravity(true);
        SpellRuntime.projectile(p,spell("Ember"),start,new Vec3(0,0,1),false);
        Check.assertTrue(h,pig.getHealth()==health,"Projectile does not hit instantly");
        h.runAfterDelay(12,()->{Check.assertTrue(h,pig.getHealth()<health || MagicStatuses.has(pig,MagicStatuses.Status.BURNING),"Moving projectile collides with target");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=70)
    public static void waterZoneAndFreeze(GameTestHelper h){
        var p=caster(h);BlockPos local=new BlockPos(2,2,2);h.setBlock(local.below(),Blocks.STONE);
        var pig=h.spawnWithNoFreeWill(EntityType.PIG,local);Vec3 point=Vec3.atCenterOf(h.absolutePos(local));
        SpellRuntime.wetZone(p,point,2,100);
        h.runAfterDelay(12,()->{
            Check.assertTrue(h,MagicStatuses.has(pig,MagicStatuses.Status.WET),"Persistent zone wets occupants");
            MagicStatuses.temperature(pig,-40);Check.assertTrue(h,MagicStatuses.has(pig,MagicStatuses.Status.FROZEN),"Wet plus cold freezes");
            pig.discard();SpellRuntime.freezeWetZones(p,point,2);
            h.assertBlockPresent(MagicBlocks.ICE.get(),local);
            WorldMagic.temperature(p,point,80,spell("Firebolt"));h.assertBlockPresent(Blocks.AIR,local);h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void reachableStairAndFloatingBridge(GameTestHelper h){
        var p=caster(h);h.setBlock(new BlockPos(1,1,2),Blocks.STONE);
        var stair=spell("Stone Stair");var plan=new MagicConstructionManager.Plan(p,stair,false,null);
        Check.assertTrue(h,plan.start.equals(p.blockPosition().south()),"Stair starts at feet, not aim hit");
        BlockPos first=plan.next(p,0),previous=first;
        for(int i=1;i<stair.budget();i++){BlockPos next=plan.next(p,i);Check.assertTrue(h,next.distManhattan(previous)==1,"Stair segments remain connected and climbable");previous=next;}
        var path=new net.minecraft.world.phys.AABB(first).expandTowards(0,2,0).inflate(1);
        h.runAfterDelay(1,()->{
            h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,path).forEach(net.minecraft.world.entity.Entity::discard);
            SpellRuntime.cast(p,stair,false);
        });
        h.runAfterDelay(9,()->Check.assertTrue(h,h.getLevel().getBlockState(first).is(MagicBlocks.EARTH.get()),"Reachable first stair step placed"));
        h.runAfterDelay(73,()->{
            Check.assertTrue(h,MagicConstructionManager.get(h.getLevel()).structures().stream().anyMatch(c->c.owner.equals(p.getUUID()) && c.blocks.size()>2),"Manager tracks constructed stair");
            var bridge=spell("Skybridge");Check.assertTrue(h,bridge.floating() && bridge.origin()==OriginMode.AIM_HIT,"Bridge supports aim and floating anchor");
            var tag=MagicConstructionManager.get(h.getLevel()).save(new net.minecraft.nbt.CompoundTag());
            Check.assertTrue(h,!tag.getList("Structures",10).isEmpty(),"Construction metadata persists");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=80)
    public static void mineAndHealingObject(GameTestHelper h){
        DeathGameTests.floor(h);var p=caster(h);var pig=h.spawnWithNoFreeWill(EntityType.PIG,new BlockPos(2,2,2));pig.setHealth(3);
        SpellRuntime.cast(p,spell("Healing Spring"),true);
        h.succeedWhen(()->{
            Check.assertTrue(h,pig.getHealth()>3,"Healing summon pulses");
            var objects=h.getLevel().getEntitiesOfClass(MagicSummon.class,pig.getBoundingBox().inflate(4));
            Check.assertTrue(h,!objects.isEmpty(),"Healer has a damageable object");var object=objects.get(0);
            float hp=object.getHealth();object.hurt(net.minecraft.world.damagesource.DamageSource.MAGIC,3);
            Check.assertTrue(h,object.getHealth()==hp-3,"Object loses actual HP");
            Check.assertTrue(h,!object.shouldBeSaved() && object.interactAt(p,Vec3.ZERO,net.minecraft.world.InteractionHand.MAIN_HAND)==net.minecraft.world.InteractionResult.FAIL,"No persistent orphan or equipment farming");
            object.hurt(net.minecraft.world.damagesource.DamageSource.MAGIC,100);Check.assertTrue(h,object.isRemoved(),"Object destroyed at zero HP");
        });
    }
    @GameTest(template="empty",timeoutTicks=80)
    public static void lightningChainAndRevival(GameTestHelper h){
        var p=caster(h);var a=h.spawnWithNoFreeWill(EntityType.PIG,new BlockPos(1,2,2));var b=h.spawnWithNoFreeWill(EntityType.PIG,new BlockPos(3,2,2));
        MagicStatuses.apply(a,MagicStatuses.Status.WET,100,1);MagicStatuses.apply(b,MagicStatuses.Status.WET,100,1);
        SpellRuntime.chain(p,spell("Stormwater"),a.position(),2,5,1);
        Check.assertTrue(h,a.getHealth()<10 && b.getHealth()<10,"Water/lightning reaches multiple targets");
        Vec3 corpse=a.position();a.hurt(net.minecraft.world.damagesource.DamageSource.MAGIC,100);
        h.runAfterDelay(25,()->{Check.assertTrue(h,RevivalManager.revive(p,corpse,2,.3f),"Soul marker revives a fallen mob");h.succeed();});
    }
    @GameTest(template="empty",batch="sky",timeoutTicks=40)
    public static void bridgeReallyCreatesAnchor(GameTestHelper h){
        var p=caster(h);p.setPos(h.absoluteVec(new Vec3(1.5,150,1.5)));p.setXRot(0);p.setYRot(180);var bridge=spell("Skybridge");
        Check.assertTrue(h,SpellAim.hit(p).getType()==net.minecraft.world.phys.HitResult.Type.MISS,"Bridge test must aim into empty sky");
        var plan=new MagicConstructionManager.Plan(p,bridge,false,null);
        var level=h.getLevel();int cx=p.blockPosition().getX()>>4,from=Math.min(p.blockPosition().getZ(),plan.start.getZ())>>4,to=Math.max(p.blockPosition().getZ(),plan.start.getZ())>>4;
        for(int cz=from;cz<=to;cz++)level.setChunkForced(cx,cz,true);SpellRuntime.cast(p,bridge,false);
        h.runAfterDelay(8,()->{
            Check.assertTrue(h,h.getLevel().getBlockState(plan.start).is(MagicBlocks.EARTH.get()),"Permanent floating bridge anchors with its own material at "+plan.start+", got "+h.getLevel().getBlockState(plan.start)+", structures="+MagicConstructionManager.get(h.getLevel()).structures().stream().filter(c->c.owner.equals(p.getUUID())).map(c->c.root.toString()).toList());
            var data=(MagicBlocks.MagicBlockEntity)h.getLevel().getBlockEntity(plan.start);
            Check.assertTrue(h,data.floating && data.expires==Long.MAX_VALUE,"Earth anchor is permanent");
            for(int cz=from;cz<=to;cz++)level.setChunkForced(cx,cz,false);h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void projectileBlockedByWall(GameTestHelper h){
        var p=caster(h);var pig=h.spawnWithNoFreeWill(EntityType.PIG,new BlockPos(1,2,5));pig.setNoGravity(true);
        for(int y=1;y<=4;y++)h.setBlock(new BlockPos(1,y,3),Blocks.STONE);
        SpellRuntime.projectile(p,spell("Ember"),h.absoluteVec(new Vec3(1.5,2.5,1.5)),new Vec3(0,0,1),false);
        h.runAfterDelay(15,()->{Check.assertTrue(h,pig.getHealth()==10 && !MagicStatuses.has(pig,MagicStatuses.Status.BURNING),"Wall intercepts projectile before target");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void delayedMine(GameTestHelper h){
        var p=caster(h);var point=h.absoluteVec(new Vec3(2,2,2));
        SpellRuntime.zone(p,spell("Steam Mine"),point,100,2,false,true);
        h.runAfterDelay(22,()->{
            var pig=h.spawnWithNoFreeWill(EntityType.PIG,new BlockPos(2,2,2));pig.setNoGravity(true);
            h.runAfterDelay(12,()->{Check.assertTrue(h,pig.getHealth()<10,"Armed persistent mine triggers on arriving target");h.succeed();});
        });
    }
    @GameTest(template="empty")
    public static void syncedCatalogAndGenericSelf(GameTestHelper h){
        var data=RecipeRegistry.recipes().stream().map(d->{var j=d.source().deepCopy();j.addProperty("id",d.id().toString());return RecipeRegistry.GSON.toJson(j);}).toList();
        RecipeRegistry.acceptClient(data);
        Check.assertTrue(h,RecipeRegistry.clientRecipes().size()==1286,"Complete catalog synchronization");
        var json=spell("Pebble").source().deepCopy();json.remove("self");
        var recipe=SpellDefinition.parse(new ResourceLocation("test:earth"),json);var p=caster(h);
        SpellRuntime.cast(p,recipe,true);Check.assertTrue(h,MagicStatuses.has(p,MagicStatuses.Status.STONE_SHELL),"Omitting Self uses generic rules");
        SpellRuntime.cast(p,spell("Ice Step"),true);Check.assertTrue(h,MagicStatuses.has(p,MagicStatuses.Status.CHILLED),"Authored construction Self also applies its slow");
        RecipeRegistry.resetClient();h.succeed();
    }
    @GameTest(template="empty")
    public static void blastsLeaveAShallowBowlWithACrackedRim(GameTestHelper h){
        for(int x=0;x<10;x++)for(int z=0;z<10;z++)for(int y=1;y<7;y++)h.setBlock(new BlockPos(x,y,z),Blocks.STONE);
        var node=new com.google.gson.JsonObject();node.addProperty("type","destroy_blocks");node.addProperty("budget",300);node.addProperty("radius",4);
        EffectGraph.world(caster(h),spell("Pebble"),List.of(node),h.absoluteVec(new Vec3(5,7,5)),false,false);
        h.assertBlockPresent(Blocks.AIR,new BlockPos(4,6,4));
        h.assertBlockPresent(Blocks.STONE,new BlockPos(4,3,4));
        int rim=0,broken=0;
        for(int x=0;x<10;x++)for(int z=0;z<10;z++){double d=Math.hypot(x+.5-5,z+.5-5);if(d<3.6 || d>4.6)continue;rim++;if(h.getBlockState(new BlockPos(x,6,z)).isAir())broken++;}
        Check.assertTrue(h,broken>0 && broken<rim,"The rim is cracked: some blocks knocked out, some left ("+broken+"/"+rim+")");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void ultimatesShatterSpawners(GameTestHelper h){
        var p=caster(h);BlockPos local=new BlockPos(2,2,2);Vec3 point=h.absoluteVec(new Vec3(2.5,2.5,2.5));
        SpellDefinition quake=spell("Earthquake"),pebble=spell("Pebble");
        Check.assertTrue(h,quake.ultimate() && !pebble.ultimate(),"Only ritual spells count as ultimates");
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
    @GameTest(template="empty")
    public static void scrollsSealStrongSpells(GameTestHelper h){
        var p=caster(h);SpellScrolls.forget(p);
        var wave=spell("Spirit Wave");
        Check.assertTrue(h,SpellScrolls.locked(wave),"Spirit Wave needs a scroll");
        var weaker=MagicStats.resolve(p,wave.formula());
        Check.assertTrue(h,!weaker.id().equals(wave.id()) && weaker.formula().size()==wave.formula().size()-1 && SpellScrolls.open(p,weaker),"Sealed formula casts a shorter open spell, got "+weaker.id());
        for(var d:RecipeRegistry.recipes())if(SpellScrolls.locked(d)){
            var cast=SpellScrolls.resolve(p,d);
            Check.assertTrue(h,SpellScrolls.open(p,cast) && !cast.ultimate() && cast.formula().size()<d.formula().size(),"Every sealed spell falls back to an open one: "+d.id());
        }
        var elementals=RecipeRegistry.recipes().stream().filter(d->"elemental".equals(SpellDefinition.string(d.delivery(),"object_kind",""))).toList();
        Check.assertTrue(h,elementals.stream().filter(d->!SpellScrolls.locked(d)).count()==1,"Exactly one elemental is open from the start");
        Check.assertTrue(h,RecipeRegistry.recipes().stream().filter(d->d.ultimate() && !SpellScrolls.locked(d)).count()==1,"Exactly one ultimate is open from the start");
        Check.assertTrue(h,SpellScrolls.learn(p,wave.id()) && !SpellScrolls.learn(p,wave.id()),"A spell is learned once");
        Check.assertTrue(h,MagicStats.resolve(p,wave.formula()).id().equals(wave.id()),"Learned scroll restores the spell");
        var other=caster(h);SpellScrolls.forget(other);
        Check.assertTrue(h,!MagicStats.resolve(other,wave.formula()).id().equals(wave.id()),"Knowledge is per player");
        other.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        Check.assertTrue(h,MagicStats.resolve(other,wave.formula()).id().equals(wave.id()),"Creative knows every spell");
        SpellScrolls.forget(p);
        h.succeed();
    }
    @GameTest(template="empty")
    public static void enemiesDropUnlearnedScrolls(GameTestHelper h){
        var p=caster(h);SpellScrolls.forget(p);
        var level=h.getLevel();var random=net.minecraft.util.RandomSource.create(7);
        var locked=RecipeRegistry.recipes().stream().filter(SpellScrolls::locked).toList();
        var rest=locked.stream().filter(d->!d.name().equals("Fire Titan")).map(d->d.id().toString()).collect(java.util.stream.Collectors.toSet());
        for(int i=0;i<20;i++)Check.assertTrue(h,ScrollDrops.pick(random,rest,1,1).name().equals("Fire Titan"),"Drops prefer unlearned spells");
        var all=locked.stream().map(d->d.id().toString()).collect(java.util.stream.Collectors.toSet());
        Check.assertTrue(h,ScrollDrops.pick(random,all,1,1)!=null,"Known spells still drop once all are learned");
        for(int i=0;i<50;i++)Check.assertTrue(h,!ScrollDrops.pick(random,Set.of(),0,1).ultimate(),"Specialists never drop ultimates");
        var mage=com.restor.magicka.enemy.MagicEnemies.COUNTERMAGE.get().create(level);
        var imp=com.restor.magicka.enemy.MagicEnemies.SPARK_IMP.get().create(level);
        var boss=net.minecraft.world.entity.EntityType.WITHER.create(level);
        var zombie=net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
        var witch=net.minecraft.world.entity.EntityType.WITCH.create(level);
        var cow=net.minecraft.world.entity.EntityType.COW.create(level);
        Check.assertTrue(h,ScrollDrops.chance(boss)==MagicConfig.BOSS_SCROLL_CHANCE.get() && ScrollDrops.chance(mage)==MagicConfig.SPECIALIST_CASTER_SCROLL_CHANCE.get() && ScrollDrops.chance(imp)==MagicConfig.SPECIALIST_SCROLL_CHANCE.get(),"Drop chances by enemy kind");
        Check.assertTrue(h,ScrollDrops.chance(zombie)==MagicConfig.MOB_SCROLL_CHANCE.get() && ScrollDrops.chance(witch)==MagicConfig.CASTER_SCROLL_CHANCE.get() && ScrollDrops.chance(cow)==0,"Any hostile mob can drop a scroll, casters more often");
        var fire=locked.stream().filter(d->d.formula().fire()>0 && !d.ultimate()).count();
        Check.assertTrue(h,fire>0 && ScrollDrops.bias(level.getServer().getLevel(net.minecraft.world.level.Level.NETHER),locked.stream().filter(d->d.formula().fire()>0).findFirst().orElseThrow())==3,"The Nether favours fire scrolls");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void scrollIsNotConsumed(GameTestHelper h){
        var p=caster(h);SpellScrolls.forget(p);
        var titan=spell("Fire Titan");var stack=SpellScrollItem.of(titan);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stack);
        stack.getItem().use(h.getLevel(),p,net.minecraft.world.InteractionHand.MAIN_HAND);
        Check.assertTrue(h,p.getMainHandItem().getCount()==1 && p.getMainHandItem().is(MagicItems.SPELL_SCROLL.get()),"Scroll stays in hand");
        Check.assertTrue(h,SpellScrolls.known(p).contains(titan.id().toString()) && SpellScrolls.open(p,titan),"Reading the scroll teaches the spell");
        SpellScrolls.acceptClient(false,List.of());
        Check.assertTrue(h,!SpellScrolls.clientOpen(p,titan) && SpellScrolls.clientOpen(p,spell("Stone Elemental")),"Book hides sealed spells it was not told about");
        SpellScrolls.acceptClient(false,List.of(titan.id().toString()));
        Check.assertTrue(h,SpellScrolls.clientOpen(p,titan),"Book shows a learned spell");
        SpellScrolls.acceptClient(false,List.of());SpellScrolls.forget(p);
        h.succeed();
    }
    @GameTest(template="empty")
    public static void dungeonModChestsGetScrolls(GameTestHelper h){
        for(String path:List.of("chests/simple_dungeon","chests/frosted_prison_treasure","skeleton_dungeon/chests/common","bastion_treasure_chest"))Check.assertTrue(h,SpellScrollLoot.chest(path),"Chest table "+path);
        for(String path:List.of("blocks/chest","entities/zombie","gameplay/fishing","archaeology/desert_pyramid"))Check.assertTrue(h,!SpellScrollLoot.chest(path),"Not a chest "+path);
        h.succeed();
    }
    @GameTest(template="empty")
    public static void mobsResistTheirOwnElement(GameTestHelper h){
        var level=h.getLevel();
        var blaze=net.minecraft.world.entity.EntityType.BLAZE.create(level);var zombie=net.minecraft.world.entity.EntityType.ZOMBIE.create(level);var enderman=net.minecraft.world.entity.EntityType.ENDERMAN.create(level);var pig=net.minecraft.world.entity.EntityType.PIG.create(level);
        Check.assertTrue(h,MobResistances.multiplier(blaze,"fire")==MagicConfig.RESIST_MULTIPLIER.get() && MobResistances.multiplier(blaze,"water")==MagicConfig.WEAK_MULTIPLIER.get(),"Blazes resist fire and fear water");
        Check.assertTrue(h,MobResistances.multiplier(zombie,"death")<1 && MobResistances.multiplier(zombie,"life")>1,"Undead resist death and suffer life");
        Check.assertTrue(h,MobResistances.multiplier(enderman,"arcane")<1 && MobResistances.multiplier(enderman,"water")>1,"Endermen resist arcane and fear water");
        Check.assertTrue(h,MobResistances.multiplier(pig,"fire")==1 && MobResistances.multiplier(caster(h),"fire")==1,"Ordinary mobs and players are unaffected");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void mageLevelGrowsAndGatesFormulas(GameTestHelper h){
        var p=new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(),h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"MageTester"),null);
        p.connection=FakePlayerFactory.getMinecraft(h.getLevel()).connection;p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        Check.assertTrue(h,MageLevel.level(0)==0 && MageLevel.level(24)==0 && MageLevel.level(25)==1 && MageLevel.level(99)==1 && MageLevel.level(100)==2,"Level curve");
        Check.assertTrue(h,MageLevel.level(Integer.MAX_VALUE)==MagicConfig.MAX_MAGE_LEVEL.get(),"Level is capped");
        Check.assertTrue(h,MageLevel.maxElements(p)==3,"Novices weave three elements");
        int before=MagicGear.total(p)[MagicStats.Stat.DAMAGE.ordinal()];
        MageLevel.gain(p,MageLevel.xpFor(MagicConfig.FIVE_ELEMENTS.get()));
        Check.assertTrue(h,MageLevel.level(p)==MagicConfig.FIVE_ELEMENTS.get() && MageLevel.maxElements(p)==5,"Experience unlocks longer formulas");
        Check.assertTrue(h,MagicGear.total(p)[MagicStats.Stat.DAMAGE.ordinal()]==before+MageLevel.level(p)*MagicConfig.LEVEL_DAMAGE.get(),"Levels add damage");
        MageLevel.set(p,0);
        p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        Check.assertTrue(h,MageLevel.maxElements(p)==Formula.MAX,"Creative is not gated");
        var fake=caster(h);MageLevel.gain(fake,100);
        Check.assertTrue(h,MageLevel.xp(fake)==0,"Fake players gain nothing");
        h.succeed();
    }
}
