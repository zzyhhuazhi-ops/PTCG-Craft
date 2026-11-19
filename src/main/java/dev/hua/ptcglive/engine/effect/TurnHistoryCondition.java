package dev.hua.ptcglive.engine.effect;

import java.util.Set;

/** Completed hand plays and printed identities in server-owned player history. */
public final class TurnHistoryCondition {
    private TurnHistoryCondition() { }
    public static boolean handles(ConditionType type) {return type==ConditionType.POKEMON_ATTACKED_LAST_OWN_TURN||type==ConditionType.CARD_PLAYED_THIS_TURN;}
    public static String field(ConditionType type) {return type==ConditionType.POKEMON_ATTACKED_LAST_OWN_TURN?"pokemon_names":"definition_ids";}
    public static Set<String> values(String raw) {
        if(raw==null||raw.isBlank())throw new IllegalArgumentException("回合记录条件须明确卡牌身份");
        var values=new java.util.LinkedHashSet<String>();
        for(String value:raw.split("\\|",-1))if(value.isBlank()||!value.equals(value.trim())||value.length()>120||!values.add(value))throw new IllegalArgumentException("回合记录身份包含空值、重复值或过长值");
        if(values.size()>60)throw new IllegalArgumentException("回合记录身份最多60种");return Set.copyOf(values);
    }
    public static void validate(ConditionDefinition condition) {
        if(condition.type()==ConditionType.POKEMON_ATTACKED_LAST_OWN_TURN&&condition.arguments().containsKey("attack_ids")) {
            if(!Set.of("target","attack_ids").containsAll(condition.arguments().keySet())
                ||!"SELF".equals(condition.arguments().getOrDefault("target","SELF")))
                throw new IllegalArgumentException("上回合招式条件只接受己方与attack_ids");
            values(condition.arguments().get("attack_ids"));return;
        }
        if(condition.type()==ConditionType.POKEMON_ATTACKED_LAST_OWN_TURN&&condition.arguments().containsKey("pokemon_families")) {
            if(!Set.of("target","pokemon_families","other_only").containsAll(condition.arguments().keySet())
                ||!"SELF".equals(condition.arguments().getOrDefault("target","SELF"))
                ||!"true".equals(condition.arguments().getOrDefault("other_only","true")))
                throw new IllegalArgumentException("上回合攻击家族条件只接受己方、pokemon_families及other_only=true");
            PokemonFamilyFilter.fromWire(condition.arguments().get("pokemon_families"),"ANY");return;
        }
        if(condition.type()==ConditionType.CARD_PLAYED_THIS_TURN&&condition.arguments().containsKey("trainer_name_contains")) {
            String value=condition.arguments().get("trainer_name_contains");
            if(!Set.of("target","trainer_name_contains","trainer_category").containsAll(condition.arguments().keySet())
                ||!"SELF".equals(condition.arguments().getOrDefault("target","SELF"))
                ||!"SUPPORTER".equals(condition.arguments().getOrDefault("trainer_category","SUPPORTER"))
                ||value==null||value.isBlank()||!value.equals(value.trim())||value.length()>64)
                throw new IllegalArgumentException("名称回合记录只接受己方已使用训练家的非空名称片段");
            return;
        }
        if(condition.type()==ConditionType.CARD_PLAYED_THIS_TURN&&condition.arguments().containsKey("trainer_category")) {
            if(!Set.of("target","trainer_category").containsAll(condition.arguments().keySet())
                ||!"SELF".equals(condition.arguments().getOrDefault("target","SELF"))
                ||!"SUPPORTER".equals(condition.arguments().get("trainer_category")))
                throw new IllegalArgumentException("类别回合记录只接受己方已从手牌使用的支援者，不能混用身份条件");
            return;
        }
        if(condition.type()==ConditionType.CARD_PLAYED_THIS_TURN&&condition.arguments().containsKey("battle_style")) {
            if(!Set.of("target","battle_style").containsAll(condition.arguments().keySet())
                ||!"SELF".equals(condition.arguments().getOrDefault("target","SELF")))
                throw new IllegalArgumentException("对战风格回合记录只接受己方已使用卡牌");
            dev.hua.ptcglive.rules.BattleStyle.valueOf(condition.arguments().get("battle_style"));return;
        }
        if(!handles(condition.type())||!Set.of("target",field(condition.type())).containsAll(condition.arguments().keySet())
            ||!"SELF".equals(condition.arguments().getOrDefault("target","SELF")))throw new IllegalArgumentException("回合记录条件只接受己方玩家及明确身份");
        values(condition.arguments().get(field(condition.type())));
    }
}
