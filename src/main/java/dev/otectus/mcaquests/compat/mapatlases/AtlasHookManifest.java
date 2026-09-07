package dev.otectus.mcaquests.compat.mapatlases;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.util.*;

/** Exact NeoForge 6.7.3 bytecode contract. Minecraft anchors allow official and production SRG names. */
public final class AtlasHookManifest {
    public static final String VERSION = "1.21-6.7.3";
    public static final String TARGET = "pepjebs.mapatlases.client.AbstractAtlasDisplay";
    public static final String HAND_TARGET = "pepjebs.mapatlases.client.AtlasInHandRenderer";
    public static final String HAND = "(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/Minecraft;)V";
    public static final String MAP_RENDER = "(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/saveddata/maps/MapId;Lnet/minecraft/world/level/saveddata/maps/MapItemSavedData;ZI)V";
    private static final String ROOT = "pepjebs/mapatlases/";
    public static final String DRAW = "(Lnet/minecraft/client/gui/GuiGraphics;IIIILnet/minecraft/world/entity/player/Player;FZL" + ROOT + "utils/MapType;ILnet/minecraft/world/level/saveddata/maps/MapItemSavedData;)V";
    public static final String TILE = "(Lnet/minecraft/world/entity/player/Player;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lcom/mojang/datafixers/util/Pair;IIL" + ROOT + "utils/MapDataHolder;ZILnet/minecraft/world/level/saveddata/maps/MapItemSavedData;)V";
    private AtlasHookManifest() { }
    public static List<String> inspectHand(ClassNode node) {
        MethodNode render = method(node, "render", HAND);
        if (render == null || (render.access & Opcodes.ACC_STATIC) == 0) return List.of("held render descriptor");
        var maps = calls(render, "net/minecraft/client/gui/MapRenderer", "render", "m_168771_", MAP_RENDER);
        var holders = calls(render, ROOT + "client/MapAtlasesClient", "getActiveMap", "getActiveMap", "()L" + ROOT + "utils/MapDataHolder;");
        var flags = calls(render, ROOT + "client/MapAtlasesClient", "setIsDrawingAtlas", "setIsDrawingAtlas", "(Z)V");
        if (maps.size() != 1 || holders.size() != 1 || flags.size() != 2) return List.of("held map render anchors");
        int map = render.instructions.indexOf(maps.get(0));
        if (render.instructions.indexOf(holders.get(0)) >= map
                || render.instructions.indexOf(flags.get(0)) >= map
                || render.instructions.indexOf(flags.get(1)) <= map) return List.of("held render ordering");
        return List.of();
    }
    public static boolean handHandlerInstalled(ClassNode node) { return has(method(node, "render", HAND), "mcaquests$held"); }
    public static List<String> inspect(ClassNode node) {
        List<String> errors = new ArrayList<>();
        MethodNode draw = method(node, "drawAtlas", DRAW), tile = method(node, "drawMap", TILE);
        if (draw == null) errors.add("drawAtlas descriptor");
        if (tile == null) errors.add("drawMap descriptor");
        if (!errors.isEmpty()) return errors;
        List<MethodInsnNode> batches = calls(draw, "net/minecraft/client/renderer/MultiBufferSource$BufferSource", "endBatch", "m_109911_", "()V");
        List<MethodInsnNode> loops = calls(draw, TARGET.replace('.', '/'), "getAndDrawMap", "getAndDrawMap", null);
        List<MethodInsnNode> scissors = calls(draw, "net/minecraft/client/gui/GuiGraphics", "disableScissor", "m_280618_", "()V");
        if (batches.size() != 2 || loops.size() != 1 || scissors.size() != 1) errors.add("terrain/border/scissor anchors");
        else {
            int loop = draw.instructions.indexOf(loops.get(0)), batch = draw.instructions.indexOf(batches.get(0));
            if (loop >= batch || batch >= draw.instructions.indexOf(scissors.get(0))) errors.add("overlay ordering");
            AbstractInsnNode next = batches.get(0).getNext();
            while (next != null && next.getOpcode() < 0) next = next.getNext();
            if (!(next instanceof VarInsnNode load) || load.getOpcode() != Opcodes.ILOAD || load.var != 8)
                errors.add("terrain flush must precede showBorders branch");
        }
        List<MethodInsnNode> pops = calls(tile, "com/mojang/blaze3d/vertex/PoseStack", "popPose", "m_85849_", "()V");
        List<MethodInsnNode> renders = calls(tile, "net/minecraft/client/gui/MapRenderer", "render", "m_168771_", null);
        if (pops.size() != 1 || renders.size() != 1) errors.add("tile render/pop anchors");
        else if (tile.instructions.indexOf(renders.get(0)) >= tile.instructions.indexOf(pops.get(0))) errors.add("tile capture ordering");
        return List.copyOf(errors);
    }
    public static boolean handlersInstalled(ClassNode node) {
        MethodNode draw = method(node, "drawAtlas", DRAW), tile = method(node, "drawMap", TILE);
        return has(draw, "mcaquests$begin") && has(draw, "mcaquests$overlay")
                && has(draw, "mcaquests$end") && has(tile, "mcaquests$capture");
    }
    private static boolean has(MethodNode node, String suffix) {
        if (node == null) return false;
        for (AbstractInsnNode instruction : node.instructions)
            if (instruction instanceof MethodInsnNode call && call.name.contains(suffix)) return true;
        return false;
    }
    public static MethodNode method(ClassNode node, String name, String desc) {
        return node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findFirst().orElse(null);
    }
    private static List<MethodInsnNode> calls(MethodNode node, String owner, String dev, String prod, String desc) {
        List<MethodInsnNode> result = new ArrayList<>();
        for (AbstractInsnNode insn : node.instructions) if (insn instanceof MethodInsnNode call
                && call.owner.equals(owner) && (call.name.equals(dev) || call.name.equals(prod))
                && (desc == null || call.desc.equals(desc))) result.add(call);
        return result;
    }
}
