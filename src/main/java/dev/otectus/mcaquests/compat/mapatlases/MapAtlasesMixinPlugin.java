package dev.otectus.mcaquests.compat.mapatlases;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;
import java.util.*;

/** Optional client hook preflight inspects bytes without initializing an atlas class. */
public final class MapAtlasesMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public List<String> getMixins() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) { }
    @Override public void preApply(String name, ClassNode node, String mixin, IMixinInfo info) { }
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        boolean hand = AtlasHookManifest.HAND_TARGET.equals(target);
        if (FMLEnvironment.dist != Dist.CLIENT || (!hand && !AtlasHookManifest.TARGET.equals(target))) return false;
        try {
            var file = LoadingModList.get().getModFileById("map_atlases");
            if (file == null) return false;
            boolean version = file.getMods().stream().anyMatch(m -> m.getModId().equals("map_atlases")
                    && AtlasHookManifest.VERSION.equals(m.getVersion().toString()));
            if (!version) { fail(hand, "unsupported atlas version"); return false; }
            ClassNode node = MixinService.getService().getBytecodeProvider().getClassNode(target);
            List<String> errors = hand ? AtlasHookManifest.inspectHand(node) : AtlasHookManifest.inspect(node);
            if (!errors.isEmpty()) { fail(hand, String.join(", ", errors)); return false; }
            if (!hand) AtlasHookState.preflightPassed();
            return true;
        } catch (Exception | LinkageError error) {
            fail(hand, "atlas preflight: " + error.getClass().getSimpleName()); return false;
        }
    }
    @Override public void postApply(String name, ClassNode node, String mixin, IMixinInfo info) {
        if (AtlasHookManifest.HAND_TARGET.equals(name)) {
            if (AtlasHookManifest.handHandlerInstalled(node)) AtlasHookState.handAppliedSuccessfully();
            else AtlasHookState.handFailed("held atlas handler missing after transformation");
            return;
        }
        if (AtlasHookManifest.handlersInstalled(node)) AtlasHookState.appliedSuccessfully();
        else AtlasHookState.failed("atlas handlers missing after transformation");
    }
    private static void fail(boolean hand, String reason) {
        if (hand) AtlasHookState.handFailed(reason); else AtlasHookState.failed(reason);
    }
}
