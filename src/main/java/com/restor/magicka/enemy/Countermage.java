package com.restor.magicka.enemy;

import com.restor.magicka.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Watches which elements hit its group and raises a ward against the dominant one. */
public class Countermage extends SpecialEnemy {
    public static final int WARD_CAST = 1;
    public static final double WATCH = 16, AURA = 8;
    public static final float THRESHOLD = 12, REDUCTION = .7f;
    public static final int WARD_TICKS = 200;
    private final float[] weights = new float[Element.values().length];
    private int ward, boltCooldown;

    public Countermage(EntityType<? extends Monster> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 45).add(Attributes.MOVEMENT_SPEED, .25).add(Attributes.ARMOR, 6)
                .add(Attributes.KNOCKBACK_RESISTANCE, .4).add(Attributes.FOLLOW_RANGE, 28);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }
    public Element warded() { return ward > 0 && extra() > 0 ? Element.values()[extra() - 1] : null; }
    public float weight(Element e) { return weights[e.ordinal()]; }
    public void observe(SpellDefinition spell) {
        for (Element e : Element.values()) weights[e.ordinal()] += spell.formula().count(e);
    }
    public float shield(LivingEntity target, SpellDefinition spell) {
        Element e = warded();
        if (e == null || spell.formula().size() == 0 || target != this && (!ally(target) || target.distanceToSqr(this) > AURA * AURA)) return 1;
        float share = spell.formula().count(e) / (float)spell.formula().size();
        if (share > 0) dust(target.position().add(0, target.getBbHeight() * .6, 0), e.color, 6, .4);
        return 1 - REDUCTION * share;
    }
    public void raiseWard(Element e) {
        ward = WARD_TICKS; setExtra(e.ordinal() + 1); setState(1);
        java.util.Arrays.fill(weights, 0);
        playSound(SoundEvents.BEACON_ACTIVATE, 1.2f, 1.2f);
        cooldown = WARD_TICKS + 300;
    }
    private void breakWard() {
        ward = 0; setExtra(0); setState(0);
        playSound(SoundEvents.GLASS_BREAK, 1.2f, .8f);
        dust(position().add(0, getBbHeight() + .3, 0), 0xFFFFFF, 20, .5);
    }
    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        if (boltCooldown > 0) boltCooldown--;
        if (tickCount % 20 == 0) for (int i = 0; i < weights.length; i++) weights[i] *= .88f;
        if (ward > 0) {
            if (disabled()) breakWard();
            else if (--ward == 0) { setExtra(0); setState(0); playSound(SoundEvents.BEACON_DEACTIVATE, 1f, 1.2f); }
            else if (ward % 10 == 0 && warded() != null) ring(position(), AURA, warded().color);
        }
        LivingEntity target = getTarget();
        if (casting() || disabled()) return;
        Element dominant = null;
        for (Element e : Element.values()) if (weights[e.ordinal()] >= THRESHOLD && (dominant == null || weights[e.ordinal()] > weights[dominant.ordinal()])) dominant = e;
        if (dominant != null && ward == 0 && cooldown == 0) {
            Element chosen = dominant;
            setExtra(chosen.ordinal() + 1);
            startCast(WARD_CAST, 30, () -> raiseWard(chosen));
            return;
        }
        if (target == null) return;
        keepDistance(target, 6, 12, 1);
        if (boltCooldown == 0 && hasLineOfSight(target) && distanceTo(target) < 18) {
            EnemyBolt.shoot(this, target, Element.ARCANE, 4, .9);
            playSound(SoundEvents.EVOKER_CAST_SPELL, .8f, 1f);
            boltCooldown = 35;
        }
    }
    @Override public void interrupt() {
        boolean warding = action() == WARD_CAST;
        super.interrupt();
        if (warding && ward == 0) setExtra(0);
    }
    @Override protected void castTell(int remaining) {
        Element e = extra() > 0 ? Element.values()[extra() - 1] : Element.ARCANE;
        dust(position().add(0, getBbHeight() + .3, 0), e.color, 3, .3);
        if (remaining % 5 == 0) playSound(SoundEvents.BEACON_AMBIENT, 1f, 1.5f);
        if (remaining % 3 == 0 && level() instanceof ServerLevel) ring(position(), AURA * castProgress(), e.color);
    }
    @Override protected float interruptDamage() { return 5; }
    @Override protected SoundEvent castSound() { return SoundEvents.ILLUSIONER_PREPARE_MIRROR; }
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.PIGLIN_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.PIGLIN_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.PIGLIN_DEATH; }
    @Override public float getVoicePitch() { return 1.25f; }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putInt("Ward", ward); tag.putInt("WardElement", extra()); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); ward = tag.getInt("Ward"); setExtra(tag.getInt("WardElement")); setState(ward > 0 ? 1 : 0); }
}
