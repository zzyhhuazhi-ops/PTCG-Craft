package dev.hua.ptcglive.engine.effect;

import dev.hua.ptcglive.engine.field.EnergyProfile;
import dev.hua.ptcglive.engine.field.EnergyType;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Read-only supplied-Energy query. A type predicate never reserves or spends a payment unit. */
public record EnergyCountQuery(EnergyType energyType,Mode mode,CardKind cardKind,Set<String> definitionIds) {
    public enum Mode { UNITS,CARDS,DISTINCT_TYPES }
    public enum CardKind { ANY,BASIC,SPECIAL }
    public record Contribution(EnergyProfile profile,boolean basicEnergy,String definitionId) {
        public Contribution(EnergyProfile profile,boolean basicEnergy){this(profile,basicEnergy,null);}
        public Contribution {Objects.requireNonNull(profile,"缺少能量供给资料");}
    }
    public EnergyCountQuery(EnergyType type,Mode mode,CardKind kind){this(type,mode,kind,Set.of());}
    public EnergyCountQuery(EnergyType type,Mode mode) {this(type,mode,CardKind.ANY);}
    public static final EnergyCountQuery ALL_UNITS=new EnergyCountQuery(null,Mode.UNITS);
    public static final Set<String> EFFECT_FIELDS=Set.of("amount","energy_type","count_mode","card_kind","definition_ids");
    public static boolean groupTarget(EffectTarget target) {
        return Set.of(EffectTarget.OWN_BENCH,EffectTarget.OPPONENT_BENCH,
            EffectTarget.ALL_OWN_POKEMON,EffectTarget.ALL_OPPONENT_POKEMON).contains(target);
    }
    public EnergyCountQuery {
        Objects.requireNonNull(mode,"能量计数方式");Objects.requireNonNull(cardKind,"能量卡类别");
        definitionIds=Set.copyOf(Objects.requireNonNull(definitionIds,"能量卡编号筛选"));
        for(String id:definitionIds)validateId(id);
        if(!definitionIds.isEmpty()&&(mode!=Mode.CARDS||energyType!=null))
            throw new IllegalArgumentException("指定能量卡编号须按CARDS实体计数，不能混用供能属性或单位计数");
        if(mode==Mode.DISTINCT_TYPES&&(cardKind!=CardKind.BASIC||energyType!=null))throw new IllegalArgumentException("能量种类计数须限定BASIC且不指定单一属性");
    }
    private static void validateId(String id) {
        if(id==null||!id.matches("[a-z0-9][a-z0-9_:-]*"))throw new IllegalArgumentException("能量卡编号筛选含无效编号");
    }
    private static Set<String> parseIds(Map<String,String> arguments) {
        if(!arguments.containsKey("definition_ids"))return Set.of();
        var result=new java.util.LinkedHashSet<String>();
        for(String id:arguments.get("definition_ids").split("\\|",-1)) {
            validateId(id);
            if(!result.add(id))throw new IllegalArgumentException("能量卡编号筛选不能重复");
        }
        return Set.copyOf(result);
    }
    public static EnergyCountQuery from(Map<String,String> arguments) {
        EnergyType type=null;Mode mode=arguments.containsKey("definition_ids")?Mode.CARDS:Mode.UNITS;CardKind kind=CardKind.ANY;
        try {
            if(arguments.containsKey("energy_type"))type=EnergyType.valueOf(arguments.get("energy_type"));
            if(arguments.containsKey("count_mode"))mode=Mode.valueOf(arguments.get("count_mode"));
            if(arguments.containsKey("card_kind"))kind=CardKind.valueOf(arguments.get("card_kind"));
        } catch(RuntimeException invalid) {throw new IllegalArgumentException("能量计数的energy_type、count_mode或card_kind无效",invalid);}
        return new EnergyCountQuery(type,mode,kind,parseIds(arguments));
    }
    public int count(Collection<Contribution> contributions) {
        int total=0;var types=java.util.EnumSet.noneOf(EnergyType.class);
        for(var contribution:contributions) {
            if(!definitionIds.isEmpty()) {
                if(contribution.definitionId()==null)throw new IllegalArgumentException("指定能量卡计数缺少实际附着卡的编号");
                if(!definitionIds.contains(contribution.definitionId()))continue;
            }
            if(cardKind==CardKind.BASIC&&!contribution.basicEnergy()||cardKind==CardKind.SPECIAL&&contribution.basicEnergy())continue;
            var profile=contribution.profile();
            // Require a resolved profile even when asking for physical cards, never evaluate fallback provision.
            int units=energyType==null?profile.unitCount():profile.unitsOfType(energyType);
            if(mode==Mode.DISTINCT_TYPES){profile.units().forEach(unit->types.addAll(unit.selectableTypes()));continue;}
            total=Math.addExact(total,mode==Mode.UNITS?units:(energyType==null||units>0?1:0));
        }
        return mode==Mode.DISTINCT_TYPES?types.size():total;
    }
    public static void validateDamageTarget(EffectTarget target) {
        if(!Set.of(EffectTarget.CHOSEN_POKEMON,EffectTarget.SELF,EffectTarget.OWN_ACTIVE,EffectTarget.OPPONENT_ACTIVE,EffectTarget.OWN_BENCH,
            EffectTarget.OPPONENT_BENCH,EffectTarget.ALL_OWN_POKEMON,EffectTarget.ALL_OPPONENT_POKEMON).contains(target))
            throw new IllegalArgumentException("能量计数伤害须指定公开固定场上范围，不能读取隐藏区域或未绑定目标");
    }
}
