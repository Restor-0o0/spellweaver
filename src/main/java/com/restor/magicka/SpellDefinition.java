package com.restor.magicka;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

public record SpellDefinition(ResourceLocation id, SpellFormula formula, String name, String tier, String tags,
        SpellShape shape, OriginMode origin, int castTicks, List<JsonObject> effects, List<JsonObject> selfEffects,
        String selfMode, JsonObject delivery, JsonObject construction, String primaryText, String selfText, JsonObject source) {
    public static SpellDefinition parse(ResourceLocation id, JsonObject json) {
        JsonObject f = json.getAsJsonObject("formula");
        if (f == null) throw new IllegalArgumentException(id + ": missing formula");
        for (String key : f.keySet()) Element.valueOf(key.toUpperCase(Locale.ROOT));
        SpellFormula formula = new SpellFormula(count(f,"fire"), count(f,"water"), count(f,"cold"), count(f,"lightning"), count(f,"earth"), count(f,"life"), count(f,"arcane"), count(f,"death"));
        if (formula.size() == 0) throw new IllegalArgumentException(id + ": empty recipe");
        JsonObject self = object(json, "self");
        List<JsonObject> effects = graph(json, "effects");
        if (effects.isEmpty()) throw new IllegalArgumentException(id + ": empty effect graph");
        List<JsonObject> selfEffects = self.has("effects") ? graph(self, "effects") : genericSelf(formula);
        String mode = string(self,"mode","generic");
        if (!Set.of("generic","effects","nova","dash","zone","summon","construct","meteor").contains(mode)) throw new IllegalArgumentException("Unknown self mode " + mode);
        if (!Set.of("default","instant","channel").contains(string(json,"cast_mode","default"))) throw new IllegalArgumentException("Unknown cast mode " + string(json,"cast_mode",""));
        String incantation = string(json,"incantation","");
        if (!incantation.isEmpty()) {
            SpellFormula spoken = ritual(id,incantation);
            if (!spoken.equals(formula)) throw new IllegalArgumentException(id + ": incantation does not spell the formula");
        }
        JsonObject construction = object(json, "construction");
        String material = string(construction,"material","earth");
        if (!Set.of("earth","ice","root","arcane","copper").contains(material)) throw new IllegalArgumentException("Unknown material " + material);
        return new SpellDefinition(id, formula, string(json,"name",id.getPath()), string(json,"tier","Generic Fusion"), string(json,"tags",""),
                SpellShape.valueOf(string(json,"shape","projectile").toUpperCase(Locale.ROOT)),
                OriginMode.valueOf(string(json,"origin","caster_front").toUpperCase(Locale.ROOT)),
                integer(json,"cast_ticks", new int[]{0,5,9,14,20,27}[formula.size()],1,200), effects, selfEffects, mode,
                object(json,"delivery"), construction, string(json,"primary",""), string(json,"self_description",""), json.deepCopy());
    }
    /** Datapacks may omit Self: literal elemental rules still execute. */
    private static List<JsonObject> genericSelf(SpellFormula formula) {
        var result=new ArrayList<JsonObject>();
        if(formula.water()>0)result.add(node("set_wet","duration_ticks",100+60*formula.water()));
        if(formula.fire()>0){var damage=node("deal_damage","amount",2*formula.fire());damage.addProperty("element","fire");result.add(damage);result.add(node("modify_temperature","amount",40*formula.fire()));}
        if(formula.cold()>0)result.add(node("modify_temperature","amount",-40*formula.cold()));
        if(formula.lightning()>0)result.add(node("add_charge","amount",35*formula.lightning()));
        if(formula.earth()>0){result.add(status("stone_shell",140,formula.earth()));result.add(status("chilled",110,formula.earth()));}
        if(formula.life()>0){result.add(node("heal","amount",2*formula.life()));result.add(status("regenerating",140,formula.life()));}
        if(formula.arcane()>0)result.add(status("arcane_unstable",100,formula.arcane()));
        if(formula.death()>0)result.add(status("withering",80,formula.death()));
        return List.copyOf(result);
    }
    private static JsonObject node(String type,String key,int value){var n=new JsonObject();n.addProperty("type",type);n.addProperty(key,value);return n;}
    private static JsonObject status(String status,int duration,int strength){var n=node("apply_status","duration_ticks",duration);n.addProperty("status",status);n.addProperty("strength",strength);return n;}
    private static int count(JsonObject f, String key) { return integer(f,key,0,0,Formula.MAX); }
    public static JsonObject object(JsonObject json, String key) { return json.has(key) ? json.getAsJsonObject(key).deepCopy() : new JsonObject(); }
    public static String string(JsonObject json, String key, String fallback) { return json.has(key) ? json.get(key).getAsString() : fallback; }
    public String incantation() { return string(source,"incantation",""); }
    /** Ultimates are only unlocked by typing their elements in a fixed order; other spells accept any order. */
    public List<Element> spoken() {
        String order = incantation();
        if (order.isEmpty()) return formula.elements();
        var elements = new ArrayList<Element>();
        for (int i = 0; i < order.length(); i++) elements.add(Element.values()[order.charAt(i) - '1']);
        return elements;
    }
    private static SpellFormula ritual(ResourceLocation id, String order) {
        Formula formula = new Formula();
        for (int i = 0; i < order.length(); i++) {
            int index = order.charAt(i) - '1';
            if (index < 0 || index >= Element.values().length || !formula.add(Element.values()[index])) throw new IllegalArgumentException(id + ": bad incantation " + order);
        }
        return formula.key();
    }
    public static int integer(JsonObject json, String key, int fallback, int min, int max) {
        if (!json.has(key)) return fallback;
        double number = json.get(key).getAsDouble();
        if (!Double.isFinite(number) || number != Math.floor(number) || number < min || number > max) throw new IllegalArgumentException("Invalid " + key + ": " + number);
        return (int) number;
    }
    public double number(String key, double fallback) {
        double value = delivery.has(key) ? delivery.get(key).getAsDouble() : fallback;
        if (!Double.isFinite(value)) return fallback;
        return Math.clamp(value, 0, 72000);
    }
    public String langKey() { return "spell." + id.getNamespace() + "." + id.getPath(); }
    public net.minecraft.network.chat.MutableComponent displayName() { return text("", name); }
    public net.minecraft.network.chat.MutableComponent primaryDescription() { return text(".desc", primaryText); }
    public net.minecraft.network.chat.MutableComponent selfDescription() { return text(".self", selfText); }
    private net.minecraft.network.chat.MutableComponent text(String suffix, String fallback) {
        return net.minecraft.network.chat.Component.translatableWithFallback(langKey() + suffix, fallback.replace("%", "%%"));
    }
    public boolean channeled() {
        String mode = string(source,"cast_mode","default");
        if (!mode.equals("default")) return mode.equals("channel");
        return switch(shape) { case BEAM, SPRAY, BRIDGE, STAIR, RAMP, GROWTH -> true; case CHAIN -> formula.size() >= 4; default -> false; };
    }
    public int budget() {
        int budget = integer(construction,"budget", new int[]{0,1,3,8,16,28}[formula.size()],1,4096);
        return construction.has("uncapped") ? budget : Math.min(MagicConfig.MAX_BLOCKS.get(), budget);
    }
    public int interval() { return integer(construction,"interval_ticks",material().equals("ice") ? 2 : 4,1,40); }
    public String material() { return string(construction,"material","earth"); }
    public boolean floating() { return construction.has("floating") && construction.get("floating").getAsBoolean(); }
    public static List<JsonObject> graph(JsonObject json, String field) {
        if (!json.has(field)) return List.of();
        var result = new ArrayList<JsonObject>();
        for (JsonElement e : json.getAsJsonArray(field)) { JsonObject node = e.getAsJsonObject().deepCopy(); EffectGraph.validate(node,0); result.add(node); }
        if (result.size() > 64) throw new IllegalArgumentException("Effect graph too large");
        return List.copyOf(result);
    }
}
