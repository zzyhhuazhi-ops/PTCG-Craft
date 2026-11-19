package dev.hua.ptcglive;

import java.util.*;

/** Server-owned, transport-independent battle state. No client supplied state is trusted. */
public final class Battle {
    public record Card(String id, String name, Kind kind, int hp, int damage, int cost) {
        public enum Kind { BASIC, ENERGY }
    }
    public static final Card PIKACHU = new Card("pikachu", "皮卡丘", Card.Kind.BASIC, 70, 30, 1);
    public static final Card EEVEE = new Card("eevee", "伊布", Card.Kind.BASIC, 60, 20, 1);
    public static final Card ENERGY = new Card("lightning", "基本能量", Card.Kind.ENERGY, 0, 0, 0);
    public static List<Card> sampleDeck() {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < 4; i++) { cards.add(PIKACHU); cards.add(EEVEE); }
        for (int i = 0; i < 52; i++) cards.add(ENERGY);
        return cards;
    }
    public static final class Player {
        public final UUID id;
        public final List<Card> deck = new ArrayList<>(), hand = new ArrayList<>(), prizes = new ArrayList<>(), discard = new ArrayList<>(), bench = new ArrayList<>(), activeEnergy = new ArrayList<>();
        public Card active;
        public int damage;
        public boolean attachedThisTurn, mustPromote;
        private Player(UUID id) { this.id = id; }
        public int remainingHp() { return active == null ? 0 : Math.max(0, active.hp() - damage); }
    }
    private final Player[] players;
    private final Random random;
    private int current = 0;
    private boolean over;
    private UUID winner;

    public Battle(UUID a, UUID b, List<Card> aDeck, List<Card> bDeck, Random random) {
        if (a.equals(b)) throw new IllegalArgumentException("需要两名不同玩家");
        this.random = Objects.requireNonNull(random);
        players = new Player[]{new Player(a), new Player(b)};
        setup(players[0], aDeck); setup(players[1], bDeck);
    }
    private void setup(Player p, List<Card> source) {
        if (source.size() != 60 || source.stream().noneMatch(c -> c.kind() == Card.Kind.BASIC))
            throw new IllegalArgumentException("卡组须有60张牌且包含基础宝可梦");
        p.deck.addAll(source); Collections.shuffle(p.deck, random);
        do {
            p.deck.addAll(p.hand); p.hand.clear(); Collections.shuffle(p.deck, random);
            for (int i = 0; i < 7; i++) drawRaw(p);
        } while (p.hand.stream().noneMatch(c -> c.kind() == Card.Kind.BASIC));
        for (int i = 0; i < 6; i++) p.prizes.add(p.deck.remove(p.deck.size() - 1));
    }
    private void drawRaw(Player p) { p.hand.add(p.deck.remove(p.deck.size() - 1)); }
    public Player player(UUID id) {
        for (Player p : players) if (p.id.equals(id)) return p;
        throw new IllegalArgumentException("玩家未加入此对战");
    }
    private Player turn(UUID id) {
        if (over) throw new IllegalStateException("对战已结束");
        Player p = player(id);
        if (p != players[current]) throw new IllegalStateException("尚未轮到你");
        if (p.mustPromote) throw new IllegalStateException("先使用 /ptcg promote <序号> 选择替补");
        return p;
    }
    public UUID currentPlayer() { return players[current].id; }
    public boolean isOver() { return over; }
    public UUID winner() { return winner; }
    public void chooseActive(UUID id, int handIndex) {
        Player p = player(id);
        if (over || p.active != null) throw new IllegalStateException("不能设置战斗宝可梦");
        Card card = at(p, handIndex);
        if (card.kind() != Card.Kind.BASIC) throw new IllegalArgumentException("请选择基础宝可梦");
        p.active = p.hand.remove(handIndex);
    }
    private Card at(Player p, int index) {
        if (index < 0 || index >= p.hand.size()) throw new IllegalArgumentException("手牌序号无效");
        return p.hand.get(index);
    }
    private void ready() {
        if (players[0].active == null || players[1].active == null)
            throw new IllegalStateException("双方须先设置战斗宝可梦");
    }
    public void bench(UUID id, int handIndex) {
        Player p = player(id);
        if (over || p.active == null || p.mustPromote) throw new IllegalStateException("请先设置战斗宝可梦");
        if (p.bench.size() >= 5) throw new IllegalStateException("备战区已满");
        if (at(p, handIndex).kind() != Card.Kind.BASIC) throw new IllegalArgumentException("请选择基础宝可梦");
        p.bench.add(p.hand.remove(handIndex));
    }
    public void promote(UUID id, int benchIndex) {
        Player p = player(id);
        if (over || !p.mustPromote) throw new IllegalStateException("目前不需要选择替补");
        if (benchIndex < 0 || benchIndex >= p.bench.size()) throw new IllegalArgumentException("备战区序号无效");
        p.active = p.bench.remove(benchIndex); p.mustPromote = false;
    }
    public void attach(UUID id, int handIndex) {
        Player p = turn(id); ready();
        if (p.attachedThisTurn) throw new IllegalStateException("每回合只能附加一次能量");
        if (at(p, handIndex).kind() != Card.Kind.ENERGY) throw new IllegalArgumentException("请选择能量卡");
        p.activeEnergy.add(p.hand.remove(handIndex)); p.attachedThisTurn = true;
    }
    public void attack(UUID id) {
        Player p = turn(id); ready();
        if (p.activeEnergy.size() < p.active.cost()) throw new IllegalStateException("能量不足");
        Player other = players[1 - current];
        other.damage += p.active.damage();
        if (other.remainingHp() == 0) {
            other.discard.add(other.active); other.discard.addAll(other.activeEnergy);
            other.activeEnergy.clear(); other.active = null; other.damage = 0;
            if (!p.prizes.isEmpty()) p.hand.add(p.prizes.remove(p.prizes.size() - 1));
            if (p.prizes.isEmpty() || other.bench.isEmpty()) { over = true; winner = p.id; }
            else { other.mustPromote = true; endTurnInternal(); }
        } else endTurnInternal();
    }
    public void endTurn(UUID id) { turn(id); ready(); endTurnInternal(); }
    private void endTurnInternal() {
        current = 1 - current;
        Player p = players[current]; p.attachedThisTurn = false;
        if (p.deck.isEmpty()) { over = true; winner = players[1 - current].id; }
        else drawRaw(p);
    }
}
