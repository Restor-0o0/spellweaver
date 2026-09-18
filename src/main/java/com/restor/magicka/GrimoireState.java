package com.restor.magicka;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Personal book position and four formula presets; independent of the current server's recipe IDs. */
public final class GrimoireState {
    public String selectedId="", query="", tag="all";
    public int size, elements, page, scroll;
    private final int[] slots=new int[4];
    public int slot(int index){return index>=0 && index<4?slots[index]:0;}
    public void assign(int index,int packed){if(index<0 || index>=4)throw new IllegalArgumentException("Slot");slots[index]=valid(packed)?Formula.unpack(packed).packed():0;}
    public static boolean valid(int packed){return Formula.valid(packed);}
    public static GrimoireState read(Path path) throws IOException {
        var state=new GrimoireState();if(!Files.exists(path))return state;
        try {
            var json=JsonParser.parseString(Files.readString(path,StandardCharsets.UTF_8)).getAsJsonObject();
            state.selectedId=SpellDefinition.string(json,"selected","");state.query=SpellDefinition.string(json,"query","");
            state.tag=SpellDefinition.string(json,"tag","all");
            state.size=SpellDefinition.integer(json,"size",0,0,Formula.MAX);state.elements=SpellDefinition.integer(json,"elements",0,0,255);state.page=SpellDefinition.integer(json,"page",0,0,1000);state.scroll=SpellDefinition.integer(json,"scroll",0,0,10000);
            if(json.has("slots")){var array=json.getAsJsonArray("slots");for(int i=0;i<Math.min(4,array.size());i++)state.assign(i,array.get(i).getAsInt());}
            return state;
        }catch(RuntimeException invalid){return new GrimoireState();}
    }
    public void save(Path path) throws IOException {
        var json=new JsonObject();json.addProperty("selected",selectedId);json.addProperty("query",query);json.addProperty("size",size);json.addProperty("elements",elements);json.addProperty("page",page);json.addProperty("scroll",scroll);
        json.addProperty("tag",tag);
        var array=new JsonArray();for(int slot:slots)array.add(slot);json.add("slots",array);
        Files.createDirectories(path.toAbsolutePath().getParent());Path temp=path.resolveSibling(path.getFileName()+".tmp");
        Files.writeString(temp,new GsonBuilder().setPrettyPrinting().create().toJson(json),StandardCharsets.UTF_8);
        try{Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}
    }
}
