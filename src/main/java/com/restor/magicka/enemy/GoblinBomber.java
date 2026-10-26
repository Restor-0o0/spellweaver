package com.restor.magicka.enemy;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

public class GoblinBomber extends Goblin {
    public static final int THROW = 1;

    public GoblinBomber(EntityType<? extends Monster> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 14).add(Attributes.MOVEMENT_SPEED, .29).add(Attributes.FOLLOW_RANGE, 24);
    }
    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        LivingEntity target = getTarget();
        if (target == null || casting() || disabled()) return;
        keepDistance(target, 6, 11, 1);
        if (cooldown == 0 && hasLineOfSight(target) && distanceTo(target) < 13) startCast(THROW, 20, () -> {
            if (target.isAlive()) toss(target);
            cooldown = 70 + random.nextInt(40);
        });
    }
    private void toss(LivingEntity target) {
        GoblinBomb bomb = GoblinBomb.create(this, getEyePosition().add(0, .1, 0), 50);
        double dx = target.getX() + target.getDeltaMovement().x * 10 - getX(), dz = target.getZ() + target.getDeltaMovement().z * 10 - getZ();
        bomb.shoot(dx, target.getY() - bomb.getY() + Math.sqrt(dx * dx + dz * dz) * .35, dz, .8f, 6 - level.getDifficulty().getId() * 1.5f);
        playSound(SoundEvents.WITCH_THROW, 1f, 1.3f);
    }
    private void drop() { GoblinBomb.create(this, position().add(0, .3, 0), 25); }
    @Override public void interrupt() {
        if (casting() && action() == THROW) drop();
        super.interrupt();
    }
    @Override public void die(DamageSource source) {
        if (!level.isClientSide && casting() && action() == THROW) drop();
        super.die(source);
    }
    @Override protected void castTell(int remaining) {
        dust(position().add(0, getBbHeight() + .1, 0), 0x8A8A8A, 1, .08);
    }
    @Override protected float interruptDamage() { return 3; }
    @Override protected SoundEvent castSound() { return SoundEvents.FLINTANDSTEEL_USE; }
}
