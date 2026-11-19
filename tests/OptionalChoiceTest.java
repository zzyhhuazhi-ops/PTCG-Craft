import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import java.util.*;

/**
 * “You may switch this Pokémon with 1 of your Benched Pokémon.”: choice with
 * optional:true offers its options plus a final “不发动” option that resolves
 * nothing; the controller decides on the server-issued prompt.
 */
public final class OptionalChoiceTest {
    static final UUID A = ResumableAbilityTest.A, B = ResumableAbilityTest.B;
    static final String MAY_SWITCH = "{\"type\":\"choice\",\"optional\":true,\"effects\":[{\"type\":\"switch\",\"target\":\"SELECT_OWN_POKEMON\"}]}";
    public static void main(String[] args) {
        decline(); accept(); schema();
        System.out.println("OptionalChoiceTest passed: optional choices can be declined and otherwise resolve their option");
    }
    static void decline() {
        var a = ResumableAttackTest.attack(MAY_SWITCH);
        check(AttackProgramRules.unsupportedReason(a).isEmpty(), "可选换位被拒绝：" + AttackProgramRules.unsupportedReason(a));
        var m = ResumableAttackTest.fixture(a); UUID active = m.activePokemonView(A).pokemonId(); int turn = m.turnNumber();
        m.attack(A, a.id());
        var prompt = m.effectChoicePrompt(A);
        check(prompt.optionsZh().size() == 2 && prompt.optionsZh().getLast().equals("不发动"), "可选效果没有给出“不发动”选项");
        reject(() -> m.chooseEffectOption(A, prompt.resolutionId(), prompt.step(), 2));
        m.chooseEffectOption(A, prompt.resolutionId(), prompt.step(), 1);
        check(m.activePokemonView(A).pokemonId().equals(active) && m.currentPlayer().equals(B) && m.turnNumber() == turn + 1,
            "不发动后仍然换位或没有结束攻击");
    }
    static void accept() {
        var a = ResumableAttackTest.attack(MAY_SWITCH);
        var m = ResumableAttackTest.fixture(a); UUID bench = m.benchPokemonView(A).getFirst().pokemonId();
        m.attack(A, a.id());
        var prompt = m.effectChoicePrompt(A);
        m.chooseEffectOption(A, prompt.resolutionId(), prompt.step(), 0);
        var target = m.effectPokemonPrompt(A);
        m.chooseEffectPokemon(A, target.resolutionId(), target.step(), bench);
        check(m.activePokemonView(A).pokemonId().equals(bench) && m.currentPlayer().equals(B), "选择发动后没有换位");
    }
    static void schema() {
        var registry = EffectRegistry.standard();
        for (String bad : List.of(
            "{\"type\":\"choice\",\"effects\":[{\"type\":\"draw\",\"amount\":1}]}",
            "{\"type\":\"choice\",\"optional\":\"maybe\",\"effects\":[{\"type\":\"draw\",\"amount\":1}]}",
            "{\"type\":\"choice\",\"optional\":true,\"amount\":1,\"effects\":[{\"type\":\"draw\",\"amount\":1}]}"))
            reject(() -> registry.parse(JsonParser.parseString(bad).getAsJsonObject()));
        var trainer = dev.hua.ptcglive.rules.BundledCombatData.trainer(JsonParser.parseString("{\"trainerCategory\":\"ITEM\",\"effects\":["
            + "{\"type\":\"choice\",\"optional\":true,\"effects\":[{\"type\":\"draw\",\"amount\":2}]}]}").getAsJsonObject(), registry);
        check(TrainerProgramRules.unsupportedReason(trainer.effectProgram()).isEmpty(), "训练家的可选效果被拒绝");
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static void reject(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("非法的可选效果数据或选项被接受");
    }
}
