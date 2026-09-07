package dev.otectus.mcaquests.atlasfixture;

import dev.otectus.mcaquests.client.ClientGuidanceData;
import dev.otectus.mcaquests.client.QuestWaypointSync;
import dev.otectus.mcaquests.client.map.ClientMapWaypointRegistry;
import dev.otectus.mcaquests.compat.*;
import dev.otectus.mcaquests.compat.mapatlases.AtlasHookState;
import dev.otectus.mcaquests.compat.mapatlases.client.*;
import dev.otectus.mcaquests.quest.guidance.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;

/** Disposable runtime test mod. Never included in the MCA: Quests release JAR. */
@Mod("mcaquests_atlas_fixture")
public final class AtlasRuntimeFixture {
    private volatile boolean serverPrepared;
    private int tick;
    private int cx,cz;
    private volatile List<ActiveGuidance> targets=List.of();
    private boolean closed;
    public AtlasRuntimeFixture() {
        if(!Boolean.getBoolean("mcaquests.atlas.fixture")) throw new IllegalStateException("Fixture requires explicit opt-in");
        MinecraftForge.EVENT_BUS.register(this);
    }
    @SubscribeEvent public void server(TickEvent.PlayerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || serverPrepared) return;
        if(!player.getServer().isSingleplayer()) throw new IllegalStateException("Fixture requires an isolated singleplayer world");
        try {
            cx=(int)Math.floor((player.getX()+64)/128)*128;
            cz=(int)Math.floor((player.getZ()+64)/128)*128;
            var level=player.serverLevel();
            ItemStack atlas=new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("map_atlases","atlas")));
            Object maps=Class.forName("pepjebs.mapatlases.item.MapAtlasItem").getMethod("getMaps",ItemStack.class,Level.class).invoke(null,atlas,level);
            Method add=Class.forName("pepjebs.mapatlases.map_collection.IMapCollection").getMethod("add",int.class,Level.class);
            for(int x=-2;x<=2;x++) for(int z=-2;z<=2;z++) {
                ItemStack map=MapItem.create(level,cx+x*128,cz+z*128,(byte)0,true,false);
                int id=MapItem.getMapId(map);
                var data=MapItem.getSavedData(map,level);
                Arrays.fill(data.colors,(byte)(x%2==0? 6:30));data.setDirty();
                add.invoke(maps,id,level);
                var packet=data.getUpdatePacket(id,player); if(packet!=null) player.connection.send(packet);
            }
            player.getInventory().setItem(0,atlas);player.getInventory().selected=0;
            player.setPos(cx+0.5,Math.max(100,player.getY()+8),cz+0.5);
            player.getAbilities().flying=true;player.onUpdateAbilities();
            player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();
            List<ActiveGuidance> next=new ArrayList<>();
            for(int i=0;i<100;i++) {
                var kind=GuidanceKind.values()[i%GuidanceKind.values().length];
                next.add(new ActiveGuidance(new ResourceLocation("mcaquests","fixture_"+(i/2)),
                        new UUID(0,i+1),new GuidanceTarget(kind,OptionalInt.empty(),
                        new BlockPos(cx-80+(i%10)*18,100,cz-80+(i/10)*18),Level.OVERWORLD,
                        Component.literal("Atlas fixture "+i),3,i%9==0,i%13==0,0)));
            }
            targets=List.copyOf(next);serverPrepared=true;
            report("server fixture prepared: 25 maps, 100 destinations, same quest from distinct givers");
        } catch(Exception error) { report("FAIL setup "+error);serverPrepared=true; }
    }
    @SubscribeEvent public void client(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || !serverPrepared || closed) return;
        Minecraft mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        tick++;
        try {
            ClientGuidanceData.update(tick<420 ? new GuidanceSnapshot(targets,0) : GuidanceSnapshot.EMPTY);
            var backend=ClientMapWaypointRegistry.backends().stream().filter(b->b.id().equals("map_atlases")).findFirst().orElse(null);
            if(tick==40) report("binding "+(backend==null?"absent":backend.status()));
            if(tick==60 && backend!=null) report("open "+backend.navigate(QuestWaypointSync.specification(targets.get(0),WaypointSpec.Ownership.AUTOMATIC)));
            if(tick==130) {
                report("fullscreen accepted="+backend.appliedKeys().size()+" visible="+AtlasQuestOverlay.visible(false));
                capture("01-fullscreen.png");
                report("pins="+backend.capabilities().pins()+" eligibility="+backend.pinAvailability(
                        QuestWaypointSync.specification(targets.get(0),WaypointSpec.Ownership.PIN),Level.OVERWORLD));
                report("pin="+backend.pin(QuestWaypointSync.specification(targets.get(0),WaypointSpec.Ownership.PIN)));
            }
            if(tick==190) { mc.setScreen(null); }
            if(tick==230) { report("minimap visible="+AtlasQuestOverlay.visible(true)+" held="+AtlasHeldOverlay.visible());capture("02-minimap-held.png"); }
            if(tick==270) {
                List<ActiveGuidance> next=new ArrayList<>(targets);
                var previous=next.get(0);
                next.set(0,new ActiveGuidance(previous.questId(),previous.villagerUuid(),
                        new GuidanceTarget(GuidanceKind.PORTAL,OptionalInt.empty(),new BlockPos(cx+240,100,cz+128),
                                Level.OVERWORLD,Component.literal("Covered primary beyond viewport"),3,false,false,0)));
                targets=List.copyOf(next);
            }
            if(tick==330) { capture("03-primary-rim.png");report("primary moved; automatic="+backend.appliedKeys().size()); }
            if(tick==450) { report("after withdrawal="+backend.appliedKeys().size());capture("04-withdrawn.png"); }
            if(tick==480) {
                report("observed viewport="+AtlasHookState.viewportObserved()+" glyph="+AtlasHookState.renderObserved());
                report("observed held viewport="+AtlasHookState.handViewportObserved()+" glyph="+AtlasHookState.handRenderObserved());
                for(Component line:backend.details())report(line.getString());
                report("DONE");closed=true;mc.stop();
            }
        } catch(Exception|LinkageError error) { report("FAIL tick "+tick+" "+error);closed=true;mc.stop(); }
    }
    private static void capture(String name) {
        Minecraft mc=Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->report(message.getString()));
    }
    private static void report(String message) {
        try {
            Path path=Minecraft.getInstance().gameDirectory.toPath().resolve("atlas-fixture-results.txt");
            Files.writeString(path,message+System.lineSeparator(),StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        } catch(Exception error) { throw new IllegalStateException(error); }
    }
}
