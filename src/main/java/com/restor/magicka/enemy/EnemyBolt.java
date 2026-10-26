package com.restor.magicka.enemy;

import net.minecraft.util.Mth;
import com.restor.magicka.Element;
import com.restor.magicka.MagicStatuses;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import com.mojang.math.Vector3f;

public class EnemyBolt extends AbstractHurtingProjectile {
    private static final EntityDataAccessor<Integer> ELEMENT = SynchedEntityData.defineId(EnemyBolt.class, EntityDataSerializers.INT);
    private float damage = 3;

    public EnemyBolt(EntityType<? extends EnemyBolt> type, Level level) { super(type, level); }

    public static EnemyBolt shoot(Mob shooter, LivingEntity target, Element element, float damage, double speed) {
        EnemyBolt bolt = new EnemyBolt(MagicEnemies.BOLT.get(), shooter.level);
        Vec3 from = shooter.getEyePosition().add(shooter.getLookAngle().scale(.4));
        Vec3 aim = target.position().add(0, target.getBbHeight() * .55, 0).subtract(from).normalize();
        bolt.setOwner(shooter);
        bolt.moveTo(from.x, from.y, from.z, shooter.getYRot(), shooter.getXRot());
        bolt.setDeltaMovement(aim.scale(speed));
        bolt.entityData.set(ELEMENT, element.ordinal());
        bolt.damage = damage;
        shooter.level.addFreshEntity(bolt);
        return bolt;
    }
    public Element element() { return Element.values()[Mth.clamp(entityData.get(ELEMENT), 0, Element.values().length - 1)]; }

    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(ELEMENT, Element.ARCANE.ordinal()); }
    @Override protected boolean shouldBurn() { return false; }
    @Override protected float getInertia() { return 1f; }
    @Override public boolean isPickable() { return false; }
    @Override protected ParticleOptions getTrailParticle() {
        int c = element().color;
        return new DustParticleOptions(new Vector3f((c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f), 1.4f);
    }
    @Override public void tick() {
        super.tick();
        if (!level.isClientSide && tickCount > 80) discard();
    }
    @Override protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && !(entity instanceof LivingEntity living && SpecialEnemy.ally(living));
    }
    @Override protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (level.isClientSide || !(hit.getEntity() instanceof LivingEntity target)) return;
        Entity owner = getOwner();
        boolean conducts = element() == Element.LIGHTNING && MagicStatuses.has(target, MagicStatuses.Status.WET);
        target.hurt(net.minecraft.world.damagesource.DamageSource.indirectMagic(this, owner instanceof LivingEntity o ? o : null), conducts ? damage * 2 : damage);
        switch (element()) {
            case WATER -> {
                MagicStatuses.apply(target, MagicStatuses.Status.WET, 160, 1);
                Vec3 push = getDeltaMovement().normalize().scale(.9);
                target.push(push.x, .15, push.z); target.hurtMarked = true;
            }
            case LIGHTNING -> MagicStatuses.charge(target, 30);
            case COLD -> MagicStatuses.temperature(target, -25);
            case FIRE -> target.setSecondsOnFire(3);
            default -> {}
        }
    }
    @Override protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (!level.isClientSide) discard();
    }
}
