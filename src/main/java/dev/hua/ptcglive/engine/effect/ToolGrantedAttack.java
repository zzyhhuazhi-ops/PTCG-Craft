package dev.hua.ptcglive.engine.effect;

import dev.hua.ptcglive.engine.field.AttackDefinition;
import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.rules.EffectSupport;
import java.util.*;

/** Reviewed Tool attack, materialized with the actual holder's damage type. */
public final class ToolGrantedAttack {
    private ToolGrantedAttack() { }
    private static final Map<String,String> TEAM_HOSTS=Map.of("aqua-diffuser","Team Aqua","magma-pointer","Team Magma");
    private static final Map<String,String> Z_HOST_ATTACKS=Map.of(
        "speeding-skystrike-gx","air-slash","barreling-blitz-gx","tackle","destructive-drake-gx","dragon-claw");
    private static final Map<String,String> LEGACY_HOSTS=Map.of(
        "g-booster","Genesect-EX","g-scope","Genesect-EX","all-cells-burn","Zygarde-EX",
        "matchless-maelstrom","RAPID_STRIKE","furious-anger","SINGLE_STRIKE","gravdrop","RAPID_STRIKE",
        "bullet-breakthrough","SINGLE_STRIKE","meteor","RAPID_STRIKE","superstrong-slash","SINGLE_STRIKE");
    public static boolean handles(EffectDefinition effect) {return effect.type().equals("grant_attack");}
    public static void validate(EffectDefinition effect) {
        var args=effect.arguments();
        if(!handles(effect)||effect.target()!=EffectTarget.SELF||!effect.effects().isEmpty()||!effect.conditions().isEmpty())
            throw new IllegalArgumentException("道具授予招式必须是SELF的独立持续效果");
        if("delta-gift".equals(args.get("attack_id"))) {
            if(!args.keySet().equals(Set.of("attack_id","name_zh"))||args.getOrDefault("name_zh","").isBlank())throw new IllegalArgumentException("德尔塔馈赠参数无效");
            return;
        }
        String legacyHost=LEGACY_HOSTS.get(args.get("attack_id"));
        String zHost=Z_HOST_ATTACKS.get(args.get("attack_id"));
        String teamHost=TEAM_HOSTS.get(args.get("attack_id"));
        if(teamHost!=null) {
            if(!args.keySet().equals(Set.of("attack_id","name_zh","holder_name_contains"))||!teamHost.equals(args.get("holder_name_contains"))||args.getOrDefault("name_zh","").isBlank())
                throw new IllegalArgumentException("队伍专属道具招式须限定印刷宝可梦名称");
            return;
        }
        if(zHost!=null) {
            if(!args.keySet().equals(Set.of("attack_id","name_zh","holder_attack_id"))||!zHost.equals(args.get("holder_attack_id"))||args.getOrDefault("name_zh","").isBlank())
                throw new IllegalArgumentException("Z道具须限定宿主自身拥有的对应招式");
            return;
        }
        if(legacyHost!=null) {
            String field=legacyHost.endsWith("_STRIKE")?"holder_battle_style":"pokemon_name";
            if(!args.keySet().equals(Set.of("attack_id","name_zh",field))||!legacyHost.equals(args.get(field))||args.getOrDefault("name_zh","").isBlank())
                throw new IllegalArgumentException("旧版道具招式的宿主限制不符");
            return;
        }
        boolean geobuster=args.keySet().equals(Set.of("attack_id","name_zh","pokemon_name","amount","energy_type","energy_count","discard_all_energy"))
            &&"geobuster".equals(args.get("attack_id"))&&"Mega Zygarde ex".equals(args.get("pokemon_name"))
            &&"350".equals(args.get("amount"))&&"FIGHTING".equals(args.get("energy_type"))
            &&"4".equals(args.get("energy_count"))&&"true".equals(args.get("discard_all_energy"));
        boolean fluorite=args.keySet().equals(Set.of("attack_id","name_zh","energy_cost","heal_families","discard_all_energy"))
            &&"fluorite".equals(args.get("attack_id"))&&"GRASS:1|WATER:1|PSYCHIC:1".equals(args.get("energy_cost"))
            &&"TERA".equals(args.get("heal_families"))&&"true".equals(args.get("discard_all_energy"));
        boolean crisis=args.keySet().equals(Set.of("attack_id","name_zh","amount","energy_type","energy_count","opponent_prizes"))
            &&"crisis-punch".equals(args.get("attack_id"))&&"280".equals(args.get("amount"))&&"COLORLESS".equals(args.get("energy_type"))
            &&"3".equals(args.get("energy_count"))&&"1".equals(args.get("opponent_prizes"));
        boolean blindside=args.keySet().equals(Set.of("attack_id","name_zh","amount","energy_type","energy_count","target_damage_counters_min"))
            &&"blindside".equals(args.get("attack_id"))&&"100".equals(args.get("amount"))&&"COLORLESS".equals(args.get("energy_type"))
            &&"3".equals(args.get("energy_count"))&&"1".equals(args.get("target_damage_counters_min"));
        boolean devolution=args.keySet().equals(Set.of("attack_id","name_zh","energy_type","energy_count","devolve_all_opponent"))
            &&"devolution".equals(args.get("attack_id"))&&"COLORLESS".equals(args.get("energy_type"))
            &&"1".equals(args.get("energy_count"))&&"true".equals(args.get("devolve_all_opponent"));
        boolean evolution=args.keySet().equals(Set.of("attack_id","name_zh","energy_type","energy_count","target_count","bench_only"))
            &&"evolution".equals(args.get("attack_id"))&&"COLORLESS".equals(args.get("energy_type"))
            &&"1".equals(args.get("energy_count"))&&"2".equals(args.get("target_count"))&&"true".equals(args.get("bench_only"));
        boolean turbo=args.keySet().equals(Set.of("attack_id","name_zh","energy_type","energy_count","amount","basic_energy_only","bench_only"))
            &&"turbo-energize".equals(args.get("attack_id"))&&"COLORLESS".equals(args.get("energy_type"))
            &&"1".equals(args.get("energy_count"))&&"2".equals(args.get("amount"))
            &&"true".equals(args.get("basic_energy_only"))&&"true".equals(args.get("bench_only"));
        boolean starGravity=args.keySet().equals(Set.of("attack_id","name_zh","energy_type","energy_count","remaining_hp","pokemon_families","holder_families"))
            &&"star-gravity".equals(args.get("attack_id"))&&"COLORLESS".equals(args.get("energy_type"))
            &&"3".equals(args.get("energy_count"))&&"100".equals(args.get("remaining_hp"))
            &&"V".equals(args.get("pokemon_families"))&&"V".equals(args.get("holder_families"));
        if((!geobuster&&!fluorite&&!crisis&&!blindside&&!devolution&&!evolution&&!turbo&&!starGravity)||args.getOrDefault("name_zh","").isBlank())
            throw new IllegalArgumentException("道具授予招式不是已审核的完整卡文");
    }
    public static EffectDefinition fromProgram(EffectProgram program) {
        var found=program.triggers().stream().flatMap(b->b.effects().stream()).filter(ToolGrantedAttack::handles).toList();
        if(found.isEmpty())return null;
        if(found.size()!=1||program.triggers().size()!=1||program.triggers().getFirst().trigger()!=EffectTrigger.PASSIVE
            ||!program.triggers().getFirst().conditions().isEmpty()||program.triggers().getFirst().effects().size()!=1)
            throw new IllegalArgumentException("道具招式须为独立持续授权");
        validate(found.getFirst());return found.getFirst();
    }
    public static AttackDefinition materialize(EffectDefinition effect,EnergyType holderType) {
        validate(effect);Objects.requireNonNull(holderType);
        var args=effect.arguments();
        if("delta-gift".equals(args.get("attack_id"))) {
            var atom=new EffectDefinition("attach_energy",EffectTarget.SELECT_OWN_POKEMON,
                Map.of("source","DECK","amount","60","minimum","0","basic_energy_only","true","target_scope","OWN_FIELD","distribution","ANY","one_per_target","true","target_tool_definition_ids","me6_en_100"),List.of(),List.of());
            AttachEnergyEffect.from(atom);
            return zeroDamageAttack("delta-gift",args.get("name_zh"),holderType,new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.AFTER_ATTACK,List.of(),List.of(atom)))));
        }
        if(TEAM_HOSTS.containsKey(args.get("attack_id"))) {
            boolean aqua=args.get("attack_id").equals("aqua-diffuser");
            var attack=new AttackDefinition(args.get("attack_id"),args.get("name_zh"),Map.of(aqua?EnergyType.WATER:EnergyType.FIGHTING,1),
                aqua?0:20,holderType,EffectSupport.SUPPORTED,null,List.of(),null,null,List.of(),
                aqua?AttackDefinition.DamageTarget.OPPONENT_ACTIVE:AttackDefinition.DamageTarget.OPPONENT_ANY,0,1,false,0,0,false);
            return aqua?attack.withEffectProgram(new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.AFTER_ATTACK,List.of(),List.of(
                new EffectDefinition("confuse",EffectTarget.OPPONENT_ACTIVE,Map.of(),List.of(),List.of()),
                new EffectDefinition("poison",EffectTarget.OPPONENT_ACTIVE,Map.of(),List.of(),List.of())))))):attack;
        }
        if(Z_HOST_ATTACKS.containsKey(args.get("attack_id")))return zAttack(args.get("attack_id"),args.get("name_zh"),holderType);
        if(LEGACY_HOSTS.containsKey(args.get("attack_id")))return legacyAttack(args.get("attack_id"),args.get("name_zh"),holderType);
        if("fluorite".equals(args.get("attack_id"))) {
            var program=new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.AFTER_ATTACK,List.of(),List.of(
                new EffectDefinition("discard_energy",EffectTarget.SELF,Map.of("all","true"),List.of(),List.of()),
                new EffectDefinition("heal",EffectTarget.ALL_OWN_POKEMON,Map.of("mode","ALL","pokemon_families","TERA"),List.of(),List.of())
            ))));
            return new AttackDefinition("fluorite",args.get("name_zh"),Map.of(EnergyType.GRASS,1,EnergyType.WATER,1,EnergyType.PSYCHIC,1),
                0,holderType,EffectSupport.SUPPORTED,null,List.of(),null,null,List.of(),
                AttackDefinition.DamageTarget.OPPONENT_ACTIVE,0,1,false,0,0,false).withEffectProgram(program);
        }
        if("crisis-punch".equals(args.get("attack_id")))return new AttackDefinition("crisis-punch",args.get("name_zh"),Map.of(EnergyType.COLORLESS,3),
            280,holderType,EffectSupport.SUPPORTED,null,List.of(),null,null,List.of(),
            AttackDefinition.DamageTarget.OPPONENT_ACTIVE,0,1,false,0,0,false);
        if("blindside".equals(args.get("attack_id"))) {
            var program=new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_ATTACK,List.of(),List.of(
                new EffectDefinition("target_filter",EffectTarget.ALL_OPPONENT_POKEMON,Map.of("damage_counters_min","1"),List.of(),List.of())
            ))));
            return new AttackDefinition("blindside",args.get("name_zh"),Map.of(EnergyType.COLORLESS,3),
                100,holderType,EffectSupport.SUPPORTED,null,List.of(),null,null,List.of(),
                AttackDefinition.DamageTarget.OPPONENT_ANY,0,1,false,0,0,false).withEffectProgram(program);
        }
        if("star-gravity".equals(args.get("attack_id"))) {
            var program=new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.AFTER_ATTACK,List.of(),List.of(
                new EffectDefinition("damage_counter",EffectTarget.ALL_OPPONENT_POKEMON,
                    Map.of("remaining_hp","100","pokemon_families","V"),List.of(),List.of())
            ))));
            return new AttackDefinition("star-gravity",args.get("name_zh"),Map.of(EnergyType.COLORLESS,3),
                0,holderType,EffectSupport.SUPPORTED,null).withEffectProgram(program)
                .withUsageLimit(new SharedUsageLimit(SharedUsageLimit.VSTAR,SharedUsageLimit.Period.GAME));
        }
        if("devolution".equals(args.get("attack_id"))) {
            var program=new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.AFTER_ATTACK,List.of(),List.of(
                new EffectDefinition("devolve",EffectTarget.ALL_OPPONENT_POKEMON,Map.of(),List.of(),List.of())
            ))));
            return zeroDamageAttack("devolution",args.get("name_zh"),holderType,program);
        }
        if("evolution".equals(args.get("attack_id"))) {
            var program=new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.AFTER_ATTACK,List.of(),List.of(
                new EffectDefinition("evolve_selected_from_deck",EffectTarget.SELECT_OWN_POKEMON,
                    Map.of("target_count","2","minimum","0","evolutions","1","bench_only","true"),List.of(),List.of())
            ))));
            return zeroDamageAttack("evolution",args.get("name_zh"),holderType,program);
        }
        if("turbo-energize".equals(args.get("attack_id"))) {
            var program=new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.AFTER_ATTACK,List.of(),List.of(
                new EffectDefinition("attach_energy",EffectTarget.SELECT_OWN_POKEMON,
                    Map.of("source","DECK","amount","2","minimum","0","target_scope","OWN_BENCH","distribution","ANY","basic_energy_only","true"),List.of(),List.of())
            ))));
            return zeroDamageAttack("turbo-energize",args.get("name_zh"),holderType,program);
        }
        return new AttackDefinition(args.get("attack_id"),args.get("name_zh"),Map.of(EnergyType.FIGHTING,4),
            350,holderType,EffectSupport.SUPPORTED,null,List.of(),null,null,List.of(),
            AttackDefinition.DamageTarget.OPPONENT_ACTIVE,0,1,true,0,0,false);
    }
    private static AttackDefinition zeroDamageAttack(String id,String name,EnergyType holderType,EffectProgram program) {
        return new AttackDefinition(id,name,Map.of(EnergyType.COLORLESS,1),0,holderType,EffectSupport.SUPPORTED,null).withEffectProgram(program);
    }
    private static AttackDefinition zAttack(String id,String name,EnergyType type) {
        var program=switch(id) {
            case "speeding-skystrike-gx" -> new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.AFTER_ATTACK,List.of(),List.of(
                new EffectDefinition("prevent_effects",EffectTarget.SELF,Map.of("source","ATTACK","from","OPPONENT","scope","DAMAGE_AND_EFFECTS","duration","OPPONENT_TURN_END"),List.of(),List.of())))));
            case "barreling-blitz-gx" -> new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_ATTACK,List.of(),List.of(
                new EffectDefinition("coin_flip",EffectTarget.SELF,Map.of("until","TAILS"),List.of(),List.of(
                    new EffectDefinition("repeat_per_heads",EffectTarget.SELF,Map.of(),List.of(),List.of(
                        new EffectDefinition("damage",EffectTarget.OPPONENT_ACTIVE,Map.of("amount","40"),List.of(),List.of())))))))));
            case "destructive-drake-gx" -> new EffectProgram(List.of(
                new TriggeredEffects(EffectTrigger.BEFORE_ATTACK,List.of(),List.of(new EffectDefinition("plan_energy_discard",EffectTarget.SELF,Map.of("all","true","card_kind","BASIC","bind","dragon_energy"),List.of(),List.of()))),
                new TriggeredEffects(EffectTrigger.ON_ATTACK,List.of(),List.of(new EffectDefinition("damage_per_bound",EffectTarget.OPPONENT_ACTIVE,Map.of("bind","dragon_energy","amount","80"),List.of(),List.of()))),
                new TriggeredEffects(EffectTrigger.AFTER_ATTACK,List.of(),List.of(new EffectDefinition("commit_energy_discard",EffectTarget.SELF,Map.of("ref","dragon_energy"),List.of(),List.of())))));
            default -> throw new IllegalArgumentException("Unknown Z attack");
        };
        int damage=id.equals("speeding-skystrike-gx")?180:id.equals("barreling-blitz-gx")?200:0;
        return new AttackDefinition(id,name,Map.of(EnergyType.COLORLESS,id.equals("destructive-drake-gx")?3:4),damage,type,EffectSupport.SUPPORTED,null)
            .withEffectProgram(program).withUsageLimit(new SharedUsageLimit(SharedUsageLimit.GX,SharedUsageLimit.Period.GAME));
    }
    private static AttackDefinition legacyAttack(String id,String name,EnergyType type) {
        var cost=switch(id) {
            case "g-booster","g-scope" -> Map.of(EnergyType.GRASS,2,EnergyType.COLORLESS,1);
            case "all-cells-burn" -> Map.of(EnergyType.FIGHTING,2,EnergyType.COLORLESS,1);
            case "matchless-maelstrom" -> Map.of(EnergyType.FIGHTING,1,EnergyType.COLORLESS,2);
            case "furious-anger" -> Map.of(EnergyType.FIGHTING,1);
            case "gravdrop" -> Map.of(EnergyType.LIGHTNING,1,EnergyType.COLORLESS,1);
            case "bullet-breakthrough" -> Map.of(EnergyType.FIRE,1,EnergyType.COLORLESS,2);
            case "meteor" -> Map.of(EnergyType.FIRE,1,EnergyType.LIGHTNING,1);
            case "superstrong-slash" -> Map.of(EnergyType.FIGHTING,1,EnergyType.METAL,2,EnergyType.COLORLESS,2);
            default -> throw new IllegalArgumentException("Unknown reviewed Tool attack");
        };
        int damage=switch(id) {case "g-booster","all-cells-burn"->200;case "g-scope"->100;case "matchless-maelstrom"->30;
            case "furious-anger","gravdrop"->10;case "bullet-breakthrough"->120;case "meteor"->90;default->300;};
        var target=switch(id) {case "g-scope"->AttackDefinition.DamageTarget.OPPONENT_BENCH;
            case "meteor"->AttackDefinition.DamageTarget.OPPONENT_ANY;
            case "matchless-maelstrom"->AttackDefinition.DamageTarget.ALL_OPPONENT_POKEMON;default->AttackDefinition.DamageTarget.OPPONENT_ACTIVE;};
        var modifiers=switch(id) {
            case "furious-anger"->List.of(new dev.hua.ptcglive.engine.field.DamageModifier(dev.hua.ptcglive.engine.field.DamageModifier.Basis.ATTACKER_DAMAGE_COUNTERS,10,0));
            case "gravdrop"->List.of(new dev.hua.ptcglive.engine.field.DamageModifier(dev.hua.ptcglive.engine.field.DamageModifier.Basis.DEFENDER_ATTACHED_ENERGY_COUNT,50,0));
            default->List.<dev.hua.ptcglive.engine.field.DamageModifier>of();
        };
        var attack=new AttackDefinition(id,name,cost,damage,type,EffectSupport.SUPPORTED,null,List.of(),null,null,modifiers,
            target,0,target==AttackDefinition.DamageTarget.ALL_OPPONENT_POKEMON?0:1,id.equals("superstrong-slash"),0,0,id.equals("bullet-breakthrough"),0,
            id.equals("g-booster")||id.equals("bullet-breakthrough"));
        int discard=switch(id){case "g-booster","meteor"->2;case "all-cells-burn"->3;default->0;};
        if(discard>0)attack=attack.withEffectProgram(new EffectProgram(List.of(new TriggeredEffects(
            id.equals("meteor")?EffectTrigger.BEFORE_ATTACK:EffectTrigger.AFTER_ATTACK,List.of(),List.of(
                new EffectDefinition("discard_energy",EffectTarget.SELF,Map.of("amount",Integer.toString(discard),"minimum",Integer.toString(discard),"count_mode","UNITS"),List.of(),List.of()))))));
        return attack;
    }
}
