package com.restor.magicka.enemy;

import com.restor.magicka.*;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.List;

public abstract class SpecialEnemy extends Monster {
    private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(SpecialEnemy.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(SpecialEnemy.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> EXTRA = SynchedEntityData.defineId(SpecialEnemy.class, EntityDataSerializers.INT);
    public int actionStart;
    protected int castTicks, castTotal, cooldown;
    private Runnable release;

    protected SpecialEnemy(EntityType<? extends Monster> type, Level level) { super(type, level); }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ACTION, 0).define(STATE, 0).define(EXTRA, 0);
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key == ACTION) actionStart = tickCount;
        if (key == STATE) refreshDimensions();
    }
    public int action() { return entityData.get(ACTION); }
    public int state() { return entityData.get(STATE); }
    public int extra() { return entityData.get(EXTRA); }
    protected void setAction(int value) { entityData.set(ACTION, value); }
    protected void setState(int value) { entityData.set(STATE, value); }
    protected void setExtra(int value) { entityData.set(EXTRA, value); }
    public float actionTime(float partial) { return tickCount - actionStart + partial; }

    public boolean casting() { return castTicks > 0; }
    public float castProgress() { return castTotal == 0 ? 0 : 1 - castTicks / (float)castTotal; }
    protected void startCast(int action, int ticks, Runnable release) {
        castTicks = castTotal = ticks; this.release = release;
        setAction(action); getNavigation().stop();
        playSound(castSound(), 1.2f, 1f);
    }
    public void interrupt() {
        if (!casting()) return;
        castTicks = 0; release = null; setAction(0);
        cooldown = Math.max(cooldown, 40);
        if (level() instanceof ServerLevel level) level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + getBbHeight() * .8, getZ(), 12, .3, .3, .3, .02);
        playSound(SoundEvents.FIRE_EXTINGUISH, 1f, 1.3f);
    }
    protected SoundEvent castSound() { return SoundEvents.EVOKER_PREPARE_ATTACK; }
    protected void castTell(int remaining) {}
    protected float interruptDamage() { return 4; }
    public boolean disabled() { return MagicStatuses.has(this, MagicStatuses.Status.STUNNED) || MagicStatuses.has(this, MagicStatuses.Status.FROZEN); }

    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        if (cooldown > 0) cooldown--;
        if (!casting()) return;
        if (disabled()) { interrupt(); return; }
        LivingEntity target = getTarget();
        if (target != null) getLookControl().setLookAt(target, 30, 30);
        castTell(--castTicks);
        if (castTicks == 0) {
            Runnable r = release; release = null; setAction(0);
            if (r != null) r.run();
        }
    }

    /** Magic damage arrives through {@link MagicEnemies#hurt}; everything else goes through here. */
    protected float physicalDamage(DamageSource source, float amount) { return amount; }
    protected float spellDamage(ServerPlayer caster, SpellDefinition spell, float amount, boolean brittle) { return amount; }

    @Override public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && !MagicEnemies.bySpell) amount = physicalDamage(source, amount);
        if (amount <= 0) return false;
        boolean hurt = super.hurt(source, amount);
        if (hurt && casting() && amount >= interruptDamage()) interrupt();
        return hurt;
    }

    public static boolean ally(LivingEntity other) {
        return other instanceof Enemy && !other.getTags().contains(UndeadMinions.TAG) && other.isAlive();
    }
    protected List<LivingEntity> allies(double radius) {
        return level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius), e -> e != this && ally(e));
    }
    protected static Vec3 flat(Vec3 v) { return new Vec3(v.x, 0, v.z); }
    protected void dust(Vec3 at, int color, int count, double spread) {
        if (level() instanceof ServerLevel level)
            level.sendParticles(new DustParticleOptions(new Vector3f((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f), 1.2f), at.x, at.y, at.z, count, spread, spread, spread, 0);
    }
    protected void beam(Vec3 from, Vec3 to, int color) {
        int steps = (int)Math.ceil(from.distanceTo(to) * 3);
        for (int i = 0; i <= steps; i++) dust(from.lerp(to, i / (double)Math.max(1, steps)), color, 1, .02);
    }
    protected void ring(Vec3 center, double radius, int color) {
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI / 12;
            dust(center.add(Math.cos(a) * radius, .1, Math.sin(a) * radius), color, 1, .02);
        }
    }
    protected void keepDistance(LivingEntity target, double min, double max, double speed) {
        double d = distanceTo(target);
        if (d < min) {
            Vec3 away = position().subtract(target.position()).normalize().scale(4);
            getNavigation().moveTo(getX() + away.x, getY(), getZ() + away.z, speed * 1.2);
        } else if (d > max || !hasLineOfSight(target)) getNavigation().moveTo(target, speed);
        else getNavigation().stop();
    }
    protected static boolean player(LivingEntity e) { return e instanceof Player p && !p.isCreative() && !p.isSpectator(); }

    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putInt("MagickaCooldown", cooldown); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); cooldown = tag.getInt("MagickaCooldown"); }
}
