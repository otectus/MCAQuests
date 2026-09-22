package dev.otectus.mcaquests.compat;

import com.google.gson.JsonObject;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.quest.kingdom.CivicBuildingSpec;
import dev.otectus.mcaquests.quest.kingdom.KingdomGateSpec;
import dev.otectus.mcaquests.state.CivicBuildingBinding;
import dev.otectus.mcaquests.state.KingdomBindingSnapshot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reflection-only access to Ultima's stable gating, factions, and Townstead facades.
 *
 * <p>Every class and method is looked up once and kept, including the answer "not there": a gate is
 * asked on every offer pass, and a failed {@code Class.forName} on an installation without Ultima costs
 * an exception each time. Ultima's classes cannot change while the game runs, so nothing expires.
 */
public final class KingdomIntegration {
    /** The loader id Ultima Kingdoms registers under. */
    public static final String MOD_ID = "ultima_kingdoms";

    /** Capabilities content can require, by the facade that answers them (see {@link #has}). */
    public static final String GATING = "gating";
    public static final String FACTIONS = "factions";
    public static final String CIVIC_BUILDINGS = "civic_buildings";
    public static final String INSTITUTIONS = "institutions";

    private static final String GATE_API = "com.ultimakingdoms.api.gating.KingdomGateApi";
    private static final String KINGDOM_CONTEXT = "com.ultimakingdoms.api.gating.KingdomContext";
    private static final String FACTIONS_API = "com.ultimakingdoms.api.factions.UltimaFactionsApi";
    private static final String FACTIONS_SERVICE = "com.ultimakingdoms.api.factions.UltimaFactionsService";
    private static final String STANDING_SCOPE = "com.ultimakingdoms.api.factions.StandingScope";
    private static final String COMMUNITY_REF = "com.ultimakingdoms.api.McaCommunityRef";
    private static final String STANDING_REQUEST = "com.ultimakingdoms.api.factions.FactionStandingRequest";
    private static final String CHANGE_CAUSE = "com.ultimakingdoms.api.factions.FactionChangeCause";
    private static final String TOWNSTEAD_API = "com.ultimakingdoms.api.townstead.UltimaTownsteadApi";
    private static final String TOWNSTEAD_SERVICE = "com.ultimakingdoms.api.townstead.TownsteadService";
    private static final String BOUND_BUILDING = "com.ultimakingdoms.api.townstead.BoundCivicBuilding";
    private static final String RECOVERY_POLICY = "com.ultimakingdoms.api.townstead.BuildingRecoveryPolicy";
    private static final AtomicBoolean FAILURE_REPORTED = new AtomicBoolean();
    private static final Map<String, Optional<Class<?>>> CLASSES = new ConcurrentHashMap<>();
    private static final Map<String, Method> METHODS = new ConcurrentHashMap<>();

    private KingdomIntegration() {
    }

    /** Ultima is installed and its gating facade is present. */
    public static boolean available() {
        return net.minecraftforge.fml.ModList.get() != null
                && net.minecraftforge.fml.ModList.get().isLoaded(MOD_ID)
                && present(GATE_API);
    }

    /**
     * Whether one facade of an installed Ultima is present. Used by the loaders to decide whether
     * kingdom content can be played here at all; whether a particular player passes a gate is the offer
     * pipeline's question.
     */
    public static boolean has(String capability) {
        if (!available()) return false;
        return switch (capability) {
            case GATING -> true;
            case FACTIONS -> present(FACTIONS_API) && present(FACTIONS_SERVICE);
            case CIVIC_BUILDINGS -> present(TOWNSTEAD_API) && present(TOWNSTEAD_SERVICE);
            case INSTITUTIONS -> dev.otectus.mcaquests.quest.InstitutionalCommissionBridge.serviceAvailable();
            default -> false;
        };
    }

    private static boolean present(String name) {
        return CLASSES.computeIfAbsent(name, KingdomIntegration::find).isPresent();
    }

    private static Optional<Class<?>> find(String name) {
        try {
            return Optional.of(Class.forName(name, false, KingdomIntegration.class.getClassLoader()));
        } catch (Throwable absent) {
            return Optional.empty();
        }
    }

