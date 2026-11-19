package dev.hua.ptcglive.engine.tournament;

/** The match structures exposed by the server; each individual game remains two-player. */
public enum TournamentMatchFormat {
    SINGLE_GAME(1, 1),
    BEST_OF_THREE(3, 2);

    private final int maximumGames;
    private final int winsRequired;

    TournamentMatchFormat(int maximumGames, int winsRequired) {
        this.maximumGames = maximumGames;
        this.winsRequired = winsRequired;
    }

    public int maximumGames() { return maximumGames; }
    public int winsRequired() { return winsRequired; }
}
