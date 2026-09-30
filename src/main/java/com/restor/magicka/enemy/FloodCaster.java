package com.restor.magicka.enemy;

import com.restor.magicka.Element;
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
import net.minecraft.world.phys.Vec3;

public class FloodCaster extends SpecialEnemy {
    public static final int FLOOD = 1, SPRAY = 2;
    public static final double ZONE_RADIUS = 3;
    public static final int ZONE_TICKS = 200;
    private Vec3 aim;
    private int sprayCooldown;

    public FloodCaster(EntityType<? extends Monster> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 22).add(Attributes.MOVEMENT_SPEED, .23).add(Attributes.FOLLOW_RANGE, 24);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }
    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        if (sprayCooldown > 0) sprayCooldown--;
        LivingEntity target = getTarget();
        if (target == null || casting() || disabled()) return;
        keepDistance(target, 7, 12, 1);
        if (cooldown == 0 && distanceTo(target) < 18 && hasLineOfSight(target)) {
            aim = target.position();
            startCast(FLOOD, 20, () -> {
                MagicEnemies.flood(this, aim, ZONE_RADIUS, ZONE_TICKS);
                playSound(SoundEvents.GENERIC_SPLASH, 1.2f, .8f);
                cooldown = 110 + random.nextInt(30);
            });
        } else if (sprayCooldown == 0 && distanceTo(target) < 5 && hasLineOfSight(target)) {
            setAction(SPRAY);
            EnemyBolt.shoot(this, target, Element.WATER, 2, .9);
            playSound(SoundEvents.PLAYER_SPLASH, 1f, 1.3f);
            sprayCooldown = 30;
        } else if (action() == SPRAY && tickCount - actionStart > 10) setAction(0);
    }
    @Override protected void castTell(int remaining) {
        if (aim == null) return;
        if (remaining % 2 == 0) ring(aim, ZONE_RADIUS * (1 - remaining / 20.0 * .5), 0x4FD8FF);
        dust(position().add(0, getBbHeight() * .7, 0).subtract(Vec3.directionFromRotation(0, yBodyRot).scale(.4)), 0x4FD8FF, 2, .2);
    }
    @Override protected SoundEvent castSound() { return SoundEvents.BUCKET_FILL; }
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.FROG_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.FROG_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.FROG_DEATH; }
    @Override public float getVoicePitch() { return .7f; }
}
