import dev.hua.ptcglive.engine.EffectSourceZone;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Versioned Special Energy units are allocated by the server, never selected or duplicated by the client. */
public final class SpecialEnergyPaymentTest {
    private static final UUID A = new UUID(0, 6351), B = new UUID(0, 6352);
    private static final EnergyProfile RAINBOW = new EnergyProfile(List.of(new EnergyProfile.Unit(Set.of(EnergyType.FIRE, EnergyType.WATER))));
    private static final EnergyProfile DOUBLE_COLORLESS = EnergyProfile.fixed(EnergyType.COLORLESS, 2);
    private static final Map<EnergyType, Integer> COST = Map.of(EnergyType.FIRE, 1, EnergyType.WATER, 1, EnergyType.COLORLESS, 1);

    public static void main(String[] args) {
        if (!EnergyProfile.canPay(List.of(RAINBOW, RAINBOW, DOUBLE_COLORLESS), COST))
            throw new AssertionError("两个可选类型单位和双无色单位应能满足火水无色费用");
        if (EnergyProfile.canPay(List.of(RAINBOW, DOUBLE_COLORLESS), COST))
            throw new AssertionError("同一个可选类型单位被错误重复用于火和水费用");

        ServerMatch paid = fixture(); UUID active = paid.activePokemonView(A).pokemonId();
        for (CardInstance card : hand(paid, "rainbow", 2))
            paid.attachEnergyByEffect(A, A, EffectSourceZone.HAND, card.instanceId(), A, active);
        CardInstance doubleColorless = hand(paid, "double", 1).getFirst();
        paid.attachEnergyByEffect(A, A, EffectSourceZone.HAND, doubleColorless.instanceId(), A, active);
        var payablePreview=dev.hua.ptcglive.engine.MatchSession.multiplayer(paid,A,B).view(A,A).guidance().attacks().getFirst();
        if(!payablePreview.payable()||payablePreview.damage()!=null)
            throw new AssertionError("特殊能量预览未采用真实支付条件，或未经核对就显示精确伤害");
        paid.attack(A, "tri");
        if (paid.lastAttack().finalDamage() != 30) throw new AssertionError("服务器没有使用特殊能量供给档案支付攻击费用");

        ServerMatch insufficient = fixture(); UUID secondActive = insufficient.activePokemonView(A).pokemonId();
        insufficient.attachEnergyByEffect(A, A, EffectSourceZone.HAND, hand(insufficient, "rainbow", 1).getFirst().instanceId(), A, secondActive);
        insufficient.attachEnergyByEffect(A, A, EffectSourceZone.HAND, hand(insufficient, "double", 1).getFirst().instanceId(), A, secondActive);
        if(dev.hua.ptcglive.engine.MatchSession.multiplayer(insufficient,A,B).view(A,A).guidance().attacks().getFirst().payable())
            throw new AssertionError("预览重复使用同一个彩色供能单位");
        rejects(() -> insufficient.attack(A, "tri"));
        System.out.println("SpecialEnergyPaymentTest passed: selectable and multi-unit Special Energy payment is server-allocated and rejects reuse");
    }

    private static ServerMatch fixture() {
        CardDefinition attacker = definition("attacker", CardCategory.POKEMON, true), defender = definition("defender", CardCategory.POKEMON, true);
        CardDefinition rainbow = definition("rainbow", CardCategory.ENERGY, false), doubleColorless = definition("double", CardCategory.ENERGY, false);
        AttackDefinition tri = new AttackDefinition("tri", "测试三色费用", COST, 30, EnergyType.FIRE, EffectSupport.SUPPORTED, null);
        CombatCatalog combat = new CombatCatalog(Map.of(
            "attacker", new PokemonProfile(120, 0, null, null, 0, null, 0, 1, List.of(tri)),
            "defender", new PokemonProfile(120, 0, null, null, 0, null, 0, 1, List.of())), Map.of(), Map.of(),
            Map.of("rainbow", RAINBOW, "double", DOUBLE_COLORLESS));
        CardDatabase data = new CardDatabase(List.of(attacker, defender, rainbow, doubleColorless));
        for (long seed = 1; seed < 10_000; seed++) {
            ServerMatch match = new ServerMatch(A, B, deck(A, true), deck(B, false), MatchRulesRegressionTest.RULE, data, combat, new ServerRandom(seed));
            OpeningTestSupport.complete(match, A, B, 0);
            for (int turns = 0; turns < 4; turns++) {
                if (match.currentPlayer().equals(A) && match.mayAttack(A) && hand(match, "rainbow", 2).size() == 2 && hand(match, "double", 1).size() == 1)
                    return match;
                match.endTurn(match.currentPlayer());
            }
        }
        throw new AssertionError("无法建立含特殊能量且甲方可攻击的确定性测试对局");
    }
    private static List<CardInstance> hand(ServerMatch match, String id, int count) {
        return match.privateView(A).hand().stream().filter(card -> card.definitionId().equals(id)).limit(count).toList();
    }
    private static CardDefinition definition(String id, CardCategory category, boolean basic) {
        return new CardDefinition(id, id, category, TrainerCategory.NONE, "H", Set.of("international"), basic,
            false, 60, Map.of(), EffectSupport.SUPPORTED, null);
    }
    private static List<CardInstance> deck(UUID owner, boolean attacking) {
        var cards = new ArrayList<CardInstance>();
        for (int i = 0; i < (attacking ? 36 : 60); i++) cards.add(new CardInstance(UUID.randomUUID(), owner, attacking ? "attacker" : "defender"));
        if (attacking) {
            for (int i = 0; i < 12; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, "rainbow"));
            for (int i = 0; i < 12; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, "double"));
        }
        return cards;
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("服务器错误接受了无法满足的特殊能量攻击费用");
    }
}
