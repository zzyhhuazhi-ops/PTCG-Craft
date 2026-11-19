import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.field.AttackDefinition;
import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.engine.field.PokemonProfile;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Attack data may request a server-only discard of all Energy still attached after the attack resolves. */
public final class AttackDiscardAllEnergyTest {
    private static final UUID A = new UUID(0, 5701), B = new UUID(0, 5702);
    public static void main(String[] args) {
        ServerMatch match = fixture();
        while (!match.currentPlayer().equals(A) || !match.mayAttack(A)) match.endTurn(match.currentPlayer());
        UUID active = match.activePokemonView(A).pokemonId();
        CardInstance energy = match.privateView(A).hand().stream().filter(card -> card.definitionId().equals("energy")).findFirst()
            .orElseThrow(() -> new AssertionError("稳定夹具未保留测试能量"));
        match.attachEnergy(A, energy.instanceId(), active);
        match.attack(A, "discharge");
        if (match.activePokemonView(A).attachedEnergyIds().contains(energy.instanceId())
            || !match.privateView(A).discard().contains(energy)
            || match.privateView(B).discard().contains(energy))
            throw new AssertionError("攻击后弃置全部附着能量没有按实际拥有者原子结算");
        if (match.replayTranscript().stream().noneMatch(event -> event.operation().equals("使用攻击")))
            throw new AssertionError("攻击后弃能没有保留为同一服务器攻击回放事实");
        System.out.println("AttackDiscardAllEnergyTest passed: attack discards all attached Energy server-side and owner-correctly");
    }
    private static ServerMatch fixture() {
        var definitions = new CardDatabase(List.of(definition("attacker", CardCategory.POKEMON, true, false),
            definition("target", CardCategory.POKEMON, true, false), definition("energy", CardCategory.ENERGY, false, true)));
        var discharge = new AttackDefinition("discharge", "测试弃能攻击", Map.of(), 20, EnergyType.COLORLESS,
            EffectSupport.SUPPORTED, null, List.of(), null, null, List.of(), AttackDefinition.DamageTarget.OPPONENT_ACTIVE, 0, 1, true);
        var combat = new CombatCatalog(Map.of("attacker", new PokemonProfile(120, 0, null, null, 0, null, 0, 1, List.of(discharge)),
            "target", new PokemonProfile(120, 0, null, null, 0, null, 0, 1, List.of())), Map.of("energy", EnergyType.GRASS), Map.of());
        for (int seed = 0; seed < 500; seed++) {
            ServerMatch match = new ServerMatch(A, B, deck(A, "attacker", "energy"), deck(B, "target", "target"),
                MatchRulesRegressionTest.RULE, definitions, combat, new ServerRandom(seed));
            OpeningTestSupport.complete(match, A, B, 0);
            if (match.privateView(A).hand().stream().anyMatch(card -> card.definitionId().equals("energy"))) return match;
        }
        throw new AssertionError("未找到包含测试能量的开局夹具");
    }
    private static CardDefinition definition(String id, CardCategory category, boolean basic, boolean basicEnergy) {
        return new CardDefinition(id, id, category, TrainerCategory.NONE, "H", Set.of("international"), basic, basicEnergy, 60,
            Map.of(), EffectSupport.SUPPORTED, null);
    }
    private static List<CardInstance> deck(UUID owner, String first, String second) {
        var cards = new ArrayList<CardInstance>();
        for (int i = 0; i < 60; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, i < 30 ? first : second));
        return cards;
    }
}
