import dev.hua.ptcglive.engine.MatchSession;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.field.AbilityDefinition;
import dev.hua.ptcglive.engine.field.AbilityEffect;
import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.engine.field.PokemonProfile;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** A selected attached Energy is moved only after server validation of both own-field targets and its source attachment. */
public final class AbilityEnergyMoveTargetTest {
    private static final UUID A = new UUID(0, 6311), B = new UUID(0, 6312);

    public static void main(String[] args) {
        ServerMatch match = fixture();
        UUID source = match.activePokemonView(A).pokemonId();
        UUID target = match.benchPokemonView(A).getFirst().pokemonId();
        UUID opponent = match.activePokemonView(B).pokemonId();
        CardInstance energy = match.privateView(A).hand().stream().filter(card -> card.definitionId().equals("energy"))
            .findFirst().orElseThrow(() -> new AssertionError("测试夹具没有把能量留在手牌"));
        match.attachEnergy(A, energy.instanceId(), source);

        rejects(() -> match.useAbility(A, source, "move-energy", null, energy.instanceId()));
        rejects(() -> match.useAbility(A, source, "move-energy", source, energy.instanceId()));
        rejects(() -> match.useAbility(A, source, "move-energy", opponent, energy.instanceId()));
        rejects(() -> match.useAbility(A, source, "move-energy", target, UUID.randomUUID()));
        if (!match.canUseAbility(A, source, "move-energy", target, energy.instanceId()))
            throw new AssertionError("服务器没有允许来源附着能量移动到另一只己方宝可梦");

        MatchSession session = MatchSession.multiplayer(match, A, B);
        MatchSession.Snapshot snapshot = session.view(A, A);
        int option = snapshot.options().stream().filter(candidate -> candidate.labelZh().contains("移动 1 张附着能量")
            && candidate.labelZh().contains("备战区")).mapToInt(MatchSession.Option::index).findFirst()
            .orElseThrow(() -> new AssertionError("会话没有仅为服务器认可的能量和己方目标生成特性操作"));
        session.submit(A, A, snapshot.revision(), option);

        if (!match.activePokemonView(A).attachedEnergyIds().isEmpty()
            || !match.benchPokemonView(A).getFirst().attachedEnergyIds().contains(energy.instanceId()))
            throw new AssertionError("特性没有从来源移动指定附着能量到己方目标");
        if (match.canUseAbility(A, source, "move-energy", target, energy.instanceId()))
            throw new AssertionError("移动能量特性没有在服务器上计入一回合一次限制");
        System.out.println("AbilityEnergyMoveTargetTest passed: selected attached Energy and public own-field target are server-validated before use");
    }

    private static ServerMatch fixture() {
        var mover = definition("mover", CardCategory.POKEMON, true);
        var target = definition("target", CardCategory.POKEMON, true);
        var energy = definition("energy", CardCategory.ENERGY, false);
        var move = new AbilityDefinition("move-energy", "能量转移特性",
            new AbilityEffect(AbilityEffect.Opcode.MOVE_ONE_ATTACHED_ENERGY_TO_OWN_POKEMON, 1), EffectSupport.SUPPORTED, null);
        var combat = new CombatCatalog(Map.of(
            "mover", new PokemonProfile(120, 0, null, null, 0, null, 0, 1, List.of(), List.of(move)),
            "target", new PokemonProfile(120, 0, null, null, 0, null, 0, 1, List.of())), Map.of("energy", EnergyType.GRASS));
        var data = new CardDatabase(List.of(mover, target, energy));
        for (long seed = 1; seed < 10_000; seed++) {
            ServerMatch match = new ServerMatch(A, B, deck(A, true), deck(B, false), MatchRulesRegressionTest.RULE,
                data, combat, new ServerRandom(seed));
            try { OpeningTestSupport.complete(match, A, B, 1); }
            catch (AssertionError unavailableOpening) { continue; }
            while (!match.currentPlayer().equals(A)) match.endTurn(match.currentPlayer());
            if (match.privateView(A).hand().stream().anyMatch(card -> card.definitionId().equals("energy"))) return match;
        }
        throw new AssertionError("无法建立含有两只宝可梦和手牌能量的确定性测试对局");
    }

    private static CardDefinition definition(String id, CardCategory category, boolean basic) {
        return new CardDefinition(id, id, category, TrainerCategory.NONE, "H", Set.of("international"), basic,
            false, 60, Map.of(), EffectSupport.SUPPORTED, null);
    }

    private static List<CardInstance> deck(UUID owner, boolean movingSide) {
        var cards = new ArrayList<CardInstance>();
        for (int i = 0; i < (movingSide ? 45 : 60); i++) cards.add(new CardInstance(UUID.randomUUID(), owner, movingSide ? "mover" : "target"));
        if (movingSide) for (int i = 0; i < 15; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, "energy"));
        return cards;
    }

    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("服务端错误接受了无效的特性能量移动参数");
    }
}
