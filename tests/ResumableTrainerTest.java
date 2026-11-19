import dev.hua.ptcglive.engine.*;
import java.util.*;

/** Continuations must retain the committed prefix, keep randomness, and block unrelated actions. */
public final class ResumableTrainerTest {
    private static final UUID A = new UUID(0, 7561), B = new UUID(0, 7562), WATCHER = new UUID(0, 7590);
    public static void main(String[] args) {
        pauseResumeAndAuthority();
        nestedCoinAndRepeatedChoice();
        knockoutWaitsAcrossPause();
        invalidBranchesRejectBeforeCommit();
        concedeCleansPendingCard();
        allOptionsRemainReachableOnTable();
        supporterAndSkippedChoice();
        System.out.println("ResumableTrainerTest passed: nested choices preserve prefix/coins, authority, turn lock and KO checkpoint");
    }
    private static void pauseResumeAndAuthority() {
        var m = fixture("""
            {"type":"draw","amount":1},
            {"type":"choice","options":[{"type":"draw","amount":2},{"type":"draw","amount":3}]},
            {"type":"draw","amount":1}
            """);
        var source = trainer(m); int before = m.privateView(A).hand().size();
        var session = MatchSession.multiplayer(m, A, B); session.addSpectator(WATCHER);
        var view = session.view(A, A);
        var action = view.options().stream().filter(o -> o.cardKeys().contains("hand:" + source.instanceId())).findFirst().orElseThrow();
        session.submit(A, A, view.revision(), action.index());
        check(m.hasPendingEffectChoice() && m.privateView(A).hand().size() == before && !m.privateView(A).discard().contains(source), "暂停前缀未恰好执行一次");
        for (UUID viewer : List.of(A, B, WATCHER)) {
            var publicSnapshot = session.view(viewer, viewer.equals(WATCHER) ? null : viewer);
            check(source.equals(publicSnapshot.game().resolvingTrainer())
                && dev.hua.ptcglive.view.MatchRegionModel.from(publicSnapshot).zone("resolving").cards().size() == 1,
                "已使用的训练家未向对方和观战者公开");
        }
        var prompt = m.effectChoicePrompt(A);
        check(prompt.optionsZh().size() == 2 && prompt.optionsZh().getFirst().contains("抽 2"), "选择不含中文效果说明");
        var state = m.privateView(A); var random = m.randomTranscript();
        rejects(() -> m.endTurn(A)); rejects(() -> m.playTrainer(A, trainer(m).instanceId(), null));
        rejects(() -> m.drawCardsByEffect(A, A, 1));
        rejects(() -> m.effectChoicePrompt(B)); rejects(() -> m.effectChoicePrompt(WATCHER));
        rejects(() -> m.chooseEffectOption(B, prompt.resolutionId(), prompt.step(), 0));
        rejects(() -> m.chooseEffectOption(A, UUID.randomUUID(), prompt.step(), 0));
        rejects(() -> m.chooseEffectOption(A, prompt.resolutionId(), prompt.step() + 1, 0));
        rejects(() -> m.chooseEffectOption(A, prompt.resolutionId(), prompt.step(), -1));
        rejects(() -> m.chooseEffectOption(A, prompt.resolutionId(), prompt.step(), 2));
        for (int i = 0; i < 3; i++) {
            var reopened = session.view(A, A);
            check(reopened.options().stream().filter(o -> o.labelZh().startsWith("选择效果")).count() == 2, "重开界面丢失选择");
            check(reopened.options().stream().noneMatch(o -> o.labelZh().equals("结束回合") || !o.cardKeys().isEmpty()), "暂停时仍有其他操作");
            check(session.view(B, B).options().size() == 1 && session.view(WATCHER, null).options().isEmpty(), "选择泄露给其他接收者");
        }
        check(state.equals(m.privateView(A)) && random.equals(m.randomTranscript()) && prompt.equals(m.effectChoicePrompt(A)), "刷新或失败请求重放前缀");
        var waiting = session.view(A, A);
        var choose = waiting.options().stream().filter(o -> o.labelZh().startsWith("选择效果 1")).findFirst().orElseThrow();
        session.submit(A, A, waiting.revision(), choose.index());
        check(!m.hasPendingEffectChoice() && m.privateView(A).hand().size() == before + 3 && m.privateView(A).discard().contains(source), "选择后未从原位置续接");
        check(m.clientView(A).resolvingTrainer() == null, "完成后仍残留结算区域");
        rejects(() -> session.submit(A, A, waiting.revision(), choose.index()));
        rejects(() -> m.chooseEffectOption(A, prompt.resolutionId(), prompt.step(), 0));
        m.endTurn(A);
    }
    private static void nestedCoinAndRepeatedChoice() {
        var m = fixture("""
            {"type":"repeat","count":2,"effects":[{"type":"coin_flip","flips":1,
              "heads":[{"type":"choice","options":[{"type":"draw","amount":1},{"type":"draw","amount":2}]}],
              "tails":[{"type":"choice","options":[{"type":"draw","amount":3},{"type":"draw","amount":4}]}]}]},
            {"type":"draw","amount":1}
            """);
        int before = m.privateView(A).hand().size(), random = m.randomTranscript().size(); var source = trainer(m);
        m.playTrainer(A, source.instanceId(), null);
        var first = m.effectChoicePrompt(A);
        check(m.randomTranscript().size() == random + 1, "第一次暂停未保存硬币结果");
        int expected = m.randomTranscript().getLast().result() == 0 ? 1 : 3;
        m.chooseEffectOption(A, first.resolutionId(), first.step(), 0);
        var second = m.effectChoicePrompt(A);
        check(second.step() != first.step() && second.resolutionId().equals(first.resolutionId()), "多步骤未保持结算身份及独立步号");
        check(m.randomTranscript().size() == random + 2, "恢复时重掷了旧硬币");
        expected += m.randomTranscript().getLast().result() == 0 ? 1 : 3;
        rejects(() -> m.chooseEffectOption(A, first.resolutionId(), first.step(), 0));
        m.chooseEffectOption(A, second.resolutionId(), second.step(), 0);
        check(m.randomTranscript().size() == random + 2 && m.privateView(A).hand().size() == before + expected, "嵌套 Choice/Repeat/Coin 时序丢失");
        check(m.privateView(A).discard().contains(source), "多次续接没有最终弃置训练家");
    }
    private static void knockoutWaitsAcrossPause() {
        var m = fixture("""
            {"type":"damage_counter","target":"OPPONENT_ACTIVE","amount":12},
            {"type":"choice","options":[{"type":"draw","amount":1},{"type":"draw","amount":2}]}
            """);
        var source = trainer(m); m.playTrainer(A, source.instanceId(), null);
        check(m.phase() == MatchPhase.TURN && m.hasPendingEffectChoice() && m.activePokemonView(B).damage() == 120, "选择未完成便开始拿奖");
        rejects(() -> m.takePrizeAt(A, 0));
        var prompt = m.effectChoicePrompt(A); m.chooseEffectOption(A, prompt.resolutionId(), prompt.step(), 0);
        check(m.phase() == MatchPhase.PRIZE_SELECTION && m.privateView(A).discard().contains(source), "续接结束没有统一击倒");
        m.takePrizeAt(A, 0); m.chooseReplacement(B, m.benchPokemonView(B).getFirst().pokemonId());
        check(m.currentPlayer().equals(A) && m.phase() == MatchPhase.TURN, "非攻击续接错误结束回合");
    }
    private static void invalidBranchesRejectBeforeCommit() {
        var m = fixture("""
            {"type":"draw","amount":1},{"type":"choice","options":[
              {"type":"draw","amount":1},{"type":"heal","target":"OWN_ACTIVE","amount":11}]}
            """);
        var state = m.privateView(A); var random = m.randomTranscript();
        rejects(() -> m.playTrainer(A, trainer(m).instanceId(), null));
        check(state.equals(m.privateView(A)) && random.equals(m.randomTranscript()) && !m.hasPendingEffectChoice(), "未选的非法分支导致已提交前缀");
    }
    private static void concedeCleansPendingCard() {
        var m = fixture("{\"type\":\"choice\",\"options\":[{\"type\":\"draw\",\"amount\":1},{\"type\":\"draw\",\"amount\":2}]}");
        var source = trainer(m); m.playTrainer(A, source.instanceId(), null); var prompt = m.effectChoicePrompt(A);
        m.concede(B);
        check(m.result().winner().equals(A) && !m.hasPendingEffectChoice() && m.privateView(A).discard().contains(source), "认输没有清理正在结算的训练家");
        rejects(() -> m.chooseEffectOption(A, prompt.resolutionId(), prompt.step(), 0));
    }
    private static void allOptionsRemainReachableOnTable() {
        String options = java.util.stream.IntStream.rangeClosed(1, 60)
            .mapToObj(n -> "{\"type\":\"draw\",\"amount\":" + n + "}").collect(java.util.stream.Collectors.joining(","));
        var m = fixture("{\"type\":\"choice\",\"options\":[" + options + "]}");
        m.playTrainer(A, trainer(m).instanceId(), null);
        var snapshot = MatchSession.multiplayer(m, A, B).view(A, A);
        var reached = new HashSet<Integer>();
        for (int i = 0; i < 30; i++) {
            var page = dev.hua.ptcglive.view.EffectChoicePage.from(snapshot, i);
            check(page.page() == i && page.pages() == 30 && page.options().size() == 2, "桌面选择分页不完整");
            page.options().forEach(option -> reached.add(option.index()));
        }
        check(reached.size() == 60 && dev.hua.ptcglive.view.EffectChoicePage.from(snapshot, 999).page() == 29
            && dev.hua.ptcglive.view.EffectChoicePage.from(snapshot, -1).page() == 0, "第三项以后的服务器方案在桌面不可达");
        var prompt = m.effectChoicePrompt(A); m.chooseEffectOption(A, prompt.resolutionId(), prompt.step(), 59);
        check(m.privateView(A).deck().isEmpty() && !m.hasPendingEffectChoice(), "末页选项不能实际提交");
    }
    private static void supporterAndSkippedChoice() {
        var original = ComposedTrainerIntegrationTest.item("{\"type\":\"choice\",\"options\":[{\"type\":\"draw\",\"amount\":1},{\"type\":\"draw\",\"amount\":2}]}");
        var supporter = new dev.hua.ptcglive.rules.TrainerProfile(dev.hua.ptcglive.rules.TrainerCategory.SUPPORTER, original.effect(), original.effectProgram());
        var m = ComposedTrainerIntegrationTest.fixture(supporter);
        rejects(() -> m.playTrainer(A, trainer(m).instanceId(), null));
        m.endTurn(A); m.endTurn(B);
        m.playTrainer(A, trainer(m).instanceId(), null);
        rejects(() -> m.playTrainer(A, trainer(m).instanceId(), null));
        var prompt = m.effectChoicePrompt(A); m.chooseEffectOption(A, prompt.resolutionId(), prompt.step(), 0);
        check(!m.mayPlaySupporter(A), "续接后的支援者没有记次数");
        rejects(() -> m.playTrainer(A, trainer(m).instanceId(), null));
        var skipped = fixture("""
            {"type":"draw","amount":1},{"type":"conditional","conditions":[{"type":"HAND_SIZE","value":60}],
             "effects":[{"type":"choice","options":[{"type":"draw","amount":1},{"type":"draw","amount":2}]}]}
            """);
        var card = trainer(skipped); int before = skipped.privateView(A).hand().size();
        skipped.playTrainer(A, card.instanceId(), null);
        check(!skipped.hasPendingEffectChoice() && skipped.privateView(A).hand().size() == before && skipped.privateView(A).discard().contains(card),
            "条件未满足的选择仍造成暂停");
    }
    private static ServerMatch fixture(String effects) { return ComposedTrainerIntegrationTest.fixture(ComposedTrainerIntegrationTest.item(effects)); }
    private static dev.hua.ptcglive.rules.CardInstance trainer(ServerMatch m) { return m.privateView(A).hand().stream().filter(c -> c.definitionId().equals("component")).findFirst().orElseThrow(); }
    private static void rejects(Runnable action) { try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; } throw new AssertionError("非法续接请求被接受"); }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
