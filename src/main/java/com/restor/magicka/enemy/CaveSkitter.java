package com.restor.magicka.enemy;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class CaveSkitter extends SpecialEnemy {
    public CaveSkitter(EntityType<? extends Monster> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 6).add(Attributes.MOVEMENT_SPEED, .34).add(Attributes.ATTACK_DAMAGE, 2).add(Attributes.FOLLOW_RANGE, 24);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new LeapAtTargetGoal(this, .35f));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.15, false));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, .8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.SILVERFISH_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.SILVERFISH_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.SILVERFISH_DEATH; }
    @Override protected void playStepSound(BlockPos pos, BlockState state) { playSound(SoundEvents.SILVERFISH_STEP, .15f, 1.3f); }
    @Override public float getVoicePitch() { return 1.4f; }
}
