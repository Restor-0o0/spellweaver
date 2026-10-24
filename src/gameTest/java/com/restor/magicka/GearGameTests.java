package com.restor.magicka;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.Map;
public final class GearGameTests {
    private static void equip(ServerPlayer player, MagicGear.Slot slot, net.neoforged.neoforge.registries.DeferredItem<GearItem> item) {
        MagicGear.Loadout loadout = new MagicGear.Loadout(player);
        loadout.setItem(slot.ordinal(), new ItemStack(item.get()));
    }

    @MagicTest
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

    @MagicTest
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

    @MagicTest
    public static void gearSlotsRejectWrongKind(GameTestHelper h) {
        ItemStack ring = new ItemStack(MagicItems.CONDUCTOR_RING.get()), trinket = new ItemStack(MagicItems.GLASS_HEART.get());
        h.assertFalse(MagicGear.Slot.FOCUS.accepts(ring), "A ring is not a focus");
        h.assertTrue(MagicGear.Slot.RING_1.accepts(ring), "Rings fit ring slots");
        h.assertTrue(MagicGear.Slot.TRINKET_1.accepts(ring), "Rings also fit trinket slots");
        h.assertFalse(MagicGear.Slot.RING_2.accepts(trinket), "Trinkets do not fit ring slots");
        h.assertFalse(MagicGear.Slot.TOOL.accepts(new ItemStack(MagicItems.SPELLBOOK.get())), "Only gear fits gear slots");
        h.succeed();
    }

    @MagicTest
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

    private static double damage(SpellDefinition spell) {
        double total = 0;
        for (JsonElement e : spell.source().getAsJsonArray("effects")) {
            JsonObject node = e.getAsJsonObject();
            if (EffectGraph.text(node, "type", "").equals("deal_damage") && node.has("amount")) total += node.get("amount").getAsDouble();
        }
        return total;
    }

    private static boolean hasEffect(SpellDefinition spell, String type, String status) {
        JsonArray effects = spell.source().getAsJsonArray("effects");
        for (JsonElement e : effects) {
            JsonObject node = e.getAsJsonObject();
            if (EffectGraph.text(node, "type", "").equals(type) && (status == null || EffectGraph.text(node, "status", "").equals(status))) return true;
        }
        return false;
    }

    @MagicTest
    public static void gearRulesFilterSpells(GameTestHelper h) {
        DeathGameTests.floor(h);
        ServerPlayer p = RecipeGameTests.caster(h);
        SpellDefinition firebolt = RecipeGameTests.spell("Firebolt"), frost = RecipeGameTests.spell("Frost Bolt");
        equip(p, MagicGear.Slot.FOCUS, MagicItems.SMOLDERING_FOCUS);
        equip(p, MagicGear.Slot.RING_1, MagicItems.RIME_BAND);
        h.assertTrue(MagicGear.patch(p, firebolt).delivery().has("hot_zone"), "Fire projectiles leave burning ground");
        h.assertFalse(MagicGear.patch(p, frost).delivery().has("hot_zone"), "Cold projectiles are not set on fire");
        h.assertTrue(hasEffect(MagicGear.patch(p, frost), "apply_status", "chilled"), "Cold spells gain a chill");
        h.assertFalse(hasEffect(firebolt, "apply_status", "chilled"), "The base recipe is left untouched");
        h.assertTrue(MagicGear.patch(p, RecipeGameTests.spell("Stone Platform")) == RecipeGameTests.spell("Stone Platform"), "Unmatched spells are not copied");
        h.succeed();
    }

    @MagicTest
    public static void gearRulesScaleDamage(GameTestHelper h) {
        DeathGameTests.floor(h);
        ServerPlayer p = RecipeGameTests.caster(h);
        SpellDefinition firebolt = RecipeGameTests.spell("Firebolt");
        equip(p, MagicGear.Slot.TRINKET_1, MagicItems.SPLITTING_LENS);
        SpellDefinition split = MagicGear.patch(p, firebolt);
        h.assertTrue(split.number("fan", 1) == 3, "The lens fans projectiles into three");
        h.assertTrue(Math.abs(damage(split) - damage(firebolt) * .45) < 1e-6, "Each fanned projectile is weaker");
        h.succeed();
    }

