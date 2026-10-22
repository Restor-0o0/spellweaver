package com.restor.magicka;

import net.minecraft.world.entity.EntityType;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.ZombieHorse;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import java.util.*;

import static com.restor.magicka.LegendMagic.*;

/** Pop-culture signatures built around the Life element. */
final class LegendLife {
    static final String PHOENIX_TAG = "magicka:phoenix";

    static void defibrillator(ServerPlayer p, SpellDefinition s, boolean self) {
        ServerLevel level = p.level();
        LivingEntity target = self ? p : aimed(p,24);
        if (target == null || !EffectGraph.ally(p,target))
            target = near(p,p.position(),8).stream().filter(e -> e != p && EffectGraph.ally(p,e)).min(Comparator.comparingDouble(e -> e.distanceToSqr(p))).orElse(p);
        Vec3 hand = p.getEyePosition().add(p.getLookAngle().scale(.8)).add(0,-.3,0), at = target.getBoundingBox().getCenter();
        announce(p,24,"magicka.legend.defibrillator");
        if (target != p) { SpellVfx.cue(p,s,SpellVfx.ARC,hand,at,.15,10); line(level,hand,at,.25,ParticleTypes.ELECTRIC_SPARK); }
        boolean critical = target.getHealth() < target.getMaxHealth()*.3f;
        EffectGraph.entity(p,target,s,s.effects(),hand,false);
        if (critical) EffectGraph.entity(p,target,s,s.effects(),hand,false);
        target.removeEffect(MobEffects.POISON); target.removeEffect(MobEffects.WITHER);
        target.push(0,.25,0); target.hurtMarked = true;
        EpicMagic.fx(level,ParticleTypes.ELECTRIC_SPARK,at.x,at.y,at.z,30,.4,.6,.4,.2);
        EpicMagic.fx(level,ParticleTypes.HEART,at.x,at.y+target.getBbHeight()*.6,at.z,critical ? 8 : 4,.4,.3,.4,0);
        sound(level,at,SoundEvents.LIGHTNING_BOLT_IMPACT,.7f,2f);
        sound(level,at,SoundEvents.BEACON_POWER_SELECT,1f,1.8f);
    }

    static void lifeDrain(ServerPlayer p, SpellDefinition s, boolean self) {
        ServerLevel level = p.level();
        sound(level,p.position(),SoundEvents.WARDEN_HEARTBEAT,1.5f,.8f);
        SpellRuntime.repeat(p,60,age -> {
            if (self) {
                if (age % 20 != 0) return;
                Vec3 heart = p.position().add(0,1,0);
                for (LivingEntity e : near(p,heart,6)) if (foe(p,e)) {
                    EffectGraph.entity(p,e,s,s.effects(),heart,false);
                    line(level,e.getBoundingBox().getCenter(),heart,.3,dust(0x9B0E1A,1f));
                }
                sound(level,heart,SoundEvents.WARDEN_HEARTBEAT,1f,1.2f);
                return;
            }
            Vec3 look = p.getLookAngle(), hand = p.getEyePosition().add(look.scale(.7)).add(0,-.3,0);
            Vec3 to = reach(p,hand,look,20);
            LivingEntity victim = segment(p,hand,to,.4).stream().filter(e -> foe(p,e)).min(Comparator.comparingDouble(e -> e.distanceToSqr(hand))).orElse(null);
            Vec3 end = victim != null ? victim.getBoundingBox().getCenter() : to;
            int count = (int)Math.min(40,end.distanceTo(hand)/.5);
            for (int i = 0; i < count; i++) {
                Vec3 at = end.lerp(hand,(i*.13+age*.08) % 1);
                EpicMagic.fx(level,dust(victim != null ? 0xB0101E : 0x4A0A10,1f),at.x,at.y,at.z,1,.04,.04,.04,0);
            }
            if (victim != null && age % 10 == 0) {
                EffectGraph.entity(p,victim,s,s.effects(),hand,false);
                EpicMagic.fx(level,ParticleTypes.DAMAGE_INDICATOR,end.x,end.y,end.z,3,.2,.2,.2,.1);
                sound(level,hand,SoundEvents.WARDEN_HEARTBEAT,1f,1.4f);
            }
        });
    }

