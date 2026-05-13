package com.alexguha.schematician.component;

import com.alexguha.schematician.Schematician;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SchematicianDataComponents {
    private SchematicianDataComponents() {}

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Schematician.MODID);

    // Default true: stacks crafted before this update keep rendering the drafting view.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> DRAFTING_VIEW_ENABLED =
            COMPONENTS.registerComponentType("drafting_view_enabled", builder -> builder
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL));
}
