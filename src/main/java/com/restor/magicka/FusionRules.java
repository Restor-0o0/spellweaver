package com.restor.magicka;

import com.google.gson.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;

/** The spell cast by an element set that matches no named sequence: the lead element picks the form, every element adds its mechanic, pairs react. */
public final class FusionRules {
    private static final List<Element> PRIORITY = List.of(Element.EARTH, Element.LIGHTNING, Element.ARCANE, Element.DEATH, Element.LIFE, Element.COLD, Element.WATER, Element.FIRE);
    private static final double[] UNIT = {0, 1, 1.4, 1.8, 2.4, 3};
    private record Mark(ServerPlayer caster, long until) {}
    private static final Map<UUID, Mark> REBIRTH = new java.util.concurrent.ConcurrentHashMap<>();
    public static final int REBIRTH_TICKS = 400;

    public static Element lead(SpellFormula f) {
        return PRIORITY.stream().max(Comparator.comparingInt(f::count).thenComparingInt(e -> -PRIORITY.indexOf(e))).orElseThrow();
    }

    public static JsonObject build(SpellFormula f) {
        int n = f.size(); double u = UNIT[n];
        int fire = f.fire(), water = f.water(), cold = f.cold(), lightning = f.lightning(), earth = f.earth(), life = f.life(), arcane = f.arcane(), death = f.death();
        Element lead = lead(f);
        var json = new JsonObject(); var counts = new JsonObject(); var delivery = new JsonObject(); var effects = new JsonArray();
        for (Element e : Element.values()) if (f.count(e) > 0) counts.addProperty(e.name().toLowerCase(Locale.ROOT), f.count(e));
        String shape = switch (lead) {
            case EARTH -> n >= 3 ? "burst_projectile" : "projectile";
            case COLD -> water > 0 ? "projectile" : "spray";
            case ARCANE, DEATH, LIFE -> "beam";
            case LIGHTNING -> "chain";
            default -> "spray";
        };
        double catalysis = arcane > 0 ? 1.5 : 1;
        delivery.addProperty("radius", (1.5 + .5 * n) * catalysis);
        delivery.addProperty("burst_radius", (1.6 + .5 * n) * catalysis);
        delivery.addProperty("speed", .8 + .06 * n);
        delivery.addProperty("duration_ticks", 80 + 40 * n);
        delivery.addProperty("interval_ticks", 20);
        delivery.addProperty("gravity", lead == Element.EARTH ? .03 : 0);
        if (lead == Element.COLD && water > 0) delivery.addProperty("fan", n >= 4 ? 5 : 3);
        if (shape.equals("beam")) { delivery.addProperty("beam_width", .12 + .04 * n); if (n >= 4) delivery.addProperty("end_burst", (1 + .4 * n) * catalysis); }
        if (fire > 0 && lightning > 0) delivery.addProperty("plasma", true);

        if (fire > 0 && cold > 0) effects.add(node("thermal_shock", "amount", 2 * u, "duration_ticks", 120));
        if (fire > 0) { effects.add(damage("fire", u * fire)); effects.add(node("modify_temperature", "amount", 40 * fire)); }
        if (water > 0) { effects.add(node("set_wet", "duration_ticks", 120 + 40 * water)); effects.add(node("apply_force", "amount", water >= 3 ? 1.6 : .2 * water, "lift", water >= 3 ? .35 : .05)); }
        if (cold > 0) {
            effects.add(damage("cold", .5 * u * cold)); effects.add(node("modify_temperature", "amount", -40 * cold));
            if (cold >= 3 || water > 0) effects.add(status("frozen", cold >= 3 ? 60 : 40, 1));
        }
        if (lightning > 0) {
            effects.add(damage("lightning", u * lightning)); effects.add(node("add_charge", "amount", 35 * lightning));
            int jumps = (2 + 2 * lightning) * (water > 0 ? 2 : 1) + (lightning >= 3 ? 4 : 0);
            effects.add(node("chain_targets", "count", jumps, "range", 4 + lightning, "amount", .6 * u * lightning));
            if (earth > 0) effects.add(status("stunned", 30, 1));
        }
        if (earth > 0) {
            effects.add(damage("earth", 1.2 * u * earth)); effects.add(node("apply_force", "amount", .25 * earth, "lift", .12));
            if (earth >= 2) effects.add(status("stunned", 15 + 10 * earth, 1));
            effects.add(node("destroy_blocks", "budget", 2 + 2 * earth, "radius", .5 + .3 * earth));
            if (earth >= 3) effects.add(node("heave_blocks", "budget", 10, "radius", 3, "lift", .6));
            if (cold > 0) effects.add(conditional("was_frozen", damage("earth", 2 * u)));
            if (life > 0) effects.add(status("rooted", 60, 1));
        }
        if (fire >= 3 || fire > 0 && earth > 0) effects.add(node("ignite_ground", "radius", 1 + .4 * n));
        if (life > 0) {
            var mend = conditional("ally", node("heal", "amount", 2.5 * life));
            if (life >= 3) mend.getAsJsonArray("effects").add(status("regenerating", 160, 2));
            effects.add(mend);
            effects.add(conditional("undead", damage("life", 2 * u * life)));
            effects.add(node("grow_plants", "budget", life));
        }
        if (arcane > 0) {
            effects.add(damage("arcane", u * arcane)); effects.add(status("arcane_unstable", 120, arcane));
            if (arcane >= 3) { var mark = node("delay", "ticks", 40); mark.add("effects", array(damage("arcane", 2 * u * arcane))); effects.add(mark); }
        }
        if (death > 0) {
            effects.add(node("drain_life", "amount", u * death, "fraction", .5)); effects.add(status("withering", 80 + 20 * death, death));
            if (cold > 0) effects.add(node("drain_life", "amount", .6 * u * cold, "fraction", .5));
            if (death >= 3) effects.add(conditional("low_health", damage("death", 40)));
            if (life > 0) effects.add(node("rebirth", "duration_ticks", 60));
        }
        if (fire > 0 && water > 0) effects.add(status("obscured", 40, 1));

        json.add("formula", counts); json.addProperty("name", "Fusion"); json.addProperty("tier", "Generic Fusion"); json.addProperty("fusion", true);
        json.addProperty("shape", shape); json.addProperty("origin", shape.equals("chain") ? "aim_hit" : "caster_front");
        json.add("delivery", delivery); json.add("effects", effects);
        return json;
    }

