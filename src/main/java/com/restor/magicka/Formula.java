package com.restor.magicka;

import java.util.ArrayList;
import java.util.List;

/** Pure rules shared by the authoritative server, HUD and tests. */
public final class Formula {
    private final List<Element> elements = new ArrayList<>(5);
    public boolean add(Element element) {
        if (elements.size() == MAX) return false;
        elements.add(element);
        return true;
    }
    public void clear() { elements.clear(); }
    public List<Element> elements() { return List.copyOf(elements); }
    public int count(Element element) { return (int) elements.stream().filter(e -> e == element).count(); }
    public boolean has(Element element) { return count(element) > 0; }
    public boolean empty() { return elements.isEmpty(); }
    public int size() { return elements.size(); }
    public int budget() { return new int[]{0, 1, 3, 8, 16, 28}[size()]; }
    public static final int MAX = 5;
    private static final int WIDE = 1 << 16;
    private static final int FIVE = 1 << 20;
    public int packed() {
        if (empty()) return 0;
        int bits = size() > 4 ? FIVE : WIDE;
        for (int i = 0; i < size(); i++) bits |= (elements.get(i).ordinal() + 1) << (i * 4);
        return bits;
    }
    public static Formula unpack(int bits) {
        Formula formula = new Formula();
        boolean five = (bits & FIVE) != 0, wide = five || (bits & WIDE) != 0;
        for (int i = 0; i < (five ? MAX : 4); i++) {
            int value = wide ? (bits >> (i * 4)) & 15 : (bits >> (i * 3)) & 7;
            if (value > 0 && value <= Element.values().length) formula.add(Element.values()[value - 1]);
        }
        return formula;
    }
    /** Presets saved before the eighth element used three bits per slot. */
    private int legacyPacked() {
        int bits = 0;
        for (int i = 0; i < size(); i++) bits |= (elements.get(i).ordinal() + 1) << (i * 3);
        return bits;
    }
    public static boolean valid(int bits) {
        if (bits <= 0) return false;
        Formula formula = unpack(bits);
        return !formula.empty() && (formula.packed() == bits || formula.legacyPacked() == bits && !formula.has(Element.DEATH));
    }
    public SpellFormula key() { return SpellFormula.of(this); }
    public boolean construction() { return !empty() && RecipeRegistry.resolve(key()).shape().construction(); }
}
