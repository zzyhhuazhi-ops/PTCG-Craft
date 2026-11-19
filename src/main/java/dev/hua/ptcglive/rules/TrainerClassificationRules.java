package dev.hua.ptcglive.rules;

/** Regional historical errata changes membership in Item filters, not the Tool's attachment behavior. */
public record TrainerClassificationRules(boolean pokemonToolsAreItems) {
    public static final TrainerClassificationRules MODERN = new TrainerClassificationRules(false);
    public boolean matches(CardDefinition card, TrainerCategory requested) {
        if (requested == null) return true;
        return card.trainerCategory() == requested
            || pokemonToolsAreItems && requested == TrainerCategory.ITEM && card.trainerCategory() == TrainerCategory.TOOL;
    }
}
