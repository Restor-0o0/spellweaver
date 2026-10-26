package com.restor.magicka.enemy;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public class GoblinArcher extends Goblin {
    public static final int AIM = 1;

    public GoblinArcher(EntityType<? extends Monster> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 12).add(Attributes.MOVEMENT_SPEED, .28).add(Attributes.FOLLOW_RANGE, 28);
    }
    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        LivingEntity target = getTarget();
        if (target == null || casting() || disabled()) return;
        keepDistance(target, 8, 14, 1);
        getLookControl().setLookAt(target, 30, 30);
        if (cooldown == 0 && hasLineOfSight(target) && distanceTo(target) < 20) startCast(AIM, 20, () -> { shoot(target); cooldown = 30 + random.nextInt(20); });
    }
    private void shoot(LivingEntity target) {
        if (!target.isAlive()) return;
        Arrow arrow = new Arrow(level, this);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        arrow.setBaseDamage(2.5);
        double dx = target.getX() - getX(), dy = target.getY(.33) - arrow.getY(), dz = target.getZ() - getZ();
        arrow.shoot(dx, dy + Math.sqrt(dx * dx + dz * dz) * .2, dz, 1.6f, 10 - level.getDifficulty().getId() * 3);
        level.addFreshEntity(arrow);
        playSound(SoundEvents.SKELETON_SHOOT, 1f, 1.2f + random.nextFloat() * .2f);
    }
    @Override protected float interruptDamage() { return 3; }
    @Override protected SoundEvent castSound() { return SoundEvents.CROSSBOW_LOADING_START; }
}
