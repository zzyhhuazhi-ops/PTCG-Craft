import com.google.gson.Gson;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.rules.CardInstance;
import dev.hua.ptcglive.view.*;
import java.util.*;

/** Revealing faces must never disclose trackable hidden instances or end a Trainer early. */
public final class TrainerSearchRevealTest {
    private static final UUID A = new UUID(0, 7561), B = new UUID(0, 7562), WATCHER = new UUID(0, 7598);
    public static void main(String[] args) {
        publicFacesAndAuthority(); repeatedRevealAndKnockout(); zeroAndNoReveal(); practiceAndConcede(); discardReveal(); malformedAndStandalone();
        System.out.println("TrainerSearchRevealTest passed: public faces without hidden IDs, acknowledgement authority, nested resolution and cleanup");
    }
    private static void publicFacesAndAuthority() {
        var m = fixture("{\"type\":\"draw\",\"amount\":1}," + search(true, 2) + ",{\"type\":\"draw\",\"amount\":1}");
        var session = MatchSession.multiplayer(m, A, B); session.addSpectator(WATCHER);
        var source = trainer(m); m.playTrainer(A, source.instanceId(), null);
        var prompt = m.effectCardPrompt(A); var selected = prompt.candidates().subList(0, 2);
        var before = m.privateView(A); var random = m.randomTranscript();
        rejects(() -> m.submitEffectCards(A, prompt.resolutionId(), prompt.step(), List.of(UUID.randomUUID())));
        check(m.effectCardReveal() == null && m.privateView(A).equals(before), "无效选择公开了卡片或移牌");
        m.submitEffectCards(A, prompt.resolutionId(), prompt.step(), ids(selected));
        var reveal = m.effectCardReveal();
        check(reveal != null && reveal.confirmingPlayer().equals(B) && reveal.actor().equals(A)
            && reveal.cardIds().equals(selected.stream().map(CardInstance::definitionId).sorted().toList()), "展示不是准确的所选卡面");
        check(m.privateView(A).equals(before) && m.randomTranscript().equals(random) && !m.privateView(A).discard().contains(source), "对手看清之前移牌或执行后续");
        for (UUID viewer : List.of(A, B, WATCHER)) {
            var snapshot = session.view(viewer, viewer.equals(WATCHER) ? null : viewer);
            check(snapshot.game().effectSelection() == null && reveal.equals(snapshot.game().effectReveal()), "展示期仍提供可改选候选");
            var faces = MatchRegionModel.from(snapshot).zone("effect-reveal").cards();
            check(faces.size() == 2 && EffectCardPage.from(snapshot, 0).cards().size() == 2, "展示没有接入卡图界面");
            String json = new Gson().toJson(snapshot);
            for (var card : m.privateView(A).deck()) check(!json.contains(card.instanceId().toString()), "展示泄露物理编号或牌库顺序");
            check(new Gson().fromJson(json, MatchSession.Snapshot.class).game().effectReveal().equals(reveal), "展示不能重同步");
            check(EffectCardPage.identity(snapshot).equals(EffectCardPage.identity(session.view(viewer, viewer.equals(WATCHER) ? null : viewer))),
                "同一展示刷新后页码身份变化");
        }
        rejects(() -> m.effectCardPrompt(A));
        rejects(() -> m.toggleEffectCard(A, prompt.resolutionId(), prompt.step(), selected.getFirst().instanceId()));
        rejects(() -> m.submitEffectCards(A, prompt.resolutionId(), prompt.step(), ids(selected)));
        rejects(() -> m.acknowledgeEffectReveal(A, reveal.id()));
        rejects(() -> m.acknowledgeEffectReveal(WATCHER, reveal.id()));
        rejects(() -> m.acknowledgeEffectReveal(B, UUID.randomUUID()));
        rejects(() -> m.endTurn(A));
        check(m.privateView(A).equals(before) && m.randomTranscript().equals(random), "错误确认导致结算");
        var view = session.view(B, B);
        var acknowledge = view.options().stream().filter(o -> o.labelZh().startsWith("确认已查看")).findFirst().orElseThrow();
        check(session.view(A, A).options().stream().noneMatch(o -> o.labelZh().startsWith("确认已查看")), "出牌人可代替对手确认");
        session.submit(B, B, view.revision(), acknowledge.index());
        rejects(() -> session.submit(B, B, view.revision(), acknowledge.index()));
        rejects(() -> m.acknowledgeEffectReveal(B, reveal.id()));
        check(m.effectCardReveal() == null && !m.hasPendingEffectChoice() && m.privateView(A).hand().containsAll(selected)
            && m.privateView(A).hand().size() == before.hand().size() + 3 && m.privateView(A).discard().contains(source), "展示后没有续接完整训练家");
        check(m.clientView(B).hand().stream().noneMatch(selected::contains), "展示结束后保留了对手隐藏手牌");
        check(m.replayTranscript().stream().anyMatch(e -> e.operation().startsWith("展示检索卡牌：")), "公开展示没有回放事实");
    }
    private static void repeatedRevealAndKnockout() {
        var m = fixture("{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":12},"
            + "{\"type\":\"repeat\",\"count\":2,\"effects\":[" + search(true, 1) + "]}");
        m.playTrainer(A, trainer(m).instanceId(), null);
        UUID prior = null;
        for (int i = 0; i < 2; i++) {
            var p = m.effectCardPrompt(A);
            m.submitEffectCards(A, p.resolutionId(), p.step(), ids(p.candidates().subList(0, 1)));
            var reveal = m.effectCardReveal();
            check(m.phase() == MatchPhase.TURN && !reveal.id().equals(prior), "提前拿奖或复用展示身份");
            if (prior != null) { UUID stale = prior; rejects(() -> m.acknowledgeEffectReveal(B, stale)); }
            prior = reveal.id(); m.acknowledgeEffectReveal(B, reveal.id());
        }
        check(m.phase() == MatchPhase.PRIZE_SELECTION, "全部展示之后没有统一击倒检查");
    }
    private static void zeroAndNoReveal() {
        for (boolean reveal : List.of(false, true)) {
            var m = fixture(search(reveal, 1)); m.playTrainer(A, trainer(m).instanceId(), null);
            var prompt = m.effectCardPrompt(A);
            m.submitEffectCards(A, prompt.resolutionId(), prompt.step(), List.of());
            check(m.effectCardReveal() == null && !m.hasPendingEffectChoice(), "零张检索要求确认空展示");
        }
        var m = fixture(search(false, 1)); m.playTrainer(A, trainer(m).instanceId(), null);
        var prompt = m.effectCardPrompt(A);
        m.submitEffectCards(A, prompt.resolutionId(), prompt.step(), ids(prompt.candidates().subList(0, 1)));
        check(m.effectCardReveal() == null && !m.hasPendingEffectChoice(), "未声明展示的检索自动公开了卡牌");
    }
    private static void practiceAndConcede() {
        var m = fixture(search(true, 8)); var source = trainer(m); m.playTrainer(A, source.instanceId(), null);
        var prompt = m.effectCardPrompt(A); m.submitEffectCards(A, prompt.resolutionId(), prompt.step(), ids(prompt.candidates().subList(0, 8)));
        UUID controller = UUID.randomUUID(); var session = MatchSession.practice(m, controller, A, B);
        var snapshot = session.view(controller, B); var seen = new HashSet<String>();
        for (int page = 0; page < 2; page++) for (var card : EffectCardPage.from(snapshot, page).cards()) check(seen.add(card.key()), "重复卡面没有独立展示锚点");
        check(seen.size() == 8 && snapshot.options().stream().anyMatch(o -> o.labelZh().startsWith("确认已查看")), "练习无法确认或展示分页缺卡");
        var reveal = m.effectCardReveal(); var before = m.privateView(A).deck(); m.concede(A);
        check(m.effectCardReveal() == null && m.privateView(A).deck().equals(before) && m.privateView(A).discard().contains(source), "认输仍然执行未完成检索或残留展示");
        rejects(() -> m.acknowledgeEffectReveal(B, reveal.id()));
    }
    private static void malformedAndStandalone() {
        rejects(() -> fixture(search(true, 1).replace("true", "null")));
        for (String value : List.of("\"yes\"", "1")) {
            var m = fixture("{\"type\":\"draw\",\"amount\":1}," + search(true, 1).replace("true", value));
            var before = m.privateView(A);
            rejects(() -> m.playTrainer(A, trainer(m).instanceId(), null));
            check(m.privateView(A).equals(before), "错误展示参数没有在首步前拒绝");
        }
        var m = fixture(search(false, 1));
        var effect = EffectRegistry.standard().parse(com.google.gson.JsonParser.parseString(search(true, 1)).getAsJsonObject());
        rejects(() -> m.openEffectDeckSearch(A, effect, 0));
        var handReveal = fixture("{\"type\":\"draw\",\"amount\":1},{\"type\":\"discard\",\"target\":\"HAND\",\"amount\":1,\"minimum\":1,\"reveal\":true}");
        var state = handReveal.privateView(A);
        rejects(() -> handReveal.playTrainer(A, trainer(handReveal).instanceId(), null));
        check(handReveal.privateView(A).equals(state), "错误入口悄悄把手牌展示当成检索展示");
    }
    private static void discardReveal() {
        var m = fixture("{\"type\":\"search_discard_pile\",\"target\":\"DISCARD\",\"amount\":1,\"minimum\":1,\"destination\":\"DECK_SHUFFLED\",\"reveal\":true}");
        var found = m.privateView(A).hand().stream().filter(c -> c.definitionId().equals("basic")).findFirst().orElseThrow();
        m.discardHandCardsByEffect(A, A, List.of(found.instanceId()));
        m.playTrainer(A, trainer(m).instanceId(), null);
        var prompt = m.effectCardPrompt(A);
        m.submitEffectCards(A, prompt.resolutionId(), prompt.step(), List.of(found.instanceId()));
        var before = m.privateView(A); var random = m.randomTranscript();
        check(before.discard().contains(found), "弃牌展示前移走来源卡");
        UUID controller = UUID.randomUUID(); var session = MatchSession.practice(m, controller, A, B);
        var view = session.view(controller, B);
        var ack = view.options().stream().filter(o -> o.labelZh().startsWith("确认已查看")).findFirst().orElseThrow();
        session.submit(controller, B, view.revision(), ack.index());
        check(m.effectCardReveal() == null && m.privateView(A).deck().contains(found)
            && !m.privateView(A).discard().contains(found) && m.randomTranscript().size() > random.size(),
            "练习换视角确认后未完成弃牌洗回");
    }
    private static String search(boolean reveal, int amount) { return "{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":" + amount + ",\"minimum\":0,\"card_category\":\"POKEMON\",\"reveal\":" + reveal + "}"; }
    private static ServerMatch fixture(String effects) { return ComposedTrainerIntegrationTest.fixture(ComposedTrainerIntegrationTest.item(effects)); }
    private static CardInstance trainer(ServerMatch m) { return m.privateView(A).hand().stream().filter(c -> c.definitionId().equals("component")).findFirst().orElseThrow(); }
    private static List<UUID> ids(List<CardInstance> cards) { return cards.stream().map(CardInstance::instanceId).toList(); }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static void rejects(Runnable action) { try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; } throw new AssertionError("非法操作未拒绝"); }
}
