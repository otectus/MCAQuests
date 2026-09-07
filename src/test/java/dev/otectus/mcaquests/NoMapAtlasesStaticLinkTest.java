package dev.otectus.mcaquests;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NoMapAtlasesStaticLinkTest {
    @Test void optionalDescriptorsAndGuardedImplementationNeverLeakIntoCommonEntrypoints() throws Exception {
        Path root=Path.of(System.getProperty("mcaquests.projectRoot", "."),"build/classes/java/main");
        assertTrue(Files.isDirectory(root));
        List<String> violations=new ArrayList<>();
        try(var files=Files.walk(root)) {
            for(Path file:files.filter(p->p.toString().endsWith(".class")).toList()) {
                String path=root.relativize(file).toString().replace('\\','/');
                if(path.startsWith("dev/otectus/mcaquests/compat/mapatlases/")
                        || path.startsWith("dev/otectus/mcaquests/mixin/mapatlases/")) continue;
                String bytes=new String(Files.readAllBytes(file),StandardCharsets.ISO_8859_1);
                for(String needle:List.of("pepjebs/mapatlases/","net/mehvahdjukaar/moonlight/",
                        "dev/otectus/mcaquests/compat/mapatlases/")) if(bytes.contains(needle)) violations.add(path+": "+needle);
            }
        }
        assertEquals(List.of(),violations);
    }
    @Test void backendAndStoreHaveNoClientTypeReferences() throws Exception {
        for(String name:List.of("MapAtlasesWaypointBackend","AtlasMarkerStore","AtlasProjection","AtlasHookState")) {
            var path=Path.of(System.getProperty("mcaquests.projectRoot", "."),"build/classes/java/main/dev/otectus/mcaquests/compat/mapatlases/"+name+".class");
            assertFalse(new String(Files.readAllBytes(path),StandardCharsets.ISO_8859_1).contains("net/minecraft/client/"),name);
        }
    }
}
