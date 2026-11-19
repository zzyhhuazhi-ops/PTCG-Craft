import dev.hua.ptcglive.engine.CoinSide;
import dev.hua.ptcglive.engine.MatchPhase;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
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

/** Audited real-card regression: Temporal Forces Dunsparce 128 → Dudunsparce 129. */
public final class DudunsparceRealCardTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final Ruleset RULE = new Ruleset("standard-international-2026", "2026.1.4", "国际 Standard（2026 赛季）", "international",
        LocalDate.of(2026, 4, 10), java.util.Set.of("H", "I", "J"), java.util.Set.of(), Map.of(), List.of(), List.of());

    public static void main(String[] args) {
        crossPrintingEvolution();
        catalogFactsAndDigProtection();
        runAwayDrawUsesServerShuffleAndReplacement();
        System.out.println("DudunsparceRealCardTest passed: SV5 128/129 facts, Dig immunity duration, Run Away Draw and server shuffle");
    }

    private static void crossPrintingEvolution() {
        var combat=BundledCombatData.load();int checked=0;
        for(var basic:combat.pokemonProfiles().entrySet()) {
            if(!"Dunsparce".equals(basic.getValue().pokemonName()))continue;
            for(String evolution:List.of("sv5_en_129","sv8-5_en_080")) {
                var field=new dev.hua.ptcglive.engine.field.BattlefieldState(A,B,combat.pokemonProfiles(),new ServerRandom(1));
                field.beginTurn(A);field.placeInitialActive(A,new CardInstance(UUID.randomUUID(),A,basic.getKey()));
                UUID target=field.activeView(A).pokemonId();
                check(!field.canEvolve(A,target,evolution),"newly placed basic evolved immediately");
                field.endTurn(A);field.beginTurn(B);field.placeInitialActive(B,new CardInstance(UUID.randomUUID(),B,"sv5_en_128"));field.endTurn(B);field.beginTurn(A);
                check(field.canEvolve(A,target,evolution),"cross-print evolution rejected: "+basic.getKey()+" -> "+evolution);
                field.evolve(A,target,new CardInstance(UUID.randomUUID(),A,evolution));checked++;
            }
        }
        check(checked>=10,"not enough real Dunsparce printings exercised");
        var profile=combat.pokemon("sv5_en_129").orElseThrow();
        check(!profile.evolvesFrom("me2-5_en_163",combat.pokemon("me2-5_en_163").orElseThrow()),"Larry's Dunsparce is a different printed name");
        System.out.println("Cross-print Dudunsparce evolution passed: "+checked+" actual printing pairs; timing and owner-name restrictions preserved");
    }

    private static void catalogFactsAndDigProtection() {
        var combat = BundledCombatData.load();
        var basic = combat.pokemon("sv5_en_128").orElseThrow();
        var evolved = combat.pokemon("sv5_en_129").orElseThrow();
        check(basic.hitPoints() == 60 && basic.retreatCost() == 0 && basic.weaknessType() == dev.hua.ptcglive.engine.field.EnergyType.FIGHTING,
            "Dunsparce SV5 128 battle facts missing");
        check(basic.attack("dig").orElseThrow().effectSupport() == EffectSupport.SUPPORTED
                && basic.attack("dig").orElseThrow().coinProtection() != null,
            "Dig protection effect was not loaded as a data-driven attack effect");
        check(evolved.hitPoints() == 140 && evolved.retreatCost() == 3 && "sv5_en_128".equals(evolved.evolvesFromDefinitionId())
            && evolved.attack("land-crush").orElseThrow().baseDamage() == 90
            && evolved.ability("run-away-draw").orElseThrow().effect().amount() == 3,
            "Dudunsparce SV5 129 official facts missing");
        var field = new dev.hua.ptcglive.engine.field.BattlefieldState(A, B, Map.of("sv5_en_128", basic), new ServerRandom(7));
        field.placeInitialActive(A, new CardInstance(UUID.randomUUID(), A, "sv5_en_128"));
        field.placeInitialActive(B, new CardInstance(UUID.randomUUID(), B, "sv5_en_128"));
        field.beginTurn(A); field.protectFromOpponentNextTurn(A, field.activeView(A).pokemonId(), B);
        check(field.preventsAttack(A, B), "Dig protection was not applied to the defending Pokémon");
        check(field.activeView(A).continuousEffectZh().contains("免受攻击伤害与效果"), "公开战场视图没有标示已生效的挖洞持续效果");
        field.beginTurn(B); check(field.preventsAttack(A, B), "Dig protection expired before the opponent's next turn");
        field.beginTurn(A); check(!field.preventsAttack(A, B) && field.activeView(A).continuousEffectZh().isBlank(), "Dig protection did not expire at its owner's next turn");
    }

    private static void runAwayDrawUsesServerShuffleAndReplacement() {
        ServerMatch m = fixture();
        var active = m.activePokemonView(A);
        m.endTurn(A); m.endTurn(B); // A's next turn: evolution is legal.
        CardInstance evolution = m.privateView(A).hand().stream().filter(c -> c.definitionId().equals("sv5_en_129")).findFirst().orElseThrow();
        m.evolve(A, active.pokemonId(), evolution.instanceId());
        int hand = m.privateView(A).hand().size();
        int audit = m.randomTranscript().size();
        UUID bench = m.benchPokemonView(A).getFirst().pokemonId();
        m.useAbility(A, active.pokemonId(), "run-away-draw");
        check(m.privateView(A).hand().size() == hand + 3, "Run Away Draw did not draw three cards server-side");
        check(m.phase() == MatchPhase.CHOOSE_REPLACEMENT && m.privateView(A).discard().isEmpty(),
            "Run Away Draw should shuffle the stack instead of discarding or awarding prizes");
        check(m.privateView(A).deck().stream().filter(c -> c.definitionId().equals("sv5_en_128")).count() >= 1
                && m.privateView(A).deck().stream().filter(c -> c.definitionId().equals("sv5_en_129")).count() >= 1
                && m.randomTranscript().size() > audit,
            "Run Away Draw did not return the complete stack and shuffle through audited server randomness");
        m.chooseReplacement(A, bench);
        check(m.phase() == MatchPhase.TURN && m.currentPlayer().equals(A) && m.activePokemonView(A).pokemonId().equals(bench),
            "replacement after a real Ability should preserve the active player's turn");
    }

    private static ServerMatch fixture() {
        for (int seed = 0; seed < 10000; seed++) {
            List<CardInstance> aDeck = deck(A), bDeck = opponentDeck(B);
            ServerMatch probe = new ServerMatch(A, B, aDeck, bDeck, RULE, BundledRules.loadCardDefinitions(), BundledCombatData.load(), new ServerRandom(seed));
            probe.callCoin(A, CoinSide.HEADS); UUID winner = probe.publicCoinResult() == CoinSide.HEADS ? A : B;
            probe.chooseWhoGoesFirst(winner, winner.equals(A));
            if (probe.currentPlayer().equals(A)
                && probe.privateView(A).hand().stream().filter(c -> c.definitionId().equals("sv5_en_128")).count() >= 2
                && probe.privateView(A).hand().stream().anyMatch(c -> c.definitionId().equals("sv5_en_129"))) {
                ServerMatch match = new ServerMatch(A, B, aDeck, bDeck, RULE, BundledRules.loadCardDefinitions(), BundledCombatData.load(), new ServerRandom(seed));
                OpeningTestSupport.complete(match, A, B, 1);
                return match;
            }
        }
        throw new AssertionError("could not find deterministic Dudunsparce fixture");
    }

    private static List<CardInstance> deck(UUID owner) {
        List<CardInstance> cards = new ArrayList<>();
        for (int i = 0; i < 24; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, "sv5_en_128"));
        for (int i = 24; i < 48; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, "sv5_en_129"));
        for (int i = 48; i < 60; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, "sve_en_001"));
        return cards;
    }
    private static List<CardInstance> opponentDeck(UUID owner) {
        List<CardInstance> cards = new ArrayList<>();
        for (int i = 0; i < 60; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, i < 30 ? "sv5_en_128" : "sve_en_001"));
        return cards;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
