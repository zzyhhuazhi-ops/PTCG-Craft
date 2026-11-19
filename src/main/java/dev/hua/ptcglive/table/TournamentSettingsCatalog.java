package dev.hua.ptcglive.table;

import dev.hua.ptcglive.engine.tournament.TiebreakerPolicy;
import dev.hua.ptcglive.engine.tournament.TournamentMatchFormat;
import dev.hua.ptcglive.engine.tournament.TournamentSettings;
import java.util.Map;

/** The public table uses one strict, server-resolved game; no referee or multi-game workflow is exposed. */
public final class TournamentSettingsCatalog {
    private static final Map<String, TournamentSettings> SETTINGS = Map.of(
        "system-single", new TournamentSettings("system-single", "系统单局定胜负", TournamentMatchFormat.SINGLE_GAME, 60, false, TiebreakerPolicy.OFFICIAL_FULL_PRIZE)
    );
    private TournamentSettingsCatalog() { }
    public static TournamentSettings require(String id) {
        TournamentSettings setting = SETTINGS.get(id);
        if (setting == null) throw new IllegalArgumentException("未安装的赛事设置: " + id);
        return setting;
    }
    public static Map<String, TournamentSettings> all() { return SETTINGS; }
}
