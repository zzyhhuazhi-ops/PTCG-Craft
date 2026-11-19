import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.field.BattlefieldState;
import dev.hua.ptcglive.engine.field.PokemonProfile;
import dev.hua.ptcglive.rules.CardInstance;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server retreat-cost modifiers stack with base cost, clamp at zero, and expire by effect controller. */
public final class RetreatCostModifierEffectTest {
    private static final UUID A = new UUID(0, 5601), B = new UUID(0, 5602);
    public static void main(String[] args) {
        battlefieldCostAndExpiry();
        matchReplayAndPublicState();
        System.out.println("RetreatCostModifierEffectTest passed: server retreat cost modifier stacks, clamps and expires");
    }
    private static void battlefieldCostAndExpiry() {
        var profile = new PokemonProfile(100, 2, null, null, 0, null, 0, 1, List.of());
        var field = new BattlefieldState(A, B, Map.of("basic", profile), new ServerRandom(5603));
        field.placeInitialActive(A, card(A)); field.placeInitialActive(B, card(B)); field.beginTurn(A);
        UUID target = field.activeView(B).pokemonId();
        field.modifyRetreatCostUntilOwnerNextTurn(A, B, target, 2);
        if (field.activeRetreatCost(B) != 4 || !field.activeView(B).continuousEffectZh().contains("增加 2"))
            throw new AssertionError("撤退费用增加没有由服务器结算或公开显示");
        field.modifyRetreatCostUntilOwnerNextTurn(B, B, target, -10);
        if (field.activeRetreatCost(B) != 0) throw new AssertionError("撤退费用减少没有与增加效果叠加并在零处截断");
        field.beginTurn(B);
        if (field.activeRetreatCost(B) != 4) throw new AssertionError("自己的撤退费用减少没有在自己下回合开始时到期");
        field.beginTurn(A);
        if (field.activeRetreatCost(B) != 2) throw new AssertionError("对方施加的撤退费用增加没有按效果拥有者回合到期");
    }
    private static void matchReplayAndPublicState() {
        ServerMatch match = PersistentTrainerTest.game();
        UUID target = match.activePokemonView(PersistentTrainerTest.A).pokemonId();
        int base = match.activeRetreatCost(PersistentTrainerTest.A);
        match.modifyRetreatCostByEffect(PersistentTrainerTest.A, PersistentTrainerTest.A, target, -2);
        if (match.activeRetreatCost(PersistentTrainerTest.A) != Math.max(0, base - 2)
            || !match.activePokemonView(PersistentTrainerTest.A).continuousEffectZh().contains("减少 2")
            || !match.replayTranscript().getLast().operation().equals("已审核效果修正撤退费用"))
            throw new AssertionError("对局撤退费用修正没有正确进入服务器状态与公开回放");
    }
    private static CardInstance card(UUID owner) { return new CardInstance(UUID.randomUUID(), owner, "basic"); }
}
