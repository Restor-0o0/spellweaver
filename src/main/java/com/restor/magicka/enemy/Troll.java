package com.restor.magicka.enemy;

import com.restor.magicka.Element;
import com.restor.magicka.MagicStatuses;
import com.restor.magicka.SpellDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class Troll extends SpecialEnemy {
    public static final int SMASH = 1;
    public static final int SCORCH_TICKS = 100;
    public static final float REGEN = 1.5f, FIRE_BONUS = 1.5f;
    private int scorched;

    public Troll(EntityType<? extends Monster> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 70).add(Attributes.MOVEMENT_SPEED, .24).add(Attributes.ATTACK_DAMAGE, 10)
                .add(Attributes.ARMOR, 2).add(Attributes.KNOCKBACK_RESISTANCE, .7).add(Attributes.FOLLOW_RANGE, 28);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1, true) {
            @Override public boolean canUse() { return !casting() && super.canUse(); }
            @Override public boolean canContinueToUse() { return !casting() && super.canContinueToUse(); }
        });
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, .6));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }
    public boolean scorched() { return scorched > 0 || MagicStatuses.has(this, MagicStatuses.Status.BURNING); }
    public void scorch() { scorched = SCORCH_TICKS; setState(1); }
    @Override protected float spellDamage(ServerPlayer caster, SpellDefinition spell, float amount, boolean brittle) {
        if (spell.formula().total(Element.FIRE) == 0) return amount;
        scorch();
        return amount * FIRE_BONUS;
    }
    @Override public void tick() {
        super.tick();
        if (!level().isClientSide()) regenerate();
    }
    private void regenerate() {
        if (MagicStatuses.has(this, MagicStatuses.Status.BURNING)) scorch();
        else if (scorched > 0 && --scorched == 0) setState(0);
        if (!scorched() && tickCount % 10 == 0 && getHealth() < getMaxHealth() && isAlive()) {
            heal(REGEN);
            if (level() instanceof ServerLevel level && tickCount % 20 == 0)
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + getBbHeight() * .6, getZ(), 3, .5, .6, .5, 0);
        }
        if (scorched() && tickCount % 6 == 0 && level() instanceof ServerLevel level)
            level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + getBbHeight() * .7, getZ(), 2, .4, .4, .4, .01);
    }
    @Override protected void customServerAiStep(ServerLevel serverLevel) {
        super.customServerAiStep(serverLevel);
        LivingEntity target = getTarget();
        if (target != null && cooldown == 0 && !casting() && !disabled() && distanceTo(target) < 4) {
            startCast(SMASH, 20, () -> {
                Vec3 forward = Vec3.directionFromRotation(0, getYRot());
                Vec3 at = position().add(forward.scale(2));
                playSound(SoundEvents.GENERIC_EXPLODE.value(), .7f, 1.6f);
                playSound(SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, 1f, .6f);
                if (level() instanceof ServerLevel level)
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()), at.x, getY() + .1, at.z, 40, 1.2, .1, 1.2, .15);
                for (LivingEntity hit : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3.5, 1, 3.5), e -> e != this && !ally(e) && e.position().distanceToSqr(at) < 7.5)) {
                    hit.hurt(damageSources().mobAttack(this), 11);
                    Vec3 push = flat(hit.position().subtract(position())).normalize();
                    hit.push(push.x, .45, push.z); hit.syncVelocity = true;
                }
                cooldown = 80;
            });
        }
    }
    @Override protected void castTell(int remaining) { if (remaining % 4 == 0) playSound(SoundEvents.RAVAGER_STEP, .8f, .6f); }
    @Override protected float interruptDamage() { return 10; }
    @Override protected SoundEvent castSound() { return SoundEvents.RAVAGER_ROAR; }
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.POLAR_BEAR_WARNING; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.RAVAGER_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.RAVAGER_DEATH; }
    @Override protected void playStepSound(BlockPos pos, BlockState state) { playSound(SoundEvents.RAVAGER_STEP, .5f, .8f); }
    @Override public float getVoicePitch() { return .7f; }
    @Override public void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput tag) { super.addAdditionalSaveData(tag); tag.putInt("Scorched", scorched); }
    @Override public void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput tag) {
        super.readAdditionalSaveData(tag);
        scorched = tag.getIntOr("Scorched", 0);
        if (scorched > 0) setState(1);
    }
}
