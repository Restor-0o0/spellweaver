package com.restor.magicka.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class HudView {
    @FunctionalInterface
    public interface Content {
        void draw(GuiGraphicsExtractor graphics);
    }

    private static double alpha=1;
    public static int color(int color){return ((int)Math.round((color>>>24)*alpha)<<24)|(color&0xFFFFFF);}
    private final MagicHudLayout.Widget widget;

    public HudView(MagicHudLayout.Widget widget){this.widget=widget;}
    public MagicHudLayout.Widget widget(){return widget;}
    public MagicHudLayout.Bounds bounds(int width,int height,int vanillaHeight){return MagicHudLayout.editBounds(widget,width,height,vanillaHeight);}
    public void begin(GuiGraphicsExtractor graphics,int baseX,int baseY){MagicHudLayout.begin(graphics,widget,baseX,baseY);alpha=MagicHudLayout.opacity(widget);}
    public void beginContent(GuiGraphicsExtractor graphics){MagicHudLayout.beginContent(graphics,widget);}
    public void endContent(GuiGraphicsExtractor graphics){MagicHudLayout.endContent(graphics);}
    public void end(GuiGraphicsExtractor graphics){alpha=1;MagicHudLayout.end(graphics);}
    public void render(GuiGraphicsExtractor graphics,int baseX,int baseY,Content content){begin(graphics,baseX,baseY);beginContent(graphics);content.draw(graphics);endContent(graphics);end(graphics);}
}
