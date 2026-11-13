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
                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                .suggests((c, b) -> {
                                    var value = values().get(StringArgumentType.getString(c, "key"));
                                    if (value instanceof ForgeConfigSpec.BooleanValue) { b.suggest("true"); b.suggest("false"); }
                                    else if(value!=null && value.getDefault() instanceof Enum<?> e)for(Object option:e.getDeclaringClass().getEnumConstants())b.suggest(((Enum<?>)option).name());
                                    else if (value != null) b.suggest(String.valueOf(value.getDefault()));
                                    b.suggest("default");
                                    return b.buildFuture();
                                })
                                .executes(c -> set(c.getSource(), StringArgumentType.getString(c, "key"), StringArgumentType.getString(c, "value")))));
    }

    public static Map<String, ForgeConfigSpec.ConfigValue<?>> values() {
        Map<String, ForgeConfigSpec.ConfigValue<?>> out = new LinkedHashMap<>();
        MagicConfig.SPEC.getValues().valueMap().forEach((k, v) -> { if (v instanceof ForgeConfigSpec.ConfigValue<?> value) out.put(k, value); });
        BalanceConfig.SPEC.getValues().valueMap().forEach((k,v)->{if(v instanceof ForgeConfigSpec.ConfigValue<?> value)out.put("balance."+k,value);});
        return out;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        var source = c.getSource();
        source.sendSuccess(Component.translatable("command.magicka.config.header").withStyle(ChatFormatting.GOLD), false);
        values().forEach((key, value) -> source.sendSuccess(line(key, value), false));
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
        source.sendSuccess(line(key, value), false);
        source.sendSuccess(Component.translatable("magicka.configuration." + key + ".tooltip").withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(Component.translatable("command.magicka.config.default", String.valueOf(value.getDefault()), allowed(value)).withStyle(ChatFormatting.DARK_GRAY), false);
        return 1;
    }

    private static String allowed(ForgeConfigSpec.ConfigValue<?> value) {
        if (value instanceof ForgeConfigSpec.BooleanValue) return "true / false";
        if(value.getDefault() instanceof Enum<?> e)return java.util.Arrays.toString(e.getDeclaringClass().getEnumConstants());
        if(value.getDefault() instanceof java.util.List<?>)return "[\"key=value\", \"key=value\"]";
        var range = SettingsData.spec(value).getRange();
        return range == null ? "" : range.getMin() + " … " + range.getMax();
    }

    @SuppressWarnings("unchecked")
    private static int set(CommandSourceStack source, String key, String text) {
        var value = (ForgeConfigSpec.ConfigValue<Object>) values().get(key);
        if (value == null) { source.sendFailure(Component.translatable("command.magicka.config.unknown", key)); return 0; }
        Object parsed = text.equalsIgnoreCase("default") ? value.getDefault() : parse(value.getDefault(), text);
        if (!SettingsData.valid(key,parsed)) {
            source.sendFailure(Component.translatable("command.magicka.config.invalid", text, key, allowed(value)));
            return 0;
        }
        value.set(parsed);
        value.save();
        SettingsData.sync();SettingsData.changed(source.getServer(),java.util.Set.of(key));
        source.sendSuccess(Component.translatable("command.magicka.config.set", key, String.valueOf(parsed)), true);
        return 1;
    }

    static Object parse(Object example, String text) {
        try {
            if (example instanceof Boolean) return text.equalsIgnoreCase("true") ? Boolean.TRUE : text.equalsIgnoreCase("false") ? Boolean.FALSE : null;
            if (example instanceof Integer) return Integer.parseInt(text);
            if (example instanceof Double) return Double.parseDouble(text.replace(',', '.'));
            if(example instanceof Enum<?> e)return java.util.Arrays.stream(e.getDeclaringClass().getEnumConstants()).filter(x->((Enum<?>)x).name().equalsIgnoreCase(text)).findFirst().orElse(null);
            if(example instanceof java.util.List<?>){var result=new java.util.ArrayList<String>();for(var entry:com.google.gson.JsonParser.parseString(text).getAsJsonArray()){if(!entry.isJsonPrimitive()||!entry.getAsJsonPrimitive().isString())return null;result.add(entry.getAsString());}return result;}
        } catch (RuntimeException e) {
            return null;
        }
        return null;
    }

    private ConfigCommand() {}
}
