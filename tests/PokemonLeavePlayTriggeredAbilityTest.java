import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.MatchPhase;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.effect.EffectRegistry;
import dev.hua.ptcglive.engine.effect.EffectTrigger;
import dev.hua.ptcglive.engine.field.AbilityDefinition;
import dev.hua.ptcglive.engine.field.EnergyProfile;
import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.engine.field.LeavePlayDestination;
import java.util.List;
import java.util.UUID;

/** Surviving public sources may react after a reviewed non-KO Pokémon leaves play. */
public final class PokemonLeavePlayTriggeredAbilityTest {
    private static final UUID A = DynamicPassiveModifierTest.A, B = DynamicPassiveModifierTest.B;

    public static void main(String[] args) {
        benchLeaveIsObservedBySurvivingSource();
        activeLeaveResolvesTriggerBeforeReplacement();
        departingSourceResolvesIndependentProgram();
        sourceDependentLastKnownInformationStaysRejected();
        System.out.println("PokemonLeavePlayTriggeredAbilityTest passed: surviving-source leave-play events and replacement order");
    }

    private static void benchLeaveIsObservedBySurvivingSource() {
        var m = fixture(ability("leave-opponent", "OPPONENT_FIELD"));
        check(EventTargetBindingTest.field(m).activeAbilities(A, m.activePokemonView(A).pokemonId()).stream()
            .anyMatch(entry -> entry.id().equals("leave-opponent")), "离场观察特性未装载到战场");
        UUID bench = m.benchPokemonView(B).getFirst().pokemonId();
        UUID physical=PokemonIdentityTestSupport.physical(m,B,bench);
        int hand = m.privateView(A).hand().size();
        m.movePokemonByEffect(A, B, bench, LeavePlayDestination.HAND);
        resolveAllTriggerPrompts(m);
        check(m.privateView(A).hand().size() == hand + 2, "对方备战宝可梦离场后，两个仍在场来源没有都触发");
        check(m.privateView(B).hand().stream().anyMatch(card -> card.instanceId().equals(physical)), "离场宝可梦未按指定目的地移动");
    }

    private static void activeLeaveResolvesTriggerBeforeReplacement() {
        var m = fixture(ability("leave-active", "OPPONENT_FIELD"));
        UUID active = m.activePokemonView(B).pokemonId();
        UUID replacement = m.benchPokemonView(B).getFirst().pokemonId();
        int hand = m.privateView(A).hand().size();
        m.movePokemonByEffect(A, B, active, LeavePlayDestination.HAND);
        resolveAllTriggerPrompts(m);
        check(m.privateView(A).hand().size() == hand + 2, "战斗宝可梦离场事件在强制替补前被遗漏或搁置");
        check(m.phase() == MatchPhase.CHOOSE_REPLACEMENT, "非击倒离场应在触发后要求替补");
        m.chooseReplacement(B, replacement);
        check(m.phase() == MatchPhase.TURN && m.currentPlayer().equals(A), "非击倒离场替补后没有回到原回合");
    }

    private static void departingSourceResolvesIndependentProgram() {
        var m = fixture(ability("self-leaves", "SELF"));
        UUID source = m.activePokemonView(A).pokemonId();
        int hand = m.privateView(A).hand().size();
        m.movePokemonByEffect(A, A, source, LeavePlayDestination.HAND);
        resolveAllTriggerPrompts(m);
        check(m.privateView(A).hand().size() == hand + 2,
            "离场来源的独立抽牌文本没有按最后已知来源结算");
        check(m.phase() == MatchPhase.CHOOSE_REPLACEMENT,
            "离场来源文本结束后应再要求战斗位替补");
    }

    private static void sourceDependentLastKnownInformationStaysRejected() {
        var ability = ability("self-leaves", "SELF");
        check(ability.eventPolicy().trigger() == EffectTrigger.ON_LEAVE_PLAY, "离场事件策略没有被数据层识别");
        rejects(() -> dev.hua.ptcglive.engine.effect.TriggeredAbilityRules.validate(malformedLeaveAbility()));
    }

    private static AbilityDefinition malformedLeaveAbility() {
        return dev.hua.ptcglive.rules.BundledCombatData.ability(JsonParser.parseString("""
            {"id":"bad-leave","nameZh":"非法离场","kind":"TRIGGERED","effectSupport":"SUPPORTED",
             "eventPolicy":{"trigger":"ON_LEAVE_PLAY","watch":"SELF","optional":false,"usage":"NONE"},
             "triggers":[{"trigger":"ON_LEAVE_PLAY","effects":[{"type":"damage_counter","target":"SELF","amount":1}]}]}
            """).getAsJsonObject(), EffectRegistry.standard());
    }

    private static AbilityDefinition ability(String id, String watch) {
        return dev.hua.ptcglive.rules.BundledCombatData.ability(JsonParser.parseString("""
            {"id":"%s","nameZh":"离场观察夹具","kind":"TRIGGERED","effectSupport":"SUPPORTED",
             "eventPolicy":{"trigger":"ON_LEAVE_PLAY","watch":"%s","optional":false,"usage":"NONE"},
             "triggers":[{"trigger":"ON_LEAVE_PLAY","effects":[{"type":"draw","target":"HAND","amount":1}]}]}
            """.formatted(id, watch)).getAsJsonObject(), EffectRegistry.standard());
    }

    private static ServerMatch fixture(AbilityDefinition ability) {
        return DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(), DynamicPassiveModifierTest.plainStadium(),
            EnergyProfile.fixed(EnergyType.COLORLESS, 3), DynamicPassiveModifierTest.attack(40, false, false), List.of(ability));
    }
    private static void resolveAllTriggerPrompts(ServerMatch match) {
        for (int guard = 0; guard < 8 && match.triggerOrderPrompt() != null; guard++) {
            var prompt = match.triggerOrderPrompt();
            if (prompt.optionalDecision()) match.decideTriggeredEffect(prompt.choosingPlayer(), prompt.id(), true);
            else match.chooseTriggeredEffect(prompt.choosingPlayer(), prompt.id(), prompt.entries().getFirst().id());
        }
        check(match.triggerOrderPrompt() == null, "离场触发排序没有在有限步骤内结束");
    }
    private static void rejects(Runnable operation) { DynamicPassiveModifierTest.reject(operation); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
