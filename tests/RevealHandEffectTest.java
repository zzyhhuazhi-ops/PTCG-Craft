import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/**
 * “Your opponent reveals their hand.”: reveal_hand pauses the card text and shows the faces of that hand on the
 * public reveal (the same overlay as a searched-card reveal). The effect's controller confirms after looking; only
 * then does the rest of the text continue. Nothing moves, and an empty hand reveals nothing.
 */
public final class RevealHandEffectTest {
    static final UUID A = BeforeDamageAttackTest.A, B = BeforeDamageAttackTest.B;
    static void check(boolean b, String reason) { BeforeDamageAttackTest.check(b, reason); }
    static final String REVEAL = "{\"type\":\"reveal_hand\",\"target\":\"OPPONENT_ACTIVE\"}";
    public static void main(String[] args) {
        afterDamage(); beforeDamage(); emptyHand(); trainer(); perCardDamage(); invalid();
        System.out.println("RevealHandEffectTest passed: 对手公开手牌，使用者看过确认后才继续结算，不移动任何卡");
    }
    static void afterDamage() {
        var a = BeforeDamageAttackTest.attack("", "", REVEAL);
        check(AttackProgramRules.unsupportedReason(a).isEmpty(), "公开手牌被拒绝：" + AttackProgramRules.unsupportedReason(a).orElse(""));
        var m = BeforeDamageAttackTest.fixture(a);
        var hand = m.privateView(B).hand().stream().map(CardInstance::definitionId).sorted().toList();
        m.attack(A, a.id());
        var reveal = m.effectCardReveal();
        check(reveal != null && reveal.confirmingPlayer().equals(A) && reveal.actor().equals(B), "没有暂停让攻击方查看对手手牌");
        check(reveal.cardIds().equals(hand) && reveal.titleZh().equals("对手公开手牌 " + hand.size() + " 张"), "公开的不是对手当前手牌");
        check(m.clientView(B).effectReveal() != null && m.clientView(A).effectReveal().cardIds().equals(hand), "双方画面都应看到公开的手牌");
        check(m.currentPlayer().equals(A) && m.activePokemonView(B).damage() == 80, "确认前攻击不应结束");
        BeforeDamageAttackTest.reject(() -> m.acknowledgeEffectReveal(B, reveal.id()));
        m.acknowledgeEffectReveal(A, reveal.id());
        check(m.effectCardReveal() == null && m.currentPlayer().equals(B), "确认后攻击没有结束");
        // B has since drawn for their turn: every revealed card is still in that hand.
        var after = new ArrayList<>(m.privateView(B).hand().stream().map(CardInstance::definitionId).toList());
        for (String id : hand) check(after.remove(id), "公开手牌不应移动任何卡");
        check(after.size() == 1, "对手手牌数量不对");
        check(m.replayTranscript().stream().anyMatch(e -> e.operation().startsWith("公开手牌：")), "回放没有记录公开的手牌");
        BeforeDamageAttackTest.reject(() -> m.acknowledgeEffectReveal(A, reveal.id()));
    }
    /** Reveal first, then damage: the damage waits for the confirmation. */
    static void beforeDamage() {
        var a = BeforeDamageAttackTest.attack(REVEAL, "", "");
        var m = BeforeDamageAttackTest.fixture(a);
        m.attack(A, a.id());
        check(m.effectCardReveal() != null && m.activePokemonView(B).damage() == 0, "伤害不应早于确认");
        m.acknowledgeEffectReveal(A, m.effectCardReveal().id());
        check(m.activePokemonView(B).damage() == 80 && m.currentPlayer().equals(B), "确认后没有造成伤害并结束攻击");
    }
    static void emptyHand() {
        var a = BeforeDamageAttackTest.attack("", "", REVEAL);
        var m = BeforeDamageAttackTest.fixture(a);
        m.shuffleHandIntoDeckUpToByEffect(A, B);
        check(m.privateView(B).hand().isEmpty(), "夹具没有清空对手手牌");
        m.attack(A, a.id());
        check(m.effectCardReveal() == null && m.currentPlayer().equals(B), "对手没有手牌时不应暂停");
    }
    /** Hand Scope: “Your opponent reveals their hand.” */
    static void trainer() {
        var program = new EffectProgram(List.of(BeforeDamageAttackTest.binding(EffectTrigger.ON_CARD_PLAYED, REVEAL)));
        TrainerProgramRules.validate(program);
        var m = EnergyTriggeredAbilityTest.fixture(List.of(), List.of(), REVEAL);
        var item = m.privateView(EnergyTriggeredAbilityTest.A).hand().stream().filter(c -> c.definitionId().equals("trainer")).findFirst().orElseThrow();
        m.playTrainer(EnergyTriggeredAbilityTest.A, item.instanceId(), null);
        var reveal = m.effectCardReveal();
        check(reveal != null && reveal.confirmingPlayer().equals(EnergyTriggeredAbilityTest.A), "训练家没有公开对手手牌");
        m.acknowledgeEffectReveal(EnergyTriggeredAbilityTest.A, reveal.id());
        check(!m.hasPendingEffectChoice() && m.privateView(EnergyTriggeredAbilityTest.A).discard().stream().anyMatch(c -> c.instanceId().equals(item.instanceId())),
            "确认后训练家没有结算完并放入弃牌区");
    }
    /** “Your opponent reveals their hand. This attack does 10 more damage for each Trainer card you find there.” */
    static void perCardDamage() {
        for (String filter : List.of("", ",\"trainer_category\":\"TOOL\"")) {
            var a = BeforeDamageAttackTest.attack(REVEAL, "{\"type\":\"damage_per_hand_card\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":10,"
                + "\"card_category\":\"TRAINER\"" + filter + "}", "");
            check(AttackProgramRules.unsupportedReason(a).isEmpty(), "按对手手牌张数追加伤害被拒绝：" + AttackProgramRules.unsupportedReason(a).orElse(""));
            var m = BeforeDamageAttackTest.fixture(a);
            // A 10-card hand keeps the result below the defender's 300 HP.
            m.shuffleHandIntoDeckUpToByEffect(A, B); m.drawCardsByEffect(A, B, 10);
            long trainers = m.privateView(B).hand().stream().filter(c -> filter.isEmpty() ? Set.of("tool", "stadium").contains(c.definitionId())
                : c.definitionId().equals("tool")).count();
            check(trainers > 0, "夹具对手手牌里没有训练家卡");
            m.attack(A, a.id());
            m.acknowledgeEffectReveal(A, m.effectCardReveal().id());
            check(m.activePokemonView(B).damage() == (40 + 10 * trainers) * 2, "伤害没有按对手手牌里的训练家卡张数追加：" + m.activePokemonView(B).damage());
        }
        for (String bad : List.of("{\"type\":\"damage_per_hand_card\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":30}",
            "{\"type\":\"damage_per_hand_card\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":30,\"card_category\":\"ENERGY\",\"trainer_category\":\"ITEM\"}",
            "{\"type\":\"damage_per_hand_card\",\"target\":\"OPPONENT_BENCH\",\"amount\":30,\"card_category\":\"TRAINER\"}",
            "{\"type\":\"damage_per_hand_card\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":25,\"card_category\":\"TRAINER\"}"))
            BeforeDamageAttackTest.reject(() -> AttackProgramRules.validate(BeforeDamageAttackTest.attack(REVEAL, bad, "")));
    }
    static void invalid() {
        for (String bad : List.of("{\"type\":\"reveal_hand\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":1}",
            "{\"type\":\"reveal_hand\",\"target\":\"OWN_ACTIVE\"}", "{\"type\":\"reveal_hand\",\"target\":\"SELECT_OPPONENT_POKEMON\"}"))
            BeforeDamageAttackTest.reject(() -> AttackProgramRules.validate(BeforeDamageAttackTest.attack("", "", bad)));
    }
}
