package dev.hua.ptcglive.engine.effect;

import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Printed searches reveal at most one card of each explicitly requested kind together. */
public final class DistinctCardKindSelection {
    private DistinctCardKindSelection() { }
    public static boolean handles(EffectDefinition effect){return effect.arguments().containsKey("one_each_card_kind");}
    public static void validate(EffectDefinition effect){
        var a=effect.arguments();String name=a.get("pokemon_name_contains");
        boolean teamBall=Set.of("Team Aqua","Team Magma").contains(name==null?"":name)
            &&a.equals(Map.of("amount","2","minimum","0","card_kinds","POKEMON|BASIC_ENERGY","one_each_card_kind","true","reveal","true",
                "basic_pokemon_only","true","pokemon_name_contains",name,"pokemon_name_or_basic_energy_type",name.equals("Team Aqua")?"WATER":"FIGHTING"));
        if(!effect.type().equals("search_deck")||effect.target()!=EffectTarget.DECK||!effect.conditions().isEmpty()||!effect.effects().isEmpty()
            ||!teamBall&&!a.equals(Map.of("amount","3","minimum","0","card_kinds","STADIUM|TOOL|SPECIAL_ENERGY","one_each_card_kind","true","reveal","true")))
            throw new IllegalArgumentException("分类各一张检索须公开至多一张竞技场、一道具和一特殊能量");
    }
    public static String kind(CardDefinition card){
        if(card.category()==CardCategory.POKEMON)return "POKEMON";
        if(card.category()==CardCategory.ENERGY&&card.basicEnergy())return "BASIC_ENERGY";
        if(card.category()==CardCategory.ENERGY&&!card.basicEnergy())return "SPECIAL_ENERGY";
        if(card.category()==CardCategory.TRAINER&&Set.of(TrainerCategory.TOOL,TrainerCategory.STADIUM).contains(card.trainerCategory()))return card.trainerCategory().name();
        return null;
    }
    public static boolean valid(EffectDefinition effect,List<CardDefinition> cards){
        if(!handles(effect))return true;
        var seen=new HashSet<String>();for(var card:cards){String kind=kind(card);if(kind==null||!seen.add(kind))return false;}return true;
    }
}
