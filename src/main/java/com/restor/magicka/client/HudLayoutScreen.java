package com.restor.magicka.client;

import com.restor.magicka.MagicNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.ArrayDeque;

public final class HudLayoutScreen extends Screen {
    private static final int HELP_WIDTH=312,HELP_HEIGHT=353;
    private float helpScale(){return Math.min(.62f,Math.min((width-8f)/HELP_WIDTH,(height-8f)/panelHeight()));}
    private MagicHudLayout.Widget selected=MagicHudLayout.Widget.ELEMENTS;
    private boolean parentSelected=true;
    private boolean dragging,resizing,draggingQuickSlot,resizingQuickSlot,draggingElementSlot,resizingElementSelection,resizingCastSelection,helpOpen=true,helpDragging;
    private double dragX,dragY,startX,startY,startScaleX,startScaleY,startFrameScaleX,startFrameScaleY,lastResizeClickX,lastResizeClickY,helpDragX,helpDragY,quickDragX,quickDragY,quickStartScale,elementDragX,elementDragY;
    private long lastResizeClickTime;
    private MagicHudLayout.Widget lastResizeWidget;
    private int helpX,helpY,selectedQuickSlot=-1,selectedElementSlot=-1;
    private final ArrayDeque<String> undoHistory=new ArrayDeque<>(),redoHistory=new ArrayDeque<>();
    private String historyStart;
    private final Set<Integer> selectedElementSlots=new LinkedHashSet<>();
    private final Set<Integer> selectedCastSlots=new LinkedHashSet<>();
    private int selectedCastSlot=-1;
    private final Map<Integer,Double> elementStartScales=new LinkedHashMap<>();
    private MagicHudLayout.Bounds elementStartBounds;
    private int vanillaHeight;
    private MagicHudLayout.Bounds resizeStartBounds;
    public HudLayoutScreen(){this(MagicHudLayout.Widget.ELEMENTS);}
    public HudLayoutScreen(MagicHudLayout.Widget initial){super(Component.translatable("screen.magicka.hud_layout.title"));selected=initial;}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void extractBackground(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick){}
    private int panelHeight(){return switch(selected){case QUICK_SLOTS->452;case ELEMENTS->438;case CAST_STATUS->347;case BOOK_HINT->347;};}
    private void selectWidget(MagicHudLayout.Widget widget){
        boolean changed=selected!=widget;
        int oldHeight=panelHeight();selected=widget;parentSelected=true;MagicHudLayout.editorSelection(widget);selectedQuickSlot=-1;
        if(changed){selectedElementSlot=-1;selectedElementSlots.clear();selectedCastSlot=-1;selectedCastSlots.clear();}
        int newHeight=panelHeight();helpY=Math.clamp(helpY-(int)Math.round((newHeight-oldHeight)*helpScale()/2),0,Math.max(0,height-(int)Math.ceil(newHeight*helpScale())));
    }
    @Override protected void init(){MagicHudLayout.editorSelection(selected);helpX=Math.max(0,(int)Math.round((width-HELP_WIDTH*helpScale())/2));helpY=Math.max(0,(int)Math.round((height-panelHeight()*helpScale())/2));}
    private int uiHeight(){var mc=Minecraft.getInstance();return Math.max(mc.gui.hud.leftHeight,mc.gui.hud.rightHeight);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick){
        int w=g.guiWidth(),h=g.guiHeight();vanillaHeight=uiHeight();
        g.fill(0,0,w,h,0xA9080B12);
        if(MagicHudLayout.gridSnap())renderGrid(g,w,h);
        MagicHud.renderLayoutPreview(g,MagicNetwork.clientState,vanillaHeight);
        g.pose().pushMatrix();g.nextStratum();
        for(var widget:MagicHudLayout.Widget.values()){
            var b=MagicHudLayout.editBounds(widget,w,h,vanillaHeight);int color=widget==selected && parentSelected?(MagicHudLayout.visible(widget)?0xFFFFD875:0x80FFD875):(MagicHudLayout.visible(widget)?0xBBE8D8FF:0x55E8D8FF);
            g.outline(b.x(),b.y(),b.width(),b.height(),color);
            g.fill(b.x()+b.width()-9,b.y()+b.height()-9,b.x()+b.width(),b.y()+b.height(),color);
        }
        if(selected==MagicHudLayout.Widget.ELEMENTS && !selectedElementSlots.isEmpty()){
            for(int index:selectedElementSlots){var b=MagicHudLayout.elementSlotBounds(index,w,h,vanillaHeight);g.outline(b.x(),b.y(),b.width(),b.height(),0xFFFFD875);}
            var b=elementSelectionBounds(w,h,vanillaHeight);g.outline(b.x(),b.y(),b.width(),b.height(),0xFFFFD875);g.fill(b.x(),b.y()+Math.max(0,b.height()-8),b.x()+Math.min(8,b.width()),b.y()+b.height(),0xFFFFD875);
        }
        if(selected==MagicHudLayout.Widget.CAST_STATUS && !selectedCastSlots.isEmpty()){
            for(int index:selectedCastSlots){var b=MagicHudLayout.castSlotBounds(index,w,h,vanillaHeight);g.outline(b.x(),b.y(),b.width(),b.height(),0xFFFFD875);}
            var b=castSelectionBounds(w,h,vanillaHeight);g.outline(b.x(),b.y(),b.width(),b.height(),0xFFFFD875);g.fill(b.x(),b.y()+Math.max(0,b.height()-8),b.x()+Math.min(8,b.width()),b.y()+b.height(),0xFFFFD875);
        }
        if(selected==MagicHudLayout.Widget.QUICK_SLOTS && selectedQuickSlot>=0){
            var b=MagicHudLayout.quickSlotBounds(selectedQuickSlot,w,h,vanillaHeight);g.outline(b.x(),b.y(),b.width(),b.height(),0xFFFFD875);
            g.fill(b.x()+b.width()-8,b.y()+b.height()-8,b.x()+b.width(),b.y()+b.height(),0xFFFFD875);
        }
        g.pose().popMatrix();
        super.extractRenderState(g,mouseX,mouseY,partialTick);
        if(helpOpen){g.pose().pushMatrix();g.nextStratum();g.pose().translate((float)(helpX),(float)(helpY));g.pose().scale(helpScale(),helpScale());g.pose().translate((float)(-helpX),(float)(-helpY));renderHelp(g);g.pose().popMatrix();}
    }
    private void renderHelp(GuiGraphicsExtractor g){
        g.fill(helpX+3,helpY+3,helpX+HELP_WIDTH+3,helpY+panelHeight()+3,0xB0000000);
        g.fill(helpX,helpY,helpX+HELP_WIDTH,helpY+panelHeight(),0xF010131C);g.outline(helpX,helpY,HELP_WIDTH,panelHeight(),0xFFFFD875);
        g.fill(helpX+1,helpY+1,helpX+HELP_WIDTH-1,helpY+22,0xFF242033);
        fittedText(g,this.title,helpX+9,helpY+7,HELP_WIDTH-80,0xFFFFD875);
        int closeX=helpX+HELP_WIDTH-19;g.fill(closeX,helpY+3,closeX+15,helpY+19,0xFF3A2630);g.outline(closeX,helpY+3,15,16,0xFF8B5963);
        stepper(g,helpY+panelHeight()-44,Component.translatable("screen.magicka.hud_layout.padding"),MagicHudLayout.padding(selected),false);
        stepper(g,helpY+panelHeight()-23,Component.translatable("screen.magicka.hud_layout.gap"),MagicHudLayout.gap(selected),false);
        g.text(this.font,"×",closeX+4,helpY+7,0xFFFFD8DC,false);
        button(g,helpX+HELP_WIDTH-61,helpY+3,17,16,Component.literal("↶"),!undoHistory.isEmpty(),false);
        button(g,helpX+HELP_WIDTH-41,helpY+3,17,16,Component.literal("↷"),!redoHistory.isEmpty(),false);
        String selectedName=Component.translatable("screen.magicka.hud_layout."+selected.name().toLowerCase(java.util.Locale.ROOT)).getString();
        fittedText(g,Component.translatable("screen.magicka.hud_layout."+selected.name().toLowerCase(java.util.Locale.ROOT)),helpX+10,helpY+29,174,0xFFFFFFFF);
        fittedText(g,Component.translatable("screen.magicka.hud_layout.opacity",Math.round(MagicHudLayout.opacity(selected)*100)),helpX+190,helpY+29,112,0xFFB8B3A8);
        button(g,helpX+10,helpY+40,141,18,Component.translatable("screen.magicka.hud_layout.visible",Component.translatable(MagicHudLayout.visible(selected)?"screen.magicka.hud_layout.on":"screen.magicka.hud_layout.off")),true,MagicHudLayout.visible(selected));
        button(g,helpX+10,helpY+63,141,18,Component.translatable("screen.magicka.hud_layout.text_hints",Component.translatable(MagicHudLayout.textHints(selected)?"screen.magicka.hud_layout.on":"screen.magicka.hud_layout.off")),true,MagicHudLayout.textHints(selected));
        button(g,helpX+161,helpY+63,141,18,Component.translatable("screen.magicka.hud_layout.background",Component.translatable(MagicHudLayout.background(selected)?"screen.magicka.hud_layout.on":"screen.magicka.hud_layout.off")),true,MagicHudLayout.background(selected));
        if(selected==MagicHudLayout.Widget.ELEMENTS||selected==MagicHudLayout.Widget.CAST_STATUS)button(g,helpX+161,helpY+40,141,18,Component.translatable("screen.magicka.hud_layout.heading",Component.translatable(MagicHudLayout.titleVisible(selected)?"screen.magicka.hud_layout.on":"screen.magicka.hud_layout.off")),true,MagicHudLayout.titleVisible(selected));
        boolean quickSelected=selected==MagicHudLayout.Widget.QUICK_SLOTS,elementSelected=selected==MagicHudLayout.Widget.ELEMENTS;
        int widthY,heightY,alignY,gridY,commonY,layerY;
        if(quickSelected){
            button(g,helpX+10,helpY+86,292,18,Component.translatable("screen.magicka.hud_layout.quick_mode",Component.translatable("screen.magicka.hud_layout."+(MagicHudLayout.quickCustom()?"custom":"row"))),true,MagicHudLayout.quickCustom());
            stepper(g,helpY+109,"screen.magicka.hud_layout.columns",MagicHudLayout.quickColumns(),MagicHudLayout.quickCustom());
            stepper(g,helpY+130,"screen.magicka.hud_layout.rows",MagicHudLayout.quickRows(),MagicHudLayout.quickCustom());
            stepper(g,helpY+151,"screen.magicka.hud_layout.quick_count",MagicHudLayout.quickCount(),false);
            widthY=172;heightY=193;alignY=235;gridY=277;layerY=300;commonY=323;
            stepper(g,helpY+214,"screen.magicka.hud_layout.slot_scale",selectedQuickSlot>=0?(int)Math.round(MagicHudLayout.quickSlotScale(selectedQuickSlot)*100):100,selectedQuickSlot<0);
        }else if(elementSelected){
            button(g,helpX+10,helpY+86,292,18,Component.translatable("screen.magicka.hud_layout.quick_mode",Component.translatable("screen.magicka.hud_layout."+(MagicHudLayout.elementCustom()?"custom":"row"))),true,MagicHudLayout.elementCustom());
            stepper(g,helpY+109,"screen.magicka.hud_layout.columns",MagicHudLayout.elementColumns(),MagicHudLayout.elementCustom());
            stepper(g,helpY+130,"screen.magicka.hud_layout.rows",MagicHudLayout.elementRows(),MagicHudLayout.elementCustom());
            widthY=151;heightY=172;alignY=220;gridY=262;layerY=285;commonY=308;
            double avgScale=selectedElementSlots.stream().mapToDouble(MagicHudLayout::elementScale).average().orElse(1);
            stepper(g,helpY+193,Component.translatable("screen.magicka.hud_layout.element_scale",selectedElementSlots.size()),(int)Math.round(avgScale*100),selectedElementSlots.isEmpty());
        }else if(selected==MagicHudLayout.Widget.CAST_STATUS){
            widthY=86;heightY=107;alignY=128;gridY=170;layerY=193;commonY=216;
        }else{
            widthY=86;heightY=107;alignY=128;gridY=170;layerY=193;commonY=216;
        }
        alignY+=12;gridY+=12;layerY+=12;commonY+=12;
        stepper(g,helpY+widthY,"screen.magicka.hud_layout.width",(int)Math.round(MagicHudLayout.frameScaleX(selected)*100),false);
        stepper(g,helpY+heightY,"screen.magicka.hud_layout.height",(int)Math.round(MagicHudLayout.frameScaleY(selected)*100),false);
        alignmentGrid(g,helpY+alignY);
        button(g,helpX+10,helpY+alignY+21,292,18,Component.translatable("screen.magicka.hud_layout.distribution",distributionLabel()),true,false);
        button(g,helpX+10,helpY+gridY,292,18,Component.translatable("screen.magicka.hud_layout.grid_button",Component.translatable(MagicHudLayout.gridSnap()?"screen.magicka.hud_layout.on":"screen.magicka.hud_layout.off")),true,MagicHudLayout.gridSnap());
        button(g,helpX+10,helpY+layerY,141,18,Component.translatable("screen.magicka.hud_layout.layer_back"),true,false);
        button(g,helpX+161,helpY+layerY,141,18,Component.translatable("screen.magicka.hud_layout.layer_front"),true,false);
        button(g,helpX+10,helpY+commonY,141,18,Component.translatable(quickSelected && selectedQuickSlot>=0?"screen.magicka.hud_layout.reset_slot":"screen.magicka.hud_layout.reset_selected"),true,false);
        button(g,helpX+161,helpY+commonY,141,18,Component.translatable("screen.magicka.hud_layout.reset_defaults"),true,false);
        button(g,helpX+10,helpY+commonY+23,141,18,Component.translatable("screen.magicka.hud_layout.export"),true,false);
        button(g,helpX+161,helpY+commonY+23,141,18,Component.translatable("screen.magicka.hud_layout.import"),true,false);
        g.pose().pushMatrix();g.pose().translate((float)(helpX+HELP_WIDTH/2),(float)(helpY+commonY+47));g.pose().scale(.78f,.78f);
        int hintY=0;for(var hint:font.split(Component.translatable(elementSelected?"screen.magicka.hud_layout.element_select_help":"screen.magicka.hud_layout.mouse_help"),360)){g.text(font,hint,-180,hintY,0xFFB8B3A8,false);hintY+=font.lineHeight;}
        g.pose().popMatrix();
    }
    private void stepper(GuiGraphicsExtractor g,int y,String label,int value,boolean disabled){
        stepper(g,y,Component.translatable(label),value,disabled);
    }
    private void alignmentGrid(GuiGraphicsExtractor g,int y){
        g.centeredText(this.font,Component.translatable("screen.magicka.hud_layout.alignment"),helpX+HELP_WIDTH/2,y-10,0xFFB8B3A8);
        int x=helpX+(HELP_WIDTH-178)/2;
        for(int index=0;index<9;index++){
            int col=index%3,row=index/3,buttonX=x+index*20;
            boolean active=MagicHudLayout.alignX(selected)==col && MagicHudLayout.alignY(selected)==row;
            button(g,buttonX,y,18,18,Component.empty(),true,active);
            int dotX=buttonX+4+col*5,dotY=y+4+row*5;
            g.fill(dotX-1,dotY-1,dotX+2,dotY+2,active?0xFFFFD875:0xFFB8B3C8);
        }
    }
    private Component distributionLabel(){return Component.translatable("screen.magicka.hud_layout.distribution."+switch(MagicHudLayout.distribution(selected)){case 1->"even";case 2->"edges";default->"packed";});}
    private void stepper(GuiGraphicsExtractor g,int y,Component label,int value,boolean disabled){
        fittedText(g,label,helpX+14,y+5,168,disabled?0xFF777785:0xFFE0DCE8);
        button(g,helpX+190,y,26,18,Component.literal("−"),!disabled,false);
        button(g,helpX+218,y,52,18,Component.literal(""+value),!disabled,true);
        button(g,helpX+272,y,26,18,Component.literal("+"),!disabled,false);
    }
    private void button(GuiGraphicsExtractor g,int x,int y,int width,int height,Component label,boolean enabled,boolean active){
        int fill=!enabled?0xFF171821:active?0xFF39304D:0xFF252536,border=!enabled?0xFF33323A:active?MagicUi.GOLD:0xFF625673,color=!enabled?0xFF777785:0xFFE8E0F0;
        g.fill(x,y,x+width,y+height,fill);g.outline(x,y,width,height,border);float textScale=Math.min(1f,(width-8f)/Math.max(1,font.width(label)));int labelWidth=Math.round(font.width(label)*textScale);fittedText(g,label,x+(width-labelWidth)/2,y+(height-8)/2,width-8,color);
    }
    private void fittedText(GuiGraphicsExtractor g,Component label,int x,int y,int maxWidth,int color){
        float scale=Math.min(1f,maxWidth/(float)Math.max(1,font.width(label)));
        g.pose().pushMatrix();g.pose().translate(x,y);g.pose().scale(scale,scale);
        g.text(font,label,0,0,color,false);g.pose().popMatrix();
    }
    private void renderGrid(GuiGraphicsExtractor g,int width,int height){
        for(int x=0;x<width;x+=MagicHudLayout.GRID_CELL)g.fill(x,0,x+1,height,0x253A5068);
        for(int y=0;y<height;y+=MagicHudLayout.GRID_CELL)g.fill(0,y,width,y+1,0x253A5068);
    }
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,boolean doubleClick){double mouseX=event.x(),mouseY=event.y();int button=event.button();
        double visualWidth=HELP_WIDTH*helpScale(),visualHeight=panelHeight()*helpScale();
        if(button==InputConstants.MOUSE_BUTTON_LEFT && helpOpen && mouseX>=helpX && mouseX<helpX+visualWidth && mouseY>=helpY && mouseY<helpY+visualHeight){
            double panelX=helpX+(mouseX-helpX)/helpScale(),panelY=helpY+(mouseY-helpY)/helpScale();
            if(panelX>=helpX+HELP_WIDTH-20 && panelY<helpY+22){onClose();return true;}
            if(panelY>=helpY+3 && panelY<helpY+19 && panelX>=helpX+HELP_WIDTH-61 && panelX<helpX+HELP_WIDTH-44){undo();return true;}
            if(panelY>=helpY+3 && panelY<helpY+19 && panelX>=helpX+HELP_WIDTH-41 && panelX<helpX+HELP_WIDTH-24){redo();return true;}
            if(panelY<helpY+23){helpDragging=true;helpDragX=mouseX-helpX;helpDragY=mouseY-helpY;return true;}
            beginHistory();
            handlePanelClick(panelX,panelY);return true;
        }
        if(button==InputConstants.MOUSE_BUTTON_LEFT){
            beginHistory();
            MagicHudLayout.Widget[] widgets=MagicHudLayout.Widget.values();
            for(int i=widgets.length-1;i>=0;i--){
                MagicHudLayout.Widget widget=widgets[i];
                var b=MagicHudLayout.editBounds(widget,width,height,vanillaHeight);
                if(!in((int)mouseX,(int)mouseY,b.x()+b.width()-9,b.y()+b.height()-9,9,9))continue;
                boolean keepChildren=keyDown(InputConstants.KEY_LSHIFT)||keyDown(InputConstants.KEY_RSHIFT)||keyDown(InputConstants.KEY_LCONTROL)||keyDown(InputConstants.KEY_RCONTROL);
                int quickSelection=selected==widget?selectedQuickSlot:-1;
                selectWidget(widget);
                if(keepChildren){selectedQuickSlot=quickSelection;}
                else{selectedElementSlots.clear();selectedElementSlot=-1;selectedCastSlots.clear();selectedCastSlot=-1;selectedQuickSlot=-1;}
                if(selectedQuickSlot>=0)quickStartScale=MagicHudLayout.quickSlotScale(selectedQuickSlot);
                resizeStartBounds=b;resizing=true;dragging=false;dragX=mouseX-b.x();dragY=mouseY-b.y();startX=mouseX;startY=mouseY;
                startScaleX=MagicHudLayout.scaleX(widget);startScaleY=MagicHudLayout.scaleY(widget);startFrameScaleX=MagicHudLayout.frameScaleX(widget);startFrameScaleY=MagicHudLayout.frameScaleY(widget);
                long now=System.currentTimeMillis();
                if(lastResizeWidget==widget && now-lastResizeClickTime<650 && Math.abs(mouseX-lastResizeClickX)<10 && Math.abs(mouseY-lastResizeClickY)<10){
                    if(widget==MagicHudLayout.Widget.ELEMENTS){selectedElementSlots.clear();for(int slot=0;slot<8;slot++)selectedElementSlots.add(slot);}
                    if(widget==MagicHudLayout.Widget.CAST_STATUS){selectedCastSlots.clear();for(int slot=0;slot<MagicHudLayout.castCount();slot++)selectedCastSlots.add(slot);}
                    lastResizeWidget=null;lastResizeClickTime=0;
                }else{lastResizeWidget=widget;lastResizeClickTime=now;lastResizeClickX=mouseX;lastResizeClickY=mouseY;}
                if(widget==MagicHudLayout.Widget.ELEMENTS && !selectedElementSlots.isEmpty()){
                    elementStartScales.clear();for(int slot:selectedElementSlots)elementStartScales.put(slot,MagicHudLayout.elementScale(slot));
                    elementStartBounds=elementSelectionBounds(width,height,vanillaHeight);
                }else elementStartBounds=null;
                return true;
            }
            if(selected==MagicHudLayout.Widget.ELEMENTS && !selectedElementSlots.isEmpty()){
                var b=elementSelectionBounds(width,height,vanillaHeight);
                if(in((int)mouseX,(int)mouseY,b.x(),b.y()+Math.max(0,b.height()-8),Math.min(8,b.width()),Math.min(8,b.height()))){
                    resizingElementSelection=true;elementStartBounds=b;elementStartScales.clear();for(int index:selectedElementSlots)elementStartScales.put(index,MagicHudLayout.elementScale(index));startX=mouseX;startY=mouseY;startFrameScaleX=MagicHudLayout.frameScaleX(selected);startFrameScaleY=MagicHudLayout.frameScaleY(selected);return true;
                }
            }
            if(selected==MagicHudLayout.Widget.CAST_STATUS && !selectedCastSlots.isEmpty()){
                var b=castSelectionBounds(width,height,vanillaHeight);
                if(in((int)mouseX,(int)mouseY,b.x(),b.y()+Math.max(0,b.height()-8),Math.min(8,b.width()),Math.min(8,b.height()))){resizingCastSelection=true;elementStartBounds=b;startX=mouseX;startY=mouseY;startScaleX=MagicHudLayout.scaleX(selected);startScaleY=MagicHudLayout.scaleY(selected);startFrameScaleX=MagicHudLayout.frameScaleX(selected);startFrameScaleY=MagicHudLayout.frameScaleY(selected);return true;}
            }
            for(int i=0;i<8;i++){
                var b=MagicHudLayout.elementSlotBounds(i,width,height,vanillaHeight);if(!b.contains(mouseX,mouseY))continue;
                boolean control=keyDown(InputConstants.KEY_LCONTROL)||keyDown(InputConstants.KEY_RCONTROL);
                boolean shift=keyDown(InputConstants.KEY_LSHIFT)||keyDown(InputConstants.KEY_RSHIFT);
                if(selected!=MagicHudLayout.Widget.ELEMENTS)selectWidget(MagicHudLayout.Widget.ELEMENTS);
                if(!control && !shift)parentSelected=false;
                selectElementSlot(i,control,shift);
                if(MagicHudLayout.elementCustom()){draggingElementSlot=true;elementDragX=mouseX-b.x();elementDragY=mouseY-b.y();}
                return true;
            }
            for(int i=0;i<MagicHudLayout.quickCount();i++){
                var b=MagicHudLayout.quickSlotBounds(i,width,height,vanillaHeight);if(!b.contains(mouseX,mouseY))continue;
                boolean control=keyDown(InputConstants.KEY_LCONTROL)||keyDown(InputConstants.KEY_RCONTROL),shift=keyDown(InputConstants.KEY_LSHIFT)||keyDown(InputConstants.KEY_RSHIFT);
                selectWidget(MagicHudLayout.Widget.QUICK_SLOTS);selectedQuickSlot=i;
                if(!control && !shift)parentSelected=false;
                resizingQuickSlot=mouseX>=b.x()+b.width()-10 && mouseY>=b.y()+b.height()-10;draggingQuickSlot=!resizingQuickSlot && MagicHudLayout.quickCustom();
                quickDragX=mouseX-b.x();quickDragY=mouseY-b.y();quickStartScale=MagicHudLayout.quickSlotScale(i);startFrameScaleX=MagicHudLayout.frameScaleX(selected);startFrameScaleY=MagicHudLayout.frameScaleY(selected);startX=mouseX;startY=mouseY;return true;
            }
            for(int i=0;i<MagicHudLayout.castCount();i++){
                var b=MagicHudLayout.castSlotBounds(i,width,height,vanillaHeight);if(!b.contains(mouseX,mouseY))continue;
                boolean control=keyDown(InputConstants.KEY_LCONTROL)||keyDown(InputConstants.KEY_RCONTROL),shift=keyDown(InputConstants.KEY_LSHIFT)||keyDown(InputConstants.KEY_RSHIFT);
                if(selected!=MagicHudLayout.Widget.CAST_STATUS)selectWidget(MagicHudLayout.Widget.CAST_STATUS);
                if(!control&&!shift){parentSelected=false;selectedCastSlots.clear();}
                if(shift&&selectedCastSlot>=0){for(int slot=Math.min(i,selectedCastSlot);slot<=Math.max(i,selectedCastSlot);slot++)selectedCastSlots.add(slot);}else if(control){if(!selectedCastSlots.remove(i))selectedCastSlots.add(i);}else selectedCastSlots.add(i);
                selectedCastSlot=i;return true;
            }
            for(var widget:MagicHudLayout.Widget.values()){
                var b=MagicHudLayout.editBounds(widget,width,height,vanillaHeight);
                if(!b.contains(mouseX,mouseY))continue;
                selectWidget(widget);if(widget==MagicHudLayout.Widget.ELEMENTS)selectedElementSlots.clear();if(widget==MagicHudLayout.Widget.QUICK_SLOTS)selectedQuickSlot=-1;if(widget==MagicHudLayout.Widget.CAST_STATUS)selectedCastSlots.clear();
                resizing=false;dragging=true;
                dragX=mouseX-b.x();dragY=mouseY-b.y();startX=mouseX;startY=mouseY;startScaleX=MagicHudLayout.scaleX(widget);startScaleY=MagicHudLayout.scaleY(widget);startFrameScaleX=MagicHudLayout.frameScaleX(widget);startFrameScaleY=MagicHudLayout.frameScaleY(widget);
                return true;
            }
        }
        return super.mouseClicked(event,doubleClick);
    }
    private void handlePanelClick(double mouseX,double mouseY){
        int x=(int)mouseX,y=(int)mouseY;
        int delta=in(x,y,helpX+190,helpY+panelHeight()-44,26,39)?-1:in(x,y,helpX+272,helpY+panelHeight()-44,26,39)?1:0;
        if(delta!=0){boolean padding=y<helpY+panelHeight()-23;MagicHudLayout.spacing(selected,MagicHudLayout.padding(selected)+(padding?delta:0),MagicHudLayout.gap(selected)+(padding?0:delta));return;}
        boolean quickSelected=selected==MagicHudLayout.Widget.QUICK_SLOTS,elementSelected=selected==MagicHudLayout.Widget.ELEMENTS;
        int widthY,heightY,alignY,gridY,layerY,commonY;
        if(quickSelected){widthY=172;heightY=193;alignY=235;gridY=277;layerY=300;commonY=323;}
        else if(elementSelected){widthY=151;heightY=172;alignY=220;gridY=262;layerY=285;commonY=308;}
        else if(selected==MagicHudLayout.Widget.CAST_STATUS){widthY=86;heightY=107;alignY=128;gridY=170;layerY=193;commonY=216;}
        else{widthY=86;heightY=107;alignY=128;gridY=170;layerY=193;commonY=216;}
        alignY+=12;gridY+=12;layerY+=12;commonY+=12;
        int alignX=helpX+(HELP_WIDTH-178)/2;
        if(in(x,y,alignX,helpY+alignY,178,18)){int index=Math.clamp((x-alignX)/20,0,8);MagicHudLayout.alignContent(selected,index%3,index/3);return;}
        if(in(x,y,helpX+10,helpY+alignY+21,292,18)){MagicHudLayout.cycleDistribution(selected);return;}
        if((selected==MagicHudLayout.Widget.ELEMENTS||selected==MagicHudLayout.Widget.CAST_STATUS)&&in(x,y,helpX+161,helpY+40,141,18)){MagicHudLayout.toggleTitle(selected);return;}
        if(in(x,y,helpX+10,helpY+40,141,18)){MagicHudLayout.toggleVisible(selected);return;}
        if(in(x,y,helpX+10,helpY+63,141,18)){MagicHudLayout.toggleTextHints(selected);return;}
        if(in(x,y,helpX+161,helpY+63,141,18)){MagicHudLayout.toggleBackground(selected);return;}
        if(quickSelected && in(x,y,helpX+10,helpY+86,292,18)){MagicHudLayout.quickCustom(!MagicHudLayout.quickCustom());selectedQuickSlot=-1;return;}
        if(elementSelected && in(x,y,helpX+10,helpY+86,292,18)){MagicHudLayout.elementCustom(!MagicHudLayout.elementCustom());selectedElementSlot=-1;return;}
        if(quickSelected && !MagicHudLayout.quickCustom() && in(x,y,helpX+190,helpY+109,26,18)){selectedQuickSlot=-1;MagicHudLayout.quickColumns(MagicHudLayout.quickColumns()-1);return;}
        if(quickSelected && !MagicHudLayout.quickCustom() && in(x,y,helpX+272,helpY+109,26,18)){selectedQuickSlot=-1;MagicHudLayout.quickColumns(MagicHudLayout.quickColumns()+1);return;}
        if(quickSelected && !MagicHudLayout.quickCustom() && in(x,y,helpX+190,helpY+130,26,18)){selectedQuickSlot=-1;MagicHudLayout.quickRows(MagicHudLayout.quickRows()-1);return;}
        if(quickSelected && !MagicHudLayout.quickCustom() && in(x,y,helpX+272,helpY+130,26,18)){selectedQuickSlot=-1;MagicHudLayout.quickRows(MagicHudLayout.quickRows()+1);return;}
        if(elementSelected && !MagicHudLayout.elementCustom() && in(x,y,helpX+190,helpY+109,26,18)){MagicHudLayout.elementColumns(MagicHudLayout.elementColumns()-1);return;}
        if(elementSelected && !MagicHudLayout.elementCustom() && in(x,y,helpX+272,helpY+109,26,18)){MagicHudLayout.elementColumns(MagicHudLayout.elementColumns()+1);return;}
        if(elementSelected && !MagicHudLayout.elementCustom() && in(x,y,helpX+190,helpY+130,26,18)){MagicHudLayout.elementRows(MagicHudLayout.elementRows()-1);return;}
        if(elementSelected && !MagicHudLayout.elementCustom() && in(x,y,helpX+272,helpY+130,26,18)){MagicHudLayout.elementRows(MagicHudLayout.elementRows()+1);return;}
        if(quickSelected && in(x,y,helpX+190,helpY+151,26,18)){MagicHudLayout.quickCount(MagicHudLayout.quickCount()-1);return;}
        if(quickSelected && in(x,y,helpX+272,helpY+151,26,18)){MagicHudLayout.quickCount(MagicHudLayout.quickCount()+1);return;}
        if(in(x,y,helpX+190,helpY+widthY,26,18)){MagicHudLayout.resizeFrame(selected,MagicHudLayout.frameScaleX(selected)-.1,MagicHudLayout.frameScaleY(selected));return;}
        if(in(x,y,helpX+272,helpY+widthY,26,18)){MagicHudLayout.resizeFrame(selected,MagicHudLayout.frameScaleX(selected)+.1,MagicHudLayout.frameScaleY(selected));return;}
        if(in(x,y,helpX+190,helpY+heightY,26,18)){MagicHudLayout.resizeFrame(selected,MagicHudLayout.frameScaleX(selected),MagicHudLayout.frameScaleY(selected)-.1);return;}
        if(in(x,y,helpX+272,helpY+heightY,26,18)){MagicHudLayout.resizeFrame(selected,MagicHudLayout.frameScaleX(selected),MagicHudLayout.frameScaleY(selected)+.1);return;}
        if(elementSelected && !selectedElementSlots.isEmpty() && in(x,y,helpX+190,helpY+193,26,18)){MagicHudLayout.scaleElements(selectedElementSlots,-.1);return;}
        if(elementSelected && !selectedElementSlots.isEmpty() && in(x,y,helpX+272,helpY+193,26,18)){MagicHudLayout.scaleElements(selectedElementSlots,.1);return;}
        if(quickSelected && in(x,y,helpX+190,helpY+214,26,18) && selectedQuickSlot>=0){MagicHudLayout.scaleQuickSlot(selectedQuickSlot,MagicHudLayout.quickSlotScale(selectedQuickSlot)-.1,width,height,vanillaHeight);return;}
        if(quickSelected && in(x,y,helpX+272,helpY+214,26,18) && selectedQuickSlot>=0){MagicHudLayout.scaleQuickSlot(selectedQuickSlot,MagicHudLayout.quickSlotScale(selectedQuickSlot)+.1,width,height,vanillaHeight);return;}
        if(in(x,y,helpX+10,helpY+gridY,292,18)){MagicHudLayout.toggleGridSnap();return;}
        if(in(x,y,helpX+10,helpY+layerY,141,18)){MagicHudLayout.moveLayer(selected,false);return;}
        if(in(x,y,helpX+161,helpY+layerY,141,18)){MagicHudLayout.moveLayer(selected,true);return;}
        if(in(x,y,helpX+10,helpY+commonY,141,18)){
            if(quickSelected && selectedQuickSlot>=0)MagicHudLayout.resetQuickSlot(selectedQuickSlot);
            else{MagicHudLayout.reset(selected);if(elementSelected){selectedElementSlots.clear();selectedElementSlot=-1;}}return;
        }
        if(in(x,y,helpX+161,helpY+commonY,141,18)){MagicHudLayout.resetAll();selectedQuickSlot=-1;selectedElementSlot=-1;selectedElementSlots.clear();return;}
        if(in(x,y,helpX+10,helpY+commonY+23,141,18)){profileCode(true);return;}
        if(in(x,y,helpX+161,helpY+commonY+23,141,18))profileCode(false);
    }
    private static boolean in(int x,int y,int left,int top,int width,int height){return x>=left && x<left+width && y>=top && y<top+height;}
    private MagicHudLayout.Bounds elementSelectionBounds(int screenWidth,int screenHeight,int uiHeight){
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE;
        for(int index:selectedElementSlots){var b=MagicHudLayout.elementSlotBounds(index,screenWidth,screenHeight,uiHeight);minX=Math.min(minX,b.x());minY=Math.min(minY,b.y());maxX=Math.max(maxX,b.x()+b.width());maxY=Math.max(maxY,b.y()+b.height());}
        return new MagicHudLayout.Bounds(minX,minY,maxX-minX,maxY-minY);
    }
    private MagicHudLayout.Bounds castSelectionBounds(int screenWidth,int screenHeight,int uiHeight){
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE;
        for(int index:selectedCastSlots){var b=MagicHudLayout.castSlotBounds(index,screenWidth,screenHeight,uiHeight);minX=Math.min(minX,b.x());minY=Math.min(minY,b.y());maxX=Math.max(maxX,b.x()+b.width());maxY=Math.max(maxY,b.y()+b.height());}
        return new MagicHudLayout.Bounds(minX,minY,maxX-minX,maxY-minY);
    }
    private boolean keyDown(int key){return InputConstants.isKeyDown(key);}
    private void selectElementSlot(int index,boolean control,boolean shift){
        if(shift && selectedElementSlot>=0){if(!control)selectedElementSlots.clear();for(int i=Math.min(index,selectedElementSlot);i<=Math.max(index,selectedElementSlot);i++)selectedElementSlots.add(i);}
        else if(control){if(!selectedElementSlots.remove(index))selectedElementSlots.add(index);selectedElementSlot=index;}
        else{selectedElementSlots.clear();selectedElementSlots.add(index);selectedElementSlot=index;}
    }
    private void beginHistory(){if(historyStart==null)historyStart=MagicHudLayout.exportCode();}
    private void endHistory(){if(historyStart==null)return;String now=MagicHudLayout.exportCode();if(now!=null && !now.equals(historyStart)){undoHistory.addLast(historyStart);while(undoHistory.size()>40)undoHistory.removeFirst();redoHistory.clear();}historyStart=null;}
    private void undo(){endHistory();if(undoHistory.isEmpty())return;String current=MagicHudLayout.exportCode(),previous=undoHistory.removeLast();if(current!=null && MagicHudLayout.importCode(previous))redoHistory.addLast(current);}
    private void redo(){endHistory();if(redoHistory.isEmpty())return;String current=MagicHudLayout.exportCode(),next=redoHistory.removeLast();if(current!=null && MagicHudLayout.importCode(next))undoHistory.addLast(current);}
    private void profileCode(boolean export){
        boolean result=false;
        try{
            var keyboard=Minecraft.getInstance().keyboardHandler;
            if(export){String code=MagicHudLayout.exportCode();if(code!=null){keyboard.setClipboard(code);result=true;}}
            else result=MagicHudLayout.importCode(keyboard.getClipboard());
        }catch(Exception ignored){}
        var player=Minecraft.getInstance().player;if(player!=null)player.sendOverlayMessage(Component.translatable(export?(result?"screen.magicka.hud_layout.code_copied":"screen.magicka.hud_layout.code_failed"):(result?"screen.magicka.hud_layout.code_imported":"screen.magicka.hud_layout.code_invalid")));
        selectedQuickSlot=-1;
    }
    @Override public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event){double mouseX=event.x(),mouseY=event.y();int button=event.button();if(button==InputConstants.MOUSE_BUTTON_LEFT){endHistory();dragging=false;resizing=false;draggingQuickSlot=false;resizingQuickSlot=false;draggingElementSlot=false;resizingElementSelection=false;resizingCastSelection=false;helpDragging=false;return true;}return super.mouseReleased(event);}
    @Override public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event,double dragX,double dragY){double mouseX=event.x(),mouseY=event.y();int button=event.button();
        if(button!=InputConstants.MOUSE_BUTTON_LEFT)return super.mouseDragged(event,dragX,dragY);
        if(helpDragging){helpX=Math.clamp((int)Math.round(mouseX-helpDragX),0,Math.max(0,width-(int)Math.ceil(HELP_WIDTH*helpScale())));helpY=Math.clamp((int)Math.round(mouseY-helpDragY),0,Math.max(0,height-(int)Math.ceil(panelHeight()*helpScale())));return true;}
        if(resizingElementSelection && elementStartBounds!=null){double factor=1+Math.max((mouseX-startX)/Math.max(12,elementStartBounds.width()),(mouseY-startY)/Math.max(12,elementStartBounds.height()));MagicHudLayout.scaleElementsTo(elementStartScales,factor);if(parentSelected)MagicHudLayout.resizeFrame(selected,startFrameScaleX*factor,startFrameScaleY*factor);return true;}
        if(resizingCastSelection && elementStartBounds!=null){double factor=1+Math.max((mouseX-startX)/Math.max(12,elementStartBounds.width()),(mouseY-startY)/Math.max(12,elementStartBounds.height()));MagicHudLayout.resizeContent(selected,startScaleX*factor,startScaleY*factor);if(parentSelected)MagicHudLayout.resizeFrame(selected,startFrameScaleX*factor,startFrameScaleY*factor);return true;}
        if(draggingElementSlot){int x=(int)Math.round(mouseX-elementDragX),y=(int)Math.round(mouseY-elementDragY);if(MagicHudLayout.gridSnap()){x=Math.round(x/(float)MagicHudLayout.GRID_CELL)*MagicHudLayout.GRID_CELL;y=Math.round(y/(float)MagicHudLayout.GRID_CELL)*MagicHudLayout.GRID_CELL;}MagicHudLayout.moveElementTo(selectedElementSlot,x,y,width,height,vanillaHeight);return true;}
        if(resizingQuickSlot){double factor=1+((mouseX-startX)+(mouseY-startY))/48/Math.max(.1,quickStartScale);MagicHudLayout.scaleQuickSlot(selectedQuickSlot,quickStartScale*factor,width,height,vanillaHeight);if(parentSelected)MagicHudLayout.resizeFrame(selected,startFrameScaleX*factor,startFrameScaleY*factor);return true;}
        if(draggingQuickSlot){int x=(int)Math.round(mouseX-quickDragX),y=(int)Math.round(mouseY-quickDragY);
            if(MagicHudLayout.gridSnap()){x=Math.round(x/(float)MagicHudLayout.GRID_CELL)*MagicHudLayout.GRID_CELL;y=Math.round(y/(float)MagicHudLayout.GRID_CELL)*MagicHudLayout.GRID_CELL;}
            MagicHudLayout.moveQuickSlotTo(selectedQuickSlot,x,y,width,height,vanillaHeight);return true;}
        if(resizing){
            double frameX=startFrameScaleX*(1+(mouseX-startX)/Math.max(1,resizeStartBounds.width())),frameY=startFrameScaleY*(1+(mouseY-startY)/Math.max(1,resizeStartBounds.height()));
            MagicHudLayout.resizeFrame(selected,frameX,frameY);
            frameX=MagicHudLayout.frameScaleX(selected);frameY=MagicHudLayout.frameScaleY(selected);
            if(parentSelected && selected==MagicHudLayout.Widget.ELEMENTS && !selectedElementSlots.isEmpty() && elementStartBounds!=null){double factor=Math.max(frameX/Math.max(.01,startFrameScaleX),frameY/Math.max(.01,startFrameScaleY));MagicHudLayout.scaleElementsTo(elementStartScales,factor);}
            if(parentSelected && selected==MagicHudLayout.Widget.QUICK_SLOTS && selectedQuickSlot>=0){double factor=Math.max(frameX/Math.max(.01,startFrameScaleX),frameY/Math.max(.01,startFrameScaleY));MagicHudLayout.scaleQuickSlot(selectedQuickSlot,quickStartScale*factor,width,height,vanillaHeight);}
            if(parentSelected && selected==MagicHudLayout.Widget.CAST_STATUS && !selectedCastSlots.isEmpty())MagicHudLayout.resizeContent(selected,startScaleX*frameX/startFrameScaleX,startScaleY*frameY/startFrameScaleY);
            return true;
        }
        if(dragging){int x=(int)Math.round(mouseX-this.dragX),y=(int)Math.round(mouseY-this.dragY);
            if(MagicHudLayout.gridSnap()){x=Math.round(x/(float)MagicHudLayout.GRID_CELL)*MagicHudLayout.GRID_CELL;y=Math.round(y/(float)MagicHudLayout.GRID_CELL)*MagicHudLayout.GRID_CELL;}
            MagicHudLayout.moveTo(selected,x,y,width,height,vanillaHeight);return true;}
        return super.mouseDragged(event,dragX,dragY);
    }
    @Override public boolean mouseScrolled(double mouseX,double mouseY,double scrollX,double scrollY){
        if(helpOpen && mouseX>=helpX && mouseX<helpX+HELP_WIDTH*helpScale() && mouseY>=helpY && mouseY<helpY+panelHeight()*helpScale())return true;
        beginHistory();
        if(keyDown(InputConstants.KEY_LALT)||keyDown(InputConstants.KEY_RALT)){MagicHudLayout.opacity(selected,MagicHudLayout.opacity(selected)+scrollY*.05);endHistory();return true;}
        if(selected==MagicHudLayout.Widget.ELEMENTS && !selectedElementSlots.isEmpty() && selectedElementSlots.stream().anyMatch(i->MagicHudLayout.elementSlotBounds(i,width,height,vanillaHeight).contains(mouseX,mouseY))){MagicHudLayout.scaleElements(selectedElementSlots,scrollY*.1);endHistory();return true;}
        if(selected==MagicHudLayout.Widget.QUICK_SLOTS && selectedQuickSlot>=0 && MagicHudLayout.quickSlotBounds(selectedQuickSlot,width,height,vanillaHeight).contains(mouseX,mouseY))
            MagicHudLayout.scaleQuickSlot(selectedQuickSlot,MagicHudLayout.quickSlotScale(selectedQuickSlot)+scrollY*.12,width,height,vanillaHeight);
        else MagicHudLayout.scale(selected,MagicHudLayout.frameScaleX(selected)+scrollY*.08,MagicHudLayout.frameScaleY(selected)+scrollY*.08,width,height,vanillaHeight);
        endHistory();return true;
    }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event){int keyCode=event.key();
        if(keyCode==InputConstants.KEY_H){helpOpen=!helpOpen;return true;}
        return super.keyPressed(event);
    }
    @Override public void onClose(){MagicHudLayout.editorSelection(null);super.onClose();}
}
