package com.restor.magicka;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import java.util.*;

public final class FusionGameTests {
    private static SpellFormula of(int... counts) { return new SpellFormula(counts[0], counts[1], counts[2], counts[3], counts[4], counts[5], counts[6], counts[7]); }
    private static com.google.gson.JsonObject node(String type, double amount) { var n = new com.google.gson.JsonObject(); n.addProperty("type", type); n.addProperty("amount", amount); n.addProperty("duration_ticks", 60); return n; }
    private static void every(int[] counts, int element, int left, List<SpellFormula> out) {
        if (element == 8) { if (left < 5) out.add(of(counts)); return; }
        for (int n = 0; n <= left; n++) { counts[element] = n; every(counts, element + 1, left - n, out); }
        counts[element] = 0;
    }
    @MagicTest
    public static void everyElementSetCastsSomething(GameTestHelper h) {
        var all = new ArrayList<SpellFormula>(); every(new int[8], 0, 5, all);
        h.assertTrue(all.size() == 1286, "All element sets enumerated, got " + all.size());
        for (var formula : all) {
            var spell = RecipeRegistry.resolve(formula);
            h.assertTrue(spell.formula().equals(formula), "The spell for " + formula + " keeps its elements");
            var fusion = RecipeRegistry.fusion(formula);
            h.assertTrue(!fusion.ordered() && fusion.formula().equals(formula) && !fusion.effects().isEmpty(), "A fusion is built for " + formula);
        }
        h.assertTrue(RecipeRegistry.fusion(of(1,0,0,0,1,0,0,0)).shape() == SpellShape.PROJECTILE, "Earth leads into a heavy projectile");
        h.assertTrue(RecipeRegistry.fusion(of(0,0,0,0,3,0,0,0)).shape() == SpellShape.BURST_PROJECTILE, "Three Earth burst on impact");
        h.assertTrue(RecipeRegistry.fusion(of(0,1,0,2,0,0,0,0)).shape() == SpellShape.CHAIN, "Lightning leads into a chain");
        h.assertTrue(RecipeRegistry.fusion(of(0,0,0,0,0,1,0,1)).shape() == SpellShape.BEAM, "Life and Death make a beam");
        h.assertTrue(RecipeRegistry.fusion(of(0,1,2,0,0,0,0,0)).number("fan", 1) > 1, "Cold with Water fans out ice shards");
        h.assertTrue(RecipeRegistry.fusion(of(1,0,0,0,0,0,1,0)).number("radius", 0) > RecipeRegistry.fusion(of(2,0,0,0,0,0,0,0)).number("radius", 0), "Arcane widens the fusion");
        h.succeed();
    }
    @MagicTest
    public static void fireAndEarthFuseIntoMagma(GameTestHelper h) {
        var forward=Reactions.type(List.of(Element.FIRE,Element.EARTH),true);
        var reverse=Reactions.type(List.of(Element.EARTH,Element.FIRE),true);
        h.assertTrue(forward.size()==1 && forward.has(Element.MAGMA),"Fire then Earth fuses into Magma");
        h.assertTrue(reverse.size()==1 && reverse.has(Element.MAGMA),"Earth then Fire fuses into Magma");
        h.assertTrue(RecipeRegistry.resolve(forward.key()).name().equals("Magma"),"The fused rune resolves the migrated Magma spell");
        int packed=forward.packed();
        h.assertTrue(Formula.valid(packed) && Formula.unpack(packed).has(Element.MAGMA),"Magma survives packed formula serialization");h.succeed();
    }
    @MagicTest
    public static void thermalShockBurstsBurningTargets(GameTestHelper h) {
        var p = RecipeGameTests.caster(h);
        var pig = h.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(3, 2, 3));
        MagicStatuses.apply(pig, MagicStatuses.Status.BURNING, 100, 1);
        EffectGraph.entity(p, pig, RecipeRegistry.fusion(of(1,0,1,0,0,0,0,0)), List.of(node("thermal_shock", 4)), p.position(), false);
        h.assertTrue(pig.getHealth() <= pig.getMaxHealth() - 3, "A burning target bursts, health " + pig.getHealth());
        h.assertTrue(MagicStatuses.has(pig, MagicStatuses.Status.BRITTLE) && !MagicStatuses.has(pig, MagicStatuses.Status.BURNING), "The shock leaves it brittle and puts the fire out");
        h.succeed();
    }
    @MagicTest
    public static void genericSelfReactionsRemainAvailable(GameTestHelper h) {
        var steam = RecipeRegistry.fusion(of(1,1,0,0,0,0,0,0));
        var ice = RecipeRegistry.fusion(of(0,1,1,0,0,0,0,0));
        var earth = RecipeRegistry.fusion(of(0,0,0,0,1,0,0,0));
        var shield = RecipeRegistry.fusion(of(0,0,0,0,1,0,1,0));
        var namedShield = RecipeRegistry.resolve(of(0,0,0,0,1,0,1,0));
        h.assertTrue(steam.selfEffects().stream().anyMatch(e -> e.get("type").getAsString().equals("thermal_shock")), "Fire and water keep the steam reaction");
        h.assertTrue(ice.selfEffects().stream().anyMatch(e -> e.get("type").getAsString().equals("apply_status") && e.get("status").getAsString().equals("chilled")), "Water and cold keep the ice self effect");
        h.assertTrue(earth.selfEffects().stream().anyMatch(e -> e.get("type").getAsString().equals("apply_status") && e.get("status").getAsString().equals("stone_shell")), "Earth keeps the shield self effect");
        h.assertTrue(shield.name().equals("Shield") && shield.tags().contains("DEF"), "Earth and Arcane keep the generic shield");
        h.assertTrue(namedShield.name().equals("Shield") && !namedShield.ordered(), "Earth and Arcane no longer resolve to Crystal");
        h.succeed();
    }
    @MagicTest
    public static void rebirthRaisesTheSlainForTheCaster(GameTestHelper h) {
        var p = RecipeGameTests.caster(h);
        var zombie = h.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(3, 2, 3));
        EffectGraph.entity(p, zombie, RecipeRegistry.fusion(of(0,0,0,0,0,1,0,1)), List.of(node("rebirth", 0)), p.position(), false);
        zombie.hurtServer(h.getLevel(), p.damageSources().magic(), 1000);
        h.succeedWhen(() -> h.assertTrue(!h.getLevel().getEntitiesOfClass(Mob.class, zombie.getBoundingBox().inflate(4), e -> e != zombie && e.isAlive() && UndeadMinions.ownedBy(e, p)).isEmpty(), "The slain zombie rises for the caster"));
    }
}
