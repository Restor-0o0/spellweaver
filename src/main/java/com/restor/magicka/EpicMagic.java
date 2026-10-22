package com.restor.magicka;

import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.List;

/** Channelled area ultimates: they live only while the caster keeps the channel open. */
public final class EpicMagic {
    public static final class Channel { boolean live = true; Vec3 focus; }
    static final double RANGE = 48;

    static Channel start(ServerPlayer p, SpellDefinition s) {
        Channel c = new Channel(); c.focus = focus(p);
        switch (SpellDefinition.string(s.delivery(),"epic","storm")) {
            case "meteors" -> meteors(p,s,c);
            case "tornado" -> tornado(p,s,c);
            case "blizzard" -> blizzard(p,s,c);
            case "titan" -> ElementalTitan.rise(p,s,c);
            default -> storm(p,s,c);
        }
        ServerLevel level = p.level();
        level.playSound(null,p.blockPosition(),SoundEvents.ENDER_DRAGON_GROWL,SoundSource.PLAYERS,.7f,1.3f);
        level.playSound(null,c.focus.x,c.focus.y,c.focus.z,SoundEvents.LIGHTNING_BOLT_THUNDER,SoundSource.PLAYERS,2f,.6f);
        p.swing(net.minecraft.world.InteractionHand.MAIN_HAND,true);
        return c;
    }
    static Vec3 focus(ServerPlayer p) {
        Vec3 eye = p.getEyePosition(), at = SpellAim.hit(p).getLocation();
        if (at.distanceToSqr(eye) > RANGE*RANGE) at = eye.add(p.getLookAngle().scale(RANGE));
        Vec3 ground = drop(p.level(),at.add(0,.5,0),32,p);
        return ground == null ? at : ground;
    }
    static final int SKY = 10;
    static boolean cramped(ServerPlayer p) {
        Vec3 f = focus(p); int open = 0;
        for (int i = 0; i < 5; i++) if (clearance(p.level(),f.add(i == 1 ? 4 : i == 2 ? -4 : 0,0,i == 3 ? 4 : i == 4 ? -4 : 0),SKY) >= SKY) open++;
        return open < 3;
    }
    static Vec3 drop(ServerLevel level, Vec3 from, double depth, Entity source) {
        if (!level.hasChunkAt(BlockPos.containing(from))) return null;
        var hit = level.clip(new ClipContext(from,from.add(0,-depth,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,source));
        return hit.getType() == HitResult.Type.MISS ? null : hit.getLocation();
    }
    static int clearance(ServerLevel level, Vec3 at, int max) {
        BlockPos base = BlockPos.containing(at);
        for (int dy = 1; dy <= max; dy++) {
            var state = level.getBlockState(base.above(dy));
            if (!state.is(net.minecraft.world.level.block.Blocks.BARRIER) && !state.getCollisionShape(level,base.above(dy)).isEmpty()) return dy-1;
        }
        return max;
    }
    static List<LivingEntity> area(ServerPlayer p, Vec3 center, double radius, double below, double above) {
        return p.level().getEntitiesOfClass(LivingEntity.class,new AABB(center.x-radius,center.y-below,center.z-radius,center.x+radius,center.y+above,center.z+radius),
                e -> e != p && e.isAlive() && !e.isSpectator() && square(e.getX()-center.x)+square(e.getZ()-center.z) <= radius*radius);
    }
    private static double square(double v) { return v*v; }
    private static Vec3 scatter(RandomSource r, Vec3 center, double radius, double y) {
        double a = r.nextDouble()*Math.PI*2, d = Math.sqrt(r.nextDouble())*radius;
        return new Vec3(center.x+Math.cos(a)*d,y,center.z+Math.sin(a)*d);
    }
    private static LivingEntity prey(ServerPlayer p, List<LivingEntity> in, RandomSource r) {
        var foes = in.stream().filter(e -> e instanceof Enemy && EffectGraph.canHarm(p,e)).toList();
        if (foes.isEmpty()) foes = in.stream().filter(e -> EffectGraph.canHarm(p,e) && !EffectGraph.ally(p,e)).toList();
        return foes.isEmpty() ? null : foes.get(r.nextInt(foes.size()));
    }
    private static Vec3 flat(Vec3 look) {
        Vec3 v = new Vec3(look.x,0,look.z);
        return v.lengthSqr() < 1e-4 ? new Vec3(1,0,0) : v.normalize();
    }
    static void fx(ServerLevel level, ParticleOptions type, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
        for (ServerPlayer viewer : level.players()) if (viewer.distanceToSqr(x,y,z) < 128*128) level.sendParticles(viewer,type,true,false,x,y,z,count,dx,dy,dz,speed);
    }
    private static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null,at.x,at.y,at.z,sound,SoundSource.PLAYERS,volume,pitch);
    }
    static void ignite(ServerPlayer p, BlockPos ground) {
        ServerLevel level = p.level();
        if (!MagicConfig.TERRAIN_DAMAGE.get()) return;
        BlockPos up = level.getBlockState(ground).isAir() ? ground : ground.above();
        if (level.isEmptyBlock(up) && BaseFireBlock.canBePlacedAt(level,up,Direction.UP)) WorldMagic.replace(p,up,BaseFireBlock.getState(level,up));
    }

    private static void storm(ServerPlayer p, SpellDefinition s, Channel c) {
        ServerLevel level = p.level(); RandomSource r = level.getRandom();
        double radius = Math.clamp(s.number("radius",12),4,24), cloud = c.focus.y+Math.max(4,clearance(level,c.focus,16)), height = cloud-c.focus.y;
        var dark = new DustParticleOptions(net.minecraft.util.ARGB.colorFromFloat(1f,.22f,.23f,.27f),4f);
        int[] next = {12}, fade = {0};
        SpellRuntime.sustain(p,age -> {
            if (!c.live && ++fade[0] > 30) return true;
            int puffs = c.live ? 14 : 5;
            for (int i = 0; i < puffs; i++) {
                Vec3 q = scatter(r,c.focus,radius*1.1,cloud+r.nextGaussian()*.6);
                fx(level,i%4 == 0 ? ParticleTypes.CLOUD : i%2 == 0 ? dark : ParticleTypes.LARGE_SMOKE,q.x,q.y,q.z,0,r.nextGaussian()*.02,0,r.nextGaussian()*.02,1);
            }
            if (!c.live) return false;
            for (int i = 0; i < 10; i++) { Vec3 q = scatter(r,c.focus,radius,cloud-1); fx(level,ParticleTypes.FALLING_WATER,q.x,q.y,q.z,1,0,0,0,0); }
            if (age%20 == 0) {
                sound(level,c.focus,SoundEvents.WEATHER_RAIN,1.2f,.8f);
                for (var e : area(p,c.focus,radius,4,height+2)) MagicStatuses.apply(e,MagicStatuses.Status.WET,60,1);
            }
            if (age >= next[0]) { next[0] = age+6+r.nextInt(9); strike(p,s,c.focus,cloud,radius); }
            return false;
        });
    }
    static void strike(ServerPlayer p, SpellDefinition s, Vec3 focus, double cloud, double radius) {
        ServerLevel level = p.level(); RandomSource r = level.getRandom();
        LivingEntity target = r.nextFloat() < .65f ? prey(p,area(p,focus,radius,6,cloud-focus.y+2),r) : null;
        Vec3 at = target != null ? target.position() : drop(level,scatter(r,focus,radius,cloud-1),cloud-focus.y+12,p);
        if (at == null) return;
        var bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (bolt != null) { bolt.snapTo(at.x,at.y,at.z); bolt.setVisualOnly(true); level.addFreshEntity(bolt); }
        sound(level,at,SoundEvents.LIGHTNING_BOLT_IMPACT,2f,.9f+r.nextFloat()*.3f);
        fx(level,ParticleTypes.ELECTRIC_SPARK,at.x,at.y+.3,at.z,24,.6,.4,.6,.15);
        for (var e : area(p,at,2.5,1.5,3)) {
            EffectGraph.entity(p,e,s,s.effects(),at,false);
            if (!MagicStatuses.has(e,MagicStatuses.Status.WET) && EffectGraph.canHarm(p,e) && r.nextFloat() < .5f) e.igniteForSeconds(3);
        }
        BlockPos ground = BlockPos.containing(at.x,at.y-.2,at.z);
        if (r.nextFloat() < .35f) {
            WorldMagic.shatterWeak(p,ground,.8f);
            if (r.nextBoolean()) WorldMagic.shatterWeak(p,ground.relative(Direction.Plane.HORIZONTAL.getRandomDirection(r)),.8f);
        }
        if (r.nextFloat() < .4f) ignite(p,ground);
    }

    private static void meteors(ServerPlayer p, SpellDefinition s, Channel c) {
        ServerLevel level = p.level(); RandomSource r = level.getRandom();
        double radius = Math.clamp(s.number("radius",10),3,24);
        Vec3 slant = flat(p.getLookAngle());
        var ember = new DustParticleOptions(net.minecraft.util.ARGB.colorFromFloat(1f,.9f,.35f,.1f),3f);
        int[] next = {0};
        SpellRuntime.sustain(p,age -> {
            if (!c.live) return true;
            for (int i = 0; i < 3; i++) { Vec3 q = scatter(r,c.focus,radius,c.focus.y+Math.max(6,clearance(level,c.focus,22))); fx(level,i == 0 ? ember : ParticleTypes.LARGE_SMOKE,q.x,q.y,q.z,0,0,-.02,0,1); }
            if (age < next[0]) return false;
            next[0] = age+5+r.nextInt(3);
            LivingEntity target = r.nextFloat() < .35f ? prey(p,area(p,c.focus,radius,6,20),r) : null;
            Vec3 at = target != null ? target.position() : drop(level,scatter(r,c.focus,radius,c.focus.y+6),20,p);
            if (at == null) return false;
            double h = Math.max(6,clearance(level,at,26));
            meteor(p,s,at.add(slant.scale(-h*.45)).add(0,h,0),at);
            return false;
        });
    }
    static void meteor(ServerPlayer p, SpellDefinition s, Vec3 start, Vec3 target) {
        ServerLevel level = p.level();
        Vec3 velocity = target.subtract(start).normalize().scale(1.4);
        Vec3[] position = {start};
        sound(level,start,SoundEvents.FIRECHARGE_USE,1.5f,.6f);
        SpellRuntime.sustain(p,age -> {
            Vec3 from = position[0], to = from.add(velocity);
            if (age > 80 || !level.hasChunkAt(BlockPos.containing(to))) return true;
            var hit = level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,p));
            Vec3 end = hit.getLocation(); boolean struck = hit.getType() != HitResult.Type.MISS;
            if (!struck) for (var e : level.getEntitiesOfClass(LivingEntity.class,new AABB(from,to).inflate(.8),e -> e != p && e.isAlive() && !e.isSpectator()))
                if (e.getBoundingBox().inflate(.5).clip(from,to).isPresent()) { struck = true; end = e.position(); break; }
            fx(level,ParticleTypes.FLAME,from.x,from.y,from.z,8,.25,.25,.25,.02);
            fx(level,ParticleTypes.LARGE_SMOKE,from.x,from.y,from.z,3,.2,.2,.2,.01);
            if (age%3 == 0) fx(level,ParticleTypes.LAVA,from.x,from.y,from.z,1,.1,.1,.1,0);
            SpellVfx.cue(p,s,SpellVfx.ORB,from,end,.9,2);
            if (!struck) { position[0] = to; return false; }
            SpellRuntime.impact(p,s,end,s.number("burst_radius",3),false,true);
            fx(level,ParticleTypes.EXPLOSION,end.x,end.y+.5,end.z,3,.8,.4,.8,0);
            fx(level,ParticleTypes.LAVA,end.x,end.y+.3,end.z,10,.6,.2,.6,0);
            sound(level,end,SoundEvents.GENERIC_EXPLODE.value(),1.4f,.7f+level.getRandom().nextFloat()*.3f);
            if (level.getRandom().nextFloat() < .3f) ignite(p,BlockPos.containing(end.x,end.y-.2,end.z));
            return true;
        });
    }

    private static void tornado(ServerPlayer p, SpellDefinition s, Channel c) {
        ServerLevel level = p.level(); RandomSource r = level.getRandom();
        double pull = Math.clamp(s.number("radius",9),3,16);
        Vec3[] base = {c.focus}; int[] fade = {0};
        SpellRuntime.sustain(p,age -> {
            double scale = 1;
            if (!c.live) { if (++fade[0] > 20) return true; scale = 1-fade[0]/20.0; }
            else {
                Vec3 aim = focus(p), delta = new Vec3(aim.x-base[0].x,0,aim.z-base[0].z);
                double length = delta.length();
                Vec3 moved = length > .05 ? base[0].add(delta.scale(Math.min(.2,length)/length)) : base[0];
                Vec3 ground = drop(level,moved.add(0,3,0),8,p);
                base[0] = ground != null ? ground : moved;
            }
            funnel(level,base[0],age,scale);
            if (!c.live) return false;
            Vec3 center = base[0];
            for (var e : area(p,center,pull,2,14)) {
                if (!EffectGraph.canHarm(p,e)) continue;
                boolean core = whirl(e,center,pull);
                if (core && age%10 == 0) EffectGraph.entity(p,e,s,s.effects(),center,false);
            }
            for (var item : level.getEntitiesOfClass(ItemEntity.class,new AABB(center,center).inflate(pull,6,pull))) whirl(item,center,pull);
            if (age%3 == 0) {
                BlockPos column = BlockPos.containing(scatter(r,center,2.5,center.y+2));
                for (int dy = 0; dy < 5; dy++) {
                    BlockPos pos = column.below(dy);
                    if (level.getBlockState(pos).isAir()) continue;
                    WorldMagic.shatterWeak(p,pos,.25f);
                    break;
                }
            }
            if (age%30 == 0) sound(level,center,SoundEvents.ELYTRA_FLYING,1.5f,.5f);
            return false;
        });
    }
    private static boolean whirl(Entity e, Vec3 center, double pull) {
        double dx = center.x-e.getX(), dz = center.z-e.getZ(), dist = Math.max(.3,Math.sqrt(dx*dx+dz*dz));
        Vec3 in = new Vec3(dx/dist,0,dz/dist), spin = new Vec3(-in.z,0,in.x);
        double k = Math.max(0,1-dist/pull);
        Vec3 v = e.getDeltaMovement().add(in.scale(.04+.1*k)).add(spin.scale(.12*k));
        boolean core = dist < 2.2;
        if (core) {
            v = new Vec3(v.x,Math.min(.55,v.y+.14),v.z);
            e.fallDistance = 0;
            if (e.getY()-center.y > 11) v = v.add(in.scale(-1.1)).add(0,.2,0);
        }
        e.setDeltaMovement(v); e.hurtMarked = true;
        return core;
    }
    private static void funnel(ServerLevel level, Vec3 base, int age, double scale) {
        double height = 13*scale;
        var floor = level.getBlockState(BlockPos.containing(base.x,base.y-.2,base.z));
        var dust = new BlockParticleOption(ParticleTypes.BLOCK,floor.isAir() ? Blocks.DIRT.defaultBlockState() : floor);
        for (double y = 0; y < height; y += .8) {
            double radius = (.4+y*.3)*scale;
            for (int k = 0; k < 2; k++) {
                double a = age*.45+y*.55+k*Math.PI, tx = -Math.sin(a), tz = Math.cos(a);
                fx(level,y < 1.6 ? dust : ParticleTypes.CLOUD,base.x+Math.cos(a)*radius,base.y+y,base.z+Math.sin(a)*radius,0,tx*.15,.05,tz*.15,1);
            }
        }
        if (age%4 == 0) fx(level,ParticleTypes.POOF,base.x,base.y+height,base.z,4,height*.25,.3,height*.25,.02);
    }

    private static void blizzard(ServerPlayer p, SpellDefinition s, Channel c) {
        ServerLevel level = p.level(); RandomSource r = level.getRandom();
        double radius = Math.clamp(s.number("radius",11),3,24), top = c.focus.y+Math.max(4,clearance(level,c.focus,10)), height = top-c.focus.y;
        List<JsonObject> shard = SpellDefinition.graph(s.delivery(),"shard");
        Vec3 wind = flat(p.getLookAngle()).scale(.25);
        var ice = new BlockParticleOption(ParticleTypes.BLOCK,Blocks.ICE.defaultBlockState());
        SpellRuntime.freezeSurface(p,c.focus,radius);
        SpellRuntime.sustain(p,age -> {
            if (!c.live) return true;
            for (int i = 0; i < 16; i++) { Vec3 q = scatter(r,c.focus,radius,top-r.nextDouble()*height); fx(level,ParticleTypes.SNOWFLAKE,q.x,q.y,q.z,0,wind.x,-.3,wind.z,1); }
            for (int i = 0; i < 4; i++) { Vec3 q = scatter(r,c.focus,radius,top+r.nextGaussian()*.4); fx(level,ParticleTypes.CLOUD,q.x,q.y,q.z,0,wind.x*.2,0,wind.z*.2,1); }
            if (age%20 == 0) {
                sound(level,c.focus,SoundEvents.ELYTRA_FLYING,1f,1.4f);
                for (var e : area(p,c.focus,radius,4,height+2)) EffectGraph.entity(p,e,s,s.effects(),c.focus,false);
            }
            if (age%4 == 0) {
                Vec3 at = drop(level,scatter(r,c.focus,radius,top-.5),height+8,p);
                if (at == null) return false;
                for (double y = 1; y < 7; y += .7) fx(level,ParticleTypes.SNOWFLAKE,at.x,at.y+y,at.z,0,0,-.9,0,1);
                fx(level,ice,at.x,at.y+.2,at.z,14,.3,.2,.3,.1);
                sound(level,at,SoundEvents.GLASS_BREAK,.6f,1.4f+r.nextFloat()*.3f);
                for (var e : area(p,at,1.5,1,2.5)) EffectGraph.entity(p,e,s,shard,at,false);
            }
            return false;
        });
    }
    private EpicMagic() {}
}
