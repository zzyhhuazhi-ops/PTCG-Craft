package dev.hua.ptcglive.ai;

import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Own initial list only. No box name, player list, shuffled order or match random source. */
public record AiDeckAnalysis(Map<String,Integer> initialCards,List<String> basics,List<String> evolutionLines,
                             List<String> attackers,Set<EnergyType> energyTypes,Map<AiEffects.Role,Integer> roles,
                             List<String> tendency,List<String> warnings) {
    public static final AiDeckAnalysis EMPTY=new AiDeckAnalysis(Map.of(),List.of(),List.of(),List.of(),Set.of(),Map.of(),List.of("未形成可靠分析"),List.of());
    public AiDeckAnalysis {initialCards=Map.copyOf(initialCards);basics=List.copyOf(basics);evolutionLines=List.copyOf(evolutionLines);attackers=List.copyOf(attackers);energyTypes=Set.copyOf(energyTypes);roles=Map.copyOf(roles);tendency=List.copyOf(tendency);warnings=List.copyOf(warnings);}
    public static AiDeckAnalysis analyze(Collection<CardInstance> deck,CardDatabase db,CombatCatalog combat){
        var counts=new TreeMap<String,Integer>();deck.forEach(c->counts.merge(c.definitionId(),1,Integer::sum));
        var basic=new ArrayList<String>();var lines=new TreeSet<String>();var attackers=new ArrayList<String>();
        var energy=EnumSet.noneOf(EnergyType.class);var roles=new EnumMap<AiEffects.Role,Integer>(AiEffects.Role.class);var warnings=new TreeSet<String>();
        for(var entry:counts.entrySet()){
            String id=entry.getKey();var def=db.find(id).orElse(null);if(def==null)continue;
            var p=combat.fieldPokemon(id).orElse(null);var e=combat.energyProfile(id).orElse(null);
            var facts=new AiView.Card(def,p,combat.trainer(id).orElse(null),e);var f=AiEffects.card(facts);
            for(var role:f.roles())roles.merge(role,entry.getValue(),Integer::sum);
            for(var warning:f.weak())warnings.add(def.nameZh()+" ["+id+"]："+warning);
            if(e!=null){for(var unit:e.units())energy.addAll(unit.selectableTypes());
                for(var branch:e.provisionRules())for(var unit:branch.units())energy.addAll(unit.selectableTypes());
                if(!e.provisionRules().isEmpty())warnings.add(def.nameZh()+" ["+id+"]：特殊能量按当前实际供能验证；未来供能仅保守估计");}
            if(def.basicPokemon())basic.add(id);
            if(p!=null){
                if(!p.attacks().isEmpty())attackers.add(id);
                for(String from:counts.keySet()){
                    var parent=combat.fieldPokemon(from).orElse(null);
                    if(parent!=null&&evolves(parent,from,p))lines.add(from+" → "+id);
                }
                for(var a:p.attacks())if(a.discardAllAttachedEnergyAfterAttack()||a.discardSelectedAttachedEnergyAfterAttack()>0||a.selfDamage()>0)
                    warnings.add(def.nameZh()+" ["+id+"]：攻击有弃能/反伤代价，保守评分而非全局资源最优");
            }
        }
        attackers.sort(Comparator.<String>comparingDouble(id->attackValue(combat.fieldPokemon(id).orElseThrow())).reversed().thenComparing(id->id));
        var tendency=new ArrayList<String>();
        if(!basic.isEmpty())tendency.add("基础宝可梦 "+basic.stream().mapToInt(counts::get).sum()+" 张；优先建立攻击位和后备");
        if(!lines.isEmpty())tendency.add("识别到 "+lines.size()+" 条进化衔接，按手牌和可攻击状态培养");
        if(roles.containsKey(AiEffects.Role.SEARCH))tendency.add("检索优先补能量缺口与缺失进化环节");
        if(roles.containsKey(AiEffects.Role.DRAW))tendency.add("手牌不足时抽牌，结果出现后重新评估");
        if(roles.containsKey(AiEffects.Role.ATTACH)||roles.containsKey(AiEffects.Role.MOVE_ENERGY))tendency.add("使用可用附能/转移手段准备主攻与后备");
        if(roles.containsKey(AiEffects.Role.RECOVER))tendency.add("按当前需求回收弃牌区资源");
        if(roles.containsKey(AiEffects.Role.SWITCH))tendency.add("仅在攻击准备或保场有收益时换位");
        if(roles.containsKey(AiEffects.Role.BOOST))tendency.add("检查增伤后攻击的短期收益；实际击倒由引擎确认");
        if(tendency.isEmpty())tendency.add("未形成可靠分析");
        return new AiDeckAnalysis(counts,basic,List.copyOf(lines),attackers,energy,roles,tendency,List.copyOf(warnings));
    }
    public static boolean evolves(PokemonProfile parent,String parentId,PokemonProfile child){return child.evolvesFromName()!=null?child.evolvesFromName().equals(parent.pokemonName()):parentId.equals(child.evolvesFromDefinitionId());}
    public static double attackValue(PokemonProfile p){return p.attacks().stream().mapToDouble(a->(a.baseDamage()+(a.coinDamage()==null?0:a.coinDamage().flips()*a.coinDamage().perHeads()*.5))/(1+a.energyCost().values().stream().mapToInt(Integer::intValue).sum())).max().orElse(0)+p.hitPoints()*.03;}
}
