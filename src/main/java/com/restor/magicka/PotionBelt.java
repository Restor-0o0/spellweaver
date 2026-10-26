package com.restor.magicka;

import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import java.util.*;

public final class PotionBelt {
    public static final int BUTTONS = 2, DRINK_TICKS = 12;
    private static final String SELECTED = "magicka_belt_selected_";
    private record Drink(int slot, int ticks) {}
    private static final Map<UUID, Drink> DRINKING = new HashMap<>();
    private static volatile List<ItemStack> client = empty();
    private static volatile int[] clientSelected = {0, 1};

    private static List<ItemStack> empty() { return Collections.nCopies(MagicGear.BELT, ItemStack.EMPTY); }

    public static boolean accepts(ItemStack stack) { return stack.is(Items.POTION); }

    public static int selected(Player player, int button) {
        return Mth.clamp(player.getPersistentData().contains(SELECTED + button) ? player.getPersistentData().getInt(SELECTED + button) : button, 0, MagicGear.BELT - 1);
    }

    public static void select(ServerPlayer player, int button, int slot) {
        if (button < 0 || button >= BUTTONS || slot < 0 || slot >= MagicGear.BELT) return;
        player.getPersistentData().putInt(SELECTED + button, slot);
        sync(player);
    }

    public static boolean drinking(Player player) { return DRINKING.containsKey(player.getUUID()); }

    public static void drink(ServerPlayer player, int button) {
        if (button < 0 || button >= BUTTONS || !player.isAlive() || player.isSpectator() || drinking(player)) return;
        int slot = selected(player, button);
        if (!accepts(MagicGear.belt(player, slot))) {
            player.displayClientMessage(Component.translatable("belt.magicka.empty"), true);
            return;
        }
        CastingServer.clearFormula(player);
        DRINKING.put(player.getUUID(), new Drink(slot, DRINK_TICKS));
        player.level().playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, .6f, 1.1f);
    }

    private static void finish(ServerPlayer player, int slot) {
        ItemStack stack = MagicGear.belt(player, slot);
        if (!accepts(stack)) return;
        ItemStack rest = stack.copyWithCount(1).finishUsingItem(player.level(), player);
        if (player.getAbilities().instabuild) return;
        stack.shrink(1);
        MagicGear.setBelt(player, slot, stack);
        if (rest.is(Items.GLASS_BOTTLE)) player.getInventory().placeItemBackInInventory(rest);
    }

    public static void sync(ServerPlayer player) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < MagicGear.BELT; i++) items.add(MagicGear.belt(player, i));
        MagicNetwork.sendToPlayer(player, new MagicNetwork.Belt(items, selected(player, 0), selected(player, 1)));
    }

    public static void acceptClient(List<ItemStack> items, int first, int second) {
        List<ItemStack> belt = new ArrayList<>(empty());
        for (int i = 0; i < Math.min(items.size(), belt.size()); i++) belt.set(i, items.get(i));
        client = List.copyOf(belt);
        clientSelected = new int[]{first, second};
    }
    public static void clearClient() { client = empty(); clientSelected = new int[]{0, 1}; }
    public static List<ItemStack> client() { return client; }
    public static int clientSelected(int button) { return clientSelected[button]; }

    @SubscribeEvent public void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        Drink drink = DRINKING.get(player.getUUID());
        if (drink == null) return;
        if (!player.isAlive()) { DRINKING.remove(player.getUUID()); return; }
        if (drink.ticks > 1) { DRINKING.put(player.getUUID(), new Drink(drink.slot, drink.ticks - 1)); return; }
        DRINKING.remove(player.getUUID());
        finish(player, drink.slot);
    }

    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) { if (event.getEntity() instanceof ServerPlayer p) sync(p); }
    @SubscribeEvent public void clone(PlayerEvent.Clone event) {
        for (int button = 0; button < BUTTONS; button++)
            event.getEntity().getPersistentData().putInt(SELECTED + button, selected(event.getOriginal(), button));
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) { DRINKING.remove(event.getEntity().getUUID()); }

    public PotionBelt() {}
}
