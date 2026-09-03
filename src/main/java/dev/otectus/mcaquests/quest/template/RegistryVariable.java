package dev.otectus.mcaquests.quest.template;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * A template variable that resolves to one registry id (item/block/entity/biome/dimension) drawn from
 * an explicit {@code ids} list and/or the members of one or more {@code tags}. Candidate ids are merged,
 * de-duplicated, and sorted by id string so the pool order is deterministic regardless of authoring;
 * one is then chosen with the offer's {@link QuestContext#stableRandom stable RNG}.
 *
 * <p>The {@link RegistryKind} is fixed by the variable's {@code "kind"} discriminator and supplied to
 * {@link #codec(RegistryKind)} at parse time, so each registry kind validates ids/tags against its own
 * registry (see {@link RegistryKind#idExistsForValidation}/{@link RegistryKind#expandTag}).
 */
public record RegistryVariable(RegistryKind kind, List<ResourceLocation> ids,
                               List<ResourceLocation> tags) implements TemplateVariable {

    /** Builds the codec for one registry kind; the kind itself is merged back as {@code "kind"} by
     * {@link TemplateVariable#CODEC} on encode, so it is not a field here. */
    public static Codec<RegistryVariable> codec(RegistryKind kind) {
        return RecordCodecBuilder.<RegistryVariable>create(instance -> instance.group(
                ResourceLocation.CODEC.listOf().lenientOptionalFieldOf("ids", List.of()).forGetter(RegistryVariable::ids),
                ResourceLocation.CODEC.listOf().lenientOptionalFieldOf("tags", List.of()).forGetter(RegistryVariable::tags)
        ).apply(instance, (ids, tags) -> new RegistryVariable(kind, ids, tags))).flatXmap(
                var -> validate(kind, var), var -> validate(kind, var));
    }

    private static DataResult<RegistryVariable> validate(RegistryKind kind, RegistryVariable var) {
        if (var.ids.isEmpty() && var.tags.isEmpty()) {
            return DataResult.error(() -> kind.key() + " variable must declare at least one of 'ids' or 'tags'");
        }
        if (!kind.allowsTags() && !var.tags.isEmpty()) {
            return DataResult.error(() -> kind.key() + " variable does not support 'tags' (no tag system for this registry)");
        }
        return DataResult.success(var);
    }

    @Override
    public String kindKey() {
        return kind.key();
    }

    @Override
    public Optional<ResolvedValue> representative() {
        if (!ids.isEmpty()) {
            return Optional.of(new ResolvedValue.IdValue(kind, ids.get(0)));
        }
        for (ResourceLocation tag : tags) {
            List<ResourceLocation> members = kind.staticMembers(tag);
            if (!members.isEmpty()) {
                return Optional.of(new ResolvedValue.IdValue(kind, members.get(0)));
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<ResolvedValue> resolve(String name, QuestContext context) {
        List<ResourceLocation> pool = candidatePool(context);
        if (pool.isEmpty()) {
            return Optional.empty();
        }
        Random random = context.stableRandom("var:" + name);
        ResourceLocation chosen = pool.get(random.nextInt(pool.size()));
        return Optional.of(new ResolvedValue.IdValue(kind, chosen));
    }

    /** Merged, de-duplicated, deterministically sorted candidate ids (explicit ids + expanded tags). */
    private List<ResourceLocation> candidatePool(QuestContext context) {
        Set<ResourceLocation> merged = new LinkedHashSet<>(ids);
        for (ResourceLocation tag : tags) {
            merged.addAll(kind.expandTag(tag, context));
        }
        List<ResourceLocation> pool = new ArrayList<>(merged);
        pool.sort(Comparator.comparing(ResourceLocation::toString));
        return pool;
    }
}
