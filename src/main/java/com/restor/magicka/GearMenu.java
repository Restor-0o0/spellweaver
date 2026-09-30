package com.restor.magicka;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class GearMenu extends AbstractContainerMenu {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Magicka.ID);
    public static final DeferredHolder<MenuType<?>, MenuType<GearMenu>> TYPE = MENUS.register("loadout",
            () -> IMenuTypeExtension.create((id, inventory, buffer) -> new GearMenu(id, inventory, new SimpleContainer(MagicGear.SLOTS))));

    public static final int[][] POSITIONS = {{26, 30}, {62, 30}, {26, 62}, {62, 62}, {26, 94}, {62, 94}, {98, 94}, {98, 30}};
    private static final int GEAR_END = MagicGear.SLOTS, INVENTORY_END = GEAR_END + 36;

    private final Container loadout;

    public GearMenu(int id, Inventory inventory, Container loadout) {
        super(TYPE.get(), id);
        checkContainerSize(loadout, MagicGear.SLOTS);
        this.loadout = loadout;
        for (MagicGear.Slot slot : MagicGear.Slot.values())
            addSlot(new GearSlot(loadout, slot, POSITIONS[slot.ordinal()][0], POSITIONS[slot.ordinal()][1]));
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            addSlot(new Slot(inventory, column + row * 9 + 9, 26 + column * 18, 137 + row * 18));
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 26 + column * 18, 195));
    }

    public static final class GearSlot extends Slot {
        public final MagicGear.Slot gear;
        GearSlot(Container container, MagicGear.Slot gear, int x, int y) {
            super(container, gear.ordinal(), x, y);
            this.gear = gear;
        }
        @Override public boolean mayPlace(ItemStack stack) { return gear.accepts(stack); }
        @Override public int getMaxStackSize() { return 1; }
    }

    @Override public boolean stillValid(Player player) { return player.isAlive() && !player.isSpectator(); }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        if (index < GEAR_END) {
            if (!moveItemStackTo(stack, GEAR_END, INVENTORY_END, true)) return ItemStack.EMPTY;
        } else if (!placeInGear(stack) && !moveItemStackTo(stack, GEAR_END, INVENTORY_END - 9, index >= INVENTORY_END - 9)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return original;
    }

    private boolean placeInGear(ItemStack stack) {
        for (int index = 0; index < GEAR_END; index++)
            if (!slots.get(index).hasItem() && slots.get(index).mayPlace(stack) && moveItemStackTo(stack, index, index + 1, false)) return true;
        return false;
    }

    public Container loadout() { return loadout; }
}
