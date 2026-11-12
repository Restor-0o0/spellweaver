package com.restor.magicka.client;

import net.minecraft.client.gui.GuiGraphics;

public final class HudView {
    @FunctionalInterface
    public interface Content {
        void draw(GuiGraphics graphics);
    }

    private final MagicHudLayout.Widget widget;

    public HudView(MagicHudLayout.Widget widget){this.widget=widget;}
    public MagicHudLayout.Widget widget(){return widget;}
    public MagicHudLayout.Bounds bounds(int width,int height,int vanillaHeight){return MagicHudLayout.editBounds(widget,width,height,vanillaHeight);}
    public void begin(GuiGraphics graphics,int baseX,int baseY){MagicHudLayout.begin(graphics,widget,baseX,baseY);graphics.setColor(1,1,1,(float)MagicHudLayout.opacity(widget));}
    public void beginContent(GuiGraphics graphics){MagicHudLayout.beginContent(graphics,widget);}
    public void endContent(GuiGraphics graphics){MagicHudLayout.endContent(graphics);}
    public void end(GuiGraphics graphics){graphics.setColor(1,1,1,1);MagicHudLayout.end(graphics);}
    public void render(GuiGraphics graphics,int baseX,int baseY,Content content){begin(graphics,baseX,baseY);beginContent(graphics);content.draw(graphics);endContent(graphics);end(graphics);}
}