    public static boolean allows(KingdomGateSpec spec, ServerPlayer player, Entity giver) {
        try {
            Class<?> api = load(GATE_API);
            if (spec.namedGate().isPresent()) {
                return (boolean) method(api, "testNamed", ServerPlayer.class, Entity.class,
                                ResourceLocation.class, Optional.class)
                        .invoke(null, player, giver, spec.namedGate().get(), spec.explicitSettlementId());
            }
            return (boolean) method(api, "testJson", ServerPlayer.class, Entity.class, String.class, Optional.class)
                    .invoke(null, player, giver, spec.predicateJson().toString(), spec.explicitSettlementId());
        } catch (ClassNotFoundException unavailable) {
            return spec.allowsWhenUnavailable();
        } catch (Throwable failure) {
            report(failure);
            return false;
        }
    }

    public static Optional<KingdomBindingSnapshot> capture(String subject, Optional<UUID> settlement,
                                                            ServerPlayer player, Entity giver) {
        try {
            Object context = method(load(GATE_API), "resolveSnapshot", ServerPlayer.class, Entity.class,
                            String.class, Optional.class)
                    .invoke(null, player, giver, subject, settlement);
            if (!(context instanceof Optional<?> optional) || optional.isEmpty()) return Optional.empty();
            Object value = optional.get();
            UUID settlementId = (UUID) accessor(value, "settlementId");
            Optional<ResourceLocation> localDimension = Optional.empty();
            OptionalInt localVillage = OptionalInt.empty();
            try {
                Object community = method(load(GATE_API), "resolveMcaCommunity", ServerPlayer.class, UUID.class)
                        .invoke(null, player, settlementId);
                if (community instanceof Optional<?> local && local.isPresent()) {
                    localDimension = Optional.of((ResourceLocation) accessor(local.get(), "dimension"));
                    localVillage = OptionalInt.of((int) accessor(local.get(), "villageId"));
                }
            } catch (NoSuchMethodException ignored) {
                // Older Ultima API: political binding remains usable; local standing is unavailable.
            }
            return Optional.of(new KingdomBindingSnapshot(
                    settlementId,
                    (ResourceLocation) accessor(value, "kingdomId"),
                    (long) accessor(value, "settlementRevision"),
                    (ResourceLocation) accessor(value, "dimension"), localDimension, localVillage));
        } catch (Throwable failure) {
            if (!(unwrap(failure) instanceof ClassNotFoundException)) report(failure);
            return Optional.empty();
        }
    }

    public static boolean allowsBound(KingdomGateSpec spec, KingdomBindingSnapshot snapshot,
                                      ServerPlayer player) {
        try {
            Class<?> contextType = load(KINGDOM_CONTEXT);
            Object context = contextType.getConstructor(UUID.class, ResourceLocation.class,
                    ResourceLocation.class, long.class).newInstance(snapshot.settlementId(), snapshot.kingdomId(),
                    snapshot.dimension(), snapshot.settlementRevision());
            Class<?> api = load(GATE_API);
            if (spec.namedGate().isPresent()) {
                return (boolean) method(api, "testNamedAgainst", ServerPlayer.class, ResourceLocation.class,
                                contextType)
                        .invoke(null, player, spec.namedGate().get(), context);
            }
            return (boolean) method(api, "testJsonAgainst", String.class, contextType)
                    .invoke(null, spec.predicateJson().toString(), context);
        } catch (Throwable failure) {
            report(failure);
            return false;
        }
    }

