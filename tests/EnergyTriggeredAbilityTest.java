import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Public card data -> real manual/composed attachment -> deferred authoritative Ability queue. */
public final class EnergyTriggeredAbilityTest {
    static final UUID A = new UUID(0, 9041), B = new UUID(0, 9042), WATCH = new UUID(0, 9043);
    static final String ATTACH = "{\"type\":\"attach_energy\",\"target\":\"OWN_ACTIVE\",\"source\":\"HAND\",\"amount\":1,\"minimum\":1,\"target_scope\":\"OWN_ACTIVE\"}";
    public static void main(String[] args) {
        automaticManual(); orderingAndOwnership(); wholeCardFirst(); optionalAndRates();
        filtersAndRates(); opponentOptionalAndPractice(); knockoutContinuation(); validationAndIsolation();
        disabledAndSharedRules(); sequentialEvents();
        movingIsNotAttachment();
        publicPresentation();
        System.out.println("EnergyTriggeredAbilityTest passed: actual attachments, deferred text, ordering owner, optional use, rate limits and privacy");
    }
    private static void automaticManual() {
        var m = fixture(List.of(ability("draw", "SELF", false, "NONE", "{\"type\":\"draw\",\"amount\":1}")), List.of(), null);
        var before = m.privateView(A); UUID active = m.activePokemonView(A).pokemonId();
        check(!m.canUseAbility(A, active, "draw"), "触发特性错误成为手动按钮");
        m.attachEnergy(A, energy(m).instanceId(), active);
        check(m.privateView(A).hand().size() == before.hand().size() && m.privateView(A).deck().size() == before.deck().size() - 1,
            "真实手动附能没有触发抽牌");
        check(m.triggerOrderPrompt() == null && m.energyAttached(A), "单个必发触发未自动完成或改变手动次数");
    }
    private static void publicPresentation(){
        var ability=ability("public-draw","SELF",false,"NONE","{\"type\":\"draw\",\"amount\":1}");
        var m=fixture(List.of(ability),List.of(),null);var session=MatchSession.multiplayer(m,A,B);session.addSpectator(WATCH);
        var view=session.view(A,A);UUID source=m.activePokemonView(A).pokemonId();
        var option=view.options().stream().filter(o->o.type()==ActionType.ATTACH&&o.cardKeys().contains("field:"+source)).findFirst().orElseThrow();
        session.submit(A,A,view.revision(),option.index());
        for(UUID viewer:List.of(A,B,WATCH)){
            var after=session.view(viewer,viewer.equals(WATCH)?null:viewer);
            var events=after.events().stream().map(BattleEventJournal.Event::cue).filter(c->c.kind()==dev.hua.ptcglive.view.MatchPresentation.Kind.ABILITY).toList();
            check(events.size()==1&&events.getFirst().pokemon().equals(source)&&events.getFirst().text().equals(ability.nameZh()),"公开触发特性名称/定位丢失或重复");
        }
    }
    private static void orderingAndOwnership() {
        var own = ability("own", "SELF", false, "NONE", "{\"type\":\"draw\",\"amount\":1}");
        var other = ability("other", "OPPONENT_FIELD", false, "NONE", "{\"type\":\"draw\",\"amount\":2}");
        var m = fixture(List.of(own), List.of(other), null); var session = MatchSession.multiplayer(m, A, B); session.addSpectator(WATCH);
        int aHand = m.privateView(A).hand().size(), bHand = m.privateView(B).hand().size();
        m.attachEnergy(A, energy(m).instanceId(), m.activePokemonView(A).pokemonId());
        var prompt = m.triggerOrderPrompt(); check(prompt != null && prompt.choosingPlayer().equals(A), "当前回合玩家没有选择同时触发顺序");
        var next = prompt.entries().stream().filter(e -> e.owner().equals(B)).findFirst().orElseThrow();
        rejects(() -> m.chooseTriggeredEffect(B, prompt.id(), next.id()));
        rejects(() -> m.chooseTriggeredEffect(A, UUID.randomUUID(), next.id()));
        rejects(() -> m.endTurn(A));
        check(m.privateView(A).hand().size() == aHand - 1 && m.privateView(B).hand().size() == bHand, "选顺序前提前执行触发");
        m.chooseTriggeredEffect(A, prompt.id(), next.id());
        check(m.privateView(B).hand().size() >= bHand + 2, "对手特性因不在己方回合被阻止或抽错玩家");
        while (m.triggerOrderPrompt() != null) { var p = m.triggerOrderPrompt(); m.chooseTriggeredEffect(A, p.id(), p.entries().getFirst().id()); }
        check(m.privateView(A).hand().size() == aHand, "己方触发未结算");
        for (UUID receiver : List.of(B, WATCH)) {
            String json = new com.google.gson.Gson().toJson(session.view(receiver, receiver.equals(B) ? B : null));
            for (var hidden : m.privateView(A).hand()) check(!json.contains(hidden.instanceId().toString()), "触发结果泄露手牌");
        }
        rejects(() -> m.chooseTriggeredEffect(A, prompt.id(), next.id()));
    }
    private static void wholeCardFirst() {
        var trigger = ability("deferred", "SELF", false, "NONE", "{\"type\":\"draw\",\"amount\":2}");
        var m = fixture(List.of(trigger), List.of(), ATTACH + ",{\"type\":\"choice\",\"options\":[{\"type\":\"draw\",\"amount\":1},{\"type\":\"draw\",\"amount\":2}]}");
        int before = m.privateView(A).hand().size(); var played = trainer(m);
        m.playTrainer(A, played.instanceId(), null); var pick = m.effectCardPrompt(A);
        m.submitEffectCards(A, pick.resolutionId(), pick.step(), List.of(pick.candidates().getFirst().instanceId()));
        var target = m.effectAttachmentPrompt(A); m.chooseEffectAttachmentTarget(A, target.resolutionId(), target.step(), target.targets().getFirst());
        check(m.privateView(A).hand().size() == before - 2 && m.triggerOrderPrompt() == null, "触发插入原卡 Choice 中途");
        var choice = m.effectChoicePrompt(A); m.chooseEffectOption(A, choice.resolutionId(), choice.step(), 0);
        check(m.privateView(A).hand().size() == before + 1 && m.privateView(A).discard().contains(played), "整卡结尾没有继续触发");
        var replay = m.replayTranscript(); int cardEnd = -1, triggerStart = -1;
        for (int i = 0; i < replay.size(); i++) { if (replay.get(i).operation().contains("完成组合训练家")) cardEnd = i; if (replay.get(i).operation().contains("触发特性")) triggerStart = i; }
        check(cardEnd >= 0 && triggerStart > cardEnd, "回放触发时序错误");
    }
    private static void optionalAndRates() {
        var m = fixture(List.of(ability("optional", "SELF", true, "ONCE_PER_TURN", "{\"type\":\"draw\",\"amount\":1}")), List.of(), null);
        UUID active = m.activePokemonView(A).pokemonId();
        m.attachEnergy(A, energy(m).instanceId(), active); var prompt = m.triggerOrderPrompt();
        check(prompt.optionalDecision() && prompt.choosingPlayer().equals(A), "可选特性未让拥有者决定");
        m.decideTriggeredEffect(A, prompt.id(), false); int hand = m.privateView(A).hand().size();
        m.attachEnergyByEffect(A, A, EffectSourceZone.HAND, energy(m).instanceId(), A, active);
        prompt = m.triggerOrderPrompt(); check(prompt != null, "拒绝可选触发错误消耗次数");
        m.decideTriggeredEffect(A, prompt.id(), true);
        check(m.privateView(A).hand().size() == hand, "确认触发抽牌错误");
        m.attachEnergyByEffect(A, A, EffectSourceZone.HAND, energy(m).instanceId(), A, active);
        check(m.triggerOrderPrompt() == null, "限次触发重复执行");
    }
    private static void filtersAndRates() {
        var base = ability("filtered", "SELF", false, "ONCE_PER_GAME", "{\"type\":\"draw\",\"amount\":1}");
        var policy = new EnergyTriggerPolicy(EnergyTriggerPolicy.Target.SELF, EnergyTriggerPolicy.Source.DECK,
            EnergyTriggerPolicy.Cause.EFFECT, false, EnergyTriggerPolicy.Usage.ONCE_PER_GAME, true);
        var filtered = new AbilityDefinition(base.id(), base.nameZh(), base.effect(), base.effectSupport(), null, base.kind(), base.effectProgram(), policy);
        var m = fixture(List.of(filtered), List.of(), null); var active = m.activePokemonView(A).pokemonId();
        int hand = m.privateView(A).hand().size(); m.attachEnergy(A, energy(m).instanceId(), active);
        check(m.privateView(A).hand().size() == hand - 1, "手动手牌附能误触发效果牌库过滤");
        for (int i = 0; i < 2; i++) {
            var energy = m.privateView(A).deck().stream().filter(c -> c.definitionId().equals("energy")).findFirst().orElseThrow();
            m.attachEnergyByEffect(A, A, EffectSourceZone.DECK, energy.instanceId(), A, active);
        }
        check(m.privateView(A).hand().size() == hand, "来源过滤或每局一次错误");
        m.endTurn(A); m.endTurn(B);
        int afterTurns = m.privateView(A).hand().size();
        var energy = m.privateView(A).deck().stream().filter(c -> c.definitionId().equals("energy")).findFirst().orElseThrow();
        m.attachEnergyByEffect(A, A, EffectSourceZone.DECK, energy.instanceId(), A, active);
        check(m.privateView(A).hand().size() == afterTurns, "回合切换重置每局一次");
        check(!policy.matches(A, active, A, active, EnergyTriggerPolicy.Source.DECK, EnergyTriggerPolicy.Cause.EFFECT, false), "特殊能量误当基础能量");
    }
    private static void opponentOptionalAndPractice() {
        var m = fixture(List.of(), List.of(ability("other", "OPPONENT_FIELD", true, "NONE", "{\"type\":\"draw\",\"amount\":1}")), null);
        var session = MatchSession.multiplayer(m, A, B); session.addSpectator(WATCH);
        m.attachEnergy(A, energy(m).instanceId(), m.activePokemonView(A).pokemonId());
        var snap = session.view(A, A); var choose = snap.options().stream().filter(o -> o.labelZh().startsWith("优先结算触发")).findFirst().orElseThrow();
        check(EffectChoicePageForTest(snap) == 2, "桌面未显示公开触发顺序");
        session.submit(A, A, snap.revision(), choose.index());
        var p = m.triggerOrderPrompt(); check(p.optionalDecision() && p.choosingPlayer().equals(B) && m.pendingChoicePlayer().equals(B), "误让当前玩家替对手决定用不用特性");
        rejects(() -> m.decideTriggeredEffect(A, p.id(), true));
        check(session.view(A, A).options().size() == 1 && session.view(WATCH, null).options().isEmpty(), "非拥有者收到特性确认按钮");
        int before = m.privateView(B).hand().size(); var reopened = session.view(B, B);
        check(p.equals(reopened.game().triggerOrder()) && m.privateView(B).hand().size() == before, "刷新重放触发");
        var use = reopened.options().stream().filter(o -> o.labelZh().startsWith("使用触发特性")).findFirst().orElseThrow();
        session.submit(B, B, reopened.revision(), use.index());
        rejects(() -> session.submit(B, B, reopened.revision(), use.index()));
        check(m.privateView(B).hand().size() == before + 1, "拥有者未收到抽牌");
        m.concede(A); check(m.triggerOrderPrompt() == null, "结束残留触发提示");

        var practiceMatch = fixture(List.of(ability("practice", "SELF", true, "NONE", "{\"type\":\"draw\",\"amount\":1}")), List.of(), null);
        var practice = MatchSession.practice(practiceMatch, WATCH, A, B);
        practiceMatch.attachEnergy(A, energy(practiceMatch).instanceId(), practiceMatch.activePokemonView(A).pokemonId());
        check(practice.view(WATCH, B).options().stream().noneMatch(o -> o.labelZh().startsWith("使用触发特性")), "练习另一席位越权");
        var own = practice.view(WATCH, A); var decline = own.options().stream().filter(o -> o.labelZh().equals("放弃触发特性")).findFirst().orElseThrow();
        practice.submit(WATCH, A, own.revision(), decline.index()); check(practiceMatch.triggerOrderPrompt() == null, "练习确认未完成");
    }
    private static int EffectChoicePageForTest(MatchSession.Snapshot view) { return dev.hua.ptcglive.view.EffectChoicePage.from(view, 0).options().size(); }
    private static void knockoutContinuation() {
        var base = ability("latched", "SELF", false, "NONE", "{\"type\":\"draw\",\"amount\":1}");
        var condition = new ConditionDefinition(ConditionType.ENERGY_COUNT, Map.of("target", "SELF", "value", "1"), List.of());
        var program = new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_ENERGY_ATTACHED, List.of(condition), base.effectProgram().triggers().getFirst().effects())));
        var latched = new AbilityDefinition(base.id(), base.nameZh(), base.effect(), base.effectSupport(), null, base.kind(), program, base.eventPolicy());
        var m = fixture(List.of(latched), List.of(), ATTACH + ",{\"type\":\"damage_counter\",\"target\":\"OWN_ACTIVE\",\"amount\":12}");
        int hand = m.privateView(A).hand().size(); m.playTrainer(A, trainer(m).instanceId(), null);
        var pick = m.effectCardPrompt(A); m.submitEffectCards(A, pick.resolutionId(), pick.step(), List.of(pick.candidates().getFirst().instanceId()));
        var target = m.effectAttachmentPrompt(A); m.chooseEffectAttachmentTarget(A, target.resolutionId(), target.step(), target.targets().getFirst());
        check(m.phase() == MatchPhase.PRIZE_SELECTION && m.privateView(A).hand().size() == hand - 2, "原卡击倒未完成就开始触发");
        m.takePrizeAt(B, 0); m.chooseReplacement(A, m.benchPokemonView(A).getFirst().pokemonId());
        check(m.phase() == MatchPhase.TURN && m.privateView(A).hand().size() == hand - 1, "击倒后漏掉已捕获触发或重读不存在来源条件");

        var counter = ability("counter", "SELF", false, "NONE", "{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":12}");
        m = fixture(List.of(counter, base), List.of(), null); hand = m.privateView(A).hand().size();
        m.attachEnergy(A, energy(m).instanceId(), m.activePokemonView(A).pokemonId()); var order = m.triggerOrderPrompt();
        var entry = order.entries().stream().filter(e -> e.nameZh().contains("counter")).findFirst().orElseThrow();
        m.chooseTriggeredEffect(A, order.id(), entry.id()); check(m.phase() == MatchPhase.PRIZE_SELECTION, "自动伤害未进入共用击倒流程");
        m.takePrizeAt(A, 0); m.chooseReplacement(B, m.benchPokemonView(B).getFirst().pokemonId());
        check(m.phase() == MatchPhase.TURN && m.privateView(A).hand().size() == hand + 1, "两触发间奖赏替补没有恢复队列");
    }
    private static void validationAndIsolation() {
        rejects(() -> fixture(List.of(ability("unsupported", "SELF", false, "NONE", "{\"type\":\"draw\",\"amount\":1},{\"type\":\"switch\",\"target\":\"OWN_BENCH\"}")), List.of(), null));
        rejects(() -> fixture(List.of(ability("too-many", "SELF", false, "NONE", "{\"type\":\"damage_counter\",\"amount\":61}")), List.of(), null));
        var rules = BundledRules.loadRulesets();
        check(rules.get("standard-international-2026").triggerTiming() == TriggerTiming.AFTER_CARD_ACTIVE_PLAYER_V1, "国际规则未启用已核验触发");
        check(rules.get("starter-deck-100-cn-community-2024").triggerTiming() == TriggerTiming.AFTER_CARD_ACTIVE_PLAYER_V1,
            "SD100 休闲包没有显式启用已测试的幸运能量触发时序");
        check(rules.values().stream().filter(r -> !Set.of("standard-international-2026", "custom-open-all", "starter-deck-100-cn-community-2024").contains(r.id()))
            .allMatch(r -> r.triggerTiming() == TriggerTiming.UNVERIFIED), "地区／历史规则错误继承新触发时序");
        var base = ability("valid", "SELF", false, "NONE", "{\"type\":\"draw\",\"amount\":1}");
        var definition = new CardDefinition("a", "规则夹具", CardCategory.POKEMON, TrainerCategory.NONE, "H", Set.of("international"), true, false, 60, Map.of(), EffectSupport.SUPPORTED, null);
        var report = new DeckValidator(new CardDatabase(List.of(definition)), new CombatCatalog(Map.of("a", profile(List.of(base))), Map.of(), Map.of()))
            .validate(rules.values().stream().filter(r -> r.triggerTiming() == TriggerTiming.UNVERIFIED).findFirst().orElseThrow(), List.of(new CardInstance(UUID.randomUUID(), A, "a")));
        check(report.issues().stream().anyMatch(i -> i.code().equals("触发时序未核验")), "卡组检查器未报告地区触发未核验");
        rejects(() -> EnergyTriggerPolicy.parse(JsonParser.parseString("{\"target\":\"SELF\",\"source\":\"ANY\",\"cause\":\"ANY\",\"usage\":\"NONE\",\"optional\":\"true\"}").getAsJsonObject()));
    }
    private static void disabledAndSharedRules() {
        var base = ability("shared", "SELF", false, "ONCE_PER_TURN",
            "{\"type\":\"heal\",\"target\":\"SELF\",\"amount\":10},{\"type\":\"poison\",\"target\":\"OPPONENT_ACTIVE\"},{\"type\":\"coin_flip\",\"flips\":1,\"effects\":[{\"type\":\"draw\",\"amount\":1}]}");
        var m = fixture(List.of(base), List.of(), null); var source = m.activePokemonView(A).pokemonId();
        m.placeDamageCountersByEffect(A, A, source, 2); m.disableAbilityEffectsByEffect(A, A, source);
        var random = m.randomTranscript(); m.attachEnergy(A, energy(m).instanceId(), source);
        check(m.activePokemonView(A).damage() == 20 && m.activePokemonView(B).conditions().isEmpty() && m.randomTranscript().equals(random), "失效特性仍触发或消费硬币");
        m.endTurn(A); m.endTurn(B); int count = m.randomTranscript().size();
        m.attachEnergy(A, energy(m).instanceId(), source);
        check(m.activePokemonView(A).damage() == 10 && m.activePokemonView(B).conditions().contains(SpecialCondition.POISONED)
            && m.randomTranscript().size() == count + 1, "回复／状态／随机没有复用服务器规则");
        m.attachEnergyByEffect(A, A, EffectSourceZone.HAND, energy(m).instanceId(), A, source);
        check(m.activePokemonView(A).damage() == 10 && m.randomTranscript().size() == count + 1, "本回合特性重复使用");
    }
    private static void sequentialEvents() {
        var trigger = ability("sequence", "SELF", true, "NONE", "{\"type\":\"draw\",\"amount\":1}");
        var m = fixture(List.of(trigger), List.of(), "{\"type\":\"repeat\",\"count\":2,\"effects\":[" + ATTACH + "]}");
        m.playTrainer(A, trainer(m).instanceId(), null);
        for (int i = 0; i < 2; i++) {
            var pick = m.effectCardPrompt(A); m.submitEffectCards(A, pick.resolutionId(), pick.step(), List.of(pick.candidates().getFirst().instanceId()));
            var target = m.effectAttachmentPrompt(A); m.chooseEffectAttachmentTarget(A, target.resolutionId(), target.step(), target.targets().getFirst());
        }
        for (int i = 0; i < 2; i++) {
            var prompt = m.triggerOrderPrompt(); check(prompt != null && prompt.optionalDecision() && prompt.entries().size() == 1, "将先后两个事件混成可重排的同时触发");
            m.decideTriggeredEffect(A, prompt.id(), false);
        }
        check(m.triggerOrderPrompt() == null, "连续事件队列未结束");
    }
    private static void movingIsNotAttachment() {
        var m = fixture(List.of(ability("move", "SELF", false, "NONE", "{\"type\":\"draw\",\"amount\":1}")), List.of(), null);
        var active = m.activePokemonView(A).pokemonId(); var bench = m.benchPokemonView(A).getFirst().pokemonId();
        var energy = energy(m); m.attachEnergy(A, energy.instanceId(), active);
        int deck = m.privateView(A).deck().size(); int hand = m.privateView(A).hand().size();
        m.moveAttachedEnergyByEffect(A, A, active, A, bench, List.of(energy.instanceId()));
        check(m.privateView(A).deck().size() == deck && m.privateView(A).hand().size() == hand && m.triggerOrderPrompt() == null,
            "移动既有能量错误作为新附着再触发");
    }
    static AbilityDefinition ability(String id, String target, boolean optional, String usage, String effects) {
        return BundledCombatData.ability(JsonParser.parseString("{\"id\":\"" + id + "\",\"nameZh\":\"触发夹具" + id
            + "\",\"kind\":\"TRIGGERED\",\"effectSupport\":\"SUPPORTED\",\"eventPolicy\":{\"target\":\"" + target
            + "\",\"source\":\"ANY\",\"cause\":\"ANY\",\"optional\":" + optional + ",\"usage\":\"" + usage
            + "\"},\"triggers\":[{\"trigger\":\"ON_ENERGY_ATTACHED\",\"effects\":[" + effects + "]}]}" ).getAsJsonObject(), EffectRegistry.standard());
    }
    static ServerMatch fixture(List<AbilityDefinition> own, List<AbilityDefinition> other, String effects) {
        var trainer = ComposedTrainerIntegrationTest.item(effects == null ? "{\"type\":\"draw\",\"amount\":1}" : effects);
        var defs = new ArrayList<CardDefinition>();
        for (String id : List.of("a", "b", "energy", "trainer")) defs.add(new CardDefinition(id, "夹具" + id,
            id.equals("energy") ? CardCategory.ENERGY : id.equals("trainer") ? CardCategory.TRAINER : CardCategory.POKEMON,
            id.equals("trainer") ? TrainerCategory.ITEM : TrainerCategory.NONE, "H", Set.of("international"), id.length() == 1, id.equals("energy"), 60, Map.of(), EffectSupport.SUPPORTED, null));
        var combat = new CombatCatalog(Map.of("a", profile(own), "b", profile(other)), Map.of("energy", EnergyType.GRASS), Map.of("trainer", trainer));
        for (int seed = 0; seed < 10000; seed++) {
            var m = new ServerMatch(A, B, deck(A, "a"), deck(B, "b"), BundledRules.loadRulesets().get("standard-international-2026"), new CardDatabase(defs), combat, new ServerRandom(seed));
            OpeningTestSupport.decideOrder(m, A, B);
            if (m.privateView(A).hand().stream().filter(c -> c.definitionId().equals("a")).count() < 2
                || m.privateView(A).hand().stream().filter(c -> c.definitionId().equals("energy")).count() < 3
                || m.privateView(A).hand().stream().noneMatch(c -> c.definitionId().equals("trainer"))
                || m.privateView(B).hand().stream().filter(c -> c.definitionId().equals("b")).count() < 2) continue;
            OpeningTestSupport.complete(m, A, B, 1); return m;
        }
        throw new AssertionError("缺少测试起手");
    }
    private static PokemonProfile profile(List<AbilityDefinition> abilities) { return new PokemonProfile(120, 0, null, null, 0, null, 0, 1, List.of(), abilities); }
    private static List<CardInstance> deck(UUID owner, String pokemon) { var result = new ArrayList<CardInstance>(); for (int i = 0; i < 60; i++) result.add(new CardInstance(new UUID(owner.getLeastSignificantBits(), i+1), owner, i < 24 ? pokemon : i < 48 ? "energy" : "trainer")); return result; }
    private static CardInstance energy(ServerMatch m) { return m.privateView(A).hand().stream().filter(c -> c.definitionId().equals("energy")).findFirst().orElseThrow(); }
    private static CardInstance trainer(ServerMatch m) { return m.privateView(A).hand().stream().filter(c -> c.definitionId().equals("trainer")).findFirst().orElseThrow(); }
    private static void rejects(Runnable operation) { try { operation.run(); } catch (IllegalStateException | IllegalArgumentException expected) { return; } throw new AssertionError("非法操作未拒绝"); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
