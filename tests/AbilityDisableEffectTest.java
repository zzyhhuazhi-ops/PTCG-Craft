import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.field.AbilityDefinition;
import dev.hua.ptcglive.engine.field.AbilityEffect;
import dev.hua.ptcglive.engine.field.PokemonProfile;
import dev.hua.ptcglive.engine.field.SpecialCondition;
import dev.hua.ptcglive.rules.CardCategory;
import dev.hua.ptcglive.rules.CardDatabase;
import dev.hua.ptcglive.rules.CardDefinition;
import dev.hua.ptcglive.rules.CardInstance;
import dev.hua.ptcglive.rules.CombatCatalog;
import dev.hua.ptcglive.rules.EffectSupport;
import dev.hua.ptcglive.rules.TrainerCategory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A temporary Ability lock suppresses both clickable and passive Ability text, then expires server-side. */
public final class AbilityDisableEffectTest {
    private static final UUID A = new UUID(0, 6361), B = new UUID(0, 6362);

    public static void main(String[] args) {
        activatedAbilityStopsAndExpires();
        passiveAbilityStopsAndExpires();
        System.out.println("AbilityDisableEffectTest passed: temporary Ability disable suppresses active/passive text and expires server-side");
    }

    private static void activatedAbilityStopsAndExpires() {
        AbilityDefinition draw = new AbilityDefinition("draw", "测试抽牌特性",
            new AbilityEffect(AbilityEffect.Opcode.DRAW, 1), EffectSupport.SUPPORTED, null);
        ServerMatch match = fixture("actor", draw);
        UUID active = match.activePokemonView(A).pokemonId();
        if (!match.canUseAbility(A, active, "draw") || match.abilityOptions(A).isEmpty())
            throw new AssertionError("失效前可点击特性没有由服务器提供");
        match.disableAbilityEffectsByEffect(A, A, active);
        if (match.canUseAbility(A, active, "draw") || !match.abilityOptions(A).isEmpty()
            || !match.activePokemonView(A).continuousEffectZh().contains("特性失效"))
            throw new AssertionError("特性失效没有隐藏可点击选项或写入公开持续效果");
        int replaySize = match.replayTranscript().size();
        rejects(() -> match.useAbility(A, active, "draw"));
        if (replaySize != match.replayTranscript().size())
            throw new AssertionError("被特性失效拒绝的操作写入了回放");
        nextOwnTurn(match);
        if (!match.canUseAbility(A, active, "draw"))
            throw new AssertionError("特性失效没有在来源玩家下回合开始时到期");
    }

    private static void passiveAbilityStopsAndExpires() {
        AbilityDefinition shield = new AbilityDefinition("shield", "异常状态免疫",
            new AbilityEffect(AbilityEffect.Opcode.PASSIVE_PREVENT_ALL_SPECIAL_CONDITIONS, 0), EffectSupport.SUPPORTED, null,
            AbilityDefinition.Kind.PASSIVE);
        ServerMatch match = fixture("guardian", shield);
        UUID active = match.activePokemonView(A).pokemonId();
        match.applySpecialConditionByEffect(A, A, SpecialCondition.POISONED);
        if (!match.activePokemonView(A).conditions().isEmpty())
            throw new AssertionError("失效前被动异常状态免疫没有生效");
        match.disableAbilityEffectsByEffect(A, A, active);
        match.applySpecialConditionByEffect(A, A, SpecialCondition.POISONED);
        if (!match.activePokemonView(A).conditions().contains(SpecialCondition.POISONED))
            throw new AssertionError("特性失效后被动效果仍错误生效");
        nextOwnTurn(match);
        if (!match.activePokemonView(A).conditions().isEmpty())
            throw new AssertionError("特性失效到期后没有立即恢复免疫并移除既有异常状态");
        match.applySpecialConditionByEffect(A, A, SpecialCondition.POISONED);
        if (!match.activePokemonView(A).conditions().isEmpty())
            throw new AssertionError("特性失效到期后被动效果没有恢复");
    }

    private static void nextOwnTurn(ServerMatch match) {
        match.endTurn(A);
        match.endTurn(B);
        if (!match.currentPlayer().equals(A)) throw new AssertionError("测试夹具未回到甲方回合");
    }

    private static ServerMatch fixture(String actorId, AbilityDefinition ability) {
        CardDefinition actor = definition(actorId); CardDefinition opponent = definition("opponent");
        CombatCatalog combat = new CombatCatalog(Map.of(
            actorId, new PokemonProfile(120, 0, null, null, 0, null, 0, 1, List.of(), List.of(ability)),
            "opponent", new PokemonProfile(120, 0, null, null, 0, null, 0, 1, List.of())), Map.of());
        CardDatabase database = new CardDatabase(List.of(actor, opponent));
        for (long seed = 1; seed < 5_000; seed++) {
            ServerMatch match = new ServerMatch(A, B, deck(A, actorId), deck(B, "opponent"), MatchRulesRegressionTest.RULE,
                database, combat, new ServerRandom(seed));
            OpeningTestSupport.complete(match, A, B, 0);
            if (match.currentPlayer().equals(A)) return match;
        }
        throw new AssertionError("无法建立甲方当前回合的确定性特性失效测试对局");
    }

    private static CardDefinition definition(String id) {
        return new CardDefinition(id, id, CardCategory.POKEMON, TrainerCategory.NONE, "H", Set.of("international"), true,
            false, 60, Map.of(), EffectSupport.SUPPORTED, null);
    }
    private static List<CardInstance> deck(UUID owner, String definitionId) {
        var cards = new ArrayList<CardInstance>();
        for (int i = 0; i < 60; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, definitionId));
        return cards;
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("服务器错误接受了特性失效期间的特性操作");
    }
}
