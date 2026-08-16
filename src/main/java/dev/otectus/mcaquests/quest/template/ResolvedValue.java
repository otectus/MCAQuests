package dev.otectus.mcaquests.quest.template;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.otectus.mcaquests.quest.QuestText;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One concrete value chosen for a template variable. Frozen onto the {@link ResolvedTemplate} of an
 * active quest, so it survives logout/restart and never rerolls. Knows how to:
 * <ul>
 *   <li>{@link #asJson()} — substitute itself into the raw objective/reward JSON (number for ints,
 *       id string for registry entries);</li>
 *   <li>{@link #display()} — render a player-facing name/number for a placeholder;</li>
 *   <li>{@link #save()}/{@link #load(CompoundTag)} — round-trip through NBT.</li>
 * </ul>
 */
public sealed interface ResolvedValue permits ResolvedValue.IdValue, ResolvedValue.IntValue, ResolvedValue.TextValue {

    /** The JSON form substituted into objective/reward fields (string id, or number). */
    JsonElement asJson();

    /** A bare string form for inline substitution inside a longer JSON string value. */
    String plain();

    /** A player-facing component: the display name of a registry id, the number, or the chosen phrase. */
    Component display();

    CompoundTag save();

    static ResolvedValue load(CompoundTag tag) {
        return switch (tag.getString("t")) {
            case "id" -> new IdValue(
                    RegistryKind.fromKey(tag.getString("kind")).orElse(RegistryKind.ITEM),
                    ResourceLocation.parse(tag.getString("id")));
            case "int" -> new IntValue(tag.getInt("v"));
            case "text" -> new TextValue(loadText(tag));
            default -> new IntValue(0);
        };
    }

    private static QuestText loadText(CompoundTag tag) {
        Optional<String> text = tag.contains("text") ? Optional.of(tag.getString("text")) : Optional.empty();
        Optional<String> translate = tag.contains("translate") ? Optional.of(tag.getString("translate")) : Optional.empty();
        List<String> with = new ArrayList<>();
        ListTag list = tag.getList("with", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            with.add(list.getString(i));
        }
        return new QuestText(text, translate, with);
    }

    /** A concrete registry id chosen from a {@link RegistryVariable} pool. */
    record IdValue(RegistryKind kind, ResourceLocation id) implements ResolvedValue {
        @Override
        public JsonElement asJson() {
            return new JsonPrimitive(id.toString());
        }

        @Override
        public String plain() {
            return id.toString();
        }

        @Override
        public Component display() {
            return kind.display(id);
        }

        @Override
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("t", "id");
            tag.putString("kind", kind.key());
            tag.putString("id", id.toString());
            return tag;
        }
    }

    /** A number chosen (and possibly context-scaled) from an {@code int} variable. */
    record IntValue(int value) implements ResolvedValue {
        @Override
        public JsonElement asJson() {
            return new JsonPrimitive(value);
        }

        @Override
        public String plain() {
            return Integer.toString(value);
        }

        @Override
        public Component display() {
            return Component.literal(Integer.toString(value));
        }

        @Override
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("t", "int");
            tag.putInt("v", value);
            return tag;
        }
    }

    /** A flavor phrase chosen from a {@code text} variable (literal or translation key). */
    record TextValue(QuestText value) implements ResolvedValue {
        @Override
        public JsonElement asJson() {
            // Text variables are for dialogue only; never substituted into objective/reward JSON.
            return new JsonPrimitive(plain());
        }

        @Override
        public String plain() {
            return value.text().or(value::translate).orElse("");
        }

        @Override
        public Component display() {
            return value.resolve();
        }

        @Override
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("t", "text");
            value.text().ifPresent(t -> tag.putString("text", t));
            value.translate().ifPresent(t -> tag.putString("translate", t));
            if (!value.with().isEmpty()) {
                ListTag list = new ListTag();
                value.with().forEach(arg -> list.add(StringTag.valueOf(arg)));
                tag.put("with", list);
            }
            return tag;
        }
    }
}
