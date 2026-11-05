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
    private static final class FlowTube {double distance,previousDistance,speed,length,radius,offsetU,offsetV;int color;float alpha;}
    private static final class Cue {
        MagicNetwork.Visual packet;Vec3 previousFrom,previousTo,beamTip,beamTarget,beamDirection,beamTurnVelocity=Vec3.ZERO;double beamDistance,beamFlowDistance;final List<FlowTube> flowTubes=new ArrayList<>();int age,until;
        Cue(MagicNetwork.Visual packet){this.packet=packet;previousFrom=packet.from();previousTo=packet.to();beamTip=beamTarget=packet.to();Vec3 ray=packet.to().subtract(packet.from());beamDistance=ray.length();beamFlowDistance=visibleBeamLength(beamDistance,packet.formula());beamDirection=ray.lengthSqr()<1e-8?new Vec3(0,0,1):ray.normalize();until=Math.min(100,packet.ticks());if(packet.kind()==SpellVfx.BEAM)ensureFlowTubes(this,beamFlowDistance,packet.radius(),packet.formula(),packet.anchor());}
        Vec3 from(float partial){return stream(packet.kind())?previousFrom.lerp(packet.from(),Math.clamp((age+partial)/5,0,1)):packet.from();}
        Vec3 to(float partial){return packet.kind()==SpellVfx.BEAM?previousTo.lerp(beamTip,partial):stream(packet.kind())?previousTo.lerp(packet.to(),Math.clamp((age+partial)/5,0,1)):packet.to();}
    }
    public static void clear(){CUES.clear();}
    private static boolean stream(int kind){return kind==SpellVfx.BEAM || kind==SpellVfx.ARC || kind==SpellVfx.SPRAY;}
    private static boolean primaryRay(MagicNetwork.Visual packet){
        var level=Minecraft.getInstance().level;if(packet.anchor()<0 || level==null)return false;
        var source=level.getEntity(packet.anchor());return source!=null && packet.from().distanceToSqr(source.getEyePosition())<2.25;
    }
    public static void receive(MagicNetwork.Visual packet){
        if(packet.kind()<0 || packet.kind()>SpellVfx.EMBERFALL || !GrimoireState.valid(packet.formula()) || !Double.isFinite(packet.from().lengthSqr()+packet.to().lengthSqr()) || !Float.isFinite(packet.radius()))return;
        if(packet.kind()==SpellVfx.BEAM && packet.anchor()>=0){
            Cue existing=null;
            for(var it=CUES.iterator();it.hasNext();){Cue c=it.next();if(c.packet.kind()!=SpellVfx.BEAM || c.packet.anchor()!=packet.anchor())continue;if(existing==null)existing=c;else it.remove();}
            if(existing!=null){existing.previousFrom=existing.from(0);existing.beamTarget=packet.to();existing.packet=packet;ensureFlowTubes(existing,visibleBeamLength(packet.from().distanceTo(packet.to()),packet.formula()),packet.radius(),packet.formula(),packet.anchor());existing.age=0;existing.until=Math.min(100,packet.ticks());return;}
        }
        boolean model=SpellModels.merges(packet.kind());
        boolean stream=stream(packet.kind());
        boolean primary=stream && primaryRay(packet);
        boolean endpoint=packet.kind()==SpellVfx.IMPACT && packet.anchor()>=0;
        if(packet.kind()==SpellVfx.ZONE || packet.kind()==SpellVfx.MINE || packet.kind()==SpellVfx.SIGIL || model || stream || endpoint)
            for(Cue c:CUES)if(c.packet.kind()==packet.kind() && c.packet.formula()==packet.formula() && c.packet.anchor()==packet.anchor() && (packet.anchor()>=0 && model || c.packet.from().distanceToSqr(packet.from())<.04 || primary && primaryRay(c.packet))){
                if(stream){c.previousFrom=c.from(0);if(packet.kind()==SpellVfx.BEAM)c.beamTarget=packet.to();else c.previousTo=c.to(0);}
                c.packet=packet;
                if(stream || endpoint){c.age=0;c.until=Math.min(100,packet.ticks());}
                else c.until=model?c.age+Math.min(100,packet.ticks()):Math.max(c.until,c.age+Math.min(100,packet.ticks()));
                return;
            }
        if(CUES.size()>=512)CUES.removeFirst();CUES.add(new Cue(packet));
    }
    public static void tick(){
        var mc=Minecraft.getInstance();if(mc.level==null){clear();return;}
        CUES.removeIf(c->++c.age>c.until || c.packet.anchor()>=0 && mc.level.getEntity(c.packet.anchor())==null);
        for(Cue cue:CUES){
            var p=cue.packet;if(p.kind()==SpellVfx.BEAM){cue.previousTo=cue.beamTip;Vec3 target=cue.beamTarget.subtract(p.from());double distance=target.length();if(distance>1e-4){cue.beamDirection=follow(cue.beamDirection,target.scale(1/distance),cue);cue.beamDistance=approach(cue.beamDistance,distance,1.5);cue.beamFlowDistance=visibleBeamLength(cue.beamDistance,p.formula());cue.beamTip=p.from().add(cue.beamDirection.scale(cue.beamDistance));ensureFlowTubes(cue,cue.beamFlowDistance,p.radius(),p.formula(),p.anchor());for(FlowTube tube:cue.flowTubes){tube.previousDistance=tube.distance;double next=tube.distance+tube.speed;if(next>=cue.beamFlowDistance){tube.distance=next%cue.beamFlowDistance;tube.previousDistance=tube.distance;}else tube.distance=next;}}}
            boolean ray=p.kind()==SpellVfx.BEAM || p.kind()==SpellVfx.ARC || p.kind()==SpellVfx.SPRAY;
            Vec3 camera=mc.gameRenderer.mainCamera().position();
            if(p.kind()==SpellVfx.SIGIL || p.from().distanceToSqr(camera)>128*128 && (!ray || p.to().distanceToSqr(camera)>128*128))continue;
            var formula=Formula.unpack(p.formula());var blend=ElementBlend.of(formula.key());
            if(formula.key().water()==1 && formula.key().life()==1 && formula.key().arcane()==1){mist(mc,cue);continue;}
            compounds(mc,cue,blend);
            if(fire(blend) && !ray)flames(mc,cue);
            if(p.kind()==SpellVfx.ZONE)ambience(mc,cue,formula.key(),blend);
            if(SpellModels.handles(p.kind())){SpellModels.tick(mc,p,cue.age,cue.until);continue;}
            if(cue.age%3!=0 || ray)continue;
            var elements=remaining(blend);if(elements.isEmpty())continue;Element element=elements.get((cue.age/3)%elements.size());
            double progress=ray?(mc.level.getGameTime()+cue.age)%8/8.0:Math.min(1,cue.age/(double)Math.max(1,p.ticks()));
            Vec3 point=p.from().lerp(p.to(),progress);
            ParticleOptions particle=switch(element){case FIRE->ParticleTypes.FLAME;case WATER->ParticleTypes.BUBBLE_POP;case COLD->ParticleTypes.SNOWFLAKE;case LIGHTNING->ParticleTypes.ELECTRIC_SPARK;case EARTH->ParticleTypes.CRIT;case LIFE->ParticleTypes.HAPPY_VILLAGER;case ARCANE->ParticleTypes.END_ROD;case DEATH->ParticleTypes.SOUL;case STEAM->ParticleTypes.CLOUD;case ICE->ParticleTypes.ITEM_SNOWBALL;case SHIELD->ParticleTypes.WAX_ON;case MAGMA->ParticleTypes.LAVA;};
            mc.level.addParticle(particle,point.x,point.y,point.z,0,.025,0);
        }
    }
    private static void mist(Minecraft mc,Cue cue){var random=mc.level.getRandom();var c=cue.packet.from();double radius=Math.clamp(cue.packet.radius(),.5,12);for(int i=0;i<12;i++){Vec3 q=inside(random,c,radius*.95);mc.level.addParticle(ParticleTypes.CLOUD,q.x,q.y+.12+random.nextDouble()*1.25,q.z,(random.nextDouble()-.5)*.018,.008+random.nextDouble()*.012,(random.nextDouble()-.5)*.018);}}
    private static List<Element> remaining(ElementBlend blend){var elements=new ArrayList<Element>();for(Element element:Element.values())for(int i=0;i<blend.count(element);i++)elements.add(element);return elements;}
    private static void compounds(Minecraft mc,Cue cue,ElementBlend blend){
        if(blend.steam()+blend.ice()+blend.shield()+blend.magma()==0)return;
        var p=cue.packet;var random=mc.level.getRandom();double progress=Math.min(1,cue.age/(double)Math.max(1,p.ticks())),spread=Math.clamp(p.radius(),.15,12);
        Vec3 point=switch(p.kind()){
            case SpellVfx.BEAM,SpellVfx.ARC,SpellVfx.SPRAY->p.from().lerp(p.to(),random.nextDouble());
            case SpellVfx.ORB->p.from().lerp(p.to(),progress);
            case SpellVfx.IMPACT,SpellVfx.ZONE,SpellVfx.MINE,SpellVfx.TELEGRAPH->{double a=random.nextDouble()*Math.PI*2,r=spread*Math.sqrt(random.nextDouble());yield p.from().add(Math.cos(a)*r,.15+random.nextDouble()*.5,Math.sin(a)*r);}
            default->p.from();
        };
        for(int i=0;i<blend.steam()*2;i++)mc.level.addParticle(ParticleTypes.CLOUD,point.x+(random.nextDouble()-.5)*.28,point.y+(random.nextDouble()-.5)*.18,point.z+(random.nextDouble()-.5)*.28,(random.nextDouble()-.5)*.025,.018+random.nextDouble()*.025,(random.nextDouble()-.5)*.025);
        for(int i=0;i<blend.ice();i++){ParticleOptions crystal=(cue.age+i)%2==0?new BlockParticleOption(ParticleTypes.BLOCK,net.minecraft.world.level.block.Blocks.PACKED_ICE.defaultBlockState()):ParticleTypes.SNOWFLAKE;mc.level.addParticle(crystal,point.x+(random.nextDouble()-.5)*.22,point.y+(random.nextDouble()-.5)*.22,point.z+(random.nextDouble()-.5)*.22,(random.nextDouble()-.5)*.04,.01,(random.nextDouble()-.5)*.04);}
        for(int i=0;i<blend.shield();i++){double a=cue.age*.35+i*Math.PI*2/Math.max(1,blend.shield()),r=.25+spread*.12;mc.level.addParticle(i%2==0?ParticleTypes.WAX_ON:ParticleTypes.END_ROD,point.x+Math.cos(a)*r,point.y+.15+Math.sin(a*.7)*.18,point.z+Math.sin(a)*r,0,.015,0);}
        for(int i=0;i<blend.magma();i++)mc.level.addParticle(i%2==0?ParticleTypes.LAVA:ParticleTypes.SMALL_FLAME,point.x+(random.nextDouble()-.5)*.24,point.y+(random.nextDouble()-.5)*.2,point.z+(random.nextDouble()-.5)*.24,0,.03,0);
    }
    private static void ambience(Minecraft mc,Cue cue,SpellFormula formula,ElementBlend f){
        var random=mc.level.getRandom();var c=cue.packet.from();
        double radius=Math.clamp(cue.packet.radius(),.5,12),time=mc.level.getGameTime();
        if(formula.arcane()==3 && formula.cold()==1 && formula.death()==1){singularityParticles(mc,c,time);return;}
        int total=Math.max(1,f.size()),rim=Math.clamp((int)(radius*2.5),6,28);
        if(f.steam()>0)for(int i=0;i<Math.max(1,rim*f.steam()/total);i++){Vec3 q=inside(random,c,radius);mc.level.addParticle(ParticleTypes.CLOUD,q.x,q.y+.15+random.nextDouble()*1.2,q.z,(random.nextDouble()-.5)*.02,.015+random.nextDouble()*.02,(random.nextDouble()-.5)*.02);}
        if(f.ice()>0)for(int i=0;i<Math.max(1,rim*f.ice()/total);i++){Vec3 q=inside(random,c,radius);ParticleOptions crystal=i%3==0?new BlockParticleOption(ParticleTypes.BLOCK,net.minecraft.world.level.block.Blocks.PACKED_ICE.defaultBlockState()):ParticleTypes.SNOWFLAKE;mc.level.addParticle(crystal,q.x,q.y+.4+random.nextDouble()*1.8,q.z,(random.nextDouble()-.5)*.03,-.015,(random.nextDouble()-.5)*.03);}
        if(f.shield()>0)for(int i=0;i<Math.max(1,rim*f.shield()/total);i++){double a=time*.12+i*Math.PI*2/Math.max(1,rim*f.shield()/total);mc.level.addParticle(i%2==0?ParticleTypes.WAX_ON:ParticleTypes.END_ROD,c.x+Math.cos(a)*radius,c.y+.35+.18*Math.sin(time*.2+i),c.z+Math.sin(a)*radius,-Math.sin(a)*.025,.01,Math.cos(a)*.025);}
        if(f.magma()>0)for(int i=0;i<Math.max(1,rim*f.magma()/total);i++){Vec3 q=inside(random,c,radius);mc.level.addParticle(i%3==0?ParticleTypes.LAVA:ParticleTypes.SMALL_FLAME,q.x,q.y+.1,q.z,0,.02,0);}
        for(Element e:Element.BASE){
            int n=f.count(e);if(n==0)continue;
            int count=Math.max(2,rim*n/total);double share=n/(double)total;
            switch(e){
                case FIRE -> {
                    for(int k=0;k<count;k++){double a=time*.14+k*Math.PI*2/count,h=.35+.3*Math.sin(time*.25+k*1.7),r=radius*(.92+.06*Math.sin(time*.3+k));
                        mc.level.addParticle(k%3==0?ParticleTypes.SMALL_FLAME:ParticleTypes.FLAME,c.x+Math.cos(a)*r,c.y+h,c.z+Math.sin(a)*r,-Math.sin(a)*.04,.01,Math.cos(a)*.04);}
                    if(random.nextInt(3)==0){Vec3 q=inside(random,c,radius*.9);mc.level.addParticle(ParticleTypes.LAVA,q.x,q.y+.1,q.z,0,0,0);}
                    if(n>=3)for(int i=0;i<Math.max(2,(int)(radius*.6*share*2));i++){Vec3 q=inside(random,c,radius);
                        mc.level.addParticle(random.nextBoolean()?ParticleTypes.FLAME:ParticleTypes.SMALL_FLAME,q.x,q.y+5+random.nextDouble()*3,q.z,(random.nextDouble()-.5)*.05,-.45-random.nextDouble()*.2,(random.nextDouble()-.5)*.05);}
                }
                case LIGHTNING -> {
                    if(random.nextDouble()<.35+.5*share){Vec3 q=random.nextBoolean()?rim(random,c,radius):inside(random,c,radius);
                        for(int i=0;i<4;i++)mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK,q.x,q.y+.2+random.nextDouble()*.8,q.z,(random.nextDouble()-.5)*.4,random.nextDouble()*.3,(random.nextDouble()-.5)*.4);}
                }
                case COLD -> {
                    for(int i=0;i<count/2;i++){Vec3 q=inside(random,c,radius);double a=Math.atan2(q.z-c.z,q.x-c.x);
                        mc.level.addParticle(ParticleTypes.SNOWFLAKE,q.x,q.y+2+random.nextDouble()*2,q.z,-Math.sin(a)*.06,-.04,Math.cos(a)*.06);}
                }
                case WATER -> {
                    for(int i=0;i<count/2;i++){Vec3 q=inside(random,c,radius);mc.level.addParticle(ParticleTypes.FALLING_WATER,q.x,q.y+3+random.nextDouble()*2,q.z,0,0,0);}
                    if(random.nextInt(2)==0){Vec3 q=rim(random,c,radius);mc.level.addParticle(ParticleTypes.SPLASH,q.x,q.y+.1,q.z,0,.1,0);}
                }
                case EARTH -> {
                    for(int i=0;i<count/3+1;i++){Vec3 q=rim(random,c,radius*(.7+random.nextDouble()*.3));
                        mc.level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK,net.minecraft.world.level.block.Blocks.COARSE_DIRT.defaultBlockState()),q.x,q.y+.1,q.z,0,.15+random.nextDouble()*.15,0);}
                }
                case LIFE -> {
                    for(int k=0;k<3;k++){double t=((time+k*13)%40)/40,a=time*.2+k*Math.PI*2/3,r=radius*.6*(1-t);
                        mc.level.addParticle(ParticleTypes.HAPPY_VILLAGER,c.x+Math.cos(a)*r,c.y+.2+t*2.5,c.z+Math.sin(a)*r,0,0,0);}
                }
                case ARCANE -> {
                    for(int k=0;k<count/2+1;k++){double a=-time*.1+k*Math.PI*2/(count/2+1),r=radius*.55;
                        mc.level.addParticle(ParticleTypes.END_ROD,c.x+Math.cos(a)*r,c.y+1.1+.25*Math.sin(time*.2+k),c.z+Math.sin(a)*r,0,0,0);}
                }
                case DEATH -> {
                    if(random.nextInt(2)==0){Vec3 q=inside(random,c,radius);mc.level.addParticle(ParticleTypes.SOUL,q.x,q.y+.2,q.z,0,.04,0);}
                    for(int i=0;i<count/3+1;i++){Vec3 q=rim(random,c,radius);mc.level.addParticle(ParticleTypes.SMOKE,q.x,q.y+.2,q.z,0,.03,0);}
                }
            }
        }
    }
    private static void singularityParticles(Minecraft mc,Vec3 c,double time){
        double t=time*.07;
        for(int i=0;i<12;i++){
            double a=t*(i%3==0?1.35:i%3==1?-.9:.62)+i*Math.PI*2/12;
            double r=1.35+(i%4)*.48;
            Vec3 q=switch(i%3){
                case 0->c.add(Math.cos(a)*r,.9+Math.sin(a*.7)*.35,Math.sin(a)*r);
                case 1->c.add(Math.cos(a)*r,Math.sin(a)*r*.72,.4*Math.sin(a)*r);
                default->c.add(Math.cos(a)*r*.78,Math.sin(a)*r*.48,Math.sin(a)*r*.78);
            };
            ParticleOptions type=switch(i%4){case 0->ParticleTypes.END_ROD;case 1->ParticleTypes.REVERSE_PORTAL;case 2->ParticleTypes.SOUL;default->ParticleTypes.SNOWFLAKE;};
            mc.level.addParticle(type,q.x,q.y+1,q.z,0,0,0);
        }
        for(int i=0;i<7;i++){
            double a=t*1.4+i*Math.PI*2/7,r=.35+.12*Math.sin(t+i);
            mc.level.addParticle(i%2==0?PowerParticleOption.create(ParticleTypes.DRAGON_BREATH,1):ParticleTypes.END_ROD,c.x+Math.cos(a)*r,c.y+1+Math.sin(t*.8+i)*r,c.z+Math.sin(a)*r,0,0,0);
        }
    }
    private static Vec3 inside(net.minecraft.util.RandomSource random,Vec3 c,double radius){double a=random.nextDouble()*Math.PI*2,r=radius*Math.sqrt(random.nextDouble());return c.add(Math.cos(a)*r,0,Math.sin(a)*r);}
    private static Vec3 rim(net.minecraft.util.RandomSource random,Vec3 c,double radius){double a=random.nextDouble()*Math.PI*2;return c.add(Math.cos(a)*radius,0,Math.sin(a)*radius);}
    private static boolean fire(ElementBlend blend){return blend.fire()>0 && blend.death()==0 && blend.lightning()==0;}
    private static void flames(Minecraft mc,Cue cue){
        var p=cue.packet;var random=mc.level.getRandom();double progress=Math.min(1,cue.age/(double)Math.max(1,p.ticks()));
        double spread=Math.clamp(p.radius(),.15,6);
        int count=switch(p.kind()){case SpellVfx.BEAM,SpellVfx.ARC,SpellVfx.SPRAY->16;case SpellVfx.ORB->8;case SpellVfx.IMPACT,SpellVfx.ZONE->14;default->6;};
        if(p.kind()==SpellVfx.BEAM)count=1;else if(p.kind()==SpellVfx.ARC)count=2;else if(p.kind()==SpellVfx.SPRAY)count=4;
        else count=(int)Math.round(count*Math.clamp(spread/2.5,1,3));
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
            if((p.kind()==SpellVfx.ZONE||p.kind()==SpellVfx.MINE||p.kind()==SpellVfx.TELEGRAPH) && singularity(formula)){
                double time=(mc.level==null?cue.age:mc.level.getGameTime())+partial,t=time*.045;Vec3 c=p.from().add(0,1,0);
                submitItem(stack,collector,mc,new ItemStack(Items.ENDER_EYE),c,.62f,(float)(time*2.5),25);
                Item[] bodies={Items.AMETHYST_SHARD,Items.ENDER_PEARL,Items.CRYING_OBSIDIAN,Items.SNOWBALL};
                for(int i=0;i<12;i++){
                    int ring=i%3;double r=ring==0?2.15:ring==1?2.8:3.45,a=(ring==0?t*.9:ring==1?-t*1.25:t*.7)+i/3*Math.PI*2/4;
                    Vec3 q=switch(ring){case 0->c.add(Math.cos(a)*r,.0,Math.sin(a)*r);case 1->c.add(Math.cos(a)*r,Math.sin(a)*r*.35,.2*Math.sin(a)*r);default->c.add(Math.cos(a)*r*.78,Math.sin(a)*r*.48,Math.sin(a)*r*.78);};
                    submitItem(stack,collector,mc,new ItemStack(bodies[i%bodies.length]),q,.18f+ring*.035f,(float)(time*7+i*37),35);
                }
                continue;
            }
            if(p.kind()!=SpellVfx.ORB || formula.earth()==0)continue;
            float progress=Math.clamp((cue.age+partial)/Math.max(1,p.ticks()),0,1);
            Vec3 center=p.from().lerp(p.to(),progress);float scale=(float)(Math.clamp(p.radius(),.05,16)*1.7);
            var item=formula.ice()>0?Items.BLUE_ICE:formula.total(Element.FIRE)>0?Items.MAGMA_BLOCK:formula.total(Element.COLD)>0?Items.PACKED_ICE:Items.COBBLESTONE;
            submitItem(stack,collector,mc,new ItemStack(item),center,scale,((mc.level==null?cue.age:mc.level.getGameTime())+partial)*18,25);
        }
        for(Cue cue:CUES)if(SpellModels.handles(cue.packet.kind()) && cue.packet.from().distanceToSqr(camera)<128*128)
            SpellModels.models(mc,stack,collector,cue.packet,cue.age+partial,cue.until,partial);
        collector.submitCustomGeometry(stack,net.minecraft.client.renderer.rendertype.RenderTypes.lightning(),(pose,consumer)->strokes(pose,consumer,camera,partial));
    }
    static void submitItem(PoseStack stack,SubmitNodeCollector collector,Minecraft mc,ItemStack item,Vec3 at,float scale,float spin,float tilt){
        stack.pushPose();stack.translate(at.x,at.y,at.z);stack.scale(scale,scale,scale);stack.rotate(Axis.YP.rotationDegrees(spin));stack.rotate(Axis.XP.rotationDegrees(tilt));
        var state=new net.minecraft.client.renderer.item.ItemStackRenderState();
        mc.getItemModelResolver().updateForTopItem(state,item,ItemDisplayContext.FIXED,mc.level,null,0);
        state.submit(stack,collector,net.minecraft.util.LightCoordsUtil.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,0);stack.popPose();
    }
    private static void strokes(PoseStack.Pose pose,VertexConsumer consumer,Vec3 camera,float partial){
        var mc=Minecraft.getInstance();
        for(Cue cue:CUES){
            var p=cue.packet;var formula=Formula.unpack(p.formula()).key();var blend=ElementBlend.of(formula);
            if(formula.cold()==2 && formula.earth()==1 && p.kind()==SpellVfx.IMPACT)continue;
            float progress=Math.clamp((cue.age+partial)/Math.max(1,p.ticks()),0,1);
            boolean stream=p.kind()==SpellVfx.BEAM || p.kind()==SpellVfx.ARC || p.kind()==SpellVfx.SPRAY;
            float alpha=stream?.85f*Math.min(1,Math.max(0,(cue.until-cue.age-partial)/2f)):(1-progress)*.85f;
            int color=color(formula);double radius=Math.clamp(p.radius(),.05,16);Vec3 rayFrom=cue.from(partial),rayTo=cue.to(partial),center=p.from();
            if(p.kind()==SpellVfx.BEAM && p.anchor()>=0 && mc.level!=null){var caster=mc.level.getEntity(p.anchor());if(caster!=null){Vec3 forward=caster.getViewVector(partial),right=forward.cross(new Vec3(0,1,0));right=right.lengthSqr()<1e-4?new Vec3(1,0,0):right.normalize();Vec3 up=right.cross(forward);Vec3 hand=new Vec3(1.5+.15*formula.size(),-.3-.06*formula.size(),.35);rayFrom=caster.getEyePosition(partial).add(forward.scale(hand.x)).add(up.scale(hand.y)).add(right.scale(hand.z));}}
            boolean flames=fire(blend);
            if(SpellModels.handles(p.kind())){
                if(p.from().distanceToSqr(camera)<128*128)SpellModels.strokes(mc,pose,consumer,camera,p,cue.age+partial,cue.until,partial);
                continue;
            }
            switch(p.kind()){
                case SpellVfx.BEAM,SpellVfx.ARC -> {
                    Vec3 delta=rayTo.subtract(rayFrom),direction=delta.lengthSqr()<1e-8?new Vec3(0,0,1):delta.normalize();
                    Vec3 reference=Math.abs(direction.y)>.98?new Vec3(1,0,0):new Vec3(0,1,0),axis=direction.cross(reference).normalize();
                    double phase=mc.level.getGameTime()+partial;
                    if(p.kind()==SpellVfx.BEAM){double beamRadius=Math.clamp(radius,.18,.38);beamFlowTubes(pose,consumer,rayFrom,rayTo,cue.flowTubes,beamRadius,alpha,partial);}
                    else {int segments=Math.clamp((int)(delta.length()/1.2),2,96);Vec3 previous=rayFrom;
                        for(int i=1;i<=segments;i++){Vec3 next=rayFrom.lerp(rayTo,i/(double)segments);
                            if(i<segments)next=next.add(axis.scale(Math.sin(i*13.7+phase*.18)*.35)).add(0,Math.cos(i*8.3+phase*.13)*.18,0);
                            stroke(pose,consumer,camera,previous,next,radius,color,alpha*.5f);stroke(pose,consumer,camera,previous,next,radius*.28,0xFFFFFF,alpha);previous=next;
                        }
                    }
                }
                case SpellVfx.SPRAY -> {
                    center=rayFrom;Vec3 direction=rayTo.subtract(center);direction=direction.lengthSqr()<1e-8?new Vec3(0,0,1):direction.normalize();
                    Vec3 reference=Math.abs(direction.y)>.98?new Vec3(1,0,0):new Vec3(0,1,0),side=direction.cross(reference).normalize();
                    for(int j=-2;j<=2;j++)stroke(pose,consumer,camera,center,rayTo.add(side.scale(j*radius*.5)),.06,color,alpha*.5f);
                }
                case SpellVfx.ORB -> {
                    if(formula.cold()==2 && formula.earth()==1){spear(pose,consumer,camera,p,progress);continue;}
                    alpha=.9f;
                    center=p.from().lerp(p.to(),progress);double orb=radius;
                    if(!flames){ring(pose,consumer,camera,center,orb,0,color,alpha,.05);ring(pose,consumer,camera,center,orb,1,color,alpha,.05);ring(pose,consumer,camera,center,orb,2,0xFFFFFF,alpha*.8f,.025);
                    stroke(pose,consumer,camera,p.from(),center,.09,color,alpha*.45f);}

                }
                case SpellVfx.ZONE,SpellVfx.MINE,SpellVfx.TELEGRAPH -> {
                    if(singularity(formula)){singularityRings(pose,consumer,camera,center,Minecraft.getInstance().level.getGameTime()+partial,alpha);continue;}
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
    private static boolean singularity(SpellFormula f){return f.arcane()==3 && f.cold()==1 && f.death()==1;}
    private static void singularityRings(PoseStack.Pose pose,VertexConsumer consumer,Vec3 camera,Vec3 center,double time,float alpha){
        Vec3 c=center.add(0,1,0);double t=time*.045;
        orbit(pose,consumer,camera,c,new Vec3(0,1,0),2.15,t*.9,.18,0xFF8E6BFF,alpha*.5f);
        orbit(pose,consumer,camera,c,new Vec3(1,.35,.2).normalize(),2.8,-t*1.25,.14,0xFFCA9CFF,alpha*.4f);
        orbit(pose,consumer,camera,c,new Vec3(-.25,.6,1).normalize(),3.45,t*.7,.1,0xFF6B4C9E,alpha*.35f);
    }
    private static void orbit(PoseStack.Pose pose,VertexConsumer consumer,Vec3 camera,Vec3 center,Vec3 normal,double radius,double spin,double width,int color,float alpha){
        Vec3 u=normal.cross(new Vec3(0,1,0));u=u.lengthSqr()<1e-5?new Vec3(1,0,0):u.normalize();Vec3 v=normal.cross(u).normalize();Vec3 previous=center.add(u.scale(radius));
        for(int i=1;i<=32;i++){double a=spin+i*Math.PI*2/32;Vec3 next=center.add(u.scale(Math.cos(a)*radius)).add(v.scale(Math.sin(a)*radius));stroke(pose,consumer,camera,previous,next,width,color,alpha);previous=next;}
    }
    private static void spear(PoseStack.Pose pose,VertexConsumer consumer,Vec3 camera,MagicNetwork.Visual p,float progress){Vec3 direction=p.to().subtract(p.from());if(direction.lengthSqr()<1e-6)return;direction=direction.normalize();Vec3 center=p.from().lerp(p.to(),progress);Vec3 side=direction.cross(new Vec3(0,1,0));side=side.lengthSqr()<1e-6?new Vec3(1,0,0):side.normalize();Vec3 lift=side.cross(direction).normalize();Vec3 tail=center.add(direction.scale(-1.6)),base=center.add(direction.scale(.65)),tip=center.add(direction.scale(2.4));stroke(pose,consumer,camera,tail,base,.11,0x80DEEA,.95f);stroke(pose,consumer,camera,tail,base,.035,0xFFFFFF,.95f);for(Vec3 edge:new Vec3[]{side,lift,side.scale(-1),lift.scale(-1)}){Vec3 corner=base.add(edge.scale(.38));stroke(pose,consumer,camera,corner,tip,.09,0x80DEEA,.95f);stroke(pose,consumer,camera,corner,tip,.025,0xFFFFFF,.9f);stroke(pose,consumer,camera,base,corner,.06,0xB3E5FC,.85f);}}
    private static int color(SpellFormula formula){
        return ElementBlend.of(formula).color();
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
        for(int i=0;i<word.length;i++)word[i]=elements.get(i%elements.size()).primary().ordinal()*3+random.nextInt(3);
        int points=new int[]{3,4,5,6,7,8}[(distinct.size()+random.nextInt(4))%6],step=points<5?1:points==6?2:(points-1)/2;
        boolean doubled=random.nextBoolean(),inner=random.nextBoolean();
        arc(pose,consumer,camera,center,u,v,radius,line*1.6,color,alpha);
        arc(pose,consumer,camera,center,u,v,radius*.8,line,color,alpha);
        if(doubled)arc(pose,consumer,camera,center,u,v,radius*.76,line*.6,color,alpha*.7f);
        double size=radius*.07;int slots=word.length+1,count=Math.max(1,(int)Math.round(2*Math.PI*.9/(.07*2.7)/slots))*slots;
        for(int k=0;k<count;k++){
            int slot=k%slots;double angle=spin+k*Math.PI*2/count;
            if(slot==word.length)glyph(pose,consumer,camera,center,u,v,angle,radius*.9,size,SEPARATOR,line*.7,color,alpha);
            else glyph(pose,consumer,camera,center,u,v,angle,radius*.9,size,safeGlyph(word[slot]),line*.7,tint(Element.values()[Math.min(Element.values().length-1,word[slot]/3)].color),alpha);
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
            glyph(pose,consumer,camera,center,u,v,-spin*2+i*Math.PI*2/distinct.size(),core*.55,core*.28,safeGlyph(e.ordinal()*3+((packed>>>i)&3)%3),line*.7,tint(e.color),alpha);
            glyph(pose,consumer,camera,center,u,v,-spin*2+i*Math.PI*2/distinct.size(),core*.55,core*.28,GLYPHS[e.primary().ordinal()*3+((packed>>>i)&3)%3],line*.7,tint(e.color),alpha);
        }
    }
    private static double[] safeGlyph(int index){return GLYPHS[Math.floorMod(index,GLYPHS.length)];}
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
    static void stroke(PoseStack.Pose pose,VertexConsumer out,Vec3 camera,Vec3 from,Vec3 to,double width,int color,float alpha){

        Vec3 side=to.subtract(from).cross(camera.subtract(from)).normalize().scale(width);if(side.lengthSqr()<1e-8)side=new Vec3(width,0,0);
        float r=((color>>16)&255)/255f,g=((color>>8)&255)/255f,b=(color&255)/255f;
        for(Vec3 v:new Vec3[]{from.subtract(side),from.add(side),to.add(side),to.subtract(side)})out.addVertex(pose,(float)v.x,(float)v.y,(float)v.z).setColor(r,g,b,alpha);
    }
    private static void beamTube(PoseStack.Pose pose,VertexConsumer out,Vec3 from,Vec3 to,double radius,int color,float alpha,int sides){
        Vec3 direction=to.subtract(from);if(direction.lengthSqr()<1e-8)return;direction=direction.normalize();
        Vec3 u=direction.cross(Math.abs(direction.y)>.95?new Vec3(1,0,0):new Vec3(0,1,0)).normalize(),v=direction.cross(u).normalize();
        float r=((color>>16)&255)/255f,g=((color>>8)&255)/255f,b=(color&255)/255f;
        for(int i=0;i<sides;i++){
            double a=i*Math.PI*2/sides,c=(i+1)*Math.PI*2/sides;
            Vec3 u0=u.scale(Math.cos(a)*radius).add(v.scale(Math.sin(a)*radius)),u1=u.scale(Math.cos(c)*radius).add(v.scale(Math.sin(c)*radius));
            for(Vec3 point:new Vec3[]{from.add(u0),from.add(u1),to.add(u1),to.add(u0)})out.addVertex(pose,(float)point.x,(float)point.y,(float)point.z).setColor(r,g,b,alpha);
            for(Vec3 point:new Vec3[]{from.add(u0),to.add(u0),to.add(u1),from.add(u1)})out.addVertex(pose,(float)point.x,(float)point.y,(float)point.z).setColor(r,g,b,alpha);
        }
        beamCap(pose,out,from,direction,radius,color,alpha,sides);
        beamCap(pose,out,to,direction.scale(-1),radius,color,alpha,sides);
    }
    private static void beamCap(PoseStack.Pose pose,VertexConsumer out,Vec3 center,Vec3 axis,double radius,int color,float alpha,int sides){
        Vec3 u=axis.cross(Math.abs(axis.y)>.95?new Vec3(1,0,0):new Vec3(0,1,0)).normalize(),v=axis.cross(u).normalize();
        float r=((color>>16)&255)/255f,g=((color>>8)&255)/255f,b=(color&255)/255f;
        for(int i=0;i<sides;i++){
            double a=i*Math.PI*2/sides,c=(i+1)*Math.PI*2/sides;
            Vec3 first=center.add(u.scale(Math.cos(a)*radius)).add(v.scale(Math.sin(a)*radius));
            Vec3 second=center.add(u.scale(Math.cos(c)*radius)).add(v.scale(Math.sin(c)*radius));
            for(Vec3 point:new Vec3[]{center,first,second})out.addVertex(pose,(float)point.x,(float)point.y,(float)point.z).setColor(r,g,b,alpha);
        }
    }
    private static void ensureFlowTubes(Cue cue,double length,float radius,int formula,int anchor){
        int wanted=Math.clamp((int)Math.ceil(Math.max(.1,length)/.017),1,1536);long seed=((long)formula<<32)^anchor^0x6A09E667F3BCC909L;double guide=radius*2;int accent=ElementBlend.of(Formula.unpack(formula).key()).color();
        while(cue.flowTubes.size()<wanted){int index=cue.flowTubes.size();Random random=new Random(seed^((long)index*0xD1B54A32D192ED03L));FlowTube tube=new FlowTube();tube.length=.5+random.nextDouble()*2.5;tube.radius=radius*(.18+random.nextDouble()*.42);tube.speed=.16+random.nextDouble()*.32;tube.distance=random.nextDouble()*Math.max(.1,length);tube.previousDistance=tube.distance;double angle=random.nextDouble()*Math.PI*2,radial=Math.pow(random.nextDouble(),1.8)*(guide-tube.radius);tube.offsetU=Math.cos(angle)*radial;tube.offsetV=Math.sin(angle)*radial;tube.color=index%7==0?0xFFFFFF:accent;tube.alpha=(float)(.62+random.nextDouble()*.28);cue.flowTubes.add(tube);}
    }
    private static void beamFlowTubes(PoseStack.Pose pose,VertexConsumer out,Vec3 from,Vec3 to,List<FlowTube> tubes,double radius,float alpha,float partial){
        Vec3 delta=to.subtract(from);double length=delta.length();if(length<.1)return;Vec3 axis=delta.scale(1/length);
        Vec3 u=axis.cross(Math.abs(axis.y)>.95?new Vec3(1,0,0):new Vec3(0,1,0)).normalize(),v=axis.cross(u).normalize();
        for(FlowTube tube:tubes){double distance=tube.previousDistance+(tube.distance-tube.previousDistance)*partial;if(distance>length)continue;Vec3 center=from.add(axis.scale(distance)).add(u.scale(tube.offsetU)).add(v.scale(tube.offsetV));Vec3 start=center.add(axis.scale(-tube.length*.5)),end=center.add(axis.scale(tube.length*.5));beamTube(pose,out,start,end,tube.radius,tube.color,alpha*tube.alpha,4);}
    }
    private static double visibleBeamLength(double length,int formula){return Math.max(.1,length-(1.5+.15*Formula.unpack(formula).size()));}
    private static double approach(double current,double target,double maxStep){return current<target?Math.min(target,current+maxStep):Math.max(target,current-maxStep);}
    private static Vec3 follow(Vec3 current,Vec3 target,Cue cue){
        double dot=Math.clamp(current.dot(target),-1,1);Vec3 error=target.subtract(current.scale(dot));
        if(error.lengthSqr()<1e-8){cue.beamTurnVelocity=cue.beamTurnVelocity.scale(.55);return current;}
        Vec3 velocity=cue.beamTurnVelocity.scale(.55).add(error.scale(.12));
        velocity=velocity.subtract(current.scale(velocity.dot(current)));
        double speed=velocity.length();if(speed>.18)velocity=velocity.scale(.18/speed);
        cue.beamTurnVelocity=velocity;
        return current.add(velocity).normalize();
    }
    private SpellVisuals(){}
}
