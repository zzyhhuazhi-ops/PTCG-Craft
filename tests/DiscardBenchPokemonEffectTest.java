import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.field.LeavePlayDestination;
import java.util.UUID;

/** Basic Pokémon revival reuses the server's Bench cap and combat-profile checks. */
public final class DiscardBenchPokemonEffectTest {
    public static void main(String[] args) {
        ServerMatch match = PersistentTrainerTest.game();
        UUID bench = match.benchPokemonView(PersistentTrainerTest.B).getFirst().pokemonId();
        UUID physical=PokemonIdentityTestSupport.physical(match,PersistentTrainerTest.B,bench);
        match.movePokemonByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, bench, LeavePlayDestination.DISCARD);
        match.putBasicPokemonFromDiscardToBenchByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, physical);
        if (match.benchPokemonView(PersistentTrainerTest.B).stream().noneMatch(pokemon -> pokemon.pokemonId().equals(PokemonIdentityTestSupport.logical(match,PersistentTrainerTest.B,physical)))
            || match.privateView(PersistentTrainerTest.B).discard().stream().anyMatch(card -> card.instanceId().equals(physical)))
            throw new AssertionError("基础宝可梦没有从弃牌区正确放入备战区");
        try { match.putBasicPokemonFromDiscardToBenchByEffect(PersistentTrainerTest.A, PersistentTrainerTest.B, physical); }
        catch (IllegalArgumentException expected) {
            if (!match.replayTranscript().getLast().operation().equals("已审核效果从弃牌区放置基础宝可梦"))
                throw new AssertionError("基础宝可梦复活没有进入服务器回放");
            PersistentTrainerTest.conserve(match);
            System.out.println("DiscardBenchPokemonEffectTest passed: server-only Basic revival validates discard identity and Bench placement");
            return;
        }
        throw new AssertionError("已离开弃牌区的基础宝可梦被再次复活");
    }
}
