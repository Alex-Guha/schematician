package com.alexguha.schematician;

import com.alexguha.schematician.compat.CreateCompat;
import com.alexguha.schematician.component.SchematicianDataComponents;
import com.alexguha.schematician.config.SchematicianClientConfig;
import com.alexguha.schematician.item.SchematicianArmorMaterials;
import com.alexguha.schematician.item.SchematicianGogglesItem;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(Schematician.MODID)
public class Schematician {
    public static final String MODID = "schematician";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredItem<SchematicianGogglesItem> SCHEMATICIANS_GOGGLES = ITEMS.register(
            "schematicians_goggles",
            () -> new SchematicianGogglesItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SCHEMATICIAN_TAB = CREATIVE_MODE_TABS.register(
            "schematician_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MODID))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> SCHEMATICIANS_GOGGLES.get().getDefaultInstance())
                    .displayItems((parameters, output) -> output.accept(SCHEMATICIANS_GOGGLES.get()))
                    .build());

    public Schematician(IEventBus modEventBus, ModContainer modContainer) {
        SchematicianArmorMaterials.ARMOR_MATERIALS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        SchematicianDataComponents.COMPONENTS.register(modEventBus);

        modEventBus.addListener((FMLCommonSetupEvent event) -> {
            CreateCompat.registerGogglesPredicate();
            CreateCompat.registerGogglesTooltip();
        });

        if (FMLEnvironment.dist == Dist.CLIENT) {
            SchematicianClientConfig.register(modContainer);
        }
    }
}
