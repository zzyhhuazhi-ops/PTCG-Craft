import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.effect.EffectRegistry;
import dev.hua.ptcglive.engine.field.AttackDefinition;
import dev.hua.ptcglive.engine.field.EnergyProfile;
import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.rules.BundledCombatData;
import dev.hua.ptcglive.rules.CardInstance;
import java.util.List;
import java.util.UUID;

/** Verifies live Colorless-only attack-cost modifiers through the ordinary attack-payment path. */
public final class AttackCostModifierTest {
    private static final UUID A = DynamicPassiveModifierTest.A;
    private static final UUID B = DynamicPassiveModifierTest.B;

    public static void main(String[] args) {
        liveToolAndStadiumSources();
        attachedSpecialEnergyMovesWithItsHost();
        typedRequirementsAreNeverReduced();
        invalidProgramsAreRejected();
        System.out.println("AttackCostModifierTest passed: live Colorless attack-cost modifiers use unified payment");
    }

    private static AttackDefinition attack() {
        return BundledCombatData.attack(JsonParser.parseString("""
            {"id":"cost-hit","nameZh":"费用测试","energyCost":{"GRASS":1,"COLORLESS":1},
             "baseDamage":40,"damageType":"GRASS","effectSupport":"SUPPORTED",
             "ignoresActiveDefenderDamageEffects":false}
            """).getAsJsonObject(), EffectRegistry.standard());
    }

    private static ServerMatch fixture(String toolEffects, String stadiumEffects, EnergyProfile special) {
        return DynamicPassiveModifierTest.fixture(
            DynamicPassiveModifierTest.trainer("TOOL", toolEffects),
            DynamicPassiveModifierTest.trainer("STADIUM", stadiumEffects), special, attack());
    }

    private static void liveToolAndStadiumSources() {
        var m = fixture("{\"type\":\"attack_cost_modifier\",\"amount\":-1}",
            "{\"type\":\"attack_cost_modifier\",\"target\":\"ALL_OPPONENT_POKEMON\",\"amount\":1}",
            EnergyProfile.fixed(EnergyType.COLORLESS, 1));
        UUID active = m.activePokemonView(A).pokemonId();
        CardInstance grass = hand(m, A, "energy");
        m.attachEnergy(A, grass.instanceId(), active);
        check(!m.canPayActiveAttackCost(A, attack()), "尚缺无色能量时仍可攻击");

        m.playTrainer(A, hand(m, A, "tool").instanceId(), active);
        check(m.canPayActiveAttackCost(A, attack()), "工具未减少己方攻击的无色费用");
        m.attack(A, "cost-hit");
        check(m.activePokemonView(B).damage() == 80, "费用验证未接入实际攻击声明");

        m.playTrainer(B, hand(m, B, "stadium").instanceId(), null);
        m.endTurn(B);
        check(!m.canPayActiveAttackCost(A, attack()), "对手场地加费未即时生效");
        m.discardStadiumByEffect(A);
        check(m.canPayActiveAttackCost(A, attack()), "场地离场后攻击费用未重算");
        m.disableToolEffectsByEffect(A, A, active);
        check(!m.canPayActiveAttackCost(A, attack()), "工具失效后攻击费用未重算");
    }

    private static void attachedSpecialEnergyMovesWithItsHost() {
        var special = BundledCombatData.specialEnergy(JsonParser.parseString("""
            {"energyUnits":[],"effects":[{"type":"attack_cost_modifier","amount":-1}]}
            """).getAsJsonObject(), EffectRegistry.standard());
        var m = fixture("{\"type\":\"retreat_cost_modifier\",\"amount\":-1}",
            "{\"type\":\"retreat_cost_modifier\",\"target\":\"ALL_OWN_POKEMON\",\"amount\":-1}", special);
        UUID active = m.activePokemonView(A).pokemonId();
        UUID bench = m.benchPokemonView(A).getFirst().pokemonId();
        CardInstance grass = hand(m, A, "energy");
        m.attachEnergy(A, grass.instanceId(), active);
        check(!m.canPayActiveAttackCost(A, attack()), "测试前的攻击费用状态错误");
        m.endTurn(A);
        m.endTurn(B);
        CardInstance card = hand(m, A, "special");
        m.attachEnergy(A, card.instanceId(), active);
        check(m.canPayActiveAttackCost(A, attack()), "特殊能量没有减少宿主无色费用");
        m.moveAttachedEnergyByEffect(A, A, active, A, bench, List.of(card.instanceId()));
        check(!m.canPayActiveAttackCost(A, attack()), "能量移走后旧宿主仍保留费用修正");
        m.moveAttachedEnergyByEffect(A, A, active, A, bench, List.of(grass.instanceId()));
        m.switchActiveByEffect(A, A, bench);
        check(m.canPayActiveAttackCost(A, attack()), "能量移动后的新宿主没有费用修正");
    }

    private static void typedRequirementsAreNeverReduced() {
        var m = fixture("{\"type\":\"attack_cost_modifier\",\"amount\":-3}",
            "{\"type\":\"retreat_cost_modifier\",\"target\":\"ALL_OWN_POKEMON\",\"amount\":-1}",
            EnergyProfile.fixed(EnergyType.COLORLESS, 1));
        UUID active = m.activePokemonView(A).pokemonId();
        m.playTrainer(A, hand(m, A, "tool").instanceId(), active);
        check(!m.canPayActiveAttackCost(A, attack()), "无色费用减免错误地消除了草能量需求");
    }

    private static void invalidProgramsAreRejected() {
        reject(() -> DynamicPassiveModifierTest.trainer("TOOL", "{\"type\":\"attack_cost_modifier\",\"amount\":0}"));
        reject(() -> DynamicPassiveModifierTest.trainer("TOOL", "{\"type\":\"attack_cost_modifier\",\"amount\":-1,\"direction\":\"DEALT\"}"));
        reject(() -> DynamicPassiveModifierTest.trainer("STADIUM", "{\"type\":\"attack_cost_modifier\",\"amount\":-1}"));
    }

    private static CardInstance hand(ServerMatch m, UUID owner, String id) {
        return DynamicPassiveModifierTest.hand(m, owner, id);
    }
    private static void reject(Runnable action) { DynamicPassiveModifierTest.reject(action); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
