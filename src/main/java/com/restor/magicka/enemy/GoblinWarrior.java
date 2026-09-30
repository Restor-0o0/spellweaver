package com.restor.magicka.enemy;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

public class GoblinWarrior extends Goblin {
    public GoblinWarrior(EntityType<? extends Monster> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 16).add(Attributes.MOVEMENT_SPEED, .3).add(Attributes.ATTACK_DAMAGE, 4)
                .add(Attributes.ARMOR, 3).add(Attributes.FOLLOW_RANGE, 24);
    }
    @Override protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.2, false));
    }
}
