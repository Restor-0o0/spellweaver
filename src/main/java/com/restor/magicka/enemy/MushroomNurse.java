package com.restor.magicka.enemy;

import com.restor.magicka.MagicStatuses;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.Comparator;

public class MushroomNurse extends SpecialEnemy {
    public static final int HEAL = 1;
    public static final float AMOUNT = 6;
    private LivingEntity patient;

    public MushroomNurse(EntityType<? extends Monster> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 14).add(Attributes.MOVEMENT_SPEED, .25).add(Attributes.ATTACK_DAMAGE, 1).add(Attributes.FOLLOW_RANGE, 20);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, 5, 1, 1.35, e -> player(e)));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, .7));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8));
    }
    public LivingEntity findPatient() {
        return allies(12).stream().filter(e -> e.getHealth() < e.getMaxHealth() * .9 && hasLineOfSight(e))
                .min(Comparator.comparingDouble(e -> e.getHealth() / e.getMaxHealth() - (e instanceof SpecialEnemy ? .25 : 0))).orElse(null);
    }
    @Override protected void customServerAiStep(net.minecraft.server.level.ServerLevel serverLevel) {
        super.customServerAiStep(serverLevel);
        if (casting() || disabled()) return;
        if (cooldown == 0 && (patient = findPatient()) != null) {
            LivingEntity target = patient;
            startCast(HEAL, 15, () -> {
                if (!target.isAlive() || distanceTo(target) > 14) return;
                target.heal(AMOUNT);
                MagicStatuses.apply(target, MagicStatuses.Status.REGENERATING, 100, 1);
                beam(getEyePosition(), target.position().add(0, target.getBbHeight() * .6, 0), 0xFFD66B);
                if (level() instanceof ServerLevel level) level.sendParticles(ParticleTypes.HAPPY_VILLAGER, target.getX(), target.getY() + target.getBbHeight(), target.getZ(), 8, .4, .3, .4, 0);
                playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 1.4f);
                cooldown = 60;
            });
            return;
        }
        if (tickCount % 20 == 0 && getNavigation().isDone()) {
            var group = allies(16);
            Player threat = level().getNearestPlayer(this, 16);
            if (!group.isEmpty()) {
                Vec3 center = group.stream().map(Entity::position).reduce(Vec3.ZERO, Vec3::add).scale(1.0 / group.size());
                if (threat != null) center = center.add(flat(center.subtract(threat.position())).normalize().scale(3));
                if (distanceToSqr(center) > 9) getNavigation().moveTo(center.x, center.y, center.z, 1);
            }
        }
    }
    @Override protected void castTell(int remaining) {
        if (patient != null && remaining % 3 == 0) beam(getEyePosition(), patient.position().add(0, patient.getBbHeight() * .6, 0), 0xB6F07A);
        dust(position().add(0, 1.7, 0), 0xFFD66B, 2, .5);
    }
    @Override protected float interruptDamage() { return 2; }
    @Override protected SoundEvent castSound() { return SoundEvents.AMETHYST_BLOCK_CHIME; }
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.MOOSHROOM_EAT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.MOSS_HIT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.MOSS_BREAK; }
    @Override public boolean doHurtTarget(net.minecraft.server.level.ServerLevel serverLevel, Entity target) { return false; }
}
