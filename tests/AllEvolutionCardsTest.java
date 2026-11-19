import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.field.BattlefieldState;
import dev.hua.ptcglive.engine.field.PokemonProfile;
import dev.hua.ptcglive.rules.BundledCombatData;
import dev.hua.ptcglive.rules.BundledRules;
import dev.hua.ptcglive.rules.CardInstance;
import dev.hua.ptcglive.rules.EffectSupport;
import java.util.*;

/** Exhaustive identity coverage, plus timing/execution for each available evolution profile.
 * This does not claim coverage of every attack/Ability on those cards.
 */
public final class AllEvolutionCardsTest {
    private static final UUID A = new UUID(0, 481), B = new UUID(0, 482);
    // These upgrade mechanics are already gated as unsupported; never exercise them as ordinary evolution.
    private static final Set<String> LEVEL_X = Set.of("swsh7-5r_en_017", "swsh7-5r_en_018");
    private static final Set<String> ABSENT_BASE = Set.of("swsh4-5_en_051", "swsh4-5_en_192", "xy10_en_026", "xy10_en_118");

    public static void main(String[] args) {
        var catalog = BundledCombatData.load();
        var definitions = BundledRules.loadCardDefinitions();
        var profiles = catalog.fieldPokemonProfiles();
        Map<String, List<String>> names = new HashMap<>();
        profiles.forEach((id, p) -> { if (p.pokemonName() != null) names.computeIfAbsent(p.pokemonName(), k -> new ArrayList<>()).add(id); });
        int evolutions=0, pairs=0, timing=0, blocked=0;
        for (var entry : new TreeMap<>(catalog.pokemonProfiles()).entrySet()) {
            String id=entry.getKey(); var p=entry.getValue();
            if (!p.isEvolution() || id.startsWith("fixture-")) continue;
            evolutions++;
            check(p.pokemonName()!=null && p.evolvesFromName()!=null, "missing printed identity: "+id);
            if (LEVEL_X.contains(id)) {
                check(definitions.find(id).orElseThrow().effectSupport()==EffectSupport.UNSUPPORTED, "LV.X must remain unsupported: "+id);
                blocked++; continue;
            }
            var sources=names.getOrDefault(p.evolvesFromName(), List.of());
            if (sources.isEmpty()) {
                check(ABSENT_BASE.contains(id), "new unresolved evolution: "+id);
                blocked++; continue;
            }
            for (String source : sources) {
                check(p.evolvesFrom(source,profiles.get(source)), "cross-print match failed: "+source+" -> "+id);
                pairs++;
            }
            // Ordinary, owner-qualified, regional and ex/V names must never be conflated.
            for (String otherName : List.of("__unrelated__", "Larry's "+p.evolvesFromName(), "Alolan "+p.evolvesFromName(), p.evolvesFromName()+" ex")) {
                var wrong=new PokemonProfile(60,0,null,null,0,null,0,1,List.of(),List.of(),null,otherName,null);
                check(!p.evolvesFrom("other-print",wrong), "name restriction lost: "+id+" / "+otherName);
            }
            String source=sources.stream().sorted().findFirst().orElseThrow();
            var subset=new HashMap<String,PokemonProfile>();
            subset.put(source,profiles.get(source)); subset.put(id,p);
            var field=new BattlefieldState(A,B,subset,new ServerRandom(42));
            field.beginTurn(A); field.placeInitialActive(A,card(A,source));
            UUID target=field.activeView(A).pokemonId();
            check(!field.canEvolve(A,target,id), "first turn evolution: "+id);
            field.endTurn(A); field.beginTurn(B); field.placeInitialActive(B,card(B,source));
            check(!field.canEvolve(A,target,id), "opponent turn evolution: "+id);
            field.endTurn(B); field.beginTurn(A);
            field.placeOnBench(A,card(A,source));
            check(!field.canEvolve(A,field.benchView(A).getFirst().pokemonId(),id), "newly placed evolution: "+id);
            check(field.canEvolve(A,target,id), "legal evolution rejected: "+source+" -> "+id);
            field.evolve(A,target,card(A,id));
            check(field.activeView(A).topCardId().equals(id), "wrong evolved card: "+id);
            check(field.previousEvolutionCards(A,target).size()==1 && field.baseCard(A,target).definitionId().equals(source), "physical stack lost: "+id);
            // Try another actual evolution from this card, if available: same-turn stacking remains forbidden.
            String next=catalog.pokemonProfiles().entrySet().stream()
                .filter(e -> p.pokemonName().equals(e.getValue().evolvesFromName()) && !LEVEL_X.contains(e.getKey()))
                .map(Map.Entry::getKey).findFirst().orElse(null);
            if(next!=null) {
                // A separate matching profile proves the timing rejection, rather than a missing-profile rejection.
                var chain=new HashMap<>(subset);chain.put(next,profiles.get(next));
                var chained=new BattlefieldState(A,B,chain,new ServerRandom(42));
                chained.beginTurn(A);chained.placeInitialActive(A,card(A,source));
                chained.endTurn(A);chained.beginTurn(B);chained.placeInitialActive(B,card(B,source));
                chained.endTurn(B);chained.beginTurn(A);
                UUID t=chained.activeView(A).pokemonId();chained.evolve(A,t,card(A,id));
                check(!chained.canEvolve(A,t,next),"double evolution in one turn: "+id);
            }
            timing++;
        }
        check(evolutions>=4500 && timing>=4500 && pairs>=30000,"unexpectedly incomplete catalog coverage");
        System.out.println("AllEvolutionCardsTest passed: evolution profiles="+evolutions+", actual printing pairs="+pairs
            +", timing/execution cases="+timing+", explicitly unexercised (LV.X / missing base rules)="+blocked);
    }
    private static CardInstance card(UUID owner,String id) {return new CardInstance(UUID.randomUUID(),owner,id);}
    private static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
