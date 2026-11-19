package dev.hua.ptcglive.view;

import dev.hua.ptcglive.engine.BattleGuidance;
import dev.hua.ptcglive.engine.MatchSession;
import java.util.*;

/** Display rows retain the issued option, including its index; preview text grants no authority. */
public final class BattleCardActions {
    public record Row(String title, String cost, String damage, String detail, String tooltip, MatchSession.Option option,
                      boolean enabled, boolean attack) { }
    private BattleCardActions() { }
    public static List<Row> rows(MatchSession.Snapshot view, String key, List<MatchSession.Option> allowed,
                                 List<String> hints) {
        var rows = new ArrayList<Row>();
        var previews = view.guidance() == null ? List.<BattleGuidance.AttackPreview>of()
            : view.guidance().attacks().stream().filter(p -> p.sourceKey().equals(key)).toList();
        for (var option : view.options()) {
            if (option.cardKeys().isEmpty() || !option.cardKeys().getFirst().equals(key)) continue;
            boolean attack = MatchActionMatcher.isAttackOption(option), enabled = allowed.contains(option);
            if (!attack && !enabled) continue;
            var preview = attack ? previews.stream().filter(p -> option.labelZh().contains(p.name()))
                .max(Comparator.comparingInt(p -> p.name().length())).orElse(null) : null;
            String title = preview == null ? option.labelZh().replaceFirst("^(攻击：|使用特性：)", "") : preview.name();
            String kind = switch(option.type()) {
                case RETREAT -> "撤退";
                case TOOL_ABILITY -> "道具特性";
                case FOSSIL_DISCARD, DOLL_RETURN -> "卡牌规则";
                default -> "特性";
            };
            String detail = attack ? enabled ? "点击使用招式" : "当前无法使用" : switch(option.type()) {
                case RETREAT -> "点击选择撤退目标";
                case TOOL_ABILITY -> "点击使用附着道具的特性";
                case FOSSIL_DISCARD -> "按卡牌规则自行弃置，不让对手拿取奖赏卡";
                case DOLL_RETURN -> "按卡牌规则收回牌库底，不让对手拿取奖赏卡";
                default -> "点击使用特性";
            };
            if (preview != null) detail = !preview.payable() ? "能量不足" : preview.certainty();
            if (attack && !enabled && view.attackBlockZh() != null && !view.attackBlockZh().isBlank()) detail = view.attackBlockZh();
            String tooltip = option.labelZh() + "\n" + detail + (preview == null ? "" : "\n" + preview.explanation());
            rows.add(new Row(title, preview == null ? attack ? "" : kind : energyNames(preview.cost()),
                preview == null || preview.damage() == null ? "" : Integer.toString(preview.damage()),
                detail, tooltip, option, enabled, attack));
        }
        // With no issued attacks (another turn, status lock), show only public guidance.
        if (rows.isEmpty()) rows.add(new Row("当前无法操作", "", "",
            hints.isEmpty() ? "等待自己的回合或完成当前选择" : hints.getFirst(), String.join("\n",hints), null, false, false));
        return List.copyOf(rows);
    }
    public static String energyNames(String cost) {
        if (cost == null || cost.isBlank()) return "无需能量";
        String result = cost;
        String[][] names = {{"COLORLESS","无"},{"LIGHTNING","雷"},{"PSYCHIC","超"},{"FIGHTING","斗"},
            {"DARKNESS","恶"},{"DRAGON","龙"},{"METAL","钢"},{"GRASS","草"},{"WATER","水"},{"FIRE","火"},{"FAIRY","妖"}};
        for (var name : names) result = result.replace(name[0], name[1]);
        return result.replace(" / ", "  ");
    }
}
