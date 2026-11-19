import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.field.AttackDefinition;
import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.engine.field.PokemonProfile;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** A declared post-attack heal is resolved by the server as part of one attack, never as a client action. */
public final class AttackSelfHealTest {
    private static final UUID A = new UUID(0, 5801), B = new UUID(0, 5802);
    public static void main(String[] args) {
        ServerMatch match = fixture();
        while (!match.currentPlayer().equals(A) || !match.mayAttack(A)) match.endTurn(match.currentPlayer());
        UUID active = match.activePokemonView(A).pokemonId();
        match.placeDamageCountersByEffect(A, A, active, 5);
        match.attack(A, "recover");
        if (match.activePokemonView(A).damage() != 20)
            throw new AssertionError("攻击后回复没有在服务器攻击流程中按 30 点正确结算");
        if (match.replayTranscript().stream().noneMatch(event -> event.operation().equals("使用攻击")))
            throw new AssertionError("攻击后回复没有保留攻击回放事实");
        System.out.println("AttackSelfHealTest passed: post-attack self-heal is server-resolved and bounded");
    }
    private static ServerMatch fixture() {
        var definitions = new CardDatabase(List.of(definition("healer"), definition("target")));
        var recover = new AttackDefinition("recover", "测试回复攻击", Map.of(), 20, EnergyType.COLORLESS,
            EffectSupport.SUPPORTED, null, List.of(), null, null, List.of(), AttackDefinition.DamageTarget.OPPONENT_ACTIVE, 0, 1, false, 30);
        var combat = new CombatCatalog(Map.of("healer", new PokemonProfile(120, 0, null, null, 0, null, 0, 1, List.of(recover)),
            "target", new PokemonProfile(120, 0, null, null, 0, null, 0, 1, List.of())), Map.of());
        var match = new ServerMatch(A, B, deck(A, "healer"), deck(B, "target"), MatchRulesRegressionTest.RULE, definitions, combat, new ServerRandom(5803));
        OpeningTestSupport.complete(match, A, B, 0);
        return match;
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
