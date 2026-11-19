import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.field.AttackDefinition;
import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.engine.field.PokemonProfile;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** A “cannot use this attack during your next turn” effect locks one ID through precisely one future owner turn. */
public final class AttackCooldownTest {
    private static final UUID A = new UUID(0, 6201), B = new UUID(0, 6202);
    public static void main(String[] args) {
        ServerMatch match = fixture();
        reachTurn(match, A);
        match.attack(A, "repeat");
        match.endTurn(B);
        if (match.activeAttacks(A).stream().anyMatch(attack -> attack.id().equals("repeat"))
            || match.activeAttacks(A).stream().noneMatch(attack -> attack.id().equals("alternate"))
            || !match.activePokemonView(A).continuousEffectZh().contains("重复攻击"))
            throw new AssertionError("攻击冷却没有只从服务器动作列表移除指定攻击");
        rejects(() -> match.attack(A, "repeat"));
        match.attack(A, "alternate");
        match.endTurn(B);
        if (match.activeAttacks(A).stream().noneMatch(attack -> attack.id().equals("repeat"))
            || !match.activePokemonView(A).continuousEffectZh().isBlank())
            throw new AssertionError("攻击冷却没有在拥有者下个回合结束后准确过期");
        match.attack(A, "repeat");
        System.out.println("AttackCooldownTest passed: specific attack lock, legal alternative and owner-turn expiry are server-resolved");
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalStateException expected) { return; }
        throw new AssertionError("攻击冷却没有由服务器拒绝指定攻击");
    }
    private static void reachTurn(ServerMatch match, UUID player) {
        while (!match.currentPlayer().equals(player) || !match.mayAttack(player)) match.endTurn(match.currentPlayer());
    }
    private static ServerMatch fixture() {
        var definitions = new CardDatabase(List.of(definition("attacker"), definition("target")));
        var repeat = new AttackDefinition("repeat", "重复攻击", Map.of(), 20, EnergyType.COLORLESS,
            EffectSupport.SUPPORTED, null, List.of(), null, null, List.of(), AttackDefinition.DamageTarget.OPPONENT_ACTIVE,
            0, 1, false, 0, 0, false, 0, false, true);
        var alternate = new AttackDefinition("alternate", "替代攻击", Map.of(), 10, EnergyType.COLORLESS,
            EffectSupport.SUPPORTED, null);
        var combat = new CombatCatalog(Map.of(
            "attacker", new PokemonProfile(180, 0, null, null, 0, null, 0, 1, List.of(repeat, alternate)),
            "target", new PokemonProfile(180, 0, null, null, 0, null, 0, 1, List.of())), Map.of());
        var match = new ServerMatch(A, B, deck(A, "attacker"), deck(B, "target"), MatchRulesRegressionTest.RULE,
            definitions, combat, new ServerRandom(6203));
        OpeningTestSupport.complete(match, A, B, 0);
        return match;
    }
    private static CardDefinition definition(String id) {
        return new CardDefinition(id, id, CardCategory.POKEMON, TrainerCategory.NONE, "H", Set.of("international"), true,
            false, 60, Map.of(), EffectSupport.SUPPORTED, null);
    }
    private static List<CardInstance> deck(UUID owner, String definitionId) {
        var cards = new ArrayList<CardInstance>();
        for (int i = 0; i < 60; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, definitionId));
        return cards;
    }
}
