package com.alexguha.schematician.compat;

import com.alexguha.schematician.Schematician;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.world.entity.EquipmentSlot;

// All Create-touching glue lives here. Create is a required dep so we don't need a ModList gate;
// keeping the separation makes future Aeronautics/Simulated compat classes easier to organize.
public final class CreateCompat {
    private CreateCompat() {}

    public static void registerGogglesPredicate() {
        GogglesItem.addIsWearingPredicate(player ->
                player.getItemBySlot(EquipmentSlot.HEAD).is(Schematician.SCHEMATICIANS_GOGGLES.asItem()));
    }

    // Hook into Create's shift-expand tooltip system. Auto-loads:
    //   item.schematician.schematicians_goggles.tooltip.summary
    //   item.schematician.schematicians_goggles.tooltip.condition1 / .behaviour1
    public static void registerGogglesTooltip() {
        TooltipModifier.REGISTRY.register(
                Schematician.SCHEMATICIANS_GOGGLES.asItem(),
                new ItemDescription.Modifier(
                        Schematician.SCHEMATICIANS_GOGGLES.asItem(),
                        FontHelper.Palette.STANDARD_CREATE));
    }
}
