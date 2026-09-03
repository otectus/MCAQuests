package dev.otectus.mcaquests.compat.journeymap;

import dev.otectus.mcaquests.client.marker.MarkerColours;
import dev.otectus.mcaquests.compat.ClearCause;
import dev.otectus.mcaquests.compat.MapMutationResult;
import dev.otectus.mcaquests.compat.WaypointSpec;
import dev.otectus.mcaquests.quest.guidance.GuidanceKind;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.display.DisplayType;
import journeymap.api.v2.client.display.Displayable;
import journeymap.api.v2.client.util.UIState;
import journeymap.api.v2.common.Context;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointGroup;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The JourneyMap backend against a hand-written {@link IClientAPI}.
 *
 * <p>A stub rather than a mocking framework because the interesting behaviour is not "was a method
 * called" but "what does the backend believe afterwards" — and the two answers came apart in the
 * defect this class exists to prevent, where the old integration recorded a waypoint as applied
 * because it had made the call, not because the map had taken it.
 *
 * <p>{@link journeymap.api.v2.common.waypoint.WaypointFactory} is unusable here: its static store is
 * installed by JourneyMap's own client bootstrap, so outside a running game it is null. Waypoint
 * construction is therefore the one thing injected; everything else goes through the stub API, which
 * is where the add / read-back / remove decisions are made.
 */
class JourneyMapWaypointBackendTest {

    @Test
    @DisplayName("an automatic waypoint carries its dimension, its colour, and map-only visibility")
    void automaticWaypointIsMapOnlyAndCorrectlyThemed() {
        StubClientApi api = new StubClientApi();
        JourneyMapWaypointBackend backend = backend(api);

        assertEquals(MapMutationResult.APPLIED, backend.apply(spec("q1", GuidanceKind.VILLAGER)));

        StubWaypoint waypoint = api.only();
        assertEquals(Set.of("minecraft:overworld"), waypoint.getDimensions());
        assertEquals("minecraft:overworld", waypoint.getPrimaryDimension());
        assertEquals(MarkerColours.of(GuidanceKind.VILLAGER), waypoint.getColor());
        // The renderer owns the in-world half of this destination. Two icons at one block, in two
        // styles, slightly apart, is what leaving JourneyMap's own defaults on would look like.
        assertTrue(waypoint.showOnMap(), "the point of the waypoint is that it is on the map");
        assertFalse(waypoint.showInWorld(), "the in-world glyph belongs to client/marker");
        assertFalse(waypoint.showBeacon(), "the beam belongs to client/marker too");
    }

    @Test
    @DisplayName("nothing is applied until JourneyMap hands the waypoint back")
    void applyIsNotRecordedUntilTheReadBackSucceeds() {
        StubClientApi api = new StubClientApi();
        api.swallowAdds = true;
        JourneyMapWaypointBackend backend = backend(api);

        assertEquals(MapMutationResult.FAILED, backend.apply(spec("q1", GuidanceKind.VILLAGER)));
        assertEquals(Set.of(), backend.appliedKeys(),
                "a waypoint the map declined must not be remembered as applied, or it is never "
                        + "retried and never appears");

        api.swallowAdds = false;
        assertEquals(MapMutationResult.APPLIED, backend.apply(spec("q1", GuidanceKind.VILLAGER)));
        assertEquals(Set.of("q1"), backend.appliedKeys());
    }

    @Test
    @DisplayName("an unchanged spec costs no call at all")
    void anUnchangedSpecIsUnchanged() {
        StubClientApi api = new StubClientApi();
        JourneyMapWaypointBackend backend = backend(api);
        backend.apply(spec("q1", GuidanceKind.VILLAGER));
        int addsSoFar = api.adds;

        assertEquals(MapMutationResult.UNCHANGED, backend.apply(spec("q1", GuidanceKind.VILLAGER)));
        assertEquals(addsSoFar, api.adds);
    }

    @Test
    @DisplayName("a kind-only change re-applies the waypoint in the new colour")
    void kindOnlyChangeRecoloursTheSameWaypoint() {
        StubClientApi api = new StubClientApi();
        JourneyMapWaypointBackend backend = backend(api);
        backend.apply(spec("q1", GuidanceKind.VILLAGER));

        // The defect: same block, same label, different objective kind. The old comparison ignored
        // kind, so a waypoint kept the wrong colour for as long as the quest lasted.
        assertEquals(MapMutationResult.APPLIED, backend.apply(spec("q1", GuidanceKind.STRUCTURE)));

        assertEquals(1, api.stored.size(), "the waypoint moved kind, it did not become two waypoints");
        assertEquals(MarkerColours.of(GuidanceKind.STRUCTURE), api.only().getColor());
    }

