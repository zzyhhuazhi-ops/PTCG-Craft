package dev.hua.ptcglive.rules;

import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import dev.hua.ptcglive.engine.field.EnergyType;

/** Explicit pool semantics; a historical pool never assumes that every printing with the same mark already existed. */
public record CardPoolPolicy(Mode mode, Set<String> legalCardIds, LocalDate asOfDate, Map<String, LocalDate> legalFromDates,
                             Set<EnergyType> basicEnergyTypes, Map<String, PracticeCardMapping> practiceMappings) {
    public enum Mode { REGULATION_MARKS, REGULATION_MARKS_ANY_REGION, REVIEWED_CARD_IDS, ALL_CARDS, REGIONAL_PRACTICE }
    public enum Status { LEGAL, ILLEGAL, NEEDS_DATA }
    public record Decision(Status status, String reasonZh) { }
    public static final CardPoolPolicy LEGACY = new CardPoolPolicy(Mode.REGULATION_MARKS, Set.of(), null, Map.of());
    public CardPoolPolicy(Mode mode, Set<String> legalCardIds, LocalDate asOfDate, Map<String, LocalDate> legalFromDates) {
        this(mode, legalCardIds, asOfDate, legalFromDates, Set.of());
    }
    public CardPoolPolicy(Mode mode, Set<String> legalCardIds, LocalDate asOfDate, Map<String, LocalDate> legalFromDates,
                          Set<EnergyType> basicEnergyTypes) {
        this(mode, legalCardIds, asOfDate, legalFromDates, basicEnergyTypes, Map.of());
    }
    public CardPoolPolicy {
        Objects.requireNonNull(mode, "卡池模式");
        legalCardIds = Set.copyOf(legalCardIds);
        legalFromDates = Map.copyOf(legalFromDates);
        basicEnergyTypes = Set.copyOf(basicEnergyTypes);
        practiceMappings = Map.copyOf(practiceMappings);
        if (mode == Mode.REGIONAL_PRACTICE) {
            if (asOfDate == null || !legalCardIds.isEmpty() || !legalFromDates.isEmpty())
                throw new IllegalArgumentException("地区练习须指定快照日期，仅使用明确对应表，不能夹带其他白名单");
        } else if (!practiceMappings.isEmpty()) throw new IllegalArgumentException("只有地区练习模式可包含代理卡对应表");
        if (practiceMappings.keySet().stream().anyMatch(id -> !id.matches("[a-z0-9_-]+")))
            throw new IllegalArgumentException("练习对应的内部卡牌编号无效");
        if (basicEnergyTypes.contains(EnergyType.DRAGON) || basicEnergyTypes.contains(EnergyType.COLORLESS))
            throw new IllegalArgumentException("基本能量卡白名单不能包含龙或无色能量");
        if (legalCardIds.stream().anyMatch(String::isBlank) || legalFromDates.keySet().stream().anyMatch(String::isBlank))
            throw new IllegalArgumentException("卡池中的卡牌编号不能为空");
        if (mode == Mode.ALL_CARDS && (!legalCardIds.isEmpty() || asOfDate != null || !legalFromDates.isEmpty() || !basicEnergyTypes.isEmpty()))
            throw new IllegalArgumentException("全卡池不能同时声明年代截止或卡牌白名单");
        if (asOfDate == null && !legalFromDates.isEmpty())
            throw new IllegalArgumentException("卡牌合法日期表必须配有卡池快照日期");
    }
    public Decision assess(Ruleset ruleset, CardDefinition card) {
        return assess(ruleset, card, null);
    }
    /** The type comes from the server combat catalogue, never a translated name or client-provided symbol. */
    public Decision assess(Ruleset ruleset, CardDefinition card, EnergyType basicEnergyType) {
        if (ruleset.bannedCardIds().contains(card.id())) return illegal("卡牌在此规则快照的禁用清单中");
        if (mode == Mode.ALL_CARDS) return legal();
        if (mode == Mode.REGIONAL_PRACTICE) {
            if (!ruleset.edition().casualOnly()) return illegal("地区代理卡仅允许休闲练习");
            var mapping = practiceMappings.get(card.id());
            if (mapping == null) return new Decision(Status.NEEDS_DATA, "尚无此卡在目标地区的已核对对应记录，练习模式也不默认放行全部英文卡");
            if (!mapping.targetRegion().equals(ruleset.region()) || !card.regions().contains(mapping.sourceRegion())
                || mapping.category() != card.category()) return illegal("练习对应的地区或卡牌类别不符");
            if (mapping.legalFrom().isAfter(asOfDate)) return illegal("对应卡牌在所选地区快照之后才可使用");
            if (ruleset.bannedCardIds().contains(mapping.targetPrintingId())) return illegal("目标地区对应卡牌已禁用");
            if (mapping.basicEnergyType() != null) {
                if (!card.basicEnergy()) return illegal("不能以基本能量的对应关系放行特殊能量");
                if (basicEnergyType == null) return new Decision(Status.NEEDS_DATA, "缺少基本能量属性核验数据");
                if (basicEnergyType != mapping.basicEnergyType() || !basicEnergyTypes.contains(basicEnergyType))
                    return illegal("基本能量属性不符或在目标地区已退出");
            } else if (card.basicEnergy() || !ruleset.legalRegulationMarks().contains(mapping.regulationMark()))
                return illegal("对应卡牌的目标地区规则标记不在当前卡池中");
            return legal();
        }
        if (mode != Mode.REGULATION_MARKS_ANY_REGION && !card.regions().contains(ruleset.region()))
            return illegal("卡牌印刷地区与此规则包不符");
        boolean allowedBasic = false;
        if (card.basicEnergy() && !basicEnergyTypes.isEmpty()) {
            if (basicEnergyType == null) return new Decision(Status.NEEDS_DATA, "缺少已审核的基本能量属性资料");
            if (!basicEnergyTypes.contains(basicEnergyType)) return illegal("此日期的地区规则不允许该种基本能量");
            allowedBasic = true;
        }
        boolean included = legalCardIds.contains(card.id())
            || allowedBasic
            || card.basicEnergy() && ruleset.legalBasicEnergyIds().contains(card.id())
            || (mode == Mode.REGULATION_MARKS || mode == Mode.REGULATION_MARKS_ANY_REGION)
                && ruleset.legalRegulationMarks().contains(card.regulationMark());
        if (!included) {
            if ((mode == Mode.REGULATION_MARKS || mode == Mode.REGULATION_MARKS_ANY_REGION)
                    && !card.regulationMark().matches("[A-J]"))
                return illegal("没有已核对的赛制字母，且不属于已核对再版或允许的基本能量例外");
            return illegal("卡牌标记 " + card.regulationMark() + " 不在此规则快照的标记／再版／卡牌白名单中");
        }
        if (asOfDate != null) {
            LocalDate legalFrom = legalFromDates.get(card.id());
            if (legalFrom == null) return new Decision(Status.NEEDS_DATA, "尚未核验此印刷版本在该地区的赛事合法日期，不能仅凭规则标记放行历史卡池");
            if (legalFrom.isAfter(asOfDate)) return illegal("此印刷版本在卡池快照日期之后才可使用");
        }
        return legal();
    }
    private static Decision legal() { return new Decision(Status.LEGAL, "符合所选卡池；仍须检查构筑及效果支持"); }
    private static Decision illegal(String reason) { return new Decision(Status.ILLEGAL, reason); }
}
