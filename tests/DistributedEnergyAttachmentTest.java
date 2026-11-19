import dev.hua.ptcglive.engine.MatchSession;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.rules.CardInstance;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** A single effect may distribute selected Energy cards across different server-issued public hosts. */
public final class DistributedEnergyAttachmentTest {
    private static final UUID A = new UUID(0, 8821), B = new UUID(0, 8822), WATCH = new UUID(0, 8866);
    private static final String DISTRIBUTE = "{\"type\":\"attach_energy\",\"target\":\"SELECT_OWN_POKEMON\",\"source\":\"HAND\",\"amount\":2,\"minimum\":2,\"basic_energy_only\":true,\"energy_type\":\"GRASS\",\"target_scope\":\"OWN_FIELD\",\"distribution\":\"ANY\"}";

    public static void main(String[] args) {
        privateSelectionThenSeparatePublicHosts();
        deckDistributionShufflesOnce();
        malformedOrIncompleteDistributionFailsClosed();
        System.out.println("DistributedEnergyAttachmentTest passed: per-card Energy host assignment is private, atomic and server-revalidated");
    }

    private static void privateSelectionThenSeparatePublicHosts() {
        ServerMatch match = ComposedEnergyAttachmentTest.fixture(DISTRIBUTE, 0);
        MatchSession session = MatchSession.multiplayer(match, A, B); session.addSpectator(WATCH);
        match.playTrainer(A, trainer(match).instanceId(), null);
        var select = match.effectCardPrompt(A);
        List<UUID> cards = select.candidates().stream().filter(card -> card.definitionId().equals("grass")).limit(2).map(CardInstance::instanceId).toList();
        match.submitEffectCards(A, select.resolutionId(), select.step(), cards);
        check(match.hasPendingEnergyDistribution() && !match.hasPendingCardSelection(), "选牌后没有进入独立的多目标分配步骤");
        var prompt = match.effectAttachmentPrompt(A);
        check(prompt.actionZh().equals("分配能量") && prompt.cards().stream().map(CardInstance::instanceId).toList().equals(cards), "分配提示没有保留已选私有能量");
        for (UUID recipient : List.of(B, WATCH)) {
            var hidden = session.view(recipient, recipient.equals(B) ? B : null);
            check(hidden.game().effectAttachment() == null && !new com.google.gson.Gson().toJson(hidden).contains(cards.getFirst().toString()),
                "对手或旁观者收到待分配能量的私有信息");
        }
        UUID active = match.activePokemonView(A).pokemonId();
        UUID bench = match.benchPokemonView(A).getFirst().pokemonId();
        check(match.energyDistributionTargets(A, prompt.resolutionId(), prompt.step(), cards.getFirst()).contains(active), "服务器未给第一张能量签发战斗位候选");
        reject(() -> match.assignEffectEnergyDistribution(B, prompt.resolutionId(), prompt.step(), cards.getFirst(), active));
        reject(() -> match.assignEffectEnergyDistribution(A, prompt.resolutionId(), prompt.step(), cards.getFirst(), UUID.randomUUID()));
        reject(() -> match.confirmEffectEnergyDistribution(A, prompt.resolutionId(), prompt.step()));
        check(match.privateView(A).hand().stream().anyMatch(card -> card.instanceId().equals(cards.getFirst())), "未确认分配就移动了能量");
        var before = session.view(A, A);
        int firstAction = before.options().stream().filter(option -> option.cardKeys().contains("effect-source:" + cards.getFirst())
            && option.cardKeys().contains("field:" + active)).findFirst().orElseThrow().index();
        session.submit(A, A, before.revision(), firstAction);
        var afterFirst = session.view(A, A);
        check(afterFirst.game().effectAttachment().assignedTargets().get(cards.getFirst()).equals(active), "刷新后的界面没有显示第一张能量的已分配目标");
        int secondAction = afterFirst.options().stream().filter(option -> option.cardKeys().contains("effect-source:" + cards.getLast())
            && option.cardKeys().contains("field:" + bench)).findFirst().orElseThrow().index();
        session.submit(A, A, afterFirst.revision(), secondAction);
        var ready = session.view(A, A);
        int confirm = ready.options().stream().filter(option -> option.labelZh().startsWith("确认能量分配")).findFirst().orElseThrow().index();
        session.submit(A, A, ready.revision(), confirm);
        check(match.activePokemonView(A).attachedEnergyIds().contains(cards.getFirst()), "第一张能量没有附着至被分配的战斗位");
        check(match.benchPokemonView(A).getFirst().attachedEnergyIds().contains(cards.getLast()), "第二张能量没有附着至被分配的备战位");
        check(!match.hasPendingEffectChoice() && match.privateView(A).discard().stream().anyMatch(card -> card.definitionId().equals("component")), "分配完成后没有续接整张训练家");
    }

