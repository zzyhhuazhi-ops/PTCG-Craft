import dev.hua.ptcglive.engine.EffectSourceZone;
import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.field.EnergyType;
import dev.hua.ptcglive.engine.field.PokemonProfile;
import dev.hua.ptcglive.rules.CardCategory;
import dev.hua.ptcglive.rules.CardDatabase;
import dev.hua.ptcglive.rules.CardDefinition;
import dev.hua.ptcglive.rules.CardInstance;
import dev.hua.ptcglive.rules.CombatCatalog;
import dev.hua.ptcglive.rules.EffectSupport;
import dev.hua.ptcglive.rules.TrainerCategory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Deck-source Energy effects must attach a real Energy then shuffle only that deck. */
public final class DeckEnergyAttachmentEffectTest {
    public static void main(String[] args) {
        ServerMatch match = fixture();
        var energy = match.privateView(PersistentTrainerTest.A).deck().stream().filter(card -> card.definitionId().equals("energy")).findFirst().orElseThrow();
        UUID target = match.activePokemonView(PersistentTrainerTest.A).pokemonId();
        int deckBefore = match.privateView(PersistentTrainerTest.A).deck().size();
        int randomBefore = match.randomTranscript().size();
        match.attachEnergyByEffect(PersistentTrainerTest.A, PersistentTrainerTest.A, EffectSourceZone.DECK, energy.instanceId(), PersistentTrainerTest.A, target);
        if (match.privateView(PersistentTrainerTest.A).deck().size() != deckBefore - 1
            || !match.activePokemonView(PersistentTrainerTest.A).attachedEnergyIds().contains(energy.instanceId())
            || match.randomTranscript().size() <= randomBefore)
            throw new AssertionError("牌库能量没有被服务器附着或牌库未洗牌审计");
        try { match.attachEnergyByEffect(PersistentTrainerTest.A, PersistentTrainerTest.A, EffectSourceZone.DECK, energy.instanceId(), PersistentTrainerTest.A, target); }
        catch (IllegalArgumentException expected) {
            System.out.println("DeckEnergyAttachmentEffectTest passed: deck Energy attachment validates identity and shuffles server-side");
            return;
        }
        throw new AssertionError("已离开牌库的能量被再次从牌库附着");
    }
    private static ServerMatch fixture() {
        CardDefinition basic = new CardDefinition("basic", "测试基础宝可梦", CardCategory.POKEMON, TrainerCategory.NONE, "H", Set.of("international"), true, false, 4, Map.of(), EffectSupport.SUPPORTED, null);
        CardDefinition energy = new CardDefinition("energy", "测试基本能量", CardCategory.ENERGY, TrainerCategory.NONE, "H", Set.of("international"), false, true, 99, Map.of(), EffectSupport.SUPPORTED, null);
        var combat = new CombatCatalog(Map.of("basic", new PokemonProfile(100, 0, null, null, 0, null, 0, 1, List.of())), Map.of("energy", EnergyType.GRASS));
        ServerMatch match = new ServerMatch(PersistentTrainerTest.A, PersistentTrainerTest.B, deck(PersistentTrainerTest.A), deck(PersistentTrainerTest.B), PersistentTrainerTest.RULE,
            new CardDatabase(List.of(basic, energy)), combat, new ServerRandom(8901));
        OpeningTestSupport.complete(match, PersistentTrainerTest.A, PersistentTrainerTest.B, 0);
        return match;
    }
    private static List<CardInstance> deck(UUID owner) {
        var cards = new ArrayList<CardInstance>();
        for (int i = 0; i < 60; i++) cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(), i + 2000), owner, i < 4 ? "basic" : "energy"));
        return cards;
    }
}
