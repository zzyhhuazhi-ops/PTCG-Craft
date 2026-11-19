import dev.hua.ptcglive.engine.EffectSourceZone;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.field.AbilityDefinition;
import dev.hua.ptcglive.engine.field.AbilityEffect;
import dev.hua.ptcglive.engine.field.PokemonProfile;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** A passive “cannot have Tools attached” rule blocks both normal play and effect attachment. */
public final class PassiveToolAttachmentPreventionTest {
    private static final UUID A = new UUID(0, 6411), B = new UUID(0, 6412);

    public static void main(String[] args) {
        rejects(() -> new AbilityDefinition("wrong-kind", "错误类型",
            new AbilityEffect(AbilityEffect.Opcode.PASSIVE_PREVENT_POKEMON_TOOLS, 0), EffectSupport.SUPPORTED, null));
        ServerMatch match = fixture();
        UUID target = match.activePokemonView(A).pokemonId();
        CardInstance tool = match.privateView(A).hand().stream().filter(card -> card.definitionId().equals("tool")).findFirst().orElseThrow();
        if (!match.abilityOptions(A).isEmpty()) throw new AssertionError("禁止工具的被动特性被错误暴露为客户端操作");
        var before = match.clientView(A);
        rejects(() -> match.playTrainer(A, tool.instanceId(), target));
        check(before.equals(match.clientView(A)) && match.privateView(A).hand().contains(tool), "普通附着工具被拒绝时没有保持原子性");
        rejects(() -> match.attachToolByEffect(A, A, EffectSourceZone.HAND, tool.instanceId(), A, target));
        check(before.equals(match.clientView(A)) && match.privateView(A).hand().contains(tool)
            && match.activePokemonView(A).attachedTools().isEmpty(), "服务器效果附着绕过了禁止工具被动");
        System.out.println("PassiveToolAttachmentPreventionTest passed: passive tool prohibition blocks play and effect attachment server-side");
    }

    private static ServerMatch fixture() {
        CardDefinition blocker = definition("blocker", CardCategory.POKEMON, TrainerCategory.NONE, true);
        CardDefinition opponent = definition("opponent", CardCategory.POKEMON, TrainerCategory.NONE, true);
        CardDefinition tool = definition("tool", CardCategory.TRAINER, TrainerCategory.TOOL, false);
        AbilityDefinition prevention = new AbilityDefinition("no-tools", "禁止工具",
            new AbilityEffect(AbilityEffect.Opcode.PASSIVE_PREVENT_POKEMON_TOOLS, 0), EffectSupport.SUPPORTED, null,
            AbilityDefinition.Kind.PASSIVE);
        TrainerProfile toolProfile = new TrainerProfile(TrainerCategory.TOOL,
            new TrainerEffect(TrainerEffect.Opcode.TOOL_RETREAT_REDUCTION, 1, 0));
        CombatCatalog combat = new CombatCatalog(Map.of(
            "blocker", new PokemonProfile(120, 0, null, null, 0, null, 0, 1, List.of(), List.of(prevention)),
            "opponent", new PokemonProfile(120, 0, null, null, 0, null, 0, 1, List.of())), Map.of(), Map.of("tool", toolProfile));
        CardDatabase definitions = new CardDatabase(List.of(blocker, opponent, tool));
        for (long seed = 1; seed < 10_000; seed++) {
            ServerMatch match = new ServerMatch(A, B, deck(A, "blocker", true), deck(B, "opponent", false),
                MatchRulesRegressionTest.RULE, definitions, combat, new ServerRandom(seed));
            try { OpeningTestSupport.complete(match, A, B, 0); }
            catch (AssertionError unavailableOpening) { continue; }
            while (!match.currentPlayer().equals(A)) match.endTurn(match.currentPlayer());
            if (match.privateView(A).hand().stream().anyMatch(card -> card.definitionId().equals("tool"))) return match;
        }
        throw new AssertionError("无法建立带工具手牌的确定性测试对局");
    }

    private static CardDefinition definition(String id, CardCategory category, TrainerCategory trainerCategory, boolean basic) {
        return new CardDefinition(id, id, category, trainerCategory, "H", Set.of("international"), basic,
            false, 60, Map.of(), EffectSupport.SUPPORTED, null);
    }

    private static List<CardInstance> deck(UUID owner, String pokemon, boolean withTools) {
        var cards = new ArrayList<CardInstance>();
        int pokemonCount = withTools ? 48 : 60;
        for (int i = 0; i < pokemonCount; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, pokemon));
        if (withTools) for (int i = 0; i < 12; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, "tool"));
        return cards;
    }

    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("服务器错误接受了禁止工具的被动特性或附着操作");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
