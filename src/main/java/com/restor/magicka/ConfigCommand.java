package com.restor.magicka;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ConfigCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("config").executes(ConfigCommand::list)
                .then(Commands.argument("key", StringArgumentType.word())
                        .suggests((c, b) -> { values().keySet().forEach(b::suggest); return b.buildFuture(); })
                        .executes(c -> show(c.getSource(), StringArgumentType.getString(c, "key")))
                        .then(Commands.argument("value", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    var value = values().get(StringArgumentType.getString(c, "key"));
                                    if (value instanceof ModConfigSpec.BooleanValue) { b.suggest("true"); b.suggest("false"); }
                                    else if (value != null) b.suggest(String.valueOf(value.getDefault()));
                                    b.suggest("default");
                                    return b.buildFuture();
                                })
                                .executes(c -> set(c.getSource(), StringArgumentType.getString(c, "key"), StringArgumentType.getString(c, "value")))));
    }

    static Map<String, ModConfigSpec.ConfigValue<?>> values() {
        Map<String, ModConfigSpec.ConfigValue<?>> out = new LinkedHashMap<>();
        MagicConfig.SPEC.getValues().valueMap().forEach((k, v) -> { if (v instanceof ModConfigSpec.ConfigValue<?> value) out.put(k, value); });
        return out;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        var source = c.getSource();
        source.sendSuccess(() -> Component.translatable("command.magicka.config.header").withStyle(ChatFormatting.GOLD), false);
        values().forEach((key, value) -> source.sendSuccess(() -> line(key, value), false));
        return values().size();
    }

    private static MutableComponent line(String key, ModConfigSpec.ConfigValue<?> value) {
        boolean changed = !value.get().equals(value.getDefault());
        return Component.literal(key).withStyle(s -> s.withColor(ChatFormatting.AQUA).withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/spellweaver config " + key + " ")))
                .append(Component.literal(" = ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(value.get())).withStyle(changed ? ChatFormatting.YELLOW : ChatFormatting.WHITE));
    }

    private static int show(CommandSourceStack source, String key) {
        var value = values().get(key);
        if (value == null) { source.sendFailure(Component.translatable("command.magicka.config.unknown", key)); return 0; }
        source.sendSuccess(() -> line(key, value), false);
        source.sendSuccess(() -> Component.translatableWithFallback("magicka.configuration." + key + ".tooltip", value.getSpec().getComment()).withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.translatable("command.magicka.config.default", String.valueOf(value.getDefault()), allowed(value)).withStyle(ChatFormatting.DARK_GRAY), false);
        return 1;
    }

    private static String allowed(ModConfigSpec.ConfigValue<?> value) {
        if (value instanceof ModConfigSpec.BooleanValue) return "true / false";
        var range = value.getSpec().getRange();
        return range == null ? "" : range.getMin() + " … " + range.getMax();
    }

    @SuppressWarnings("unchecked")
    private static int set(CommandSourceStack source, String key, String text) {
        var value = (ModConfigSpec.ConfigValue<Object>) values().get(key);
        if (value == null) { source.sendFailure(Component.translatable("command.magicka.config.unknown", key)); return 0; }
        Object parsed = text.equalsIgnoreCase("default") ? value.getDefault() : parse(value.getDefault(), text);
        if (parsed == null || !value.getSpec().test(parsed)) {
            source.sendFailure(Component.translatable("command.magicka.config.invalid", text, key, allowed(value)));
            return 0;
        }
        value.set(parsed);
        value.save();
        net.neoforged.neoforge.network.ConfigSync.syncConfigs().stream().filter(file -> file.fileName().equals("spellweaver-server.toml")).forEach(file -> net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(new MagicNetwork.Config(file.fileName(), file.contents())));
        source.sendSuccess(() -> Component.translatable("command.magicka.config.set", key, String.valueOf(parsed)), true);
        return 1;
    }

    private static Object parse(Object example, String text) {
        try {
            if (example instanceof Boolean) return text.equalsIgnoreCase("true") ? Boolean.TRUE : text.equalsIgnoreCase("false") ? Boolean.FALSE : null;
            if (example instanceof Integer) return Integer.parseInt(text);
            if (example instanceof Double) return Double.parseDouble(text.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
        return null;
    }

    private ConfigCommand() {}
}