    @Test
    @DisplayName("a dimension change removes the old waypoint and creates a new one")
    void dimensionChangeReplacesRatherThanEdits() {
        StubClientApi api = new StubClientApi();
        JourneyMapWaypointBackend backend = backend(api);
        backend.apply(spec("q1", GuidanceKind.VILLAGER));
        String overworldId = api.only().getId();

        WaypointSpec inNether = new WaypointSpec("q1", new BlockPos(10, 64, 10), Level.NETHER, "Anna",
                GuidanceKind.VILLAGER, WaypointSpec.Ownership.AUTOMATIC);
        assertEquals(MapMutationResult.APPLIED, backend.apply(inNether));

        assertEquals(1, api.stored.size());
        assertFalse(api.stored.containsKey(overworldId),
                "the overworld waypoint is still filed under the overworld; editing it in place "
                        + "leaves a marker on the map the player has left");
        assertEquals("minecraft:the_nether", api.only().getPrimaryDimension());
    }

    @Test
    @DisplayName("a failed removal is retained and withdrawn again")
    void aFailedRemovalIsRetried() {
        StubClientApi api = new StubClientApi();
        JourneyMapWaypointBackend backend = backend(api);
        backend.apply(spec("q1", GuidanceKind.VILLAGER));

        api.failRemovals = true;
        assertEquals(MapMutationResult.FAILED, backend.withdraw("q1"));
        assertEquals(Set.of("q1"), backend.appliedKeys(),
                "a waypoint that could not be removed is still on the map, so it stays in the "
                        + "applied set and the reconciler asks again");

        api.failRemovals = false;
        assertEquals(MapMutationResult.APPLIED, backend.withdraw("q1"));
        assertEquals(Set.of(), backend.appliedKeys());
        assertTrue(api.stored.isEmpty());
    }

    @Test
    @DisplayName("withdrawing a key we never applied is not a failure")
    void withdrawingAnUnknownKeyIsUnchanged() {
        assertEquals(MapMutationResult.UNCHANGED, backend(new StubClientApi()).withdraw("nothing"));
    }

    @Test
    @DisplayName("clearing the automatic waypoints leaves the player's pin alone")
    void clearAutomaticPreservesPins() {
        StubClientApi api = new StubClientApi();
        JourneyMapWaypointBackend backend = backend(api);
        assertEquals(MapMutationResult.APPLIED, backend.pin(
                new WaypointSpec("pin", new BlockPos(1, 2, 3), Level.OVERWORLD, "Home",
                        GuidanceKind.LOCATION, WaypointSpec.Ownership.PIN)));
        StubWaypoint pin = api.only();
        backend.apply(spec("q1", GuidanceKind.VILLAGER));

        backend.clearAutomatic(ClearCause.DIMENSION_CHANGE);

        assertEquals(0, api.removeAllCalls,
                "removeAllWaypoints(modId) is the call that deleted players' saved pins; this "
                        + "backend must never make it");
        assertEquals(List.of(pin.getId()), List.copyOf(api.stored.keySet()));
        assertEquals(Set.of(), backend.appliedKeys());
    }

    @Test
    @DisplayName("a pin is persistent and keeps JourneyMap's own appearance")
    void aPinIsPersistentAndVisibleAsUsual() {
        StubClientApi api = new StubClientApi();
        JourneyMapWaypointBackend backend = backend(api);

        assertEquals(MapMutationResult.APPLIED, backend.pin(
                new WaypointSpec("pin", new BlockPos(1, 2, 3), Level.OVERWORLD, "Home",
                        GuidanceKind.LOCATION, WaypointSpec.Ownership.PIN)));

        StubWaypoint pin = api.only();
        assertTrue(pin.isPersistent(), "the quest log calls this a permanent waypoint");
        assertTrue(pin.showInWorld(), "the player asked for a waypoint, not for a map-only one");
        assertEquals(Set.of(), backend.appliedKeys(), "a pin is never an automatic waypoint");
    }

    @Test
    @DisplayName("the probe adds, reads and removes a waypoint, and leaves nothing behind")
    void theProbeRoundTripsAndCleansUp() {
        StubClientApi api = new StubClientApi();

        List<String> failures = backend(api).probe().stream()
                .filter(step -> !step.passed())
                .map(step -> step.name() + ' ' + step.detail().orElse(""))
                .toList();

        assertEquals(List.of(), failures);
        assertTrue(api.stored.isEmpty(), "the probe writes, so it must also tidy up after itself");
    }

    private static JourneyMapWaypointBackend backend(StubClientApi api) {
        return new JourneyMapWaypointBackend(api,
                (modId, pos, name, dimension, persistent) ->
                        new StubWaypoint(modId, pos, name, dimension, persistent));
    }

