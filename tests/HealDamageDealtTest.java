import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.AttackDefinition;
import dev.hua.ptcglive.rules.BundledCombatData;
import java.util.*;

/**
 * “Heal from this Pokémon the same amount of damage you did to your opponent's
 * Active Pokémon.”: heal mode DAMAGE_DEALT reads the damage this attack actually
 * placed on the opponent's Active Pokémon, after every damage step.
 */
public final class HealDamageDealtTest {
    static final UUID A = ResumableAbilityTest.A, B = ResumableAbilityTest.B;
    static final String HEAL = "{\"type\":\"heal\",\"target\":\"SELF\",\"mode\":\"DAMAGE_DEALT\"}";
    public static void main(String[] args) {
        healsDealtDamage(); cappedByOwnDamage(); onlyAfterAttack(); schema();
        System.out.println("HealDamageDealtTest passed: healing equal to the damage dealt reads the server's attack result");
    }
    static AttackDefinition attack(int damage) {
        return BundledCombatData.attack(JsonParser.parseString("{\"id\":\"drain\",\"nameZh\":\"吸取\",\"energyCost\":{},\"baseDamage\":" + damage
            + ",\"damageType\":\"GRASS\",\"effectSupport\":\"SUPPORTED\",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[" + HEAL + "]}]}")
            .getAsJsonObject(), EffectRegistry.standard());
    }
    static void healsDealtDamage() {
        var a = attack(60);
        check(AttackProgramRules.unsupportedReason(a).isEmpty(), "攻击后按伤害回复被拒绝：" + AttackProgramRules.unsupportedReason(a));
        var m = SharedUsageLimitTest.attackFixture(3, List.of(), List.of(a));
        m.placeDamageCountersByEffect(A, A, m.activePokemonView(A).pokemonId(), 10);
        m.attack(A, a.id());
        check(m.activePokemonView(B).damage() == 60 && m.lastAttack().finalDamage() == 60, "攻击伤害不对");
        check(m.activePokemonView(A).damage() == 40, "没有回复与造成伤害相同的 60");
    }
    static void cappedByOwnDamage() {
        var a = attack(90);
        var m = SharedUsageLimitTest.attackFixture(3, List.of(), List.of(a));
        m.placeDamageCountersByEffect(A, A, m.activePokemonView(A).pokemonId(), 3);
        m.attack(A, a.id());
        check(m.activePokemonView(A).damage() == 0, "回复量应以自身伤害为上限");
    }
    static void onlyAfterAttack() {
        var trainer = BundledCombatData.trainer(JsonParser.parseString("{\"trainerCategory\":\"ITEM\",\"effects\":["
            + "{\"type\":\"heal\",\"target\":\"OWN_ACTIVE\",\"mode\":\"DAMAGE_DEALT\"}]}").getAsJsonObject(), EffectRegistry.standard());
        check(TrainerProgramRules.unsupportedReason(trainer.effectProgram()).isPresent(), "训练家不能读取攻击伤害");
        var onAttack = BundledCombatData.attack(JsonParser.parseString("{\"id\":\"early\",\"nameZh\":\"提前\",\"energyCost\":{},\"baseDamage\":30,"
            + "\"damageType\":\"GRASS\",\"effectSupport\":\"SUPPORTED\",\"effects\":[" + HEAL + "]}").getAsJsonObject(), EffectRegistry.standard());
        check(AttackProgramRules.unsupportedReason(onAttack).isPresent(), "伤害计算阶段不能读取尚未造成的伤害");
    }
    static void schema() {
        var registry = EffectRegistry.standard();
        for (String bad : List.of(
            "{\"type\":\"heal\",\"target\":\"SELF\",\"mode\":\"DAMAGE_DEALT\",\"amount\":30}",
            "{\"type\":\"heal\",\"target\":\"SELF\"}"))
            reject(() -> registry.parse(JsonParser.parseString(bad).getAsJsonObject()));
        // Other heal parameters keep their existing per-entry-point validation.
        var unknownMode = registry.parse(JsonParser.parseString("{\"type\":\"heal\",\"target\":\"SELF\",\"mode\":\"EVERYTHING\",\"amount\":30}").getAsJsonObject());
        reject(() -> HealingEffect.from(unknownMode));
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static void reject(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("非法的回复数据被接受");
    }
}
