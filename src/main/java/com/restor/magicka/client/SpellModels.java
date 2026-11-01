package com.restor.magicka.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.restor.magicka.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.*;
import net.minecraft.sounds.*;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SpeleothemThickness;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Per-spell scenery built from vanilla block models and textures, so every named zone reads at a glance. */
final class SpellModels {
    static boolean handles(int kind){return kind>=SpellVfx.GEYSER && kind<=SpellVfx.EMBERFALL;}
    static boolean merges(int kind){return handles(kind) && kind!=SpellVfx.LANCE && kind!=SpellVfx.CHAINS;}

    private static Random seed(MagicNetwork.Visual p){return new Random(p.formula()*0x9E3779B97F4A7C15L^Math.round(p.from().x*16)*31^Math.round(p.from().z*16)*131);}
    private static double ease(double t){t=Math.clamp(t,0,1);return 1-(1-t)*(1-t)*(1-t);}
    private static double presence(float age,int until,double in,double out){return Math.min(ease(age/in),ease((until-age)/out));}
    private static Vec3 center(Minecraft mc,MagicNetwork.Visual p,float partial){
        if(p.anchor()<0)return p.from();
        Entity e=mc.level.getEntity(p.anchor());return e==null?null:e.getPosition(partial);
    }
    private static SpellFormula formula(MagicNetwork.Visual p){return Formula.unpack(p.formula()).key();}

