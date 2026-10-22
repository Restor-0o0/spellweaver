package com.restor.magicka.client;

import net.minecraft.util.Mth;
import com.restor.magicka.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Compact corner HUD; the crosshair, vanilla health and hotbar retain their own space. */
public final class MagicHud {
    private static void socket(GuiGraphics g,Element element,int x,int y,boolean lit){
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(.7f,.7f,1);MagicUi.socket(g,element,0,0,lit);g.pose().popPose();
    }
    private static String fit(String text,int width){
        var font=Minecraft.getInstance().font;
        return font.width(text)<=width?text:font.plainSubstrByWidth(text,width-font.width("…"))+"…";
    }
    private static void keyBadge(GuiGraphics g,Component key,int x,int y){
        String label=fit(key.getString().replace(" ",""),16);int width=(int)Math.ceil(Minecraft.getInstance().font.width(label)*.75f);
        g.pose().pushPose();g.pose().translate(0,0,200);
        g.fill(x,y,x+width+2,y+7,0xE80B0D14);MagicUi.small(g,Component.literal(label),x+1,y+1,MagicUi.GOLD);
        g.pose().popPose();
    }
    private static void countBadge(GuiGraphics g,int count,int x,int y){
        g.pose().pushPose();g.pose().translate(0,0,200);
        g.fill(x-6,y-7,x,y,0xE80B0D14);MagicUi.small(g,Component.literal(""+count),x-4,y-6,0xFFFFFFFF);
        g.pose().popPose();
    }
    public static void render(GuiGraphics g,MagicNetwork.State state,int vanillaHeight){
        int w=g.guiWidth(),h=g.guiHeight();Formula prepared=Formula.unpack(state.formula());
        int px=6,py=Math.max(6,h-110);
        MagicUi.panel(g,px,py,45,104);
        MagicUi.small(g,Component.translatable("hud.magicka.elements"),px+4,py+4,MagicUi.GOLD);
        if(state.instant())MagicUi.small(g,Component.literal("⚡"),px+37,py+4,0xFFFFDC84);
        else if(!state.enabled())MagicUi.small(g,MagicClient.toggleKey(),px+36,py+4,MagicUi.MUTED);
        for(Element element:Element.values()){
            int sx=px+4+(element.ordinal()%2)*20,sy=py+13+(element.ordinal()/2)*18;
            socket(g,element,sx,sy,state.enabled());
            long lockLeft=MagicNetwork.clientLocksUntil-System.currentTimeMillis();
            if(lockLeft>0 && (MagicNetwork.clientLocks&1<<element.ordinal())!=0){
                g.pose().pushPose();g.pose().translate(0,0,150);
                g.fill(sx,sy,sx+17,sy+17,0xB0100008);g.renderOutline(sx,sy,17,17,0xFFE0424A);
                g.fill(sx+3,sy+8,sx+14,sy+9,0xFFE0424A);
                MagicUi.small(g,Component.literal(""+(lockLeft/1000+1)),sx+6,sy+10,0xFFFF9A9A);
                g.pose().popPose();
            }
            keyBadge(g,MagicClient.elementKey(element),sx,sy);
            if(prepared.count(element)>0)countBadge(g,prepared.count(element),sx+17,sy+17);
        }
        int bx=px+14,by=py+85;
        g.fill(bx,by,bx+17,by+17,0xFF0E1119);g.renderOutline(bx,by,17,17,0xFF423E4C);
        g.pose().pushPose();g.pose().translate(bx+3,by+3,0);g.pose().scale(.7f,.7f,1);g.renderItem(MagicItems.SPELLBOOK.get().getDefaultInstance(),0,0);g.pose().popPose();
        keyBadge(g,MagicClient.bookKey(),bx,by);

        int panelX=w-117;
        // At narrow GUI sizes the right corner overlaps vanilla horizontally, so raise it above the bars.
        int bottom=w>=410?h-6:h-Math.max(49,vanillaHeight)-7;
        int presetY=Math.max(6,bottom-25);
        for(int i=0;i<4;i++){
            int sx=panelX+21+i*23,packed=BookPreferences.get().slot(i);
            g.fill(sx,presetY,sx+21,presetY+25,0xB0141820);g.renderOutline(sx,presetY,21,25,MagicNetwork.clientQueue.formula()!=0 && MagicNetwork.clientQueue.formula()==packed?MagicUi.GOLD:0xFF494454);
            MagicUi.small(g,Component.literal(fit(MagicClient.quickKey(i).getString().replace(" ",""),25)),sx+2,presetY+2,MagicUi.GOLD);
            if(packed!=0){var f=RecipeRegistry.clientResolve(Formula.unpack(packed).key()).spokenFormula();
                g.pose().pushPose();g.pose().translate(sx+5,presetY+10,0);g.pose().scale(.75f,.75f,1);MagicUi.icon(g,f.elements().get(0),0,0);g.pose().popPose();
                for(int j=0;j<f.size();j++)g.fill(sx+3+j*4,presetY+23,sx+6+j*4,presetY+24,f.elements().get(j).color);
            }
        }
        boolean queuing=MagicNetwork.clientQueue.formula()!=0,casting=state.remaining()>0;
        if(!state.enabled() && prepared.empty() && !queuing)return;
        int y=Math.max(6,presetY-37);MagicUi.panel(g,panelX,y,111,32);
        Formula display=queuing?Formula.unpack(MagicNetwork.clientQueue.formula()):prepared;
        Component label=display.empty()?Component.translatable("hud.magicka.prepared"):RecipeRegistry.clientResolve(display.key()).displayName();
        MagicUi.small(g,Component.literal(fit(label.getString(),130)),panelX+4,y+3,queuing?0xFFFFDC84:casting?0xFFE0B3FF:MagicUi.GOLD);
        for(int i=0;i<Formula.MAX;i++){
            socket(g,i<display.size()?display.elements().get(i):null,panelX+4+i*21,y+12,true);
            if(queuing && i>=prepared.size() && i<display.size())g.fill(panelX+5+i*21,y+13,panelX+20+i*21,y+28,0xAF0E1119);
        }
        if(state.instant()){
            boolean channel=!display.empty() && (RecipeRegistry.clientResolve(display.key()).channeled() || RecipeRegistry.clientResolve(display.key()).epic());
            String text=state.channeling()?"≈≈":channel?"≈":"⚡";
            MagicUi.small(g,Component.literal(text),panelX+109-(int)Math.ceil(Minecraft.getInstance().font.width(text)*.75f),y+13,channel?0xFF7FD0FF:0xFFFFDC84);
        }
        if(casting || queuing){
            float progress=queuing?MagicNetwork.clientQueue.elapsed()/(float)Math.max(1,Formula.unpack(MagicNetwork.clientQueue.formula()).size()*com.restor.magicka.CastingServer.QUEUE_TICKS_PER_ELEMENT):1-state.remaining()/(float)Math.max(1,state.total());
            g.fill(panelX+2,y+31,panelX+109,y+33,0xFF30293B);g.fill(panelX+2,y+31,panelX+2+(int)(107*Mth.clamp(progress,0,1)),y+33,queuing?MagicUi.GOLD:0xFFBF92E6);
        }
    }
    public static void render(GuiGraphics g,int offset){
        var mc=Minecraft.getInstance();if(mc.player==null || mc.options.hideGui || mc.player.isSpectator() || mc.screen!=null)return;
        render(g,MagicNetwork.clientState,offset);
    }
    private MagicHud(){}
}
