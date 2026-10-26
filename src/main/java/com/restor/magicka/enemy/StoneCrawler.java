package com.restor.magicka.enemy;

import com.restor.magicka.Element;
import com.restor.magicka.SpellDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class StoneCrawler extends SpecialEnemy {
    public static final int SLAM = 1;
    public static final float ARMORED = .4f;
    private static final int SHATTER_TICKS = 300;
    private int shattered;

    public StoneCrawler(EntityType<? extends Monster> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 40).add(Attributes.MOVEMENT_SPEED, .18).add(Attributes.ATTACK_DAMAGE, 7)
                .add(Attributes.ARMOR, 8).add(Attributes.KNOCKBACK_RESISTANCE, .9).add(Attributes.FOLLOW_RANGE, 24);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1, true) {
            @Override public boolean canUse() { return !casting() && super.canUse(); }
            @Override public boolean canContinueToUse() { return !casting() && super.canContinueToUse(); }
        });
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, .6));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }
    public boolean armored() { return state() == 0; }
    public void shatter() {
        if (!armored()) return;
        shattered = SHATTER_TICKS; setState(1);
        getAttribute(Attributes.ARMOR).setBaseValue(0);
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(.27);
        playSound(SoundEvents.ANVIL_LAND, 1f, .6f);
        playSound(SoundEvents.STONE_BREAK, 1.4f, .7f);
        if (level instanceof ServerLevel level)
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBBLESTONE.defaultBlockState()), getX(), getY() + 1, getZ(), 60, .7, .6, .7, .2);
    }
    private void regrow() {
        setState(0);
        getAttribute(Attributes.ARMOR).setBaseValue(8);
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(.18);
        playSound(SoundEvents.STONE_PLACE, 1.2f, .6f);
    }
    @Override protected float spellDamage(ServerPlayer caster, SpellDefinition spell, float amount, boolean brittle) {
        if (armored()) {
            if (brittle && spell.formula().count(Element.EARTH) > 0) { shatter(); return amount * 1.5f; }
            return amount * ARMORED;
        }
        int size = Math.max(1, spell.formula().size());
        return amount * (1.2f + .5f * (spell.formula().count(Element.FIRE) + spell.formula().count(Element.ARCANE)) / size);
    }
    @Override protected float physicalDamage(DamageSource source, float amount) {
        return armored() && !source.getMsgId().equals("genericKill") && !source.getMsgId().equals("outOfWorld") ? amount * ARMORED : amount;
    }
    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        if (shattered > 0 && --shattered == 0) regrow();
        LivingEntity target = getTarget();
        if (target != null && cooldown == 0 && !casting() && !disabled() && distanceTo(target) < 3.5) {
            startCast(SLAM, 18, () -> {
                playSound(SoundEvents.GENERIC_EXPLODE, .8f, 1.4f);
                if (level instanceof ServerLevel level)
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), getX(), getY() + .1, getZ(), 50, 1.8, .1, 1.8, .15);
                for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3, 1, 3), e -> e != this && !ally(e) && e.distanceToSqr(this) < 16)) {
                    hit.hurt(net.minecraft.world.damagesource.DamageSource.mobAttack(this), 8);
                    hit.push(0, .5, 0); hit.hurtMarked = true;
                }
                cooldown = 70;
            });
        }
    }
    @Override protected void castTell(int remaining) { if (remaining % 3 == 0) ring(position(), 3.5, armored() ? 0x9A9C9F : 0xFF6A2A); }
    @Override protected float interruptDamage() { return 8; }
    @Override protected SoundEvent castSound() { return SoundEvents.RAVAGER_ATTACK; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return armored() ? SoundEvents.STONE_HIT : SoundEvents.RAVAGER_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.RAVAGER_DEATH; }
    @Override protected void playStepSound(BlockPos pos, BlockState state) { playSound(SoundEvents.IRON_GOLEM_STEP, .6f, .6f); }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putInt("Shattered", shattered); }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        shattered = tag.getInt("Shattered");
        if (shattered > 0) { setState(1); getAttribute(Attributes.ARMOR).setBaseValue(0); }
    }
}
