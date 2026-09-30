package com.restor.magicka.enemy;

import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Flyers orbit their target at a fixed height; being stunned or frozen drops them out of the air. */
public abstract class HoverEnemy extends SpecialEnemy {
    public static final int CHARGE = 1;
    protected int retreat;
    private double orbit = Math.random() * Math.PI * 2;
    private Vec3 home;

    protected HoverEnemy(EntityType<? extends Monster> type, Level level) { super(type, level); setNoGravity(true); }
    @Override protected void registerGoals() {
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }
    protected double radius() { return 7; }
    protected double altitude() { return 5; }
    protected double speed() { return .09; }
    protected int chargeTicks() { return 15; }
    protected abstract void attack(LivingEntity target);

    @Override public void travel(Vec3 input) {
        if (!isNoGravity()) { super.travel(input); return; }
        move(MoverType.SELF, getDeltaMovement());
        setDeltaMovement(getDeltaMovement().scale(.9));
    }
    @Override protected void checkFallDamage(double y, boolean ground, BlockState state, BlockPos pos) {}
    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }
    @Override public boolean onClimbable() { return false; }

    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        boolean grounded = disabled();
        if (isNoGravity() == grounded) setNoGravity(!grounded);
        if (grounded) return;
        if (home == null) home = position();
        LivingEntity target = getTarget();
        Vec3 anchor = target != null ? target.position() : home;
        orbit += (retreat > 0 ? .06 : .035) * (getId() % 2 == 0 ? 1 : -1);
        double r = (target != null ? radius() : 3) + (retreat > 0 ? 4 : 0);
        Vec3 goal = anchor.add(Math.cos(orbit) * r, (target != null ? altitude() : 2) + Math.sin(tickCount * .08) * .6, Math.sin(orbit) * r);
        Vec3 steer = goal.subtract(position());
        if (steer.lengthSqr() > 1) steer = steer.normalize();
        if (casting()) steer = steer.scale(.2);
        setDeltaMovement(getDeltaMovement().add(steer.scale(speed())));
        if (retreat > 0) retreat--;
        if (target == null) return;
        getLookControl().setLookAt(target, 30, 30);
        if (!casting() && cooldown == 0 && hasLineOfSight(target) && distanceTo(target) < 22)
            startCast(CHARGE, chargeTicks(), () -> { if (target.isAlive() && hasLineOfSight(target)) attack(target); retreat = 30; });
    }
    @Override protected float interruptDamage() { return 2; }
}
