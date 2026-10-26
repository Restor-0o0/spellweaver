package com.restor.magicka.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.restor.magicka.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.*;

/** Every spell keeps a fixed page in catalog order, blank pages follow up to the number of possible formulas; narrow screens show one page and slide the book. */
public final class GrimoireScreen extends Screen {
    private static final String[] TAGS={"damage","heal","control","construction","destruction","summon","utility"};
    private static final Item[] TAG_ICONS={Items.IRON_SWORD,Items.GOLDEN_APPLE,Items.COBWEB,Items.BRICKS,Items.TNT,Items.ZOMBIE_HEAD,Items.COMPASS};
    private static final int PANEL=88,TOP=4,MAX_HEIGHT=560,MIN_HALF=150,LEAF=11,STAGGER=2,OPEN_IN=7,OPEN_TURN=14,SEGMENTS=10,MUTED_INK=0xFF816B50,RUNE_INK=0x40302B37,FACT_INK=0x5A4A3A,CELL=18,CELL_W=20;
    private static final float RATIO=1.42f;
    private static final int FORMULAS=formulas();
    private static final Style RUNES=Style.EMPTY.withFont(ResourceLocation.withDefaultNamespace("alt"));
    private static final ResourceLocation EMBLEM=ResourceLocation.fromNamespaceAndPath(Magicka.ID,"textures/gui/cover_emblem.png");
    private static final ResourceLocation ELEMENTS=ResourceLocation.fromNamespaceAndPath(Magicka.ID,"textures/gui/elements.png");
    private record StatRow(MagicStats.Stat stat,int x,int y,int width){}
    private interface Face{void draw(GuiGraphics g,int side);}
    private record View(String guideKey){}
    private record Line(FormattedCharSequence text,int kind){}
    private static final View BOOK=new View(null);
    private static final int TEXT=0,HEADING=1,BULLET=2,MORE=3;
    private int x,y,bookX,bookWidth,bookHeight,half,spine,frameX,frameWidth,panelX,page,elements,ticks,spellPage,catalogRevision=-1;
    private float clock,camFrom,camTo,camStart=-100;
    private String query="",tag="all",restoreId="";
    private boolean single,laidOut;
    private EditBox search;
    private List<SpellDefinition> catalog=List.of();
    private List<Integer> matches=List.of();
    private View from;private int fromPage,flipStart=-100,leaves=1,direction=1;
    private final Map<String,List<FormattedCharSequence>> splits=new HashMap<>();
    private final Map<String,List<List<Line>>> layouts=new HashMap<>();
    private final List<net.minecraft.client.gui.components.AbstractWidget> statWidgets=new ArrayList<>();
    private final List<StatRow> statRows=new ArrayList<>();
    private int[] shownStats;
    private int dragFormula;private double dragX,dragY;private boolean dragging;

    public GrimoireScreen(){
        super(Component.translatable("book.magicka.title"));var saved=BookPreferences.get();
        tag=Arrays.asList(TAGS).contains(saved.tag)?saved.tag:"all";query=saved.query;restoreId=saved.selectedId;
        elements=net.minecraft.util.Mth.clamp(saved.elements,0,255);
    }
    private static int formulas(){
        int n=Element.values().length,total=0;long multisets=1;
        for(int k=1;k<=Formula.MAX;k++){multisets=multisets*(n+k-1)/k;total+=(int)multisets;}
        return total;
    }
    private void refresh(){
        catalog=RecipeRegistry.clientRecipes().stream().filter(d->d.tier().equalsIgnoreCase("Signature"))
                .sorted(Comparator.comparingInt((SpellDefinition d)->RecipeRegistry.catalogPosition(d.id())).thenComparing(d->d.id().toString())).toList();
        List<Integer> found=new ArrayList<>();
        if(filtering())for(int i=0;i<catalog.size();i++)if(matches(catalog.get(i)))found.add(front()+i);
        matches=found;
    }
    private boolean open(SpellDefinition spell){return SpellScrolls.clientOpen(minecraft.player,spell);}
    private String number(){String q=query.trim();return q.matches("\\d{1,5}")?q:null;}
    private List<String> words(){String q=query.toLowerCase(Locale.ROOT).replace("#","").trim();return number()!=null || q.isEmpty()?List.of():List.of(q.split("\\s+"));}
    private boolean filtering(){return !tag.equals("all") || elements!=0 || !words().isEmpty();}
    private boolean matches(SpellDefinition d){
        if(!open(d) || !tag.equals("all") && !SpellTags.of(d).contains(tag))return false;
        for(Element element:Element.values())if((elements&1<<element.ordinal())!=0 && d.formula().count(element)==0)return false;
        String text=(d.name()+" "+d.displayName().getString()+" "+d.tags()+" "+d.primaryDescription().getString()+" "+d.selfDescription().getString()+" "+tagText(d)).toLowerCase(Locale.ROOT);
        return words().stream().allMatch(text::contains);
    }
    private boolean dimmed(int p){return filtering() && p>=front() && p<statsPage() && !matches.contains(p);}
    private String tagText(SpellDefinition spell){return String.join(" · ",SpellTags.of(spell).stream().map(t->Component.translatable("book.magicka.tag."+t).getString()).toList());}
    private static Component label(SpellDefinition spell){return spell.ultimate()?Component.literal("★ ").append(spell.displayName()):spell.displayName();}
    private Component mageLine(){
        if(filtering())return Component.translatable("book.magicka.found",matches.size());
        var locked=RecipeRegistry.clientRecipes().stream().filter(SpellScrolls::locked).toList();
        long read=locked.stream().filter(s->SpellScrolls.clientKnown(s.id())).count();
        Component scrolls=Component.translatable("book.magicka.scrolls",read,locked.size());
        if(!MagicConfig.MAGE_LEVELS.get())return scrolls;
        int xp=MageLevel.clientXp(),level=MageLevel.level(xp);
        Component progress=level>=MagicConfig.MAX_MAGE_LEVEL.get()?Component.translatable("book.magicka.mage_max"):Component.literal(xp+" / "+MageLevel.xpFor(level+1));
        return Component.translatable("book.magicka.mage",level,progress,scrolls);
    }

