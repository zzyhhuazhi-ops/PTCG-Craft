package dev.hua.ptcglive.table;

public enum TableLobbyStatus {
    WAITING("等待准备"), IN_MATCH("对局中"), BETWEEN_GAMES("等待赛事下一局／裁判确认"), ENDED("已结束");
    private final String labelZh;
    TableLobbyStatus(String labelZh) { this.labelZh = labelZh; }
    public String labelZh() { return labelZh; }
}
