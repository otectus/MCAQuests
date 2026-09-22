package dev.otectus.mcaquests.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * One reputation outcome Quests wants recorded, in Minecraft and Java types only (see
 * {@link ReputationBackend} for why that matters).
 *
 * <p>{@link #dedupeKey} is the field that makes every integration path safe. §14.2 recommends the
 * shapes; {@link ReputationDedupe} builds them so no two call sites can spell the same logical
 * outcome differently.
 *
 * <p>{@link #incidentType} names the deed for the canonical backend's ledger. The legacy backend has
 * no ledger and ignores it, applying only the delta — which is precisely the pre-1.1.0 behaviour, so
 * a Quests-only install is unchanged.
 *
 * <h2>An omitted delta is not a zero</h2>
 *
 * <p>{@link #delta} is an {@link OptionalInt} from 1.7.0, because the two states a plain {@code int}
 * conflated mean opposite things to MCA: Reputation. <b>Empty</b> means "this deed is worth whatever
 * its incident definition says", which is what a pack author naming only an incident asked for.
 * <b>Present and zero</b> means "record the deed, move no standing" — a real instruction, and the one
 * that carries profile evidence without a number attached. The old field silently turned the first
 * into the second, so every unpriced deed was recorded as an explicit zero override and no datapack
 * could ever set an incident's own value (spec §16.1, audit R15).
 *
 * <p>{@link #incidentProfile} is the authored {@code incident_profile} selection for a generic deed
 * (0.6.0 profiles, spec §9.5). It is a bare {@link ResourceLocation} rather than a Reputation type,
 * exactly like {@link #incidentType}: an id naming content from a mod that is not installed is just a
 * string, while a <em>type</em> from one is a crash on a Quests-only install.
 */
public record ReputationAward(
        MinecraftServer server,
        UUID player,
        ResourceLocation dimension,
        int villageId,
        OptionalInt delta,
        @Nullable ResourceLocation incidentType,
        ResourceLocation source,
        @Nullable String dedupeKey,
        @Nullable String visibility,
        List<String> tags,
        Map<String, String> context,
        @Nullable UUID subjectUuid,
        @Nullable String subjectName,
        @Nullable String subjectRole,
        @Nullable ResourceLocation incidentProfile) {

    public ReputationAward {
        delta = delta == null ? OptionalInt.empty() : delta;
        tags = tags == null ? List.of() : List.copyOf(tags);
        context = context == null ? Map.of() : Map.copyOf(context);
    }

    /**
     * The stated delta, or {@code 0} when none was stated.
     *
     * <p>For the legacy backend and for "would this do anything" tests only. The canonical backend
     * must pass {@link #delta} through unchanged, or it reintroduces the conflation this record now
     * keeps apart.
     */
    public int deltaOrZero() {
        return delta.orElse(0);
    }

    /** True when this award would neither move standing nor name a deed. */
    public boolean isNoOp() {
        return incidentType == null && deltaOrZero() == 0;
    }

    public static Builder builder(MinecraftServer server, UUID player, ResourceLocation dimension,
                                  int villageId, ResourceLocation source) {
        return new Builder(server, player, dimension, villageId, source);
    }

    public static final class Builder {

        private final MinecraftServer server;
        private final UUID player;
        private final ResourceLocation dimension;
        private final int villageId;
        private final ResourceLocation source;

        private OptionalInt delta = OptionalInt.empty();
        @Nullable private ResourceLocation incidentType;
        @Nullable private ResourceLocation incidentProfile;
        @Nullable private String dedupeKey;
        @Nullable private String visibility;
        private List<String> tags = List.of();
        private final Map<String, String> context = new java.util.LinkedHashMap<>();
        @Nullable private UUID subjectUuid;
        @Nullable private String subjectName;
        @Nullable private String subjectRole;

        private Builder(MinecraftServer server, UUID player, ResourceLocation dimension, int villageId,
                        ResourceLocation source) {
            this.server = server;
            this.player = player;
            this.dimension = dimension;
            this.villageId = villageId;
            this.source = source;
        }

        /** States an explicit delta. A stated zero is an instruction, not an absence. */
        public Builder delta(int value) {
            this.delta = OptionalInt.of(value);
            return this;
        }

        /** States the delta only if the author wrote one; empty leaves the incident's own value. */
        public Builder delta(@Nullable OptionalInt value) {
            this.delta = value == null ? OptionalInt.empty() : value;
            return this;
        }

        /** States the delta only if the author wrote one, from an {@code Optional<Integer>}. */
        public Builder delta(java.util.Optional<Integer> value) {
            this.delta = value == null || value.isEmpty()
                    ? OptionalInt.empty()
                    : OptionalInt.of(value.get());
            return this;
        }

        public Builder incident(@Nullable ResourceLocation type) {
            this.incidentType = type;
            return this;
        }

        /** The authored {@code incident_profile} selection for this deed (0.6.0 profiles). */
        public Builder incidentProfile(@Nullable ResourceLocation profile) {
            this.incidentProfile = profile;
            return this;
        }

        public Builder dedupeKey(@Nullable String key) {
            this.dedupeKey = key;
            return this;
        }

        public Builder visibility(@Nullable String value) {
            this.visibility = value;
            return this;
        }

        public Builder tags(List<String> values) {
            this.tags = values == null ? List.of() : List.copyOf(values);
            return this;
        }

        public Builder context(String key, @Nullable String value) {
            if (key != null && value != null && !value.isBlank()) {
                context.put(key, value);
            }
            return this;
        }

        public Builder subject(@Nullable UUID uuid, @Nullable String name, @Nullable String role) {
            this.subjectUuid = uuid;
            this.subjectName = name;
            this.subjectRole = role;
            return this;
        }

        public ReputationAward build() {
            return new ReputationAward(server, player, dimension, villageId, delta, incidentType, source,
                    dedupeKey, visibility, tags, context, subjectUuid, subjectName, subjectRole,
                    incidentProfile);
        }
    }
}
