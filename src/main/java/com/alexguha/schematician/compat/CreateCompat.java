package com.alexguha.schematician.compat;

import com.alexguha.schematician.Schematician;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import net.minecraft.world.entity.EquipmentSlot;

// All Create-touching glue lives here. Create is a required dep so we don't need a ModList gate;
// keeping the separation makes future Aeronautics/Simulated compat classes easier to organize.
public final class CreateCompat {
    private CreateCompat() {}

    public static void registerGogglesPredicate() {
        GogglesItem.addIsWearingPredicate(player ->
                player.getItemBySlot(EquipmentSlot.HEAD).is(Schematician.SCHEMATICIANS_GOGGLES.asItem()));
    }
}
