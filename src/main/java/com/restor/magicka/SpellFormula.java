package com.restor.magicka;

import java.util.List;

/** Immutable multiset key: equality and hashing never depend on input order. Derived elements fill one slot each. */
public record SpellFormula(int fire, int water, int cold, int lightning, int earth, int life, int arcane, int death, int steam, int ice, int shield) {
    public SpellFormula {
        if (fire < 0 || water < 0 || cold < 0 || lightning < 0 || earth < 0 || life < 0 || arcane < 0 || death < 0 || steam < 0 || ice < 0 || shield < 0
                || (long)fire + water + cold + lightning + earth + life + arcane + death + steam + ice + shield > Formula.MAX) throw new IllegalArgumentException("Formula must contain 0..5 elements");
    }
    public SpellFormula(int fire, int water, int cold, int lightning, int earth, int life, int arcane, int death) { this(fire, water, cold, lightning, earth, life, arcane, death, 0, 0, 0); }
    public SpellFormula(int fire, int water, int cold, int lightning, int earth, int life, int arcane) { this(fire, water, cold, lightning, earth, life, arcane, 0); }
    public static SpellFormula of(int[] c) { return new SpellFormula(c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], c[8], c[9], c[10]); }
    public int size() { return fire + water + cold + lightning + earth + life + arcane + death + steam + ice + shield; }
    public int count(Element e) { return switch(e) { case FIRE -> fire; case WATER -> water; case COLD -> cold; case LIGHTNING -> lightning; case EARTH -> earth; case LIFE -> life; case ARCANE -> arcane; case DEATH -> death; case STEAM -> steam; case ICE -> ice; case SHIELD -> shield; }; }
    /** How much of a base element the formula carries, counting the parents of derived elements. */
    public int total(Element e) {
        int n = count(e);
        if (e.base()) for (Element d : Element.values()) if (!d.base() && d.parents().contains(e)) n += count(d);
        return n;
    }
    public boolean derived() { return steam + ice + shield > 0; }
    public SpellFormula without(Element e) {
        int[] c = new int[Element.values().length];
        for (Element x : Element.values()) c[x.ordinal()] = count(x) - (x == e ? 1 : 0);
        return of(c);
    }
    public Formula prepared() {
        Formula f = new Formula();
        for (Element e : Element.values()) for (int i = 0; i < count(e); i++) f.add(e);
        return f;
    }
    public List<Element> elements() { return prepared().elements(); }
    public static SpellFormula of(Formula f) {
        int[] c = new int[Element.values().length];
        for (Element e : Element.values()) c[e.ordinal()] = f.count(e);
        return of(c);
    }
}
