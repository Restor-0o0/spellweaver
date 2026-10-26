package com.restor.magicka;

import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;

final class Check {
    static void assertTrue(GameTestHelper helper, boolean condition, String message) { if (!condition) throw new GameTestAssertException(message); }
    static void assertFalse(GameTestHelper helper, boolean condition, String message) { if (condition) throw new GameTestAssertException(message); }
    private Check() {}
}
