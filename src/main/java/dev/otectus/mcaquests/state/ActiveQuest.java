package dev.otectus.mcaquests.state;

import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.objective.ObjectiveProgress;
import dev.otectus.mcaquests.quest.template.PlaceholderResolver;
import dev.otectus.mcaquests.quest.template.ResolvedTemplate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One accepted, in-flight quest stored on the player (spec section 16). Holds a snapshot of the
 * giver's identity so the quest survives the villager unloading, moving, or dying.
 */
public final class ActiveQuest {

    private final ResourceLocation questId;
    private final UUID villagerUuid;
    private final Component villagerName;
    @Nullable
    private final ResourceLocation villagerProfession;
    private final ResourceLocation dimension;
    private final long startGameTime;
    private final List<ObjectiveProgress> progress;
    /** Frozen template values for a quest accepted from a template (spec: chosen values must not reroll). */
    @Nullable
    private final ResolvedTemplate template;
    private boolean rewardClaimed;
    private boolean readyNotified;

    /** Lazily-built concretized definition + text resolver, derived from {@link #template}; not persisted. */
    @Nullable
    private transient QuestDefinition resolvedCache;
    @Nullable
    private transient PlaceholderResolver resolverCache;

    public ActiveQuest(ResourceLocation questId, UUID villagerUuid, Component villagerName,
                       @Nullable ResourceLocation villagerProfession, ResourceLocation dimension,
                       long startGameTime, List<ObjectiveProgress> progress, @Nullable ResolvedTemplate template) {
        this.questId = questId;
        this.villagerUuid = villagerUuid;
        this.villagerName = villagerName;
        this.villagerProfession = villagerProfession;
        this.dimension = dimension;
        this.startGameTime = startGameTime;
        this.progress = progress;
        this.template = template;
    }

    /** Fresh acceptance with empty progress for each objective. */
    public static ActiveQuest create(ResourceLocation questId, UUID villagerUuid, Component villagerName,
                                     @Nullable ResourceLocation villagerProfession, ResourceLocation dimension,
                                     long startGameTime, int objectiveCount, @Nullable ResolvedTemplate template) {
        List<ObjectiveProgress> progress = new ArrayList<>();
        for (int i = 0; i < objectiveCount; i++) {
            progress.add(new ObjectiveProgress());
        }
        return new ActiveQuest(questId, villagerUuid, villagerName, villagerProfession, dimension,
                startGameTime, progress, template);
    }

    /**
     * The definition to use for this active quest: the concretized template (objectives/rewards filled
     * from the frozen {@link #template} values) when this came from a template, otherwise {@code base}
     * unchanged. Cached so the per-second progress tick does not re-parse JSON. Falls back to
     * {@code base} if the stored values can no longer be substituted (e.g. a datapack changed).
     */
    public QuestDefinition resolve(QuestDefinition base) {
        if (template == null || base.template().isEmpty()) {
            return base;
        }
        if (resolvedCache == null) {
            resolvedCache = base.template()
                    .flatMap(spec -> spec.toConcrete(template))
                    .map(base::withConcrete)
                    .orElse(base);
        }
        return resolvedCache;
    }

    /** The placeholder resolver for dialogue/title, or {@code null} for a non-template quest. */
    @Nullable
    public PlaceholderResolver textResolver() {
        if (template == null) {
            return null;
        }
        if (resolverCache == null) {
            resolverCache = new PlaceholderResolver(template);
        }
        return resolverCache;
    }

    public ResourceLocation questId() {
        return questId;
    }

    public UUID villagerUuid() {
        return villagerUuid;
    }

    public Component villagerName() {
        return villagerName;
    }

    @Nullable
    public ResourceLocation villagerProfession() {
        return villagerProfession;
    }

    public ResourceLocation dimension() {
        return dimension;
    }

    public long startGameTime() {
        return startGameTime;
    }

    public ObjectiveProgress progress(int index) {
        return progress.get(index);
    }

    public boolean rewardClaimed() {
        return rewardClaimed;
    }

    public void setRewardClaimed(boolean claimed) {
        this.rewardClaimed = claimed;
    }

    /** Whether the player has already been notified (toast) that this quest is ready to turn in. */
    public boolean readyNotified() {
        return readyNotified;
    }

    public void setReadyNotified(boolean notified) {
        this.readyNotified = notified;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("quest", questId.toString());
        tag.putUUID("villager", villagerUuid);
        tag.putString("villager_name", Component.Serializer.toJson(villagerName));
        if (villagerProfession != null) {
            tag.putString("profession", villagerProfession.toString());
        }
        tag.putString("dimension", dimension.toString());
        tag.putLong("start", startGameTime);
        tag.putBoolean("claimed", rewardClaimed);
        tag.putBoolean("ready_notified", readyNotified);
        ListTag list = new ListTag();
        for (ObjectiveProgress p : progress) {
            list.add(p.save());
        }
        tag.put("progress", list);
        if (template != null) {
            tag.put("template", template.save());
        }
        return tag;
    }

    public static ActiveQuest load(CompoundTag tag) {
        Component name = Component.Serializer.fromJson(tag.getString("villager_name"));
        ResourceLocation profession = tag.contains("profession")
                ? new ResourceLocation(tag.getString("profession")) : null;
        List<ObjectiveProgress> progress = new ArrayList<>();
        ListTag list = tag.getList("progress", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            progress.add(ObjectiveProgress.load(list.getCompound(i)));
        }
        ResolvedTemplate template = tag.contains("template")
                ? ResolvedTemplate.load(tag.getCompound("template")) : null;
        ActiveQuest quest = new ActiveQuest(
                new ResourceLocation(tag.getString("quest")),
                tag.getUUID("villager"),
                name != null ? name : Component.empty(),
                profession,
                new ResourceLocation(tag.getString("dimension")),
                tag.getLong("start"),
                progress,
                template);
        quest.rewardClaimed = tag.getBoolean("claimed");
        quest.readyNotified = tag.getBoolean("ready_notified");
        return quest;
    }
}
