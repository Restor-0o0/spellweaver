package com.restor.magicka;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import java.util.*;

public final class ElementLocks {
    private record Lock(int mask, long until) {}
    private static final Map<UUID, Lock> LOCKS = new HashMap<>();

    public static int mask(Formula formula) {
        int mask = 0;
        for (Element e : formula.elements()) mask |= 1 << e.ordinal();
        return mask;
    }
    public static void lock(ServerPlayer player, int mask, int ticks) {
        if (mask == 0) return;
        long now = player.serverLevel().getGameTime();
        LOCKS.put(player.getUUID(), new Lock(mask | active(player), now + ticks));
        MagicNetwork.sendToPlayer(player, new MagicNetwork.Locks(active(player), ticks));
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.HOSTILE, 1f, .6f);
        player.displayClientMessage(Component.translatable("hud.magicka.elements_locked", names(mask), ticks / 20), true);
        CastingServer.dissolveLocked(player);
    }
    public static int active(ServerPlayer player) {
        Lock lock = LOCKS.get(player.getUUID());
        if (lock == null) return 0;
        if (lock.until <= player.serverLevel().getGameTime()) { LOCKS.remove(player.getUUID()); return 0; }
        return lock.mask;
    }
    public static boolean locked(ServerPlayer player, Element element) { return (active(player) & 1 << element.ordinal()) != 0; }
    public static void reject(ServerPlayer player, Element element) {
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, .7f, 1.4f);
        player.displayClientMessage(Component.translatable("hud.magicka.element_locked", Component.translatable(element.key())), true);
    }
    public static void clear(ServerPlayer player) {
        if (LOCKS.remove(player.getUUID()) != null) MagicNetwork.sendToPlayer(player, new MagicNetwork.Locks(0, 0));
    }
    static void reset() { LOCKS.clear(); }
    private static Component names(int mask) {
        var out = Component.empty();
        boolean first = true;
        for (Element e : Element.values()) if ((mask & 1 << e.ordinal()) != 0) {
            if (!first) out.append(", ");
            out.append(Component.translatable(e.key()));
            first = false;
        }
        return out;
    }
    private ElementLocks() {}
}
