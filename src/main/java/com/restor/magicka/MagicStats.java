package com.restor.magicka;

import com.google.gson.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

public final class MagicStats {
    public enum Stat {
        FIRE(0,2000), WATER(0,2000), COLD(0,2000), LIGHTNING(0,2000), EARTH(0,2000), LIFE(0,2000), ARCANE(0,2000), DEATH(0,2000),
        DAMAGE(0,5000), HEALING(0,5000), AREA(10,1000), DURATION(10,2000), DESTRUCTION(0,5000), SUMMONS(0,5000),
        CONSTRUCTION(10,2000), CHAINS(0,5000), FORCE(0,2000), CAST_SPEED(10,2000);
        public final int min, max;
        Stat(int min,int max){this.min=min;this.max=max;}
        public String key(){return name().toLowerCase(Locale.ROOT);}
        public int clamp(int value){return Math.clamp(value,min,max);}
    }
    public static final int COUNT = Stat.values().length, DEFAULT = 100;
    private static final String TAG = "magicka_stats";
    private record Cached(SpellDefinition base, SpellDefinition scaled) {}
    private static final Map<UUID, Map<String, Cached>> CACHE = new HashMap<>();

    public static int[] defaults(){int[] v=new int[COUNT];Arrays.fill(v,DEFAULT);return v;}
    public static int[] get(Player player){
        int[] values=defaults();CompoundTag tag=player.getPersistentData().getCompound(TAG);
        for(Stat s:Stat.values())if(tag.contains(s.key()))values[s.ordinal()]=s.clamp(tag.getInt(s.key()));
        return values;
    }
    public static void set(ServerPlayer player,int index,int value){
        if(index<0 || index>=COUNT)return;
        Stat stat=Stat.values()[index];var data=player.getPersistentData();CompoundTag tag=data.getCompound(TAG);
        tag.putInt(stat.key(),stat.clamp(value));
        data.put(TAG,tag);CACHE.remove(player.getUUID());sync(player);
    }
    public static double power(Player player,Stat stat){return MagicGear.total(player)[stat.ordinal()]/100.0;}
    public static void reset(ServerPlayer player){player.getPersistentData().remove(TAG);CACHE.remove(player.getUUID());sync(player);}
    public static void invalidate(ServerPlayer player){CACHE.remove(player.getUUID());sync(player);}
    public static void sync(ServerPlayer player){PacketDistributor.sendToPlayer(player,new MagicNetwork.Stats(get(player),MagicGear.deltas(player)));}
    public static int minionCap(ServerPlayer owner){int[] v=MagicGear.total(owner);return (int)Math.clamp(Math.round(MagicConfig.MAX_MINIONS.get()*Math.max(1,v[Stat.SUMMONS.ordinal()]/100.0*v[Stat.DEATH.ordinal()]/100.0)),1,512);}

    public static SpellDefinition resolve(ServerPlayer player,SpellFormula key){return apply(player,SpellScrolls.resolve(player,RecipeRegistry.resolve(MagicGear.transmute(player,key))));}
    public static SpellDefinition apply(ServerPlayer player,SpellDefinition base){
        if(base==null)return null;
        var cache=CACHE.computeIfAbsent(player.getUUID(),id->new HashMap<>());
        String key=base.id().toString()+"|"+base.formula();
        Cached hit=cache.get(key);
        if(hit!=null && hit.base==base)return hit.scaled;
        int[] values=MagicGear.total(player);
        double bias=MagicGear.castBias(player,base.formula().size());
        SpellDefinition patched=MagicGear.patch(player,base),scaled;
        if(patched==base && bias==1 && Arrays.stream(values).allMatch(v->v==DEFAULT))scaled=base;
        else try{scaled=scale(patched,values,bias);}catch(RuntimeException e){scaled=patched;}
        cache.put(key,new Cached(base,scaled));
        return scaled;
    }

