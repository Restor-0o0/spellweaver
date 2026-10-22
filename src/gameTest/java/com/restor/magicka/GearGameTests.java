package com.restor.magicka;

import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.*;

@GameTestHolder(Magicka.ID)
@PrefixGameTestTemplate(false)
public final class GearGameTests {
    private static void equip(ServerPlayer player, MagicGear.Slot slot, net.minecraftforge.registries.RegistryObject<GearItem> item) {
        MagicGear.Loadout loadout = new MagicGear.Loadout(player);
        loadout.setItem(slot.ordinal(), new ItemStack(item.get()));
    }

    @GameTest(template="empty")
    public static void gearStatsAndAttributes(GameTestHelper h) {
        DeathGameTests.floor(h);
        ServerPlayer p = RecipeGameTests.caster(h);
        int[] before = MagicGear.total(p);
        equip(p, MagicGear.Slot.FOCUS, MagicItems.STONE_GAUNTLET);
        equip(p, MagicGear.Slot.TRINKET_1, MagicItems.GLASS_HEART);
        int[] after = MagicGear.total(p);
        h.assertTrue(after[MagicStats.Stat.EARTH.ordinal()] > before[MagicStats.Stat.EARTH.ordinal()], "Focus raises its element");
        h.assertTrue(after[MagicStats.Stat.DAMAGE.ordinal()] > before[MagicStats.Stat.DAMAGE.ordinal()], "Trinket raises damage");
        h.assertTrue(MagicGear.has(p, MagicGear.Trait.GLASS_HEART), "Equipped traits are active");
        h.assertTrue(p.getMaxHealth() < 20, "Glass Heart cuts maximum health");
        h.assertTrue(MagicGear.equipped(p).size() == 2, "Both pieces are equipped");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void gearPatchesRecipe(GameTestHelper h) {
        DeathGameTests.floor(h);
        ServerPlayer p = RecipeGameTests.caster(h);
        SpellDefinition base = RecipeGameTests.spell("Stone Platform");
        h.assertFalse(base.floating(), "Stone Platform needs support by default");
        equip(p, MagicGear.Slot.RING_1, MagicItems.RING_OF_SUSPENSION);
        h.assertTrue(MagicGear.patch(p, base).floating(), "Ring of Suspension makes the platform float");
        h.assertTrue(MagicGear.patch(p, RecipeGameTests.spell("Firebomb")) == RecipeGameTests.spell("Firebomb"), "Untouched recipes are not copied");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void gearSlotsRejectWrongKind(GameTestHelper h) {
        ItemStack ring = new ItemStack(MagicItems.CONDUCTOR_RING.get()), trinket = new ItemStack(MagicItems.GLASS_HEART.get());
        h.assertFalse(MagicGear.Slot.FOCUS.accepts(ring), "A ring is not a focus");
        h.assertTrue(MagicGear.Slot.RING_1.accepts(ring), "Rings fit ring slots");
        h.assertTrue(MagicGear.Slot.TRINKET_1.accepts(ring), "Rings also fit trinket slots");
        h.assertFalse(MagicGear.Slot.RING_2.accepts(trinket), "Trinkets do not fit ring slots");
        h.assertFalse(MagicGear.Slot.TOOL.accepts(new ItemStack(MagicItems.SPELLBOOK.get())), "Only gear fits gear slots");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void gearCastBiasAndPersistence(GameTestHelper h) {
        DeathGameTests.floor(h);
        ServerPlayer p = RecipeGameTests.caster(h);
        h.assertTrue(MagicGear.castBias(p, 1) == 1 && MagicGear.castBias(p, 5) == 1, "Bare hands cast at base speed");
        equip(p, MagicGear.Slot.FOCUS, MagicItems.WAND);
        h.assertTrue(MagicGear.castBias(p, 1) < 1 && MagicGear.castBias(p, 5) > 1, "The wand rushes short formulas and drags long ones");
        h.assertTrue(MagicGear.item(p, MagicGear.Slot.FOCUS).getItem() == MagicItems.WAND.get(), "Gear is stored on the player");
        h.assertTrue(new MagicGear.Loadout(p).getItem(0).getItem() == MagicItems.WAND.get(), "Gear reloads into a fresh loadout");
        h.succeed();
    }
}