    /** Marks a victim so that dying soon after rises it on the caster's side. */
    public static void markRebirth(ServerPlayer caster, LivingEntity target, int ticks) {
        long now = caster.level.getGameTime();
        REBIRTH.values().removeIf(mark -> mark.until < now);
        REBIRTH.put(target.getUUID(), new Mark(caster, now + ticks));
    }

    public static boolean reborn(ServerLevel level, LivingEntity dead) {
        var mark = REBIRTH.remove(dead.getUUID());
        return mark != null && mark.until >= level.getGameTime() && !mark.caster.isRemoved() && mark.caster.level == level
                && UndeadMinions.spawn(mark.caster, dead.getType(), dead.position(), 0, REBIRTH_TICKS, .6);
    }

    private static JsonObject node(String type, Object... pairs) {
        var n = new JsonObject(); n.addProperty("type", type);
        for (int i = 0; i < pairs.length; i += 2) n.addProperty((String) pairs[i], round((Number) pairs[i + 1]));
        return n;
    }
    private static Number round(Number value) { return value instanceof Double d ? Math.round(d * 100) / 100.0 : value; }
    private static JsonObject damage(String element, double amount) { var n = node("deal_damage", "amount", amount); n.addProperty("element", element); return n; }
    private static JsonObject status(String status, int ticks, int strength) { var n = node("apply_status", "duration_ticks", ticks, "strength", strength); n.addProperty("status", status); return n; }
    private static JsonObject conditional(String condition, JsonObject child) { var n = node("conditional"); n.addProperty("condition", condition); n.add("effects", array(child)); return n; }
    private static JsonArray array(JsonObject... nodes) { var a = new JsonArray(); for (var n : nodes) a.add(n); return a; }

    private FusionRules() {}
}
