import dev.hua.ptcglive.engine.MatchSession;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.engine.field.PokemonProfile;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Verifies a deck search choice stays private, is revalidated and is atomically resolved by the server. */
public final class TrainerDeckSearchTest {
    private static final UUID A = new UUID(0, 7151), B = new UUID(0, 7152);
    public static void main(String[] args) {
        ServerMatch match = fixture(); OpeningTestSupport.complete(match, A, B, 0);
        MatchSession session = MatchSession.multiplayer(match, A, B);
        CardInstance search = hand(match, A, "search");
        CardInstance invalid = match.privateView(A).deck().stream().filter(c -> c.definitionId().equals("energy")).findFirst().orElseThrow();
        var before = match.privateView(A);
        rejects(() -> match.playTrainerWithDeckSearch(A, search.instanceId(), invalid.instanceId()));
        if (!before.equals(match.privateView(A))) throw new AssertionError("伪造选择改变了对局状态");
        CardInstance selected = match.privateView(A).deck().stream().filter(c -> c.definitionId().equals("basic")).findFirst().orElseThrow();
        if (session.view(A, A).options().stream().noneMatch(o -> o.labelZh().contains("检索"))) throw new AssertionError("拥有者没有检索操作");
        if (session.view(B, B).options().stream().anyMatch(o -> o.labelZh().contains("检索"))) throw new AssertionError("对手收到私有检索操作");
        int randomBefore = match.randomTranscript().size();
        match.playTrainerWithDeckSearch(A, search.instanceId(), selected.instanceId());
        if (!match.privateView(A).hand().contains(selected) || match.privateView(A).deck().contains(selected) || !match.privateView(A).discard().contains(search))
            throw new AssertionError("基础宝可梦检索移动错误");
        if (match.randomTranscript().size() <= randomBefore || match.clientView(B).hand().contains(selected))
            throw new AssertionError("检索没有服务器洗牌审计或泄露手牌");
        CardInstance anySearch = hand(match, A, "search-card");
        CardInstance energy = match.privateView(A).deck().stream().filter(c -> c.definitionId().equals("energy")).findFirst().orElseThrow();
        match.playTrainerWithDeckSearch(A, anySearch.instanceId(), energy.instanceId());
        if (!match.privateView(A).hand().contains(energy) || !match.privateView(A).discard().contains(anySearch))
            throw new AssertionError("任意卡检索没有允许符合卡文的能量卡");
        CardInstance costSearch = hand(match, A, "search-cost");
        List<UUID> costs = match.privateView(A).hand().stream()
            .filter(c -> !c.instanceId().equals(costSearch.instanceId()) && !c.definitionId().equals("search-two")
                && !c.definitionId().equals("search-up-to-two"))
            .limit(2).map(CardInstance::instanceId).toList();
        CardInstance pokemon = match.privateView(A).deck().stream().filter(c -> c.definitionId().equals("basic")).findFirst().orElseThrow();
        match.playTrainerWithDiscardAndDeckSearch(A, costSearch.instanceId(), costs, pokemon.instanceId());
        if (!match.privateView(A).hand().contains(pokemon) || costs.stream().anyMatch(id -> match.privateView(A).hand().stream().anyMatch(c -> c.instanceId().equals(id)))
            || costs.stream().anyMatch(id -> match.privateView(A).discard().stream().noneMatch(c -> c.instanceId().equals(id))) )
            throw new AssertionError("弃置成本后检索没有原子结算");
        multipleAndOptionalSearchesRemainAtomicAndPrivate(match, session);
        System.out.println("TrainerDeckSearchTest passed: owner-private one/multiple/optional searches are server-validated, atomic and shuffled");
    }

