package dev.hua.ptcglive.view;
import dev.hua.ptcglive.engine.*;
import java.util.Objects;
/** Stable pending-step identity, not packet revision: duplicate refreshes must not ring again. */
public final class BattleAttention {
    private BattleAttention() {}
    public static String key(MatchSession.Snapshot state) {
        var game=state.game();var seat=game.viewingSeat();
        if(seat==null)return "";
        if(game.phase()==MatchPhase.PRIZE_SELECTION&&Objects.equals(game.choicePlayer(),seat))
            return "prize:"+seat+":"+game.turnNumber();
        var reveal=game.effectReveal();
        if(reveal!=null&&Objects.equals(reveal.confirmingPlayer(),seat))return "reveal:"+reveal.id();
        if(game.phase()==MatchPhase.MULLIGAN_REVEAL&&state.options().stream().anyMatch(o->o.enabled()&&o.labelZh().equals("确认无基础宝可梦的重抽展示")))
            return "mulligan:"+seat+":"+game.mulliganReveals();
        return "";
    }
}
