package dev.hua.ptcglive.engine.effect;
import java.util.Set;

/** A declared attack takes two actual face-down Prizes; this is independent of Knock Out awards. */
public final class TakePrizeCards {
    private TakePrizeCards() { }
    public static final Set<String> FIELDS=Set.of("amount","bind");
    public static void validate(EffectDefinition effect) {
        if(!effect.type().equals("take_prize_cards")||!Set.of(EffectTarget.SELF,EffectTarget.OPPONENT_ACTIVE).contains(effect.target())||!effect.effects().isEmpty()
            ||!effect.arguments().keySet().equals(FIELDS)||!Set.of("1","2").contains(effect.arguments().get("amount")))
            throw new IllegalArgumentException("直接拿奖须由己方或对方拿取1至2张实际暗面奖赏并记录张数");
        AttackCountBindings.name(effect.arguments().get("bind"));
    }
}
