import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Static passive attack reduction is applied to each actual target after normal damage calculation and can be explicitly bypassed. */
public final class PassiveAttackDamageReductionTest {
    private static final UUID A = new UUID(0, 6361), B = new UUID(0, 6362);
    public static void main(String[] args) {
        ServerMatch reduced = fixture(false);
        if (!reduced.abilityOptions(B).isEmpty()) throw new AssertionError("被动攻击减伤被错误暴露为可点击操作");
        reduced.attack(A, "strike");
        if (reduced.lastAttack().finalDamage() != 30 || reduced.activePokemonView(B).damage() != 30)
            throw new AssertionError("服务器没有在攻击伤害上应用被动减伤");

        ServerMatch bypassed = fixture(true);
        bypassed.attack(A, "strike");
        if (bypassed.lastAttack().finalDamage() != 50 || bypassed.activePokemonView(B).damage() != 50)
            throw new AssertionError("明确忽略防伤效果的攻击没有穿透被动攻击减伤");
        System.out.println("PassiveAttackDamageReductionTest passed: passive reduction is target-scoped, ordered and explicitly bypassable");
    }
    private static ServerMatch fixture(boolean bypass) {
        CardDefinition attacker = definition("attacker"), defender = definition("defender");
        AbilityDefinition reduction = new AbilityDefinition("damage-reduce", "攻击减伤",
            new AbilityEffect(AbilityEffect.Opcode.PASSIVE_REDUCE_OPPONENT_ATTACK_DAMAGE, 20), EffectSupport.SUPPORTED, null,
            AbilityDefinition.Kind.PASSIVE);
        AttackDefinition strike = new AttackDefinition("strike", "测试攻击", Map.of(), 50, EnergyType.FIRE, EffectSupport.SUPPORTED, null,
            List.of(), null, null, List.of(), AttackDefinition.DamageTarget.OPPONENT_ACTIVE, 0, 1, false, 0, 0, false, 0, bypass, false);
        CombatCatalog combat = new CombatCatalog(Map.of(
            "attacker", new PokemonProfile(120, 0, null, null, 0, null, 0, 1, List.of(strike)),
            "defender", new PokemonProfile(120, 0, null, null, 0, null, 0, 1, List.of(), List.of(reduction))), Map.of());
        for (long seed = 1; seed < 5000; seed++) {
            ServerMatch match = new ServerMatch(A, B, deck(A, "attacker"), deck(B, "defender"), MatchRulesRegressionTest.RULE,
                new CardDatabase(List.of(attacker, defender)), combat, new ServerRandom(seed));
            OpeningTestSupport.complete(match, A, B, 0);
            for (int turns = 0; turns < 4; turns++) {
                if (match.currentPlayer().equals(A) && match.mayAttack(A)) return match;
                match.endTurn(match.currentPlayer());
            }
        }
        throw new AssertionError("无法建立甲方攻击合法的确定性被动减伤测试对局");
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
