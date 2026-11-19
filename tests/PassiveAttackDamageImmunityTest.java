import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Passive attack-damage immunity blocks damage only, not damage-counter effects or a supported attack's other effects. */
public final class PassiveAttackDamageImmunityTest {
    private static final UUID A = new UUID(0, 6341), B = new UUID(0, 6342);

    public static void main(String[] args) {
        ServerMatch match = fixture();
        UUID protectedPokemon = match.activePokemonView(B).pokemonId();
        if (!match.abilityOptions(B).isEmpty()) throw new AssertionError("攻击伤害免疫被动特性被错误暴露为可点击操作");
        match.placeDamageCountersByEffect(A, B, protectedPokemon, 2);
        if (match.activePokemonView(B).damage() != 20)
            throw new AssertionError("攻击伤害免疫错误阻止了非攻击伤害指示物效果");
        match.attack(A, "strike");
        if (match.lastAttack().finalDamage() != 0)
            throw new AssertionError("攻击伤害绕过了目标的被动免疫");
        if (!match.activePokemonView(B).conditions().contains(SpecialCondition.POISONED))
            throw new AssertionError("仅免攻击伤害的被动特性错误阻止了攻击附加异常状态");
        bypassDeclaredDefenderDamageEffects();
        System.out.println("PassiveAttackDamageImmunityTest passed: passive attack immunity blocks only attack damage on the actual target");
    }

    private static void bypassDeclaredDefenderDamageEffects() {
        ServerMatch match = fixture(true);
        match.attack(A, "strike");
        // The attached Poison adds its normal checkup damage after the attack;
        // assert the audited attack damage separately from the later checkup.
        if (match.lastAttack().finalDamage() != 50 || match.activePokemonView(B).damage() < 50)
            throw new AssertionError("明确忽略对方战斗宝可梦防伤效果的攻击没有穿透被动防伤");
    }

    private static ServerMatch fixture() { return fixture(false); }
    private static ServerMatch fixture(boolean ignoresDefenderDamageEffects) {
        CardDefinition attacker = definition("attacker"), immune = definition("immune");
        AbilityDefinition immunity = new AbilityDefinition("damage-shield", "攻击伤害免疫",
            new AbilityEffect(AbilityEffect.Opcode.PASSIVE_PREVENT_OPPONENT_ATTACK_DAMAGE, 0), EffectSupport.SUPPORTED, null,
            AbilityDefinition.Kind.PASSIVE);
        AttackDefinition strike = new AttackDefinition("strike", "测试攻击", Map.of(), 50, EnergyType.GRASS, EffectSupport.SUPPORTED, null,
            List.of(new ConditionEffect(ConditionEffect.Target.DEFENDER, SpecialCondition.POISONED)), null, null, List.of(),
            AttackDefinition.DamageTarget.OPPONENT_ACTIVE, 0, 1, false, 0, 0, false, 0, ignoresDefenderDamageEffects, false);
        CombatCatalog combat = new CombatCatalog(Map.of(
            "attacker", new PokemonProfile(120, 0, null, null, 0, null, 0, 1, List.of(strike)),
            "immune", new PokemonProfile(120, 0, null, null, 0, null, 0, 1, List.of(), List.of(immunity))), Map.of());
        for (long seed = 1; seed < 5000; seed++) {
            ServerMatch match = new ServerMatch(A, B, deck(A, "attacker"), deck(B, "immune"), MatchRulesRegressionTest.RULE,
                new CardDatabase(List.of(attacker, immune)), combat, new ServerRandom(seed));
            OpeningTestSupport.complete(match, A, B, 0);
            for (int turns = 0; turns < 4; turns++) {
                if (match.currentPlayer().equals(A) && match.mayAttack(A)) return match;
                match.endTurn(match.currentPlayer());
            }
        }
        throw new AssertionError("无法建立甲方攻击合法的确定性伤害免疫测试对局");
    }
    private static CardDefinition definition(String id) {
        return new CardDefinition(id, id, CardCategory.POKEMON, TrainerCategory.NONE, "H", Set.of("international"), true,
            false, 60, Map.of(), EffectSupport.SUPPORTED, null);
    }
    private static List<CardInstance> deck(UUID owner, String id) {
        var cards = new ArrayList<CardInstance>();
        for (int i = 0; i < 60; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, id));
        return cards;
    }
}
