package dev.hua.ptcglive.engine.effect;

import java.util.Set;
import dev.hua.ptcglive.engine.field.EnergyType;

/** Fixed counters, counters per opposing Prize taken, or counters up to a remaining-HP ceiling. Never healing or attack damage. */
public record DamageCounterEffect(int amount,int remainingHp,boolean perOpponentPrizeTaken, boolean fromTriggerDamage, Set<String> previousTurnAttackIds,int counterMultiplier,EnergyType sourceEnergyType) {
    public DamageCounterEffect(int amount,int remainingHp,boolean perOpponentPrizeTaken,boolean fromTriggerDamage,Set<String> previousTurnAttackIds,int counterMultiplier){this(amount,remainingHp,perOpponentPrizeTaken,fromTriggerDamage,previousTurnAttackIds,counterMultiplier,null);}
    public DamageCounterEffect(int amount,int remainingHp,boolean perOpponentPrizeTaken,boolean fromTriggerDamage,Set<String> previousTurnAttackIds){this(amount,remainingHp,perOpponentPrizeTaken,fromTriggerDamage,previousTurnAttackIds,0);}
    public DamageCounterEffect { previousTurnAttackIds=Set.copyOf(previousTurnAttackIds); }
    public DamageCounterEffect(int amount,int remainingHp,boolean perOpponentPrizeTaken,boolean fromTriggerDamage) {this(amount,remainingHp,perOpponentPrizeTaken,fromTriggerDamage,Set.of());}
    public boolean extraTurnOnKo(){return !previousTurnAttackIds.isEmpty();}
    public DamageCounterEffect(int amount,int remainingHp,boolean perOpponentPrizeTaken) {this(amount,remainingHp,perOpponentPrizeTaken,false);}
    public static boolean wholeOpponentField(EffectDefinition effect){return effect.type().equals("damage_counter")&&effect.target()==EffectTarget.ALL_OPPONENT_POKEMON;}
    public static boolean expandedAttack(EffectDefinition effect){return effect.type().equals("damage_counter")&&(wholeOpponentField(effect)||effect.arguments().containsKey("counter_multiplier")||"BENCH".equals(effect.arguments().get("position")));}
    public static DamageCounterEffect from(EffectDefinition effect) {
        if(!effect.type().equals("damage_counter")||DamageCounterDistribution.handles(effect)||!effect.effects().isEmpty()
            ||!Set.of("amount","remaining_hp","per_opponent_prize_taken","target_count","position","from_trigger_damage","extra_turn_on_ko","previous_turn_attack_ids","bind","bind_mode","counter_multiplier","per_source_energy_type","per_own_pokemon_names","per_discard_basic_energy_type","per_hand_card","basic_pokemon_only","has_abilities","status","damage_counters_min","except_name_contains","except_name","pokemon_families").containsAll(effect.arguments().keySet()))
            throw new IllegalArgumentException("指示物效果包含未支持参数");
        var a=effect.arguments();
        if(a.containsKey("per_own_pokemon_names")) {
            boolean attack=effect.target()==EffectTarget.ALL_OPPONENT_POKEMON&&a.equals(java.util.Map.of("amount","1","per_own_pokemon_names","Maushold"));
            boolean reaction=effect.target()==EffectTarget.ATTACKING_POKEMON&&a.equals(java.util.Map.of("amount","3","per_own_pokemon_names","Tandemaus|Maushold|Maushold ex"));
            if(!attack&&!reaction)throw new IllegalArgumentException("同名己方场上计数仅核验一家鼠全场指示物与团结一致反击");
        }
        if(a.containsKey("per_hand_card") && (!a.keySet().equals(Set.of("amount","per_hand_card"))
            || !"true".equals(a.get("per_hand_card")) || effect.target()!=EffectTarget.OPPONENT_ACTIVE))
            throw new IllegalArgumentException("按手牌放指示物须为对方战斗宝可梦与明确的每张数量");
        if(a.containsKey("position")) {
            boolean selected=effect.target()==EffectTarget.SELECT_OPPONENT_POKEMON
                &&a.keySet().equals(Set.of("amount","target_count","position"));
            boolean wholeBench=wholeOpponentField(effect)&&(a.keySet().equals(Set.of("amount","position","damage_counters_min"))
                ||a.keySet().equals(Set.of("remaining_hp","position")));
            if(!"BENCH".equals(a.get("position"))||!selected&&!wholeBench)
                throw new IllegalArgumentException("备战指示物须明确选择多个备战目标、受伤备战目标或备战剩余HP上限");
        }
        boolean stadiumFamilies=Set.of(EffectTarget.ALL_OWN_POKEMON,EffectTarget.ALL_OPPONENT_POKEMON).contains(effect.target())
            &&(a.equals(java.util.Map.of("amount","1","pokemon_families","GX|EX_LEGACY"))
                ||a.equals(java.util.Map.of("amount","2","pokemon_families","MEGA_EVOLUTION_LEGACY")));
        if(a.containsKey("pokemon_families")&&!stadiumFamilies&&(!a.keySet().equals(Set.of("remaining_hp","pokemon_families"))
            ||!wholeOpponentField(effect)||!"V".equals(a.get("pokemon_families"))))
            throw new IllegalArgumentException("V家族剩余HP指示物须只作用于对方全场");
        if(a.containsKey("per_discard_basic_energy_type")) {
            dev.hua.ptcglive.engine.field.EnergyType.valueOf(a.get("per_discard_basic_energy_type"));
            int each=Integer.parseInt(a.getOrDefault("amount","0"));
            if(effect.target()!=EffectTarget.SELECT_OPPONENT_POKEMON||!a.keySet().equals(Set.of("amount","per_discard_basic_energy_type"))||each<1||each>10)
                throw new IllegalArgumentException("按弃牌区基本能量放置指示物须选择1只对方宝可梦并声明每张1至10个");
            return new DamageCounterEffect(each,0,false);
        }
        if(a.containsKey("damage_counters_min")) {
            int minimum=Integer.parseInt(a.get("damage_counters_min"));
            if(effect.target()!=EffectTarget.ALL_OPPONENT_POKEMON||minimum<1||minimum>60)
                throw new IllegalArgumentException("受伤宝可梦指示物筛选须作用于对方全场且为1至60");
        }
        if(a.containsKey("status")) {
            if(effect.target()!=EffectTarget.ALL_OPPONENT_POKEMON)throw new IllegalArgumentException("异常状态指示物筛选须作用于对方全场");
            dev.hua.ptcglive.engine.field.SpecialCondition.valueOf(a.get("status"));
        }
        if(a.containsKey("basic_pokemon_only")&&(!"true".equals(a.get("basic_pokemon_only"))||effect.target()!=EffectTarget.ALL_OPPONENT_POKEMON))
            throw new IllegalArgumentException("基础宝可梦指示物筛选只接受对方全场与true");
        if(a.containsKey("has_abilities")&&!"true".equals(a.get("has_abilities")))
            throw new IllegalArgumentException("特性筛选须明确true");
        if(a.containsKey("except_name_contains")&&(effect.target()!=EffectTarget.ALL_OWN_POKEMON&&effect.target()!=EffectTarget.ALL_OPPONENT_POKEMON
            ||!"true".equals(a.get("has_abilities"))||a.get("except_name_contains").isBlank()))
            throw new IllegalArgumentException("名称例外只用于带特性的整方场上指示物效果");
        if(a.containsKey("except_name")&&(!a.keySet().equals(Set.of("amount","has_abilities","except_name"))
            ||!Set.of(EffectTarget.ALL_OWN_POKEMON,EffectTarget.ALL_OPPONENT_POKEMON).contains(effect.target())
            ||!"true".equals(a.get("has_abilities"))||a.get("except_name").isBlank()))
            throw new IllegalArgumentException("精确名称例外须用于整方带特性的宝可梦");
        if(a.containsKey("per_source_energy_type")) {
            if(!a.keySet().equals(Set.of("amount","per_source_energy_type"))||effect.target()!=EffectTarget.ATTACKING_POKEMON)
                throw new IllegalArgumentException("按来源能量反击须仅指定每单位数量、属性及实际攻击者");
            int amount=Integer.parseInt(a.get("amount"));
            if(amount<1||amount>10)throw new IllegalArgumentException("每能量单位的反击指示物须为1至10个");
            return new DamageCounterEffect(amount,0,false,false,Set.of(),0,EnergyType.valueOf(a.get("per_source_energy_type")));
        }
        if(a.containsKey("counter_multiplier")) {
            if(wholeOpponentField(effect)&&a.equals(java.util.Map.of("counter_multiplier","2")))
                return new DamageCounterEffect(0,0,false,false,Set.of(),2);
            if(!a.keySet().equals(Set.of("counter_multiplier","target_count"))||effect.target()!=EffectTarget.SELECT_OPPONENT_POKEMON)
                throw new IllegalArgumentException("指示物倍增须明确选择对方多个目标，不能混用其它数量来源");
            int multiplier=Integer.parseInt(a.get("counter_multiplier")),targets=Integer.parseInt(a.get("target_count"));
            if(multiplier<2||multiplier>4||targets<2||targets>6)throw new IllegalArgumentException("指示物总量倍数须为2至4，目标数须为2至6");
            return new DamageCounterEffect(0,0,false,false,Set.of(),multiplier);
        }
        boolean successBinding=effect.target()==EffectTarget.OPPONENT_ACTIVE&&a.keySet().equals(Set.of("remaining_hp","bind","bind_mode"))&&"10".equals(a.get("remaining_hp"))&&"SUCCESS".equals(a.get("bind_mode"));
        if(a.containsKey("bind_mode")&&!successBinding)throw new IllegalArgumentException("成功布尔计数仅支持对手战斗宝可梦剩余10HP指示物效果");
        if(a.containsKey("bind")) {
            if(!successBinding&&(!a.keySet().equals(Set.of("amount","bind"))||!(effect.target()==EffectTarget.ATTACKING_POKEMON
                ||effect.target()==EffectTarget.SELF&&Integer.parseInt(a.getOrDefault("amount","0"))>=1&&Integer.parseInt(a.getOrDefault("amount","0"))<=12
                ||effect.target()==EffectTarget.SELECT_OPPONENT_POKEMON&&"2".equals(a.get("amount")))))throw new IllegalArgumentException("成功放置计数仅核验实际攻击者、自身1至12个或所选对手2个指示物");
            AttackCountBindings.name(a.get("bind"));
        }
        if(a.containsKey("extra_turn_on_ko")||a.containsKey("previous_turn_attack_ids")) {
            if(!a.keySet().equals(Set.of("amount","extra_turn_on_ko","previous_turn_attack_ids"))
                ||!"true".equals(a.get("extra_turn_on_ko"))||effect.target()!=EffectTarget.SELECT_OPPONENT_POKEMON)
                throw new IllegalArgumentException("指示物昏厥额外回合须明确单个对方目标及上次自己回合招式限制");
            int count=Integer.parseInt(a.get("amount"));if(count<1||count>60)throw new IllegalArgumentException("指示物数量须为1至60");
            return new DamageCounterEffect(count,0,false,false,TurnHistoryCondition.values(a.get("previous_turn_attack_ids")));
        }
        if(a.containsKey("from_trigger_damage")) {
            if(!a.keySet().equals(Set.of("from_trigger_damage"))||!"true".equals(a.get("from_trigger_damage"))||effect.target()!=EffectTarget.ATTACKING_POKEMON)throw new IllegalArgumentException("事件伤害指示物必须只指定攻击者");
            return new DamageCounterEffect(0,0,false,true);
        }
        if(a.containsKey("target_count")) {
            int count=Integer.parseInt(a.get("target_count"));
            if(count<2||count>6||!Set.of(EffectTarget.SELECT_OWN_POKEMON,EffectTarget.SELECT_OPPONENT_POKEMON).contains(effect.target()))
                throw new IllegalArgumentException("多个指示物目标须为2至6只公开选择的宝可梦");
        }
        if(a.containsKey("remaining_hp")) {
            if(a.containsKey("amount")||a.containsKey("per_opponent_prize_taken"))throw new IllegalArgumentException("剩余HP指示物不能混用固定数量");
            int hp=Integer.parseInt(a.get("remaining_hp"));
            if(hp<10||hp>1000||hp%10!=0)throw new IllegalArgumentException("剩余HP须为10至1000的10倍数");
            return new DamageCounterEffect(0,hp,false);
        }
        int amount=Integer.parseInt(a.get("amount"));
        if(amount<1||amount>60)throw new IllegalArgumentException("指示物数量须为1至60");
        boolean scaled=a.containsKey("per_opponent_prize_taken");
        if(scaled&&(!"true".equals(a.get("per_opponent_prize_taken"))||amount>10))throw new IllegalArgumentException("按奖赏放指示物须为true且每张最多10个");
        return new DamageCounterEffect(amount,0,scaled);
    }
    public static boolean basicPokemonOnly(EffectDefinition effect){return "true".equals(effect.arguments().get("basic_pokemon_only"));}
    public static PokemonFamilyFilter pokemonFamilies(EffectDefinition effect){return PokemonFamilyFilter.fromWire(effect.arguments().get("pokemon_families"),null);}
    public static boolean requiresAbilities(EffectDefinition effect){return "true".equals(effect.arguments().get("has_abilities"));}
    public static String exceptNameContains(EffectDefinition effect){return effect.arguments().get("except_name_contains");}
    public static dev.hua.ptcglive.engine.field.SpecialCondition requiredStatus(EffectDefinition effect) {
        return effect.arguments().containsKey("status")?dev.hua.ptcglive.engine.field.SpecialCondition.valueOf(effect.arguments().get("status")):null;
    }
    public static Set<String> restrictedPreviousAttacks(EffectProgram program) {
        var result=new java.util.LinkedHashSet<String>();
        for(var binding:program.triggers())for(var effect:binding.effects())collectRestrictions(effect,result);
        return Set.copyOf(result);
    }
    private static void collectRestrictions(EffectDefinition effect,Set<String> result) {
        if(effect.type().equals("damage_counter")&&effect.arguments().containsKey("extra_turn_on_ko"))result.addAll(from(effect).previousTurnAttackIds());
        effect.effects().forEach(e->collectRestrictions(e,result));
    }
    public int counters(int hp,int damage,int prizesTaken) { return counters(hp,damage,prizesTaken,0); }
    public int counters(int hp,int damage,int prizesTaken,int triggerDamage) {
        if(sourceEnergyType!=null)throw new IllegalStateException("按能量反击须由服务器查询当前来源的供能单位");
        if(counterMultiplier>0)return Math.multiplyExact(damage/10,counterMultiplier-1);
        if(fromTriggerDamage)return triggerDamage/10;
        if(remainingHp>0)return Math.max(0,(hp-damage-remainingHp)/10);
        return perOpponentPrizeTaken?Math.multiplyExact(amount,prizesTaken):amount;
    }
    public int sourceEnergyCounters(int units) {
        if(sourceEnergyType==null||units<0)throw new IllegalArgumentException("缺少有效的来源能量计数");
        return Math.multiplyExact(amount,units);
    }
}
