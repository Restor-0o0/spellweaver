package com.restor.magicka;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import java.util.List;

@GameTestHolder(Magicka.ID)
@PrefixGameTestTemplate(false)
public final class LegendGameTests {
    private static final List<String> LEGENDS = List.of("Field of Flowers", "Zoltraak", "Kamehameha", "Rasengan", "Chidori", "Expelliarmus",
            "Expecto Patronum", "Fus Ro Dah", "Explosion", "Za Warudo", "You Shall Not Pass", "Defibrillator", "Life Drain", "Purifying Flame",
            "Cryosleep", "Phoenix Tears", "Kaioken", "Protego Maxima", "Zombie Cure", "Wellspring", "Amaterasu", "Mjolnir", "Accio", "Infinity",
            "Scale of Obedience", "Judradjim", "Igni", "Aard", "Quen", "Axii", "Yrden", "Sling Ring Portal");

    private static Zombie dummy(GameTestHelper h, Vec3 at) {
        Zombie zombie = h.spawn(EntityType.ZOMBIE, at);
        zombie.setNoAi(true);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        return zombie;
    }

    @GameTest(template="empty")
    public static void legendsAreInstantSignatures(GameTestHelper h) {
        for (String name : LEGENDS) {
            var s = RecipeGameTests.spell(name);
            h.assertTrue(s.tier().equals("Signature") && !s.channeled() && !s.epic() && s.delivery().has("legend"), name + " is an instant signature legend");
        }
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=100)
    public static void flowerFieldBlooms(GameTestHelper h) {
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
        var p = RecipeGameTests.caster(h);
        p.setHealth(10);
        SpellRuntime.cast(p, RecipeGameTests.spell("Field of Flowers"), true);
        h.succeedWhen(() -> {
            int flowers = 0;
            for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) if (h.getBlockState(new BlockPos(x, 2, z)).is(BlockTags.SMALL_FLOWERS)) flowers++;
            h.assertTrue(flowers >= 3, "Flowers bloom around the caster, got " + flowers);
            h.assertTrue(p.getHealth() > 10, "The field heals the caster");
        });
    }

    @GameTest(template="empty", timeoutTicks=100)
    public static void expelliarmusDisarms(GameTestHelper h) {
        DeathGameTests.floor(h);
        var p = RecipeGameTests.caster(h);
        var zombie = dummy(h, new Vec3(1.5, 2, 4.5));
        zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        SpellRuntime.cast(p, RecipeGameTests.spell("Expelliarmus"), false);
        h.succeedWhen(() -> {
            h.assertTrue(zombie.getMainHandItem().isEmpty(), "The zombie loses its sword");
            var dropped = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(BlockPos.ZERO)).inflate(8), i -> i.getItem().is(Items.IRON_SWORD));
            h.assertTrue(!dropped.isEmpty(), "The sword flies out");
            dropped.forEach(ItemEntity::discard);
        });
    }

    @GameTest(template="empty", timeoutTicks=200, batch="legend_time")
    public static void zaWarudoStopsAndResumesTime(GameTestHelper h) {
        DeathGameTests.floor(h);
        var p = RecipeGameTests.caster(h);
        Zombie zombie = h.spawn(EntityType.ZOMBIE, new Vec3(3.5, 2, 3.5));
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        SpellRuntime.cast(p, RecipeGameTests.spell("Za Warudo"), false);
        h.runAfterDelay(5, () -> h.assertTrue(zombie.isNoAi() && zombie.getPersistentData().getBoolean(LegendMagic.STOPPED_TAG), "The zombie freezes"));
        h.runAfterDelay(115, () -> {
            h.assertFalse(zombie.isNoAi() || zombie.getPersistentData().contains(LegendMagic.STOPPED_TAG), "Time resumes for the zombie");
            h.assertTrue(MagicStatuses.has(zombie, MagicStatuses.Status.STUNNED), "The zombie is dazed after time resumes");
            h.succeed();
        });
    }

    @GameTest(template="empty", timeoutTicks=100, batch="legend_shout")
    public static void fusRoDahHitsTheCone(GameTestHelper h) {
        DeathGameTests.floor(h);
        var p = RecipeGameTests.caster(h);
        var ahead = dummy(h, new Vec3(1.5, 2, 4.5));
        var behind = dummy(h, new Vec3(1.5, 2, -2.5));
        SpellRuntime.cast(p, RecipeGameTests.spell("Fus Ro Dah"), false);
        h.succeedWhen(() -> {
            h.assertTrue(ahead.getHealth() < ahead.getMaxHealth(), "The shout hits the zombie ahead");
            h.assertTrue(behind.getHealth() == behind.getMaxHealth(), "The zombie behind is spared");
        });
    }

    @GameTest(template="empty", timeoutTicks=120)
    public static void explosionCostsEveryElement(GameTestHelper h) {
        DeathGameTests.floor(h);
        var p = RecipeGameTests.caster(h);
        var zombie = dummy(h, new Vec3(3.5, 2, 3.5));
        SpellRuntime.cast(p, RecipeGameTests.spell("Explosion"), true);
        h.runAfterDelay(3, () -> h.assertTrue(zombie.getHealth() == zombie.getMaxHealth() && ElementLocks.active(p) == 0, "The blast waits for the chant"));
        h.succeedWhen(() -> {
            h.assertTrue(zombie.getHealth() < zombie.getMaxHealth() || !zombie.isAlive(), "The blast hits the zombie");
            for (Element e : Element.values()) h.assertTrue(ElementLocks.locked(p, e), e + " is spent");
            ElementLocks.clear(p);
        });
    }

    @GameTest(template="empty", timeoutTicks=60, batch="legend_life")
    public static void defibrillatorRestartsTheCaster(GameTestHelper h) {
        var p = RecipeGameTests.caster(h);
        p.setHealth(4);
        SpellRuntime.cast(p, RecipeGameTests.spell("Defibrillator"), true);
        h.succeedWhen(() -> h.assertTrue(p.getHealth() >= 14, "A critical jolt heals twice, got " + p.getHealth()));
    }

    @GameTest(template="empty", timeoutTicks=100, batch="legend_life")
    public static void lifeDrainStealsHealth(GameTestHelper h) {
        DeathGameTests.floor(h);
        var p = RecipeGameTests.caster(h);
        p.setHealth(10);
        var zombie = dummy(h, new Vec3(1.5, 2, 4.5));
        SpellRuntime.cast(p, RecipeGameTests.spell("Life Drain"), false);
        h.succeedWhen(() -> {
            h.assertTrue(zombie.getHealth() < zombie.getMaxHealth(), "The zombie is drained");
            h.assertTrue(p.getHealth() > 10, "The caster drinks the life");
        });
    }

    @GameTest(template="empty", timeoutTicks=60, batch="legend_life")
    public static void zombieCureRestoresVillagers(GameTestHelper h) {
        DeathGameTests.floor(h);
        var p = RecipeGameTests.caster(h);
        ZombieVillager zv = h.spawn(EntityType.ZOMBIE_VILLAGER, new Vec3(2.5, 2, 2.5));
        zv.setNoAi(true);
        SpellRuntime.cast(p, RecipeGameTests.spell("Zombie Cure"), true);
        h.succeedWhen(() -> {
            h.assertFalse(zv.isAlive(), "The zombie villager is gone");
            h.assertEntityPresent(EntityType.VILLAGER);
        });
    }

    @GameTest(template="empty", timeoutTicks=60, batch="legend_life")
    public static void phoenixTearsCheatDeath(GameTestHelper h) {
        var p = RecipeGameTests.caster(h);
        var zombie = dummy(h, new Vec3(3.5, 2, 3.5));
        SpellRuntime.cast(p, RecipeGameTests.spell("Phoenix Tears"), true);
        zombie.getPersistentData().putLong(LegendLife.PHOENIX_TAG, h.getLevel().getGameTime() + 100);
        zombie.hurt(h.getLevel().damageSources().magic(), 1000);
        h.succeedWhen(() -> {
            h.assertTrue(zombie.isAlive() && zombie.getHealth() >= zombie.getMaxHealth() * .4f, "The marked zombie rises from the ashes");
            h.assertTrue(p.getPersistentData().contains(LegendLife.PHOENIX_TAG), "The caster carries the phoenix mark");
        });
    }

    @GameTest(template="empty", timeoutTicks=60, batch="legend_life")
    public static void quenShieldsTheCaster(GameTestHelper h) {
        var p = RecipeGameTests.caster(h);
        SpellRuntime.cast(p, RecipeGameTests.spell("Quen"), false);
        h.succeedWhen(() -> h.assertTrue(p.hasEffect(MobEffects.ABSORPTION) && p.getAbsorptionAmount() > 0, "Quen grants absorption"));
    }

    @GameTest(template="empty", timeoutTicks=60, batch="legend_life")
    public static void kaiokenPowersUp(GameTestHelper h) {
        var p = RecipeGameTests.caster(h);
        SpellRuntime.cast(p, RecipeGameTests.spell("Kaioken"), false);
        h.succeedWhen(() -> h.assertTrue(p.hasEffect(MobEffects.DAMAGE_BOOST) && p.hasEffect(MobEffects.JUMP), "Kaioken grants strength and jump"));
    }

    @GameTest(template="empty", timeoutTicks=100, batch="legend_cone")
    public static void igniBurnsTheCone(GameTestHelper h) {
        DeathGameTests.floor(h);
        var p = RecipeGameTests.caster(h);
        var ahead = dummy(h, new Vec3(1.5, 2, 4.5));
        var behind = dummy(h, new Vec3(1.5, 2, -2.5));
        SpellRuntime.cast(p, RecipeGameTests.spell("Igni"), false);
        h.succeedWhen(() -> {
            h.assertTrue(ahead.getHealth() < ahead.getMaxHealth() && ahead.isOnFire(), "Igni burns the zombie ahead");
            h.assertTrue(behind.getHealth() == behind.getMaxHealth(), "The zombie behind is spared");
        });
    }

    @GameTest(template="empty", timeoutTicks=100, batch="legend_arts")
    public static void judradjimStrikesAfterTheCloud(GameTestHelper h) {
        DeathGameTests.floor(h);
        var p = RecipeGameTests.caster(h);
        var zombie = dummy(h, new Vec3(3.5, 2, 3.5));
        SpellRuntime.cast(p, RecipeGameTests.spell("Judradjim"), true);
        h.runAfterDelay(10, () -> h.assertTrue(zombie.getHealth() == zombie.getMaxHealth(), "The cloud gathers first"));
        h.succeedWhen(() -> h.assertTrue(zombie.getHealth() < zombie.getMaxHealth(), "The thunder strikes the zombie"));
    }

    @GameTest(template="empty", timeoutTicks=60, batch="legend_arts")
    public static void accioSummonsItems(GameTestHelper h) {
        DeathGameTests.floor(h);
        var p = RecipeGameTests.caster(h);
        ItemEntity item = new ItemEntity(h.getLevel(), h.absoluteVec(new Vec3(3.5, 2, 3.5)).x, h.absoluteVec(new Vec3(3.5, 2, 3.5)).y, h.absoluteVec(new Vec3(3.5, 2, 3.5)).z, new ItemStack(Items.DIAMOND));
        item.setDeltaMovement(Vec3.ZERO);
        h.getLevel().addFreshEntity(item);
        SpellRuntime.cast(p, RecipeGameTests.spell("Accio"), true);
        h.succeedWhen(() -> {
            h.assertTrue(item.getDeltaMovement().horizontalDistanceSqr() > 0 || item.distanceToSqr(p) < 4, "The diamond flies to the caster");
            item.discard();
        });
    }
}
