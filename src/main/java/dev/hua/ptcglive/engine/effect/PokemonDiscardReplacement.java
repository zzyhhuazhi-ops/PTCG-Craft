package dev.hua.ptcglive.engine.effect;

import java.util.Set;

/** The selected own Basic keeps its in-play identity while its physical top card is exchanged. */
public final class PokemonDiscardReplacement {
    private PokemonDiscardReplacement() { }
    public static final Set<String> REQUIRED=Set.of("amount","minimum","card_category","basic_pokemon_only","preserve_state","return_old_card");
    public static final Set<String> FIELDS=Set.of("amount","minimum","card_category","basic_pokemon_only","preserve_state","return_old_card",
        "pokemon_name_contains","pokemon_families","pokemon_types","replace_evolution_stack");
    public static boolean handles(EffectDefinition effect){return effect.type().equals("replace_basic_from_discard");}
    public static boolean wholePokemon(EffectDefinition effect){return handles(effect)&&"true".equals(effect.arguments().get("replace_evolution_stack"));}
    public static void validate(EffectDefinition effect) {
        var a=effect.arguments();
        if(!handles(effect)||effect.target()!=EffectTarget.CHOSEN_POKEMON||!effect.effects().isEmpty()
                ||!FIELDS.containsAll(a.keySet())||!a.keySet().containsAll(REQUIRED)||!"1".equals(a.get("amount"))||!"1".equals(a.get("minimum"))
                ||!"POKEMON".equals(a.get("card_category"))||!"true".equals(a.get("basic_pokemon_only"))
                ||!"ALL".equals(a.get("preserve_state"))||!"DISCARD".equals(a.get("return_old_card")))
            throw new IllegalArgumentException("弃牌替换须选己方基础宝可梦，以1张符合筛选的弃牌基础宝可梦完整继承状态并弃置旧顶卡");
        if(a.containsKey("pokemon_name_contains")&&a.get("pokemon_name_contains").isBlank())
            throw new IllegalArgumentException("弃牌替换名称筛选不能为空");
        if(a.containsKey("pokemon_families"))PokemonFamilyFilter.fromWire(a.get("pokemon_families"),"ANY");
        if(a.containsKey("replace_evolution_stack")&&!wholePokemon(effect))throw new IllegalArgumentException("完整替换须明确为true");
        if(a.containsKey("pokemon_types")||wholePokemon(effect)) {
            if(!wholePokemon(effect)||!"DARKNESS".equals(a.get("pokemon_types"))||a.containsKey("pokemon_name_contains")||a.containsKey("pokemon_families"))
                throw new IllegalArgumentException("任意阶段整叠替换仅核验弃牌区恶属性基础宝可梦");
        }
    }
}
