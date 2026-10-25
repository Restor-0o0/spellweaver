package com.restor.magicka;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import java.util.*;

/** Server-owned, bounded runtime instances. Vanilla particles carry their visuals to every observer. */
public final class SpellRuntime {
    private interface Active { ServerPlayer caster(); boolean tick(); default void dispose() {} default void charge(Vec3 point,double radius) {} }
    private static final List<Active> ACTIVE = new ArrayList<>(), PENDING = new ArrayList<>();
    private static final Map<UUID, ServerPlayer> OWNERS = new HashMap<>();
    private static final Map<Active,ServerLevel> LEVELS = new IdentityHashMap<>();
    private static boolean add(Active value) {
        if (ACTIVE.size()+PENDING.size() >= 2048 || java.util.stream.Stream.concat(ACTIVE.stream(),PENDING.stream())
                .filter(a -> a.caster().getUUID().equals(value.caster().getUUID())).count() >= MagicConfig.MAX_RUNTIME.get()) { value.dispose(); return false; }
        OWNERS.put(value.caster().getUUID(), value.caster()); LEVELS.put(value,value.caster().serverLevel());PENDING.add(value); return true;
    }
    public static ServerPlayer owner(ServerLevel level, UUID id) {
        var player = level.getServer().getPlayerList().getPlayer(id);
        if (player == null) player = OWNERS.get(id);
        return player != null && player.level() == level && !player.isRemoved() ? player : null;
    }
    public static Vec3 origin(Player caster, OriginMode mode) {
        Vec3 eye = caster.getEyePosition(), look = caster.getLookAngle();
        return switch(mode) {
            case CASTER -> caster.position();
            case CASTER_FEET, UNDER_TARGET -> caster.position();
            case CASTER_FRONT -> eye.add(look.scale(.7));
            default -> SpellAim.hit(caster).getLocation();
        };
    }
    private static final double UNLIMITED = 4096;
    /** True when no other element outnumbers this one, so mixed spells keep the behaviour of their main element. */
    private static boolean leads(SpellDefinition s, Element element) {
        var f = s.formula(); int count = f.count(element);
        if (count == 0) return false;
        for (Element other : Element.values()) if (other != element && f.count(other) > count) return false;
        return true;
    }
    static double reach(ServerPlayer p, SpellDefinition s) {
        if (s.shape()!=SpellShape.SPRAY || !leads(s,Element.FIRE)) return UNLIMITED;
        return Math.clamp((3+3*s.formula().fire())*MagicStats.power(p,MagicStats.Stat.FIRE),4,64);
    }
    private static double speed(SpellDefinition s) { return Math.clamp(s.number("speed",.8)*(leads(s,Element.EARTH)?2:1),.1,4); }
    private static double gravity(SpellDefinition s) { return s.number("gravity",0)*(leads(s,Element.EARTH)?.25:1); }
    private static List<LivingEntity> near(ServerPlayer p, Vec3 point, double radius) {
        return p.serverLevel().getEntitiesOfClass(LivingEntity.class,new AABB(point,point).inflate(radius),
                e -> e.isAlive() && !e.isSpectator() && e.getBoundingBox().distanceToSqr(point) <= radius*radius);
    }
    static LivingEntity target(ServerPlayer p) {
        return SpellAim.entity(p,SpellAim.hit(p).getLocation());
    }
    public static void cast(ServerPlayer p, SpellDefinition s, boolean self, boolean instant) {
        MagicConstructionManager.instantBuilds = instant;
        try { cast(p, s, self); } finally { MagicConstructionManager.instantBuilds = false; }
    }
    static void pulse(ServerPlayer p, SpellDefinition s) {
        switch(s.shape()) {
            case BEAM -> beam(p,s,false);
            case SPRAY -> beam(p,s,true);
            case CHAIN -> lightning(p,s);
            default -> cast(p,s,false);
        }
    }
    public static void cast(ServerPlayer p, SpellDefinition s, boolean self) {
        OWNERS.put(p.getUUID(),p);
        p.serverLevel().playSound(null,p.blockPosition(),net.minecraft.sounds.SoundEvents.EVOKER_CAST_SPELL,net.minecraft.sounds.SoundSource.PLAYERS,.6f,1.1f);
        if (s.formula().size() >= 5) {
            p.serverLevel().playSound(null,p.blockPosition(),net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL,net.minecraft.sounds.SoundSource.PLAYERS,.7f,1.3f);
            p.serverLevel().playSound(null,p.blockPosition(),net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER,net.minecraft.sounds.SoundSource.PLAYERS,.8f,.7f);
        }
        p.swing(net.minecraft.world.InteractionHand.MAIN_HAND,true);
        Vec3 point = origin(p,s.origin());
        if(s.origin()!=OriginMode.CASTER && s.origin()!=OriginMode.CASTER_FEET && s.origin()!=OriginMode.CASTER_FRONT){
            LivingEntity aimed=target(p);if(aimed!=null)point=aimed.position();
        }
        if (LegendMagic.handles(s,self)) { LegendMagic.cast(p,s,self); return; }
        if (self) {
            switch(s.selfMode()) {
                case "nova" -> impact(p,s,p.position(),s.number("radius",3),true,true);
                case "zone" -> zone(p,s,p.position(),(int)s.number("duration_ticks",160),s.number("radius",3),true,false);
                case "construct" -> { EffectGraph.entity(p,p,s,s.selfEffects(),p.position(),true);MagicConstructionManager.start(p,s,true,p.position()); }
                case "summon" -> summon(p,s,p.position(),true);
                case "dash" -> dash(p,s);
                case "meteor" -> meteor(p,s,p.position(),true);
                default -> { EffectGraph.entity(p,p,s,s.selfEffects(),p.position(),true); EffectGraph.world(p,s,s.selfEffects(),p.position(),true,true); }
            }
            return;
        }
        double reach = reach(p,s);
        if (point.distanceToSqr(p.getEyePosition()) > reach*reach) point = p.getEyePosition().add(p.getLookAngle().scale(reach));
        if (s.shape().construction()) { MagicConstructionManager.start(p,s,false,point); return; }
        if (s.delivery().has("meteor")) { meteor(p,s,point,false); return; }
        if (Deluge.handles(s)) { Deluge.cast(p,s); return; }
        switch(s.shape()) {
            case PROJECTILE, BURST_PROJECTILE -> fan(p,s);
            case BEAM -> beam(p,s,false);
            case SPRAY -> beam(p,s,true);
            case CHAIN -> lightning(p,s);
            case TARGETED_EXPLOSION -> impact(p,s,point,s.number("radius",3),false,true);
            case TARGETED_ZONE -> { if(Fissure.handles(s))Fissure.open(p,s,point); zone(p,s,point,(int)s.number("duration_ticks",160),s.number("radius",3),false,false); }
            case NOVA -> impact(p,s,p.position(),s.number("radius",3),false,true);
            case DIRECT_TARGET -> { LivingEntity victim=target(p); if(victim != null && victim.distanceToSqr(p.getEyePosition())<=reach*reach) { point=victim.position(); EffectGraph.entity(p,victim,s,s.effects(),p.position(),false); } EffectGraph.world(p,s,s.effects(),point,false,true); }
            case DASH -> dash(p,s);
            case SUMMON_OBJECT -> summon(p,s,point,false);
            case MINE -> zone(p,s,point,(int)s.number("duration_ticks",200),s.number("radius",3),false,true);
            default -> throw new IllegalStateException("Unhandled shape " + s.shape());
        }
    }
    static void particles(ServerPlayer p, SpellDefinition s, Vec3 point, int count, double spread) {
        if (!p.serverLevel().hasChunkAt(BlockPos.containing(point))) return;
        int color = s.formula().elements().getFirst().color;
        p.serverLevel().sendParticles(new DustParticleOptions(new org.joml.Vector3f(((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f),1.1f),
                point.x,point.y,point.z,count,spread,spread,spread,.02);
        SpellVfx.particles(p,s,point,Math.max(3,count/2),spread);
    }
    public static void impact(ServerPlayer p, SpellDefinition s, Vec3 point, double radius, boolean self, boolean spawn) {
        radius=Math.clamp(radius,.4,32);
        List<JsonObject> graph=self?s.selfEffects():s.effects();
        for (LivingEntity e:near(p,point,radius)) if (e != p || self) EffectGraph.entity(p,e,s,graph,point,self);
        EffectGraph.world(p,s,graph,point,self,spawn); particles(p,s,point,18,radius/3);
        SpellVfx.cue(p,s,SpellVfx.IMPACT,point,point,radius,12);SpellVfx.sound(p,s,point,true);
    }
    private static Vec3 beamEnd(ServerPlayer p,double reach) {
        Vec3 from=p.getEyePosition(); double range=Math.min(reach,from.distanceTo(SpellAim.hit(p).getLocation()));
        return p.serverLevel().clip(new ClipContext(from,from.add(p.getLookAngle().scale(range)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getLocation();
    }
    private static List<LivingEntity> along(ServerPlayer p,Vec3 from,Vec3 end,boolean spray) {
        Vec3 look=end.subtract(from).normalize(); double length=from.distanceTo(end);
        Set<LivingEntity> candidates=new HashSet<>();
        for(double d=0;d<length+.5;d+=8)candidates.addAll(p.serverLevel().getEntitiesOfClass(LivingEntity.class,new AABB(from.add(look.scale(d)),from.add(look.scale(Math.min(length+.5,d+8)))).inflate(spray?4:1)));
        List<LivingEntity> hits=new ArrayList<>();
        for(LivingEntity e:candidates) {
            if(e==p || !e.isAlive() || e.isSpectator())continue;
            Vec3 delta=e.getBoundingBox().getCenter().subtract(from); double at=delta.dot(look);
            if(at<0 || at>length+.4 || delta.subtract(look.scale(at)).length()> (spray?Math.min(3,.5+at*.3):.65+e.getBbWidth()*.5))continue;
            if(p.serverLevel().clip(new ClipContext(from,e.getBoundingBox().getCenter(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()!=HitResult.Type.MISS)continue;
            hits.add(e);
        }
        hits.sort(Comparator.comparingDouble(e->e.distanceToSqr(from)));
        return hits;
    }
    private static void beam(ServerPlayer p,SpellDefinition s,boolean spray) {
        Vec3 from=p.getEyePosition(), look=p.getLookAngle(), end=beamEnd(p,reach(p,s));
        Set<LivingEntity> struck=new HashSet<>();
        segment(p,s,spray,from,end,struck);
        double left=reach(p,s)-from.distanceTo(end);
        for(int bounce=Math.clamp((int)s.number("ricochet",0),0,3);bounce>0 && left>1;bounce--){
            var hit=p.level().clip(new ClipContext(end.subtract(look.scale(.05)),end.add(look.scale(.3)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
            if(hit.getType()!=HitResult.Type.BLOCK)break;
            look=reflect(look,hit.getDirection());from=hit.getLocation().add(look.scale(.05));
            end=p.level().clip(new ClipContext(from,from.add(look.scale(left)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getLocation();
            left-=from.distanceTo(end);
            segment(p,s,spray,from,end,struck);
        }
    }
    private static void segment(ServerPlayer p,SpellDefinition s,boolean spray,Vec3 from,Vec3 end,Set<LivingEntity> struck) {
        Vec3 look=end.subtract(from).normalize();
        SpellVfx.cue(p,s,spray?SpellVfx.SPRAY:SpellVfx.BEAM,from,end,spray?1.5:s.number("beam_width",.15),10);
        for(double d=.5;d<from.distanceTo(end);d+=Math.max(.8,from.distanceTo(end)/64)) particles(p,s,from.add(look.scale(d)),spray?3:1,spray?d*.12:.04);
        double burst=s.number("end_burst",0);
        for(LivingEntity e:along(p,from,end,spray)) if(struck.add(e) && (burst<=0 || e.getBoundingBox().distanceToSqr(end)>burst*burst)) EffectGraph.entity(p,e,s,s.effects(),from,false);
        if(burst>0) impact(p,s,end,burst,false,true); else EffectGraph.world(p,s,s.effects(),end,false,true);
    }
    static Vec3 reflect(Vec3 v,net.minecraft.core.Direction face) {
        Vec3 n=new Vec3(face.getStepX(),face.getStepY(),face.getStepZ());
        return v.subtract(n.scale(2*v.dot(n)));
    }
    static LivingEntity seek(ServerPlayer p,Vec3 position,Vec3 velocity,double range) {
        Vec3 heading=velocity.normalize();
        return near(p,position,range).stream().filter(e->e!=p && EffectGraph.canHarm(p,e))
                .filter(e->e.getBoundingBox().getCenter().subtract(position).normalize().dot(heading)>.3)
                .min(Comparator.comparingDouble(e->e.distanceToSqr(position))).orElse(null);
    }
    private static Set<UUID> chainSkip=Set.of();
    private static void lightning(ServerPlayer p,SpellDefinition s) {
        Vec3 eye=p.getEyePosition(), hand=eye.add(p.getLookAngle().scale(.6)).add(0,-.3,0), end=beamEnd(p,reach(p,s));
        boolean pierce=s.delivery().has("pierce");
        List<LivingEntity> struck=along(p,eye,end,false);
        LivingEntity aimed=target(p);
        if(!pierce) struck=aimed!=null && aimed!=p ? List.of(aimed) : struck.stream().limit(1).toList();
        Vec3 tip=struck.isEmpty() || pierce ? end : struck.getFirst().getBoundingBox().getCenter();
        double width=s.number("beam_width",.14), length=hand.distanceTo(tip);
        SpellVfx.cue(p,s,SpellVfx.ARC,hand,tip,width,10);SpellVfx.cue(p,s,SpellVfx.ARC,hand,tip,width*.6,6);
        for(double d=.5;d<length;d+=1.2){Vec3 at=hand.add(tip.subtract(hand).scale(d/length));p.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK,at.x,at.y,at.z,2,.08,.08,.08,.02);}
        for(LivingEntity e:struck) EffectGraph.entity(p,e,s,s.effects(),hand,false);
        Vec3 chainFrom=struck.isEmpty() ? tip : struck.getLast().getBoundingBox().getCenter();
        chainSkip=struck.stream().map(LivingEntity::getUUID).collect(java.util.stream.Collectors.toSet());
        try { EffectGraph.world(p,s,s.effects(),chainFrom,false,true); } finally { chainSkip=Set.of(); }
        p.serverLevel().playSound(null,tip.x,tip.y,tip.z,net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_IMPACT,net.minecraft.sounds.SoundSource.PLAYERS,.7f,1.5f);
    }
    static void tesla(ServerPlayer p,SpellDefinition s,UUID construction) {
        add(new Active(){int age;
            public ServerPlayer caster(){return p;}
            public boolean tick(){
                if(++age>Math.clamp((int)s.number("duration_ticks",600),20,72000))return true;
                if(age%Math.max(10,(int)s.number("interval_ticks",20))!=0)return false;
                var level=p.serverLevel();var c=MagicConstructionManager.get(level).structure(construction);
                if(c==null)return true;
                var top=c.blocks.stream().filter(b->level.hasChunkAt(b) && level.getBlockEntity(b) instanceof MagicBlocks.MagicBlockEntity d && d.construction.equals(construction)).max(Comparator.comparingInt(BlockPos::getY));
                if(top.isEmpty())return true;
                Vec3 from=top.get().getCenter().add(0,.6,0);
                var victims=near(p,from,s.number("radius",8)).stream().filter(e->e!=p && EffectGraph.canHarm(p,e))
                        .filter(e->level.clip(new ClipContext(from,e.getBoundingBox().getCenter(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()==HitResult.Type.MISS)
                        .sorted(Comparator.comparingDouble(e->e.distanceToSqr(from))).limit(Math.clamp((int)s.number("targets",3),1,64)).toList();
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK,from.x,from.y,from.z,6,.25,.25,.25,.05);
                for(var e:victims){SpellVfx.cue(p,s,SpellVfx.ARC,from,e.getBoundingBox().getCenter(),.14,10);EffectGraph.entity(p,e,s,s.effects(),from,false);}
                if(!victims.isEmpty())level.playSound(null,from.x,from.y,from.z,net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_IMPACT,net.minecraft.sounds.SoundSource.PLAYERS,.5f,1.8f);
                return false;
            }});
    }
    private static void fan(ServerPlayer p,SpellDefinition s) {
        int count=Math.clamp((int)s.number("fan",1),1,7);
        Vec3 look=p.getLookAngle(),start=p.getEyePosition().add(look.scale(.7));
        for(int i=0;i<count;i++)projectile(p,s,start,look.yRot((float)Math.toRadians((i-(count-1)/2.0)*12)),false);
    }
    public static void projectile(ServerPlayer p,SpellDefinition s,Vec3 start,Vec3 direction,boolean self) {
        add(new Active() {
            Vec3 position=start,velocity=direction.normalize().scale(speed(s)); int age,bounces=Math.clamp((int)s.number("bounce",0),0,4); boolean charged;
            final double reach=reach(p,s),fall=gravity(s); final boolean homing=s.delivery().has("homing"); double travelled;
            public ServerPlayer caster(){return p;}
            public void charge(Vec3 point,double radius){if(s.delivery().has("volatile") && position.distanceToSqr(point)<radius*radius)charged=true;}
            public boolean tick(){
                if(++age>12000 || !p.serverLevel().hasChunkAt(BlockPos.containing(position)) || p.serverLevel().isOutsideBuildHeight(BlockPos.containing(position)))return true;
                if(homing && age>3){LivingEntity prey=seek(p,position,velocity,12);
                    if(prey!=null){double speed=velocity.length();velocity=velocity.normalize().scale(.75).add(prey.getBoundingBox().getCenter().subtract(position).normalize().scale(.25)).normalize().scale(speed);}}
                Vec3 next=position.add(velocity);
                if(!p.serverLevel().hasChunkAt(BlockPos.containing(next)))return true;
                var hit=p.serverLevel().clip(new ClipContext(position,next,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
                Vec3 end=hit.getLocation(); LivingEntity victim=null; double distance=position.distanceToSqr(end);
                for(LivingEntity e:p.serverLevel().getEntitiesOfClass(LivingEntity.class,new AABB(position,end).inflate(.5),e->e!=p && e.isAlive() && !e.isSpectator())) {
                    var intercept=e.getBoundingBox().inflate(.3).clip(position,end);
                    if(intercept.isPresent() && position.distanceToSqr(intercept.get())<=distance){distance=position.distanceToSqr(intercept.get());end=intercept.get();victim=e;}
                }
                if(age%2==1)SpellVfx.cue(p,s,SpellVfx.ORB,position,end.add(velocity),s.formula().earth()>0?.35+s.formula().earth()*.17:.22+s.formula().size()*.07,2);
                particles(p,s,end,5,.12);
                boolean volatileBurst=s.delivery().has("volatile") && (age>=80 || charged);
                travelled+=position.distanceTo(end);
                if(victim==null && bounces>0 && hit.getType()==HitResult.Type.BLOCK && travelled<reach && !volatileBurst){
                    bounces--;velocity=reflect(velocity,hit.getDirection()).scale(.85);position=end.add(velocity.normalize().scale(.05));
                    particles(p,s,end,8,.2);return false;
                }
                if(victim!=null || hit.getType()!=HitResult.Type.MISS || volatileBurst || travelled>=reach) {
                    Vec3 impactPoint=end;
                    Runnable explode=()->{
                        impact(p,s,impactPoint,s.shape()==SpellShape.BURST_PROJECTILE || s.delivery().has("meteor") || s.delivery().has("shards") || volatileBurst?s.number("burst_radius",2):.8,self,true);
                        if(s.delivery().has("hot_zone"))zone(p,s,impactPoint,80,2,self,false);
                        int shards=Math.min(6,(int)s.number("shards",0));
                        // Shards use independent short rays; they cannot recursively split.
                        for(int i=0;i<shards;i++){double a=2*Math.PI*i/shards; Vec3 q=impactPoint.add(Math.cos(a)*1.7,.4,Math.sin(a)*1.7); impact(p,s,q,.6,self,false);}
                    };
                    int wait=(int)s.number("delay_ticks",0);if(wait>0)delay(p,wait,explode);else explode.run();return true;
                }
                position=next;velocity=velocity.add(0,-fall,0);
                for(Active a:ACTIVE)if(a instanceof Zone z && z.slow && z.point.distanceToSqr(position)<z.radius*z.radius)velocity=velocity.scale(.8);
                return false;
            }
        });
    }
    private static final class Zone implements Active {
        final ServerPlayer p;final SpellDefinition s;Vec3 point;final double radius;final boolean self,mine,wet,slow;int remaining,age;
        Zone(ServerPlayer p,SpellDefinition s,Vec3 point,int ticks,double radius,boolean self,boolean mine,boolean wet){this.p=p;this.s=s;this.point=point;this.remaining=Math.clamp(ticks,1,72000);this.radius=Math.clamp(radius,.5,32);this.self=self;this.mine=mine;this.wet=wet;slow=s!=null && s.delivery().has("slow_projectiles");}
        public ServerPlayer caster(){return p;}
        public boolean tick(){
            if(--remaining<0 || !p.serverLevel().hasChunkAt(BlockPos.containing(point)))return true;
            if(s!=null && !mine && s.delivery().has("follow") && point.distanceToSqr(p.position())>2.25)point=point.add(p.position().subtract(point).normalize().scale(.08));
            if(slow)for(var projectile:p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,new AABB(point,point).inflate(radius)))projectile.setDeltaMovement(projectile.getDeltaMovement().scale(.8));
            if(age++%10!=0)return false;
            SpellVfx.cue(p,s,mine?SpellVfx.MINE:SpellVfx.ZONE,point,point,radius,Math.min(12,remaining+1));
            if(s!=null)particles(p,s,point,6,radius*.5);else p.serverLevel().sendParticles(ParticleTypes.SPLASH,point.x,point.y+.05,point.z,8,radius*.5,.02,radius*.5,0);
            var victims=near(p,point,radius);
            if(s!=null && s.delivery().has("grasp"))hands(p,point,radius,victims);
            if(mine){if(age>20 && victims.stream().anyMatch(e->e!=p && EffectGraph.canHarm(p,e))){impact(p,s,point,radius,self,true);return true;}return false;}
            if(wet){for(var e:victims)MagicStatuses.apply(e,MagicStatuses.Status.WET,60,1);return false;}
            int interval=Math.max(10,(int)s.number("interval_ticks",20));if((age-1)%interval!=0)return false;
            boolean weakest=s.delivery().has("heal_weakest");
            if(weakest)victims=victims.stream().filter(e->EffectGraph.ally(p,e)).sorted(Comparator.comparingDouble(e->e.getHealth()/e.getMaxHealth())).limit(1).toList();
            for(var e:victims)if(e!=p || self || s.formula().life()>0)EffectGraph.entity(p,e,s,self?s.selfEffects():s.effects(),point,self);
            else if(s.delivery().has("hits_caster"))EffectGraph.entity(p,p,s,s.effects().stream().filter(n->!SpellDefinition.string(n,"type","").equals("deal_damage")).toList(),point,false);
            EffectGraph.world(p,s,self?s.selfEffects():s.effects(),point,self,false);return false;
        }
    }
    private static void hands(ServerPlayer p,Vec3 point,double radius,List<LivingEntity> victims){
        var level=p.serverLevel();var random=level.getRandom();
        BlockPos below=BlockPos.containing(point).below();var ground=level.getBlockState(below).isAir()?net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState():level.getBlockState(below);
        for(int i=0;i<6;i++){double a=random.nextDouble()*Math.PI*2,r=Math.sqrt(random.nextDouble())*radius;Vec3 at=point.add(Math.cos(a)*r,.05,Math.sin(a)*r);
            level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,ground),at.x,at.y,at.z,4,.15,.05,.15,.1);
            level.sendParticles(ParticleTypes.SOUL,at.x,at.y+.2,at.z,1,.05,.2,.05,.02);}
        for(var e:victims){if(e==p || !EffectGraph.canHarm(p,e))continue;Vec3 feet=e.position();
            level.sendParticles(ParticleTypes.SCULK_SOUL,feet.x,feet.y+.3,feet.z,3,e.getBbWidth()*.4,.3,e.getBbWidth()*.4,.02);
            level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,ground),feet.x,feet.y+.1,feet.z,10,e.getBbWidth()*.5,.1,e.getBbWidth()*.5,.15);}
        if(random.nextInt(3)==0)level.playSound(null,BlockPos.containing(point),net.minecraft.sounds.SoundEvents.ROOTED_DIRT_BREAK,net.minecraft.sounds.SoundSource.PLAYERS,.8f,.6f);
    }
    public static void zone(ServerPlayer p,SpellDefinition s,Vec3 point,int ticks,double radius,boolean self,boolean mine){
        if(s.delivery().has("ice_surface"))freezeSurface(p,point,radius);
        add(new Zone(p,s,point,ticks,radius,self,mine,false));
    }
    public static void wetZone(ServerPlayer p,Vec3 point,double radius,int ticks){
        for(Active a:java.util.stream.Stream.concat(ACTIVE.stream(),PENDING.stream()).toList())if(a instanceof Zone z && z.wet && z.p==p && z.point.distanceToSqr(point)<1){z.remaining=Math.max(z.remaining,ticks);return;}
        add(new Zone(p,null,point,ticks,radius,false,false,true));
    }
    public static void freezeWetZones(ServerPlayer p,Vec3 point,double radius){
        for(Active a:java.util.stream.Stream.concat(ACTIVE.stream(),PENDING.stream()).toList())if(a instanceof Zone z && z.wet && z.p.level()==p.level() && z.point.distanceToSqr(point)<Math.pow(radius+z.radius,2)){z.remaining=0;freezeSurface(p,z.point,z.radius);}
    }
    static void freezeSurface(ServerPlayer p,Vec3 point,double radius){
        BlockPos center=BlockPos.containing(point); UUID id=UUID.randomUUID();int budget=0,r=(int)Math.ceil(radius),limit=Math.min(2048,(int)(Math.PI*radius*radius*1.5));
        for(BlockPos candidate:BlockPos.betweenClosed(center.offset(-r,-1,-r),center.offset(r,1,r)))if(candidate.distSqr(center)<=radius*radius && budget<limit){
            BlockPos pos=candidate.immutable();var state=p.serverLevel().getBlockState(pos);
            if(state.is(net.minecraft.world.level.block.Blocks.WATER) && WorldMagic.replace(p,pos,MagicBlocks.ICE.get().defaultBlockState())){if(p.serverLevel().getBlockEntity(pos) instanceof MagicBlocks.MagicBlockEntity data)data.initialize(p.getUUID(),id,pos,p.serverLevel().getGameTime()+MagicConfig.ICE_LIFE.get(),true);budget++;}
            else if(WorldPlacement.placeBlock(p,pos,MagicBlocks.ICE.get().defaultBlockState(),id,pos,false,false,null))budget++;
        }
    }
    public static void chain(ServerPlayer p,SpellDefinition s,Vec3 point,int count,double range,float amount){
        java.util.stream.Stream.concat(ACTIVE.stream(),PENDING.stream()).filter(a->LEVELS.get(a)==p.serverLevel()).forEach(a->a.charge(point,range));
        Set<UUID> seen=new HashSet<>(chainSkip);seen.add(p.getUUID());Vec3 current=point;
        for(int i=0;i<Math.clamp(count,0,128);i++){
            final Vec3 from=current;
            boolean conductive=java.util.stream.Stream.concat(ACTIVE.stream(),PENDING.stream()).anyMatch(a->a instanceof Zone z && LEVELS.get(z)==p.serverLevel() && (z.wet || z.s!=null && z.s.delivery().has("conductive")) && z.point.distanceToSqr(from)<Math.pow(z.radius+1,2));
            double jumpRange=Math.clamp(range*(conductive?1.5:1),1,48);
            var victim=near(p,from,Math.min(72,jumpRange*1.5)).stream().filter(e->!seen.contains(e.getUUID()) && EffectGraph.canHarm(p,e))
                    .filter(e->e.distanceToSqr(from)<=Math.pow(jumpRange*(MagicStatuses.has(e,MagicStatuses.Status.WET)?1.5:1),2))
                    .min(Comparator.comparingDouble(e->e.distanceToSqr(from)/(MagicStatuses.has(e,MagicStatuses.Status.WET)?2:1))).orElse(null);
            if(victim==null)break;
            current=victim.getBoundingBox().getCenter();seen.add(victim.getUUID());
            SpellVfx.cue(p,s,SpellVfx.ARC,from,current,.12,10);
            float damage=amount*(MagicStatuses.has(victim,MagicStatuses.Status.WET)?2:1);
            if(victim instanceof Player)damage*=MagicConfig.PLAYER_DAMAGE.get().floatValue();
            com.restor.magicka.enemy.MagicEnemies.hurt(victim,p.damageSources().indirectMagic(p,p),damage,p,s,false);MagicStatuses.charge(victim,40);
        }
    }
    static void sustain(ServerPlayer p,java.util.function.IntPredicate step){add(new Active(){int age;public ServerPlayer caster(){return p;}public boolean tick(){return step.test(age++);}});}
    static void repeat(ServerPlayer p,int ticks,java.util.function.IntConsumer step){add(new Active(){int age;public ServerPlayer caster(){return p;}public boolean tick(){step.accept(age);return ++age>=ticks;}});}
    static void timed(ServerPlayer p,int ticks,java.util.function.IntConsumer step,Runnable end){add(new Active(){int age;boolean done;public ServerPlayer caster(){return p;}public boolean tick(){step.accept(age);return ++age>=ticks;}public void dispose(){if(!done){done=true;end.run();}}});}
    public static void delay(ServerPlayer p,int ticks,Runnable action){add(new Active(){int remaining=Math.clamp(ticks,1,1200);public ServerPlayer caster(){return p;}public boolean tick(){if(--remaining<=0){action.run();return true;}return false;}});}
    private static void meteor(ServerPlayer p,SpellDefinition s,Vec3 point,boolean self){
        SpellVfx.cue(p,s,SpellVfx.TELEGRAPH,point,point,s.number("radius",3),(int)s.number("delay_ticks",12)+12);
        particles(p,s,point,24,1.3);delay(p,(int)s.number("delay_ticks",12),()->projectile(p,s,point.add(0,10,0),new Vec3(0,-1,0),self));
    }
    private static void dash(ServerPlayer p,SpellDefinition s){
        EffectGraph.entity(p,p,s,s.selfEffects(),p.position(),true);
        Vec3 velocity=p.getLookAngle().scale(Math.clamp(s.number("dash_speed",1.1),.2,2));
        p.setDeltaMovement(velocity.add(0,.3,0));p.hurtMarked=true;
        add(new Active(){int age;public ServerPlayer caster(){return p;}public boolean tick(){if(++age>10)return true;particles(p,s,p.position(),4,.2);if(age%3==0)zone(p,s,p.position(),40,1,false,false);return false;}});
    }
    private static void summon(ServerPlayer p,SpellDefinition s,Vec3 point,boolean self){
        String kind=SpellDefinition.string(s.delivery(),"object_kind","anchor");
        if(kind.equals("anchor")){MagicConstructionManager.start(p,s,self,point);return;}
        if(kind.equals("elemental")){for(int i=0;i<=Math.min(3,(int)s.number("summon_extra",0));i++)Elementals.companion(p,s,self?p.position():point);return;}
        if(kind.equals("undead")){var graph=self?s.selfEffects():s.effects();for(var e:near(p,point,s.number("radius",2)))if(e!=p || self)EffectGraph.entity(p,e,s,graph,point,self);EffectGraph.world(p,s,graph,point,self,true);particles(p,s,point,20,.8);return;}
        if(kind.equals("bubble")){
            int lift=(int)s.number("lift_ticks",100);
            var lifted=s.delivery().has("area") ? java.util.stream.Stream.concat(self?java.util.stream.Stream.of((LivingEntity)p):java.util.stream.Stream.empty(),near(p,point,s.number("radius",3)).stream().filter(e->e!=p)).toList() : java.util.Optional.ofNullable(self?p:target(p)).stream().toList();
            for(var e:lifted){EffectGraph.entity(p,e,s,self?s.selfEffects():s.effects(),point,self);MagicStatuses.apply(e,MagicStatuses.Status.LEVITATING,lift,1);SpellVfx.cue(p,s,SpellVfx.IMPACT,e.getBoundingBox().getCenter(),e.getBoundingBox().getCenter(),1,12);}
            if(s.delivery().has("area"))SpellVfx.cue(p,s,SpellVfx.ZONE,point,point,s.number("radius",3),12);
            return;
        }
        if(!WorldPlacement.allowed(p,BlockPos.containing(point)))return;
        MagicSummon stand=new MagicSummon(p.serverLevel(),point,(float)(s.number("object_health",12)*s.number("summon_health",1)));
        stand.setItemSlot(EquipmentSlot.HEAD,new ItemStack(kind.equals("healer")?Items.SEA_LANTERN:Items.AMETHYST_BLOCK));
        stand.setCustomName(s.displayName());stand.setCustomNameVisible(true);
        if(!p.serverLevel().addFreshEntity(stand))return;
        Zone field=new Zone(p,s,point,(int)s.number("duration_ticks",200),s.number("radius",3),self,false,false);
        add(new Active(){public ServerPlayer caster(){return p;}public boolean tick(){return !stand.isAlive() || field.tick();}public void dispose(){stand.discard();}});
    }
    @SubscribeEvent public void tick(LevelTickEvent.Post event){
        if(!(event.getLevel() instanceof ServerLevel level))return;
        ACTIVE.addAll(PENDING);PENDING.clear();
        for(var it=ACTIVE.iterator();it.hasNext();){Active a=it.next();if(LEVELS.get(a)!=level)continue;
            if(a.caster().level()!=level || !a.caster().isAlive() || a.caster().isRemoved() || a.tick()){a.dispose();it.remove();LEVELS.remove(a);}}
        OWNERS.entrySet().removeIf(e->e.getValue().isRemoved());
    }
    @SubscribeEvent public void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event){
        var id=event.getEntity().getUUID();OWNERS.remove(id);
        for(var list:List.of(ACTIVE,PENDING))list.removeIf(a->{if(!a.caster().getUUID().equals(id))return false;a.dispose();LEVELS.remove(a);return true;});
    }
    @SubscribeEvent public void stop(ServerStoppedEvent event){ACTIVE.forEach(Active::dispose);PENDING.forEach(Active::dispose);ACTIVE.clear();PENDING.clear();OWNERS.clear();LEVELS.clear();}
}
