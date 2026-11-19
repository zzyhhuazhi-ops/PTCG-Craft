import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.effect.EffectRegistry;
import dev.hua.ptcglive.engine.effect.EffectTrigger;
import dev.hua.ptcglive.rules.BundledCombatData;
import java.util.List;
import java.util.UUID;

/** Multiple public target choices are server-bound by incarnation, never by a client card name or slot. */
public final class GenericPokemonBindingsTest {
    static final UUID A = DynamicPassiveModifierTest.A, B = DynamicPassiveModifierTest.B;
    public static void main(String[] args) {
        optionalNamedHealing(); trainerBindsTwoIndependentTargets(); invalidFlowsFailBeforePlay();
        System.out.println("GenericPokemonBindingsTest passed: resumable Trainer target bindings and fail-closed data flow");
    }

    private static void optionalNamedHealing() {
        var m=fixture("""
            {"type":"select_pokemon","target":"SELECT_OWN_POKEMON","position":"BENCH","bind":"ally","effects":[]},
            {"type":"choice","optional":true,"effects":[{"type":"with_pokemon","ref":"ally","effects":[{"type":"heal","target":"CHOSEN_POKEMON","amount":10}]}]}
            """);
        UUID host=m.benchPokemonView(A).getFirst().pokemonId();m.placeDamageCountersByEffect(A,A,host,2);
        m.playTrainer(A,DynamicPassiveModifierTest.hand(m,A,"item").instanceId(),null);
        var target=m.effectPokemonPrompt(A);m.chooseEffectPokemon(A,target.resolutionId(),target.step(),host);
        var choice=m.effectChoicePrompt(A);m.chooseEffectOption(A,choice.resolutionId(),choice.step(),0);
        check(m.benchPokemonView(A).getFirst().damage()==10&&m.activePokemonView(A).damage()==0,"optional named healing evaluated with the unbound atomic context");
    }
    private static void trainerBindsTwoIndependentTargets() {
        var match = fixture("""
            {"type":"select_pokemon","target":"SELECT_OWN_POKEMON","position":"BENCH","bind":"ally","effects":[]},
            {"type":"select_pokemon","target":"SELECT_OPPONENT_POKEMON","position":"ACTIVE","bind":"foe","effects":[]},
            {"type":"with_pokemon","ref":"ally","position":"BENCH","effects":[{"type":"heal","target":"CHOSEN_POKEMON","amount":10}]},
            {"type":"with_pokemon","ref":"foe","position":"ACTIVE","effects":[{"type":"damage_counter","target":"CHOSEN_POKEMON","amount":1}]}
            """);
        UUID ally = match.benchPokemonView(A).getFirst().pokemonId();
        UUID foe = match.activePokemonView(B).pokemonId();
        match.placeDamageCountersByEffect(A, A, ally, 2);
        int allyBefore = match.benchPokemonView(A).getFirst().damage();
        int foeBefore = match.activePokemonView(B).damage();

        match.playTrainer(A, DynamicPassiveModifierTest.hand(match, A, "item").instanceId(), null);
        var first = match.effectPokemonPrompt(A);
        check(first.targets().stream().anyMatch(target -> target.pokemonId().equals(ally)), "第一个服务器目标未包含己方备战区");
        match.chooseEffectPokemon(A, first.resolutionId(), first.step(), ally);
        var second = match.effectPokemonPrompt(A);
        check(second.targets().size() == 1 && second.targets().getFirst().pokemonId().equals(foe), "第二个选择没有按独立对方战斗位规则签发");
        match.chooseEffectPokemon(A, second.resolutionId(), second.step(), foe);

        check(match.benchPokemonView(A).stream().filter(view -> view.pokemonId().equals(ally)).findFirst().orElseThrow().damage() == allyBefore - 10,
            "第一个已绑定目标没有在后续步骤正确回复");
        check(match.activePokemonView(B).pokemonId().equals(foe) && match.activePokemonView(B).damage() == foeBefore + 10,
            "第二个已绑定目标没有在后续步骤正确放置伤害指示物");
        check(match.replayTranscript().stream().filter(event -> event.operation().contains("ally") || event.operation().contains("foe")).count() == 2,
            "服务器回放没有记录恰好两个独立的目标绑定");
    }

    private static void invalidFlowsFailBeforePlay() {
        rejects(() -> trainer("{\"type\":\"with_pokemon\",\"ref\":\"unknown\",\"effects\":[{\"type\":\"heal\",\"target\":\"CHOSEN_POKEMON\",\"amount\":10}]}"));
        rejects(() -> trainer("""
            {"type":"select_pokemon","target":"SELECT_OWN_POKEMON","bind":"same","effects":[]},
            {"type":"select_pokemon","target":"SELECT_OPPONENT_POKEMON","bind":"same","effects":[]}
            """));
        rejects(() -> dev.hua.ptcglive.engine.effect.TriggeredAbilityRules.validate(
            EnergyTriggeredAbilityTest.ability("automatic-bind", "SELF", false, "NONE",
                "{\"type\":\"select_pokemon\",\"target\":\"SELECT_OWN_POKEMON\",\"bind\":\"later\",\"effects\":[]}")));
    }

    private static ServerMatch fixture(String effects) {
        return DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(), DynamicPassiveModifierTest.plainStadium(),
            dev.hua.ptcglive.engine.field.EnergyProfile.basic(dev.hua.ptcglive.engine.field.EnergyType.COLORLESS),
            DynamicPassiveModifierTest.attack(40, false, false), List.of(), 0, trainer(effects));
    }

    private static dev.hua.ptcglive.rules.TrainerProfile trainer(String effects) {
        var profile = BundledCombatData.trainer(JsonParser.parseString("{\"trainerCategory\":\"ITEM\",\"triggers\":[{\"trigger\":\"ON_CARD_PLAYED\",\"effects\":[" + effects + "]}]}").getAsJsonObject(), EffectRegistry.standard());
        dev.hua.ptcglive.engine.effect.TrainerProgramRules.validate(profile.effectProgram());
        return profile;
    }
    private static void rejects(Runnable operation) {
        try { operation.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("非法命名目标数据流被接受");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