    private static WaypointSpec spec(String key, GuidanceKind kind) {
        return new WaypointSpec(key, new BlockPos(10, 64, 10), Level.OVERWORLD, "Anna", kind,
                WaypointSpec.Ownership.AUTOMATIC);
    }

    /**
     * As much of JourneyMap's waypoint store as this backend touches, and no more.
     *
     * <p>{@code swallowAdds} is the case the whole read-back exists for: JourneyMap accepting a call
     * and doing nothing, which it can do without throwing and without saying so.
     */
    private static final class StubClientApi implements IClientAPI {

        final Map<String, StubWaypoint> stored = new LinkedHashMap<>();

        boolean swallowAdds;
        boolean failRemovals;
        int adds;
        int removeAllCalls;

        StubWaypoint only() {
            assertEquals(1, stored.size(), "expected exactly one waypoint on the map");
            return stored.values().iterator().next();
        }

        @Override
        public void addWaypoint(String modId, Waypoint waypoint) {
            adds++;
            if (swallowAdds) {
                return;
            }
            stored.put(waypoint.getId(), (StubWaypoint) waypoint);
        }

        @Override
        public void removeWaypoint(String modId, Waypoint waypoint) {
            if (failRemovals) {
                throw new IllegalStateException("stub refuses to remove");
            }
            stored.remove(waypoint.getId());
        }

        @Override
        @Nullable
        public Waypoint getWaypoint(String modId, String waypointId) {
            return stored.get(waypointId);
        }

        @Override
        public void removeAllWaypoints(String modId) {
            removeAllCalls++;
            stored.clear();
        }

        @Override
        public List<? extends Waypoint> getAllWaypoints() {
            return List.copyOf(stored.values());
        }

        @Override
        public List<? extends Waypoint> getAllWaypoints(ResourceKey<Level> dimension) {
            return getAllWaypoints();
        }

        @Override
        public List<? extends Waypoint> getWaypoints(String modId) {
            return getAllWaypoints();
        }

        // Everything below is API surface this backend never touches.

