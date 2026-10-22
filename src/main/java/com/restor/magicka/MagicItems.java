package com.restor.magicka;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import static com.restor.magicka.GearItem.Grade.*;
import static com.restor.magicka.GearItem.Kind.*;
import static com.restor.magicka.MagicStats.Stat.*;

public final class MagicItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, Magicka.ID);
    public static final RegistryObject<Item> SPELLBOOK = ITEMS.register("spellbook", () -> new Item(new Item.Properties().stacksTo(1)));
    private static final List<RegistryObject<GearItem>> GEAR = new ArrayList<>();

    private static RegistryObject<GearItem> gear(String id, GearItem.Kind kind, GearItem.Grade grade, UnaryOperator<GearItem.Builder> setup) {
        RegistryObject<GearItem> item = ITEMS.register(id, () -> setup.apply(GearItem.of(id, kind, grade)).build());
        GEAR.add(item);
        return item;
    }
    private static JsonObject patch(String json) { return JsonParser.parseString(json).getAsJsonObject(); }
    private static final SpellFormula STONE_PLATFORM = new SpellFormula(0, 0, 0, 0, 2, 0, 1, 0);
    private static final SpellFormula ICE_STEP = new SpellFormula(0, 1, 1, 0, 0, 0, 1, 0);
    private static final SpellFormula FIREBOMB = new SpellFormula(2, 0, 0, 0, 0, 0, 1, 0);
    private static final SpellFormula BUBBLE_LIFT = new SpellFormula(0, 2, 0, 0, 0, 0, 1, 0);

    public static final RegistryObject<GearItem> WAND = gear("wand", FOCUS, UNCOMMON, b -> b
            .bias(.7, .8, 1, 1.25, 1.45).stat(DAMAGE, 10).stat(AREA, -10));
    public static final RegistryObject<GearItem> STAFF = gear("staff", FOCUS, UNCOMMON, b -> b
            .bias(1.3, 1.15, 1, .85, .75).stat(AREA, 20).stat(DURATION, 25).stat(FORCE, -15));
    public static final RegistryObject<GearItem> STONE_GAUNTLET = gear("stone_gauntlet", FOCUS, UNCOMMON, b -> b
            .bias(1, 1, 1.05, 1.1, 1.15).stat(EARTH, 35).stat(CONSTRUCTION, 40).stat(DESTRUCTION, 25).stat(AREA, -25));
    public static final RegistryObject<GearItem> ARCANE_ORB = gear("arcane_orb", FOCUS, RARE, b -> b
            .bias(1.1, 1.05, 1, 1, .95).stat(AREA, 35).stat(DURATION, 25).stat(DAMAGE, -15));
    public static final RegistryObject<GearItem> RITUAL_TOME = gear("ritual_tome", FOCUS, RARE, b -> b
            .bias(1.2, 1.1, 1, .9, .8).stat(ARCANE, 25).stat(CHAINS, 20).stat(DAMAGE, -10));
    public static final RegistryObject<GearItem> RITUAL_FOCUS = gear("ritual_focus", FOCUS, RARE, b -> b
            .bias(1.25, 1.15, 1.05, .95, .85).stat(LIFE, 30).stat(SUMMONS, 50).stat(HEALING, 25).stat(DAMAGE, -25));

    public static final RegistryObject<GearItem> HEAVY_COAT = gear("heavy_expedition_coat", ROBE, COMMON, b -> b
            .attribute(Attributes.ARMOR, Operation.ADDITION, 5).attribute(Attributes.MOVEMENT_SPEED, Operation.MULTIPLY_BASE, -.1)
            .stat(CAST_SPEED, -10));
    public static final RegistryObject<GearItem> LIGHT_ROBE = gear("light_robe", ROBE, COMMON, b -> b
            .attribute(Attributes.MOVEMENT_SPEED, Operation.MULTIPLY_BASE, .1).attribute(Attributes.MAX_HEALTH, Operation.ADDITION, -2)
            .stat(CAST_SPEED, 15));
    public static final RegistryObject<GearItem> INSULATED_SUIT = gear("insulated_suit", ROBE, UNCOMMON, b -> b
            .trait(MagicGear.Trait.SHOCK_WARD).attribute(Attributes.ARMOR, Operation.ADDITION, 2).stat(LIGHTNING, 15));
    public static final RegistryObject<GearItem> FROST_GEAR = gear("frost_gear", ROBE, UNCOMMON, b -> b
            .trait(MagicGear.Trait.FROST_WARD).stat(COLD, 25).stat(FIRE, -15));
    public static final RegistryObject<GearItem> LIVING_ROBE = gear("living_robe", ROBE, RARE, b -> b
            .trait(MagicGear.Trait.LIVING_REGEN).trait(MagicGear.Trait.FIRE_FRAIL).stat(LIFE, 20));

    public static final RegistryObject<GearItem> CONDUCTOR_RING = gear("conductor_ring", RING, UNCOMMON, b -> b
            .trait(MagicGear.Trait.SELF_CONDUCTIVE).stat(LIGHTNING, 30).stat(CHAINS, 40));
    public static final RegistryObject<GearItem> RING_OF_SUSPENSION = gear("ring_of_suspension", RING, RARE, b -> b
            .stat(CONSTRUCTION, 20).patch(STONE_PLATFORM, patch("{\"construction\":{\"floating\":true,\"lifetime_ticks\":900}}")));
    public static final RegistryObject<GearItem> DROWNED_KINGS_RING = gear("drowned_kings_ring", RING, RARE, b -> b
            .stat(WATER, 25).patch(BUBBLE_LIFT, patch("{\"shape\":\"targeted_zone\",\"delivery\":{\"radius\":3,\"duration_ticks\":200},"
                    + "\"effects\":[{\"type\":\"set_wet\",\"duration_ticks\":200},{\"type\":\"apply_status\",\"status\":\"rooted\",\"duration_ticks\":120,\"strength\":1},"
                    + "{\"type\":\"deal_damage\",\"amount\":2,\"element\":\"water\"}]}")));
    public static final RegistryObject<GearItem> ARCHITECTS_FINGER = gear("architects_finger", RING, UNCOMMON, b -> b
            .stat(CONSTRUCTION, 60).stat(DAMAGE, -10));
    public static final RegistryObject<GearItem> FROZEN_HEEL = gear("frozen_heel", RING, UNCOMMON, b -> b
            .trait(MagicGear.Trait.FROST_WARD).stat(COLD, 20));

    public static final RegistryObject<GearItem> GLASS_HEART = gear("glass_heart", TRINKET, LEGENDARY, b -> b
            .trait(MagicGear.Trait.GLASS_HEART).attribute(Attributes.MAX_HEALTH, Operation.MULTIPLY_BASE, -.3).stat(DAMAGE, 40));
    public static final RegistryObject<GearItem> STONEHEART = gear("stoneheart", TRINKET, RARE, b -> b
            .trait(MagicGear.Trait.STONEHEART).attribute(Attributes.KNOCKBACK_RESISTANCE, Operation.ADDITION, .4)
            .attribute(Attributes.MOVEMENT_SPEED, Operation.MULTIPLY_BASE, -.05).stat(EARTH, 20));
    public static final RegistryObject<GearItem> GLACIER_HEART = gear("glacier_heart", TRINKET, RARE, b -> b
            .stat(COLD, 20).patch(ICE_STEP, patch("{\"shape\":\"stair\",\"construction\":{\"material\":\"ice\",\"budget\":14,\"uncapped\":true}}")));
    public static final RegistryObject<GearItem> ROCKET_CHARM = gear("rocket_charm", TRINKET, UNCOMMON, b -> b
            .stat(FORCE, 20).patch(FIREBOMB, patch("{\"self\":{\"mode\":\"effects\",\"effects\":[{\"type\":\"apply_force\",\"amount\":0.4,\"lift\":1.4},"
                    + "{\"type\":\"apply_status\",\"status\":\"slow_falling\",\"duration_ticks\":120,\"strength\":1}]}}")));
    public static final RegistryObject<GearItem> CONJURERS_BELL = gear("conjurers_bell", TRINKET, RARE, b -> b
            .stat(SUMMONS, 60).stat(DEATH, 15).stat(DAMAGE, -15));
    public static final RegistryObject<GearItem> STORM_VESSEL = gear("storm_vessel", TRINKET, RARE, b -> b
            .stat(LIGHTNING, 40).stat(CHAINS, 30).stat(CAST_SPEED, -10));
    public static final RegistryObject<GearItem> HEART_OF_THE_MOUNTAIN = gear("heart_of_the_mountain", TRINKET, RARE, b -> b
            .stat(EARTH, 40).stat(DESTRUCTION, 50).attribute(Attributes.MOVEMENT_SPEED, Operation.MULTIPLY_BASE, -.08));
    public static final RegistryObject<GearItem> SHEPHERDS_BELL = gear("shepherds_bell", TRINKET, WEIRD, b -> b
            .stat(ARCANE, 25).stat(DURATION, 40));
    public static final RegistryObject<GearItem> MIRROR_PRISM = gear("mirror_prism", TRINKET, LEGENDARY, b -> b
            .trait(MagicGear.Trait.MIRROR_PRISM).stat(CAST_SPEED, -20));
    public static final RegistryObject<GearItem> SECOND_TONGUE = gear("second_tongue", TRINKET, WEIRD, b -> b
            .trait(MagicGear.Trait.SECOND_TONGUE).stat(CAST_SPEED, -15));
    public static final RegistryObject<GearItem> BLOOD_FOCUS = gear("blood_focus", TRINKET, RARE, b -> b
            .trait(MagicGear.Trait.BLOOD_FOCUS).stat(DEATH, 25).stat(HEALING, -20));
    public static final RegistryObject<GearItem> GRAVE_BELL = gear("grave_bell", TRINKET, RARE, b -> b
            .trait(MagicGear.Trait.GRAVE_BELL).stat(DEATH, 20));
    public static final RegistryObject<GearItem> CHAOS_LENS = gear("chaos_lens", TRINKET, WEIRD, b -> b
            .trait(MagicGear.Trait.CHAOS_LENS).stat(DAMAGE, 35).stat(AREA, 20));
    public static final RegistryObject<GearItem> SOUL_THREAD = gear("soul_thread", TRINKET, RARE, b -> b
            .stat(LIFE, 30).stat(HEALING, 35).stat(DAMAGE, -20));
    public static final RegistryObject<GearItem> SPRING_SEED = gear("spring_seed", TRINKET, UNCOMMON, b -> b
            .stat(LIFE, 25).stat(CONSTRUCTION, 25));
    public static final RegistryObject<GearItem> IMPACT_SEAL = gear("impact_seal", TRINKET, UNCOMMON, b -> b
            .stat(FORCE, 60).stat(DAMAGE, -5));
    public static final RegistryObject<GearItem> WET_LENS = gear("wet_lens", TRINKET, UNCOMMON, b -> b
            .stat(WATER, 40).stat(DURATION, 20));
    public static final RegistryObject<GearItem> BRITTLE_CHARM = gear("brittle_charm", TRINKET, UNCOMMON, b -> b
            .stat(COLD, 25).stat(DESTRUCTION, 30));

    public static final RegistryObject<GearItem> EXPEDITION_BEACON = gear("expedition_beacon", TOOL, COMMON, b -> b
            .action(60, player -> SupportMagic.glowstone(player, SpellAim.hit(player).getLocation(), .5)));
    public static final RegistryObject<GearItem> ROPE_LAUNCHER = gear("rope_launcher", TOOL, UNCOMMON, b -> b
            .action(80, player -> {
                Vec3 look = player.getLookAngle();
                player.push(look.x * .9, Math.max(.5, look.y * .9 + .4), look.z * .9);
                player.hurtMarked = true;
                MagicStatuses.apply(player, MagicStatuses.Status.SLOW_FALLING, 100, 1);
            }));
    public static final RegistryObject<GearItem> SURVEY_SCANNER = gear("survey_scanner", TOOL, UNCOMMON, b -> b
            .action(200, player -> SupportMagic.reveal(player, player.position(), 28, 600)));
    public static final RegistryObject<GearItem> CAMP_KIT = gear("camp_kit", TOOL, COMMON, b -> b
            .action(600, player -> {
                SupportMagic.ring(player, player.position(), 3, 6);
                player.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, player.getBoundingBox().inflate(6))
                        .forEach(target -> MagicStatuses.apply(target, MagicStatuses.Status.REGENERATING, 400, 1));
            }));
    public static final RegistryObject<GearItem> REINFORCEMENT_TOOL = gear("reinforcement_tool", TOOL, UNCOMMON, b -> b
            .stat(CONSTRUCTION, 25).stat(DURATION, 15));
    public static final RegistryObject<GearItem> INGREDIENT_CASE = gear("ingredient_case", TOOL, COMMON, b -> b
            .stat(DURATION, 20).stat(CAST_SPEED, 5));

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Magicka.ID);
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("magicka", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.magicka"))
            .icon(() -> new ItemStack(SPELLBOOK.get()))
            .displayItems((parameters, output) -> {
                output.accept(SPELLBOOK.get());
                for (GearItem.Kind kind : GearItem.Kind.values())
                    GEAR.stream().map(RegistryObject::get).filter(item -> item.kind == kind).forEach(output::accept);
                com.restor.magicka.enemy.MagicEnemies.EGGS.forEach(egg -> output.accept(egg.get()));
            })
            .build());

    public static void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(SPELLBOOK);
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) com.restor.magicka.enemy.MagicEnemies.EGGS.forEach(event::accept);
    }
    public static List<RegistryObject<GearItem>> gear() { return List.copyOf(GEAR); }
    private MagicItems() {}
}
