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
        com.restor.magicka.enemy.MagicEnemies.TYPES.register(bus);
        Elementals.TYPES.register(bus);
        MagicBlocks.BLOCKS.register(bus);
        MagicBlocks.ENTITIES.register(bus);
        GearBonus.COMPONENTS.register(bus);
        MagicItems.ITEMS.register(bus);
        MagicItems.TABS.register(bus);
        GearMenu.MENUS.register(bus);
        SpellScrollLoot.MODIFIERS.register(bus);
        MagicPotions.EFFECTS.register(bus);
        MagicPotions.POTIONS.register(bus);
        bus.addListener(MagicItems::creative);
        bus.addListener(com.restor.magicka.enemy.MagicEnemies::attributes);
        bus.addListener(com.restor.magicka.enemy.MagicEnemies::placements);
        bus.addListener(MagicNetwork::register);
        bus.addListener(Elementals::attributes);
        NeoForge.EVENT_BUS.addListener(Elementals::stop);
        NeoForge.EVENT_BUS.addListener(LegendMagic::join);
        NeoForge.EVENT_BUS.addListener(LegendMagic::target);
        NeoForge.EVENT_BUS.addListener(LegendMagic::ward);
        NeoForge.EVENT_BUS.addListener(LegendLife::death);
        container.registerConfig(ModConfig.Type.SYNCED, MagicConfig.SPEC, "spellweaver-server.toml");
        NeoForge.EVENT_BUS.register(new CastingServer());
        NeoForge.EVENT_BUS.register(new MagicStats());
        NeoForge.EVENT_BUS.register(new MagicGear());
        NeoForge.EVENT_BUS.register(new SpellScrolls());
        NeoForge.EVENT_BUS.register(new ScrollDrops());
        NeoForge.EVENT_BUS.register(new MageLevel());
        NeoForge.EVENT_BUS.register(new MagicPotions());
        NeoForge.EVENT_BUS.register(new PotionBelt());
        NeoForge.EVENT_BUS.register(new RecipeRegistry());
        NeoForge.EVENT_BUS.register(new MagicStatuses());
        NeoForge.EVENT_BUS.register(new SpellRuntime());
        NeoForge.EVENT_BUS.register(new RevivalManager());
        NeoForge.EVENT_BUS.register(new UndeadMinions());
        NeoForge.EVENT_BUS.register(new PolymorphManager());
        NeoForge.EVENT_BUS.register(new MagicConstructionManager());
        NeoForge.EVENT_BUS.register(new ShapePattern());
        NeoForge.EVENT_BUS.addListener(MagicBlocks::climbRoots);
        NeoForge.EVENT_BUS.register(new com.restor.magicka.enemy.MagicEnemies());
    }
}
