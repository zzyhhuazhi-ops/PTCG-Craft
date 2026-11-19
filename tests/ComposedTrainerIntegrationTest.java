import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.AttackDefinition;
import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.engine.field.SpecialCondition;
import dev.hua.ptcglive.engine.field.PokemonProfile;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Synthetic data only: JSON -> real catalog/session -> authoritative effect engine. No state mutation shortcuts. */
public final class ComposedTrainerIntegrationTest {
    private static final UUID A = new UUID(0, 7561), B = new UUID(0, 7562), WATCHER = new UUID(0, 7563);
    private static final Ruleset RULE = BundledRules.loadRulesets().get("standard-international-2026");
    public static void main(String[] args) {
        nestedDrawThroughSession();
        supporterLimits();
        selectedPokemonConditionsAndAuthority();
        projectedHandAndFinalDiscard();
        coinDoesNotRollDuringValidation();
        noEffectAndPartialDraw();
        knockoutWaitsForWholeTrainer();
        devolutionKeepsPublicIdentityAndCompletesText();
        devolutionCanApplySameTurnEvolutionLock();
        actionLocksAreReusableComposedEffects();
        cardTextSuppressionIsReusableComposedEffect();
        statusTargetsAndOpponentDraw();
        malformedProgramsAreAtomicAndReported();
        mixedLegacyDataIsRejected();
        System.out.println("ComposedTrainerIntegrationTest passed: JSON/session execution, targets, limits, coins, privacy, atomic rejection and KO boundary");
    }
    private static void nestedDrawThroughSession() {
        var profile = item("""
            {"type":"repeat","count":2,"effects":[{"type":"draw","target":"SELF","amount":1}]}
            """);
        check(profile.composed(), "JSON 没有解析为组合训练家");
        var m = fixture(profile); var card = hand(m, A);
        var session = MatchSession.multiplayer(m, A, B); session.addSpectator(WATCHER);
        var snapshot = session.view(A, A); var action = action(snapshot, card, null);
        var ownBefore = m.privateView(A); var opponentBefore = session.view(B, B).game().hand();
        rejects(() -> session.submit(B, A, snapshot.revision(), action.index()));
        rejects(() -> session.submit(WATCHER, A, snapshot.revision(), action.index()));
        session.submit(A, A, snapshot.revision(), action.index());
        check(m.privateView(A).hand().size() == ownBefore.hand().size() + 1, "训练家出牌和重复抽牌张数错误");
        check(m.privateView(A).deck().size() == ownBefore.deck().size() - 2 && m.privateView(A).discard().contains(card), "区域移动错误");
        rejects(() -> session.submit(A, A, snapshot.revision(), action.index()));
        rejects(() -> m.playTrainer(A, card.instanceId(), null));
        check(session.view(B, B).game().hand().equals(opponentBefore) && session.view(WATCHER, null).game().hand().isEmpty(), "抽牌泄露他人手牌");
        Set<UUID> hidden = new HashSet<>();
        m.privateView(A).hand().forEach(c -> hidden.add(c.instanceId()));
        m.privateView(A).deck().forEach(c -> hidden.add(c.instanceId()));
        m.privateView(A).prizes().forEach(c -> hidden.add(c.instanceId()));
        for (UUID id : hidden) for (var view : List.of(session.view(B, B), session.view(WATCHER, null)))
            check(!view.toString().contains(id.toString()), "隐藏实例进入对手／观战快照");
        check(m.phase() == MatchPhase.TURN && m.currentPlayer().equals(A), "Item 错误结束回合");
    }
    private static void supporterLimits() {
        var profile = parse("{\"trainerCategory\":\"SUPPORTER\",\"effects\":[{\"type\":\"draw\",\"amount\":1}]}");
        var m = fixture(profile); var card = hand(m, A);
        unchangedReject(m, () -> m.playTrainer(A, card.instanceId(), null));
        check(!m.canPlayTrainer(A, card.instanceId(), null), "先手首回合支援者可用");
        m.endTurn(A);
        m.playTrainer(B, hand(m, B).instanceId(), null); // The second player's first turn is not banned.
        unchangedReject(m, () -> m.playTrainer(B, hand(m, B).instanceId(), null));
        m.endTurn(B);
        m.playTrainer(A, card.instanceId(), null);
        check(!m.mayPlaySupporter(A), "组合支援者未占用次数");
        unchangedReject(m, () -> m.playTrainer(A, hand(m, A).instanceId(), null));
        m.endTurn(A); m.endTurn(B);
        check(m.mayPlaySupporter(A), "新回合支援者次数未恢复");
        m.playTrainer(A, hand(m, A).instanceId(), null);
    }
    private static void selectedPokemonConditionsAndAuthority() {
        var profile = item("""
            {"type":"conditional","conditions":[{"type":"AND","conditions":[
              {"type":"IS_BASIC","target":"SELECT_OWN_POKEMON"},
              {"type":"DAMAGE_COUNTER_COUNT","target":"SELECT_OWN_POKEMON","value":1}]}],
             "effects":[{"type":"heal","target":"SELECT_OWN_POKEMON","amount":10},{"type":"draw","amount":1}]}
            """);
        var m = fixture(profile); var card = hand(m, A);
        UUID own = m.activePokemonView(A).pokemonId(), bench = m.benchPokemonView(A).getFirst().pokemonId();
        unchangedReject(m, () -> m.playTrainer(A, card.instanceId(), bench));
        m.placeDamageCountersByEffect(A, A, own, 3); m.placeDamageCountersByEffect(A, A, bench, 2);
        unchangedReject(m, () -> m.playTrainer(A, card.instanceId(), m.activePokemonView(B).pokemonId()));
        unchangedReject(m, () -> m.playTrainer(A, card.instanceId(), UUID.randomUUID()));
        unchangedReject(m, () -> m.playTrainer(A, card.instanceId(), null));
        var session = MatchSession.multiplayer(m, A, B); var snapshot = session.view(A, A);
        int size = m.privateView(A).hand().size();
        session.submit(A, A, snapshot.revision(), action(snapshot, card, bench).index());
        check(m.benchPokemonView(A).getFirst().damage() == 10 && m.activePokemonView(A).damage() == 30, "选定备战目标未正确回复");
        check(m.privateView(A).hand().size() == size, "后续抽牌没有执行");
    }
    private static void projectedHandAndFinalDiscard() {
        var profile = item("""
            {"type":"conditional","conditions":[{"type":"AND","conditions":[
              {"type":"HAND_SIZE","value":5,"comparison":"="},{"type":"DISCARD_COUNT","value":0,"comparison":"="}]}],
             "effects":[{"type":"draw","amount":1}]}
            """);
        var m = fixture(profile); check(m.privateView(A).hand().size() == 6, "起手夹具张数变化");
        var card = hand(m, A);
        m.playTrainer(A, card.instanceId(), null);
        check(m.privateView(A).hand().size() == 6 && m.privateView(A).discard().equals(List.of(card)),
            "使用中的训练家错误计入手牌或提前放入弃牌区");
    }
    private static void coinDoesNotRollDuringValidation() {
        var profile = item("{\"type\":\"coin_flip\",\"flips\":1,\"heads\":[{\"type\":\"draw\",\"amount\":2}]}");
        var outcomes = new HashSet<Integer>();
        for (int start = 0; start < 80 && outcomes.size() < 2; start++) {
            var m = fixture(profile, start); var card = hand(m, A); int random = m.randomTranscript().size();
            var before = m.privateView(A);
            for (int i = 0; i < 3; i++) {
                check(m.canPlayTrainer(A, card.instanceId(), null), "可能正面生效的训练家被错误拒绝");
                MatchSession.multiplayer(m, A, B).view(A, A);
            }
            check(m.randomTranscript().size() == random && m.privateView(A).equals(before), "预检偷偷掷币或改变手牌");
            m.playTrainer(A, card.instanceId(), null);
            check(m.randomTranscript().size() == random + 1 && m.privateView(A).discard().contains(card), "实际只应掷一次且消耗训练家");
            int drawn = m.privateView(A).hand().size() - before.hand().size() + 1;
            check(drawn == 0 || drawn == 2, "硬币分支张数错误"); outcomes.add(drawn);
        }
        check(outcomes.equals(Set.of(0, 2)), "未覆盖正反两个确定性结果");
    }
    private static void noEffectAndPartialDraw() {
        var heal = fixture(item("{\"type\":\"heal\",\"target\":\"ALL_OWN_POKEMON\",\"amount\":10}"));
        unchangedReject(heal, () -> heal.playTrainer(A, hand(heal, A).instanceId(), null));
        UUID bench = heal.benchPokemonView(A).getFirst().pokemonId();
        heal.placeDamageCountersByEffect(A, A, bench, 1);
        heal.playTrainer(A, hand(heal, A).instanceId(), null);
        check(heal.benchPokemonView(A).getFirst().damage() == 0, "群体回复被满血目标中断");
        var m = fixture(item("{\"type\":\"draw\",\"amount\":60}"));
        var first = hand(m, A); m.playTrainer(A, first.instanceId(), null);
        check(m.privateView(A).deck().isEmpty() && m.result() == null, "效果部分抽牌错误导致牌库抽空败北");
        unchangedReject(m, () -> m.playTrainer(A, hand(m, A).instanceId(), null));
        m.endTurn(A); m.endTurn(B);
        check(m.result().reason() == MatchResult.Reason.DECK_OUT && m.result().winner().equals(B), "必须到回合抽牌才败北");
    }
    private static void knockoutWaitsForWholeTrainer() {
        var m = fixture(item("""
            {"type":"damage_counter","target":"OPPONENT_ACTIVE","amount":12},{"type":"draw","amount":2}
            """));
        var card = hand(m, A); int before = m.privateView(A).hand().size(), turn = m.turnNumber();
        m.playTrainer(A, card.instanceId(), null);
        check(m.phase() == MatchPhase.PRIZE_SELECTION && m.privateView(A).hand().size() == before + 1
            && m.privateView(A).discard().contains(card), "击倒在训练家抽牌／弃置完成前打断结算");
        m.takePrizeAt(A, 0);
        check(m.phase() == MatchPhase.CHOOSE_REPLACEMENT, "未等待对方选替补");
        m.chooseReplacement(B, m.benchPokemonView(B).getFirst().pokemonId());
        check(m.phase() == MatchPhase.TURN && m.currentPlayer().equals(A) && m.turnNumber() == turn, "非攻击击倒错误结束回合");
    }
    /** A data-driven DevolveEffect is not leave-play: it keeps the public Pokémon identity, attachments and damage. */
    private static void devolutionKeepsPublicIdentityAndCompletesText() {
        var fixedProfile = item("""
            {"type":"devolve","target":"OPPONENT_ACTIVE"},{"type":"draw","amount":1}
            """);
        var fixed = devolutionFixture(fixedProfile);
        var m = fixed.match();
        check(TrainerProgramRules.unsupportedReason(fixedProfile.effectProgram()).isEmpty(),
            "已审核的退化组合训练家被错误标为未支持");
        int hand = m.privateView(A).hand().size();
        CardInstance trainer = hand(m, A);
        m.playTrainer(A, trainer.instanceId(), null);
        var target = m.activePokemonView(B);
        check(target.pokemonId().equals(fixed.target()) && target.topCardId().equals("basic") && target.damage() == 30
            && target.conditions().isEmpty() && m.privateView(B).hand().contains(fixed.evolution())
            && m.privateView(A).hand().size() == hand && m.privateView(A).discard().contains(trainer),
            "组合退化没有保留场上身份/伤害、清除状态、回手进化牌或完成后续抽牌");
        unchangedReject(m, () -> m.playTrainer(A, hand(m, A).instanceId(), null));

        var selected = devolutionFixture(item("{\"type\":\"devolve\",\"target\":\"SELECT_OPPONENT_POKEMON\"}"));
        unchangedReject(selected.match(), () -> selected.match().playTrainer(A, hand(selected.match(), A).instanceId(), selected.match().activePokemonView(A).pokemonId()));
        selected.match().playTrainer(A, hand(selected.match(), A).instanceId(), selected.target());
        check(selected.match().activePokemonView(B).pokemonId().equals(selected.target())
            && selected.match().activePokemonView(B).topCardId().equals("basic")
            && selected.match().privateView(B).hand().contains(selected.evolution()),
            "选择对方公开宝可梦的退化没有经过服务器目标校验与统一结算");
    }
    /** Devolution Spray-style text may reuse the same public target without treating it as a leave-play replacement. */
    private static void devolutionCanApplySameTurnEvolutionLock() {
        var fixture = devolutionFixture(item("""
            {"type":"devolve","target":"OPPONENT_ACTIVE"},
            {"type":"prevent_evolution","target":"OPPONENT_ACTIVE"}
            """));
        var m = fixture.match();
        m.playTrainer(A, hand(m, A).instanceId(), null);
        check(m.activePokemonView(B).pokemonId().equals(fixture.target()) && m.activePokemonView(B).topCardId().equals("basic")
            && m.activePokemonView(B).continuousEffectZh().contains("不能进化"),
            "退化后同一公开对象没有接收禁止进化持续效果");
        m.endTurn(A);
        unchangedReject(m, () -> m.evolve(B, fixture.target(), fixture.evolution().instanceId()));
        m.endTurn(B);
        check(!m.activePokemonView(B).continuousEffectZh().contains("不能进化"), "来源玩家下回合开始时进化限制未到期");
        m.endTurn(A);
        check(m.canEvolve(B, fixture.target(), "evolved"), "进化限制到期后服务器仍拒绝正常进化");
        m.evolve(B, fixture.target(), fixture.evolution().instanceId());
        check(m.activePokemonView(B).topCardId().equals("evolved"), "到期后的普通进化没有使用同一服务器路径恢复");
    }
    /** Attack and retreat prohibition share the public target / server expiry path rather than card-specific code. */
    private static void actionLocksAreReusableComposedEffects() {
        var attack = fixture(item("{\"type\":\"prevent_attack\",\"target\":\"OPPONENT_ACTIVE\"}"));
        attack.playTrainer(A, hand(attack, A).instanceId(), null);
        check(attack.activePokemonView(B).continuousEffectZh().contains("不能攻击"),
            "组合禁止攻击没有成为公开场上持续效果");
        attack.endTurn(A);
        check(!attack.mayAttack(B), "组合禁止攻击没有在攻击费用和随机数之前阻止攻击");
        unchangedReject(attack, () -> attack.attack(B, "tap"));
        attack.endTurn(B);
        check(attack.activePokemonView(B).continuousEffectZh().isBlank(),
            "禁止攻击没有在来源玩家下回合开始时到期");

        var retreat = fixture(item("{\"type\":\"prevent_retreat\",\"target\":\"OWN_ACTIVE\"}"));
        UUID original = retreat.activePokemonView(A).pokemonId();
        UUID replacement = retreat.benchPokemonView(A).getFirst().pokemonId();
        retreat.playTrainer(A, hand(retreat, A).instanceId(), null);
        check(retreat.activePokemonView(A).continuousEffectZh().contains("不能撤退"),
            "组合禁止撤退没有成为公开场上持续效果");
        unchangedReject(retreat, () -> retreat.retreat(A, replacement, List.of()));
        check(retreat.activePokemonView(A).pokemonId().equals(original), "受限撤退错误改变了战斗位");
        retreat.endTurn(A); retreat.endTurn(B);
        retreat.retreat(A, replacement, List.of());
        check(retreat.activePokemonView(A).pokemonId().equals(replacement), "禁止撤退到期后服务器仍拒绝普通撤退");
    }
    /** Tool and Ability suppression use the same public target / expiry contract as other continuous components. */
    private static void cardTextSuppressionIsReusableComposedEffect() {
        var abilities = fixture(item("{\"type\":\"disable_abilities\",\"target\":\"OPPONENT_ACTIVE\"}"));
        abilities.playTrainer(A, hand(abilities, A).instanceId(), null);
        check(abilities.activePokemonView(B).continuousEffectZh().contains("特性失效"),
            "组合特性失效没有成为公开服务器持续效果");
        abilities.endTurn(A); abilities.endTurn(B);
        check(abilities.activePokemonView(B).continuousEffectZh().isBlank(), "组合特性失效没有在来源玩家下回合开始时到期");

        var tools = fixture(item("{\"type\":\"disable_tools\",\"target\":\"OWN_ACTIVE\"}"));
        tools.playTrainer(A, hand(tools, A).instanceId(), null);
        check(tools.activePokemonView(A).continuousEffectZh().contains("工具失效"),
            "组合工具失效没有成为公开服务器持续效果");
        tools.endTurn(A); tools.endTurn(B);
        check(tools.activePokemonView(A).continuousEffectZh().isBlank(), "组合工具失效没有在来源玩家下回合开始时到期");
    }
    private static void statusTargetsAndOpponentDraw() {
        var m = fixture(item("""
            {"type":"poison","target":"SELECT_OPPONENT_POKEMON"},{"type":"draw","target":"OPPONENT_ACTIVE","amount":1}
            """));
        var card = hand(m, A);
        unchangedReject(m, () -> m.playTrainer(A, card.instanceId(), m.benchPokemonView(B).getFirst().pokemonId()));
        var session = MatchSession.multiplayer(m, A, B); var snapshot = session.view(A, A);
        int opponent = m.privateView(B).hand().size();
        UUID target = m.activePokemonView(B).pokemonId();
        session.submit(A, A, snapshot.revision(), action(snapshot, card, target).index());
        check(m.activePokemonView(B).conditions().contains(SpecialCondition.POISONED) && m.privateView(B).hand().size() == opponent + 1,
            "对方目标状态／抽牌归属错误");
    }
    private static void malformedProgramsAreAtomicAndReported() {
        for (String effects : List.of(
            "{\"type\":\"draw\",\"amount\":1},{\"type\":\"heal\",\"target\":\"OWN_ACTIVE\",\"amount\":11}",
            "{\"type\":\"draw\",\"amount\":1},{\"type\":\"switch\",\"target\":\"OWN_ACTIVE\"}",
            "{\"type\":\"draw\",\"amount\":1},{\"type\":\"heal\",\"target\":\"SELF\",\"amount\":10}",
            "{\"type\":\"heal\",\"target\":\"SELECT_OWN_POKEMON\",\"amount\":10},{\"type\":\"heal\",\"target\":\"SELECT_OPPONENT_POKEMON\",\"amount\":10}",
            "{\"type\":\"draw\",\"amount\":1,\"conditions\":[{\"type\":\"HAND_SIZE\",\"value\":1,\"comparision\":\"=\"}]}")) {
            var profile = item(effects); var m = fixture(profile); var card = hand(m, A);
            var problem = TrainerProgramRules.unsupportedReason(profile.effectProgram());
            check(problem.isPresent(), "检查器把未支持组合报为可用");
            var report = new DeckValidator(database(profile), combat(profile)).validate(RULE, deck(A));
            check(!report.canStartMatch() && report.issues().stream().anyMatch(i -> i.code().equals("训练家组合未支持")), "卡组检查器未共用运行时支持规则");
            unchangedReject(m, () -> m.playTrainer(A, card.instanceId(), null));
            var session = MatchSession.multiplayer(m, A, B); var snapshot = session.view(A, A);
            var disabled = snapshot.options().stream().filter(o -> o.labelZh().startsWith("训练家组合尚未支持")).findFirst().orElseThrow();
            unchangedReject(m, () -> session.submit(A, A, snapshot.revision(), disabled.index()));
            check(session.audit().isEmpty(), "不支持提示误记为成功操作");
        }
    }
    private static void mixedLegacyDataIsRejected() {
        rejects(() -> parse("{\"trainerCategory\":\"ITEM\",\"effect\":{\"opcode\":\"DRAW\",\"amount\":1},\"effects\":[{\"type\":\"draw\",\"amount\":2}]}"));
        rejects(() -> parse("{\"trainerCategory\":\"ITEM\"}"));
        rejects(() -> parse("{\"trainerCategory\":\"TOOL\",\"effects\":[{\"type\":\"draw\",\"amount\":2}]}"));
        rejects(() -> parse("{\"trainerCategory\":\"STADIUM\",\"effects\":[{\"type\":\"draw\",\"amount\":2}]}"));
        check(!parse("{\"trainerCategory\":\"ITEM\",\"effect\":{\"opcode\":\"DRAW\",\"amount\":1}}").composed(), "旧数据兼容性损坏");
        var wrongTrigger = new TrainerProfile(TrainerCategory.ITEM, new TrainerEffect(TrainerEffect.Opcode.COMPOSED, 0, 0),
            new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_ATTACK, List.of(),
                List.of(new EffectDefinition("draw", EffectTarget.SELF, Map.of("amount", "1"), List.of(), List.of()))))));
        check(TrainerProgramRules.unsupportedReason(wrongTrigger.effectProgram()).isPresent(), "训练家错误触发时机被悄悄忽略");
        var m = fixture(wrongTrigger); unchangedReject(m, () -> m.playTrainer(A, hand(m, A).instanceId(), null));
        rejects(() -> item("""
            {"type":"draw","amount":1,"conditions":[{"type":"NOT","unexpected":"ignored",
              "conditions":[{"type":"HAND_SIZE","value":60}]}]}
            """));
    }
    static TrainerProfile item(String effects) { return parse("{\"trainerCategory\":\"ITEM\",\"effects\":[" + effects + "]}"); }
    private static TrainerProfile parse(String json) { return BundledCombatData.trainer(JsonParser.parseString(json).getAsJsonObject(), EffectRegistry.standard()); }
    private static CardDatabase database(TrainerProfile profile) {
        return new CardDatabase(List.of(
            new CardDefinition("basic", "夹具基础", CardCategory.POKEMON, TrainerCategory.NONE, "H", Set.of("international"), true, false, 60, Map.of(), EffectSupport.SUPPORTED, null),
            new CardDefinition("evolved", "夹具进化", CardCategory.POKEMON, TrainerCategory.NONE, "H", Set.of("international"), false, false, 60, Map.of(), EffectSupport.SUPPORTED, null),
            new CardDefinition("component", "夹具组合训练家", CardCategory.TRAINER, profile.category(), "H", Set.of("international"), false, false, 60, Map.of(), EffectSupport.SUPPORTED, null)));
    }
    private static CombatCatalog combat(TrainerProfile profile) {
        return new CombatCatalog(Map.of(
            "basic", new PokemonProfile(120, 0, null, null, 0, null, 0, 1,
                List.of(new AttackDefinition("tap", "测试拍击", Map.of(), 10, EnergyType.COLORLESS, EffectSupport.SUPPORTED, null))),
            "evolved", new PokemonProfile(180, 0, "basic", null, 0, null, 0, 1, List.of())), Map.of(), Map.of("component", profile));
    }
    static ServerMatch fixture(TrainerProfile profile) { return fixture(profile, 0); }
    private static ServerMatch fixture(TrainerProfile profile, int firstSeed) {
        for (int seed = firstSeed; seed < firstSeed + 10000; seed++) {
            var m = new ServerMatch(A, B, deck(A), deck(B), RULE, database(profile), combat(profile), new ServerRandom(seed));
            OpeningTestSupport.decideOrder(m, A, B);
            if (List.of(A, B).stream().anyMatch(p -> m.privateView(p).hand().stream().filter(c -> c.definitionId().equals("basic")).count() < 2
                || m.privateView(p).hand().stream().filter(c -> c.definitionId().equals("component")).count() < 2)) continue;
            OpeningTestSupport.complete(m, A, B, 1);
            check(m.currentPlayer().equals(A), "夹具必须甲方先手"); return m;
        }
        throw new AssertionError("未找到测试起手");
    }
    private static List<CardInstance> deck(UUID owner) {
        var cards = new ArrayList<CardInstance>();
        for (int i = 0; i < 60; i++) cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(), i + 1), owner, i < 36 ? "basic" : "component"));
        return cards;
    }
    private record DevolutionFixture(ServerMatch match, UUID target, CardInstance evolution) { }
    private static DevolutionFixture devolutionFixture(TrainerProfile profile) {
        for (int seed = 0; seed < 10_000; seed++) {
            var match = new ServerMatch(A, B, devolutionDeck(A), devolutionDeck(B), RULE, database(profile), combat(profile), new ServerRandom(seed));
            OpeningTestSupport.decideOrder(match, A, B);
            if (List.of(A, B).stream().anyMatch(player -> match.privateView(player).hand().stream()
                .filter(card -> card.definitionId().equals("basic")).count() < 2)) continue;
            OpeningTestSupport.complete(match, A, B, 1);
            if (!match.currentPlayer().equals(A) || match.privateView(A).hand().stream().noneMatch(card -> card.definitionId().equals("component"))
                || match.privateView(B).hand().stream().noneMatch(card -> card.definitionId().equals("evolved"))) continue;
            UUID target = match.activePokemonView(B).pokemonId();
            // Ordinary evolution requires the owner's second turn. Advance a
            // full harmless round rather than granting a card-effect timing
            // exception in this integration fixture.
            match.endTurn(A);
            match.endTurn(B);
            match.endTurn(A);
            CardInstance evolution = match.privateView(B).hand().stream().filter(card -> card.definitionId().equals("evolved")).findFirst().orElseThrow();
            match.evolve(B, target, evolution.instanceId());
            match.placeDamageCountersByEffect(B, B, target, 2);
            match.applySpecialConditionByEffect(B, B, SpecialCondition.POISONED);
            match.endTurn(B);
            return new DevolutionFixture(match, target, evolution);
        }
        throw new AssertionError("未找到可验证组合退化的开局");
    }
    private static List<CardInstance> devolutionDeck(UUID owner) {
        var cards = new ArrayList<CardInstance>();
        for (int i = 0; i < 60; i++) cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits() + 10_000, i + 1), owner,
            i < 26 ? "basic" : i < 43 ? "evolved" : "component"));
        return cards;
    }
    private static CardInstance hand(ServerMatch m, UUID owner) { return m.privateView(owner).hand().stream().filter(c -> c.definitionId().equals("component")).findFirst().orElseThrow(); }
    private static MatchSession.Option action(MatchSession.Snapshot snapshot, CardInstance card, UUID target) {
        return snapshot.options().stream().filter(o -> o.cardKeys().contains("hand:" + card.instanceId())
            && (target == null || o.cardKeys().contains("field:" + target))).findFirst().orElseThrow();
    }
    private static void unchangedReject(ServerMatch m, Runnable operation) {
        var own = m.privateView(A); var opponent = m.privateView(B); var publicView = m.clientView(A);
        var random = m.randomTranscript(); var replay = m.replayTranscript(); var supporter = m.mayPlaySupporter(m.currentPlayer());
        rejects(operation);
        check(own.equals(m.privateView(A)) && opponent.equals(m.privateView(B)) && publicView.equals(m.clientView(A))
            && random.equals(m.randomTranscript()) && replay.equals(m.replayTranscript()) && supporter == m.mayPlaySupporter(m.currentPlayer()),
            "失败请求已移动牌、改场面、消耗次数或随机数");
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("无效训练家请求未被拒绝");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
