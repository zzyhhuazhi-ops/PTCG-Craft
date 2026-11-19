package dev.hua.ptcglive.engine.effect;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

/** Joint allocation of Fire, Water and Lightning units, followed by an indivisible physical-card discard. */
public final class TypedEnergyDiscard {
    private TypedEnergyDiscard() { }
    public static final String TYPES="FIRE|WATER|LIGHTNING";
    public static final String SINGLE_TYPE_BASIC_TYPES="FIRE|LIGHTNING";
    private static final List<EnergyType> REQUIRED=List.of(EnergyType.FIRE,EnergyType.WATER,EnergyType.LIGHTNING);
    public static boolean handles(EffectDefinition effect) {return effect.arguments().containsKey("energy_types");}
    public static void validate(EffectDefinition effect) {
        if(SINGLE_TYPE_BASIC_TYPES.equals(effect.arguments().get("energy_types"))) {
            int maximum=Integer.parseInt(effect.arguments().getOrDefault("amount","-1"));
            if(!effect.type().equals("discard_energy")||effect.target()!=EffectTarget.SELF||!effect.effects().isEmpty()
                ||!effect.arguments().keySet().equals(Set.of("amount","minimum","card_kind","energy_types","single_type_choice","bind"))
                ||maximum<1||maximum>60||!"0".equals(effect.arguments().get("minimum"))
                ||!"BASIC".equals(effect.arguments().get("card_kind"))||!"true".equals(effect.arguments().get("single_type_choice")))
                throw new IllegalArgumentException("同属性基本弃能须从SELF选择0至60张，且全部为火或全部为雷");
            AttackCountBindings.name(effect.arguments().get("bind"));return;
        }
        if(!effect.type().equals("discard_energy")||effect.target()!=EffectTarget.SELF||!effect.effects().isEmpty()
            ||!effect.arguments().keySet().equals(Set.of("amount","minimum","count_mode","energy_types"))
            ||!"3".equals(effect.arguments().get("amount"))||!"3".equals(effect.arguments().get("minimum"))
            ||!"UNITS".equals(effect.arguments().get("count_mode"))||!TYPES.equals(effect.arguments().get("energy_types")))
            throw new IllegalArgumentException("混合弃能仅核验SELF的火、水、雷各1个，须联合分配当前供能，不接受独立卡数或自定义支付");
    }
    public static int maximum(Collection<EnergyProfile> profiles) {
        var actual=List.copyOf(profiles);
        for(int count=3;count>0;count--)for(int mask=1;mask<8;mask++)if(Integer.bitCount(mask)==count) {
            var cost=new EnumMap<EnergyType,Integer>(EnergyType.class);
            for(int i=0;i<3;i++)if((mask&(1<<i))!=0)cost.put(REQUIRED.get(i),1);
            if(EnergyProfile.canPay(actual,cost))return count;
        }
        return 0;
    }
    public static boolean eligible(EnergyProfile profile) {return maximum(List.of(profile))>0;}
    public static boolean eligibleSingleType(EnergyProfile profile) {
        return profile.unitsOfType(EnergyType.FIRE)>0||profile.unitsOfType(EnergyType.LIGHTNING)>0;
    }
    public static boolean validSingleTypeSelection(List<EnergyProfile> profiles) {
        if(profiles.isEmpty())return true;
        return profiles.stream().allMatch(profile->profile.unitsOfType(EnergyType.FIRE)>0)
            ||profiles.stream().allMatch(profile->profile.unitsOfType(EnergyType.LIGHTNING)>0);
    }
    private record OfferedUnit(int card,Set<EnergyType> types) { }
    /** Every selected physical card contributes a unit; an all-type unit cannot serve more than one requirement. */
    public static boolean validSelection(List<EnergyProfile> profiles,int count) {
        if(count<0||count>3||profiles.size()>count)return false;
        if(count==0)return profiles.isEmpty();
        if(profiles.isEmpty())return false;
        var units=new ArrayList<OfferedUnit>();
        for(int card=0;card<profiles.size();card++) {
            var profile=profiles.get(card);profile.unitCount(); // rejects unresolved conditional provision
            for(var unit:profile.units())units.add(new OfferedUnit(card,unit.selectableTypes()));
        }
        for(int mask=1;mask<8;mask++)if(Integer.bitCount(mask)==count) {
            var wanted=new ArrayList<EnergyType>();for(int i=0;i<3;i++)if((mask&(1<<i))!=0)wanted.add(REQUIRED.get(i));
            if(assign(wanted,units,0,0,0,(1<<profiles.size())-1))return true;
        }
        return false;
    }
    private static boolean assign(List<EnergyType> wanted,List<OfferedUnit> units,int at,int used,int cards,int allCards) {
        if(at==wanted.size())return cards==allCards;
        for(int i=0;i<units.size();i++)if((used&(1<<i))==0&&units.get(i).types().contains(wanted.get(at))
            &&assign(wanted,units,at+1,used|(1<<i),cards|(1<<units.get(i).card()),allCards))return true;
        return false;
    }
}
