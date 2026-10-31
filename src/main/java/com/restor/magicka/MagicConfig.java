package com.restor.magicka;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class MagicConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue CONSTRUCTION = B.comment("Spells may place blocks: walls, bridges, stairs, ice. false turns building spells off").define("constructiveMagic", true);
    public static final ModConfigSpec.BooleanValue LIFETIME = B.comment("Ice, arcane and other temporary magic blocks disappear after their lifetime").define("magicBlockLifetime", true);
    public static final ModConfigSpec.BooleanValue PERMANENT_EARTH = B.comment("Earth constructions stay forever").define("permanentEarthBlocks", true);
    public static final ModConfigSpec.BooleanValue PERMANENT_ROOTS = B.comment("Roots stay forever").define("permanentRootBlocks", true);
    public static final ModConfigSpec.IntValue EARTH_LIFE = B.comment("Ticks before non-permanent earth fades (20 ticks = 1 second)").defineInRange("earthLifetimeTicks", 1200, 20, 72000);
    public static final ModConfigSpec.IntValue ICE_LIFE = B.comment("Ticks before magic ice melts").defineInRange("iceLifetimeTicks", 600, 20, 72000);
    public static final ModConfigSpec.IntValue ROOT_LIFE = B.comment("Ticks before non-permanent roots wither").defineInRange("rootLifetimeTicks", 900, 20, 72000);
    public static final ModConfigSpec.IntValue ARCANE_LIFE = B.comment("Ticks before arcane blocks fade").defineInRange("arcaneLifetimeTicks", 400, 20, 72000);
    public static final ModConfigSpec.IntValue MAX_BLOCKS = B.comment("How many blocks one construction may place").defineInRange("maxConstructionBlocks", 16, 1, 64);
    public static final ModConfigSpec.BooleanValue INSTANT_CAST = B.comment("Spells fire at once without a cast time; channeled spells last while the button is held. false brings back the cast time").define("instantCast", true);
    public static final ModConfigSpec.DoubleValue CAST_SCALE = B.comment("Scales every cast time; below 1 is faster").defineInRange("castTimeMultiplier", 1.0, 0.25, 4.0);
    public static final ModConfigSpec.IntValue CHANNEL_TICKS = B.comment("Maximum channel ticks per element in the formula").defineInRange("channelMaxTicksPerElement", 20, 5, 200);
    public static final ModConfigSpec.DoubleValue PLAYER_DAMAGE = B.comment("Multiplier for spell damage dealt to players").defineInRange("playerMagicDamageMultiplier", 0.5, 0.0, 2.0);
    public static final ModConfigSpec.IntValue MAX_RUNTIME = B.comment("How many spells one player may have running at once").defineInRange("maxActiveSpellsPerPlayer", 32, 4, 128);
    public static final ModConfigSpec.IntValue REVIVE_TICKS = B.comment("Ticks a fallen creature can still be revived").defineInRange("revivalWindowTicks", 600, 40, 6000);
    public static final ModConfigSpec.IntValue MAX_MINIONS = B.comment("How many undead servants one player may command").defineInRange("maxUndeadMinionsPerPlayer", 24, 1, 64);
    public static final ModConfigSpec.BooleanValue TERRAIN_DAMAGE = B.comment("Spells may break blocks. false keeps the world intact").define("allowTerrainDamage", true);
    public static final ModConfigSpec.BooleanValue FRIENDLY_FIRE = B.comment("Spells can hurt other players").define("friendlyFire", true);
    public static final ModConfigSpec.BooleanValue COLLAPSE = B.comment("Constructions collapse when their base is destroyed").define("collapseWithRoot", true);
    public static final ModConfigSpec.BooleanValue SPECIAL_SPAWNS = B.comment("Spellweaver specialist enemies spawn in the world. false stops natural spawns").define("specialEnemyNaturalSpawns", true);
    public static final ModConfigSpec.BooleanValue UNLOCK_ALL = B.comment("Open every sealed spell without scrolls").define("unlockAllSpells", false);

    public static final ModConfigSpec.BooleanValue ALL_MOB_SCROLLS = B.comment("Any hostile mob, from any mod, can drop a spell scroll when a player kills it").define("scrollDropsFromAllMobs", true);
    public static final ModConfigSpec.DoubleValue MOB_SCROLL_CHANCE = B.comment("Chance for an ordinary hostile mob (0.005 = 0.5%)").defineInRange("mobScrollChance", 0.005, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue CASTER_SCROLL_CHANCE = B.comment("Witches, evokers and mobs in the magicka:spellcasters tag").defineInRange("casterMobScrollChance", 0.05, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue SPECIALIST_SCROLL_CHANCE = B.comment("Spellweaver specialist enemies").defineInRange("specialistScrollChance", 0.04, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue SPECIALIST_CASTER_SCROLL_CHANCE = B.comment("Spellweaver specialist casters, like countermages and goblin mages").defineInRange("specialistCasterScrollChance", 0.1, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue BOSS_SCROLL_CHANCE = B.comment("Chance for bosses; 1 = always").defineInRange("bossScrollChance", 1.0, 0.0, 1.0);
    public static final ModConfigSpec.IntValue BOSS_HEALTH = B.comment("Hostile mobs with at least this much max health count as bosses even without the c:bosses tag; 0 disables").defineInRange("bossHealthThreshold", 150, 0, 100000);
    public static final ModConfigSpec.IntValue BOSS_ULTIMATE_WEIGHT = B.comment("How much likelier an ultimate is than another spell in a boss drop; specialists and mobs never drop ultimates").defineInRange("bossUltimateWeight", 3, 0, 100);
    public static final ModConfigSpec.BooleanValue DIMENSION_SCROLLS = B.comment("Nether scrolls lean to fire, End scrolls to arcane; both drop more ultimates").define("dimensionScrollBias", true);
    public static final ModConfigSpec.DoubleValue CHEST_SCROLLS = B.comment("Multiplies the chance of a scroll in any chest, including structures from dungeon mods; 0 disables").defineInRange("chestScrollMultiplier", 1.0, 0.0, 16.0);

    public static final ModConfigSpec.BooleanValue MOB_RESISTANCES = B.comment("Mobs resist or suffer elements by their nature (fire-immune, freeze-immune, undead, aquatic, End) and magicka:resists/* and magicka:weak/* entity tags").define("mobElementResistances", true);
    public static final ModConfigSpec.DoubleValue RESIST_MULTIPLIER = B.comment("Damage taken from a resisted element").defineInRange("elementResistMultiplier", 0.5, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue WEAK_MULTIPLIER = B.comment("Damage taken from an element the mob is weak to").defineInRange("elementWeaknessMultiplier", 1.5, 1.0, 4.0);

    public static final ModConfigSpec.BooleanValue MAGE_LEVELS = B.comment("Mage level: experience from casting, magic kills and reading scrolls. false turns levels off").define("mageLevels", true);
    public static final ModConfigSpec.DoubleValue MAGE_XP = B.comment("Scales all mage experience").defineInRange("mageXpMultiplier", 1.0, 0.0, 100.0);
    public static final ModConfigSpec.IntValue MAX_MAGE_LEVEL = B.comment("Mage level cap").defineInRange("maxMageLevel", 30, 1, 100);
    public static final ModConfigSpec.BooleanValue FORMULA_BY_LEVEL = B.comment("New mages weave three elements; four and five open with mage level. false allows five elements from the start").define("formulaLengthByLevel", true);
    public static final ModConfigSpec.IntValue FOUR_ELEMENTS = B.comment("Mage level that opens four-element formulas").defineInRange("fourElementsLevel", 3, 0, 100);
    public static final ModConfigSpec.IntValue FIVE_ELEMENTS = B.comment("Mage level that opens five-element formulas").defineInRange("fiveElementsLevel", 8, 0, 100);
    public static final ModConfigSpec.IntValue LEVEL_CAST_SPEED = B.comment("Cast speed per mage level, in percent").defineInRange("castSpeedPerLevel", 1, 0, 10);
    public static final ModConfigSpec.IntValue LEVEL_DAMAGE = B.comment("Spell damage per mage level, in percent").defineInRange("damagePerLevel", 1, 0, 10);
    public static final ModConfigSpec SPEC = B.build();
    public static boolean permanent(String material) {
        return switch(material) { case "earth" -> PERMANENT_EARTH.get(); case "root" -> PERMANENT_ROOTS.get(); case "light", "glowstone" -> true; default -> false; };
    }
    private MagicConfig() {}
}
