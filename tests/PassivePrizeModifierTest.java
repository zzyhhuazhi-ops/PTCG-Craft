import dev.hua.ptcglive.engine.MatchPhase;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.field.AbilityDefinition;
import dev.hua.ptcglive.engine.field.AbilityEffect;
import dev.hua.ptcglive.engine.field.PokemonProfile;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Prize modifiers are read from the Knocked Out Pokémon's current supported passive Ability. */
public final class PassivePrizeModifierTest {
    private static final UUID A = new UUID(0, 6521), B = new UUID(0, 6522);

    public static void main(String[] args) {
        rejects(() -> new AbilityDefinition("wrong-kind", "错误类型",
            new AbilityEffect(AbilityEffect.Opcode.PASSIVE_PRIZE_CARD_MODIFIER, -1), EffectSupport.SUPPORTED, null));
        verify(-1, 1); verify(1, 3);
        System.out.println("PassivePrizeModifierTest passed: supported passive Prize modifiers are server-resolved at Knock Out");
    }

    private static void verify(int modifier, int expectedPrizes) {
        ServerMatch match = fixture(modifier);
        UUID target = match.activePokemonView(A).pokemonId();
        match.placeDamageCountersByEffect(B, A, target, 12);
        check(match.phase() == MatchPhase.PRIZE_SELECTION && match.pendingChoicePlayer().equals(B), "击倒后没有轮到对手选择奖赏");
        for (int i = 0; i < expectedPrizes; i++) {
            if (match.phase() != MatchPhase.PRIZE_SELECTION) throw new AssertionError("奖赏张数少于服务器计算的数值");
            match.takePrizeAt(B, 0);
        }
        if (match.privateView(B).prizes().size() != 6 - expectedPrizes)
            throw new AssertionError("服务器结算的奖赏张数错误：期望 " + expectedPrizes);
        if (match.phase() == MatchPhase.PRIZE_SELECTION)
            throw new AssertionError("奖赏张数超过了被动特性修正后的数值");
    }

    private static ServerMatch fixture(int modifier) {
        CardDefinition prizePokemon = definition("prize-pokemon", true);
        CardDefinition plainPokemon = definition("plain-pokemon", true);
        AbilityDefinition prizeAbility = new AbilityDefinition("prize-modifier", "奖赏修正",
            new AbilityEffect(AbilityEffect.Opcode.PASSIVE_PRIZE_CARD_MODIFIER, modifier), EffectSupport.SUPPORTED, null,
            AbilityDefinition.Kind.PASSIVE);
        CombatCatalog combat = new CombatCatalog(Map.of(
            "prize-pokemon", new PokemonProfile(120, 0, null, null, 0, null, 0, 2, List.of(), List.of(prizeAbility)),
            "plain-pokemon", new PokemonProfile(120, 0, null, null, 0, null, 0, 1, List.of())), Map.of());
        CardDatabase definitions = new CardDatabase(List.of(prizePokemon, plainPokemon));
        for (long seed = 1; seed < 10_000; seed++) {
            ServerMatch match = new ServerMatch(A, B, deck(A, "prize-pokemon"), deck(B, "plain-pokemon"),
                MatchRulesRegressionTest.RULE, definitions, combat, new ServerRandom(seed));
            try { OpeningTestSupport.complete(match, A, B, 1); }
            catch (AssertionError unavailableOpening) { continue; }
            while (!match.currentPlayer().equals(B)) match.endTurn(match.currentPlayer());
            return match;
        }
        throw new AssertionError("无法建立奖赏修正测试对局");
    }
    private static CardDefinition definition(String id, boolean basic) {
        return new CardDefinition(id, id, CardCategory.POKEMON, TrainerCategory.NONE, "H", Set.of("international"), basic,
            false, 60, Map.of(), EffectSupport.SUPPORTED, null);
    }
    private static List<CardInstance> deck(UUID owner, String id) {
        var cards = new ArrayList<CardInstance>();
        for (int i = 0; i < 60; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, id));
        return cards;
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("奖赏被动特性被错误接受为可点击特性");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
