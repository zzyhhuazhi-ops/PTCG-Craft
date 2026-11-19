import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.EffectSourceZone;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.effect.EffectExecutionCapability;
import dev.hua.ptcglive.engine.effect.EffectExecutionCapabilityCatalog;
import dev.hua.ptcglive.engine.effect.EffectRegistry;
import dev.hua.ptcglive.engine.field.AbilityDefinition;
import dev.hua.ptcglive.engine.field.AttackDefinition;
import dev.hua.ptcglive.rules.CardInstance;
import java.util.List;
import java.util.UUID;

/** Regression contract for data-driven all:true attached-Energy operations. */
public final class AllAttachedEnergyEffectTest {
    private static final UUID A = DynamicPassiveModifierTest.A;
    private static final UUID B = DynamicPassiveModifierTest.B;

    public static void main(String[] args) {
        schemaAndCapability();
        attackDiscardsAllWithoutSelection();
        attackReturnsAllToActualOwner();
        energyTriggeredAllDiscardUsesServerEventQueue();
        System.out.println("AllAttachedEnergyEffectTest passed: all attached Energy is server-resolved without a client card selection");
    }

    private static void schemaAndCapability() {
        EffectRegistry registry = EffectRegistry.standard();
        var discard = registry.parse(JsonParser.parseString(
            "{\"type\":\"discard_energy\",\"target\":\"OWN_ACTIVE\",\"all\":true}").getAsJsonObject());
        var returned = registry.parse(JsonParser.parseString(
            "{\"type\":\"return_energy\",\"target\":\"ALL_OPPONENT_POKEMON\",\"all\":true}").getAsJsonObject());
        check(EffectExecutionCapabilityCatalog.assess(discard).capability() == EffectExecutionCapability.DIRECT_SERVER
                && EffectExecutionCapabilityCatalog.assess(returned).capability() == EffectExecutionCapability.DIRECT_SERVER,
            "all:true 附着能量没有被标记为服务器直接结算组件");
        for (String invalid : List.of(
            "{\"type\":\"discard_energy\",\"target\":\"OWN_ACTIVE\",\"all\":true,\"amount\":1}",
            "{\"type\":\"return_energy\",\"target\":\"OWN_ACTIVE\",\"all\":\"ALL\"}",
            "{\"type\":\"move_energy\",\"target\":\"OWN_ACTIVE\",\"all\":true}"))
            rejects(() -> registry.parse(JsonParser.parseString(invalid).getAsJsonObject()));
    }

    private static void attackDiscardsAllWithoutSelection() {
        AttackDefinition attack = BeforeDamageAttackTest.attack("", "",
            "{\"type\":\"discard_energy\",\"target\":\"OWN_ACTIVE\",\"all\":true}");
        ServerMatch match = BeforeDamageAttackTest.fixture(attack);
        UUID active = match.activePokemonView(A).pokemonId();
        List<CardInstance> attached = attachTwo(match, A, A, active);
        match.attack(A, attack.id());
        check(!match.hasPendingCardSelection() && !match.hasPendingAttachmentTarget(),
            "all:true 弃能错误要求客户端选择能量");
        check(match.activePokemonView(A).attachedEnergyIds().isEmpty()
                && attached.stream().allMatch(card -> match.privateView(A).discard().contains(card)),
            "all:true 没有将当前战斗位全部附着能量移入实际拥有者弃牌区");
        check(match.replayTranscript().stream().anyMatch(entry -> entry.operation().equals("已审核效果弃置场上的能量")),
            "all:true 弃能没有写入服务器审计记录");
    }

    private static void attackReturnsAllToActualOwner() {
        AttackDefinition attack = BeforeDamageAttackTest.attack("", "",
            "{\"type\":\"return_energy\",\"target\":\"OPPONENT_ACTIVE\",\"all\":true}");
        ServerMatch match = BeforeDamageAttackTest.fixture(attack);
        UUID active = match.activePokemonView(B).pokemonId();
        List<CardInstance> attached = attachTwo(match, A, B, active);
        int before = match.privateView(B).hand().size();
        match.attack(A, attack.id());
        // Resolving the attack ends A's turn, so B also receives its normal
        // draw before this assertion observes the post-attack state.
        check(match.activePokemonView(B).attachedEnergyIds().isEmpty()
                && match.privateView(B).hand().size() == before + attached.size() + 1
                && attached.stream().allMatch(card -> match.privateView(B).hand().contains(card)),
            "all:true 回手没有保留每张能量的真实拥有者，或没有一次性回收全部能量"
                + "；附着=" + match.activePokemonView(B).attachedEnergyIds().size()
                + "；手牌期望=" + (before + attached.size() + 1) + "；实际=" + match.privateView(B).hand().size()
                + "；实际拥有=" + attached.stream().filter(card -> match.privateView(B).hand().contains(card)).count());
        check(!match.hasPendingCardSelection(), "all:true 回手错误产生附着能量选牌事务");
    }

    private static void energyTriggeredAllDiscardUsesServerEventQueue() {
        AbilityDefinition reaction = EnergyTriggeredAbilityTest.ability("discard-on-energy", "SELF", false, "NONE",
            "{\"type\":\"discard_energy\",\"target\":\"OWN_ACTIVE\",\"all\":true}");
        ServerMatch match = EnergyTriggeredAbilityTest.fixture(List.of(reaction), List.of(), null);
        UUID active = match.activePokemonView(EnergyTriggeredAbilityTest.A).pokemonId();
        CardInstance energy = match.privateView(EnergyTriggeredAbilityTest.A).hand().stream()
            .filter(card -> card.definitionId().equals("energy")).findFirst().orElseThrow();
        match.attachEnergy(EnergyTriggeredAbilityTest.A, energy.instanceId(), active);
        check(match.activePokemonView(EnergyTriggeredAbilityTest.A).attachedEnergyIds().isEmpty()
                && match.privateView(EnergyTriggeredAbilityTest.A).discard().contains(energy)
                && match.replayTranscript().stream().anyMatch(entry -> entry.operation().equals("已审核效果弃置场上的能量")),
            "自动特性没有经服务器事件队列弃置当前战斗位全部能量");
        check(match.triggerOrderPrompt() == null && !match.hasPendingCardSelection(),
            "自动 all:true 弃能错误向客户端发出选择提示");
    }

    private static List<CardInstance> attachTwo(ServerMatch match, UUID controller, UUID owner, UUID host) {
        CardInstance first = DynamicPassiveModifierTest.hand(match, owner, "energy");
        if (owner.equals(controller)) match.attachEnergy(controller, first.instanceId(), host);
        else match.attachEnergyByEffect(controller, owner, EffectSourceZone.HAND, first.instanceId(), owner, host);
        CardInstance second = DynamicPassiveModifierTest.hand(match, owner, "energy");
        match.attachEnergyByEffect(controller, owner, EffectSourceZone.HAND, second.instanceId(), owner, host);
        return List.of(first, second);
    }

    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("非法 all:true 附着能量定义被静默接受");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
