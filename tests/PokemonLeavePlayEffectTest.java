import dev.hua.ptcglive.engine.MatchPhase;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.field.LeavePlayDestination;
import java.util.List;
import java.util.UUID;

/** Non-KO leave-play effects must move a whole stack and use replacement/loss, never Prizes. */
public final class PokemonLeavePlayEffectTest {
    public static void main(String[] args) {
        returnCompleteBenchStackToHand();
        returnActiveRequestsReplacementThenNoPokemonLoss();
        returnToDeckShufflesOnServer();
        System.out.println("PokemonLeavePlayEffectTest passed: server-only non-KO leave-play conserves stacks and resolves replacement/loss");
    }

    private static void returnCompleteBenchStackToHand() {
        ServerMatch match = PersistentTrainerTest.game();
        UUID bench = match.benchPokemonView(PersistentTrainerTest.B).getFirst().pokemonId();
        match.endTurn(PersistentTrainerTest.A);
        match.evolve(PersistentTrainerTest.B, bench, PersistentTrainerTest.hand(match, PersistentTrainerTest.B, "evolved").instanceId());
        var energy = PersistentTrainerTest.hand(match, PersistentTrainerTest.B, "energy");
        var tool = PersistentTrainerTest.hand(match, PersistentTrainerTest.B, "balloon");
        match.attachEnergy(PersistentTrainerTest.B, energy.instanceId(), bench);
        match.playTrainer(PersistentTrainerTest.B, tool.instanceId(), bench);
        match.endTurn(PersistentTrainerTest.B);

        int handBefore = match.privateView(PersistentTrainerTest.B).hand().size();
        int prizesBefore = match.privateView(PersistentTrainerTest.A).prizes().size();
        match.movePokemonByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, bench, LeavePlayDestination.HAND);
        var hand = match.privateView(PersistentTrainerTest.B).hand();
        check(hand.size() == handBefore + 4, "回手时没有带回基础、进化、能量和工具的完整堆叠");
        for (var card : List.of(energy, tool)) check(hand.contains(card), "附属卡没有回到受影响玩家手牌");
        check(hand.stream().filter(card -> card.definitionId().equals("basic")).count() >= 1
            && hand.stream().filter(card -> card.definitionId().equals("evolved")).count() >= 1, "宝可梦堆叠没有完整回手");
        check(match.benchPokemonView(PersistentTrainerTest.B).isEmpty(), "回手后备战宝可梦仍留在场上");
        check(match.privateView(PersistentTrainerTest.A).prizes().size() == prizesBefore, "非击倒离场错误发放奖赏");
        check(match.replayTranscript().getLast().operation().equals("已审核效果使宝可梦离场"), "离场效果没有写入公开回放");
        PersistentTrainerTest.conserve(match);
    }

    private static void returnActiveRequestsReplacementThenNoPokemonLoss() {
        ServerMatch match = PersistentTrainerTest.game();
        UUID bench = match.benchPokemonView(PersistentTrainerTest.B).getFirst().pokemonId();
        UUID active = match.activePokemonView(PersistentTrainerTest.B).pokemonId();
        int prizesBefore = match.privateView(PersistentTrainerTest.A).prizes().size();
        match.movePokemonByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, active, LeavePlayDestination.HAND);
        check(match.phase() == MatchPhase.CHOOSE_REPLACEMENT && match.pendingChoicePlayer().equals(PersistentTrainerTest.B),
            "战斗宝可梦非击倒离场后没有要求选择替补");
        check(match.privateView(PersistentTrainerTest.A).prizes().size() == prizesBefore, "非击倒离场错误给予奖赏");
        match.chooseReplacement(PersistentTrainerTest.B, bench);
        check(match.phase() == MatchPhase.TURN && match.currentPlayer().equals(PersistentTrainerTest.A), "替补后没有回到原回合玩家");

        UUID finalActive = match.activePokemonView(PersistentTrainerTest.B).pokemonId();
        match.movePokemonByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, finalActive, LeavePlayDestination.DISCARD);
        check(match.phase() == MatchPhase.FINISHED && match.result().winner().equals(PersistentTrainerTest.A),
            "无备战的战斗宝可梦离场没有以无宝可梦结束比赛");
        check(match.privateView(PersistentTrainerTest.A).prizes().size() == prizesBefore, "无宝可梦败北错误发放奖赏");
    }

    private static void returnToDeckShufflesOnServer() {
        ServerMatch match = PersistentTrainerTest.game();
        UUID bench = match.benchPokemonView(PersistentTrainerTest.B).getFirst().pokemonId();
        UUID physical=PokemonIdentityTestSupport.physical(match,PersistentTrainerTest.B,bench);
        int deckBefore = match.privateView(PersistentTrainerTest.B).deck().size();
        int randomBefore = match.randomTranscript().size();
        match.movePokemonByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, bench, LeavePlayDestination.DECK_SHUFFLED);
        check(match.privateView(PersistentTrainerTest.B).deck().size() == deckBefore + 1, "洗回牌库没有归还宝可梦卡");
        check(match.randomTranscript().size() > randomBefore, "洗回牌库没有由服务器洗牌并审计随机数");
        check(match.privateView(PersistentTrainerTest.B).deck().stream().anyMatch(card -> card.instanceId().equals(physical)), "回牌库后缺少原宝可梦实例");
        PersistentTrainerTest.conserve(match);
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
