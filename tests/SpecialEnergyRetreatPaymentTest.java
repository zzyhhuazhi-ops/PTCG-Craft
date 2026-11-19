import dev.hua.ptcglive.engine.EffectSourceZone;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.field.EnergyProfile;
import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.engine.field.PokemonProfile;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Retreat costs use the same server-owned Special Energy unit model as attack costs. */
public final class SpecialEnergyRetreatPaymentTest {
    private static final UUID A = new UUID(0, 6361), B = new UUID(0, 6362);
    private static final EnergyProfile DOUBLE_COLORLESS = EnergyProfile.fixed(EnergyType.COLORLESS, 2);
    private static final EnergyProfile SINGLE_CHOICE = new EnergyProfile(List.of(
        new EnergyProfile.Unit(Set.of(EnergyType.FIRE, EnergyType.WATER))));

    public static void main(String[] args) {
        ServerMatch paid = fixture();
        UUID active = paid.activePokemonView(A).pokemonId();
        UUID bench = paid.benchPokemonView(A).getFirst().pokemonId();
        CardInstance doubleEnergy = hand(paid, "double").getFirst();
        paid.attachEnergyByEffect(A, A, EffectSourceZone.HAND, doubleEnergy.instanceId(), A, active);
        paid.retreat(A, bench, List.of(doubleEnergy.instanceId()));
        if (!paid.activePokemonView(A).pokemonId().equals(bench)
            || paid.privateView(A).discard().stream().noneMatch(card -> card.instanceId().equals(doubleEnergy.instanceId())))
            throw new AssertionError("双单位特殊能量没有作为一张卡正确支付两点撤退费用");

        ServerMatch insufficient = fixture();
        UUID secondActive = insufficient.activePokemonView(A).pokemonId();
        UUID secondBench = insufficient.benchPokemonView(A).getFirst().pokemonId();
        CardInstance single = hand(insufficient, "single").getFirst();
        insufficient.attachEnergyByEffect(A, A, EffectSourceZone.HAND, single.instanceId(), A, secondActive);
        rejects(() -> insufficient.retreat(A, secondBench, List.of(single.instanceId())));
        if (!insufficient.activePokemonView(A).pokemonId().equals(secondActive)
            || insufficient.privateView(A).discard().stream().anyMatch(card -> card.instanceId().equals(single.instanceId())))
            throw new AssertionError("不足的特殊能量撤退支付没有原子性拒绝");

        ServerMatch extra = fixture();
        UUID thirdActive = extra.activePokemonView(A).pokemonId();
        UUID thirdBench = extra.benchPokemonView(A).getFirst().pokemonId();
        CardInstance redundantDouble = hand(extra, "double").getFirst();
        CardInstance redundantSingle = hand(extra, "single").getFirst();
        extra.attachEnergyByEffect(A, A, EffectSourceZone.HAND, redundantDouble.instanceId(), A, thirdActive);
        extra.attachEnergyByEffect(A, A, EffectSourceZone.HAND, redundantSingle.instanceId(), A, thirdActive);
        // Official Dash Pouch FAQ permits two Double Colorless cards for a two-Energy retreat cost.
        // Likewise, one chosen unit may come from the Double and the other from the single-unit card.
        extra.retreat(A, thirdBench, List.of(redundantDouble.instanceId(), redundantSingle.instanceId()));
        if(!extra.privateView(A).discard().containsAll(List.of(redundantDouble,redundantSingle)))throw new AssertionError("合法的双能量与单能量组合未整卡弃置");
        if(!EnergyProfile.isValidGenericPayment(List.of(DOUBLE_COLORLESS,DOUBLE_COLORLESS),2)
            ||EnergyProfile.isValidGenericPayment(List.of(DOUBLE_COLORLESS,DOUBLE_COLORLESS),1)
            ||EnergyProfile.isValidGenericPayment(List.of(new EnergyProfile(List.of()),DOUBLE_COLORLESS),2)
            ||EnergyProfile.isValidGenericPayment(List.of(DOUBLE_COLORLESS),0))throw new AssertionError("撤退的卡数或零供能边界错误");
        System.out.println("SpecialEnergyRetreatPaymentTest passed: multi-unit retreat choices, indivisible cards, underpayment, card-count limit and zero-unit rejection");
    }

    static ServerMatch fixture() {
        CardDefinition active = definition("active", CardCategory.POKEMON, true);
        CardDefinition bench = definition("bench", CardCategory.POKEMON, true);
        CardDefinition doubleEnergy = definition("double", CardCategory.ENERGY, false);
        CardDefinition single = definition("single", CardCategory.ENERGY, false);
        CombatCatalog combat = new CombatCatalog(Map.of(
            "active", new PokemonProfile(120, 2, null),
            "bench", new PokemonProfile(100, 0, null)), Map.of(), Map.of(),
            Map.of("double", DOUBLE_COLORLESS, "single", SINGLE_CHOICE));
        CardDatabase data = new CardDatabase(List.of(active, bench, doubleEnergy, single));
        for (long seed = 1; seed < 10_000; seed++) {
            ServerMatch match = new ServerMatch(A, B, deck(A, true), deck(B, false), MatchRulesRegressionTest.RULE, data, combat, new ServerRandom(seed));
            OpeningTestSupport.complete(match, A, B, 1);
            if (match.currentPlayer().equals(B)) match.endTurn(B);
            if (match.currentPlayer().equals(A) && match.activePokemonView(A).topCardId().equals("active") && match.benchPokemonView(A).size() >= 1
                && hand(match, "double").size() >= 1 && hand(match, "single").size() >= 2) return match;
            match.endTurn(match.currentPlayer());
        }
        throw new AssertionError("无法建立含特殊能量和备战宝可梦的确定性撤退测试对局");
    }
    private static CardDefinition definition(String id, CardCategory category, boolean basic) {
        return new CardDefinition(id, id, category, TrainerCategory.NONE, "H", Set.of("international"), basic,
            false, 60, Map.of(), EffectSupport.SUPPORTED, null);
    }
    private static List<CardInstance> deck(UUID owner, boolean first) {
        List<CardInstance> cards = new ArrayList<>();
        for (int i = 0; i < (first ? 18 : 60); i++) cards.add(new CardInstance(UUID.randomUUID(), owner, first ? "active" : "bench"));
        if (first) {
            for (int i = 0; i < 18; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, "bench"));
            for (int i = 0; i < 12; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, "double"));
            for (int i = 0; i < 12; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, "single"));
        }
        return cards;
    }
    private static List<CardInstance> hand(ServerMatch match, String id) {
        return match.privateView(A).hand().stream().filter(card -> card.definitionId().equals(id)).toList();
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("服务器错误接受了无效特殊能量撤退支付");
    }
}
