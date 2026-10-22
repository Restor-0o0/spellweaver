package com.restor.magicka.enemy;

import com.restor.magicka.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.level.block.state.BlockState;
import java.util.Comparator;

public class StoneEater extends SpecialEnemy {
    public static final int EAT = 2;
    private BlockPos meal;
    private int chew;

    public StoneEater(EntityType<? extends Monster> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 32).add(Attributes.MOVEMENT_SPEED, .22).add(Attributes.ATTACK_DAMAGE, 5)
                .add(Attributes.ARMOR, 4).add(Attributes.KNOCKBACK_RESISTANCE, .5).add(Attributes.FOLLOW_RANGE, 24);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1, true) {
            @Override public boolean canUse() { return meal == null && super.canUse(); }
            @Override public boolean canContinueToUse() { return meal == null && super.canContinueToUse(); }
        });
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, .6) {
            @Override public boolean canUse() { return meal == null && super.canUse(); }
        });
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }
    public static boolean edible(BlockState state) { return state.getBlock() instanceof MagicBlocks.TemporaryBlock && !state.is(MagicBlocks.PUDDLE.get()) && !state.is(MagicBlocks.LIGHT.get()); }
    public BlockPos findMeal() {
        if (!(level() instanceof ServerLevel level)) return null;
        return MagicConstructionManager.get(level).structures().stream().flatMap(c -> c.blocks.stream())
                .filter(p -> p.distSqr(blockPosition()) < 256 && edible(level.getBlockState(p)))
                .min(Comparator.comparingDouble(p -> p.distSqr(blockPosition()))).orElse(null);
    }
    @Override protected void customServerAiStep(net.minecraft.server.level.ServerLevel serverLevel) {
        super.customServerAiStep(serverLevel);
        if (disabled()) return;
        if (meal != null && !edible(level().getBlockState(meal))) meal = null;
        if (meal == null && tickCount % 10 == 0) meal = findMeal();
        if (meal == null) { if (action() == EAT) setAction(0); return; }
        double reach = distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(meal));
        getLookControl().setLookAt(meal.getX() + .5, meal.getY() + .5, meal.getZ() + .5);
        if (reach > 6.5) { getNavigation().moveTo(meal.getX() + .5, meal.getY(), meal.getZ() + .5, 1.15); chew = 0; return; }
        getNavigation().stop();
        if (action() != EAT) setAction(EAT);
        if (++chew % 5 == 0) playSound(SoundEvents.GENERIC_EAT.value(), .8f, .5f);
        if (chew >= 15) {
            chew = 0;
            if (level() instanceof ServerLevel level)
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(meal)), meal.getX() + .5, meal.getY() + .5, meal.getZ() + .5, 20, .3, .3, .3, .1);
            level().removeBlock(meal, false);
            playSound(SoundEvents.STONE_BREAK, 1f, .6f);
            heal(2);
            MagicStatuses.apply(this, MagicStatuses.Status.STONE_SHELL, 100, 1);
            meal = null;
        }
    }
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.RAVAGER_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.STONE_HIT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.RAVAGER_DEATH; }
    @Override protected void playStepSound(BlockPos pos, BlockState state) { playSound(SoundEvents.GRAVEL_STEP, .6f, .7f); }
    @Override public float getVoicePitch() { return .8f; }
}
