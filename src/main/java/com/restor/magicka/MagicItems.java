package com.restor.magicka;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

public final class MagicItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Magicka.ID);
    public static final DeferredItem<Item> SPELLBOOK = ITEMS.registerSimpleItem("spellbook", new Item.Properties().stacksTo(1));
    public static void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(SPELLBOOK);
    }
    private MagicItems() {}
}
