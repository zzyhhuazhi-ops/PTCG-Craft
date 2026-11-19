import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.rules.CardInstance;
import java.util.*;

/** Real JSON/session path: private card selection is part of the same suspended Trainer resolution. */
public final class ResumableZoneTrainerTest {
    private static final UUID A = new UUID(0, 7561), B = new UUID(0, 7562), WATCHER = new UUID(0, 7599);
    public static void main(String[] args) {
        searchDiscardRecover(); authorityAndPrivacy(); emptyAndPartialResolution(); nestedChoiceAndKnockout();
        nestedCoinsAndPractice(); filteredFailureAndLimits(); invalidDataIsAtomic();
        System.out.println("ResumableZoneTrainerTest passed: private multi-step cards, refresh, stale/foreign rejection, partial effects and KO boundary");
    }
    private static void searchDiscardRecover() {
        var m = fixture("""
            {"type":"draw","amount":1},
            {"type":"search_deck","target":"DECK","amount":2,"minimum":2,"card_category":"POKEMON"},
            {"type":"discard","target":"HAND","amount":2,"minimum":2,"card_category":"POKEMON"},
            {"type":"search_discard_pile","target":"DISCARD","amount":2,"minimum":2,"destination":"HAND"},
            {"type":"draw","amount":1}
            """);
        var source = trainer(m); int hand = m.privateView(A).hand().size();
        m.playTrainer(A, source.instanceId(), null);
        var first = m.effectCardPrompt(A); var found = first.candidates().subList(0, 2);
        check(m.privateView(A).hand().size() == hand && first.minimum() == 2, "前缀或最低张数错误");
        m.submitEffectCards(A, first.resolutionId(), first.step(), ids(found));
        var second = m.effectCardPrompt(A);
        check(second.step() > first.step() && second.resolutionId().equals(first.resolutionId()), "续接身份错误");
        check(second.candidates().containsAll(found) && !second.candidates().contains(source), "选牌未反映上一步或包含结算中训练家");
        m.submitEffectCards(A, second.resolutionId(), second.step(), ids(found));
        var third = m.effectCardPrompt(A);
        check(third.candidates().containsAll(found) && !third.candidates().contains(source), "弃牌取回候选错误");
        m.submitEffectCards(A, third.resolutionId(), third.step(), ids(found));
        check(!m.hasPendingEffectChoice() && m.privateView(A).hand().size() == hand + 3
            && m.privateView(A).discard().contains(source), "整卡多区域结算错误");
        rejects(() -> m.submitEffectCards(A, first.resolutionId(), first.step(), ids(found)));
    }
    private static void authorityAndPrivacy() {
        var m = fixture("{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":2,\"minimum\":1}");
        var session = MatchSession.multiplayer(m, A, B); session.addSpectator(WATCHER);
        m.playTrainer(A, trainer(m).instanceId(), null);
        var prompt = m.effectCardPrompt(A); var snapshot = session.view(A, A);
        check(snapshot.game().effectSelection().equals(prompt), "拥有者未收到私有候选");
        var reachable = new HashSet<String>();
        int pages = dev.hua.ptcglive.view.EffectCardPage.from(snapshot, 0).pages();
        for (int page = 0; page < pages; page++) for (var card : dev.hua.ptcglive.view.EffectCardPage.from(snapshot, page).cards()) {
            check(reachable.add(card.key()), "选牌分页重复卡片");
            check(snapshot.options().stream().anyMatch(o -> o.cardKeys().contains(card.key())), "卡图没有对应的服务器选牌意图");
        }
        check(reachable.size() == prompt.candidates().size()
            && dev.hua.ptcglive.view.EffectCardPage.from(snapshot, 999).page() == pages - 1, "部分候选在卡图界面不可达");
        var sorted = prompt.candidates().stream().sorted(Comparator.comparing(CardInstance::definitionId).thenComparing(c -> c.instanceId().toString())).toList();
        check(sorted.equals(prompt.candidates()) && !prompt.candidates().equals(m.privateView(A).deck()), "候选泄露了牌库顺序");
        for (UUID recipient : List.of(B, WATCHER)) {
            var other = session.view(recipient, recipient.equals(B) ? B : null);
            check(other.game().effectSelection() == null, "对方或旁观者收到私有候选");
            var serialized = new com.google.gson.Gson().toJson(other);
            for (var card : prompt.candidates()) check(!serialized.contains(card.instanceId().toString()), "私有实例泄露到包");
            rejects(() -> m.effectCardPrompt(recipient));
            rejects(() -> m.submitEffectCards(recipient, prompt.resolutionId(), prompt.step(), ids(prompt.candidates().subList(0, 1))));
        }
        var before = m.privateView(A); var random = m.randomTranscript();
        var one = prompt.candidates().getFirst().instanceId();
        rejects(() -> m.submitEffectCards(A, UUID.randomUUID(), prompt.step(), List.of(one)));
        rejects(() -> m.submitEffectCards(A, prompt.resolutionId(), prompt.step() + 1, List.of(one)));
        rejects(() -> m.submitEffectCards(A, prompt.resolutionId(), prompt.step(), List.of(one, one)));
        rejects(() -> m.submitEffectCards(A, prompt.resolutionId(), prompt.step(), List.of(UUID.randomUUID())));
        rejects(() -> m.submitEffectCards(A, prompt.resolutionId(), prompt.step(), List.of()));
        rejects(() -> m.endTurn(A));
        check(before.equals(m.privateView(A)) && random.equals(m.randomTranscript()), "非法输入改变状态");
        var choose = snapshot.options().stream().filter(o -> o.cardKeys().contains("effect:" + one)).findFirst().orElseThrow();
        session.submit(A, A, snapshot.revision(), choose.index());
        rejects(() -> session.submit(A, A, snapshot.revision(), choose.index()));
        var reopened = session.view(A, A);
        check(reopened.game().effectSelection().selected().equals(List.of(one)) && before.equals(m.privateView(A)), "重开界面丢失暂选或提前移牌");
        var confirm = reopened.options().stream().filter(o -> o.labelZh().startsWith("确认选牌")).findFirst().orElseThrow();
        session.submit(A, A, reopened.revision(), confirm.index());
        check(m.clientView(A).effectSelection() == null && m.privateView(A).hand().stream().anyMatch(c -> c.instanceId().equals(one)), "会话确认没有取牌并清理候选");
    }
    private static void emptyAndPartialResolution() {
        var m = fixture("""
            {"type":"draw","amount":60},
            {"type":"search_deck","target":"DECK","amount":2,"minimum":2},
            {"type":"discard","target":"HAND","amount":60,"minimum":60},
            {"type":"search_discard_pile","target":"DISCARD","amount":60,"minimum":60,"destination":"HAND"}
            """);
        m.playTrainer(A, trainer(m).instanceId(), null);
        var discard = m.effectCardPrompt(A);
        check(discard.minimum() == discard.candidates().size() && discard.minimum() < 60, "不足指定数量未按效果尽量执行");
        m.submitEffectCards(A, discard.resolutionId(), discard.step(), ids(discard.candidates()));
        var recover = m.effectCardPrompt(A);
        m.submitEffectCards(A, recover.resolutionId(), recover.step(), ids(recover.candidates()));
        check(!m.hasPendingEffectChoice(), "空牌库或不足数量卡死结算");
        var zero = fixture("""
            {"type":"draw","amount":1},
            {"type":"search_discard_pile","target":"DISCARD","amount":1,"minimum":1,"destination":"DECK_SHUFFLED"}
            """);
        var random = zero.randomTranscript(); zero.playTrainer(A, trainer(zero).instanceId(), null);
        check(!zero.hasPendingEffectChoice() && random.equals(zero.randomTranscript()), "零张洗回仍洗牌或错误暂停");
    }
    private static void nestedChoiceAndKnockout() {
        var m = fixture("""
            {"type":"damage_counter","target":"OPPONENT_ACTIVE","amount":12},
            {"type":"choice","options":[
              {"type":"search_deck","target":"DECK","amount":1,"minimum":1},
              {"type":"draw","amount":1}]}
            """);
        var source = trainer(m); m.playTrainer(A, source.instanceId(), null);
        var choice = m.effectChoicePrompt(A); m.chooseEffectOption(A, choice.resolutionId(), choice.step(), 0);
        var cards = m.effectCardPrompt(A);
        check(m.phase() == MatchPhase.TURN && cards.step() > choice.step(), "选牌前就检查击倒或重用步骤");
        rejects(() -> m.chooseEffectOption(A, choice.resolutionId(), choice.step(), 0));
        m.submitEffectCards(A, cards.resolutionId(), cards.step(), ids(cards.candidates().subList(0, 1)));
        check(m.phase() == MatchPhase.PRIZE_SELECTION && m.privateView(A).discard().contains(source), "整卡之后未处理击倒");
        var concede = fixture("{\"type\":\"discard\",\"target\":\"HAND\",\"amount\":1,\"minimum\":1}");
        concede.playTrainer(A, trainer(concede).instanceId(), null); var stale = concede.effectCardPrompt(A);
        concede.concede(B);
        check(concede.clientView(A).effectSelection() == null, "结束对局后残留私有候选");
        rejects(() -> concede.submitEffectCards(A, stale.resolutionId(), stale.step(), ids(stale.candidates().subList(0, 1))));
    }
    private static void invalidDataIsAtomic() {
        for (String invalid : List.of(
            "{\"type\":\"search_deck\",\"target\":\"HAND\",\"amount\":1,\"minimum\":1}",
            "{\"type\":\"search_discard_pile\",\"target\":\"DISCARD\",\"amount\":1,\"minimum\":1,\"destination\":\"LOST_ZONE\"}",
            "{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":1,\"minimum\":0}",
            "{\"type\":\"discard\",\"target\":\"HAND\",\"amount\":1,\"minimum\":1,\"cost\":true}")) {
            var m = fixture("{\"type\":\"draw\",\"amount\":1}," + invalid);
            var before = m.privateView(A); var random = m.randomTranscript();
            rejects(() -> m.playTrainer(A, trainer(m).instanceId(), null));
            check(before.equals(m.privateView(A)) && random.equals(m.randomTranscript()), "无效后续步骤未在出牌前拒绝");
        }
    }
    private static void nestedCoinsAndPractice() {
        var m = fixture("""
            {"type":"repeat","count":2,"effects":[{"type":"coin_flip","flips":1,
              "heads":[{"type":"search_deck","target":"DECK","amount":1,"minimum":1}],
              "tails":[{"type":"search_deck","target":"DECK","amount":1,"minimum":1}]}]},
            {"type":"discard","target":"HAND","amount":1,"minimum":1,
             "conditions":[{"type":"HAND_SIZE","value":60,"comparison":">"}]},
            {"type":"draw","amount":1}
            """);
        int before = m.privateView(A).hand().size();
        m.playTrainer(A, trainer(m).instanceId(), null);
        UUID controller = UUID.randomUUID(); var session = MatchSession.practice(m, controller, A, B);
        var first = m.effectCardPrompt(A);
        check(session.view(controller, B).game().effectSelection() == null && session.view(controller, A).game().effectSelection().equals(first),
            "练习切换后把甲方选牌候选发送给乙方视角");
        rejects(() -> session.view(B, A));
        var selected = ids(first.candidates().subList(0, 1));
        m.submitEffectCards(A, first.resolutionId(), first.step(), selected);
        var second = m.effectCardPrompt(A); var random = m.randomTranscript();
        check(second.step() > first.step() && !ids(second.candidates()).contains(selected.getFirst()), "重复步骤候选未重新核验");
        for (int i = 0; i < 3; i++) session.view(controller, A);
        check(random.equals(m.randomTranscript()), "重同步私有步骤重掷或洗牌");
        m.submitEffectCards(A, second.resolutionId(), second.step(), ids(second.candidates().subList(0, 1)));
        check(!m.hasPendingEffectChoice() && m.privateView(A).hand().size() == before + 2
            && m.randomTranscript().stream().filter(e -> e.operation().startsWith("effect-coin:")).count() == 2,
            "硬币/重复作用域或条件跳过未保留");
    }
    private static void filteredFailureAndLimits() {
        var m = fixture("{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":2,\"minimum\":0,\"card_category\":\"POKEMON\"}");
        int before = m.privateView(A).hand().size(); m.playTrainer(A, trainer(m).instanceId(), null);
        var prompt = m.effectCardPrompt(A); var random = m.randomTranscript();
        var ids = ids(prompt.candidates().subList(0, 3));
        m.toggleEffectCard(A, prompt.resolutionId(), prompt.step(), ids.get(0));
        m.toggleEffectCard(A, prompt.resolutionId(), prompt.step(), ids.get(1));
        rejects(() -> m.toggleEffectCard(A, prompt.resolutionId(), prompt.step(), ids.get(2)));
        rejects(() -> m.submitEffectCards(A, prompt.resolutionId(), prompt.step(), ids));
        check(m.effectCardPrompt(A).selected().size() == 2 && random.equals(m.randomTranscript()), "超量选择改变暂选或随机状态");
        m.toggleEffectCard(A, prompt.resolutionId(), prompt.step(), ids.get(0));
        m.toggleEffectCard(A, prompt.resolutionId(), prompt.step(), ids.get(1));
        m.submitEffectCards(A, prompt.resolutionId(), prompt.step(), List.of());
        check(m.privateView(A).hand().size() == before - 1 && m.randomTranscript().size() > random.size(), "筛选检索零张没有仍然洗牌");
        var onlySource = fixture("{\"type\":\"discard\",\"target\":\"HAND\",\"amount\":1,\"minimum\":1}");
        var source = trainer(onlySource);
        onlySource.discardHandCardsByEffect(A, A, ids(onlySource.privateView(A).hand().stream().filter(c -> !c.equals(source)).toList()));
        var state = onlySource.privateView(A);
        rejects(() -> onlySource.playTrainer(A, source.instanceId(), null));
        check(state.equals(onlySource.privateView(A)), "预检把出牌中的训练家当成可弃手牌");
    }
    private static ServerMatch fixture(String effects) { return ComposedTrainerIntegrationTest.fixture(ComposedTrainerIntegrationTest.item(effects)); }
    private static CardInstance trainer(ServerMatch m) { return m.privateView(A).hand().stream().filter(c -> c.definitionId().equals("component")).findFirst().orElseThrow(); }
    private static List<UUID> ids(List<CardInstance> cards) { return cards.stream().map(CardInstance::instanceId).toList(); }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static void rejects(Runnable action) { try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; } throw new AssertionError("非法操作未拒绝"); }
}
