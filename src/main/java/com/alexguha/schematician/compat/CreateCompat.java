package com.alexguha.schematician.compat;

import com.alexguha.schematician.Schematician;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import net.minecraft.world.entity.EquipmentSlot;

// IMPORTANT: This class imports Create. Never reference it from a code path that runs when Create
// is absent — the JVM will fail to link it. Schematician's main constructor gates the call on
// ModList.isLoaded("create"). All Create-touching code lives here so the rest of the mod stays
// loadable without Create.
public final class CreateCompat {
    private CreateCompat() {}

    public static void registerGogglesPredicate() {
        GogglesItem.addIsWearingPredicate(player ->
                player.getItemBySlot(EquipmentSlot.HEAD).is(Schematician.SCHEMATICIANS_GOGGLES.asItem()));
    }
}
