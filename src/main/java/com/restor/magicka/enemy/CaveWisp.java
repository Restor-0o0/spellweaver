package com.restor.magicka.enemy;

import com.restor.magicka.Element;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

public class CaveWisp extends HoverEnemy {
    public CaveWisp(EntityType<? extends Monster> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 10).add(Attributes.FLYING_SPEED, .5).add(Attributes.MOVEMENT_SPEED, .3).add(Attributes.FOLLOW_RANGE, 28);
    }
    @Override protected double radius() { return 6 + getId() % 4; }
    @Override protected void attack(LivingEntity target) {
        EnemyBolt.shoot(this, target, Element.ARCANE, 3, .9);
        playSound(SoundEvents.ALLAY_THROW, 1f, .7f);
        cooldown = 50 + random.nextInt(20);
    }
    @Override protected void castTell(int remaining) { dust(position().add(0, .4, 0), 0xBFFBFF, 2, .15); }
    @Override protected SoundEvent castSound() { return SoundEvents.BEACON_POWER_SELECT; }
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.SILVERFISH_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.SILVERFISH_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.SILVERFISH_DEATH; }
    @Override public float getVoicePitch() { return 1.7f; }
}
