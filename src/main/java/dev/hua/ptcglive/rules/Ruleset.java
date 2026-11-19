package dev.hua.ptcglive.rules;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable international/regional rules package selected when a table creates a match. */
public record Ruleset(
    String id, String version, String displayNameZh, String region, LocalDate effectiveDate,
    Set<String> legalRegulationMarks, Set<String> bannedCardIds,
    Map<String, Integer> deckTagLimits, List<RuleSource> sources, List<String> changeLogZh,
    Set<String> legalBasicEnergyIds, SetupProcedure setupProcedure, MatchSetupRules matchSetupRules, FirstTurnRules firstTurnRules, TurnActionRules turnActionRules,
    RulesetEdition edition, CardPoolPolicy cardPool, TrainerClassificationRules trainerClassification, TriggerTiming triggerTiming
) {
    public Ruleset(String id, String version, String displayNameZh, String region, LocalDate effectiveDate,
                   Set<String> legalRegulationMarks, Set<String> bannedCardIds, Map<String, Integer> deckTagLimits,
                   List<RuleSource> sources, List<String> changeLogZh, Set<String> legalBasicEnergyIds, SetupProcedure setupProcedure,
                   MatchSetupRules matchSetupRules, FirstTurnRules firstTurnRules, TurnActionRules turnActionRules,
                   RulesetEdition edition, CardPoolPolicy cardPool, TrainerClassificationRules trainerClassification) {
        this(id, version, displayNameZh, region, effectiveDate, legalRegulationMarks, bannedCardIds, deckTagLimits, sources, changeLogZh,
            legalBasicEnergyIds, setupProcedure, matchSetupRules, firstTurnRules, turnActionRules, edition, cardPool, trainerClassification, TriggerTiming.UNVERIFIED);
    }
    public Ruleset(String id, String version, String displayNameZh, String region, LocalDate effectiveDate,
                   Set<String> legalRegulationMarks, Set<String> bannedCardIds, Map<String, Integer> deckTagLimits,
                   List<RuleSource> sources, List<String> changeLogZh, Set<String> legalBasicEnergyIds, SetupProcedure setupProcedure,
                   MatchSetupRules matchSetupRules, FirstTurnRules firstTurnRules, TurnActionRules turnActionRules,
                   RulesetEdition edition, CardPoolPolicy cardPool) {
        this(id, version, displayNameZh, region, effectiveDate, legalRegulationMarks, bannedCardIds, deckTagLimits, sources, changeLogZh,
            legalBasicEnergyIds, setupProcedure, matchSetupRules, firstTurnRules, turnActionRules, edition, cardPool, TrainerClassificationRules.MODERN);
    }
    /** Existing saved packages and engine fixtures retain their explicit legacy semantics. */
    public Ruleset(String id, String version, String displayNameZh, String region, LocalDate effectiveDate,
                   Set<String> legalRegulationMarks, Set<String> bannedCardIds, Map<String, Integer> deckTagLimits,
                   List<RuleSource> sources, List<String> changeLogZh, Set<String> legalBasicEnergyIds, SetupProcedure setupProcedure,
                   MatchSetupRules matchSetupRules, FirstTurnRules firstTurnRules, TurnActionRules turnActionRules) {
        this(id, version, displayNameZh, region, effectiveDate, legalRegulationMarks, bannedCardIds, deckTagLimits, sources, changeLogZh,
            legalBasicEnergyIds, setupProcedure, matchSetupRules, firstTurnRules, turnActionRules, RulesetEdition.LEGACY, CardPoolPolicy.LEGACY);
    }
    public Ruleset(String id, String version, String displayNameZh, String region, LocalDate effectiveDate,
                   Set<String> legalRegulationMarks, Set<String> bannedCardIds, Map<String, Integer> deckTagLimits,
                   List<RuleSource> sources, List<String> changeLogZh) {
        this(id, version, displayNameZh, region, effectiveDate, legalRegulationMarks, bannedCardIds, deckTagLimits, sources, changeLogZh, Set.of(), SetupProcedure.INTERNATIONAL_V1, MatchSetupRules.INTERNATIONAL_STANDARD, FirstTurnRules.INTERNATIONAL_STANDARD, TurnActionRules.INTERNATIONAL_STANDARD);
    }
    public Ruleset(String id, String version, String displayNameZh, String region, LocalDate effectiveDate,
                   Set<String> legalRegulationMarks, Set<String> bannedCardIds, Map<String, Integer> deckTagLimits,
                   List<RuleSource> sources, List<String> changeLogZh, Set<String> legalBasicEnergyIds) {
        this(id, version, displayNameZh, region, effectiveDate, legalRegulationMarks, bannedCardIds, deckTagLimits, sources, changeLogZh, legalBasicEnergyIds, SetupProcedure.INTERNATIONAL_V1, MatchSetupRules.INTERNATIONAL_STANDARD, FirstTurnRules.INTERNATIONAL_STANDARD, TurnActionRules.INTERNATIONAL_STANDARD);
    }
    public Ruleset(String id, String version, String displayNameZh, String region, LocalDate effectiveDate,
                   Set<String> legalRegulationMarks, Set<String> bannedCardIds, Map<String, Integer> deckTagLimits,
                   List<RuleSource> sources, List<String> changeLogZh, Set<String> legalBasicEnergyIds, SetupProcedure setupProcedure) {
        this(id, version, displayNameZh, region, effectiveDate, legalRegulationMarks, bannedCardIds, deckTagLimits, sources, changeLogZh,
            legalBasicEnergyIds, setupProcedure, MatchSetupRules.INTERNATIONAL_STANDARD, FirstTurnRules.INTERNATIONAL_STANDARD, TurnActionRules.INTERNATIONAL_STANDARD);
    }
    public Ruleset {
        if (id == null || !id.matches("[a-z0-9-]+")) throw new IllegalArgumentException("规则包 ID 无效");
        if (version == null || version.isBlank() || displayNameZh == null || displayNameZh.isBlank())
            throw new IllegalArgumentException("规则包版本或名称为空");
        Objects.requireNonNull(effectiveDate, "生效日期");
        if (region == null || region.isBlank()) throw new IllegalArgumentException("规则地区不能为空");
        if (edition == null) edition = RulesetEdition.LEGACY;
        if (cardPool == null) cardPool = CardPoolPolicy.LEGACY;
        if (trainerClassification == null) trainerClassification = TrainerClassificationRules.MODERN;
        if (triggerTiming == null) triggerTiming = TriggerTiming.UNVERIFIED;
        if (edition.effectiveUntil() != null && edition.effectiveUntil().isBefore(effectiveDate))
            throw new IllegalArgumentException("规则失效日期不能早于生效日期");
        if (cardPool.asOfDate() != null && (cardPool.asOfDate().isBefore(effectiveDate)
            || edition.effectiveUntil() != null && cardPool.asOfDate().isAfter(edition.effectiveUntil())))
            throw new IllegalArgumentException("卡池快照日期必须在规则生效区间内");
        if (cardPool.mode() == CardPoolPolicy.Mode.ALL_CARDS && !edition.casualOnly())
            throw new IllegalArgumentException("跨年代地区的全卡池只能标记为自定义休闲规则");
        if (cardPool.mode() == CardPoolPolicy.Mode.REGIONAL_PRACTICE && !edition.casualOnly())
            throw new IllegalArgumentException("跨地区对应卡仅能在明确标记为休闲的练习规则包中使用");
        Objects.requireNonNull(setupProcedure, "开局流程版本");
        if (matchSetupRules == null) matchSetupRules = MatchSetupRules.INTERNATIONAL_STANDARD;
        if (firstTurnRules == null) firstTurnRules = FirstTurnRules.INTERNATIONAL_STANDARD;
        if (turnActionRules == null) turnActionRules = TurnActionRules.INTERNATIONAL_STANDARD;
        legalRegulationMarks = Set.copyOf(legalRegulationMarks);
        bannedCardIds = Set.copyOf(bannedCardIds);
        deckTagLimits = Map.copyOf(deckTagLimits);
        sources = List.copyOf(sources);
        changeLogZh = List.copyOf(changeLogZh);
        legalBasicEnergyIds = Set.copyOf(legalBasicEnergyIds);
    }

    public boolean allows(CardDefinition card) {
        return cardPool.assess(this, card).status() == CardPoolPolicy.Status.LEGAL;
    }
    public boolean coversDate(LocalDate date) {
        return !date.isBefore(effectiveDate) && (edition.effectiveUntil() == null || !date.isAfter(edition.effectiveUntil()));
    }
    public String selectionLabelZh() {
        return displayNameZh + (!edition.matchEnabled() ? "【资料待核验，不能开局】" : edition.casualOnly() ? "【休闲】" : "");
    }
    /** Public configuration only: never includes a player's deck or other hidden information. */
    public String policySummaryZh() {
        var lines = new java.util.ArrayList<String>();
        lines.add("构筑：正好 " + matchSetupRules.deckSize() + " 张，至少 1 张基础宝可梦；同名卡跨编号／稀有度合计最多 4 张，基本能量不限。");
        lines.add("特殊限制：同名棱镜之星最多 1 张；卡牌明确写明的数量限制优先。");
        deckTagLimits.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
            lines.add(entry.getKey() + "：整副卡组合计最多 " + entry.getValue() + " 张。"));
        lines.add("开局：手牌 " + matchSetupRules.openingHandSize() + " 张，奖赏 " + matchSetupRules.prizeCards()
            + " 张，备战位 " + matchSetupRules.benchLimit() + " 个；无基础宝可梦重新洗牌抽牌。");
        lines.add("先手首回合：" + (firstTurnRules.firstPlayerMayAttack() ? "可攻击" : "不能攻击") + "、"
            + (firstTurnRules.firstPlayerMayPlaySupporter() ? "可用支援者" : "不能用支援者") + "；双方各自首回合及刚出场的宝可梦不能通常进化，卡文另有规定除外。");
        lines.add("每回合通常限次：手动附能 " + turnActionRules.energyAttachmentsFromHand() + "，撤退 " + turnActionRules.retreats()
            + "，支援者 " + turnActionRules.supporters() + "，场地 " + turnActionRules.stadiums() + "；卡文效果另计。");
        lines.add("胜负：取完奖赏、对手场上无宝可梦，或对手回合开始无法抽牌；具体结算由服务端执行。");
        lines.add("卡池合法不等于效果已实现；缺失的战斗规则仍会明确列出并阻止开局。");
        lines.add("日期：" + effectiveDate + " ～ " + (edition.effectiveUntil() == null ? "后续公告更新前" : edition.effectiveUntil()));
        if (!legalRegulationMarks.isEmpty()) lines.add("规则标记：" + String.join(" / ", legalRegulationMarks.stream().sorted().toList()));
        if (cardPool.mode() == CardPoolPolicy.Mode.REGULATION_MARKS_ANY_REGION)
            lines.add("按卡左下角字母判定，不按卡图语言或原始地区 ID 限制；未知字母不推测。旧版再版仅放行已核对编号与效果的对应卡。");
        if (cardPool.mode() == CardPoolPolicy.Mode.REVIEWED_CARD_IDS)
            lines.add("限定卡池：" + cardPool.legalCardIds().size() + " 个已有卡牌编号；允许在该卡池内自由混编。");
        lines.add(trainerClassification.pokemonToolsAreItems() ? "道具仍属于物品类别" : "道具独立于物品类别");
        if (!cardPool.basicEnergyTypes().isEmpty()) lines.add("基本能量：" + cardPool.basicEnergyTypes().size() + " 类"
            + (cardPool.basicEnergyTypes().contains(dev.hua.ptcglive.engine.field.EnergyType.FAIRY) ? "（含妖精）" : "（不含妖精）"));
        if (cardPool.asOfDate() != null) lines.add("卡池快照：" + cardPool.asOfDate() + "；印刷合法日期缺失时不放行");
        if (cardPool.mode() == CardPoolPolicy.Mode.REGIONAL_PRACTICE) lines.add("练习对应表：" + cardPool.practiceMappings().size() + " 条；不代表官方赛事资格");
        if (!edition.noticeZh().isBlank()) lines.add(edition.noticeZh());
        return String.join("\n", lines);
    }
}
