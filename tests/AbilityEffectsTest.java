import dev.hua.ptcglive.engine.CoinSide;
import dev.hua.ptcglive.engine.MatchPhase;
import dev.hua.ptcglive.engine.MatchResult;
import dev.hua.ptcglive.engine.MatchSession;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.field.AbilityDefinition;
import dev.hua.ptcglive.engine.field.AbilityEffect;
import dev.hua.ptcglive.rules.BundledCombatData;
import dev.hua.ptcglive.rules.BundledRules;
import dev.hua.ptcglive.rules.CardInstance;
import dev.hua.ptcglive.rules.EffectSupport;
import dev.hua.ptcglive.rules.Ruleset;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Regression coverage for the first server-authoritative, data-driven Ability opcodes. */
public final class AbilityEffectsTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-000000000202");
    private static final Ruleset RULE = new Ruleset("standard-international-2026", "2026.1.0", "国际 Standard（2026 赛季）", "international",
        LocalDate.of(2026, 4, 10), java.util.Set.of("H", "I", "J"), java.util.Set.of(), Map.of(), List.of(), List.of());

    public static void main(String[] args) {
        loaderAndPerInstanceLimit();
        drawUntilHandSizeIsServerResolved();
        deckSearchIsPrivateAndAtomic();
        multipleAndOptionalDeckSearchesAreAtomic();
        legacySearchUsesResumableSelector();
        passiveAbilityIsNotAnIntent();
        cardTableSessionUsesSameServerAuthority();
        voluntaryLeaveRequiresReplacementButKeepsTurn();
        voluntaryLeaveWithNoBenchLosesWithoutPrize();
        System.out.println("AbilityEffectsTest passed: data-driven abilities, private one/multiple/optional deck search, per-instance turn limit, server draw, draw-until-hand-size, leave-play, replacement and no-prize loss");
    }

    private static void drawUntilHandSizeIsServerResolved() {
        ServerMatch m = fixture("fixture-ability-draw-until-h", 0);
        UUID active = m.activePokemonView(A).pokemonId();
        int hand = m.privateView(A).hand().size();
        check(hand < 8, "fixture should leave room for the draw-until-hand-size Ability");
        check(m.canUseAbility(A, active, "fixture-draw-until"), "draw-until-hand-size Ability should be offered when it can draw");
        m.useAbility(A, active, "fixture-draw-until");
        check(m.privateView(A).hand().size() == 8, "server did not draw exactly up to the specified hand size");
        check(!m.canUseAbility(A, active, "fixture-draw-until"), "draw-until-hand-size Ability was offered with no legal draw remaining");
    }

    private static void deckSearchIsPrivateAndAtomic() {
        ServerMatch m = fixture("fixture-ability-search-h", 0);
        UUID active = m.activePokemonView(A).pokemonId();
        CardInstance selected = m.privateView(A).deck().getFirst();
        MatchSession session = MatchSession.multiplayer(m, A, B);
        var before = m.privateView(A);
        rejects(() -> m.useAbilityWithDeckSearch(A, active, "fixture-search", UUID.randomUUID()));
        check(before.equals(m.privateView(A)), "forged Ability deck choice changed state");
        check(m.canUseAbilityDeckSearch(A, active, "fixture-search", List.of(selected.instanceId())),
            "owner's valid Ability deck choice was not authorized by server");
        check(session.view(A, A).options().stream().anyMatch(option -> option.labelZh().contains("测试检索") && option.labelZh().contains("检索")),
            "owner did not receive private Ability deck search actions");
        check(session.view(B, B).options().stream().noneMatch(option -> option.labelZh().contains("测试检索") && option.labelZh().contains("检索")),
            "opponent received private Ability deck search actions");
        int audit = m.randomTranscript().size();
        m.useAbilityWithDeckSearch(A, active, "fixture-search", selected.instanceId());
        check(m.privateView(A).hand().contains(selected) && !m.privateView(A).deck().contains(selected),
            "Ability deck search did not move the server-selected instance into hand");
        check(m.randomTranscript().size() > audit && !m.clientView(B).hand().contains(selected),
            "Ability deck search lacked shuffle audit or leaked the selected hand card");
        check(!m.canUseAbilityDeckSearch(A, active, "fixture-search", List.of()),
            "same Pokémon instance used its deck-search Ability twice in one turn");
    }

    private static void multipleAndOptionalDeckSearchesAreAtomic() {
        ServerMatch exact = fixture("fixture-ability-search-two-h", 0);
        UUID exactActive = exact.activePokemonView(A).pokemonId();
        List<CardInstance> selected = exact.privateView(A).deck().stream().limit(2).toList();
        var before = exact.privateView(A);
        rejects(() -> exact.useAbilityWithDeckSearches(A, exactActive, "fixture-search-two",
            List.of(selected.getFirst().instanceId(), selected.getFirst().instanceId())));
        check(before.equals(exact.privateView(A)), "duplicate multi-card Ability selection was not atomically rejected");
        MatchSession exactSession = MatchSession.multiplayer(exact, A, B);
        check(exactSession.view(A, A).options().stream().anyMatch(option -> option.labelZh().contains("测试两张检索")&&option.labelZh().endsWith("（开始检索）")
                && option.cardKeys().contains("field:" + exactActive)),
            "multi-card Ability must enter the committed server selector");
        int audit = exact.randomTranscript().size();
        exact.useAbilityWithDeckSearches(A, exactActive, "fixture-search-two", selected.stream().map(CardInstance::instanceId).toList());
        check(exact.privateView(A).hand().containsAll(selected) && selected.stream().noneMatch(card -> exact.privateView(A).deck().contains(card))
                && exact.randomTranscript().size() > audit,
            "exact two-card Ability search did not resolve as one authoritative operation");

        ServerMatch optional = fixture("fixture-ability-search-up-to-two-h", 0);
        UUID optionalActive = optional.activePokemonView(A).pokemonId();
        MatchSession optionalSession = MatchSession.multiplayer(optional, A, B);
        check(optionalSession.view(A, A).options().stream().anyMatch(option -> option.labelZh().contains("测试至多两张检索")&&option.labelZh().endsWith("（开始检索）")
                && option.cardKeys().contains("field:" + optionalActive)),
            "optional Ability must enter the committed server selector");
        int hand = optional.privateView(A).hand().size(); audit = optional.randomTranscript().size();
        optional.useAbilityWithDeckSearches(A, optionalActive, "fixture-search-up-to-two", List.of());
        check(optional.privateView(A).hand().size() == hand && optional.randomTranscript().size() > audit,
            "optional Ability search did not permit a zero-card selection with server shuffle");
    }

    private static void legacySearchUsesResumableSelector() {
        var game=fixture("fixture-ability-search-two-h",0);var session=MatchSession.multiplayer(game,A,B);
        var before=session.view(A,A);var start=before.options().stream().filter(o->o.labelZh().contains("测试两张检索")&&o.labelZh().endsWith("（开始检索）")).findFirst().orElseThrow();
        session.submit(A,A,before.revision(),start.index());var q=game.effectCardPrompt(A);
        check(q!=null&&q.minimum()==2&&q.maximum()==2,"Ability selection contract changed");
        check(session.view(B,B).game().effectSelection()==null,"Ability candidates leaked to opponent");
        check(session.view(A,A).options().stream().noneMatch(o->o.labelZh().equals("结束回合")),"Committed Ability was cancellable");
        game.submitEffectCards(A,q.resolutionId(),q.step(),q.candidates().stream().limit(2).map(CardInstance::instanceId).toList());
        check(!game.hasPendingCardSelection(),"Legacy Ability did not complete");
        check(!game.canBeginAbilitySearch(A,game.activePokemonView(A).pokemonId(),"fixture-search-two"),"Ability usage not consumed");
    }
    private static void passiveAbilityIsNotAnIntent() {
        var passive = new AbilityDefinition("passive-test", "测试被动特性", new AbilityEffect(AbilityEffect.Opcode.DRAW, 1),
            EffectSupport.UNSUPPORTED, "被动持续效果尚未录入服务器结算", AbilityDefinition.Kind.PASSIVE);
        check(!passive.activated() && !passive.oncePerTurn(), "被动特性被错误标记为可点击的每回合特性");
    }

    private static void cardTableSessionUsesSameServerAuthority() {
        ServerMatch m = fixture("fixture-ability-draw-h", 0);
        MatchSession session = MatchSession.multiplayer(m, A, B);
        MatchSession.Snapshot snapshot = session.view(A, A);
        int option = -1;
        for (MatchSession.Option candidate : snapshot.options()) if (candidate.labelZh().contains("使用特性：") && candidate.labelZh().contains("测试抽牌")) {
            option = candidate.index();
            break;
        }
        check(option >= 0, "card table session did not expose the authorized Ability intent");
        int hand = m.privateView(A).hand().size();
        session.submit(A, A, snapshot.revision(), option);
        check(m.privateView(A).hand().size() == hand + 1 && session.audit().getLast().operation().contains("使用特性："),
            "card table session did not route Ability through the same server authority");
    }

    private static void loaderAndPerInstanceLimit() {
        check(BundledCombatData.load().pokemon("fixture-ability-draw-h").orElseThrow().abilities().size() == 1,
            "bundled Ability data was not loaded");
        ServerMatch m = fixture("fixture-ability-draw-h", 1);
        UUID active = m.activePokemonView(A).pokemonId();
        int hand = m.privateView(A).hand().size();
        check(m.canUseAbility(A, active, "fixture-draw"), "own in-play Ability should be available");
        rejects(() -> m.useAbility(B, active, "fixture-draw"));
        m.useAbility(A, active, "fixture-draw");
        check(m.privateView(A).hand().size() == hand + 1, "Ability draw was not resolved by server");
        check(!m.canUseAbility(A, active, "fixture-draw"), "same Pokémon instance used its Ability twice in one turn");
        rejects(() -> m.useAbility(A, active, "fixture-draw"));
        m.endTurn(A); m.endTurn(B);
        check(m.currentPlayer().equals(A) && m.canUseAbility(A, active, "fixture-draw"),
            "once-during-your-turn Ability did not reset at its owner's next turn");
    }

    private static void voluntaryLeaveRequiresReplacementButKeepsTurn() {
        ServerMatch m = fixture("fixture-ability-leave-h", 1);
        UUID active = m.activePokemonView(A).pokemonId();
        UUID physical=PokemonIdentityTestSupport.physical(m,A,active);
        UUID bench = m.benchPokemonView(A).getFirst().pokemonId();
        int hand = m.privateView(A).hand().size();
        int opponentPrizes = m.privateView(B).prizes().size();
        m.useAbility(A, active, "fixture-leave-draw");
        check(m.phase() == MatchPhase.CHOOSE_REPLACEMENT && m.publicView().get(A).activeCardId() == null,
            "discarding Active by Ability did not request a replacement");
        check(m.privateView(A).discard().stream().anyMatch(card -> card.instanceId().equals(physical)),
            "discard-self Ability did not discard the Pokémon card");
        check(m.privateView(A).hand().size() == hand + 1, "discard-self Ability did not draw server-side");
        check(m.privateView(B).prizes().size() == opponentPrizes, "voluntary leave-play incorrectly awarded a Prize");
        m.chooseReplacement(A, bench);
        check(m.phase() == MatchPhase.TURN && m.currentPlayer().equals(A)
                && m.activePokemonView(A).pokemonId().equals(bench),
            "Ability replacement did not return control to the same turn");
    }

    private static void voluntaryLeaveWithNoBenchLosesWithoutPrize() {
        ServerMatch m = fixture("fixture-ability-leave-h", 0);
        int opponentPrizes = m.privateView(B).prizes().size();
        m.useAbility(A, m.activePokemonView(A).pokemonId(), "fixture-leave-draw");
        check(m.phase() == MatchPhase.FINISHED && m.result().winner().equals(B) && m.result().reason() == MatchResult.Reason.NO_POKEMON,
            "leaving the only Pokémon should lose for no Pokémon");
        check(m.privateView(B).prizes().size() == opponentPrizes, "no-Pokémon loss from Ability incorrectly awarded a Prize");
    }

    private static ServerMatch fixture(String aCard, int benches) {
        for (int seed = 0; seed < 2000; seed++) {
            List<CardInstance> aDeck = deck(A, aCard), bDeck = deck(B, "fixture-basic-h");
            ServerMatch candidate = new ServerMatch(A, B, aDeck, bDeck, RULE, BundledRules.loadCardDefinitions(), BundledCombatData.load(), new ServerRandom(seed));
            candidate.callCoin(A, CoinSide.HEADS);
            UUID winner = candidate.publicCoinResult() == CoinSide.HEADS ? A : B;
            candidate.chooseWhoGoesFirst(winner, winner.equals(A));
            if (candidate.currentPlayer().equals(A)) {
                ServerMatch match = new ServerMatch(A, B, aDeck, bDeck, RULE, BundledRules.loadCardDefinitions(), BundledCombatData.load(), new ServerRandom(seed));
                OpeningTestSupport.complete(match, A, B, benches);
                return match;
            }
        }
        throw new AssertionError("could not find A-first deterministic fixture");
    }

    private static List<CardInstance> deck(UUID owner, String definitionId) {
        List<CardInstance> result = new ArrayList<>();
        for (int i = 0; i < 60; i++) result.add(new CardInstance(UUID.randomUUID(), owner, definitionId));
        return result;
    }
    private static void check(boolean okay, String message) { if (!okay) throw new AssertionError(message); }
    private static void rejects(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("illegal Ability operation was accepted");
    }
}
