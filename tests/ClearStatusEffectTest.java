import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.effect.EffectExecutionCapability;
import dev.hua.ptcglive.engine.effect.EffectExecutionCapabilityCatalog;
import dev.hua.ptcglive.engine.effect.EffectRegistry;
import dev.hua.ptcglive.engine.effect.ResumableProgramRules;
import dev.hua.ptcglive.engine.field.SpecialCondition;
import java.util.List;
import java.util.UUID;

/** Regression coverage for the reusable clear_status component, not a card-specific cure. */
public final class ClearStatusEffectTest {
    private static final UUID A = ResumableAbilityTest.A;

    public static void main(String[] args) {
        registryAndSchema();
        activeCureIsAtomic();
        selectedActiveCureUsesServerTarget();
        damageTriggeredCureUsesCurrentActive();
        System.out.println("ClearStatusEffectTest passed: data-driven condition cures are server-authoritative and Active-only");
    }

    private static void registryAndSchema() {
        EffectRegistry registry = EffectRegistry.standard();
        var all = registry.parse(JsonParser.parseString("{\"type\":\"clear_status\",\"target\":\"OWN_ACTIVE\",\"statuses\":\"ALL\"}").getAsJsonObject());
        if (EffectExecutionCapabilityCatalog.assess(all).capability() != EffectExecutionCapability.DIRECT_SERVER)
            throw new AssertionError("clear_status 没有标记为服务器直接结算组件");
        registry.parse(JsonParser.parseString("{\"type\":\"clear_status\",\"target\":\"OWN_ACTIVE\",\"statuses\":\"POISONED,BURNED\"}").getAsJsonObject());
        for (String bad : List.of(
            "{\"type\":\"clear_status\",\"target\":\"OWN_ACTIVE\"}",
            "{\"type\":\"clear_status\",\"target\":\"OWN_ACTIVE\",\"statuses\":\"\"}",
            "{\"type\":\"clear_status\",\"target\":\"OWN_ACTIVE\",\"statuses\":\"POISONED,POISONED\"}",
            "{\"type\":\"clear_status\",\"target\":\"OWN_ACTIVE\",\"statuses\":\"NAPPING\"}",
            "{\"type\":\"clear_status\",\"target\":\"OWN_ACTIVE\",\"statuses\":\"ALL\",\"duration\":\"FOREVER\"}"))
            rejects(() -> registry.parse(JsonParser.parseString(bad).getAsJsonObject()));
        var bench = ResumableAbilityTest.ability("bad-position", """
            {"type":"select_pokemon","target":"SELECT_OWN_POKEMON","position":"BENCH","effects":[
              {"type":"clear_status","target":"CHOSEN_POKEMON","statuses":"ALL"}]}
            """);
        if (ResumableProgramRules.unsupportedReason(bench.effectProgram(), true).isEmpty())
            throw new AssertionError("备战宝可梦解除异常状态被静态规则错误放行");
    }

    private static void activeCureIsAtomic() {
        var ability = ResumableAbilityTest.ability("all", "{\"type\":\"clear_status\",\"target\":\"OWN_ACTIVE\",\"statuses\":\"ALL\"}");
        var match = ResumableAbilityTest.fixture(ability);
        UUID active = match.activePokemonView(A).pokemonId();
        match.applySpecialConditionByEffect(A, A, SpecialCondition.POISONED);
        match.applySpecialConditionByEffect(A, A, SpecialCondition.BURNED);
        match.applySpecialConditionByEffect(A, A, SpecialCondition.ASLEEP);
        match.placeDamageCountersByEffect(A, A, active, 2);
        match.useAbility(A, active, ability.id());
        if (!match.activePokemonView(A).conditions().isEmpty() || match.activePokemonView(A).damage() != 20)
            throw new AssertionError("ALL 解除状态误保留异常状态或误改伤害");
        if (match.replayTranscript().stream().noneMatch(entry -> entry.operation().equals("已审核效果移除异常状态")))
            throw new AssertionError("通用解除状态没有进入服务器审计记录");

        var noStatus = ResumableAbilityTest.fixture(ability);
        UUID source = noStatus.activePokemonView(A).pokemonId();
        var before = noStatus.privateView(A);
        ResumableAbilityTest.rejects(() -> noStatus.useAbility(A, source, ability.id()));
        if (!before.equals(noStatus.privateView(A))) throw new AssertionError("无可解除状态时错误消耗特性或改变隐藏区域");
    }

    private static void selectedActiveCureUsesServerTarget() {
        var ability = ResumableAbilityTest.ability("selected", """
            {"type":"select_pokemon","target":"SELECT_OWN_POKEMON","position":"ACTIVE","effects":[
              {"type":"clear_status","target":"CHOSEN_POKEMON","statuses":"POISONED,BURNED"}]}
            """);
        var match = ResumableAbilityTest.fixture(ability);
        UUID source = match.activePokemonView(A).pokemonId();
        match.applySpecialConditionByEffect(A, A, SpecialCondition.POISONED);
        match.applySpecialConditionByEffect(A, A, SpecialCondition.BURNED);
        match.applySpecialConditionByEffect(A, A, SpecialCondition.CONFUSED);
        match.useAbility(A, source, ability.id());
        var prompt = match.effectPokemonPrompt(A);
        if (prompt.targets().size() != 1 || !prompt.targets().getFirst().pokemonId().equals(source))
            throw new AssertionError("解除状态选择错误暴露备战或非己方目标");
        ResumableAbilityTest.rejects(() -> match.chooseEffectPokemon(A, prompt.resolutionId(), prompt.step(), UUID.randomUUID()));
        match.chooseEffectPokemon(A, prompt.resolutionId(), prompt.step(), source);
        if (match.activePokemonView(A).conditions().size() != 1 || !match.activePokemonView(A).conditions().contains(SpecialCondition.CONFUSED))
            throw new AssertionError("指定解除状态错误清除了未声明状态，或未清除指定状态：" + match.activePokemonView(A).conditions());
    }

    private static void damageTriggeredCureUsesCurrentActive() {
        var reaction = DamageTriggeredAbilityTest.ability("clear-on-damage", true, false, false,
            "{\"type\":\"clear_status\",\"target\":\"OWN_ACTIVE\",\"statuses\":\"POISONED\"}");
        var match = DamageTriggeredAbilityTest.fixture(DamageTriggeredAbilityTest.attack(10, ""), reaction);
        match.applySpecialConditionByEffect(A, ResumableAbilityTest.B, SpecialCondition.POISONED);
        match.applySpecialConditionByEffect(A, ResumableAbilityTest.B, SpecialCondition.BURNED);
        if (!match.activePokemonView(ResumableAbilityTest.B).conditions().contains(SpecialCondition.POISONED))
            throw new AssertionError("伤害触发夹具未能在攻击前给乙方战斗位附加中毒");
        match.attack(A, "hit");
        DamageTriggeredAbilityTest.finishTriggers(match);
        var conditions = match.activePokemonView(ResumableAbilityTest.B).conditions();
        if (conditions.size() != 1 || !conditions.contains(SpecialCondition.BURNED))
            throw new AssertionError("伤害触发的 clear_status 错误清除了未声明状态或未清除中毒：" + conditions);

        var none = DamageTriggeredAbilityTest.fixture(DamageTriggeredAbilityTest.attack(10, ""), reaction);
        none.attack(A, "hit");
        if (none.triggerOrderPrompt() != null)
            throw new AssertionError("没有可解除状态时仍向客户端发出无作用的自动特性选择");
    }

    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("非法 clear_status 定义被接受");
    }
}
