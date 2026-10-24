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
    public static final ModConfigSpec SPEC = B.build();
    public static boolean permanent(String material) {
        return switch(material) { case "earth" -> PERMANENT_EARTH.get(); case "root" -> PERMANENT_ROOTS.get(); case "light", "glowstone" -> true; default -> false; };
    }
    private MagicConfig() {}
}
