import dev.hua.ptcglive.engine.MatchPhase;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.field.AttackDefinition;
import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.engine.field.PokemonProfile;
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

/** Damage-counter placement is an effect, not attack damage modified by weakness/resistance. */
public final class DamageCounterPlacementTest {
    private static final UUID A = new UUID(0, 8501), B = new UUID(0, 8502);
    public static void main(String[] args) {
        ServerMatch match = fixture();
        while (!match.currentPlayer().equals(A)) match.endTurn(match.currentPlayer());
        UUID target = match.benchPokemonView(B).getFirst().pokemonId();
        try { match.placeDamageCountersByEffect(A, B, target, 0); throw new AssertionError("零个伤害指示物被接受"); }
        catch (IllegalArgumentException expected) { }
        match.placeDamageCountersByEffect(A, B, target, 9);
        if (match.phase() != MatchPhase.PRIZE_SELECTION || !match.pendingChoicePlayer().equals(A))
            throw new AssertionError("伤害指示物击倒未进入同一套奖赏结算");
        if (!match.benchPokemonView(B).isEmpty() || match.activePokemonView(B) == null)
            throw new AssertionError("放置伤害指示物错误移除了战斗宝可梦");
        match.takePrizeAt(A, 0);
        System.out.println("DamageCounterPlacementTest passed: effect counters bypass attack modifiers and use KO/prize resolution");
    }
    private static ServerMatch fixture() {
        CardDefinition attacker = definition("attacker"), defender = definition("defender");
        var combat = new CombatCatalog(Map.of(
            "attacker", new PokemonProfile(120, 0, null, null, 0, null, 0, 1, List.of(new AttackDefinition("noop", "测试攻击", Map.of(), 0, EnergyType.COLORLESS, EffectSupport.SUPPORTED, null))),
            "defender", new PokemonProfile(90, 0, null, EnergyType.COLORLESS, 2, EnergyType.GRASS, 30, 1, List.of())), Map.of());
        ServerMatch match = new ServerMatch(A, B, deck(A, "attacker"), deck(B, "defender"), MatchRulesRegressionTest.RULE,
            new CardDatabase(List.of(attacker, defender)), combat, new ServerRandom(8503));
        OpeningTestSupport.complete(match, A, B, 1);
        return match;
    }
    private static CardDefinition definition(String id) { return new CardDefinition(id, id, CardCategory.POKEMON, TrainerCategory.NONE, "H", Set.of("international"), true, false, 4, Map.of(), EffectSupport.SUPPORTED, null); }
    private static List<CardInstance> deck(UUID owner, String id) { List<CardInstance> cards = new ArrayList<>(); for (int i = 0; i < 60; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, id)); return cards; }
}
