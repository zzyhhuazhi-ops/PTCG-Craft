package dev.hua.ptcglive.engine.effect;
import java.util.*;

/** An attacker selects one Item from the opponent's actually revealed hand and returns it to that deck's bottom. */
public final class OpponentHandBottom {
    private OpponentHandBottom() { }
    public static final Set<String> FIELDS=Set.of("amount","minimum","trainer_category","card_category","card_kinds","destination","bind");
    public static boolean energy(EffectDefinition effect) {
        return effect.arguments().equals(Map.of("amount","1","card_category","ENERGY"));
    }
    public static boolean pokemon(EffectDefinition effect) {
        return effect.arguments().equals(Map.of("amount","1","card_category","POKEMON"));
    }
    public static boolean allTrainers(EffectDefinition effect) {return effect.arguments().equals(Map.of("amount","1","card_category","TRAINER"));}
    public static boolean trainer(EffectDefinition effect) {
        if(!effect.arguments().keySet().equals(Set.of("amount","card_category","trainer_category"))
            ||!"1".equals(effect.arguments().get("amount"))||!"TRAINER".equals(effect.arguments().get("card_category")))return false;
        try { return dev.hua.ptcglive.rules.TrainerCategory.valueOf(effect.arguments().get("trainer_category"))
            !=dev.hua.ptcglive.rules.TrainerCategory.NONE; }
        catch(RuntimeException invalid) { return false; }
    }
    public static dev.hua.ptcglive.rules.TrainerCategory trainerCategory(EffectDefinition effect) {
        if(!trainer(effect))throw new IllegalArgumentException("对手手牌训练家分类无效");
        return dev.hua.ptcglive.rules.TrainerCategory.valueOf(effect.arguments().get("trainer_category"));
    }
    public static boolean discardAny(EffectDefinition effect) {
        return effect.arguments().equals(Map.of("amount","1","destination","DISCARD"));
    }
    public static boolean discardItems(EffectDefinition effect) {
        return effect.arguments().equals(Map.of("amount","2","minimum","0","card_category","TRAINER","trainer_category","ITEM","destination","DISCARD"));
    }
    public static boolean discardEnergy(EffectDefinition effect) {
        return effect.arguments().equals(Map.of("amount","2","minimum","2","card_category","ENERGY","destination","DISCARD"));
    }
    public static boolean discardToolEnergyStadium(EffectDefinition effect) {
        return effect.arguments().equals(Map.of("amount","2","minimum","0","card_kinds","TOOL|SPECIAL_ENERGY|STADIUM","destination","DISCARD"));
    }
    public static boolean discards(EffectDefinition effect) {return discardAny(effect)||discardItems(effect)||discardToolEnergyStadium(effect)||discardEnergy(effect);}
    public static boolean matches(EffectDefinition effect,dev.hua.ptcglive.rules.CardDefinition card,dev.hua.ptcglive.rules.TrainerClassificationRules classification) {
        if(discardAny(effect)||anyToBottom(effect))return true;
        if(discardToolEnergyStadium(effect))return card.category()==dev.hua.ptcglive.rules.CardCategory.ENERGY&&!card.basicEnergy()
            ||classification.matches(card,dev.hua.ptcglive.rules.TrainerCategory.TOOL)||classification.matches(card,dev.hua.ptcglive.rules.TrainerCategory.STADIUM);
        if(pokemon(effect))return card.category()==dev.hua.ptcglive.rules.CardCategory.POKEMON;
        if(allTrainers(effect))return card.category()==dev.hua.ptcglive.rules.CardCategory.TRAINER;
        if(energy(effect)||discardEnergy(effect))return card.category()==dev.hua.ptcglive.rules.CardCategory.ENERGY;
        if(trainer(effect))return classification.matches(card,trainerCategory(effect));
        return classification.matches(card,dev.hua.ptcglive.rules.TrainerCategory.ITEM);
    }
    public static boolean anyToBottom(EffectDefinition effect) {
        var arguments=new LinkedHashMap<>(effect.arguments());arguments.remove("bind");
        return arguments.equals(Map.of("amount","1"));
    }
    public static int minimum(EffectDefinition effect) {
        return discardEnergy(effect)?2:discardItems(effect)||discardToolEnergyStadium(effect)?0:1;
    }
    public static int maximum(EffectDefinition effect) {
        return discardItems(effect)||discardToolEnergyStadium(effect)||discardEnergy(effect)?2:1;
    }
    public static void validate(EffectDefinition effect) {
        if(!effect.type().equals("opponent_hand_to_bottom")||effect.target()!=EffectTarget.OPPONENT_ACTIVE
            ||!effect.effects().isEmpty()
            ||!energy(effect)&&!pokemon(effect)&&!allTrainers(effect)&&!trainer(effect)&&!discards(effect)&&!anyToBottom(effect)&&!effect.arguments().equals(Map.of("amount","1","trainer_category","ITEM")))
            throw new IllegalArgumentException("对手手牌处理须展示后选择物品、能量或任意牌，并按声明放底或弃置");
    }
    public static void validateBindings(EffectProgram program) {
        int[] budget={4096};
        for(var binding:program.triggers())sequence(binding.effects(),false,0,budget);
    }
    private static boolean sequence(List<EffectDefinition> effects,boolean revealed,int depth,int[] budget) {
        if(depth>32)throw new IllegalArgumentException("手牌展示数据流嵌套过深");
        for(var effect:effects) {
            if(--budget[0]<0)throw new IllegalArgumentException("手牌展示数据流超出安全步数");
            if(effect.type().equals("delayed_effect"))continue;
            if((effect.type().equals("opponent_hand_to_bottom")||OpponentHandShuffle.handles(effect)&&!OpponentHandShuffle.opponentChooses(effect))&&!revealed)
                throw new IllegalArgumentException("选择对手手牌必须由同一路径此前的完整手牌展示提供，不能追溯其他分支");
            boolean local=revealed||effect.type().equals("reveal_hand")&&effect.target()==EffectTarget.OPPONENT_ACTIVE;
            if(effect.type().equals("choice")) {
                for(var child:effect.effects())sequence(List.of(child),local,depth+1,budget);
            } else local=sequence(effect.effects(),local,depth+1,budget);
            if(effect.conditions().isEmpty()&&!Set.of("conditional","choice","repeat_per_heads").contains(effect.type()))revealed=local;
        }
        return revealed;
    }
}
