import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Passive Special Condition immunity is checked by the server for both attack and audited-effect application paths. */
public final class PassiveSpecialConditionImmunityTest {
    private static final UUID A = new UUID(0, 6331), B = new UUID(0, 6332);

    public static void main(String[] args) {
        ServerMatch match = fixture();
        if (!match.abilityOptions(B).isEmpty()) throw new AssertionError("异常状态免疫被动特性被错误暴露为可点击操作");
        match.attack(A, "poison");
        if (!match.activePokemonView(B).conditions().isEmpty())
            throw new AssertionError("攻击附加的异常状态绕过了防止异常状态的被动特性");
        // The attack ends A's turn. The server-only effect entry must respect
        // the same public passive while B is now the authorized acting player.
        match.applySpecialConditionByEffect(B, B, SpecialCondition.BURNED);
        if (!match.activePokemonView(B).conditions().isEmpty())
            throw new AssertionError("审核效果附加的异常状态绕过了防止异常状态的被动特性");
        if (!match.replayTranscript().getLast().operation().contains("持续免疫阻止"))
            throw new AssertionError("异常状态免疫没有写入公开事实回放");
        System.out.println("PassiveSpecialConditionImmunityTest passed: passive condition immunity is server-resolved for attacks and audited effects");
    }

    private static ServerMatch fixture() {
        CardDefinition attacker = definition("attacker"); CardDefinition immune = definition("immune");
        AbilityDefinition immunity = new AbilityDefinition("condition-shield", "异常状态免疫",
            new AbilityEffect(AbilityEffect.Opcode.PASSIVE_PREVENT_ALL_SPECIAL_CONDITIONS, 0), EffectSupport.SUPPORTED, null,
            AbilityDefinition.Kind.PASSIVE);
        AttackDefinition poison = new AttackDefinition("poison", "测试中毒", Map.of(), 0, EnergyType.GRASS, EffectSupport.SUPPORTED, null,
            List.of(new ConditionEffect(ConditionEffect.Target.DEFENDER, SpecialCondition.POISONED)));
        CombatCatalog combat = new CombatCatalog(Map.of(
            "attacker", new PokemonProfile(120, 0, null, null, 0, null, 0, 1, List.of(poison)),
            "immune", new PokemonProfile(120, 0, null, null, 0, null, 0, 1, List.of(), List.of(immunity))), Map.of());
        for (long seed = 1; seed < 5000; seed++) {
            ServerMatch match = new ServerMatch(A, B, deck(A, "attacker"), deck(B, "immune"), MatchRulesRegressionTest.RULE,
                new CardDatabase(List.of(attacker, immune)), combat, new ServerRandom(seed));
            OpeningTestSupport.complete(match, A, B, 0);
            // International Standard prohibits the first player's first-turn
            // attack, so advance normally until A has an attack-legal turn.
            for (int turns = 0; turns < 4; turns++) {
                if (match.currentPlayer().equals(A) && match.mayAttack(A)) return match;
                match.endTurn(match.currentPlayer());
            }
        }
        throw new AssertionError("无法建立甲方先手的确定性异常状态测试对局");
    }

    private static CardDefinition definition(String id) {
        return new CardDefinition(id, id, CardCategory.POKEMON, TrainerCategory.NONE, "H", Set.of("international"), true,
            false, 60, Map.of(), EffectSupport.SUPPORTED, null);
    }
    private static List<CardInstance> deck(UUID owner, String id) {
        var cards = new ArrayList<CardInstance>();
        for (int i = 0; i < 60; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, id));
        return cards;
    }
}
