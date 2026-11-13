package com.restor.magicka;

import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.util.List;

@GameTestHolder(Magicka.ID)
@PrefixGameTestTemplate(false)
public final class BalanceGameTests {
    private static void configured(Runnable action){
        var mode=BalanceConfig.MODE.get();boolean fake=BalanceConfig.FAKE.get(),creative=BalanceConfig.CREATIVE.get();var overrides=BalanceConfig.OVERRIDES.get();
        try{BalanceConfig.MODE.set(BalanceConfig.Mode.DEFAULT);BalanceConfig.FAKE.set(false);BalanceConfig.CREATIVE.set(false);action.run();}
        finally{BalanceConfig.MODE.set(mode);BalanceConfig.FAKE.set(fake);BalanceConfig.CREATIVE.set(creative);BalanceConfig.OVERRIDES.set(overrides);}
    }
    @GameTest(template="empty") public static void recoveryKeepsFormulaAndDoesNotBlockTyping(GameTestHelper h){configured(()->{
        var p=RecipeGameTests.caster(h);CastingServer.input(p,7);CastingServer.input(p,11);CastingServer.input(p,0);CastingServer.input(p,10);
        h.assertTrue(CastingServer.snapshot(p).formula()==0,"First instant cast succeeds");
        CastingServer.input(p,0);int packed=CastingServer.snapshot(p).formula();CastingServer.input(p,10);
        h.assertTrue(CastingServer.snapshot(p).formula()==packed && packed!=0,"Rejected cast preserves formula");
        CastingServer.input(p,0);h.assertTrue(Formula.unpack(CastingServer.snapshot(p).formula()).size()==2,"Recovery allows typing");
        var other=RecipeGameTests.caster(h);h.assertTrue(SpellBalance.begin(other,RecipeGameTests.spell("Firebomb"))!=null,"Other player has independent cooldowns");
    });h.succeed();}
    @GameTest(template="empty") public static void automaticGroupsAndOverrides(GameTestHelper h){configured(()->{
        var spell=RecipeGameTests.spell("Firebomb");var p=RecipeGameTests.caster(h);
        BalanceConfig.OVERRIDES.set(List.of(spell.id()+"|group=shared|recovery=7|cooldown=80|mana=17|active_limit=2"));
        var cast=SpellBalance.begin(p,spell);h.assertTrue(cast!=null && cast.plan.group().equals("shared") && cast.plan.recovery()==7 && cast.plan.mana()==17,"Override resolves costs and group");
        h.assertTrue(SpellBalance.begin(p,spell)==null,"Group cooldown applies before windup ends");
        SpellBalance.finish(p,cast);var saved=p.getPersistentData().getCompound("magicka_balance");var state=SpellBalance.State.load(saved,SpellBalance.now(p));
        h.assertTrue(SpellBalance.waitTicks(state,cast.plan,SpellBalance.now(p))==80,"Cooldown survives NBT round trip");
        h.assertTrue(SpellBalance.waitTicks(state,cast.plan,SpellBalance.now(p)+81)==0,"Cooldown expires");
        h.assertTrue(RecipeRegistry.recipes().stream().filter(s->s.ultimate()||s.epic()).allMatch(s->BalanceRules.group(s).equals("ultimate")),"Ultimates classified automatically");
    });h.succeed();}
    @GameTest(template="empty") public static void derivedElementsShareParentCooldowns(GameTestHelper h){configured(()->{
        BalanceConfig.MODE.set(BalanceConfig.Mode.ELEMENTS);var p=RecipeGameTests.caster(h);Formula f=new Formula();f.add(Element.MAGMA);
        var cast=SpellBalance.begin(p,RecipeRegistry.fusion(f.key()));h.assertTrue(cast!=null,"Magma starts");
        h.assertTrue(cast.plan.elements()[Element.FIRE.ordinal()]>0 && cast.plan.elements()[Element.EARTH.ordinal()]>0,"Derived formula carries parent cooldowns");
        Formula fire=new Formula();fire.add(Element.FIRE);h.assertTrue(SpellBalance.begin(p,RecipeRegistry.fusion(fire.key()))==null,"Fire cannot bypass magma cooldown");
        Formula water=new Formula();water.add(Element.WATER);h.assertTrue(SpellBalance.begin(p,RecipeRegistry.fusion(water.key()))!=null,"Unrelated element stays available");
    });h.succeed();}
    @GameTest(template="empty") public static void manaChargesOnceAndDrainsPerChannelTick(GameTestHelper h){configured(()->{
        BalanceConfig.MODE.set(BalanceConfig.Mode.MANA);var p=RecipeGameTests.caster(h);var spell=RecipeGameTests.spell("Firebomb");
        BalanceConfig.OVERRIDES.set(List.of(spell.id()+"|mana=90|mana_per_second=20"));var cast=SpellBalance.begin(p,spell);
        h.assertTrue(cast!=null,"Initial cast can be paid");SpellBalance.started(p,cast);SpellBalance.started(p,cast);
        h.assertTrue(p.getPersistentData().getCompound("magicka_balance").getDouble("mana")==10,"Starting and pulses do not charge initial cost twice");
        for(int i=0;i<10;i++)h.assertTrue(SpellBalance.channel(p,cast),"Channel drains one mana per tick");
        h.assertFalse(SpellBalance.channel(p,cast),"Empty mana stops channel");
        h.assertTrue(SpellBalance.begin(p,spell)==null,"Cannot cast without mana");
    });h.succeed();}
    @GameTest(template="empty") public static void strainAndResourceRegeneration(GameTestHelper h){configured(()->{
        BalanceConfig.MODE.set(BalanceConfig.Mode.STRAIN);var p=RecipeGameTests.caster(h);var spell=RecipeGameTests.spell("Firebomb");BalanceConfig.OVERRIDES.set(List.of(spell.id()+"|strain=75"));
        h.assertTrue(SpellBalance.begin(p,spell)!=null,"First strain cast fits");h.assertTrue(SpellBalance.begin(p,spell)==null,"Strain prevents spam");
        var s=new SpellBalance.State(100);s.mana=0;s.strain=100;s.lastMana=100;s.lastStrain=100;s.advance(100,false);h.assertTrue(s.mana==0 && s.strain==100,"No duplicate recovery in one tick");
        s.advance(120,false);h.assertTrue(s.mana==0 && s.strain==91,"Regen delay counted exactly");s.advance(140,false);h.assertTrue(s.mana==12 && s.strain==73,"Resources recover over time");
    });h.succeed();}
    @GameTest(template="empty") public static void channelRecoveryAndQuickFillUseSameGate(GameTestHelper h){configured(()->{
        var p=RecipeGameTests.caster(h);CastingServer.input(p,7);CastingServer.input(p,11);CastingServer.input(p,1);CastingServer.input(p,1);CastingServer.input(p,9);
        h.assertTrue(CastingServer.snapshot(p).channeling(),"Channel starts");CastingServer.input(p,12);
        Formula f=new Formula();f.add(Element.WATER);CastingServer.quickFill(p,f.packed());for(int i=0;i<4;i++)new CastingServer().tick(new PlayerTickEvent.Post(p));
        int packed=CastingServer.snapshot(p).formula();CastingServer.input(p,9);h.assertTrue(packed!=0 && CastingServer.snapshot(p).formula()==packed && !CastingServer.snapshot(p).channeling(),"Quick fill cannot bypass channel recovery");
    });h.succeed();}
    @GameTest(template="empty") public static void activeLimitCountsCastsRatherThanNodes(GameTestHelper h){configured(()->{
        var p=RecipeGameTests.caster(h);var spell=RecipeGameTests.spell("Firebomb");BalanceConfig.MODE.set(BalanceConfig.Mode.MANA);
        BalanceConfig.OVERRIDES.set(List.of(spell.id()+"|mana=0|active_group=zone|active_limit=1"));
        var first=SpellBalance.begin(p,spell);SpellBalance.run(first,()->{SpellRuntime.delay(p,100,()->{});SpellRuntime.delay(p,100,()->{});});
        h.assertTrue(SpellRuntime.activeCasts(p,"zone").size()==1,"Multiple nodes form one cast");var next=SpellBalance.begin(p,spell);
        h.assertTrue(next!=null && first.cancelled,"Oldest cast replaced");
        h.assertTrue(SpellRuntime.activeCasts(p,"zone").isEmpty(),"Cancelled cast no longer counts");
        var policy=BalanceConfig.ZONE_POLICY.get();try{BalanceConfig.ZONE_POLICY.set(BalanceConfig.ZonePolicy.REJECT);SpellBalance.run(next,()->SpellRuntime.delay(p,100,()->{}));h.assertTrue(SpellBalance.begin(p,spell)==null,"Reject policy refuses excess casts");}finally{BalanceConfig.ZONE_POLICY.set(policy);}
    });h.succeed();}
    @GameTest(template="empty") public static void offAndCreativeBypass(GameTestHelper h){configured(()->{
        var p=RecipeGameTests.caster(h);BalanceConfig.MODE.set(BalanceConfig.Mode.OFF);h.assertTrue(SpellBalance.begin(p,RecipeGameTests.spell("Firebomb")).plan==null,"OFF bypasses all balance rules");
        BalanceConfig.MODE.set(BalanceConfig.Mode.DEFAULT);BalanceConfig.CREATIVE.set(true);p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);h.assertTrue(SpellBalance.begin(p,RecipeGameTests.spell("Firebomb")).plan==null,"Creative bypass configurable");
    });h.succeed();}
}
