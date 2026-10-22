package com.restor.magicka;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.Locale;

public final class MagicStatuses {
    public enum Status { WET, BURNING, CHILLED, FROZEN, SHOCKED, STUNNED, BRITTLE, ROOTED, REGENERATING, ARCANE_UNSTABLE, STONE_SHELL, LEVITATING, SLOW_FALLING, HASTE, OBSCURED, WITHERING }
    private static CompoundTag data(LivingEntity entity) {
        CompoundTag root = entity.getPersistentData();
        if (!root.contains("magicka:statuses")) root.put("magicka:statuses",new CompoundTag());
        return root.getCompoundOrEmpty("magicka:statuses");
    }
    public static boolean has(LivingEntity entity, Status status) {
        if (status == Status.WET && entity.isInWaterOrRain()) return true;
        if (status == Status.BURNING && entity.isOnFire()) return true;
        return data(entity).getLongOr(status.name(), 0L) > entity.level().getGameTime();
    }
    public static void apply(LivingEntity entity, String name, int duration, int strength) {
        apply(entity,Status.valueOf(name.toUpperCase(Locale.ROOT)),duration,strength);
    }
    public static void apply(LivingEntity entity, Status status, int duration, int strength) {
        CompoundTag tag = data(entity);
        tag.putLong(status.name(), Math.max(tag.getLongOr(status.name(), 0L), entity.level().getGameTime()+Math.clamp(duration,1,72000)));
        tag.putInt(status.name()+"_strength",Math.max(tag.getIntOr(status.name()+"_strength", 0),Math.clamp(strength,1,5)));
        switch(status) {
            case WET -> entity.clearFire();
            case BURNING -> entity.igniteForTicks(duration);
            case FROZEN -> { entity.setTicksFrozen(entity.getTicksRequiredToFreeze()+duration); apply(entity,Status.BRITTLE,duration,1); }
            default -> {}
        }
    }
    public static void remove(LivingEntity entity, Status status) {
        data(entity).remove(status.name()); data(entity).remove(status.name()+"_strength");
        if (status == Status.BURNING) entity.clearFire();
        if (status == Status.FROZEN) entity.setTicksFrozen(0);
        if (status == Status.CHILLED || status == Status.FROZEN || status == Status.ROOTED) entity.removeEffect(MobEffects.SLOWNESS);
    }
    public static void temperature(LivingEntity target, double amount) {
        var tag = data(target);
        if (amount > 0) {
            remove(target,Status.FROZEN); remove(target,Status.CHILLED);
            tag.putDouble("cold",Math.max(0,tag.getDoubleOr("cold", 0)-amount));
            if (!has(target,Status.WET)) apply(target,Status.BURNING,(int)(amount*3),1);
            else remove(target,Status.WET);
        } else {
            remove(target,Status.BURNING);
            double buildup = tag.getDoubleOr("cold", 0) - amount * (has(target,Status.WET) ? 2.5 : 1);
            tag.putDouble("cold", Math.min(200,buildup));
            apply(target,Status.CHILLED,100,1);
            if (buildup >= 100) apply(target,Status.FROZEN,80,1);
        }
    }
    public static void charge(LivingEntity target, double amount) {
        var tag = data(target);
        double charge = tag.getDoubleOr("charge", 0)+amount*(has(target,Status.WET) ? 2 : 1);
        tag.putDouble("charge", Math.min(150,charge)); apply(target,Status.SHOCKED,80,1);
        if (charge >= 65) { apply(target,Status.STUNNED,20,1); tag.putDouble("charge",charge-50); }
    }
    public static int mask(LivingEntity target) {
        int mask = 0; for (Status status : Status.values()) if (has(target,status)) mask |= 1 << status.ordinal(); return mask;
    }
    @SubscribeEvent public void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || !entity.isAlive() || entity.level().isClientSide()) return;
        if (!entity.getPersistentData().contains("magicka:statuses")) return;
        var tag = data(entity); long now = entity.level().getGameTime();
        for (Status status : Status.values()) {
            if (tag.contains(status.name()) && tag.getLongOr(status.name(), 0L) <= now) { tag.remove(status.name()); tag.remove(status.name()+"_strength"); }
        }
        boolean immobilized = has(entity,Status.STUNNED) || has(entity,Status.ROOTED) || has(entity,Status.FROZEN);
        if (immobilized) {
            entity.setDeltaMovement(0, Math.min(0,entity.getDeltaMovement().y), 0);
            if (entity instanceof net.minecraft.server.level.ServerPlayer player && (Math.abs(entity.getX()-entity.xo)+Math.abs(entity.getZ()-entity.zo)) > .01)
                player.connection.teleport(entity.xo,entity.getY(),entity.zo,entity.getYRot(),entity.getXRot());
            else entity.setPos(entity.xo,entity.getY(),entity.zo);
        }
        if (now % 10 == 0) {
            if (has(entity,Status.CHILLED) || immobilized) entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,15,immobilized ? 5 : Math.max(0,tag.getIntOr("CHILLED_strength", 0)-1),false,false));
            if (has(entity,Status.STONE_SHELL)) entity.addEffect(new MobEffectInstance(MobEffects.RESISTANCE,15,Math.min(2,tag.getIntOr("STONE_SHELL_strength", 0)-1),false,false));
            if (has(entity,Status.SHOCKED)) entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,15,0,false,false));
            if (has(entity,Status.LEVITATING)) entity.addEffect(new MobEffectInstance(MobEffects.LEVITATION,15,0,false,false));
            if (has(entity,Status.SLOW_FALLING)) entity.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,15,0,false,false));
            if (has(entity,Status.HASTE)) entity.addEffect(new MobEffectInstance(MobEffects.SPEED,15,Math.clamp(tag.getIntOr("HASTE_strength", 0),1,4),false,false));
            if (has(entity,Status.OBSCURED)) entity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,15,0,false,false));
            if (has(entity,Status.WET)) entity.clearFire();
            tag.putDouble("cold", Math.max(0,tag.getDoubleOr("cold", 0)-2));
            tag.putDouble("charge",Math.max(0,tag.getDoubleOr("charge", 0)-3));
            int mask = mask(entity);
            if (tag.getIntOr("last_mask", 0) != mask || now % 40 == 0) {
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity,new MagicNetwork.Statuses(entity.getId(),mask));
                tag.putInt("last_mask",mask);
            }
        }
        if (now % 40 == 0) {
            if (has(entity,Status.REGENERATING)) entity.heal(Math.max(1,tag.getIntOr("REGENERATING_strength", 0)));
            if (has(entity,Status.ARCANE_UNSTABLE)) entity.hurt(entity.damageSources().magic(),.5F*tag.getIntOr("ARCANE_UNSTABLE_strength", 0));
        }
        if (now % 20 == 0 && has(entity,Status.WITHERING) && !entity.isInvertedHealAndHarm()) {
            entity.hurt(entity.damageSources().wither(),.5F+.5F*tag.getIntOr("WITHERING_strength", 0));
            if (entity.level() instanceof net.minecraft.server.level.ServerLevel level) level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,entity.getX(),entity.getY()+entity.getBbHeight()*.6,entity.getZ(),4,.25,.3,.25,.01);
        }
    }
}