    public static boolean standingAllows(KingdomGateSpec.StandingGate gate, KingdomBindingSnapshot context,
                                         ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        try {
            Object service = method(load(FACTIONS_API), "get", MinecraftServer.class).invoke(null, server);
            ResourceLocation kingdom = gate.kingdom().orElse(context.kingdomId());
            OptionalInt local = OptionalInt.empty();
            if (gate.scope() != KingdomGateSpec.StandingScope.FACTION
                    && context.localDimension().isPresent() && context.localVillageId().isPresent()) {
                Object community = load(COMMUNITY_REF).getConstructor(ResourceLocation.class, int.class)
                        .newInstance(context.localDimension().get(), context.localVillageId().getAsInt());
                Object value = invokePublic(service, FACTIONS_SERVICE, "getLocalStanding",
                        new Class<?>[]{UUID.class, load(COMMUNITY_REF)}, player.getUUID(), community);
                if (value instanceof OptionalInt score) local = score;
            }
            Class<?> scopeType = load(STANDING_SCOPE);
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object scope = Enum.valueOf((Class<? extends Enum>) scopeType.asSubclass(Enum.class), gate.scope().name());
            return (boolean) invokePublic(service, FACTIONS_SERVICE, "matches",
                    new Class<?>[]{scopeType, UUID.class, ResourceLocation.class, OptionalInt.class,
                            int.class, int.class}, scope, player.getUUID(), kingdom, local,
                    gate.minimum(), gate.maximum());
        } catch (Throwable failure) {
            report(failure);
            return false;
        }
    }

    public static Optional<CivicBuildingBinding> bindBuilding(CivicBuildingSpec spec, ServerPlayer player,
                                                               Entity giver) {
        MinecraftServer server = player.getServer();
        Entity target = spec.target() == CivicBuildingSpec.Target.PLAYER ? player : giver;
        if (server == null || !(target.level() instanceof ServerLevel level)) return Optional.empty();
        try {
            Object service = method(load(TOWNSTEAD_API), "get", MinecraftServer.class).invoke(null, server);
            Object found = invokePublic(service, TOWNSTEAD_SERVICE, "buildingAt",
                    new Class<?>[]{ServerLevel.class, net.minecraft.core.BlockPos.class},
                    level, target.blockPosition());
            if (!(found instanceof Optional<?> optional) || optional.isEmpty()) return Optional.empty();
            Object bound = invokePublic(service, TOWNSTEAD_SERVICE, "bind",
                    new Class<?>[]{load("com.ultimakingdoms.api.townstead.TownsteadBuildingView")},
                    optional.get());
            return Optional.of(readBuilding(bound));
        } catch (Throwable failure) {
            report(failure);
            return Optional.empty();
        }
    }

    public enum RecoveryStatus { FOUND, WAITING, REBOUND, FAILED, UNAVAILABLE }
    public record Recovery(RecoveryStatus status, Optional<CivicBuildingBinding> binding, String reason) { }