    private View current(){return BOOK;}
    private int pageX(int side){return side==0?x+20:spine+12;}
    private int pageWidth(){return half-32;}
    private int pageTop(){return y+15;}
    private int pageBottom(){return y+bookHeight-24;}
    private int perPage(){return Math.max(1,(pageBottom()-pageTop())/10);}
    private static int leftPage(int spread){return 2*spread;}
    private static int rightPage(int spread){return 2*spread+1;}
    private List<FormattedCharSequence> split(String key,Component text,int width){return splits.computeIfAbsent(key+"@"+width,k->font.split(text,width));}
    private List<List<Line>> layout(String key){
        return layouts.computeIfAbsent(key+"@"+pageWidth()+"x"+perPage(),k->{
            int width=pageWidth(),per=perPage();List<List<Line>> out=new ArrayList<>();List<Line> page=new ArrayList<>();out.add(page);
            for(String paragraph:Component.translatable(key).getString().split("\n")){
                String text=paragraph.strip();if(text.isEmpty())continue;
                int kind=text.startsWith("# ")?HEADING:text.startsWith("• ")?BULLET:TEXT;if(kind!=TEXT)text=text.substring(2);
                var lines=font.split(Component.literal(text),kind==BULLET?width-8:width);
                int gap=page.isEmpty()?0:kind==HEADING?2:kind==TEXT?1:0,need=gap+(kind==HEADING?lines.size()+2:Math.min(2,lines.size()));
                if(page.size()+need>per){page=new ArrayList<>();out.add(page);gap=0;}
                for(int i=0;i<gap;i++)page.add(new Line(FormattedCharSequence.EMPTY,TEXT));
                for(int i=0;i<lines.size();i++){
                    if(page.size()>=per){page=new ArrayList<>();out.add(page);}
                    page.add(new Line(lines.get(i),i==0?kind:kind==BULLET?MORE:kind));
                }
            }
            return out;
        });
    }
    private int guidePages(){return layout("book.magicka.guide").size();}
    private int magicPages(){return layout("book.magicka.magic").size();}
    private int front(){return (guidePages()+magicPages()+1)/2*2;}
    private boolean creative(){return minecraft.player!=null && minecraft.player.isCreative();}
    private int statsPage(){return front()+(Math.max(FORMULAS,catalog.size())+1)/2*2;}
    private boolean onStats(){return creative() && page/2==statsPage()/2;}
    private int pages(View v){return statsPage()+(creative()?2:0);}

    @Override protected void init(){
        int side=0,room=0;
        for(int columns=1;columns<=2;columns++){
            bookHeight=Math.max(140,Math.min(height-TOP-6,MAX_HEIGHT));
            side=columns*CELL_W+4;room=width-PANEL-side-10;
            half=Math.round(bookHeight/RATIO);
            single=2*half>room && room/2<MIN_HALF;
            half=single?Math.min(half,room):Math.min(half,room/2);
            bookHeight=Math.round(half*RATIO);
            if(stacked())break;
        }
        bookWidth=2*half;frameWidth=single?half:bookWidth;
        y=TOP+Math.max(0,(height-TOP-4-bookHeight)/2);
        frameX=PANEL+8+(room-frameWidth)/2;panelX=frameX-PANEL-4;bookX=single?frameX-half/2:frameX;x=bookX;spine=x+half;splits.clear();layouts.clear();
        refresh();
        if(!laidOut){for(int i=0;i<catalog.size();i++)if(catalog.get(i).id().toString().equals(restoreId))page=front()+i;restoreId="";}
        page=net.minecraft.util.Mth.clamp(page,0,pages(current())-1);
        camFrom=camTo=target();camStart=-100;
        laidOut=true;from=null;
        rebuild(false);
    }
    @Override public void tick(){
        ticks++;
        if(catalogRevision!=RecipeRegistry.clientRevision()){catalogRevision=RecipeRegistry.clientRevision();refresh();rebuild(search!=null && search.isFocused());}
        else if(onStats() && shownStats!=MagicNetwork.clientStats && !isDragging())rebuild(search!=null && search.isFocused());
    }

