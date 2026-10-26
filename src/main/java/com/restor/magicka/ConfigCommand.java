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
import net.minecraftforge.common.ForgeConfigSpec;
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
                                    if (value instanceof ForgeConfigSpec.BooleanValue) { b.suggest("true"); b.suggest("false"); }
                                    else if (value != null) b.suggest(String.valueOf(value.getDefault()));
                                    b.suggest("default");
                                    return b.buildFuture();
                                })
                                .executes(c -> set(c.getSource(), StringArgumentType.getString(c, "key"), StringArgumentType.getString(c, "value")))));
    }

    static Map<String, ForgeConfigSpec.ConfigValue<?>> values() {
        Map<String, ForgeConfigSpec.ConfigValue<?>> out = new LinkedHashMap<>();
        MagicConfig.SPEC.getValues().valueMap().forEach((k, v) -> { if (v instanceof ForgeConfigSpec.ConfigValue<?> value) out.put(k, value); });
        return out;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        var source = c.getSource();
        source.sendSuccess(() -> Component.translatable("command.magicka.config.header").withStyle(ChatFormatting.GOLD), false);
        values().forEach((key, value) -> source.sendSuccess(() -> line(key, value), false));
        return values().size();
    }

    private static MutableComponent line(String key, ForgeConfigSpec.ConfigValue<?> value) {
        boolean changed = !value.get().equals(value.getDefault());
        return Component.literal(key).withStyle(s -> s.withColor(ChatFormatting.AQUA).withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/spellweaver config " + key + " ")))
                .append(Component.literal(" = ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(value.get())).withStyle(changed ? ChatFormatting.YELLOW : ChatFormatting.WHITE));
    }

    private static int show(CommandSourceStack source, String key) {
        var value = values().get(key);
        if (value == null) { source.sendFailure(Component.translatable("command.magicka.config.unknown", key)); return 0; }
        source.sendSuccess(() -> line(key, value), false);
        source.sendSuccess(() -> Component.translatableWithFallback("magicka.configuration." + key + ".tooltip", spec(value).getComment()).withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.translatable("command.magicka.config.default", String.valueOf(value.getDefault()), allowed(value)).withStyle(ChatFormatting.DARK_GRAY), false);
        return 1;
    }

    private static ForgeConfigSpec.ValueSpec spec(ForgeConfigSpec.ConfigValue<?> value) { return MagicConfig.SPEC.getSpec().get(value.getPath()); }

    private static String allowed(ForgeConfigSpec.ConfigValue<?> value) {
        if (value instanceof ForgeConfigSpec.BooleanValue) return "true / false";
        var range = spec(value).getRange();
        return range == null ? "" : range.getMin() + " … " + range.getMax();
    }

    @SuppressWarnings("unchecked")
    private static int set(CommandSourceStack source, String key, String text) {
        var value = (ForgeConfigSpec.ConfigValue<Object>) values().get(key);
        if (value == null) { source.sendFailure(Component.translatable("command.magicka.config.unknown", key)); return 0; }
        Object parsed = text.equalsIgnoreCase("default") ? value.getDefault() : parse(value.getDefault(), text);
        if (parsed == null || !spec(value).test(parsed)) {
            source.sendFailure(Component.translatable("command.magicka.config.invalid", text, key, allowed(value)));
            return 0;
        }
        value.set(parsed);
        value.save();
        MagicNetwork.sendConfig(source.getServer(), "spellweaver-server.toml");
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
