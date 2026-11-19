package dev.hua.ptcglive.table;

import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.rules.CardBoxService;
import dev.hua.ptcglive.rules.CardInstance;
import dev.hua.ptcglive.rules.DeckValidationReport;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** State owned by the server for exactly one two-sided official match table. */
public final class CardTableLobby {
    /** Public lobby state. It deliberately contains no card IDs, deck contents or private validation details. */
    public record PublicView(CardTableKey key, String rulesetId, String tournamentSettingId, UUID host, UUID guest, TableLobbyStatus status,
                             boolean hostReady, boolean guestReady, int spectatorCount, UUID matchId) { }
    private final CardTableKey key;
    private final UUID host;
    private String rulesetId;
    private String tournamentSettingId = "system-single";
    private UUID guest;
    private UUID hostBox, guestBox;
    private boolean hostReady, guestReady;
    private final java.util.Set<UUID> spectators = new java.util.LinkedHashSet<>();
    private TableLobbyStatus status = TableLobbyStatus.WAITING;
    private UUID matchId;
    private ServerMatch match;

    CardTableLobby(CardTableKey key, UUID host, String rulesetId) {
        this.key = Objects.requireNonNull(key); this.host = Objects.requireNonNull(host); this.rulesetId = Objects.requireNonNull(rulesetId);
    }
    /**
     * Restores only an unopened lobby.  A live match is never reconstructed from
     * a lobby record because doing so could fabricate hidden cards, random state,
     * or an in-progress effect stack after a server restart.
     */
    static CardTableLobby restoreWaiting(CardTableKey key, UUID host, String rulesetId, UUID guest, UUID hostBox, UUID guestBox) {
        CardTableLobby restored = new CardTableLobby(key, host, rulesetId);
        restored.guest = guest;
        restored.hostBox = hostBox;
        restored.guestBox = guestBox;
        // Ready acknowledgements are intentionally not durable: both players
        // must confirm their still-valid card boxes again after a restart.
        restored.hostReady = false;
        restored.guestReady = false;
        return restored;
    }
    public CardTableKey key() { return key; }
    public UUID host() { return host; }
    public String rulesetId() { return rulesetId; }
    public Optional<UUID> guest() { return Optional.ofNullable(guest); }
    public TableLobbyStatus status() { return status; }
    public Optional<ServerMatch> match() { return Optional.ofNullable(match); }
    public List<UUID> spectators() { return List.copyOf(spectators); }
    public boolean isParticipant(UUID player) { return player.equals(host) || player.equals(guest); }
    public boolean isHost(UUID player) { return player.equals(host); }
    public String tournamentSettingId() { return tournamentSettingId; }
    public PublicView publicView() { return new PublicView(key, rulesetId, tournamentSettingId, host, guest, status, hostReady, guestReady, spectators.size(), matchId); }
    public void chooseRuleset(UUID player, String id) { requireWaiting(); requireHost(player); rulesetId = Objects.requireNonNull(id); hostReady = false; guestReady = false; }
    public void chooseTournamentSetting(UUID player, String id) { requireWaiting(); requireHost(player); tournamentSettingId = Objects.requireNonNull(id); hostReady = false; guestReady = false; }
    public void join(UUID player) {
        requireWaiting(); if (player.equals(host)) throw new IllegalStateException("你已经是本桌房主");
        if (guest != null && !guest.equals(player)) throw new IllegalStateException("本桌已有对手，可使用观战"); guest = player;
    }
    public void leave(UUID player) {
        if (!isParticipant(player)) { spectators.remove(player); return; }
        requireWaiting();
        if (player.equals(guest)) { guest = null; guestBox = null; guestReady = false; return; }
        spectators.remove(player);
        if (player.equals(host)) throw new IllegalStateException("房主不能离开大厅；请结束本桌后重新创建");
    }
    public void spectate(UUID player) { if (player.equals(host) || player.equals(guest)) throw new IllegalStateException("对局玩家无需以旁观者身份加入"); spectators.add(player); }
    public void selectBox(UUID player, UUID boxId) {
        requireWaiting(); requireSeat(player); if (player.equals(host)) { hostBox = boxId; hostReady = false; } else { guestBox = boxId; guestReady = false; }
    }
    public void setReady(UUID player, boolean ready) {
        requireWaiting(); requireSeat(player); if (player.equals(host)) hostReady = ready; else guestReady = ready;
    }
    void start(UUID id, ServerMatch created) { requireWaiting(); matchId = id; match = created; status = TableLobbyStatus.IN_MATCH; }
    void startNext(UUID id, ServerMatch created) { if (status != TableLobbyStatus.BETWEEN_GAMES) throw new IllegalStateException("当前不能开始赛事下一局"); matchId = id; match = created; status = TableLobbyStatus.IN_MATCH; }
    void finishTournamentGame() { if (status != TableLobbyStatus.IN_MATCH) throw new IllegalStateException("当前没有可结束的赛事对局"); match = null; matchId = null; status = TableLobbyStatus.BETWEEN_GAMES; }
    UUID matchId() { if (matchId == null) throw new IllegalStateException("本桌尚未开始"); return matchId; }
    UUID boxFor(UUID player) { requireSeat(player); UUID box = player.equals(host) ? hostBox : guestBox; if (box == null) throw new IllegalStateException("尚未选择卡盒"); return box; }
    /** The box a seated player has chosen, if any; for the player's own view only. */
    public Optional<UUID> boxOf(UUID player) {
        if (player.equals(host)) return Optional.ofNullable(hostBox);
        if (player.equals(guest)) return Optional.ofNullable(guestBox);
        return Optional.empty();
    }
    boolean bothReady() { return guest != null && hostReady && guestReady && hostBox != null && guestBox != null; }
    List<UUID> players() { return guest == null ? List.of(host) : List.of(host, guest); }
    void end() { status = TableLobbyStatus.ENDED; match = null; }
    void resetAfterEnd() { status = TableLobbyStatus.WAITING; matchId = null; hostReady = false; guestReady = false; }
    private void requireWaiting() { if (status != TableLobbyStatus.WAITING) throw new IllegalStateException("卡桌当前不在大厅状态"); }
    private void requireHost(UUID player) { if (!host.equals(player)) throw new IllegalStateException("只有房主可修改规则包"); }
    private void requireSeat(UUID player) { if (!player.equals(host) && !player.equals(guest)) throw new IllegalStateException("你不是本桌对局玩家"); }
}
