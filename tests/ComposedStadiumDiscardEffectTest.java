import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.effect.EffectRegistry;
import dev.hua.ptcglive.engine.effect.EffectTrigger;
import dev.hua.ptcglive.engine.effect.TrainerProgramRules;
import dev.hua.ptcglive.rules.BundledCombatData;
import dev.hua.ptcglive.rules.CardInstance;
import dev.hua.ptcglive.rules.TrainerProfile;

/** DiscardStadiumEffect shares the existing owner-correct, public Stadium boundary. */
public final class ComposedStadiumDiscardEffectTest {
    public static void main(String[] args) {
        var profile=BundledCombatData.trainer(JsonParser.parseString("""
            {"trainerCategory":"ITEM","effects":[{"type":"discard_stadium"}]}
            """).getAsJsonObject(), EffectRegistry.standard());
        TrainerProgramRules.validate(profile.effectProgram());
        ServerMatch match=PersistentTrainerTest.game();
        CardInstance stadium=PersistentTrainerTest.hand(match,PersistentTrainerTest.A,"court");
        int normalCost=match.activeRetreatCost(PersistentTrainerTest.A);
        match.playTrainer(PersistentTrainerTest.A,stadium.instanceId(),null);
        check(match.clientView(null).stadium().equals(stadium)&&match.activeRetreatCost(PersistentTrainerTest.A)<normalCost,
            "场地夹具未正确进入共享场地区");
        match.executeEffectProgramByRule(PersistentTrainerTest.A,null,profile.effectProgram(),EffectTrigger.ON_CARD_PLAYED);
        check(match.clientView(null).stadium()==null&&match.privateView(PersistentTrainerTest.A).discard().contains(stadium)
            &&match.activeRetreatCost(PersistentTrainerTest.A)==normalCost,
            "DiscardStadiumEffect 没有清除场地、还原持续效果或归还到原所有者弃牌区");
        var before=match.clientView(PersistentTrainerTest.A);
        rejects(()->match.executeEffectProgramByRule(PersistentTrainerTest.A,null,profile.effectProgram(),EffectTrigger.ON_CARD_PLAYED));
        check(before.equals(match.clientView(PersistentTrainerTest.A)),"空场地 Effect 失败后仍改变公开状态");
        System.out.println("ComposedStadiumDiscardEffectTest passed: data-driven Stadium discard is owner-correct and atomic");
    }
    static void rejects(Runnable action) {try{action.run();}catch(IllegalArgumentException|IllegalStateException expected){return;}throw new AssertionError("空场地弃置被接受");}
    static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
