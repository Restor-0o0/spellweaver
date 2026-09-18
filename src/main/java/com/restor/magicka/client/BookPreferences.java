package com.restor.magicka.client;

import com.restor.magicka.GrimoireState;
import net.minecraft.client.Minecraft;
import java.nio.file.Path;

public final class BookPreferences {
    private static GrimoireState state;
    private static Path path(){return Minecraft.getInstance().gameDirectory.toPath().resolve("config/magicka-grimoire.json");}
    public static GrimoireState get(){
        if(state==null){try{state=GrimoireState.read(path());}catch(Exception e){state=new GrimoireState();org.slf4j.LoggerFactory.getLogger("magicka").warn("Cannot read grimoire preferences",e);}}
        return state;
    }
    public static void save(){try{get().save(path());}catch(Exception e){org.slf4j.LoggerFactory.getLogger("magicka").warn("Cannot save grimoire preferences",e);}}
    private BookPreferences(){}
}
