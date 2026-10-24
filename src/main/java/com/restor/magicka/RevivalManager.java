package com.restor.magicka;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

/** Short-lived soul markers; resurrection never copies dropped inventory/equipment. */
public final class RevivalManager {
    private record Remains(UUID id, ServerLevel level, Vec3 point, EntityType<?> type, boolean player, long until) {}
    private static final Map<UUID,Remains> SOULS = new HashMap<>();
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST) public void death(LivingDeathEvent event) {
        if (event.getEntity().level() instanceof ServerLevel world && !event.isCanceled()) {
            var e = event.getEntity();
            if (e.getType() == EntityType.ENDER_DRAGON || e.getType() == EntityType.WITHER || e.getTags().contains("magicka_object") || e.getTags().contains("magicka_revived")) return;
            if (SOULS.size() >= 256) return;
            SOULS.put(e.getUUID(),new Remains(e.getUUID(),world,e.position(),e.getType(),e instanceof ServerPlayer,world.getGameTime()+MagicConfig.REVIVE_TICKS.get()));
        }
    }
    // A player who has chosen normal respawn is no longer a corpse target.
    @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent event) { SOULS.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) { SOULS.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public void stop(ServerStoppedEvent event) { SOULS.clear(); }
    @SubscribeEvent public void tick(ServerTickEvent.Post event) {
        SOULS.values().removeIf(r -> r.level.getGameTime() >= r.until);
        for (Remains r : SOULS.values()) if (r.level.getGameTime()%20==0 && r.level.hasChunkAt(net.minecraft.core.BlockPos.containing(r.point)))
            r.level.sendParticles(ParticleTypes.SOUL,r.point.x,r.point.y+.8,r.point.z,2,.2,.3,.2,.01);
    }
    public static int raise(ServerPlayer caster, Vec3 point, double radius, int count, int ticks) { return raise(caster, point, radius, count, ticks, 1); }

    public static int raise(ServerPlayer caster, Vec3 point, double radius, int count, int ticks, double health) {
        var level = caster.serverLevel();
        var found = SOULS.values().stream().filter(r -> !r.player && r.level==level && r.until>level.getGameTime() && r.point.distanceToSqr(point)<=radius*radius)
                .sorted(Comparator.comparingDouble(r -> r.point.distanceToSqr(point))).limit(Math.clamp(count,1,128)).toList();
        int raised = 0;
        for (Remains r : found) if (level.getEntity(r.id) == null && UndeadMinions.spawn(caster,r.type,r.point,raised,ticks,health)) { SOULS.remove(r.id); raised++; }
        return raised;
    }
    public static boolean revive(ServerPlayer caster, Vec3 point, double radius, float health) {
        Remains remains = SOULS.values().stream().filter(r -> r.level==caster.serverLevel() && r.until>r.level.getGameTime() && r.point.distanceToSqr(point)<=radius*radius)
                .min(Comparator.comparingDouble(r -> r.point.distanceToSqr(point))).orElse(null);
        if (remains == null || !WorldPlacement.allowed(caster,net.minecraft.core.BlockPos.containing(remains.point))) return false;
        var level = caster.serverLevel();
        if (!level.noCollision(new AABB(remains.point.x-.4,remains.point.y,remains.point.z-.4,remains.point.x+.4,remains.point.y+2,remains.point.z+.4))) return false;
        if (remains.player) {
            var player = level.getServer().getPlayerList().getPlayer(remains.id);
            if (player == null || player.isAlive() || level.getServer().isHardcore()) return false;
            SOULS.remove(remains.id);
            var restored = level.getServer().getPlayerList().respawn(player,false,Entity.RemovalReason.KILLED);
            restored.connection.player = restored;
            restored.teleportTo(level,remains.point.x,remains.point.y,remains.point.z,Set.of(),restored.getYRot(),restored.getXRot());
            restored.setHealth(Math.max(1,restored.getMaxHealth()*health));
        } else {
            if (level.getEntity(remains.id) != null) return false;
            Entity entity = remains.type.create(level);
            if (!(entity instanceof LivingEntity living)) return false;
            living.setPos(remains.point); living.setHealth(Math.max(1,living.getMaxHealth()*health)); living.addTag("magicka_revived");
            if (!level.addFreshEntity(living)) return false;
            SOULS.remove(remains.id);
        }
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,remains.point.x,remains.point.y+1,remains.point.z,30,.5,.8,.5,.1);
        return true;
    }
}
