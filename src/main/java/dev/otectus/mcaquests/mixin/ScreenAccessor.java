package dev.otectus.mcaquests.mixin;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Exposes vanilla {@code Screen}'s widget lists so our MCA-screen mixin can register a button.
 * Non-generic field accessors (unlike the generic {@code addRenderableWidget} method) map cleanly
 * under the Mixin AP. Adding to all three lists is equivalent to {@code addRenderableWidget}.
 */
@Mixin(Screen.class)
public interface ScreenAccessor {

    @Accessor("renderables")
    List<Renderable> mcaquests$getRenderables();

    @Accessor("children")
    List<GuiEventListener> mcaquests$getChildren();

    @Accessor("narratables")
    List<NarratableEntry> mcaquests$getNarratables();
}
