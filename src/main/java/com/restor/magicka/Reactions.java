package com.restor.magicka;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What happens when an element is typed onto a queue. Opposites annihilate; an element fuses with the one typed right before it:
 * Water with Fire into Steam, Water with Cold into Ice, Arcane with Earth into Shield.
 * Fire melts Ice and Cold condenses Steam back into Water. Annihilation, melting and condensing yield when the sequence leads to a named spell.
 */
public final class Reactions {
    public enum Kind { NONE, CANCEL, FUSE, MELT }
    public record Result(Kind kind, int index, Element became) {
        static final Result NONE = new Result(Kind.NONE, -1, null);
    }
    private static Set<String> prefixes;
    private static Collection<SpellDefinition> source;

    public static boolean opposed(Element a, Element b) {
        return switch (a) {
            case FIRE -> b == Element.COLD;
            case COLD -> b == Element.FIRE;
            case WATER -> b == Element.LIGHTNING;
            case LIGHTNING -> b == Element.WATER || b == Element.EARTH;
            case EARTH -> b == Element.LIGHTNING;
            case LIFE -> b == Element.DEATH;
            case DEATH -> b == Element.LIFE;
            default -> false;
        };
    }
    /** The derived element that the next element breaks back into Water, or null. */
    public static Element dissolves(Element next) {
        return switch (next) { case FIRE -> Element.ICE; case COLD -> Element.STEAM; default -> null; };
    }
    public static String key(List<Element> elements) {
        StringBuilder b = new StringBuilder();
        for (Element e : elements) b.append(e.code());
        return b.toString();
    }
    static synchronized Set<String> prefixes() {
        Collection<SpellDefinition> recipes = RecipeRegistry.recipes();
        if (prefixes == null || source != recipes) {
            Set<String> built = new HashSet<>();
            for (SpellDefinition d : recipes) {
                if (!d.ordered()) continue;
                List<Element> state = new ArrayList<>();
                for (Element k : d.keys()) { built.add(key(state) + k.code()); fuse(state, k); }
            }
            prefixes = built; source = recipes;
        }
        return prefixes;
    }
    public static boolean guarded(List<Element> queued, Element next) {
        var sequence = new ArrayList<>(queued);
        sequence.add(next);
        return prefixes().contains(key(sequence));
    }
    private static int latest(List<Element> queued, java.util.function.Predicate<Element> test) {
        for (int i = queued.size() - 1; i >= 0; i--) if (test.test(queued.get(i))) return i;
        return -1;
    }
    private static int partner(List<Element> queue, Element next) {
        return !queue.isEmpty() && Element.fuse(queue.get(queue.size()-1), next) != null ? queue.size() - 1 : -1;
    }
    private static void fuse(List<Element> queue, Element next) {
        int partner = partner(queue, next);
        if (partner >= 0) queue.set(partner, Element.fuse(queue.get(partner), next));
        else queue.add(next);
    }
    /** The queue that a key sequence leaves when only fusion applies. */
    public static List<Element> fused(List<Element> keys) {
        List<Element> queue = new ArrayList<>();
        for (Element k : keys) fuse(queue, k);
        return queue;
    }
    public static Result react(List<Element> queued, Element next, boolean cancelOpposites) {
        int opposite = cancelOpposites ? latest(queued, e -> opposed(e, next)) : -1;
        if (opposite >= 0 && !guarded(queued, next)) return new Result(Kind.CANCEL, opposite, null);
        int partner = partner(queued, next);
        if (partner >= 0) return new Result(Kind.FUSE, partner, Element.fuse(queued.get(partner), next));
        Element brittle = dissolves(next);
        int melt = brittle == null ? -1 : latest(queued, e -> e == brittle);
        if (melt >= 0 && !guarded(queued, next)) return new Result(Kind.MELT, melt, Element.WATER);
        return Result.NONE;
    }
    /** Applies the reaction to the formula; returns NONE when the element should simply be added. */
    public static Result apply(Formula formula, StringBuilder typed, Element next, boolean cancelOpposites) {
        Result r = react(formula.elements(), next, cancelOpposites);
        switch (r.kind) {
            case CANCEL -> formula.remove(r.index);
            case FUSE, MELT -> formula.set(r.index, r.became);
            default -> { return r; }
        }
        typed.setLength(0);
        typed.append(key(formula.elements()));
        return r;
    }
    /** Types a whole sequence the way a player would, reactions included. */
    public static Formula type(List<Element> sequence, boolean cancelOpposites) {
        Formula f = new Formula();
        StringBuilder typed = new StringBuilder();
        for (Element e : sequence) if (apply(f, typed, e, cancelOpposites).kind() == Kind.NONE) f.add(e);
        return f;
    }
}
