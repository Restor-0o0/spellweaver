package com.restor.magicka.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.restor.magicka.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.*;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Bright world-space ribbons, persistent zone borders and vanilla block cores. No custom textures required. */
public final class SpellVisuals {
    private static final List<Cue> CUES=new ArrayList<>();
    private static final class Cue {final MagicNetwork.Visual packet;int age;Cue(MagicNetwork.Visual packet){this.packet=packet;}}
    public static void clear(){CUES.clear();}
    public static void receive(MagicNetwork.Visual packet){
        if(packet.kind()<0 || packet.kind()>7 || !GrimoireState.valid(packet.formula()) || !Double.isFinite(packet.from().lengthSqr()+packet.to().lengthSqr()) || !Float.isFinite(packet.radius()))return;
        if(packet.kind()==SpellVfx.ZONE || packet.kind()==SpellVfx.MINE)CUES.removeIf(c->c.packet.kind()==packet.kind() && c.packet.formula()==packet.formula() && c.packet.from().distanceToSqr(packet.from())<.01);
        if(CUES.size()>=512)CUES.removeFirst();CUES.add(new Cue(packet));
    }
    public static void tick(){
        var mc=Minecraft.getInstance();if(mc.level==null){clear();return;}
        CUES.removeIf(c->++c.age>Math.min(100,c.packet.ticks()));
        for(Cue cue:CUES){
            var p=cue.packet;if(p.from().distanceToSqr(mc.gameRenderer.getMainCamera().position())>128*128)continue;
            var formula=Formula.unpack(p.formula());
            if(fire(formula.key()))flames(mc,cue);
            if(cue.age%3!=0)continue;
            var elements=formula.elements();Element element=elements.get((cue.age/3)%elements.size());
            Vec3 point=p.from().lerp(p.to(),Math.min(1,cue.age/(double)Math.max(1,p.ticks())));
            ParticleOptions particle=switch(element){case FIRE->ParticleTypes.FLAME;case WATER->ParticleTypes.BUBBLE_POP;case COLD->ParticleTypes.SNOWFLAKE;case LIGHTNING->ParticleTypes.ELECTRIC_SPARK;case EARTH->ParticleTypes.CRIT;case LIFE->ParticleTypes.HAPPY_VILLAGER;case ARCANE->ParticleTypes.END_ROD;case DEATH->ParticleTypes.SOUL;};
            mc.level.addParticle(particle,point.x,point.y,point.z,0,.025,0);
        }
    }
    private static boolean fire(SpellFormula formula){return formula.fire()>0 && formula.death()==0 && formula.lightning()==0;}
    private static void flames(Minecraft mc,Cue cue){
        var p=cue.packet;var random=mc.level.getRandom();double progress=Math.min(1,cue.age/(double)Math.max(1,p.ticks()));
        double spread=Math.clamp(p.radius(),.15,6);
        int count=switch(p.kind()){case SpellVfx.BEAM,SpellVfx.ARC,SpellVfx.SPRAY->16;case SpellVfx.ORB->8;case SpellVfx.IMPACT,SpellVfx.ZONE->14;default->6;};
        count=(int)Math.round(count*Math.clamp(spread/2.5,1,3));
        for(int i=0;i<count;i++){
            Vec3 point=switch(p.kind()){
                case SpellVfx.BEAM,SpellVfx.ARC,SpellVfx.SPRAY->p.from().lerp(p.to(),random.nextDouble());
                case SpellVfx.ORB->p.from().lerp(p.to(),progress*random.nextDouble());
                case SpellVfx.IMPACT->{double angle=random.nextDouble()*Math.PI*2,reach=spread*(.2+progress)*Math.sqrt(random.nextDouble());
                    yield p.from().add(Math.cos(angle)*reach,random.nextDouble()*spread*.4,Math.sin(angle)*reach);}
                case SpellVfx.ZONE,SpellVfx.MINE,SpellVfx.TELEGRAPH->{double angle=random.nextDouble()*Math.PI*2,reach=spread*Math.sqrt(random.nextDouble());
                    yield p.from().add(Math.cos(angle)*reach,.1+random.nextDouble()*.5,Math.sin(angle)*reach);}
                default->p.from();
            };
            double jitter=p.kind()==SpellVfx.SPRAY||p.kind()==SpellVfx.BEAM||p.kind()==SpellVfx.ARC?Math.min(.35,spread*.3):.18;
            point=point.add((random.nextDouble()-.5)*jitter,(random.nextDouble()-.5)*jitter,(random.nextDouble()-.5)*jitter);
            mc.level.addParticle(random.nextInt(4)==0?ParticleTypes.SMALL_FLAME:ParticleTypes.FLAME,point.x,point.y,point.z,
                    (random.nextDouble()-.5)*.03,.02+random.nextDouble()*.03,(random.nextDouble()-.5)*.03);
        }
    }
    public static void render(PoseStack stack,SubmitNodeCollector collector,Vec3 camera,float partial){
        var mc=Minecraft.getInstance();
        for(Cue cue:CUES){
            var p=cue.packet;var formula=Formula.unpack(p.formula()).key();
            if(p.kind()!=SpellVfx.ORB || formula.earth()==0)continue;
            float progress=Math.clamp((cue.age+partial)/Math.max(1,p.ticks()),0,1);
            Vec3 center=p.from().lerp(p.to(),progress);float scale=(float)(Math.clamp(p.radius(),.05,16)*1.7);
            stack.pushPose();stack.translate(center.x,center.y,center.z);stack.scale(scale,scale,scale);stack.mulPose(Axis.YP.rotationDegrees(((mc.level==null?cue.age:mc.level.getGameTime())+partial)*18));stack.mulPose(Axis.XP.rotationDegrees(25));
            var item=formula.fire()>0?Items.MAGMA_BLOCK:formula.cold()>0?Items.PACKED_ICE:Items.COBBLESTONE;
            var state=new net.minecraft.client.renderer.item.ItemStackRenderState();
            mc.getItemModelResolver().updateForTopItem(state,new ItemStack(item),ItemDisplayContext.FIXED,mc.level,null,0);
            state.submit(stack,collector,net.minecraft.util.LightCoordsUtil.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,0);stack.popPose();
        }
        collector.submitCustomGeometry(stack,net.minecraft.client.renderer.rendertype.RenderTypes.lightning(),(pose,consumer)->strokes(pose,consumer,camera,partial));
    }
    private static void strokes(PoseStack.Pose pose,VertexConsumer consumer,Vec3 camera,float partial){
        for(Cue cue:CUES){
            var p=cue.packet;var formula=Formula.unpack(p.formula()).key();
            float progress=Math.clamp((cue.age+partial)/Math.max(1,p.ticks()),0,1),alpha=(1-progress)*.85f;
            int color=color(formula);double radius=Math.clamp(p.radius(),.05,16);Vec3 center=p.from();
            boolean flames=fire(formula);
            if(flames && (p.kind()==SpellVfx.BEAM || p.kind()==SpellVfx.ARC || p.kind()==SpellVfx.SPRAY))continue;
            switch(p.kind()){
                case SpellVfx.BEAM,SpellVfx.ARC -> {
                    Vec3 delta=p.to().subtract(p.from()),axis=delta.normalize().cross(new Vec3(0,1,0)).normalize();
                    int segments=Math.clamp((int)(delta.length()/1.2),2,96);Vec3 previous=p.from();
                    for(int i=1;i<=segments;i++){Vec3 next=p.from().lerp(p.to(),i/(double)segments);
                        if(i<segments && p.kind()==SpellVfx.ARC)next=next.add(axis.scale(Math.sin(i*13.7+cue.age*.9)*.35)).add(0,Math.cos(i*8.3)*.18,0);
                        stroke(pose,consumer,camera,previous,next,radius,color,alpha*.5f);stroke(pose,consumer,camera,previous,next,radius*.28,0xFFFFFF,alpha);previous=next;
                    }
                }
                case SpellVfx.SPRAY -> {
                    Vec3 direction=p.to().subtract(center),side=direction.normalize().cross(new Vec3(0,1,0)).normalize();
                    for(int j=-2;j<=2;j++)stroke(pose,consumer,camera,center,p.to().add(side.scale(j*radius*.35)),.06,color,alpha*.5f);
                }
                case SpellVfx.ORB -> {
                    alpha=.9f;
                    center=p.from().lerp(p.to(),progress);double orb=radius;
                    if(!flames){ring(pose,consumer,camera,center,orb,0,color,alpha,.05);ring(pose,consumer,camera,center,orb,1,color,alpha,.05);ring(pose,consumer,camera,center,orb,2,0xFFFFFF,alpha*.8f,.025);
                    stroke(pose,consumer,camera,p.from(),center,.09,color,alpha*.45f);}
                }
                case SpellVfx.ZONE,SpellVfx.MINE,SpellVfx.TELEGRAPH -> {
                    double pulse=1+Math.sin((cue.age+partial)*.35)*.025;
                    ring(pose,consumer,camera,center.add(0,.06,0),radius,0,color,.65f,.045);
                    ring(pose,consumer,camera,center.add(0,.08,0),radius*.92*pulse,0,color,.3f,.025);
                    if(p.kind()!=SpellVfx.ZONE){stroke(pose,consumer,camera,center.add(-.4,.1,0),center.add(.4,.1,0),.04,color,.9f);stroke(pose,consumer,camera,center.add(0,.1,-.4),center.add(0,.1,.4),.04,color,.9f);}
                    if(p.kind()==SpellVfx.TELEGRAPH)stroke(pose,consumer,camera,center,center.add(0,10,0),.045,color,alpha);
                    for(int i=0;i<8;i++){double angle=i*Math.PI/4;Vec3 foot=center.add(Math.cos(angle)*radius,.06,Math.sin(angle)*radius);stroke(pose,consumer,camera,foot,foot.add(0,.6,0),.025,color,.5f);}
                }
                case SpellVfx.IMPACT -> {
                    double wave=radius*(.2+progress);ring(pose,consumer,camera,center,wave,0,color,alpha,.09);ring(pose,consumer,camera,center,wave*.7,1,0xFFFFFF,alpha*.7f,.035);
                    for(int i=0;i<10;i++){double angle=i*Math.PI/5;Vec3 end=center.add(Math.cos(angle)*wave,(i%3)*wave*.35,Math.sin(angle)*wave);stroke(pose,consumer,camera,center.lerp(end,.55),end,.045,color,alpha);}
                }
                default -> {}
            }
        }
    }
    private static int color(SpellFormula formula){
        // Retain an elemental identity; mixed recipes show their other elements in the particle layer.
        Element element=formula.death()>0?Element.DEATH:formula.lightning()>0?Element.LIGHTNING:formula.fire()>0?Element.FIRE:formula.cold()>0?Element.COLD:formula.life()>0?Element.LIFE:formula.arcane()>0?Element.ARCANE:formula.water()>0?Element.WATER:Element.EARTH;
        return element.color;
    }
    private static void ring(PoseStack.Pose pose,VertexConsumer consumer,Vec3 camera,Vec3 center,double radius,int plane,int color,float alpha,double width){
        Vec3 previous=null;for(int i=0;i<=40;i++){double a=i*Math.PI/20,x=Math.cos(a)*radius,y=Math.sin(a)*radius;Vec3 point=center.add(plane==2?0:x,plane==0?0:y,plane==0?y:plane==2?x:0);if(previous!=null)stroke(pose,consumer,camera,previous,point,width,color,alpha);previous=point;}
    }
    private static void stroke(PoseStack.Pose pose,VertexConsumer out,Vec3 camera,Vec3 from,Vec3 to,double width,int color,float alpha){
        Vec3 side=to.subtract(from).cross(camera.subtract(from)).normalize().scale(width);if(side.lengthSqr()<1e-8)side=new Vec3(width,0,0);
        float r=((color>>16)&255)/255f,g=((color>>8)&255)/255f,b=(color&255)/255f;
        for(Vec3 v:new Vec3[]{from.subtract(side),from.add(side),to.add(side),to.subtract(side)})out.addVertex(pose,(float)v.x,(float)v.y,(float)v.z).setColor(r,g,b,alpha);
    }
    private SpellVisuals(){}
}
