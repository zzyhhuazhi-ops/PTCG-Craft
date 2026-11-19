package dev.hua.ptcglive.ai;

import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Strategy vocabulary derived from opcodes/arguments, never names, artwork or rarity. Not a rules executor. */
public final class AiEffects {
    public enum Role { DRAW, SEARCH, RECOVER, ATTACH, MOVE_ENERGY, SWITCH, GUST, HEAL, BOOST, DEFEND, EVOLVE, DISCARD, STATUS, TOOL, PRIZE, COIN_CONTROL }
    public record Facts(Set<Role> roles,int draw,int drawUntil,boolean resetHand,int handCost,int boost,
                        Set<String> types,Set<String> weak,boolean uncertain) {
        public Facts {roles=Set.copyOf(roles);types=Set.copyOf(types);weak=Set.copyOf(weak);}
        public boolean has(Role role){return roles.contains(role);}
    }
    private AiEffects(){}
    public static Facts trainer(TrainerProfile p){return trainer(p,1);}
    public static Facts trainer(TrainerProfile p,int cards){var b=new Builder();b.trainer(p,cards);return b.build();}
    public static Facts ability(AbilityDefinition a){var b=new Builder();b.ability(a);return b.build();}
    public static Facts card(AiView.Card c){
        var b=new Builder();if(c.trainer()!=null){b.trainer(c.trainer());if(c.trainer().pairedPlay()!=null)b.program(c.trainer().pairedPlay().program());}
        if(c.pokemon()!=null){
            for(var a:c.pokemon().abilities())b.ability(a);
            for(var a:c.pokemon().attacks()){
                b.program(a.effectProgram());
                if(a.coinDamage()!=null||a.coinProtection()!=null)b.uncertain=true;
                if(!a.damageModifiers().isEmpty()||a.useRequirement()!=null)b.weak.add("招式条件/变动伤害仅保守评价");
                if(a.discardAllAttachedEnergyAfterAttack()||a.discardSelectedAttachedEnergyAfterAttack()>0)b.roles.add(Role.DISCARD);
            }
        }
        if(c.energy()!=null){b.program(c.energy().passiveProgram());for(var a:c.energy().automaticEffects())b.program(a.effectProgram());}
        return b.build();
    }
    private static final class Builder {
        final Set<Role> roles=EnumSet.noneOf(Role.class);final Set<String> types=new TreeSet<>(),weak=new TreeSet<>();
        int draw,drawUntil,handCost,boost;boolean resetHand,uncertain;
        Facts build(){return new Facts(roles,draw,drawUntil,resetHand,handCost,boost,types,weak,uncertain);}
        void trainer(TrainerProfile p){trainer(p,1);}
        void trainer(TrainerProfile p,int cards){
            var e=p.effect();handCost+=e.discardHandCost();if(handCost>0)roles.add(Role.DISCARD);
            switch(e.opcode()){
                case DRAW -> {roles.add(Role.DRAW);draw=Math.max(draw,e.amount());}
                case DRAW_ON_TURN_DRAW -> roles.add(Role.DRAW);
                case PRIZE_COIN_EXTRA_PRIZE -> roles.add(Role.PRIZE);
                case PRIZE_SEARCH_POKEMON -> roles.add(Role.SEARCH);
                case DRAW_UNTIL_HAND_SIZE -> {roles.add(Role.DRAW);drawUntil=Math.max(drawUntil,e.amount());}
                case DISCARD_HAND_DRAW,SHUFFLE_HAND_DRAW -> {roles.add(Role.DRAW);draw=Math.max(draw,e.amount());resetHand=true;}
                case SEARCH_BASIC_POKEMON_TO_HAND,SEARCH_POKEMON_TO_HAND,SEARCH_CARD_TO_HAND -> roles.add(Role.SEARCH);
                case SWITCH_ACTIVE -> roles.add(Role.SWITCH);
                case SWITCH_OPPONENT_ACTIVE -> roles.add(Role.GUST);
                case HEAL,CURE_OWN_ACTIVE_SPECIAL_CONDITIONS -> roles.add(Role.HEAL);
                case EVOLVE_OWN_POKEMON_FROM_HAND,EVOLVE_OWN_BASIC_SKIP_STAGE_FROM_HAND -> roles.add(Role.EVOLVE);
                case TOOL_RETREAT_REDUCTION,TOOL_RETREAT_REDUCTION_ALLOW_TWO,STADIUM_BASIC_RETREAT_REDUCTION -> roles.add(Role.SWITCH);
                case COMPOSED,PASSIVE_TOOL,PASSIVE_STADIUM,SETUP_FIRST_TICKET -> { }
                default -> weak.add("训练家操作码："+e.opcode());
            }
            if(p.category()==TrainerCategory.TOOL)roles.add(Role.TOOL);
            program(p.pairedPlay()!=null&&cards==p.pairedPlay().count()&&p.pairedPlay().allowsSingle()?p.pairedPlay().program():p.effectProgram());program(p.ongoingProgram());for(var a:p.automaticEffects())program(a.effectProgram());
            if(p.activation()!=null)program(p.activation().program());if(p.toolActivation()!=null)program(p.toolActivation().program());
        }
        void ability(AbilityDefinition a){
            var e=a.effect();switch(e.opcode()){
                case DRAW -> {roles.add(Role.DRAW);draw=Math.max(draw,e.amount());}
                case DRAW_UNTIL_HAND_SIZE -> {roles.add(Role.DRAW);drawUntil=Math.max(drawUntil,e.amount());}
                case SEARCH_BASIC_POKEMON_TO_HAND,SEARCH_POKEMON_TO_HAND,SEARCH_CARD_TO_HAND -> roles.add(Role.SEARCH);
                case HEAL_OWN_POKEMON -> roles.add(Role.HEAL);
                case MOVE_ONE_ATTACHED_ENERGY_TO_OWN_POKEMON -> roles.add(Role.MOVE_ENERGY);
                case COMPOSED -> { }
                case DISCARD_SELF_DRAW,SHUFFLE_SELF_DRAW_IF_DREW -> {roles.add(Role.DRAW);roles.add(Role.DISCARD);draw=Math.max(draw,e.amount());weak.add("离场抽牌需要保守评价");}
                default -> roles.add(Role.DEFEND);
            }
            program(a.effectProgram());
        }
        void program(EffectProgram p){for(var binding:p.triggers()){
            if(!binding.conditions().isEmpty())weak.add("条件/触发收益由引擎验证，策略只作保守估计");
            for(var e:binding.effects())effect(e);
        }}
        void effect(EffectDefinition e){
            types.add(e.type());if(!e.conditions().isEmpty())weak.add("条件/触发收益由引擎验证，策略只作保守估计");
            switch(e.type()){
                case "turn_choose_next_coin" -> roles.add(Role.COIN_CONTROL);
                case "draw" -> {roles.add(Role.DRAW);draw=Math.max(draw,number(e,"amount"));}
                case "take_prize_cards" -> {if(e.target()==EffectTarget.SELF)roles.add(Role.PRIZE);}
                case "draw_until_hand_size","draw_until" -> {roles.add(Role.DRAW);drawUntil=Math.max(drawUntil,number(e,"amount"));}
                case "search_deck","search_deck_per_bench","deck_search" -> roles.add(Role.SEARCH);
                case "discard_self_to_deck_bottom","search_discard_pile","recover","return_energy" -> roles.add(Role.RECOVER);
                case "bench_restored_from_hand" -> roles.add(Role.SEARCH);
                case "prepare_distinct_bench_energy","attach_energy","attach_energy_each_selected","attach_energy_each_named_bench" -> roles.add(Role.ATTACH);
                case "move_energy","redistribute_energy" -> roles.add(Role.MOVE_ENERGY);
                case "switch","switch_source_with_active","retreat","free_retreat","retreat_cost_modifier" -> roles.add(Role.SWITCH);
                case "force_switch","gust" -> roles.add(Role.GUST);
                case "heal","clear_status" -> roles.add(Role.HEAL);
                case "boost_attack_damage" -> {roles.add(Role.BOOST);weak.add("下回合增伤仅保守评价");}
                case "attack_damage_modifier" -> {if("DEALT".equals(e.arguments().get("direction"))&&number(e,"amount")>0){roles.add(Role.BOOST);if(e.conditions().isEmpty())boost=Math.max(boost,number(e,"amount"));}else roles.add(Role.DEFEND);}
                case "reduce_damage","prevent_damage","prevent_effects","modify_damage_taken","hit_points_modifier","tool_capacity" -> roles.add(Role.DEFEND);
                case "evolve","evolve_from_hand" -> roles.add(Role.EVOLVE);
                case "discard_hand" -> {roles.add(Role.DISCARD);handCost+=number(e,"minimum");}
                case "discard_energy","discard_source","discard_self","return_to_hand","self_damage","recoil" -> roles.add(Role.DISCARD);
                case "poison","burn","sleep","paralyze","confuse","damage_counter","bench_damage" -> roles.add(Role.STATUS);
                case "shuffle_hand","shuffle_hand_to_deck" -> resetHand=true;
                case "coin_flip","random_discard" -> {uncertain=true;weak.add("随机结果只保守评价");}
                case "conditional","sequence","opt_in","reveal","reveal_hand_cards" -> { }
                default -> weak.add("未细化策略的效果："+e.type());
            }
            if(e.arguments().containsKey("source")||e.arguments().containsKey("destination"))types.add(e.type()+":"+e.arguments().getOrDefault("source","")+":"+e.arguments().getOrDefault("destination",""));
            for(var child:e.effects())effect(child);
        }
    }
    private static int number(EffectDefinition e,String key){try{return Integer.parseInt(e.arguments().getOrDefault(key,"0"));}catch(NumberFormatException ignored){return 0;}}
}
