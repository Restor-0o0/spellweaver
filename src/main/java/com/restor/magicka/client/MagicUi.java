package com.restor.magicka.client;

import com.restor.magicka.Element;
import com.restor.magicka.ElementBlend;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Shared ink, brass and elemental glyphs for the grimoire and HUD. */
public final class MagicUi {
    public static final int GOLD = 0xFFD4B77A, INK = 0xFF302B37, MUTED = 0xFFAFA6B9;
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 2, y + 3, x + w + 2, y + h + 3, 0x60000000);
        g.fill(x, y, x + w, y + h, 0xDF141820);
        g.renderOutline(x, y, w, h, 0xFF494454);
        for (int dx : new int[]{0, w - 5}) for (int dy : new int[]{0, h - 1}) g.fill(x + dx, y + dy, x + dx + 5, y + dy + 1, GOLD);
    }
    public static void icon(GuiGraphics g, Element e, int x, int y) { ElementIcons.draw(g,e,x,y,16,true); }
    public static void socket(GuiGraphics g, Element e, int x, int y, boolean lit) {
        g.fill(x, y, x + 24, y + 24, 0xFF0E1119);
        g.renderOutline(x, y, 24, 24, lit && e != null ? e.color : 0xFF423E4C);
        if (e != null) {
            icon(g, e, x + 4, y + 3);
            g.fill(x + 6, y + 21, x + 18, y + 22, e.color);
        } else {
            g.fill(x + 11, y + 10, x + 13, y + 14, 0xFF514957);
            g.fill(x + 10, y + 11, x + 14, y + 13, 0xFF514957);
        }
    }
    public static int composite(Element first, Element second) {
        return ElementBlend.composite(first,second);
    }
    public static void compositeSocket(GuiGraphics g, int composite, int x, int y) {
        socket(g, Element.values()[8 + composite], x, y, true);
    }
    public static void text(GuiGraphics g, Component text, int x, int y, int color) {
        g.drawString(Minecraft.getInstance().font, text, x, y, color, false);
    }
    public static void small(GuiGraphics g, Component text, int x, int y, int color) {
        g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale(0.75F, 0.75F, 1);
        text(g, text, 0, 0, color); g.pose().popPose();
    }
    public static void elementGlyph(GuiGraphics g,Element element,int x,int y,float size){
        ElementIcons.draw(g,element,x,y,size,true);
    }
    public static void formulaIcon(GuiGraphics g,com.restor.magicka.Formula formula,int x,int y,int size){
        int count=formula.size();if(count==0)return;
        int columns=(int)Math.ceil(Math.sqrt(count)),rows=(count+columns-1)/columns;
        float cell=size/(float)columns;
        for(int i=0;i<count;i++){
            int row=i/columns,col=i%columns,inRow=Math.min(columns,count-row*columns);
            int px=x+Math.round((size-inRow*cell)/2+col*cell),py=y+Math.round((size-rows*cell)/2+row*cell);
            elementGlyph(g,formula.elements().get(i),px,py,cell);
        }
    }
    private MagicUi() {}
}
