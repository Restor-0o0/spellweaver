package com.restor.magicka;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class MagicConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue CONSTRUCTION = B.define("constructiveMagic", true);
    public static final ModConfigSpec.BooleanValue LIFETIME = B.define("magicBlockLifetime", true);
    public static final ModConfigSpec.BooleanValue PERMANENT_EARTH = B.define("permanentEarthBlocks", true);
    public static final ModConfigSpec.BooleanValue PERMANENT_ROOTS = B.define("permanentRootBlocks", true);
    public static final ModConfigSpec.IntValue EARTH_LIFE = B.defineInRange("earthLifetimeTicks", 1200, 20, 72000);
    public static final ModConfigSpec.IntValue ICE_LIFE = B.defineInRange("iceLifetimeTicks", 600, 20, 72000);
    public static final ModConfigSpec.IntValue ROOT_LIFE = B.defineInRange("rootLifetimeTicks", 900, 20, 72000);
    public static final ModConfigSpec.IntValue ARCANE_LIFE = B.defineInRange("arcaneLifetimeTicks", 400, 20, 72000);
    public static final ModConfigSpec.IntValue MAX_BLOCKS = B.defineInRange("maxConstructionBlocks", 16, 1, 64);
    public static final ModConfigSpec.DoubleValue CAST_SCALE = B.defineInRange("castTimeMultiplier", 1.0, 0.25, 4.0);
    public static final ModConfigSpec.IntValue CHANNEL_TICKS = B.defineInRange("channelMaxTicksPerElement", 20, 5, 200);
    public static final ModConfigSpec.DoubleValue PLAYER_DAMAGE = B.defineInRange("playerMagicDamageMultiplier", 0.5, 0.0, 2.0);
    public static final ModConfigSpec.IntValue MAX_RUNTIME = B.defineInRange("maxActiveSpellsPerPlayer", 32, 4, 128);
    public static final ModConfigSpec.IntValue REVIVE_TICKS = B.defineInRange("revivalWindowTicks", 600, 40, 6000);
    public static final ModConfigSpec.IntValue MAX_MINIONS = B.defineInRange("maxUndeadMinionsPerPlayer", 24, 1, 64);
    public static final ModConfigSpec.BooleanValue TERRAIN_DAMAGE = B.define("allowTerrainDamage", true);
    public static final ModConfigSpec.BooleanValue FRIENDLY_FIRE = B.define("friendlyFire", true);
    public static final ModConfigSpec.BooleanValue COLLAPSE = B.define("collapseWithRoot", true);
    public static final ModConfigSpec.BooleanValue SPECIAL_SPAWNS = B.define("specialEnemyNaturalSpawns", true);
    public static final ModConfigSpec.BooleanValue UNLOCK_ALL = B.define("unlockAllSpells", false);

    public static final ModConfigSpec.BooleanValue ALL_MOB_SCROLLS = B.comment("Any hostile mob, from any mod, can drop a spell scroll when a player kills it").define("scrollDropsFromAllMobs", true);
    public static final ModConfigSpec.DoubleValue MOB_SCROLL_CHANCE = B.defineInRange("mobScrollChance", 0.005, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue CASTER_SCROLL_CHANCE = B.comment("Evokers, witches and mobs in the magicka:spellcasters tag").defineInRange("casterMobScrollChance", 0.05, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue SPECIALIST_SCROLL_CHANCE = B.defineInRange("specialistScrollChance", 0.04, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue SPECIALIST_CASTER_SCROLL_CHANCE = B.defineInRange("specialistCasterScrollChance", 0.1, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue BOSS_SCROLL_CHANCE = B.defineInRange("bossScrollChance", 1.0, 0.0, 1.0);
    public static final ModConfigSpec.IntValue BOSS_HEALTH = B.comment("Hostile mobs with at least this much max health count as bosses even without the c:bosses tag; 0 disables").defineInRange("bossHealthThreshold", 150, 0, 100000);
    public static final ModConfigSpec.IntValue BOSS_ULTIMATE_WEIGHT = B.comment("How much likelier an ultimate is than another spell in a boss drop; specialists and mobs never drop ultimates").defineInRange("bossUltimateWeight", 3, 0, 100);
    public static final ModConfigSpec.BooleanValue DIMENSION_SCROLLS = B.comment("Nether scrolls lean to fire, End scrolls to arcane; both drop more ultimates").define("dimensionScrollBias", true);
    public static final ModConfigSpec.DoubleValue CHEST_SCROLLS = B.comment("Multiplies the chance of a scroll in any chest, including structures from dungeon mods; 0 disables").defineInRange("chestScrollMultiplier", 1.0, 0.0, 16.0);

    public static final ModConfigSpec.BooleanValue MOB_RESISTANCES = B.comment("Mobs resist or suffer elements by their nature (fire-immune, freeze-immune, undead, aquatic, End) and magicka:resists/* and magicka:weak/* entity tags").define("mobElementResistances", true);
    public static final ModConfigSpec.DoubleValue RESIST_MULTIPLIER = B.defineInRange("elementResistMultiplier", 0.5, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue WEAK_MULTIPLIER = B.defineInRange("elementWeaknessMultiplier", 1.5, 1.0, 4.0);

    public static final ModConfigSpec.BooleanValue MAGE_LEVELS = B.comment("Mage level: experience from casting, magic kills and reading scrolls").define("mageLevels", true);
    public static final ModConfigSpec.DoubleValue MAGE_XP = B.defineInRange("mageXpMultiplier", 1.0, 0.0, 100.0);
    public static final ModConfigSpec.IntValue MAX_MAGE_LEVEL = B.defineInRange("maxMageLevel", 30, 1, 100);
    public static final ModConfigSpec.BooleanValue FORMULA_BY_LEVEL = B.comment("New mages weave three elements; four and five open with mage level").define("formulaLengthByLevel", true);
    public static final ModConfigSpec.IntValue FOUR_ELEMENTS = B.defineInRange("fourElementsLevel", 3, 0, 100);
    public static final ModConfigSpec.IntValue FIVE_ELEMENTS = B.defineInRange("fiveElementsLevel", 8, 0, 100);
    public static final ModConfigSpec.IntValue LEVEL_CAST_SPEED = B.comment("Cast speed per mage level, in percent").defineInRange("castSpeedPerLevel", 1, 0, 10);
    public static final ModConfigSpec.IntValue LEVEL_DAMAGE = B.comment("Spell damage per mage level, in percent").defineInRange("damagePerLevel", 1, 0, 10);
    public static final ModConfigSpec SPEC = B.build();
    public static boolean permanent(String material) {
        return switch(material) { case "earth" -> PERMANENT_EARTH.get(); case "root" -> PERMANENT_ROOTS.get(); case "light", "glowstone" -> true; default -> false; };
    }
    private MagicConfig() {}
}
