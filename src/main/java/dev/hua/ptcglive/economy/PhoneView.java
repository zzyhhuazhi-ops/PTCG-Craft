package dev.hua.ptcglive.economy;

import java.util.List;
import java.util.Map;

public record PhoneView(String sessionId, long balance, String tab, String search, String era, int page, int pages,
                        List<PackRow> packs, List<MineralRow> minerals, List<HistoryRow> history,
                        Map<String, Integer> cart, List<String> favorites, List<String> eras) {
    public record PackRow(String setId, String nameZh, String era, long price, boolean favorite, int cartQuantity) { }
    public record MineralRow(String itemId, String nameZh, int available, long unitValue) { }
    public record HistoryRow(String id, String kind, long delta, long balanceAfter, String detail, String recordedAt) { }
}
