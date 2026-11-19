import dev.hua.ptcglive.Battle;
import java.util.*;

public class BattleTest {
    private static int find(Battle.Player p, Battle.Card.Kind kind) {
        for (int i = 0; i < p.hand.size(); i++) if (p.hand.get(i).kind() == kind) return i;
        return -1;
    }
    public static void main(String[] args) {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        List<Battle.Card> deck = new ArrayList<>();
        for (int i = 0; i < 30; i++) { deck.add(Battle.PIKACHU); deck.add(Battle.ENERGY); }
        Battle game = null;
        for (int seed = 0; seed < 1000; seed++) {
            Battle candidate = new Battle(a, b, deck, deck, new Random(seed));
            Battle.Player x = candidate.player(a), y = candidate.player(b);
            if (x.hand.stream().filter(c -> c.kind() == Battle.Card.Kind.BASIC).count() >= 2
                && y.hand.stream().filter(c -> c.kind() == Battle.Card.Kind.BASIC).count() >= 2
                && find(x, Battle.Card.Kind.ENERGY) >= 0) { game = candidate; break; }
        }
        if (game == null) throw new AssertionError("test fixture unavailable");
        Battle.Player x = game.player(a), y = game.player(b);
        for (UUID id : List.of(a, b)) {
            Battle.Player p = game.player(id);
            game.chooseActive(id, find(p, Battle.Card.Kind.BASIC));
            game.bench(id, find(p, Battle.Card.Kind.BASIC));
            if (p.prizes.size() != 6 || p.hand.size() != 5 || p.deck.size() != 47 || p.bench.size() != 1)
                throw new AssertionError("setup counts");
        }
        try { game.attack(b); throw new AssertionError("out of turn action accepted"); }
        catch (IllegalStateException expected) { }
        try { game.attack(a); throw new AssertionError("attack without energy accepted"); }
        catch (IllegalStateException expected) { }
        game.attach(a, find(x, Battle.Card.Kind.ENERGY));
        if (x.activeEnergy.size() != 1 || x.discard.size() != 0) throw new AssertionError("energy zone");
        try { game.attach(a, find(x, Battle.Card.Kind.ENERGY)); throw new AssertionError("second attachment accepted"); }
        catch (IllegalStateException expected) { }
        game.attack(a);
        if (!game.currentPlayer().equals(b) || y.damage != 30) throw new AssertionError("attack or turn change");
        game.endTurn(b);
        game.attack(a);
        game.endTurn(b);
        game.attack(a);
        if (!y.mustPromote || game.isOver() || x.prizes.size() != 5) throw new AssertionError("knockout or prize");
        try { game.endTurn(b); throw new AssertionError("played before promotion"); }
        catch (IllegalStateException expected) { }
        game.promote(b, 0);
        if (y.active == null || y.mustPromote || y.bench.size() != 0) throw new AssertionError("promotion");
        System.out.println("BattleTest passed");
    }
}
