import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Synthetic battle fixtures exercise rules, not purported official card texts. */
public final class MatchRulesRegressionTest {
    static final UUID A = new UUID(0, 201), B = new UUID(0, 202);
    static final Ruleset RULE = BundledRules.loadRulesets().get("standard-international-2026");
    public static void main(String[] args) {
        evolution(); checkup(); privacy();
        System.out.println("MatchRulesRegressionTest passed: evolution timing, atomic rejection, checkup KO, simultaneous wins and hidden zones");
    }
    static void evolution() {
        ServerMatch m = game(100, List.of()); setup(m, true);
        m.endTurn(A);
        UUID target = m.activePokemonView(B).pokemonId();
        var evo = hand(m, B, "stage-one");
        int count = m.privateView(B).hand().size();
        rejects(() -> m.evolve(B, target, evo.instanceId()));
        check(m.privateView(B).hand().size() == count, "rejected evolution removed a card");
        m.endTurn(B); m.endTurn(A);
        m.evolve(B, target, evo.instanceId());
        check(m.activePokemonView(B).pokemonId().equals(target), "evolution changed Pokémon identity");
        rejects(() -> m.evolve(B, target, hand(m, B, "stage-two").instanceId()));
        var energy = hand(m, B, "energy");
        UUID benched = m.benchPokemonView(B).getFirst().pokemonId();
        m.attachEnergy(B, energy.instanceId(), benched);
        rejects(() -> m.attachEnergy(B, energy.instanceId(), target));
        m.retreat(B, benched, List.of()); // Stage 1 has a zero retreat cost.
        check(m.activePokemonView(B).attachedEnergyIds().contains(energy.instanceId()), "bench energy lost on promotion");
        m.endTurn(B); m.endTurn(A);
        m.evolve(B, target, hand(m, B, "stage-two").instanceId());
        check(m.benchPokemonView(B).stream().anyMatch(v -> v.topCardId().equals("stage-two")), "bench evolution not reflected");
    }
    static void checkup() {
        var poisonBoth = List.of(new ConditionEffect(ConditionEffect.Target.DEFENDER, SpecialCondition.POISONED),
            new ConditionEffect(ConditionEffect.Target.SELF, SpecialCondition.POISONED));
        ServerMatch tie = game(10, poisonBoth); setup(tie, false);
        tie.endTurn(A); tie.attack(B, "effect");
        check(tie.phase() == MatchPhase.PRIZE_SELECTION, "checkup KO did not pause for prizes");
        check(tie.publicView().get(A).activeCardId() == null && tie.publicView().get(B).activeCardId() == null,
            "simultaneous KOs must remove both Pokémon before any claim");
        while (tie.phase() == MatchPhase.PRIZE_SELECTION) {
            UUID chooser = tie.pendingChoicePlayer();
            tie.takePrizeAt(chooser, 0);
        }
        check(tie.result() != null && tie.result().winner() == null && tie.result().reason() == MatchResult.Reason.DRAW,
            "simultaneous no-Pokémon wins must tie, not favour first resolver");

        ServerMatch replacement = game(10, poisonBoth); setup(replacement, true);
        replacement.endTurn(A); replacement.attack(B, "effect");
        while (replacement.phase() == MatchPhase.PRIZE_SELECTION) replacement.takePrizeAt(replacement.pendingChoicePlayer(), 0);
        while (replacement.phase() == MatchPhase.CHOOSE_REPLACEMENT) {
            UUID chooser = replacement.pendingChoicePlayer();
            replacement.chooseReplacement(chooser, replacement.benchPokemonView(chooser).getFirst().pokemonId());
        }
        check(replacement.phase() == MatchPhase.TURN && replacement.currentPlayer().equals(A), "checkup did not resume once");
        check(replacement.activePokemonView(A).damage() == 0, "checkup was repeated on replacement");
    }
    static void privacy() {
        ServerMatch m = game(100, List.of());
        check(m.clientView(A).hand().isEmpty(), "opening hand leaked before turn order was decided");
        setup(m, true);
        var own = m.clientView(A); var observer = m.clientView(null);
        check(!own.hand().isEmpty() && observer.hand().isEmpty(), "spectator received a hand");
        check(own.prizeSlots().size() == 6 && observer.prizeSlots().isEmpty(), "prize selection slots incorrect");
        String json = new com.google.gson.Gson().toJson(own);
        for (CardInstance c : m.privateView(A).deck()) check(!json.contains(c.instanceId().toString()), "deck instance leaked");
        for (CardInstance c : m.privateView(A).prizes()) check(!json.contains(c.instanceId().toString()), "face-down prize identity leaked");
        rejects(() -> m.clientView(UUID.randomUUID()));
    }
    static ServerMatch game(int hp, List<ConditionEffect> effects) {
        List<CardDefinition> defs = new ArrayList<>();
        for (String id : List.of("base", "stage-one", "stage-two", "energy")) {
            boolean energy = id.equals("energy");
            defs.add(new CardDefinition(id, id, energy ? CardCategory.ENERGY : CardCategory.POKEMON, TrainerCategory.NONE,
                "H", Set.of("international"), id.equals("base"), energy, energy ? 999 : 4, Map.of(), EffectSupport.SUPPORTED, null));
        }
        var attack = new AttackDefinition("effect", "测试状态攻击", Map.of(), 0, EnergyType.COLORLESS, EffectSupport.SUPPORTED, null, effects);
        var combat = new CombatCatalog(Map.of("base", new PokemonProfile(hp, 0, null, null, 0, null, 0, 1, List.of(attack)),
            "stage-one", new PokemonProfile(120, 0, "base"), "stage-two", new PokemonProfile(160, 0, "stage-one")), Map.of("energy", EnergyType.LIGHTNING));
        // Find deterministic fixtures with the needed opening cards; no test mutates the match state.
        for (int seed = 0; seed < 10000; seed++) {
            var aDeck = deck(A); var bDeck = deck(B);
            var m = new ServerMatch(A, B, aDeck, bDeck, RULE, new CardDatabase(defs), combat, new ServerRandom(seed));
            OpeningTestSupport.decideOrder(m, A, B);
            if (List.of(A, B).stream().allMatch(p -> m.privateView(p).hand().stream().filter(c -> c.definitionId().equals("base")).count() >= 2)
                && List.of("stage-one", "stage-two", "energy").stream().allMatch(id -> m.privateView(B).hand().stream().anyMatch(c -> c.definitionId().equals(id))))
                return new ServerMatch(A, B, aDeck, bDeck, RULE, new CardDatabase(defs), combat, new ServerRandom(seed));
        }
        throw new AssertionError("fixture not found");
    }
    static List<CardInstance> deck(UUID player) {
        List<CardInstance> result = new ArrayList<>();
        for (int i = 0; i < 60; i++) result.add(new CardInstance(UUID.randomUUID(), player,
            i < 24 ? "base" : i < 36 ? "stage-one" : i < 48 ? "stage-two" : "energy"));
        return result;
    }
    static void setup(ServerMatch m, boolean bench) {
        OpeningTestSupport.complete(m, A, B, bench ? 1 : 0);
    }
    static CardInstance hand(ServerMatch m, UUID p, String id) { return m.privateView(p).hand().stream().filter(c -> c.definitionId().equals(id)).findFirst().orElseThrow(); }
    static void rejects(Runnable action) { try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; } throw new AssertionError("illegal action accepted"); }
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
