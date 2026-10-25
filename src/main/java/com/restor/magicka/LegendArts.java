package com.restor.magicka;

import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import java.util.*;
import java.util.function.Predicate;

import static com.restor.magicka.LegendMagic.*;

/** Legendary signatures and the five elemental signs. */
final class LegendArts {
    static void amaterasu(ServerPlayer p, SpellDefinition s, boolean self) {
        ServerLevel level = p.level();
        int ticks = Math.clamp((int)s.number("duration_ticks",200),20,600);
        announce(p,24,"magicka.legend.amaterasu");
        sound(level,p.position(),SoundEvents.FIRECHARGE_USE,1f,.4f);
        if (self) {
            Map<UUID,Integer> cooldown = new HashMap<>();
            SpellRuntime.repeat(p,ticks/2,age -> {
                Vec3 c = p.position();
                for (int i = 0; i < 10; i++) {
                    double a = age*.2+i*Math.PI*2/10;
                    EpicMagic.fx(level,dust(0x0A0A0A,1.6f),c.x+Math.cos(a)*3,c.y+.3,c.z+Math.sin(a)*3,1,.05,.2,.05,0);
                    if (i % 3 == 0) EpicMagic.fx(level,ParticleTypes.SOUL_FIRE_FLAME,c.x+Math.cos(a)*3,c.y+.3,c.z+Math.sin(a)*3,0,0,.04,0,1);
                }
                for (LivingEntity e : near(p,c.add(0,1,0),3)) if (foe(p,e) && cooldown.getOrDefault(e.getUUID(),-99) <= age) {
                    cooldown.put(e.getUUID(),age+20);
                    e.igniteForTicks(40);
                    EffectGraph.entity(p,e,s,s.effects(),c,false);
                }
            });
            return;
        }
        LivingEntity first = aimed(p,64);
        if (first == null) {
            Vec3 at = SpellAim.hit(p).getLocation();
            EpicMagic.fx(level,dust(0x0A0A0A,1.6f),at.x,at.y+.3,at.z,20,.3,.3,.3,0);
            return;
        }
        LivingEntity[] burning = {first};
        SpellRuntime.repeat(p,ticks,age -> {
            LivingEntity e = burning[0];
            if (e == null) return;
            if (!e.isAlive()) {
                LivingEntity next = near(p,e.position(),4).stream().filter(o -> o != e && foe(p,o)).min(Comparator.comparingDouble(o -> o.distanceToSqr(e))).orElse(null);
                if (next != null) line(level,e.position().add(0,1,0),next.getBoundingBox().getCenter(),.3,dust(0x0A0A0A,1.4f));
                burning[0] = next;
                return;
            }
            e.igniteForTicks(20);
            AABB b = e.getBoundingBox();
            EpicMagic.fx(level,dust(0x0A0A0A,1.6f),b.getCenter().x,b.minY+b.getYsize()*.5,b.getCenter().z,6,b.getXsize()*.4,b.getYsize()*.4,b.getZsize()*.4,0);
            EpicMagic.fx(level,ParticleTypes.SOUL_FIRE_FLAME,b.getCenter().x,b.minY+.2,b.getCenter().z,2,b.getXsize()*.3,b.getYsize()*.3,b.getZsize()*.3,.01);
            EpicMagic.fx(level,ParticleTypes.LARGE_SMOKE,b.getCenter().x,b.maxY,b.getCenter().z,1,.2,.1,.2,.01);
            if (age % 20 == 0) EffectGraph.entity(p,e,s,s.effects(),p.position(),false);
        });
    }

