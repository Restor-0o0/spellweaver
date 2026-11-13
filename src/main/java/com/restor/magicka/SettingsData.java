package com.restor.magicka;

import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;

public final class SettingsData {
    private static final Map<String,net.minecraftforge.fml.config.ModConfig> CONFIGS=new HashMap<>();
    public static void loaded(net.minecraftforge.fml.event.config.ModConfigEvent.Loading event){var config=event.getConfig();if(config.getSpec()==MagicConfig.SPEC||config.getSpec()==BalanceConfig.SPEC){CONFIGS.put(config.getFileName(),config);if(config.getConfigData() instanceof com.electronwill.nightconfig.core.file.FileConfig file)com.electronwill.nightconfig.core.file.FileWatcher.defaultInstance().removeWatch(file.getNioPath());}}
    static java.nio.file.Path configPath(String name){var config=CONFIGS.get(name);if(config==null)throw new IllegalStateException("Config not loaded: "+name);return config.getFullPath().toAbsolutePath().normalize();}
    public static ForgeConfigSpec.ValueSpec spec(ForgeConfigSpec.ConfigValue<?> value){ForgeConfigSpec.ValueSpec general=MagicConfig.SPEC.getSpec().get(value.getPath());return general!=null?general:BalanceConfig.SPEC.getSpec().get(value.getPath());}
    public enum Category { BALANCE, COOLDOWNS, MANA, STRAIN, LIMITS, CASTING, WORLD, PROGRESSION, SCROLLS;
        public String key(){return "settings.magicka.category."+name().toLowerCase(Locale.ROOT);}
    }
    public static Category category(String key){
        if(key.startsWith("balance.")){
            String name=key.substring(8);
            if(name.startsWith("mana")||name.equals("regenerateWhileChanneling")||name.equals("refillManaOnDeath"))return Category.MANA;
            if(name.startsWith("strain"))return Category.STRAIN;
            if(name.startsWith("active")||name.equals("spellOverrides"))return Category.LIMITS;
            if(Set.of("recoveryTicks","groupCooldownTicks","cooldownMultiplier","castSpeedAffectsCooldowns","minimumRecoveryTicks","elementCooldownTicks","elementRepeatMultiplier","derivedElementsShareCooldowns").contains(name))return Category.COOLDOWNS;
            return Category.BALANCE;
        }
        if(key.toLowerCase(Locale.ROOT).contains("scroll")||key.startsWith("boss"))return Category.SCROLLS;
        if(Set.of("mageLevels","mageXpMultiplier","maxMageLevel","formulaLengthByLevel","fourElementsLevel","fiveElementsLevel","castSpeedPerLevel","damagePerLevel","unlockAllSpells").contains(key))return Category.PROGRESSION;
        if(Set.of("maxActiveSpellsPerPlayer","maxUndeadMinionsPerPlayer","revivalWindowTicks").contains(key))return Category.LIMITS;
        if(key.contains("Lifetime")||key.startsWith("permanent")||Set.of("constructiveMagic","allowTerrainDamage","maxConstructionBlocks","collapseWithRoot","specialEnemyNaturalSpawns").contains(key))return Category.WORLD;
        return Category.CASTING;
    }
    public static String translation(String key){return "magicka.configuration."+key;}
    public static Object value(String key,ForgeConfigSpec.ConfigValue<?> value){return (key.startsWith("balance.")?BalanceConfig.SPEC:MagicConfig.SPEC).isLoaded()?value.get():value.getDefault();}
    public static String text(Object value){return value instanceof Enum<?> e?e.name():String.valueOf(value);}
    public static String command(String key,Object value){if(!ConfigCommand.values().containsKey(key))throw new IllegalArgumentException(key);return "/spellweaver config "+key+" "+(value instanceof List<?>?new GsonBuilder().disableHtmlEscaping().create().toJson(value):text(value));}
    public static Object parse(String key,String text){var value=ConfigCommand.values().get(key);if(value==null)return null;Object parsed=ConfigCommand.parse(value.getDefault(),text);return valid(key,parsed)?parsed:null;}
    public static boolean valid(String key,Object value){
        var spec=ConfigCommand.values().get(key);if(spec==null||value==null||!SettingsData.spec(spec).test(value))return false;
        if(value instanceof Number n && !Double.isFinite(n.doubleValue()))return false;
        if(value instanceof List<?> list){
            if(list.size()>256)return false;
            for(Object row:list){if(!(row instanceof String s)||s.length()>1024)return false;
                if(key.equals("balance.spellOverrides")){if(!overrideValid(s))return false;}
                else{String[] pair=s.split("=",-1);if(pair.length!=2||!pair[0].trim().matches("[a-z0-9_.-]{1,64}"))return false;try{if(key.equals("balance.elementCooldownTicks"))Element.valueOf(pair[0].trim().toUpperCase(Locale.ROOT));double n=Double.parseDouble(pair[1].trim());if(!Double.isFinite(n)||n<0||n>1000000)return false;}catch(IllegalArgumentException e){return false;}}
            }
        }
        return true;
    }
    private static boolean overrideValid(String row){
        String[] parts=row.split("\\|",-1);if(net.minecraft.resources.ResourceLocation.tryParse(parts[0].trim())==null||parts.length<2)return false;
        Set<String> seen=new HashSet<>();
        for(int i=1;i<parts.length;i++){String[] pair=parts[i].split("=",-1);if(pair.length!=2||!seen.add(pair[0].trim()))return false;String key=pair[0].trim(),value=pair[1].trim();
            if(key.equals("group")||key.equals("active_group")){if(!value.matches("[a-z0-9_.-]{1,64}"))return false;}
            else if(Set.of("recovery","cooldown","mana","mana_per_second","strain","strain_per_second","active_limit").contains(key)){try{double n=Double.parseDouble(value);if(!Double.isFinite(n)||n<0||n>1000000)return false;}catch(NumberFormatException e){return false;}}
            else return false;
        }return true;
    }
    public static boolean authorized(ServerPlayer player){var server=player.serverLevel().getServer();return player.hasPermissions(2)||server.isSingleplayerOwner(player.getGameProfile());}
    private static final Map<UUID,Long> LAST=new HashMap<>();
    @SuppressWarnings("unchecked")
    public static void apply(ServerPlayer player,MagicNetwork.SettingsUpdate packet){
        if(!authorized(player)){reply(player,packet.request(),false,"denied","");return;}
        long now=SpellBalance.now(player);if(now-LAST.getOrDefault(player.getUUID(),-1000L)<10){reply(player,packet.request(),false,"busy","");return;}LAST.put(player.getUUID(),now);
        Map<String,Object> changes=new LinkedHashMap<>();var values=ConfigCommand.values();
        try{
            var object=JsonParser.parseString(packet.json()).getAsJsonObject();if(object.size()==0||object.size()>values.size())throw new IllegalArgumentException();
            for(var entry:object.entrySet()){
                String key=entry.getKey();var target=values.get(key);if(target==null)throw new IllegalArgumentException();
                String text=entry.getValue().isJsonArray()?entry.getValue().toString():entry.getValue().getAsString();
                Object parsed=parse(key,text);if(parsed==null){reply(player,packet.request(),false,"invalid",key);return;}changes.put(key,parsed);
            }
        }catch(RuntimeException e){reply(player,packet.request(),false,"invalid","");return;}
        Map<String,Object> previous=new LinkedHashMap<>();Set<ForgeConfigSpec.ConfigValue<?>> files=new LinkedHashSet<>();boolean general=false,balance=false;
        try{
            for(var entry:changes.entrySet()){
                var value=(ForgeConfigSpec.ConfigValue<Object>)values.get(entry.getKey());previous.put(entry.getKey(),value.get());value.set(entry.getValue());
                if(entry.getKey().startsWith("balance.")){if(!balance)files.add(value);balance=true;}else{if(!general)files.add(value);general=true;}
            }
            files.forEach(ForgeConfigSpec.ConfigValue::save);sync();changed(player.serverLevel().getServer(),changes.keySet());reply(player,packet.request(),true,"saved",Integer.toString(changes.size()));
        }catch(RuntimeException e){
            previous.forEach((key,value)->((ForgeConfigSpec.ConfigValue<Object>)values.get(key)).set(value));
            try{files.forEach(ForgeConfigSpec.ConfigValue::save);sync();changed(player.serverLevel().getServer(),previous.keySet());}catch(RuntimeException ignored){}
            org.slf4j.LoggerFactory.getLogger(SettingsData.class).error("Unable to save Spellweaver settings",e);reply(player,packet.request(),false,"save_failed","");
        }
    }
    static void sync(){var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();if(server!=null){MagicNetwork.sendConfig(server,"spellweaver-server.toml");MagicNetwork.sendConfig(server,"spellweaver-balance.toml");}}

    static void changed(net.minecraft.server.MinecraftServer server,Set<String> keys){
        MagicStats.clearCache();
        for(ServerPlayer player:server.getPlayerList().getPlayers()){
            MagicStats.sync(player);MageLevel.sync(player);SpellScrolls.sync(player);
            CastingServer.settingsChanged(player,keys.contains("instantCast"));SpellBalance.settingsChanged(player);
        }
    }
    private static void reply(ServerPlayer player,int request,boolean success,String reason,String detail){MagicNetwork.sendToPlayer(player,new MagicNetwork.SettingsResult(request,success,reason,detail));}
    public static void forget(UUID id){LAST.remove(id);}
    public static void reset(){LAST.clear();}
    private SettingsData(){}
}
