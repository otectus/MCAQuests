package dev.otectus.mcaquests.mixin;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Exposes vanilla {@code Screen}'s widget lists so {@code client.McaScreenButtons} can register a
 * button on MCA's villager menu. Non-generic field accessors (unlike the generic
 * {@code addRenderableWidget} method) map cleanly under the Mixin AP, and adding to all three lists is
 * equivalent to {@code addRenderableWidget}.
 *
 * <p>This targets vanilla {@code Screen}, not MCA — the two mixins that did target MCA were replaced
 * by ordinary Forge screen events, because an {@code @Accessor}'s descriptor is validated against the
 * target field's declared type and so cannot be made agnostic of MCA's package root. That is also why
 * {@code mcaquests.mixins.json} can safely keep {@code "required": true}: nothing it targets can be
 * absent.
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