    static void mjolnir(ServerPlayer p, SpellDefinition s, boolean self) {
        announce(p,24,"magicka.legend.mjolnir");
        if (self) { hammer(p,s,p.position()); return; }
        Vec3 look = flat(p.getLookAngle());
        p.setDeltaMovement(look.x*1.1,1.25,look.z*1.1); p.hurtMarked = true;
        sound(p.level(),p.position(),SoundEvents.TRIDENT_RIPTIDE_3.value(),1f,.8f);
        SpellRuntime.sustain(p,age -> {
            p.fallDistance = 0;
            EpicMagic.fx(p.level(),ParticleTypes.ELECTRIC_SPARK,p.getX(),p.getY()+1,p.getZ(),4,.3,.5,.3,.05);
            if (age > 5 && (p.onGround() || p.isInWater()) || age > 60) { hammer(p,s,p.position()); return true; }
            return false;
        });
    }
    private static void hammer(ServerPlayer p, SpellDefinition s, Vec3 at) {
        ServerLevel level = p.level();
        var bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (bolt != null) { bolt.snapTo(at.x,at.y,at.z); bolt.setVisualOnly(true); level.addFreshEntity(bolt); }
        sound(level,at,SoundEvents.LIGHTNING_BOLT_THUNDER,2f,1f);
        sound(level,at,SoundEvents.ANVIL_LAND,1.2f,.5f);
        EpicMagic.fx(level,ParticleTypes.EXPLOSION,at.x,at.y+.5,at.z,3,1,.2,1,0);
        for (int i = 0; i < 40; i++) {
            double a = i*Math.PI*2/40;
            EpicMagic.fx(level,ParticleTypes.ELECTRIC_SPARK,at.x+Math.cos(a)*1.5,at.y+.2,at.z+Math.sin(a)*1.5,0,Math.cos(a),.05,Math.sin(a),.5);
        }
        for (LivingEntity e : near(p,at.add(0,1,0),Math.clamp(s.number("radius",4),2,8))) if (foe(p,e)) EffectGraph.entity(p,e,s,s.effects(),at,false);
    }

