import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** JSON -> card play -> private energy selection -> public target -> shared attachment rules. */
public final class ComposedEnergyAttachmentTest {
    private static final UUID A = new UUID(0, 8821), B = new UUID(0, 8822), WATCH = new UUID(0, 8823);
    private static final Ruleset RULE = BundledRules.loadRulesets().get("standard-international-2026");
    private static final String ATTACH = "{\"type\":\"attach_energy\",\"target\":\"SELECT_OWN_POKEMON\",\"source\":\"HAND\",\"amount\":1,\"minimum\":1,\"basic_energy_only\":true,\"energy_type\":\"GRASS\",\"target_scope\":\"OWN_BENCH\"}";
    public static void main(String[] args) {
        handAndAuthority(); independentRepeatedTargets(); discardAndDeck(); coinAndNoEffect(); malformedAndAtomic(); knockoutBoundary();
        specialCardsAndPartialCounts();
        System.out.println("ComposedEnergyAttachmentTest passed: JSON continuation, private cards, target authority, sources, coins, repeat, limits and KO boundary");
    }
    private static void handAndAuthority() {
        var m = fixture(ATTACH + ",{\"type\":\"draw\",\"amount\":1}", 0);
        var source = trainer(m); var energy = m.privateView(A).hand().stream().filter(c -> c.definitionId().equals("grass")).findFirst().orElseThrow();
        m.attachEnergy(A, energy.instanceId(), m.activePokemonView(A).pokemonId());
        int attached = m.activePokemonView(A).attachedEnergyIds().size();
        var session = MatchSession.multiplayer(m, A, B); session.addSpectator(WATCH);
        m.playTrainer(A, source.instanceId(), null);
        var prompt = m.effectCardPrompt(A);
        check(prompt.candidates().stream().allMatch(c -> c.definitionId().equals("grass")), "候选未按基础草能量过滤");
        var selected = prompt.candidates().getFirst(); var before = m.privateView(A); var random = m.randomTranscript();
        rejects(() -> m.submitEffectCards(A, prompt.resolutionId(), prompt.step(), List.of(selected.instanceId(), selected.instanceId())));
        rejects(() -> m.submitEffectCards(B, prompt.resolutionId(), prompt.step(), List.of(selected.instanceId())));
        check(before.equals(m.privateView(A)) && random.equals(m.randomTranscript()), "非法选能量已经移牌");
        m.submitEffectCards(A, prompt.resolutionId(), prompt.step(), List.of(selected.instanceId()));
        var target = m.effectAttachmentPrompt(A);
        check(!m.hasPendingCardSelection() && before.equals(m.privateView(A)), "选目标前提前附着／抽牌");
        check(target.cards().equals(List.of(selected)) && target.targets().equals(m.benchPokemonView(A).stream().map(p -> p.pokemonId()).toList()), "目标范围错误");
        for (UUID recipient : List.of(B, WATCH)) {
            var view = session.view(recipient, recipient.equals(B) ? B : null);
            check(view.game().effectAttachment() == null && view.game().effectSelection() == null, "向他人发送私有能量选择");
            check(!new com.google.gson.Gson().toJson(view).contains(selected.instanceId().toString()), "选中手牌实例泄露");
        }
        var view = session.view(A, A); var refresh = session.view(A, A);
        check(view.game().effectAttachment().equals(refresh.game().effectAttachment()), "刷新丢失目标选择");
        check(dev.hua.ptcglive.view.EffectCardPage.from(view, 0).cards().getFirst().key().equals("effect-source:" + selected.instanceId()), "选中能量没有可拖拽卡图");
        rejects(() -> m.chooseEffectAttachmentTarget(A, UUID.randomUUID(), target.step(), target.targets().getFirst()));
        rejects(() -> m.chooseEffectAttachmentTarget(A, target.resolutionId(), target.step(), m.activePokemonView(A).pokemonId()));
        rejects(() -> m.chooseEffectAttachmentTarget(B, target.resolutionId(), target.step(), target.targets().getFirst()));
        rejects(() -> m.endTurn(A));
        rejects(() -> m.submitEffectCards(A, prompt.resolutionId(), prompt.step(), List.of(selected.instanceId())));
        check(before.equals(m.privateView(A)), "伪造目标或重复提交改变手牌");
        var option = view.options().stream().filter(o -> o.cardKeys().contains("field:" + target.targets().getFirst())).findFirst().orElseThrow();
        check(option.cardKeys().contains("effect-source:" + selected.instanceId()), "附能意图无法从卡图拖到目标");
        session.submit(A, A, view.revision(), option.index());
        rejects(() -> session.submit(A, A, view.revision(), option.index()));
        check(m.benchPokemonView(A).getFirst().attachedEnergyIds().contains(selected.instanceId()) && m.activePokemonView(A).attachedEnergyIds().size() == attached,
            "附着到了错误目标");
        check(m.energyAttached(A) && !m.hasPendingEffectChoice() && m.privateView(A).discard().contains(source), "效果附能消耗／重置手动次数或未完成整卡");
    }
    private static void independentRepeatedTargets() {
        var m = fixture("{\"type\":\"repeat\",\"count\":2,\"effects\":[" + ATTACH + "]}", 0);
        m.playTrainer(A, trainer(m).instanceId(), null);
        UUID controller = UUID.randomUUID(); var session = MatchSession.practice(m, controller, A, B);
        long firstStep = -1;
        for (int i = 0; i < 2; i++) {
            var p = m.effectCardPrompt(A); var card = p.candidates().getFirst();
            if (i == 0) firstStep = p.step(); else check(p.step() > firstStep, "重复效果复用了旧步骤");
            m.submitEffectCards(A, p.resolutionId(), p.step(), List.of(card.instanceId()));
            var t = m.effectAttachmentPrompt(A);
            check(session.view(controller, B).game().effectAttachment() == null, "练习乙方看到甲方待附能量");
            var v = session.view(controller, A); UUID dest = t.targets().get(i);
            var action = v.options().stream().filter(o -> o.cardKeys().contains("field:" + dest)).findFirst().orElseThrow();
            session.submit(controller, A, v.revision(), action.index());
        }
        check(m.benchPokemonView(A).stream().allMatch(p -> p.attachedEnergyIds().size() == 1), "两个步骤不能独立选择目标");
        check(!m.energyAttached(A), "效果附能错误占用手动附着次数");
    }
    private static void discardAndDeck() {
        var m = fixture("{\"type\":\"discard\",\"target\":\"HAND\",\"card_category\":\"ENERGY\",\"amount\":1,\"minimum\":1}," + ATTACH.replace("HAND", "DISCARD"), 0);
        m.playTrainer(A, trainer(m).instanceId(), null); var discard = m.effectCardPrompt(A);
        UUID selected = discard.candidates().stream().filter(c -> c.definitionId().equals("grass")).findFirst().orElseThrow().instanceId();
        m.submitEffectCards(A, discard.resolutionId(), discard.step(), List.of(selected));
        var pick = m.effectCardPrompt(A); check(pick.candidates().size() == 1 && pick.candidates().getFirst().instanceId().equals(selected), "未读取上一步弃牌结果");
        m.submitEffectCards(A, pick.resolutionId(), pick.step(), List.of(selected)); var dest = m.effectAttachmentPrompt(A);
        m.chooseEffectAttachmentTarget(A, dest.resolutionId(), dest.step(), dest.targets().getFirst());
        check(m.privateView(A).discard().stream().noneMatch(c -> c.instanceId().equals(selected)), "弃牌回收附能后复制了能量");
        var deck = fixture(ATTACH.replace("HAND", "DECK"), 0); deck.playTrainer(A, trainer(deck).instanceId(), null);
        var p = deck.effectCardPrompt(A); var before = deck.randomTranscript();
        var sorted = p.candidates().stream().sorted(Comparator.comparing(CardInstance::definitionId).thenComparing(c -> c.instanceId().toString())).toList();
        check(sorted.equals(p.candidates()), "附能检索泄露牌库顺序");
        deck.submitEffectCards(A, p.resolutionId(), p.step(), List.of(p.candidates().getFirst().instanceId()));
        var target = deck.effectAttachmentPrompt(A); check(before.equals(deck.randomTranscript()), "未完成选择先洗牌");
        deck.chooseEffectAttachmentTarget(A, target.resolutionId(), target.step(), target.targets().getFirst());
        check(deck.randomTranscript().stream().anyMatch(e -> e.operation().startsWith("effect-attach-deck-energy:")), "牌库附能后未洗牌");
    }
    private static void coinAndNoEffect() {
        var outcomes = new HashSet<Boolean>();
        for (int seed = 0; seed < 100 && outcomes.size() < 2; seed++) {
            var m = fixture("{\"type\":\"coin_flip\",\"flips\":1,\"heads\":[" + ATTACH + "]}", seed);
            var rng = m.randomTranscript(); var card = trainer(m);
            for (int i = 0; i < 3; i++) check(m.canPlayTrainer(A, card.instanceId(), null), "掷币前错误要求选择目标");
            check(rng.equals(m.randomTranscript()), "预检掷币");
            m.playTrainer(A, card.instanceId(), null); outcomes.add(m.hasPendingCardSelection());
            check(m.randomTranscript().size() == rng.size() + 1, "实际硬币不是一次");
        }
        check(outcomes.equals(Set.of(true, false)), "未覆盖正反两种结果");
        var empty = fixture(ATTACH.replace("HAND", "DISCARD"), 0); var before = empty.privateView(A);
        rejects(() -> empty.playTrainer(A, trainer(empty).instanceId(), null)); check(before.equals(empty.privateView(A)), "无可附能量仍消耗训练家");
        var partial = fixture("{\"type\":\"draw\",\"amount\":1}," + ATTACH.replace("HAND", "DISCARD"), 0);
        partial.playTrainer(A, trainer(partial).instanceId(), null); check(!partial.hasPendingEffectChoice(), "空候选未自动继续");
        var zero = fixture(ATTACH.replace("\"minimum\":1", "\"minimum\":0"), 0);
        zero.playTrainer(A, trainer(zero).instanceId(), null); var prompt = zero.effectCardPrompt(A);
        zero.submitEffectCards(A, prompt.resolutionId(), prompt.step(), List.of()); check(!zero.hasPendingEffectChoice(), "零张选择仍要求选目标");
    }
    private static void malformedAndAtomic() {
        for (String invalid : List.of(ATTACH.replace(",\"minimum\":1", ""), ATTACH.replace("HAND", "LOST_ZONE"),
            ATTACH.replace("OWN_BENCH", "OPPONENT_BENCH"), ATTACH.replace("\"basic_energy_only\":true", "\"basic_energy_only\":false"),
            ATTACH.replace("\"energy_type\":\"GRASS\"", "\"energy_type\":\"BAD\""), ATTACH.replace("\"amount\":1", "\"amount\":61"),
            "{\"type\":\"conditional\",\"conditions\":[{\"type\":\"IS_BASIC\",\"target\":\"SELECT_OWN_POKEMON\"}],\"effects\":[" + ATTACH + "]}",
            ATTACH.replace("\"source\":\"HAND\"", "\"source\":\"HAND\",\"cost\":true"))) {
            var profile = ComposedTrainerIntegrationTest.item("{\"type\":\"draw\",\"amount\":1}," + invalid);
            check(TrainerProgramRules.unsupportedReason(profile.effectProgram()).isPresent(), "未支持参数被悄悄忽略");
            var m = fixture(profile, 0); var before = m.privateView(A); var rng = m.randomTranscript();
            rejects(() -> m.playTrainer(A, trainer(m).instanceId(), null));
            check(before.equals(m.privateView(A)) && rng.equals(m.randomTranscript()), "无效后续效果未在首个操作前拒绝");
        }
    }
    private static void specialCardsAndPartialCounts() {
        String special = ATTACH.replace("SELECT_OWN_POKEMON", "OWN_ACTIVE").replace("OWN_BENCH", "OWN_ACTIVE")
            .replace("\"basic_energy_only\":true", "\"basic_energy_only\":false")
            .replace("\"energy_type\":\"GRASS\"", "\"definition_ids\":\"double\"")
            .replace("\"amount\":1", "\"amount\":2").replace("\"minimum\":1", "\"minimum\":2");
        var m = fixture(special, 0); m.drawCardsByEffect(A, A, 60);
        var definition = ComposedTrainerIntegrationTest.item(special).effectProgram().triggers().getFirst().effects().getFirst();
        rejects(() -> m.openEffectZoneSelection(A, definition, 0));
        var random = m.randomTranscript();
        rejects(() -> m.executeEffectProgramByRule(A, null, ComposedTrainerIntegrationTest.item(special).effectProgram(), EffectTrigger.ON_CARD_PLAYED));
        check(random.equals(m.randomTranscript()), "旧无续接入口仍执行了需选择附能");
        m.playTrainer(A, trainer(m).instanceId(), null); var prompt = m.effectCardPrompt(A);
        check(!prompt.candidates().isEmpty() && prompt.candidates().stream().allMatch(c -> c.definitionId().equals("double")), "特殊能量按编号筛选错误");
        var ids = prompt.candidates().stream().limit(2).map(CardInstance::instanceId).toList();
        m.submitEffectCards(A, prompt.resolutionId(), prompt.step(), ids); var target = m.effectAttachmentPrompt(A);
        check(target.targets().equals(List.of(m.activePokemonView(A).pokemonId())), "固定战斗位附能允许其它目标");
        m.chooseEffectAttachmentTarget(A, target.resolutionId(), target.step(), target.targets().getFirst());
        check(m.activePokemonView(A).attachedEnergyIds().equals(ids), "将能量单位数量误当成实际卡张数");
        var partial = fixture(ATTACH.replace("\"amount\":1", "\"amount\":60").replace("\"minimum\":1", "\"minimum\":60"), 0);
        partial.playTrainer(A, trainer(partial).instanceId(), null); var p = partial.effectCardPrompt(A);
        check(p.minimum() == p.candidates().size() && p.minimum() < 60, "能量不足数量未按效果尽量执行");
        partial.submitEffectCards(A, p.resolutionId(), p.step(), p.candidates().stream().map(CardInstance::instanceId).toList());
        var t = partial.effectAttachmentPrompt(A); partial.chooseEffectAttachmentTarget(A, t.resolutionId(), t.step(), t.targets().getFirst());
        check(partial.benchPokemonView(A).getFirst().attachedEnergyIds().size() == p.candidates().size(), "不足数量时漏附能量");
    }
    private static void knockoutBoundary() {
        var m = fixture("{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":12}," + ATTACH + ",{\"type\":\"draw\",\"amount\":1}", 0);
        m.playTrainer(A, trainer(m).instanceId(), null); var p = m.effectCardPrompt(A);
        m.submitEffectCards(A, p.resolutionId(), p.step(), List.of(p.candidates().getFirst().instanceId())); var t = m.effectAttachmentPrompt(A);
        check(m.phase() == MatchPhase.TURN, "选目标前就处理击倒");
        m.chooseEffectAttachmentTarget(A, t.resolutionId(), t.step(), t.targets().getFirst());
        check(m.phase() == MatchPhase.PRIZE_SELECTION, "整卡完成后未处理击倒");
        var end = fixture(ATTACH, 0); end.playTrainer(A, trainer(end).instanceId(), null); p = end.effectCardPrompt(A);
        end.submitEffectCards(A, p.resolutionId(), p.step(), List.of(p.candidates().getFirst().instanceId())); var old = end.effectAttachmentPrompt(A);
        end.concede(B); check(end.clientView(A).effectAttachment() == null, "认输后残留隐藏选牌");
        rejects(() -> end.chooseEffectAttachmentTarget(A, old.resolutionId(), old.step(), old.targets().getFirst()));
    }
    static ServerMatch fixture(String effects, int seed) { return fixture(ComposedTrainerIntegrationTest.item(effects), seed); }
    private static ServerMatch fixture(TrainerProfile profile, int start) {
        var cards = new ArrayList<CardDefinition>();
        for (String id : List.of("basic", "component", "grass", "fire", "double", "tool")) cards.add(new CardDefinition(id, "夹具" + id,
            id.equals("basic") ? CardCategory.POKEMON : id.equals("component") || id.equals("tool") ? CardCategory.TRAINER : CardCategory.ENERGY,
            id.equals("component") ? profile.category() : id.equals("tool") ? TrainerCategory.TOOL : TrainerCategory.NONE, "H", Set.of("international"), id.equals("basic"),
            id.equals("grass") || id.equals("fire"), 60, Map.of(), EffectSupport.SUPPORTED, null));
        var database = new CardDatabase(cards);
        var combat = new CombatCatalog(Map.of("basic", new PokemonProfile(120, 0, null, null, 0, null, 0, 1, List.of())),
            Map.of("grass", EnergyType.GRASS, "fire", EnergyType.FIRE), Map.of("component", profile,
                "tool", new TrainerProfile(TrainerCategory.TOOL, new TrainerEffect(TrainerEffect.Opcode.PASSIVE_TOOL, 0, 0),
                    new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.PASSIVE, List.of(), List.of(
                        new EffectDefinition("tool_capacity", EffectTarget.SELF, Map.of("amount", "1"), List.of(), List.of()))))))),
            Map.of("double", EnergyProfile.fixed(EnergyType.COLORLESS, 2)));
        for (int seed = start; seed < start + 100000; seed++) {
            var m = new ServerMatch(A, B, deck(A), deck(B), RULE, database, combat, new ServerRandom(seed));
            OpeningTestSupport.decideOrder(m, A, B);
            if (m.privateView(A).hand().stream().filter(c -> c.definitionId().equals("basic")).count() < 3
                || m.privateView(A).hand().stream().filter(c -> c.definitionId().equals("grass")).count() < 2
                || m.privateView(A).hand().stream().noneMatch(c -> c.definitionId().equals("component"))
                || m.privateView(B).hand().stream().filter(c -> c.definitionId().equals("basic")).count() < 3) continue;
            OpeningTestSupport.complete(m, A, B, 2); return m;
        }
        throw new AssertionError("未找到起手夹具");
    }
    private static List<CardInstance> deck(UUID owner) {
        var cards = new ArrayList<CardInstance>();
        for (int i = 0; i < 60; i++) cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(), i + 1), owner,
            i < 26 ? "basic" : i < 36 ? "component" : i < 54 ? "grass" : i < 57 ? "fire" : i < 59 ? "double" : "tool"));
        return cards;
    }
    private static CardInstance trainer(ServerMatch m) { return m.privateView(A).hand().stream().filter(c -> c.definitionId().equals("component")).findFirst().orElseThrow(); }
    private static void rejects(Runnable action) { try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; } throw new AssertionError("非法输入未被拒绝"); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
