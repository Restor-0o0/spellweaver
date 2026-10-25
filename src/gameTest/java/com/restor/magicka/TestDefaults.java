package com.restor.magicka;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@EventBusSubscriber(modid = Magicka.ID)
public final class TestDefaults {
    @SubscribeEvent public static void castTimes(ServerStartedEvent event) { MagicConfig.INSTANT_CAST.set(false); }

    private TestDefaults() {}
}
