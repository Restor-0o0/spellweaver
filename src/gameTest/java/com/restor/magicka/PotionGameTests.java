package com.restor.magicka;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraftforge.event.TickEvent;

@net.minecraftforge.gametest.GameTestHolder(Magicka.ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class PotionGameTests {
    static net.minecraft.world.item.ItemStack potion(String name) {
        return MagicPotions.stack(Items.POTION, MagicPotions.ALL.get(name).get());
    }

    private static net.minecraft.world.item.ItemStack brew(GameTestHelper h, net.minecraft.world.item.Item reagent, net.minecraft.world.item.ItemStack potion) {
        return net.minecraftforge.common.brewing.BrewingRecipeRegistry.getOutput(potion, new net.minecraft.world.item.ItemStack(reagent));
    }

    @GameTest(template = "empty")
    public static void wardReducesItsElement(GameTestHelper h) {
        DeathGameTests.floor(h);
        var warded = h.spawn(EntityType.PIG, new BlockPos(1, 2, 1));
        var plain = h.spawn(EntityType.PIG, new BlockPos(3, 2, 1));
        var other = h.spawn(EntityType.PIG, new BlockPos(1, 2, 3));
        for (var pig : java.util.List.of(warded, other)) pig.addEffect(new MobEffectInstance(MagicPotions.WARDS.get(Element.ARCANE).get(), 200));
        var level = h.getLevel();
        warded.hurt(level.damageSources().magic(), 4);
        plain.hurt(level.damageSources().magic(), 4);
        h.assertTrue(warded.getHealth() > plain.getHealth(), "Arcane ward softens magic damage");
        float before = other.getHealth();
        other.hurt(level.damageSources().generic(), 2);
        h.assertTrue(Math.abs(before - 2 - other.getHealth()) < .01f, "A ward ignores other elements");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void beltDrinkAppliesPotion(GameTestHelper h) {
        DeathGameTests.floor(h);
        ServerPlayer p = RecipeGameTests.caster(h);
        p.getInventory().clearContent();
        int before = MagicGear.total(p)[MagicStats.Stat.CAST_SPEED.ordinal()];
        MagicGear.setBelt(p, 2, potion("swift_casting"));
        PotionBelt.select(p, 1, 2);
        h.assertTrue(PotionBelt.selected(p, 1) == 2, "Selecting a belt slot is remembered");
        PotionBelt.drink(p, 1);
        h.assertTrue(PotionBelt.drinking(p), "Drinking takes a moment");
        var belt = new PotionBelt();
        for (int i = 0; i < PotionBelt.DRINK_TICKS; i++) belt.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, p));
        h.assertFalse(PotionBelt.drinking(p), "Drinking finishes");
        h.assertTrue(MagicGear.belt(p, 2).isEmpty(), "The potion is used up");
        h.assertTrue(p.hasEffect(MagicPotions.SWIFT_CASTING.get()), "The potion's effect applies");
        h.assertTrue(p.getInventory().countItem(Items.GLASS_BOTTLE) == 1, "The empty bottle returns");
        h.assertTrue(MagicGear.total(p)[MagicStats.Stat.CAST_SPEED.ordinal()] > before, "Swift Casting raises cast speed");
        PotionBelt.drink(p, 1);
        h.assertFalse(PotionBelt.drinking(p), "An empty slot cannot be drunk");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void beltAcceptsOnlyDrinkablePotions(GameTestHelper h) {
        h.assertTrue(PotionBelt.accepts(potion("fire_ward")), "Drinkable potions fit the belt");
        h.assertFalse(PotionBelt.accepts(MagicPotions.stack(Items.SPLASH_POTION, MagicPotions.ALL.get("fire_ward").get())), "Splash potions do not");
        h.assertFalse(PotionBelt.accepts(new net.minecraft.world.item.ItemStack(Items.APPLE)), "Other items do not");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void magicPotionsBrew(GameTestHelper h) {
        var awkward = MagicPotions.stack(Items.POTION, net.minecraft.world.item.alchemy.Potions.AWKWARD);
        var out = brew(h, Items.MAGMA_BLOCK, awkward);
        h.assertTrue(PotionUtils.getPotion(out) == MagicPotions.ALL.get("fire_ward").get(), "Magma brews a fire ward");
        var longer = brew(h, Items.REDSTONE, out);
        h.assertTrue(PotionUtils.getPotion(longer) == MagicPotions.ALL.get("long_fire_ward").get(), "Redstone extends it");
        h.succeed();
    }
}
