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
    private static final class Cue {MagicNetwork.Visual packet;int age,until;Cue(MagicNetwork.Visual packet){this.packet=packet;until=Math.min(100,packet.ticks());}}
    public static void clear(){CUES.clear();}
    public static void receive(MagicNetwork.Visual packet){
        if(packet.kind()<0 || packet.kind()>SpellVfx.SIGIL || !GrimoireState.valid(packet.formula()) || !Double.isFinite(packet.from().lengthSqr()+packet.to().lengthSqr()) || !Float.isFinite(packet.radius()))return;
        if(packet.kind()==SpellVfx.ZONE || packet.kind()==SpellVfx.MINE || packet.kind()==SpellVfx.SIGIL)
            for(Cue c:CUES)if(c.packet.kind()==packet.kind() && c.packet.formula()==packet.formula() && c.packet.anchor()==packet.anchor() && c.packet.from().distanceToSqr(packet.from())<.04){
                c.packet=packet;c.until=Math.max(c.until,c.age+Math.min(100,packet.ticks()));return;
            }
        if(CUES.size()>=512)CUES.removeFirst();CUES.add(new Cue(packet));
    }
    public static void tick(){
        var mc=Minecraft.getInstance();if(mc.level==null){clear();return;}
        CUES.removeIf(c->++c.age>c.until || c.packet.anchor()>=0 && mc.level.getEntity(c.packet.anchor())==null);
        for(Cue cue:CUES){
            var p=cue.packet;if(p.kind()==SpellVfx.SIGIL || p.from().distanceToSqr(mc.gameRenderer.mainCamera().position())>128*128)continue;
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
                    float in=Math.min(1,(cue.age+partial)/4);
                    sigil(pose,consumer,camera,center.add(0,.07,0),new Vec3(0,1,0),radius,spin(partial,radius),p.formula(),.75f*in);
                    if(p.kind()==SpellVfx.TELEGRAPH)stroke(pose,consumer,camera,center,center.add(0,10,0),.045,color,alpha);
                }
                case SpellVfx.IMPACT -> {
                    double wave=radius*(.2+progress);ring(pose,consumer,camera,center,wave,0,color,alpha,.09);ring(pose,consumer,camera,center,wave*.7,1,0xFFFFFF,alpha*.7f,.035);
                    for(int i=0;i<10;i++){double angle=i*Math.PI/5;Vec3 end=center.add(Math.cos(angle)*wave,(i%3)*wave*.35,Math.sin(angle)*wave);stroke(pose,consumer,camera,center.lerp(end,.55),end,.045,color,alpha);}
                }
                case SpellVfx.SIGIL -> {
                    float age=cue.age+partial,fade=.9f*Math.min(1,age/4)*Math.clamp((cue.until-age)/4,0,1);
                    Vec3 normal=p.to().subtract(center);
                    if(p.anchor()>=0){
                        var owner=Minecraft.getInstance().level.getEntity(p.anchor());if(owner==null)continue;
                        Vec3 forward=owner.getViewVector(partial),right=forward.cross(new Vec3(0,1,0));right=right.lengthSqr()<1e-4?new Vec3(1,0,0):right.normalize();Vec3 up=right.cross(forward);
                        center=owner.getEyePosition(partial).add(forward.scale(p.from().x)).add(up.scale(p.from().y)).add(right.scale(p.from().z));
                        normal=forward.scale(p.to().x).add(up.scale(p.to().y)).add(right.scale(p.to().z));
                    }
                    sigil(pose,consumer,camera,center,normal,radius*Math.min(1,.6+age*.1),spin(partial,radius),p.formula(),fade);
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
    private static final double[][] GLYPHS={
        {-.6,-1,0,1, 0,1,.6,-1, -.6,-1,.6,-1},
        {-.6,-1,0,.6, 0,.6,.6,-1, 0,1,0,-.4},
        {-.5,-1,.5,-.3, .5,-.3,-.5,.3, -.5,.3,.5,1},
        {-.6,.3,-.2,.6, -.2,.6,.2,.3, .2,.3,.6,.6, -.6,-.5,-.2,-.2, -.2,-.2,.2,-.5, .2,-.5,.6,-.2},
        {-.6,1,.6,1, -.6,1,0,-1, .6,1,0,-1},
        {-.6,1,-.6,-.6, -.6,-.6,.6,-.6, .6,-.6,.6,1, 0,.3,0,-1},
        {0,-1,0,1, -.6,-.5,.6,.5, -.6,.5,.6,-.5},
        {0,-1,0,1, -.5,0,.5,0, -.3,.7,.3,.7, -.3,-.7,.3,-.7},
        {0,1,.5,0, .5,0,0,-1, 0,-1,-.5,0, -.5,0,0,1},
        {.3,1,-.4,.1, -.4,.1,.4,-.1, .4,-.1,-.3,-1},
        {-.6,-1,-.6,1, -.6,1,.6,-1, .6,-1,.6,1},
        {-.5,1,.5,.3, .5,.3,-.5,-.3, -.5,-.3,.5,-1, .5,-1,.1,-1},
        {-.5,-.8,.5,-.8, .5,-.8,.5,.8, .5,.8,-.5,.8, -.5,.8,-.5,-.8},
        {-.6,-1,0,1, 0,1,.6,-1, -.3,0,.3,0},
        {-.5,-1,-.5,1, -.5,1,.5,1, -.5,0,.3,0, -.5,-1,.5,-1},
        {0,-1,0,1, 0,.2,-.5,.8, 0,.2,.5,.8, 0,-.4,-.4,0},
        {0,-1,0,.3, -.5,.3,.5,.3, 0,.3,-.3,.8, -.3,.8,0,1, 0,1,.3,.8, .3,.8,0,.3},
        {-.5,1,0,0, .5,1,0,0, 0,0,0,-1, -.3,-.6,.3,-.6},
        {-.6,0,0,.6, 0,.6,.6,0, .6,0,0,-.6, 0,-.6,-.6,0, 0,.15,0,-.15},
        {-.5,1,.5,1, .5,1,-.5,-1, -.5,-1,.5,-1, .5,-1,-.5,1},
        {0,-1,0,1, -.6,0,.6,0, -.4,.6,.4,.6},
        {0,-1,0,1, -.4,-.4,.4,-.4},
        {-.6,-1,.6,1, .6,-1,-.6,1, -.6,1,.6,1},
        {.4,1,-.3,.6, -.3,.6,-.5,0, -.5,0,-.3,-.6, -.3,-.6,.4,-1}
    };
    private static final double[] SEPARATOR={0,.3,.15,0, .15,0,0,-.3, 0,-.3,-.15,0, -.15,0,0,.3};
    private static double spin(float partial,double radius){
        var level=Minecraft.getInstance().level;
        return ((level==null?0:level.getGameTime())+partial)*.05/Math.max(1,radius*.4);
    }
    private static int tint(int color){return ((((color>>16)&255)+255)/2<<16)|((((color>>8)&255)+255)/2<<8)|(((color&255)+255)/2);}
    /** Every formula seeds its own rune: a word of its element glyphs around the rim, a star and an inner ring. */
    private static void sigil(PoseStack.Pose pose,VertexConsumer consumer,Vec3 camera,Vec3 center,Vec3 normal,double radius,double spin,int packed,float alpha){
        if(alpha<=.01 || normal.lengthSqr()<1e-6)return;
        var formula=Formula.unpack(packed);var elements=formula.elements();if(elements.isEmpty())return;
        var distinct=elements.stream().distinct().toList();int color=color(formula.key());
        var random=new Random(packed*0x9E3779B97F4A7C15L+0x632BE5AB);
        Vec3 n=normal.normalize(),u=(Math.abs(n.y)>.95?new Vec3(1,0,0):new Vec3(0,1,0)).cross(n).normalize(),v=n.cross(u);
        double line=Math.clamp(radius*.02,.012,.08);
        int[] word=new int[Math.clamp(elements.size()*2,4,10)];
        for(int i=0;i<word.length;i++)word[i]=elements.get(i%elements.size()).ordinal()*3+random.nextInt(3);
        int points=new int[]{3,4,5,6,7,8}[(distinct.size()+random.nextInt(4))%6],step=points<5?1:points==6?2:(points-1)/2;
        boolean doubled=random.nextBoolean(),inner=random.nextBoolean();
        arc(pose,consumer,camera,center,u,v,radius,line*1.6,color,alpha);
        arc(pose,consumer,camera,center,u,v,radius*.8,line,color,alpha);
        if(doubled)arc(pose,consumer,camera,center,u,v,radius*.76,line*.6,color,alpha*.7f);
        double size=radius*.07;int slots=word.length+1,count=Math.max(1,(int)Math.round(2*Math.PI*.9/(.07*2.7)/slots))*slots;
        for(int k=0;k<count;k++){
            int slot=k%slots;double angle=spin+k*Math.PI*2/count;
            if(slot==word.length)glyph(pose,consumer,camera,center,u,v,angle,radius*.9,size,SEPARATOR,line*.7,color,alpha);
            else glyph(pose,consumer,camera,center,u,v,angle,radius*.9,size,GLYPHS[word[slot]],line*.7,tint(Element.values()[word[slot]/3].color),alpha);
        }
        double star=inner?radius*.72:radius*.8;
        for(int i=0;i<points;i++){
            double a=-spin*1.5+i*Math.PI*2/points,b=-spin*1.5+(i+step)*Math.PI*2/points;
            stroke(pose,consumer,camera,center.add(u.scale(Math.cos(a)*star)).add(v.scale(Math.sin(a)*star)),center.add(u.scale(Math.cos(b)*star)).add(v.scale(Math.sin(b)*star)),line,color,alpha*.8f);
        }
        if(points==4)for(int i=0;i<4;i++){
            double a=-spin*1.5+Math.PI/4+i*Math.PI/2,b=a+Math.PI/2;
            stroke(pose,consumer,camera,center.add(u.scale(Math.cos(a)*star)).add(v.scale(Math.sin(a)*star)),center.add(u.scale(Math.cos(b)*star)).add(v.scale(Math.sin(b)*star)),line,color,alpha*.8f);
        }
        if(inner)arc(pose,consumer,camera,center,u,v,star,line*.8,color,alpha*.6f);
        double core=radius*.34;
        arc(pose,consumer,camera,center,u,v,core,line,0xFFFFFF,alpha*.8f);
        if(radius>.5)for(int i=0;i<distinct.size();i++){
            Element e=distinct.get(i);
            glyph(pose,consumer,camera,center,u,v,-spin*2+i*Math.PI*2/distinct.size(),core*.55,core*.28,GLYPHS[e.ordinal()*3+((packed>>>i)&3)%3],line*.7,tint(e.color),alpha);
        }
    }
    private static void glyph(PoseStack.Pose pose,VertexConsumer consumer,Vec3 camera,Vec3 center,Vec3 u,Vec3 v,double angle,double distance,double size,double[] g,double width,int color,float alpha){
        Vec3 r=u.scale(Math.cos(angle)).add(v.scale(Math.sin(angle))),t=u.scale(-Math.sin(angle)).add(v.scale(Math.cos(angle))),base=center.add(r.scale(distance));
        for(int i=0;i+3<g.length;i+=4)stroke(pose,consumer,camera,base.add(t.scale(g[i]*size)).add(r.scale(g[i+1]*size)),base.add(t.scale(g[i+2]*size)).add(r.scale(g[i+3]*size)),width,color,alpha);
    }
    private static void arc(PoseStack.Pose pose,VertexConsumer consumer,Vec3 camera,Vec3 center,Vec3 u,Vec3 v,double radius,double width,int color,float alpha){
        int steps=Math.clamp((int)(radius*16),24,96);Vec3 previous=null;
        for(int i=0;i<=steps;i++){double a=i*Math.PI*2/steps;Vec3 point=center.add(u.scale(Math.cos(a)*radius)).add(v.scale(Math.sin(a)*radius));if(previous!=null)stroke(pose,consumer,camera,previous,point,width,color,alpha);previous=point;}
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
