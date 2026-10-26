package com.restor.magicka.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.restor.magicka.MagicNetwork;
import com.restor.magicka.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class BookWidgets {
    private BookWidgets(){}
    static void sendStat(int index,int value){
        int[] copy=MagicNetwork.clientStats.clone();if(index>=0)copy[index]=MagicStats.Stat.values()[index].clamp(value);MagicNetwork.clientStats=copy;
        if(net.minecraft.client.Minecraft.getInstance().getConnection()!=null)MagicNetwork.sendToServer(new MagicNetwork.SetStat(index,value));
    }
    static final class StatSlider extends net.minecraft.client.gui.components.AbstractSliderButton {
        private final MagicStats.Stat stat;private final EditBox box;
        StatSlider(int x,int y,int width,int height,MagicStats.Stat stat,EditBox box){
            super(x,y,width,height,Component.empty(),0);this.stat=stat;this.box=box;show(MagicNetwork.clientStats[stat.ordinal()]);
        }
        void show(int value){this.value=value<=MagicStats.DEFAULT?.5*(value-stat.min)/(double)(MagicStats.DEFAULT-stat.min):.5+.5*Math.sqrt((value-MagicStats.DEFAULT)/(double)(stat.max-MagicStats.DEFAULT));updateMessage();}
        int current(){int raw=(int)Math.round(value<=.5?stat.min+value/.5*(MagicStats.DEFAULT-stat.min):MagicStats.DEFAULT+Math.pow((value-.5)/.5,2)*(stat.max-MagicStats.DEFAULT));int step=raw<200?1:raw<1000?5:25;return stat.clamp(Math.round(raw/(float)step)*step);}
        @Override protected void updateMessage(){setMessage(Component.literal(current()+"%"));}
        @Override protected void applyValue(){if(box!=null){box.setResponder(t->{});box.setValue(Integer.toString(current()));box.setResponder(t->{if(t.isEmpty())return;int v=stat.clamp(Integer.parseInt(t));show(v);sendStat(stat.ordinal(),v);});}}
        @Override public void onRelease(double mouseX,double mouseY){super.onRelease(mouseX,mouseY);sendStat(stat.ordinal(),current());}
        @Override public void renderButton(PoseStack pose,int mouseX,int mouseY,float partialTick){var g=new GuiGraphics(pose);
            g.fill(x,y+6,x+width,y+10,0xFFA58B64);
            g.fill(x+width/2,y+3,x+width/2+1,y+13,0xFF6C533C);
            int knob=x+(int)(value*(width-8));
            g.fill(x,y+6,knob+4,y+10,0xFF7A4E8C);
            g.fill(knob,y+1,knob+8,y+15,isHoveredOrFocused()?0xFFE1C995:0xFFCDB68D);g.renderOutline(knob,y+1,8,14,0xFF4C3D50);
            if(isHovered)g.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font,getMessage(),x+width/2,y-8,MagicUi.GOLD);
        }
    }
    static final class BookButton extends Button {
        private final boolean selected;private Component tip;
        BookButton setTooltip(Component tip){this.tip=tip;return this;}
        static void renderTips(net.minecraft.client.gui.screens.Screen screen,GuiGraphics g,int mouseX,int mouseY){
            for(var child:screen.children()){
                Component tip=child instanceof BookButton b && b.isMouseOver(mouseX,mouseY)?b.tip:child instanceof ElementButton e && e.isMouseOver(mouseX,mouseY)?e.tip:null;
                if(tip!=null){g.renderTooltip(net.minecraft.client.Minecraft.getInstance().font,tip,mouseX,mouseY);return;}
            }
        }
        BookButton(int x,int y,int width,int height,Component label,OnPress press,boolean selected){super(x,y,width,height,label,press);this.selected=selected;}
        @Override public void renderButton(PoseStack pose,int mouseX,int mouseY,float partialTick){var g=new GuiGraphics(pose);
            int bg=selected?0xFF4C3D50:isHoveredOrFocused() && active?0xFFE1C995:0xFFCDB68D;
            g.fill(x,y,x+width,y+height,bg);g.renderOutline(x,y,width,height,selected?0xFFAB8957:0xFFA58B64);
            var font=net.minecraft.client.Minecraft.getInstance().font;Component label=getMessage();
            if(font.width(label)>width-8 && font.width(label)*.75<=width-8){MagicUi.small(g,label,x+4,y+(height-6)/2,!active?0xFF9B896F:selected?0xFFFFEAC1:MagicUi.INK);return;}
            if(font.width(label)>width-8)label=Component.literal(font.plainSubstrByWidth(label.getString(),width-15)+"…");
            g.drawString(font,label,x+4,y+(height-8)/2,!active?0xFF9B896F:selected?0xFFFFEAC1:MagicUi.INK,false);
        }
    }
    static final class ElementButton extends Button {
        private final Element element;private final boolean on;private final Component tip;
        ElementButton(int x,int y,int width,int height,Element element,boolean on,OnPress press){
            super(x,y,width,height,Component.translatable(element.key()),press);
            this.element=element;this.on=on;this.tip=Component.translatable(element.key());
        }
        @Override public void renderButton(PoseStack pose,int mouseX,int mouseY,float partialTick){var g=new GuiGraphics(pose);
            g.fill(x,y,x+width,y+height,on?0xFF4C3D50:isHoveredOrFocused()?0xFFE1C995:0xFFCDB68D);
            g.renderOutline(x,y,width,height,on?0xFFFFEAC1:0xFFA58B64);
            int inset=on?3:4;g.fill(x+inset,y+inset,x+width-inset,y+height-inset,element.color);
        }
    }
}
