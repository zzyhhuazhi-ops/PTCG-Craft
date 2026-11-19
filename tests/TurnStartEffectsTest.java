import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.BundledCombatData;
import dev.hua.ptcglive.rules.CardInstance;
import java.util.*;

/** Synthetic programs verify the server-owned start-of-turn boundary. */
public final class TurnStartEffectsTest {
    static final UUID A = DynamicPassiveModifierTest.A, B = DynamicPassiveModifierTest.B;
    static final String OWN_START = "{\"trigger\":\"ON_TURN_START\",\"turn\":\"OWN\",\"optional\":false,\"usage\":\"NONE\"}";

    static String trigger(String effects) { return PersistentSourceTriggerTest.trigger("start", OWN_START, effects); }
    static EnergyProfile energy(String effects) { return PersistentSourceTriggerTest.energy(trigger(effects)); }
    static ServerMatch fixture(EnergyProfile profile) {
        return PersistentSourceTriggerTest.fixture(DynamicPassiveModifierTest.plainTool(), DynamicPassiveModifierTest.plainStadium(), profile);
    }
    static CardInstance hand(ServerMatch match, UUID owner, String definitionId) {
        return DynamicPassiveModifierTest.hand(match, owner, definitionId);
    }

    public static void main(String[] args) {
        ownBoundaryAfterNormalDraw(); opponentBoundary(); pauseBlocksNormalActions(); abilityAndDataGuards();
        System.out.println("TurnStartEffectsTest passed: post-draw trigger boundary, ownership, continuation and server action gate");
    }

    static void ownBoundaryAfterNormalDraw() {
        var match = fixture(energy("{\"type\":\"discard_source\"}"));
        var source = hand(match, A, "special");
        match.attachEnergy(A, source.instanceId(), match.activePokemonView(A).pokemonId());
        match.endTurn(A);
        check(!match.privateView(A).discard().contains(source) && match.currentPlayer().equals(B), "己方回合开始效果错误地在对手回合触发");
        match.endTurn(B);
        check(match.privateView(A).discard().contains(source) && match.currentPlayer().equals(A) && match.phase() == MatchPhase.TURN,
            "己方回合开始没有在正常抽牌后结算来源效果");
    }

    static void opponentBoundary() {
        String opponent = OWN_START.replace("\"OWN\"", "\"OPPONENT\"");
        var match = fixture(PersistentSourceTriggerTest.energy(PersistentSourceTriggerTest.trigger("start", opponent,
            "{\"type\":\"draw\",\"amount\":1}")));
        var source = hand(match, A, "special");
        match.attachEnergy(A, source.instanceId(), match.activePokemonView(A).pokemonId());
        int before = match.privateView(A).hand().size();
        match.endTurn(A);
        check(match.currentPlayer().equals(B) && match.privateView(A).hand().size() == before + 1,
            "对手回合开始触发没有归属来源控制者，或没有在正常抽牌后执行");
    }

    static void pauseBlocksNormalActions() {
        var match = fixture(energy("{\"type\":\"draw\",\"amount\":1},{\"type\":\"choice\",\"effects\":[{\"type\":\"draw\",\"amount\":1},{\"type\":\"draw\",\"amount\":2}]}"));
        var source = hand(match, A, "special");
        match.attachEnergy(A, source.instanceId(), match.activePokemonView(A).pokemonId());
        match.endTurn(A);
        int before = match.privateView(A).hand().size();
        match.endTurn(B);
        check(match.hasPendingEffectChoice() && !match.mayAttack(A) && !match.mayPlaySupporter(A),
            "回合开始选择没有锁定普通行动入口");
        reject(() -> match.endTurn(A));
        var prompt = match.effectChoicePrompt(A);
        reject(() -> match.chooseEffectOption(B, prompt.resolutionId(), prompt.step(), 0));
        match.chooseEffectOption(A, prompt.resolutionId(), prompt.step(), 0);
        check(match.currentPlayer().equals(A) && match.privateView(A).hand().size() == before + 3,
            "回合开始续接重复或遗漏正常抽牌／效果抽牌");
    }

    static void abilityAndDataGuards() {
        var ability = BundledCombatData.ability(JsonParser.parseString("""
            {"id":"start-ability","nameZh":"开始夹具","kind":"TRIGGERED","effectSupport":"SUPPORTED",
             "eventPolicy":{"trigger":"ON_TURN_START","turn":"OWN","optional":false,"usage":"NONE"},
             "triggers":[{"trigger":"ON_TURN_START","effects":[{"type":"draw","amount":1}]}]}
            """).getAsJsonObject(), EffectRegistry.standard());
        TriggeredAbilityRules.validate(ability);
        reject(() -> AbilityEventPolicy.parse(JsonParser.parseString("{\"trigger\":\"ON_TURN_START\",\"turn\":\"OWN\",\"optional\":false,\"usage\":\"NONE\",\"extra\":1}").getAsJsonObject()));
        String onceGame = OWN_START.replace("\"NONE\"", "\"ONCE_PER_GAME\"");
        reject(() -> PersistentSourceTriggerTest.energy(PersistentSourceTriggerTest.trigger("start", onceGame,
            "{\"type\":\"draw\",\"amount\":1}")));
    }

    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static void reject(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("非法操作未被服务器拒绝");
    }
}
