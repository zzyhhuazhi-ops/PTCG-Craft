import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Official Rulebook setup / mulligan sequence; synthetic cards, never a claimed official deck. */
public final class OpeningSequenceTest {
    static final UUID A = new UUID(0, 501), B = new UUID(0, 502);
    static final CardDatabase DATA = BundledRules.loadCardDefinitions();
    static final CombatCatalog COMBAT = BundledCombatData.load();
    static final Ruleset RULE = BundledRules.loadRulesets().get("standard-international-2026");
    public static void main(String[] args) {
        coinBeforeCards(); prizesBeforeReveal(); singleMulliganAndBonusBench(); simultaneousMulligans(); officialDifferenceExample();
        System.out.println("OpeningSequenceTest passed: coin/order, setup/prize timing, revealed failed hands, simultaneous/differential mulligans, bonus-only bench, authorization and privacy");
    }
    static void coinBeforeCards() {
        check(!RULE.version().isBlank() && RULE.setupProcedure() == SetupProcedure.INTERNATIONAL_V1, "Ruleset must select the versioned setup procedure");
        rejects(() -> SetupProcedure.fromId("unknown-future-rules"));
        var m = game(0, 4, 4);
        check(m.phase() == MatchPhase.COIN_CALL, "Every game must begin with the coin call, not compensation draws");
        for (UUID p : List.of(A, B)) {
            check(m.privateView(p).hand().isEmpty(), "Do not deal opening cards before the turn-order decision");
            check(m.privateView(p).deck().size() == 60 && m.mulliganCount(p) == 0, "Opening setup happened too soon");
        }
        check(m.randomTranscript().isEmpty(), "No shuffle should precede the opening coin in the audit");
        m.callCoin(A, CoinSide.HEADS);
        check(m.randomTranscript().size() == 1 && m.randomTranscript().getFirst().operation().equals("coin-flip"), "First random event must be the coin flip");
        check(m.clientView(A).hand().isEmpty(), "Coin winner must not inspect opening cards before choosing first/second");
    }
    static void prizesBeforeReveal() {
        var m = game(1, 60, 60); begin(m);
        m.chooseOpeningActive(A, m.privateView(A).hand().getFirst().instanceId());
        m.startAfterOpening(A);
        check(m.privateView(A).prizes().size() == 6, "Ready player's Prizes must be set before opponent's later mulligan");
        check(m.privateView(B).prizes().isEmpty(), "Unready player's Prizes must not be set prematurely");
        check(m.clientView(B).players().getFirst().active() == null, "Opponent saw face-down Active before setup was complete");
        check(m.publicView().get(A).activeCardId() == null, "Public API leaked the face-down Active");
        m.chooseOpeningActive(B, m.privateView(B).hand().getFirst().instanceId());
        m.startAfterOpening(B);
        check(m.phase() == MatchPhase.TURN && m.turnNumber() == 1, "Game did not start after both setups");
        check(m.privateView(A).hand().size() == 7 && m.privateView(B).hand().size() == 6, "Only the first player draws at turn start");
        check(m.privateView(A).prizes().size() == 6 && m.privateView(B).prizes().size() == 6, "Prizes were set more than once");
    }
    static void singleMulliganAndBonusBench() {
        for (int seed = 0; seed < 300; seed++) {
            var m = game(seed, 1, 40); begin(m);
            if (!m.needsMulligan(A) || m.needsMulligan(B)) continue;
            check(m.phase() == MatchPhase.OPENING_POKEMON && m.clientView(null).mulliganReveals().isEmpty(), "Single mulligan was revealed before valid player set up");
            rejects(() -> m.acknowledgeMulligan(A));
            List<String> failedHand = m.privateView(A).hand().stream().map(CardInstance::definitionId).toList();
            setUp(m, B);
            var prizes = m.privateView(B).prizes();
            check(m.phase() == MatchPhase.MULLIGAN_REVEAL && prizes.size() == 6, "Valid player's Prizes must precede the reveal");
            var reveals = m.clientView(null).mulliganReveals();
            check(reveals.size() == 1 && reveals.getFirst().playerId().equals(A) && reveals.getFirst().cardIds().equals(failedHand), "Reveal is not exactly the failed opening hand");
            check(reveals.getFirst().cardIds().stream().noneMatch(id -> DATA.find(id).orElseThrow().basicPokemon()), "A valid opening hand was revealed");
            checkPrivacy(m);
            var handBeforeAck = m.privateView(A).hand();
            m.acknowledgeMulligan(A);
            rejects(() -> m.acknowledgeMulligan(A)); rejects(() -> m.acknowledgeMulligan(new UUID(0, 9999)));
            check(m.privateView(A).hand().equals(handBeforeAck), "One acknowledgment redrew before opponent could inspect");
            m.acknowledgeMulligan(B); driveToCompensation(m);
            check(m.privateView(B).prizes().equals(prizes), "Opponent's repeated mulligans changed already set Prizes");
            check(m.phase() == MatchPhase.MULLIGAN_DRAW && m.mulliganChosen(A), "Only the entitled player should choose compensation");
            int allowance = m.mulliganDrawLimit(B);
            check(allowance == Math.min(m.mulliganCount(A), 47), "Compensation must use the extra mulligans and post-Prize deck");
            var top = m.privateView(B).deck().getLast();
            if (!DATA.find(top.definitionId()).orElseThrow().basicPokemon()) continue;
            var previousBasic = m.privateView(B).hand().stream().filter(c -> DATA.find(c.definitionId()).orElseThrow().basicPokemon()).findFirst().orElseThrow();
            var before = m.privateView(B);
            rejects(() -> m.chooseMulliganDraw(B, -1)); rejects(() -> m.chooseMulliganDraw(B, allowance + 1));
            check(m.privateView(B).equals(before), "Rejected compensation mutated cards");
            m.chooseMulliganDraw(B, 1);
            check(m.phase() == MatchPhase.MULLIGAN_BENCH && m.privateView(B).hand().contains(top), "Compensation drew the wrong post-Prize card");
            rejects(() -> m.chooseMulliganDraw(B, 1));
            rejects(() -> m.placeMulliganBench(B, previousBasic.instanceId()));
            rejects(() -> m.placeMulliganBench(A, top.instanceId()));
            rejects(() -> m.chooseOpeningActive(B, top.instanceId()));
            check(m.clientView(A).players().get(1).active() == null, "Field revealed before bonus bench decision");
            m.placeMulliganBench(B, top.instanceId());
            rejects(() -> m.placeMulliganBench(B, top.instanceId()));
            m.finishMulliganBench(B);
            check(m.phase() == MatchPhase.TURN && m.benchPokemonView(B).size() == 1, "Bonus Basic was not put on Bench");
            check(m.privateView(B).prizes().equals(prizes), "Bonus draw altered Prize identities");
            checkPrivacy(m); return;
        }
        throw new AssertionError("Single mulligan / bonus Basic fixture not found");
    }
    static void simultaneousMulligans() {
        for (int seed = 0; seed < 200; seed++) {
            var m = game(seed, 1, 1); begin(m);
            if (m.phase() != MatchPhase.MULLIGAN_REVEAL) continue;
            check(m.clientView(null).mulliganReveals().size() == 2, "Both invalid hands must be revealed together");
            check(m.mulliganCount(A) == 1 && m.mulliganCount(B) == 1, "Shared mulligan counts");
            for (UUID p : List.of(A, B)) check(m.privateView(p).prizes().isEmpty(), "Prizes before a valid hand");
            check(m.mulliganDrawLimit(A) == 0 && m.mulliganDrawLimit(B) == 0, "Shared mulligans must cancel");
            checkPrivacy(m);
            driveToCompensation(m);
            check(m.mulliganDrawLimit(A) == Math.min(Math.max(0, m.mulliganCount(B) - m.mulliganCount(A)), m.privateView(A).deck().size()), "Difference formula for A");
            check(m.mulliganDrawLimit(B) == Math.min(Math.max(0, m.mulliganCount(A) - m.mulliganCount(B)), m.privateView(B).deck().size()), "Difference formula for B");
            OpeningTestSupport.complete(m, A, B, 0);
            var replay = game(seed, 1, 1); OpeningTestSupport.complete(replay, A, B, 0);
            check(m.mulliganHistory().equals(replay.mulliganHistory()) && m.randomTranscript().equals(replay.randomTranscript()), "Same seed/actions must reproduce mulligans");
            check(m.privateView(A).equals(replay.privateView(A)) && m.privateView(B).equals(replay.privateView(B)), "Replay changed hidden card zones");
            return;
        }
        throw new AssertionError("Simultaneous mulligan fixture not found");
    }
    static void officialDifferenceExample() {
        // Official p18 example: both took two; A then took three more; B may draw 0..3.
        for (int seed = 0; seed < 5000; seed++) {
            var m = game(seed, 1, 1); begin(m); driveToCompensation(m);
            if (m.mulliganCount(A) != 5 || m.mulliganCount(B) != 2) continue;
            check(m.phase() == MatchPhase.MULLIGAN_DRAW && m.mulliganDrawLimit(B) == 3 && m.mulliganDrawLimit(A) == 0, "Official 5-vs-2 example must award 3, not 5");
            int count = m.privateView(B).hand().size(); m.chooseMulliganDraw(B, 0);
            check(m.phase() == MatchPhase.TURN && m.privateView(B).hand().size() == count, "Declining optional compensation must be allowed");
            return;
        }
        throw new AssertionError("Official 5-vs-2 seeded example not found");
    }
    static void driveToCompensation(ServerMatch m) {
        for (int step = 0; step < 1000; step++) {
            if (m.phase() == MatchPhase.MULLIGAN_DRAW || m.phase() == MatchPhase.TURN) return;
            if (m.phase() == MatchPhase.MULLIGAN_REVEAL) { m.acknowledgeMulligan(A); m.acknowledgeMulligan(B); }
            else if (m.phase() == MatchPhase.OPENING_POKEMON) {
                for (UUID p : List.of(A, B)) if (m.phase() == MatchPhase.OPENING_POKEMON && !m.openingReady(p) && !m.needsMulligan(p)) setUp(m, p);
            } else throw new AssertionError("Unexpected phase " + m.phase());
        }
        throw new AssertionError("Opening failed to progress");
    }
    static void setUp(ServerMatch m, UUID p) {
        var basic = m.privateView(p).hand().stream().filter(c -> DATA.find(c.definitionId()).orElseThrow().basicPokemon()).findFirst().orElseThrow();
        m.chooseOpeningActive(p, basic.instanceId()); m.startAfterOpening(p);
    }
    static void checkPrivacy(ServerMatch m) {
        String publicJson = new com.google.gson.Gson().toJson(m.clientView(null));
        for (UUID p : List.of(A, B)) for (var cards : List.of(m.privateView(p).hand(), m.privateView(p).deck(), m.privateView(p).prizes()))
            for (var card : cards) check(!publicJson.contains(card.instanceId().toString()), "Physical ID leaked from a hidden zone or reveal proof");
        check(m.clientView(null).hand().isEmpty() && m.clientView(null).prizeSlots().isEmpty(), "Observer received private selections");
    }
    static void rejects(Runnable action) { MatchRulesRegressionTest.rejects(action); }
    static ServerMatch game(long seed, int aBasics, int bBasics) {
        return new ServerMatch(A, B, deck(A, aBasics), deck(B, bBasics), RULE, DATA, COMBAT, new ServerRandom(seed));
    }
    static List<CardInstance> deck(UUID owner, int basics) {
        List<CardInstance> cards = new ArrayList<>();
        for (int i = 0; i < 60; i++) cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(), i + 1), owner,
            i < basics ? "fixture-basic-h" : "fixture-energy-h"));
        return cards;
    }
    static void begin(ServerMatch m) {
        m.callCoin(A, CoinSide.HEADS); UUID winner = m.publicCoinResult() == CoinSide.HEADS ? A : B;
        m.chooseWhoGoesFirst(winner, winner.equals(A));
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
