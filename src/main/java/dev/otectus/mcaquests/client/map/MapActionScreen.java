package dev.otectus.mcaquests.client.map;

import dev.otectus.mcaquests.client.*;
import dev.otectus.mcaquests.compat.*;
import dev.otectus.mcaquests.network.QuestNetwork;
import dev.otectus.mcaquests.network.QuestTrackC2SPacket;
import dev.otectus.mcaquests.quest.guidance.ActiveGuidance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Focusable contextual choices. Every pin click names its destination and rechecks current guidance. */
public final class MapActionScreen extends Screen implements MapContextScreen {
    private final Screen parent;
    private final String key;
    private long revision;
    private boolean committed;
    public MapActionScreen(Screen parent, String key) {
        super(Component.translatable("mcaquests.atlas.actions")); this.parent = parent; this.key = key;
    }
    @Override public Screen mapParent() { return parent; }
    public static Optional<ActiveGuidance> current(String key) {
        return ClientGuidanceData.all().stream().filter(g -> key.equals(g.questId() + "/" + g.villagerUuid())).findFirst();
    }
    public static Component backendName(MapWaypointBackend backend) {
        return Component.translatable("mcaquests.atlas.backend." + backend.id());
    }
    public static void open(Screen parent, ActiveGuidance guidance) {
        Minecraft mc = Minecraft.getInstance();
        WaypointSpec spec = QuestWaypointSync.specification(guidance, WaypointSpec.Ownership.PIN);
        var eligible = MapPinRouting.eligible(ClientMapWaypointRegistry.backends(), spec, mc.level == null ? null : mc.level.dimension());
        boolean navigation = ClientMapWaypointRegistry.backends().stream().anyMatch(MapWaypointBackend::supportsNavigation);
        if (eligible.size() == 1 && !navigation) report(MapPinRouting.save(eligible, Set.of(eligible.get(0).id()), spec, mc.level.dimension()));
        else mc.setScreen(new MapActionScreen(parent, spec.key()));
    }
    @Override protected void init() {
        revision = ClientGuidanceData.revision();
        int w = Math.min(280, width - 24), x = (width - w) / 2, y = 48;
        var guidance = current(key);
        if (guidance.isPresent()) {
            WaypointSpec spec = QuestWaypointSync.specification(guidance.get(), WaypointSpec.Ownership.PIN);
            for (MapWaypointBackend backend : ClientMapWaypointRegistry.backends().stream().sorted(Comparator.comparing(MapWaypointBackend::id)).toList()) {
                if (backend.supportsNavigation()) {
                    var availability = backend.navigationAvailability(spec);
                    Button button = addRenderableWidget(Button.builder(Component.translatable("mcaquests.atlas.show"), b ->
                            current(key).ifPresent(g -> {
                                MapMutationResult result = backend.navigate(QuestWaypointSync.specification(g, WaypointSpec.Ownership.AUTOMATIC));
                                if (result != MapMutationResult.APPLIED) message("mcaquests.atlas.navigation_failed");
                            })).bounds(x, y, w, 20).build());
                    button.active = availability.available();
                    button.setTooltip(Tooltip.create(Component.translatable("mcaquests.atlas.reason." + availability.reason())));
                    y += 24;
                }
                if (backend.capabilities().pins() == PinSupport.NONE && !backend.id().equals("map_atlases")) continue;
                var availability = backend.pinAvailability(spec, minecraft.level == null ? null : minecraft.level.dimension());
                String label = backend.capabilities().pins() == PinSupport.SESSION ? "save_session" : "save";
                Button button = addRenderableWidget(Button.builder(Component.translatable("mcaquests.atlas." + label, backendName(backend)), b -> save(Set.of(backend.id())))
                        .bounds(x, y, w, 20).build());
                button.active = !committed && availability.available();
                button.setTooltip(Tooltip.create(Component.translatable("mcaquests.atlas.reason." + availability.reason())));
                y += 24;
            }
            var eligible = MapPinRouting.eligible(ClientMapWaypointRegistry.backends(), spec, minecraft.level == null ? null : minecraft.level.dimension());
            if (eligible.size() > 1) {
                Set<String> selected = new LinkedHashSet<>(); eligible.forEach(b -> selected.add(b.id()));
                addRenderableWidget(Button.builder(Component.translatable("mcaquests.atlas.save_all"), b -> save(selected))
                        .bounds(x, y, w, 20).build()).active = !committed;
                y += 24;
            }
            addRenderableWidget(Button.builder(Component.translatable("mcaquests.atlas.follow"), b -> current(key).ifPresent(g -> {
                QuestNetwork.CHANNEL.sendToServer(QuestTrackC2SPacket.of(g.villagerUuid(), g.questId()));
                onClose();
            })).bounds(x, y, w, 20).build()); y += 24;
            addRenderableWidget(Button.builder(Component.translatable("mcaquests.atlas.view_quest"), b -> {
                QuestLogScreen log = new QuestLogScreen();
                log.selectMapQuest(key);
                minecraft.setScreen(log);
            }).bounds(x, y, w, 20).build()); y += 24;
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose()).bounds(x, y + 4, w, 20).build());
        if (y + 28 > height) {
            int step = Math.max(14, (height - 48) / children().size()), row = 0;
            for (var child : children()) if (child instanceof Button button) {
                button.setY(44 + row++ * step); button.setHeight(step - 2);
            }
        }
    }
    private void save(Set<String> selected) {
        if (committed || minecraft.level == null) return;
        current(key).ifPresent(g -> {
            committed = true;
            report(MapPinRouting.save(ClientMapWaypointRegistry.backends(), selected,
                    QuestWaypointSync.specification(g, WaypointSpec.Ownership.PIN), minecraft.level.dimension()));
            onClose();
        });
    }
    public static void report(Map<String, MapMutationResult> results) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        results.forEach((id, result) -> {
            var backend = ClientMapWaypointRegistry.backends().stream().filter(b -> b.id().equals(id)).findFirst();
            boolean session = backend.map(b -> b.capabilities().pins() == PinSupport.SESSION).orElse(false);
            String outcome = result == MapMutationResult.APPLIED ? (session ? "saved_session" : "saved") : "save_failed";
            mc.player.displayClientMessage(Component.translatable("mcaquests.atlas." + outcome,
                    Component.translatable("mcaquests.atlas.backend." + id)), false);
        });
    }
    private static void message(String key) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(Component.translatable(key), true);
    }
    @Override public void tick() {
        if (minecraft.level == null) { minecraft.setScreen(null); return; }
        if (revision != ClientGuidanceData.revision()) {
            Component focused = getFocused() instanceof Button b ? b.getMessage() : null;
            rebuildWidgets();
            if (focused != null) children().stream().filter(c -> c instanceof Button b && b.getMessage().equals(focused))
                    .findFirst().ifPresent(this::setFocused);
        }
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xE0101018);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFFFF);
        current(key).ifPresent(g -> graphics.drawCenteredString(font,
                font.plainSubstrByWidth(g.target().label().getString(), width - 24), width / 2, 29, 0xFFFFFFAA));
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
