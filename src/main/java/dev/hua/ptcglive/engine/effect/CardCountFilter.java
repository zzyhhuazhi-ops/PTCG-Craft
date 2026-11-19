package dev.hua.ptcglive.engine.effect;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Physical card categories in a zone; Special Energy provision is never a Basic Energy identity. */
public record CardCountFilter(CardCategory category,TrainerCategory trainer,boolean basicEnergyOnly) {
    public static CardCountFilter from(Map<String,String> args) {
        var category=args.containsKey("card_category")?CardCategory.valueOf(args.get("card_category")):null;
        var trainer=args.containsKey("trainer_category")?TrainerCategory.valueOf(args.get("trainer_category")):null;
        if(trainer!=null&&(category!=CardCategory.TRAINER||trainer==TrainerCategory.NONE))throw new IllegalArgumentException("训练家计数筛选须限定TRAINER和有效子类");
        String basic=args.getOrDefault("basic_energy_only","false");
        if(!Set.of("true","false").contains(basic)||basic.equals("true")&&category!=CardCategory.ENERGY)throw new IllegalArgumentException("基础能量计数须限定ENERGY");
        return new CardCountFilter(category,trainer,Boolean.parseBoolean(basic));
    }
    public boolean matches(CardDefinition card,TrainerClassificationRules rules) {
        return (category==null||category==card.category())&&(trainer==null||rules.matches(card,trainer))&&(!basicEnergyOnly||card.basicEnergy());
    }
}
