package dev.hua.ptcglive.rules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The same server validator is used by the card-box UI, card table, and match creation. */
public final class DeckValidator {
    private final CardDatabase cards;
    private final CombatCatalog combat;
    public DeckValidator(CardDatabase cards) { this(cards, CombatCatalog.empty()); }
    public DeckValidator(CardDatabase cards, CombatCatalog combat) {
        this.cards = java.util.Objects.requireNonNull(cards);
        this.combat = java.util.Objects.requireNonNull(combat);
    }

    public DeckValidationReport validate(Ruleset ruleset, List<CardInstance> deck) {
        List<DeckIssue> issues = new ArrayList<>();
        if (!ruleset.edition().matchEnabled()) issues.add(error("规则数据待核验", ruleset.edition().noticeZh()));
        if (ruleset.edition().casualOnly()) issues.add(new DeckIssue(DeckIssue.Severity.WARNING, "自定义休闲规则",
            "此规则允许休闲开局，不代表满足官方赛事资格。"));
        if (deck.size() != ruleset.matchSetupRules().deckSize()) issues.add(error("牌组张数", "规则包要求正好 "
            + ruleset.matchSetupRules().deckSize() + " 张卡；当前为 " + deck.size() + " 张。"));
        Map<String, Integer> tags = new HashMap<>();
        java.util.Set<String> combatReported = new java.util.HashSet<>();
        java.util.Set<java.util.UUID> instances = new java.util.HashSet<>();
        Map<String, Integer> namedCopies = new HashMap<>(), namedLimits = new HashMap<>();
        Map<String, Integer> prismCopies = new HashMap<>();
        int basicPokemon = 0;
        boolean typedEnergyInDeck=deck.stream().anyMatch(card->combat.energyProfile(card.definitionId())
            .filter(profile->!profile.attachmentRule().pokemonTypes().isEmpty()).isPresent());
        for (CardInstance instance : deck) {
            if (!instances.add(instance.instanceId())) issues.add(error("卡牌实例重复", "同一张实际卡牌不能在卡组中重复使用。"));
            CardDefinition card = cards.find(instance.definitionId()).orElse(null);
            if (card == null) { issues.add(error("卡牌数据缺失", "卡牌 “" + instance.definitionId() + "”："
                + PrintedRegulationMarks.labelZh(instance.definitionId()) + "；缺少战斗规则数据，不能用于对战。")); continue; }
            boolean prism = card.pokemonFamilies().contains(PokemonFamily.PRISM_STAR) || card.specialDeckLimits().containsKey("棱镜之星");
            String deckName = card.nameZh() + (prism ? "◇" : "");
            if (!card.basicEnergy()) {
                namedCopies.merge(deckName, 1, Integer::sum);
                namedLimits.merge(deckName, card.copyLimit(), Math::min);
            }
            if (card.basicPokemon()) basicPokemon++;
            if (prism) prismCopies.merge(card.nameZh(), 1, Integer::sum);
            var pool = ruleset.cardPool().assess(ruleset, card, combat.energyType(card.id()).orElse(null));
            if (pool.status() != CardPoolPolicy.Status.LEGAL) issues.add(error(
                pool.status() == CardPoolPolicy.Status.NEEDS_DATA ? "卡牌合法性数据缺失" : "赛制不合法",
                "“" + card.nameZh() + "”在 “" + ruleset.displayNameZh() + "”：" + pool.reasonZh()));
            if (card.effectSupport() == EffectSupport.UNSUPPORTED)
                issues.add(error("效果未支持", "“" + card.nameZh() + "”暂不支持：" + card.unsupportedReasonZh()));
            validatePlayableFacts(ruleset, card, combatReported, issues, typedEnergyInDeck);
            card.specialDeckLimits().forEach((tag, amount) -> tags.merge(tag, amount, Integer::sum));
        }
        dev.hua.ptcglive.engine.effect.HitPointEnergyDependencies.unsupportedReason(combat,deck)
            .ifPresent(reason->issues.add(error("循环供能依赖未支持",reason)));
        if (basicPokemon == 0) issues.add(error("缺少基础宝可梦", "牌组至少需要 1 张基础宝可梦。"));
        namedCopies.forEach((name, count) -> {
            int limit = namedLimits.get(name);
            if (count > limit) issues.add(error("同名数量超限", "“" + name + "”不同印刷版本合计最多 " + limit + " 张，当前为 " + count + " 张。"));
        });
        prismCopies.forEach((name, count) -> {
            if (count > 1) issues.add(error("棱镜之星数量超限", "同名棱镜之星“" + name + "”最多 1 张，当前为 " + count + " 张。"));
        });
        tags.forEach((tag, actual) -> {
            int limit = ruleset.deckTagLimits().getOrDefault(tag, Integer.MAX_VALUE);
            if (actual > limit) issues.add(error("特殊构筑限制", "“" + tag + "”最多可放入 " + limit + " 张，当前为 " + actual + " 张。"));
        });
        return new DeckValidationReport(ruleset.id(), issues, ruleset.edition().casualOnly());
    }
    /**
     * A card with artwork and collection metadata is not automatically playable.
     * This check is active only when the server has installed a combat snapshot;
     * command-line compatibility callers may deliberately use an empty snapshot.
     */
    private void validatePlayableFacts(Ruleset ruleset, CardDefinition card, java.util.Set<String> reported, List<DeckIssue> issues, boolean typedEnergyInDeck) {
        if (!combat.isConfigured() || !reported.add(card.id())) return;
        if(typedEnergyInDeck)combat.fieldPokemon(card.id()).filter(profile->profile.pokemonType()==null&&card.pokemonTypes().isEmpty())
            .ifPresent(profile->issues.add(error("宝可梦属性资料缺失","“"+card.nameZh()+"”缺少属性资料；当前牌组的特殊能量附着限制需要此资料。")));
        combat.pokemon(card.id()).filter(profile->profile.pokemonType()==null
            &&profile.attacks().stream().anyMatch(dev.hua.ptcglive.engine.effect.CopyAttackProgram::containsCopy))
            .ifPresent(profile->issues.add(error("宝可梦属性资料缺失","“"+card.nameZh()+"”的复制攻击需要明确pokemonType，不能从别的卡借用属性。")));
        combat.pokemon(card.id()).ifPresent(profile -> profile.attacks().forEach(attack ->
            dev.hua.ptcglive.engine.effect.AttackProgramRules.unsupportedReason(attack).ifPresent(reason ->
                issues.add(error("攻击组合未支持", "“" + card.nameZh() + "”的“" + attack.nameZh() + "”：" + reason)))));
        combat.fieldPokemon(card.id()).ifPresent(profile -> profile.abilities().stream()
            .filter(a -> a.activated() && a.composed()).forEach(ability ->
                dev.hua.ptcglive.engine.effect.ActivatedAbilityRules.unsupportedReason(ability).ifPresent(reason ->
                    issues.add(error("主动特性组合未支持", "“" + card.nameZh() + "”的“" + ability.nameZh() + "”：" + reason)))));
        combat.fieldPokemon(card.id()).ifPresent(profile -> profile.abilities().stream().filter(a -> a.triggered()).forEach(ability -> {
            if (ruleset.triggerTiming() == TriggerTiming.UNVERIFIED)
                issues.add(error("触发时序未核验", "“" + card.nameZh() + "”的自动特性尚未核验本地区／版本的触发时序。"));
            dev.hua.ptcglive.engine.effect.TriggeredAbilityRules.unsupportedReason(ability).ifPresent(reason ->
                issues.add(error("自动特性未支持", "“" + card.nameZh() + "”：" + reason)));
        }));
        combat.trainer(card.id()).filter(TrainerProfile::composed).ifPresent(profile ->
            dev.hua.ptcglive.engine.effect.TrainerProgramRules.unsupportedReason(profile.effectProgram()).ifPresent(reason ->
                issues.add(error("训练家组合未支持", "“" + card.nameZh() + "”：" + reason))));
        if (card.category() == CardCategory.POKEMON && combat.pokemon(card.id()).isEmpty()) {
            issues.add(error("战斗数据未支持", "“" + card.nameZh() + "”缺少已审核的 HP、攻击或击倒数据，暂不能用于正式对局。"));
        } else if (card.category() == CardCategory.ENERGY && combat.energyProfile(card.id()).isEmpty()) {
            issues.add(error("战斗数据未支持", "“" + card.nameZh() + "”缺少已审核的能量供给数据，暂不能用于正式对局。"));
        } else if (card.category() == CardCategory.TRAINER && card.effectSupport() == EffectSupport.SUPPORTED
                && (combat.trainer(card.id()).isEmpty() || combat.trainer(card.id()).orElseThrow().category() != card.trainerCategory())) {
            issues.add(error("训练家效果未支持", "“" + card.nameZh() + "”尚未配置可复盘的服务端效果解释器，不能按猜测规则结算。"));
        }
    }
    private static DeckIssue error(String code, String message) { return new DeckIssue(DeckIssue.Severity.ERROR, code, message); }
}