    static void purifyingFlame(ServerPlayer p, SpellDefinition s, boolean self) {
        ServerLevel level = p.level();
        Vec3 center = self ? p.position() : EpicMagic.focus(p);
        double radius = Math.clamp(s.number("radius",5),2,12);
        List<com.google.gson.JsonObject> support = graph(s,"support");
        sound(level,center,SoundEvents.FIRECHARGE_USE,1.2f,.7f);
        SpellRuntime.repeat(p,Math.max(20,(int)s.number("duration_ticks",160)),age -> {
            RandomSource r = level.getRandom();
            if (age % 2 == 0) for (int i = 0; i < 20; i++) {
                double a = age*.05+i*Math.PI*2/20;
                EpicMagic.fx(level,ParticleTypes.FLAME,center.x+Math.cos(a)*radius,center.y+.1,center.z+Math.sin(a)*radius,0,0,.05,0,1);
                if (i % 3 == 0) EpicMagic.fx(level,dust(0xFFE08A,1f),center.x+(r.nextDouble()*2-1)*radius*.8,center.y+.2+r.nextDouble()*2,center.z+(r.nextDouble()*2-1)*radius*.8,0,0,.08,0,1);
            }
            if (age % 20 == 10) sound(level,center,SoundEvents.FIRE_AMBIENT,1f,1.2f);
            if (age % 20 != 0) return;
            for (LivingEntity e : withCaster(p,near(p,center,radius),center,radius)) {
                if (foe(p,e)) {
                    EffectGraph.entity(p,e,s,s.effects(),center,false);
                    if (e.getType().builtInRegistryHolder().is(EntityTypeTags.UNDEAD)) EffectGraph.entity(p,e,s,s.effects(),center,false);
                } else if (EffectGraph.ally(p,e)) {
                    EffectGraph.entity(p,e,s,support,center,false);
                    e.clearFire();
                    EpicMagic.fx(level,ParticleTypes.HEART,e.getX(),e.getY()+e.getBbHeight()+.3,e.getZ(),1,.2,.1,.2,0);
                }
            }
        });
    }

    static void cryosleep(ServerPlayer p, SpellDefinition s, boolean self) {
        ServerLevel level = p.level();
        LivingEntity aimed = self ? p : aimed(p,24);
        LivingEntity target = aimed == null ? p : aimed;
        int ticks = Math.clamp((int)s.number("duration_ticks",60),20,200);
        boolean hostile = foe(p,target);
        Vec3 anchor = target.position();
        sound(level,anchor,SoundEvents.GLASS_PLACE,1.2f,.6f);
        if (hostile) EffectGraph.entity(p,target,s,graph(s,"foe"),p.position(),false);
        else target.addEffect(new MobEffectInstance(MobEffects.RESISTANCE,ticks,4));
        SpellRuntime.repeat(p,ticks,age -> {
            if (!target.isAlive()) return;
            if (!hostile) {
                target.setDeltaMovement(Vec3.ZERO); target.fallDistance = 0;
                if (target.position().distanceToSqr(anchor) > .09) target.teleportTo(anchor.x,anchor.y,anchor.z);
                if (age % 10 == 0) EffectGraph.entity(p,target,s,s.effects(),anchor,false);
            }
            if (age % 4 == 0) {
                box(level,target.getBoundingBox().inflate(.3),dust(0xBDE8FF,.9f));
                EpicMagic.fx(level,ParticleTypes.SNOWFLAKE,target.getX(),target.getY()+target.getBbHeight()/2,target.getZ(),3,.4,.6,.4,0);
            }
            if (age == ticks-1) {
                Vec3 at = target.getBoundingBox().getCenter();
                EpicMagic.fx(level,new BlockParticleOption(ParticleTypes.BLOCK,Blocks.ICE.defaultBlockState()),at.x,at.y,at.z,40,.5,.7,.5,.1);
                sound(level,at,SoundEvents.GLASS_BREAK,1.2f,1f);
            }
        });
    }
    private static void box(ServerLevel level, AABB b, net.minecraft.core.particles.ParticleOptions type) {
        double[] xs = {b.minX,b.maxX}, ys = {b.minY,b.maxY}, zs = {b.minZ,b.maxZ};
        for (double y : ys) for (double z : zs) line(level,new Vec3(b.minX,y,z),new Vec3(b.maxX,y,z),.35,type);
        for (double x : xs) for (double z : zs) line(level,new Vec3(x,b.minY,z),new Vec3(x,b.maxY,z),.35,type);
        for (double x : xs) for (double y : ys) line(level,new Vec3(x,y,b.minZ),new Vec3(x,y,b.maxZ),.35,type);
    }

