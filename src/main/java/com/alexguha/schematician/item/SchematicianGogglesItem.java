package com.alexguha.schematician.item;

import com.alexguha.schematician.component.SchematicianDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class SchematicianGogglesItem extends ArmorItem {
    public SchematicianGogglesItem(Properties properties) {
        super(SchematicianArmorMaterials.SCHEMATICIANS_GOGGLES, Type.HELMET, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean current = stack.getOrDefault(SchematicianDataComponents.DRAFTING_VIEW_ENABLED.get(), Boolean.TRUE);
        boolean next = !current;

        if (!level.isClientSide) {
            stack.set(SchematicianDataComponents.DRAFTING_VIEW_ENABLED.get(), next);
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.4f, next ? 1.2f : 0.9f);

        // Delegate to ArmorItem so right-click still equips when held in hand.
        // The stack carries the new component value into the head slot.
        return super.use(level, player, hand);
    }

    // The summary / condition / behaviour body lives in lang/en_us.json under
    // item.schematician.schematicians_goggles.tooltip.* and is injected by Create's
    // ItemDescription.Modifier (registered in CreateCompat). This method only adds the
    // always-visible state line and the shift-gated toggle hint that sits beside it.
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        boolean enabled = stack.getOrDefault(SchematicianDataComponents.DRAFTING_VIEW_ENABLED.get(), Boolean.TRUE);
        tooltip.add(Component.translatable(
                enabled
                        ? "item.schematician.schematicians_goggles.tooltip.state.on"
                        : "item.schematician.schematicians_goggles.tooltip.state.off")
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED));

        if (Screen.hasShiftDown()) {
            tooltip.add(Component.translatable("item.schematician.schematicians_goggles.tooltip.toggle_hint")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }
}
