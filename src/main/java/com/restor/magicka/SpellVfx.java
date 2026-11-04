package com.restor.magicka;

import net.minecraft.core.particles.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Compact visual cues are rendered locally, independently of the particle quality setting. */
public final class SpellVfx {
    public static final int ORB=0,BEAM=1,ARC=2,IMPACT=3,ZONE=4,MINE=5,TELEGRAPH=6,SPRAY=7,SIGIL=8,
            GEYSER=9,PYRE=10,PYLON=11,STORM=12,MIST=13,TOTEM=14,MIRROR=15,LANCE=16,CHAINS=17,SPIRES=18,BLOOM=19,POOL=20,VORTEX=21,EMBERFALL=22;
    /** A spell's "visual" delivery key swaps the generic cue for its own scenery. */
    public static int visual(SpellDefinition s,int fallback){
        if(s==null)return fallback;
        return switch(SpellDefinition.string(s.delivery(),"visual","")){
            case "geyser"->GEYSER;case "pyre"->PYRE;case "pylon"->PYLON;case "storm"->STORM;case "mist"->MIST;
            case "totem"->TOTEM;case "mirror"->MIRROR;case "lance"->LANCE;case "chains"->CHAINS;
            case "spires"->SPIRES;case "bloom"->BLOOM;case "pool"->POOL;case "vortex"->VORTEX;case "emberfall"->EMBERFALL;default->fallback;
        };
    }
    public static void anchored(ServerPlayer p,SpellDefinition s,int kind,net.minecraft.world.entity.Entity anchor,double radius,int ticks){
        send(p,s.formula(),kind,anchor.position(),anchor.position(),radius,ticks,anchor.getId());
    }
    public static void cue(ServerPlayer p,SpellDefinition s,int kind,Vec3 from,Vec3 to,double radius,int ticks){
        send(p,s==null?new SpellFormula(0,1,0,0,0,0,0):s.formula(),kind,from,to,radius,ticks,-1);
    }
    public static void sigil(ServerPlayer p,SpellFormula formula,Vec3 center,Vec3 normal,double radius,int ticks){
        send(p,formula,SIGIL,center,center.add(normal.normalize()),radius,ticks,-1);
    }
    /** Offset and normal are in the caster's view frame: x forward, y up, z right. */
    public static void attached(ServerPlayer p,SpellFormula formula,Vec3 offset,Vec3 normal,double radius,int ticks){
        send(p,formula,SIGIL,offset,normal.normalize(),radius,ticks,p.getId());
    }
    public static double handRadius(SpellFormula formula){return .3+.25*formula.size();}
    public static void hand(ServerPlayer p,SpellFormula formula,int ticks){
        int size=formula.size();
        attached(p,formula,new Vec3(1.5+.15*size,-.3-.06*size,.35),new Vec3(1,0,0),handRadius(formula),ticks);
    }
    private static void send(ServerPlayer p,SpellFormula formula,int kind,Vec3 from,Vec3 to,double radius,int ticks,int anchor){
        int packed=formula.prepared().packed();
        var packet=new MagicNetwork.Visual(kind,packed,from,to,(float)Math.clamp(radius,.05,16),Math.clamp(ticks,1,100),anchor);
        Vec3 near=anchor>=0?p.position():from;
        for(var viewer:p.serverLevel().players())if(viewer==p || viewer.distanceToSqr(near)<256*256 || anchor<0 && viewer.distanceToSqr(to)<256*256)PacketDistributor.sendToPlayer(viewer,packet);
    }
    public static void particles(ServerPlayer p,SpellDefinition s,Vec3 point,int count,double spread){
        var blend=ElementBlend.of(s.formula());int per=Math.max(1,Math.min(24,count)/Math.max(1,blend.size()));
        if(blend.steam()>0)EpicMagic.fx(p.serverLevel(),ParticleTypes.CLOUD,point.x,point.y,point.z,per*blend.steam(),spread,spread,spread,.035);
        if(blend.ice()>0){EpicMagic.fx(p.serverLevel(),ParticleTypes.SNOWFLAKE,point.x,point.y,point.z,per*blend.ice(),spread,spread,spread,.015);EpicMagic.fx(p.serverLevel(),new BlockParticleOption(ParticleTypes.BLOCK,net.minecraft.world.level.block.Blocks.PACKED_ICE.defaultBlockState()),point.x,point.y,point.z,Math.max(1,per*blend.ice()/2),spread,spread,spread,.04);}
        if(blend.shield()>0)EpicMagic.fx(p.serverLevel(),ParticleTypes.WAX_ON,point.x,point.y,point.z,per*blend.shield(),spread,spread,spread,.04);
        if(blend.magma()>0)EpicMagic.fx(p.serverLevel(),ParticleTypes.LAVA,point.x,point.y,point.z,per*blend.magma(),spread,spread,spread,.03);
        for(Element e:Element.BASE){
            int amount=blend.count(e);if(amount==0)continue;
            ParticleOptions type=switch(e){case FIRE->ParticleTypes.FLAME;case WATER->ParticleTypes.SPLASH;case COLD->ParticleTypes.SNOWFLAKE;case LIGHTNING->ParticleTypes.ELECTRIC_SPARK;case EARTH->new BlockParticleOption(ParticleTypes.BLOCK,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());case LIFE->ParticleTypes.HAPPY_VILLAGER;case ARCANE->ParticleTypes.END_ROD;default->ParticleTypes.SOUL;};
            EpicMagic.fx(p.serverLevel(),type,point.x,point.y,point.z,per*amount,spread,spread,spread,.025);
        }
    }
    public static void sound(ServerPlayer p,SpellDefinition s,Vec3 point,boolean impact){
        var blend=ElementBlend.of(s.formula());SoundEvent sound=blend.steam()>0?SoundEvents.FIRE_EXTINGUISH:blend.ice()>0?SoundEvents.GLASS_BREAK:blend.shield()>0?SoundEvents.SHIELD_BLOCK:blend.magma()>0?SoundEvents.LAVA_POP:s.formula().lightning()>0?SoundEvents.TRIDENT_THUNDER.value():blend.fire()>0?SoundEvents.FIRECHARGE_USE:blend.cold()>0?SoundEvents.GLASS_BREAK:blend.water()>0?SoundEvents.GENERIC_SPLASH:blend.earth()>0?SoundEvents.STONE_BREAK:s.formula().death()>0?SoundEvents.WITHER_SKELETON_AMBIENT:s.formula().life()>0?SoundEvents.AMETHYST_BLOCK_CHIME:SoundEvents.BEACON_POWER_SELECT;
        p.serverLevel().playSound(null,point.x,point.y,point.z,sound,SoundSource.PLAYERS,impact?.65f:.4f,impact?1.3f:1.0f);
    }
    private SpellVfx(){}
}
