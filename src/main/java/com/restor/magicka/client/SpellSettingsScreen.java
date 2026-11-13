package com.restor.magicka.client;

import com.google.gson.*;
import com.restor.magicka.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

public final class SpellSettingsScreen extends Screen {
    private static final net.minecraft.resources.Identifier MOD_ICON=net.minecraft.resources.Identifier.fromNamespaceAndPath(Magicka.ID,"textures/gui/mod_logo.png");
    private static final int TEXT=0xFFE8E3D9,DIM=0xFF9B99A6,EDGE=0xFF353746,ACCENT=0xFFD4B77A;
    private final Screen parent;
    private final Map<String,ModConfigSpec.ConfigValue<?>> values=ConfigCommand.values();
    private final Map<String,Object> original=new LinkedHashMap<>(),draft=new LinkedHashMap<>();
    private final Map<String,String> raw=new HashMap<>();
    private final Set<String> invalid=new HashSet<>();
    private final List<Row> rows=new ArrayList<>();
    private SettingsData.Category category=SettingsData.Category.BALANCE;
    private String query="";
    private int scroll,navScroll,left,top,panelWidth,panelHeight,contentX,contentWidth,bodyY,bodyHeight,rowHeight,rowCount,request,pendingTicks;
    private boolean refresh,pending;
    private EditBox search;
    private Button apply,reset;
    private Component status=Component.empty();
    private boolean failure;
    private record Row(String key,int y){}
    public SpellSettingsScreen(Screen parent){super(tr("title"));this.parent=parent;reload();}
    private void reload(){original.clear();draft.clear();values.forEach((key,value)->{Object current=SettingsData.value(key,value);if(current instanceof List<?> list)current=List.copyOf(list);original.put(key,current);draft.put(key,current);});raw.clear();invalid.clear();}
    private static Component tr(String key,Object...args){return Component.translatable("settings.magicka."+key,args);}
    public int optionCount(){return values.size();}
    private boolean loaded(){return MagicConfig.SPEC.isLoaded()&&BalanceConfig.SPEC.isLoaded();}
    private boolean editable(){return loaded()&&minecraft!=null&&minecraft.player!=null&&(minecraft.hasSingleplayerServer()||minecraft.player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER));}
    private int changed(){return (int)draft.entrySet().stream().filter(e->!Objects.equals(e.getValue(),original.get(e.getKey()))).count();}
    private Component name(String key){return Component.translatable(SettingsData.translation(key));}
    private Component description(String key){return Component.translatableWithFallback(SettingsData.translation(key)+".tooltip",values.get(key).getSpec().getComment());}
    private List<String> matching(){String q=query.strip().toLowerCase(Locale.ROOT);return values.keySet().stream().filter(key->q.isEmpty()?SettingsData.category(key)==category:name(key).getString().toLowerCase(Locale.ROOT).contains(q)||key.toLowerCase(Locale.ROOT).contains(q)||Component.translatable(SettingsData.category(key).key()).getString().toLowerCase(Locale.ROOT).contains(q)).toList();}
    private Element icon(SettingsData.Category c){return switch(c){case BALANCE->Element.ARCANE;case COOLDOWNS->Element.LIGHTNING;case MANA->Element.WATER;case STRAIN->Element.FIRE;case LIMITS->Element.SHIELD;case CASTING->Element.DEATH;case WORLD->Element.EARTH;case PROGRESSION->Element.LIFE;case SCROLLS->Element.STEAM;};}
    @Override protected void init(){
        panelWidth=Math.min(900,width-12);panelHeight=Math.min(540,height-12);left=(width-panelWidth)/2;top=(height-panelHeight)/2;
        int navWidth=panelWidth>=600?140:panelWidth>=380?100:84;contentX=left+navWidth+12;contentWidth=panelWidth-navWidth-24;bodyY=top+44;bodyHeight=panelHeight-80;rowHeight=panelHeight<310?32:44;rowCount=Math.max(1,(bodyHeight-20)/rowHeight);
        search=new EditBox(font,left+panelWidth-Math.max(112,panelWidth/2-20)-13,top+13,Math.max(112,panelWidth/2-20),16,tr("search"));search.setBordered(false);search.setMaxLength(128);search.setHint(tr("search"));search.setTextColor(TEXT);search.setValue(query);search.setResponder(text->{query=text;scroll=0;refresh=true;});addRenderableWidget(search);
        int navCount=Math.max(1,bodyHeight/19);navScroll=Math.clamp(navScroll,0,Math.max(0,SettingsData.Category.values().length-navCount));
        for(int i=navScroll;i<Math.min(SettingsData.Category.values().length,navScroll+navCount);i++){
            var c=SettingsData.Category.values()[i];int y=bodyY+(i-navScroll)*19;
            var button=new Action(left+7,y,navWidth-8,17,Component.translatable(c.key()),b->{category=c;query="";scroll=0;rebuildWidgets();},()->c==category&&query.isBlank());button.icon=icon(c);addRenderableWidget(button);
        }
        var keys=matching();scroll=Math.clamp(scroll,0,Math.max(0,keys.size()-rowCount));rows.clear();
        var commands=new Action(contentX+contentWidth-23,bodyY-1,20,15,Component.literal("/>"),b->minecraft.gui.setScreen(new CommandHelp(null)),()->false);commands.setTooltip(Tooltip.create(tr("server_commands")));commands.active=!pending;addRenderableWidget(commands);
        for(int i=scroll;i<Math.min(keys.size(),scroll+rowCount);i++)addRow(keys.get(i),bodyY+20+(i-scroll)*rowHeight);
        int footer=top+panelHeight-27;
        reset=addRenderableWidget(new Action(left+panelWidth-230,footer,72,20,tr("reset_section"),b->{matching().forEach(k->set(k,values.get(k).getDefault()));rebuildWidgets();},()->false));
        addRenderableWidget(new Action(left+panelWidth-152,footer,64,20,Component.translatable("gui.cancel"),b->onClose(),()->false));
        apply=addRenderableWidget(new Action(left+panelWidth-82,footer,74,20,tr("apply"),b->save(),()->true));updateButtons();
    }
    private void addRow(String key,int y){
        rows.add(new Row(key,y));Object value=draft.get(key);int controlWidth=Math.min(112,Math.max(76,contentWidth/3)),x=contentX+contentWidth-controlWidth-39,cy=y+(rowHeight-3-18)/2;
        if(value instanceof Boolean bool){var button=new Action(x,cy,controlWidth,18,bool?tr("on"):tr("off"),b->{boolean next=!(Boolean)draft.get(key);set(key,next);b.setMessage(next?tr("on"):tr("off"));},()->Boolean.TRUE.equals(draft.get(key)));button.active=editable()&&!pending;addRenderableWidget(button);}
        else if(value instanceof Enum<?> e){var button=new Action(x,cy,controlWidth,18,enumText(e),b->{Object[] options=e.getDeclaringClass().getEnumConstants();Enum<?> current=(Enum<?>)draft.get(key),next=(Enum<?>)options[Math.floorMod(current.ordinal()+(com.mojang.blaze3d.platform.InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow(),340)?-1:1),options.length)];set(key,next);b.setMessage(enumText(next));},()->false);button.active=editable()&&!pending;addRenderableWidget(button);}
        else if(value instanceof List<?> list){var button=new Action(x,cy,controlWidth,18,tr("entries",list.size()),b->minecraft.gui.setScreen(new ListEditor(key)),()->false);button.active=!pending;addRenderableWidget(button);}
        else{
            var field=new EditBox(font,x+5,cy+5,controlWidth-10,10,name(key));field.setBordered(false);field.setMaxLength(32);field.setTextColor(invalid.contains(key)?0xFFFF8F85:TEXT);field.setValue(raw.getOrDefault(key,SettingsData.text(value)));field.setResponder(text->{raw.put(key,text);Object parsed=SettingsData.parse(key,text);if(parsed==null)invalid.add(key);else{invalid.remove(key);draft.put(key,parsed);}field.setTextColor(parsed==null?0xFFFF8F85:TEXT);updateButtons();});field.setEditable(editable()&&!pending);field.active=editable()&&!pending;addRenderableWidget(field);
        }
        var restore=new Action(contentX+contentWidth-17,cy,15,18,Component.literal("↺"),b->{set(key,values.get(key).getDefault());rebuildWidgets();},()->false);restore.setTooltip(Tooltip.create(tr("default",SettingsData.text(values.get(key).getDefault()))));restore.active=editable()&&!pending;addRenderableWidget(restore);
        var commands=new Action(contentX+contentWidth-35,cy,15,18,Component.literal("/"),b->minecraft.gui.setScreen(new CommandHelp(key)),()->false);commands.setTooltip(Tooltip.create(tr("commands_for",name(key))));commands.active=!pending;addRenderableWidget(commands);
    }
    private Component enumText(Enum<?> e){return e instanceof net.neoforged.neoforge.common.TranslatableEnum t?t.getTranslatedName():Component.literal(e.name());}
    private void set(String key,Object value){draft.put(key,value instanceof List<?> list?List.copyOf(list):value);raw.remove(key);invalid.remove(key);status=Component.empty();failure=false;updateButtons();}
    private void updateButtons(){if(apply!=null)apply.active=editable()&&!pending&&invalid.isEmpty()&&changed()>0;if(reset!=null)reset.active=editable()&&!pending;}
    private void save(){
        if(!editable()||pending||!invalid.isEmpty())return;var json=new JsonObject();
        draft.forEach((key,value)->{if(!Objects.equals(value,original.get(key)))json.add(key,new Gson().toJsonTree(value));});
        if(json.size()==0)return;if(json.toString().length()>32767){status=tr("too_large");failure=true;return;}
        pending=true;pendingTicks=0;request++;status=tr("saving");failure=false;net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new MagicNetwork.SettingsUpdate(request,json.toString()));rebuildWidgets();
    }
    public void accept(MagicNetwork.SettingsResult result){if(!pending||result.request()!=request)return;pending=false;failure=!result.success();status=tr(result.reason(),result.detail());if(result.success())reload();rebuildWidgets();}
    @Override public void tick(){
        if(refresh){refresh=false;boolean focus=search.isFocused();int cursor=search.getCursorPosition();rebuildWidgets();if(focus){setFocused(search);search.setFocused(true);search.setCursorPosition(Math.min(cursor,search.getValue().length()));}}
        if(pending&&++pendingTicks>200){pending=false;failure=true;status=tr("timeout");rebuildWidgets();}
        updateButtons();
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){
        if(pending)return true;
        if(mx>=left&&mx<contentX-5&&my>=bodyY&&my<bodyY+bodyHeight){int next=Math.clamp(navScroll-(int)Math.signum(vertical),0,Math.max(0,SettingsData.Category.values().length-Math.max(1,bodyHeight/19)));if(next!=navScroll){navScroll=next;rebuildWidgets();}return true;}
        if(mx>=contentX&&my>=bodyY&&my<bodyY+bodyHeight){int next=Math.clamp(scroll-(int)Math.signum(vertical)*2,0,Math.max(0,matching().size()-rowCount));if(next!=scroll){scroll=next;rebuildWidgets();}return true;}
        return super.mouseScrolled(mx,my,horizontal,vertical);
    }
    @Override public void onClose(){if(!pending)minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float partial){}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float partial){
        backdrop(g,left,top,panelWidth,panelHeight);g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,MOD_ICON,left+10,top+10,0,0,22,22,22,22);
        UiText.line(g,title,left+39,top+11,panelWidth/2-50,12,.9f,false,TEXT);
        UiText.line(g,tr("subtitle"),left+39,top+25,panelWidth/2-50,8,.6f,false,DIM);
        surface(g,search.getX()-5,search.getY()-4,search.getWidth()+10,23,0xFF10121A,search.isFocused()?ACCENT:EDGE);
        g.fill(contentX-7,bodyY,contentX-6,bodyY+bodyHeight,EDGE);
        var keys=matching();UiText.line(g,query.isBlank()?Component.translatable(category.key()):tr("results"),contentX,bodyY+2,contentWidth-70,11,.8f,false,ACCENT);
        UiText.line(g,Component.literal(Integer.toString(keys.size())),contentX+contentWidth-47,bodyY+2,20,10,.7f,true,DIM);
        for(Row row:rows){String key=row.key();int y=row.y();boolean dirty=!Objects.equals(draft.get(key),original.get(key));surface(g,contentX,y,contentWidth,rowHeight-3,0xFF1A1D27,invalid.contains(key)?0xFFBB6666:dirty?0xFF6B5C3D:EDGE);if(dirty)g.fill(contentX,y+3,contentX+2,y+rowHeight-6,ACCENT);
            int cw=Math.min(112,Math.max(76,contentWidth/3)),labelWidth=contentWidth-cw-52;
            UiText.line(g,name(key),contentX+7,y+4,labelWidth,10,.8f,false,TEXT);
            String hint=description(key).getString().split("[.;]\\s",2)[0];int limit=Math.max(1,(int)(labelWidth/.65f));if(font.width(hint)>limit)hint=font.plainSubstrByWidth(hint,Math.max(1,limit-font.width("…")))+"…";
            UiText.line(g,Component.literal(hint),contentX+7,y+17,labelWidth,Math.max(6,rowHeight-22),.65f,false,DIM);
            if(!(draft.get(key) instanceof Boolean)&&!(draft.get(key) instanceof Enum<?>)&&!(draft.get(key) instanceof List<?>))surface(g,contentX+contentWidth-cw-39,y+(rowHeight-21)/2,cw,18,0xFF10131C,EDGE);
        }
        if(keys.isEmpty())UiText.block(g,tr("empty"),contentX+10,bodyY+36,contentWidth-20,40,.85f,true,true,DIM);
        if(keys.size()>rowCount){int track=rowCount*rowHeight-3,thumb=Math.max(8,track*rowCount/keys.size()),sy=bodyY+20+(track-thumb)*scroll/Math.max(1,keys.size()-rowCount);g.fill(contentX+contentWidth+3,bodyY+20,contentX+contentWidth+5,bodyY+20+track,EDGE);g.fill(contentX+contentWidth+3,sy,contentX+contentWidth+5,sy+thumb,ACCENT);}
        int navCount=Math.max(1,bodyHeight/19);if(SettingsData.Category.values().length>navCount){int thumb=Math.max(10,bodyHeight*navCount/SettingsData.Category.values().length),ny=bodyY+(bodyHeight-thumb)*navScroll/(SettingsData.Category.values().length-navCount);g.fill(contentX-11,bodyY,contentX-10,bodyY+bodyHeight,EDGE);g.fill(contentX-11,ny,contentX-10,ny+thumb,ACCENT);}
        Component message=pending?tr("saving"):!status.getString().isEmpty()?status:!loaded()||minecraft.player==null?tr("load_world"):!editable()?tr("read_only"):!invalid.isEmpty()?tr("invalid",invalid.iterator().next()):tr("changes",changed());
        UiText.line(g,message,left+9,top+panelHeight-23,Math.max(1,panelWidth-247),14,.7f,false,failure||!invalid.isEmpty()?0xFFFF9E91:DIM);
        super.extractRenderState(g,mx,my,partial);
        for(Row row:rows)if(mx>=contentX&&mx<contentX+contentWidth-Math.min(112,Math.max(76,contentWidth/3))-45&&my>=row.y()&&my<row.y()+rowHeight-3)g.setTooltipForNextFrame(font,description(row.key()).copy().append("\n").append(tr("default",SettingsData.text(values.get(row.key()).getDefault()))).append("\n").append(Component.literal("/spellweaver config "+row.key())).append("\n").append(tr("commands_tooltip")),mx,my);
    }
    private static void surface(GuiGraphicsExtractor g,int x,int y,int w,int h,int fill,int border){g.fill(x+1,y,x+w-1,y+h,fill);g.fill(x,y+1,x+w,y+h-1,fill);g.outline(x+1,y,w-2,h,border);}
    private static void backdrop(GuiGraphicsExtractor g,int x,int y,int w,int h){g.fillGradient(0,0,g.guiWidth(),g.guiHeight(),0xF20A0C13,0xF2151420);MagicUi.panel(g,x,y,w,h);g.fill(x+1,y+1,x+w-1,y+37,0xFF1B1B26);g.fill(x+8,y+37,x+w-8,y+38,0xFF655737);g.fill(x+8,y+h-34,x+w-8,y+h-33,EDGE);}
    private static final class Action extends Button {
        private final java.util.function.BooleanSupplier selected;
        private Element icon;
        Action(int x,int y,int w,int h,Component title,OnPress press,java.util.function.BooleanSupplier selected){super(x,y,w,h,title,press,DEFAULT_NARRATION);this.selected=selected;}
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float partial){boolean checked=selected.getAsBoolean();surface(g,getX(),getY(),width,height,!active?0xFF181A22:isHoveredOrFocused()?0xFF343442:checked?0xFF3B3427:0xFF232631,!active?0xFF292B36:checked?0xFFAD945E:isHoveredOrFocused()?0xFF77707A:EDGE);int inset=icon==null?3:21;if(icon!=null)ElementIcons.draw(g,icon,getX()+3,getY()+(height-14)/2,14,active);UiText.line(g,getMessage(),getX()+inset,getY()+3,width-inset-3,height-6,.75f,icon==null,active?checked?0xFFFFDEA0:TEXT:0xFF747482);}
    }
    private final class CommandHelp extends Screen {
        private record Command(Component label,String text){}
        private final List<Command> commands=new ArrayList<>();
        private int offset,x,y,w,h,visible,copied=-1;
        CommandHelp(String key){
            super(key==null?tr("server_commands"):tr("commands_for",name(key)));
            if(key==null){
                commands.add(new Command(tr("command_list"),"/spellweaver config"));
                commands.add(new Command(tr("command_standard"),"/spellweaver config balance.mode DEFAULT"));
                commands.add(new Command(tr("command_mana"),"/spellweaver config balance.mode MANA"));
                commands.add(new Command(tr("command_disable"),"/spellweaver config balance.mode OFF"));
                commands.add(new Command(tr("command_creative"),"/spellweaver config balance.creativeBypass false"));
            }else{
                commands.add(new Command(tr("command_inspect"),"/spellweaver config "+key));
                commands.add(new Command(tr("command_set"),SettingsData.command(key,draft.get(key))));
                commands.add(new Command(tr("command_reset"),"/spellweaver config "+key+" default"));
            }
        }
        @Override protected void init(){
            w=Math.min(720,width-20);h=Math.min(430,height-20);x=(width-w)/2;y=(height-h)/2;visible=Math.max(1,(h-104)/38);offset=Math.clamp(offset,0,Math.max(0,commands.size()-visible));
            for(int i=offset;i<Math.min(commands.size(),offset+visible);i++){
                final int index=i;int ry=y+68+(i-offset)*38;var command=commands.get(i);
                var field=new EditBox(font,x+18,ry+20,w-111,10,command.label());field.setBordered(false);field.setMaxLength(Math.max(32767,command.text().length()));field.setValue(command.text());field.setEditable(false);field.setTextColorUneditable(TEXT);field.moveCursorToStart(false);addRenderableWidget(field);
                addRenderableWidget(new Action(x+w-83,ry+14,68,20,index==copied?tr("copied"):tr("copy"),b->{minecraft.keyboardHandler.setClipboard(command.text());copied=index;rebuildWidgets();},()->index==copied));
            }
            addRenderableWidget(new Action(x+w-84,y+h-28,72,20,Component.translatable("gui.done"),b->onClose(),()->false));
        }
        @Override public void onClose(){minecraft.gui.setScreen(SpellSettingsScreen.this);}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float partial){}
        @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){int next=Math.clamp(offset-(int)Math.signum(vertical),0,Math.max(0,commands.size()-visible));if(next!=offset){offset=next;rebuildWidgets();}return true;}
        @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float partial){
            backdrop(g,x,y,w,h);UiText.line(g,title,x+12,y+11,w-24,13,.9f,false,TEXT);
            UiText.line(g,tr("commands_hint"),x+12,y+40,w-24,10,.65f,false,DIM);UiText.line(g,tr("commands_draft_hint"),x+12,y+52,w-24,10,.65f,false,DIM);
            for(int i=offset;i<Math.min(commands.size(),offset+visible);i++){int ry=y+68+(i-offset)*38;UiText.line(g,commands.get(i).label(),x+14,ry+1,w-30,10,.75f,false,ACCENT);surface(g,x+13,ry+14,w-105,20,0xFF10131C,EDGE);}
            if(commands.size()>visible){int track=visible*38-4,thumb=Math.max(8,track*visible/commands.size()),sy=y+68+(track-thumb)*offset/(commands.size()-visible);g.fill(x+w-8,y+68,x+w-6,y+68+track,EDGE);g.fill(x+w-8,sy,x+w-6,sy+thumb,ACCENT);}
            UiText.line(g,tr("commands_immediate"),x+12,y+h-25,w-108,14,.65f,false,DIM);super.extractRenderState(g,mx,my,partial);
        }
    }
    private final class ListEditor extends Screen {
        private final String key;
        private final List<String> entries=new ArrayList<>();
        private int offset,x,y,w,h,visible;
        private Button done;
        ListEditor(String key){super(name(key));this.key=key;for(Object row:(List<?>)draft.get(key))entries.add(row.toString());}
        @Override protected void init(){
            w=Math.min(680,width-20);h=Math.min(430,height-20);x=(width-w)/2;y=(height-h)/2;visible=Math.max(1,(h-100)/28);offset=Math.clamp(offset,0,Math.max(0,entries.size()-visible));
            for(int i=offset;i<Math.min(entries.size(),offset+visible);i++){final int index=i;int ry=y+58+(i-offset)*28;EditBox field=new EditBox(font,x+19,ry+6,w-64,10,title);field.setBordered(false);field.setTextColor(TEXT);field.setMaxLength(1024);field.setValue(entries.get(i));field.setEditable(editable());field.active=editable();field.setResponder(text->{entries.set(index,text);update();});addRenderableWidget(field);var remove=new Action(x+w-34,ry,20,20,Component.literal("×"),b->{entries.remove(index);rebuildWidgets();},()->false);remove.active=editable();remove.setTooltip(Tooltip.create(tr("remove")));addRenderableWidget(remove);}
            var add=new Action(x+12,y+h-28,76,20,tr("add"),b->{entries.add("");offset=Math.max(0,entries.size()-visible);rebuildWidgets();},()->false);add.active=editable()&&entries.size()<256;addRenderableWidget(add);
            addRenderableWidget(new Action(x+w-158,y+h-28,68,20,Component.translatable("gui.cancel"),b->onClose(),()->false));
            done=addRenderableWidget(new Action(x+w-84,y+h-28,72,20,tr("keep"),b->{set(key,entries);onClose();},()->true));update();
        }
        private void update(){if(done!=null)done.active=editable()&&SettingsData.valid(key,entries);}
        @Override public void onClose(){minecraft.gui.setScreen(SpellSettingsScreen.this);}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float partial){}
        @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){int next=Math.clamp(offset-(int)Math.signum(vertical)*2,0,Math.max(0,entries.size()-visible));if(next!=offset){offset=next;rebuildWidgets();}return true;}
        @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float partial){
            backdrop(g,x,y,w,h);UiText.line(g,title,x+12,y+11,w-60,13,.9f,false,TEXT);UiText.line(g,Component.literal(Integer.toString(entries.size())),x+w-43,y+12,30,12,.75f,true,DIM);
            UiText.line(g,tr(key.equals("balance.spellOverrides")?"override_hint":"list_hint"),x+12,y+39,w-24,12,.7f,false,DIM);
            for(int i=offset;i<Math.min(entries.size(),offset+visible);i++)surface(g,x+14,y+58+(i-offset)*28,w-54,20,0xFF10131C,EDGE);
            if(entries.size()>visible){int track=visible*28-8,thumb=Math.max(8,track*visible/entries.size()),sy=y+58+(track-thumb)*offset/(entries.size()-visible);g.fill(x+w-9,y+58,x+w-7,y+58+track,EDGE);g.fill(x+w-9,sy,x+w-7,sy+thumb,ACCENT);}
            if(entries.isEmpty())UiText.line(g,tr("list_empty"),x+14,y+64,w-28,18,.8f,true,DIM);if(!SettingsData.valid(key,entries))UiText.line(g,tr("list_invalid"),x+94,y+h-25,w-260,14,.65f,false,0xFFFF9E91);super.extractRenderState(g,mx,my,partial);
        }
    }
}
