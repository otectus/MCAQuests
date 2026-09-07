package dev.otectus.mcaquests.compat.pack;

import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.network.chat.Component;
import net.minecraftforge.resource.PathPackResources;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompatPackPriorityTest {
    static { TestBootstrap.ensureBootstrapped(); }

    @Test
    void newlyMountedDefaultsAreInsertedBelowTheOwnersSelectedPack(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("pack.mcmeta"),
                "{\"pack\":{\"pack_format\":15,\"description\":\"priority fixture\"}}");
        Pack defaults = CompatPackFinder.createPack("mcaquests/test", "test", root);
        Pack owner = Pack.readMetaAndCreate("file/owner", Component.literal("Owner"), false,
                id -> new PathPackResources(id, false, root), PackType.SERVER_DATA,
                Pack.Position.TOP, PackSource.DEFAULT);
        assertNotNull(defaults);
        assertNotNull(owner);
        PackRepository repository = new PackRepository(consumer -> {
            consumer.accept(defaults);
            consumer.accept(owner);
        });
        repository.reload();
        repository.setSelected(List.of("file/owner"));
        assertEquals(List.of("mcaquests/test", "file/owner"),
                repository.getSelectedPacks().stream().map(Pack::getId).toList(),
                "server resources are resolved from the end: owner definitions must win");
    }
}
