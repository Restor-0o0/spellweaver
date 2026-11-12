package com.restor.magicka.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public final class MagicHudLayout {
    public static final int GRID_CELL=8;
    public enum Widget { ELEMENTS, QUICK_SLOTS, CAST_STATUS, BOOK_HINT }
    public record Point(int x,int y) {}
    public record Bounds(int x,int y,int width,int height) {
        public boolean contains(double px,double py){return px>=x && py>=y && px<x+width && py<y+height;}
    }
    private static final class Placement {double x,y,scaleX=1,scaleY=1,frameScaleX=1,frameScaleY=1,opacity=1,padding=3,gap=4;int alignX=1,alignY=1,distribution,layer;boolean visible=true,textHints=true,titleVisible=true,background=true;}
    private static final class SlotPlacement {double x,y,scale=1;}
    private static final Placement[] PLACEMENTS=new Placement[Widget.values().length];
    static {for(int i=0;i<PLACEMENTS.length;i++){PLACEMENTS[i]=new Placement();PLACEMENTS[i].layer=i;}}
    private static final SlotPlacement[] QUICK_PLACEMENTS=new SlotPlacement[12];
    private static final SlotPlacement[] ELEMENT_PLACEMENTS=new SlotPlacement[8];
    static {for(int i=0;i<QUICK_PLACEMENTS.length;i++)QUICK_PLACEMENTS[i]=new SlotPlacement();}
    static {for(int i=0;i<ELEMENT_PLACEMENTS.length;i++)ELEMENT_PLACEMENTS[i]=new SlotPlacement();}
    private static final Path FILE=Minecraft.getInstance().gameDirectory.toPath().resolve("config/magicka_hud.json");
    private static final Path PROFILE=FILE.resolveSibling("magicka_hud_profile.json");
    private static final String CODE_ALPHABET="0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static int quickColumns=4,quickCount=4,elementColumns=2,elementRows=4;
    private static int quickRows=1;
    private static boolean gridSnap=true,quickCustom,quickCustomLayout,elementCustom,elementCustomLayout;
    static { load(); }

    public static Point base(Widget widget,int width,int height,int vanillaHeight){
        int panelX=width-117;
        int bottom=width>=410?height-6:height-Math.max(49,vanillaHeight)-7;
        int defaultHeight=quickRows*32-8;
        int presetY=Math.max(6,bottom-defaultHeight);
        return switch(widget){case ELEMENTS->new Point(6,Math.max(6,height-110));case QUICK_SLOTS->new Point(panelX,presetY);case CAST_STATUS->new Point(panelX,Math.max(6,bottom-68));case BOOK_HINT->new Point(6,Math.max(6,height-25));};
    }
    private static double quickMinX(){double min=0;if(quickCustom)for(int i=0;i<quickCount;i++)min=Math.min(min,QUICK_PLACEMENTS[i].x);return min;}
    private static double quickMinY(){double min=0;if(quickCustom)for(int i=0;i<quickCount;i++)min=Math.min(min,QUICK_PLACEMENTS[i].y);return min;}
    private static double quickMaxX(){double max=quickColumns*32-8;if(quickCustom){max=0;for(int i=0;i<quickCount;i++){var slot=QUICK_PLACEMENTS[i];max=Math.max(max,slot.x+24*slot.scale);}}return max;}
    private static double quickMaxY(){double max=quickRows*32-8;if(quickCustom){max=0;for(int i=0;i<quickCount;i++){var slot=QUICK_PLACEMENTS[i];max=Math.max(max,slot.y+24*slot.scale);}}return max;}
    public static int elementColumns(){return elementColumns;}
    public static int elementRows(){return elementRows;}
    public static void elementColumns(int value){elementColumns=net.minecraft.util.Mth.clamp(value,1,8);elementRows=(8+elementColumns-1)/elementColumns;save();}
    public static void elementRows(int value){elementRows=net.minecraft.util.Mth.clamp(value,1,8);elementColumns=(8+elementRows-1)/elementRows;save();}
    public static boolean elementCustom(){return elementCustom;}
    public static void elementCustom(boolean value){if(elementCustom==value)return;if(value && !elementCustomLayout){for(int i=0;i<ELEMENT_PLACEMENTS.length;i++){ELEMENT_PLACEMENTS[i].x=4+(i%elementColumns)*20;ELEMENT_PLACEMENTS[i].y=13+(i/elementColumns)*18;}elementCustomLayout=true;}elementCustom=value;save();}
    public static double elementX(int index){return elementCustom?ELEMENT_PLACEMENTS[index].x:position(Widget.ELEMENTS,index%elementColumns,elementColumns,17*ELEMENT_PLACEMENTS[index].scale,true);}
    public static double elementY(int index){return elementCustom?ELEMENT_PLACEMENTS[index].y:position(Widget.ELEMENTS,index/elementColumns,elementRows,17*ELEMENT_PLACEMENTS[index].scale,false);}
    public static double elementScale(int index){return ELEMENT_PLACEMENTS[index].scale;}
    public static void scaleElements(Iterable<Integer> indices,double delta){for(int index:indices)ELEMENT_PLACEMENTS[index].scale=net.minecraft.util.Mth.clamp(ELEMENT_PLACEMENTS[index].scale+delta,.5,3);save();}
    public static void scaleElementsTo(java.util.Map<Integer,Double> initialScales,double factor){for(var entry:initialScales.entrySet())ELEMENT_PLACEMENTS[entry.getKey()].scale=net.minecraft.util.Mth.clamp(entry.getValue()*factor,.5,3);save();}
    public static Bounds elementSlotBounds(int index,int width,int height,int vanillaHeight){Point base=base(Widget.ELEMENTS,width,height,vanillaHeight);Placement p=PLACEMENTS[Widget.ELEMENTS.ordinal()];double scale=elementScale(index);return new Bounds((int)Math.round(base.x+p.x+elementX(index)*p.scaleX*contentFit(Widget.ELEMENTS)+contentOffsetX(Widget.ELEMENTS)),(int)Math.round(base.y+p.y+elementY(index)*p.scaleY*contentFit(Widget.ELEMENTS)+contentOffsetY(Widget.ELEMENTS)),(int)Math.ceil(17*p.scaleX*scale*contentFit(Widget.ELEMENTS)),(int)Math.ceil(17*p.scaleY*scale*contentFit(Widget.ELEMENTS)));}
    public static Bounds castSlotBounds(int index,int width,int height,int vanillaHeight){return castSlotBounds(index,castCount,width,height,vanillaHeight);}
    public static Bounds castSlotBounds(int index,int count,int width,int height,int vanillaHeight){Point base=base(Widget.CAST_STATUS,width,height,vanillaHeight);Placement p=PLACEMENTS[Widget.CAST_STATUS.ordinal()];int runeY=castSlotY();return new Bounds((int)Math.round(base.x+p.x+castSlotX(index,count)*p.scaleX*contentFit(Widget.CAST_STATUS)+contentOffsetX(Widget.CAST_STATUS)),(int)Math.round(base.y+p.y+runeY*p.scaleY*contentFit(Widget.CAST_STATUS)+contentOffsetY(Widget.CAST_STATUS)),(int)Math.ceil(17*p.scaleX*contentFit(Widget.CAST_STATUS)),(int)Math.ceil(17*p.scaleY*contentFit(Widget.CAST_STATUS)));}
    private static int castCount=5;
    public static void castCount(int count){castCount=Math.max(1,count);}
    public static int castCount(){return castCount;}
    public static int castSlotX(int index,int count){return (int)Math.round(position(Widget.CAST_STATUS,index,Math.max(1,count),17,true));}
    public static int castSlotY(){return (int)Math.round(position(Widget.CAST_STATUS,0,1,17,false));}
    public static int distribution(Widget widget){return PLACEMENTS[widget.ordinal()].distribution;}
    public static void cycleDistribution(Widget widget){Placement p=PLACEMENTS[widget.ordinal()];if(widget==Widget.ELEMENTS)elementCustom=false;if(widget==Widget.QUICK_SLOTS)quickCustom=false;p.distribution=(p.distribution+1)%3;save();}
    public static void moveElementTo(int index,int x,int y,int width,int height,int vanillaHeight){Point base=base(Widget.ELEMENTS,width,height,vanillaHeight);Placement p=PLACEMENTS[Widget.ELEMENTS.ordinal()];ELEMENT_PLACEMENTS[index].x=Math.max(4,(x-base.x-p.x)/p.scaleX);ELEMENT_PLACEMENTS[index].y=Math.max(13,(y-base.y-p.y)/p.scaleY);save();}
    private static double elementMaxX(){double max=Math.max(45,4+elementColumns*20);for(int i=0;i<8;i++)max=Math.max(max,elementX(i)+17*elementScale(i));return max;}
    private static double elementMaxY(){double max=(titleVisible(Widget.ELEMENTS)?13:3)+elementRows*18-1;for(int i=0;i<8;i++)max=Math.max(max,elementY(i)+17*elementScale(i));return max;}
    private static double elementFrameMaxX(){if(!elementCustom)return Math.max(45,4+elementColumns*20);double max=45;for(int i=0;i<8;i++)max=Math.max(max,ELEMENT_PLACEMENTS[i].x+17);return max;}
    private static double elementFrameMaxY(){double top=titleVisible(Widget.ELEMENTS)?13:3;if(!elementCustom)return top+elementRows*18-1;double max=top+elementRows*18-1;for(int i=0;i<8;i++)max=Math.max(max,ELEMENT_PLACEMENTS[i].y+17);return max;}
    public static int elementBookY(){return (int)Math.ceil(elementMaxY()+1);}
    public static int elementPanelWidth(){return baseWidth(Widget.ELEMENTS);}
    public static int elementPanelHeight(){return baseHeight(Widget.ELEMENTS);}
    public static int castPanelHeight(){return baseHeight(Widget.CAST_STATUS);}
    public static int frameWidth(Widget widget,int contentWidth){Placement p=PLACEMENTS[widget.ordinal()];return Math.max(1,(int)Math.round(contentWidth*p.frameScaleX/p.scaleX));}
    public static int frameHeight(Widget widget,int contentHeight){Placement p=PLACEMENTS[widget.ordinal()];return Math.max(1,(int)Math.round(contentHeight*p.frameScaleY/p.scaleY));}
    private static double contentOffsetX(Widget widget){return alignedOffset(widget,true);}
    private static double contentOffsetY(Widget widget){return alignedOffset(widget,false);}
    private static int baseWidth(Widget widget){return switch(widget){case ELEMENTS->(int)Math.ceil(elementFrameMaxX());case QUICK_SLOTS->Math.max(1,(int)Math.ceil(quickMaxX()-quickMinX()));case CAST_STATUS->111;case BOOK_HINT->21;};}
    private static int baseHeight(Widget widget){return switch(widget){case ELEMENTS->Math.max(1,(int)Math.ceil(elementFrameMaxY()+3));case QUICK_SLOTS->Math.max(1,(int)Math.ceil(quickMaxY()-quickMinY()));case CAST_STATUS->titleVisible(Widget.CAST_STATUS)?36:23;case BOOK_HINT->21;};}
    public static Bounds bounds(Widget widget,int width,int height,int vanillaHeight){
        Point base=base(widget,width,height,vanillaHeight);Placement p=PLACEMENTS[widget.ordinal()];
        double minX=widget==Widget.QUICK_SLOTS?Math.min(0,quickMinX()*p.scaleX):0,minY=widget==Widget.QUICK_SLOTS?Math.min(0,quickMinY()*p.scaleY):0;
        double boundsWidth=Math.max(baseWidth(widget)*p.scaleX,frameWidth(widget,baseWidth(widget))*p.scaleX),boundsHeight=Math.max(baseHeight(widget)*p.scaleY,frameHeight(widget,baseHeight(widget))*p.scaleY);
        return new Bounds((int)Math.round(base.x+p.x+minX),(int)Math.round(base.y+p.y+minY),(int)Math.ceil(boundsWidth-minX),(int)Math.ceil(boundsHeight-minY));
    }
    public static Bounds editBounds(Widget widget,int width,int height,int vanillaHeight){Point base=base(widget,width,height,vanillaHeight);Placement p=PLACEMENTS[widget.ordinal()];double minX=widget==Widget.QUICK_SLOTS?Math.min(0,quickMinX()*p.scaleX):0,minY=widget==Widget.QUICK_SLOTS?Math.min(0,quickMinY()*p.scaleY):0;int frameW=(int)Math.ceil(frameWidth(widget,baseWidth(widget))*p.scaleX),frameH=(int)Math.ceil(frameHeight(widget,baseHeight(widget))*p.scaleY);return new Bounds((int)Math.round(base.x+p.x+minX),(int)Math.round(base.y+p.y+minY),(int)Math.max(1,frameW-minX),(int)Math.max(1,frameH-minY));}
    public static void clampToScreen(int width,int height,int vanillaHeight){
        for(Widget widget:Widget.values()){
            Point base=base(widget,width,height,vanillaHeight);Placement p=PLACEMENTS[widget.ordinal()];
            int w=(int)Math.ceil(Math.max(baseWidth(widget)*p.scaleX,frameWidth(widget,baseWidth(widget))*p.scaleX)),h=(int)Math.ceil(Math.max(baseHeight(widget)*p.scaleY,frameHeight(widget,baseHeight(widget))*p.scaleY));
            double minX=widget==Widget.QUICK_SLOTS?Math.min(0,quickMinX()*p.scaleX):0,minY=widget==Widget.QUICK_SLOTS?Math.min(0,quickMinY()*p.scaleY):0;
            p.x=net.minecraft.util.Mth.clamp(base.x+p.x+minX,0,Math.max(0,width-w))-base.x-minX;p.y=net.minecraft.util.Mth.clamp(base.y+p.y+minY,0,Math.max(0,height-h))-base.y-minY;
        }
    }
    static double scale(Widget widget){return scaleX(widget);}
    static double scaleX(Widget widget){return PLACEMENTS[widget.ordinal()].scaleX;}
    static double scaleY(Widget widget){return PLACEMENTS[widget.ordinal()].scaleY;}
    static double frameScaleX(Widget widget){return PLACEMENTS[widget.ordinal()].frameScaleX;}
    static double frameScaleY(Widget widget){return PLACEMENTS[widget.ordinal()].frameScaleY;}
    public static int layer(Widget widget){return PLACEMENTS[widget.ordinal()].layer;}
    public static boolean visible(Widget widget){return PLACEMENTS[widget.ordinal()].visible;}
    public static void toggleVisible(Widget widget){PLACEMENTS[widget.ordinal()].visible=!PLACEMENTS[widget.ordinal()].visible;save();}
    public static boolean titleVisible(Widget widget){return PLACEMENTS[widget.ordinal()].titleVisible;}
    public static void toggleTitle(Widget widget){PLACEMENTS[widget.ordinal()].titleVisible=!PLACEMENTS[widget.ordinal()].titleVisible;save();}
    public static boolean textHints(Widget widget){return PLACEMENTS[widget.ordinal()].textHints;}
    public static void toggleTextHints(Widget widget){PLACEMENTS[widget.ordinal()].textHints=!PLACEMENTS[widget.ordinal()].textHints;save();}
    public static boolean background(Widget widget){return PLACEMENTS[widget.ordinal()].background;}
    public static void toggleBackground(Widget widget){PLACEMENTS[widget.ordinal()].background=!PLACEMENTS[widget.ordinal()].background;save();}
    public static double opacity(Widget widget){return PLACEMENTS[widget.ordinal()].opacity;}
    public static void opacity(Widget widget,double value){PLACEMENTS[widget.ordinal()].opacity=net.minecraft.util.Mth.clamp(value,.15,1);save();}
    private static Widget editorSelection;
    public static void editorSelection(Widget widget){editorSelection=widget;}
    public static int renderLayer(Widget widget){return Minecraft.getInstance().screen instanceof HudLayoutScreen && editorSelection==widget?5:layer(widget);}
    public static void moveLayer(Widget widget,boolean forward){Placement current=PLACEMENTS[widget.ordinal()];int other=-1;for(int i=0;i<PLACEMENTS.length;i++){if(i==widget.ordinal())continue;int layer=PLACEMENTS[i].layer;if(forward?(layer>current.layer):(layer<current.layer)){if(other<0 || (forward?layer<PLACEMENTS[other].layer:layer>PLACEMENTS[other].layer))other=i;}}if(other>=0){int value=current.layer;current.layer=PLACEMENTS[other].layer;PLACEMENTS[other].layer=value;save();}}
    public static int quickColumns(){return quickColumns;}
    public static int quickRows(){return quickRows;}
    public static int quickCount(){return quickCount;}
    public static void quickCount(int value){quickCount=net.minecraft.util.Mth.clamp(value,4,12);quickColumns=Math.min(quickColumns,quickCount);quickRows=Math.max(quickRows,(quickCount+quickColumns-1)/quickColumns);save();}
    public static void quickColumns(int value){quickColumns=net.minecraft.util.Mth.clamp(value,1,quickCount);quickRows=(quickCount+quickColumns-1)/quickColumns;save();}
    public static void quickRows(int value){quickRows=net.minecraft.util.Mth.clamp(value,1,quickCount);quickColumns=(quickCount+quickRows-1)/quickRows;save();}
    public static boolean quickCustom(){return quickCustom;}
    public static void quickCustom(boolean value){
        if(quickCustom==value)return;
        if(value && !quickCustomLayout){for(int i=0;i<QUICK_PLACEMENTS.length;i++){QUICK_PLACEMENTS[i].x=(i%quickColumns)*32;QUICK_PLACEMENTS[i].y=(i/quickColumns)*32;QUICK_PLACEMENTS[i].scale=1;}quickCustomLayout=true;}
        quickCustom=value;
        save();
    }
    public static boolean gridSnap(){return gridSnap;}
    public static void toggleGridSnap(){gridSnap=!gridSnap;save();}
    public static double quickSlotX(int index){return quickCustom?QUICK_PLACEMENTS[index].x:position(Widget.QUICK_SLOTS,index%quickColumns,quickColumns,24*QUICK_PLACEMENTS[index].scale,true);}
    public static double quickSlotY(int index){return quickCustom?QUICK_PLACEMENTS[index].y:position(Widget.QUICK_SLOTS,index/quickColumns,quickRows,24*QUICK_PLACEMENTS[index].scale,false);}
    public static double quickSlotScale(int index){return QUICK_PLACEMENTS[index].scale;}
    public static Bounds quickSlotBounds(int index,int width,int height,int vanillaHeight){
        Point base=base(Widget.QUICK_SLOTS,width,height,vanillaHeight);Placement group=PLACEMENTS[Widget.QUICK_SLOTS.ordinal()];
        double sx=base.x+group.x+quickSlotX(index)*group.scaleX*contentFit(Widget.QUICK_SLOTS)+contentOffsetX(Widget.QUICK_SLOTS),sy=base.y+group.y+quickSlotY(index)*group.scaleY*contentFit(Widget.QUICK_SLOTS)+contentOffsetY(Widget.QUICK_SLOTS);
        return new Bounds((int)Math.round(sx),(int)Math.round(sy),(int)Math.ceil(24*group.scaleX*quickSlotScale(index)*contentFit(Widget.QUICK_SLOTS)),(int)Math.ceil(24*group.scaleY*quickSlotScale(index)*contentFit(Widget.QUICK_SLOTS)));
    }
    public static void moveQuickSlotTo(int index,int x,int y,int width,int height,int vanillaHeight){
        Point base=base(Widget.QUICK_SLOTS,width,height,vanillaHeight);Placement group=PLACEMENTS[Widget.QUICK_SLOTS.ordinal()];SlotPlacement slot=QUICK_PLACEMENTS[index];
        slot.x=net.minecraft.util.Mth.clamp((x-base.x-group.x)/group.scaleX,-(base.x+group.x)/group.scaleX,(width-24*group.scaleX*slot.scale-base.x-group.x)/group.scaleX);
        slot.y=net.minecraft.util.Mth.clamp((y-base.y-group.y)/group.scaleY,-(base.y+group.y)/group.scaleY,(height-24*group.scaleY*slot.scale-base.y-group.y)/group.scaleY);save();
    }
    public static void scaleQuickSlot(int index,double value){QUICK_PLACEMENTS[index].scale=net.minecraft.util.Mth.clamp(value,.6,3);save();}
    public static void scaleQuickSlot(int index,double value,int width,int height,int vanillaHeight){
        QUICK_PLACEMENTS[index].scale=net.minecraft.util.Mth.clamp(value,.6,3);Bounds after=quickSlotBounds(index,width,height,vanillaHeight);
        moveQuickSlotTo(index,net.minecraft.util.Mth.clamp(after.x,0,Math.max(0,width-after.width)),net.minecraft.util.Mth.clamp(after.y,0,Math.max(0,height-after.height)),width,height,vanillaHeight);
    }
    public static void resetQuickSlot(int index){SlotPlacement p=QUICK_PLACEMENTS[index];p.x=(index%quickColumns)*32;p.y=(index/quickColumns)*32;p.scale=1;save();}
    public static void begin(GuiGraphics graphics,Widget widget,int baseX,int baseY){
        Placement p=PLACEMENTS[widget.ordinal()];graphics.pose().pushPose();graphics.pose().translate(baseX+p.x,baseY+p.y,renderLayer(widget)*100);graphics.pose().scale((float)p.scaleX,(float)p.scaleY,1);graphics.pose().translate(-baseX,-baseY,0);
    }
    public static void beginContent(GuiGraphics graphics,Widget widget){
        Placement p=PLACEMENTS[widget.ordinal()];var mc=Minecraft.getInstance();
        Point base=base(widget,graphics.guiWidth(),graphics.guiHeight(),Math.max(((net.minecraftforge.client.gui.overlay.ForgeGui)mc.gui).leftHeight,((net.minecraftforge.client.gui.overlay.ForgeGui)mc.gui).rightHeight));
        float fit=(float)contentFit(widget);
        graphics.pose().pushPose();graphics.pose().translate(contentOffsetX(widget)/p.scaleX,contentOffsetY(widget)/p.scaleY,0);
        graphics.pose().translate(base.x,base.y,0);graphics.pose().scale(fit,fit,1);graphics.pose().translate(-base.x,-base.y,0);
    }
    public static void endContent(GuiGraphics graphics){graphics.pose().popPose();}
    public static void end(GuiGraphics graphics){graphics.pose().popPose();}
    public static int alignX(Widget widget){return PLACEMENTS[widget.ordinal()].alignX;}
    public static int alignY(Widget widget){return PLACEMENTS[widget.ordinal()].alignY;}
    public static void alignContent(Widget widget,int x,int y){Placement p=PLACEMENTS[widget.ordinal()];p.alignX=net.minecraft.util.Mth.clamp(x,0,2);p.alignY=net.minecraft.util.Mth.clamp(y,0,2);save();}
    public static void moveTo(Widget widget,int x,int y,int width,int height,int vanillaHeight){
        Point base=base(widget,width,height,vanillaHeight);Placement p=PLACEMENTS[widget.ordinal()];
        int w=(int)Math.ceil(Math.max(baseWidth(widget)*p.scaleX,baseWidth(widget)*p.frameScaleX)),h=(int)Math.ceil(Math.max(baseHeight(widget)*p.scaleY,baseHeight(widget)*p.frameScaleY));
        double minX=widget==Widget.QUICK_SLOTS?Math.min(0,quickMinX()*p.scaleX):0,minY=widget==Widget.QUICK_SLOTS?Math.min(0,quickMinY()*p.scaleY):0;
        p.x=net.minecraft.util.Mth.clamp(x,0,Math.max(0,width-w))-base.x-minX;p.y=net.minecraft.util.Mth.clamp(y,0,Math.max(0,height-h))-base.y-minY;save();
    }
    public static void scale(Widget widget,double value,int width,int height,int vanillaHeight){
        scale(widget,value,value,width,height,vanillaHeight);
    }
    public static void scale(Widget widget,double sx,double sy,int width,int height,int vanillaHeight){Point base=base(widget,width,height,vanillaHeight);Placement p=PLACEMENTS[widget.ordinal()];double oldLeft=base.x+p.x+(widget==Widget.QUICK_SLOTS?quickMinX()*p.scaleX:0),oldTop=base.y+p.y+(widget==Widget.QUICK_SLOTS?quickMinY()*p.scaleY:0);p.scaleX=net.minecraft.util.Mth.clamp(sx,.2,2.25);p.scaleY=net.minecraft.util.Mth.clamp(sy,.2,2.25);p.frameScaleX=p.scaleX;p.frameScaleY=p.scaleY;int w=(int)Math.ceil(baseWidth(widget)*p.scaleX),h=(int)Math.ceil(baseHeight(widget)*p.scaleY);double minX=widget==Widget.QUICK_SLOTS?quickMinX()*p.scaleX:0,minY=widget==Widget.QUICK_SLOTS?quickMinY()*p.scaleY:0;p.x=net.minecraft.util.Mth.clamp(oldLeft,0,Math.max(0,width-w))-base.x-minX;p.y=net.minecraft.util.Mth.clamp(oldTop,0,Math.max(0,height-h))-base.y-minY;save();}
    public static void resizeFrame(Widget widget,double sx,double sy){Placement p=PLACEMENTS[widget.ordinal()];p.frameScaleX=net.minecraft.util.Mth.clamp(sx,.2,2.25);p.frameScaleY=net.minecraft.util.Mth.clamp(sy,.2,2.25);save();}
    public static void resizeContent(Widget widget,double sx,double sy){Placement p=PLACEMENTS[widget.ordinal()];p.scaleX=net.minecraft.util.Mth.clamp(sx,.2,2.25);p.scaleY=net.minecraft.util.Mth.clamp(sy,.2,2.25);save();}
    public static void reset(Widget widget){
        PLACEMENTS[widget.ordinal()]=new Placement();PLACEMENTS[widget.ordinal()].layer=widget.ordinal();
        if(widget==Widget.QUICK_SLOTS){quickCount=4;quickColumns=4;quickRows=1;quickCustom=false;quickCustomLayout=false;for(int i=0;i<QUICK_PLACEMENTS.length;i++){QUICK_PLACEMENTS[i].x=i*32;QUICK_PLACEMENTS[i].y=0;QUICK_PLACEMENTS[i].scale=1;}}
        if(widget==Widget.ELEMENTS){elementColumns=2;elementRows=4;elementCustom=false;elementCustomLayout=false;for(int i=0;i<ELEMENT_PLACEMENTS.length;i++){ELEMENT_PLACEMENTS[i].x=4+(i%2)*20;ELEMENT_PLACEMENTS[i].y=13+(i/2)*18;ELEMENT_PLACEMENTS[i].scale=1;}}
        save();
    }
    public static void resetAll(){for(int i=0;i<PLACEMENTS.length;i++){PLACEMENTS[i]=new Placement();PLACEMENTS[i].layer=i;}quickCount=4;quickColumns=4;quickRows=1;elementColumns=2;elementRows=4;gridSnap=true;quickCustom=false;quickCustomLayout=false;elementCustom=false;elementCustomLayout=false;for(int i=0;i<QUICK_PLACEMENTS.length;i++){QUICK_PLACEMENTS[i].x=i*32;QUICK_PLACEMENTS[i].y=0;QUICK_PLACEMENTS[i].scale=1;}for(int i=0;i<ELEMENT_PLACEMENTS.length;i++){ELEMENT_PLACEMENTS[i].x=4+(i%2)*20;ELEMENT_PLACEMENTS[i].y=13+(i/2)*18;ELEMENT_PLACEMENTS[i].scale=1;}save();}
    private static JsonObject toJson(){
        JsonObject root=new JsonObject();
        for(Widget w:Widget.values()){Placement p=PLACEMENTS[w.ordinal()];JsonObject value=new JsonObject();value.addProperty("x",p.x);value.addProperty("y",p.y);value.addProperty("scale_x",p.scaleX);value.addProperty("scale_y",p.scaleY);value.addProperty("frame_scale_x",p.frameScaleX);value.addProperty("frame_scale_y",p.frameScaleY);value.addProperty("padding",p.padding);value.addProperty("gap",p.gap);value.addProperty("opacity",p.opacity);value.addProperty("align_x",p.alignX);value.addProperty("align_y",p.alignY);value.addProperty("distribution",p.distribution);value.addProperty("layer",p.layer);value.addProperty("visible",p.visible);value.addProperty("text_hints",p.textHints);value.addProperty("title_visible",p.titleVisible);value.addProperty("background",p.background);root.add(w.name(),value);}
        root.addProperty("quick_count",quickCount);root.addProperty("quick_columns",quickColumns);root.addProperty("quick_rows",quickRows);root.addProperty("grid_snap",gridSnap);root.addProperty("quick_custom",quickCustom);root.addProperty("quick_custom_layout",quickCustomLayout);root.addProperty("element_columns",elementColumns);root.addProperty("element_rows",elementRows);root.addProperty("element_custom",elementCustom);root.addProperty("element_custom_layout",elementCustomLayout);
        for(int i=0;i<QUICK_PLACEMENTS.length;i++){SlotPlacement p=QUICK_PLACEMENTS[i];JsonObject value=new JsonObject();value.addProperty("x",p.x);value.addProperty("y",p.y);value.addProperty("scale",p.scale);root.add("quick_slot_"+i,value);}
        for(int i=0;i<ELEMENT_PLACEMENTS.length;i++){SlotPlacement p=ELEMENT_PLACEMENTS[i];JsonObject value=new JsonObject();value.addProperty("x",p.x);value.addProperty("y",p.y);value.addProperty("scale",p.scale);root.add("element_slot_"+i,value);}
        return root;
    }
    private static void apply(JsonObject root){
        for(Widget w:Widget.values()){Placement p=PLACEMENTS[w.ordinal()];p.x=0;p.y=0;p.scaleX=1;p.scaleY=1;p.frameScaleX=1;p.frameScaleY=1;p.opacity=1;p.alignX=1;p.alignY=1;p.distribution=0;p.layer=w.ordinal();p.visible=true;p.textHints=true;p.titleVisible=true;p.background=true;if(root.has(w.name())){JsonObject value=root.getAsJsonObject(w.name());p.x=value.has("x")?value.get("x").getAsDouble():0;p.y=value.has("y")?value.get("y").getAsDouble():0;double old=value.has("scale")?value.get("scale").getAsDouble():1;p.scaleX=net.minecraft.util.Mth.clamp(value.has("scale_x")?value.get("scale_x").getAsDouble():old,.2,2.25);p.scaleY=net.minecraft.util.Mth.clamp(value.has("scale_y")?value.get("scale_y").getAsDouble():old,.2,2.25);p.frameScaleX=net.minecraft.util.Mth.clamp(value.has("frame_scale_x")?value.get("frame_scale_x").getAsDouble():p.scaleX,.2,2.25);p.frameScaleY=net.minecraft.util.Mth.clamp(value.has("frame_scale_y")?value.get("frame_scale_y").getAsDouble():p.scaleY,.2,2.25);p.padding=value.has("padding")?net.minecraft.util.Mth.clamp(value.get("padding").getAsDouble(),0,24):3;p.gap=value.has("gap")?net.minecraft.util.Mth.clamp(value.get("gap").getAsDouble(),0,24):4;p.opacity=net.minecraft.util.Mth.clamp(value.has("opacity")?value.get("opacity").getAsDouble():1,.15,1);p.alignX=net.minecraft.util.Mth.clamp(value.has("align_x")?value.get("align_x").getAsInt():1,0,2);p.alignY=net.minecraft.util.Mth.clamp(value.has("align_y")?value.get("align_y").getAsInt():1,0,2);p.distribution=net.minecraft.util.Mth.clamp(value.has("distribution")?value.get("distribution").getAsInt():0,0,2);p.layer=net.minecraft.util.Mth.clamp(value.has("layer")?value.get("layer").getAsInt():w.ordinal(),0,3);p.visible=!value.has("visible")||value.get("visible").getAsBoolean();p.textHints=!value.has("text_hints")||value.get("text_hints").getAsBoolean();p.titleVisible=value.has("title_visible")?value.get("title_visible").getAsBoolean():p.textHints;p.background=!value.has("background")||value.get("background").getAsBoolean();}}
        quickCount=net.minecraft.util.Mth.clamp(root.has("quick_count")?root.get("quick_count").getAsInt():4,4,12);quickColumns=net.minecraft.util.Mth.clamp(root.has("quick_columns")?root.get("quick_columns").getAsInt():4,1,quickCount);quickRows=net.minecraft.util.Mth.clamp(root.has("quick_rows")?root.get("quick_rows").getAsInt():(quickCount+quickColumns-1)/quickColumns,1,12);
        if(quickColumns*quickRows<quickCount){quickRows=(quickCount+quickColumns-1)/quickColumns;}gridSnap=!root.has("grid_snap") || root.get("grid_snap").getAsBoolean();quickCustom=root.has("quick_custom") && root.get("quick_custom").getAsBoolean();quickCustomLayout=root.has("quick_custom_layout")?root.get("quick_custom_layout").getAsBoolean():quickCustom;
        for(int i=0;i<QUICK_PLACEMENTS.length;i++){SlotPlacement slot=QUICK_PLACEMENTS[i];slot.x=(i%quickColumns)*32;slot.y=(i/quickColumns)*32;slot.scale=1;if(root.has("quick_slot_"+i)){JsonObject value=root.getAsJsonObject("quick_slot_"+i);slot.x=value.has("x")?value.get("x").getAsDouble():slot.x;slot.y=value.has("y")?value.get("y").getAsDouble():slot.y;slot.scale=net.minecraft.util.Mth.clamp(value.has("scale")?value.get("scale").getAsDouble():1,.6,3);}}
        elementColumns=net.minecraft.util.Mth.clamp(root.has("element_columns")?root.get("element_columns").getAsInt():2,1,8);elementRows=net.minecraft.util.Mth.clamp(root.has("element_rows")?root.get("element_rows").getAsInt():(8+elementColumns-1)/elementColumns,1,8);if(elementColumns*elementRows<8)elementRows=(8+elementColumns-1)/elementColumns;elementCustom=root.has("element_custom")&&root.get("element_custom").getAsBoolean();elementCustomLayout=root.has("element_custom_layout")?root.get("element_custom_layout").getAsBoolean():elementCustom;
        for(int i=0;i<ELEMENT_PLACEMENTS.length;i++){SlotPlacement slot=ELEMENT_PLACEMENTS[i];slot.x=4+(i%elementColumns)*20;slot.y=13+(i/elementColumns)*18;slot.scale=1;if(root.has("element_slot_"+i)){JsonObject value=root.getAsJsonObject("element_slot_"+i);slot.x=Math.max(4,value.has("x")?value.get("x").getAsDouble():slot.x);slot.y=Math.max(13,value.has("y")?value.get("y").getAsDouble():slot.y);slot.scale=net.minecraft.util.Mth.clamp(value.has("scale")?value.get("scale").getAsDouble():1,.5,3);}}
    }
    static void load(){try{if(Files.isRegularFile(FILE))apply(JsonParser.parseString(Files.readString(FILE)).getAsJsonObject());}catch(Exception ignored){}}
    private static void save(){try{Files.createDirectories(FILE.getParent());Files.writeString(FILE,toJson().toString());}catch(Exception ignored){}}
    public static boolean exportProfile(){try{Files.createDirectories(PROFILE.getParent());Files.writeString(PROFILE,toJson().toString());return true;}catch(Exception ignored){return false;}}
    public static boolean importProfile(){try{if(!Files.isRegularFile(PROFILE))return false;apply(JsonParser.parseString(Files.readString(PROFILE)).getAsJsonObject());save();return true;}catch(Exception ignored){return false;}}
    public static Path profilePath(){return PROFILE;}
    public static String exportCode(){
        try{
            byte[] json=toJson().toString().getBytes(StandardCharsets.UTF_8);CRC32 crc=new CRC32();crc.update(json);
            ByteArrayOutputStream raw=new ByteArrayOutputStream();raw.write(json);long checksum=crc.getValue();for(int shift=24;shift>=0;shift-=8)raw.write((int)(checksum>>>shift)&255);
            ByteArrayOutputStream compressed=new ByteArrayOutputStream();try(DeflaterOutputStream deflater=new DeflaterOutputStream(compressed)){deflater.write(raw.toByteArray());}
            return "MH1"+base62(compressed.toByteArray());
        }catch(Exception ignored){return null;}
    }
    public static boolean importCode(String input){
        try{
            if(input==null)return false;String code=input.replaceAll("\\s+","");if(code.length()<8 || code.length()>4096 || !code.startsWith("MH1"))return false;
            byte[] compressed=fromBase62(code.substring(3));ByteArrayOutputStream raw=new ByteArrayOutputStream();
            try(InflaterInputStream inflater=new InflaterInputStream(new ByteArrayInputStream(compressed))){byte[] buffer=new byte[512];int n;while((n=inflater.read(buffer))>=0){if(raw.size()+n>65536)return false;raw.write(buffer,0,n);}}
            byte[] payload=raw.toByteArray();if(payload.length<5)return false;int jsonLength=payload.length-4;
            long expected=((payload[jsonLength]&255L)<<24)|((payload[jsonLength+1]&255L)<<16)|((payload[jsonLength+2]&255L)<<8)|(payload[jsonLength+3]&255L);
            CRC32 crc=new CRC32();crc.update(payload,0,jsonLength);if(crc.getValue()!=expected)return false;
            JsonObject root=JsonParser.parseString(new String(payload,0,jsonLength,StandardCharsets.UTF_8)).getAsJsonObject();apply(root);save();return true;
        }catch(Exception ignored){return false;}
    }
    private static String base62(byte[] bytes){
        int zeros=0;while(zeros<bytes.length && bytes[zeros]==0)zeros++;
        BigInteger value=new BigInteger(1,bytes);BigInteger radix=BigInteger.valueOf(62);StringBuilder digits=new StringBuilder();
        while(value.signum()>0){BigInteger[] qr=value.divideAndRemainder(radix);digits.append(CODE_ALPHABET.charAt(qr[1].intValue()));value=qr[0];}
        digits.reverse();for(int i=0;i<zeros;i++)digits.insert(0,CODE_ALPHABET.charAt(0));return digits.toString();
    }
    private static byte[] fromBase62(String value){
        if(value.isEmpty())throw new IllegalArgumentException();int zeros=0;while(zeros<value.length() && value.charAt(zeros)==CODE_ALPHABET.charAt(0))zeros++;
        BigInteger number=BigInteger.ZERO,radix=BigInteger.valueOf(62);for(int i=0;i<value.length();i++){int digit=CODE_ALPHABET.indexOf(value.charAt(i));if(digit<0)throw new IllegalArgumentException();number=number.multiply(radix).add(BigInteger.valueOf(digit));}
        byte[] magnitude=number.signum()==0?new byte[0]:number.toByteArray();if(magnitude.length>0 && magnitude[0]==0)magnitude=Arrays.copyOfRange(magnitude,1,magnitude.length);
        byte[] decoded=new byte[zeros+magnitude.length];System.arraycopy(magnitude,0,decoded,zeros,magnitude.length);return decoded;
    }
    private static double position(Widget widget,int index,int count,double size,boolean horizontal){
        Placement p=PLACEMENTS[widget.ordinal()];
        double scale=horizontal?p.scaleX:p.scaleY;
        double start=p.padding/scale+(!horizontal&&(widget==Widget.ELEMENTS||widget==Widget.CAST_STATUS)&&p.titleVisible?13:0);
        double end=(horizontal?frameWidth(widget,baseWidth(widget)):frameHeight(widget,baseHeight(widget)))/contentFit(widget)-p.padding/scale;
        double available=Math.max(0,end-start),position=start;
        if(widget==Widget.QUICK_SLOTS||widget==Widget.ELEMENTS){
            double[] sizes=new double[count];
            int columns=widget==Widget.ELEMENTS?elementColumns:quickColumns;
            for(int i=0;i<(widget==Widget.ELEMENTS?8:quickCount);i++){int track=horizontal?i%columns:i/columns;if(track<count)sizes[track]=Math.max(sizes[track],widget==Widget.ELEMENTS?17*elementScale(i):24*quickSlotScale(i));}
            double total=Arrays.stream(sizes).sum();
            double gap=p.distribution==2&&count>1?Math.max(0,(available-total)/(count-1)):p.gap/scale;
            if(p.distribution==1)position+=available*index/count+Math.max(0,available/count-size)*(horizontal?p.alignX:p.alignY)/2;
            else{
                position+=Math.max(0,available-total-gap*(count-1))*(horizontal?p.alignX:p.alignY)/2;
                for(int i=0;i<index;i++)position+=sizes[i]+gap;
                position+=(sizes[index]-size)*(horizontal?p.alignX:p.alignY)/2;
            }
            return Math.max(start,Math.min(position,Math.max(start,end-size)));
        }
        if(p.distribution==1)position+=available*index/count+Math.max(0,available/count-size)*(horizontal?p.alignX:p.alignY)/2;
        else if(p.distribution==2)position+=count==1?Math.max(0,available-size)*(horizontal?p.alignX:p.alignY)/2:Math.max(0,available-size)*index/(count-1);
        else{
            double step=(widget==Widget.QUICK_SLOTS?24:17)+p.gap/scale;
            position+=Math.max(0,available-(count-1)*step-size)*(horizontal?p.alignX:p.alignY)/2+index*step;
        }
        return Math.max(start,Math.min(position,Math.max(start,end-size)));
    }
    public static int padding(Widget widget){return (int)PLACEMENTS[widget.ordinal()].padding;}
    public static int gap(Widget widget){return (int)PLACEMENTS[widget.ordinal()].gap;}
    public static void spacing(Widget widget,int padding,int gap){Placement p=PLACEMENTS[widget.ordinal()];p.padding=net.minecraft.util.Mth.clamp(padding,0,24);p.gap=net.minecraft.util.Mth.clamp(gap,0,24);save();}
    public static double contentFit(Widget widget){
        double requiredX=requiredContent(widget,true),requiredY=requiredContent(widget,false);
        return Math.min(1,Math.min(frameWidth(widget,baseWidth(widget))/requiredX,frameHeight(widget,baseHeight(widget))/requiredY));
    }
    private static double requiredContent(Widget widget,boolean horizontal){
        Placement p=PLACEMENTS[widget.ordinal()];
        double scale=horizontal?p.scaleX:p.scaleY;
        double padding=2*p.padding/scale;
        if(widget==Widget.BOOK_HINT)return 11.2+padding;
        if(widget==Widget.CAST_STATUS)return padding+(horizontal?17*castCount+(castCount-1)*p.gap/scale:17+(p.titleVisible?13:0));
        boolean elements=widget==Widget.ELEMENTS,custom=elements?elementCustom:quickCustom;
        int columns=elements?elementColumns:quickColumns,rows=elements?elementRows:quickRows,count=elements?8:quickCount;
        double[] sizes=new double[horizontal?columns:rows];
        double extent=0;
        for(int i=0;i<count;i++){
            SlotPlacement slot=elements?ELEMENT_PLACEMENTS[i]:QUICK_PLACEMENTS[i];
            double size=(elements?17:24)*slot.scale;
            int track=horizontal?i%columns:i/columns;
            sizes[track]=Math.max(sizes[track],size);
            extent=Math.max(extent,(horizontal?slot.x:slot.y)+size);
        }
        if(custom)return Math.max(1,extent+padding);
        double content=p.distribution==1?Arrays.stream(sizes).max().orElse(0)*sizes.length:Arrays.stream(sizes).sum()+(sizes.length-1)*p.gap/scale;
        return Math.max(1,padding+content+(!horizontal&&elements&&p.titleVisible?13:0));
    }
    private static double alignedOffset(Widget widget,boolean horizontal){
        boolean custom=widget==Widget.ELEMENTS?elementCustom:widget==Widget.QUICK_SLOTS&&quickCustom;
        if(!custom&&widget!=Widget.BOOK_HINT)return 0;
        Placement p=PLACEMENTS[widget.ordinal()];
        double fit=contentFit(widget),scale=(horizontal?p.scaleX:p.scaleY)*fit;
        double min=widget==Widget.BOOK_HINT?5:Double.POSITIVE_INFINITY,max=widget==Widget.BOOK_HINT?16.2:Double.NEGATIVE_INFINITY;
        if(custom){
            int count=widget==Widget.ELEMENTS?8:quickCount;
            for(int i=0;i<count;i++){
                SlotPlacement slot=widget==Widget.ELEMENTS?ELEMENT_PLACEMENTS[i]:QUICK_PLACEMENTS[i];
                double start=horizontal?slot.x:slot.y,size=(widget==Widget.ELEMENTS?17:24)*slot.scale;
                min=Math.min(min,start);max=Math.max(max,start+size);
            }
        }
        double frame=(horizontal?frameWidth(widget,baseWidth(widget))*p.scaleX:frameHeight(widget,baseHeight(widget))*p.scaleY);
        double padding=p.padding*fit,header=!horizontal&&widget==Widget.ELEMENTS&&p.titleVisible?13*scale:0;
        double available=Math.max(0,frame-2*padding-header-(max-min)*scale);
        return padding+header+available*(horizontal?p.alignX:p.alignY)/2-min*scale;
    }
    private MagicHudLayout(){}
}
