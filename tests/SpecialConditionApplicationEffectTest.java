import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.field.SpecialCondition;
import java.util.List;

/** Application keeps the official positional-condition rule on the server. */
public final class SpecialConditionApplicationEffectTest {
    public static void main(String[] args) {
        ServerMatch match = PersistentTrainerTest.game();
        match.applySpecialConditionByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, SpecialCondition.POISONED);
        match.applySpecialConditionByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, SpecialCondition.ASLEEP);
        match.applySpecialConditionByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, SpecialCondition.CONFUSED);
        List<SpecialCondition> conditions = match.activePokemonView(PersistentTrainerTest.B).conditions();
        if (!conditions.contains(SpecialCondition.POISONED) || !conditions.contains(SpecialCondition.CONFUSED)
            || conditions.contains(SpecialCondition.ASLEEP))
            throw new AssertionError("异常状态应用没有保留中毒或替换互斥的睡眠状态");
        try { match.applySpecialConditionByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, SpecialCondition.POISONED); }
        catch (IllegalStateException expected) {
            if (!match.replayTranscript().getLast().operation().equals("已审核效果附加异常状态"))
                throw new AssertionError("异常状态效果没有进入服务器回放");
            System.out.println("SpecialConditionApplicationEffectTest passed: server-only application keeps positional exclusivity and rejects no-op");
            return;
        }
        throw new AssertionError("重复施加同一异常状态被静默接受");
    }
}
