package dev.hua.ptcglive.engine.effect;
import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Discard every top card requested, then optionally count only the moved cards matching the printed filter. */
public final class TopDeckDiscardEffect {
    private TopDeckDiscardEffect() { }
    public static final Set<String> FIELDS=Set.of("amount","bind","card_category","trainer_category","basic_energy_only","energy_type","pokemon_name_contains","destination","per_source_energy_type","printed_retreat_cost","recover_items");
    public static CardCountFilter from(EffectDefinition effect) {
        if(!effect.type().equals("discard_top_deck")||!FIELDS.containsAll(effect.arguments().keySet())||!effect.effects().isEmpty()
            ||!Set.of(EffectTarget.SELF,EffectTarget.HAND,EffectTarget.DECK,EffectTarget.OPPONENT_ACTIVE).contains(effect.target()))throw new IllegalArgumentException("弃置牌库顶的参数或区域无效");
        int count=Integer.parseInt(effect.arguments().getOrDefault("amount","0"));if(count<1||count>60)throw new IllegalArgumentException("弃置牌库顶数量须为1至60");
        if(effect.arguments().containsKey("recover_items")) {
            if(effect.target()!=EffectTarget.SELF||!Set.of(3,7).contains(count)
                ||!effect.arguments().equals(Map.of("amount",Integer.toString(count),"recover_items","true")))
                throw new IllegalArgumentException("弃牌后回收物品只核验自身牌库顶3或7张的本次弃置");
            return CardCountFilter.from(Map.of());
        }
        if(effect.arguments().containsKey("per_source_energy_type")) {
            if(effect.target()!=EffectTarget.OPPONENT_ACTIVE||!effect.arguments().equals(Map.of("amount","1","per_source_energy_type","FIGHTING")))
                throw new IllegalArgumentException("按来源能量弃牌库顶只核验每个斗能量弃对方1张");
            return CardCountFilter.from(Map.of());
        }
        if(effect.arguments().containsKey("destination")) {
            if(!effect.arguments().keySet().equals(Set.of("amount","destination"))
                ||!"LOST_ZONE".equals(effect.arguments().get("destination"))
                ||!Set.of(EffectTarget.SELF,EffectTarget.OPPONENT_ACTIVE).contains(effect.target()))
                throw new IllegalArgumentException("牌库顶放逐须仅指定数量、放逐区及一方玩家");
            return CardCountFilter.from(effect.arguments());
        }
        if(effect.arguments().containsKey("bind"))AttackCountBindings.name(effect.arguments().get("bind"));
        else if(effect.arguments().size()!=1)throw new IllegalArgumentException("弃置牌库顶筛选只用于bind计数，不能筛选实际弃置卡牌");
        var filter=CardCountFilter.from(effect.arguments());
        if(effect.arguments().containsKey("printed_retreat_cost")&&
            (filter.category()!=CardCategory.POKEMON||!"4".equals(effect.arguments().get("printed_retreat_cost"))))
            throw new IllegalArgumentException("弃置牌库顶撤退费用计数只核验撤退费用恰好4的宝可梦");
        if(effect.arguments().containsKey("energy_type")) {
            if(filter.category()!=CardCategory.ENERGY||!filter.basicEnergyOnly())throw new IllegalArgumentException("弃置牌库顶的能量属性计数须限定基本能量");
            var type=EnergyType.valueOf(effect.arguments().get("energy_type"));
            if(type==EnergyType.COLORLESS||type==EnergyType.DRAGON)throw new IllegalArgumentException("弃置牌库顶的计数属性须为基本能量属性");
        }
        if(effect.arguments().containsKey("pokemon_name_contains")) {
            String name=effect.arguments().get("pokemon_name_contains");
            if(filter.category()!=CardCategory.POKEMON||name.isBlank()||name.length()>64||!name.equals(name.trim()))
                throw new IllegalArgumentException("弃置牌库顶的名称计数须限定宝可梦及有效名称子串");
        }
        return filter;
    }
    /** Match a physical card that actually entered discard; Special Energy provision never becomes Basic identity. */
    public static boolean matchesCountedCard(EffectDefinition effect,CardDefinition card,EnergyType basicEnergyType,TrainerClassificationRules rules) {
        if(!from(effect).matches(card,rules))return false;
        String type=effect.arguments().get("energy_type");
        return type==null||basicEnergyType!=null&&basicEnergyType.name().equals(type);
    }
}
