import dev.hua.ptcglive.engine.ServerMatch;
import java.util.UUID;
/** Keep physical cards, logical Pokémon and incarnations separate in fixture assertions. */
public final class PokemonIdentityTestSupport {
    public static UUID physical(ServerMatch match,UUID owner,UUID logical) {return EventTargetBindingTest.field(match).baseCard(owner,logical).instanceId();}
    public static UUID logical(ServerMatch match,UUID owner,UUID physical) {return EventTargetBindingTest.field(match).printedPokemonView(owner,physical).pokemonId();}
}
