package dev.hua.ptcglive.engine.effect;
import java.util.Set;
/** A top-card exchange inherits the same in-play Pokemon; it is neither evolution nor a new entry. */
public final class PokemonDeckReplacement {
    private PokemonDeckReplacement() { }
    public static final Set<String> FIELDS=Set.of("amount","minimum","card_category","preserve_state","return_old_card","definition_ids","pokemon_names","basic_pokemon_only");
    public static boolean handles(EffectDefinition effect){return effect.type().equals("replace_pokemon_from_deck");}
    public static boolean selectedBasic(EffectDefinition effect){return handles(effect)&&effect.target()==EffectTarget.CHOSEN_POKEMON;}
    public static boolean activeToBench(EffectDefinition effect){
        return handles(effect)&&"Palafin ex".equals(effect.arguments().get("pokemon_names"));
    }
    public static void validate(EffectDefinition effect){
        if(!handles(effect)||(effect.target()!=EffectTarget.SELF&&!selectedBasic(effect))||!effect.effects().isEmpty()
            ||!FIELDS.containsAll(effect.arguments().keySet())||!effect.arguments().keySet().containsAll(Set.of("amount","minimum","card_category","preserve_state","return_old_card"))||!"1".equals(effect.arguments().get("amount"))
            ||!"0".equals(effect.arguments().get("minimum"))||!"POKEMON".equals(effect.arguments().get("card_category"))
            ||!"ALL".equals(effect.arguments().get("preserve_state"))||!"DECK_SHUFFLED".equals(effect.arguments().get("return_old_card")))
            throw new IllegalArgumentException("牌库替换须为SELF、0至1张宝可梦、完整继承状态并将旧顶卡洗回牌库");
        if(selectedBasic(effect)) {
            if(!"true".equals(effect.arguments().get("basic_pokemon_only"))||effect.arguments().containsKey("definition_ids")||effect.arguments().containsKey("pokemon_names"))
                throw new IllegalArgumentException("训练家牌库替换须为已选基础宝可梦与牌库基础宝可梦的完整替换");
        } else if(effect.arguments().containsKey("basic_pokemon_only"))throw new IllegalArgumentException("招式原来源替换不能混用训练家筛选");
        if(effect.arguments().containsKey("definition_ids"))TurnHistoryCondition.values(effect.arguments().get("definition_ids"));
        if(effect.arguments().containsKey("pokemon_names")&&!activeToBench(effect))throw new IllegalArgumentException("按名替换仅核验海豚侠ex");
    }
}
