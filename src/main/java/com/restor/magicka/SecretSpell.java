package com.restor.magicka;

import net.minecraft.server.level.ServerPlayer;

public interface SecretSpell {
    SecretSpell INSTANCE = load();

    boolean matches(String typed);

    void cast(ServerPlayer player);

    private static SecretSpell load() {
        try {
            return (SecretSpell) Class.forName("com.restor.magicka.SecretBuild").getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError e) {
            return new SecretSpell() {
                @Override public boolean matches(String typed) { return false; }
                @Override public void cast(ServerPlayer player) {}
            };
        }
    }
}
