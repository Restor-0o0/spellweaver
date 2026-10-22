package com.restor.magicka.enemy;

import com.restor.magicka.*;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Set;

public class Burrower extends SpecialEnemy {
    public static final int SURFACE = 0, BURROWED = 1, EMERGING = 2;
    public static final int DIG = 1;
    private static final Set<SpellShape> SEISMIC = Set.of(SpellShape.NOVA, SpellShape.TARGETED_ZONE, SpellShape.TARGETED_EXPLOSION, SpellShape.SPIKE);
    private int timer;

    public Burrower(EntityType<? extends Monster> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 24).add(Attributes.MOVEMENT_SPEED, .28).add(Attributes.ATTACK_DAMAGE, 5).add(Attributes.FOLLOW_RANGE, 28);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1, false) {
            @Override public boolean canUse() { return surfaced() && !casting() && super.canUse(); }
            @Override public boolean canContinueToUse() { return surfaced() && !casting() && super.canContinueToUse(); }
        });
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, .7) {
            @Override public boolean canUse() { return surfaced() && super.canUse(); }
        });
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }
    public boolean surfaced() { return state() == SURFACE; }
    public boolean burrowed() { return state() != SURFACE; }
    public BlockState ground() { return level().getBlockState(blockPosition().below()); }
    private boolean diggable() {
        BlockState ground = ground();
        return !isInWater() && (ground.is(BlockTags.MINEABLE_WITH_SHOVEL) || ground.is(BlockTags.BASE_STONE_OVERWORLD)) && !pinned();
    }
    /** Water, cold and roots pin it to the surface. */
    public boolean pinned() {
        BlockState feet = level().getBlockState(blockPosition()), ground = ground();
        return MagicStatuses.has(this, MagicStatuses.Status.WET) || MagicStatuses.has(this, MagicStatuses.Status.CHILLED) || MagicStatuses.has(this, MagicStatuses.Status.FROZEN)
                || MagicStatuses.has(this, MagicStatuses.Status.ROOTED) || feet.is(MagicBlocks.PUDDLE.get()) || ground.is(MagicBlocks.ICE.get()) || ground.is(MagicBlocks.ROOT.get()) || ground.is(BlockTags.ICE);
    }
    private void burrow() {
        setState(BURROWED); timer = 60 + random.nextInt(40);
        setInvisible(true); refreshDimensions();
        playSound(SoundEvents.ROOTED_DIRT_BREAK, 1.2f, .7f);
        debris(24);
    }
    private void surface(boolean forced) {
        setState(SURFACE); setInvisible(false); refreshDimensions();
        debris(30);
        if (forced) {
            MagicStatuses.apply(this, MagicStatuses.Status.STUNNED, 40, 1);
            cooldown = 200;
            playSound(SoundEvents.TURTLE_EGG_CRACK, 1.3f, .6f);
        } else {
            cooldown = 100;
            playSound(SoundEvents.RAVAGER_ATTACK, 1f, 1.4f);
            for (LivingEntity hit : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(1.2, .5, 1.2), e -> e != this && !ally(e))) {
                hit.hurt(damageSources().mobAttack(this), 6);
                hit.push(0, .55, 0); hit.hurtMarked = true;
            }
        }
    }
    public void forceOut() { if (burrowed()) surface(true); }
    private void debris(int count) {
        if (level() instanceof ServerLevel level) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground()), getX(), getY() + .1, getZ(), count, .4, .1, .4, .1);
    }
    @Override public EntityDimensions getDefaultDimensions(Pose pose) { return burrowed() ? super.getDefaultDimensions(pose).scale(1, .25f) : super.getDefaultDimensions(pose); }
    @Override public boolean isPickable() { return surfaced() && super.isPickable(); }
    @Override public boolean isPushable() { return surfaced(); }
    @Override protected float spellDamage(ServerPlayer caster, SpellDefinition spell, float amount, boolean brittle) {
        if (surfaced()) return amount;
        if (!SEISMIC.contains(spell.shape())) return 0;
        forceOut();
        return amount * .5f;
    }
    @Override protected float physicalDamage(DamageSource source, float amount) {
        if (surfaced() || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return amount;
        if (source.is(DamageTypeTags.IS_EXPLOSION)) { forceOut(); return amount * .5f; }
        return 0;
    }
    @Override protected void customServerAiStep(net.minecraft.server.level.ServerLevel serverLevel) {
        super.customServerAiStep(serverLevel);
        LivingEntity target = getTarget();
        switch (state()) {
            case BURROWED -> {
                if (pinned()) { forceOut(); return; }
                if (tickCount % 3 == 0) debris(3);
                if (tickCount % 12 == 0) playSound(SoundEvents.ROOTED_DIRT_STEP, .8f, .6f);
                if (target != null) getNavigation().moveTo(target, 1.5);
                if (--timer <= 0 || target != null && distanceTo(target) < 2.2) { setState(EMERGING); timer = 14; getNavigation().stop(); playSound(SoundEvents.WARDEN_DIG, .6f, 1.6f); }
            }
            case EMERGING -> {
                if (pinned()) { forceOut(); return; }
                ring(position(), 1.2, 0x8A6A45);
                debris(4);
                if (--timer <= 0) surface(false);
            }
            default -> {
                if (target != null && cooldown == 0 && !casting() && !disabled() && distanceTo(target) > 4 && diggable())
                    startCast(DIG, 20, () -> { if (diggable()) burrow(); });
            }
        }
    }
    @Override protected void castTell(int remaining) { if (remaining % 2 == 0) debris(4); }
    @Override protected SoundEvent castSound() { return SoundEvents.ROOTED_DIRT_HIT; }
    @Override protected SoundEvent getAmbientSound() { return burrowed() ? null : SoundEvents.RAVAGER_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.HOGLIN_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.HOGLIN_DEATH; }
    @Override public float getVoicePitch() { return 1.5f; }
}
