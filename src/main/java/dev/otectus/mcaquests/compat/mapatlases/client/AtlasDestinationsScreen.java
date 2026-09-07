package dev.otectus.mcaquests.compat.mapatlases.client;

import dev.otectus.mcaquests.client.ClientGuidanceData;
import dev.otectus.mcaquests.client.QuestWaypointSync;
import dev.otectus.mcaquests.client.map.MapActionScreen;
import dev.otectus.mcaquests.client.map.MapContextScreen;
import dev.otectus.mcaquests.compat.WaypointSpec;
import dev.otectus.mcaquests.compat.mapatlases.AtlasMarkerStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Paged native widgets keep every destination reachable with Tab, narrator and a small window. */
final class AtlasDestinationsScreen extends Screen implements MapContextScreen {
    private final Screen parent;
    private final Set<String> filter;
    private int page;
    private long revision;
    AtlasDestinationsScreen(Screen parent, Set<String> filter) {
        super(Component.translatable("mcaquests.atlas.destinations")); this.parent=parent; this.filter=Set.copyOf(filter);
    }
    @Override public Screen mapParent() { return parent; }
    @Override protected void init() {
        revision = ClientGuidanceData.revision();
        List<WaypointSpec> all = ClientGuidanceData.all().stream()
                .map(g -> QuestWaypointSync.specification(g, WaypointSpec.Ownership.AUTOMATIC))
                .filter(s -> filter.isEmpty() || filter.contains(s.key())).sorted(AtlasMarkerStore.ORDER).toList();
        int rows = Math.max(1, (height-108)/24), pages = Math.max(1, (all.size()+rows-1)/rows);
        page = Math.min(page,pages-1);
        int w=Math.min(340,width-24), x=(width-w)/2;
        addRenderableWidget(Button.builder(Component.translatable("mcaquests.atlas.focus_primary"), b ->
                ClientGuidanceData.primary().ifPresent(g -> AtlasRuntime.get().navigate(
                        QuestWaypointSync.specification(g, WaypointSpec.Ownership.AUTOMATIC))))
                .bounds(x,30,w,20).build()).active=ClientGuidanceData.primary().isPresent();
        for (int i=page*rows; i<Math.min(all.size(),(page+1)*rows); i++) {
            WaypointSpec spec=all.get(i);
            Component label=Component.literal((spec.presentation().primary()? "* " : "")+spec.label());
            Button row=addRenderableWidget(Button.builder(label,b -> minecraft.setScreen(new MapActionScreen(this,spec.key())))
                    .bounds(x,56+(i-page*rows)*24,w,20).build());
            var availability=AtlasRuntime.get().availability(spec,false);
            row.setTooltip(Tooltip.create(Component.literal(spec.presentation().questTitle()).append("\n")
                    .append(Component.translatable("mcaquests.atlas.reason."+availability.reason()))));
        }
        addRenderableWidget(Button.builder(Component.literal("<"),b -> { page--; rebuildWidgets(); })
                .bounds(x,height-46,36,20).build()).active=page>0;
        addRenderableWidget(Button.builder(Component.literal(">"),b -> { page++; rebuildWidgets(); })
                .bounds(x+w-36,height-46,36,20).build()).active=page+1<pages;
        addRenderableWidget(Button.builder(Component.translatable("gui.back"),b -> onClose())
                .bounds(width/2-60,height-46,120,20).build());
    }
    @Override public void tick() {
        if (minecraft.level==null) { minecraft.setScreen(null); return; }
        if (revision!=ClientGuidanceData.revision()) {
            Component focused=getFocused() instanceof Button b? b.getMessage():null;
            rebuildWidgets();
            if (focused!=null) children().stream().filter(c -> c instanceof Button b && b.getMessage().equals(focused))
                    .findFirst().ifPresent(this::setFocused);
        }
    }
    @Override public void render(GuiGraphics graphics,int x,int y,float partial) {
        graphics.fill(0,0,width,height,0xE0101018);
        graphics.drawCenteredString(font,title,width/2,12,-1);
        super.render(graphics,x,y,partial);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
