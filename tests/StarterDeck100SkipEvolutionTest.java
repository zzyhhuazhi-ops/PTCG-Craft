import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

public final class StarterDeck100SkipEvolutionTest {
    static final UUID A=StarterDeck100PokemonTest.A,B=StarterDeck100PokemonTest.B;
    static String id(int n){return StarterDeck100PokemonTest.id(n);}
    static void check(boolean ok,String why){StarterDeck100PokemonTest.check(ok,why);}
    public static void main(String[] args){for(boolean take:List.of(false,true))outcomes(take);malformed();System.out.println("StarterDeck100SkipEvolutionTest passed: one coin, named exact one-stage skip, captured source, preserved attachments/damage, fail-to-find shuffle and tails no shuffle");}
    static void outcomes(boolean take){boolean heads=false,tails=false;for(int seed=0;seed<160&&(!heads||!tails);seed++){
        var m=StarterDeck100PokemonTest.gameSeed(317,1,seed,id(318),id(319),id(1));var source=m.activePokemonView(A).pokemonId();StarterDeck100PokemonTest.charge(m,0);m.placeDamageCountersByEffect(A,A,source,2);m.applySpecialConditionByEffect(A,A,SpecialCondition.POISONED);var energies=m.activePokemonView(A).attachedEnergyIds();int rng=m.randomTranscript().size();var before=m.privateView(A).deck();m.attack(A,"ultra-evolution");var p=m.hasPendingCardSelection()?m.effectCardPrompt(A):null;
        if(p==null){tails=true;check(m.randomTranscript().size()==rng+1&&m.privateView(A).deck().equals(before)&&m.activePokemonView(A).topCardId().equals(id(317)),"tails searched, evolved or shuffled");continue;}
        heads=true;check(p.candidates().size()>0&&p.candidates().stream().allMatch(c->c.definitionId().equals(id(319))),"Axew offered Fraxure, unrelated Pokemon or wrong Haxorus");check(m.randomTranscript().size()==rng+1,"heads rolled twice or shuffled before selection");var wrong=before.stream().filter(c->c.definitionId().equals(id(318))).findFirst().orElseThrow();TopDeckLookTest.rejects(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(wrong.instanceId())));var chosen=p.candidates().getFirst();m.submitEffectCards(A,p.resolutionId(),p.step(),take?List.of(chosen.instanceId()):List.of());var view=m.activePokemonView(A);check(view.pokemonId().equals(source)&&view.topCardId().equals(id(take?319:317))&&view.attachedEnergyIds().equals(energies),"evolution changed instance/attachments or skipped wrong stage");check(view.damage()==(take?20:30)&&view.conditions().contains(SpecialCondition.POISONED)==!take,"evolution did not clear poison or preserve damage before Checkup");check(m.randomTranscript().size()==rng+1+m.privateView(A).deck().size()-1,"heads did not shuffle exactly once");TopDeckLookTest.rejects(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(chosen.instanceId())));
    }check(heads&&tails,"seed coverage missing coin side");}
    static void malformed(){String good="{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":1,\"minimum\":0,\"card_category\":\"POKEMON\",\"destination\":\"EVOLVE_SELF\",\"pokemon_names\":\"Haxorus\",\"skip_stage\":1}";for(String bad:List.of(good.replace("\"skip_stage\":1","\"skip_stage\":2"),good.replace(",\"pokemon_names\":\"Haxorus\"",""),good.replace("EVOLVE_SELF","BENCH"))){TopDeckLookTest.rejects(()->ZoneSelectionEffect.forProgram(EffectRegistry.standard().parse(JsonParser.parseString(bad).getAsJsonObject()),true));}}
}