    private float target(){return single?(page%2==0?half/2f:-half/2f):0;}
    private float camera(float time){float t=net.minecraft.util.Mth.clamp((time-camStart)/LEAF,0,1);return camFrom+(camTo-camFrom)*t*t*(3-2*t);}
    private void turn(View before,int beforePage){
        View after=current();page=net.minecraft.util.Mth.clamp(page,0,pages(after)-1);
        float aim=target();if(aim!=camTo){camFrom=camera(clock);camTo=aim;camStart=clock;}
        boolean same=Objects.equals(before.guideKey,after.guideKey);
        int was=beforePage/2,now=page/2;
        if(same && was==now)return;
        from=before;fromPage=beforePage;
        direction=same?Integer.signum(now-was):before.guideKey!=null && after.guideKey==null?-1:1;
        leaves=same?net.minecraft.util.Mth.clamp(Math.abs(now-was),1,5):1;
        if(ticks-flipStart>=3)minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN,1F));
        flipStart=ticks;
    }
    private void go(int target){View before=current();int was=page;page=target;turn(before,was);rebuild(search!=null && search.isFocused());}
    private int next(int dir){
        int plain=net.minecraft.util.Mth.clamp(single?page+dir:2*(page/2+dir),0,pages(current())-1);
        if(!filtering() || plain<front() && (dir<0 || page<front()))return plain;
        int best=page;
        for(int p:matches)if(single?(dir>0?p>page:p<page):(dir>0?p/2>page/2:p/2<page/2)){if(dir<0 || best==page){best=p;if(dir>0)break;}}
        return best==page && dir<0 && page>=front()?(single?front()-1:front()-2):best;
    }
    private void step(int dir){int target=next(dir);if(target!=page)go(target);}
    private int firstFrom(int p){for(int m:matches)if(m>=p)return m;return matches.isEmpty()?p:matches.get(0);}
    private void refilter(Runnable change){
        View before=current();int was=page;
        change.run();refresh();
        if(filtering() && !matches.contains(page))page=firstFrom(page);
        turn(before,was);rebuild(false);
    }
    private void openStats(){
        View before=current();int was=page;
        if(onStats())page=spellPage;else{remember(page);page=statsPage();}
        turn(before,was);rebuild(false);
    }
    private boolean inGuide(){return page<guidePages();}
    private boolean inMagic(){return page>=guidePages() && page<front();}
    private void remember(int p){if(p>=front() && p<statsPage())spellPage=p;else if(spellPage<front())spellPage=front();}
    private void openGuide(boolean magicPage){
        View before=current();int was=page;
        if(magicPage?inMagic():inGuide())page=Math.max(front(),spellPage);else{remember(page);page=magicPage?guidePages():0;}
        turn(before,was);rebuild(false);
    }
    private int firstOfSize(int size){
        for(int i=0;i<catalog.size();i++)if(catalog.get(i).formula().size()==size && open(catalog.get(i)) && (!filtering() || matches.contains(front()+i)))return front()+i;
        return -1;
    }
    private void jump(int size){
        int target=firstOfSize(size);if(target<0)return;
        View before=current();int was=page;page=target;turn(before,was);rebuild(search!=null && search.isFocused());
    }
    private SpellDefinition spellOn(int p){int i=p-front();return i>=0 && i<catalog.size() && open(catalog.get(i))?catalog.get(i):null;}
    private SpellDefinition shown(){return spellOn(page);}

    private void rebuild(boolean focus){
        clearWidgets();
        for(int i=0;i<TAGS.length;i++){String value=TAGS[i];
            addRenderableWidget(new TagButton(panelX+3+i%4*21,tagsY()+i/4*18,19,16,TAG_ICONS[i],Component.translatable("book.magicka.tag."+value),b->refilter(()->tag=tag.equals(value)?"all":value),tag.equals(value)));}
        var spell=shown();
        for(int i=1;i<=Formula.MAX;i++){int value=i;
            var b=addRenderableWidget(new RuneButton(panelX+(PANEL-84)/2+(i-1)*17,tagsY()+38,16,16,Component.literal(Integer.toString(i)),btn->jump(value),spell!=null && spell.formula().size()==i));
            b.active=firstOfSize(i)>=0;b.setTooltip(Component.translatable("book.magicka.filter.points",i));}
        for(int i=0;i<Element.values().length;i++){Element element=Element.values()[i];int bit=1<<i;
            addRenderableWidget(new RuneElement(sideX(1)+1,sideY(1,i),16,16,element,(elements&bit)!=0,b->refilter(()->elements^=bit)));}
        search=new SearchBox(font,panelX+4,y+5,PANEL-8,14,Component.translatable("book.magicka.search"));
        search.setValue(query);
        search.setResponder(value->{
            View before=current();int was=page;
            query=value;refresh();
            var n=number();
            if(n!=null)page=front()+Integer.parseInt(n)-1;else if(filtering() && !matches.contains(page))page=firstFrom(page);
            turn(before,was);rebuild(true);
        });
        addRenderableWidget(search);if(focus)setFocused(search);
        mark(0,"×",null,b->onClose(),false);
        mark(1,"?",Component.translatable("book.magicka.guide_tip"),b->openGuide(false),inGuide());
        mark(2,"✦",Component.translatable("book.magicka.magic_button"),b->openGuide(true),inMagic());
        mark(3,"✎",Component.translatable("book.magicka.shape"),b->minecraft.setScreen(new ShapeEditorScreen(this)),false);
        if(creative())mark(4,"⚙",Component.translatable("book.magicka.stats"),b->openStats(),onStats());
        int arrowY=y+bookHeight-23;
        addRenderableWidget(new RuneButton(frameX+9,arrowY,14,14,Component.literal("◀"),b->step(-1),false)).active=next(-1)!=page;
        addRenderableWidget(new RuneButton(frameX+frameWidth-23,arrowY,14,14,Component.literal("▶"),b->step(1),false)).active=next(1)!=page;
        statWidgets.clear();statRows.clear();
        if(onStats())for(int side=0;side<2;side++)if(!single || side==page%2)statControls(side);
    }
    private void statControls(int side){
        shownStats=MagicNetwork.clientStats;
        int bx=bookX+Math.round(target()),px=side==0?bx+20:bx+half+12,pw=pageWidth(),top=pageTop()+16;
        var all=MagicStats.Stat.values();int per=(all.length+1)/2,row=statRow(per),labelWidth=pw*9/20;
        for(int i=side*per;i<Math.min(all.length,(side+1)*per);i++){
            var stat=all[i];int ry=top+(i-side*per)*row;
            statRows.add(new StatRow(stat,px,ry,labelWidth));
            statWidgets.add(addRenderableWidget(new BookWidgets.StatSlider(px+labelWidth+2,ry,pw-labelWidth-20,16,stat,null)));
            var reset=addRenderableWidget(new RuneButton(px+pw-14,ry+1,14,14,Component.literal("↺"),b->{BookWidgets.sendStat(stat.ordinal(),MagicStats.DEFAULT);rebuild(false);},false));
            reset.setTooltip(Component.translatable("book.magicka.stats.reset.tip"));statWidgets.add(reset);
        }
        if(side==1){var label=Component.translatable("book.magicka.stats.reset_all");int w=font.width(label)+12;
            statWidgets.add(addRenderableWidget(new RuneButton(px+(pw-w)/2,top+per*row+4,w,14,label,b->{BookWidgets.sendStat(-1,0);MagicNetwork.clientStats=MagicStats.defaults();rebuild(false);},false)));}
    }
    private int statRow(int per){return Math.min(22,(pageBottom()-pageTop()-16-20)/per);}
    private void statsPage(GuiGraphics g,int side,int px,int top,int pw){
        var heading=Component.translatable("book.magicka.stats");
        g.drawString(font,heading,px+pw/2-font.width(heading)/2,top+2,MagicUi.INK,false);
        var all=MagicStats.Stat.values();int per=(all.length+1)/2,row=statRow(per),labelWidth=pw*9/20;
        for(int i=side*per;i<Math.min(all.length,(side+1)*per);i++){
            var stat=all[i];int ry=top+16+(i-side*per)*row,value=MagicNetwork.clientStats[stat.ordinal()];
            String label=font.plainSubstrByWidth(Component.translatable("book.magicka.stat."+stat.key()).getString(),(int)(labelWidth/.75f));
            MagicUi.small(g,Component.literal(label),px,ry+1,MagicUi.INK);
            MagicUi.small(g,Component.literal(value+"%"),px,ry+9,value==MagicStats.DEFAULT?MUTED_INK:0xFF8A3B12);
        }
    }
    private void mark(int at,String glyph,Component tip,Button.OnPress press,boolean on){
        var b=addRenderableWidget(new RuneButton(sideX(0),sideY(0,at),18,16,Component.literal(glyph),press,on));if(tip!=null)b.setTooltip(tip);
    }
    private boolean stacked(){return bookHeight-8>=13.5f*16;}
    private int sideX(int group){return frameX+frameWidth+4+(stacked()?0:group)*CELL_W;}
    private int tagsY(){return slotY(4)+2;}
    private int sideY(int group,int index){
        float cell=Math.min(CELL,(bookHeight-8)/13.5f),at=group==1 && stacked()?5.5f+index:index;
        return y+6+Math.round(at*cell);
    }

    @Override public void render(com.mojang.blaze3d.vertex.PoseStack pose,int mouseX,int mouseY,float partialTick){var g=new GuiGraphics(pose);
        g.fill(0,0,width,height,0xB810101B);
        float time=ticks+partialTick;clock=time;
        View now=current();layer=0;nest=0;
        float elapsed=time-flipStart;int length=LEAF+STAGGER*(leaves-1);
        if(from!=null && elapsed>=length)from=null;
        float shut=single?-half/2f:0,open=net.minecraft.util.Mth.clamp((time-OPEN_IN)/OPEN_TURN,0,1);
        float cam=time<OPEN_IN+OPEN_TURN?shut+(camTo-shut)*open*open*(3-2*open):camera(time);
        x=bookX+Math.round(cam);spine=x+half;
        if(time<OPEN_IN)closed(g,time/OPEN_IN);
        else if(time<OPEN_IN+OPEN_TURN)opening(g,now,(time-OPEN_IN)/OPEN_TURN);
        else{
            boards(g,0);boards(g,1);
            if(from==null){page(g,now,leftPage(page/2),0);page(g,now,rightPage(page/2),1);}
            else flipping(g,now,elapsed);
            layered(g,()->g.fill(spine-1,y+3,spine+1,y+bookHeight-3,0xFF2A1A18));
        }
        boolean settled=from==null && time>=OPEN_IN+OPEN_TURN && Math.abs(cam-camTo)<.5f;
        for(var widget:statWidgets)widget.visible=settled;
        com.mojang.blaze3d.systems.RenderSystem.clear(256,net.minecraft.client.Minecraft.ON_OSX);
        panel(g,now,mouseX,mouseY,time);
        super.render(g.pose(),mouseX,mouseY,partialTick);
        for(var child:children())if(child instanceof TipButton b && b.visible && b.tip!=null && b.isMouseOver(mouseX,mouseY)){g.renderTooltip(font,b.tip,mouseX,mouseY);break;}
        if(dragging && dragFormula!=0){g.pose().pushPose();g.pose().translate(0,0,400);var f=Formula.unpack(dragFormula);for(int i=0;i<f.size();i++)MagicUi.socket(g,f.elements().get(i),mouseX+5+i*26,mouseY-12,true);g.pose().popPose();return;}
        if(slotAt(mouseX,mouseY)>=0)g.renderTooltip(font,Component.translatable("book.magicka.quick_help"),mouseX,mouseY);
        if(settled)for(var row:statRows)if(mouseX>=row.x && mouseX<row.x+row.width && mouseY>=row.y && mouseY<row.y+16)
            g.renderTooltip(font,font.split(Component.translatable("book.magicka.stat."+row.stat.key()+".desc",row.stat.min,row.stat.max),220),mouseX,mouseY);
    }
    private void flipping(GuiGraphics g,View now,float elapsed){
        boolean forward=direction>0;int fromSpread=fromPage/2,spread=page/2;
        int oldL=leftPage(fromSpread),oldR=rightPage(fromSpread),newL=leftPage(spread),newR=rightPage(spread);
        if(forward){page(g,from,oldL,0);page(g,now,newR,1);}else{page(g,now,newL,0);page(g,from,oldR,1);}
        int started=Math.min(leaves,(int)(elapsed/STAGGER)+1);
        int seed=fromSpread*31+spread;
        if(started<leaves)face(g,(gg,side)->runePage(gg,side,seed+started),forward?1:0);
        for(int i=0;i<started;i++){
            float t=Math.min(1,(elapsed-i*STAGGER)/LEAF);int leaf=i;
            Face front=i==0?(gg,side)->page(gg,from,forward?oldR:oldL,side):(gg,side)->runePage(gg,side,seed+leaf);
            Face back=i==leaves-1?(gg,side)->page(gg,now,forward?newL:newR,side):(gg,side)->runePage(gg,side,seed+leaf+50);
            if(t>=1)face(g,back,forward?0:1);else leaf(g,t,forward,half-7,front,back);
        }
    }
    private void closed(GuiGraphics g,float t){
        float back=1.7f,u=t-1,eased=1+(back+1)*u*u*u+back*u*u;
        float cx=spine+half/2f,cy=y+bookHeight/2f,scale=.55f+.45f*eased;
        g.pose().pushPose();g.pose().translate(cx,cy-(1-eased)*18,0);g.pose().scale(scale,scale,1);g.pose().translate(-cx,-cy,0);
        closedBook(g);
        g.pose().popPose();
    }
    private void closedBook(GuiGraphics g){
        int r=x+bookWidth,b=y+bookHeight;
        g.fill(spine+2,y+4,r+3,b+4,0x70000000);
        for(int k=0;k<3;k++)g.fill(spine+2,b-3+k,r-4-k,b-2+k,k%2==0?0xFFE6D6B4:0xFFC9B48C);
        g.fill(r-4,y+6,r-1,b-4,0xFFE6D6B4);
        coverFront(g,spine-2,y,r-3,b-3);
        g.fill(spine-7,y,spine-1,b-3,0xFF3A2320);
        for(int band:new int[]{y+20,b-25})g.fill(spine-7,band,spine-1,band+2,0xFF8A6A3A);
    }
    private void opening(GuiGraphics g,View now,float t){
        boards(g,1);page(g,now,rightPage(page/2),1);
        layered(g,()->g.fill(spine-1,y+3,spine+1,y+bookHeight-3,0xFF2A1A18));
        int r=x+bookWidth;
        leaf(g,t,true,half,(gg,side)->coverFront(gg,spine,y,r,y+bookHeight),(gg,side)->{boards(gg,0);page(gg,now,leftPage(page/2),0);});
    }

    private void bar(GuiGraphics g,int bx,int by,int bw,int bh,float time){
        MagicUi.panel(g,bx,by,bw,bh);
        g.fillGradient(bx+1,by+1,bx+bw-1,by+bh-1,0x403A2466,0x08000000);
        int perimeter=2*(bw+bh);
        for(int k=0;k<2;k++){
            int p=(int)(time*3+k*perimeter/2)%perimeter,px,py;
            if(p<bw){px=bx+p;py=by;}else if(p<bw+bh){px=bx+bw-1;py=by+p-bw;}else if(p<2*bw+bh){px=bx+bw-1-(p-bw-bh);py=by+bh-1;}else{px=bx;py=by+bh-1-(p-2*bw-bh);}
            g.fill(px-1,py-1,px+2,py+2,0x60D4B77A);g.fill(px,py,px+1,py+1,0xFFFFEAC1);
        }
    }
    private static int hash(int a,int b){int h=a*0x27D4EB2D^b*0x165667B1;h^=h>>>15;h*=0x2C1B3C6D;h^=h>>>12;return h;}
    private void leather(GuiGraphics g,int l,int t,int r,int b){
        g.fill(l,t,r,b,0xFF4A2F2A);
        int w=r-l,h=b-t,count=w*h/110;
        for(int i=0;i<count;i++){int k=hash(i,(l-x)*7+t);int px=l+Math.floorMod(k,w),py=t+Math.floorMod(k>>>11,h);
            g.fill(px,py,px+1+(k>>>29&1),py+1,(k&4)==0?0x22000000:0x14FFE0C0);}
    }
    private void boards(GuiGraphics g,int side){
        int l=side==0?x:spine,r=side==0?spine:x+bookWidth,b=y+bookHeight;
        g.fill(l+2,y+3,r+3,b+4,0x55000000);
        leather(g,l,y,r,b);
        g.fill(l,y,r,y+1,0xFF221412);g.fill(l,b-1,r,b,0xFF221412);g.fill(side==0?l:r-1,y,side==0?l+1:r,b,0xFF221412);
        g.fill(l,y+2,r,y+3,0xFF8A6A3A);g.fill(l,b-3,r,b-2,0xFF8A6A3A);int edge=side==0?l+2:r-3;g.fill(edge,y+2,edge+1,b-2,0xFF8A6A3A);
        int corner=side==0?l+2:r-6;for(int cy:new int[]{y+2,b-6})g.fill(corner,cy,corner+4,cy+4,MagicUi.GOLD);
        for(int k=1;k<=3;k++){int c=k%2==1?0xFFC9B48C:0xFFE6D6B4;
            if(side==0)g.fill(x+7+k,b-7+k,spine-1,b-6+k,c);else g.fill(spine+1,b-7+k,r-7-k,b-6+k,c);}
        if(side==0)g.fill(x+5,y+6,x+7,b-6,0xFFC9B48C);else g.fill(r-7,y+6,r-5,b-6,0xFFC9B48C);
    }
    private void coverFront(GuiGraphics g,int l,int t,int r,int b){
        leather(g,l,t,r,b);
        g.renderOutline(l,t,r-l,b-t,0xFF221412);
        g.renderOutline(l+5,t+5,r-l-10,b-t-10,0xFF9A7A44);g.renderOutline(l+8,t+8,r-l-16,b-t-16,0xFF6E5530);
        for(int cx:new int[]{l+5,r-14})for(int cy:new int[]{t+5,b-14}){g.fill(cx,cy,cx+9,cy+2,MagicUi.GOLD);g.fill(cx+(cx>l+5?7:0),cy,cx+(cx>l+5?9:2),cy+9,MagicUi.GOLD);}
        int cx=(l+r)/2,cy=(t+b)/2+4,size=Math.min(r-l,b-t)>=210?128:64;
        emblem(g,cx-size/2+1,cy-size/2+1,size,0x60E8B888);emblem(g,cx-size/2,cy-size/2,size,0xF01C0F0B);
        int radius=size/2+14;String glyphs="spellweaverarcanumignisaquaglacies";
        for(int i=0;i<24;i++){double a=i*Math.PI*2/24;var glyph=Component.literal(String.valueOf(glyphs.charAt(i%glyphs.length()))).withStyle(RUNES);
            int gx=cx+(int)Math.round(Math.cos(a)*radius)-3,gy=cy+(int)Math.round(Math.sin(a)*radius)-4;
            g.drawString(font,glyph,gx+1,gy+1,0x50E8B888,false);g.drawString(font,glyph,gx,gy,0xFF241310,false);}
        int titleY=t+16;
        g.drawCenteredString(font,title,cx+1,titleY+1,0xFF1C0F0B);g.drawCenteredString(font,title,cx,titleY,MagicUi.GOLD);
    }
    private void emblem(GuiGraphics g,int ex,int ey,int size,int color){com.mojang.blaze3d.systems.RenderSystem.enableBlend();g.setColor((color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,(color>>>24)/255f);g.blit(EMBLEM,ex,ey,0,0,size,size,size,size);g.setColor(1,1,1,1);}
    private int paperLeft(int side){return side==0?x+7:spine;}
    private int paperRight(int side){return side==0?spine:x+bookWidth-7;}
    private void paper(GuiGraphics g,int side){
        int l=paperLeft(side),r=paperRight(side),b=y+bookHeight-6;
        g.fillGradient(l,y+5,r,b,0xFFF4E9D0,0xFFEADBB8);
        int w=r-l,h=b-y-5;
        for(int i=0;i<w*h/260;i++){int k=hash(i,side*991+3);int px=l+Math.floorMod(k,w),py=y+5+Math.floorMod(k>>>11,h);g.fill(px,py,px+1,py+1,0x10805A30);}
        for(int i=0;i<12;i++){int a=(12-i)*7,sx=side==0?r-2-i*2:l+i*2;g.fill(sx,y+5,sx+2,b,a<<24|0x4C352B);}
        int outer=side==0?l:r-3;g.fill(outer,y+5,outer+3,b,0x18000000);
    }
    private int layer,nest;
    private void face(GuiGraphics g,Face f,int side){layered(g,()->f.draw(g,side));}
    private void layered(GuiGraphics g,Runnable draw){g.pose().pushPose();if(nest++==0)com.mojang.blaze3d.systems.RenderSystem.clear(256,net.minecraft.client.Minecraft.ON_OSX);draw.run();nest--;g.pose().popPose();}
    private void page(GuiGraphics g,View v,int p,int side){face(g,(gg,s)->drawPage(gg,v,p,s),side);}
    private void drawPage(GuiGraphics g,View v,int p,int side){
        paper(g,side);
        int px=pageX(side),pw=pageWidth(),top=pageTop(),bottom=pageBottom();
        if(p>=pages(v)){runes(g,px,top,bottom,p*13+7);return;}
        if(p<front()){if(p<guidePages()+magicPages())guidePage(g,p<guidePages()?layout("book.magicka.guide").get(p):layout("book.magicka.magic").get(p-guidePages()),px,top,pw);return;}
        if(p-front()<catalog.size()){
            var spell=catalog.get(p-front());
            if(open(spell))spellPage(g,spell,px,top,pw,bottom,side);else sealed(g,px,top,pw,bottom,p);
            if(!single && p==page && spell==shown()){int rx=px+pw-8;g.fill(rx,y+2,rx+6,y+24,0xFF9A2E2A);g.fill(rx+2,y+21,rx+4,y+24,0xFFEADBB8);}
        }
        else if(p>=statsPage()){statsPage(g,p-statsPage(),px,top,pw);return;}
        String number=Integer.toString(p-front()+1);
        MagicUi.small(g,Component.literal(number),side==0?px+8:px+pw-8-(int)(font.width(number)*.75),y+bookHeight-17,MUTED_INK);
        if(dimmed(p))g.fill(paperLeft(side),y+5,paperRight(side),y+bookHeight-6,0x90EADBB8);
    }
    private void guidePage(GuiGraphics g,List<Line> lines,int px,int top,int pw){
        for(int i=0;i<lines.size();i++){
            var line=lines.get(i);int ly=top+i*10;
            switch(line.kind){
                case HEADING -> {int w=font.width(line.text),cx=px+pw/2;g.drawString(font,line.text,cx-w/2,ly,0xFF8A3B12,false);
                    g.fill(px+4,ly+4,cx-w/2-4,ly+5,0xFFAA8C59);g.fill(cx+w/2+4,ly+4,px+pw-4,ly+5,0xFFAA8C59);}
                case BULLET -> {g.fill(px+2,ly+3,px+5,ly+6,0xFF8A6A3A);g.drawString(font,line.text,px+8,ly,MagicUi.INK,false);}
                case MORE -> g.drawString(font,line.text,px+8,ly,MagicUi.INK,false);
                default -> g.drawString(font,line.text,px,ly,MagicUi.INK,false);
            }
        }
    }
    private void runePage(GuiGraphics g,int side,int seed){paper(g,side);runes(g,pageX(side),pageTop(),pageBottom(),seed);}
    private void runes(GuiGraphics g,int px,int top,int bottom,int seed){
        var random=new Random(seed*7919L);var text=new StringBuilder();
        for(int i=0;i<140;i++){int n=2+random.nextInt(7);for(int j=0;j<n;j++)text.append((char)('a'+random.nextInt(26)));text.append(random.nextInt(9)==0?"\n\n":" ");}
        var lines=split("runes"+seed,Component.literal(text.toString()).withStyle(RUNES),pageWidth());
        for(int i=0;i<lines.size() && top+i*10<=bottom-8;i++)g.drawString(font,lines.get(i),px,top+i*10,RUNE_INK,false);
    }
    private void sealed(GuiGraphics g,int px,int top,int pw,int bottom,int p){
        runes(g,px,top,bottom,p*13+7);
        var lines=split("sealed",Component.translatable("book.magicka.sealed"),pw-16);
        int h=lines.size()*10+8,ty=(top+bottom-h)/2;
        g.fill(px+2,ty,px+pw-2,ty+h,0xF0EFE2C4);g.renderOutline(px+2,ty,pw-4,h,0xFFAA8C59);
        for(int i=0;i<lines.size();i++)g.drawString(font,lines.get(i),px+pw/2-font.width(lines.get(i))/2,ty+5+i*10,MagicUi.INK,false);
    }
    private void spellPage(GuiGraphics g,SpellDefinition entry,int px,int top,int pw,int bottom,int side){
        int cx=px+pw/2,cy=top+2;
        for(var line:split(entry.id()+"/name",label(entry),pw)){g.drawString(font,line,cx-font.width(line)/2,cy,MagicUi.INK,false);cy+=11;}
        cy+=3;
        var spoken=entry.spoken();int runes=spoken.size()*28-4;
        for(int i=0;i<spoken.size();i++){MagicUi.socket(g,spoken.get(i),cx-runes/2+i*28,cy,true);if(i>0)g.drawString(font,"›",cx-runes/2+i*28-4,cy+8,0xFF786047,false);}
        cy+=29;
        int arm=pw/3;g.fill(cx-arm,cy,cx-5,cy+1,0xFFAA8C59);g.fill(cx+5,cy,cx+arm,cy+1,0xFFAA8C59);g.fill(cx-1,cy-2,cx+1,cy+3,0xFF8A6A3A);g.fill(cx-2,cy-1,cx+2,cy+2,0xFF8A6A3A);
        cy+=6;
        var tags=Component.literal(tagText(entry));
        MagicUi.small(g,tags,cx-(int)(font.width(tags)*.375),cy,MUTED_INK);cy+=10;
        boolean full=hasShiftDown();
        fit(g,entry.id()+(full?"/full":"/brief"),body(entry,full),px,cy,pw,bottom,MagicUi.INK);
        if(!full){var more=Component.translatable("book.magicka.more");MagicUi.small(g,more,px+pw/2-(int)(font.width(more)*.375),y+bookHeight-17,MUTED_INK);}
    }
    private Component body(SpellDefinition entry,boolean full){
        var facts=Component.translatable("book.magicka.cast_time",String.format(Locale.ROOT,"%.2f",entry.castTicks()/20.0));
        boolean ordered=entry.formula().size()>1 && entry.ordered();
        if(full){
            facts.append("\n").append(Component.translatable(entry.epic()?"book.magicka.mode_epic":!entry.channeled()?"book.magicka.mode_instant":entry.shape().construction()?"book.magicka.mode_growth":"book.magicka.mode_channel"));
            if(ordered)facts.append("\n").append(Component.translatable(entry.ultimate()?"book.magicka.incantation":"book.magicka.order"));
        }else if(ordered)facts.append(" · ").append(Component.translatable("book.magicka.ordered"));
        return Component.literal("").append(facts.withStyle(s->s.withColor(FACT_INK))).append("\n\n")
                .append(MagicClient.castHint("primary")).append("\n").append(full?entry.primaryDescription():entry.primaryBrief()).append("\n\n")
                .append(MagicClient.castHint("self")).append("\n").append(full?entry.selfDescription():entry.selfBrief());
    }
    private void fit(GuiGraphics g,String key,Component text,int px,int top,int pw,int bottom,int color){
        for(float scale:new float[]{1,.9f,.8f,.7f,.6f,.5f}){
            var lines=split(key,text,(int)(pw/scale));
            if(lines.size()*10*scale>bottom-top && scale>.5f)continue;
            g.pose().pushPose();g.pose().translate(px,top,0);g.pose().scale(scale,scale,1);
            for(int i=0;i<lines.size() && (i+1)*10*scale<=bottom-top+2;i++)g.drawString(font,lines.get(i),0,i*10,color,false);
            g.pose().popPose();return;
        }
    }
    private void leaf(GuiGraphics g,float t,boolean forward,int extent,Face front,Face back){
        float eased=t*t*(3-2*t);double theta=Math.PI*eased;float bend=.9f*(float)Math.sin(theta),s=forward?1:-1,seg=extent/(float)SEGMENTS,cy=y+bookHeight/2f;
        float[] starts=new float[SEGMENTS+1];double[] angles=new double[SEGMENTS];starts[0]=spine;
        float[] heights=new float[SEGMENTS+1];
        for(int k=0;k<SEGMENTS;k++){angles[k]=net.minecraft.util.Mth.clamp(theta-bend*k/(SEGMENTS-1),0,Math.PI);starts[k+1]=starts[k]+s*(float)Math.cos(angles[k])*seg;heights[k+1]=heights[k]+(float)Math.sin(angles[k])*seg;}
        float tip=starts[SEGMENTS],depth=(float)Math.sin(theta);int away=tip>=spine?1:-1;
        g.pose().pushPose();com.mojang.blaze3d.systems.RenderSystem.clear(256,net.minecraft.client.Minecraft.ON_OSX);
        for(int i=0;i<10;i++){int a=(int)(depth*(10-i)*8),sx=Math.round(tip)+away*i*3-(away<0?3:0);g.fill(sx,y+5,sx+3,y+bookHeight-6,a<<24|0x2A1A10);}
        g.pose().popPose();
        for(int k=0;k<SEGMENTS;k++){
            float c=(float)Math.cos(angles[k]),lift=1+.1f*(heights[k]+heights[k+1])/2/extent;
            if(Math.abs(c)<.015f)continue;
            int l=(int)Math.floor(Math.min(starts[k],starts[k+1]))-1,r=(int)Math.ceil(Math.max(starts[k],starts[k+1]))+1,grow=(int)(bookHeight*(lift-1)/2)+2;
            boolean showFront=c>0;int side=showFront==forward?1:0;
            float offset=showFront?starts[k]-c*spine-s*c*k*seg:starts[k]+c*spine-s*c*k*seg;
            g.enableScissor(l,y-grow,r,y+bookHeight+grow);
            g.pose().pushPose();g.pose().translate(offset,cy,0);g.pose().scale(showFront?c:-c,lift,1);g.pose().translate(0,-cy,0);
            Face shown=showFront?front:back;int at=side,alpha=(int)((1-Math.abs(c))*120)+(showFront?0:12);layered(g,()->{shown.draw(g,at);shade(g,at,alpha);});
            g.pose().popPose();g.disableScissor();
        }
    }
    private void shade(GuiGraphics g,int side,int alpha){int l=side==0?x:spine,r=side==0?spine:x+bookWidth;g.fill(l,y,r,y+bookHeight,alpha<<24|0x2A1A10);}
    private void panel(GuiGraphics g,View now,int mouseX,int mouseY,float time){
        var mage=font.split(mageLine(),(int)((PANEL-10)/.75f));
        int linesY=tagsY()+58;
        bar(g,panelX,y,PANEL,linesY-y+mage.size()*9+5,time+40);
        g.pose().pushPose();g.pose().translate(panelX+5,linesY,0);g.pose().scale(.75f,.75f,1);
        for(int i=0;i<mage.size();i++)g.drawString(font,mage.get(i),0,i*11,0xFFB59BD0,false);
        g.pose().popPose();
        for(int i=0;i<4;i++){
            int sx=slotX(i),sy=slotY(i),sw=slotWidth();int packed=BookPreferences.get().slot(i);
            boolean hover=mouseX>=sx && mouseX<sx+sw && mouseY>=sy && mouseY<sy+20;
            g.fill(sx,sy,sx+sw,sy+20,hover?0xFF2E2A44:0xFF161824);g.renderOutline(sx,sy,sw,20,hover?MagicUi.GOLD:0xFF5A4E70);
            MagicUi.small(g,MagicClient.quickKey(i),sx+4,sy+3,MagicUi.GOLD);
            String label=packed==0?Component.translatable("book.magicka.assign").getString():MagicClient.spell(Formula.unpack(packed).key()).displayName().getString();
            MagicUi.small(g,Component.literal(font.plainSubstrByWidth(label,(int)((sw-8)/.75))),sx+4,sy+11,0xFFE8E0F0);
        }
    }
    private int slotWidth(){return PANEL-8;}
    private int slotX(int i){return panelX+4;}
    private int slotY(int i){return y+24+i*24;}
    private int slotAt(double mx,double my){for(int i=0;i<4;i++)if(mx>=slotX(i) && mx<slotX(i)+slotWidth() && my>=slotY(i) && my<slotY(i)+20)return i;return -1;}
    private boolean overBook(double mx,double my){return mx>=frameX && mx<frameX+frameWidth && my>=y && my<y+bookHeight;}
    private int pageUnder(double mx){return single?page:mx<spine?leftPage(page/2):rightPage(page/2);}
    private void assign(int slot,int packed){BookPreferences.get().assign(slot,packed);remember();BookPreferences.save();}

    @Override public boolean mouseClicked(double mx,double my,int button){
        int slot=slotAt(mx,my);var spell=shown();
        if(slot>=0){if(button==1)assign(slot,0);else if(button==0 && spell!=null)assign(slot,spell.spokenPacked());return true;}
        dragFormula=0;
        if(button==0 && overBook(mx,my) && from==null){
            int under=pageUnder(mx);var picked=spellOn(under);
            if(picked!=null){if(under!=page){page=under;rebuild(false);}dragFormula=picked.spokenPacked();dragX=mx;dragY=my;dragging=false;}
        }
        return super.mouseClicked(mx,my,button);
    }
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){
        if(button==0 && dragFormula!=0 && Math.abs(mx-dragX)+Math.abs(my-dragY)>4){dragging=true;return true;}return super.mouseDragged(mx,my,button,dx,dy);
    }
    @Override public boolean mouseReleased(double mx,double my,int button){
        if(button==0 && dragging){int slot=slotAt(mx,my);if(slot>=0)assign(slot,dragFormula);dragFormula=0;dragging=false;super.mouseReleased(mx,my,button);return true;}
        dragFormula=0;return super.mouseReleased(mx,my,button);
    }
    @Override public boolean mouseScrolled(double mx,double my,double vertical){
        if(vertical!=0)step(vertical>0?-1:1);return true;
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        boolean typing=getFocused() instanceof EditBox box && box.isFocused();
        if(!typing){
            if(MagicClient.bookMatches(key,scan)){onClose();return true;}
            if(key==InputConstants.KEY_LEFT || key==InputConstants.KEY_PAGEUP){step(-1);return true;}
            if(key==InputConstants.KEY_RIGHT || key==InputConstants.KEY_PAGEDOWN){step(1);return true;}
        }
        return super.keyPressed(key,scan,modifiers);
    }
    private void remember(){var saved=BookPreferences.get();var spell=shown();if(spell!=null)saved.selectedId=spell.id().toString();saved.query=query;saved.tag=tag;saved.size=0;saved.elements=elements;saved.page=page;saved.scroll=0;}
    @Override public void removed(){remember();BookPreferences.save();super.removed();}
    @Override public boolean isPauseScreen(){return false;}

    private static final class SearchBox extends EditBox {
        SearchBox(net.minecraft.client.gui.Font font,int x,int y,int width,int height,Component label){super(font,x,y,width,height,label);}
        @Override public void insertText(String text){setMaxLength(100);super.insertText(text);setMaxLength(getValue().length());}
        @Override public void setValue(String text){setMaxLength(100);super.setValue(text);setMaxLength(getValue().length());}
        @Override public void renderButton(com.mojang.blaze3d.vertex.PoseStack pose,int mouseX,int mouseY,float partialTick){super.renderButton(pose,mouseX,mouseY,partialTick);if(getValue().isEmpty() && !isFocused())net.minecraft.client.Minecraft.getInstance().font.drawShadow(pose,getMessage(),x+4,y+(height-8)/2f,0xFF808080);}
    }
    private static abstract class TipButton extends Button {
        Component tip;
        TipButton(int x,int y,int width,int height,Component label,OnPress press){super(x,y,width,height,label,press);}
        void setTooltip(Component tip){this.tip=tip;}
    }
    private static final class RuneButton extends TipButton {
        private final boolean on;
        RuneButton(int x,int y,int width,int height,Component label,OnPress press,boolean on){super(x,y,width,height,label,press);this.on=on;}
        @Override public void renderButton(com.mojang.blaze3d.vertex.PoseStack pose,int mouseX,int mouseY,float partialTick){var g=new GuiGraphics(pose);
            boolean hover=isHoveredOrFocused() && active;
            if(on)g.fill(x-1,y-1,x+width+1,y+height+1,0x50D4B77A);
            g.fill(x,y,x+width,y+height,on?0xFF3A2D52:hover?0xFF2A2840:0xFF161824);
            g.renderOutline(x,y,width,height,on?MagicUi.GOLD:hover?0xFF8B7BB0:0xFF3D3850);
            int color=!active?0xFF5A5468:on?MagicUi.GOLD:hover?0xFFE8E0F0:MagicUi.MUTED;
            var font=net.minecraft.client.Minecraft.getInstance().font;Component label=getMessage();String glyph=label.getString();
            if(glyph.equals("◀") || glyph.equals("▶")){
                int cx=x+width/2,cy=y+height/2,dir=glyph.equals("▶")?1:-1;
                for(int i=0;i<4;i++){int lx=cx+dir*(i-2)-(dir<0?1:0);g.fill(lx,cy-3+i,lx+1,cy+4-i,color);}
                return;
            }
            g.drawString(font,label,x+(width-font.width(label)+1)/2,y+(height-8)/2,color,false);
        }
    }
    private static final class TagButton extends TipButton {
        private final Item icon;private final boolean on;private ItemStack stack;
        TagButton(int x,int y,int width,int height,Item icon,Component label,OnPress press,boolean on){
            super(x,y,width,height,label,press);this.icon=icon;this.on=on;setTooltip(label);
        }
        @Override public void renderButton(com.mojang.blaze3d.vertex.PoseStack pose,int mouseX,int mouseY,float partialTick){var g=new GuiGraphics(pose);
            boolean hover=isHoveredOrFocused();
            if(on)g.fill(x-1,y-1,x+width+1,y+height+1,0x50D4B77A);
            g.fill(x,y,x+width,y+height,on?0xFF3A2D52:hover?0xFF2A2840:0xFF161824);
            g.renderOutline(x,y,width,height,on?MagicUi.GOLD:hover?0xFF8B7BB0:0xFF3D3850);
            if(stack==null)stack=new ItemStack(icon);
            g.pose().pushPose();g.pose().translate(x+(width-12)/2f,y+(height-12)/2f,0);g.pose().scale(.75f,.75f,1);
            g.renderItem(stack,0,0);
            g.pose().popPose();
        }
    }
    private static final class RuneElement extends TipButton {
        private final Element element;private final boolean on;
        RuneElement(int x,int y,int width,int height,Element element,boolean on,OnPress press){
            super(x,y,width,height,Component.translatable(element.key()),press);
            this.element=element;this.on=on;setTooltip(Component.translatable(element.key()));
        }
        @Override public void renderButton(com.mojang.blaze3d.vertex.PoseStack pose,int mouseX,int mouseY,float partialTick){var g=new GuiGraphics(pose);
            boolean hover=isHoveredOrFocused();
            if(on)g.fill(x-2,y-2,x+width+2,y+height+2,0x90000000|element.color&0xFFFFFF);
            g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(width/32f,height/32f,1);
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();g.blit(ELEMENTS,0,0,element.ordinal()*32,0,32,32,256,32);
            g.pose().popPose();
            if(!on && !hover)g.fill(x+1,y+1,x+width-1,y+height-1,0x70000000);
        }
    }
}
