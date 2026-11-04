package com.restor.magicka;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum Element {
    FIRE(0xFFFF7043), WATER(0xFF42A5F5), COLD(0xFF80DEEA), LIGHTNING(0xFFAF8EFF),
    EARTH(0xFFBCAAA4), LIFE(0xFF81C784), ARCANE(0xFFFF70CF), DEATH(0xFFD04860),
    STEAM(0xFFDCE3EA), ICE(0xFF8FB8FF), SHIELD(0xFFE8C66A), MAGMA(0xFFFF6B32);

    public static final List<Element> BASE = Arrays.stream(values()).filter(Element::base).toList();
    private static final String CODES = "12345678SIWM";
    public final int color;
    Element(int color) { this.color = color; }
    public String key() { return "element.magicka." + name().toLowerCase(Locale.ROOT); }
    public boolean base() { return ordinal() < 8; }
    /** The two base elements a derived element is fused from; a base element is its own single parent. */
    public List<Element> parents() {
        return switch (this) {
            case STEAM -> List.of(WATER, FIRE);
            case ICE -> List.of(WATER, COLD);
            case SHIELD -> List.of(ARCANE, EARTH);
            case MAGMA -> List.of(FIRE, EARTH);
            default -> List.of(this);
        };
    }
    /** The base element that stands in for this one where only base elements exist (models, glyphs). */
    public Element primary() { return parents().get(parents().size()-1); }
    public static Element fuse(Element a, Element b) {
        for (Element d : values()) if (!d.base() && d.parents().contains(a) && d.parents().contains(b) && a != b) return d;
        return null;
    }
    public char code() { return CODES.charAt(ordinal()); }
    public static Element byCode(char c) {
        int i = CODES.indexOf(Character.toUpperCase(c));
        return i < 0 ? null : values()[i];
    }
}
