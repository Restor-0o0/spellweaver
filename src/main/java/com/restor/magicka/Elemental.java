package com.restor.magicka;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A small elemental that follows its caster for a while and fights whatever threatens them. */
public class Elemental extends PathfinderMob implements ElementalBody {
    private static final EntityDataAccessor<Integer> ELEMENT = SynchedEntityData.defineId(Elemental.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(Elemental.class, EntityDataSerializers.INT);
    public static final int ATTACK = 1;
    ServerPlayer owner;
    private SpellDefinition spell;
    private long until;
    private int cooldown, windup, actionStart, shown, previous;
    int slot;
    private LivingEntity target;

    public Elemental(EntityType<? extends Elemental> type, Level level) { super(type, level); xpReward = 0; }
    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 30).add(Attributes.MOVEMENT_SPEED, .3).add(Attributes.ARMOR, 4)
                .add(Attributes.KNOCKBACK_RESISTANCE, .4).add(Attributes.FLYING_SPEED, .4).add(Attributes.FOLLOW_RANGE, 24);
    }
    void setup(ServerPlayer owner, SpellDefinition spell, Element element, long until) {
        this.owner = owner; this.spell = spell; this.until = until;
        entityData.set(ELEMENT, element.ordinal());
        setNoGravity(flyer());
        setPersistenceRequired();
        addTag("magicka_object");
    }
    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ELEMENT, 0); entityData.define(ACTION, 0);
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (ACTION.equals(key)) { previous = shown; shown = entityData.get(ACTION); actionStart = tickCount; }
    }
    @Override public Element element() { return Element.values()[Math.floorMod(entityData.get(ELEMENT), Element.values().length)]; }
    @Override public int action() { return entityData.get(ACTION); }
    @Override public int previousAction() { return previous; }
    @Override public float actionTime(float partial) { return tickCount - actionStart + partial; }
    public ServerPlayer owner() { return owner; }
    public boolean flyer() { return switch (element()) { case FIRE, LIGHTNING, ARCANE, DEATH -> true; default -> false; }; }
    private void act(int action) { entityData.set(ACTION, action); }

    @Override protected Component getTypeName() { return Component.translatable("entity.magicka." + modelId()); }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public void checkDespawn() {}
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override protected boolean shouldDropLoot() { return false; }
    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }
    @Override public boolean fireImmune() { return element() == Element.FIRE || super.fireImmune(); }
    @Override public boolean canFreeze() { return element() != Element.COLD && super.canFreeze(); }
    @Override public void thunderHit(ServerLevel level, LightningBolt bolt) { if (element() != Element.LIGHTNING) super.thunderHit(level, bolt); }
    @Override public boolean canBeLeashed(net.minecraft.world.entity.player.Player player) { return false; }
    @Override public boolean isAlliedTo(Entity other) {
        if (owner != null && (other == owner || other instanceof Elemental e && e.owner == owner || UndeadMinions.ownedBy(other, owner))) return true;
        return super.isAlliedTo(other);
    }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (owner != null && source.getEntity() != null && isAlliedTo(source.getEntity())) return false;
        if (element() == Element.FIRE && source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE) || source.is(net.minecraft.world.damagesource.DamageTypes.DROWN)) return false;
        return super.hurt(source, amount);
    }
    @Override public void remove(RemovalReason reason) {
        if (!level().isClientSide) Elementals.forget(this);
        super.remove(reason);
    }
    void vanish() {
        if (level() instanceof ServerLevel level && isAlive()) {
            Elementals.burst(level, element(), position().add(0, getBbHeight() * .5, 0), 24, .4);
            level.playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, .6f, 1.4f);
        }
        discard();
    }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide && tickCount % 2 == 0) {
            var r = random;
            level().addParticle(Elementals.trail(element()), getX() + r.nextGaussian() * .2, getY() + getBbHeight() * (.3 + r.nextFloat() * .6), getZ() + r.nextGaussian() * .2, 0, flyer() ? -.02 : .02, 0);
        }
    }

    @Override protected void customServerAiStep() {
        if (!(level() instanceof ServerLevel level)) return;
        if (owner == null || spell == null || !owner.isAlive() || owner.isRemoved() || owner.level() != level || level.getGameTime() > until) { vanish(); return; }
        if (distanceToSqr(owner) > 24 * 24) { home(level); return; }
        if (target != null && !valid(target)) target = null;
        if (tickCount % 10 == 0 && windup == 0) {
            LivingEntity pick = Elementals.pick(owner, position(), 14, 6);
            if (pick != null && (target == null || pick != target && pick.distanceToSqr(this) + 9 < target.distanceToSqr(this))) target = pick;
        }
        if (cooldown > 0) cooldown--;
        if (element() == Element.LIFE && tickCount % 40 == 0) mend(level);
        if (target == null) { if (windup > 0) { windup = 0; act(0); } follow(); }
        else fight(level);
        if (flyer()) {
            Vec3 v = getDeltaMovement();
            Vec3 face = target != null ? target.position().subtract(position()) : v.horizontalDistanceSqr() > 1e-3 ? v : owner.getLookAngle();
            float yaw = Mth.approachDegrees(getYRot(), (float)(Mth.atan2(face.z, face.x) * Mth.RAD_TO_DEG) - 90, 12);
            setYRot(yaw); yBodyRot = yaw; yHeadRot = yaw;
        }
    }
    private boolean valid(LivingEntity e) {
        return e.isAlive() && e.level() == level() && e.distanceToSqr(this) < 18 * 18 && Elementals.foe(owner, e);
    }
    private void home(ServerLevel level) {
        Vec3 side = owner.position().add(owner.getLookAngle().multiply(-1.5, 0, -1.5));
        Vec3 ground = EpicMagic.drop(level, side.add(0, 1.5, 0), 4, this);
        Vec3 at = ground == null ? owner.position() : ground;
        getNavigation().stop();
        moveTo(at.x, at.y + (flyer() ? 1 : 0), at.z, owner.getYRot(), 0);
        setDeltaMovement(Vec3.ZERO);
        Elementals.burst(level, element(), at.add(0, .8, 0), 16, .4);
    }
    private void follow() {
        Vec3 look = Vec3.directionFromRotation(0, owner.getYRot());
        Vec3 right = new Vec3(-look.z, 0, look.x);
        Vec3 goal = owner.position().add(right.scale(slot % 2 == 0 ? -1.8 : 1.8)).add(look.scale(-1.2));
        double far = distanceToSqr(goal);
        if (flyer()) fly(goal.add(0, 1.6 + Mth.sin(tickCount * .08f) * .3, 0), far > 64 ? .55 : .3);
        else if (far > 6) getNavigation().moveTo(goal.x, goal.y, goal.z, far > 64 ? 1.6 : 1.1);
        else if (far < 2) getNavigation().stop();
        if (!flyer()) getLookControl().setLookAt(owner.getX(), owner.getEyeY(), owner.getZ());
    }
    private void fly(Vec3 goal, double speed) {
        Vec3 d = goal.subtract(position());
        double length = d.length();
        Vec3 want = length < .2 ? Vec3.ZERO : d.scale(Math.min(speed, length * .2) / length);
        setDeltaMovement(getDeltaMovement().lerp(want, .2));
    }
    private void fight(ServerLevel level) {
        boolean melee = element() == Element.EARTH;
        double distance = Math.sqrt(distanceToSqr(target)), reach = melee ? 2.4 + target.getBbWidth() * .5 : 13;
        if (!flyer()) getLookControl().setLookAt(target, 40, 40);
        if (windup > 0) {
            if (flyer()) fly(position().add(0, .02, 0), 0);
            else getNavigation().stop();
            if (--windup == 0) { release(level, distance <= reach + (melee ? 1.2 : 3)); act(0); }
            return;
        }
        if (melee) {
            if (distance > reach - .4) getNavigation().moveTo(target, 1.3);
            else getNavigation().stop();
        } else {
            Vec3 away = position().subtract(target.position()).multiply(1, 0, 1);
            away = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize();
            double want = distance > 9 ? 7 : distance < 4 ? 6 : distance;
            Vec3 spot = target.position().add(away.yRot(slot % 2 == 0 ? .35f : -.35f).scale(want));
            if (flyer()) fly(spot.add(0, 2.2 + Mth.sin(tickCount * .1f) * .4, 0), .35);
            else if (Math.abs(distance - want) > 1) getNavigation().moveTo(spot.x, spot.y, spot.z, 1.2);
            else getNavigation().stop();
        }
        if (cooldown == 0 && distance <= reach && hasLineOfSight(target)) {
            windup = melee ? 6 : 8;
            act(ATTACK);
            Elementals.burst(level, element(), hand(), 6, .15);
        }
    }
    private Vec3 hand() { return position().add(0, getBbHeight() * .62, 0).add(Vec3.directionFromRotation(0, getYRot()).scale(.5)); }
    private void release(ServerLevel level, boolean inReach) {
        Element e = element();
        cooldown = e == Element.EARTH ? 10 : 18;
        if (!inReach || !target.isAlive()) return;
        var graph = spell.effects();
        switch (e) {
            case FIRE -> Elementals.bolt(owner, spell, e, hand(), target, .9, .3, 0, graph);
            case WATER -> Elementals.bolt(owner, spell, e, hand(), target, .75, .35, 0, graph);
            case COLD -> Elementals.bolt(owner, spell, e, hand(), target, 1.1, .2, 0, graph);
            case LIFE -> Elementals.bolt(owner, spell, e, hand(), target, .7, .25, 0, graph);
            case LIGHTNING, ARCANE -> Elementals.beam(owner, spell, e, hand(), target, graph);
            case DEATH -> { Elementals.beam(owner, spell, e, hand(), target, graph); heal(2); }
            case EARTH -> {
                swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                EffectGraph.entity(owner, target, spell, graph, position(), false);
                Vec3 push = target.position().subtract(position()).multiply(1, 0, 1);
                if (push.lengthSqr() > 1e-4) target.knockback(.8, -push.x, -push.z);
                Elementals.burst(level, e, target.position().add(0, .3, 0), 18, .4);
                level.playSound(null, getX(), getY(), getZ(), Elementals.shot(e), SoundSource.PLAYERS, 1f, .8f);
            }
        }
    }
    private void mend(ServerLevel level) {
        for (LivingEntity ally : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(6), a -> a == owner || a instanceof Elemental m && m.owner == owner))
            if (ally.getHealth() < ally.getMaxHealth()) {
                MagicGear.heal(owner, ally, 2);
                level.sendParticles(ParticleTypes.HEART, ally.getX(), ally.getY() + ally.getBbHeight() + .2, ally.getZ(), 2, .3, .1, .3, 0);
            }
    }
}
