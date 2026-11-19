import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/**
 * Special Energy data shapes written by PTCGCardCN for reviewed printed cards
 * (Unit Energy, Prism Energy, Draw Energy, Capture Energy, Rainbow Energy) run
 * through the existing provision branches and Energy-attachment triggers.
 */
public final class SpecialEnergyTemplateTest {
    static final UUID A = DynamicPassiveModifierTest.A;
    static final String ALL_TYPES = "[\"GRASS\",\"FIRE\",\"WATER\",\"LIGHTNING\",\"PSYCHIC\",\"FIGHTING\",\"DARKNESS\",\"METAL\",\"DRAGON\",\"COLORLESS\",\"FAIRY\"]";
    static final String FROM_HAND = "{\"trigger\":\"ON_ENERGY_ATTACHED\",\"target\":\"SELF\",\"source\":\"HAND\",\"cause\":\"ANY\",\"optional\":false,\"usage\":\"NONE\"}";
    public static void main(String[] args) {
        provision("{\"energyUnits\":[[\"GRASS\",\"FIRE\",\"WATER\"]]}", true, "Unit Energy GRW 应能支付火能量");
        provision("{\"energyUnits\":[[\"LIGHTNING\",\"PSYCHIC\",\"METAL\"]]}", false, "Unit Energy LPM 不应支付火能量");
        provision("{\"energyUnits\":[[\"COLORLESS\"]],\"provisionRules\":[{\"conditions\":[{\"type\":\"IS_BASIC\"}],\"energyUnits\":[" + ALL_TYPES + "]}]}",
            true, "Prism Energy 附在基础宝可梦上应提供任意属性");
        drawEnergy(); captureEnergy(); rainbowEnergy();
        System.out.println("SpecialEnergyTemplateTest passed: reviewed Special Energy templates provide and trigger as printed");
    }
    static EnergyProfile energy(String json) { return BundledCombatData.specialEnergy(JsonParser.parseString(json).getAsJsonObject(), EffectRegistry.standard()); }
    static String trigger(String effects) {
        return ",\"automaticEffects\":[{\"id\":\"attach\",\"nameZh\":\"附着时\",\"eventPolicy\":" + FROM_HAND + ",\"sourceCardOnly\":true,\"effects\":[" + effects + "]}]";
    }
    static AttackDefinition fireAttack() {
        return BundledCombatData.attack(JsonParser.parseString("{\"id\":\"hit\",\"nameZh\":\"测试攻击\",\"energyCost\":{\"FIRE\":1},\"baseDamage\":10,"
            + "\"damageType\":\"GRASS\",\"effectSupport\":\"SUPPORTED\"}").getAsJsonObject(), EffectRegistry.standard());
    }
    static ServerMatch fixture(EnergyProfile special) {
        return DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(), DynamicPassiveModifierTest.plainStadium(), special, fireAttack());
    }
    static CardInstance hand(ServerMatch m, String id) { return DynamicPassiveModifierTest.hand(m, A, id); }
    static void provision(String json, boolean payable, String message) {
        var m = fixture(energy(json));
        UUID host = m.activePokemonView(A).pokemonId();
        m.attachEnergy(A, hand(m, "special").instanceId(), host);
        check(m.canPayActiveAttackCost(A, fireAttack()) == payable, message);
    }
    static void drawEnergy() {
        var m = fixture(energy("{\"energyUnits\":[[\"COLORLESS\"]]" + trigger("{\"type\":\"draw\",\"amount\":1}") + "}"));
        int before = m.privateView(A).hand().size();
        m.attachEnergy(A, hand(m, "special").instanceId(), m.activePokemonView(A).pokemonId());
        check(m.privateView(A).hand().size() == before, "Draw Energy 从手牌附着后应抽 1 张");
        int after = m.privateView(A).hand().size();
        m.attachEnergyByEffect(A, A, EffectSourceZone.HAND, hand(m, "energy").instanceId(), A, m.activePokemonView(A).pokemonId());
        check(m.privateView(A).hand().size() == after - 1, "附着其它能量不应触发 Draw Energy");
    }
    static void captureEnergy() {
        var m = fixture(energy("{\"energyUnits\":[[\"COLORLESS\"]]" + trigger("{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":1,\"minimum\":0,"
            + "\"card_category\":\"POKEMON\",\"basic_pokemon_only\":true,\"destination\":\"BENCH\"}") + "}"));
        int bench = m.benchPokemonView(A).size();
        m.attachEnergy(A, hand(m, "special").instanceId(), m.activePokemonView(A).pokemonId());
        var prompt = m.effectCardPrompt(A);
        check(prompt.candidates().stream().allMatch(c -> c.definitionId().equals("basic")), "Capture Energy 只能检索基础宝可梦");
        m.submitEffectCards(A, prompt.resolutionId(), prompt.step(), List.of(prompt.candidates().getFirst().instanceId()));
        check(m.benchPokemonView(A).size() == bench + 1, "Capture Energy 没有把基础宝可梦放到备战区");
    }
    static void rainbowEnergy() {
        var m = fixture(energy("{\"energyUnits\":[" + ALL_TYPES + "]" + trigger("{\"type\":\"damage_counter\",\"target\":\"TRIGGER_POKEMON\",\"amount\":1}") + "}"));
        UUID host = m.activePokemonView(A).pokemonId();
        int damage = m.activePokemonView(A).damage();
        m.attachEnergy(A, hand(m, "special").instanceId(), host);
        check(m.activePokemonView(A).damage() == damage + 10, "Rainbow Energy 附着后应在该宝可梦身上放 1 个伤害指示物");
        check(m.canPayActiveAttackCost(A, fireAttack()), "Rainbow Energy 应提供任意属性");
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
