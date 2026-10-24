package com.restor.magicka;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.*;

public record GearRule(Set<Element> elements, Set<SpellShape> shapes, Set<String> tags, int minSize,
                       JsonObject patch, List<JsonObject> append, double damage, double healing,
                       int summons, double summonHealth, double cast, double lifetime) {
    public static GearRule parse(String json) { return parse(JsonParser.parseString(json).getAsJsonObject()); }

    public static GearRule parse(JsonObject json) {
        Set<Element> elements = EnumSet.noneOf(Element.class);
        for (String key : strings(json, "elements")) elements.add(Element.valueOf(key.toUpperCase(Locale.ROOT)));
        Set<SpellShape> shapes = EnumSet.noneOf(SpellShape.class);
        for (String key : strings(json, "shapes")) shapes.add(SpellShape.valueOf(key.toUpperCase(Locale.ROOT)));
        List<JsonObject> append = new ArrayList<>();
        if (json.has("append")) for (JsonElement node : json.getAsJsonArray("append")) {
            JsonObject effect = node.getAsJsonObject().deepCopy();
            EffectGraph.validate(effect, 0);
            append.add(effect);
        }
        double damage = json.has("damage") ? json.get("damage").getAsDouble() : 1, healing = json.has("healing") ? json.get("healing").getAsDouble() : 1;
        double summonHealth = number(json, "summon_health"), cast = number(json, "cast"), lifetime = number(json, "lifetime");
        int summons = json.has("summons") ? json.get("summons").getAsInt() : 0;
        if (!(damage >= 0 && damage <= 10 && healing >= 0 && healing <= 10)) throw new IllegalArgumentException("Gear rule multiplier out of range");
        if (summons < 0 || summons > 3) throw new IllegalArgumentException("Gear rule summons out of range");
        return new GearRule(elements, shapes, Set.copyOf(strings(json, "tags")), json.has("min_size") ? json.get("min_size").getAsInt() : 1,
                json.has("patch") ? json.getAsJsonObject("patch").deepCopy() : new JsonObject(), List.copyOf(append), damage, healing,
                summons, summonHealth, cast, lifetime);
    }

    private static double number(JsonObject json, String key) {
        double value = json.has(key) ? json.get(key).getAsDouble() : 1;
        if (!(value >= .1 && value <= 10)) throw new IllegalArgumentException("Gear rule " + key + " out of range");
        return value;
    }

    private static List<String> strings(JsonObject json, String key) {
        if (!json.has(key)) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonElement e : json.getAsJsonArray(key)) result.add(e.getAsString());
        return result;
    }

    public boolean matches(SpellDefinition spell) {
        if (spell.ultimate() || spell.formula().size() < minSize) return false;
        if (!shapes.isEmpty() && !shapes.contains(spell.shape())) return false;
        if (!elements.isEmpty() && elements.stream().noneMatch(e -> spell.formula().count(e) > 0)) return false;
        return tags.isEmpty() || SpellTags.of(spell).stream().anyMatch(tags::contains);
    }

    public void apply(JsonObject json, SpellDefinition base) {
        MagicGear.merge(json, patch);
        if (!append.isEmpty()) {
            JsonArray effects = json.has("effects") ? json.getAsJsonArray("effects") : new JsonArray();
            for (JsonObject effect : append) if (effects.size() < 64) effects.add(effect.deepCopy());
            json.add("effects", effects);
        }
        if (damage != 1 || healing != 1) {
            scale(json.getAsJsonArray("effects"));
            if (json.has("self") && json.getAsJsonObject("self").has("effects")) scale(json.getAsJsonObject("self").getAsJsonArray("effects"));
        }
        if (summons != 0 || summonHealth != 1) {
            JsonObject delivery = object(json, "delivery");
            delivery.addProperty("summon_extra", (delivery.has("summon_extra") ? delivery.get("summon_extra").getAsInt() : 0) + summons);
            delivery.addProperty("summon_health", (delivery.has("summon_health") ? delivery.get("summon_health").getAsDouble() : 1) * summonHealth);
        }
        if (cast != 1) {
            int ticks = json.has("cast_ticks") ? json.get("cast_ticks").getAsInt() : base.castTicks();
            json.addProperty("cast_ticks", Math.clamp(Math.round(ticks * cast), 1, 200));
            if (base.shape().construction()) {
                JsonObject construction = object(json, "construction");
                int interval = construction.has("interval_ticks") ? construction.get("interval_ticks").getAsInt() : base.interval();
                construction.addProperty("interval_ticks", Math.clamp(Math.round(interval * cast), 1, 40));
            }
        }
        if (lifetime != 1) {
            JsonObject construction = object(json, "construction");
            construction.addProperty("lifetime_scale", (construction.has("lifetime_scale") ? construction.get("lifetime_scale").getAsDouble() : 1) * lifetime);
        }
    }

    private static JsonObject object(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonObject()) json.add(key, new JsonObject());
        return json.getAsJsonObject(key);
    }

    private void scale(JsonArray graph) {
        if (graph == null) return;
        for (JsonElement element : graph) {
            JsonObject node = element.getAsJsonObject();
            String type = EffectGraph.text(node, "type", "");
            double factor = type.equals("deal_damage") || type.equals("drain_life") || type.equals("chain_targets") ? damage : type.equals("heal") ? healing : 1;
            if (factor != 1 && node.has("amount")) node.addProperty("amount", node.get("amount").getAsDouble() * factor);
            if (node.has("effects")) scale(node.getAsJsonArray("effects"));
        }
    }
}
