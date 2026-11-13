package com.restor.magicka;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.*;
import java.nio.file.*;
import java.util.*;

public final class SettingsApplicationGameTests {
    static final class Fixture implements AutoCloseable {
        final GameTestHelper h;final Map<String,ModConfigSpec.ConfigValue<?>> values=ConfigCommand.values();final Map<String,Object> previous=new LinkedHashMap<>();
        Fixture(GameTestHelper h){this.h=h;values.forEach((k,v)->previous.put(k,v.get()));}
        void update(Map<String,Object> changes){var p=SettingsGameTests.operator(h);var json=new Gson().toJson(changes);SettingsData.apply(p,new MagicNetwork.SettingsUpdate(1,json));changes.forEach((key,value)->h.assertTrue(Objects.equals(values.get(key).get(),value),"Server applies "+key));SettingsData.forget(p.getUUID());}
        void update(String key,Object value){update(Map.of(key,value));}
        @SuppressWarnings("unchecked") @Override public void close(){previous.forEach((k,v)->((ModConfigSpec.ConfigValue<Object>)values.get(k)).set(v));MagicConfig.CONSTRUCTION.save();BalanceConfig.MODE.save();SettingsData.changed(h.getLevel().getServer(),previous.keySet());}
    }
    static Object alternate(String key,ModConfigSpec.ConfigValue<?> value){
        Object original=value.get();if(original instanceof Boolean b)return !b;
        if(original instanceof Enum<?> e){Object[] constants=e.getDeclaringClass().getEnumConstants();return constants[(e.ordinal()+1)%constants.length];}
        if(original instanceof Integer n){var range=value.getSpec().getRange();return n<((Number)range.getMax()).intValue()?n+1:n-1;}
        if(original instanceof Double n){var range=value.getSpec().getRange();double max=((Number)range.getMax()).doubleValue(),min=((Number)range.getMin()).doubleValue();return n<max?Math.min(max,n+.125):Math.max(min,n-.125);}
        if(original instanceof List<?> list){var result=new ArrayList<String>();for(Object row:list)result.add(row.toString());if(key.equals("balance.spellOverrides"))return List.of("magicka:firebomb|group=audit|cooldown=53|mana=17");if(result.isEmpty())return List.of("audit=7");String[] pair=result.getFirst().split("=",2);result.set(0,pair[0]+"="+(Double.parseDouble(pair[1])+1));return result;}
        throw new IllegalStateException(key);
    }
    @MagicTest(batch="settings_audit") public static void everySettingChangesAndPersistsThroughServerHandler(GameTestHelper h){
        try(var fixture=new Fixture(h)){
            Map<String,Object> changes=new LinkedHashMap<>();fixture.values.forEach((key,value)->changes.put(key,alternate(key,value)));fixture.update(changes);
            Path generalFile=SettingsData.configPath("spellweaver-server.toml"),balanceFile=SettingsData.configPath("spellweaver-balance.toml");
            var general=new com.electronwill.nightconfig.toml.TomlParser().parse(Files.readString(generalFile));
            var balance=new com.electronwill.nightconfig.toml.TomlParser().parse(Files.readString(balanceFile));
            var report=new JsonObject();
            for(var entry:changes.entrySet()){
                String key=entry.getKey();Object stored=key.startsWith("balance.")?balance.get(key.substring(8)):general.get(key),expected=entry.getValue();
                boolean matches=expected instanceof Enum<?> e?e.name().equals(String.valueOf(stored)):expected instanceof Number n?stored instanceof Number actual&&Double.compare(n.doubleValue(),actual.doubleValue())==0:Objects.equals(expected,stored);
                h.assertTrue(matches,"Saved TOML retains "+key);var row=new JsonObject();row.addProperty("changed",true);row.addProperty("persisted",true);row.addProperty("file",(key.startsWith("balance.")?balanceFile:generalFile).toString());report.add(key,row);
            }
            Path reportFile=Path.of("..","test-jars","settings-application-audit.json").normalize();Files.createDirectories(reportFile.getParent());Files.writeString(reportFile,new GsonBuilder().setPrettyPrinting().create().toJson(report));
            h.assertTrue(changes.size()==82,"Every exposed setting checked, got "+changes.size());
        }catch(java.io.IOException e){throw new RuntimeException(e);}h.succeed();
    }
    @MagicTest(batch="settings_audit") public static void applyingProgressionChangesInvalidatesScaledSpellCache(GameTestHelper h){
        try(var fixture=new Fixture(h)){
            var p=RecipeGameTests.caster(h);p.getPersistentData().putInt("magicka_mage_xp",MageLevel.xpFor(10));fixture.update(Map.of("mageLevels",true,"maxMageLevel",30,"damagePerLevel",1,"castSpeedPerLevel",1));
            var base=RecipeGameTests.spell("Firebomb");var before=MagicStats.apply(p,base);fixture.update(Map.of("damagePerLevel",7,"castSpeedPerLevel",8));var after=MagicStats.apply(p,base);
            h.assertTrue(before!=after,"Cached spell rebuilt after settings update");h.assertTrue(after.castTicks()<before.castTicks(),"Live cast speed bonus changes the next spell");
            double a=before.effects().stream().filter(n->SpellDefinition.string(n,"type","").equals("deal_damage")).mapToDouble(n->n.get("amount").getAsDouble()).sum(),b=after.effects().stream().filter(n->SpellDefinition.string(n,"type","").equals("deal_damage")).mapToDouble(n->n.get("amount").getAsDouble()).sum();h.assertTrue(b>a,"Live damage bonus affects spell damage");
            fixture.update("mageLevels",false);var disabled=MagicStats.apply(p,base);h.assertTrue(disabled.castTicks()>after.castTicks(),"Disabling mage levels removes the cached bonus");
        }h.succeed();
    }
    @MagicTest(batch="settings_audit") public static void disablingCooldownMultiplierDisablesAlreadyRunningCooldown(GameTestHelper h){
        try(var fixture=new Fixture(h)){
            fixture.update(Map.of("balance.mode",BalanceConfig.Mode.DEFAULT,"balance.fakePlayerBypass",false,"balance.cooldownMultiplier",1.0));var p=RecipeGameTests.caster(h);var spell=RecipeGameTests.spell("Firebomb");var first=SpellBalance.begin(p,spell);h.assertTrue(first!=null,"First cast accepted");SpellBalance.finish(p,first);h.assertTrue(SpellBalance.begin(p,spell)==null,"Cooldown actually blocks recast");fixture.update("balance.cooldownMultiplier",0.0);h.assertTrue(SpellBalance.begin(p,spell)!=null,"Zero multiplier immediately disables old cooldown gate");
        }h.succeed();
    }
    @MagicTest(batch="settings_audit") public static void constructionToggleControlsDirectPlacementAndIce(GameTestHelper h){
        try(var fixture=new Fixture(h)){
            var p=RecipeGameTests.caster(h);BlockPos pos=h.absolutePos(new BlockPos(3,2,3));h.setBlock(new BlockPos(3,1,3),Blocks.STONE);fixture.update("constructiveMagic",false);
            h.assertFalse(WorldPlacement.placeBlock(p,pos,MagicBlocks.EARTH.get().defaultBlockState(),UUID.randomUUID(),pos,true,false,null),"Direct placement respects disabled building");
            h.getLevel().setBlock(pos,Blocks.WATER.defaultBlockState(),3);SpellRuntime.freezeSurface(p,net.minecraft.world.phys.Vec3.atCenterOf(pos),1);h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.WATER),"Water cannot become a placed ice block while building is disabled");
            fixture.update("constructiveMagic",true);SpellRuntime.freezeSurface(p,net.minecraft.world.phys.Vec3.atCenterOf(pos),1);h.assertTrue(h.getLevel().getBlockState(pos).is(MagicBlocks.ICE.get()),"Enabling building immediately allows ice placement");
        }h.succeed();
    }
    @MagicTest(batch="settings_audit") public static void chestScrollModifierIsLoadedAndUsesConfiguredChance(GameTestHelper h){
        try(var fixture=new Fixture(h)){
            var params=new net.minecraft.world.level.storage.loot.LootParams.Builder(h.getLevel()).withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,h.absoluteVec(new net.minecraft.world.phys.Vec3(2,2,2))).create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
            var context=new net.minecraft.world.level.storage.loot.LootContext.Builder(params).withOptionalRandomSeed(34567).create(Optional.empty());var table=net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft","chests/simple_dungeon");
            fixture.update("chestScrollMultiplier",0.0);h.assertTrue(net.neoforged.neoforge.common.CommonHooks.modifyLoot(table,new it.unimi.dsi.fastutil.objects.ObjectArrayList<>(),context).isEmpty(),"Zero chest multiplier prevents drops");
            fixture.update("chestScrollMultiplier",16.0);int found=0;for(int i=0;i<16;i++)found+=net.neoforged.neoforge.common.CommonHooks.modifyLoot(table,new it.unimi.dsi.fastutil.objects.ObjectArrayList<>(),context).stream().filter(s->s.getItem() instanceof SpellScrollItem).count();h.assertTrue(found>0,"Registered chest modifier actually produces scrolls when enabled");
        }h.succeed();
    }
    @MagicTest(batch="settings_audit") public static void resourceSettingsChangeActualConsumptionAndRecovery(GameTestHelper h){
        try(var f=new Fixture(h)){
            f.update(Map.of("balance.mode",BalanceConfig.Mode.CUSTOM,"balance.customRecovery",false,"balance.customGroupCooldowns",false,"balance.customMana",true,"balance.customStrain",true,"balance.fakePlayerBypass",false));
            f.update(Map.of("balance.manaCapacity",200.0,"balance.manaBaseCost",5.0,"balance.manaCostPerElement",4.0,"balance.manaChannelPerSecond",20.0,"balance.manaGroupMultipliers",List.of("healing=2")));
            f.update(Map.of("balance.strainCapacity",1000.0,"balance.strainBaseCost",3.0,"balance.strainPerElement",2.0,"balance.strainChannelPerSecond",10.0,"balance.strainGroupMultipliers",List.of("healing=3")));
            var p=RecipeGameTests.caster(h);var formula=new Formula();formula.add(Element.LIFE);formula.add(Element.LIFE);var spell=RecipeRegistry.fusion(formula.key());var cast=SpellBalance.begin(p,spell);h.assertTrue(cast!=null,"Configured resource cast accepted");var tag=p.getPersistentData().getCompoundOrEmpty("magicka_balance");h.assertTrue(tag.getDoubleOr("mana",0.0)==174&&tag.getDoubleOr("strain",0.0)==21,"Base, point and group costs actually consumed");
            SpellBalance.channel(p,cast);tag=p.getPersistentData().getCompoundOrEmpty("magicka_balance");h.assertTrue(tag.getDoubleOr("mana",0.0)==172&&tag.getDoubleOr("strain",0.0)==22.5,"Configured channel rates actually consumed");
            f.update(Map.of("balance.manaRegenPerSecond",40.0,"balance.manaRegenDelayTicks",10,"balance.strainDecayPerSecond",80.0,"balance.strainDecayDelayTicks",5,"balance.regenerateWhileChanneling",false));
            var state=new SpellBalance.State(100);state.mana=10;state.strain=1000;state.lastMana=state.lastStrain=100;state.advance(110,false);h.assertTrue(state.mana==10&&state.strain==980,"Resource delays actually gate regeneration");state.advance(120,false);h.assertTrue(state.mana==30&&state.strain==940,"Configured regeneration and decay rates used");state.advance(130,true);h.assertTrue(state.mana==30,"Channel regeneration off");f.update("balance.regenerateWhileChanneling",true);state.advance(140,true);h.assertTrue(state.mana==50,"Channel regeneration on");
            f.update(Map.of("balance.manaBaseCost",0.0,"balance.manaCostPerElement",0.0,"balance.manaChannelPerSecond",0.0,"balance.strainBaseCost",0.0,"balance.strainPerElement",0.0,"balance.strainChannelPerSecond",0.0));var free=RecipeGameTests.caster(h);var zero=SpellBalance.begin(free,spell);SpellBalance.channel(free,zero);tag=free.getPersistentData().getCompoundOrEmpty("magicka_balance");h.assertTrue(tag.getLongOr("mana_used",0L)==0&&tag.getLongOr("strain_used",0L)==0,"Zero costs do not restart regeneration delays");
        }h.succeed();
    }
    @MagicTest(batch="settings_audit") public static void cooldownSettingsAndSubsystemSwitchesAffectCastGate(GameTestHelper h){
        try(var f=new Fixture(h)){
            f.update(Map.of("balance.mode",BalanceConfig.Mode.CUSTOM,"balance.fakePlayerBypass",false,"balance.customMana",false,"balance.customStrain",false,"balance.customRecovery",true,"balance.customGroupCooldowns",true,"balance.customElementCooldowns",true));
            f.update(Map.of("balance.recoveryTicks",List.of("basic=12"),"balance.groupCooldownTicks",List.of("basic=30"),"balance.elementCooldownTicks",List.of("death=10"),"balance.cooldownMultiplier",2.0,"balance.minimumRecoveryTicks",2,"balance.elementRepeatMultiplier",.5,"balance.castSpeedAffectsCooldowns",false));
            var formula=new Formula();formula.add(Element.DEATH);formula.add(Element.DEATH);var spell=RecipeRegistry.fusion(formula.key());var p=RecipeGameTests.caster(h);var cast=SpellBalance.begin(p,spell);h.assertTrue(cast!=null&&cast.plan.recovery()==24&&cast.plan.cooldown()==60&&cast.plan.elements()[Element.DEATH.ordinal()]==30,"Recovery, group, repeat and global multipliers used by accepted cast");SpellBalance.finish(p,cast);h.assertTrue(SpellBalance.begin(p,spell)==null,"Configured cooldown actually blocks cast");
            f.update(Map.of("balance.customRecovery",false,"balance.customGroupCooldowns",false,"balance.customElementCooldowns",false));h.assertTrue(SpellBalance.begin(p,spell)!=null,"Turning subsystems off removes their gates");
            f.update(Map.of("balance.customRecovery",true,"balance.cooldownMultiplier",.01,"balance.minimumRecoveryTicks",7));h.assertTrue(BalanceRules.plan(spell,100).recovery()==7,"Minimum recovery clamp applies");f.update(Map.of("balance.cooldownMultiplier",1.0,"balance.minimumRecoveryTicks",0,"balance.castSpeedAffectsCooldowns",true));h.assertTrue(BalanceRules.plan(spell,200).recovery()==6,"Cast speed changes cooldown duration when enabled");
            f.update("balance.derivedElementsShareCooldowns",false);var magma=new Formula();magma.add(Element.MAGMA);h.assertTrue(BalanceRules.plan(RecipeRegistry.fusion(magma.key()),100).elements()[Element.FIRE.ordinal()]==0,"Derived parent sharing can be disabled");f.update("balance.derivedElementsShareCooldowns",true);h.assertTrue(BalanceRules.plan(RecipeRegistry.fusion(magma.key()),100).elements()[Element.FIRE.ordinal()]>0,"Derived parent sharing can be enabled");
            f.update("balance.fakePlayerBypass",true);h.assertTrue(SpellBalance.begin(p,spell).plan==null,"Fake-player exception actually bypasses balance");
        }h.succeed();
    }
    @MagicTest(batch="settings_audit") public static void blockLifetimesAndPermanentFlagsControlActualBlocks(GameTestHelper h){
        try(var f=new Fixture(h)){
            f.update(Map.of("constructiveMagic",true,"magicBlockLifetime",true,"permanentEarthBlocks",false,"permanentRootBlocks",false,"earthLifetimeTicks",21,"iceLifetimeTicks",33,"rootLifetimeTicks",45,"arcaneLifetimeTicks",57));var p=RecipeGameTests.caster(h);
            var blocks=List.of(MagicBlocks.EARTH.get(),MagicBlocks.ICE.get(),MagicBlocks.ROOT.get(),MagicBlocks.ARCANE.get());int[] life={21,33,45,57};
            for(int i=0;i<blocks.size();i++){BlockPos pos=h.absolutePos(new BlockPos(i+1,3,3));h.getLevel().setBlock(pos,Blocks.AIR.defaultBlockState(),3);h.assertTrue(WorldPlacement.placeBlock(p,pos,blocks.get(i).defaultBlockState(),UUID.randomUUID(),pos,true,false,null),"Place lifetime sample");var data=(MagicBlocks.MagicBlockEntity)h.getLevel().getBlockEntity(pos);h.assertTrue(data.expires-h.getLevel().getGameTime()==life[i],"Configured lifetime used for "+data.material);data.expires=h.getLevel().getGameTime()-1;tickBlock(data,h);h.assertTrue(h.getLevel().getBlockState(pos).isAir(),"Expired nonpermanent block disappears");}
            f.update(Map.of("permanentEarthBlocks",true,"permanentRootBlocks",true));for(var block:List.of(MagicBlocks.EARTH.get(),MagicBlocks.ROOT.get())){BlockPos pos=h.absolutePos(new BlockPos(2,3,3));h.getLevel().setBlock(pos,block.defaultBlockState(),3);var data=(MagicBlocks.MagicBlockEntity)h.getLevel().getBlockEntity(pos);data.initialize(p.getUUID(),UUID.randomUUID(),pos,h.getLevel().getGameTime()-1,true);tickBlock(data,h);h.assertTrue(h.getLevel().getBlockState(pos).is(block),"Permanent material ignores expiry");h.getLevel().removeBlock(pos,false);}
            BlockPos pos=h.absolutePos(new BlockPos(2,3,3));h.getLevel().setBlock(pos,MagicBlocks.PUDDLE.get().defaultBlockState(),3);var puddle=(MagicBlocks.MagicBlockEntity)h.getLevel().getBlockEntity(pos);puddle.initialize(p.getUUID(),UUID.randomUUID(),pos,h.getLevel().getGameTime()-1,true);tickBlock(puddle,h);h.assertTrue(h.getLevel().getBlockState(pos).isAir(),"Permanent earth does not make water puddles immortal");
            h.getLevel().setBlock(pos,MagicBlocks.ICE.get().defaultBlockState(),3);var ice=(MagicBlocks.MagicBlockEntity)h.getLevel().getBlockEntity(pos);ice.initialize(p.getUUID(),UUID.randomUUID(),pos,h.getLevel().getGameTime()-1,true);f.update("magicBlockLifetime",false);tickBlock(ice,h);h.assertTrue(h.getLevel().getBlockState(pos).is(MagicBlocks.ICE.get()),"Disabling expiry protects existing ice");f.update("magicBlockLifetime",true);tickBlock(ice,h);h.assertTrue(h.getLevel().getBlockState(pos).isAir(),"Reenabling expiry processes overdue ice");
        }h.succeed();
    }
    private static void tickBlock(MagicBlocks.MagicBlockEntity data,GameTestHelper h){try{var tick=MagicBlocks.MagicBlockEntity.class.getDeclaredMethod("tick",net.minecraft.server.level.ServerLevel.class);tick.setAccessible(true);tick.invoke(data,h.getLevel());}catch(ReflectiveOperationException e){throw new RuntimeException(e);}}
    private static net.minecraft.server.level.ServerPlayer damagePlayer(GameTestHelper h){var p=new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(),h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"DamageTester"),net.minecraft.server.level.ClientInformation.createDefault()){@Override public boolean isInvulnerableTo(net.minecraft.server.level.ServerLevel level,net.minecraft.world.damagesource.DamageSource source){return isInvulnerableToBase(source);}};p.connection=RecipeGameTests.caster(h).connection;p.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(2,3,2)));p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);return p;}
    @MagicTest(batch="settings_audit") public static void playerDamageAndSelfHarmSettingsChangeActualDamage(GameTestHelper h){
        try(var f=new Fixture(h)){
            var p=damagePlayer(h);
            var spell=RecipeGameTests.spell("Firebomb");var damage=new JsonObject();damage.addProperty("type","deal_damage");damage.addProperty("element","arcane");damage.addProperty("amount",8);var graph=List.of(damage);
            f.update(Map.of("playerMagicDamageMultiplier",.5,"selfHarm",MagicConfig.SelfHarm.FULL));p=damagePlayer(h);EffectGraph.entity(p,p,spell,graph,p.position(),false);h.assertTrue(p.getHealth()==16,"Player multiplier affects actual incidental self damage");
            f.update(Map.of("selfHarm",MagicConfig.SelfHarm.SCALED,"selfHarmMultiplier",.25));p=damagePlayer(h);EffectGraph.entity(p,p,spell,graph,p.position(),false);h.assertTrue(p.getHealth()==19,"Scaled self harm affects actual damage");
            f.update("selfHarm",MagicConfig.SelfHarm.OFF);p=damagePlayer(h);EffectGraph.entity(p,p,spell,graph,p.position(),false);h.assertTrue(p.getHealth()==20,"Self harm OFF prevents incidental self damage");
            p=damagePlayer(h);EffectGraph.entity(p,p,spell,graph,p.position(),true);h.assertTrue(p.getHealth()==12,"Deliberate self cast retains recipe damage");
            var attacker=new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"PvpTester")){@Override public boolean canHarmPlayer(net.minecraft.world.entity.player.Player other){return true;}};f.update("friendlyFire",false);h.assertFalse(EffectGraph.canHarm(attacker,p),"Friendly fire OFF blocks player targeting");f.update("friendlyFire",true);h.assertTrue(EffectGraph.canHarm(attacker,p),"Friendly fire ON permits player targeting when server PvP allows it");
        }h.succeed();
    }
    @MagicTest(batch="settings_audit") public static void scrollChanceBossAndDimensionSettingsChangeSelection(GameTestHelper h){
        try(var f=new Fixture(h)){
            var zombie=net.minecraft.world.entity.EntityTypes.ZOMBIE.create(h.getLevel(),net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);var witch=net.minecraft.world.entity.EntityTypes.WITCH.create(h.getLevel(),net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);f.update(Map.of("scrollDropsFromAllMobs",true,"mobScrollChance",.13,"casterMobScrollChance",.24,"bossHealthThreshold",0));h.assertTrue(ScrollDrops.chance(zombie)==.13&&ScrollDrops.chance(witch)==.24,"Mob and caster chances actually differ");f.update("scrollDropsFromAllMobs",false);h.assertTrue(ScrollDrops.chance(zombie)==0&&ScrollDrops.chance(witch)==0,"Mob drops toggle actually disables ordinary drops");
            zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(300);f.update(Map.of("bossHealthThreshold",200,"bossScrollChance",.57));h.assertTrue(ScrollDrops.boss(zombie)&&ScrollDrops.chance(zombie)==.57,"Boss health and chance settings used");f.update("bossHealthThreshold",400);h.assertFalse(ScrollDrops.boss(zombie),"Raising boss threshold removes health-based boss status");
            f.update(Map.of("bossHealthThreshold",200,"bossUltimateWeight",0));var p=RecipeGameTests.caster(h);for(int i=0;i<16;i++)h.assertFalse(ScrollDrops.drop(zombie,p).ultimate(),"Zero boss ultimate weight excludes ultimates");f.update("bossUltimateWeight",100);zombie.getRandom().setSeed(34567);boolean ultimate=false;for(int i=0;i<16;i++)ultimate|=ScrollDrops.drop(zombie,p).ultimate();h.assertTrue(ultimate,"Increasing boss ultimate weight enables ultimate drops");
            var fire=RecipeRegistry.recipes().stream().filter(s->s.formula().total(Element.FIRE)>0).findFirst().orElseThrow();var nether=h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);f.update("dimensionScrollBias",true);h.assertTrue(ScrollDrops.bias(nether,fire)==3,"Dimension bias increases element weight");f.update("dimensionScrollBias",false);h.assertTrue(ScrollDrops.bias(nether,fire)==1,"Dimension bias toggle actually removes weighting");
        }h.succeed();
    }
}
