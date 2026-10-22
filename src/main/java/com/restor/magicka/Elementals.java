package com.restor.magicka;

import net.minecraft.util.Mth;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import org.joml.Vector3f;
import java.util.*;

/** Summoned elementals: companions that follow their caster and titans that live only while an ultimate is channelled. */
public final class Elementals {
    public static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, Magicka.ID);
    public static final RegistryObject<EntityType<Elemental>> COMPANION = TYPES.register("elemental", () -> EntityType.Builder.of(Elemental::new, MobCategory.MISC)
            .sized(.8f, 1.6f).clientTrackingRange(10).build("elemental"));
    public static final RegistryObject<EntityType<ElementalTitan>> TITAN = TYPES.register("elemental_titan", () -> EntityType.Builder.<ElementalTitan>of(ElementalTitan::new, MobCategory.MISC)
            .sized(4f, 6f).clientTrackingRange(16).updateInterval(1).fireImmune().build("elemental_titan"));
    public static final int LIMIT = 2;
    private static final Map<UUID, Deque<Elemental>> OWNED = new HashMap<>();

    public static void attributes(EntityAttributeCreationEvent event) { event.put(COMPANION.get(), Elemental.attributes().build()); }
    public static void stop(ServerStoppedEvent event) { OWNED.clear(); }

    public static Element element(SpellDefinition s) {
        try { return Element.valueOf(SpellDefinition.string(s.delivery(),"element","fire").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { return Element.FIRE; }
    }
    public static String id(Element e, String kind) { return e.name().toLowerCase(Locale.ROOT) + "_" + kind; }

    static void companion(ServerPlayer p, SpellDefinition s, Vec3 point) {
        ServerLevel level = p.serverLevel();
        Elemental mob = COMPANION.get().create(level);
        if (mob == null) return;
        Element e = element(s);
        mob.setup(p, s, e, level.getGameTime() + Mth.clamp((int)s.number("duration_ticks",1200), 20, 12000));
        Vec3 at = place(level, mob, point);
        if (at == null) at = place(level, mob, p.position().add(0,1,0));
        if (at == null || !WorldPlacement.allowed(p, BlockPos.containing(at))) { mob.discard(); return; }
        mob.moveTo(at.x, at.y + (mob.flyer() ? .8 : 0), at.z, p.getYRot(), 0);
        if (!level.addFreshEntity(mob)) return;
        Deque<Elemental> owned = OWNED.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
        owned.removeIf(m -> !m.isAlive());
        owned.addLast(mob);
        while (owned.size() > LIMIT) owned.pollFirst().vanish();
        int slot = 0;
        for (Elemental m : owned) m.slot = slot++;
        burst(level, e, at.add(0,.8,0), 30, .5);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, .8f, 1.4f);
        level.playSound(null, at.x, at.y, at.z, shot(e), SoundSource.PLAYERS, 1f, .7f);
    }
    static void forget(Elemental mob) {
        if (mob.owner == null) return;
        var owned = OWNED.get(mob.owner.getUUID());
        if (owned != null) { owned.remove(mob); if (owned.isEmpty()) OWNED.remove(mob.owner.getUUID()); }
    }
    public static List<Elemental> companions(ServerPlayer p) {
        var owned = OWNED.get(p.getUUID());
        return owned == null ? List.of() : owned.stream().filter(Elemental::isAlive).toList();
    }
    private static Vec3 place(ServerLevel level, Elemental mob, Vec3 point) {
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = attempt * 2.39996, distance = attempt == 0 ? 0 : .8 + attempt * .25;
            Vec3 column = point.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            Vec3 ground = EpicMagic.drop(level, column.add(0,.5,0), 6, mob);
            if (ground == null) continue;
            if (level.noCollision(mob, mob.getType().getDimensions().makeBoundingBox(ground))) return ground;
        }
        return null;
    }

    static boolean hostile(ServerPlayer owner, LivingEntity e) {
        return e.isAlive() && !e.isSpectator() && e != owner && EffectGraph.canHarm(owner, e) && !EffectGraph.ally(owner, e);
    }
    static boolean foe(ServerPlayer owner, LivingEntity e) {
        if (!e.isAlive() || e == owner || e.isSpectator()) return false;
        if (e instanceof Player player) return (owner.getLastHurtByMob() == player || owner.getLastHurtMob() == player) && owner.canHarmPlayer(player) && !player.isCreative();
        if (EffectGraph.ally(owner, e)) return false;
        return e instanceof Enemy || owner.getLastHurtByMob() == e || owner.getLastHurtMob() == e && owner.tickCount - owner.getLastHurtMobTimestamp() < 200;
    }
    static LivingEntity pick(ServerPlayer owner, Vec3 from, double range, double height) {
        LivingEntity attacker = owner.getLastHurtByMob();
        if (attacker != null && foe(owner, attacker) && attacker.distanceToSqr(from) < range * range) return attacker;
        LivingEntity victim = owner.getLastHurtMob();
        if (victim != null && foe(owner, victim) && victim.distanceToSqr(from) < range * range) return victim;
        return owner.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(from, from).inflate(range, height, range), e -> foe(owner, e))
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(from))).orElse(null);
    }

    static ParticleOptions trail(Element e) {
        return switch (e) {
            case FIRE -> ParticleTypes.FLAME;
            case WATER -> ParticleTypes.FALLING_WATER;
            case COLD -> ParticleTypes.SNOWFLAKE;
            case LIGHTNING -> ParticleTypes.ELECTRIC_SPARK;
            case EARTH -> new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState());
            case LIFE -> ParticleTypes.HAPPY_VILLAGER;
            case ARCANE -> new DustParticleOptions(new Vector3f(1f, .45f, .85f), 1.5f);
            case DEATH -> ParticleTypes.SOUL_FIRE_FLAME;
        };
    }
    static ParticleOptions spark(Element e) {
        return switch (e) {
            case FIRE -> ParticleTypes.LAVA;
            case WATER -> ParticleTypes.SPLASH;
            case COLD -> new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState());
            case LIGHTNING -> ParticleTypes.END_ROD;
            case EARTH -> new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState());
            case LIFE -> ParticleTypes.COMPOSTER;
            case ARCANE -> ParticleTypes.WITCH;
            case DEATH -> ParticleTypes.SOUL;
        };
    }
    static SoundEvent shot(Element e) {
        return switch (e) {
            case FIRE -> SoundEvents.BLAZE_SHOOT;
            case WATER -> SoundEvents.BUCKET_EMPTY;
            case COLD -> SoundEvents.SNOW_GOLEM_SHOOT;
            case LIGHTNING -> SoundEvents.LIGHTNING_BOLT_IMPACT;
            case EARTH -> SoundEvents.IRON_GOLEM_ATTACK;
            case LIFE -> SoundEvents.BONE_MEAL_USE;
            case ARCANE -> SoundEvents.EVOKER_CAST_SPELL;
            case DEATH -> SoundEvents.WITHER_SHOOT;
        };
    }
    static SoundEvent hit(Element e) {
        return switch (e) {
            case FIRE -> SoundEvents.FIRECHARGE_USE;
            case WATER -> SoundEvents.GENERIC_SPLASH;
            case COLD -> SoundEvents.GLASS_BREAK;
            case LIGHTNING -> SoundEvents.LIGHTNING_BOLT_IMPACT;
            case EARTH -> SoundEvents.STONE_BREAK;
            case LIFE -> SoundEvents.AZALEA_LEAVES_BREAK;
            case ARCANE -> SoundEvents.AMETHYST_BLOCK_BREAK;
            case DEATH -> SoundEvents.SOUL_ESCAPE;
        };
    }
    static void burst(ServerLevel level, Element e, Vec3 at, int count, double spread) {
        EpicMagic.fx(level, trail(e), at.x, at.y, at.z, count, spread, spread, spread, .06);
        EpicMagic.fx(level, spark(e), at.x, at.y, at.z, count / 3, spread, spread, spread, .1);
    }
    static void ring(ServerLevel level, Element e, Vec3 at, double radius) {
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI / 12;
            EpicMagic.fx(level, trail(e), at.x + Math.cos(a) * radius, at.y + .15, at.z + Math.sin(a) * radius, 1, 0, .05, 0, .01);
        }
    }
    static void line(ServerLevel level, Element e, Vec3 from, Vec3 to, double jitter) {
        Vec3 d = to.subtract(from);
        int steps = Math.max(2, (int)(d.length() * 3));
        var r = level.random;
        for (int i = 0; i <= steps; i++) {
            Vec3 q = from.add(d.scale(i / (double)steps));
            EpicMagic.fx(level, trail(e), q.x + r.nextGaussian() * jitter, q.y + r.nextGaussian() * jitter, q.z + r.nextGaussian() * jitter, 1, 0, 0, 0, 0);
        }
    }
    static Vec3 aim(LivingEntity target) { return target.position().add(0, target.getBbHeight() * .55, 0); }

    /** A homing elemental shot; {@code splash} 0 hits only what it strikes. */
    static void bolt(ServerPlayer p, SpellDefinition s, Element e, Vec3 from, LivingEntity target, double speed, double size, double splash, List<JsonObject> graph) {
        ServerLevel level = p.serverLevel();
        Vec3[] position = {from}, velocity = {aim(target).subtract(from).normalize().scale(speed)};
        level.playSound(null, from.x, from.y, from.z, shot(e), SoundSource.PLAYERS, .7f + (float)size * .5f, 1.3f - (float)size * .6f);
        SpellRuntime.sustain(p, age -> {
            if (age > 60) return true;
            Vec3 f = position[0];
            if (target.isAlive() && target.level() == level) {
                Vec3 want = aim(target).subtract(f).normalize().scale(speed);
                velocity[0] = velocity[0].lerp(want, .25).normalize().scale(speed);
            }
            Vec3 to = f.add(velocity[0]);
            if (!level.hasChunkAt(BlockPos.containing(to))) return true;
            var clip = level.clip(new ClipContext(f, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            Vec3 end = clip.getLocation();
            boolean struck = clip.getType() != HitResult.Type.MISS;
            LivingEntity victim = null;
            for (var v : level.getEntitiesOfClass(LivingEntity.class, new AABB(f, end).inflate(size + .3), v -> hostile(p, v)))
                if (v.getBoundingBox().inflate(size).clip(f, end).isPresent()) { victim = v; end = v.getBoundingBox().getCenter(); struck = true; break; }
            line(level, e, f, end, size * .35);
            if (size > .4) EpicMagic.fx(level, spark(e), f.x, f.y, f.z, 2, size * .3, size * .3, size * .3, .01);
            if (!struck) { position[0] = to; return false; }
            strike(p, s, e, end, victim, splash, graph);
            return true;
        });
    }
    static void strike(ServerPlayer p, SpellDefinition s, Element e, Vec3 at, LivingEntity victim, double splash, List<JsonObject> graph) {
        ServerLevel level = p.serverLevel();
        burst(level, e, at, splash > 0 ? 40 : 14, splash > 0 ? splash * .4 : .3);
        level.playSound(null, at.x, at.y, at.z, hit(e), SoundSource.PLAYERS, splash > 0 ? 1.4f : .8f, .8f + level.random.nextFloat() * .3f);
        if (splash > 0) { blast(p, s, e, at, splash, graph, false); return; }
        if (victim != null) EffectGraph.entity(p, victim, s, graph, at, false);
        else for (var v : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(.8), v -> hostile(p, v))) EffectGraph.entity(p, v, s, graph, at, false);
    }
    static void blast(ServerPlayer p, SpellDefinition s, Element e, Vec3 at, double radius, List<JsonObject> graph, boolean rings) {
        ServerLevel level = p.serverLevel();
        if (rings) { ring(level, e, at, radius * .5); ring(level, e, at, radius); }
        for (var v : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius, radius * .8, radius), v -> hostile(p, v) && v.distanceToSqr(at) <= radius * radius))
            EffectGraph.entity(p, v, s, graph, at, false);
        EffectGraph.world(p, s, graph, at, false, false);
    }
    static void beam(ServerPlayer p, SpellDefinition s, Element e, Vec3 from, LivingEntity target, List<JsonObject> graph) {
        ServerLevel level = p.serverLevel();
        Vec3 to = aim(target);
        line(level, e, from, to, e == Element.LIGHTNING ? .18 : .04);
        if (e == Element.DEATH) line(level, e, to, from, .12);
        burst(level, e, to, 10, .25);
        level.playSound(null, from.x, from.y, from.z, shot(e), SoundSource.PLAYERS, .6f, 1.4f);
        EffectGraph.entity(p, target, s, graph, from, false);
    }
    private Elementals() {}
}
