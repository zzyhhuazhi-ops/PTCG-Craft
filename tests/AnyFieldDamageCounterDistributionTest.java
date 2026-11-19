import dev.hua.ptcglive.engine.DamageAllocationPrompt;
import dev.hua.ptcglive.engine.MatchSession;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.effect.EffectTarget;
import java.util.List;
import java.util.UUID;

/** Both public fields may be used only when data explicitly declares SELECT_ANY_POKEMON + ANY_FIELD. */
public final class AnyFieldDamageCounterDistributionTest {
    private static final String ANY_SPLIT = "{\"type\":\"damage_counter\",\"amount\":4,\"distribution\":\"EXACT\",\"target\":\"SELECT_ANY_POKEMON\",\"target_scope\":\"ANY_FIELD\",\"min_targets\":2,\"max_targets\":2}";

    public static void main(String[] args) {
        bothFieldsAreServerIssuedAndPrivate();
        incompatibleTargetAndScopeFailBeforePlay();
        directPublicSelectionAlsoRechecksBothFields();
        System.out.println("AnyFieldDamageCounterDistributionTest passed: explicit ANY_FIELD allocation stays server-authoritative and private");
    }

    private static void bothFieldsAreServerIssuedAndPrivate() {
        ServerMatch match = ComposedDamageDistributionTest.game(ANY_SPLIT);
        MatchSession session = MatchSession.multiplayer(match, ComposedDamageDistributionTest.A, ComposedDamageDistributionTest.B);
        match.playTrainer(ComposedDamageDistributionTest.A, ComposedDamageDistributionTest.trainer(match).instanceId(), null);
        DamageAllocationPrompt prompt = match.damageAllocationPrompt(ComposedDamageDistributionTest.A);
        check(prompt.candidates().size() == 4, "任一方范围没有同时给出双方公开场上宝可梦");
        check(session.view(ComposedDamageDistributionTest.B, ComposedDamageDistributionTest.B).game().damageAllocation() == null,
            "分配草案错误泄露给对手");
        UUID ownActive = match.activePokemonView(ComposedDamageDistributionTest.A).pokemonId();
        UUID opponentBench = match.benchPokemonView(ComposedDamageDistributionTest.B).getFirst().pokemonId();
        match.submitDamageAllocation(ComposedDamageDistributionTest.A, prompt.resolutionId(), prompt.step(), List.of(
            new ServerMatch.DamageCounterPlacement(ComposedDamageDistributionTest.A, ownActive, 1),
            new ServerMatch.DamageCounterPlacement(ComposedDamageDistributionTest.B, opponentBench, 3)));
        check(match.activePokemonView(ComposedDamageDistributionTest.A).damage() == 10, "己方目标未按分配结算");
        check(match.benchPokemonView(ComposedDamageDistributionTest.B).getFirst().damage() == 30, "对方目标未按分配结算");
    }

    private static void incompatibleTargetAndScopeFailBeforePlay() {
        String mismatch = ANY_SPLIT.replace("SELECT_ANY_POKEMON", "SELECT_OPPONENT_POKEMON");
        var profile = ComposedTrainerIntegrationTest.item(mismatch);
        check(dev.hua.ptcglive.engine.effect.TrainerProgramRules.unsupportedReason(profile.effectProgram()).isPresent(),
            "ANY_FIELD 与单方选择目标的不一致数据被错误接受");
    }

    private static void directPublicSelectionAlsoRechecksBothFields() {
        ServerMatch match = PersistentTrainerTest.game();
        var transaction = match.openEffectPokemonSelection(PersistentTrainerTest.A, null, EffectTarget.SELECT_ANY_POKEMON, 2, 2, 44);
        var prompt = transaction.promptFor(PersistentTrainerTest.A);
        check(prompt.candidateIds().size() == 4, "公开选择事务没有包含双方场上目标");
        var selected = match.submitEffectPokemonSelection(PersistentTrainerTest.A, transaction, transaction.transactionId(), 44,
            List.of(prompt.candidateIds().getFirst(), prompt.candidateIds().getLast()));
        check(selected.size() == 2 && !selected.getFirst().owner().equals(selected.getLast().owner()), "任一方公开选择没有保留双方所属者");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
