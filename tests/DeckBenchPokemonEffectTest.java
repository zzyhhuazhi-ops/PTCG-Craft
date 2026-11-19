import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.field.PokemonProfile;
import dev.hua.ptcglive.rules.CardCategory;
import dev.hua.ptcglive.rules.CardDatabase;
import dev.hua.ptcglive.rules.CardDefinition;
import dev.hua.ptcglive.rules.CardInstance;
import dev.hua.ptcglive.rules.CombatCatalog;
import dev.hua.ptcglive.rules.EffectSupport;
import dev.hua.ptcglive.rules.TrainerCategory;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Own-deck Basic placement validates hidden identity server-side and shuffles afterwards. */
public final class DeckBenchPokemonEffectTest {
    public static void main(String[] args) {
        ServerMatch match = fixture();
        var chosen = match.privateView(PersistentTrainerTest.A).deck().stream()
            .filter(card -> match.cardDefinition(card.definitionId()).basicPokemon()).findFirst().orElseThrow();
        int deckBefore = match.privateView(PersistentTrainerTest.A).deck().size();
        int benchBefore = match.benchPokemonView(PersistentTrainerTest.A).size();
        int randomBefore = match.randomTranscript().size();
        match.putBasicPokemonFromOwnDeckToBenchByEffect(PersistentTrainerTest.A, chosen.instanceId());
        if (match.privateView(PersistentTrainerTest.A).deck().size() != deckBefore - 1
            || match.benchPokemonView(PersistentTrainerTest.A).size() != benchBefore + 1
            || match.benchPokemonView(PersistentTrainerTest.A).stream().noneMatch(pokemon -> pokemon.pokemonId().equals(PokemonIdentityTestSupport.logical(match,PersistentTrainerTest.A,chosen.instanceId())))
            || match.randomTranscript().size() <= randomBefore)
            throw new AssertionError("从牌库放置基础宝可梦没有完成服务器移动或洗牌");
        try { match.putBasicPokemonFromOwnDeckToBenchByEffect(PersistentTrainerTest.A, chosen.instanceId()); }
        catch (IllegalArgumentException expected) {
            if (!match.replayTranscript().getLast().operation().equals("已审核效果从牌库放置基础宝可梦"))
                throw new AssertionError("牌库基础宝可梦放置没有写入服务器回放");
            PersistentTrainerTest.conserve(match);
            System.out.println("DeckBenchPokemonEffectTest passed: server own-deck Basic placement shuffles and rejects stale identity");
            return;
        }
        throw new AssertionError("已离开牌库的基础宝可梦被再次放置");
    }

    private static ServerMatch fixture() {
        CardDefinition basic = new CardDefinition("basic", "测试基础宝可梦", CardCategory.POKEMON, TrainerCategory.NONE,
            "H", Set.of("international"), true, false, 4, Map.of(), EffectSupport.SUPPORTED, null);
        var combat = new CombatCatalog(Map.of("basic", new PokemonProfile(100, 0, null, null, 0, null, 0, 1, java.util.List.of())), Map.of());
        ServerMatch match = new ServerMatch(PersistentTrainerTest.A, PersistentTrainerTest.B, deck(PersistentTrainerTest.A), deck(PersistentTrainerTest.B),
            PersistentTrainerTest.RULE, new CardDatabase(java.util.List.of(basic)), combat, new ServerRandom(8801));
        OpeningTestSupport.complete(match, PersistentTrainerTest.A, PersistentTrainerTest.B, 1);
        return match;
    }
    private static java.util.List<CardInstance> deck(UUID owner) {
        var cards = new ArrayList<CardInstance>();
        for (int i = 0; i < 60; i++) cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(), i + 1000), owner, "basic"));
        return cards;
    }
}
