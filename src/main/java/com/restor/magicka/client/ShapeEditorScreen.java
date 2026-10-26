package com.restor.magicka.client;

import com.restor.magicka.MagicNetwork;
import com.restor.magicka.ShapePattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Paints the caster's 5x5x5 stencil as five floor plans, the bottom layer on the left. */
public final class ShapeEditorScreen extends Screen {
    private static final int SIZE = ShapePattern.SIZE;
    private final long[] bits = MagicNetwork.clientPattern.clone();
    private int cell, gap, gridX, gridTop, panelX, panelY, panelW, panelH;
    private boolean dirty, painting, paintValue;
    private final Screen parent;
    public ShapeEditorScreen(){this(null);}
    public ShapeEditorScreen(Screen parent){super(Component.translatable("shape.magicka.title"));this.parent=parent;}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override protected void init(){
        cell=Math.clamp((width-72)/(SIZE*SIZE+3),9,20);gap=Math.max(4,cell/2);
        int span=SIZE*(SIZE*cell)+(SIZE-1)*gap;
        panelW=span+24;panelH=SIZE*cell+114;
        panelX=(width-panelW)/2;panelY=Math.max(4,(height-panelH)/2);
        gridX=panelX+12;gridTop=panelY+64;
        int layerRow=gridTop+SIZE*cell+8, layerWidth=SIZE*cell, half=layerWidth/2;
        for(int i=0;i<SIZE;i++){
            int layer=i, lx=gridX+i*(layerWidth+gap);
            addRenderableWidget(new BookWidgets.BookButton(lx,layerRow,half-1,14,Component.literal("■"),b->layer(layer,true),false))
                    .setTooltip(Tooltip.create(Component.translatable("shape.magicka.layer_fill")));
            addRenderableWidget(new BookWidgets.BookButton(lx+half+1,layerRow,layerWidth-half-1,14,Component.literal("□"),b->layer(layer,false),false))
                    .setTooltip(Tooltip.create(Component.translatable("shape.magicka.layer_clear")));
        }
        int wide=(span-16)/3, bottom=layerRow+20;
        addRenderableWidget(new BookWidgets.BookButton(gridX,bottom,wide,18,Component.translatable("shape.magicka.clear"),b->all(false),false));
        addRenderableWidget(new BookWidgets.BookButton(gridX+wide+8,bottom,wide,18,Component.translatable("shape.magicka.cube"),b->all(true),false));
        addRenderableWidget(new BookWidgets.BookButton(gridX+2*wide+16,bottom,span-2*wide-16,18,Component.translatable("shape.magicka.hollow"),b->hollow(),false));
    }
    private void changed(){dirty=true;MagicNetwork.clientPattern=bits.clone();}
    private void layer(int y,boolean value){for(int x=0;x<SIZE;x++)for(int z=0;z<SIZE;z++)ShapePattern.set(bits,x,y,z,value);changed();}
    private void all(boolean value){for(int y=0;y<SIZE;y++)layer(y,value);}
    private void hollow(){
        for(int y=0;y<SIZE;y++)for(int z=0;z<SIZE;z++)for(int x=0;x<SIZE;x++)
            ShapePattern.set(bits,x,y,z,x==0 || x==SIZE-1 || z==0 || z==SIZE-1 || y==0 || y==SIZE-1);
        for(int y=0;y<2;y++)ShapePattern.set(bits,SIZE/2,y,0,false);
        changed();
    }
    private int layerAt(double mx,double my){
        if(my<gridTop || my>=gridTop+SIZE*cell)return -1;
        for(int i=0;i<SIZE;i++){int lx=gridX+i*(SIZE*cell+gap);if(mx>=lx && mx<lx+SIZE*cell)return i;}
        return -1;
    }
    private boolean paint(double mx,double my,boolean value){
        int layer=layerAt(mx,my);if(layer<0)return false;
        int lx=gridX+layer*(SIZE*cell+gap);
        int x=(int)((mx-lx)/cell), z=SIZE-1-(int)((my-gridTop)/cell);
        if(!ShapePattern.inside(x,layer,z))return false;
        if(ShapePattern.filled(bits,x,layer,z)!=value){ShapePattern.set(bits,x,layer,z,value);changed();}
        return true;
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        int layer=layerAt(mx,my);
        if(layer>=0 && (button==0 || button==1)){
            int lx=gridX+layer*(SIZE*cell+gap);
            int x=(int)((mx-lx)/cell), z=SIZE-1-(int)((my-gridTop)/cell);
            paintValue=button==0 && !ShapePattern.filled(bits,x,layer,z);
            painting=true;paint(mx,my,paintValue);return true;
        }
        return super.mouseClicked(mx,my,button);
    }
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){
        if(painting){paint(mx,my,paintValue);return true;}
        return super.mouseDragged(mx,my,button,dx,dy);
    }
    @Override public boolean mouseReleased(double mx,double my,int button){painting=false;return super.mouseReleased(mx,my,button);}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick){
        g.fill(0,0,width,height,0xB810101B);
        MagicUi.panel(g,panelX,panelY,panelW,panelH);
        MagicUi.text(g,title,panelX+12,panelY+10,MagicUi.GOLD);
        var count=Component.translatable("shape.magicka.count",ShapePattern.count(bits),ShapePattern.CELLS);
        MagicUi.small(g,count,panelX+panelW-14-(int)Math.ceil(font.width(count)*.75f),panelY+12,MagicUi.MUTED);
        var hint=font.split(Component.translatable("shape.magicka.hint"),(int)((panelW-24)/.75));
        for(int i=0;i<Math.min(2,hint.size());i++){
            g.pose().pushPose();g.pose().translate(panelX+12,panelY+26+i*8,0);g.pose().scale(.75f,.75f,1);
            g.drawString(font,hint.get(i),0,0,MagicUi.MUTED,false);g.pose().popPose();
        }
        MagicUi.small(g,Component.translatable("shape.magicka.spell"),panelX+12,panelY+46,0xFFA8D8A0);
        for(int layer=0;layer<SIZE;layer++){
            int lx=gridX+layer*(SIZE*cell+gap);
            MagicUi.small(g,Component.translatable("shape.magicka.layer",layer),lx,gridTop-10,layer==0?MagicUi.GOLD:MagicUi.MUTED);
            for(int z=SIZE-1;z>=0;z--)for(int x=0;x<SIZE;x++){
                int cx=lx+x*cell, cy=gridTop+(SIZE-1-z)*cell;
                boolean filled=ShapePattern.filled(bits,x,layer,z);
                boolean hover=mouseX>=cx && mouseX<cx+cell && mouseY>=cy && mouseY<cy+cell;
                g.fill(cx,cy,cx+cell,cy+cell,filled?0xFF8A6A46:0xFF171B23);
                if(!filled && layer>0 && ShapePattern.filled(bits,x,layer-1,z))g.fill(cx+cell/2-1,cy+cell/2-1,cx+cell/2+1,cy+cell/2+1,0xFF4C5A45);
                g.renderOutline(cx,cy,cell,cell,hover?MagicUi.GOLD:filled?0xFFC9A96E:0xFF343040);
            }
        }
        super.render(g,mouseX,mouseY,partialTick);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(MagicClient.shapeMatches(key,scan)){onClose();return true;}
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public void removed(){
        if(dirty && Minecraft.getInstance().getConnection()!=null)PacketDistributor.sendToServer(new MagicNetwork.SetShapeGrid(bits[0],bits[1]));
        super.removed();
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g,int mouseX,int mouseY,float partialTick){}
}