    public static Recovery recoverBuilding(CivicBuildingBinding binding, CivicBuildingSpec spec,
                                            ServerPlayer player) {
        MinecraftServer server = player.getServer();
        ServerLevel level = server == null ? null : server.getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, binding.dimension()));
        if (server == null || level == null) return new Recovery(RecoveryStatus.WAITING, Optional.of(binding), "dimension unavailable");
        try {
            Object service = method(load(TOWNSTEAD_API), "get", MinecraftServer.class).invoke(null, server);
            Class<?> boundType = load(BOUND_BUILDING);
            Object bound = boundType.getConstructor(UUID.class, UUID.class, ResourceLocation.class, int.class,
                            int.class, String.class, String.class)
                    .newInstance(binding.bindingId(), binding.settlementId(), binding.dimension(), binding.villageId(),
                            binding.buildingId(), binding.family(), binding.typeAtBinding());
            Class<?> policyType = load(RECOVERY_POLICY);
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object policy = Enum.valueOf((Class<? extends Enum>) policyType.asSubclass(Enum.class), spec.recovery().name());
            Object result = invokePublic(service, TOWNSTEAD_SERVICE, "recover",
                    new Class<?>[]{ServerLevel.class, boundType, policyType}, level, bound, policy);
            RecoveryStatus status = RecoveryStatus.valueOf(accessor(result, "status").toString());
            Object recovered = accessor(result, "binding");
            Optional<CivicBuildingBinding> updated = recovered instanceof Optional<?> optional && optional.isPresent()
                    ? Optional.of(readBuilding(optional.get())) : Optional.empty();
            return new Recovery(status, updated, String.valueOf(accessor(result, "reason")));
        } catch (ClassNotFoundException unavailable) {
            return new Recovery(RecoveryStatus.UNAVAILABLE, Optional.of(binding), "Ultima Townstead API unavailable");
        } catch (Throwable failure) {
            report(failure);
            return new Recovery(RecoveryStatus.UNAVAILABLE, Optional.of(binding), "Ultima Townstead API failed");
        }
    }

    public static boolean grantFactionStanding(ServerPlayer player, ResourceLocation kingdom, int delta,
                                                ResourceLocation source, UUID operationId, long sourceRevision,
                                                Optional<UUID> settlementId, Optional<String> description,
                                                boolean quiet) {
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        try {
            Object service = method(load(FACTIONS_API), "get", MinecraftServer.class).invoke(null, server);
            Class<?> causeType = load(CHANGE_CAUSE);
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object cause = Enum.valueOf((Class<? extends Enum>) causeType.asSubclass(Enum.class), "QUEST");
            Class<?> requestType = load(STANDING_REQUEST);
            Constructor<?> ctor = requestType.getConstructor(UUID.class, ResourceLocation.class, int.class,
                    ResourceLocation.class, causeType, UUID.class, long.class, Optional.class, Optional.class,
                    boolean.class);
            Object request = ctor.newInstance(player.getUUID(), kingdom, delta, source, cause, operationId,
                    Math.max(0L, sourceRevision), settlementId, description, quiet);
            Object result = invokePublic(service, FACTIONS_SERVICE, "apply",
                    new Class<?>[]{requestType}, request);
            String status = String.valueOf(accessor(result, "status"));
            if (!"APPLIED".equals(status) && !"REPLAYED".equals(status)) return false;
            Object flushed = invokePublic(service, FACTIONS_SERVICE, "flushStandingChanges",
                    new Class<?>[0]);
            return !(flushed instanceof Boolean success) || success;
        } catch (Throwable failure) {
            report(failure);
            return false;
        }
    }

    public static UUID rewardOperation(UUID instance, ResourceLocation quest, int rewardIndex) {
        return UUID.nameUUIDFromBytes((instance + "|" + quest + "|" + rewardIndex)
                .getBytes(StandardCharsets.UTF_8));
    }

    private static CivicBuildingBinding readBuilding(Object value) throws ReflectiveOperationException {
        return new CivicBuildingBinding((UUID) accessor(value, "bindingId"),
                (UUID) accessor(value, "settlementId"), (ResourceLocation) accessor(value, "dimension"),
                (int) accessor(value, "villageId"), (int) accessor(value, "buildingId"),
                String.valueOf(accessor(value, "family")), String.valueOf(accessor(value, "typeAtBinding")));
    }

    private static Object accessor(Object target, String name) throws ReflectiveOperationException {
        return method(target.getClass(), name).invoke(target);
    }

    static Object invokePublic(Object target, String interfaceName, String method,
                               Class<?>[] parameterTypes, Object... arguments)
            throws ReflectiveOperationException {
        return method(load(interfaceName), method, parameterTypes).invoke(target, arguments);
    }

    private static Class<?> load(String name) throws ClassNotFoundException {
        return CLASSES.computeIfAbsent(name, KingdomIntegration::find)
                .orElseThrow(() -> new ClassNotFoundException(name));
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        String key = owner.getName() + '#' + name + Arrays.toString(parameterTypes);
        Method found = METHODS.get(key);
        if (found == null) {
            found = owner.getMethod(name, parameterTypes);
            METHODS.put(key, found);
        }
        return found;
    }

    private static Throwable unwrap(Throwable throwable) {
        while (throwable instanceof java.lang.reflect.InvocationTargetException invocation
                && invocation.getCause() != null) throwable = invocation.getCause();
        return throwable;
    }

    private static void report(Throwable failure) {
        if (FAILURE_REPORTED.compareAndSet(false, true)) {
            McaQuests.LOGGER.error("[MCA: Quests] Optional Ultima integration failed closed", unwrap(failure));
        }
    }
}
