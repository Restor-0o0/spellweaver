package com.restor.magicka;

import com.restor.magicka.enemy.Arena;
import net.minecraft.gametest.framework.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
import java.util.ArrayList;
import java.util.List;

@GameTestHolder(Magicka.ID)
@PrefixGameTestTemplate(false)
public final class ModeGameTests {
    private static <T extends Mob> T spawn(GameTestHelper h, EntityType<T> type, double x, double z) {
        T mob = h.spawn(type, new Vec3(x, 2, z));
        mob.setNoAi(true);
        return mob;
    }
    private static SpellDefinition fusion(Element... elements) {
        Formula f = new Formula();
        for (Element e : elements) f.add(e);
        return RecipeRegistry.fusion(f.key());
    }

    @GameTest(template="empty")
    public static void fireWallBurnsAlongTheRow(GameTestHelper h) {
        DeathGameTests.floor(h);
        var p = RecipeGameTests.caster(h);
        var left = spawn(h, EntityType.ZOMBIE, 1.3, 5.5);
        var right = spawn(h, EntityType.ZOMBIE, 7.7, 5.5);
        float l = left.getHealth(), r = right.getHealth();
        SpellRuntime.row(p, RecipeGameTests.spell("Fire Wall"), h.absoluteVec(new Vec3(4.5, 2, 5.5)), false);
        h.succeedWhen(() -> Check.assertTrue(h,left.getHealth() < l && right.getHealth() < r, "Both ends of the fire wall burn"));
    }

    @GameTest(template="empty")
    public static void reflectionTurnsShotsBack(GameTestHelper h) {
        DeathGameTests.floor(h);
        var p = RecipeGameTests.caster(h);
        var skeleton = spawn(h, EntityType.SKELETON, 1.5, 7.5);
        var arrow = new net.minecraft.world.entity.projectile.Arrow(EntityType.ARROW, h.getLevel());
        arrow.setOwner(skeleton);
        arrow.setPos(p.getX(), p.getY() + 1, p.getZ() + 2.5);
        arrow.setNoGravity(true);
        h.getLevel().addFreshEntity(arrow);
        var s = RecipeGameTests.spell("Reflection");
        SpellRuntime.zone(p, s, p.position(), 100, s.number("radius", 2.5), false, false);
        h.runAtTickTime(1, () -> arrow.setDeltaMovement(0, 0, -.3));
        h.succeedWhen(() -> Check.assertTrue(h,arrow.getOwner() == p && arrow.getDeltaMovement().z > 0, "The shell sends the arrow back as the caster's"));
    }

