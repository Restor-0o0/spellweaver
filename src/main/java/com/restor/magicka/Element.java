package com.restor.magicka;

import java.util.Locale;

public enum Element {
    FIRE(0xFFFF7043), WATER(0xFF42A5F5), COLD(0xFF80DEEA), LIGHTNING(0xFFAF8EFF),
    EARTH(0xFFBCAAA4), LIFE(0xFF81C784), ARCANE(0xFFFF70CF), DEATH(0xFFD04860);

    public final int color;
    Element(int color) { this.color = color; }
    public String key() { return "element.magicka." + name().toLowerCase(Locale.ROOT); }
}
