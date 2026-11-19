import dev.hua.ptcglive.engine.ServerMatch;
import java.util.List;
import java.util.UUID;

/** A forced switch can target the opponent but never becomes a retreat or client action. */
public final class ForcedSwitchEffectTest {
    public static void main(String[] args) {
        ServerMatch match = PersistentTrainerTest.game();
        UUID formerActive = match.activePokemonView(PersistentTrainerTest.B).pokemonId();
        UUID replacement = match.benchPokemonView(PersistentTrainerTest.B).getFirst().pokemonId();
        int discard = match.privateView(PersistentTrainerTest.B).discard().size();
        match.switchActiveByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, replacement);
        if (!match.activePokemonView(PersistentTrainerTest.B).pokemonId().equals(replacement)
            || match.benchPokemonView(PersistentTrainerTest.B).stream().noneMatch(pokemon -> pokemon.pokemonId().equals(formerActive))
            || match.privateView(PersistentTrainerTest.B).discard().size() != discard)
            throw new AssertionError("强制交换错误支付了撤退费用，或未正确交换对方场上位置");
        try { match.switchActiveByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, formerActive); }
        catch (RuntimeException impossible) { throw new AssertionError("原战斗宝可梦已在备战区，应可被再次指定为替换", impossible); }
        if (!match.replayTranscript().getLast().operation().equals("已审核效果交换战斗宝可梦"))
            throw new AssertionError("强制交换没有进入服务器回放");
        System.out.println("ForcedSwitchEffectTest passed: server-only forced switch changes opponent field without retreat cost");
    }
}