    private static void multipleAndOptionalSearchesRemainAtomicAndPrivate(ServerMatch match, MatchSession session) {
        CardInstance exactTwo = hand(match, A, "search-two");
        List<CardInstance> selected = match.privateView(A).deck().stream().filter(c -> c.definitionId().equals("basic")).limit(2).toList();
        var before = match.privateView(A);
        rejects(() -> match.playTrainerWithDeckSearches(A, exactTwo.instanceId(), List.of(selected.getFirst().instanceId(), selected.getFirst().instanceId())));
        if (!before.equals(match.privateView(A))) throw new AssertionError("重复多张检索选择没有原子拒绝");
        var start=session.view(A,A).options().stream().filter(option->option.cardKeys().contains("hand:"+exactTwo.instanceId())).toList();
        if(start.size()!=1||!start.getFirst().labelZh().endsWith("（开始检索）"))
            throw new AssertionError("多张检索应只提供进入服务器选择器的操作，不能预先列出牌库候选");
        int audit = match.randomTranscript().size();
        match.playTrainerWithDeckSearches(A, exactTwo.instanceId(), selected.stream().map(CardInstance::instanceId).toList());
        if (!match.privateView(A).hand().containsAll(selected) || selected.stream().anyMatch(card -> match.privateView(A).deck().contains(card))
            || !match.privateView(A).discard().contains(exactTwo) || match.randomTranscript().size() <= audit)
            throw new AssertionError("恰好两张牌库检索没有作为同一原子操作结算");
        CardInstance optional = hand(match, A, "search-up-to-two");
        audit = match.randomTranscript().size();
        match.playTrainerWithDeckSearches(A, optional.instanceId(), List.of());
        if (!match.privateView(A).discard().contains(optional) || match.randomTranscript().size() <= audit)
            throw new AssertionError("至多两张检索没有允许合法的零张选择并洗牌");
    }
    private static ServerMatch fixture() {
        List<CardDefinition> defs = List.of(def("basic", CardCategory.POKEMON, TrainerCategory.NONE, true, false),
            def("search", CardCategory.TRAINER, TrainerCategory.ITEM, false, false), def("search-card", CardCategory.TRAINER, TrainerCategory.ITEM, false, false), def("search-cost", CardCategory.TRAINER, TrainerCategory.ITEM, false, false),
            def("search-two", CardCategory.TRAINER, TrainerCategory.ITEM, false, false), def("search-up-to-two", CardCategory.TRAINER, TrainerCategory.ITEM, false, false),
            def("energy", CardCategory.ENERGY, TrainerCategory.NONE, false, true));
        CombatCatalog combat = new CombatCatalog(Map.of("basic", new PokemonProfile(70, 0, null, null, 0, null, 0, 1, List.of())), Map.of("energy", EnergyType.GRASS),
            Map.of("search", new TrainerProfile(TrainerCategory.ITEM, new TrainerEffect(TrainerEffect.Opcode.SEARCH_BASIC_POKEMON_TO_HAND, 1, 0)),
                "search-card", new TrainerProfile(TrainerCategory.ITEM, new TrainerEffect(TrainerEffect.Opcode.SEARCH_CARD_TO_HAND, 1, 0)),
                "search-cost", new TrainerProfile(TrainerCategory.ITEM, new TrainerEffect(TrainerEffect.Opcode.SEARCH_POKEMON_TO_HAND, 1, 0, 0, 2)),
                "search-two", new TrainerProfile(TrainerCategory.ITEM, new TrainerEffect(TrainerEffect.Opcode.SEARCH_BASIC_POKEMON_TO_HAND, 2, 0, 0, 0, 2)),
                "search-up-to-two", new TrainerProfile(TrainerCategory.ITEM, new TrainerEffect(TrainerEffect.Opcode.SEARCH_CARD_TO_HAND, 2, 0, 0, 0, 0))));
        for (long seed = 1; seed < 20_000; seed++) {
            ServerMatch match = new ServerMatch(A, B, deck(A, true), deck(B, false), BundledRules.loadRulesets().get("standard-international-2026"), new CardDatabase(defs), combat, new ServerRandom(seed));
            OpeningTestSupport.decideOrder(match, A, B);
            if (!match.needsMulligan(A) && !match.needsMulligan(B) && match.privateView(A).hand().stream().anyMatch(c -> c.definitionId().equals("search"))
                && match.privateView(A).hand().stream().anyMatch(c -> c.definitionId().equals("search-card"))
                && match.privateView(A).hand().stream().anyMatch(c -> c.definitionId().equals("search-cost"))
                && match.privateView(A).hand().stream().anyMatch(c -> c.definitionId().equals("search-two"))
                && match.privateView(A).hand().stream().anyMatch(c -> c.definitionId().equals("search-up-to-two"))
                && match.privateView(A).deck().stream().anyMatch(c -> c.definitionId().equals("basic"))) return match;
        }
        throw new AssertionError("未找到测试开局");
    }
    private static List<CardInstance> deck(UUID owner, boolean search) {
        List<CardInstance> result = new ArrayList<>();
        for (int i = 0; i < 60; i++) result.add(new CardInstance(UUID.randomUUID(), owner,
            i < 15 ? "basic" : search && i < 23 ? "search" : search && i < 31 ? "search-card" : search && i < 39 ? "search-cost"
                : search && i < 47 ? "search-two" : search && i < 55 ? "search-up-to-two" : "energy"));
        return result;
    }
    private static CardDefinition def(String id, CardCategory category, TrainerCategory trainer, boolean basic, boolean basicEnergy) {
        return new CardDefinition(id, id, category, trainer, "H", Set.of("international"), basic, basicEnergy, 60, Map.of(), EffectSupport.SUPPORTED, null);
    }
    private static CardInstance hand(ServerMatch m, UUID p, String id) { return m.privateView(p).hand().stream().filter(c -> c.definitionId().equals(id)).findFirst().orElseThrow(); }
    private static void rejects(Runnable action) { try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; } throw new AssertionError("非法检索选择被接受"); }
}