    private static void deckDistributionShufflesOnce() {
        ServerMatch match = ComposedEnergyAttachmentTest.fixture(DISTRIBUTE.replace("\"HAND\"", "\"DECK\""), 0);
        match.playTrainer(A, trainer(match).instanceId(), null);
        var select = match.effectCardPrompt(A);
        List<UUID> cards = select.candidates().stream().filter(card -> card.definitionId().equals("grass")).limit(2).map(CardInstance::instanceId).toList();
        var before = match.randomTranscript();
        match.submitEffectCards(A, select.resolutionId(), select.step(), cards);
        var prompt = match.effectAttachmentPrompt(A);
        UUID active = match.activePokemonView(A).pokemonId(), bench = match.benchPokemonView(A).getFirst().pokemonId();
        match.assignEffectEnergyDistribution(A, prompt.resolutionId(), prompt.step(), cards.getFirst(), active);
        match.assignEffectEnergyDistribution(A, prompt.resolutionId(), prompt.step(), cards.getLast(), bench);
        match.confirmEffectEnergyDistribution(A, prompt.resolutionId(), prompt.step());
        boolean inShuffle = false; int shuffleBatches = 0;
        for (var event : match.randomTranscript().stream().skip(before.size()).toList()) {
            boolean shuffle = event.operation().startsWith("effect-attach-deck-energy:");
            if (shuffle && !inShuffle) shuffleBatches++;
            inShuffle = shuffle;
        }
        check(shuffleBatches == 1, "多目标牌库附能没有恰好洗牌一次");
    }

    private static void malformedOrIncompleteDistributionFailsClosed() {
        for (String invalid : List.of(
            DISTRIBUTE.replace("\"target_scope\":\"OWN_FIELD\"", "\"target_scope\":\"OPPONENT_FIELD\""),
            DISTRIBUTE.replace("\"distribution\":\"ANY\"", "\"distribution\":\"SPLIT\""),
            DISTRIBUTE.replace("SELECT_OWN_POKEMON", "CHOSEN_POKEMON"))) {
            var profile = ComposedTrainerIntegrationTest.item(invalid);
            check(dev.hua.ptcglive.engine.effect.TrainerProgramRules.unsupportedReason(profile.effectProgram()).isPresent(), "非法分配数据没有在开局前拒绝");
        }
        ServerMatch match = ComposedEnergyAttachmentTest.fixture(DISTRIBUTE, 0);
        match.playTrainer(A, trainer(match).instanceId(), null);
        var select = match.effectCardPrompt(A);
        List<UUID> cards = select.candidates().stream().filter(card -> card.definitionId().equals("grass")).limit(2).map(CardInstance::instanceId).toList();
        match.submitEffectCards(A, select.resolutionId(), select.step(), cards);
        var prompt = match.effectAttachmentPrompt(A);
        match.assignEffectEnergyDistribution(A, prompt.resolutionId(), prompt.step(), cards.getFirst(), match.activePokemonView(A).pokemonId());
        var before = match.clientView(A);
        reject(() -> match.confirmEffectEnergyDistribution(A, prompt.resolutionId(), prompt.step()));
        check(before.equals(match.clientView(A)), "不完整分配改变了对局状态");
    }

    private static CardInstance trainer(ServerMatch match) {
        return match.privateView(A).hand().stream().filter(card -> card.definitionId().equals("component")).min(Comparator.comparing(CardInstance::instanceId)).orElseThrow();
    }
    private static void reject(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("非法多目标附能操作未被服务器拒绝");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
