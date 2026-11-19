package dev.hua.ptcglive.engine.effect;

import java.util.Map;
import java.util.Set;

/** Small server contracts for the four SV8.5 Trainers whose choices span more than one ordinary zone action. */
public final class Sv85TrainerEffects {
    private static final Set<String> TYPES=Set.of("split_basic_energy_search","discard_reorder_top","guess_pokemon_hp","top_distinct_category_search");
    private Sv85TrainerEffects() { }
    public static boolean handles(EffectDefinition effect){return TYPES.contains(effect.type());}
    public static void validate(EffectDefinition effect) {
        if(!handles(effect)||!effect.conditions().isEmpty()||!effect.effects().isEmpty())throw new IllegalArgumentException("SV8.5训练家交互必须是原子步骤");
        Map<String,String> a=effect.arguments();
        switch(effect.type()) {
            case "split_basic_energy_search" -> {
                if(effect.target()!=EffectTarget.DECK||!a.equals(Map.of("amount","2","different_types","true","destinations","HAND|ATTACHED_OWN_FIELD")))
                    throw new IllegalArgumentException("Crispin须检索2张不同属性基本能量并分别加入手牌及附着");
            }
            case "discard_reorder_top" -> {
                if(effect.target()!=EffectTarget.DECK||!a.keySet().equals(Set.of("amount","minimum_discard"))
                    ||!"0".equals(a.get("minimum_discard"))||integer(a,"amount",1,60)<1)
                    throw new IllegalArgumentException("Raifort须查看牌库顶并可弃置任意张后重排");
            }
            case "guess_pokemon_hp" -> {
                if(effect.target()!=EffectTarget.HAND||!a.keySet().equals(Set.of("opponent_draw","owner_draw"))
                    ||integer(a,"opponent_draw",1,60)<1||integer(a,"owner_draw",1,60)<1)
                    throw new IllegalArgumentException("Tyme须从手牌选择宝可梦并由对手猜HP");
            }
            case "top_distinct_category_search" -> {
                if(effect.target()!=EffectTarget.DECK||!a.keySet().equals(Set.of("top_count","categories","amount_each","minimum_each","remainder"))
                    ||integer(a,"top_count",1,60)<1||!"POKEMON|TRAINER".equals(a.get("categories"))
                    ||!"1".equals(a.get("amount_each"))||!"0".equals(a.get("minimum_each"))||!"DECK_SHUFFLED".equals(a.get("remainder")))
                    throw new IllegalArgumentException("Drayton须从牌库顶窗口各取至多1张宝可梦和训练家");
            }
            default -> throw new IllegalArgumentException("未知SV8.5训练家交互");
        }
    }
    private static int integer(Map<String,String> a,String key,int minimum,int maximum) {
        int value;
        try {value=Integer.parseInt(a.get(key));}catch(RuntimeException invalid){throw new IllegalArgumentException(key+"必须是整数",invalid);}
        if(value<minimum||value>maximum)throw new IllegalArgumentException(key+"超出范围");
        return value;
    }
}
