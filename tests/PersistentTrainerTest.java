import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.view.MatchRegionModel;
import java.util.*;

/** Fixtures are deliberately synthetic, not claims of legal physical deck lists. */
public final class PersistentTrainerTest {
    static final UUID A = new UUID(0, 901), B = new UUID(0, 902), S = new UUID(0, 903);
    static final Ruleset RULE = BundledRules.loadRulesets().get("standard-international-2026");
    public static void main(String[] args) {
        stadiumAndTools(); toolEvolutionAndKnockout(); paidRetreat(); actualData();
        System.out.println("PersistentTrainerTest passed: stadium limits/ownership, tools, continuous retreat, evolution/KO, atomic rejects, public privacy");
    }
    static void stadiumAndTools() {
        var m = game(); UUID active = m.activePokemonView(A).pokemonId(), bench = m.benchPokemonView(A).getFirst().pokemonId();
        var session = MatchSession.multiplayer(m, A, B); session.addSpectator(S);
        var initial = session.view(S, null);
        check(initial.game().stadium() == null && initial.game().hand().isEmpty(), "spectator pre-play information");
        var stadium = hand(m, A, "court");
        rejectAtomic(m, B, hand(m, B, "court"), null);
        m.playTrainer(A, stadium.instanceId(), null);
        check(m.clientView(null).stadium().equals(stadium), "stadium is not public");
        check(m.activeRetreatCost(A) == 2 && m.activeRetreatCost(B) == 2, "stadium must affect both sides");
        rejectAtomic(m, A, hand(m, A, "court2"), null);
        rejectAtomic(m, A, hand(m, A, "balloon"), m.activePokemonView(B).pokemonId());
        var extraTool = hand(m, A, "tool-extra"); m.playTrainer(A, extraTool.instanceId(), active);
        // Capacity is contributed by the already attached Tool, so a normal Tool may become the second card.
        var tool = hand(m, A, "balloon"); m.playTrainer(A, tool.instanceId(), active);
        check(m.activeRetreatCost(A) == 0, "tool + stadium must stack and clamp at zero");
        check(m.activePokemonView(A).attachedTools().size() == 2 && m.activePokemonView(A).attachedTools().contains(extraTool),
            "明确允许第二张工具的数据例外没有由服务器结算");
        rejectAtomic(m, A, hand(m, A, "balloon"), active);
        rejectAtomic(m, A, hand(m, A, "tool-extra"), active);
        m.playTrainer(A, hand(m, A, "balloon").instanceId(), bench);
        check(m.activePokemonView(A).attachedTools().size() == 2 && m.activePokemonView(A).attachedTools().contains(tool), "工具在第二张工具例外后丢失或被错误弃置");
        check(!m.privateView(A).discard().contains(tool), "attached tool also in discard");
        check(m.mayPlaySupporter(A), "tool/stadium consumed Supporter action");
        m.retreat(A, bench, List.of());
        check(m.benchPokemonView(A).getFirst().attachedTools().contains(tool), "retreat discarded tool");
        var publicView = session.view(S, null);
        check(publicView.game().hand().isEmpty() && publicView.options().isEmpty(), "spectator received private actions/hand");
        var region = MatchRegionModel.from(publicView);
        check(region.zone("stadium").cards().size() == 1 && region.visibleCards().stream().anyMatch(c -> c.definitionId().equals("balloon")), "public persistent-card art missing");
        m.endTurn(A);
        rejectAtomic(m, B, hand(m, B, "court-reprint"), null);
        var replacement = hand(m, B, "court2"); m.playTrainer(B, replacement.instanceId(), null);
        check(m.privateView(A).discard().contains(stadium) && !m.privateView(B).discard().contains(stadium), "replaced stadium went to wrong owner");
        check(m.activeRetreatCost(B) == 1, "old stadium effect survived replacement");
        m.endTurn(B); m.playTrainer(A, hand(m, A, "court").instanceId(), null);
        check(m.privateView(B).discard().contains(replacement), "stadium turn limit did not reset or wrong discard");
        conserve(m);
    }
    static void toolEvolutionAndKnockout() {
        var m = game(); UUID active = m.activePokemonView(A).pokemonId();
        var tool = hand(m, A, "balloon"); m.playTrainer(A, tool.instanceId(), active);
        m.playTrainer(A, hand(m, A, "court").instanceId(), null);
        m.endTurn(A); m.endTurn(B);
        m.evolve(A, active, hand(m, A, "evolved").instanceId());
        check(m.activeRetreatCost(A) == 1, "basic-only stadium applied after evolution");
        check(m.activePokemonView(A).attachedTools().contains(tool), "evolution discarded tool");
        var energy = hand(m, A, "energy"); m.attachEnergy(A, energy.instanceId(), active);
        int count = m.privateView(A).discard().size();
        TrainerEffectsTest.rejects(() -> m.retreat(A, m.benchPokemonView(A).getFirst().pokemonId(), List.of()));
        check(m.privateView(A).discard().size() == count && m.activePokemonView(A).pokemonId().equals(active), "underpayment changed state");
        m.endTurn(A); m.attack(B, "ko");
        check(m.privateView(A).discard().contains(tool) && m.privateView(A).discard().contains(energy), "KO lost tool/energy");
        check(m.privateView(A).discard().stream().filter(c -> c.equals(tool)).count() == 1, "KO duplicated tool");
        conserve(m);
    }
    static void actualData() {
        var db = BundledRules.loadCardDefinitions(); var combat = BundledCombatData.load();
        check(combat.trainer("zsv10-5_en_079").orElseThrow().category() == TrainerCategory.TOOL, "actual balloon mapping");
        check(combat.trainer("sv1_en_167").orElseThrow().category() == TrainerCategory.STADIUM, "actual court mapping");
        check(!RULE.allows(db.find("sv1_en_167").orElseThrow()), "G stadium silently legalized");
        var legal = new ArrayList<CardInstance>();
        for (int i = 0; i < 60; i++) legal.add(new CardInstance(new UUID(90, i), A, i < 4 ? "sv5_en_014" : i < 8 ? "zsv10-5_en_079" : "sve_en_001"));
        check(new DeckValidator(db, combat).validate(RULE, legal).canStartOfficialMatch(), "real I-mark tool deck rejected");
        TrainerEffectsTest.rejects(() -> new TrainerProfile(TrainerCategory.ITEM, effect(TrainerEffect.Opcode.TOOL_RETREAT_REDUCTION, 2)));
        TrainerEffectsTest.rejects(() -> new TrainerProfile(TrainerCategory.STADIUM, effect(TrainerEffect.Opcode.DRAW, 2)));
        check(new TrainerEffect(TrainerEffect.Opcode.TOOL_RETREAT_REDUCTION, 1, 0, 4).toolCapacityOnAttach() == 4,
            "版本化工具容量没有保留到效果数据");
        TrainerEffectsTest.rejects(() -> new TrainerEffect(TrainerEffect.Opcode.TOOL_RETREAT_REDUCTION, 1, 0, 5));
        TrainerEffectsTest.rejects(() -> new TrainerEffect(TrainerEffect.Opcode.DRAW, 1, 0, 1));
    }
    static void paidRetreat() {
        var m = game(); UUID active = m.activePokemonView(A).pokemonId();
        var tool = hand(m, A, "balloon"); var energy = hand(m, A, "energy");
        m.playTrainer(A, tool.instanceId(), active); m.attachEnergy(A, energy.instanceId(), active);
        check(m.activeRetreatCost(A) == 1, "tool-only reduction");
        m.retreat(A, m.benchPokemonView(A).getFirst().pokemonId(), List.of(energy.instanceId()));
        check(m.privateView(A).discard().contains(energy) && !m.privateView(A).discard().contains(tool), "modified cost payment confused energy and tool");
        TrainerEffectsTest.rejects(() -> m.retreat(A, active, List.of()));
        conserve(m);
    }
    static void conserve(ServerMatch m) {
        var seen = new HashSet<UUID>(); int total = 0;
        for (UUID player : List.of(A, B)) {
            var state = m.privateView(player);
            for (var zone : List.of(state.hand(), state.deck(), state.prizes(), state.discard())) for (var card : zone) {
                check(seen.add(card.instanceId()), "card exists in multiple zones"); total++;
            }
            var view = m.clientView(null).players().stream().filter(p -> p.id().equals(player)).findFirst().orElseThrow();
            var pokemon = new ArrayList<BattlefieldState.PokemonView>(view.bench()); if (view.active() != null) pokemon.add(view.active());
            for (var field : pokemon) {
                total += field.evolutionCardIds().size() + field.attachedEnergyIds().size() + field.attachedTools().size();
                check(seen.add(field.pokemonId()), "base card duplicated");
                for (UUID energy : field.attachedEnergyIds()) check(seen.add(energy), "energy duplicated");
                for (var tool : field.attachedTools()) check(seen.add(tool.instanceId()), "tool duplicated");
            }
        }
        if (m.clientView(null).stadium() != null) { total++; check(seen.add(m.clientView(null).stadium().instanceId()), "stadium duplicated"); }
        check(total == 120, "card count not conserved across persistent zones/KO");
    }
    static void rejectAtomic(ServerMatch m, UUID player, CardInstance card, UUID target) {
        var before = m.clientView(player); int audit = m.randomTranscript().size();
        TrainerEffectsTest.rejects(() -> m.playTrainer(player, card.instanceId(), target));
        check(before.equals(m.clientView(player)) && audit == m.randomTranscript().size(), "rejected trainer changed state");
    }
    static TrainerEffect effect(TrainerEffect.Opcode op, int amount) { return new TrainerEffect(op, amount, 0); }
    static ServerMatch game() {
        return game(RULE);
    }
    static ServerMatch game(Ruleset rule) {
        var defs = new ArrayList<CardDefinition>();
        String[] ids = {"basic", "evolved", "court", "court-reprint", "court2", "balloon", "tool-extra", "energy", "draw-all"};
        for (String id : ids) {
            boolean pokemon = id.equals("basic") || id.equals("evolved"), energy = id.equals("energy");
            var category = pokemon || energy ? TrainerCategory.NONE : id.startsWith("court") ? TrainerCategory.STADIUM : (id.equals("balloon") || id.equals("tool-extra")) ? TrainerCategory.TOOL : TrainerCategory.ITEM;
            defs.add(new CardDefinition(id, id.equals("court-reprint") ? "court" : id, pokemon ? CardCategory.POKEMON : energy ? CardCategory.ENERGY : CardCategory.TRAINER,
                category, "H", Set.of("international"), id.equals("basic"), energy, 4, Map.of(), EffectSupport.SUPPORTED, null));
        }
        var ko = new AttackDefinition("ko", "测试击倒", Map.of(), 200, EnergyType.COLORLESS, EffectSupport.SUPPORTED, null);
        var trainers = Map.of("court", new TrainerProfile(TrainerCategory.STADIUM, effect(TrainerEffect.Opcode.STADIUM_BASIC_RETREAT_REDUCTION, 1)),
            "court-reprint", new TrainerProfile(TrainerCategory.STADIUM, effect(TrainerEffect.Opcode.STADIUM_BASIC_RETREAT_REDUCTION, 1)),
            "court2", new TrainerProfile(TrainerCategory.STADIUM, effect(TrainerEffect.Opcode.STADIUM_BASIC_RETREAT_REDUCTION, 2)),
            "balloon", new TrainerProfile(TrainerCategory.TOOL, effect(TrainerEffect.Opcode.TOOL_RETREAT_REDUCTION, 2)),
            "tool-extra", new TrainerProfile(TrainerCategory.TOOL, effect(TrainerEffect.Opcode.TOOL_RETREAT_REDUCTION_ALLOW_TWO, 1)),
            "draw-all", new TrainerProfile(TrainerCategory.ITEM, effect(TrainerEffect.Opcode.DRAW, 40)));
        var combat = new CombatCatalog(Map.of("basic", new PokemonProfile(100, 3, null, null, 0, null, 0, 1, List.of(ko)),
            "evolved", new PokemonProfile(150, 3, "basic", null, 0, null, 0, 1, List.of(ko))), Map.of("energy", EnergyType.GRASS), trainers);
        for (int seed = 0; seed < 10000; seed++) {
            var decks = new ArrayList<List<CardInstance>>();
            for (UUID owner : List.of(A, B)) {
                var cards = new ArrayList<CardInstance>();
                for (int i = 0; i < 60; i++) cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(), i+1), owner, ids[i % ids.length]));
                decks.add(cards);
            }
            var m = new ServerMatch(A, B, decks.get(0), decks.get(1), rule, new CardDatabase(defs), combat, new ServerRandom(seed));
            OpeningTestSupport.decideOrder(m, A, B);
            if (List.of(A, B).stream().anyMatch(p -> m.privateView(p).hand().stream().filter(c -> c.definitionId().equals("basic")).count() < 2)) continue;
            OpeningTestSupport.complete(m, A, B, 1);
            if (List.of(A,B).stream().allMatch(p -> !m.benchPokemonView(p).isEmpty() && m.privateView(p).hand().stream().anyMatch(c -> c.definitionId().equals("draw-all")))) {
                m.playTrainer(A, hand(m, A, "draw-all").instanceId(), null);
                check(!m.mayPlaySupporter(A), "first-turn supporter restriction");
                if (m.privateView(A).hand().stream().anyMatch(c -> c.definitionId().equals("balloon")))
                    check(m.canPlayTrainer(A, hand(m, A, "balloon").instanceId(), m.activePokemonView(A).pokemonId()), "first-turn Tool wrongly forbidden");
                m.endTurn(A);
                m.playTrainer(B, hand(m, B, "draw-all").instanceId(), null); m.endTurn(B);
                if (List.of(A,B).stream().allMatch(p -> Arrays.stream(ids).skip(1).allMatch(id -> m.privateView(p).hand().stream().anyMatch(c -> c.definitionId().equals(id))))
                    && m.privateView(A).deck().stream().anyMatch(c -> c.definitionId().equals("basic"))) return m;
            }
        }
        throw new AssertionError("fixture seed not found");
    }
    static CardInstance hand(ServerMatch m, UUID p, String id) { return TrainerEffectsTest.hand(m, p, id); }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
