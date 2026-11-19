import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.field.AttackDefinition;
import dev.hua.ptcglive.engine.field.BattlefieldState;
import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.engine.field.PokemonProfile;
import dev.hua.ptcglive.rules.CardInstance;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Server-only regression for temporary attack protection scope and expiry. */
public final class AttackProtectionScopeTest {
    private static final UUID A = new UUID(0, 1101), B = new UUID(0, 1102);
    public static void main(String[] args) {
        scopeAndExpiryOnBattlefield();
        damageOnlyProtectionKeepsAttackEffects();
        fullProtectionDoesNotCancelAttackerOwnEffect();
        System.out.println("AttackProtectionScopeTest passed: 保护范围、攻击结算、公共提示和到期时点均由服务器验证");
    }
    private static void scopeAndExpiryOnBattlefield() {
        var profile = new PokemonProfile(100, 0, null, null, 0, null, 0, 1, java.util.List.of());
        var field = new BattlefieldState(A, B, Map.of("basic", profile), new ServerRandom(1));
        field.placeInitialActive(A, new CardInstance(UUID.randomUUID(), A, "basic"));
        field.placeInitialActive(B, new CardInstance(UUID.randomUUID(), B, "basic"));
        field.placeOnBench(B, new CardInstance(UUID.randomUUID(), B, "basic"));
        field.beginTurn(A);
        field.protectFromOpponentNextTurn(A, field.activeView(A).pokemonId(), B, AttackDefinition.ProtectionScope.DAMAGE_ONLY);
        if (field.attackProtection(A, B) != AttackDefinition.ProtectionScope.DAMAGE_ONLY
            || !field.activeView(A).continuousEffectZh().contains("免受攻击伤害")
            || field.activeView(A).continuousEffectZh().contains("与效果"))
            throw new AssertionError("仅伤害保护没有被服务器按范围保存并公开为公共战场信息");
        field.reduceDamageFromOpponentNextTurn(A, field.activeView(A).pokemonId(), B, 30);
        if (field.attackDamageReduction(A, field.activeView(A).pokemonId(), B) != 30
            || !field.activeView(A).continuousEffectZh().contains("减少 30"))
            throw new AssertionError("服务器没有保存并公开临时攻击减伤");
        field.beginTurn(B);
        if (field.attackProtection(A, B) != AttackDefinition.ProtectionScope.DAMAGE_ONLY
            || field.attackDamageReduction(A, field.activeView(A).pokemonId(), B) != 30)
            throw new AssertionError("保护不应在对手回合开始前过期");
        UUID protectedBench = field.benchView(B).getFirst().pokemonId();
        field.protectFromOpponentNextTurn(B, protectedBench, A, AttackDefinition.ProtectionScope.DAMAGE_AND_EFFECTS);
        if (field.attackProtection(B, protectedBench, A) != AttackDefinition.ProtectionScope.DAMAGE_AND_EFFECTS
            || field.attackProtection(B, A) != null)
            throw new AssertionError("备战宝可梦的攻击保护没有按实际目标保存");
        field.beginTurn(A);
        if (field.attackProtection(A, B) != null || field.attackDamageReduction(A, field.activeView(A).pokemonId(), B) != 0
            || !field.activeView(A).continuousEffectZh().isBlank())
            throw new AssertionError("保护必须在拥有者下个回合开始时失效");
    }
    private static void damageOnlyProtectionKeepsAttackEffects() {
        for (int seed = 0; seed < 100; seed++) {
            var match = match(seed);
            while (!match.currentPlayer().equals(A) || !match.mayAttack(A)) match.endTurn(match.currentPlayer());
            match.attack(A, "guard");
            if (!match.activePokemonView(A).continuousEffectZh().contains("免受攻击伤害")
                || match.activePokemonView(A).continuousEffectZh().contains("与效果")) continue;
            if (!match.currentPlayer().equals(B) || !match.mayAttack(B)) throw new AssertionError("保护攻击后应由对手正常进行回合");
            match.attack(B, "strike");
            if (match.lastAttack().finalDamage() != 0 || !match.activePokemonView(A).conditions().contains(dev.hua.ptcglive.engine.field.SpecialCondition.POISONED))
                throw new AssertionError("仅伤害保护必须阻止伤害、但不阻止攻击附加效果");
            return;
        }
        throw new AssertionError("没有找到触发服务器硬币保护的可复现种子");
    }
    private static void fullProtectionDoesNotCancelAttackerOwnEffect() {
        var conditions = List.of(
            new dev.hua.ptcglive.engine.field.ConditionEffect(dev.hua.ptcglive.engine.field.ConditionEffect.Target.DEFENDER,
                dev.hua.ptcglive.engine.field.SpecialCondition.POISONED),
            new dev.hua.ptcglive.engine.field.ConditionEffect(dev.hua.ptcglive.engine.field.ConditionEffect.Target.SELF,
                dev.hua.ptcglive.engine.field.SpecialCondition.BURNED));
        for (int seed = 0; seed < 100; seed++) {
            var match = match(seed, AttackDefinition.ProtectionScope.DAMAGE_AND_EFFECTS, conditions);
            while (!match.currentPlayer().equals(A) || !match.mayAttack(A)) match.endTurn(match.currentPlayer());
            match.attack(A, "guard");
            if (!match.activePokemonView(A).continuousEffectZh().contains("与效果")) continue;
            match.attack(B, "strike");
            if (match.activePokemonView(A).conditions().contains(dev.hua.ptcglive.engine.field.SpecialCondition.POISONED)
                || !match.activePokemonView(B).conditions().contains(dev.hua.ptcglive.engine.field.SpecialCondition.BURNED))
                throw new AssertionError("完整攻击保护错误阻止了攻击方自身效果，或未阻止作用于防守方的效果");
            return;
        }
        throw new AssertionError("没有找到触发服务器完整硬币保护的可复现种子");
    }
    private static ServerMatch match(int seed) {
        return match(seed, AttackDefinition.ProtectionScope.DAMAGE_ONLY, List.of(
            new dev.hua.ptcglive.engine.field.ConditionEffect(dev.hua.ptcglive.engine.field.ConditionEffect.Target.DEFENDER,
                dev.hua.ptcglive.engine.field.SpecialCondition.POISONED)));
    }
    private static ServerMatch match(int seed, AttackDefinition.ProtectionScope protectionScope,
                                     List<dev.hua.ptcglive.engine.field.ConditionEffect> strikeConditions) {
        var defs = new CardDatabase(List.of(definition("guarder"), definition("striker")));
        var guard = new AttackDefinition("guard", "测试保护", Map.of(), 0, EnergyType.COLORLESS, EffectSupport.SUPPORTED, null,
            List.of(), null, new AttackDefinition.CoinProtection(1, 1, protectionScope), List.of());
        var strike = new AttackDefinition("strike", "测试攻击", Map.of(), 40, EnergyType.COLORLESS, EffectSupport.SUPPORTED, null,
            strikeConditions);
        var combat = new CombatCatalog(Map.of(
            "guarder", new PokemonProfile(120, 0, null, null, 0, null, 0, 1, List.of(guard)),
            "striker", new PokemonProfile(120, 0, null, null, 0, null, 0, 1, List.of(strike))), Map.of());
        var result = new ServerMatch(A, B, deck(A, "guarder"), deck(B, "striker"), MatchRulesRegressionTest.RULE, defs, combat, new ServerRandom(seed));
        OpeningTestSupport.complete(result, A, B, 0);
        return result;
    }
    private static CardDefinition definition(String id) {
        return new CardDefinition(id, id, CardCategory.POKEMON, TrainerCategory.NONE, "H", Set.of("international"), true,
            false, 4, Map.of(), EffectSupport.SUPPORTED, null);
    }
    private static List<CardInstance> deck(UUID owner, String id) {
        var cards = new ArrayList<CardInstance>();
        for (int i = 0; i < 60; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, id));
        return cards;
    }
}