    static void accio(ServerPlayer p, SpellDefinition s, boolean self) {
        ServerLevel level = p.level();
        announce(p,24,"magicka.legend.accio");
        sound(level,p.position(),SoundEvents.ILLUSIONER_CAST_SPELL,1f,1.8f);
        Predicate<Entity> loot = e -> e instanceof ItemEntity || e instanceof ExperienceOrb;
        if (self) {
            for (Entity e : level.getEntities(p,p.getBoundingBox().inflate(16),loot)) {
                line(level,e.position(),p.position().add(0,1,0),.6,dust(0xFFD24A,.7f));
                fly(p,e);
            }
            return;
        }
        Vec3 from = p.getEyePosition(), look = p.getLookAngle(), to = reach(p,from,look,64);
        Entity hit = level.getEntities(p,new AABB(from,to).inflate(1),e -> e.isAlive() && !e.isSpectator() && (e instanceof LivingEntity || loot.test(e) || e instanceof Projectile)
                        && e.getBoundingBox().inflate(.3).clip(from,to).isPresent())
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(from))).orElse(null);
        Vec3 end = hit != null ? hit.getBoundingBox().getCenter() : to;
        line(level,from.add(look.scale(.8)),end,.3,dust(0xFFD24A,1f));
        if (hit != null) {
            fly(p,hit);
            if (hit instanceof LivingEntity living && foe(p,living)) EffectGraph.entity(p,living,s,s.effects(),from,false);
        }
        for (Entity e : level.getEntities(p,new AABB(end,end).inflate(2.5),loot)) if (e != hit) fly(p,e);
    }
    private static void fly(ServerPlayer p, Entity e) {
        Vec3 d = p.position().add(0,1,0).subtract(e.position());
        Vec3 v = e instanceof LivingEntity ? new Vec3(d.x*.18,Math.clamp(d.y*.1+.35,.2,1.2),d.z*.18) : new Vec3(d.x*.1,d.y*.1+Math.sqrt(Math.sqrt(d.lengthSqr()))*.08,d.z*.1);
        if (v.length() > 2.5) v = v.normalize().scale(2.5);
        e.setDeltaMovement(v); e.hurtMarked = true;
        if (e instanceof Projectile shot) shot.setNoGravity(false);
        EpicMagic.fx(p.level(),ParticleTypes.ENCHANT,e.getX(),e.getY()+.5,e.getZ(),10,.3,.3,.3,.5);
    }

    private record Paused(boolean noGravity) {}
    static void infinity(ServerPlayer p, SpellDefinition s, boolean self) {
        ServerLevel level = p.level();
        double radius = Math.clamp(s.number("radius",3),2,6);
        Map<Projectile,Paused> held = new IdentityHashMap<>();
        Map<UUID,Integer> cooldown = new HashMap<>();
        announce(p,24,"magicka.legend.infinity");
        sound(level,p.position(),SoundEvents.AMETHYST_BLOCK_RESONATE,1.5f,.5f);
        SpellRuntime.timed(p,Math.clamp((int)s.number("duration_ticks",100),20,400),age -> {
            Vec3 c = p.position().add(0,1,0);
            RandomSource r = level.getRandom();
            if (age % 3 == 0) for (int i = 0; i < 16; i++) {
                Vec3 v = new Vec3(r.nextGaussian(),r.nextGaussian(),r.nextGaussian()).normalize().scale(radius);
                EpicMagic.fx(level,dust(0xB8D8FF,.5f),c.x+v.x,c.y+v.y,c.z+v.z,1,0,0,0,0);
            }
            for (Projectile shot : level.getEntitiesOfClass(Projectile.class,new AABB(c,c).inflate(radius+1),e -> e.isAlive() && e.position().distanceToSqr(c) < radius*radius)) {
                if (held.containsKey(shot) || shot.getOwner() == p || shot.getOwner() instanceof LivingEntity owner && EffectGraph.ally(p,owner)) continue;
                held.put(shot,new Paused(shot.isNoGravity()));
                EpicMagic.fx(level,ParticleTypes.REVERSE_PORTAL,shot.getX(),shot.getY(),shot.getZ(),6,.1,.1,.1,.02);
            }
            for (Projectile shot : held.keySet()) { shot.setDeltaMovement(Vec3.ZERO); shot.setNoGravity(true); shot.hurtMarked = true; }
            for (LivingEntity e : near(p,c,2.2)) if (foe(p,e)) {
                Vec3 out = flat(e.position().subtract(p.position()));
                e.setDeltaMovement(out.x*.5,.05,out.z*.5); e.hurtMarked = true;
                if (cooldown.getOrDefault(e.getUUID(),-99) <= age) { cooldown.put(e.getUUID(),age+20); EffectGraph.entity(p,e,s,s.effects(),c,false); }
            }
        },() -> held.forEach((shot,paused) -> { if (shot.isAlive()) { shot.setNoGravity(paused.noGravity()); shot.hurtMarked = true; } }));
    }

    static void auraScale(ServerPlayer p, SpellDefinition s) {
        ServerLevel level = p.level();
        LivingEntity aimed = aimed(p,24);
        if (!(aimed instanceof Mob mob) || mob.getType().builtInRegistryHolder().is(Tags.EntityTypes.BOSSES) || !foe(p,mob)) {
            p.sendOverlayMessage(Component.translatable("magicka.legend.aura_none"));
            return;
        }
        Vec3 top = p.position().lerp(mob.position(),.5).add(0,Math.max(p.getBbHeight(),mob.getBbHeight())+1.2,0);
        Vec3 across = side(mob.position().subtract(p.position()));
        boolean obey = mob.getHealth() <= p.getHealth();
        Vec3 mine = top.add(across.scale(-.8)).add(0,obey ? -.3 : .3,0), theirs = top.add(across.scale(.8)).add(0,obey ? .3 : -.3,0);
        line(level,top.add(0,.8,0),top,.15,dust(0xFFD24A,.8f));
        line(level,mine,theirs,.12,dust(0xFFD24A,.8f));
        EpicMagic.fx(level,dust(0xFFE9A8,1.5f),mine.x,mine.y-.2,mine.z,8,.2,.05,.2,0);
        EpicMagic.fx(level,dust(0x7A4AFF,1.5f),theirs.x,theirs.y-.2,theirs.z,8,.2,.05,.2,0);
        sound(level,top,SoundEvents.BELL_BLOCK,1.5f,obey ? .8f : 1.4f);
        if (!obey) {
            EffectGraph.entity(p,mob,s,s.effects(),p.position(),false);
            p.sendOverlayMessage(Component.translatable("magicka.legend.aura_fail"));
            return;
        }
        announce(p,24,"magicka.legend.aura_obey");
        charm(p,mob,Math.clamp((int)s.number("duration_ticks",600),20,6000),true);
    }
    private static void charm(ServerPlayer p, Mob mob, int ticks, boolean servant) {
        ServerLevel level = p.level();
        UUID id = mob.getUUID();
        Map<UUID,UUID> registry = servant ? CHARMED : CONFUSED;
        registry.put(id,p.getUUID());
        mob.setTarget(null);
        SpellRuntime.timed(p,ticks,age -> {
            if (!mob.isAlive()) return;
            if (age % 5 == 0) {
                LivingEntity t = mob.getTarget();
                if (t == null || !t.isAlive() || t == mob || t instanceof Player || servant && !foe(p,t)) {
                    t = level.getEntitiesOfClass(LivingEntity.class,mob.getBoundingBox().inflate(14),e -> e != mob && e.isAlive() && e instanceof Enemy && !(e instanceof Player) && (!servant || foe(p,e)))
                            .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(mob))).orElse(null);
                    mob.setTarget(t);
                }
                if (servant && t == null && mob.distanceToSqr(p) > 25) mob.getNavigation().moveTo(p,1.2);
            }
            if (age % 10 == 0) EpicMagic.fx(level,dust(servant ? 0xFFD24A : 0x9FFFE0,.9f),mob.getX(),mob.getY()+mob.getBbHeight()+.4,mob.getZ(),4,.25,.1,.25,0);
        },() -> { if (p.getUUID().equals(registry.get(id))) registry.remove(id); if (mob.isAlive()) mob.setTarget(null); });
    }

    static void judradjim(ServerPlayer p, SpellDefinition s, boolean self) {
        ServerLevel level = p.level();
        Vec3 at = self ? p.position() : EpicMagic.focus(p);
        double radius = Math.clamp(s.number("radius",4),2,8);
        int charge = 30;
        announce(p,32,"magicka.legend.judradjim");
        SpellRuntime.repeat(p,charge+10,age -> {
            RandomSource r = level.getRandom();
            if (age < charge) {
                for (int i = 0; i < 10; i++) {
                    double a = age*.15+i*Math.PI*2/10, d = 1+r.nextDouble()*radius;
                    EpicMagic.fx(level,ParticleTypes.LARGE_SMOKE,at.x+Math.cos(a)*d,at.y+11,at.z+Math.sin(a)*d,1,.2,.2,.2,0);
                }
                if (age % 3 == 0) EpicMagic.fx(level,ParticleTypes.ELECTRIC_SPARK,at.x,at.y+10.5,at.z,6,radius*.6,.3,radius*.6,.1);
                if (age % 2 == 0) for (int i = 0; i < 24; i++) {
                    double a = i*Math.PI*2/24;
                    EpicMagic.fx(level,dust(0xAFC8FF,1f),at.x+Math.cos(a)*radius,at.y+.15,at.z+Math.sin(a)*radius,1,0,0,0,0);
                }
                if (age % 10 == 0) sound(level,at,SoundEvents.LIGHTNING_BOLT_THUNDER,1f,1.6f);
                return;
            }
            if (age != charge) return;
            for (int i = 0; i < 3; i++) {
                var bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.MOB_SUMMONED);
                if (bolt == null) continue;
                double a = i*Math.PI*2/3;
                bolt.snapTo(at.x+(i == 0 ? 0 : Math.cos(a)*1.5),at.y,at.z+(i == 0 ? 0 : Math.sin(a)*1.5));
                bolt.setVisualOnly(true); level.addFreshEntity(bolt);
            }
            sound(level,at,SoundEvents.LIGHTNING_BOLT_THUNDER,4f,.7f);
            sound(level,at,SoundEvents.LIGHTNING_BOLT_IMPACT,2f,.8f);
            EpicMagic.fx(level,net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH,-1),at.x,at.y+1,at.z,2,0,0,0,0);
            EpicMagic.fx(level,ParticleTypes.ELECTRIC_SPARK,at.x,at.y+.5,at.z,80,radius*.5,.5,radius*.5,.4);
            for (LivingEntity e : near(p,at.add(0,1,0),radius)) if (foe(p,e)) EffectGraph.entity(p,e,s,s.effects(),at,false);
        });
    }

    private static boolean inCone(Vec3 origin, Vec3 dir, Vec3 point, double cos, boolean all) {
        Vec3 to = point.subtract(origin);
        return all || to.lengthSqr() < 1 || to.normalize().dot(dir) >= cos;
    }
    private static void wave(ServerPlayer p, SpellDefinition s, boolean self, double angle, java.util.function.BiConsumer<Vec3,Double> front, java.util.function.BiConsumer<LivingEntity,Vec3> hit) {
        Vec3 origin = p.getEyePosition().add(0,-.3,0), dir = p.getLookAngle();
        double range = Math.clamp(s.number("radius",7),3,16), cos = Math.cos(Math.toRadians(angle));
        Set<UUID> struck = new HashSet<>();
        SpellRuntime.repeat(p,6,age -> {
            double r = range*(age+1)/6;
            RandomSource random = p.level().getRandom();
            for (int i = 0; i < 24; i++) {
                Vec3 v = new Vec3(random.nextGaussian(),random.nextGaussian()*(self ? .3 : 1),random.nextGaussian()).normalize();
                if (!self) { v = dir.add(v.scale(.6)).normalize(); if (v.dot(dir) < cos) continue; }
                front.accept(origin.add(v.scale(r)),r);
            }
            for (LivingEntity e : near(p,origin,r)) {
                Vec3 c = e.getBoundingBox().getCenter();
                if (!foe(p,e) || struck.contains(e.getUUID()) || !inCone(origin,dir,c,cos,self)) continue;
                struck.add(e.getUUID());
                hit.accept(e,c.subtract(origin).lengthSqr() < .01 ? dir : c.subtract(origin).normalize());
            }
        });
    }

    static void igni(ServerPlayer p, SpellDefinition s, boolean self) {
        ServerLevel level = p.level();
        announce(p,24,"magicka.legend.igni");
        sound(level,p.position(),SoundEvents.BLAZE_SHOOT,1.2f,.8f);
        sound(level,p.position(),SoundEvents.FIRECHARGE_USE,1f,.7f);
        wave(p,s,self,30,(at,r) -> EpicMagic.fx(level,ParticleTypes.FLAME,at.x,at.y,at.z,1,.1,.1,.1,.02),
                (e,dir) -> { EffectGraph.entity(p,e,s,s.effects(),p.position(),false); e.igniteForSeconds(4); });
    }

    static void aard(ServerPlayer p, SpellDefinition s, boolean self) {
        ServerLevel level = p.level();
        int[] budget = {24};
        announce(p,24,"magicka.legend.aard");
        sound(level,p.position(),SoundEvents.ENDER_DRAGON_FLAP,1.5f,1.4f);
        sound(level,p.position(),SoundEvents.PLAYER_ATTACK_SWEEP,1.5f,.6f);
        wave(p,s,self,35,(at,r) -> {
            EpicMagic.fx(level,ParticleTypes.CLOUD,at.x,at.y,at.z,0,0,0,0,0);
            if (budget[0] > 0 && WorldMagic.shatterWeak(p,BlockPos.containing(at),.6f)) budget[0]--;
        }, (e,dir) -> {
            EffectGraph.entity(p,e,s,s.effects(),p.position(),false);
            e.push(dir.x*1.5,.4,dir.z*1.5); e.hurtMarked = true;
        });
    }

    static void quen(ServerPlayer p, SpellDefinition s) {
        ServerLevel level = p.level();
        int ticks = Math.clamp((int)s.number("duration_ticks",400),20,2400);
        p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,ticks,1));
        float[] last = {p.getAbsorptionAmount()};
        int[] ready = {0};
        announce(p,24,"magicka.legend.quen");
        sound(level,p.position(),SoundEvents.ARMOR_EQUIP_GOLD.value(),1.5f,.8f);
        SpellRuntime.sustain(p,age -> {
            float now = p.getAbsorptionAmount();
            if (now < last[0]-.01f && age >= ready[0]) {
                LivingEntity attacker = p.getLastHurtByMob();
                if (attacker != null && attacker.distanceToSqr(p) < 64 && foe(p,attacker)) {
                    ready[0] = age+10;
                    SpellVfx.cue(p,s,SpellVfx.ARC,p.position().add(0,1,0),attacker.getBoundingBox().getCenter(),.15,8);
                    EffectGraph.entity(p,attacker,s,s.effects(),p.position(),false);
                    sound(level,attacker.position(),SoundEvents.LIGHTNING_BOLT_IMPACT,.8f,1.6f);
                }
            }
            last[0] = now;
            if (age % 3 == 0) for (int i = 0; i < 10; i++) {
                double a = age*.1+i*Math.PI*2/10;
                EpicMagic.fx(level,dust(0xFFB547,.8f),p.getX()+Math.cos(a)*1.1,p.getY()+.3+(i % 3)*.7,p.getZ()+Math.sin(a)*1.1,1,0,0,0,0);
            }
            if (now > 0 && p.hasEffect(MobEffects.ABSORPTION)) return false;
            p.removeEffect(MobEffects.ABSORPTION);
            EpicMagic.fx(level,dust(0xFFB547,1.2f),p.getX(),p.getY()+1,p.getZ(),30,.6,.8,.6,0);
            sound(level,p.position(),SoundEvents.SHIELD_BREAK.value(),1f,1.2f);
            return true;
        });
    }

    static void axii(ServerPlayer p, SpellDefinition s) {
        ServerLevel level = p.level();
        int ticks = Math.clamp((int)s.number("duration_ticks",160),20,1200);
        announce(p,24,"magicka.legend.axii");
        sound(level,p.position(),SoundEvents.ILLUSIONER_PREPARE_MIRROR,1.2f,1.2f);
        wave(p,s,false,25,(at,r) -> EpicMagic.fx(level,dust(0x9FFFE0,.8f),at.x,at.y,at.z,1,0,0,0,0), (e,dir) -> {
            EffectGraph.entity(p,e,s,s.effects(),p.position(),false);
            if (e instanceof Mob mob && e instanceof Enemy && !mob.getType().builtInRegistryHolder().is(Tags.EntityTypes.BOSSES)) charm(p,mob,ticks,false);
        });
    }

    static void yrden(ServerPlayer p, SpellDefinition s, boolean self) {
        ServerLevel level = p.level();
        Vec3 center = self ? p.position() : EpicMagic.focus(p);
        double radius = Math.clamp(s.number("radius",4),2,10);
        announce(p,24,"magicka.legend.yrden");
        sound(level,center,SoundEvents.ENCHANTMENT_TABLE_USE,1.5f,.6f);
        SpellRuntime.repeat(p,Math.max(20,(int)s.number("duration_ticks",240)),age -> {
            if (age % 2 == 0) {
                for (int i = 0; i < 32; i++) {
                    double a = i*Math.PI*2/32;
                    EpicMagic.fx(level,dust(0xB57BFF,1f),center.x+Math.cos(a)*radius,center.y+.15,center.z+Math.sin(a)*radius,1,0,0,0,0);
                }
                for (int k = 0; k < 3; k++) {
                    double a = age*.05+k*Math.PI*2/3;
                    Vec3 rune = center.add(Math.cos(a)*radius,.15,Math.sin(a)*radius);
                    line(level,rune,rune.add(0,1.2,0),.2,dust(0xD9B8FF,.8f));
                }
            }
            if (age % 5 != 0) return;
            for (LivingEntity e : near(p,center,radius)) if (foe(p,e)) {
                e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,15,2));
                e.addEffect(new MobEffectInstance(MobEffects.GLOWING,15,0));
                if (age % 20 == 0) EffectGraph.entity(p,e,s,s.effects(),center,false);
            }
            for (Projectile shot : level.getEntitiesOfClass(Projectile.class,new AABB(center,center).inflate(radius,3,radius)))
                if (shot.getOwner() instanceof LivingEntity owner && foe(p,owner)) { shot.setDeltaMovement(shot.getDeltaMovement().scale(.5)); shot.hurtMarked = true; }
        });
    }

    static void portal(ServerPlayer p, SpellDefinition s, boolean self) {
        ServerLevel level = p.level();
        Vec3 fwd = flat(p.getLookAngle()), across = new Vec3(-fwd.z,0,fwd.x);
        Vec3 a = p.position().add(fwd.scale(2.5)).add(0,1.1,0);
        Vec3 b = (self ? home(p) : destination(p)).add(0,1.1,0);
        Map<UUID,Integer> cooldown = new HashMap<>();
        cooldown.put(p.getUUID(),10);
        sound(level,a,SoundEvents.BEACON_ACTIVATE,1.2f,1.6f);
        sound(level,a,SoundEvents.FIRECHARGE_USE,1f,1.2f);
        SpellRuntime.repeat(p,Math.max(20,(int)s.number("duration_ticks",240)),age -> {
            ring(level,a,fwd,across,age);
            ring(level,b,fwd,across,age);
            travel(p,s,a,b.add(fwd.scale(1.3)),cooldown,age);
            travel(p,s,b,a.subtract(fwd.scale(1.3)),cooldown,age);
        });
    }
    private static Vec3 destination(ServerPlayer p) {
        var hit = SpellAim.hit(p);
        Vec3 at = hit.getLocation();
        if (hit.getType() != HitResult.Type.MISS && hit.getDirection() != Direction.UP)
            at = at.add(Vec3.atLowerCornerOf(hit.getDirection().getUnitVec3i()).scale(1.2));
        Vec3 ground = EpicMagic.drop(p.level(),at.add(0,.5,0),hit.getType() == HitResult.Type.MISS ? 320 : 64,p);
        return ground == null ? at : ground;
    }
    private static Vec3 home(ServerPlayer p) {
        ServerLevel level = p.level();
        var respawn = p.getRespawnConfig();
        BlockPos bed = respawn == null ? null : respawn.respawnData().pos();
        if (bed != null && respawn.respawnData().dimension() == level.dimension()) return Vec3.atBottomCenterOf(bed).add(0,.6,0);
        return Vec3.atBottomCenterOf(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,level.getRespawnData().pos()));
    }
    private static void ring(ServerLevel level, Vec3 c, Vec3 fwd, Vec3 across, int age) {
        if (age % 2 != 0) return;
        for (int i = 0; i < 24; i++) {
            double t = age*.12+i*Math.PI*2/24;
            Vec3 at = c.add(across.scale(Math.cos(t)*1.2)).add(0,Math.sin(t)*1.4,0);
            EpicMagic.fx(level,dust(i % 4 == 0 ? 0xFFE28A : 0xFF9A2E,1f),at.x,at.y,at.z,1,0,0,0,0);
            if (i % 6 == 0) EpicMagic.fx(level,ParticleTypes.SMALL_FLAME,at.x,at.y,at.z,0,-Math.sin(t)*across.x*.1,Math.cos(t)*.1,-Math.sin(t)*across.z*.1,1);
        }
        EpicMagic.fx(level,dust(0xFFD28A,.5f),c.x,c.y,c.z,4,across.x*.6+fwd.x*.05,.7,across.z*.6+fwd.z*.05,0);
    }
    private static void travel(ServerPlayer p, SpellDefinition s, Vec3 gate, Vec3 exit, Map<UUID,Integer> cooldown, int age) {
        ServerLevel level = p.level();
        List<Entity> passing = new ArrayList<>(level.getEntities((Entity)null,new AABB(gate,gate).inflate(1.5),e -> e.isAlive() && !e.isSpectator() && !e.isPassenger() && e.getBoundingBox().getCenter().distanceToSqr(gate) < 1.44));
        if (!passing.contains(p) && p.isAlive() && p.getBoundingBox().getCenter().distanceToSqr(gate) < 1.44) passing.add(p);
        for (Entity e : passing) {
            if (cooldown.getOrDefault(e.getUUID(),-99) > age) continue;
            cooldown.put(e.getUUID(),age+30);
            Vec3 to = clear(level,e,exit.add(0,-e.getBbHeight()/2,0));
            if (to == null) continue;
            if (e instanceof ServerPlayer player) player.teleportTo(level,to.x,to.y,to.z,java.util.Set.of(),player.getYRot(),player.getXRot(),false);
            else e.teleportTo(to.x,to.y,to.z);
            e.fallDistance = 0; e.hurtMarked = true;
            if (e instanceof LivingEntity living) EffectGraph.entity(p,living,s,s.effects(),gate,false);
            sound(level,to,SoundEvents.ENDERMAN_TELEPORT,.8f,1.2f);
            EpicMagic.fx(level,ParticleTypes.FLAME,to.x,to.y+e.getBbHeight()/2,to.z,12,.3,.5,.3,.05);
        }
    }

    private static Vec3 clear(ServerLevel level, Entity e, Vec3 want) {
        if (level.noCollision(e,e.getDimensions(e.getPose()).makeBoundingBox(want))) return want;
        Vec3 best = null; double bestScore = Double.MAX_VALUE;
        for (int dy = -2; dy <= 3; dy++) for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            Vec3 at = new Vec3(Math.floor(want.x)+dx+.5,Math.floor(want.y)+dy,Math.floor(want.z)+dz+.5);
            double score = at.distanceToSqr(want)+(dy < 0 ? 1 : 0);
            if (score >= bestScore || !level.noCollision(e,e.getDimensions(e.getPose()).makeBoundingBox(at))) continue;
            BlockPos below = BlockPos.containing(at).below();
            boolean floor = !level.getBlockState(below).getCollisionShape(level,below).isEmpty();
            if (!floor) score += 4;
            if (score < bestScore) { best = at; bestScore = score; }
        }
        return best;
    }

    private LegendArts() {}
}
