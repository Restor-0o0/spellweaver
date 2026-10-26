package com.restor.magicka;

import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder(Magicka.ID)
@PrefixGameTestTemplate(false)
public final class ElementalGameTests {
    private static Zombie dummy(GameTestHelper h, Vec3 at) {
        Zombie zombie = h.spawn(EntityType.ZOMBIE, at);
        zombie.setNoAi(true);
        return zombie;
    }

    @GameTest(template="empty")
    public static void everyElementHasACompanionAndATitan(GameTestHelper h) {
        for (Element e : Element.values()) {
            var companion = RecipeRegistry.recipes().stream().filter(d -> d.id().getPath().equals(Elementals.id(e, "elemental"))).findFirst().orElseThrow();
            var titan = RecipeRegistry.recipes().stream().filter(d -> d.id().getPath().equals(Elementals.id(e, "titan"))).findFirst().orElseThrow();
            Check.assertTrue(h,Elementals.element(companion) == e && companion.tier().equals("Signature") && !companion.epic(), e + " companion is a plain signature");
            Check.assertTrue(h,Elementals.element(titan) == e && titan.epic() && !titan.incantation().isEmpty(), e + " titan is an epic ultimate");
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void companionServesItsCasterOnly(GameTestHelper h) {
        DeathGameTests.floor(h);
        var p = RecipeGameTests.caster(h);
        Elementals.companion(p, RecipeGameTests.spell("Fire Elemental"), h.absoluteVec(new Vec3(3.5, 2, 3.5)));
        var owned = Elementals.companions(p);
        Check.assertTrue(h,owned.size() == 1, "The elemental answers the call");
        var mob = owned.get(0);
        Check.assertTrue(h,mob.element() == Element.FIRE && mob.flyer() && mob.fireImmune(), "A fire elemental flies and does not burn");
        Check.assertTrue(h,mob.isAlliedTo(p) && EffectGraph.ally(p, mob), "The elemental is the caster's ally");
        Check.assertFalse(h,mob.hurt(net.minecraft.world.damagesource.DamageSource.playerAttack(p), 6) || mob.getHealth() < mob.getMaxHealth(), "The caster cannot hurt their own elemental");
        Check.assertFalse(h,mob.shouldBeSaved(), "Elementals are never saved to disk");
        Elementals.companion(p, RecipeGameTests.spell("Frost Elemental"), h.absoluteVec(new Vec3(3.5, 2, 1.5)));
        Elementals.companion(p, RecipeGameTests.spell("Stone Elemental"), h.absoluteVec(new Vec3(1.5, 2, 3.5)));
        owned = Elementals.companions(p);
        Check.assertTrue(h,owned.size() == Elementals.LIMIT && !mob.isAlive(), "A third elemental replaces the oldest");
        Check.assertTrue(h,owned.get(0).element() == Element.COLD && owned.get(1).element() == Element.EARTH, "The newest two stay");
        owned.forEach(Elemental::discard);
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=200)
    public static void companionAttacksMonsters(GameTestHelper h) {
        DeathGameTests.floor(h);
        var p = RecipeGameTests.caster(h);
        var zombie = dummy(h, new Vec3(3.5, 2, 3.5));
        Elementals.companion(p, RecipeGameTests.spell("Storm Elemental"), h.absoluteVec(new Vec3(1.5, 2, 1.5)));
        h.succeedWhen(() -> {
            Check.assertTrue(h,zombie.getHealth() < zombie.getMaxHealth(), "The storm elemental strikes the zombie");
            Elementals.companions(p).forEach(Elemental::discard);
        });
    }

    @GameTest(template="empty", timeoutTicks=240)
    public static void titanFightsOnlyWhileChannelled(GameTestHelper h) {
        DeathGameTests.floor(h);
        var p = RecipeGameTests.caster(h); p.setXRot(60);
        var zombie = dummy(h, new Vec3(1.5, 2, 4.5));
        var channel = EpicMagic.start(p, RecipeGameTests.spell("Stone Titan"));
        var box = new AABB(h.absolutePos(net.minecraft.core.BlockPos.ZERO)).inflate(12);
        var titans = h.getLevel().getEntitiesOfClass(ElementalTitan.class, box);
        Check.assertTrue(h,titans.size() == 1 && titans.get(0).element() == Element.EARTH, "The titan rises at the aimed spot");
        var titan = titans.get(0);
        Check.assertTrue(h,titan.emergence(0) < .2f, "It starts underground");
        h.runAfterDelay(90, () -> {
            Check.assertTrue(h,titan.emergence(0) == 1 && !titan.sinking(), "It stands waist-deep while the channel lives");
            Check.assertTrue(h,zombie.getHealth() < zombie.getMaxHealth(), "It attacks a nearby monster");
            channel.live = false;
        });
        h.runAfterDelay(92, () -> Check.assertTrue(h,titan.sinking(), "A broken channel sends it back underground"));
        h.runAfterDelay(125, () -> {
            Check.assertTrue(h,titan.isRemoved(), "It is gone once it has sunk");
            h.succeed();
        });
    }
}
