import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.effect.EffectRegistry;
import dev.hua.ptcglive.engine.effect.EffectTrigger;
import dev.hua.ptcglive.engine.field.AbilityDefinition;
import dev.hua.ptcglive.engine.field.EnergyProfile;
import dev.hua.ptcglive.engine.field.EnergyType;
import java.util.List;
import java.util.UUID;

/** Synthetic programs only: public battlefield changes must use one captured event boundary. */
public final class PokemonChangeTriggeredAbilityTest {
    static final UUID A = DynamicPassiveModifierTest.A, B = DynamicPassiveModifierTest.B;

    public static void main(String[] args) {
        handAndEffectEntry(); evolveAndRetreat(); effectSwitchAndPolicyValidation();
        System.out.println("PokemonChangeTriggeredAbilityTest passed: field-entry, evolution, retreat and switch trigger boundaries");
    }

    private static void handAndEffectEntry() {
        var entry = ability("entry", EffectTrigger.ON_ENTER_PLAY, "SELF", "{\"type\":\"draw\",\"amount\":1}");
        var match = fixture(List.of(entry), null);
        check(match.replayTranscript().stream().noneMatch(event -> event.operation().contains("触发特性")),
            "开局摆放错误触发普通登场特性");
        int hand = match.privateView(A).hand().size();
        match.playBasicPokemonToBench(A, DynamicPassiveModifierTest.hand(match, A, "basic").instanceId());
        check(match.privateView(A).hand().size() == hand, "从手牌登场没有在移牌后触发自身抽牌");

    }

    private static void evolveAndRetreat() {
        var evolution = ability("evolve", EffectTrigger.ON_EVOLVE, "SELF", "{\"type\":\"draw\",\"amount\":1}");
        var match = fixture(List.of(evolution), null);
        UUID active = match.activePokemonView(A).pokemonId();
        int hand = match.privateView(A).hand().size();
        match.evolve(A, active, DynamicPassiveModifierTest.hand(match, A, "evolved").instanceId());
        check(match.privateView(A).hand().size() == hand, "进化后没有由进化后的场上对象触发特性");

        var retreat = ability("retreat", EffectTrigger.ON_RETREAT, "SELF", "{\"type\":\"draw\",\"amount\":1}");
        match = fixture(List.of(retreat), null);
        UUID retreating = match.activePokemonView(A).pokemonId();
        UUID replacement = match.benchPokemonView(A).getFirst().pokemonId();
        var payment = DynamicPassiveModifierTest.hand(match, A, "special");
        match.attachEnergy(A, payment.instanceId(), retreating);
        hand = match.privateView(A).hand().size();
        match.retreat(A, replacement, List.of(payment.instanceId()));
        check(match.privateView(A).hand().size() == hand + 1
                && match.benchPokemonView(A).stream().anyMatch(pokemon -> pokemon.pokemonId().equals(retreating)),
            "撤退事件没有绑定刚离开战斗位的宝可梦");
    }

    private static void effectSwitchAndPolicyValidation() {
        var switched = ability("switched", EffectTrigger.ON_SWITCH, "SELF", "{\"type\":\"draw\",\"amount\":1}");
        var match = fixture(List.of(switched), null);
        int hand = match.privateView(A).hand().size();
        match.switchActiveByEffect(A, A, match.benchPokemonView(A).getFirst().pokemonId());
        check(match.privateView(A).hand().size() == hand + 1, "效果换位没有在原效果完成后触发新战斗位特性");

        rejects(() -> dev.hua.ptcglive.engine.effect.TriggeredAbilityRules.validate(
            ability("bad", EffectTrigger.ON_ENTER_PLAY, "SELF", "{\"type\":\"return_to_hand\",\"target\":\"SELF\"}")));
        rejects(() -> ability("bad-watch", EffectTrigger.ON_SWITCH, "MISSING", "{\"type\":\"draw\",\"amount\":1}"));
    }

    private static AbilityDefinition ability(String id, EffectTrigger trigger, String watch, String effects) {
        return dev.hua.ptcglive.rules.BundledCombatData.ability(JsonParser.parseString("{\"id\":\"" + id
            + "\",\"nameZh\":\"场上变化夹具\" ,\"kind\":\"TRIGGERED\",\"effectSupport\":\"SUPPORTED\",\"eventPolicy\":{\"trigger\":\""
            + trigger + "\",\"watch\":\"" + watch + "\",\"optional\":false,\"usage\":\"NONE\"},\"triggers\":[{\"trigger\":\""
            + trigger + "\",\"effects\":[" + effects + "]}]}" ).getAsJsonObject(), EffectRegistry.standard());
    }

    private static ServerMatch fixture(List<AbilityDefinition> abilities, String itemEffects) {
        var item = itemEffects == null ? null : DynamicPassiveModifierTest.trainer("ITEM", itemEffects);
        return DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(), DynamicPassiveModifierTest.plainStadium(),
            EnergyProfile.fixed(EnergyType.COLORLESS, 3), DynamicPassiveModifierTest.attack(40, false, false), abilities, 0, item);
    }

    private static void rejects(Runnable operation) {
        try { operation.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("无效场上变化事件被接受");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
