package com.restor.magicka;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

@EventBusSubscriber(modid = Magicka.ID)
public final class MagicTestRegistry {
    @SubscribeEvent public static void castTimes(net.neoforged.neoforge.event.server.ServerStartedEvent event) { MagicConfig.INSTANT_CAST.set(false); }
    private static final Class<?>[] SUITES = {AuditGameTests.class, DeathGameTests.class, ElementalGameTests.class, EnemyGameTests.class,
            EpicGameTests.class, GearGameTests.class, LegendGameTests.class, MagicGameTests.class, QuickCastGameTests.class,
            RecipeGameTests.class, SupportGameTests.class, PotionGameTests.class, FusionGameTests.class};
    private static final Identifier STRUCTURE = Identifier.fromNamespaceAndPath(Magicka.ID, "empty");

    private record Entry(Identifier id, Method method, MagicTest test) {}

    private static List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        for (Class<?> suite : SUITES) for (Method method : suite.getDeclaredMethods()) {
            MagicTest test = method.getAnnotation(MagicTest.class);
            if (test == null || !Modifier.isStatic(method.getModifiers())) continue;
            String path = (suite.getSimpleName() + "." + method.getName()).toLowerCase(Locale.ROOT);
            entries.add(new Entry(Identifier.fromNamespaceAndPath(Magicka.ID, path), method, test));
        }
        return entries;
    }

    @SubscribeEvent public static void functions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, registry -> {
            for (Entry entry : entries()) registry.register(entry.id, (Consumer<GameTestHelper>) helper -> invoke(entry.method, helper));
        });
    }

    @SubscribeEvent public static void tests(RegisterGameTestsEvent event) {
        Map<String, Holder<TestEnvironmentDefinition<?>>> environments = new HashMap<>();
        for (Entry entry : entries()) {
            String batch = entry.test.batch().isEmpty() ? "default" : entry.test.batch();
            var environment = environments.computeIfAbsent(batch, name -> event.registerEnvironment(Identifier.fromNamespaceAndPath(Magicka.ID, name)));
            event.registerTest(entry.id, new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, entry.id),
                    new TestData<>(environment, Level.OVERWORLD, STRUCTURE, entry.test.timeoutTicks(), 0, true, Rotation.NONE, false, 1, 1, true, 0)));
        }
    }

    private static void invoke(Method method, GameTestHelper helper) {
        try {
            method.invoke(null, helper);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtime) throw runtime;
            if (e.getCause() instanceof Error error) throw error;
            throw new RuntimeException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private MagicTestRegistry() {}
}
