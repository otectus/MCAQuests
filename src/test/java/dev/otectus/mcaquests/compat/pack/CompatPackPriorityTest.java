package dev.otectus.mcaquests.compat.pack;

import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.BuiltInPackSource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CompatPackPriorityTest {
    static { TestBootstrap.ensureBootstrapped(); }

    @Test
    void newlyMountedDefaultsAreInsertedBelowTheOwnersSelectedPack(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("pack.mcmeta"),
                "{\"pack\":{\"pack_format\":48,\"description\":\"priority fixture\"}}");
        Pack defaults = CompatPackFinder.createPack("mcaquests/test", "test", root);
        PackLocationInfo location = new PackLocationInfo("file/owner", Component.literal("Owner"),
                PackSource.DEFAULT, Optional.empty());
        Pack owner = Pack.readMetaAndCreate(location,
                BuiltInPackSource.fromName(path -> new PathPackResources(path, root)),
                PackType.SERVER_DATA, new PackSelectionConfig(false, Pack.Position.TOP, false));
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
