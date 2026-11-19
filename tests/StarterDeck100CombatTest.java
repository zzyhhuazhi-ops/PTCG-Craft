import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.starter.StarterDeck100Catalog;
import java.nio.file.*;
import java.util.*;

/** Real bundled SD100 profiles; synthetic battle decks are not acceptance of the 102 products. */
public final class StarterDeck100CombatTest {
    static final UUID A = new UUID(0, 10001), B = new UUID(0, 10002);
    static final CardDatabase CARDS = BundledRules.loadCardDefinitions();
    static final CombatCatalog COMBAT = BundledCombatData.load();
    static final Ruleset RULE = BundledRules.loadCurrentRulesets().get("starter-deck-100-cn-community-2024");
    public static void main(String[] args) throws Exception {
        coverageAndDecks(); readyDeckOpenings(); evolution(); battle(); energySearch(); judge(false); judge(true); cook();
        tropius("ATTACK"); tropius("COUNTERS"); tropius("NONE");
        System.out.println("StarterDeck100CombatTest passed: 438 identities, all 102 deck gates, all ready deck openings, local evolution, battle/KO/prize/end and Trainer programs");
    }
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static CardInstance card(UUID owner, String id) { return new CardInstance(UUID.randomUUID(), owner, id); }
    static void coverageAndDecks() throws Exception {
        var validator = new DeckValidator(CARDS, COMBAT);
        int supported = 0;
        for (var def : CARDS.all()) if (def.id().startsWith("sd100_cn_")) {
            if (def.effectSupport() == EffectSupport.SUPPORTED) {
                supported++;
                var checked = validator.validate(RULE, List.of(card(A, def.id())));
                check(checked.issues().stream().noneMatch(i -> i.code().contains("未支持") || i.code().contains("资料缺失")),
                    def.id() + ": supported flag without executable data: " + checked.issues());
            }
            if (def.category() == CardCategory.POKEMON) {
                var p = COMBAT.pokemon(def.id()).orElseThrow();
                check(def.basicPokemon() == !p.isEvolution(), "incorrect basic flag: " + def.id());
                var visited = new HashSet<String>(); String at = def.id();
                while (at != null) {
                    check(at.startsWith("sd100_cn_") && visited.add(at), "foreign or cyclic evolution: " + def.id());
                    at = COMBAT.pokemon(at).orElseThrow().evolvesFromDefinitionId();
                }
            }
        }
        check(supported == 438, "reviewed support count changed: " + supported);
        var results = new ArrayList<Map<String, Object>>();
        for (var deck : StarterDeck100Catalog.loadBundled().decks()) {
            var ids = deck.expandedCardIds();
            var validation = validator.validate(RULE, ids.stream().map(id -> card(A, id)).toList());
            var pending = ids.stream().distinct().filter(id -> CARDS.find(id).orElseThrow().effectSupport() != EffectSupport.SUPPORTED).sorted().toList();
            check(ids.size() == 60 && ids.stream().anyMatch(id -> CARDS.find(id).orElseThrow().basicPokemon()), "invalid construction: " + deck.number());
            boolean ready = Set.of("001", "002", "003", "004", "005", "006", "007", "008", "009", "010", "011", "012", "013", "014", "015", "016", "017", "018", "019", "020", "021", "022", "023", "024", "025", "026", "027", "028", "029", "030", "031", "032", "033", "034", "035", "036", "037", "038", "039", "040", "041", "042", "043", "044", "045", "046", "047", "048", "049", "050", "051", "052", "053", "054", "055", "056", "057", "058", "059", "060", "061", "062", "063", "064", "065", "066", "067", "068", "069", "070", "071", "072", "073", "074", "075", "076", "077", "078", "079", "080", "081", "082", "083", "084", "085", "086", "087", "088", "089", "090", "091", "092", "093", "094", "095", "096", "097", "098", "099", "100", "101", "102").contains(deck.number());
            check(ready == pending.isEmpty() && ready == validation.canStartMatch(), "wrong deck readiness: " + deck.number() + validation.issues());
            results.add(Map.of("deck", deck.number(), "canStart", validation.canStartMatch(), "pendingCardIds", pending,
                "issues", validation.issues(), "fullMatchTest", ready ? "USER_ACCEPTANCE_PENDING" : "NOT_RUN_UNSUPPORTED_CARDS"));
        }
        Files.createDirectories(Path.of("build/reports/sd100"));
        Files.writeString(Path.of("build/reports/sd100/decks.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(results));
    }
    static void readyDeckOpenings() {
        var catalog = StarterDeck100Catalog.loadBundled();
        for (String number : Set.of("001", "002", "003", "004", "005", "006", "007", "008", "009", "010", "011", "012", "013", "014", "015", "016", "017", "018", "019", "020", "021", "022", "023", "024", "025", "026", "027", "028", "029", "030", "031", "032", "033", "034", "035", "036", "037", "038", "039", "040", "041", "042", "043", "044", "045", "046", "047", "048", "049", "050", "051", "052", "053", "054", "055", "056", "057", "058", "059", "060", "061", "062", "063", "064", "065", "066", "067", "068", "069", "070", "071", "072", "073", "074", "075", "076", "077", "078", "079", "080", "081", "082", "083", "084", "085", "086", "087", "088", "089", "090", "091", "092", "093", "094", "095", "096", "097", "098", "099", "100", "101", "102")) for (int seed = 0; seed < 12; seed++) {
            var first = catalog.deck(number).expandedCardIds().stream().map(id -> card(A, id)).toList();
            var second = catalog.deck("066").expandedCardIds().stream().map(id -> card(B, id)).toList();
            var m = new ServerMatch(A, B, first, second, RULE, CARDS, COMBAT, new ServerRandom(seed));
            OpeningTestSupport.complete(m, A, B, 0);
            check(m.phase() == MatchPhase.TURN && m.publicView().get(A).prizeCount() == 6 && m.publicView().get(B).prizeCount() == 6,
                "real SD100 opening failed: seed " + seed);
        }
    }
    static void evolution() {
        var field = new BattlefieldState(A, B, COMBAT.pokemonProfiles(), new ServerRandom(10));
        field.beginTurn(A); field.placeInitialActive(A, card(A, "sd100_cn_140")); field.endTurn(A);
        field.beginTurn(B); field.placeInitialActive(B, card(B, "sd100_cn_005")); field.endTurn(B);
        field.beginTurn(A); var target = field.activeView(A).pokemonId();
        check(field.canEvolve(A, target, "sd100_cn_141"), "CN Shinx cannot evolve into CN Luxio");
        check(!field.canEvolve(A, target, "sd100_cn_142"), "ordinary evolution skipped stage");
        field.evolve(A, target, card(A, "sd100_cn_141")); field.endTurn(A);
        field.beginTurn(B); field.endTurn(B); field.beginTurn(A);
        field.evolve(A, target, card(A, "sd100_cn_142"));
        check(field.activeView(A).topCardId().equals("sd100_cn_142"), "CN stage-two evolution failed");
    }
    static List<CardInstance> syntheticDeck(UUID owner, String pokemon, String trainer) {
        var deck = new ArrayList<CardInstance>();
        for (int i = 0; i < 60; i++) deck.add(card(owner, i < 20 ? pokemon : trainer != null && i < 40 ? trainer : "sd100_cn_grass_energy"));
        return deck;
    }
    static ServerMatch game(String trainer) {
        return game(trainer, 0);
    }
    static ServerMatch game(String trainer, int benches) {
        for (int seed = 0; seed < 1000; seed++) {
            var m = new ServerMatch(A, B, syntheticDeck(A, "sd100_cn_001", trainer), syntheticDeck(B, "sd100_cn_005", null),
                RULE, CARDS, COMBAT, new ServerRandom(seed));
            OpeningTestSupport.decideOrder(m, A, B);
            if (List.of(A, B).stream().anyMatch(p -> m.privateView(p).hand().stream().filter(c -> CARDS.find(c.definitionId()).orElseThrow().basicPokemon()).count() < benches + 1)) continue;
            OpeningTestSupport.complete(m, A, B, benches);
            long energies = m.privateView(A).hand().stream().filter(c -> c.definitionId().equals("sd100_cn_grass_energy")).count();
            if (energies >= 2 && (trainer == null || m.privateView(A).hand().stream().anyMatch(c -> c.definitionId().equals(trainer)))) return m;
        }
        throw new AssertionError("no deterministic fixture");
    }
    static CardInstance hand(ServerMatch m, String id) { return m.privateView(A).hand().stream().filter(c -> c.definitionId().equals(id)).findFirst().orElseThrow(); }
    static void battle() {
        var m = game(null, 1);
        m.attachBasicEnergy(A, hand(m, "sd100_cn_grass_energy").instanceId()); m.endTurn(A); m.endTurn(B);
        m.attachBasicEnergy(A, hand(m, "sd100_cn_grass_energy").instanceId());
        String attack = COMBAT.pokemon("sd100_cn_001").orElseThrow().attacks().getFirst().id();
        int attacks = 0, prizes = 0;
        while (m.phase() != MatchPhase.FINISHED && attacks < 10) {
            m.attack(A, attack); attacks++;
            while (m.phase() == MatchPhase.PRIZE_SELECTION) { m.takePrizeAt(m.pendingChoicePlayer(), 0); prizes++; }
            while (m.phase() == MatchPhase.CHOOSE_REPLACEMENT) {
                var chooser = m.pendingChoicePlayer(); m.chooseReplacement(chooser, m.benchPokemonView(chooser).getFirst().pokemonId());
            }
            if (m.phase() == MatchPhase.TURN) m.endTurn(B);
        }
        check(attacks > 0 && prizes >= 1 && m.result() != null && A.equals(m.result().winner()), "CN attack/KO/prize/end flow failed: " + m.result() + ", prizes=" + prizes);
    }
    static void energySearch() {
        var m = game("sd100_cn_367");
        m.playTrainer(A, hand(m, "sd100_cn_367").instanceId(), null);
        var prompt = m.effectCardPrompt(A);
        check(prompt != null && prompt.minimum() == 0 && prompt.maximum() == 1, "filtered search must permit fail-to-find");
        check(!prompt.candidates().isEmpty() && prompt.candidates().stream().allMatch(c -> CARDS.find(c.definitionId()).orElseThrow().basicEnergy()), "Energy Search offered nonbasic energy");
        for (UUID unauthorized : Arrays.asList(B, null)) {
            boolean rejected = false;
            try { m.effectCardPrompt(unauthorized); } catch (IllegalStateException expected) { rejected = true; }
            check(rejected, "private search leaked");
        }
        var selected = prompt.candidates().getFirst();
        m.submitEffectCards(A, prompt.resolutionId(), prompt.step(), List.of(selected.instanceId()));
        if (m.effectCardReveal() != null) {
            var revealId = m.effectCardReveal().id();
            m.acknowledgeEffectReveal(B, revealId);
        }
        check(m.privateView(A).hand().contains(selected), "Energy Search did not transfer selected card");
    }
    static void judge(boolean emptyOpponent) {
        var m = game("sd100_cn_398"); m.endTurn(A); m.endTurn(B);
        if (emptyOpponent) m.discardHandCardsByEffect(A, B, m.privateView(B).hand().stream().map(CardInstance::instanceId).toList());
        var trainer = hand(m, "sd100_cn_398");
        m.playTrainer(A, trainer.instanceId(), null);
        check(m.privateView(A).hand().size() == 4 && m.privateView(B).hand().size() == 4, "Judge did not draw four for both players");
        check(m.privateView(A).discard().contains(trainer) && !m.mayPlaySupporter(A), "Judge discard or Supporter limit incorrect");
        check(m.clientView(null).hand().isEmpty(), "Judge leaked spectator hand");
    }
    static void cook() {
        var m = game("sd100_cn_393"); m.endTurn(A); m.endTurn(B);
        var target = m.activePokemonView(A).pokemonId();
        m.placeDamageCountersByEffect(A, A, target, 10);
        m.playTrainer(A, hand(m, "sd100_cn_393").instanceId(), null);
        check(m.activePokemonView(A).damage() == 30 && !m.mayPlaySupporter(A), "Cook healing or Supporter limit incorrect");
    }
    static ServerMatch tropiusGame() {
        for (int seed = 0; seed < 1000; seed++) {
            var m = new ServerMatch(A, B, syntheticDeck(A, "sd100_cn_013", null), syntheticDeck(B, "sd100_cn_013", null), RULE, CARDS, COMBAT, new ServerRandom(seed));
            OpeningTestSupport.decideOrder(m, A, B);
            if (List.of(A, B).stream().anyMatch(p -> m.privateView(p).hand().stream().filter(c -> CARDS.find(c.definitionId()).orElseThrow().basicPokemon()).count() < 2)) continue;
            if (m.privateView(A).hand().stream().filter(c -> c.definitionId().equals("sd100_cn_grass_energy")).count() < 4) continue;
            OpeningTestSupport.complete(m, A, B, 1); return m;
        }
        throw new AssertionError("No Tropius fixture");
    }
    static void settleKnockouts(ServerMatch m) {
        while (m.phase() == MatchPhase.PRIZE_SELECTION) m.takePrizeAt(m.pendingChoicePlayer(), 0);
        while (m.phase() == MatchPhase.CHOOSE_REPLACEMENT) {
            var p = m.pendingChoicePlayer(); m.chooseReplacement(p, m.benchPokemonView(p).getFirst().pokemonId());
        }
    }
    static void tropius(String knockout) {
        var m = tropiusGame(); var bench = m.benchPokemonView(A).getFirst().pokemonId();
        for (int turn = 0; turn < 3; turn++) {
            m.attachEnergy(A, hand(m, "sd100_cn_grass_energy").instanceId(), turn < 2 ? bench : m.activePokemonView(A).pokemonId());
            m.endTurn(A);
            if (turn < 2) m.attachBasicEnergy(B, m.privateView(B).hand().stream().filter(c -> c.definitionId().equals("sd100_cn_grass_energy")).findFirst().orElseThrow().instanceId());
            m.endTurn(B);
        }
        m.attachBasicEnergy(A, hand(m, "sd100_cn_grass_energy").instanceId());
        m.attack(A, "rally-back"); check(m.lastAttack().finalDamage() == 30, "Rally Back's base damage is not 30");
        if (knockout.equals("ATTACK")) {
            m.placeDamageCountersByEffect(B, A, m.activePokemonView(A).pokemonId(), 9);
            m.attack(B, "rally-back"); settleKnockouts(m);
        } else if (knockout.equals("COUNTERS")) {
            m.placeDamageCountersByEffect(B, A, m.activePokemonView(A).pokemonId(), 11); settleKnockouts(m); m.endTurn(B);
        } else m.endTurn(B);
        m.attack(A, "rally-back");
        check(m.lastAttack().finalDamage() == (knockout.equals("ATTACK") ? 120 : 30), "Rally Back's KO condition wrong: " + knockout);
        if (knockout.equals("ATTACK")) {
            settleKnockouts(m); m.endTurn(B); m.attack(A, "rally-back");
            check(m.lastAttack().finalDamage() == 30, "Rally Back bonus persisted beyond the next turn");
        }
    }
}
