package dev.hua.ptcglive.engine.effect;
import java.util.Set;
/** Separates an effect on the Defending Pokemon from a restriction on the opposing player. */
public final class HandEnergyLock {
    public static final Set<String> FIELDS=Set.of("scope","card_kind");
    private HandEnergyLock() { }
    public static boolean onPlayer(EffectDefinition effect) {
        if(!effect.type().equals("prevent_hand_energy")||effect.target()!=EffectTarget.OPPONENT_ACTIVE||!effect.effects().isEmpty()
            ||!effect.arguments().keySet().equals(FIELDS))throw new IllegalArgumentException("手牌附能限制须明确范围和能量类别");
        String scope=effect.arguments().get("scope"),kind=effect.arguments().get("card_kind");
        if(scope.equals("PLAYER")&&kind.equals("SPECIAL"))return true;
        if(scope.equals("DEFENDING_POKEMON")&&kind.equals("ANY"))return false;
        throw new IllegalArgumentException("未支持的手牌附能限制组合");
    }
}
