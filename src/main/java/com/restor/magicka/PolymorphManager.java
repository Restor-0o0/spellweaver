package com.restor.magicka;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/** Turns a mob into a harmless animal that keeps its own health; any damage brings it back.
 *  A player cannot be swapped out, so players keep their body and are only re-skinned on the client. */
public final class PolymorphManager {
    public static final String TAG = "magicka_polymorph";
    private static final String DATA = "magicka:polymorph", UNTIL = "magicka:polymorph_until", SHAPE = "magicka:polymorph_shape";
    private static final Set<EntityType<?>> DENIED = Set.of(EntityType.ENDER_DRAGON, EntityType.WITHER,
            EntityType.WARDEN, EntityType.ELDER_GUARDIAN);
    private static final Map<UUID, LivingEntity> ACTIVE = new LinkedHashMap<>();
    private static final Map<UUID, String> PLAYERS = new LinkedHashMap<>();

    public static boolean apply(ServerPlayer caster, LivingEntity target, String id, int ticks) {
        if (!(target.level() instanceof ServerLevel level) || !EffectGraph.canHarm(caster, target)) return false;
        ticks = Math.clamp(ticks, 20, 24000);
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.tryParse(id)).orElse(EntityType.SHEEP);
        if (target instanceof Player) return target instanceof ServerPlayer victim && living(type, level) && applyPlayer(victim, type, ticks);
        if (ACTIVE.containsKey(target.getUUID())) {
            target.getPersistentData().putLong(UNTIL, level.getGameTime() + ticks);
            return true;
        }
        // 200 hearts of health is the cheapest way to leave modded bosses alone.
        if (!(target instanceof Mob mob) || DENIED.contains(mob.getType()) || mob.getMaxHealth() > 200) return false;
        if (!(type.create(level) instanceof Mob shape)) return false;
        CompoundTag original = new CompoundTag();
        if (!mob.save(original)) { shape.discard(); return false; }
        shape.moveTo(mob.getX(), mob.getY(), mob.getZ(), mob.getYRot(), mob.getXRot());
        AttributeInstance health = shape.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) health.setBaseValue(mob.getMaxHealth());
        shape.setHealth(mob.getHealth());
        shape.setCustomName(mob.getCustomName());
        shape.setPersistenceRequired();
        shape.setCanPickUpLoot(false);
        shape.addTag(TAG);
        shape.getPersistentData().put(DATA, original);
        shape.getPersistentData().putLong(UNTIL, level.getGameTime() + ticks);
        if (!level.addFreshEntity(shape)) { shape.discard(); return false; }
        ACTIVE.put(shape.getUUID(), shape);
        mob.discard();
        puff(level, shape.position());
        return true;
    }

    private static boolean living(EntityType<?> type, ServerLevel level) {
        Entity probe = type.create(level);
        if (probe != null) probe.discard();
        return probe instanceof LivingEntity;
    }

    private static boolean applyPlayer(ServerPlayer victim, EntityType<?> type, int ticks) {
        if (victim.isSpectator() || !victim.isAlive()) return false;
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        victim.getPersistentData().putString(SHAPE, id);
        victim.getPersistentData().putLong(UNTIL, victim.serverLevel().getGameTime() + ticks);
        if (!id.equals(PLAYERS.put(victim.getUUID(), id))) {
            sync(victim, id);
            puff(victim.serverLevel(), victim.position());
        }
        return true;
    }

    private static void revertPlayer(ServerPlayer victim) {
        PLAYERS.remove(victim.getUUID());
        victim.getPersistentData().remove(SHAPE);
        victim.getPersistentData().remove(UNTIL);
        sync(victim, "");
        if (victim.level() instanceof ServerLevel level) puff(level, victim.position());
    }

    private static void sync(ServerPlayer victim, String id) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(victim, new MagicNetwork.Shape(victim.getId(), id));
    }

    private static void resync(Player player) {
        if (!(player instanceof ServerPlayer victim)) return;
        String id = victim.getPersistentData().getString(SHAPE);
        if (id.isEmpty()) { PLAYERS.remove(victim.getUUID()); return; }
        if (victim.serverLevel().getGameTime() >= victim.getPersistentData().getLong(UNTIL)) { revertPlayer(victim); return; }
        PLAYERS.put(victim.getUUID(), id);
        sync(victim, id);
    }

    private static SoundEvent voice(String id) {
        return switch (id) {
            case "minecraft:sheep" -> SoundEvents.SHEEP_AMBIENT;
            case "minecraft:frog" -> SoundEvents.FROG_AMBIENT;
            case "minecraft:pig" -> SoundEvents.PIG_AMBIENT;
            case "minecraft:chicken" -> SoundEvents.CHICKEN_AMBIENT;
            default -> null;
        };
    }

    private static LivingEntity revert(LivingEntity shape) {
        ACTIVE.remove(shape.getUUID());
        if (!(shape.level() instanceof ServerLevel level)) return null;
        CompoundTag original = shape.getPersistentData().getCompound(DATA);
        shape.getPersistentData().remove(DATA);
        Entity restored = original.isEmpty() ? null : EntityType.loadEntityRecursive(original, level, entity -> entity);
        if (restored != null) {
            restored.moveTo(shape.getX(), shape.getY(), shape.getZ(), shape.getYRot(), shape.getXRot());
            if (restored instanceof LivingEntity living)
                living.setHealth(Math.clamp(shape.getHealth(), 1, living.getMaxHealth()));
            if (!level.addFreshEntity(restored)) restored = null;
        }
        puff(level, shape.position());
        shape.discard();
        return restored instanceof LivingEntity living ? living : null;
    }

    private static void puff(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.POOF, at.x, at.y + .5, at.z, 18, .35, .4, .35, .02);
        level.sendParticles(ParticleTypes.WITCH, at.x, at.y + .6, at.z, 10, .3, .4, .3, .01);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, .7f, 1.2f);
    }

    public static boolean polymorphed(Entity entity) {
        if (entity == null) return false;
        if (ACTIVE.containsKey(entity.getUUID())) return true;
        if (!PLAYERS.containsKey(entity.getUUID())) return false;
        if (!(entity instanceof ServerPlayer victim)) return true;
        if (victim.serverLevel().getGameTime() < victim.getPersistentData().getLong(UNTIL)) return true;
        revertPlayer(victim);
        return false;
    }

    private static boolean swapped(Entity entity) { return entity != null && ACTIVE.containsKey(entity.getUUID()); }

    @SubscribeEvent public void damaged(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer victim && PLAYERS.containsKey(victim.getUUID())) { revertPlayer(victim); return; }
        if (!ACTIVE.containsKey(event.getEntity().getUUID())) return;
        event.setCanceled(true);
        LivingEntity restored = revert(event.getEntity());
        if (restored == null) return;
        restored.invulnerableTime = 0;
        restored.hurt(event.getSource(), event.getAmount());
    }

    @SubscribeEvent public void drops(LivingDropsEvent event) { if (swapped(event.getEntity())) event.setCanceled(true); }
    @SubscribeEvent public void experience(LivingExperienceDropEvent event) { if (swapped(event.getEntity())) event.setCanceled(true); }

    @SubscribeEvent public void attack(AttackEntityEvent event) { if (polymorphed(event.getEntity())) event.setCanceled(true); }
    @SubscribeEvent public void mine(PlayerInteractEvent.LeftClickBlock event) { if (polymorphed(event.getEntity())) event.setCanceled(true); }
    @SubscribeEvent public void place(PlayerInteractEvent.RightClickBlock event) { if (polymorphed(event.getEntity())) event.setCanceled(true); }
    @SubscribeEvent public void use(PlayerInteractEvent.RightClickItem event) { if (polymorphed(event.getEntity())) event.setCanceled(true); }
    @SubscribeEvent public void touch(PlayerInteractEvent.EntityInteract event) { if (polymorphed(event.getEntity())) event.setCanceled(true); }

    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) { resync(event.getEntity()); }
    @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent event) { resync(event.getEntity()); }
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { resync(event.getEntity()); }

    @SubscribeEvent public void track(PlayerEvent.StartTracking event) {
        String id = event.getTarget() instanceof ServerPlayer target ? PLAYERS.get(target.getUUID()) : null;
        if (id != null && event.getEntity() instanceof ServerPlayer watcher)
            PacketDistributor.sendToPlayer(watcher, new MagicNetwork.Shape(event.getTarget().getId(), id));
    }

    @SubscribeEvent public void join(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof LivingEntity shape)) return;
        if (!shape.getTags().contains(TAG)) return;
        if (shape.getPersistentData().contains(DATA)) ACTIVE.putIfAbsent(shape.getUUID(), shape);
        else shape.removeTag(TAG);
    }

    @SubscribeEvent public void tick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty() && PLAYERS.isEmpty() || event.getServer().getTickCount() % 10 != 0) return;
        for (UUID id : List.copyOf(PLAYERS.keySet())) {
            ServerPlayer victim = event.getServer().getPlayerList().getPlayer(id);
            if (victim == null) { PLAYERS.remove(id); continue; }
            if (!victim.isAlive() || victim.serverLevel().getGameTime() >= victim.getPersistentData().getLong(UNTIL)) { revertPlayer(victim); continue; }
            SoundEvent voice = voice(PLAYERS.get(id));
            if (voice != null && victim.getRandom().nextInt(8) == 0)
                victim.serverLevel().playSound(null, victim.getX(), victim.getY(), victim.getZ(), voice, SoundSource.NEUTRAL, 1f, 1f);
        }
        for (LivingEntity shape : List.copyOf(ACTIVE.values()))
            if (shape.isRemoved() || !shape.isAlive() || shape.level().getGameTime() >= shape.getPersistentData().getLong(UNTIL))
                revert(shape);
    }

    @SubscribeEvent public void stop(ServerStoppedEvent event) {
        for (LivingEntity shape : List.copyOf(ACTIVE.values())) revert(shape);
        ACTIVE.clear();
        PLAYERS.clear();
    }
}
