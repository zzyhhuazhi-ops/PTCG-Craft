package dev.hua.ptcglive.rules;

import java.util.List;

public record DeckValidationReport(String rulesetId, List<DeckIssue> issues, boolean casualOnly) {
    public DeckValidationReport(String rulesetId, List<DeckIssue> issues) { this(rulesetId, issues, false); }
    public DeckValidationReport { issues = List.copyOf(issues); }
    public boolean canStartMatch() { return issues.stream().noneMatch(i -> i.severity() == DeckIssue.Severity.ERROR); }
    public boolean canStartOfficialMatch() { return canStartMatch() && !casualOnly; }
    public String statusZh() {
        if (canStartMatch()) return casualOnly ? "仅休闲可用" : "可用";
        return issues.stream().anyMatch(i -> i.code().contains("数据") || i.code().contains("未支持") || i.code().contains("待核验"))
            ? "需更新卡牌／规则数据" : "不可用";
    }
}
