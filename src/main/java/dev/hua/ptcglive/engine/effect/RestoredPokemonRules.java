package dev.hua.ptcglive.engine.effect;

import dev.hua.ptcglive.engine.field.AbilityDefinition;

/** Printed Restored classification and its two reviewed zone operations. */
public final class RestoredPokemonRules {
    private RestoredPokemonRules() { }
    public static final String BENCH="bench_restored_from_hand", BOTTOM="discard_self_to_deck_bottom";
    public static void validate(EffectDefinition e) {
        if(!(e.type().equals(BENCH)&&e.target()==EffectTarget.HAND||e.type().equals(BOTTOM)&&e.target()==EffectTarget.SELF)
            ||!e.arguments().isEmpty()||!e.conditions().isEmpty()||!e.effects().isEmpty())
            throw new IllegalArgumentException("复原宝可梦的指定区域操作不能附带参数、条件或子动作");
    }
    public static boolean containsBottom(AbilityDefinition ability) {
        return ability.effectProgram().triggers().stream().flatMap(t->t.effects().stream()).anyMatch(RestoredPokemonRules::containsBottom);
    }
    private static boolean containsBottom(EffectDefinition e){return e.type().equals(BOTTOM)||e.effects().stream().anyMatch(RestoredPokemonRules::containsBottom);}
    public static boolean prehistoricCall(AbilityDefinition ability) {
        if(ability.kind()!=AbilityDefinition.Kind.ACTIVATED_ONCE_PER_TURN||!ability.composed()||ability.endsTurn()||ability.usageLimit()!=null)return false;
        var triggers=ability.effectProgram().triggers();if(triggers.size()!=1)return false;var trigger=triggers.getFirst();
        if(trigger.trigger()!=EffectTrigger.ONCE_PER_TURN||!trigger.conditions().isEmpty()||trigger.effects().size()!=1)return false;
        var e=trigger.effects().getFirst();
        return e.type().equals(BOTTOM)&&e.target()==EffectTarget.SELF&&e.arguments().isEmpty()&&e.conditions().isEmpty()&&e.effects().isEmpty();
    }
}
