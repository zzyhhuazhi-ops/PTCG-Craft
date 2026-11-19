package dev.hua.ptcglive.client;

/** Frame-time presentation only; no server tick, card-count or RNG dependency. */
public final class PackRevealAnimation {
    public enum Stage { PACK, OPENING, FIRST_ENTER, FLIP, HOLD, SWITCH, SUMMARY }
    public static final double PACK_WAIT = .15, PACK_OPEN = .46, STACK = .18, FLIP = .24, SWITCH = .18;
    private Stage stage = Stage.PACK;
    private double elapsed;
    private int cards, card;

    public void reset(int count) { cards = Math.max(0, count); card = 0; elapsed = 0; stage = cards == 0 ? Stage.SUMMARY : Stage.PACK; }
    public Stage stage() { return stage; }
    public double elapsed() { return elapsed; }
    public int cardIndex() { return card; }
    public double progress() { return Math.min(1, elapsed / duration()); }
    public double duration() {
        return switch (stage) {
            case PACK -> PACK_WAIT; case OPENING -> PACK_OPEN; case FIRST_ENTER -> STACK;
            case FLIP -> FLIP; case SWITCH -> SWITCH; default -> Double.POSITIVE_INFINITY;
        };
    }
    public void update(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0) throw new IllegalArgumentException("Invalid animation frame time");
        elapsed += seconds;
        while (elapsed >= duration()) {
            elapsed -= duration();
            switch (stage) {
                case PACK -> stage = Stage.OPENING;
                case OPENING -> stage = Stage.FIRST_ENTER;
                case FIRST_ENTER -> stage = Stage.FLIP;
                case FLIP -> stage = Stage.HOLD;
                case SWITCH -> { if (++card >= cards) stage = Stage.SUMMARY; else stage = Stage.HOLD; }
                default -> { }
            }
        }
    }
    public void proceed() {
        if (stage == Stage.PACK) { stage = Stage.OPENING; elapsed = 0; }
        else if (stage == Stage.HOLD) { stage = Stage.SWITCH; elapsed = 0; }
    }
    public void skip() { stage = Stage.SUMMARY; elapsed = 0; }
}
