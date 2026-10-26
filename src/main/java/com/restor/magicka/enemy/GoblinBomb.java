package com.restor.magicka.enemy;

import com.restor.magicka.MagicStatuses;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class GoblinBomb extends ThrowableItemProjectile {
    public static final double RADIUS = 3;
    public int fuse = 50;
    private boolean landed;

    public GoblinBomb(EntityType<? extends GoblinBomb> type, Level level) { super(type, level); }
    public static GoblinBomb create(LivingEntity owner, Vec3 at, int fuse) {
        GoblinBomb bomb = new GoblinBomb(MagicEnemies.GOBLIN_BOMB.get(), owner.level);
        bomb.setOwner(owner);
        bomb.setPos(at);
        bomb.fuse = fuse;
        owner.level.addFreshEntity(bomb);
        return bomb;
    }
    @Override protected Item getDefaultItem() { return Items.TNT; }
    public boolean landed() { return landed; }

    @Override public void tick() {
        if (landed) {
            baseTick();
            setDeltaMovement(isOnGround() ? Vec3.ZERO : new Vec3(0, getDeltaMovement().y - .04, 0));
            move(MoverType.SELF, getDeltaMovement());
        } else super.tick();
        if (level.isClientSide || isRemoved()) return;
        ServerLevel level = (ServerLevel)this.level;
        level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + .35, getZ(), 1, .02, .02, .02, .01);
        if (tickCount % 3 == 0) level.sendParticles(ParticleTypes.FLAME, getX(), getY() + .35, getZ(), 1, .02, .02, .02, .005);
        if (landed && fuse % 4 == 0) ring(level);
        if (fuse % 10 == 0) playSound(SoundEvents.TNT_PRIMED, .5f, 1.6f);
        if (isInWaterOrRain() || isInPowderSnow || MagicEnemies.flooded(level, position())) { fizzle(level); return; }
        if (--fuse <= 0) explode(level);
    }
    private void ring(ServerLevel level) {
        for (int i = 0; i < 20; i++) {
            double a = i * Math.PI / 10;
            level.sendParticles(ParticleTypes.SMALL_FLAME, getX() + Math.cos(a) * RADIUS, getY() + .1, getZ() + Math.sin(a) * RADIUS, 1, 0, 0, 0, 0);
        }
    }
    public void fizzle(ServerLevel level) {
        level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + .3, getZ(), 10, .2, .2, .2, .02);
        playSound(SoundEvents.FIRE_EXTINGUISH, 1f, 1.2f);
        discard();
    }
    public void explode(ServerLevel level) {
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + .3, getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.FLAME, getX(), getY() + .3, getZ(), 30, 1, .4, 1, .05);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, getSoundSource(), 1.2f, 1.1f);
        LivingEntity owner = getOwner() instanceof LivingEntity o ? o : null;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(RADIUS), LivingEntity::isAlive)) {
            double d = e.position().distanceTo(position());
            if (d > RADIUS) continue;
            float k = (float)(1 - d / RADIUS);
            e.hurt(net.minecraft.world.damagesource.DamageSource.explosion(owner), 2 + 7 * k);
            Vec3 push = e.position().subtract(position()).multiply(1, 0, 1).normalize().scale(.4 + k * .8);
            e.push(push.x, .3 + k * .3, push.z); e.hurtMarked = true;
            if (!MagicStatuses.has(e, MagicStatuses.Status.WET)) e.setSecondsOnFire(2);
        }
        discard();
    }
    @Override protected void onHitEntity(EntityHitResult hit) { land(); }
    @Override protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        if (hit.getDirection() == Direction.UP) setPos(hit.getLocation());
        land();
    }
    private void land() {
        if (landed) return;
        landed = true;
        setDeltaMovement(Vec3.ZERO);
        playSound(SoundEvents.WOOD_FALL, .8f, 1.2f);
    }
}