    @MagicTest
    public static void gearStackBonus(GameTestHelper h) {
        DeathGameTests.floor(h);
        ServerPlayer p = RecipeGameTests.caster(h);
        ItemStack plain = new ItemStack(MagicItems.WAND.get()), rolled = new ItemStack(MagicItems.WAND.get());
        rolled.set(GearBonus.TYPE.get(), new GearBonus(Map.of("fire", 25), List.of("quickening"),
                List.of("{\"elements\":[\"fire\"],\"patch\":{\"delivery\":{\"hot_zone\":true}}}"), List.of()));
        new MagicGear.Loadout(p).setItem(MagicGear.Slot.FOCUS.ordinal(), plain);
        int base = MagicGear.total(p)[MagicStats.Stat.FIRE.ordinal()];
        h.assertFalse(MagicGear.has(p, MagicGear.Trait.QUICKENING), "A plain wand has no bonus trait");
        new MagicGear.Loadout(p).setItem(MagicGear.Slot.FOCUS.ordinal(), rolled);
        h.assertTrue(MagicGear.total(p)[MagicStats.Stat.FIRE.ordinal()] == base + 25, "Stack bonus adds stats");
        h.assertTrue(MagicGear.has(p, MagicGear.Trait.QUICKENING), "Stack bonus adds traits");
        h.assertTrue(MagicGear.patch(p, RecipeGameTests.spell("Firebolt")).delivery().has("hot_zone"), "Stack bonus adds spell rules");
        h.assertTrue(GearBonus.of(new MagicGear.Loadout(p).getItem(0)).stats().get("fire") == 25, "Stack bonus survives a reload");
        h.succeed();
    }

