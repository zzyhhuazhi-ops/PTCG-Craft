import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Real generated programs in controlled matches; neutral type/free cost isolate effect semantics. */
public final class GeneratedRuleBatch6Test {
    static final UUID A = DynamicPassiveModifierTest.A, B = DynamicPassiveModifierTest.B;
    static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }

    static AttackDefinition attack(String card, int slot) {
        String set = card.substring(0, card.indexOf("_en_"));
        try (var in = GeneratedRuleBatch6Test.class.getResourceAsStream("/data/ptcglive/combat/generated-" + set + ".json")) {
            var data = JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in), StandardCharsets.UTF_8)).getAsJsonObject();
            for (var entry : data.getAsJsonArray("cards")) {
                var pokemon = entry.getAsJsonObject();
                if (!pokemon.get("id").getAsString().equals(card)) continue;
                var json = pokemon.getAsJsonArray("attacks").get(slot).getAsJsonObject().deepCopy();
                check(json.get("effectSupport").getAsString().equals("SUPPORTED"), card + " attack unsupported");
                json.add("energyCost", new JsonObject());
                json.addProperty("damageType", "COLORLESS");
                var result = BundledCombatData.attack(json, EffectRegistry.standard());
                AttackProgramRules.validate(result);
                return result;
            }
            throw new AssertionError("Missing generated card " + card);
        } catch (IOException error) { throw new UncheckedIOException(error); }
    }

    static ServerMatch fixture(AttackDefinition attack) { return BeforeDamageAttackTest.fixture(attack); }
    static void use(ServerMatch m, AttackDefinition attack) { m.attack(A, attack.id()); }
    static void attach(ServerMatch m, UUID owner, UUID host, String id) { TypedEnergyCountTest.attach(m, owner, host, id); }

    public static void main(String[] args) {
        emptyHand(); energyUnits(); allHeads(); becameActive(); basicGuard(); energyDistribution(); extraPrizes();
        System.out.println("GeneratedRuleBatch6Test passed: real generated programs, zero/nonzero hand, Energy units, all coin outcomes, switch expiry, Basic guard, distribution authority and attack-only prizes");
    }

    static void emptyHand() {
        var attack = attack("sm8_en_138", 0);
        for (boolean empty : List.of(false, true)) {
            var m = fixture(attack);
            if (empty) m.discardHandCardsByEffect(A, A, m.privateView(A).hand().stream().map(CardInstance::instanceId).toList());
            use(m, attack);
            check(m.lastAttack().finalDamage() == (empty ? 160 : 30), "All Out hand condition or base damage");
        }
    }

    static void energyUnits() {
        var attack = attack("sm2_en_061", 0);
        for (boolean psychic : List.of(false, true)) {
            var m = DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(), DynamicPassiveModifierTest.plainStadium(),
                EnergyProfile.fixed(EnergyType.PSYCHIC, 2), attack);
            var active = m.activePokemonView(A).pokemonId();
            attach(m, A, active, "energy");
            if (psychic) attach(m, A, active, "special");
            attach(m, A, m.benchPokemonView(A).getFirst().pokemonId(), "special");
            use(m, attack);
            check(m.lastAttack().finalDamage() == (psychic ? 80 : 0), "Shatter Shot counts type/units/host incorrectly");
        }
    }

    static void allHeads() {
        var attack = attack("sm9_en_116", 0);
        var outcomes = new HashSet<String>();
        for (int skip = 0; skip < 32 && outcomes.size() < 4; skip++) {
            var m = fixture(attack);
            for (int i = 0; i < skip; i++) m.flipCoinsByEffect(A, 1);
            int start = m.randomTranscript().size();
            use(m, attack);
            var rolls = m.randomTranscript().subList(start, m.randomTranscript().size());
            check(rolls.size() == 2, "Dragon Rage must flip exactly two coins");
            outcomes.add(rolls.get(0).result() + ":" + rolls.get(1).result());
            boolean heads = rolls.stream().allMatch(r -> r.result() == 0);
            check(m.lastAttack().finalDamage() == (heads ? 60 : 0), "Dragon Rage requires both heads");
            var random = m.randomTranscript(); m.clientView(A); m.clientView(B);
            check(random.equals(m.randomTranscript()) && m.currentPlayer().equals(B), "View rerolled coins or attack failed to end turn");
        }
        check(outcomes.size() == 4, "Missing two-coin outcome");
    }

    static void becameActive() {
        var attack = attack("sv3_en_062", 1);
        for (boolean switched : List.of(false, true)) {
            var m = fixture(attack);
            var active = m.activePokemonView(A).pokemonId();
            if (switched) {
                m.switchActiveByEffect(A, A, m.benchPokemonView(A).getFirst().pokemonId());
                m.switchActiveByEffect(A, A, active);
            }
            use(m, attack);
            check(m.lastAttack().finalDamage() == (switched ? 210 : 0), "Justice Kick ignored switch condition");
            m.endTurn(B); use(m, attack);
            check(m.lastAttack().finalDamage() == 0, "Switch condition survived into next turn");
        }
    }

    static void basicGuard() {
        var attack = attack("xy9-5_en_028", 1);
        for (boolean evolved : List.of(false, true)) {
            var m = fixture(attack);
            use(m, attack);
            if (evolved) m.evolveByEffect(B, m.activePokemonView(B).pokemonId(), DynamicPassiveModifierTest.hand(m, B, "evolved").instanceId(), EvolutionPermission.CARD_EFFECT_ALL_TIMING);
            m.placeDamageCountersByEffect(B, A, m.activePokemonView(A).pokemonId(), 1);
            m.attack(B, attack.id());
            check(m.lastAttack().finalDamage() == (evolved ? 70 : 0), "Flash Ray must only block Basic attack damage");
            check(m.activePokemonView(A).damage() == (evolved ? 80 : 10), "Flash Ray blocked effect counters");
            m.endTurn(A); m.attack(B, attack.id());
            check(m.lastAttack().finalDamage() == 70, "Flash Ray failed to expire");
        }
    }

    static void energyDistribution() {
        var attack = attack("sm2_en_061", 1);
        for (boolean bench : List.of(false, true)) {
            var m = fixture(attack);
            var active = m.activePokemonView(A).pokemonId();
            attach(m, A, active, "energy"); attach(m, A, active, "special");
            var target = m.benchPokemonView(A).getFirst().pokemonId();
            if (!bench) m.movePokemonByEffect(A, A, target, LeavePlayDestination.HAND);
            use(m, attack);
            check(m.lastAttack().finalDamage() == 130, "Wings of the Moone printed damage");
            if (!bench) {
                check(!m.hasPendingCardSelection() && m.activePokemonView(A).attachedEnergyIds().size() == 2, "No Bench must keep Energy and finish");
                continue;
            }
            var cards = m.effectCardPrompt(A);
            check(cards.minimum() == 2 && cards.maximum() == 2, "All Energy includes Basic and Special cards");
            BeforeDamageAttackTest.reject(() -> m.submitEffectCards(A, cards.resolutionId(), cards.step(), List.of(cards.candidates().getFirst().instanceId())));
            m.submitEffectCards(A, cards.resolutionId(), cards.step(), cards.candidates().stream().map(CardInstance::instanceId).toList());
            var p = m.effectAttachmentPrompt(A);
            BeforeDamageAttackTest.reject(() -> m.confirmEffectEnergyDistribution(A, p.resolutionId(), p.step()));
            for (var card : p.cards()) {
                BeforeDamageAttackTest.reject(() -> m.assignEffectEnergyDistribution(B, p.resolutionId(), p.step(), card.instanceId(), target));
                BeforeDamageAttackTest.reject(() -> m.assignEffectEnergyDistribution(A, p.resolutionId(), p.step(), card.instanceId(), active));
                m.assignEffectEnergyDistribution(A, p.resolutionId(), p.step(), card.instanceId(), target);
            }
            check(m.activePokemonView(A).attachedEnergyIds().size() == 2, "Draft allocation moved Energy");
            m.confirmEffectEnergyDistribution(A, p.resolutionId(), p.step());
            check(m.activePokemonView(A).attachedEnergyIds().isEmpty() && m.benchPokemonView(A).getFirst().attachedEnergyIds().size() == 2, "Energy lost or duplicated");
            BeforeDamageAttackTest.reject(() -> m.confirmEffectEnergyDistribution(A, p.resolutionId(), p.step()));
        }
    }

    static void extraPrizes() {
        for (String id : List.of("swsh5_en_117", "swsh8_en_218")) {
            var attack = attack(id, 0);
            int bonus = id.equals("swsh5_en_117") ? 1 : 2;
            for (String mode : List.of("basic", "evolved", "poison", "later")) {
                var m = fixture(attack);
                if (mode.equals("evolved")) {
                    m.endTurn(A);
                    m.evolveByEffect(B, m.activePokemonView(B).pokemonId(), DynamicPassiveModifierTest.hand(m, B, "evolved").instanceId(), EvolutionPermission.CARD_EFFECT_ALL_TIMING);
                    m.endTurn(B);
                }
                var target = m.activePokemonView(B);
                int remaining = attack.baseDamage() + (mode.equals("poison") ? 10 : 0);
                if (!mode.equals("later")) m.placeDamageCountersByEffect(A, B, target.pokemonId(), (target.hitPoints() - remaining) / 10);
                if (mode.equals("poison")) m.applySpecialConditionByEffect(A, B, SpecialCondition.POISONED);
                use(m, attack);
                if (mode.equals("later")) m.knockOutPokemonByEffect(B, B, target.pokemonId());
                int before = m.privateView(A).prizes().size();
                while (m.phase() == MatchPhase.PRIZE_SELECTION) m.takePrizeAt(A, 0);
                check(before - m.privateView(A).prizes().size() == (mode.equals("basic") ? 1 + bonus : 1), "Extra prizes applied to wrong KO: " + id + " " + mode);
            }
        }
    }
}
