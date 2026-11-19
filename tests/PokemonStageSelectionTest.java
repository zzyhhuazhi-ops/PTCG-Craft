import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.CoinSide;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.effect.EffectRegistry;
import dev.hua.ptcglive.engine.field.PokemonProfile;
import dev.hua.ptcglive.rules.BundledRules;
import dev.hua.ptcglive.rules.CardCategory;
import dev.hua.ptcglive.rules.CardDatabase;
import dev.hua.ptcglive.rules.CardDefinition;
import dev.hua.ptcglive.rules.CardInstance;
import dev.hua.ptcglive.rules.CombatCatalog;
import dev.hua.ptcglive.rules.EffectSupport;
import dev.hua.ptcglive.rules.TrainerCategory;
import dev.hua.ptcglive.rules.TrainerProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A Stage is derived from reviewed evolution links, never from a translated name or item model. */
public final class PokemonStageSelectionTest {
    private static final UUID A = new UUID(0, 9921), B = new UUID(0, 9922);

    public static void main(String[] args) {
        stageTwoCandidateIsServerFiltered();
        malformedStageIsRejectedBeforeMatch();
        System.out.println("PokemonStageSelectionTest passed: audited Stage filters issue only valid server targets");
    }

    private static void stageTwoCandidateIsServerFiltered() {
        ServerMatch match = fixture();
        UUID active = match.activePokemonView(A).pokemonId();
        match.endTurn(A); match.endTurn(B);
        match.evolve(A, active, hand(match, A, "stage-one").instanceId());
        match.endTurn(A); match.endTurn(B);
        match.evolve(A, active, hand(match, A, "stage-two").instanceId());
        match.placeDamageCountersByEffect(A, A, active, 2);

        match.playTrainer(A, hand(match, A, "item").instanceId(), null);
        var prompt = match.effectPokemonPrompt(A);
        check(prompt.targets().size() == 1 && prompt.targets().getFirst().pokemonId().equals(active),
            "STAGE2 选择混入基础或一阶宝可梦");
        UUID basicBench = match.benchPokemonView(A).getFirst().pokemonId();
        rejects(() -> match.chooseEffectPokemon(A, prompt.resolutionId(), prompt.step(), basicBench));
        match.chooseEffectPokemon(A, prompt.resolutionId(), prompt.step(), active);
        check(match.activePokemonView(A).damage() == 10, "选择到二阶宝可梦后没有继续执行绑定的回复效果");
    }

    private static void malformedStageIsRejectedBeforeMatch() {
        rejects(() -> trainer("STAGE3"));
        rejects(() -> trainer("stage2"));
    }

    private static ServerMatch fixture() {
        TrainerProfile profile = trainer("STAGE2");
        var definitions = List.of(
            card("base", CardCategory.POKEMON, true), card("stage-one", CardCategory.POKEMON, false),
            card("stage-two", CardCategory.POKEMON, false), card("item", CardCategory.TRAINER, false));
        var combat = new CombatCatalog(Map.of(
            "base", new PokemonProfile(100, 0, null),
            "stage-one", new PokemonProfile(120, 0, "base"),
            "stage-two", new PokemonProfile(140, 0, "stage-one")), Map.of(), Map.of("item", profile));
        for (int seed = 0; seed < 20_000; seed++) {
            var first = new ServerMatch(A, B, deck(A), deck(B), BundledRules.loadRulesets().get("standard-international-2026"),
                new CardDatabase(definitions), combat, new ServerRandom(seed));
            first.callCoin(A, CoinSide.HEADS);
            if (first.publicCoinResult() != CoinSide.HEADS) continue;
            first.chooseWhoGoesFirst(A, true);
            if (first.needsMulligan(A) || first.needsMulligan(B)) continue;
            if (!has(first, A, "base", 2) || !has(first, B, "base", 2) || !has(first, A, "stage-one", 1)
                || !has(first, A, "stage-two", 1) || !has(first, A, "item", 1)) continue;
            OpeningTestSupport.complete(first, A, B, 1);
            return first;
        }
        throw new AssertionError("未找到阶段选择测试所需的开局手牌");
    }

    private static TrainerProfile trainer(String stage) {
        var json = """
            {"trainerCategory":"ITEM","triggers":[{"trigger":"ON_CARD_PLAYED","effects":[
              {"type":"select_pokemon","target":"SELECT_OWN_POKEMON","stage":"%s","bind":"target","effects":[]},
              {"type":"with_pokemon","ref":"target","effects":[{"type":"heal","target":"CHOSEN_POKEMON","amount":10}]}
            ]}]}
            """.formatted(stage);
        var profile = dev.hua.ptcglive.rules.BundledCombatData.trainer(JsonParser.parseString(json).getAsJsonObject(), EffectRegistry.standard());
        dev.hua.ptcglive.engine.effect.TrainerProgramRules.validate(profile.effectProgram());
        return profile;
    }

    private static CardDefinition card(String id, CardCategory category, boolean basic) {
        return new CardDefinition(id, id, category, category == CardCategory.TRAINER ? TrainerCategory.ITEM : TrainerCategory.NONE,
            "H", Set.of("international"), basic, false, 60, Map.of(), EffectSupport.SUPPORTED, null);
    }
    private static List<CardInstance> deck(UUID owner) {
        var cards = new ArrayList<CardInstance>();
        for (int i = 0; i < 60; i++) {
            String id = i < 30 ? "base" : i < 40 ? "stage-one" : i < 50 ? "stage-two" : "item";
            cards.add(new CardInstance(UUID.randomUUID(), owner, id));
        }
        return cards;
    }
    private static boolean has(ServerMatch match, UUID owner, String id, int count) {
        return match.privateView(owner).hand().stream().filter(card -> card.definitionId().equals(id)).count() >= count;
    }
    private static CardInstance hand(ServerMatch match, UUID owner, String id) {
        return match.privateView(owner).hand().stream().filter(card -> card.definitionId().equals(id)).findFirst().orElseThrow();
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("非法阶段筛选没有在开始对局前拒绝");
    }
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
