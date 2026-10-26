package com.restor.magicka;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.server.ServerStartedEvent;

@EventBusSubscriber(modid = Magicka.ID)
public final class TestDefaults {
    @SubscribeEvent public static void castTimes(ServerStartedEvent event) { MagicConfig.INSTANT_CAST.set(false); }

    private TestDefaults() {}
}
