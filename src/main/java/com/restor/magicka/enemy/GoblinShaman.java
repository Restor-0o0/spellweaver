package com.restor.magicka.enemy;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
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
import java.util.List;

public class GoblinShaman extends Goblin {
    public static final int CHANT = 1;
    public static final double RANGE = 10;

    public GoblinShaman(EntityType<? extends Monster> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 14).add(Attributes.MOVEMENT_SPEED, .26).add(Attributes.FOLLOW_RANGE, 24);
    }
    public List<LivingEntity> kin() { return allies(RANGE).stream().filter(Goblin::kin).toList(); }
    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        LivingEntity target = getTarget();
        if (target == null || casting() || disabled()) return;
        keepDistance(target, 9, 15, 1);
        if (cooldown == 0 && !kin().isEmpty()) startCast(CHANT, 30, () -> { rally(); cooldown = 200 + random.nextInt(60); });
    }
    public void rally() {
        for (LivingEntity e : kin()) {
            e.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 160, 0), this);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 160, 0), this);
            e.heal(4);
            if (level() instanceof ServerLevel level) level.sendParticles(ParticleTypes.ANGRY_VILLAGER, e.getX(), e.getY() + e.getBbHeight() + .2, e.getZ(), 2, .2, .1, .2, 0);
        }
        playSound(SoundEvents.RAVAGER_ROAR, .7f, 1.8f);
    }
    @Override protected void castTell(int remaining) {
        if (remaining % 6 == 0) { playSound(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 1.2f, .7f); ring(position(), RANGE * (1 - remaining / 30.0), 0xB0402A); }
        dust(position().add(0, getBbHeight() + .2, 0), 0xB0402A, 2, .2);
    }
    @Override protected float interruptDamage() { return 3; }
    @Override protected SoundEvent castSound() { return SoundEvents.NOTE_BLOCK_BASEDRUM.value(); }
}
