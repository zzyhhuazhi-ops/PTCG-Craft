import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.rules.BundledCombatData;
import dev.hua.ptcglive.rules.CardInstance;
import java.util.*;

/**
 * “Search your deck for up to N Basic Pokémon and put them onto your Bench.”:
 * search_deck destination BENCH requires a Basic Pokémon filter, offers at most
 * as many cards as there are free Bench slots and shuffles the deck once.
 */
public final class DeckSearchToBenchTest {
    static final UUID A = ResumableAbilityTest.A, B = ResumableAbilityTest.B;
    static final String SEARCH = "{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":2,\"minimum\":0,\"card_category\":\"POKEMON\","
        + "\"basic_pokemon_only\":true,\"destination\":\"BENCH\"}";
    public static void main(String[] args) {
        putsOntoBench(); limitedByFreeBench(); fullBenchDoesNothing(); trainerProgram(); schema();
        System.out.println("DeckSearchToBenchTest passed: deck-to-Bench searches are Basic-only, Bench-limited and server-validated");
    }
    static void putsOntoBench() {
        var a = ResumableAttackTest.attack(SEARCH);
        check(AttackProgramRules.unsupportedReason(a).isEmpty(), "招式检索放到备战区被拒绝：" + AttackProgramRules.unsupportedReason(a));
        var m = ResumableAttackTest.fixture(a);
        int bench = m.benchPokemonView(A).size(), deck = m.privateView(A).deck().size();
        m.attack(A, a.id());
        var prompt = m.effectCardPrompt(A);
        var chosen = prompt.candidates().stream().limit(2).map(CardInstance::instanceId).toList();
        m.submitEffectCards(A, prompt.resolutionId(), prompt.step(), chosen);
        check(m.benchPokemonView(A).size() == bench + 2 && m.privateView(A).deck().size() == deck - 2
            && m.privateView(A).hand().stream().noneMatch(c -> chosen.contains(c.instanceId())), "所选基础宝可梦没有放到备战区");
        check(m.currentPlayer().equals(B), "攻击后效果完成后没有结束回合");
    }
    static void limitedByFreeBench() {
        var a = ResumableAttackTest.attack(SEARCH);
        var m = ResumableAttackTest.fixture(a);
        int free = m.benchLimit() - m.benchPokemonView(A).size();
        m.putBasicPokemonFromOwnDeckToBenchByEffect(A, new ServerMatch.CardSelectionPolicy(free - 1, free - 1,
                dev.hua.ptcglive.rules.CardCategory.POKEMON, null, true, Set.of()),
            m.privateView(A).deck().stream().limit(free - 1).map(CardInstance::instanceId).toList());
        m.attack(A, a.id());
        var prompt = m.effectCardPrompt(A);
        var two = prompt.candidates().stream().limit(2).map(CardInstance::instanceId).toList();
        reject(() -> m.submitEffectCards(A, prompt.resolutionId(), prompt.step(), two));
        m.submitEffectCards(A, prompt.resolutionId(), prompt.step(), two.subList(0, 1));
        check(m.benchPokemonView(A).size() == m.benchLimit(), "只剩 1 个空位时应只能放 1 只");
    }
    static void fullBenchDoesNothing() {
        var a = ResumableAttackTest.attack(SEARCH + ",{\"type\":\"draw\",\"amount\":1}");
        var m = ResumableAttackTest.fixture(a);
        int free = m.benchLimit() - m.benchPokemonView(A).size();
        m.putBasicPokemonFromOwnDeckToBenchByEffect(A, new ServerMatch.CardSelectionPolicy(free, free,
                dev.hua.ptcglive.rules.CardCategory.POKEMON, null, true, Set.of()),
            m.privateView(A).deck().stream().limit(free).map(CardInstance::instanceId).toList());
        int hand = m.privateView(A).hand().size();
        m.attack(A, a.id());
        check(!m.hasPendingEffectChoice() && m.privateView(A).hand().size() == hand + 1 && m.currentPlayer().equals(B),
            "备战区已满时应跳过检索并继续后续文字");
    }
    static void trainerProgram() {
        var trainer = BundledCombatData.trainer(JsonParser.parseString("{\"trainerCategory\":\"ITEM\",\"effects\":[" + SEARCH + "]}").getAsJsonObject(),
            EffectRegistry.standard());
        check(TrainerProgramRules.unsupportedReason(trainer.effectProgram()).isEmpty(), "训练家的检索放到备战区被拒绝");
    }
    static void schema() {
        var registry=EffectRegistry.standard();
        reject(()->ZoneSelectionEffect.forProgram(registry.parse(JsonParser.parseString("{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":1,\"minimum\":0,\"card_category\":\"POKEMON\",\"destination\":\"BENCH\"}").getAsJsonObject())));
        reject(()->ZoneSelectionEffect.forProgram(registry.parse(JsonParser.parseString("{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":1,\"minimum\":0,\"destination\":\"BENCH\"}").getAsJsonObject())));
        ZoneSelectionEffect.forProgram(registry.parse(JsonParser.parseString("{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":1,\"minimum\":0,\"card_category\":\"POKEMON\",\"destination\":\"HAND\",\"basic_pokemon_only\":true}").getAsJsonObject()));
        reject(()->ZoneSelectionEffect.forProgram(registry.parse(JsonParser.parseString("{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":1,\"minimum\":0,\"card_category\":\"POKEMON\",\"destination\":\"BENCH\",\"basic_pokemon_only\":true,\"reveal\":true}").getAsJsonObject())));
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static void reject(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("非法的检索放到备战区数据或选择被接受");
    }
}
