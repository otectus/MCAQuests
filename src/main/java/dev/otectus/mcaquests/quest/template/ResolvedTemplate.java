package dev.otectus.mcaquests.quest.template;

import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The frozen result of resolving a template: the concrete value chosen for each variable. This is the
 * single source of truth persisted on an {@code ActiveQuest} (spec: "chosen values must not reroll").
 * Re-substituting the (current) template JSON with these values reproduces the identical concrete
 * quest, so only the small value map is stored — never the expanded objectives.
 */
public final class ResolvedTemplate {

    private final Map<String, ResolvedValue> values;

    public ResolvedTemplate(Map<String, ResolvedValue> values) {
        this.values = new LinkedHashMap<>(values);
    }

    public Optional<ResolvedValue> get(String name) {
        return Optional.ofNullable(values.get(name));
    }

    public Map<String, ResolvedValue> values() {
        return values;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        CompoundTag valuesTag = new CompoundTag();
        values.forEach((name, value) -> valuesTag.put(name, value.save()));
        tag.put("values", valuesTag);
        return tag;
    }

    public static ResolvedTemplate load(CompoundTag tag) {
        Map<String, ResolvedValue> values = new LinkedHashMap<>();
        CompoundTag valuesTag = tag.getCompound("values");
        for (String name : valuesTag.getAllKeys()) {
            values.put(name, ResolvedValue.load(valuesTag.getCompound(name)));
        }
        return new ResolvedTemplate(values);
    }
}
