package com.alexguha.aerogoggles.drafting;

import com.alexguha.aerogoggles.AeroGoggles;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

// Temporary dev-only keybind kept from the upstream Aeronautics drafting-view fork so the
// pipeline can be toggled manually until the goggles-worn trigger replaces it.
public enum DraftingKeys {
    TOGGLE_DRAFTING_VIEW("toggle_drafting_view", GLFW.GLFW_KEY_F7, "Toggle Drafting View"),
    ;

    private KeyMapping keybind;
    private final String description;
    private final String translation;
    private final int key;

    DraftingKeys(final String description, final int defaultKey, final String translation) {
        this.description = AeroGoggles.MODID + ".keyinfo." + description;
        this.key = defaultKey;
        this.translation = translation;
    }

    public static void provideLang(final BiConsumer<String, String> consumer) {
        for (final DraftingKeys key : values())
            consumer.accept(key.description, key.translation);
    }

    public static void registerTo(final Consumer<KeyMapping> consumer) {
        for (final DraftingKeys key : values()) {
            key.keybind = new KeyMapping(key.description, key.key, "AeroGoggles");
            consumer.accept(key.keybind);
        }
    }

    public KeyMapping getKeybind() {
        return this.keybind;
    }
}
