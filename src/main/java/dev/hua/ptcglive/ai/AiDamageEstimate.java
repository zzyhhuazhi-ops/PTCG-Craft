package dev.hua.ptcglive.ai;

import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

/** Strategy estimates from the filtered public view; never authorizes attacks or predicts hidden cards. */
final class AiDamageEstimate {
    private final AiPolicy.Context c;
    private final BattlefieldState.PokemonView source,defender;
    private final MatchClientView.Player own,opponent;
    private final Map<UUID,List<EnergyProfile.Unit>> projected;
    private AiDamageEstimate(AiPolicy.Context c,BattlefieldState.PokemonView source,BattlefieldState.PokemonView defender,Map<UUID,List<EnergyProfile.Unit>> projected){
        this.c=c;this.source=source;this.defender=defender;this.projected=projected;
        own=c.ours(source)?c.own:c.opponent;opponent=c.ours(source)?c.opponent:c.own;
    }
    static double estimate(AiPolicy.Context c,AttackDefinition a,BattlefieldState.PokemonView source,BattlefieldState.PokemonView defender,Map<UUID,List<EnergyProfile.Unit>> projected){
        if(source==null||defender==null)return AiPolicy.Context.expected(a);
        var e=new AiDamageEstimate(c,source,defender,projected);double value=AiPolicy.Context.expected(a)+a.resolveDamageModifiers(e.context());
        for(var binding:a.effectProgram().forTrigger(EffectTrigger.ON_ATTACK)){
            if(!Boolean.TRUE.equals(e.conditions(binding.conditions())))continue;
            for(var effect:binding.effects()){double extra=e.effect(effect);if(Double.isFinite(extra))value+=extra;}
        }
        return Math.max(0,value);
    }
    static boolean projectable(AiPolicy.Context c,AttackDefinition a,BattlefieldState.PokemonView source,BattlefieldState.PokemonView defender,Map<UUID,List<EnergyProfile.Unit>> projected){
        if(source==null||defender==null)return false;
        var e=new AiDamageEstimate(c,source,defender,projected);
        for(var b:a.effectProgram().triggers()){
            if(b.trigger()!=EffectTrigger.ON_ATTACK||e.conditions(b.conditions())==null)return false;
            for(var effect:b.effects())if(!Double.isFinite(e.effect(effect)))return false;
        }
        return true;
    }
    private DamageContext context(){var facts=c.facts(defender.topCardId());return new DamageContext(source.damage(),defender.damage(),own.bench().size(),opponent.bench().size(),own.prizeCount(),opponent.prizeCount(),source.conditions().size(),defender.conditions().size(),energy(source,null),energy(defender,null),source.attachedTools().size(),defender.attachedTools().size(),own.handSize(),opponent.handSize(),own.deckSize(),opponent.deckSize(),own.discard().size(),opponent.discard().size(),own.lostZone().size(),opponent.lostZone().size(),facts==null||facts.pokemon()==null?0:facts.pokemon().retreatCost());}
    private List<BattlefieldState.PokemonView> field(MatchClientView.Player p){var result=new ArrayList<>(p.bench());if(p.active()!=null)result.addFirst(p.active());return result;}
    private List<BattlefieldState.PokemonView> targets(EffectTarget target){return switch(target){case SELF->List.of(source);case OWN_ACTIVE->own.active()==null?List.of():List.of(own.active());case OPPONENT_ACTIVE->List.of(defender);case OWN_BENCH->own.bench();case OPPONENT_BENCH->opponent.bench();case ALL_OWN_POKEMON->field(own);case ALL_OPPONENT_POKEMON->field(opponent);default->List.of();};}
    private int energy(BattlefieldState.PokemonView mon,String type){
        var units=projected.get(mon.pokemonId());if(units==null){var facts=c.input.energy().get(mon.pokemonId());if(facts!=null)units=facts.units();}
        if(units==null){units=new ArrayList<>();for(String id:mon.attachedEnergyCardIds()){var f=c.facts(id);if(f!=null&&f.energy()!=null)units.addAll(f.energy().units());}}
        EnergyType wanted=type==null?null:EnergyType.valueOf(type);return (int)units.stream().filter(u->wanted==null||u.selectableTypes().contains(wanted)).count();
    }
    private Boolean conditions(List<ConditionDefinition> list){boolean unknown=false;for(var condition:list){var result=condition(condition);if(Boolean.FALSE.equals(result))return false;if(result==null)unknown=true;}return unknown?null:true;}
    private Boolean condition(ConditionDefinition condition){
        var args=condition.arguments();
        if(condition.type()==ConditionType.NOT){var child=condition(condition.children().getFirst());return child==null?null:!child;}
        if(condition.type()==ConditionType.AND)return conditions(condition.children());
        if(condition.type()==ConditionType.OR){boolean unknown=false;for(var child:condition.children()){var result=condition(child);if(Boolean.TRUE.equals(result))return true;if(result==null)unknown=true;}return unknown?null:false;}
        if(!Set.of("target","value","comparison","energy_type","status").containsAll(args.keySet()))return null;
        var target=EffectTarget.valueOf(args.getOrDefault("target","SELF"));var mons=targets(target);var mon=mons.isEmpty()?null:mons.getFirst();
        boolean theirs=target==EffectTarget.OPPONENT_ACTIVE||target==EffectTarget.OPPONENT_BENCH||target==EffectTarget.ALL_OPPONENT_POKEMON;var player=theirs?opponent:own;
        if(condition.type()==ConditionType.HAS_TOOL)return mon==null?null:!mon.attachedTools().isEmpty();
        if(condition.type()==ConditionType.HAS_ENERGY)return mon==null?null:energy(mon,args.get("energy_type"))>0;
        if(condition.type()==ConditionType.HAS_STATUS)return mon==null?null:args.getOrDefault("status","ANY").equals("ANY")?!mon.conditions().isEmpty():mon.conditions().stream().anyMatch(s->s.name().equals(args.get("status")));
        Integer count=switch(condition.type()){
            case ENERGY_COUNT->mons.stream().mapToInt(m->energy(m,args.get("energy_type"))).sum();
            case DAMAGE_COUNTER_COUNT->mon==null?null:mon.damage()/10;
            case PRIZE_COUNT->player.prizeCount();case HAND_SIZE->player.handSize();case DECK_SIZE->player.deckSize();
            case DISCARD_COUNT->player.discard().size();case LOST_ZONE_COUNT->player.lostZone().size();
            case BENCH_COUNT->player.bench().size();case POKEMON_COUNT->target==EffectTarget.SELF?field(own).size():mons.size();default->null;
        };
        if(count==null||!args.containsKey("value"))return null;int value=Integer.parseInt(args.get("value"));return switch(args.getOrDefault("comparison",">=")){case ">="->count>=value;case "<="->count<=value;case "=="->count==value;case ">"->count>value;case "<"->count<value;case "!="->count!=value;default->null;};
    }
    private double effect(EffectDefinition effect){
        var applies=conditions(effect.conditions());if(applies==null)return Double.NaN;if(!applies)return 0;
        if(effect.type().equals("conditional")){double sum=0;for(var child:effect.effects())sum+=effect(child);return sum;}
        if(!effect.effects().isEmpty())return Double.NaN;
        var args=effect.arguments();if(!Set.of("amount","energy_type","count_mode","prize_count").containsAll(args.keySet()))return Double.NaN;
        int amount=Integer.parseInt(args.getOrDefault("amount","0"));var mons=targets(effect.target());
        return switch(effect.type()){
            case "damage"->effect.target()==EffectTarget.OPPONENT_ACTIVE?amount:Double.NaN;
            case "damage_per_energy"->args.getOrDefault("count_mode","UNITS").equals("UNITS")?amount*mons.stream().mapToInt(m->energy(m,args.get("energy_type"))).sum():Double.NaN;
            case "damage_per_bench"->amount*(effect.target()==EffectTarget.SELF?own.bench().size():opponent.bench().size());
            case "damage_per_damage_counter"->amount*mons.stream().mapToInt(m->m.damage()/10).sum();
            case "damage_per_special_condition"->amount*mons.stream().mapToInt(m->m.conditions().size()).sum();
            case "damage_per_prize"->args.getOrDefault("prize_count","REMAINING").equals("REMAINING")?amount*(effect.target()==EffectTarget.SELF?own.prizeCount():opponent.prizeCount()):Double.NaN;
            case "damage_per_pokemon"->switch(args.getOrDefault("count_mode","POKEMON")){case "POKEMON"->amount*mons.size();case "DAMAGE_COUNTERS"->amount*mons.stream().mapToInt(m->m.damage()/10).sum();case "ATTACHED_TOOLS"->amount*mons.stream().mapToInt(m->m.attachedTools().size()).sum();default->Double.NaN;};
            default->Double.NaN;
        };
    }
}
