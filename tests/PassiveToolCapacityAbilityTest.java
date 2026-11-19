import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.field.AbilityDefinition;
import dev.hua.ptcglive.engine.field.AbilityEffect;
import dev.hua.ptcglive.engine.field.PokemonProfile;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** A supported passive Ability changes only its own public Tool capacity and never becomes a clickable client intent. */
public final class PassiveToolCapacityAbilityTest {
    private static final UUID A = new UUID(0, 6321), B = new UUID(0, 6322);

    public static void main(String[] args) {
        rejects(() -> new AbilityDefinition("wrong-kind", "错误类型",
            new AbilityEffect(AbilityEffect.Opcode.PASSIVE_OWN_TOOL_CAPACITY, 2), EffectSupport.SUPPORTED, null));
        rejects(() -> new AbilityDefinition("wrong-passive", "错误被动",
            new AbilityEffect(AbilityEffect.Opcode.DRAW, 1), EffectSupport.SUPPORTED, null, AbilityDefinition.Kind.PASSIVE));

        ServerMatch match = fixture();
        UUID target = match.activePokemonView(A).pokemonId();
        if (!match.abilityOptions(A).isEmpty()) throw new AssertionError("被动工具容量特性被错误发送成可点击操作");
        List<CardInstance> tools = match.privateView(A).hand().stream().filter(card -> card.definitionId().startsWith("tool-")).limit(3).toList();
        match.playTrainer(A, tools.get(0).instanceId(), target);
        match.playTrainer(A, tools.get(1).instanceId(), target);
        if (match.activePokemonView(A).attachedTools().size() != 2)
            throw new AssertionError("服务器没有按当前被动特性允许第二张工具");
        if (match.canPlayTrainer(A, tools.get(2).instanceId(), target))
            throw new AssertionError("被动工具容量错误允许超过声明上限的第三张工具");
        rejects(() -> match.playTrainer(A, tools.get(2).instanceId(), target));
        if (match.activePokemonView(A).attachedTools().size() != 2 || !match.privateView(A).hand().contains(tools.get(2)))
            throw new AssertionError("拒绝第三张工具时没有保持服务器状态原子性");
        System.out.println("PassiveToolCapacityAbilityTest passed: passive own-Tool capacity is server-derived, non-clickable and bounded");
    }

    private static ServerMatch fixture() {
        CardDefinition master = definition("toolmaster", CardCategory.POKEMON, TrainerCategory.NONE, true);
        CardDefinition target = definition("target", CardCategory.POKEMON, TrainerCategory.NONE, true);
        CardDefinition toolA = definition("tool-a", CardCategory.TRAINER, TrainerCategory.TOOL, false);
        CardDefinition toolB = definition("tool-b", CardCategory.TRAINER, TrainerCategory.TOOL, false);
        AbilityDefinition capacity = new AbilityDefinition("two-tools", "双工具被动",
            new AbilityEffect(AbilityEffect.Opcode.PASSIVE_OWN_TOOL_CAPACITY, 2), EffectSupport.SUPPORTED, null,
            AbilityDefinition.Kind.PASSIVE);
        TrainerProfile tool = new TrainerProfile(TrainerCategory.TOOL,
            new TrainerEffect(TrainerEffect.Opcode.TOOL_RETREAT_REDUCTION, 1, 0));
        CombatCatalog combat = new CombatCatalog(Map.of(
            "toolmaster", new PokemonProfile(120, 0, null, null, 0, null, 0, 1, List.of(), List.of(capacity)),
            "target", new PokemonProfile(120, 0, null, null, 0, null, 0, 1, List.of())), Map.of(), Map.of("tool-a", tool, "tool-b", tool));
        CardDatabase definitions = new CardDatabase(List.of(master, target, toolA, toolB));
        for (long seed = 1; seed < 10_000; seed++) {
            ServerMatch match = new ServerMatch(A, B, deck(A, true), deck(B, false), MatchRulesRegressionTest.RULE,
                definitions, combat, new ServerRandom(seed));
            try { OpeningTestSupport.complete(match, A, B, 1); }
            catch (AssertionError unavailableOpening) { continue; }
            while (!match.currentPlayer().equals(A)) match.endTurn(match.currentPlayer());
            if (match.privateView(A).hand().stream().filter(card -> card.definitionId().startsWith("tool-")).count() >= 3) return match;
        }
        throw new AssertionError("无法建立含 3 张工具和 2 只基础宝可梦的确定性测试对局");
    }

    private static CardDefinition definition(String id, CardCategory category, TrainerCategory trainerCategory, boolean basic) {
        return new CardDefinition(id, id, category, trainerCategory, "H", Set.of("international"), basic,
            false, 60, Map.of(), EffectSupport.SUPPORTED, null);
    }

    private static List<CardInstance> deck(UUID owner, boolean master) {
        var cards = new ArrayList<CardInstance>();
        for (int i = 0; i < (master ? 36 : 60); i++) cards.add(new CardInstance(UUID.randomUUID(), owner, master ? "toolmaster" : "target"));
        if (master) {
            for (int i = 0; i < 12; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, "tool-a"));
            for (int i = 0; i < 12; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, "tool-b"));
        }
        return cards;
    }

    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("服务器错误接受了无效的被动特性或工具容量操作");
    }
}