    static SpellDefinition scale(SpellDefinition base,int[] v){return scale(base,v,1);}
    static SpellDefinition scale(SpellDefinition base,int[] v,double bias){
        java.util.function.ToDoubleFunction<Stat> m=s->v[s.ordinal()]/100.0;
        var f=base.formula();
        double power=0;int total=0;
        for(Element e:f.elements()){power+=element(v,e.name());total++;}
        power=total==0?1:power/total;
        double area=m.applyAsDouble(Stat.AREA)*Math.sqrt(power), duration=m.applyAsDouble(Stat.DURATION);
        JsonObject json=base.source().deepCopy();
        json.addProperty("cast_ticks",Math.clamp(Math.round(base.castTicks()*bias/m.applyAsDouble(Stat.CAST_SPEED)),1,200));
        JsonObject d=json.has("delivery")?json.getAsJsonObject("delivery"):new JsonObject();json.add("delivery",d);
        if(!d.has("radius"))d.addProperty("radius",3.0);
        if(!d.has("burst_radius"))d.addProperty("burst_radius",2.0);
        if(!d.has("duration_ticks") && (base.shape()==SpellShape.TARGETED_ZONE || base.shape()==SpellShape.MINE || base.selfMode().equals("zone")))d.addProperty("duration_ticks",base.shape()==SpellShape.MINE?200:160);
        for(String k:List.of("radius","burst_radius","end_burst"))mul(d,k,area,.2,64);
        mul(d,"duration_ticks",duration,1,72000);mul(d,"lift_ticks",duration,1,72000);
        mul(d,"targets",m.applyAsDouble(Stat.CHAINS)*m.applyAsDouble(Stat.LIGHTNING),1,64);
        JsonObject c=json.has("construction")?json.getAsJsonObject("construction"):new JsonObject();
        if(base.shape().construction() || base.selfMode().equals("construct") || c.size()>0){
            double cm=m.applyAsDouble(Stat.CONSTRUCTION);
            if(cm!=1){c.addProperty("budget",Math.clamp(Math.round(base.budget()*cm),1,4096));c.addProperty("uncapped",true);json.add("construction",c);}
            mul(c,"lifetime_ticks",duration,1,72000);
        }
        scaleGraph(json.getAsJsonArray("effects"),v,area,duration);
        JsonObject self=json.has("self")?json.getAsJsonObject("self"):new JsonObject();json.add("self",self);
        if(!self.has("effects")){var array=new JsonArray();for(JsonObject n:base.selfEffects())array.add(n.deepCopy());self.add("effects",array);}
        scaleGraph(self.getAsJsonArray("effects"),v,area,duration);
        return SpellDefinition.parse(base.id(),json);
    }
    private static void scaleGraph(JsonArray graph,int[] v,double area,double duration){
        if(graph==null)return;
        java.util.function.ToDoubleFunction<Stat> m=s->v[s.ordinal()]/100.0;
        for(JsonElement el:graph){
            JsonObject n=el.getAsJsonObject();String type=EffectGraph.text(n,"type","");
            switch(type){
                case "deal_damage" -> mul(n,"amount",m.applyAsDouble(Stat.DAMAGE)*element(v,EffectGraph.text(n,"element","arcane")),0,72000);
                case "drain_life" -> mul(n,"amount",m.applyAsDouble(Stat.DAMAGE)*m.applyAsDouble(Stat.DEATH),0,72000);
                case "heal" -> mul(n,"amount",m.applyAsDouble(Stat.HEALING)*m.applyAsDouble(Stat.LIFE),0,72000);
                case "modify_temperature" -> mul(n,"amount",EffectGraph.number(n,"amount",0)>0?m.applyAsDouble(Stat.FIRE):m.applyAsDouble(Stat.COLD),-72000,72000);
                case "add_charge" -> mul(n,"amount",m.applyAsDouble(Stat.LIGHTNING),0,72000);
                case "apply_force" -> {mul(n,"amount",m.applyAsDouble(Stat.FORCE),-72000,72000);mul(n,"lift",m.applyAsDouble(Stat.FORCE),-72000,72000);}
                case "set_wet" -> mul(n,"duration_ticks",duration*m.applyAsDouble(Stat.WATER),1,72000);
                case "apply_status","apply_effect" -> mul(n,"duration_ticks",duration,1,72000);
                case "destroy_blocks" -> {
                    double destruction=m.applyAsDouble(Stat.DESTRUCTION)*m.applyAsDouble(Stat.EARTH);
                    if(!n.has("budget"))n.addProperty("budget",1);if(!n.has("radius"))n.addProperty("radius",1);
                    mul(n,"budget",destruction*area*area,0,4096);mul(n,"radius",area*Math.cbrt(Math.max(destruction,.001)),0,16);
                }
                case "heave_blocks" -> {
                    if(!n.has("budget"))n.addProperty("budget",8);if(!n.has("radius"))n.addProperty("radius",3);
                    mul(n,"budget",m.applyAsDouble(Stat.EARTH)*area,0,256);mul(n,"radius",area,0,16);
                }
                case "brittle_terrain" -> mul(n,"radius",area,0,16);
                case "grow_plants" -> mul(n,"budget",m.applyAsDouble(Stat.LIFE)*area*area,0,4096);
                case "summon_undead","raise_dead" -> {
                    if(!n.has("count"))n.addProperty("count",type.equals("raise_dead")?3:1);
                    mul(n,"count",m.applyAsDouble(Stat.SUMMONS)*m.applyAsDouble(Stat.DEATH),0,128);
                    mul(n,"duration_ticks",duration,20,72000);mul(n,"radius",area,0,64);
                }
                case "chain_targets" -> {
                    if(!n.has("count"))n.addProperty("count",4);if(!n.has("range"))n.addProperty("range",4);if(!n.has("amount"))n.addProperty("amount",2);
                    mul(n,"count",m.applyAsDouble(Stat.CHAINS),0,128);mul(n,"range",m.applyAsDouble(Stat.AREA),0,48);
                    mul(n,"amount",m.applyAsDouble(Stat.DAMAGE)*m.applyAsDouble(Stat.LIGHTNING),0,72000);
                }
                case "spawn_zone" -> {mul(n,"duration_ticks",duration,1,72000);mul(n,"radius",area,0,64);}
                case "reveal" -> {mul(n,"radius",area,1,128);mul(n,"duration_ticks",duration,20,72000);}
                case "extinguish","light_ring" -> mul(n,"radius",area,1,64);
                case "revive" -> mul(n,"health_fraction",m.applyAsDouble(Stat.HEALING)*m.applyAsDouble(Stat.LIFE),.05,1);
                default -> {}
            }
            if(n.has("effects"))scaleGraph(n.getAsJsonArray("effects"),v,area,duration);
        }
    }
    private static double element(int[] v,String name){
        String key=name.toLowerCase(Locale.ROOT);
        if(key.equals("thermal"))return (v[Stat.FIRE.ordinal()]+v[Stat.COLD.ordinal()])/200.0;
        for(Stat s:Stat.values())if(s.ordinal()<8 && s.key().equals(key))return v[s.ordinal()]/100.0;
        return v[Stat.ARCANE.ordinal()]/100.0;
    }
    private static void mul(JsonObject o,String key,double factor,double min,double max){
        if(!o.has(key) || !o.get(key).isJsonPrimitive() || !o.getAsJsonPrimitive(key).isNumber())return;
        double base=o.get(key).getAsDouble(), value=Math.clamp(base*factor,min,max);
        boolean integer=base==Math.floor(base) && !key.equals("radius") && !key.equals("range") && !key.equals("amount") && !key.endsWith("_radius") && !key.equals("end_burst") && !key.equals("lift") && !key.equals("health_fraction");
        if(integer)o.addProperty(key,(long)Math.round(value));else o.addProperty(key,round(value));
    }
    private static double round(double value){return Math.round(value*1000)/1000.0;}

    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event){if(event.getEntity() instanceof ServerPlayer p)sync(p);}
    @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent event){if(event.getEntity() instanceof ServerPlayer p)sync(p);}
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event){CACHE.remove(event.getEntity().getUUID());}
    @SubscribeEvent public void clone(PlayerEvent.Clone event){
        var tag=event.getOriginal().getPersistentData().getCompound(TAG);
        if(!tag.isEmpty())event.getEntity().getPersistentData().put(TAG,tag.copy());
        CACHE.remove(event.getEntity().getUUID());
    }
    public MagicStats() {}
}