    static void phoenix(ServerPlayer p, SpellDefinition s, boolean self) {
        ServerLevel level = p.level();
        LivingEntity aimed = self ? p : aimed(p,24);
        LivingEntity target = aimed != null && EffectGraph.ally(p,aimed) ? aimed : p;
        int ticks = Math.clamp((int)s.number("duration_ticks",1200),20,12000);
        target.getPersistentData().putLong(PHOENIX_TAG,level.getGameTime()+ticks);
        EffectGraph.entity(p,target,s,s.effects(),p.position(),false);
        Vec3 at = target.getBoundingBox().getCenter();
        announce(p,24,"magicka.legend.phoenix");
        EpicMagic.fx(level,ParticleTypes.FLAME,at.x,at.y,at.z,40,.4,.8,.4,.08);
        EpicMagic.fx(level,dust(0xFF8A1F,1.4f),at.x,at.y+.5,at.z,20,.5,.8,.5,0);
        sound(level,at,SoundEvents.BLAZE_SHOOT,1f,1.4f);
        sound(level,at,SoundEvents.AMETHYST_BLOCK_CHIME,1.2f,.7f);
        SpellRuntime.repeat(p,ticks,age -> {
            if (age % 10 != 0 || !target.isAlive() || !target.getPersistentData().contains(PHOENIX_TAG)) return;
            double a = age*.3;
            EpicMagic.fx(level,ParticleTypes.SMALL_FLAME,target.getX()+Math.cos(a)*.6,target.getY()+1.2,target.getZ()+Math.sin(a)*.6,1,0,0,0,0);
        });
    }
    static void death(LivingDeathEvent event) {
        LivingEntity v = event.getEntity();
        if (!(v.level() instanceof ServerLevel level) || v.getPersistentData().getLongOr(PHOENIX_TAG, 0L) <= level.getGameTime()) return;
        v.getPersistentData().remove(PHOENIX_TAG);
        event.setCanceled(true);
        v.setHealth(v.getMaxHealth()*.5f);
        v.clearFire();
        v.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,200));
        v.addEffect(new MobEffectInstance(MobEffects.REGENERATION,100,1));
        Vec3 at = v.getBoundingBox().getCenter();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,v.getBoundingBox().inflate(5),e -> e != v && e instanceof Enemy && e.isAlive())) {
            e.igniteForSeconds(5);
            Vec3 away = flat(e.position().subtract(v.position()));
            e.push(away.x*1.5,.5,away.z*1.5); e.hurtMarked = true;
        }
        EpicMagic.fx(level,ParticleTypes.FLAME,at.x,at.y,at.z,120,1.2,1.2,1.2,.2);
        EpicMagic.fx(level,ParticleTypes.TOTEM_OF_UNDYING,at.x,at.y,at.z,40,.6,.8,.6,.4);
        sound(level,at,SoundEvents.TOTEM_USE,1f,1.2f);
        sound(level,at,SoundEvents.BLAZE_SHOOT,1.5f,.6f);
        if (v instanceof ServerPlayer player) player.sendOverlayMessage(Component.translatable("magicka.legend.phoenix_rebirth"));
    }

    static void kaioken(ServerPlayer p, SpellDefinition s) {
        ServerLevel level = p.level();
        int ticks = Math.clamp((int)s.number("duration_ticks",400),20,2400);
        EffectGraph.entity(p,p,s,s.effects(),p.position(),true);
        p.addEffect(new MobEffectInstance(MobEffects.STRENGTH,ticks,1));
        p.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST,ticks,1));
        p.addEffect(new MobEffectInstance(MobEffects.HASTE,ticks,1));
        announce(p,24,"magicka.legend.kaioken");
        sound(level,p.position(),SoundEvents.RAVAGER_ROAR,1f,1.5f);
        sound(level,p.position(),SoundEvents.FIRECHARGE_USE,1f,.6f);
        SpellRuntime.repeat(p,ticks,age -> {
            if (age % 2 == 0) for (int i = 0; i < 4; i++) {
                double a = level.getRandom().nextDouble()*Math.PI*2;
                EpicMagic.fx(level,dust(i % 2 == 0 ? 0xFF1E1E : 0xFF7A5A,1.2f),p.getX()+Math.cos(a)*.5,p.getY()+level.getRandom().nextDouble()*2,p.getZ()+Math.sin(a)*.5,0,0,.15,0,1);
            }
            if (age % 20 == 19 && p.getHealth() > 2) p.setHealth(Math.max(2,p.getHealth()-1));
        });
    }

    static void protego(ServerPlayer p, SpellDefinition s, boolean self) {
        ServerLevel level = p.level();
        double radius = self ? 3.5 : Math.clamp(s.number("radius",6),3,12);
        Vec3 fixed = p.position();
        Map<UUID,Integer> cooldown = new HashMap<>();
        announce(p,24,"magicka.legend.protego");
        sound(level,fixed,SoundEvents.BEACON_ACTIVATE,1.5f,1.4f);
        SpellRuntime.repeat(p,Math.max(20,(int)s.number("duration_ticks",200)),age -> {
            Vec3 center = self ? p.position() : fixed;
            RandomSource r = level.getRandom();
            if (age % 2 == 0) for (int i = 0; i < 30; i++) {
                Vec3 v = new Vec3(r.nextGaussian(),Math.abs(r.nextGaussian()),r.nextGaussian()).normalize().scale(radius);
                EpicMagic.fx(level,dust(0xFFE9A8,.9f),center.x+v.x,center.y+v.y,center.z+v.z,1,0,0,0,0);
            }
            if (age % 20 == 0) for (LivingEntity e : withCaster(p,near(p,center,radius),center,radius))
                if (EffectGraph.ally(p,e)) e.addEffect(new MobEffectInstance(MobEffects.RESISTANCE,30,0));
            for (LivingEntity e : near(p,center,radius+.5)) {
                if (!foe(p,e)) continue;
                Vec3 out = flat(e.position().subtract(center));
                e.setDeltaMovement(out.x*.6,.2,out.z*.6); e.hurtMarked = true;
                if (cooldown.getOrDefault(e.getUUID(),-99) <= age) { cooldown.put(e.getUUID(),age+20); EffectGraph.entity(p,e,s,s.effects(),center,false); }
            }
            for (Projectile shot : level.getEntitiesOfClass(Projectile.class,new AABB(center,center).inflate(radius+1))) {
                if (shot.getOwner() == p || shot.getOwner() instanceof LivingEntity owner && EffectGraph.ally(p,owner)) continue;
                Vec3 rel = shot.position().subtract(center), v = shot.getDeltaMovement();
                double d = rel.length();
                if (d > radius+1 || d < radius-1.5 || v.dot(rel) >= 0) continue;
                Vec3 n = rel.normalize();
                shot.setDeltaMovement(v.subtract(n.scale(2*v.dot(n))));
                shot.setOwner(p); shot.hurtMarked = true;
                EpicMagic.fx(level,ParticleTypes.ENCHANTED_HIT,shot.getX(),shot.getY(),shot.getZ(),8,.2,.2,.2,.2);
                sound(level,shot.position(),SoundEvents.SHIELD_BLOCK.value(),1f,1.4f);
            }
        });
    }

    static void zombieCure(ServerPlayer p, SpellDefinition s, boolean self) {
        ServerLevel level = p.level();
        Vec3 center = self ? p.position() : EpicMagic.focus(p);
        double radius = Math.clamp(s.number("radius",4),2,10);
        sound(level,center,SoundEvents.ZOMBIE_VILLAGER_CURE,1.2f,1.2f);
        EpicMagic.fx(level,ParticleTypes.TOTEM_OF_UNDYING,center.x,center.y+1,center.z,60,radius*.4,.8,radius*.4,.3);
        for (int i = 0; i < 48; i++) {
            double a = i*Math.PI*2/48;
            EpicMagic.fx(level,dust(0xFFD84A,1.2f),center.x+Math.cos(a)*radius,center.y+.2,center.z+Math.sin(a)*radius,1,0,0,0,0);
        }
        for (LivingEntity e : near(p,center,radius)) {
            LivingEntity cured = switch (e) {
                case ZombieVillager zv -> {
                    var villager = zv.convertTo(EntityType.VILLAGER,net.minecraft.world.entity.ConversionParams.single(zv,false,false),v -> {});
                    if (villager != null) {
                        villager.setVillagerData(zv.getVillagerData());
                        villager.setVillagerXp(zv.getVillagerXp());
                        villager.addEffect(new MobEffectInstance(MobEffects.NAUSEA,200));
                    }
                    yield villager;
                }
                case ZombifiedPiglin zp -> { var piglin = zp.convertTo(EntityType.PIGLIN,net.minecraft.world.entity.ConversionParams.single(zp,true,false),v -> {}); if (piglin != null) piglin.setImmuneToZombification(true); yield piglin; }
                case Zoglin zg -> { var hoglin = zg.convertTo(EntityType.HOGLIN,net.minecraft.world.entity.ConversionParams.single(zg,true,false),v -> {}); if (hoglin != null) hoglin.setImmuneToZombification(true); yield hoglin; }
                case ZombieHorse zh -> zh.convertTo(EntityType.HORSE,net.minecraft.world.entity.ConversionParams.single(zh,false,false),v -> {});
                default -> null;
            };
            if (cured != null) {
                EventHooks.onLivingConvert(e,cured);
                EpicMagic.fx(level,ParticleTypes.HAPPY_VILLAGER,cured.getX(),cured.getY()+1,cured.getZ(),16,.4,.6,.4,0);
            } else if (e.getType().builtInRegistryHolder().is(EntityTypeTags.UNDEAD) && foe(p,e)) EffectGraph.entity(p,e,s,s.effects(),center,false);
        }
    }

    static void wellspring(ServerPlayer p, SpellDefinition s, boolean self) {
        ServerLevel level = p.level();
        Vec3 center = self ? p.position() : EpicMagic.focus(p);
        double radius = Math.clamp(s.number("radius",4),2,10);
        sound(level,center,SoundEvents.BUCKET_EMPTY,1.2f,.8f);
        SpellRuntime.repeat(p,Math.max(40,(int)s.number("duration_ticks",600)),age -> {
            RandomSource r = level.getRandom();
            if (age % 2 == 0) {
                for (int i = 0; i < 6; i++) {
                    double a = r.nextDouble()*Math.PI*2, t = r.nextDouble();
                    EpicMagic.fx(level,dust(0x5EC8FF,1f),center.x+Math.cos(a)*t*1.6,center.y+.2+2.6*4*t*(1-t),center.z+Math.sin(a)*t*1.6,1,0,0,0,0);
                }
                EpicMagic.fx(level,ParticleTypes.SPLASH,center.x,center.y+.3,center.z,6,.3,.1,.3,.1);
                EpicMagic.fx(level,ParticleTypes.FALLING_WATER,center.x,center.y+2.4,center.z,2,.2,.1,.2,0);
            }
            if (age % 40 != 0) return;
            sound(level,center,SoundEvents.WATER_AMBIENT,.8f,1.2f);
            for (LivingEntity e : withCaster(p,near(p,center,radius),center,radius)) if (EffectGraph.ally(p,e)) {
                EffectGraph.entity(p,e,s,s.effects(),center,false);
                EpicMagic.fx(level,ParticleTypes.HEART,e.getX(),e.getY()+e.getBbHeight()+.3,e.getZ(),1,.2,.1,.2,0);
            }
        });
    }

    private LegendLife() {}
}
