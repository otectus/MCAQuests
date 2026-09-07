package dev.otectus.mcaquests.compat.mapatlases.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.otectus.mcaquests.compat.mapatlases.AtlasHookManifest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Supplier;

/** Version-specific native members, resolved once. No third-party types in JVM signatures. */
public final class AtlasNativeBinding {
    private static final String ROOT = "pepjebs.mapatlases.";
    private final Class<?> screenType = type("client.screen.AtlasOverviewScreen");
    private final Class<?> hudType = type("client.ui.MapAtlasesHUD");
    private final Class<?> holderType = type("utils.MapDataHolder");
    private final Class<?> sliceType = type("utils.Slice");
    private final Class<?> clientType = type("client.MapAtlasesClient");
    private final Class<?> collectionType = type("map_collection.MapCollection");
    private final Field data = field(holderType, "data"), height = field(holderType, "height"),
            mapType = field(holderType, "type"), slice = field(holderType, "slice"), id = field(holderType, "id");
    private final Field screenMaps = field(screenType, "currentMaps"), screenWidget = field(screenType, "mapWidget"),
            widgetScreen = field(type("client.screen.MapWidget"), "mapScreen"), hudMaps = field(hudType, "currentMaps");
    private final Field activeHolder = field(typeAbsolute(AtlasHookManifest.TARGET), "mapWherePlayerIs");
    private final Method all = method(collectionType, "getAllFound"),
            getActiveAtlas = method(clientType, "getCurrentActiveAtlas"),
            getActiveMap = method(clientType, "getActiveMap"),
            getAtlas = method(screenType, "getAtlas"),
            getMaps = method(type("item.MapAtlasItem"), "getMaps", ItemStack.class, Level.class),
            getSelected = method(screenType, "getSelectedSlice"),
            selectedForAtlas = method(type("item.MapAtlasItem"), "getSelectedSlice", ItemStack.class, ResourceKey.class),
            selectDimension = method(screenType, "selectDimension", ResourceKey.class),
            updateSlice = method(screenType, "updateSlice", sliceType),
            center = method(type("client.screen.MapWidget"), "resetAndCenter", int.class, int.class, boolean.class, boolean.class),
            iconTransform = method(clientType, "modifyDecorationTransform", PoseStack.class),

            placing = method(screenType, "isPlacingPin"), shearing = method(screenType, "isShearing"),
            editing = method(screenType, "isEditingText");
    private Constructor<?> openPacket;
    private Method send, addPin, pinType;
    private Field nativePins;
    private Object channel;
    private String navigationFailure = "", pinsFailure = "";

