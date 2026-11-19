import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.rules.*;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Historical Item/Tool membership is a match-wide rule, not a rewrite of old printed card data. */
public final class TrainerCategoryEditionTest {
    private static final UUID A = PersistentTrainerTest.A, B = PersistentTrainerTest.B;
    public static void main(String[] args) {
        for (boolean old : List.of(true, false)) {
            Ruleset rule = rule(old);
            var match = PersistentTrainerTest.game(rule);
            var tool = PersistentTrainerTest.hand(match, A, "balloon");
            var itemPolicy = new ServerMatch.CardSelectionPolicy(1, 1, CardCategory.TRAINER, TrainerCategory.ITEM, false, Set.of());
            var before = match.privateView(A);
            if (old) {
                match.discardHandCardsByEffect(A, A, itemPolicy, List.of(tool.instanceId()));
                match.recoverDiscardCardsByEffect(A, A, itemPolicy, List.of(tool.instanceId()), CardEffectDestination.HAND);
                check(new HashSet<>(before.hand()).equals(new HashSet<>(match.privateView(A).hand()))
                    && before.discard().equals(match.privateView(A).discard()) && before.deck().equals(match.privateView(A).deck()),
                    "旧规则的物品筛选没有正确处理工具弃置和回收");
            } else {
                rejects(() -> match.discardHandCardsByEffect(A, A, itemPolicy, List.of(tool.instanceId())));
                check(before.equals(match.privateView(A)), "现代规则拒绝工具为物品时改变了区域");
            }
            for (String type : List.of("discard", "search_discard_pile", "search_deck")) {
                EffectTarget zone = type.equals("discard") ? EffectTarget.HAND : type.equals("search_deck") ? EffectTarget.DECK : EffectTarget.DISCARD;
                // Move to the source via a server-only unfiltered rule so both editions have identical candidates.
                if (zone == EffectTarget.DISCARD && match.privateView(A).hand().contains(tool))
                    match.discardHandCardsByEffect(A, A, List.of(tool.instanceId()));
                if (zone == EffectTarget.DECK) {
                    if (match.privateView(A).hand().contains(tool)) match.discardHandCardsByEffect(A, A, List.of(tool.instanceId()));
                    match.recoverDiscardCardByEffect(A, A, tool.instanceId(), CardEffectDestination.DECK_SHUFFLED);
                    var deckPolicy = new ServerMatch.DeckSearchPolicy(1, 1, CardCategory.TRAINER, TrainerCategory.ITEM, false, Set.of());
                    if (old) {
                        match.searchOwnDeckToHandByEffect(A, deckPolicy, List.of(tool.instanceId()));
                        match.discardHandCardsByEffect(A, A, List.of(tool.instanceId()));
                        match.recoverDiscardCardByEffect(A, A, tool.instanceId(), CardEffectDestination.DECK_SHUFFLED);
                    } else {
                        var state = match.privateView(A); int audit = match.randomTranscript().size();
                        rejects(() -> match.searchOwnDeckToHandByEffect(A, deckPolicy, List.of(tool.instanceId())));
                        check(state.equals(match.privateView(A)) && audit == match.randomTranscript().size(), "现代物品牌库检索错误放行工具");
                    }
                }
                var argsMap = new HashMap<String,String>(Map.of("amount", "1", "minimum", "0", "card_category", "TRAINER", "trainer_category", "ITEM"));
                if (zone == EffectTarget.DISCARD) argsMap.put("destination", "HAND");
                var effect = new EffectDefinition(type, zone, argsMap, List.of(), List.of());
                var tx = match.openEffectZoneSelection(A, effect, 7);
                check(tx.promptFor(A).candidateIds().contains(tool.instanceId()) == old, "区域组件未按本局工具分类产生候选：" + type);
                rejects(() -> tx.promptFor(B));
                var state = match.privateView(A); int audit = match.randomTranscript().size();
                if (old) match.resolveEffectZoneSelectionByRule(A, effect, tx, tx.transactionId(), 7, List.of(tool.instanceId()));
                else {
                    rejects(() -> match.resolveEffectZoneSelectionByRule(A, effect, tx, tx.transactionId(), 7, List.of(tool.instanceId())));
                    check(state.equals(match.privateView(A)) && audit == match.randomTranscript().size(), "错误工具选择改变了区域或随机数");
                }
            }
            // Membership as an Item must not turn a Tool into a one-shot discarded Trainer.
            if (!match.privateView(A).hand().contains(tool))
                match.searchOwnDeckToHandByEffect(A, List.of(tool.instanceId()));
            match.playTrainer(A, tool.instanceId(), match.activePokemonView(A).pokemonId());
            check(match.activePokemonView(A).attachedTools().contains(tool) && !match.privateView(A).discard().contains(tool), "分类调整破坏了工具附着");
            PersistentTrainerTest.conserve(match);
        }
        var rules = BundledRules.loadRulesets();
        check(rules.get("standard-cn-2024-0618").trainerClassification().pokemonToolsAreItems(), "简中 2024 档案未保存旧工具分类");
        check(!rules.get("standard-cn-2025").trainerClassification().pokemonToolsAreItems(), "简中 2025 档案未应用分类勘误");
        check(!rules.get("standard-international-2023").trainerClassification().pokemonToolsAreItems(), "国际分类变更日期与简中混用");
        System.out.println("TrainerCategoryEditionTest passed: versioned Item/Tool membership in server zone filters, private prompts and attachments");
    }
    private static Ruleset rule(boolean toolsAreItems) {
        try (var in = TrainerCategoryEditionTest.class.getResourceAsStream("/data/ptcglive/rulesets/standard-international-2026.json");
             var reader = new InputStreamReader(Objects.requireNonNull(in), StandardCharsets.UTF_8)) {
            var json = JsonParser.parseReader(reader).getAsJsonObject();
            json.addProperty("id", "fixture-tool-classification");
            var classification = new JsonObject(); classification.addProperty("pokemonToolsAreItems", toolsAreItems);
            json.add("trainerClassification", classification);
            return BundledRules.ruleset(json);
        } catch (Exception e) { throw new AssertionError(e); }
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("服务器接受了无效的工具分类选择");
    }
}