    static void tick(Minecraft mc,MagicNetwork.Visual p,int age,int until){
        var level=mc.level;var random=level.getRandom();Vec3 c=center(mc,p,1);if(c==null)return;
        double r=Math.clamp(p.radius(),.3,12);var f=formula(p);
        switch(p.kind()){
            case SpellVfx.GEYSER -> {
                double phase=(age%20)/20.0;
                if(phase<.45){double top=jet(age);
                    for(int i=0;i<6;i++){double a=random.nextDouble()*Math.PI*2,h=random.nextDouble()*top;
                        level.addParticle(ParticleTypes.SPLASH,c.x+Math.cos(a)*.3,c.y+h,c.z+Math.sin(a)*.3,Math.cos(a)*.2,.2,Math.sin(a)*.2);}
                    for(int i=0;i<4;i++){double a=random.nextDouble()*Math.PI*2;
                        level.addParticle(ParticleTypes.FALLING_WATER,c.x+Math.cos(a)*r*.6*random.nextDouble(),c.y+top,c.z+Math.sin(a)*r*.6*random.nextDouble(),0,0,0);}
                    level.addParticle(ParticleTypes.CLOUD,c.x,c.y+top,c.z,(random.nextDouble()-.5)*.2,.08,(random.nextDouble()-.5)*.2);
                    if(age%20==0)level.playLocalSound(c.x,c.y,c.z,SoundEvents.GENERIC_SPLASH,SoundSource.PLAYERS,.9f,.7f,false);
                }else if(random.nextInt(2)==0){
                    level.addParticle(ParticleTypes.CLOUD,c.x+(random.nextDouble()-.5)*.5,c.y+.3,c.z+(random.nextDouble()-.5)*.5,0,.05,0);
                    level.addParticle(ParticleTypes.BUBBLE_POP,c.x+(random.nextDouble()-.5)*.6,c.y+.4,c.z+(random.nextDouble()-.5)*.6,0,.02,0);
                }
            }
            case SpellVfx.PYRE -> {
                if(random.nextInt(2)==0)level.addParticle(ParticleTypes.FLAME,c.x+(random.nextDouble()-.5)*r,c.y+.2+random.nextDouble()*1.6,c.z+(random.nextDouble()-.5)*r,0,.04,0);
                if(random.nextInt(4)==0)level.addParticle(ParticleTypes.LARGE_SMOKE,c.x+(random.nextDouble()-.5)*.6,c.y+2.2,c.z+(random.nextDouble()-.5)*.6,0,.05,0);
                if(random.nextInt(12)==0)level.addParticle(ParticleTypes.LAVA,c.x,c.y+.3,c.z,0,0,0);
                if(age%30==0)level.playLocalSound(c.x,c.y,c.z,SoundEvents.FIRE_AMBIENT,SoundSource.PLAYERS,.6f,.9f+random.nextFloat()*.2f,false);
            }
            case SpellVfx.PYLON -> {
                if(random.nextInt(3)==0)level.addParticle(ParticleTypes.ELECTRIC_SPARK,c.x+(random.nextDouble()-.5)*.3,c.y+2.05,c.z+(random.nextDouble()-.5)*.3,(random.nextDouble()-.5)*.2,.1,(random.nextDouble()-.5)*.2);
            }
            case SpellVfx.STORM -> {
                double base=cloudBase(c);
                for(int i=0;i<3;i++){Vec3 q=inside(random,c,r);level.addParticle(ParticleTypes.FALLING_WATER,q.x,base-.2,q.z,0,0,0);}
                Vec3 strike=bolt(p,age);
                if(strike!=null && age%6==0){
                    for(int i=0;i<8;i++)level.addParticle(ParticleTypes.ELECTRIC_SPARK,strike.x,strike.y+.1,strike.z,(random.nextDouble()-.5)*.5,random.nextDouble()*.4,(random.nextDouble()-.5)*.5);
                    level.playLocalSound(strike.x,strike.y,strike.z,SoundEvents.LIGHTNING_BOLT_IMPACT,SoundSource.PLAYERS,.5f,1.4f+random.nextFloat()*.4f,false);
                }
            }
            case SpellVfx.MIST -> mist(mc,p,c,r,f,age);
            case SpellVfx.TOTEM -> {
                if(age==21)for(int i=0;i<12;i++){double a=i*Math.PI/6;level.addParticle(ParticleTypes.WAX_ON,c.x+Math.cos(a)*r*.5,c.y+.1,c.z+Math.sin(a)*r*.5,0,.05,0);}
                if(random.nextInt(4)!=0)return;
                ParticleOptions particle=f.total(Element.LIFE)>0?ParticleTypes.HAPPY_VILLAGER:f.total(Element.DEATH)>0?ParticleTypes.SOUL:ParticleTypes.SNOWFLAKE;
                level.addParticle(particle,c.x+(random.nextDouble()-.5)*.8,c.y+.5+random.nextDouble()*.5,c.z+(random.nextDouble()-.5)*.8,0,.02,0);
            }
            case SpellVfx.MIRROR -> {
                if(random.nextInt(3)==0){double a=random.nextDouble()*Math.PI*2,b=random.nextDouble()*Math.PI/2;
                    level.addParticle(ParticleTypes.END_ROD,c.x+Math.cos(a)*Math.cos(b)*r,c.y+Math.sin(b)*r,c.z+Math.sin(a)*Math.cos(b)*r,0,0,0);}
            }
            case SpellVfx.SPIRES -> {
                if(random.nextInt(2)!=0)return;Vec3 q=inside(random,c,r);
                ParticleOptions particle=crystal(f)?ParticleTypes.END_ROD:f.total(Element.DEATH)>0 && random.nextBoolean()?ParticleTypes.SOUL:ParticleTypes.SNOWFLAKE;
                level.addParticle(particle,q.x,q.y+.3+random.nextDouble()*1.5,q.z,0,.01,0);
            }
            case SpellVfx.BLOOM -> {
                if(random.nextInt(2)!=0)return;Vec3 q=inside(random,c,r);
                ParticleOptions particle=f.total(Element.FIRE)>0?ParticleTypes.SMALL_FLAME:f.total(Element.COLD)>0?ParticleTypes.SNOWFLAKE:f.total(Element.LIGHTNING)>0?ParticleTypes.ELECTRIC_SPARK:random.nextInt(3)==0?ParticleTypes.SPORE_BLOSSOM_AIR:ParticleTypes.HAPPY_VILLAGER;
                level.addParticle(particle,q.x,q.y+.3+random.nextDouble(),q.z,0,.02,0);
            }
            case SpellVfx.POOL -> {
                Vec3 q=inside(random,c,r*.9);
                if(f.total(Element.LIGHTNING)>0 && random.nextInt(2)==0)level.addParticle(ParticleTypes.ELECTRIC_SPARK,q.x,q.y+.15,q.z,(random.nextDouble()-.5)*.3,.05,(random.nextDouble()-.5)*.3);
                else if(random.nextInt(3)==0)level.addParticle(pool(f)==0?ParticleTypes.BUBBLE_POP:ParticleTypes.SNOWFLAKE,q.x,q.y+.12,q.z,0,.02,0);
                if(f.total(Element.LIFE)>0 && random.nextInt(4)==0)level.addParticle(ParticleTypes.HAPPY_VILLAGER,q.x,q.y+.4,q.z,0,.02,0);
            }
            case SpellVfx.VORTEX -> {
                Vec3 eye=c.add(0,2,0);
                for(int i=0;i<4;i++){Vec3 q=inside(random,c,r).add(0,random.nextDouble()*3,0);
                    level.addParticle(ParticleTypes.PORTAL,eye.x,eye.y,eye.z,q.x-eye.x,q.y-eye.y,q.z-eye.z);}
                if(random.nextInt(3)==0){Vec3 q=inside(random,c,r);level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK,Blocks.COBBLESTONE.defaultBlockState()),q.x,q.y+.1,q.z,(c.x-q.x)*.05,.2,(c.z-q.z)*.05);}
                if(age%40==0)level.playLocalSound(eye.x,eye.y,eye.z,SoundEvents.PORTAL_AMBIENT,SoundSource.PLAYERS,.6f,.5f,false);
            }
            case SpellVfx.EMBERFALL -> {
                for(int i=0;i<METEORS;i++){if((age+i*METEOR_STEP)%METEOR_TICKS!=METEOR_TICKS-1)continue;
                    Vec3 land=meteor(p,i,age)[1];
                    for(int k=0;k<10;k++)level.addParticle(random.nextBoolean()?ParticleTypes.LAVA:ParticleTypes.FLAME,land.x,land.y+.2,land.z,(random.nextDouble()-.5)*.3,random.nextDouble()*.3,(random.nextDouble()-.5)*.3);
                    level.addParticle(ParticleTypes.EXPLOSION,land.x,land.y+.5,land.z,0,0,0);
                    level.playLocalSound(land.x,land.y,land.z,SoundEvents.GENERIC_EXPLODE.value(),SoundSource.PLAYERS,.35f,1.3f+random.nextFloat()*.3f,false);
                }
                if(random.nextInt(2)==0){Vec3 q=inside(random,c,r);level.addParticle(ParticleTypes.SMOKE,q.x,q.y+.2,q.z,0,.03,0);}
            }
            case SpellVfx.LANCE -> level.addParticle(ParticleTypes.SNOWFLAKE,p.from().x,p.from().y,p.from().z,0,0,0);
            case SpellVfx.CHAINS -> {
                Entity e=mc.level.getEntity(p.anchor());if(e==null)return;
                if(random.nextInt(2)==0)level.addParticle(ParticleTypes.SNOWFLAKE,e.getX()+(random.nextDouble()-.5)*e.getBbWidth(),e.getY()+random.nextDouble()*e.getBbHeight(),e.getZ()+(random.nextDouble()-.5)*e.getBbWidth(),0,-.02,0);
                if(random.nextInt(5)==0)level.addParticle(ParticleTypes.SOUL,e.getX(),e.getY()+e.getBbHeight()*.5,e.getZ(),0,.03,0);
                if(age==0)level.playLocalSound(e.getX(),e.getY(),e.getZ(),SoundEvents.CHAIN_PLACE,SoundSource.PLAYERS,1f,.7f,false);
            }
            default -> {}
        }
    }
    private static void mist(Minecraft mc,MagicNetwork.Visual p,Vec3 c,double r,SpellFormula f,int age){
        var level=mc.level;var random=level.getRandom();
        boolean wall=f.total(Element.SHIELD)>0,healing=f.total(Element.LIFE)>0,poison=f.total(Element.DEATH)>0;
        int color=healing?0x9CF2A8:poison?0x6E8B2E:0xE8EEF2;
        var dust=new DustParticleOptions(ARGB.colorFromFloat(1f,((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f),wall?3.5f:3f);
        int count=(int)Math.ceil(r*(wall?1.5:1.2));
        for(int i=0;i<count;i++){Vec3 q=inside(random,c,r);double h=wall?random.nextDouble()*2.6:.15+random.nextDouble()*(healing?1.4:.9);
            level.addParticle(dust,q.x,q.y+h,q.z,0,0,0);}
        if(wall){Vec3 q=inside(random,c,r);level.addParticle(ParticleTypes.CLOUD,q.x,q.y+.2,q.z,0,.06+random.nextDouble()*.04,0);}
        if(healing && random.nextInt(3)==0){Vec3 q=inside(random,c,r*.8);level.addParticle(ParticleTypes.HAPPY_VILLAGER,q.x,q.y+.3+random.nextDouble(),q.z,0,.02,0);}
        if(healing && random.nextInt(12)==0){Vec3 q=inside(random,c,r*.6);level.addParticle(ParticleTypes.HEART,q.x,q.y+1,q.z,0,0,0);}
        if(poison){Vec3 q=inside(random,c,r);
            level.addParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT,0xFF3E6B1F),q.x,q.y+.2,q.z,0,0,0);
            if(random.nextInt(3)==0)level.addParticle(ParticleTypes.SNEEZE,q.x,q.y+.4,q.z,0,.01,0);
            if(age%40==0)level.playLocalSound(c.x,c.y,c.z,SoundEvents.BREWING_STAND_BREW,SoundSource.PLAYERS,.5f,.6f,false);}
    }

    static void strokes(Minecraft mc,PoseStack.Pose pose,VertexConsumer out,Vec3 camera,MagicNetwork.Visual p,float age,int until,float partial){
        Vec3 c=center(mc,p,partial);if(c==null)return;
        double r=Math.clamp(p.radius(),.3,12);var f=formula(p);
        switch(p.kind()){
            case SpellVfx.GEYSER -> {
                double top=jet(age)*presence(age,until,4,6);if(top<.05)return;
                float glow=(float)(.35+.35*Math.clamp(top/6,0,1));
                for(int k=0;k<4;k++){double a=k*Math.PI/2+age*.4,w=.18;
                    Vec3 from=c.add(Math.cos(a)*w,.05,Math.sin(a)*w);Vec3 previous=from;
                    for(int i=1;i<=8;i++){double t=i/8.0,s=Math.sin(age*.9+i*1.3+k)*.08*(1+t);
                        Vec3 next=c.add(Math.cos(a)*w*(1+t)+s,top*t,Math.sin(a)*w*(1+t)-s);
                        SpellVisuals.stroke(pose,out,camera,previous,next,.22*(1+t*.8),Element.WATER.color,glow*.45f);previous=next;}
                }
                SpellVisuals.stroke(pose,out,camera,c,c.add(0,top,0),.12,0xFFFFFF,glow*.6f);
            }
            case SpellVfx.STORM -> {
                Vec3 strike=bolt(p,(int)age);if(strike==null)return;
                double base=cloudBase(c);var random=new Random((long)((int)age/6)*7919+p.formula());
                Vec3 previous=new Vec3(strike.x+(random.nextDouble()-.5),base,strike.z+(random.nextDouble()-.5));int steps=7;
                float alpha=(float)(.9*(1-((age%6)/6.0)));
                for(int i=1;i<=steps;i++){double t=i/(double)steps;
                    Vec3 next=i==steps?strike:new Vec3(strike.x+(random.nextDouble()-.5)*.9,base+(strike.y-base)*t,strike.z+(random.nextDouble()-.5)*.9);
                    SpellVisuals.stroke(pose,out,camera,previous,next,.16,Element.LIGHTNING.color,alpha*.5f);SpellVisuals.stroke(pose,out,camera,previous,next,.05,0xFFFFFF,alpha);previous=next;}
            }
            case SpellVfx.MIRROR -> {
                float alpha=(float)(.55*presence(age,until,5,6));int color=Element.SHIELD.color;
                double spin=age*.03;
                for(int lat=0;lat<3;lat++){double b=lat*Math.PI/7,ring=Math.cos(b)*r,y=Math.sin(b)*r;Vec3 previous=null;
                    for(int i=0;i<=6;i++){double a=spin*(lat%2==0?1:-1)+i*Math.PI/3;Vec3 q=c.add(Math.cos(a)*ring,y+.05,Math.sin(a)*ring);
                        if(previous!=null)SpellVisuals.stroke(pose,out,camera,previous,q,.035,color,alpha);previous=q;}
                }
                for(int i=0;i<6;i++){double a=spin+i*Math.PI/3;Vec3 previous=null;
                    for(int lat=0;lat<=3;lat++){double b=Math.min(lat,2.6)*Math.PI/7,ring=Math.cos(b)*r;Vec3 q=c.add(Math.cos(a+(lat%2)*Math.PI/6)*ring,Math.sin(b)*r+.05,Math.sin(a+(lat%2)*Math.PI/6)*ring);
                        if(previous!=null)SpellVisuals.stroke(pose,out,camera,previous,q,.025,0xFFFFFF,alpha*.7f);previous=q;}
                }
            }
            case SpellVfx.POOL -> {
                if(f.total(Element.LIGHTNING)==0)return;
                double grow=presence(age,until,8,6);
                for(int k=0;k<2;k++){var random=new Random((long)((int)age/3)*104729+k*31+p.formula());
                    Vec3 a=inside(random,c,r*.85*grow),b=inside(random,c,r*.85*grow);Vec3 previous=a.add(0,.12,0);
                    for(int i=1;i<=5;i++){Vec3 next=a.lerp(b,i/5.0).add((random.nextDouble()-.5)*.4,.12+random.nextDouble()*.15,(random.nextDouble()-.5)*.4);
                        SpellVisuals.stroke(pose,out,camera,previous,next,.07,Element.LIGHTNING.color,.5f);SpellVisuals.stroke(pose,out,camera,previous,next,.025,0xFFFFFF,.9f);previous=next;}
                }
            }
            case SpellVfx.VORTEX -> {
                float alpha=(float)(.6*presence(age,until,10,8));Vec3 eye=c.add(0,2,0);
                for(int ring=0;ring<4;ring++){double rr=.9+ring*r*.22,spin=age*(.25-ring*.05);Vec3 previous=null;
                    for(int i=0;i<=32;i++){double a=spin+i*Math.PI/16;Vec3 q=eye.add(Math.cos(a)*rr,Math.sin(a*2+ring)*.15*ring,Math.sin(a)*rr);
                        if(previous!=null)SpellVisuals.stroke(pose,out,camera,previous,q,.05,ring%2==0?Element.ARCANE.color:Element.DEATH.color,alpha*(1-ring*.18f));previous=q;}
                }
            }
            case SpellVfx.EMBERFALL -> {
                float grow=(float)presence(age,until,4,4);
                for(int i=0;i<METEORS;i++){Vec3[] m=meteor(p,i,(int)age);double t=((age+i*METEOR_STEP)%METEOR_TICKS)/(double)METEOR_TICKS;
                    Vec3 at=m[0].lerp(m[1],t),tail=m[0].lerp(m[1],Math.max(0,t-.25));
                    SpellVisuals.stroke(pose,out,camera,tail,at,.35,Element.FIRE.color,.45f*grow);SpellVisuals.stroke(pose,out,camera,tail,at,.12,0xFFE08A,.8f*grow);}
            }
            case SpellVfx.LANCE -> {
                float progress=Math.clamp(age/Math.max(1,p.ticks()),0,1);Vec3 tip=p.from().lerp(p.to(),progress);
                SpellVisuals.stroke(pose,out,camera,p.from().lerp(tip,.2),tip,.05,Element.ICE.color,.5f);
            }
            default -> {}
        }
    }

    static void models(Minecraft mc,PoseStack pose,SubmitNodeCollector collector,MagicNetwork.Visual p,float age,int until,float partial){
        Vec3 c=center(mc,p,partial);if(c==null)return;
        double r=Math.clamp(p.radius(),.3,12);var f=formula(p);var random=seed(p);
        switch(p.kind()){
            case SpellVfx.GEYSER -> {
                double rise=presence(age,until,8,10);
                block(mc,pose,collector,Blocks.DRIPSTONE_BLOCK.defaultBlockState(),c.x,c.y-.75+.5*rise,c.z,1.3,.6,1.3,0,0,-1);
                int count=5+random.nextInt(2);
                for(int i=0;i<count;i++){double a=i*Math.PI*2/count+random.nextDouble()*.6,d=r*(.55+random.nextDouble()*.35);int h=1+random.nextInt(3);
                    double lift=h*(rise-1)-.1;
                    stalagmite(mc,pose,collector,c.x+Math.cos(a)*d,c.y+lift,c.z+Math.sin(a)*d,h,(float)Math.toDegrees(-a),8+random.nextInt(10));
                }
            }
            case SpellVfx.PYRE -> {
                double rise=presence(age,until,6,8),flicker=1+.12*Math.sin(age*.7+random.nextInt(9));
                block(mc,pose,collector,Blocks.MAGMA_BLOCK.defaultBlockState(),c.x,c.y-.2,c.z,r,.25,r,0,0,LightCoordsUtil.FULL_BRIGHT);
                block(mc,pose,collector,Blocks.FIRE.defaultBlockState(),c.x,c.y,c.z,r*1.1,2.2*rise*flicker,r*1.1,random.nextInt(90),0,LightCoordsUtil.FULL_BRIGHT);
                block(mc,pose,collector,Blocks.FIRE.defaultBlockState(),c.x,c.y,c.z,r*.8,1.5*rise*(2-flicker),r*.8,45+random.nextInt(90),0,LightCoordsUtil.FULL_BRIGHT);
            }
            case SpellVfx.PYLON -> {
                double rise=presence(age,until,6,8);
                block(mc,pose,collector,Blocks.CUT_COPPER.weathering().unaffected().defaultBlockState(),c.x,c.y-.35+.2*rise,c.z,.7,.35,.7,45,0,-1);
                block(mc,pose,collector,Blocks.LIGHTNING_ROD.weathering().unaffected().defaultBlockState(),c.x,c.y-2+2*rise,c.z,1.3,2,1.3,0,0,-1);
            }
            case SpellVfx.STORM -> {
                double grow=presence(age,until,10,12),base=cloudBase(c),drift=Math.sin(age*.05)*.15;
                int count=9+random.nextInt(4);
                for(int i=0;i<count;i++){Vec3 q=inside(random,c,r*.85);double w=1.4+random.nextDouble()*1.6,h=.7+random.nextDouble()*.8,d=1.4+random.nextDouble()*1.6;
                    BlockState state=(random.nextInt(3)==0?Blocks.CONCRETE_POWDER.lightGray():Blocks.CONCRETE_POWDER.gray()).defaultBlockState();
                    block(mc,pose,collector,state,q.x+drift,base+random.nextDouble()*.6,q.z,w*grow,h*grow,d*grow,random.nextInt(4)*22,0,-1);}
            }
            case SpellVfx.TOTEM -> {
                double rise=presence(age,until,6,4),bob=Math.sin(age*.15)*.04;
                if(f.total(Element.LIFE)>0)block(mc,pose,collector,Blocks.FLOWERING_AZALEA.defaultBlockState(),c.x,c.y-.6+.6*rise+bob,c.z,.7,.7,.7,age*.6f,0,-1);
                else if(f.total(Element.DEATH)>0)
                    SpellVisuals.submitItem(pose,collector,mc,new ItemStack(Items.WITHER_SKELETON_SKULL),new Vec3(c.x,c.y+.15*rise+bob,c.z),.9f,age*2,0);
                else if(f.total(Element.COLD)>0){
                    block(mc,pose,collector,Blocks.BLUE_ICE.defaultBlockState(),c.x,c.y-.45+.4*rise+bob,c.z,.55,.55,.55,45+age,35,-1);
                    for(int i=0;i<3;i++){double a=i*Math.PI*2/3;
                        block(mc,pose,collector,Blocks.PACKED_ICE.defaultBlockState(),c.x+Math.cos(a)*.45,c.y-.3+.25*rise,c.z+Math.sin(a)*.45,.2,.45,.2,(float)Math.toDegrees(-a),20,-1);}
                }else if(f.total(Element.STEAM)>0){
                    block(mc,pose,collector,Blocks.CAULDRON.defaultBlockState(),c.x,c.y-.75+.7*rise,c.z,.75,.75,.75,15,0,-1);
                    if(mc.level.getRandom().nextInt(3)==0)mc.level.addParticle(ParticleTypes.CLOUD,c.x,c.y+.1+.5*rise,c.z,0,.06,0);
                }else block(mc,pose,collector,Blocks.AMETHYST_CLUSTER.defaultBlockState(),c.x,c.y-.8+.8*rise+bob,c.z,1,1,1,age*2,0,LightCoordsUtil.FULL_BRIGHT);
            }
            case SpellVfx.MIRROR -> {
                double grow=presence(age,until,5,6);
                for(int i=0;i<6;i++){double a=age*.05+i*Math.PI/3;
                    block(mc,pose,collector,Blocks.STAINED_GLASS.lightBlue().defaultBlockState(),c.x+Math.cos(a)*r*.8,c.y+.6+.25*Math.sin(age*.1+i),c.z+Math.sin(a)*r*.8,.75*grow,1*grow,.06,(float)Math.toDegrees(-a)+90,0,LightCoordsUtil.FULL_BRIGHT);}
            }
            case SpellVfx.SPIRES -> {
                double grow=presence(age,until,8,8);int count=Math.clamp((int)(r*r*.8),6,24);boolean crystal=crystal(f),bone=f.total(Element.DEATH)>0;
                for(int i=0;i<count;i++){Vec3 q=inside(random,c,r*.95);double h=.6+random.nextDouble()*1.6,stagger=ease(grow*1.6-i/(double)count*.6);
                    float yaw=random.nextFloat()*360,tilt=random.nextFloat()*25;
                    if(crystal)block(mc,pose,collector,(random.nextInt(3)==0?Blocks.LARGE_AMETHYST_BUD:Blocks.AMETHYST_CLUSTER).defaultBlockState(),q.x,q.y-.1,q.z,1.2*stagger,(.8+h*.6)*stagger,1.2*stagger,yaw,tilt,LightCoordsUtil.FULL_BRIGHT);
                    else block(mc,pose,collector,(bone && i%3==0?Blocks.BONE_BLOCK:i%4==0?Blocks.BLUE_ICE:Blocks.PACKED_ICE).defaultBlockState(),q.x,q.y-.3,q.z,.3,h*stagger,.3,yaw,tilt,-1);
                }
            }
            case SpellVfx.BLOOM -> {
                double grow=presence(age,until,20,10);int count=Math.clamp((int)(r*r*1.3),8,40);var palette=palette(f);
                for(int i=0;i<count;i++){Vec3 q=inside(random,c,r*.95);BlockState state=random.nextInt(3)==0?Blocks.SHORT_GRASS.defaultBlockState():palette[random.nextInt(palette.length)];
                    double s=ease(grow*1.8-i/(double)count*.8);
                    block(mc,pose,collector,state,q.x,q.y,q.z,.9*s,.9*s,.9*s,random.nextFloat()*360,0,-1);}
                if(f.total(Element.DEATH)>0){int posts=Math.clamp((int)(r*2*Math.PI/1.5),8,40);
                    for(int i=0;i<posts;i++){double a=i*Math.PI*2/posts;block(mc,pose,collector,Blocks.OAK_FENCE.defaultBlockState(),c.x+Math.cos(a)*r,c.y-1+grow,c.z+Math.sin(a)*r,1,1,1,(float)Math.toDegrees(-a),0,-1);}}
            }
            case SpellVfx.POOL -> {
                double grow=presence(age,until,10,8);
                surface(mc,pose,collector,c,r*grow,pool(f),f);
                if(f.total(Element.LIFE)>0)for(int i=0;i<Math.clamp((int)(r*1.5),3,10);i++){Vec3 q=inside(random,c,r*.85*grow);
                    block(mc,pose,collector,Blocks.LILY_PAD.defaultBlockState(),q.x,q.y+.03+Math.sin(age*.08+i)*.015,q.z,1,1,1,random.nextFloat()*360,0,-1);}
            }
            case SpellVfx.VORTEX -> {
                double grow=presence(age,until,12,8);Vec3 eye=c.add(0,2,0);
                pose.pushPose();pose.translate(eye.x,eye.y,eye.z);pose.rotate(Axis.YP.rotationDegrees(age*7));pose.rotate(Axis.XP.rotationDegrees(age*5));
                float s=(float)(1.1*grow*(1+.08*Math.sin(age*.4)));
                piece(mc,pose,collector,Blocks.CRYING_OBSIDIAN.defaultBlockState(),0,-s/2,0,s,s,s,LightCoordsUtil.FULL_BRIGHT);pose.popPose();
                BlockState[] debris={Blocks.COBBLESTONE.defaultBlockState(),Blocks.DIRT.defaultBlockState(),Blocks.GRAVEL.defaultBlockState(),Blocks.STONE.defaultBlockState()};
                for(int i=0;i<14;i++){double t=(age*.006+i/14.0)%1,a=i*2.4+age*.05/(1.05-t)+t*8,d=r*(1-t),y=c.y+.3+t*1.7,size=.35*grow*(1-t*.6);
                    block(mc,pose,collector,debris[i%debris.length],c.x+Math.cos(a)*d,y,c.z+Math.sin(a)*d,size,size,size,age*9+i*40,age*7,-1);}
            }
            case SpellVfx.EMBERFALL -> {
                for(int i=0;i<METEORS;i++){Vec3[] m=meteor(p,i,(int)age);double t=((age+i*METEOR_STEP)%METEOR_TICKS)/(double)METEOR_TICKS;Vec3 at=m[0].lerp(m[1],t);
                    float s=(float)(.6*presence(age,until,4,4));
                    pose.pushPose();pose.translate(at.x,at.y,at.z);pose.rotate(Axis.YP.rotationDegrees(age*20+i*50));pose.rotate(Axis.XP.rotationDegrees(age*15));
                    piece(mc,pose,collector,Blocks.MAGMA_BLOCK.defaultBlockState(),0,-s/2,0,s,s,s,LightCoordsUtil.FULL_BRIGHT);pose.popPose();}
            }
            case SpellVfx.LANCE -> {
                float progress=Math.clamp(age/Math.max(1,p.ticks()),0,1);Vec3 at=p.from().lerp(p.to(),progress),d=p.to().subtract(p.from());
                if(d.lengthSqr()<1e-6)return;d=d.normalize();
                float yaw=(float)Math.toDegrees(Math.atan2(d.x,d.z)),pitch=(float)Math.toDegrees(-Math.asin(Math.clamp(d.y,-1,1)));
                pose.pushPose();pose.translate(at.x,at.y,at.z);pose.rotate(Axis.YP.rotationDegrees(yaw));pose.rotate(Axis.XP.rotationDegrees(pitch));
                pose.rotate(Axis.ZP.rotationDegrees(age*25));
                piece(mc,pose,collector,Blocks.BLUE_ICE.defaultBlockState(),0,-.11,-.4,.22,.22,1.4,light(mc,at));
                piece(mc,pose,collector,Blocks.PACKED_ICE.defaultBlockState(),0,-.065,.55,.13,.13,.5,light(mc,at));
                pose.popPose();
            }
            case SpellVfx.CHAINS -> {
                Entity e=mc.level.getEntity(p.anchor());if(e==null)return;
                double grip=presence(age,until,6,6);Vec3 feet=e.getPosition(partial);double w=e.getBbWidth(),h=e.getBbHeight();
                block(mc,pose,collector,Blocks.PACKED_ICE.defaultBlockState(),feet.x,feet.y-.25,feet.z,(w+.5)*grip,.4,(w+.5)*grip,15,0,-1);
                for(int i=0;i<4;i++){double a=i*Math.PI/2+Math.PI/4+random.nextDouble()*.3,d=w*.5+1.3;
                    Vec3 anchor=p.from().add(Math.cos(a)*d,0,Math.sin(a)*d),grab=feet.add(Math.cos(a)*w*.3,h*(.35+.15*(i%2)),Math.sin(a)*w*.3);
                    chain(mc,pose,collector,anchor,anchor.lerp(grab,grip));
                }
            }
            default -> {}
        }
    }
    private static double jet(double age){
        double phase=(age%20)/20.0;
        return phase<.12?.8+5.2*phase/.12:phase<.45?6-5.2*(phase-.12)/.33:.8;
    }
    private static double cloudBase(Vec3 c){return c.y+4.5;}
    private static final int METEORS=5,METEOR_TICKS=20,METEOR_STEP=4;
    private static Vec3[] meteor(MagicNetwork.Visual p,int index,int age){
        int wave=(age+index*METEOR_STEP)/METEOR_TICKS;var random=new Random(wave*7919L+index*104729L+p.formula());
        Vec3 land=inside(random,p.from(),Math.clamp(p.radius(),.3,12)*.9);
        return new Vec3[]{land.add((random.nextDouble()-.5)*6,14,(random.nextDouble()-.5)*6),land};
    }
    private static boolean crystal(SpellFormula f){return f.total(Element.ARCANE)>f.total(Element.COLD)+f.total(Element.ICE);}
    private static BlockState[] palette(SpellFormula f){
        Block[] blocks=f.total(Element.FIRE)>0?new Block[]{Blocks.TORCHFLOWER,Blocks.ORANGE_TULIP,Blocks.RED_TULIP,Blocks.POPPY}
                :f.total(Element.COLD)>0 || f.total(Element.ICE)>0?new Block[]{Blocks.BLUE_ORCHID,Blocks.LILY_OF_THE_VALLEY,Blocks.WHITE_TULIP,Blocks.AZURE_BLUET}
                :f.total(Element.LIGHTNING)>0?new Block[]{Blocks.DANDELION,Blocks.OXEYE_DAISY,Blocks.AZURE_BLUET}
                :f.total(Element.DEATH)>0?new Block[]{Blocks.DANDELION,Blocks.POPPY,Blocks.FERN,Blocks.WITHER_ROSE}
                :new Block[]{Blocks.POPPY,Blocks.DANDELION,Blocks.CORNFLOWER,Blocks.ALLIUM,Blocks.OXEYE_DAISY,Blocks.PINK_TULIP,Blocks.FERN};
        return Arrays.stream(blocks).map(Block::defaultBlockState).toArray(BlockState[]::new);
    }
    /** 0 water, 1 mud, 2 ice. */
    private static int pool(SpellFormula f){return f.total(Element.WATER)>0 || f.total(Element.STEAM)>0?0:f.total(Element.EARTH)>0?1:2;}
    private static void surface(Minecraft mc,PoseStack pose,SubmitNodeCollector collector,Vec3 c,double radius,int kind,SpellFormula f){
        if(radius<.3)return;
        var sprite=mc.getModelManager().getBlockStateModelSet().getParticleMaterial((kind==0?Blocks.WATER:kind==1?Blocks.MUD:Blocks.ICE).defaultBlockState()).sprite();
        int color=kind!=0?0xFFFFFF:f.total(Element.LIFE)>0?0x3FB8A0:f.total(Element.LIGHTNING)>0?0x5A7FE8:0x3F76E4;
        float red=((color>>16)&255)/255f,green=((color>>8)&255)/255f,blue=(color&255)/255f,alpha=kind==1?1f:.78f;
        collector.submitCustomGeometry(pose,RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS),(last,out)->{
            double y=c.y+.07;int span=(int)Math.ceil(radius);
            for(int ix=-span;ix<=span;ix++)for(int iz=-span;iz<=span;iz++){
                double x0=Math.floor(c.x)+ix,z0=Math.floor(c.z)+iz;if(Math.hypot(x0+.5-c.x,z0+.5-c.z)>radius)continue;
                int light=light(mc,new Vec3(x0+.5,y,z0+.5));
                float[][] corners={{(float)x0,(float)z0,sprite.getU0(),sprite.getV0()},{(float)x0,(float)(z0+1),sprite.getU0(),sprite.getV1()},{(float)(x0+1),(float)(z0+1),sprite.getU1(),sprite.getV1()},{(float)(x0+1),(float)z0,sprite.getU1(),sprite.getV0()}};
                for(float[] v:corners)out.addVertex(last,v[0],(float)y,v[1]).setColor(red,green,blue,alpha).setUv(v[2],v[3]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0,1,0);
            }
        });
    }
    private static Vec3 bolt(MagicNetwork.Visual p,int age){
        var random=new Random((long)(age/6)*7919+p.formula());if(random.nextInt(3)==0)return null;
        Vec3 c=p.from();double r=Math.clamp(p.radius(),.3,12);double a=random.nextDouble()*Math.PI*2,d=Math.sqrt(random.nextDouble())*r*.85;
        return c.add(Math.cos(a)*d,.05,Math.sin(a)*d);
    }
    private static Vec3 inside(net.minecraft.util.RandomSource random,Vec3 c,double radius){double a=random.nextDouble()*Math.PI*2,r=radius*Math.sqrt(random.nextDouble());return c.add(Math.cos(a)*r,0,Math.sin(a)*r);}
    private static Vec3 inside(Random random,Vec3 c,double radius){double a=random.nextDouble()*Math.PI*2,r=radius*Math.sqrt(random.nextDouble());return c.add(Math.cos(a)*r,0,Math.sin(a)*r);}
    private static int light(Minecraft mc,Vec3 at){
        if(mc.level==null)return LightCoordsUtil.FULL_BRIGHT;
        var pos=BlockPos.containing(at.x,at.y+.5,at.z);var engine=mc.level.getLightEngine();
        return LightCoordsUtil.pack(engine.getLayerListener(LightLayer.BLOCK).getLightValue(pos),engine.getLayerListener(LightLayer.SKY).getLightValue(pos));
    }

    private static void stalagmite(Minecraft mc,PoseStack pose,SubmitNodeCollector collector,double x,double y,double z,int height,float yaw,float tilt){
        SpeleothemThickness[] parts=height==1?new SpeleothemThickness[]{SpeleothemThickness.TIP}:height==2?new SpeleothemThickness[]{SpeleothemThickness.FRUSTUM,SpeleothemThickness.TIP}:new SpeleothemThickness[]{SpeleothemThickness.BASE,SpeleothemThickness.FRUSTUM,SpeleothemThickness.TIP};
        pose.pushPose();pose.translate(x,y,z);pose.rotate(Axis.YP.rotationDegrees(yaw));pose.rotate(Axis.ZP.rotationDegrees(tilt));pose.scale(1.6f,1,1.6f);
        int light=light(mc,new Vec3(x,y+height,z));
        for(int i=0;i<parts.length;i++){
            BlockState state=Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(PointedDripstoneBlock.TIP_DIRECTION,Direction.UP).setValue(PointedDripstoneBlock.THICKNESS,parts[i]);
            piece(mc,pose,collector,state,0,i,0,1,1,1,light);
        }
        pose.popPose();
    }
    private static void chain(Minecraft mc,PoseStack pose,SubmitNodeCollector collector,Vec3 from,Vec3 to){
        Vec3 d=to.subtract(from);double length=d.length();if(length<.05)return;d=d.scale(1/length);
        float yaw=(float)Math.toDegrees(Math.atan2(d.x,d.z)),pitch=(float)Math.toDegrees(Math.acos(Math.clamp(d.y,-1,1)));
        int links=Math.max(1,(int)Math.round(length));double each=length/links;int light=light(mc,from.lerp(to,.5));
        pose.pushPose();pose.translate(from.x,from.y,from.z);pose.rotate(Axis.YP.rotationDegrees(yaw));pose.rotate(Axis.XP.rotationDegrees(pitch));
        for(int i=0;i<links;i++)piece(mc,pose,collector,Blocks.IRON_CHAIN.defaultBlockState(),0,i*each,0,1.4,each,1.4,light);
        pose.popPose();
    }
    /** Draws a block model with its bottom centre at (x,y,z); light -1 samples the world. */
    private static void block(Minecraft mc,PoseStack pose,SubmitNodeCollector collector,BlockState state,double x,double y,double z,double sx,double sy,double sz,float yaw,float tilt,int light){
        if(sx<=.01 || sy<=.01 || sz<=.01)return;
        pose.pushPose();pose.translate(x,y,z);pose.rotate(Axis.YP.rotationDegrees(yaw));if(tilt!=0)pose.rotate(Axis.XP.rotationDegrees(tilt));
        piece(mc,pose,collector,state,0,0,0,sx,sy,sz,light<0?light(mc,new Vec3(x,y,z)):light);pose.popPose();
    }
    private static void piece(Minecraft mc,PoseStack pose,SubmitNodeCollector collector,BlockState state,double x,double y,double z,double sx,double sy,double sz,int light){
        pose.pushPose();pose.translate(x,y,z);pose.scale((float)sx,(float)sy,(float)sz);pose.translate(-.5,0,-.5);
        var renderState=new BlockModelRenderState();
        mc.getBlockModelResolver().update(renderState,state,BlockDisplayContext.create());
        renderState.submitMultiLayer(pose,collector,light,OverlayTexture.NO_OVERLAY,0);
        pose.popPose();
    }
    private SpellModels(){}
}
