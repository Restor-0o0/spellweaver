package com.restor.magicka;

import com.restor.magicka.client.SpellVisuals;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/** Renders the actual world VFX geometry through a fixed camera for automated visual inspection. */
public final class VfxPreviewScreen extends Screen {
    public VfxPreviewScreen(){super(Component.literal("Spell VFX"));}
    private void cue(int kind,SpellFormula f,Vec3 from,Vec3 to,float radius){SpellVisuals.receive(new MagicNetwork.Visual(kind,f.prepared().packed(),from,to,radius,20));}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        g.fill(0,0,width,height,0xFF18222C);g.drawCenteredString(font,"MAGICKA · SPELL EFFECTS",width/2,12,0xFFFFDCA0);
        SpellVisuals.clear();
        var fire=new SpellFormula(2,0,0,0,0,0,1);var water=new SpellFormula(0,2,0,0,0,0,0);var cold=new SpellFormula(0,1,2,0,0,0,0);
        var bolt=new SpellFormula(0,0,0,3,0,0,0);var earth=new SpellFormula(0,0,0,0,3,0,1);var life=new SpellFormula(0,1,0,0,0,2,0);var arcane=new SpellFormula(0,0,0,0,0,0,3);
        cue(SpellVfx.BEAM,arcane,new Vec3(-8,4,0),new Vec3(-1,4,0),.18f);cue(SpellVfx.ARC,bolt,new Vec3(1,4,0),new Vec3(8,4,0),.14f);
        var formulas=new SpellFormula[]{fire,water,cold,earth,arcane};for(int i=0;i<5;i++){var center=new Vec3(-7+i*3.5,1,0);cue(SpellVfx.ORB,formulas[i],center,center,.65f);}
        cue(SpellVfx.ZONE,life,new Vec3(-5,-2,0),new Vec3(-5,-2,0),2.2f);cue(SpellVfx.MINE,fire,new Vec3(0,-2,0),new Vec3(0,-2,0),1.5f);cue(SpellVfx.IMPACT,cold,new Vec3(5,-2,0),new Vec3(5,-2,0),3);
        g.flush();var pose=g.pose();pose.pushPose();pose.translate(width/2.0,height*.54,300);float scale=Math.min(width/20f,height/11f);pose.scale(scale,-scale,scale);pose.mulPose(Axis.XP.rotationDegrees(28));
        SpellVisuals.render(pose,minecraft.renderBuffers().bufferSource(),new Vec3(0,8,20),.6f);minecraft.renderBuffers().bufferSource().endBatch();pose.popPose();
        g.drawCenteredString(font,"BEAM / LIGHTNING · ELEMENTAL CORES · ZONE / MINE / IMPACT",width/2,height-19,0xFFBBC9D5);
    }
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){}
}
