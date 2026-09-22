package dev.otectus.mcaquests.quest.kingdom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.data.StrictCodecs;

import java.util.Optional;

/** Optional per-definition political and civic lifecycle policy. */
public record KingdomLifecycleSpec(KingdomLifecycleMode mode, Optional<KingdomGateSpec> gate,
                                   Optional<String> failureReason,
                                   Optional<CivicBuildingSpec> civicBuilding,
                                   Optional<String> bindingSubject) {
    public static final Codec<KingdomLifecycleSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            StrictCodecs.strictOptional(KingdomLifecycleMode.CODEC, "mode", KingdomLifecycleMode.OFFER_ONLY)
                    .forGetter(KingdomLifecycleSpec::mode),
            StrictCodecs.strictOptional(KingdomGateSpec.CODEC, "gate").forGetter(KingdomLifecycleSpec::gate),
            StrictCodecs.strictOptional(Codec.STRING, "failure_reason").forGetter(KingdomLifecycleSpec::failureReason),
            StrictCodecs.strictOptional(CivicBuildingSpec.CODEC, "civic_building")
                    .forGetter(KingdomLifecycleSpec::civicBuilding),
            StrictCodecs.strictOptional(Codec.STRING, "binding_subject")
                    .forGetter(KingdomLifecycleSpec::bindingSubject)
    ).apply(instance, KingdomLifecycleSpec::new));

    public KingdomLifecycleSpec {
        gate = gate == null ? Optional.empty() : gate;
        failureReason = failureReason == null ? Optional.empty() : failureReason.map(String::strip)
                .filter(value -> !value.isEmpty());
        civicBuilding = civicBuilding == null ? Optional.empty() : civicBuilding;
        bindingSubject = bindingSubject == null ? Optional.empty() : bindingSubject.map(String::strip)
                .filter(value -> !value.isEmpty());
        if (gate.isEmpty() && civicBuilding.isEmpty()) {
            throw new IllegalArgumentException("kingdom_lifecycle requires gate or civic_building");
        }
        if (mode != KingdomLifecycleMode.OFFER_ONLY && gate.isEmpty()) {
            throw new IllegalArgumentException("kingdom_lifecycle.gate is required outside offer_only mode");
        }
        if (mode == KingdomLifecycleMode.FAIL_ON_CHANGE && failureReason.isEmpty()) {
            throw new IllegalArgumentException("kingdom_lifecycle.failure_reason is required for fail_on_change");
        }
        bindingSubject.ifPresent(subject -> {
            if (!java.util.Set.of("giver_residence", "giver_origin", "giver_location", "player_location",
                    "explicit_settlement").contains(subject)) {
                throw new IllegalArgumentException("Unknown kingdom lifecycle binding_subject: " + subject);
            }
        });
    }

    public String effectiveBindingSubject() {
        return bindingSubject.orElseGet(() -> gate.map(KingdomGateSpec::subject).orElse("giver_residence"));
    }
}
