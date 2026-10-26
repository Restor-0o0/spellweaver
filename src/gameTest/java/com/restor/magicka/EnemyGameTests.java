package com.restor.magicka;

import com.restor.magicka.enemy.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
import java.util.function.Predicate;

@GameTestHolder(Magicka.ID)
@PrefixGameTestTemplate(false)
public final class EnemyGameTests {
    private static SpellDefinition find(Predicate<SpellDefinition> test) { return RecipeRegistry.recipes().stream().filter(test).findFirst().orElseThrow(); }
    private static <T extends SpecialEnemy> T mob(GameTestHelper h, EntityType<T> type, double x, double z, float yaw) {
        T mob = h.spawn(type, new Vec3(x, 2, z));
        mob.setNoAi(true); mob.setYRot(yaw); mob.yBodyRot = yaw; mob.yHeadRot = yaw;
        return mob;
    }
    private static float hit(SpecialEnemy mob, ServerPlayer caster, SpellDefinition spell, float damage, boolean brittle) {
        float before = mob.getHealth();
        MagicEnemies.hurt(mob, net.minecraft.world.damagesource.DamageSource.MAGIC, damage, caster, spell, brittle);
        return before - mob.getHealth();
    }

    @GameTest(template="empty")
    public static void elementLockRejectsInput(GameTestHelper h) {
        DeathGameTests.floor(h);
        ServerPlayer p = RecipeGameTests.caster(h);
        CastingServer.input(p, 7);
        ElementLocks.lock(p, 1 << Element.FIRE.ordinal(), 100);
        Check.assertTrue(h,ElementLocks.locked(p, Element.FIRE) && !ElementLocks.locked(p, Element.WATER), "Only the bound element is locked");
        CastingServer.input(p, Element.FIRE.ordinal());
        Check.assertTrue(h,CastingServer.prepared(p).count(Element.FIRE) == 0, "A locked element cannot be typed");
        CastingServer.input(p, Element.WATER.ordinal());
        Check.assertTrue(h,CastingServer.prepared(p).count(Element.WATER) == 1, "Other elements still work");
        ElementLocks.clear(p);
        CastingServer.input(p, Element.FIRE.ordinal());
        Check.assertTrue(h,CastingServer.prepared(p).count(Element.FIRE) == 1, "Clearing the lock restores the element");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void beetleShieldFacesFront(GameTestHelper h) {
        DeathGameTests.floor(h);
        ServerPlayer p = RecipeGameTests.caster(h);
        SpellDefinition bolt = find(d -> d.shape() == SpellShape.PROJECTILE);
        ShieldBeetle front = mob(h, MagicEnemies.SHIELD_BEETLE.get(), 1.5, 3.5, 180);
        float blocked = hit(front, p, bolt, 10, false);
        ShieldBeetle rear = mob(h, MagicEnemies.SHIELD_BEETLE.get(), 3.5, 3.5, 0);
        p.setPos(h.absoluteVec(new Vec3(3.5, 2, 1.5)));
        float exposed = hit(rear, p, bolt, 10, false);
        Check.assertTrue(h,blocked < 3, "Front shield absorbs projectiles, took " + blocked);
        Check.assertTrue(h,exposed > 12, "The rear takes bonus damage, took " + exposed);
        h.succeed();
    }

    @GameTest(template="empty")
    public static void crawlerShattersOnBrittleEarth(GameTestHelper h) {
        DeathGameTests.floor(h);
        ServerPlayer p = RecipeGameTests.caster(h);
        StoneCrawler crawler = mob(h, MagicEnemies.STONE_CRAWLER.get(), 2.5, 3.5, 0);
        SpellDefinition fire = find(d -> d.formula().count(Element.FIRE) > 0 && d.formula().count(Element.EARTH) == 0);
        SpellDefinition earth = find(d -> d.formula().count(Element.EARTH) > 0);
        Check.assertTrue(h,hit(crawler, p, fire, 10, false) <= 4.01f, "Armor soaks plain spells");
        Check.assertTrue(h,crawler.armored(), "Armor holds without a brittle Earth hit");
        crawler.invulnerableTime = 0;
        hit(crawler, p, earth, 4, true);
        Check.assertFalse(h,crawler.armored(), "Brittle Earth shatters the armor");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void countermageWardsDominantElement(GameTestHelper h) {
        DeathGameTests.floor(h);
        ServerPlayer p = RecipeGameTests.caster(h);
        Countermage mage = mob(h, MagicEnemies.COUNTERMAGE.get(), 2.5, 3.5, 0);
        ShieldBeetle guard = mob(h, MagicEnemies.SHIELD_BEETLE.get(), 1.5, 3.5, 0);
        SpellDefinition fire = find(d -> d.formula().size() >= 2 && d.formula().count(Element.FIRE) == d.formula().size());
        SpellDefinition water = find(d -> d.formula().size() > 0 && d.formula().count(Element.WATER) == d.formula().size());
        for (int i = 0; i < 6; i++) { guard.invulnerableTime = 0; MagicEnemies.hurt(guard, net.minecraft.world.damagesource.DamageSource.MAGIC, .1f, p, fire, false); }
        Check.assertTrue(h,mage.weight(Element.FIRE) >= Countermage.THRESHOLD, "Spells on allies are observed");
        mage.raiseWard(Element.FIRE);
        Check.assertTrue(h,mage.warded() == Element.FIRE, "Ward takes the dominant element");
        Check.assertTrue(h,mage.shield(guard, fire) < .5f, "Allies in the aura resist the warded element");
        Check.assertTrue(h,mage.shield(guard, water) == 1, "Other elements pass through");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void encounterSpawnsOnCaveFloor(GameTestHelper h) {
        for (int x = -1; x <= 6; x++) for (int z = -1; z <= 6; z++) {
            h.setBlock(new net.minecraft.core.BlockPos(x, 1, z), net.minecraft.world.level.block.Blocks.STONE);
            h.setBlock(new net.minecraft.core.BlockPos(x, 5, z), net.minecraft.world.level.block.Blocks.STONE);
        }
        var mobs = MagicEnemies.encounter(h.getLevel(), h.absoluteVec(new Vec3(2.5, 2, 2.5)), "d");
        int floor = h.absolutePos(new net.minecraft.core.BlockPos(0, 2, 0)).getY();
        boolean grounded = mobs.stream().allMatch(m -> m.getBlockY() == floor);
        mobs.forEach(m -> m.discard());
        Check.assertTrue(h,mobs.size() == 3, "Encounter D spawns two crawlers and a burrower");
        Check.assertTrue(h,grounded, "Encounter mobs stand on the cave floor, not on the roof");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void goblinWarbandSpawns(GameTestHelper h) {
        DeathGameTests.floor(h);
        var mobs = MagicEnemies.encounter(h.getLevel(), h.absoluteVec(new Vec3(2.5, 2, 2.5)), "g");
        long goblins = mobs.stream().filter(m -> m instanceof Goblin || m instanceof Countermage).count();
        mobs.forEach(m -> m.discard());
        Check.assertTrue(h,mobs.size() == 7 && goblins == 7, "Encounter G spawns a goblin warband of seven, got " + mobs.size());
        h.succeed();
    }

    @GameTest(template="empty")
    public static void goblinBombExplodesButWaterDousesIt(GameTestHelper h) {
        DeathGameTests.floor(h);
        GoblinBomber bomber = mob(h, MagicEnemies.GOBLIN_BOMBER.get(), 5.5, 5.5, 0);
        GoblinWarrior dry = mob(h, MagicEnemies.GOBLIN_WARRIOR.get(), 1.5, 2, 0);
        GoblinWarrior wet = mob(h, MagicEnemies.GOBLIN_WARRIOR.get(), 4.5, 2, 0);
        h.setBlock(new net.minecraft.core.BlockPos(4, 2, 1), net.minecraft.world.level.block.Blocks.WATER);
        GoblinBomb.create(bomber, h.absoluteVec(new Vec3(1.5, 2.1, 1)), 5);
        GoblinBomb doused = GoblinBomb.create(bomber, h.absoluteVec(new Vec3(4.5, 2.1, 1.5)), 5);
        h.runAfterDelay(10, () -> {
            Check.assertTrue(h,dry.getHealth() < dry.getMaxHealth(), "A lit bomb blasts everyone nearby, goblins included");
            Check.assertTrue(h,doused.isRemoved() && wet.getHealth() == wet.getMaxHealth(), "Water puts the fuse out");
            h.succeed();
        });
    }

    @GameTest(template="empty")
    public static void shamanRalliesOnlyGoblins(GameTestHelper h) {
        DeathGameTests.floor(h);
        GoblinShaman shaman = mob(h, MagicEnemies.GOBLIN_SHAMAN.get(), 2.5, 2.5, 0);
        GoblinWarrior warrior = mob(h, MagicEnemies.GOBLIN_WARRIOR.get(), 3.5, 2.5, 0);
        ShieldBeetle beetle = mob(h, MagicEnemies.SHIELD_BEETLE.get(), 1.5, 4.5, 0);
        shaman.rally();
        Check.assertTrue(h,warrior.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST), "Goblins get the war chant");
        Check.assertFalse(h,beetle.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST), "Other monsters do not");
        var mobs = MagicEnemies.encounter(h.getLevel(), h.absoluteVec(new Vec3(2.5, 2, 2.5)), "h");
        boolean kin = mobs.stream().allMatch(Goblin::kin);
        mobs.forEach(m -> m.discard());
        Check.assertTrue(h,mobs.size() == 8 && kin, "Encounter H spawns eight goblins, got " + mobs.size());
        h.succeed();
    }

    @GameTest(template="empty")
    public static void stoneEaterOnlyEatsMagic(GameTestHelper h) {
        Check.assertFalse(h,StoneEater.edible(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()), "Natural stone is not food");
        Check.assertFalse(h,StoneEater.edible(MagicBlocks.PUDDLE.get().defaultBlockState()), "Puddles are not food");
        h.succeed();
    }
}
