package com.restor.magicka.client;

import com.restor.magicka.MagicNetwork;
import com.restor.magicka.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
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
        @Override public void renderWidget(GuiGraphics g,int mouseX,int mouseY,float partialTick){
            g.fill(getX(),getY()+6,getX()+width,getY()+10,0xFFA58B64);
            g.fill(getX()+width/2,getY()+3,getX()+width/2+1,getY()+13,0xFF6C533C);
            int knob=getX()+(int)(value*(width-8));
            g.fill(getX(),getY()+6,knob+4,getY()+10,0xFF7A4E8C);
            g.fill(knob,getY()+1,knob+8,getY()+15,isHoveredOrFocused()?0xFFE1C995:0xFFCDB68D);g.renderOutline(knob,getY()+1,8,14,0xFF4C3D50);
            if(isHovered())g.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font,getMessage(),getX()+width/2,getY()-8,MagicUi.GOLD);
        }
    }
    static final class BookButton extends Button {
        private final boolean selected;
        BookButton(int x,int y,int width,int height,Component label,OnPress press,boolean selected){super(x,y,width,height,label,press,DEFAULT_NARRATION);this.selected=selected;}
        @Override protected void renderWidget(GuiGraphics g,int mouseX,int mouseY,float partialTick){
            int bg=selected?0xFF4C3D50:isHoveredOrFocused() && active?0xFFE1C995:0xFFCDB68D;
            g.fill(getX(),getY(),getX()+width,getY()+height,bg);g.renderOutline(getX(),getY(),width,height,selected?0xFFAB8957:0xFFA58B64);
            var font=net.minecraft.client.Minecraft.getInstance().font;Component label=getMessage();
            if(font.width(label)>width-8 && font.width(label)*.75<=width-8){MagicUi.small(g,label,getX()+4,getY()+(height-6)/2,!active?0xFF9B896F:selected?0xFFFFEAC1:MagicUi.INK);return;}
            if(font.width(label)>width-8)label=Component.literal(font.plainSubstrByWidth(label.getString(),width-15)+"…");
            g.drawString(font,label,getX()+4,getY()+(height-8)/2,!active?0xFF9B896F:selected?0xFFFFEAC1:MagicUi.INK,false);
        }
    }
}
