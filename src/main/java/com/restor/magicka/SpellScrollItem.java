package com.restor.magicka;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class SpellScrollItem extends Item {
    public SpellScrollItem(Properties properties) { super(properties); }

    public static ItemStack of(SpellDefinition spell) {
        ItemStack stack = new ItemStack(MagicItems.SPELL_SCROLL.get());
        stack.set(GearBonus.SCROLL.get(), spell.id());
        return stack;
    }

    private static SpellDefinition spell(ItemStack stack) {
        ResourceLocation id = stack.get(GearBonus.SCROLL.get());
        if (id == null) return null;
        SpellDefinition spell = RecipeRegistry.clientById(id);
        return spell != null ? spell : RecipeRegistry.byId(id);
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        ResourceLocation id = stack.get(GearBonus.SCROLL.get());
        if (id == null) return InteractionResultHolder.pass(stack);
        if (player instanceof ServerPlayer server) {
            SpellDefinition spell = RecipeRegistry.byId(id);
            if (spell == null) {
                server.displayClientMessage(Component.translatable("scroll.magicka.unknown"), true);
                return InteractionResultHolder.fail(stack);
            }
            if (SpellScrolls.learn(server, id)) {
                level.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1f, 1f);
                server.displayClientMessage(Component.translatable("scroll.magicka.learned", spell.displayName()).withStyle(ChatFormatting.LIGHT_PURPLE), true);
            } else server.displayClientMessage(Component.translatable("scroll.magicka.already", spell.displayName()), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override public Component getName(ItemStack stack) {
        SpellDefinition spell = spell(stack);
        return spell == null ? super.getName(stack) : Component.translatable("item.magicka.spell_scroll.named", spell.displayName());
    }

    @Override public boolean isFoil(ItemStack stack) { return true; }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<Component> lines, TooltipFlag flag) {
        SpellDefinition spell = spell(stack);
        if (spell == null) return;
        if (SpellScrolls.clientKnown(spell.id())) lines.add(Component.translatable("scroll.magicka.known").withStyle(ChatFormatting.GREEN));
        else lines.add(Component.translatable("scroll.magicka.hint").withStyle(ChatFormatting.GRAY));
    }
}