    @MagicTest
    public static void gearQuickening(GameTestHelper h) {
        DeathGameTests.floor(h);
        ServerPlayer p = RecipeGameTests.caster(h);
        equip(p, MagicGear.Slot.TRINKET_1, MagicItems.HUNTERS_MARK);
        double before = MagicGear.castBias(p, 3);
        var zombie = h.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 2, 3));
        zombie.hurtServer(h.getLevel(), p.damageSources().playerAttack(p), 1000);
        h.assertTrue(MagicGear.quickened(p) && MagicGear.castBias(p, 3) < before * .5, "A kill readies an instant cast");
        MagicGear.cast(p, RecipeGameTests.spell("Firebolt"), false);
        h.assertFalse(MagicGear.quickened(p), "The quick cast is spent");
        h.succeed();
    }

    private static ItemStack charmed(ItemStack base, net.neoforged.neoforge.registries.DeferredItem<CharmItem> charm) {
        return CharmItem.socket(base, new ItemStack(charm.get()));
    }

    @MagicTest
    public static void charmSockets(GameTestHelper h) {
        ItemStack sword = charmed(new ItemStack(net.minecraft.world.item.Items.IRON_SWORD), MagicItems.CHARM_POWER);
        h.assertFalse(sword.isEmpty(), "Vanilla tools accept a charm");
        h.assertTrue(charmed(sword, MagicItems.CHARM_SWIFT).isEmpty(), "Vanilla tools have one socket");
        h.assertTrue(charmed(new ItemStack(net.minecraft.world.item.Items.DIRT), MagicItems.CHARM_POWER).isEmpty(), "Plain blocks take no charms");
        ItemStack lens = charmed(new ItemStack(MagicItems.SPLITTING_LENS.get()), MagicItems.CHARM_POWER);
        h.assertTrue(charmed(lens, MagicItems.CHARM_POWER).isEmpty(), "The same charm cannot be inserted twice");
        lens = charmed(charmed(lens, MagicItems.CHARM_SWIFT), MagicItems.CHARM_REACH);
        h.assertTrue(GearBonus.of(lens).charms().size() == 3, "Rare gear has three sockets");
        h.assertTrue(charmed(lens, MagicItems.CHARM_LEECH).isEmpty(), "A fourth charm does not fit");
        h.assertTrue(GearBonus.of(CharmItem.strip(lens)).charms().isEmpty(), "A grindstone strips every charm");
        h.succeed();
    }

    @MagicTest
    public static void charmsOnVanillaArmor(GameTestHelper h) {
        DeathGameTests.floor(h);
        ServerPlayer p = RecipeGameTests.caster(h);
        int base = MagicGear.total(p)[MagicStats.Stat.DAMAGE.ordinal()];
        p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, charmed(new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE), MagicItems.CHARM_POWER));
        p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, charmed(new ItemStack(net.minecraft.world.item.Items.IRON_SWORD), MagicItems.CHARM_HOMING));
        MagicGear.invalidate(p);
        h.assertTrue(MagicGear.total(p)[MagicStats.Stat.DAMAGE.ordinal()] == base + 8, "A charmed chestplate adds its stats");
        h.assertTrue(MagicGear.patch(p, RecipeGameTests.spell("Firebolt")).delivery().has("homing"), "A charmed sword changes projectiles");
        h.succeed();
    }

    @MagicTest
    public static void charmRulesChangeSpells(GameTestHelper h) {
        DeathGameTests.floor(h);
        ServerPlayer p = RecipeGameTests.caster(h);
        ItemStack lens = charmed(charmed(charmed(new ItemStack(MagicItems.SPLITTING_LENS.get()), MagicItems.CHARM_RICOCHET), MagicItems.CHARM_DRIFT), MagicItems.CHARM_LEECH);
        new MagicGear.Loadout(p).setItem(MagicGear.Slot.TRINKET_1.ordinal(), lens);
        h.assertTrue(MagicGear.patch(p, RecipeGameTests.spell("Arcane Beam")).number("ricochet", 0) == 1, "Beams ricochet");
        h.assertTrue(MagicGear.patch(p, RecipeGameTests.spell("Conductive Flood")).delivery().has("follow"), "Zones follow the caster");
        h.assertTrue(hasEffect(MagicGear.patch(p, RecipeGameTests.spell("Firebolt")), "drain_life", null), "Damage spells drain life");
        equip(p, MagicGear.Slot.TRINKET_2, MagicItems.THRAIN_GAUNTLET);
        h.assertTrue(MagicGear.patch(p, RecipeGameTests.spell("Pebble")).number("bounce", 0) == 2, "Thrain's gauntlet bounces earth projectiles");
        h.assertTrue(MagicItems.THRAIN_GAUNTLET.get().named, "Named gear is marked");
        h.succeed();
    }

    @MagicTest
    public static void sharedBlessingReachesAllies(GameTestHelper h) {
        DeathGameTests.floor(h);
        ServerPlayer p = RecipeGameTests.caster(h);
        SpellDefinition cautery = RecipeGameTests.spell("Cautery");
        h.assertFalse(cautery.selfEffects().isEmpty(), "Cautery has a self branch");
        var friend = h.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 2, 1));
        var scoreboard = h.getLevel().getScoreboard();
        var team = scoreboard.addPlayerTeam("magicka_share_" + p.getUUID().toString().substring(0, 8));
        scoreboard.addPlayerToTeam(p.getScoreboardName(), team);
        scoreboard.addPlayerToTeam(friend.getScoreboardName(), team);
        try {
            h.assertTrue(MagicGear.share(p, cautery) >= 1, "Allies nearby share self spells");
        } finally {
            scoreboard.removePlayerTeam(team);
        }
        h.succeed();
    }

    @MagicTest
    public static void gearSummonModifiers(GameTestHelper h) {
        DeathGameTests.floor(h);
        ServerPlayer p = RecipeGameTests.caster(h);
        SpellDefinition husks = RecipeGameTests.spell("Crypt Husks");
        equip(p, MagicGear.Slot.TRINKET_1, MagicItems.LEGION_BANNER);
        SpellDefinition legion = MagicGear.patch(p, husks);
        h.assertTrue(legion.number("summon_extra", 0) == 1, "The banner calls one more creature");
        h.assertTrue(Math.abs(legion.number("summon_health", 1) - .6) < 1e-6, "Bannered summons are frailer");
        h.assertTrue(MagicGear.patch(p, RecipeGameTests.spell("Firebolt")) == RecipeGameTests.spell("Firebolt"), "Other spells are untouched");
        var zombie = h.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 2, 3));
        MagicGear.frail(zombie, .5);
        h.assertTrue(zombie.getMaxHealth() == 10 && zombie.getHealth() == 10, "Summon health is scaled and refilled");
        h.succeed();
    }

    @MagicTest
    public static void gearAdaptation(GameTestHelper h) {
        DeathGameTests.floor(h);
        ServerPlayer p = RecipeGameTests.caster(h);
        h.assertTrue(MagicGear.element(p.damageSources().onFire()) == Element.FIRE, "Burning is fire damage");
        h.assertTrue(MagicGear.element(p.damageSources().generic()) == null, "Plain damage has no element");
        h.assertTrue(MagicGear.adapt(p, Element.FIRE, 10) == 10, "Without the hide nothing adapts");
        equip(p, MagicGear.Slot.ROBE, MagicItems.CHAMELEON_HIDE);
        int fire = MagicGear.total(p)[MagicStats.Stat.FIRE.ordinal()];
        h.assertTrue(MagicGear.adapt(p, Element.FIRE, 10) == 10, "The first hit lands in full");
        h.assertTrue(MagicGear.adapted(p) == Element.FIRE, "The hide takes on fire");
        h.assertTrue(MagicGear.total(p)[MagicStats.Stat.FIRE.ordinal()] == fire + MagicGear.ADAPT_BONUS, "Adapted element is empowered");
        h.assertTrue(MagicGear.adapt(p, Element.FIRE, 10) == 7, "Repeat hits are resisted");
        h.assertTrue(MagicGear.adapt(p, Element.COLD, 10) == 10 && MagicGear.adapted(p) == Element.COLD, "A new element replaces the old");
        h.assertTrue(MagicGear.total(p)[MagicStats.Stat.FIRE.ordinal()] == fire, "The old bonus fades");
        h.succeed();
    }

    @MagicTest
    public static void gearTransmutesFormula(GameTestHelper h) {
        DeathGameTests.floor(h);
        ServerPlayer p = RecipeGameTests.caster(h);
        SpellFormula water = new SpellFormula(0, 4, 0, 0, 0, 0, 0, 0), firebolt = RecipeGameTests.spell("Firebolt").formula();
        h.assertTrue(MagicGear.transmute(p, water) == water, "Bare hands keep the formula");
        equip(p, MagicGear.Slot.RING_1, MagicItems.STEAM_RING);
        h.assertTrue(MagicGear.transmute(p, water).equals(new SpellFormula(4, 0, 0, 0, 0, 0, 0, 0)), "Water boils into fire");
        equip(p, MagicGear.Slot.TRINKET_1, MagicItems.WHISPERING_SHARD);
        SpellFormula whispered = MagicGear.transmute(p, firebolt);
        h.assertTrue(whispered.count(Element.ARCANE) == 1 && whispered.size() == firebolt.size() + 1, "The shard adds arcane");
        SpellFormula full = new SpellFormula(5, 0, 0, 0, 0, 0, 0, 0);
        h.assertTrue(MagicGear.transmute(p, full).equals(full), "Full formulas have no room for arcane");
        h.assertTrue(MagicStats.resolve(p, firebolt).formula().equals(whispered), "Casting resolves the transmuted formula");
        h.succeed();
    }

    @MagicTest
    public static void gearWorldRoots(GameTestHelper h) {
        DeathGameTests.floor(h);
        ServerPlayer p = RecipeGameTests.caster(h);
        SpellDefinition wall = RecipeGameTests.spell("Stone Wall");
        equip(p, MagicGear.Slot.TRINKET_1, MagicItems.WORLD_ROOTS);
        SpellDefinition rooted = MagicGear.patch(p, wall);
        h.assertTrue(rooted.source().getAsJsonObject("construction").get("lifetime_scale").getAsDouble() == 2, "Earth constructions last twice as long");
        h.assertTrue(rooted.castTicks() == Math.round(wall.castTicks() * 1.5), "Earth constructions are slower to raise");
        h.assertTrue(MagicGear.patch(p, RecipeGameTests.spell("Firebolt")) == RecipeGameTests.spell("Firebolt"), "Other spells are untouched");
        h.succeed();
    }

    @MagicTest
    public static void gearHasRecipes(GameTestHelper h) {
        for (var item : MagicItems.charms()) {
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.get()).getPath();
            h.assertTrue(GearGameTests.class.getResource("/data/magicka/recipe/" + id + ".json") != null, "Missing recipe for " + id);
        }
        for (var item : MagicItems.gear()) {
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.get()).getPath();
            h.assertTrue(GearGameTests.class.getResource("/data/magicka/recipe/" + id + ".json") != null, "Missing recipe for " + id);
        }
        h.succeed();
    }
}
