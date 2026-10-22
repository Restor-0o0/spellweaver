package com.restor.magicka;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** A waist-deep elemental giant that stands in place while its ultimate is channelled and sinks back when the channel breaks. */
public class ElementalTitan extends Entity implements ElementalBody {
    private static final EntityDataAccessor<Integer> ELEMENT = SynchedEntityData.defineId(ElementalTitan.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(ElementalTitan.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SINKING = SynchedEntityData.defineId(ElementalTitan.class, EntityDataSerializers.BOOLEAN);
    public static final float SCALE = 2.4f;
    public static final int RISE = 30, SINK = 24, THROW = 1, SLAM = 2, THROW_TICKS = 14, SLAM_TICKS = 18;
    static final double REACH = 20, SLAM_REACH = 8, SLAM_RADIUS = 5;
    private ServerPlayer owner;
    private SpellDefinition spell;
    private EpicMagic.Channel channel;
    private List<JsonObject> slam = List.of();
    private LivingEntity target;
    private int cooldown = 10, windup, attacks, sinkAge, actionStart, sinkStart, shown, previous;

    public ElementalTitan(EntityType<? extends ElementalTitan> type, Level level) { super(type, level); noPhysics = true; setNoGravity(true); }

    static void rise(ServerPlayer p, SpellDefinition s, EpicMagic.Channel c) {
        ServerLevel level = p.serverLevel();
        ElementalTitan titan = Elementals.TITAN.get().create(level);
        if (titan == null) return;
        titan.owner = p; titan.spell = s; titan.channel = c;
        titan.slam = SpellDefinition.graph(s.delivery(), "slam");
        titan.entityData.set(ELEMENT, Elementals.element(s).ordinal());
        titan.moveTo(c.focus.x, c.focus.y, c.focus.z, p.getYRot(), 0);
        titan.setYHeadRot(p.getYRot());
        if (!level.addFreshEntity(titan)) return;
        level.playSound(null, c.focus.x, c.focus.y, c.focus.z, SoundEvents.WARDEN_EMERGE, SoundSource.PLAYERS, 2f, .7f);
        level.playSound(null, c.focus.x, c.focus.y, c.focus.z, Elementals.shot(titan.element()), SoundSource.PLAYERS, 2f, .5f);
    }

    @Override protected void defineSynchedData() { entityData.define(ELEMENT, 0); entityData.define(ACTION, 0); entityData.define(SINKING, false); }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (ACTION.equals(key)) { previous = shown; shown = entityData.get(ACTION); actionStart = tickCount; }
        if (SINKING.equals(key) && sinking()) sinkStart = tickCount;
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override public boolean fireImmune() { return true; }
    @Override public boolean isAttackable() { return false; }
    @Override protected Component getTypeName() { return Component.translatable("entity.magicka." + modelId()); }
    @Override public Element element() { return Element.values()[Math.floorMod(entityData.get(ELEMENT), Element.values().length)]; }
    @Override public int action() { return entityData.get(ACTION); }
    @Override public int previousAction() { return previous; }
    @Override public float actionTime(float partial) { return tickCount - actionStart + partial; }
    public boolean sinking() { return entityData.get(SINKING); }
    public ServerPlayer owner() { return owner; }
    public float emergence(float partial) {
        if (sinking()) return Mth.clamp(1 - (tickCount - sinkStart + partial) / SINK, 0, 1);
        float t = Mth.clamp((tickCount + partial) / RISE, 0, 1);
        return 1 - (1 - t) * (1 - t);
    }
    private void act(int action) { entityData.set(ACTION, action); }

    /** A point of the model in Blockbench pixels, turned into world space at full height. */
    Vec3 local(double x, double y, double z) {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        Vec3 look = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)), right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        double k = SCALE / 16;
        return position().add(right.scale(-x * k)).add(0, y * k, 0).add(look.scale(-z * k));
    }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide) { ambient(); return; }
        ServerLevel level = (ServerLevel) level();
        if (!sinking() && (owner == null || channel == null || !channel.live || !owner.isAlive() || owner.isRemoved() || owner.level() != level)) sink(level);
        if (sinking()) {
            if (++sinkAge > SINK) discard();
            else if (sinkAge % 3 == 0) rubble(level, 2.5, 8);
            return;
        }
        if (tickCount < RISE) {
            if (tickCount % 2 == 0) rubble(level, 3, 10);
            if (tickCount % 8 == 0) level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_DIG, SoundSource.PLAYERS, 1.5f, .6f);
            return;
        }
        if (tickCount == RISE) { Elementals.ring(level, element(), position(), 4); Elementals.burst(level, element(), local(0, 34, -6), 40, 1.2); }
        if (target != null && !(target.isAlive() && target.level() == level && target.distanceToSqr(this) < REACH * REACH * 1.3 && Elementals.foe(owner, target))) target = null;
        if (windup == 0 && (target == null || tickCount % 10 == 0)) {
            LivingEntity pick = Elementals.pick(owner, position(), REACH, 10);
            if (pick != null) target = pick;
        }
        if (target != null) {
            Vec3 d = target.position().subtract(position());
            float want = (float)(Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90;
            setYRot(Mth.approachDegrees(getYRot(), want, windup > 0 ? 3 : 8));
            setYHeadRot(getYRot());
        }
        if (windup > 0) {
            windup--;
            if (action() == SLAM && windup % 3 == 0) Elementals.ring(level, element(), impact(level), SLAM_RADIUS * (1 - windup / (double)SLAM_TICKS) + .5);
            if (action() == THROW && windup % 2 == 0) Elementals.burst(level, element(), hand(), 4, .3);
            if (windup == 0) { if (action() == SLAM) slam(level); else throwAt(level); act(0); cooldown = 14; }
            return;
        }
        if (cooldown > 0) { cooldown--; return; }
        if (target == null) return;
        boolean close = target.distanceToSqr(impact(level)) < SLAM_REACH * SLAM_REACH;
        if (close && attacks % 3 != 1 || !close && attacks % 4 == 3) { act(SLAM); windup = SLAM_TICKS; level.playSound(null, getX(), getY() + 4, getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.2f, .6f); }
        else { act(THROW); windup = THROW_TICKS; }
        attacks++;
    }
    private void sink(ServerLevel level) {
        entityData.set(SINKING, true);
        sinkStart = tickCount; windup = 0; act(0);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_DIG, SoundSource.PLAYERS, 2f, .5f);
        Elementals.burst(level, element(), local(0, 30, -4), 30, 1.5);
    }
    private void rubble(ServerLevel level, double radius, int count) {
        BlockPos under = BlockPos.containing(position().add(0, -.2, 0));
        var state = level.getBlockState(under);
        var dust = new BlockParticleOption(ParticleTypes.BLOCK, state.isAir() ? net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState() : state);
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * Math.PI * 2, r = radius * Math.sqrt(random.nextDouble());
            EpicMagic.fx(level, dust, getX() + Math.cos(a) * r, getY() + .2, getZ() + Math.sin(a) * r, 3, .2, .3, .2, .15);
        }
        EpicMagic.fx(level, Elementals.trail(element()), getX(), getY() + .5, getZ(), 6, radius * .5, .3, radius * .5, .04);
    }
    private Vec3 hand() { return action() == SLAM ? local(0, 44, -6) : local(-15, 40, -6); }
    private Vec3 impact(ServerLevel level) {
        Vec3 front = local(0, 0, -10);
        Vec3 ground = EpicMagic.drop(level, front.add(0, 3, 0), 8, this);
        return ground == null ? front : ground;
    }
    private List<LivingEntity> foes(Vec3 around, double radius, int max) {
        var list = new ArrayList<LivingEntity>();
        if (target != null && target.isAlive()) list.add(target);
        for (var e : level().getEntitiesOfClass(LivingEntity.class, new AABB(around, around).inflate(radius), e -> e != target && Elementals.foe(owner, e) && e.distanceToSqr(around) < radius * radius)) {
            if (list.size() >= max) break;
            list.add(e);
        }
        return list;
    }

    private void throwAt(ServerLevel level) {
        if (target == null || !target.isAlive()) return;
        Element e = element();
        Vec3 from = hand();
        var graph = spell.effects();
        level.playSound(null, from.x, from.y, from.z, SoundEvents.RAVAGER_ATTACK, SoundSource.PLAYERS, 1f, .6f);
        switch (e) {
            case FIRE -> Elementals.bolt(owner, spell, e, from, target, 1, .6, 2.5, graph);
            case WATER -> Elementals.bolt(owner, spell, e, from, target, .9, .6, 2.5, graph);
            case EARTH -> Elementals.bolt(owner, spell, e, from, target, .8, .8, 2.2, graph);
            case LIFE -> Elementals.bolt(owner, spell, e, from, target, .8, .45, 2, graph);
            case COLD -> { for (var v : foes(target.position(), 8, 3)) Elementals.bolt(owner, spell, e, from, v, 1.2, .3, 0, graph); }
            case ARCANE -> { for (var v : foes(position(), REACH, 3)) Elementals.beam(owner, spell, e, from, v, graph); }
            case LIGHTNING -> {
                Vec3 at = from;
                for (var v : foes(target.position(), 6, 3)) { Elementals.beam(owner, spell, e, at, v, graph); at = Elementals.aim(v); }
            }
            case DEATH -> { Elementals.beam(owner, spell, e, from, target, graph); MagicGear.heal(owner, owner, 2); }
        }
    }
    private void slam(ServerLevel level) {
        Element e = element();
        Vec3 at = impact(level);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2f, .7f);
        level.playSound(null, at.x, at.y, at.z, Elementals.hit(e), SoundSource.PLAYERS, 2f, .6f);
        rubble(level, SLAM_RADIUS, 16);
        switch (e) {
            case FIRE -> { for (int i = 0; i < 6; i++) EpicMagic.ignite(owner, BlockPos.containing(at.add(random.nextGaussian() * 2, -.5, random.nextGaussian() * 2))); }
            case WATER -> { SpellRuntime.wetZone(owner, at, SLAM_RADIUS, 120); EpicMagic.fx(level, ParticleTypes.SPLASH, at.x, at.y + 1, at.z, 80, 1.5, 1.5, 1.5, .3); }
            case COLD -> SpellRuntime.freezeSurface(owner, at, 3);
            case LIGHTNING -> EpicMagic.strike(owner, spell, at, at.y + 9, 1.5);
            case EARTH -> WorldMagic.heave(owner, at, 10, 3, .7);
            case LIFE -> {
                for (var ally : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(SLAM_RADIUS + 2), a -> a == owner || EffectGraph.ally(owner, a) && !(a instanceof net.minecraft.world.entity.player.Player))) {
                    MagicGear.heal(owner, ally, 4);
                    MagicStatuses.apply(ally, MagicStatuses.Status.REGENERATING, 100, 1);
                }
                WorldMagic.grow(owner, at, 6);
            }
            case ARCANE -> Elementals.ring(level, e, at, SLAM_RADIUS * .75);
            case DEATH -> EpicMagic.fx(level, ParticleTypes.SCULK_SOUL, at.x, at.y + .5, at.z, 40, SLAM_RADIUS * .4, .4, SLAM_RADIUS * .4, .08);
        }
        Elementals.blast(owner, spell, e, at, SLAM_RADIUS, slam, true);
        for (var v : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(SLAM_RADIUS), v -> Elementals.hostile(owner, v) && v.distanceToSqr(at) < SLAM_RADIUS * SLAM_RADIUS)) {
            v.push(0, .45, 0); v.hurtMarked = true;
        }
    }

    private void ambient() {
        float up = emergence(0);
        if (up <= .05f) return;
        var e = element();
        double height = 6 * up - 6 * (1 - up);
        for (int i = 0; i < 3; i++) {
            double a = random.nextDouble() * Math.PI * 2, r = 1 + random.nextDouble() * 1.4;
            level().addParticle(Elementals.trail(e), getX() + Math.cos(a) * r, getY() + random.nextDouble() * Math.max(.5, height), getZ() + Math.sin(a) * r, 0, e == Element.FIRE || e == Element.DEATH ? .05 : .01, 0);
        }
        if (tickCount % 4 == 0) level().addParticle(Elementals.spark(e), getX() + random.nextGaussian(), getY() + .3, getZ() + random.nextGaussian(), 0, .05, 0);
    }
}
