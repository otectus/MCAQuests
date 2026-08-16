package dev.otectus.mcaquests.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
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
 */
public record ReputationAward(
        MinecraftServer server,
        UUID player,
        ResourceLocation dimension,
        int villageId,
        int delta,
        @Nullable ResourceLocation incidentType,
        ResourceLocation source,
        @Nullable String dedupeKey,
        @Nullable String visibility,
        List<String> tags,
        Map<String, String> context,
        @Nullable UUID subjectUuid,
        @Nullable String subjectName,
        @Nullable String subjectRole) {

    public ReputationAward {
        tags = tags == null ? List.of() : List.copyOf(tags);
        context = context == null ? Map.of() : Map.copyOf(context);
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

        private int delta;
        @Nullable private ResourceLocation incidentType;
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

        public Builder delta(int value) {
            this.delta = value;
            return this;
        }

        public Builder incident(@Nullable ResourceLocation type) {
            this.incidentType = type;
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
                    dedupeKey, visibility, tags, context, subjectUuid, subjectName, subjectRole);
        }
    }
}
