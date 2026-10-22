package com.restor.magicka.enemy;

import com.restor.magicka.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import java.util.Comparator;

public class ElementBinder extends SpecialEnemy {
    public static final int BIND = 1;
    public static final int LOCK_TICKS = 100;
    private int boltCooldown;

    public ElementBinder(EntityType<? extends Monster> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 22).add(Attributes.MOVEMENT_SPEED, .24).add(Attributes.ARMOR, 2).add(Attributes.FOLLOW_RANGE, 28);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }
    /** Heavier prepared formulas draw the binder's attention first. */
    public ServerPlayer pickTarget() {
        return level().getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(20), SpecialEnemy::player).stream()
                .max(Comparator.<ServerPlayer>comparingInt(p -> CastingServer.prepared(p).size()).thenComparingDouble(p -> -distanceToSqr(p))).orElse(null);
    }
    public void bind(ServerPlayer player, Formula seen) {
        Formula now = CastingServer.prepared(player);
        int mask = ElementLocks.mask(now.empty() ? seen : now);
        if (mask == 0 || !hasLineOfSight(player) || distanceTo(player) > 24) return;
        ElementLocks.lock(player, mask, LOCK_TICKS);
        beam(getEyePosition(), player.getEyePosition(), 0xC77DFF);
    }
    @Override protected void customServerAiStep(net.minecraft.server.level.ServerLevel serverLevel) {
        super.customServerAiStep(serverLevel);
        if (boltCooldown > 0) boltCooldown--;
        if (tickCount % 20 == 0 && !casting()) { ServerPlayer pick = pickTarget(); if (pick != null) setTarget(pick); }
        LivingEntity target = getTarget();
        if (target == null || casting() || disabled()) return;
        keepDistance(target, 7, 12, 1);
        getLookControl().setLookAt(target, 30, 30);
        if (target instanceof ServerPlayer player && cooldown == 0 && hasLineOfSight(player)) {
            Formula seen = CastingServer.prepared(player);
            if (!seen.empty()) {
                setExtra(seen.packed());
                startCast(BIND, 22, () -> { bind(player, seen); cooldown = 160 + random.nextInt(61); });
                return;
            }
        }
        if (boltCooldown == 0 && hasLineOfSight(target) && distanceTo(target) < 18) {
            EnemyBolt.shoot(this, target, Element.ARCANE, 2, .8);
            playSound(SoundEvents.EVOKER_CAST_SPELL, .6f, 1.5f);
            boltCooldown = 45;
        }
    }
    @Override protected void castTell(int remaining) {
        var elements = Formula.unpack(extra()).elements();
        for (int i = 0; i < elements.size(); i++) {
            double a = (tickCount * .25) + i * Math.PI * 2 / elements.size();
            dust(position().add(Math.cos(a) * .9, getBbHeight() + .2, Math.sin(a) * .9), elements.get(i).color, 1, .03);
        }
        if (remaining % 6 == 0) playSound(SoundEvents.CHAIN_STEP, 1f, .6f + castProgress());
    }
    @Override protected float interruptDamage() { return 3; }
    @Override protected SoundEvent castSound() { return SoundEvents.EVOKER_PREPARE_WOLOLO; }
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.PIGLIN_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.PIGLIN_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.PIGLIN_DEATH; }
    @Override public float getVoicePitch() { return 1.4f; }
}
