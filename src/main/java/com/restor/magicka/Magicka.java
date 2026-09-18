package com.restor.magicka;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

@Mod(Magicka.ID)
public final class Magicka {
    public static final String ID = "magicka";

    public Magicka(IEventBus bus, ModContainer container) {
        MagicBlocks.BLOCKS.register(bus);
        MagicBlocks.ENTITIES.register(bus);
        MagicItems.ITEMS.register(bus);
        bus.addListener(MagicItems::creative);
        bus.addListener(MagicNetwork::register);
        container.registerConfig(ModConfig.Type.SERVER, MagicConfig.SPEC);
        NeoForge.EVENT_BUS.register(new CastingServer());
        NeoForge.EVENT_BUS.register(new MagicStats());
        NeoForge.EVENT_BUS.register(new RecipeRegistry());
        NeoForge.EVENT_BUS.register(new MagicStatuses());
        NeoForge.EVENT_BUS.register(new SpellRuntime());
        NeoForge.EVENT_BUS.register(new RevivalManager());
        NeoForge.EVENT_BUS.register(new UndeadMinions());
        NeoForge.EVENT_BUS.register(new PolymorphManager());
        NeoForge.EVENT_BUS.register(new MagicConstructionManager());
        NeoForge.EVENT_BUS.register(new ShapePattern());
        NeoForge.EVENT_BUS.addListener(MagicBlocks::climbRoots);
    }
}
