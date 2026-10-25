package com.restor.magicka;

import net.minecraft.core.particles.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Compact visual cues are rendered locally, independently of the particle quality setting. */
public final class SpellVfx {
    public static final int ORB=0,BEAM=1,ARC=2,IMPACT=3,ZONE=4,MINE=5,TELEGRAPH=6,SPRAY=7,SIGIL=8;
    public static void cue(ServerPlayer p,SpellDefinition s,int kind,Vec3 from,Vec3 to,double radius,int ticks){
        send(p,s==null?new SpellFormula(0,1,0,0,0,0,0):s.formula(),kind,from,to,radius,ticks);
    }
    public static void sigil(ServerPlayer p,SpellFormula formula,Vec3 center,Vec3 normal,double radius,int ticks){
        send(p,formula,SIGIL,center,center.add(normal.normalize()),radius,ticks);
    }
    public static void hand(ServerPlayer p,SpellFormula formula,double radius,int ticks){
        Vec3 look=p.getLookAngle(),side=look.cross(new Vec3(0,1,0));side=side.lengthSqr()<1e-4?new Vec3(1,0,0):side.normalize();
        Vec3 up=side.cross(look).normalize();
        sigil(p,formula,p.getEyePosition().add(look.scale(1.7)).add(side.scale(.35)).subtract(up.scale(.4)),look,radius,ticks);
    }
    private static void send(ServerPlayer p,SpellFormula formula,int kind,Vec3 from,Vec3 to,double radius,int ticks){
        int packed=formula.prepared().packed();
        var packet=new MagicNetwork.Visual(kind,packed,from,to,(float)Math.clamp(radius,.05,16),Math.clamp(ticks,1,100));
        for(var viewer:p.level().players())if(viewer==p || viewer.distanceToSqr(from)<256*256 || viewer.distanceToSqr(to)<256*256)PacketDistributor.sendToPlayer(viewer,packet);
    }
    public static void particles(ServerPlayer p,SpellDefinition s,Vec3 point,int count,double spread){
        var elements=s.formula().elements().stream().distinct().toList();int per=Math.max(1,Math.min(24,count)/elements.size());
        for(Element e:elements){
            ParticleOptions type=switch(e){case FIRE->ParticleTypes.FLAME;case WATER->ParticleTypes.SPLASH;case COLD->ParticleTypes.SNOWFLAKE;case LIGHTNING->ParticleTypes.ELECTRIC_SPARK;case EARTH->new BlockParticleOption(ParticleTypes.BLOCK,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());case LIFE->ParticleTypes.HAPPY_VILLAGER;case ARCANE->ParticleTypes.END_ROD;case DEATH->ParticleTypes.SOUL;};
            p.level().sendParticles(type,point.x,point.y,point.z,per,spread,spread,spread,.025);
        }
    }
    public static void sound(ServerPlayer p,SpellDefinition s,Vec3 point,boolean impact){
        SoundEvent sound=s.formula().lightning()>0?SoundEvents.TRIDENT_THUNDER.value():s.formula().fire()>0?SoundEvents.FIRECHARGE_USE:s.formula().cold()>0?SoundEvents.GLASS_BREAK:s.formula().water()>0?SoundEvents.GENERIC_SPLASH:s.formula().earth()>0?SoundEvents.STONE_BREAK:s.formula().death()>0?SoundEvents.WITHER_SKELETON_AMBIENT:s.formula().life()>0?SoundEvents.AMETHYST_BLOCK_CHIME:SoundEvents.BEACON_POWER_SELECT;
        p.level().playSound(null,point.x,point.y,point.z,sound,SoundSource.PLAYERS,impact?.65f:.4f,impact?1.3f:1.0f);
    }
    private SpellVfx(){}
}
