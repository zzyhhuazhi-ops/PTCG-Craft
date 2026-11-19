package dev.hua.ptcglive.engine.effect;

import java.util.Set;

/** Private top-card inspection with an explicit destination or shuffle action. */
public final class TopCardInspection {
    private TopCardInspection() { }
    public static final Set<String> FIELDS=Set.of("amount","minimum","top_count","source_owner","selection_action");
    public static void validate(EffectDefinition effect) {
        if(effect.type().equals("inspect_top_deck")&&effect.target()==EffectTarget.DECK&&effect.effects().isEmpty()) {
            var args=effect.arguments();
            if(args.equals(java.util.Map.of("amount","1","minimum","0","top_count","1","source_owner","OPPONENT","selection_action","DISCARD")))return;
            for(String count:java.util.List.of("1","3"))
                if(args.equals(java.util.Map.of("amount",count,"minimum","0","top_count",count,"selection_action","TAKE_OR_DISCARD_DRAW")))return;
            for(String minimum:java.util.List.of("0","1"))
                if(args.equals(java.util.Map.of("amount","1","minimum",minimum,"top_count","2","source_owner","OPPONENT","selection_action","BOTTOM")))return;
            // Empty server-issued windows narrow the mandatory confirmation to zero cards.
            for(String minimum:java.util.List.of("0","1"))
                if(args.equals(java.util.Map.of("amount","1","minimum",minimum,"top_count","1","selection_action","VIEW_ONLY"))
                    ||args.equals(java.util.Map.of("amount","1","minimum",minimum,"top_count","1","selection_action","VIEW_ONLY","source_owner","OPPONENT")))return;
        }
        if(effect.type().equals("inspect_top_deck")&&effect.target()==EffectTarget.DECK&&effect.effects().isEmpty()
            &&effect.arguments().equals(java.util.Map.of("amount","1","minimum","0","top_count","1","selection_action","BOTTOM")))return;
        if(!effect.type().equals("inspect_top_deck")||effect.target()!=EffectTarget.DECK||!effect.effects().isEmpty()
            ||!Set.of(Set.of("amount","minimum","top_count"),FIELDS).contains(effect.arguments().keySet())||!"1".equals(effect.arguments().get("amount"))
            ||!"0".equals(effect.arguments().get("minimum"))||!"1".equals(effect.arguments().get("top_count")))
            throw new IllegalArgumentException("查看牌库顶可弃置目前只接受DECK、top_count=1、amount=1、minimum=0");
        if(effect.arguments().containsKey("source_owner")&&(!"OPPONENT".equals(effect.arguments().get("source_owner"))
            ||!"SHUFFLE".equals(effect.arguments().get("selection_action"))))
            throw new IllegalArgumentException("查看对手牌库顶只支持可选重洗");
    }
}
