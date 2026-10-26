package com.restor.magicka.enemy;

import com.restor.magicka.Element;
import com.restor.magicka.MagicStatuses;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import java.util.Comparator;

public class SparkImp extends HoverEnemy {
    public SparkImp(EntityType<? extends Monster> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 8).add(Attributes.FLYING_SPEED, .6).add(Attributes.MOVEMENT_SPEED, .32).add(Attributes.FOLLOW_RANGE, 24);
    }
    @Override protected double radius() { return 5; }
    @Override protected double altitude() { return 3; }
    @Override protected double speed() { return .12; }
    @Override protected int chargeTicks() { return 12; }
    @Override protected void customServerAiStep() {
        if (tickCount % 20 == 0 && !casting())
            level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(18), SpecialEnemy::player).stream()
                    .min(Comparator.comparingDouble(p -> distanceToSqr(p) * (MagicStatuses.has(p, MagicStatuses.Status.WET) ? .25 : 1))).ifPresent(this::setTarget);
        super.customServerAiStep();
    }
    @Override protected void attack(LivingEntity target) {
        EnemyBolt.shoot(this, target, Element.LIGHTNING, 2, 1.1);
        playSound(SoundEvents.TRIDENT_THUNDER, .25f, 2f);
        cooldown = 40 + random.nextInt(20);
    }
    @Override protected void castTell(int remaining) { dust(position().add(0, .9, 0), 0x9FD8FF, 2, .2); }
    @Override protected SoundEvent castSound() { return SoundEvents.BEEHIVE_WORK; }
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.BAT_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.BAT_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.BAT_DEATH; }
}
