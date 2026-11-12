package com.restor.magicka.client;

import com.restor.magicka.Element;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;

public final class ElementIcons {
    private static final Identifier ELEMENTS=Identifier.fromNamespaceAndPath("magicka","textures/gui/elements.png");
    public static void draw(GuiGraphicsExtractor g,Element element,int x,int y,float size,boolean lit){
        background(g,element,x,y,size,lit);glyph(g,element,x,y,size,lit);
    }
    public static void background(GuiGraphicsExtractor g,Element element,int x,int y,float size,boolean lit){
        int tint;
        if(element==Element.STEAM||element==Element.ICE)tint=iconTone(element==Element.STEAM?0x9DAFBA:0x63B9EB,lit)&0xFFFFFF;
        else if(element==Element.SHIELD)tint=lit?element.color&0xFFFFFF:0x7F6C3A;
        else if(element==Element.MAGMA)tint=element.color&0xFFFFFF;
        else return;
        g.pose().pushMatrix();g.pose().translate(x,y);g.pose().scale(size/32,size/32);
        drawShieldBackground(g,0,0,32,32,0x70000000|tint);
        g.pose().popMatrix();
    }
    public static void glyph(GuiGraphicsExtractor g,Element element,int x,int y,float size,boolean lit){
        g.pose().pushMatrix();g.pose().translate(x,y);g.pose().scale(size/32,size/32);
        if(element==Element.STEAM||element==Element.ICE)drawVaporCrystal(g,0,0,32,32,element,lit);
        else if(element==Element.SHIELD)drawShield(g,0,0,32,32,lit);
        else{
            float scale=element==Element.MAGMA?.9f:1f;
            g.pose().translate((1-scale)*16,(1-scale)*16);g.pose().scale(scale,scale);
            int tone=lit?255:140;g.blit(RenderPipelines.GUI_TEXTURED,ELEMENTS,0,0,element.ordinal()*32,0,32,32,32*Element.values().length,32,HudView.color(0xFF000000|tone<<16|tone<<8|tone));
        }
        g.pose().popMatrix();
    }
    private static final String[] STEAM_ICON={
        "................",
        ".....h....h.....",
        "....h....h......",
        ".....s....s.....",
        "................",
        ".......hhh......",
        "...hhhhhhhh.....",
        "..hhhhhhhhhhh...",
        "..hhssssssshhh..",
        "...sssssssssss..",
        "....ddddddddd...",
        "................",
        "....s..s..s.....",
        "...d..d..d......",
        "................",
        "................"
    };
    private static final String[] ICE_ICON={
        "................",
        ".......h........",
        "......hhs.......",
        ".....hhhss......",
        "....hhhhsss.....",
        "...hhhhhsddd....",
        "...hhhhssddd....",
        "...hhhsssddd....",
        "...hhssssddd....",
        "...hsssssddd....",
        "....sssssdd.....",
        ".....ssssdd.....",
        "......ssd.......",
        ".......d........",
        "................",
        "................"
    };
    private static int iconTone(int rgb,boolean lit){
        if(lit)return 0xFF000000|rgb;
        return 0xFF000000|((rgb>>16&255)*140/255)<<16|((rgb>>8&255)*140/255)<<8|(rgb&255)*140/255;
    }
    private static void drawVaporCrystal(GuiGraphicsExtractor g,int x,int y,int width,int height,Element element,boolean lit){
        boolean steam=element==Element.STEAM;
        String[] glyph=steam?STEAM_ICON:ICE_ICON;
        int light=iconTone(steam?0xEEF3F2:0xD4F7FF,lit);
        int middle=iconTone(steam?0xBACDD2:0x7CD7F2,lit);
        int dark=iconTone(steam?0x7899A7:0x3789C4,lit);
        g.pose().pushMatrix();g.pose().translate(x,y);g.pose().scale(width/16f,height/16f);
        if(!steam)g.pose().translate(.5f,0);
        for(int row=0;row<16;row++)for(int col=0;col<16;col++){
            char pixel=glyph[row].charAt(col);
            if(pixel!='.')g.fill(col,row,col+1,row+1,HudView.color(pixel=='h'?light:pixel=='s'?middle:dark));
        }
        g.pose().popMatrix();
    }
    private static final int[] SHIELD_BACKGROUND_EDGE={14,10,8,7,5,4,4,3,2,2,1,1,1,1,0,0,0,0,1,1,1,1,2,2,3,4,4,5,7,8,10,14};
    private static void drawShieldBackground(GuiGraphicsExtractor g,int x,int y,int width,int height,int color){
        g.pose().pushMatrix();g.pose().translate(x,y);g.pose().scale(width/32f,height/32f);
        for(int row=0;row<32;row++){int left=SHIELD_BACKGROUND_EDGE[row];g.fill(left,row,32-left,row+1,HudView.color(color));}
        g.pose().popMatrix();
    }
    private static void drawShield(GuiGraphicsExtractor g,int x,int y,int width,int height,boolean lit){
        int tone=lit?255:140;
        g.pose().pushMatrix();g.pose().translate(x+width/2f,y+height/2f);g.pose().scale(width/32f*1.1f,height/32f*1.1f);g.pose().translate(-16f,-16.5f);
        for(int row=7;row<=25;row++){
            int left=row<=17?7:row-10;
            g.blit(RenderPipelines.GUI_TEXTURED,ELEMENTS,left,row,Element.SHIELD.ordinal()*32+left,row,32-left*2,1,32*Element.values().length,32,HudView.color(0xFF000000|tone<<16|tone<<8|tone));
        }
        g.pose().popMatrix();
    }
    private ElementIcons(){}
}
