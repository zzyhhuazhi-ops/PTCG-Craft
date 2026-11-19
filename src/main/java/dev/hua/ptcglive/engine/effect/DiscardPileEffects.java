package dev.hua.ptcglive.engine.effect;
import java.util.*;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.engine.field.*;

/** Public discard queries and mandatory recovery of every matching card. */
public final class DiscardPileEffects {
    private DiscardPileEffects() { }
    public static final Set<String> DAMAGE_FIELDS=Set.of("amount","card_category","trainer_category","basic_energy_only","attack_id","pokemon_names","pokemon_types","pokemon_families","family_match","battle_style","energy_type","limit","definition_ids");
    public static final Set<String> RECOVERY_FIELDS=Set.of("destination","card_category","trainer_category","basic_energy_only","energy_type","excluded_definition_ids");
    public static CardCountFilter from(EffectDefinition effect) {
        boolean damage=effect.type().equals("damage_per_discard_card"),recover=effect.type().equals("recover_all_discard");
        if((!damage&&!recover)||!effect.effects().isEmpty()||!(damage?DAMAGE_FIELDS:RECOVERY_FIELDS).containsAll(effect.arguments().keySet())
            ||!(damage?Set.of(EffectTarget.SELF,EffectTarget.OPPONENT_ACTIVE):Set.of(EffectTarget.DISCARD,EffectTarget.OPPONENT_ACTIVE)).contains(effect.target()))
            throw new IllegalArgumentException("弃牌计数／全部取回的参数或区域无效");
        if(recover&&!"DECK_SHUFFLED".equals(effect.arguments().get("destination")))throw new IllegalArgumentException("全部取回当前仅支持洗回牌库");
        var filter=CardCountFilter.from(effect.arguments());
        boolean allExcept=recover&&effect.arguments().keySet().equals(Set.of("destination","excluded_definition_ids"));
        if(effect.arguments().containsKey("excluded_definition_ids")) {
            if(!allExcept)throw new IllegalArgumentException("全部弃牌回库例外须为唯一的明确印刷编号排除");
            TurnHistoryCondition.values(effect.arguments().get("excluded_definition_ids"));
        }
        if(filter.category()==null&&!allExcept&&!(damage&&effect.arguments().containsKey("battle_style")))throw new IllegalArgumentException("弃牌操作须明确卡牌类别或印刷标记");
        if(damage){int n=Integer.parseInt(effect.arguments().getOrDefault("amount","0"));if(n<10||n>1000||n%10!=0)throw new IllegalArgumentException("弃牌计数伤害须为10至1000的10倍数");}
        if(effect.arguments().containsKey("definition_ids"))definitionIds(effect);
        if(damage||recover&&effect.arguments().containsKey("energy_type")) {
            var args=effect.arguments();
            if(Set.of("attack_id","pokemon_names","pokemon_types","pokemon_families","family_match").stream().anyMatch(args::containsKey)
                &&filter.category()!=CardCategory.POKEMON)throw new IllegalArgumentException("宝可梦弃牌筛选须明确POKEMON类别");
            if(args.containsKey("attack_id"))AttackCountBindings.name(args.get("attack_id"));
            if(args.containsKey("pokemon_names"))pokemonNames(effect);
            PokemonTypeFilter.fromWire(args.get("pokemon_types"));
            PokemonFamilyFilter.fromWire(args.get("pokemon_families"),args.get("family_match"));
            if(args.containsKey("energy_type")) {
                if(filter.category()!=CardCategory.ENERGY||!filter.basicEnergyOnly())throw new IllegalArgumentException("弃牌能量属性筛选须明确基础能量身份");
                EnergyType.valueOf(args.get("energy_type"));
            }
            if(damage){int limit=limit(effect);if(limit<1||limit>60)throw new IllegalArgumentException("弃牌计数上限须为1至60张");}
        }
        return filter;
    }
    public static int limit(EffectDefinition effect) {return Integer.parseInt(effect.arguments().getOrDefault("limit","60"));}
    private static Set<String> pokemonNames(EffectDefinition effect) {
        String raw=effect.arguments().get("pokemon_names");if(raw==null)return Set.of();
        var names=new LinkedHashSet<String>();
        for(String name:raw.split("\\|",-1))if(name.isBlank()||!name.equals(name.trim())||!names.add(name))throw new IllegalArgumentException("弃牌宝可梦名称须为非空、无重复的精确印刷名");
        return names;
    }
    private static Set<String> definitionIds(EffectDefinition effect) {
        String raw=effect.arguments().get("definition_ids");if(raw==null)return Set.of();
        var ids=new LinkedHashSet<String>();
        for(String id:raw.split("\\|",-1))if(!id.matches("[a-z0-9][a-z0-9_:-]*")||!ids.add(id))throw new IllegalArgumentException("弃牌卡牌编号须为非空、无重复的稳定编号");
        return Set.copyOf(ids);
    }
    /** Only facts of the discarded card count; an attached Special Energy never inherits its former host's type. */
    public static boolean matchesDamageCard(EffectDefinition effect,CardDefinition card,PokemonProfile profile,EnergyType basicEnergyType,TrainerClassificationRules rules) {
        if(!from(effect).matches(card,rules)||!definitionIds(effect).isEmpty()&&!definitionIds(effect).contains(card.id()))return false;
        var args=effect.arguments();String attack=args.get("attack_id");
        if(attack!=null&&(profile==null||profile.attack(attack).isEmpty()))return false;
        var names=pokemonNames(effect);if(!names.isEmpty()&&(profile==null||!names.contains(profile.pokemonName())))return false;
        var types=PokemonTypeFilter.fromWire(args.get("pokemon_types"));
        if(!types.equals(PokemonTypeFilter.ANY)&&(profile==null||!types.matches(card,profile)))return false;
        if(!PokemonFamilyFilter.fromWire(args.get("pokemon_families"),args.get("family_match")).matches(card))return false;
        if(args.containsKey("battle_style")&&!card.battleStyles().contains(BattleStyle.valueOf(args.get("battle_style"))))return false;
        return !args.containsKey("energy_type")||basicEnergyType!=null&&basicEnergyType.name().equals(args.get("energy_type"));
    }
    public static boolean matchesRecoveryCard(EffectDefinition effect,CardDefinition card,EnergyType basicEnergyType,TrainerClassificationRules rules) {
        var filter=from(effect);
        return filter.matches(card,rules)&&(!effect.arguments().containsKey("excluded_definition_ids")
            ||!TurnHistoryCondition.values(effect.arguments().get("excluded_definition_ids")).contains(card.id()))&&(!effect.arguments().containsKey("energy_type")
            ||basicEnergyType!=null&&basicEnergyType.name().equals(effect.arguments().get("energy_type")));
    }
}
