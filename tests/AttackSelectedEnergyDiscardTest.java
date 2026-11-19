import dev.hua.ptcglive.engine.EffectSourceZone;
import dev.hua.ptcglive.engine.MatchSession;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.field.AttackDefinition;
import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.engine.field.PokemonProfile;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Selected post-attack Energy discard accepts only exact unique public attachments and remains atomic on rejection. */
public final class AttackSelectedEnergyDiscardTest {
    private static final UUID A = new UUID(0, 5901), B = new UUID(0, 5902);
    public static void main(String[] args) {
        ServerMatch match = fixture();
        while (!match.currentPlayer().equals(A) || !match.mayAttack(A)) match.endTurn(match.currentPlayer());
        UUID active = match.activePokemonView(A).pokemonId();
        List<CardInstance> energies = match.privateView(A).hand().stream().filter(card -> card.definitionId().equals("energy")).limit(2).toList();
        match.attachEnergy(A, energies.getFirst().instanceId(), active);
        match.attachEnergyByEffect(A, A, EffectSourceZone.HAND, energies.get(1).instanceId(), A, active);
        var before = match.clientView(A); int audit = match.randomTranscript().size();
        rejects(() -> match.attack(A, "burn"));
        rejects(() -> match.attackWithEnergyDiscards(A, "burn", List.of(), List.of(energies.getFirst().instanceId(), energies.getFirst().instanceId())));
        if (!before.equals(match.clientView(A)) || audit != match.randomTranscript().size())
            throw new AssertionError("无效弃能选择在服务器造成了状态或随机数变化");
        MatchSession session = MatchSession.multiplayer(match, A, B);
        var snapshot = session.view(A, A);
        var choices = snapshot.options().stream().filter(option -> option.labelZh().startsWith("攻击：测试选择弃能攻击")).toList();
        if (choices.size() != 2 || choices.stream().anyMatch(option -> !option.labelZh().contains("弃置 1 张附着能量")))
            throw new AssertionError("客户端没有收到服务器生成的两种公开能量选择组合");
        session.submit(A, A, snapshot.revision(), choices.getFirst().index());
        long discarded = match.privateView(A).discard().stream().filter(card -> card.definitionId().equals("energy")).count();
        if (discarded != 1 || match.activePokemonView(A).attachedEnergyIds().size() != 1)
            throw new AssertionError("服务器没有只弃置客户端所选组合中的一张公开附着能量");
        System.out.println("AttackSelectedEnergyDiscardTest passed: exact public Energy selection is validated and server-resolved");
    }
    private static ServerMatch fixture() {
        var definitions = new CardDatabase(List.of(definition("attacker", CardCategory.POKEMON, true, false),
            definition("target", CardCategory.POKEMON, true, false), definition("energy", CardCategory.ENERGY, false, true)));
        var burn = new AttackDefinition("burn", "测试选择弃能攻击", Map.of(), 20, EnergyType.COLORLESS,
            EffectSupport.SUPPORTED, null, List.of(), null, null, List.of(), AttackDefinition.DamageTarget.OPPONENT_ACTIVE, 0, 1, false, 0, 1);
        var combat = new CombatCatalog(Map.of("attacker", new PokemonProfile(120, 0, null, null, 0, null, 0, 1, List.of(burn)),
            "target", new PokemonProfile(120, 0, null, null, 0, null, 0, 1, List.of())), Map.of("energy", EnergyType.GRASS), Map.of());
        for (int seed = 0; seed < 500; seed++) {
            ServerMatch match = new ServerMatch(A, B, deck(A, "attacker", "energy", 15), deck(B, "target", "target", 60),
                MatchRulesRegressionTest.RULE, definitions, combat, new ServerRandom(seed));
            OpeningTestSupport.complete(match, A, B, 0);
            if (match.privateView(A).hand().stream().filter(card -> card.definitionId().equals("energy")).count() >= 2) return match;
        }
        throw new AssertionError("未找到含两张测试能量的开局夹具");
    }
    private static CardDefinition definition(String id, CardCategory category, boolean basic, boolean basicEnergy) {
        return new CardDefinition(id, id, category, TrainerCategory.NONE, "H", Set.of("international"), basic, basicEnergy, 60,
            Map.of(), EffectSupport.SUPPORTED, null);
    }
    private static List<CardInstance> deck(UUID owner, String first, String second, int firstCount) {
        var cards = new ArrayList<CardInstance>();
        for (int i = 0; i < 60; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, i < firstCount ? first : second));
        return cards;
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("无效的攻击后弃能选择没有被服务器拒绝");
    }
}
