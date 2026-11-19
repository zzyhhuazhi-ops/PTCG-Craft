import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class TrainerEffectsTest {
    static final UUID A = new UUID(0, 801), B = new UUID(0, 802);
    static final Ruleset RULE = BundledRules.loadRulesets().get("standard-international-2026");
    public static void main(String[] args) {
        effects(); drawingEmptyDeck(); drawUntilHandSize(); drawUntilBoundary(); actualCards(); actualShuffle();
        System.out.println("TrainerEffectsTest passed: timing, targets, healing, switching, hand-size draw, partial draw, real card mappings and coin audit");
    }
    static void effects() {
        var m = game(); setup(m);
        var supporter = hand(m, A, "draw");
        rejects(() -> m.playTrainer(A, supporter.instanceId(), null)); // first player's first turn
        int before = m.privateView(A).hand().size();
        rejects(() -> m.playTrainer(A, hand(m, A, "potion").instanceId(), UUID.randomUUID()));
        check(m.privateView(A).hand().size() == before, "invalid target consumed card");
        rejects(() -> m.playTrainer(A, hand(m, A, "potion").instanceId(), m.activePokemonView(A).pokemonId()));
        m.endTurn(A); m.attack(B, "hit");
        UUID original = m.activePokemonView(A).pokemonId();
        check(m.activePokemonView(A).damage() == 30, "damage fixture");
        m.playTrainer(A, hand(m, A, "potion").instanceId(), original);
        check(m.activePokemonView(A).damage() == 0, "heal amount");
        check(m.activePokemonView(A).conditions().contains(SpecialCondition.PARALYZED), "healing cured paralysis");
        UUID bench = m.benchPokemonView(A).getFirst().pokemonId();
        m.playTrainer(A, hand(m, A, "switch").instanceId(), bench);
        check(!m.retreated(), "effect switch consumed retreat action");
        m.retreat(A, original, List.of());
        check(m.activePokemonView(A).conditions().isEmpty(), "switch did not clear conditions");
        before = m.privateView(A).hand().size();
        m.playTrainer(A, supporter.instanceId(), null);
        check(m.privateView(A).hand().size() == before + 2, "draw supporter count or own discard");
        check(!m.mayPlaySupporter(A), "supporter per-turn flag missing");
        rejects(() -> m.playTrainer(A, hand(m, A, "draw").instanceId(), null));
        rejects(() -> m.playTrainer(B, hand(m, B, "draw").instanceId(), null));
    }
    static void drawingEmptyDeck() {
        var m = game(); setup(m); m.endTurn(A);
        // Effects draw as much as possible. Deck-out is only checked on the required turn draw.
        m.playTrainer(B, hand(m, B, "draw-all").instanceId(), null);
        check(m.privateView(B).deck().isEmpty() && m.result() == null, "effect draw incorrectly loses the game");
        m.endTurn(B); m.endTurn(A);
        check(m.result().reason() == MatchResult.Reason.DECK_OUT && m.result().winner().equals(A), "mandatory draw deck-out");
    }
    static void drawUntilHandSize() {
        var m = game(); setup(m); m.endTurn(A);
        var trainer = hand(m, B, "draw-to");
        int handBefore = m.privateView(B).hand().size();
        int deckBefore = m.privateView(B).deck().size();
        check(handBefore < 7, "补齐手牌测试夹具起始手牌不应达到目标数量");
        m.playTrainer(B, trainer.instanceId(), null);
        check(m.privateView(B).hand().size() == 7, "补齐手牌效果没有以移出该训练家后的手牌数为准");
        check(m.privateView(B).deck().size() == deckBefore - (8 - handBefore), "补齐手牌效果抽取数量错误");
        check(m.privateView(B).discard().contains(trainer), "补齐手牌训练家没有进入弃牌区");
    }
    static void actualCards() {
        CardDatabase db = BundledRules.loadCardDefinitions(); var combat = BundledCombatData.load();
        Set<String> art = new HashSet<>(); dev.hua.ptcglive.CardCatalog.load().forEach(c -> art.add(c.id()));
        for (String id : List.of("me1_en_119", "sv1_en_188", "sv1_en_194", "sv5_en_014", "sve_en_001")) {
            check(art.contains(id) && db.find(id).isPresent(), "real art ID has no rule definition: " + id);
        }
        var effect = combat.trainer("me1_en_119").orElseThrow().effect();
        check(effect.drawCount(6) == 8 && effect.drawCount(5) == 6, "Lillie prize-dependent draw");
        check(RULE.allows(db.find("sve_en_001").orElseThrow()), "basic energy wrongly needs regulation mark");
        List<CardInstance> deck = new ArrayList<>();
        for (int i = 0; i < 60; i++) deck.add(new CardInstance(UUID.randomUUID(), A, i < 4 ? "sv5_en_014" : i < 8 ? "me1_en_119" : "sve_en_001"));
        check(new DeckValidator(db, combat).validate(RULE, deck).canStartOfficialMatch(), "audited real-card deck rejected");
        var attack = combat.pokemon("sv5_en_014").orElseThrow().attacks().getFirst();
        for (int seed = 0; seed < 20; seed++) {
            ServerRandom random = new ServerRandom(seed);
            int damage = attack.resolveBaseDamage(random);
            check(damage >= 0 && damage <= 30 && damage % 10 == 0 && random.transcript().size() == 3, "triple-spin coins not authoritative/audited");
        }
        rejects(() -> new TrainerProfile(TrainerCategory.STADIUM, new TrainerEffect(TrainerEffect.Opcode.DRAW, 3, 0)));
    }
    static void drawUntilBoundary() {
        var combat=BundledCombatData.load();
        for(String id:List.of("sd100_cn_394","swsh5_en_128","swsh5_en_160","swsh5_en_174")) {
            var profile=combat.trainer(id).orElseThrow();
            check(profile.effect().opcode()==TrainerEffect.Opcode.DRAW_UNTIL_HAND_SIZE&&profile.effect().amount()==6,"Korrina rule differs: "+id);
            for(int handSize:List.of(5,6,7))for(boolean emptyDeck:List.of(false,true)) {
                var m=game(profile);setup(m);m.endTurn(A);var trainer=hand(m,B,"draw-to");
                var held=GeneratedRuleBatch49Test.zone(m,B,"hand");var deck=GeneratedRuleBatch49Test.zone(m,B,"deck");
                while(held.size()>handSize){var card=held.stream().filter(c->!c.equals(trainer)).findFirst().orElseThrow();held.remove(card);deck.add(card);}
                while(held.size()<handSize)held.add(deck.removeFirst());
                if(emptyDeck){GeneratedRuleBatch49Test.zone(m,B,"discard").addAll(deck);deck.clear();}
                int before=deck.size();boolean allowed=handSize<=6&&!emptyDeck;
                check(m.canPlayTrainer(B,trainer.instanceId(),null)==allowed,"Played supporter counted in hand: "+id+" hand="+handSize+" empty="+emptyDeck);
                if(allowed){
                    m.playTrainer(B,trainer.instanceId(),null);
                    check(m.privateView(B).hand().size()==6&&m.privateView(B).deck().size()==before-(7-handSize),"Wrong draw at boundary: "+id);
                    check(m.privateView(B).discard().contains(trainer)&&!m.mayPlaySupporter(B),"Supporter not consumed correctly");
                }else{
                    rejects(()->m.playTrainer(B,trainer.instanceId(),null));
                    check(m.privateView(B).hand().contains(trainer)&&m.privateView(B).hand().size()==handSize&&m.mayPlaySupporter(B),"Rejected card consumed state");
                }
            }
        }
    }
    static ServerMatch game() {
        return game(new TrainerProfile(TrainerCategory.SUPPORTER,new TrainerEffect(TrainerEffect.Opcode.DRAW_UNTIL_HAND_SIZE,7,0)));
    }
    static ServerMatch game(TrainerProfile drawUntil) {
        var defs = new ArrayList<CardDefinition>();
        for (String id : List.of("basic", "potion", "switch", "draw", "draw-all", "draw-to", "energy")) {
            boolean pokemon = id.equals("basic"), energy = id.equals("energy");
            defs.add(new CardDefinition(id, id, pokemon ? CardCategory.POKEMON : energy ? CardCategory.ENERGY : CardCategory.TRAINER,
                pokemon || energy ? TrainerCategory.NONE : id.startsWith("draw") ? TrainerCategory.SUPPORTER : TrainerCategory.ITEM,
                "H", Set.of("international"), pokemon, energy, 4, Map.of(), EffectSupport.SUPPORTED, null));
        }
        var hit = new AttackDefinition("hit", "测试攻击", Map.of(), 30, EnergyType.COLORLESS, EffectSupport.SUPPORTED, null,
            List.of(new ConditionEffect(ConditionEffect.Target.DEFENDER, SpecialCondition.PARALYZED)));
        var trainers = Map.of("potion", new TrainerProfile(TrainerCategory.ITEM, new TrainerEffect(TrainerEffect.Opcode.HEAL, 30, 0)),
            "switch", new TrainerProfile(TrainerCategory.ITEM, new TrainerEffect(TrainerEffect.Opcode.SWITCH_ACTIVE, 0, 0)),
            "draw", new TrainerProfile(TrainerCategory.SUPPORTER, new TrainerEffect(TrainerEffect.Opcode.DRAW, 3, 0)),
            "draw-all", new TrainerProfile(TrainerCategory.SUPPORTER, new TrainerEffect(TrainerEffect.Opcode.DISCARD_HAND_DRAW, 60, 0)),
            "draw-to", drawUntil);
        var combat = new CombatCatalog(Map.of("basic", new PokemonProfile(100, 0, null, null, 0, null, 0, 1, List.of(hit))),
            Map.of("energy", EnergyType.GRASS), trainers);
        for (int seed = 0; seed < 20000; seed++) {
            var aDeck = deck(A); var bDeck = deck(B);
            var m = new ServerMatch(A, B, aDeck, bDeck, RULE, new CardDatabase(defs), combat, new ServerRandom(seed));
            OpeningTestSupport.decideOrder(m, A, B);
            if (List.of(A, B).stream().allMatch(p -> m.privateView(p).hand().stream().filter(c -> c.definitionId().equals("basic")).count() >= 2)
                && List.of("potion", "switch", "draw").stream().allMatch(id -> m.privateView(A).hand().stream().anyMatch(c -> c.definitionId().equals(id)))
                && m.privateView(A).hand().stream().filter(c -> c.definitionId().equals("draw")).count() >= 2
                && List.of("draw", "draw-all", "draw-to").stream().allMatch(id -> m.privateView(B).hand().stream().anyMatch(c -> c.definitionId().equals(id))))
                return new ServerMatch(A, B, aDeck, bDeck, RULE, new CardDatabase(defs), combat, new ServerRandom(seed));
        }
        throw new AssertionError("no test fixture found");
    }
    static List<CardInstance> deck(UUID owner) {
        var cards = new ArrayList<CardInstance>();
        for (int i = 0; i < 60; i++) cards.add(new CardInstance(UUID.randomUUID(), owner,
            i < 18 ? "basic" : i < 26 ? "potion" : i < 34 ? "switch" : i < 44 ? "draw" : i < 52 ? "draw-all" : i < 59 ? "draw-to" : "energy"));
        return cards;
    }
    static void actualShuffle() {
        var db = BundledRules.loadCardDefinitions(); var combat = BundledCombatData.load();
        var decks = new ArrayList<List<CardInstance>>();
        for (UUID owner : List.of(A, B)) {
            var cards = new ArrayList<CardInstance>();
            for (int i = 0; i < 60; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, i < 4 ? "sv5_en_014" : i < 8 ? "me1_en_119" : "sve_en_001"));
            decks.add(cards);
        }
        ServerMatch selected = null;
        for (int seed = 0; seed < 1000; seed++) {
            var m = new ServerMatch(A, B, decks.get(0), decks.get(1), RULE, db, combat, new ServerRandom(seed));
            OpeningTestSupport.decideOrder(m, A, B);
            if (!m.needsMulligan(A) && !m.needsMulligan(B)
                && m.privateView(B).hand().stream().anyMatch(c -> c.definitionId().equals("me1_en_119"))) { selected = m; break; }
        }
        check(selected != null, "real-card shuffle fixture"); var m = selected;
        OpeningTestSupport.complete(m, A, B, 0); m.endTurn(A);
        var trainer = hand(m, B, "me1_en_119");
        int total = m.privateView(B).hand().size() + m.privateView(B).deck().size();
        int randomness = m.randomTranscript().size();
        m.playTrainer(B, trainer.instanceId(), null);
        check(m.privateView(B).hand().size() == 8 && m.privateView(B).deck().size() == total - 9, "Lillie shuffle/draw conservation");
        check(m.privateView(B).discard().contains(trainer) && !m.privateView(B).hand().contains(trainer), "played supporter shuffled itself in");
        check(m.randomTranscript().size() > randomness, "shuffle not audited");
        var unknownEnergy = new CardDefinition("unknown-energy", "未知能量", CardCategory.ENERGY, TrainerCategory.NONE,
            "NONE", Set.of("international"), false, true, 999, Map.of(), EffectSupport.SUPPORTED, null);
        check(!RULE.allows(unknownEnergy), "unknown basic energy bypassed explicit whitelist");
    }
    static void setup(ServerMatch m) {
        OpeningTestSupport.complete(m, A, B, 1);
    }
    static CardInstance hand(ServerMatch m, UUID p, String id) { return m.privateView(p).hand().stream().filter(c -> c.definitionId().equals(id)).findFirst().orElseThrow(); }
    static void check(boolean result, String message) { if (!result) throw new AssertionError(message); }
    static void rejects(Runnable op) { try { op.run(); } catch (IllegalStateException | IllegalArgumentException expected) { return; } throw new AssertionError("illegal action accepted"); }
}
