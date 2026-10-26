package com.restor.magicka.enemy;

import com.restor.magicka.SpellDefinition;
import com.restor.magicka.SpellShape;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.Set;

public class ShieldBeetle extends SpecialEnemy {
    public static final int CHARGE = 1, RECOVER = 2;
    public static final float FRONT = .2f, REAR = 1.5f;
    private static final Set<SpellShape> BLOCKED = Set.of(SpellShape.PROJECTILE, SpellShape.BURST_PROJECTILE, SpellShape.DIRECT_TARGET, SpellShape.CHAIN, SpellShape.SPRAY);
    private int charging, recovering;

    public ShieldBeetle(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        moveControl = new MoveControl(this) {
            @Override public void tick() {
                float before = mob.getYRot();
                super.tick();
                mob.setYRot(Mth.approachDegrees(before, mob.getYRot(), 5));
            }
        };
    }
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 30).add(Attributes.MOVEMENT_SPEED, .2).add(Attributes.ATTACK_DAMAGE, 5)
                .add(Attributes.ARMOR, 4).add(Attributes.KNOCKBACK_RESISTANCE, .6).add(Attributes.FOLLOW_RANGE, 24);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1, true) {
            @Override public boolean canUse() { return charging == 0 && recovering == 0 && super.canUse(); }
            @Override public boolean canContinueToUse() { return charging == 0 && recovering == 0 && super.canContinueToUse(); }
        });
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, .7));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }
    @Override protected float tickHeadTurn(float yRot, float step) { yBodyRot = getYRot(); return step; }
    @Override public int getMaxHeadYRot() { return 15; }

    public boolean guarding() { return !disabled() && recovering == 0; }
    public boolean front(Vec3 from) {
        Vec3 to = flat(from.subtract(position()));
        return to.lengthSqr() > 1e-4 && to.normalize().dot(Vec3.directionFromRotation(0, yBodyRot)) > .5;
    }
    public boolean rear(Vec3 from) {
        Vec3 to = flat(from.subtract(position()));
        return to.lengthSqr() > 1e-4 && to.normalize().dot(Vec3.directionFromRotation(0, yBodyRot)) < -.5;
    }
    private float block(float amount) {
        playSound(SoundEvents.SHIELD_BLOCK, 1f, .7f);
        dust(position().add(Vec3.directionFromRotation(0, yBodyRot).scale(.9)).add(0, .7, 0), 0xC9A646, 6, .3);
        return amount * FRONT;
    }
    @Override protected float spellDamage(ServerPlayer caster, SpellDefinition spell, float amount, boolean brittle) {
        if (rear(caster.position())) return amount * REAR;
        return guarding() && BLOCKED.contains(spell.shape()) && front(caster.position()) ? block(amount) : amount;
    }
    @Override protected float physicalDamage(DamageSource source, float amount) {
        Vec3 from = source.getSourcePosition();
        if (from == null || source.getMsgId().equals("fall") || source.getMsgId().equals("genericKill")) return amount;
        if (rear(from)) return amount * REAR;
        if (!guarding() || !front(from)) return amount;
        return source.getDirectEntity() instanceof Projectile ? block(amount) : amount * .5f;
    }
    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        LivingEntity target = getTarget();
        if (charging > 0) {
            Vec3 forward = Vec3.directionFromRotation(0, getYRot());
            setDeltaMovement(forward.x * .55, getDeltaMovement().y, forward.z * .55);
            for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(.3), e -> e != this && !ally(e))) {
                hit.hurt(net.minecraft.world.damagesource.DamageSource.mobAttack(this), 6);
                hit.push(forward.x * 1.2, .3, forward.z * 1.2); hit.hurtMarked = true;
                charging = 1;
            }
            if (--charging == 0 || horizontalCollision) { charging = 0; recovering = 40; setAction(RECOVER); playSound(SoundEvents.SHIELD_BREAK, .8f, 1.4f); }
        } else if (recovering > 0) {
            getNavigation().stop();
            if (--recovering == 0) setAction(0);
        } else if (target != null && cooldown == 0 && !casting() && !disabled() && distanceTo(target) > 3 && distanceTo(target) < 9 && front(target.position()) && hasLineOfSight(target)) {
            startCast(CHARGE, 16, () -> { charging = 14; cooldown = 120; playSound(SoundEvents.RAVAGER_ROAR, .7f, 1.5f); });
        }
    }
    @Override protected void castTell(int remaining) { if (remaining % 4 == 0) playSound(SoundEvents.RAVAGER_STEP, .6f, 1.6f); }
    @Override protected SoundEvent castSound() { return SoundEvents.IRON_GOLEM_REPAIR; }
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.SPIDER_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.TURTLE_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.TURTLE_DEATH; }
    @Override protected void playStepSound(BlockPos pos, BlockState state) { playSound(SoundEvents.SPIDER_STEP, .3f, .7f); }
    @Override public float getVoicePitch() { return .6f; }
}
