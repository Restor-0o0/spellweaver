package com.restor.magicka.client;

import com.restor.magicka.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Compact corner HUD; the crosshair, vanilla health and hotbar retain their own space. */
public final class MagicHud {
    private record Rune(Element element,int composite,int sourceEnd){}
    private static void socket(GuiGraphicsExtractor g,Element element,int x,int y,boolean lit){
        g.pose().pushMatrix();g.pose().translate((float)(x),(float)(y));g.pose().scale(.7f,.7f);MagicUi.socket(g,element,0,0,lit);g.pose().popMatrix();
    }
    private static void socket(GuiGraphicsExtractor g,Rune rune,int x,int y){
        g.pose().pushMatrix();g.pose().translate((float)(x),(float)(y));g.pose().scale(.7f,.7f);
        if(rune==null)MagicUi.socket(g,null,0,0,true);else if(rune.composite>=0)MagicUi.compositeSocket(g,rune.composite,0,0);else MagicUi.socket(g,rune.element,0,0,true);
        g.pose().popMatrix();
    }
    private static java.util.List<Rune> resolve(Formula formula){
        var runes=new java.util.ArrayList<Rune>();
        for(int source=0;source<formula.size();source++){
            Element element=formula.elements().get(source);int found=-1,composite=-1;
            for(int i=runes.size()-1;i>=0;i--){
                if(runes.get(i).composite>=0)continue;
                int match=MagicUi.composite(element,runes.get(i).element);
                if(match>=0){found=i;composite=match;break;}
            }
            if(found>=0)runes.set(found,new Rune(null,composite,source+1));else runes.add(new Rune(element,-1,source+1));
        }
        return java.util.List.copyOf(runes);
    }
    private static String fit(String text,int width){
        var font=Minecraft.getInstance().font;
        return font.width(text)<=width?text:font.plainSubstrByWidth(text,width-font.width("…"))+"…";
    }
    private static void keyBadge(GuiGraphicsExtractor g,Component key,int x,int y){
        String label=fit(key.getString().replace(" ",""),16);int width=(int)Math.ceil(Minecraft.getInstance().font.width(label)*.75f);
        g.pose().pushMatrix();g.nextStratum();
        g.fill(x,y,x+width+2,y+7,HudView.color(0xE80B0D14));MagicUi.small(g,Component.literal(label),x+1,y+1,MagicUi.GOLD);
        g.pose().popMatrix();
    }
    private static void countBadge(GuiGraphicsExtractor g,int count,int x,int y){
        g.pose().pushMatrix();g.nextStratum();
        g.fill(x-6,y-7,x,y,HudView.color(0xE80B0D14));MagicUi.small(g,Component.literal(""+count),x-4,y-6,0xFFFFFFFF);
        g.pose().popMatrix();
    }
    private static void quickKeyBadge(GuiGraphicsExtractor g,int slot){
        String label=MagicClient.quickKey(slot).getString().replace(" ","");
        float scale=Math.min(.75f,20f/Math.max(1,Minecraft.getInstance().font.width(label)));
        g.pose().pushMatrix();g.pose().translate(2,1);g.pose().scale(scale,scale);
        g.text(Minecraft.getInstance().font,label,0,0,HudView.color(MagicUi.GOLD),false);
        g.pose().popMatrix();
    }
    private static ElementalTitan titan(){
        var mc=Minecraft.getInstance();
        if(mc.level==null || mc.player==null)return null;
        for(var entity:mc.level.entitiesForRendering())if(entity instanceof ElementalTitan titan && titan.isAlive() && !titan.sinking() && titan.ownedBy(mc.player))return titan;
        return null;
    }
    public static void render(GuiGraphicsExtractor g,MagicNetwork.State state,int vanillaHeight){
        var mc=Minecraft.getInstance();if(mc.player==null || mc.options.hideGui || mc.player.isSpectator() || mc.screen!=null)return;
        renderLayoutPreview(g,state,vanillaHeight);
    }
    public static void renderLayoutPreview(GuiGraphicsExtractor g,MagicNetwork.State state,int vanillaHeight){
        var order=MagicHudLayout.Widget.values();
        java.util.Arrays.sort(order,java.util.Comparator.comparingInt(MagicHudLayout::renderLayer));
        for(var widget:order){g.nextStratum();renderPart(g,state,vanillaHeight,widget);}
    }
    private static void renderPart(GuiGraphicsExtractor g,MagicNetwork.State state,int vanillaHeight,MagicHudLayout.Widget selected){
        int w=g.guiWidth(),h=g.guiHeight();Formula prepared=Formula.unpack(state.formula());boolean editor=Minecraft.getInstance().screen instanceof HudLayoutScreen;
        Formula castFormula=MagicNetwork.clientQueue.formula()!=0?Formula.unpack(MagicNetwork.clientQueue.formula()):prepared;
        MagicHudLayout.castCount(resolve(castFormula).size()+Math.max(0,com.restor.magicka.MageLevel.clientMaxElements(Minecraft.getInstance().player)-castFormula.size()));
        MagicHudLayout.clampToScreen(w,h,vanillaHeight);
        if(selected==MagicHudLayout.Widget.ELEMENTS&&(MagicHudLayout.visible(MagicHudLayout.Widget.ELEMENTS)||editor)){
        int px=6,py=Math.max(6,h-110);
        var elementsClip=MagicHudLayout.editBounds(MagicHudLayout.Widget.ELEMENTS,w,h,vanillaHeight);g.enableScissor(elementsClip.x(),elementsClip.y(),elementsClip.x()+elementsClip.width(),elementsClip.y()+elementsClip.height());
        HudView elementsView=new HudView(MagicHudLayout.Widget.ELEMENTS);elementsView.begin(g,px,py);
        drawPanel(g,MagicHudLayout.Widget.ELEMENTS,px,py,MagicHudLayout.elementPanelWidth(),MagicHudLayout.elementPanelHeight(),editor);
        elementsView.beginContent(g);
        if(MagicHudLayout.titleVisible(MagicHudLayout.Widget.ELEMENTS))MagicUi.small(g,Component.translatable("hud.magicka.elements"),px+4,py+4,MagicUi.GOLD);
        if(MagicHudLayout.titleVisible(MagicHudLayout.Widget.ELEMENTS)&&state.instant())MagicUi.small(g,Component.literal("⚡"),px+37,py+4,0xFFFFDC84);
        else if(MagicHudLayout.titleVisible(MagicHudLayout.Widget.ELEMENTS)&&!state.enabled())MagicUi.small(g,MagicClient.toggleKey(),px+36,py+4,MagicUi.MUTED);
        for(Element element:Element.BASE){
            int sx=px+(int)Math.round(MagicHudLayout.elementX(element.ordinal())),sy=py+(int)Math.round(MagicHudLayout.elementY(element.ordinal()));
            g.pose().pushMatrix();g.pose().translate((float)(sx),(float)(sy));float buttonScale=(float)MagicHudLayout.elementScale(element.ordinal());g.pose().scale(buttonScale,buttonScale);
            MagicUi.elementGlyph(g,element,0,0,17);
            long lockLeft=MagicNetwork.clientLocksUntil-System.currentTimeMillis();
            if(lockLeft>0 && (MagicNetwork.clientLocks&1<<element.ordinal())!=0){
                g.pose().pushMatrix();g.nextStratum();
                g.fill(0,0,17,17,HudView.color(0xB0100008));g.outline(0,0,17,17,HudView.color(0xFFE0424A));
                g.fill(3,8,14,9,HudView.color(0xFFE0424A));
                MagicUi.small(g,Component.literal(""+(lockLeft/1000+1)),6,10,0xFFFF9A9A);
                g.pose().popMatrix();
            }
            if(MagicHudLayout.textHints(MagicHudLayout.Widget.ELEMENTS))keyBadge(g,MagicClient.elementKey(element),0,0);
            if(prepared.count(element)>0)countBadge(g,prepared.count(element),17,17);
            g.pose().popMatrix();
        }
        elementsView.endContent(g);g.disableScissor();elementsView.end(g);
        if(!MagicHudLayout.visible(MagicHudLayout.Widget.ELEMENTS))dimWindow(g,MagicHudLayout.Widget.ELEMENTS,w,h,vanillaHeight);
        }
        if(selected==MagicHudLayout.Widget.BOOK_HINT)renderBookHint(g,w,h,vanillaHeight,editor);

        int panelX=w-117;
        // At narrow GUI sizes the right corner overlaps vanilla horizontally, so raise it above the bars.
        int bottom=w>=410?h-6:h-Math.max(49,vanillaHeight)-7;
        int quickRows=MagicHudLayout.quickRows();
        int presetY=Math.max(6,bottom-(quickRows*32-8));
        if(selected==MagicHudLayout.Widget.QUICK_SLOTS&&(MagicHudLayout.visible(MagicHudLayout.Widget.QUICK_SLOTS)||editor)){
        var quickClip=MagicHudLayout.editBounds(MagicHudLayout.Widget.QUICK_SLOTS,w,h,vanillaHeight);g.enableScissor(quickClip.x(),quickClip.y(),quickClip.x()+quickClip.width(),quickClip.y()+quickClip.height());
        HudView quickView=new HudView(MagicHudLayout.Widget.QUICK_SLOTS);quickView.begin(g,panelX,presetY);
        quickView.beginContent(g);
        for(int i=0;i<MagicHudLayout.quickCount();i++){
            double slotX=MagicHudLayout.quickSlotX(i),slotY=MagicHudLayout.quickSlotY(i);int packed=BookPreferences.get().slot(i);
            g.pose().pushMatrix();g.pose().translate((float)(panelX+slotX),(float)(presetY+slotY));g.pose().scale((float)MagicHudLayout.quickSlotScale(i),(float)MagicHudLayout.quickSlotScale(i));
            drawSlotBackground(g,MagicHudLayout.Widget.QUICK_SLOTS,packed!=0 && MagicNetwork.clientQueue.formula()==packed,editor);
            if(MagicHudLayout.textHints(MagicHudLayout.Widget.QUICK_SLOTS))quickKeyBadge(g,i);
            if(packed!=0){var f=MagicClient.spell(Formula.unpack(packed).key()).spokenFormula();
                MagicUi.formulaIcon(g,f,3,MagicHudLayout.textHints(MagicHudLayout.Widget.QUICK_SLOTS)?7:3,MagicHudLayout.textHints(MagicHudLayout.Widget.QUICK_SLOTS)?14:18);
                for(int j=0;j<Math.min(f.size(),7);j++)g.fill(2+j*3,22,4+j*3,23,HudView.color(f.elements().get(j).color));
            }
            g.pose().popMatrix();
        }
        quickView.endContent(g);g.disableScissor();quickView.end(g);
        if(!MagicHudLayout.visible(MagicHudLayout.Widget.QUICK_SLOTS))dimWindow(g,MagicHudLayout.Widget.QUICK_SLOTS,w,h,vanillaHeight);
        }
        boolean queuing=MagicNetwork.clientQueue.formula()!=0,casting=state.remaining()>0;
        ElementalTitan titan=titan();
        if(selected==MagicHudLayout.Widget.CAST_STATUS&&(MagicHudLayout.visible(MagicHudLayout.Widget.CAST_STATUS)||editor) && (editor || state.enabled() || !prepared.empty() || queuing || titan!=null)){
        int y=MagicHudLayout.base(MagicHudLayout.Widget.CAST_STATUS,w,h,vanillaHeight).y();
        var castClip=MagicHudLayout.editBounds(MagicHudLayout.Widget.CAST_STATUS,w,h,vanillaHeight);g.enableScissor(castClip.x(),castClip.y(),castClip.x()+castClip.width(),castClip.y()+castClip.height());
        HudView castView=new HudView(MagicHudLayout.Widget.CAST_STATUS);castView.begin(g,panelX,y);
        drawPanel(g,MagicHudLayout.Widget.CAST_STATUS,panelX,y,111,MagicHudLayout.castPanelHeight(),editor);
        castView.beginContent(g);
        Formula display=queuing?Formula.unpack(MagicNetwork.clientQueue.formula()):prepared;var runes=resolve(display);
        var spell=display.empty()?null:MagicClient.spell(display.key());
        boolean named=spell!=null && spell.ordered() && spell.spoken().equals(display.elements());
        Component label=spell==null?Component.translatable("hud.magicka.prepared"):named?spell.displayName():Component.empty();
        if(MagicHudLayout.titleVisible(MagicHudLayout.Widget.CAST_STATUS))MagicUi.small(g,Component.literal(fit(label.getString(),130)),panelX+4,y+3,queuing?0xFFFFDC84:casting?0xFFE0B3FF:MagicUi.GOLD);
        int cells=runes.size()+Math.max(0,com.restor.magicka.MageLevel.clientMaxElements(Minecraft.getInstance().player)-display.size());
        for(int i=0;i<cells;i++){
            Rune rune=i<runes.size()?runes.get(i):null;int runeY=MagicHudLayout.castSlotY(),slotX=MagicHudLayout.castSlotX(i,cells);socket(g,rune,panelX+slotX,y+runeY);
            if(queuing && rune!=null && rune.sourceEnd>prepared.size())g.fill(panelX+slotX+1,y+runeY+1,panelX+slotX+16,y+runeY+16,HudView.color(0xAF0E1119));
        }
        if(casting || queuing || titan!=null){
            float progress=titan!=null?titan.getHealth()/Math.max(1,titan.getMaxHealth()):queuing?MagicNetwork.clientQueue.elapsed()/(float)Math.max(1,Formula.unpack(MagicNetwork.clientQueue.formula()).size()*com.restor.magicka.CastingServer.QUEUE_TICKS_PER_ELEMENT):1-state.remaining()/(float)Math.max(1,state.total());
            int color=titan!=null?titan.element().color:queuing?MagicUi.GOLD:0xFFBF92E6;
            int progressY=y+MagicHudLayout.castSlotY()+15;
            g.fill(panelX+2,progressY,panelX+109,progressY+2,HudView.color(0xFF30293B));g.fill(panelX+2,progressY,panelX+2+(int)(107*Math.clamp(progress,0,1)),progressY+2,HudView.color(color));
        }
        castView.endContent(g);g.disableScissor();castView.end(g);
        if(!MagicHudLayout.visible(MagicHudLayout.Widget.CAST_STATUS))dimWindow(g,MagicHudLayout.Widget.CAST_STATUS,w,h,vanillaHeight);
        }
    }
    private static void renderBookHint(GuiGraphicsExtractor g,int width,int height,int vanillaHeight,boolean editor){
        var widget=MagicHudLayout.Widget.BOOK_HINT;if(!MagicHudLayout.visible(widget)&&!editor)return;
        var base=MagicHudLayout.base(widget,width,height,vanillaHeight);int x=base.x(),y=base.y();
        var bookClip=MagicHudLayout.editBounds(widget,width,height,vanillaHeight);g.enableScissor(bookClip.x(),bookClip.y(),bookClip.x()+bookClip.width(),bookClip.y()+bookClip.height());
        HudView bookView=new HudView(widget);bookView.begin(g,x,y);
        drawSmallBackground(g,widget,x,y,21,editor);
        bookView.beginContent(g);
        if(MagicHudLayout.textHints(widget)){g.pose().pushMatrix();g.pose().translate((float)(x+5),(float)(y+5));g.pose().scale(.7f,.7f);g.item(MagicItems.SPELLBOOK.get().getDefaultInstance(),0,0);g.pose().popMatrix();}
        if(MagicHudLayout.textHints(widget))keyBadge(g,MagicClient.bookKey(),x+2,y+2);bookView.endContent(g);g.disableScissor();bookView.end(g);
        if(!MagicHudLayout.visible(widget))dimWindow(g,widget,width,height,vanillaHeight);
    }
    private static void dimWindow(GuiGraphicsExtractor g,MagicHudLayout.Widget widget,int width,int height,int vanillaHeight){
        var b=MagicHudLayout.editBounds(widget,width,height,vanillaHeight);g.pose().pushMatrix();g.nextStratum();g.fill(b.x(),b.y(),b.x()+b.width(),b.y()+b.height(),HudView.color(0x80000000));g.pose().popMatrix();
    }
    private static void drawPanel(GuiGraphicsExtractor g,MagicHudLayout.Widget widget,int x,int y,int width,int height,boolean editor){
        width=MagicHudLayout.frameWidth(widget,width);height=MagicHudLayout.frameHeight(widget,height);
        if(MagicHudLayout.background(widget)){MagicUi.panel(g,x,y,width,height);return;}
        if(editor){g.fill(x,y,x+width,y+height,HudView.color(0x80141820));g.outline(x,y,width,height,HudView.color(0x80645D70));}
    }
    private static void drawSmallBackground(GuiGraphicsExtractor g,MagicHudLayout.Widget widget,int x,int y,int size,boolean editor){
        int width=MagicHudLayout.frameWidth(widget,size),height=MagicHudLayout.frameHeight(widget,size);
        if(MagicHudLayout.background(widget)){g.fill(x,y,x+width,y+height,HudView.color(0xFF0E1119));g.outline(x,y,width,height,HudView.color(0xFF423E4C));}
        else if(editor){g.fill(x,y,x+width,y+height,HudView.color(0x800E1119));g.outline(x,y,width,height,HudView.color(0x80423E4C));}
    }
    private static void drawSlotBackground(GuiGraphicsExtractor g,MagicHudLayout.Widget widget,boolean active,boolean editor){
        if(MagicHudLayout.background(widget)){g.fill(0,0,24,24,HudView.color(0xD0141820));g.outline(0,0,24,24,HudView.color(active?MagicUi.GOLD:0xFF494454));}
        else if(editor){g.fill(0,0,24,24,HudView.color(0x68141820));g.outline(0,0,24,24,HudView.color(active?0x80FFD875:0x80494454));}
    }
    public static void render(GuiGraphicsExtractor g){
        var mc=Minecraft.getInstance();if(mc.player==null || mc.options.hideGui || mc.player.isSpectator() || mc.screen!=null)return;
        render(g,MagicNetwork.clientState,Math.max(mc.gui.leftHeight,mc.gui.rightHeight));
    }
    private MagicHud(){}
}
