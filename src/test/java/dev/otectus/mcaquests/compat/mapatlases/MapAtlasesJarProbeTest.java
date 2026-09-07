package dev.otectus.mcaquests.compat.mapatlases;

import org.junit.jupiter.api.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import org.objectweb.asm.Type;
import static org.junit.jupiter.api.Assertions.*;

/** Opens exact production bytes; neither optional mod nor Minecraft client is loaded. */
class MapAtlasesJarProbeTest {
    private static ClassNode node(JarFile jar,String path) throws Exception {
        var entry=jar.getJarEntry(path.replace('.','/')+".class"); assertNotNull(entry,path);
        ClassNode node=new ClassNode(); try(var in=jar.getInputStream(entry)){new ClassReader(in).accept(node,0);} return node;
    }
    @Test void releaseHasCompleteRenderAndNativeActionContracts() throws Exception {
        String path=System.getProperty("mcaquests.atlas.jar");
        Assumptions.assumeTrue(path!=null,"Use mapAtlasesProbeTest with both exact JAR paths");
        assertTrue(Files.isRegularFile(Path.of(path)));
        try(var jar=new JarFile(path)) {
            verifyReflectiveBindings(jar);
            var display=node(jar,AtlasHookManifest.TARGET);
            assertEquals(List.of(),AtlasHookManifest.inspect(display));
            var held = node(jar,AtlasHookManifest.HAND_TARGET);
            assertEquals(List.of(),AtlasHookManifest.inspectHand(held));
            assertFalse(AtlasHookManifest.handHandlerInstalled(held));
            AtlasHookManifest.method(held,"render",AtlasHookManifest.HAND).desc="()V";
            assertFalse(AtlasHookManifest.inspectHand(held).isEmpty());
            assertFalse(AtlasHookManifest.handlersInstalled(display),"a bytecode probe is not a runtime render observation");
            var changed=node(jar,AtlasHookManifest.TARGET);
            AtlasHookManifest.method(changed,"drawMap",AtlasHookManifest.TILE).desc="()V";
            assertFalse(AtlasHookManifest.inspect(changed).isEmpty(),"unknown descriptors must be rejected");
            var screen=node(jar,"pepjebs.mapatlases.client.screen.AtlasOverviewScreen");
            for(String field:List.of("atlas","currentMaps","mapWidget")) assertTrue(screen.fields.stream().anyMatch(f->f.name.equals(field)),field);
            for(String name:List.of("getAtlas","getSelectedSlice","selectDimension","updateSlice","isPlacingPin","isShearing","isEditingText"))
                assertTrue(screen.methods.stream().anyMatch(m->m.name.equals(name)),name);
            var holder=node(jar,"pepjebs.mapatlases.utils.MapDataHolder");
            for(String name:List.of("id","data","slice","type","height")) assertTrue(holder.fields.stream().anyMatch(f->f.name.equals(name)),name);
            var pins=node(jar,"pepjebs.mapatlases.integration.moonlight.ClientMarkers");
            assertNotNull(AtlasHookManifest.method(pins,"placePin","(Lpepjebs/mapatlases/utils/MapDataHolder;Lnet/minecraft/server/level/ColumnPos;Ljava/lang/String;I)V"));
            assertTrue(pins.fields.stream().anyMatch(f->f.name.equals("MARKERS_PER_MAP")));
            assertTrue(pins.methods.stream().anyMatch(m->m.name.equals("getPinWithIndex")));
            assertNotNull(AtlasHookManifest.method(node(jar,"pepjebs.mapatlases.networking.C2S2COpenAtlasScreenPacket"),"<init>","()V"));
        }
        String moon=System.getProperty("mcaquests.moonlight.jar"); assertNotNull(moon);
        try(var jar=new JarFile(moon)) {
            var channel=node(jar,"net.mehvahdjukaar.moonlight.api.platform.network.NetworkHelper");
            assertNotNull(AtlasHookManifest.method(channel,"sendToServer","(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V"));
        }
        for(String artifact:List.of(path,moon)) System.out.println(Path.of(artifact).getFileName()+" SHA-256 "+
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(artifact)))));
    }
    private static void verifyReflectiveBindings(JarFile jar) throws Exception {
        Path source = Path.of(System.getProperty("mcaquests.projectRoot", "."),
                "src/main/java/dev/otectus/mcaquests/compat/mapatlases/client/AtlasNativeBinding.java");
        String binding = Files.readString(source);
        Map<String,String> owners = new HashMap<>();
        var classes = Pattern.compile("Class<\\?>\\s+(\\w+)\\s*=\\s*type\\(\"([^\"]+)\"\\)").matcher(binding);
        while (classes.find()) owners.put(classes.group(1), "pepjebs.mapatlases." + classes.group(2));
        // These are the Java parameter types used by the binding, independent of native method names.
        Map<String,String> parameters = Map.of("ItemStack.class", "net.minecraft.world.item.ItemStack",
                "Level.class", "net.minecraft.world.level.Level", "ResourceKey.class", "net.minecraft.resources.ResourceKey",
                "PoseStack.class", "com.mojang.blaze3d.vertex.PoseStack", "ColumnPos.class", "net.minecraft.server.level.ColumnPos",
                "String.class", "java.lang.String", "int.class", "int", "boolean.class", "boolean");
        var lookups = Pattern.compile("method\\((?:type\\(\"([^\"]+)\"\\)|(\\w+)),\\s*\"([^\"]+)\"([^)]*)\\)").matcher(binding);
        int checked = 0;
        while (lookups.find()) {
            String owner = lookups.group(1) != null ? "pepjebs.mapatlases." + lookups.group(1) : owners.get(lookups.group(2));
            assertNotNull(owner, "unresolved binding owner: " + lookups.group());
            List<String> expected = new ArrayList<>();
            for (String argument : lookups.group(4).split(",")) {
                if (argument.isBlank()) continue;
                String type = parameters.getOrDefault(argument.trim(), owners.get(argument.trim()));
                assertNotNull(type, "unresolved binding parameter: " + argument);
                expected.add(type);
            }
            String name = lookups.group(3);
            assertTrue(node(jar, owner).methods.stream().anyMatch(m -> m.name.equals(name)
                            && Arrays.stream(Type.getArgumentTypes(m.desc)).map(Type::getClassName).toList().equals(expected)),
                    () -> "Missing native binding: " + owner + "." + name + expected);
            checked++;
        }
        assertTrue(checked >= 16, "probe must inspect the complete native method binding set");
    }
}
