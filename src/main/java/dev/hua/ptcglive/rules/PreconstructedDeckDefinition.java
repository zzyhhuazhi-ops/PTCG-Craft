package dev.hua.ptcglive.rules;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Catalogue entry. It cannot be granted until the official complete 60-card list has been verified. */
public record PreconstructedDeckDefinition(
    String id, String productNameZh, String deckNameZh, LocalDate releaseDate,
    String historicalRulesetId, String officialSourceUrl, Availability availability,
    String availabilityNoteZh, List<String> verifiedCardDefinitionIds
) {
    public enum Availability { AVAILABLE, PENDING_OFFICIAL_CARDLIST }
    public PreconstructedDeckDefinition {
        Objects.requireNonNull(id); Objects.requireNonNull(productNameZh); Objects.requireNonNull(deckNameZh);
        Objects.requireNonNull(releaseDate); Objects.requireNonNull(historicalRulesetId); Objects.requireNonNull(officialSourceUrl);
        Objects.requireNonNull(availability); Objects.requireNonNull(availabilityNoteZh);
        verifiedCardDefinitionIds = List.copyOf(verifiedCardDefinitionIds);
        if (availability == Availability.AVAILABLE && verifiedCardDefinitionIds.size() != 60)
            throw new IllegalArgumentException("可领取预组必须有经核验的完整 60 张卡表");
        if (availability == Availability.PENDING_OFFICIAL_CARDLIST && !verifiedCardDefinitionIds.isEmpty())
            throw new IllegalArgumentException("待核验预组不能混入未经验证的卡表");
    }
}