    @GameTest(template="empty")
    public static void areaNovaSparesTheCaster(GameTestHelper h) {
        DeathGameTests.floor(h);
        var p = RecipeGameTests.caster(h);
        var zombie = spawn(h, EntityType.ZOMBIE, 3.5, 1.5);
        float before = zombie.getHealth(), self = p.getHealth();
        CastModes.area(p, fusion(Element.FIRE, Element.FIRE));
        Check.assertTrue(h,zombie.getHealth() < before, "The nova burns the zombie");
        Check.assertTrue(h,p.getHealth() >= self, "The nova spares its caster");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void shieldDomePushesMobsOut(GameTestHelper h) {
        DeathGameTests.floor(h);
        var p = RecipeGameTests.caster(h);
        var pig = h.spawn(EntityType.PIG, new Vec3(2.3, 2, 2.3));
        CastModes.area(p, fusion(Element.SHIELD));
        var dome = CastModes.dome(p);
        Check.assertTrue(h,dome != null && dome.radius > 3, "Shield in an area cast raises a dome");
        h.succeedWhen(() -> {
            double d = pig.position().subtract(dome.center).horizontalDistance();
            Check.assertTrue(h,d > dome.radius - .5, "The dome pushes the pig out, at " + d);
        });
    }

    @GameTest(template="empty")
    public static void shieldSelfCastWards(GameTestHelper h) {
        var p = RecipeGameTests.caster(h);
        var zombie = spawn(h, EntityType.ZOMBIE, 3.5, 1.5);
        CastModes.ward(p, fusion(Element.SHIELD, Element.FIRE).formula(), CastModes.WARD_TICKS);
        Check.assertTrue(h,Math.abs(CastModes.resist(p, net.minecraft.world.damagesource.DamageSource.IN_FIRE) - .5f) < 1e-4, "A fire ward halves fire");
        Check.assertTrue(h,CastModes.resist(p, net.minecraft.world.damagesource.DamageSource.mobAttack(zombie)) == 0, "A fire ward ignores blows");
        CastModes.ward(p, fusion(Element.SHIELD, Element.SHIELD).formula(), CastModes.WARD_TICKS);
        Check.assertTrue(h,Math.abs(CastModes.resist(p, net.minecraft.world.damagesource.DamageSource.mobAttack(zombie)) - .6f) < 1e-4, "A bare Shield ward blunts blows");
        Check.assertTrue(h,CastModes.resist(p, net.minecraft.world.damagesource.DamageSource.IN_FIRE) == 0, "A bare Shield ward ignores fire");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void imbuedWeaponAddsTheSpell(GameTestHelper h) {
        DeathGameTests.floor(h);
        var p = RecipeGameTests.caster(h);
        var zombie = spawn(h, EntityType.ZOMBIE, 1.5, 2.5);
        CastModes.imbue(p, fusion(Element.LIGHTNING, Element.LIGHTNING));
        int hits = CastModes.imbued(p).hits();
        float before = zombie.getHealth();
        p.attack(zombie);
        Check.assertTrue(h,before - zombie.getHealth() > 2, "The imbued blow carries the spell, took " + (before - zombie.getHealth()));
        Check.assertTrue(h,CastModes.imbued(p).hits() == hits - 1, "Each blow spends a charge");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void oppositesCancelButNamedSpellsStayTypable(GameTestHelper h) {
        for (var d : RecipeRegistry.recipes()) {
            if (!d.ordered()) continue;
            Check.assertTrue(h,d.keys().stream().allMatch(Element::base), d.name() + " is typed with base keys");
            Check.assertTrue(h,Reactions.type(d.keys(), true).elements().equals(d.spoken()), d.name() + " stays typable");
        }
        boolean cancelled = false;
        for (Element a : Element.BASE) for (Element b : Element.BASE) {
            if (!Reactions.opposed(a, b) || cancelled) continue;
            for (int n = 1; n <= 3 && !cancelled; n++) {
                List<Element> queue = new ArrayList<>();
                for (int i = 0; i < n; i++) queue.add(a);
                if (Reactions.guarded(queue, b)) continue;
                Formula f = new Formula();
                queue.forEach(f::add);
                Check.assertTrue(h,Reactions.apply(f, new StringBuilder(), b, true).kind() == Reactions.Kind.CANCEL && f.size() == n - 1, "An opposite removes one queued element");
                cancelled = true;
            }
        }
        Check.assertTrue(h,cancelled, "Some opposite pair cancels");
        Check.assertFalse(h,Reactions.opposed(Element.ARCANE, Element.FIRE), "Arcane has no opposite");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void waterFusesAndHeatBreaksItBack(GameTestHelper h) {
        Check.assertTrue(h,Reactions.type(List.of(Element.WATER, Element.FIRE), true).elements().equals(List.of(Element.STEAM)), "Water and Fire make Steam");
        Check.assertTrue(h,Reactions.type(List.of(Element.COLD, Element.WATER), true).elements().equals(List.of(Element.ICE)), "Cold and Water make Ice");
        Check.assertTrue(h,Reactions.type(List.of(Element.EARTH, Element.WATER, Element.LIFE, Element.FIRE), true).elements().equals(List.of(Element.EARTH, Element.WATER, Element.LIFE, Element.FIRE)), "Fusion never reaches past other elements");
        Check.assertTrue(h,Reactions.type(List.of(Element.EARTH, Element.ARCANE), true).elements().equals(List.of(Element.SHIELD)), "Earth and Arcane typed together make Shield");
        Check.assertTrue(h,Reactions.type(List.of(Element.ARCANE, Element.FIRE, Element.EARTH), true).elements().equals(List.of(Element.ARCANE, Element.FIRE, Element.EARTH)), "Earth and Arcane typed apart stay themselves");
        var melted = Reactions.type(List.of(Element.ARCANE, Element.COLD, Element.WATER, Element.FIRE), true).elements();
        Check.assertTrue(h,melted.equals(List.of(Element.ARCANE, Element.WATER)) || Reactions.guarded(List.of(Element.ARCANE, Element.ICE), Element.FIRE), "Fire melts Ice back into Water: " + melted);
        var steam = Reactions.fused(List.of(Element.WATER, Element.FIRE));
        Check.assertTrue(h,Reactions.react(steam, Element.COLD, true).kind() != Reactions.Kind.NONE || Reactions.guarded(steam, Element.COLD), "Cold condenses Steam");
        var f = SpellFormula.of(Reactions.type(List.of(Element.WATER, Element.FIRE, Element.WATER, Element.COLD), true));
        Check.assertTrue(h,f.steam() == 1 && f.ice() == 1 && f.total(Element.WATER) == 2 && f.size() == 2, "Each derived element holds both parents in one slot");
        var fusion = RecipeRegistry.fusion(Reactions.type(List.of(Element.WATER, Element.FIRE, Element.WATER, Element.FIRE, Element.EARTH), true).key());
        Check.assertTrue(h,fusion.formula().steam() == 2, "Fusions keep Steam in their formula");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void arenaWavesGrow(GameTestHelper h) {
        RandomSource random = RandomSource.create(7);
        for (int n = 1; n <= 12; n++) {
            int wave = n;
            List<String> mobs = Arena.wave(n, random);
            int cost = 0;
            for (String id : mobs) {
                var entry = Arena.ROSTER.stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow();
                Check.assertTrue(h,entry.from() <= wave || wave % 5 == 0, id + " is not due on wave " + wave);
                cost += entry.cost();
            }
            Check.assertTrue(h,cost <= Arena.budget(n) && mobs.size() <= Arena.CAP && !mobs.isEmpty(), "Wave " + n + " fits its budget");
        }
        Check.assertTrue(h,Arena.wave(5, random).contains("armored_knight"), "Wave 5 brings a knight");
        Check.assertTrue(h,Arena.wave(10, random).contains("troll"), "Wave 10 brings a troll");
        Check.assertTrue(h,Arena.wave(1, random).stream().allMatch(id -> Arena.ROSTER.stream().anyMatch(e -> e.id().equals(id) && e.cost() <= 2)), "Wave 1 is cheap");
        h.succeed();
    }
}
