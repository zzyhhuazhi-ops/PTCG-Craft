import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.effect.EffectCardSelectionSpec;
import dev.hua.ptcglive.engine.effect.EffectDefinition;
import dev.hua.ptcglive.engine.effect.EffectTarget;
import dev.hua.ptcglive.engine.effect.PokemonFamilyFilter;
import dev.hua.ptcglive.engine.effect.RuleBoxFilter;
import dev.hua.ptcglive.engine.effect.ZoneSelectionEffect;
import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.rules.CardCategory;
import dev.hua.ptcglive.rules.CardDefinition;
import dev.hua.ptcglive.rules.CardInstance;
import dev.hua.ptcglive.rules.EffectSupport;
import dev.hua.ptcglive.rules.PokemonFamily;
import dev.hua.ptcglive.rules.TrainerCategory;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reviewed deck-search criteria constrain hidden selections before any card leaves the deck. */
public final class DeckSearchPolicyTest {
    public static void main(String[] args) {
        ServerMatch match = PersistentTrainerTest.game();
        explicitRuleBoxFilters();
        CardInstance basic = deck(match, "basic");
        CardInstance energy = deck(match, "energy");
        var basicOnly = new ServerMatch.DeckSearchPolicy(1, 1, CardCategory.POKEMON, null, true, Set.of());
        match.searchOwnDeckToHandByEffect(PersistentTrainerTest.A, basicOnly, List.of(basic.instanceId()));
        if (!match.privateView(PersistentTrainerTest.A).hand().contains(basic))
            throw new AssertionError("受限检索没有移动符合条件的基础宝可梦");

        var before = match.privateView(PersistentTrainerTest.A);
        int replaySize = match.replayTranscript().size();
        rejects(() -> match.searchOwnDeckToHandByEffect(PersistentTrainerTest.A, basicOnly, List.of(energy.instanceId())));
        if (!before.equals(match.privateView(PersistentTrainerTest.A)) || replaySize != match.replayTranscript().size())
            throw new AssertionError("不符合检索条件的隐藏卡改变了状态或回放");

        var namedEnergy = new ServerMatch.DeckSearchPolicy(0, 1, null, null, false, Set.of("energy"));
        match.searchOwnDeckToHandByEffect(PersistentTrainerTest.A, namedEnergy, List.of(energy.instanceId()));
        if (!match.privateView(PersistentTrainerTest.A).hand().contains(energy))
            throw new AssertionError("指定编号检索没有接受匹配的能量");
        rejects(() -> new ServerMatch.DeckSearchPolicy(2, 1, null, null, false, Set.of()));
        rejects(() -> new ServerMatch.DeckSearchPolicy(0, 1, CardCategory.ENERGY, null, true, Set.of()));
        System.out.println("DeckSearchPolicyTest passed: card type, Basic, Rule Box and exact-definition deck search policies are server-enforced");
    }
    private static void explicitRuleBoxFilters() {
        CardDefinition ruleBox = pokemon("rule-box", Set.of(PokemonFamily.V, PokemonFamily.RULE_BOX));
        CardDefinition vWithoutPrintedBox = pokemon("plain-v", Set.of(PokemonFamily.V));
        CardDefinition ex = pokemon("plain-ex", Set.of(PokemonFamily.EX));
        CardDefinition water = pokemon("water", Set.of(), Set.of(EnergyType.WATER));
        CardDefinition grass = pokemon("grass", Set.of(), Set.of(EnergyType.GRASS));
        var hasBox = new ServerMatch.DeckSearchPolicy(0, 1, CardCategory.POKEMON, null, false, RuleBoxFilter.HAS, Set.of());
        var noBox = new ServerMatch.CardSelectionPolicy(0, 1, CardCategory.POKEMON, null, false, RuleBoxFilter.NONE, Set.of());
        if (!hasBox.allows(ruleBox) || hasBox.allows(vWithoutPrintedBox))
            throw new AssertionError("牌库 Rule Box 筛选没有只接受审核标记，或错误根据 V 自动推断");
        if (noBox.allows(ruleBox) || !noBox.allows(vWithoutPrintedBox))
            throw new AssertionError("手牌／弃牌区 Rule Box 排除筛选没有复用同一审核标记");
        var effect = new EffectDefinition("search_deck", EffectTarget.DECK,
            Map.of("amount", "1", "minimum", "0", "card_category", "POKEMON", "rule_box", "NONE"), List.of(), List.of());
        ZoneSelectionEffect selection = ZoneSelectionEffect.from(effect);
        if (selection.selection().ruleBoxFilter() != RuleBoxFilter.NONE || selection.allows(ruleBox) || !selection.allows(vWithoutPrintedBox))
            throw new AssertionError("规则数据 rule_box: NONE 没有转换为统一私有区域筛选");
        var onlyV = new ServerMatch.DeckSearchPolicy(0, 1, CardCategory.POKEMON, null, false, RuleBoxFilter.ANY,
            new PokemonFamilyFilter(Set.of(PokemonFamily.V), PokemonFamilyFilter.Match.ALL), Set.of());
        var notEx = new ServerMatch.CardSelectionPolicy(0, 1, CardCategory.POKEMON, null, false, RuleBoxFilter.ANY,
            new PokemonFamilyFilter(Set.of(PokemonFamily.EX), PokemonFamilyFilter.Match.NONE), Set.of());
        if (!onlyV.allows(ruleBox) || !onlyV.allows(vWithoutPrintedBox) || onlyV.allows(ex))
            throw new AssertionError("宝可梦家族筛选没有按审核 V 标记限制牌库候选");
        if (notEx.allows(ex) || !notEx.allows(vWithoutPrintedBox))
            throw new AssertionError("宝可梦家族 NONE 筛选没有排除已审核 EX 标记");
        var allFamilies = ZoneSelectionEffect.from(new EffectDefinition("search_deck", EffectTarget.DECK,
            Map.of("amount", "1", "minimum", "0", "card_category", "POKEMON", "pokemon_families", "V|RULE_BOX", "family_match", "ALL"),
            List.of(), List.of()));
        if (!allFamilies.allows(ruleBox) || allFamilies.allows(vWithoutPrintedBox) || allFamilies.allows(ex))
            throw new AssertionError("pokemon_families + family_match 没有组合为同一服务器筛选");
        var waterOnly = new ServerMatch.DeckSearchPolicy(0, 1, CardCategory.POKEMON, null, false, RuleBoxFilter.ANY,
            PokemonFamilyFilter.ANY, new dev.hua.ptcglive.engine.effect.PokemonTypeFilter(Set.of(EnergyType.WATER)), Set.of());
        if (!waterOnly.allows(water) || waterOnly.allows(grass) || waterOnly.allows(vWithoutPrintedBox))
            throw new AssertionError("宝可梦属性筛选没有只读取审核的私有区域属性元数据");
        var typed = ZoneSelectionEffect.from(new EffectDefinition("search_deck", EffectTarget.DECK,
            Map.of("amount", "1", "minimum", "0", "card_category", "POKEMON", "pokemon_types", "WATER"), List.of(), List.of()));
        if (!typed.allows(water) || typed.allows(grass) || typed.allows(vWithoutPrintedBox))
            throw new AssertionError("pokemon_types 没有转换为统一服务器候选筛选");
        rejects(() -> new EffectCardSelectionSpec(0, 1, CardCategory.ENERGY, null, false, RuleBoxFilter.HAS, Set.of()));
        rejects(() -> new ServerMatch.DeckSearchPolicy(0, 1, CardCategory.ENERGY, null, false, RuleBoxFilter.HAS, Set.of()));
        rejects(() -> new EffectCardSelectionSpec(0, 1, CardCategory.ENERGY, null, false, RuleBoxFilter.ANY,
            new PokemonFamilyFilter(Set.of(PokemonFamily.V), PokemonFamilyFilter.Match.ANY), Set.of()));
        rejects(() -> new EffectCardSelectionSpec(0, 1, CardCategory.ENERGY, null, false, RuleBoxFilter.ANY,
            PokemonFamilyFilter.ANY, new dev.hua.ptcglive.engine.effect.PokemonTypeFilter(Set.of(EnergyType.WATER)), Set.of()));
        rejects(() -> EffectCardSelectionSpec.from(new EffectDefinition("search_deck", EffectTarget.DECK,
            Map.of("amount", "1", "minimum", "0", "card_category", "POKEMON", "pokemon_families", "NO_SUCH_FAMILY"), List.of(), List.of())));
        rejects(() -> EffectCardSelectionSpec.from(new EffectDefinition("search_deck", EffectTarget.DECK,
            Map.of("amount", "1", "minimum", "0", "card_category", "POKEMON", "family_match", "NONE"), List.of(), List.of())));
        rejects(() -> EffectCardSelectionSpec.from(new EffectDefinition("search_deck", EffectTarget.DECK,
            Map.of("amount", "1", "minimum", "0", "card_category", "POKEMON", "pokemon_types", "MYSTERY"), List.of(), List.of())));
        rejects(() -> new EffectCardSelectionSpec(0, 1, CardCategory.POKEMON, null, false, null, Set.of()));
    }
    private static CardDefinition pokemon(String id, Set<PokemonFamily> families) {
        return pokemon(id, families, Set.of());
    }
    private static CardDefinition pokemon(String id, Set<PokemonFamily> families, Set<EnergyType> types) {
        return new CardDefinition(id, id, CardCategory.POKEMON, TrainerCategory.NONE, "H", Set.of("international"), true,
            false, 4, Map.of(), EffectSupport.SUPPORTED, null, families, types);
    }
    private static CardInstance deck(ServerMatch match, String definitionId) {
        return match.privateView(PersistentTrainerTest.A).deck().stream().filter(card -> card.definitionId().equals(definitionId)).findFirst()
            .orElseThrow(() -> new AssertionError("测试夹具牌库缺少 " + definitionId));
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException | NullPointerException expected) { return; }
        throw new AssertionError("服务器错误接受了无效牌库检索策略或选择");
    }
}
