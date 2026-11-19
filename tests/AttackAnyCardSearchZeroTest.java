import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/**
 * Official rule (TPCi Rules Team rulings on Aura Jab and Photon Cord): only
 * attacks may choose zero for an “up to” effect. An attack's “search your
 * deck for up to N cards” may take none; Trainer and Ability text searching
 * for any card must still take at least one when the deck is not empty.
 */
public final class AttackAnyCardSearchZeroTest {
    static final UUID A = ResumableAbilityTest.A, B = ResumableAbilityTest.B;
    static final String SEARCH = "{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":2,\"minimum\":0}";
    public static void main(String[] args) {
        attackMayTakeNone(); attackMayTakeUpToMaximum(); trainerAndAbilityStillTakeOne();
        System.out.println("AttackAnyCardSearchZeroTest passed: attack \"up to\" any-card search may take zero; Trainers/Abilities may not");
    }
    static void attackMayTakeNone() {
        var a = ResumableAttackTest.attack(SEARCH);
        check(AttackProgramRules.unsupportedReason(a).isEmpty(), "招式的任意卡“最多”检索被拒绝：" + AttackProgramRules.unsupportedReason(a));
        var m = ResumableAttackTest.fixture(a); int hand = m.privateView(A).hand().size(), deck = m.privateView(A).deck().size(), turn = m.turnNumber();
        m.attack(A, a.id());
        var prompt = m.effectCardPrompt(A);
        rejects(() -> m.submitEffectCards(A, prompt.resolutionId(), prompt.step(),
            prompt.candidates().stream().limit(3).map(CardInstance::instanceId).toList()));
        m.submitEffectCards(A, prompt.resolutionId(), prompt.step(), List.of());
        check(m.privateView(A).hand().size() == hand && m.privateView(A).deck().size() == deck, "选 0 张时手牌或牌库数量改变");
        check(m.currentPlayer().equals(B) && m.turnNumber() == turn + 1, "选 0 张后攻击没有结束");
    }
    static void attackMayTakeUpToMaximum() {
        var a = ResumableAttackTest.attack(SEARCH);
        var m = ResumableAttackTest.fixture(a); int hand = m.privateView(A).hand().size();
        m.attack(A, a.id());
        var prompt = m.effectCardPrompt(A);
        m.submitEffectCards(A, prompt.resolutionId(), prompt.step(), prompt.candidates().stream().limit(2).map(CardInstance::instanceId).toList());
        check(m.privateView(A).hand().size() == hand + 2, "选满 2 张时没有加入手牌");
    }
    static void trainerAndAbilityStillTakeOne() {
        var trainer = BundledCombatData.trainer(JsonParser.parseString("{\"trainerCategory\":\"ITEM\",\"effects\":[" + SEARCH + "]}").getAsJsonObject(),
            EffectRegistry.standard());
        check(TrainerProgramRules.unsupportedReason(trainer.effectProgram()).isPresent(), "训练家的任意卡检索可以选 0 张，不符合规则书");
        var program = new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ONCE_PER_TURN, List.of(),
            List.of(EffectRegistry.standard().parse(JsonParser.parseString(SEARCH).getAsJsonObject())))));
        check(ResumableProgramRules.unsupportedReason(program, true).isPresent(), "特性的任意卡检索可以选 0 张，不符合规则书");
        var filtered = BundledCombatData.trainer(JsonParser.parseString("{\"trainerCategory\":\"ITEM\",\"effects\":["
            + "{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":2,\"minimum\":0,\"card_category\":\"POKEMON\"}]}").getAsJsonObject(), EffectRegistry.standard());
        check(TrainerProgramRules.unsupportedReason(filtered.effectProgram()).isEmpty(), "指定种类的检索仍应允许找不到（选 0 张）");
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("超出检索数量的选择被接受");
    }
}
