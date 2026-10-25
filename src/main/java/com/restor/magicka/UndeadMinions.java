package com.restor.magicka;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

/** Temporary undead servants: bound to one caster, never saved, never dropping loot. */
public final class UndeadMinions {
    public static final String TAG = "magicka_minion";
    private record Minion(Mob mob, ServerPlayer owner, long until) {}
    private static final Map<UUID, Minion> MINIONS = new LinkedHashMap<>();
    private static final int RISE_TICKS = 16;
    private static final Map<Mob, Vec3> RISING = new LinkedHashMap<>();

    public static int summon(ServerPlayer owner, Vec3 point, String id, int count, int ticks) { return summon(owner, point, id, count, ticks, 1); }

    public static int summon(ServerPlayer owner, Vec3 point, String id, int count, int ticks, double health) {
        var type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.tryParse(id)).orElse(EntityType.ZOMBIE);
        int spawned = 0;
        for (int i = 0; i < Math.clamp(count, 1, 128); i++) if (spawn(owner, type, point, i, ticks, health)) spawned++;
        return spawned;
    }

    public static boolean spawn(ServerPlayer owner, EntityType<?> type, Vec3 point, int index, int ticks) { return spawn(owner, type, point, index, ticks, 1); }

    public static boolean spawn(ServerPlayer owner, EntityType<?> type, Vec3 point, int index, int ticks, double health) {
        ServerLevel level = owner.serverLevel();
        Entity created = type.create(level);
        if (!(created instanceof Mob mob) || !(created instanceof Enemy) || type == EntityType.WITHER || type == EntityType.ENDER_DRAGON || type == EntityType.WARDEN) {
            if (created != null) created.discard();
            if (type == EntityType.ZOMBIE) return false;
            return spawn(owner, EntityType.ZOMBIE, point, index, ticks, health);
        }
        Vec3 at = place(level, mob, point, index);
        if (at == null || !WorldPlacement.allowed(owner, BlockPos.containing(at))) { mob.discard(); return false; }
        mob.moveTo(at.x, at.y - mob.getBbHeight(), at.z, owner.getYRot() + index * 45, 0);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
        if (mob.getVehicle() != null) { Entity vehicle = mob.getVehicle(); mob.stopRiding(); vehicle.discard(); }
        if (mob instanceof Zombie zombie) zombie.setBaby(false);
        MagicGear.frail(mob, health);
        if (mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.SKELETON_SKULL));
        for (EquipmentSlot slot : EquipmentSlot.values()) mob.setDropChance(slot, 0);
        mob.setPersistenceRequired();
        mob.setCanPickUpLoot(false);
        mob.addTag(TAG);
        mob.addTag("magicka_object");
        mob.setCustomName(net.minecraft.network.chat.Component.translatable("entity.magicka.minion", owner.getDisplayName()));
        long until = level.getGameTime() + Math.clamp(ticks, 20, 72000);
        MINIONS.put(mob.getUUID(), new Minion(mob, owner, until));
        mob.noPhysics = true;
        mob.setNoAi(true);
        if (!level.addFreshEntity(mob)) { MINIONS.remove(mob.getUUID()); return false; }
        RISING.put(mob, at);
        trim(owner);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + .1, at.z, 8, .4, .1, .4, .01);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.HOSTILE, .5f, .6f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.HOSTILE, 1f, .7f);
        return true;
    }

    private static Vec3 place(ServerLevel level, Mob mob, Vec3 point, int index) {
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = index * 2.39996 + attempt * 1.13 + level.random.nextDouble() * .7;
            double distance = 1.3 + (index % 6) * .45 + level.random.nextDouble() * .9;
            BlockPos column = BlockPos.containing(point.x + Math.cos(angle) * distance, point.y + 1, point.z + Math.sin(angle) * distance);
            for (int dy = 0; dy < 5; dy++) {
                BlockPos feet = column.below(dy);
                if (!level.hasChunkAt(feet) || level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()) continue;
                Vec3 at = new Vec3(feet.getX() + .5, feet.getY(), feet.getZ() + .5);
                if (taken(feet)) continue;
                if (level.noCollision(mob, mob.getType().getDimensions().makeBoundingBox(at))) return at;
            }
        }
        return null;
    }

    private static boolean taken(BlockPos feet) {
        return MINIONS.values().stream().anyMatch(m -> m.mob.isAlive() && (RISING.containsKey(m.mob) ? BlockPos.containing(RISING.get(m.mob)) : m.mob.blockPosition()).equals(feet));
    }

    private static void trim(ServerPlayer owner) {
        var owned = MINIONS.values().stream().filter(m -> m.owner == owner).toList();
        for (int i = 0; i < owned.size() - MagicStats.minionCap(owner); i++) vanish(owned.get(i).mob);
    }

    private static void rise(Mob mob, Vec3 at) {
        ServerLevel level = (ServerLevel) mob.level();
        if (mob.position().subtract(at).horizontalDistanceSqr() > 1) at = new Vec3(mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ());
        double y = Math.min(at.y, mob.getY() + mob.getBbHeight() / RISE_TICKS);
        mob.setPos(at.x, y, at.z);
        var ground = level.getBlockState(BlockPos.containing(at).below());
        if (!ground.isAir()) level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + .1, at.z, 6, .3, .05, .3, .1);
        if (y < at.y) return;
        RISING.remove(mob);
        mob.noPhysics = false;
        mob.setNoAi(false);
        level.sendParticles(ParticleTypes.SOUL, at.x, at.y + .5, at.z, 16, .35, .6, .35, .02);
    }

    private static void vanish(Mob mob) {
        RISING.remove(mob);
        MINIONS.remove(mob.getUUID());
        if (mob.level() instanceof ServerLevel level && mob.isAlive())
            level.sendParticles(ParticleTypes.SOUL, mob.getX(), mob.getY() + .8, mob.getZ(), 12, .3, .5, .3, .03);
        mob.discard();
    }

    public static boolean ownedBy(Entity entity, ServerPlayer owner) {
        Minion minion = entity == null ? null : MINIONS.get(entity.getUUID());
        return minion != null && minion.owner == owner;
    }

    public static int count(ServerPlayer owner) { return (int) MINIONS.values().stream().filter(m -> m.owner == owner && m.mob.isAlive()).count(); }

    private static boolean enemyOf(ServerPlayer owner, LivingEntity target) {
        return owner.getLastHurtByMob() == target || owner.getLastHurtMob() == target && owner.tickCount - owner.getLastHurtMobTimestamp() < 200;
    }

    private static boolean allowed(Minion minion, LivingEntity target) {
        if (target == null) return true;
        if (!target.isAlive() || target == minion.owner) return false;
        if (ownedBy(target, minion.owner)) return minion.mob.getLastHurtByMob() == target;
        if (target instanceof Player player) return enemyOf(minion.owner, player) && minion.owner.canHarmPlayer(player) && !player.isCreative();
        return target instanceof Enemy || enemyOf(minion.owner, target);
    }

    @SubscribeEvent public void target(LivingChangeTargetEvent event) {
        Minion minion = MINIONS.get(event.getEntity().getUUID());
        if (minion != null && !allowed(minion, event.getNewAboutToBeSetTarget())) event.setCanceled(true);
    }

    @SubscribeEvent public void drops(LivingDropsEvent event) { if (event.getEntity().getTags().contains(TAG)) event.setCanceled(true); }
    @SubscribeEvent public void experience(LivingExperienceDropEvent event) { if (event.getEntity().getTags().contains(TAG)) event.setCanceled(true); }

    @SubscribeEvent public void join(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity().getTags().contains(TAG) && !MINIONS.containsKey(event.getEntity().getUUID())) event.setCanceled(true);
    }

    @SubscribeEvent public void tick(ServerTickEvent.Post event) {
        RISING.keySet().removeIf(Entity::isRemoved);
        new LinkedHashMap<>(RISING).forEach(UndeadMinions::rise);
        if (event.getServer().getTickCount() % 10 != 0 || MINIONS.isEmpty()) return;
        for (Minion minion : List.copyOf(MINIONS.values())) {
            Mob mob = minion.mob;
            ServerPlayer owner = minion.owner;
            if (!mob.isAlive() || mob.isRemoved()) { MINIONS.remove(mob.getUUID()); continue; }
            if (owner.isRemoved() || (!owner.isAlive() && !MagicGear.has(owner, MagicGear.Trait.GRAVE_BELL)) || owner.level() != mob.level() || mob.level().getGameTime() >= minion.until) { vanish(mob); continue; }
            if (RISING.containsKey(mob)) { if (mob.distanceToSqr(owner) > 32 * 32) vanish(mob); continue; }
            if (mob.getTarget() != null && !allowed(minion, mob.getTarget())) mob.setTarget(null);
            if (mob.getTarget() == null) {
                LivingEntity next = owner.getLastHurtByMob();
                if (next == null || !allowed(minion, next) || next.distanceToSqr(mob) > 32 * 32) next = owner.getLastHurtMob();
                if (next == null || !allowed(minion, next) || next.distanceToSqr(mob) > 32 * 32)
                    next = mob.level().getEntitiesOfClass(LivingEntity.class, new AABB(mob.blockPosition()).inflate(14), e -> e instanceof Enemy && allowed(minion, e) && mob.hasLineOfSight(e))
                            .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(mob))).orElse(null);
                if (next != null) mob.setTarget(next);
            }
            double distance = mob.distanceToSqr(owner);
            if (distance > 32 * 32) { vanish(mob); continue; }
            if (mob.getTarget() == null && distance > 8 * 8) mob.getNavigation().moveTo(owner, 1.2);
        }
    }

    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        for (Minion minion : List.copyOf(MINIONS.values())) if (minion.owner.getUUID().equals(event.getEntity().getUUID())) vanish(minion.mob);
    }

    @SubscribeEvent public void stop(ServerStoppedEvent event) { MINIONS.clear(); RISING.clear(); }
}
