package dev.otectus.mcaquests.client.map;

import net.minecraft.client.gui.screens.Screen;

/** Keeps explicit map actions attached to the native atlas (including lecterns) being viewed. */
public interface MapContextScreen {
    Screen mapParent();
    static Screen unwrap(Screen screen) {
        while (screen instanceof MapContextScreen context) screen = context.mapParent();
        return screen;
    }
}
