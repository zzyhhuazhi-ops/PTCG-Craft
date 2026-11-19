import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Exercises JSON -> catalog -> MatchSession intent -> shared effect engine, not a mock ability runner. */
public final class ComposedAbilityIntegrationTest {
    private static final UUID A = new UUID(0, 7551), B = new UUID(0, 7552), WATCHER = new UUID(0, 7553);
    public static void main(String[] args) {
        jsonAbilityUsesSharedInterpreterAndSeparateLimits();
        gateAndInvalidProgramDoNotSpendUsage();
        leaveAndReenterCreatesANewInPlayUsage();
        mixedLegacyDataIsRejected();
        System.out.println("ComposedAbilityIntegrationTest passed: JSON abilities execute via real session, gates, limits and entry identity");
    }
    private static void jsonAbilityUsesSharedInterpreterAndSeparateLimits() {
        AbilityDefinition first = parse("""
            {"id":"draw-two","nameZh":"组合抽二","effectSupport":"SUPPORTED",
             "effects":[{"type":"repeat","count":2,"effects":[{"type":"draw","target":"SELF","amount":1}]}]}
            """);
        AbilityDefinition second = parse("""
            {"id":"draw-one","nameZh":"组合抽一","effectSupport":"SUPPORTED",
             "effects":[{"type":"draw","target":"SELF","amount":1}]}
            """);
        check(first.composed() && first.effectProgram().forTrigger(EffectTrigger.ONCE_PER_TURN).size() == 1,
            "无旧操作码的特性 JSON 未默认绑定正确触发器");
        ServerMatch match = fixture(List.of(first, second));
        UUID active = match.activePokemonView(A).pokemonId(), bench = match.benchPokemonView(A).getFirst().pokemonId();
        MatchSession session = MatchSession.multiplayer(match, A, B);
        session.addSpectator(WATCHER);
        int before = match.privateView(A).hand().size();
        var snapshot = session.view(A, A);
        var option = option(snapshot, first.nameZh(), active);
        rejects(() -> session.submit(B, A, snapshot.revision(), option.index()));
        session.submit(A, A, snapshot.revision(), option.index());
        var publicAbility=session.view(A,A).events().stream().map(BattleEventJournal.Event::cue)
            .filter(c->c.kind()==dev.hua.ptcglive.view.MatchPresentation.Kind.ABILITY).findFirst().orElseThrow();
        check(first.nameZh().equals(publicAbility.text())&&active.equals(publicAbility.pokemon()),"特性演出丢失实际名称或公开来源");
        check(match.privateView(A).hand().size() == before + 2, "实际特性入口忽略了组合效果");
        rejects(() -> session.submit(A, A, snapshot.revision(), option.index()));
        rejects(() -> match.useAbility(A, active, first.id()));
        check(!match.canUseAbility(A, active, first.id()), "已用特性仍被判定可用");
        check(match.abilityOptions(A).stream().anyMatch(value -> value.pokemonId().equals(active)
            && value.ability().id().equals(first.id()) && value.usedThisTurn()), "公开操作状态没有显示限次结果");
        check(session.view(A, A).options().stream().noneMatch(value -> value.labelZh().startsWith("使用特性")
            && value.labelZh().contains(first.nameZh()) && value.cardKeys().contains("field:" + active)), "会话仍显示重复使用操作");
        match.useAbility(A, active, second.id());
        match.useAbility(A, bench, first.id());
        check(match.privateView(A).hand().size() == before + 5, "不同特性或不同宝可梦被错误共享次数");
        check(session.view(B, B).game().hand().equals(match.clientView(B).hand())
            && session.view(WATCHER, null).game().hand().isEmpty(), "对手或观战者获得了他人抽牌身份");
        match.endTurn(A); match.endTurn(B);
        check(match.canUseAbility(A, active, first.id()), "新的己方回合没有恢复特性次数");
        match.useAbility(A, active, first.id());
    }
    private static void gateAndInvalidProgramDoNotSpendUsage() {
        var gate = new ConditionDefinition(ConditionType.DAMAGE_COUNTER_COUNT, Map.of("target", "SELF", "value", "1"), List.of());
        var gated = ability("gated", List.of(gate), effect("draw", "1"));
        var bad = ability("bad", List.of(), effect("draw", "1"), effect("heal", "11"));
        ServerMatch match = fixture(List.of(gated, bad));
        UUID active = match.activePokemonView(A).pokemonId();
        var before = match.privateView(A);
        int replay = match.replayTranscript().size();
        rejects(() -> match.useAbility(A, active, gated.id()));
        rejects(() -> match.useAbility(A, active, bad.id()));
        check(before.equals(match.privateView(A)) && replay == match.replayTranscript().size(), "无效特性已部分执行或记录使用");
        check(match.abilityOptions(A).stream().noneMatch(ServerMatch.AbilityOption::usedThisTurn), "失败校验消耗了次数");
        match.placeDamageCountersByEffect(A, A, active, 1);
        check(match.canUseAbility(A, active, gated.id()), "条件后来满足仍不能使用特性");
        match.useAbility(A, active, gated.id());
        check(match.privateView(A).hand().size() == before.hand().size() + 1, "条件满足后没有抽牌");
    }
    private static void leaveAndReenterCreatesANewInPlayUsage() {
        var draw = ability("entry-draw", List.of(), effect("draw", "1"));
        ServerMatch match = fixture(List.of(draw));
        UUID bench = match.benchPokemonView(A).getFirst().pokemonId();
        UUID physical=PokemonIdentityTestSupport.physical(match,A,bench);
        match.useAbility(A, bench, draw.id());
        // Changing position is not leaving play and must not reset the usage limit.
        match.switchActiveByEffect(A, A, bench);
        check(!match.canUseAbility(A, bench, draw.id()), "换位错误重置特性次数");
        match.movePokemonByEffect(A, A, bench, LeavePlayDestination.HAND);
        match.chooseReplacement(A, match.benchPokemonView(A).getFirst().pokemonId());
        match.playBasicPokemonToBench(A, physical);
        UUID returned=PokemonIdentityTestSupport.logical(match,A,physical);
        check(!returned.equals(bench),"Reentered card reused old logical identity");
        check(match.canUseAbility(A, returned, draw.id()), "离场再登场的新场上实例错误继承旧次数");
        match.useAbility(A, returned, draw.id());
    }
    private static void mixedLegacyDataIsRejected() {
        rejects(() -> parse("""
            {"id":"mixed","nameZh":"混合错误","effectSupport":"SUPPORTED","effect":{"opcode":"DRAW","amount":1},
             "effects":[{"type":"draw","amount":2}]}
            """));
        rejects(() -> parse("{\"id\":\"empty\",\"nameZh\":\"空错误\",\"effectSupport\":\"SUPPORTED\"}"));
    }
    private static MatchSession.Option option(MatchSession.Snapshot view, String label, UUID source) {
        return view.options().stream().filter(value -> value.labelZh().contains(label)
            && value.cardKeys().contains("field:" + source)).findFirst().orElseThrow();
    }
    private static AbilityDefinition parse(String json) {
        return BundledCombatData.ability(JsonParser.parseString(json).getAsJsonObject(), EffectRegistry.standard());
    }
    private static AbilityDefinition ability(String id, List<ConditionDefinition> gate, EffectDefinition... effects) {
        return new AbilityDefinition(id, id, new AbilityEffect(AbilityEffect.Opcode.COMPOSED, 0), EffectSupport.SUPPORTED, null,
            AbilityDefinition.Kind.ACTIVATED_ONCE_PER_TURN,
            new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ONCE_PER_TURN, gate, List.of(effects)))));
    }
    private static EffectDefinition effect(String type, String amount) {
        return new EffectDefinition(type, EffectTarget.SELF, Map.of("amount", amount), List.of(), List.of());
    }
    private static ServerMatch fixture(List<AbilityDefinition> abilities) {
        var definition = new CardDefinition("basic", "测试基础", CardCategory.POKEMON, TrainerCategory.NONE,
            "H", Set.of("international"), true, false, 60, Map.of(), EffectSupport.SUPPORTED, null);
        var combat = new CombatCatalog(Map.of("basic", new PokemonProfile(120, 0, null, null, 0, null, 0, 1, List.of(), abilities)), Map.of());
        ServerMatch match = new ServerMatch(A, B, deck(A), deck(B), BundledRules.loadRulesets().get("standard-international-2026"),
            new CardDatabase(List.of(definition)), combat, new ServerRandom(92));
        OpeningTestSupport.complete(match, A, B, 1);
        if (match.currentPlayer().equals(B)) match.endTurn(B);
        return match;
    }
    private static List<CardInstance> deck(UUID owner) {
        var cards = new ArrayList<CardInstance>();
        for (int i = 0; i < 60; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, "basic"));
        return cards;
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("非法特性意图被接受");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
