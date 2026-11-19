import dev.hua.ptcglive.engine.*;
import java.util.*;

/** Test-only user actions through the public core API; never mutates a match's private collections. */
final class OpeningTestSupport {
    static void decideOrder(ServerMatch m, UUID a, UUID b) {
        if (m.phase() != MatchPhase.COIN_CALL) return;
        m.callCoin(a, CoinSide.HEADS); UUID winner = m.publicCoinResult() == CoinSide.HEADS ? a : b;
        m.chooseWhoGoesFirst(winner, winner.equals(a));
    }
    static void complete(ServerMatch m, UUID a, UUID b, int benches) {
        decideOrder(m, a, b);
        int steps = 0;
        while (m.phase() != MatchPhase.TURN && m.phase() != MatchPhase.FINISHED) {
            if (++steps > 1000) throw new AssertionError("Setup did not terminate");
            switch (m.phase()) {
                case OPENING_POKEMON -> {
                    for (UUID p : List.of(a, b)) {
                        if (m.phase() != MatchPhase.OPENING_POKEMON || m.openingReady(p) || m.needsMulligan(p)) continue;
                        var basics = m.privateView(p).hand().stream().filter(c -> m.cardDefinition(c.definitionId()).basicPokemon()).toList();
                        if (basics.size() < benches + 1) throw new AssertionError("Fixture lacks requested opening Pokémon");
                        m.chooseOpeningActive(p, basics.getFirst().instanceId());
                        for (int i = 0; i < benches; i++) m.placeOpeningBench(p, basics.get(i + 1).instanceId());
                        m.startAfterOpening(p);
                    }
                }
                case MULLIGAN_REVEAL -> { m.acknowledgeMulligan(a); m.acknowledgeMulligan(b); }
                case MULLIGAN_DRAW -> {
                    for (UUID p : List.of(a, b)) if (m.phase() == MatchPhase.MULLIGAN_DRAW && !m.mulliganChosen(p)) m.chooseMulliganDraw(p, 0);
                }
                case MULLIGAN_BENCH -> {
                    for (UUID p : List.of(a, b)) if (m.phase() == MatchPhase.MULLIGAN_BENCH && !m.mulliganBenchReady(p)) m.finishMulliganBench(p);
                }
                default -> throw new AssertionError("Unexpected setup phase: " + m.phase());
            }
        }
    }
}
