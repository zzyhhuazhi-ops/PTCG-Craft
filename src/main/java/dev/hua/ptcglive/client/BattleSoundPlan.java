package dev.hua.ptcglive.client;

import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.view.MatchPresentation.Cue;
import java.util.Locale;

/** Names of our synthesized assets; never infer a hit from an attack declaration. */
final class BattleSoundPlan {
    private BattleSoundPlan() { }
    static long compensatedOffsetNanos(double offsetMs,long deviceLatencyNanos) {
        long offset=Math.max(0,(long)(offsetMs*1e6));
        long lead=deviceLatencyNanos>=0&&deviceLatencyNanos<=1_000_000_000L?deviceLatencyNanos:0;
        // Never play before the confirmed animation starts, including high-latency devices.
        return offset-Math.min(offset,lead);
    }
    static String event(Cue cue,EnergyType type,boolean winner) {
        return switch(cue.kind()) {
            case DAMAGE -> cue.text().startsWith("招式伤害·")&&type!=null
                ? "hit_"+type.name().toLowerCase(Locale.ROOT) : "counter";
            case COIN -> "coin_spin";
            case DRAW -> "draw";
            case PLAY -> cue.text().equals("能量附着")?"energy":"play";
            case ENERGY, TOOL -> "energy";
            case EVOLVE -> "evolve";
            case DEVOLVE -> "devolve";
            case STADIUM -> "stadium";
            case HEAL -> "heal";
            case STATUS -> cue.text().endsWith("解除")?"heal":switch(cue.text().split("·",2)[0]) {
                case "中毒" -> "poison";case "灼伤" -> "burn";case "睡眠" -> "sleep";
                case "麻痹" -> "paralysis";case "混乱" -> "confusion";default -> "ability";
            };
            case ATTACK -> cue.text().contains("攻击失败")?"fail":"attack";
            case ABILITY -> "ability";
            case MOVE -> "move";
            case LEAVE -> cue.text().startsWith("击倒")?"knockout":cue.text().contains("放逐")?"lost":"discard";
            case KNOCKOUT -> null; // LEAVE already represents each knocked-out stack.
            case LOST -> "lost";
            case DISCARD -> "discard";
            case SHUFFLE -> "shuffle";
            case PRIZE -> "prize";
            case TURN -> "turn";
            case CHECKUP -> "checkup";
            case RESULT -> cue.seat()==null?"turn":winner?"win":"lose";
        };
    }
}
