package com.restor.magicka;

import com.google.gson.JsonObject;
import java.util.*;

public final class BalanceRules {
    public record Plan(String group,int recovery,int cooldown,int[] elements,double mana,double manaRate,double strain,double strainRate,String activeGroup,int activeLimit) {}
    private static final Map<List<? extends String>,Map<String,Double>> ROWS=new HashMap<>();
    private static List<? extends String> overrideRows=List.of();
    private static final Map<String,JsonObject> OVERRIDES=new HashMap<>();
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(BalanceRules.class);
    public static String group(SpellDefinition s){
        if(s.ultimate() || s.epic())return "ultimate";
        if(SpellRuntime.mining(s))return "mining";
        if(s.shape().construction())return "construction";
        if(s.shape()==SpellShape.SUMMON_OBJECT || s.selfMode().equals("summon") || has(s,Set.of("summon_undead","raise_dead"),Set.of()))return "summon";
        if(s.shape()==SpellShape.TARGETED_ZONE || s.shape()==SpellShape.MINE || s.selfMode().equals("zone") || s.delivery().has("hot_zone") || has(s,Set.of("spawn_zone"),Set.of()))return "zone";
        if(has(s,Set.of("polymorph"),Set.of("frozen","stunned","rooted","silenced","polymorphed")))return "control";
        if(java.util.stream.Stream.concat(s.effects().stream(),s.selfEffects().stream()).anyMatch(n->SpellDefinition.string(n,"type","").equals("heal")))return "healing";
        if(s.shape()==SpellShape.BURST_PROJECTILE || s.shape()==SpellShape.TARGETED_EXPLOSION || s.shape()==SpellShape.NOVA)return "burst";
        if(s.channeled())return "channel";
        if(s.shape()==SpellShape.DASH || s.shape()==SpellShape.DIRECT_TARGET)return "utility";
        return "basic";
    }
    private static boolean has(SpellDefinition s,Set<String> types,Set<String> statuses){return s.effects().stream().anyMatch(n->has(n,types,statuses));}
    private static boolean has(com.google.gson.JsonElement element,Set<String> types,Set<String> statuses){
        if(element.isJsonArray()){for(var child:element.getAsJsonArray())if(has(child,types,statuses))return true;}
        if(element.isJsonObject()){var n=element.getAsJsonObject();if(types.contains(SpellDefinition.string(n,"type",""))||statuses.contains(SpellDefinition.string(n,"status","")))return true;for(var child:n.entrySet())if((child.getValue().isJsonObject()||child.getValue().isJsonArray())&&has(child.getValue(),types,statuses))return true;}
        return false;
    }
    static double row(List<? extends String> rows,String key,double fallback){
        if(!ROWS.containsKey(rows)){
            Map<String,Double> values=new HashMap<>();
            for(String line:rows)try{String[] pair=line.split("=",2);double n=Double.parseDouble(pair[1].trim());if(!Double.isFinite(n)||n<0||n>1000000)throw new IllegalArgumentException();values.put(pair[0].trim().toLowerCase(Locale.ROOT),n);}catch(RuntimeException e){LOG.warn("Invalid balance entry: {}",line);}
            if(ROWS.size()>64)ROWS.clear();
            ROWS.put(List.copyOf(rows),values);
        }
        return ROWS.get(rows).getOrDefault(key,fallback);
    }
    private static JsonObject override(SpellDefinition s){
        var rows=BalanceConfig.OVERRIDES.get();
        if(!overrideRows.equals(rows)){
            overrideRows=List.copyOf(rows);OVERRIDES.clear();
            for(String line:rows)try{
                String[] parts=line.split("\\|");var object=new JsonObject();
                if(net.minecraft.resources.Identifier.tryParse(parts[0].trim())==null)throw new IllegalArgumentException();
                for(int i=1;i<parts.length;i++){String[] pair=parts[i].split("=",2);String key=pair[0].trim(),value=pair[1].trim();if(key.equals("group")||key.equals("active_group"))object.addProperty(key,value);else{double n=Double.parseDouble(value);if(!Double.isFinite(n)||n<0||n>1000000)throw new IllegalArgumentException();object.addProperty(key,n);}}
                OVERRIDES.put(parts[0].trim(),object);
            }catch(RuntimeException e){LOG.warn("Invalid spell balance override: {}",line);}
        }
        JsonObject result=s.source().has("balance") && s.source().get("balance").isJsonObject()?s.source().getAsJsonObject("balance").deepCopy():new JsonObject();
        var config=OVERRIDES.get(s.id().toString());if(config!=null)config.entrySet().forEach(e->result.add(e.getKey(),e.getValue()));return result;
    }
    private static double number(JsonObject j,String key,double fallback){try{double n=j.has(key)?j.get(key).getAsDouble():fallback;return Double.isFinite(n)&&n>=0&&n<=1000000?n:fallback;}catch(RuntimeException e){return fallback;}}
    private static String text(JsonObject j,String key,String fallback){try{String value=SpellDefinition.string(j,key,fallback);return value.matches("[a-z0-9_.-]{1,64}")?value:fallback;}catch(RuntimeException e){return fallback;}}
    public static Plan plan(SpellDefinition s,double speed){
        JsonObject j=override(s);String group=text(j,"group",group(s));
        double scale=BalanceConfig.COOLDOWN_SCALE.get()*(BalanceConfig.CAST_SPEED.get()?100/Math.max(1,speed):1);
        double recovery=number(j,"recovery",row(BalanceConfig.RECOVERY_TICKS.get(),group,5));
        int[] elements=new int[Element.values().length];
        for(Element e:Element.values()){
            int count=BalanceConfig.PARENTS.get()?s.formula().total(e):s.formula().count(e);
            if(count>0)elements[e.ordinal()]=(int)Math.min(1000000,Math.ceil(row(BalanceConfig.ELEMENT_TICKS.get(),e.name().toLowerCase(Locale.ROOT),12)*(1+(count-1)*BalanceConfig.ELEMENT_REPEAT.get())*scale));
        }
        double manaWeight=row(BalanceConfig.MANA_WEIGHTS.get(),group,1),strainWeight=row(BalanceConfig.STRAIN_WEIGHTS.get(),group,1);
        String active=text(j,"active_group",Set.of("zone","ultimate","summon").contains(group)?group:"none");
        return new Plan(group,BalanceConfig.recovery() && recovery>0 && scale>0?Math.max(BalanceConfig.MIN_RECOVERY.get(),(int)Math.min(1000000,Math.ceil(recovery*scale))):0,
            BalanceConfig.groups()?(int)Math.min(1000000,Math.ceil(number(j,"cooldown",row(BalanceConfig.GROUP_TICKS.get(),group,0))*scale)):0,elements,
            number(j,"mana",(BalanceConfig.MANA_BASE.get()+s.formula().size()*BalanceConfig.MANA_POINT.get())*manaWeight),number(j,"mana_per_second",BalanceConfig.MANA_CHANNEL.get()*manaWeight),
            number(j,"strain",(BalanceConfig.STRAIN_BASE.get()+s.formula().size()*BalanceConfig.STRAIN_POINT.get())*strainWeight),number(j,"strain_per_second",BalanceConfig.STRAIN_CHANNEL.get()*strainWeight),active,(int)number(j,"active_limit",row(BalanceConfig.ACTIVE_LIMITS.get(),active,0)));
    }
    private BalanceRules(){}
}
