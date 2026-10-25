package com.restor.magicka.client;

import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import com.restor.magicka.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** The grimoire reads the server-synchronized resolver, never a second spell list. */
public final class SpellbookScreen extends Screen {
    private int size, elements, page, selected, scroll, x, y, bookWidth, bookHeight, divider, rows;
    private static final int LIST_TOP=112;
    private String query="";
    private static final String[] TAGS={"all","damage","heal","control","construction","destruction","summon","utility"};
    private String tag="all";
    private EditBox search;
    private boolean guide, stats;
    private int[] shownStats;
    private record StatRow(MagicStats.Stat stat,int x,int y,int labelWidth){}
    private final List<StatRow> statRows=new ArrayList<>();
    private int catalogRevision=-1;
    private String restoreId="";
    private int dragFormula;private double dragX,dragY;private boolean dragging;
    public SpellbookScreen(){
        super(Component.translatable("book.magicka.title"));var saved=BookPreferences.get();
        tag=Arrays.asList(TAGS).contains(saved.tag)?saved.tag:"all";query=saved.query;page=saved.page;scroll=saved.scroll;restoreId=saved.selectedId;
        size=Math.clamp(saved.size,0,Formula.MAX);elements=Math.clamp(saved.elements,0,255);
    }
    public static List<SpellDefinition> uniqueSpells(){return RecipeRegistry.clientRecipes().stream().filter(d->d.tier().equalsIgnoreCase("Signature") && com.restor.magicka.SpellScrolls.clientOpen(net.minecraft.client.Minecraft.getInstance().player,d)).toList();}
    private List<SpellDefinition> entries(){
        String q=query.toLowerCase(Locale.ROOT).replace("#","").trim();
        return uniqueSpells().stream().filter(d->tag.equals("all") || SpellTags.of(d).contains(tag))
                .filter(d->size==0 || d.formula().size()==size).filter(this::uses)
                .filter(d->q.isEmpty() || Arrays.stream(q.split("\\s+")).allMatch(word->(d.name()+" "+d.displayName().getString()+" "+d.tags()+" "+d.primaryDescription().getString()+" "+d.selfDescription().getString()+" "+tagText(d)+" "+SpellTags.of(d)).toLowerCase(Locale.ROOT).contains(word))).toList();
    }
    private boolean uses(SpellDefinition spell){
        for(Element element:Element.values())if((elements&1<<element.ordinal())!=0 && spell.formula().count(element)==0)return false;
        return true;
    }
    private Component mageLine(){
        var locked=RecipeRegistry.clientRecipes().stream().filter(com.restor.magicka.SpellScrolls::locked).toList();
        long read=locked.stream().filter(s->com.restor.magicka.SpellScrolls.clientKnown(s.id())).count();
        Component scrolls=Component.translatable("book.magicka.scrolls",read,locked.size());
        if(!MagicConfig.MAGE_LEVELS.get())return scrolls;
        int xp=MageLevel.clientXp(),level=MageLevel.level(xp);
        Component progress=level>=MagicConfig.MAX_MAGE_LEVEL.get()?Component.translatable("book.magicka.mage_max"):Component.literal(xp+" / "+MageLevel.xpFor(level+1));
        return Component.translatable("book.magicka.mage",level,progress,scrolls);
    }
    private String tagText(SpellDefinition spell){return String.join(" · ",SpellTags.of(spell).stream().map(t->Component.translatable("book.magicka.tag."+t).getString()).toList());}
    @Override protected void init(){
        bookWidth=Math.min(540,width-16);bookHeight=Math.min(330,height-16);
        x=(width-bookWidth)/2;y=(height-bookHeight)/2;divider=x+bookWidth*2/5;
        rows=Math.max(1,(bookHeight-LIST_TOP-58)/23);
        var list=entries();if(!restoreId.isEmpty()){for(int i=0;i<list.size();i++)if(list.get(i).id().toString().equals(restoreId)){selected=i;page=i/rows;break;}restoreId="";}
        if(selected>0)page=selected/rows;
        rebuild(false);
    }
    @Override public void tick(){
        if(catalogRevision!=RecipeRegistry.clientRevision()){catalogRevision=RecipeRegistry.clientRevision();rebuild(search!=null && search.isFocused());}
        if(stats && shownStats!=MagicNetwork.clientStats && !(getFocused() instanceof EditBox && getFocused().isFocused()) && !(getFocused() instanceof StatSlider && isDragging()))rebuild(false);
    }
    public void showStats(boolean show){stats=show;guide=false;rebuild(false);}
    private static void sendStat(int index,int value){
        int[] copy=MagicNetwork.clientStats.clone();if(index>=0)copy[index]=MagicStats.Stat.values()[index].clamp(value);MagicNetwork.clientStats=copy;
        if(net.minecraft.client.Minecraft.getInstance().getConnection()!=null)net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new MagicNetwork.SetStat(index,value));
    }
    private void rebuildStats(){
        statRows.clear();shownStats=MagicNetwork.clientStats;
        addRenderableWidget(new BookButton(x+bookWidth-28,y+10,16,16,Component.literal("×"),b->onClose(),false));
        addRenderableWidget(new BookButton(x+bookWidth-150,y+10,98,16,Component.translatable("book.magicka.stats.back"),b->showStats(false),true));
        addRenderableWidget(new BookButton(x+bookWidth-250,y+10,96,16,Component.translatable("book.magicka.stats.reset_all"),b->{sendStat(-1,0);MagicNetwork.clientStats=MagicStats.defaults();rebuild(false);},false));
        var all=MagicStats.Stat.values();int perColumn=(all.length+1)/2;
        int top=y+48,rowHeight=Math.clamp((bookHeight-58)/perColumn,17,30),columnWidth=(bookWidth-36)/2;
        for(int i=0;i<all.length;i++){
            var stat=all[i];int column=i/perColumn,row=i%perColumn;
            int rx=x+14+column*(columnWidth+8),ry=top+row*rowHeight,labelWidth=Math.min(120,columnWidth*2/5);
            int boxWidth=40,resetWidth=18,sliderWidth=columnWidth-labelWidth-boxWidth-resetWidth-12;
            statRows.add(new StatRow(stat,rx,ry,labelWidth));
            var box=new EditBox(font,rx+labelWidth+sliderWidth+4,ry,boxWidth,15,Component.translatable("book.magicka.stat."+stat.key()));
            var slider=new StatSlider(rx+labelWidth,ry,sliderWidth,16,stat,box);
            box.setMaxLength(5);box.setValue(Integer.toString(MagicNetwork.clientStats[stat.ordinal()]));
            box.setResponder(t->{if(!t.matches("\\d*")){box.setValue(t.replaceAll("\\D",""));return;}if(t.isEmpty())return;int value=stat.clamp(Integer.parseInt(t));slider.show(value);sendStat(stat.ordinal(),value);});
            addRenderableWidget(slider);addRenderableWidget(box);
            addRenderableWidget(new BookButton(rx+columnWidth-resetWidth-4,ry,resetWidth,15,Component.translatable("book.magicka.stats.reset"),b->{sendStat(stat.ordinal(),MagicStats.DEFAULT);rebuild(false);},false)).setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("book.magicka.stats.reset.tip")));
        }
    }
    private void rebuild(boolean focus){
        clearWidgets();
        if(stats){rebuildStats();return;}
        int tabWidth=(bookWidth-24)/TAGS.length;
        for(int i=0;i<TAGS.length;i++){String value=TAGS[i];addRenderableWidget(new BookButton(x+12+i*tabWidth,y+34,tabWidth-2,18,
                Component.translatable("book.magicka.tag."+value),b->{tag=value;page=selected=scroll=0;guide=false;rebuild(false);},tag.equals(value)));}
        int filterWidth=divider-x-24;
        for(int i=0;i<Element.values().length;i++){Element element=Element.values()[i];int bit=1<<i;
            addRenderableWidget(new ElementButton(x+12+i*(filterWidth/8),y+54,filterWidth/8-2,16,element,(elements&bit)!=0,
                    b->{elements^=bit;page=selected=scroll=0;rebuild(false);}));}
        for(int i=0;i<=Formula.MAX;i++){int value=i;
            addRenderableWidget(new BookButton(x+12+i*(filterWidth/6),y+72,filterWidth/6-2,16,
                    i==0?Component.translatable("book.magicka.filter.any"):Component.literal(Integer.toString(i)),
                    b->{size=value;page=selected=scroll=0;rebuild(false);},size==value))
                    .setTooltip(net.minecraft.client.gui.components.Tooltip.create(i==0?Component.translatable("book.magicka.filter.any.tip"):Component.translatable("book.magicka.filter.points",i)));}
        search=new EditBox(font,x+13,y+92,divider-x-25,16,Component.translatable("book.magicka.search"));
        search.setMaxLength(100);search.setValue(query);search.setHint(Component.translatable("book.magicka.search"));
        search.setResponder(value->{query=value;page=selected=scroll=0;rebuild(true);});addRenderableWidget(search);if(focus)setFocused(search);
        addRenderableWidget(new BookButton(x+bookWidth-28,y+10,16,16,Component.literal("×"),b->onClose(),false));
        addRenderableWidget(new BookButton(x+bookWidth-48,y+10,16,16,Component.literal("?"),b->{guide=true;scroll=0;},false));
        addRenderableWidget(new BookButton(x+bookWidth-150,y+10,98,16,Component.translatable("book.magicka.stats"),b->showStats(true),false));
        addRenderableWidget(new BookButton(x+bookWidth-250,y+10,96,16,Component.translatable("book.magicka.shape"),b->minecraft.setScreen(new ShapeEditorScreen()),false));
        var list=entries();page=Math.clamp(page,0,Math.max(0,(list.size()-1)/rows));selected=Math.clamp(selected,0,Math.max(0,list.size()-1));
        for(int i=0;i<rows && page*rows+i<list.size();i++){int index=page*rows+i;
            addRenderableWidget(new BookButton(x+12,y+LIST_TOP+i*23,divider-x-23,21,list.get(index).displayName(),
                    b->{selected=index;guide=false;scroll=0;rebuild(false);},index==selected));}
        var prev=addRenderableWidget(new BookButton(x+12,y+bookHeight-65,23,16,Component.literal("<"),b->{page--;selected=page*rows;scroll=0;rebuild(false);},false));
        var next=addRenderableWidget(new BookButton(divider-34,y+bookHeight-65,23,16,Component.literal(">"),b->{page++;selected=page*rows;scroll=0;rebuild(false);},false));
        prev.active=page>0;next.active=(page+1)*rows<list.size();
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick){
        g.fill(0,0,width,height,0xB810101B);MagicUi.panel(g,x-3,y-3,bookWidth+6,bookHeight+6);
        g.fill(x+4,y+4,x+bookWidth-4,y+bookHeight-4,0xFFDDCBA7);
        g.fill(x+7,y+7,divider-3,y+bookHeight-7,0xFFEADBB8);g.fill(divider+4,y+7,x+bookWidth-7,y+bookHeight-7,0xFFF1E4C8);
        g.fillGradient(divider-4,y+7,divider+5,y+bookHeight-7,0x806C533C,0x304C352B);
        MagicUi.text(g,title,x+14,y+14,MagicUi.INK);
        if(stats){renderStats(g,mouseX,mouseY,partialTick);return;}
        MagicUi.small(g,Component.literal(" / "+uniqueSpells().size()),x+14+font.width(title),y+16,0xFF786047);
        MagicUi.small(g,mageLine(),x+14,y+22,0xFF6B3F8A);
        g.fill(x+13,y+29,x+bookWidth-13,y+30,0xFFAA8C59);
        var list=entries();
        g.centeredText(font,Component.literal((page+1)+" / "+Math.max(1,(list.size()+rows-1)/rows)),(x+divider)/2,y+bookHeight-60,0xFF786047);
        int dx=divider+14,dw=x+bookWidth-17-dx,textTop=y+64;
        SpellDefinition entry=list.isEmpty()?null:list.get(Math.min(selected,list.size()-1));
        if(entry!=null && !guide){
            var heading=entry.displayName();g.textWithWordWrap(font,heading,dx,textTop,Math.max(50,dw-76),MagicUi.INK);
            g.pose().pushMatrix();g.pose().translate(dx+dw-72,textTop-2);g.pose().scale(.7f,.7f);
            var spoken=entry.spoken();for(int i=0;i<spoken.size();i++)MagicUi.socket(g,spoken.get(i),i*26,0,true);
            g.pose().popMatrix();textTop+=Math.max(20,font.split(heading,Math.max(50,dw-76)).size()*10)+4;
        }
        Component body=guide?Component.translatable("book.magicka.guide") :entry==null?Component.translatable("book.magicka.empty"):
                Component.literal(tagText(entry)+"\n\n")
                        .append(MagicClient.castHint("primary")).append("\n").append(entry.primaryDescription()).append("\n\n")
                        .append(MagicClient.castHint("self")).append("\n").append(entry.selfDescription()).append("\n\n")
                        .append(Component.translatable("book.magicka.cast_time",String.format(Locale.ROOT,"%.2f",entry.castTicks()/20.0)))
                        .append("\n").append(Component.translatable(entry.epic()?"book.magicka.mode_epic":!entry.channeled()?"book.magicka.mode_instant":entry.shape().construction()?"book.magicka.mode_growth":"book.magicka.mode_channel"));
        if(entry!=null && !guide && !entry.incantation().isEmpty())body=Component.literal("").append(body).append("\n\n").append(Component.translatable("book.magicka.incantation"));
        var lines=font.split(body,dw);int visible=Math.max(1,(y+bookHeight-72-textTop)/11);
        scroll=Math.clamp(scroll,0,Math.max(0,lines.size()-visible));
        for(int i=0;i<visible && i+scroll<lines.size();i++)g.text(font,lines.get(i+scroll),dx,textTop+i*11,MagicUi.INK,false);
        if(lines.size()>visible){int bottom=textTop+visible*11,thumb=Math.max(5,(bottom-textTop)*visible/lines.size()),offset=(bottom-textTop-thumb)*scroll/Math.max(1,lines.size()-visible);
            g.fill(x+bookWidth-12,textTop,x+bookWidth-10,bottom,0xFFC9B68D);g.fill(x+bookWidth-12,textTop+offset,x+bookWidth-10,textTop+offset+thumb,0xFF80653F);}
        MagicUi.small(g,Component.translatable("book.magicka.catalog_footer",list.size()),dx,y+bookHeight-61,0xFF816B50);
        g.fill(x+12,y+bookHeight-46,x+bookWidth-12,y+bookHeight-45,0xFFAA8C59);
        for(int i=0;i<4;i++){
            int sx=slotX(i),sy=y+bookHeight-40,sw=slotWidth();int packed=BookPreferences.get().slot(i);
            boolean hover=mouseX>=sx && mouseX<sx+sw && mouseY>=sy && mouseY<sy+29;
            g.fill(sx,sy,sx+sw,sy+29,hover?0xFF54445A:0xFF2B2935);g.outline(sx,sy,sw,29,hover?MagicUi.GOLD:0xFF88714E);
            MagicUi.small(g,MagicClient.quickKey(i),sx+4,sy+3,MagicUi.GOLD);
            String label=packed==0?Component.translatable("book.magicka.assign").getString():MagicClient.spell(Formula.unpack(packed).key()).displayName().getString();
            MagicUi.small(g,Component.literal(font.plainSubstrByWidth(label,(int)((sw-8)/.75))),sx+4,sy+17,0xFFF1E4C8);
        }
        super.extractRenderState(g,mouseX,mouseY,partialTick);
        if(dragging && dragFormula!=0){g.pose().pushMatrix();g.nextStratum();var f=Formula.unpack(dragFormula);for(int i=0;i<f.size();i++)MagicUi.socket(g,f.elements().get(i),mouseX+5+i*26,mouseY-12,true);g.pose().popMatrix();}
        else for(int i=0;i<4;i++)if(mouseX>=slotX(i) && mouseX<slotX(i)+slotWidth() && mouseY>=y+bookHeight-40 && mouseY<y+bookHeight-11)
            g.setTooltipForNextFrame(font,Component.translatable("book.magicka.quick_help"),mouseX,mouseY);
    }
    private void renderStats(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick){
        g.fill(x+7,y+31,x+bookWidth-7,y+bookHeight-7,0xFFF1E4C8);g.fill(x+13,y+29,x+bookWidth-13,y+30,0xFFAA8C59);
        var hint=font.split(Component.translatable("book.magicka.stats.hint"),(int)((bookWidth-28)/.75));
        for(int i=0;i<Math.min(2,hint.size());i++){g.pose().pushMatrix();g.pose().translate(x+14,y+33+i*7);g.pose().scale(.75f,.75f);g.text(font,hint.get(i),0,0,0xFF816B50,false);g.pose().popMatrix();}
        for(var row:statRows){
            int value=MagicNetwork.clientStats[row.stat.ordinal()];
            Component label=Component.translatable("book.magicka.stat."+row.stat.key());
            int color=value==MagicStats.DEFAULT?MagicUi.INK:0xFF8A3B12;
            if(font.width(label)<=row.labelWidth-4)g.text(font,label,row.x,row.y+4,color,false);
            else MagicUi.small(g,Component.literal(font.plainSubstrByWidth(label.getString(),(int)((row.labelWidth-4)/.75))),row.x,row.y+5,color);
        }
        super.extractRenderState(g,mouseX,mouseY,partialTick);
        for(var row:statRows)if(mouseX>=row.x && mouseX<row.x+row.labelWidth && mouseY>=row.y && mouseY<row.y+18){
            g.setTooltipForNextFrame(font,font.split(Component.translatable("book.magicka.stat."+row.stat.key()+".desc",row.stat.min,row.stat.max),220),mouseX,mouseY);
        }
    }
    private int slotWidth(){return (bookWidth-27)/4-3;}
    private int slotX(int index){return x+12+index*((bookWidth-27)/4);}
    private int slotAt(double mx,double my){for(int i=0;i<4;i++)if(mx>=slotX(i) && mx<slotX(i)+slotWidth() && my>=y+bookHeight-40 && my<y+bookHeight-11)return i;return -1;}
    private void assign(int slot,int packed){BookPreferences.get().assign(slot,packed);remember();BookPreferences.save();}
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick){double mx=event.x(),my=event.y();int button=event.button();
        if(stats)return super.mouseClicked(event,doubleClick);
        var list=entries();int slot=slotAt(mx,my);
        if(slot>=0){if(button==1)assign(slot,0);else if(button==0 && !list.isEmpty())assign(slot,list.get(selected).spokenPacked());return true;}
        if(button==0){
            int index=page*rows+(int)((my-y-LIST_TOP)/23);
            if(mx>=x+12 && mx<divider-11 && my>=y+LIST_TOP && my<y+LIST_TOP+rows*23 && index<list.size())dragFormula=list.get(index).spokenPacked();
            else if(mx>divider && my>=y+64 && my<y+130 && !list.isEmpty())dragFormula=list.get(selected).spokenPacked();
            else dragFormula=0;
            dragX=mx;dragY=my;dragging=false;
        }
        return super.mouseClicked(event,doubleClick);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy){double mx=event.x(),my=event.y();int button=event.button();
        if(button==0 && dragFormula!=0 && Math.abs(mx-dragX)+Math.abs(my-dragY)>4){dragging=true;return true;}return super.mouseDragged(event,dx,dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event){double mx=event.x(),my=event.y();int button=event.button();
        if(button==0 && dragging){int slot=slotAt(mx,my);if(slot>=0)assign(slot,dragFormula);dragFormula=0;dragging=false;super.mouseReleased(event);return true;}
        dragFormula=0;return super.mouseReleased(event);
    }
    private void remember(){var saved=BookPreferences.get();var list=entries();saved.selectedId=list.isEmpty()?"":list.get(Math.clamp(selected,0,list.size()-1)).id().toString();saved.query=query;saved.tag=tag;saved.size=size;saved.elements=elements;saved.page=page;saved.scroll=scroll;}
    @Override public void removed(){remember();BookPreferences.save();super.removed();}
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){
        if(stats)return super.mouseScrolled(mx,my,horizontal,vertical);
        if(mx>divider)scroll=Math.max(0,scroll-(int)Math.signum(vertical)*3);
        else{page=Math.clamp(page-(int)Math.signum(vertical),0,Math.max(0,(entries().size()-1)/rows));selected=page*rows;rebuild(false);}return true;
    }
    @Override public boolean keyPressed(KeyEvent event){
        if(!(getFocused() instanceof EditBox box && box.isFocused()) && MagicClient.bookMatches(event)){onClose();return true;}return super.keyPressed(event);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void extractBackground(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick){}
    private static final class StatSlider extends net.minecraft.client.gui.components.AbstractSliderButton {
        private final MagicStats.Stat stat;private final EditBox box;
        StatSlider(int x,int y,int width,int height,MagicStats.Stat stat,EditBox box){
            super(x,y,width,height,Component.empty(),0);this.stat=stat;this.box=box;show(MagicNetwork.clientStats[stat.ordinal()]);
        }
        void show(int value){this.value=value<=MagicStats.DEFAULT?.5*(value-stat.min)/(double)(MagicStats.DEFAULT-stat.min):.5+.5*Math.sqrt((value-MagicStats.DEFAULT)/(double)(stat.max-MagicStats.DEFAULT));updateMessage();}
        int current(){int raw=(int)Math.round(value<=.5?stat.min+value/.5*(MagicStats.DEFAULT-stat.min):MagicStats.DEFAULT+Math.pow((value-.5)/.5,2)*(stat.max-MagicStats.DEFAULT));int step=raw<200?1:raw<1000?5:25;return stat.clamp(Math.round(raw/(float)step)*step);}
        @Override protected void updateMessage(){setMessage(Component.literal(current()+"%"));}
        @Override protected void applyValue(){if(box!=null){box.setResponder(t->{});box.setValue(Integer.toString(current()));box.setResponder(t->{if(!t.matches("\\d*")){box.setValue(t.replaceAll("\\D",""));return;}if(t.isEmpty())return;int v=stat.clamp(Integer.parseInt(t));show(v);sendStat(stat.ordinal(),v);});}}
        @Override public void onRelease(MouseButtonEvent event){super.onRelease(event);sendStat(stat.ordinal(),current());}
        @Override public void extractWidgetRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick){
            g.fill(getX(),getY()+6,getX()+width,getY()+10,0xFFA58B64);
            g.fill(getX()+width/2,getY()+3,getX()+width/2+1,getY()+13,0xFF6C533C);
            int knob=getX()+(int)(value*(width-8));
            g.fill(getX(),getY()+6,knob+4,getY()+10,0xFF7A4E8C);
            g.fill(knob,getY()+1,knob+8,getY()+15,isHoveredOrFocused()?0xFFE1C995:0xFFCDB68D);g.outline(knob,getY()+1,8,14,0xFF4C3D50);
            if(isHovered())g.centeredText(net.minecraft.client.Minecraft.getInstance().font,getMessage(),getX()+width/2,getY()-8,MagicUi.GOLD);
        }
    }
    private static final class ElementButton extends Button {
        private final Element element;private final boolean on;
        ElementButton(int x,int y,int width,int height,Element element,boolean on,OnPress press){
            super(x,y,width,height,Component.translatable(element.key()),press,DEFAULT_NARRATION);
            this.element=element;this.on=on;setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(element.key())));
        }
        @Override protected void extractContents(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick){
            g.fill(getX(),getY(),getX()+width,getY()+height,on?0xFF4C3D50:isHoveredOrFocused()?0xFFE1C995:0xFFCDB68D);
            g.outline(getX(),getY(),width,height,on?0xFFFFEAC1:0xFFA58B64);
            int inset=on?3:4;g.fill(getX()+inset,getY()+inset,getX()+width-inset,getY()+height-inset,element.color);
        }
    }
    static final class BookButton extends Button {
        private final boolean selected;
        BookButton(int x,int y,int width,int height,Component label,OnPress press,boolean selected){super(x,y,width,height,label,press,DEFAULT_NARRATION);this.selected=selected;}
        @Override protected void extractContents(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick){
            int bg=selected?0xFF4C3D50:isHoveredOrFocused() && active?0xFFE1C995:0xFFCDB68D;
            g.fill(getX(),getY(),getX()+width,getY()+height,bg);g.outline(getX(),getY(),width,height,selected?0xFFAB8957:0xFFA58B64);
            var font=net.minecraft.client.Minecraft.getInstance().font;Component label=getMessage();
            if(font.width(label)>width-8 && font.width(label)*.75<=width-8){MagicUi.small(g,label,getX()+4,getY()+(height-6)/2,!active?0xFF9B896F:selected?0xFFFFEAC1:MagicUi.INK);return;}
            if(font.width(label)>width-8)label=Component.literal(font.plainSubstrByWidth(label.getString(),width-15)+"…");
            g.text(font,label,getX()+4,getY()+(height-8)/2,!active?0xFF9B896F:selected?0xFFFFEAC1:MagicUi.INK,false);
        }
    }
}
