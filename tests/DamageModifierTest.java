import dev.hua.ptcglive.engine.field.AttackDefinition;
import dev.hua.ptcglive.engine.field.DamageContext;
import dev.hua.ptcglive.engine.field.DamageModifier;
import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.rules.EffectSupport;
import java.util.List;
import java.util.Map;

/** Regression coverage for the fail-closed, public-fact damage modifier data model. */
public final class DamageModifierTest {
    public static void main(String[] args) {
        publicContextBases();
        var attack = new AttackDefinition("counter-hit", "测试伤害指示物攻击", Map.of(), 20, EnergyType.COLORLESS,
            EffectSupport.SUPPORTED, null, List.of(), null, null,
            List.of(new DamageModifier(DamageModifier.Basis.DEFENDER_DAMAGE_COUNTERS, 10, 3)));
        if (attack.resolveDamageModifiers(0) != 0 || attack.resolveDamageModifiers(20) != 20 || attack.resolveDamageModifiers(80) != 30)
            throw new AssertionError("伤害指示物修正或上限未按数据结算");
        var reduction = new AttackDefinition("counter-reduce", "测试减伤", Map.of(), 20, EnergyType.COLORLESS,
            EffectSupport.SUPPORTED, null, List.of(), null, null,
            List.of(new DamageModifier(DamageModifier.Basis.DEFENDER_DAMAGE_COUNTERS, -10, 0)));
        if (reduction.resolveDamageModifiers(30) != -30) throw new AssertionError("负伤害修正未保留给服务器最终伤害下限处理");
        try { new DamageModifier(DamageModifier.Basis.DEFENDER_DAMAGE_COUNTERS, 5, 0); throw new AssertionError("非十的倍数伤害修正被接受"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("DamageModifierTest passed: 公共战场事实、伤害指示物修正、上限和非法数据拒绝均已验证");
    }
    private static void publicContextBases() {
        DamageContext context = new DamageContext(20, 30, 2, 5, 4, 1, 2, 1, 3, 4, 2, 1, 6, 7, 8, 9, 10, 11, 12, 13);
        if (new DamageModifier(DamageModifier.Basis.ATTACKER_DAMAGE_COUNTERS, 10, 0).resolve(context) != 20
            || new DamageModifier(DamageModifier.Basis.DEFENDER_DAMAGE_COUNTERS, 10, 0).resolve(context) != 30
            || new DamageModifier(DamageModifier.Basis.ATTACKER_BENCH_SIZE, 10, 0).resolve(context) != 20
            || new DamageModifier(DamageModifier.Basis.DEFENDER_BENCH_SIZE, 10, 3).resolve(context) != 30
            || new DamageModifier(DamageModifier.Basis.ATTACKER_PRIZES_REMAINING, -10, 0).resolve(context) != -40
            || new DamageModifier(DamageModifier.Basis.DEFENDER_PRIZES_REMAINING, 10, 0).resolve(context) != 10
            || new DamageModifier(DamageModifier.Basis.ATTACKER_SPECIAL_CONDITION_COUNT, 10, 0).resolve(context) != 20
            || new DamageModifier(DamageModifier.Basis.DEFENDER_SPECIAL_CONDITION_COUNT, 10, 0).resolve(context) != 10
            || new DamageModifier(DamageModifier.Basis.ATTACKER_ATTACHED_ENERGY_COUNT, 10, 0).resolve(context) != 30
            || new DamageModifier(DamageModifier.Basis.DEFENDER_ATTACHED_ENERGY_COUNT, 10, 0).resolve(context) != 40
            || new DamageModifier(DamageModifier.Basis.ATTACKER_ATTACHED_TOOL_COUNT, 10, 0).resolve(context) != 20
            || new DamageModifier(DamageModifier.Basis.DEFENDER_ATTACHED_TOOL_COUNT, 10, 0).resolve(context) != 10
            || new DamageModifier(DamageModifier.Basis.ATTACKER_HAND_SIZE, 10, 0).resolve(context) != 60
            || new DamageModifier(DamageModifier.Basis.DEFENDER_HAND_SIZE, 10, 0).resolve(context) != 70
            || new DamageModifier(DamageModifier.Basis.ATTACKER_DECK_SIZE, 10, 0).resolve(context) != 80
            || new DamageModifier(DamageModifier.Basis.DEFENDER_DECK_SIZE, 10, 0).resolve(context) != 90
            || new DamageModifier(DamageModifier.Basis.ATTACKER_DISCARD_SIZE, 10, 0).resolve(context) != 100
            || new DamageModifier(DamageModifier.Basis.DEFENDER_DISCARD_SIZE, 10, 0).resolve(context) != 110
            || new DamageModifier(DamageModifier.Basis.ATTACKER_LOST_ZONE_SIZE, 10, 0).resolve(context) != 120
            || new DamageModifier(DamageModifier.Basis.DEFENDER_LOST_ZONE_SIZE, 10, 0).resolve(context) != 130)
            throw new AssertionError("所有已声明的公共伤害修正依据必须由同一个服务器快照结算");
        try { new DamageContext(1, 0, 0, 0, 0, 0); throw new AssertionError("非伤害指示物数值被接受"); }
        catch (IllegalArgumentException expected) { }
        // MatchSetupRules may version historical/custom games with more than
        // six Prizes. Damage modifiers therefore read the server snapshot,
        // instead of hard-coding the current Standard prize count.
        if (new DamageModifier(DamageModifier.Basis.ATTACKER_PRIZES_REMAINING, 10, 0)
            .resolve(new DamageContext(0, 0, 0, 0, 12, 0)) != 120)
            throw new AssertionError("版本化奖赏数量未传入伤害上下文");
    }
}
