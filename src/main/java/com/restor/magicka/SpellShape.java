package com.restor.magicka;

public enum SpellShape {
    PROJECTILE, BURST_PROJECTILE, BEAM, SPRAY, CHAIN, TARGETED_EXPLOSION, TARGETED_ZONE, NOVA,
    PLATFORM, PILLAR, WALL, BRIDGE, STAIR, RAMP, SPIKE, GROWTH, DIRECT_TARGET, DASH, SUMMON_OBJECT, MINE;
    public boolean construction() { return switch(this) { case PLATFORM, PILLAR, WALL, BRIDGE, STAIR, RAMP, SPIKE, GROWTH -> true; default -> false; }; }
}
