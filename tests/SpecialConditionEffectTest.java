import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.field.AttackDefinition;
import dev.hua.ptcglive.engine.field.ConditionEffect;
import dev.hua.ptcglive.engine.field.EnergyType;
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

/** Curing is a card-effect operation: it changes conditions, not damage or turn-action counters. */
public final class SpecialConditionEffectTest {
    private static final UUID A = new UUID(0, 8601), B = new UUID(0, 8602);
    public static void main(String[] args) {
        ServerMatch match = fixture();
        while (!match.currentPlayer().equals(A) || !match.mayAttack(A)) match.endTurn(match.currentPlayer());
        match.attack(A, "poison");
        UUID target = match.activePokemonView(B).pokemonId();
        int damage = match.activePokemonView(B).damage();
        match.clearSpecialConditionsByEffect(B, B, target, Set.of(SpecialCondition.POISONED));
        if (!match.activePokemonView(B).conditions().isEmpty() || match.activePokemonView(B).damage() != damage)
            throw new AssertionError("移除异常状态错误清除了伤害，或未清除目标状态");
        try { match.clearSpecialConditionsByEffect(B, B, target, Set.of(SpecialCondition.POISONED)); throw new AssertionError("没有异常状态的空操作被接受"); }
        catch (IllegalStateException expected) { }
        if (!match.replayTranscript().getLast().operation().equals("已审核效果移除异常状态"))
            throw new AssertionError("移除异常状态没有进入服务器回放");
        System.out.println("SpecialConditionEffectTest passed: server-only condition curing preserves damage and rejects no-op");
    }
    private static ServerMatch fixture() {
        CardDefinition attacker = definition("attacker"), defender = definition("defender");
        var attack = new AttackDefinition("poison", "测试中毒", Map.of(), 0, EnergyType.COLORLESS, EffectSupport.SUPPORTED, null,
            List.of(new ConditionEffect(ConditionEffect.Target.DEFENDER, SpecialCondition.POISONED)));
        var combat = new CombatCatalog(Map.of("attacker", new PokemonProfile(120, 0, null, null, 0, null, 0, 1, List.of(attack)),
            "defender", new PokemonProfile(120, 0, null, null, 0, null, 0, 1, List.of())), Map.of());
        ServerMatch match = new ServerMatch(A, B, deck(A, "attacker"), deck(B, "defender"), MatchRulesRegressionTest.RULE,
            new CardDatabase(List.of(attacker, defender)), combat, new ServerRandom(8603));
        OpeningTestSupport.complete(match, A, B, 0);
        return match;
    }
    private static CardDefinition definition(String id) { return new CardDefinition(id, id, CardCategory.POKEMON, TrainerCategory.NONE, "H", Set.of("international"), true, false, 4, Map.of(), EffectSupport.SUPPORTED, null); }
    private static List<CardInstance> deck(UUID owner, String id) { List<CardInstance> cards = new ArrayList<>(); for (int i = 0; i < 60; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, id)); return cards; }
}
