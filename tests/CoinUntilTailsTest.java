import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import java.util.*;

/**
 * “Flip a coin until you get tails.”: coin_flip until=TAILS flips one server
 * coin at a time; the first tails ends the sequence. The result feeds
 * repeat_per_heads for both attack damage and after-damage effects;
 * every sequence includes its first actual tails, including runs longer than 20 heads.
 */
public final class CoinUntilTailsTest {
    static final UUID A = ResumableAbilityTest.A, B = ResumableAbilityTest.B;
    static final String UNTIL = "{\"type\":\"coin_flip\",\"until\":\"TAILS\",\"effects\":[{\"type\":\"repeat_per_heads\",\"effects\":[%s]}]}";
    public static void main(String[] args) {
        damagePerHeads(); afterDamagePerHeads(); longSequence(); schema();
        System.out.println("CoinUntilTailsTest passed: until-tails flips are server-owned, stop at the actual tails and drive per-heads effects");
    }
    static dev.hua.ptcglive.engine.field.AttackDefinition damageAttack() {
        return dev.hua.ptcglive.rules.BundledCombatData.attack(JsonParser.parseString("{\"id\":\"until\",\"nameZh\":\"掷到反面\",\"energyCost\":{},"
            + "\"baseDamage\":0,\"damageType\":\"GRASS\",\"effectSupport\":\"SUPPORTED\",\"effects\":["
            + UNTIL.formatted("{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":30}") + "]}").getAsJsonObject(), EffectRegistry.standard());
    }
    static void damagePerHeads() {
        var a = damageAttack();
        check(AttackProgramRules.unsupportedReason(a).isEmpty(), "掷到反面的伤害程序被拒绝：" + AttackProgramRules.unsupportedReason(a));
        Set<Integer> seen = new HashSet<>();
        for (int seed = 0; seed < 40; seed++) {
            var m = SharedUsageLimitTest.attackFixture(seed, List.of(), List.of(a));
            int before = m.randomTranscript().size();
            m.attack(A, a.id());
            int flips = m.randomTranscript().size() - before, damage = m.activePokemonView(B).damage();
            check(flips >= 1 && damage == 30 * (flips - 1), "伤害不等于 30 × 正面数（掷 " + flips + " 次，伤害 " + damage + "）");
            seen.add(flips - 1);
        }
        check(seen.contains(0) && seen.stream().anyMatch(h -> h >= 2), "多个种子没有覆盖 0 次和多次正面");
    }
    static void afterDamagePerHeads() {
        var a = ResumableAttackTest.attack(UNTIL.formatted("{\"type\":\"discard_top_deck\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":1}"));
        check(AttackProgramRules.unsupportedReason(a).isEmpty(), "攻击后掷到反面被拒绝：" + AttackProgramRules.unsupportedReason(a));
        for (int seed = 0; seed < 20; seed++) {
            var m = SharedUsageLimitTest.attackFixture(seed, List.of(), List.of(a));
            int before = m.randomTranscript().size();
            m.attack(A, a.id());
            int flips = m.randomTranscript().size() - before;
            check(m.privateView(B).discard().size() == flips - 1, "每个正面没有各弃置对手牌库上方 1 张");
        }
    }
    static void longSequence() {
        var effect=EffectRegistry.standard().parse(JsonParser.parseString(UNTIL.formatted("{\"type\":\"draw\",\"amount\":1}")).getAsJsonObject());
        var policy=CoinFlipPolicy.from(effect);check(policy.untilTails()&&policy.maximum()==0,"Until tails must have no invented fixed maximum");
        reject(()->policy.resolvedFlips(null));
        for(int heads:List.of(0,21,481,1000)) {
            int[] calls={0};
            var scripted=(EffectExecutionContext)java.lang.reflect.Proxy.newProxyInstance(EffectExecutionContext.class.getClassLoader(),new Class<?>[]{EffectExecutionContext.class},(proxy,method,args)->{
                if(method.getName().equals("canChooseNextCoinOutcome"))return false;
                if(method.getName().equals("flipCoins")){check((int)args[1]==1,"Until tails must request one actual flip");return new EffectExecutionContext.CoinResult(1,calls[0]++<heads?1:0);}throw new UnsupportedOperationException(method.getName());
            });
            var result=policy.flip(scripted,effect);check(calls[0]==heads+1&&result.flips()==heads+1&&result.heads()==heads,"Sequence truncated before its tails");
        }
    }
    static void schema() {
        var registry = EffectRegistry.standard();
        for (String bad : List.of(
            "{\"type\":\"coin_flip\",\"until\":\"HEADS\",\"effects\":[{\"type\":\"draw\",\"amount\":1}]}",
            "{\"type\":\"coin_flip\",\"until\":\"TAILS\",\"flips\":2,\"effects\":[{\"type\":\"draw\",\"amount\":1}]}",
            "{\"type\":\"coin_flip\",\"until\":\"TAILS\",\"reroll\":\"TAILS_ONCE\",\"effects\":[{\"type\":\"draw\",\"amount\":1}]}"))
            reject(() -> registry.parse(JsonParser.parseString(bad).getAsJsonObject()));
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static void reject(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("非法的掷到反面数据被接受");
    }
}
