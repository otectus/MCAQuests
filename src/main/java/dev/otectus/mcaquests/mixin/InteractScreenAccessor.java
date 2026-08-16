package dev.otectus.mcaquests.mixin;

import net.conczin.mca.client.gui.InteractScreen;
import net.conczin.mca.entity.VillagerLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads the private {@code villager} field of MCA's interaction screen so the menu-button mixin can
 * recover the villager's UUID. MCA's own field name survives remapping (it is not a vanilla member).
 */
@Mixin(InteractScreen.class)
public interface InteractScreenAccessor {

    // remap = false: MCA's own field, matched by literal name (no vanilla obfuscation mapping).
    @Accessor(value = "villager", remap = false)
    VillagerLike<?> getVillager();
}
