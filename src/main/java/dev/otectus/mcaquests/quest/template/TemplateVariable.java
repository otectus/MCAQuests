package dev.otectus.mcaquests.quest.template;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import dev.otectus.mcaquests.quest.condition.QuestContext;

import java.util.Optional;

/**
 * A declared template variable: a pool/range that resolves to one concrete {@link ResolvedValue} given
 * the offer {@link QuestContext}. Dispatched by a single flat {@code "kind"} discriminator — the five
 * registry kinds (item/block/entity/biome/dimension) map to {@link RegistryVariable}, {@code int} to
 * {@link IntVariable}, {@code text} to {@link TextVariable}.
 */
public sealed interface TemplateVariable permits RegistryVariable, IntVariable, TextVariable {

    /** The {@code "kind"} discriminator written back on encode. */
    String kindKey();

    /** Resolves to a concrete value, or empty if the pool has no eligible entry in this context. */
    Optional<ResolvedValue> resolve(String name, QuestContext context);

    /**
     * A representative value resolvable <em>without</em> a live world, used by load-time validation to
     * trial-substitute and parse the template. Empty when no representative is knowable at load (e.g. a
     * registry variable backed only by a dynamic biome/dimension tag).
     */
    Optional<ResolvedValue> representative();

    Codec<String> KIND_CODEC = Codec.STRING.fieldOf("kind").codec();

    Codec<TemplateVariable> CODEC = new Codec<>() {
        @Override
        public <T> DataResult<Pair<TemplateVariable, T>> decode(DynamicOps<T> ops, T input) {
            DataResult<String> kind = KIND_CODEC.parse(ops, input);
            if (kind.result().isEmpty()) {
                return DataResult.error(() -> "Template variable is missing a valid 'kind'");
            }
            String key = kind.result().get();
            Codec<? extends TemplateVariable> sub = subCodec(key);
            if (sub == null) {
                return DataResult.error(() -> "Unknown template variable kind: '" + key + "'");
            }
            return sub.decode(ops, input).map(pair -> Pair.of((TemplateVariable) pair.getFirst(), pair.getSecond()));
        }

        @Override
        public <T> DataResult<T> encode(TemplateVariable input, DynamicOps<T> ops, T prefix) {
            DataResult<T> body;
            if (input instanceof RegistryVariable registry) {
                body = RegistryVariable.codec(registry.kind()).encode(registry, ops, prefix);
            } else if (input instanceof IntVariable intVar) {
                body = IntVariable.CODEC.encode(intVar, ops, prefix);
            } else {
                body = TextVariable.CODEC.encode((TextVariable) input, ops, prefix);
            }
            return body.flatMap(encoded ->
                    ops.mergeToMap(encoded, ops.createString("kind"), ops.createString(input.kindKey())));
        }
    };

    private static Codec<? extends TemplateVariable> subCodec(String kind) {
        Optional<RegistryKind> registryKind = RegistryKind.fromKey(kind);
        if (registryKind.isPresent()) {
            return RegistryVariable.codec(registryKind.get());
        }
        return switch (kind) {
            case "int" -> IntVariable.CODEC;
            case "text" -> TextVariable.CODEC;
            default -> null;
        };
    }
}
