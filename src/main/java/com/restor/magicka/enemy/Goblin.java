package com.restor.magicka.enemy;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public abstract class Goblin extends SpecialEnemy {
    protected Goblin(EntityType<? extends Monster> type, Level level) { super(type, level); }
    @Override protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, .8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }
    public static boolean kin(net.minecraft.world.entity.LivingEntity e) { return e instanceof Goblin || e instanceof Countermage || e instanceof ElementBinder; }
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.PIGLIN_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.PIGLIN_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.PIGLIN_DEATH; }
    @Override protected void playStepSound(BlockPos pos, BlockState state) { playSound(SoundEvents.PIGLIN_STEP, .12f, 1.3f); }
    @Override public float getVoicePitch() { return 1.45f + (random.nextFloat() - .5f) * .15f; }
}