        @Override
        public UIState getUIState(Context.UI ui) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void show(Displayable displayable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void remove(Displayable displayable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeAll(String modId, DisplayType displayType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeAll(String modId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean exists(Displayable displayable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean playerAccepts(String modId, DisplayType displayType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void requestMapTile(String modId, ResourceKey<Level> dimension, Context.MapType mapType,
                                   ChunkPos start, ChunkPos end, Integer chunkY, int zoom,
                                   boolean showGrid, Consumer<com.mojang.blaze3d.platform.NativeImage> callback) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void disableFeature(ResourceKey<Level> dimension, Context.MapType mapType, boolean off) {
            throw new UnsupportedOperationException();
        }

        @Override
        public File getDataPath(String modId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addWaypointGroup(WaypointGroup group) {
            throw new UnsupportedOperationException();
        }

        @Override
        public WaypointGroup getWaypointGroup(String groupId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public WaypointGroup getWaypointGroupByName(String modId, String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<? extends WaypointGroup> getWaypointGroups(String modId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<? extends WaypointGroup> getAllWaypointGroups() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeWaypointGroup(WaypointGroup group, boolean removeWaypoints) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeWaypointGroups(String modId, boolean removeWaypoints) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getWorldId() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void toggleMinimap(boolean enabled) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean minimapEnabled() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * A JourneyMap waypoint as a plain mutable bag.
     *
     * <p>The three visibility flags are {@code default} methods on the interface that ignore their
     * argument and answer {@code true} — the real implementation overrides them, and so must this, or
     * the assertions about map-only visibility would pass without the backend setting anything.
     */
    private static final class StubWaypoint implements Waypoint {

        private static int nextId;

        private final String id = "stub-" + nextId++;
        private final String modId;

        private String name;
        private BlockPos pos;
        private String primaryDimension;
        private final TreeSet<String> dimensions = new TreeSet<>();
        private final Map<String, String> customData = new HashMap<>();
        private boolean persistent;
        private boolean enabled = true;
        private boolean showDeviation;
        private boolean showOnMap = true;
        private boolean showInWorld = true;
        private boolean showBeacon = true;
        private int red;
        private int green;
        private int blue;
        private int iconRotation;
        @Nullable
        private Integer iconColor;
        private float iconOpacity = 1.0F;
        @Nullable
        private ResourceLocation iconIdentifier;
        private int iconTextureWidth;
        private int iconTextureHeight;

        StubWaypoint(String modId, BlockPos pos, String name, String dimension, boolean persistent) {
            this.modId = modId;
            this.pos = pos;
            this.name = name;
            this.persistent = persistent;
            this.primaryDimension = dimension;
            this.dimensions.add(dimension);
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getGuid() {
            return id;
        }

        @Override
        public String getGroupId() {
            return modId;
        }

        @Override
        public String getModId() {
            return modId;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void setName(String name) {
            this.name = name;
        }

        @Override
        public void setPos(int x, int y, int z) {
            pos = new BlockPos(x, y, z);
        }

        @Override
        public void setBlockPos(BlockPos pos) {
            this.pos = pos;
        }

        @Override
        public BlockPos getBlockPos() {
            return pos;
        }

        @Override
        public int getX() {
            return pos.getX();
        }

        @Override
        public void setX(int x) {
            pos = new BlockPos(x, pos.getY(), pos.getZ());
        }

        @Override
        public int getY() {
            return pos.getY();
        }

        @Override
        public void setY(int y) {
            pos = new BlockPos(pos.getX(), y, pos.getZ());
        }

        @Override
        public int getZ() {
            return pos.getZ();
        }

        @Override
        public void setZ(int z) {
            pos = new BlockPos(pos.getX(), pos.getY(), z);
        }

        @Override
        public int getRed() {
            return red;
        }

        @Override
        public void setRed(int red) {
            this.red = red;
        }

        @Override
        public int getGreen() {
            return green;
        }

        @Override
        public void setGreen(int green) {
            this.green = green;
        }

        @Override
        public int getBlue() {
            return blue;
        }

        @Override
        public void setBlue(int blue) {
            this.blue = blue;
        }

        @Override
        public int getColor() {
            return (red << 16) | (green << 8) | blue;
        }

        @Override
        public void setColor(int color) {
            red = (color >> 16) & 0xFF;
            green = (color >> 8) & 0xFF;
            blue = color & 0xFF;
        }

        @Override
        public TreeSet<String> getDimensions() {
            return dimensions;
        }

        @Override
        public void setDimensions(Collection<String> dimensions) {
            this.dimensions.clear();
            this.dimensions.addAll(dimensions);
        }

        @Override
        public void setPrimaryDimension(String dimension) {
            primaryDimension = dimension;
        }

        @Override
        public String getPrimaryDimension() {
            return primaryDimension;
        }

        @Override
        public boolean isPersistent() {
            return persistent;
        }

        @Override
        public void setPersistent(boolean persistent) {
            this.persistent = persistent;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public boolean showDeviation() {
            return showDeviation;
        }

        @Override
        public void setShowDeviation(boolean showDeviation) {
            this.showDeviation = showDeviation;
        }

        @Override
        public boolean showOnMap() {
            return showOnMap;
        }

        @Override
        public void setShowOnMap(boolean showOnMap) {
            this.showOnMap = showOnMap;
        }

        @Override
        public boolean showInWorld() {
            return showInWorld;
        }

        @Override
        public void setShowInWorld(boolean showInWorld) {
            this.showInWorld = showInWorld;
        }

        @Override
        public boolean showBeacon() {
            return showBeacon;
        }

        @Override
        public void setShowBeacon(boolean showBeacon) {
            this.showBeacon = showBeacon;
        }

        @Override
        public int getIconRotation() {
            return iconRotation;
        }

        @Override
        public void setIconRotation(int iconRotation) {
            this.iconRotation = iconRotation;
        }

        @Override
        @Nullable
        public Integer getIconColor() {
            return iconColor;
        }

        @Override
        public void setIconColor(Integer iconColor) {
            this.iconColor = iconColor;
        }

        @Override
        public float getIconOpacity() {
            return iconOpacity;
        }

        @Override
        public void setIconOpacity(float iconOpacity) {
            this.iconOpacity = iconOpacity;
        }

        @Override
        @Nullable
        public ResourceLocation getIconIdentifier() {
            return iconIdentifier;
        }

        @Override
        public void setIconIdentifier(ResourceLocation iconIdentifier) {
            this.iconIdentifier = iconIdentifier;
        }

        @Override
        public int getIconTextureWidth() {
            return iconTextureWidth;
        }

        @Override
        public void setIconTextureWidth(Integer width) {
            iconTextureWidth = width == null ? 0 : width;
        }

        @Override
        public int getIconTextureHeight() {
            return iconTextureHeight;
        }

        @Override
        public void setIconTextureHeight(Integer height) {
            iconTextureHeight = height == null ? 0 : height;
        }

        @Override
        public void setCustomData(String key, String value) {
            customData.put(key, value);
        }

        @Override
        @Nullable
        public String getCustomData(String key) {
            return customData.get(key);
        }
    }
}
