package dev.hua.ptcglive.engine.effect;

import dev.hua.ptcglive.rules.CardCategory;
import dev.hua.ptcglive.rules.TrainerCategory;
import com.google.gson.JsonObject;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Explicit server-side filter for a card-play event.  It deliberately carries
 * only public classification facts: an event never exposes a hand, deck, or
 * private card identity to an observing effect.
 */
public record CardPlayedTriggerPolicy(Player player, CardKind card, boolean optional,
                                      EnergyTriggerPolicy.Usage usage) implements AbilityEventPolicy {
    public enum Player { SELF, OPPONENT, ANY }
    public enum CardKind { ANY, POKEMON, ENERGY, TRAINER, ITEM, SUPPORTER, STADIUM, TOOL }

    public CardPlayedTriggerPolicy {
        Objects.requireNonNull(player, "出牌玩家范围");
        Objects.requireNonNull(card, "出牌卡牌类别");
        Objects.requireNonNull(usage, "触发次数");
    }

    @Override public EffectTrigger trigger() { return EffectTrigger.ON_CARD_PLAYED; }

    /** Called only after the authoritative card-play operation has committed. */
    public boolean matches(UUID listenerOwner, UUID playedBy, CardCategory category, TrainerCategory trainerCategory) {
        boolean playerMatches = switch (player) {
            case SELF -> listenerOwner.equals(playedBy);
            case OPPONENT -> !listenerOwner.equals(playedBy);
            case ANY -> true;
        };
        if (!playerMatches) return false;
        return switch (card) {
            case ANY -> true;
            case POKEMON -> category == CardCategory.POKEMON;
            case ENERGY -> category == CardCategory.ENERGY;
            case TRAINER -> category == CardCategory.TRAINER;
            case ITEM -> category == CardCategory.TRAINER && trainerCategory == TrainerCategory.ITEM;
            case SUPPORTER -> category == CardCategory.TRAINER && trainerCategory == TrainerCategory.SUPPORTER;
            case STADIUM -> category == CardCategory.TRAINER && trainerCategory == TrainerCategory.STADIUM;
            case TOOL -> category == CardCategory.TRAINER && trainerCategory == TrainerCategory.TOOL;
        };
    }

    public static CardPlayedTriggerPolicy parse(JsonObject json) {
        if (!json.keySet().equals(Set.of("trigger", "player", "card", "optional", "usage"))
            || !json.has("player") || !json.get("player").isJsonPrimitive()
            || !json.has("card") || !json.get("card").isJsonPrimitive()
            || !json.has("optional") || !json.get("optional").isJsonPrimitive()
            || !json.getAsJsonPrimitive("optional").isBoolean()
            || !json.has("usage") || !json.get("usage").isJsonPrimitive())
            throw new IllegalArgumentException("出牌触发必须明确 player、card、optional、usage，不能忽略未知字段");
        return new CardPlayedTriggerPolicy(Player.valueOf(json.get("player").getAsString()),
            CardKind.valueOf(json.get("card").getAsString()), json.get("optional").getAsBoolean(),
            EnergyTriggerPolicy.Usage.valueOf(json.get("usage").getAsString()));
    }
}