    public AtlasNativeBinding() {
        try {
            Class<?> packet = type("networking.C2S2COpenAtlasScreenPacket");
            openPacket = packet.getConstructor();
            channel = null;
            send = typeAbsolute("net.mehvahdjukaar.moonlight.api.platform.network.NetworkHelper").getMethod("sendToServer", net.minecraft.network.protocol.common.custom.CustomPacketPayload.class);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            navigationFailure = error.getClass().getSimpleName();
        }
        try {
            Class<?> pins = type("integration.moonlight.ClientMarkers");
            addPin = method(pins, "placePin", holderType, ColumnPos.class, String.class, int.class);
            pinType = method(pins, "getPinWithIndex", int.class);
            nativePins = field(pins, "MARKERS_PER_MAP");
        } catch (LinkageError | RuntimeException error) { pinsFailure = error.getClass().getSimpleName(); }
    }
    public record Tile(Object nativeHolder, MapItemSavedData data, Integer height, String type, Object slice,
                       boolean allowsMarkers) { }
    public Tile tile(Object holder) {
        Object type = read(mapType, holder);
        return new Tile(holder, (MapItemSavedData) read(data, holder), (Integer) read(height, holder),
                ((Enum<?>) type).name(), read(slice, holder), !((Enum<?>) type).name().equals("MAGIC"));
    }
    public boolean isScreen(Object object) { return screenType.isInstance(object); }
    public boolean isHud(Object object) { return hudType.isInstance(object); }
    public boolean busy(Screen screen) {
        return isScreen(screen) && ((boolean) invoke(placing, screen) || (boolean) invoke(shearing, screen)
                || (boolean) invoke(editing, screen) || screen.isDragging());
    }
    public ItemStack activeAtlas() { return (ItemStack) invoke(getActiveAtlas, null); }
    public Optional<Tile> heldTile(ItemStack stack) {
        Object active = invoke(getActiveMap, null);
        if (active == null || stack.isEmpty() || Minecraft.getInstance().level == null) return Optional.empty();
        Object mapId = read(id, active);
        // Native rendering uses its active holder. Require that exact map in the item being rendered.
        return maps(null, stack).stream().filter(holder -> mapId.equals(read(id, holder)))
                .findFirst().map(ignored -> tile(active));
    }
    public ItemStack atlas(Screen screen) {
        return isScreen(screen) ? (ItemStack) invoke(getAtlas, screen) : activeAtlas();
    }
    public Object selected(Screen screen, ItemStack atlas, ResourceKey<Level> dimension) {
        return isScreen(screen) ? invoke(getSelected, screen) : invoke(selectedForAtlas, null, atlas, dimension);
    }
    @SuppressWarnings("unchecked")
    public List<Object> maps(Screen screen, ItemStack atlas) {
        Object collection = isScreen(screen) ? read(screenMaps, screen)
                : invoke(getMaps, null, atlas, Minecraft.getInstance().level);
        return List.copyOf((Collection<Object>) invoke(all, collection));
    }
    @SuppressWarnings("unchecked")
    public List<Object> surfaceMaps(Object widget) {
        Object collection = isHud(widget) ? read(hudMaps, widget) : read(screenMaps, read(widgetScreen, widget));
        return List.copyOf((Collection<Object>) invoke(all, collection));
    }
    public Object surfaceSlice(Object widget) {
        return isHud(widget) ? read(slice, read(activeHolder, widget)) : invoke(getSelected, read(widgetScreen, widget));
    }
    public void transformIcon(PoseStack pose) { invoke(iconTransform, null, pose); }
    public boolean coordinates() { return setting("MapAtlasesClientConfig", "drawWorldMapCoords"); }
    public boolean pinsSupported() { return pinsFailure.isEmpty() && addPin != null && nativePins != null; }
    public boolean navigationSupported() { return navigationFailure.isEmpty() && send != null; }
    public String pinsFailure() { return pinsFailure; }
    public boolean pinsEnabled() {
        return pinsSupported() && setting("MapAtlasesClientConfig", "moonlightCompat")
                && !((Supplier<?>) read(field(type("config.MapAtlasesConfig"), "pinMarkerId"), null)).get().toString().isEmpty();
    }
    public boolean pinStyleAvailable() {
        try { return invoke(pinType, null, 0) != null; }
        catch (RuntimeException | LinkageError error) { return false; }
    }
    public void requestOpen() { invoke(send, channel, construct(openPacket)); }
    public void focus(Screen screen, Tile tile, int x, int z) {
        invoke(selectDimension, screen, tile.data.dimension);
        // Only an existing eligible slice is selected, through the native flow (lectern included).
        if (!tile.slice.equals(invoke(getSelected, screen))) invoke(updateSlice, screen, tile.slice);
        invoke(center, read(screenWidget, screen), x, z, false, true);
    }
    @SuppressWarnings("unchecked")
    public boolean savePin(Tile tile, int x, int z, String label) {
        Map<Object, Set<?>> store = (Map<Object, Set<?>>) read(nativePins, null);
        Object mapId = read(id, tile.nativeHolder);
        Set<?> before = new HashSet<>(store.getOrDefault(mapId, Set.of()));
        invoke(addPin, null, tile.nativeHolder, new ColumnPos(x, z), label, 0);
        Set<?> after = store.getOrDefault(mapId, Set.of());
        // The native store may coalesce identical points. Report acceptance only with an observed insertion.
        return after.stream().anyMatch(marker -> !before.contains(marker));
    }
    private static boolean setting(String owner, String name) {
        return Boolean.TRUE.equals(((Supplier<?>) read(field(type("config." + owner), name), null)).get());
    }
    private static Class<?> type(String name) { return typeAbsolute(ROOT + name); }
    private static Class<?> typeAbsolute(String name) {
        try { return Class.forName(name); }
        catch (ClassNotFoundException error) { throw new IllegalStateException(name, error); }
    }
    private static Field field(Class<?> type, String name) {
        try { Field f = type.getDeclaredField(name); f.setAccessible(true); return f; }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(type.getName() + "." + name, error); }
    }
    private static Method method(Class<?> type, String name, Class<?>... params) {
        try { Method m = type.getDeclaredMethod(name, params); m.setAccessible(true); return m; }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(type.getName() + "." + name, error); }
    }
    private static Object read(Field field, Object owner) {
        try { return field.get(owner); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    private static Object invoke(Method method, Object owner, Object... args) {
        try { return method.invoke(owner, args); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(method.getName(), error); }
    }
    private static Object construct(Constructor<?> constructor) {
        try { return constructor.newInstance(); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
}
