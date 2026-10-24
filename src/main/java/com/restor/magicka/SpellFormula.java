package com.restor.magicka;

import java.util.List;

/** Immutable multiset key: equality and hashing never depend on input order. */
public record SpellFormula(int fire, int water, int cold, int lightning, int earth, int life, int arcane, int death) {
    public SpellFormula {
        if (fire < 0 || water < 0 || cold < 0 || lightning < 0 || earth < 0 || life < 0 || arcane < 0 || death < 0
                || (long)fire + water + cold + lightning + earth + life + arcane + death > Formula.MAX) throw new IllegalArgumentException("Formula must contain 0..5 elements");
    }
    public SpellFormula(int fire, int water, int cold, int lightning, int earth, int life, int arcane) { this(fire, water, cold, lightning, earth, life, arcane, 0); }
    public int size() { return fire + water + cold + lightning + earth + life + arcane + death; }
    public int count(Element e) { return switch(e) { case FIRE -> fire; case WATER -> water; case COLD -> cold; case LIGHTNING -> lightning; case EARTH -> earth; case LIFE -> life; case ARCANE -> arcane; case DEATH -> death; }; }
    public SpellFormula without(Element e) {
        int[] c = new int[Element.values().length];
        for (Element x : Element.values()) c[x.ordinal()] = count(x) - (x == e ? 1 : 0);
        return new SpellFormula(c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7]);
    }
    public Formula prepared() {
        Formula f = new Formula();
        for (Element e : Element.values()) for (int i = 0; i < count(e); i++) f.add(e);
        return f;
    }
    public List<Element> elements() { return prepared().elements(); }
    public static SpellFormula of(Formula f) { return new SpellFormula(f.count(Element.FIRE), f.count(Element.WATER), f.count(Element.COLD), f.count(Element.LIGHTNING), f.count(Element.EARTH), f.count(Element.LIFE), f.count(Element.ARCANE), f.count(Element.DEATH)); }
}
