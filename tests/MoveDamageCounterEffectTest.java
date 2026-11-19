import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.effect.EffectDefinition;
import dev.hua.ptcglive.engine.effect.EffectProgram;
import dev.hua.ptcglive.engine.effect.EffectTarget;
import dev.hua.ptcglive.engine.effect.EffectTrigger;
import dev.hua.ptcglive.engine.effect.TriggeredEffects;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative transfer of existing counters, not attack damage. */
public final class MoveDamageCounterEffectTest {
    private static final UUID A = DynamicPassiveModifierTest.A, B = DynamicPassiveModifierTest.B;
    public static void main(String[] args) {
        fixedPublicTargetsMoveAtomically();
        boundPublicTargetsRemainServerOwned();
        malformedOrInsufficientMovesFailClosed();
        System.out.println("MoveDamageCounterEffectTest passed: existing counters move through authoritative public targets only");
    }

    private static void fixedPublicTargetsMoveAtomically() {
        ServerMatch match = fixture(null);
        UUID source = match.activePokemonView(A).pokemonId();
        UUID destination = match.activePokemonView(B).pokemonId();
        match.placeDamageCountersByEffect(A, A, source, 3);
        execute(match, move(EffectTarget.OWN_ACTIVE, "OPPONENT_ACTIVE", 2));
        check(match.activePokemonView(A).damage() == 10 && match.activePokemonView(B).damage() == 20,
            "固定公开目标没有恰好移动两个伤害指示物");
        check(match.replayTranscript().stream().anyMatch(event -> event.operation().contains("移动伤害指示物")),
            "服务器回放没有记录伤害指示物移动");
    }

    private static void boundPublicTargetsRemainServerOwned() {
        var profile = ComposedTrainerIntegrationTest.item("""
            {"type":"select_pokemon","target":"SELECT_OPPONENT_POKEMON","position":"BENCH","bind":"target","effects":[]},
            {"type":"with_pokemon","ref":"target","position":"BENCH","effects":[
              {"type":"move_damage_counter","target":"OWN_ACTIVE","destination":"CHOSEN_POKEMON","amount":1}
            ]}
            """);
        ServerMatch match = fixture(profile);
        UUID source = match.activePokemonView(A).pokemonId();
        UUID destination = match.benchPokemonView(B).getFirst().pokemonId();
        match.placeDamageCountersByEffect(A, A, source, 2);
        match.playTrainer(A, DynamicPassiveModifierTest.hand(match, A, "item").instanceId(), null);
        var prompt = match.effectPokemonPrompt(A);
        match.chooseEffectPokemon(A, prompt.resolutionId(), prompt.step(), destination);
        check(match.activePokemonView(A).damage() == 10
                && match.benchPokemonView(B).stream().filter(p -> p.pokemonId().equals(destination)).findFirst().orElseThrow().damage() == 10,
            "已绑定的公开目的地没有从服务器选择事务取得指示物");
    }

    private static void malformedOrInsufficientMovesFailClosed() {
        ServerMatch match = fixture(null);
        UUID source = match.activePokemonView(A).pokemonId();
        UUID destination = match.activePokemonView(B).pokemonId();
        int own = match.activePokemonView(A).damage(), foe = match.activePokemonView(B).damage();
        rejects(() -> execute(match, move(EffectTarget.OWN_ACTIVE, "OPPONENT_ACTIVE", 1)));
        check(match.activePokemonView(A).damage() == own && match.activePokemonView(B).damage() == foe,
            "来源指示物不足时仍移动了部分伤害");
        rejects(() -> dev.hua.ptcglive.engine.effect.EffectRegistry.standard().parse(
            com.google.gson.JsonParser.parseString("{\"type\":\"move_damage_counter\",\"target\":\"OWN_ACTIVE\",\"destination\":\"SELECT_OPPONENT_POKEMON\",\"amount\":1}").getAsJsonObject()));
        match.placeDamageCountersByEffect(A, A, source, 1);
        rejects(() -> match.moveDamageCountersByEffect(A, A, source, B, destination, 2));
        check(match.activePokemonView(A).damage() == 10 && match.activePokemonView(B).damage() == foe,
            "直接服务器入口在无足够来源时没有原子拒绝");
    }

    private static EffectDefinition move(EffectTarget source, String destination, int amount) {
        return new EffectDefinition("move_damage_counter", source, Map.of("destination", destination, "amount", Integer.toString(amount)), List.of(), List.of());
    }
    private static void execute(ServerMatch match, EffectDefinition effect) {
        match.executeEffectProgramByRule(A, match.activePokemonView(A).pokemonId(),
            new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_CARD_PLAYED, List.of(), List.of(effect)))), EffectTrigger.ON_CARD_PLAYED);
    }
    private static ServerMatch fixture(dev.hua.ptcglive.rules.TrainerProfile item) {
        return DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(), DynamicPassiveModifierTest.plainStadium(),
            dev.hua.ptcglive.engine.field.EnergyProfile.basic(dev.hua.ptcglive.engine.field.EnergyType.COLORLESS),
            DynamicPassiveModifierTest.attack(40, false, false), List.of(), 0, item);
    }
    private static void rejects(Runnable operation) {
        try { operation.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("非法伤害指示物移动被接受");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
