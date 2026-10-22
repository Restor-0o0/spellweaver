package com.restor.magicka;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.common.MinecraftForge;

@Mod(Magicka.ID)
public final class Magicka {
    public static final String ID = "magicka";

    public Magicka() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        com.restor.magicka.enemy.MagicEnemies.TYPES.register(bus);
        Elementals.TYPES.register(bus);
        MagicBlocks.BLOCKS.register(bus);
        MagicBlocks.ENTITIES.register(bus);
        MagicItems.ITEMS.register(bus);
        MagicItems.TABS.register(bus);
        GearMenu.MENUS.register(bus);
        bus.addListener(MagicItems::creative);
        bus.addListener(com.restor.magicka.enemy.MagicEnemies::attributes);
        bus.addListener(com.restor.magicka.enemy.MagicEnemies::placements);
        MagicNetwork.register();
        bus.addListener(Elementals::attributes);
        MinecraftForge.EVENT_BUS.addListener(Elementals::stop);
        MinecraftForge.EVENT_BUS.addListener(LegendMagic::join);
        MinecraftForge.EVENT_BUS.addListener(LegendMagic::target);
        MinecraftForge.EVENT_BUS.addListener(LegendLife::death);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, MagicConfig.SPEC, "spellweaver-server.toml");
        MinecraftForge.EVENT_BUS.register(new CastingServer());
        MinecraftForge.EVENT_BUS.register(new MagicStats());
        MinecraftForge.EVENT_BUS.register(new MagicGear());
        MinecraftForge.EVENT_BUS.register(new RecipeRegistry());
        MinecraftForge.EVENT_BUS.register(new MagicStatuses());
        MinecraftForge.EVENT_BUS.register(new SpellRuntime());
        MinecraftForge.EVENT_BUS.register(new RevivalManager());
        MinecraftForge.EVENT_BUS.register(new UndeadMinions());
        MinecraftForge.EVENT_BUS.register(new PolymorphManager());
        MinecraftForge.EVENT_BUS.register(new MagicConstructionManager());
        MinecraftForge.EVENT_BUS.register(new ShapePattern());
        MinecraftForge.EVENT_BUS.addListener(MagicBlocks::climbRoots);
        MinecraftForge.EVENT_BUS.register(new com.restor.magicka.enemy.MagicEnemies());
    }
}
