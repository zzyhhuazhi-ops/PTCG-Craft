package dev.hua.ptcglive.engine.effect;

import dev.hua.ptcglive.rules.*;
import java.util.Set;

/** Reviewed Fossil Quarry transfer: an Item in the private deck becomes a Pokemon only after placement. */
public final class ItemPokemonDeckSearch {
    private ItemPokemonDeckSearch() { }
    public static boolean handles(EffectDefinition effect) {
        return "ITEM_POKEMON_BENCH".equals(effect.arguments().get("destination"));
    }
    public static void validate(EffectDefinition effect) {
        boolean rare="Rare Fossil".equals(effect.arguments().get("trainer_name_equals"));
        var fields=Set.of("amount","minimum","card_category","trainer_category",rare?"trainer_name_equals":"trainer_name_contains","destination");
        if(!effect.type().equals("search_deck") || effect.target()!=EffectTarget.DECK
                || !effect.arguments().keySet().equals(fields) || !effect.effects().isEmpty()
                || !"TRAINER".equals(effect.arguments().get("card_category"))
                || !"ITEM".equals(effect.arguments().get("trainer_category"))
                || !rare&&!"Antique".equals(effect.arguments().get("trainer_name_contains"))
                || !"0".equals(effect.arguments().get("minimum"))
                || !Set.of("1","2").contains(effect.arguments().get("amount")) || !handles(effect))
            throw new IllegalArgumentException("化石检索须限定印刷卡名含Antique或名为Rare Fossil的物品直接上场");
    }
    public static boolean matches(CardDefinition card,TrainerProfile trainer) {
        return matches(null,card,trainer);
    }
    public static boolean matches(EffectDefinition effect,CardDefinition card,TrainerProfile trainer) {
        return card.category()==CardCategory.TRAINER && card.trainerCategory()==TrainerCategory.ITEM
            && card.effectSupport()==EffectSupport.SUPPORTED && trainer!=null && trainer.inPlayPokemon()!=null
            && (effect!=null&&effect.arguments().containsKey("trainer_name_equals")
                ?trainer.inPlayPokemon().pokemonName().equals(effect.arguments().get("trainer_name_equals"))
                :trainer.inPlayPokemon().pokemonName().contains("Antique"));
    }
}
