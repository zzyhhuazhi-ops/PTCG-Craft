import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.field.BattlefieldState;
import dev.hua.ptcglive.engine.field.PokemonProfile;
import dev.hua.ptcglive.rules.CardInstance;
import dev.hua.ptcglive.rules.TurnActionRules;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Ensures per-turn action caps are ruleset parameters, rather than hidden one-use flags. */
public final class TurnActionRulesTest {
    private static final UUID A = new UUID(0, 8101);
    private static final UUID B = new UUID(0, 8102);

    public static void main(String[] args) {
        rejects(() -> new TurnActionRules(-1, 1, 1, 1));
        BattlefieldState field = new BattlefieldState(A, B, Map.of("basic", new PokemonProfile(100, 0, null, null, 0, null, 0, 1, List.of())),
            new ServerRandom(8103), (owner, pokemon) -> 0, 2, 2, 1);
        field.placeInitialActive(A, card(A, "basic"));
        field.placeOnBench(A, card(A, "basic"));
        rejects(() -> field.placeOnBench(A, card(A, "basic")));
        field.placeInitialActive(B, card(B, "basic"));
        field.beginTurn(A);

        field.attachEnergy(A, field.activeView(A).pokemonId(), card(A, "energy"));
        field.attachEnergy(A, field.activeView(A).pokemonId(), card(A, "energy"));
        rejects(() -> field.attachEnergy(A, field.activeView(A).pokemonId(), card(A, "energy")));

        UUID originalActive = field.activeView(A).pokemonId();
        UUID originalBench = field.benchView(A).getFirst().pokemonId();
        field.retreat(A, originalBench, List.of());
        field.retreat(A, originalActive, List.of());
        rejects(() -> field.retreat(A, originalBench, List.of()));
        System.out.println("TurnActionRulesTest passed: configurable energy and retreat caps are server-enforced");
    }

    private static CardInstance card(UUID owner, String definition) {
        return new CardInstance(UUID.randomUUID(), owner, definition);
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("动作次数上限没有被服务端拒绝");
    }
}
