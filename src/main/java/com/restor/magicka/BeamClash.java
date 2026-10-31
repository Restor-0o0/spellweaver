package com.restor.magicka;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class BeamClash {
    public static final double RADIUS = 4, TOUCH = .8;
    public static final float DAMAGE = 8;
    private static final int MEMORY = 3, COOLDOWN = 10;
    private record Segment(ServerPlayer p, ServerLevel level, Vec3 from, Vec3 to, long time) {}
    private static final List<Segment> SEGMENTS = new ArrayList<>();
    private static final Map<Set<UUID>, Long> LAST = new HashMap<>();

    public static void beam(ServerPlayer p, Vec3 from, Vec3 to) {
        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        SEGMENTS.removeIf(s -> s.time+MEMORY < now || s.p.isRemoved());
        for (Segment other : List.copyOf(SEGMENTS)) {
            if (other.p == p || other.level != level) continue;
            Vec3 at = crossing(from,to,other.from,other.to);
            if (at == null) continue;
            Set<UUID> pair = Set.of(p.getUUID(),other.p.getUUID());
            if (LAST.getOrDefault(pair,-99L)+COOLDOWN > now) continue;
            LAST.put(pair,now);
            blast(level,at,p,other.p);
            CastingServer.interrupt(p);
            CastingServer.interrupt(other.p);
        }
        SEGMENTS.add(new Segment(p,level,from,to,now));
    }

    static Vec3 crossing(Vec3 a0, Vec3 a1, Vec3 b0, Vec3 b1) {
        Vec3 u = a1.subtract(a0), v = b1.subtract(b0), w = a0.subtract(b0);
        double a = u.dot(u), b = u.dot(v), c = v.dot(v), d = u.dot(w), e = v.dot(w), den = a*c-b*b;
        if (a < 1e-6 || c < 1e-6) return null;
        double s = den < 1e-9 ? 0 : Math.clamp((b*e-c*d)/den,0,1);
        double t = (b*s+e)/c;
        if (t < 0) { t = 0; s = Math.clamp(-d/a,0,1); }
        else if (t > 1) { t = 1; s = Math.clamp((b-d)/a,0,1); }
        Vec3 pa = a0.add(u.scale(s)), pb = b0.add(v.scale(t));
        return pa.distanceToSqr(pb) <= TOUCH*TOUCH ? pa.add(pb).scale(.5) : null;
    }

    static void blast(ServerLevel level, Vec3 at, ServerPlayer a, ServerPlayer b) {
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,at.x,at.y,at.z,1,0,0,0,0);
        level.sendParticles(ParticleTypes.END_ROD,at.x,at.y,at.z,40,.3,.3,.3,.35);
        level.playSound(null,at.x,at.y,at.z,SoundEvents.GENERIC_EXPLODE.value(),SoundSource.PLAYERS,1.2f,1.2f);
        List<LivingEntity> victims = new ArrayList<>(SpellRuntime.near(a,at,RADIUS));
        for (ServerPlayer owner : List.of(a,b)) if (!victims.contains(owner) && owner.getBoundingBox().distanceToSqr(at) <= RADIUS*RADIUS) victims.add(owner);
        for (LivingEntity e : victims) {
            boolean owner = e == a || e == b;
            if (owner && !MagicConfig.selfHarm()) continue;
            double falloff = 1-.5*Math.min(1,Math.sqrt(e.getBoundingBox().distanceToSqr(at))/RADIUS);
            float damage = (float)(DAMAGE*falloff);
            if (e instanceof Player) damage *= MagicConfig.PLAYER_DAMAGE.get().floatValue();
            if (owner) damage *= MagicConfig.selfHarmScale();
            ServerPlayer cause = e == a ? b : a;
            e.invulnerableTime = 0;
            com.restor.magicka.enemy.MagicEnemies.hurt(e,cause.damageSources().indirectMagic(cause,cause),damage,cause,null,false);
            Vec3 push = e.position().subtract(at).multiply(1,0,1);
            push = push.lengthSqr() < 1e-4 ? new Vec3(1,0,0) : push.normalize();
            e.push(push.x*1.6*falloff,.55,push.z*1.6*falloff);
            e.hurtMarked = true;
        }
    }

    private BeamClash() {}
}
