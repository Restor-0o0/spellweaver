package com.restor.magicka.enemy;

import com.restor.magicka.Element;
import com.restor.magicka.MagicStatuses;
import com.restor.magicka.SpellDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class ArmoredKnight extends SpecialEnemy {
    public static final int CLEAVE = 1, LEAP = 2;
    public static final float WARDED = .35f, LIGHTNING = 2, SHIELDED = .4f;
    public static final int STUN_TICKS = 50;

    public ArmoredKnight(EntityType<? extends Monster> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 50).add(Attributes.MOVEMENT_SPEED, .22).add(Attributes.ATTACK_DAMAGE, 8)
                .add(Attributes.ARMOR, 12).add(Attributes.ARMOR_TOUGHNESS, 4).add(Attributes.KNOCKBACK_RESISTANCE, .8).add(Attributes.FOLLOW_RANGE, 24);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1, true) {
            @Override public boolean canUse() { return !casting() && super.canUse(); }
            @Override public boolean canContinueToUse() { return !casting() && super.canContinueToUse(); }
        });
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, .6));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }
    public boolean front(Vec3 from) {
        Vec3 to = flat(from.subtract(position()));
        return to.lengthSqr() > 1e-4 && to.normalize().dot(Vec3.directionFromRotation(0, yBodyRot)) > .5;
    }
    @Override protected float spellDamage(ServerPlayer caster, SpellDefinition spell, float amount, boolean brittle) {
        if (spell.formula().count(Element.LIGHTNING) > 0) {
            MagicStatuses.apply(this, MagicStatuses.Status.STUNNED, STUN_TICKS, 1);
            interrupt();
            playSound(SoundEvents.LIGHTNING_BOLT_IMPACT, .6f, 1.6f);
            if (level() instanceof ServerLevel level) level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.2, getZ(), 30, .4, .7, .4, .3);
            return amount * LIGHTNING;
        }
        playSound(SoundEvents.ANVIL_PLACE, .35f, 1.8f);
        if (level() instanceof ServerLevel level) level.sendParticles(ParticleTypes.ENCHANTED_HIT, getX(), getY() + 1.2, getZ(), 10, .4, .6, .4, .1);
        return amount * WARDED;
    }
    @Override protected float physicalDamage(DamageSource source, float amount) {
        Vec3 from = source.getSourcePosition();
        if (from == null || disabled() || source.is(DamageTypes.FALL) || source.is(DamageTypes.GENERIC_KILL) || !front(from)) return amount;
        playSound(SoundEvents.SHIELD_BLOCK, 1f, .8f);
        return amount * SHIELDED;
    }
    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        LivingEntity target = getTarget();
        if (target == null || cooldown > 0 || casting() || disabled()) return;
        double d = distanceTo(target);
        if (d < 3.2) {
            startCast(CLEAVE, 16, () -> {
                Vec3 forward = Vec3.directionFromRotation(0, getYRot());
                playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, .7f);
                if (level() instanceof ServerLevel level) {
                    Vec3 at = position().add(forward.scale(1.6)).add(0, 1.1, 0);
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 3, .6, .1, .6, 0);
                }
                for (LivingEntity hit : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3, 1, 3), e -> e != this && !ally(e) && e.distanceToSqr(this) < 12 && front(e.position()))) {
                    hit.hurt(damageSources().mobAttack(this), 10);
                    hit.push(forward.x * .9, .25, forward.z * .9); hit.hurtMarked = true;
                }
                cooldown = 60;
            });
        } else if (d > 5 && d < 11 && onGround() && hasLineOfSight(target)) {
            startCast(LEAP, 12, () -> {
                Vec3 to = flat(target.position().subtract(position()));
                Vec3 v = to.normalize().scale(Math.min(1.3, to.length() * .13));
                setDeltaMovement(v.x, .55, v.z); hurtMarked = true;
                playSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 1.2f, .7f);
                cooldown = 90;
            });
        }
    }
    @Override protected void castTell(int remaining) {
        if (remaining % 4 == 0) playSound(action() == CLEAVE ? SoundEvents.ARMOR_EQUIP_IRON.value() : SoundEvents.IRON_GOLEM_STEP, .7f, 1.3f);
    }
    @Override protected float interruptDamage() { return 8; }
    @Override protected SoundEvent castSound() { return SoundEvents.ARMOR_EQUIP_NETHERITE.value(); }
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.ARMOR_EQUIP_CHAIN.value(); }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.IRON_GOLEM_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.IRON_GOLEM_DEATH; }
    @Override protected void playStepSound(BlockPos pos, BlockState state) { playSound(SoundEvents.NETHERITE_BLOCK_STEP, .6f, .8f); }
}
