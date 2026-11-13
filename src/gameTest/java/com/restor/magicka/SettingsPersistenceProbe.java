package com.restor.magicka;

import com.google.gson.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import java.nio.file.*;
import java.util.*;

@EventBusSubscriber(modid=Magicka.ID)
public final class SettingsPersistenceProbe {
    private static final Path FILE=Path.of("settings-persistence-probe.json");
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("SettingsPersistenceProbe");
    private static String phase(){return System.getProperty("magicka.settingsPersistence","");}
    private static void save(){MagicConfig.CONSTRUCTION.save();BalanceConfig.MODE.save();}
    @SuppressWarnings("unchecked") private static void assign(Map<String,Object> values){values.forEach((k,v)->((ModConfigSpec.ConfigValue<Object>)ConfigCommand.values().get(k)).set(v));save();}
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.HIGHEST) public static void read(ServerStartedEvent event){
        if(!phase().equals("read"))return;
        try{
            var document=JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();var expected=document.getAsJsonObject("expected");Map<String,Object> restore=new LinkedHashMap<>();
            for(var entry:expected.entrySet()){
                String key=entry.getKey(),text=entry.getValue().isJsonArray()?entry.getValue().toString():entry.getValue().getAsString();Object value=SettingsData.parse(key,text);
                if(value==null||!Objects.equals(ConfigCommand.values().get(key).get(),value))throw new IllegalStateException("Restart did not retain "+key+": "+ConfigCommand.values().get(key).get());
                var original=document.getAsJsonObject("original").get(key);restore.put(key,SettingsData.parse(key,original.isJsonArray()?original.toString():original.getAsString()));
            }
            if(expected.size()!=ConfigCommand.values().size())throw new IllegalStateException("Missing settings in restart probe");
            assign(restore);LOG.info("SETTINGS_RELOAD_OK: {} settings loaded from saved configs in a new server process; original values restored",expected.size());
        }catch(java.io.IOException e){throw new RuntimeException(e);}
    }
    @SubscribeEvent public static void write(ServerStoppingEvent event){
        if(!phase().equals("write"))return;
        Map<String,Object> original=new LinkedHashMap<>(),expected=new LinkedHashMap<>();ConfigCommand.values().forEach((k,v)->{original.put(k,v.get());expected.put(k,SettingsApplicationGameTests.alternate(k,v));});
        var document=new JsonObject();var gson=new GsonBuilder().setPrettyPrinting().create();document.add("original",gson.toJsonTree(original));document.add("expected",gson.toJsonTree(expected));
        try{Files.writeString(FILE,gson.toJson(document));assign(expected);LOG.info("SETTINGS_PERSISTENCE_WRITTEN: {} settings saved for a new server process",expected.size());}catch(java.io.IOException e){throw new RuntimeException(e);}
    }
    private SettingsPersistenceProbe(){}
}
