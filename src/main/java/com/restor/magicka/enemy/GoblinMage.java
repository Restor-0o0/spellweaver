package com.restor.magicka.enemy;

import com.restor.magicka.Element;
import com.restor.magicka.MagicStatuses;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

public class GoblinMage extends Goblin {
    public static final int CAST = 1;

    public GoblinMage(EntityType<? extends Monster> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 12).add(Attributes.MOVEMENT_SPEED, .26).add(Attributes.FOLLOW_RANGE, 24);
    }
    public Element element() { return Element.FIRE; }
    protected float boltDamage() { return 4; }
    protected int tellColor() { return 0xE0602A; }
    protected void hit(LivingEntity target) {}
    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        LivingEntity target = getTarget();
        if (target == null || casting() || disabled()) return;
        keepDistance(target, 6, 12, 1);
        if (cooldown == 0 && hasLineOfSight(target) && distanceTo(target) < 18) startCast(CAST, 25, () -> {
            if (target.isAlive()) { EnemyBolt.shoot(this, target, element(), boltDamage(), .7); hit(target); }
            playSound(release(), .8f, 1.3f);
            cooldown = 60 + random.nextInt(30);
        });
    }
    protected SoundEvent release() { return SoundEvents.BLAZE_SHOOT; }
    @Override protected void castTell(int remaining) {
        dust(position().add(0, getBbHeight() + .2, 0), tellColor(), 2, .15);
    }
    @Override protected float interruptDamage() { return 3; }
    @Override protected SoundEvent castSound() { return SoundEvents.FIRECHARGE_USE; }

    public static class Frost extends GoblinMage {
        public Frost(EntityType<? extends Monster> type, Level level) { super(type, level); }
        @Override public Element element() { return Element.COLD; }
        @Override protected float boltDamage() { return 3; }
        @Override protected int tellColor() { return 0xBFE4F2; }
        @Override protected void hit(LivingEntity target) { target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), this); }
        @Override protected SoundEvent release() { return SoundEvents.PLAYER_HURT_FREEZE; }
        @Override protected SoundEvent castSound() { return SoundEvents.POWDER_SNOW_STEP; }
    }

    public static class Storm extends GoblinMage {
        public Storm(EntityType<? extends Monster> type, Level level) { super(type, level); }
        @Override public Element element() { return Element.LIGHTNING; }
        @Override protected float boltDamage() { return 3; }
        @Override protected int tellColor() { return 0xB8C8FF; }
        @Override protected SoundEvent release() { return SoundEvents.TRIDENT_THUNDER; }
        @Override protected SoundEvent castSound() { return SoundEvents.BEACON_POWER_SELECT; }
        @Override protected void customServerAiStep() {
            LivingEntity target = getTarget();
            if (target != null && MagicStatuses.has(target, MagicStatuses.Status.WET) && cooldown > 20) cooldown--;
            super.customServerAiStep();
        }
    }
}
