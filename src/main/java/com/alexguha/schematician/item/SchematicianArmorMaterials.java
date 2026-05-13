package com.alexguha.schematician.item;

import com.alexguha.schematician.Schematician;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class SchematicianArmorMaterials {
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, Schematician.MODID);

    // Mirrors Aeronautics' AeroArmorMaterials.AVIATORS_GOGGLES so the upgrade preserves stats.
    // If you change one, change the other.
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> SCHEMATICIANS_GOGGLES = ARMOR_MATERIALS.register(
            "schematicians_goggles",
            () -> new ArmorMaterial(
                    new EnumMap<>(Map.of(ArmorItem.Type.HELMET, 1)),
                    15,
                    SoundEvents.ARMOR_EQUIP_LEATHER,
                    () -> Ingredient.of(Items.LEATHER),
                    List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(Schematician.MODID, "schematicians_goggles"))),
                    0.0f,
                    0.0f));

    private SchematicianArmorMaterials() {}
}
