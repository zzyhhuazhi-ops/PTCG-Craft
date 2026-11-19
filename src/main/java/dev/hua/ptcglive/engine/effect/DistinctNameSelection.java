package dev.hua.ptcglive.engine.effect;

import java.util.*;

/** Audited printing groups identify distinct names without depending on translated display titles. */
public final class DistinctNameSelection {
    private DistinctNameSelection() { }
    public static boolean handles(EffectDefinition effect) {return effect.arguments().containsKey("distinct_name_groups");}
    public static Map<String,Integer> groups(EffectDefinition effect) {
        if(!handles(effect))return Map.of();
        String[] groups=effect.arguments().get("distinct_name_groups").split(";",-1);
        if(groups.length<1||groups.length>100)throw new IllegalArgumentException("不同名称分组数量无效");
        var result=new HashMap<String,Integer>();
        for(int n=0;n<groups.length;n++)for(String id:TurnHistoryCondition.values(groups[n]))
            if(!id.matches("[a-z0-9_-]+")||result.putIfAbsent(id,n)!=null)throw new IllegalArgumentException("同一印刷身份不能属于多个名称");
        return Map.copyOf(result);
    }
    public static void validate(EffectDefinition effect) {
        var args=new HashMap<>(effect.arguments());args.remove("distinct_name_groups");
        if(!effect.type().equals("search_deck")||effect.target()!=EffectTarget.DECK||!effect.conditions().isEmpty()||!effect.effects().isEmpty()
            ||!args.equals(Map.of("amount","3","minimum","0","card_category","TRAINER","trainer_category","ITEM","reveal","true")))
            throw new IllegalArgumentException("不同名称检索只核验公开选取至多3种指定物品");
        groups(effect);
    }
    public static boolean valid(EffectDefinition effect,List<String> definitions) {
        if(!handles(effect))return true;
        var groups=groups(effect);var seen=new HashSet<Integer>();
        for(String id:definitions)if(!groups.containsKey(id)||!seen.add(groups.get(id)))return false;
        return true;
    }
}
