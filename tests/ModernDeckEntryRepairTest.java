import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernDeckEntryRepairTest {
    static final UUID A=ModernRuleRepair2Test.A,B=ModernRuleRepair2Test.B;
    static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean b,String why){ModernRuleRepair2Test.check(b,why);}
    public static void main(String[] args){parasect();drilbur();System.out.println("ModernDeckEntryRepairTest passed: zero/one/two heads, Grass Evolution direct placement, Bench capacity clamp, private search, optional hand-entry basic Fighting discard and source-zone exclusions");}
    static void parasect(){
        var attack=DATA.pokemon("sv3-5_en_047").orElseThrow().attacks().getFirst();AttackProgramRules.validate(attack);Set<Integer> seen=new HashSet<>();boolean evolution=false;
        for(int seed=0;seed<24;seed++)for(boolean narrow:List.of(false,true)){
            var m=ModernEnergyRepairTest.game(DATA.trainer("swsh5_en_124").orElseThrow(),List.of(),attack,EnergyType.GRASS,seed);
            if(narrow)while(m.benchPokemonView(A).size()<4)m.playBasicPokemonToBench(A,ModernRuleRepair2Test.hand(m,A,"basic").instanceId());
            int before=m.benchPokemonView(A).size();ModernRuleRepair2Test.pay(m,A,m.activePokemonView(A).pokemonId(),"energy",1);int rng=m.randomTranscript().size();m.attack(A,attack.id());
            var flips=m.randomTranscript().subList(rng,m.randomTranscript().size()).stream().filter(e->e.operation().startsWith("effect-coin")).toList();check(flips.size()==2,"Parasect did not flip exactly two server coins");int heads=(int)flips.stream().filter(e->e.result()==0).count();seen.add(heads);
            if(heads==0){check(!m.hasPendingEffectChoice(),"Zero heads created mandatory search");continue;}
            var q=m.effectCardPrompt(A);check(q.maximum()==Math.min(heads,5-before)&&q.minimum()==0,"Coin search ignored free Bench slots");
            check(q.candidates().stream().noneMatch(c->c.definitionId().equals("wrongmon"))&&m.clientView(B).effectSelection()==null,"Grass filter or privacy failed");
            var selected=q.candidates().stream().sorted(Comparator.comparing(c->!c.definitionId().equals("evolved"))).limit(q.maximum()).toList();evolution|=selected.stream().anyMatch(c->c.definitionId().equals("evolved"));
            m.submitEffectCards(A,q.resolutionId(),q.step(),selected.stream().map(CardInstance::instanceId).toList());var reveal=m.effectCardReveal();if(reveal!=null)m.acknowledgeEffectReveal(reveal.confirmingPlayer(),reveal.id());
            check(m.benchPokemonView(A).size()==before+selected.size()&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Parasect failed direct placement or stalled");
        }check(seen.equals(Set.of(0,1,2))&&evolution,"Missing coin branch or Evolution placement coverage");
    }
    static void drilbur(){
        var ability=DATA.pokemon("sv5_en_085").orElseThrow().abilities().getFirst();TriggeredAbilityRules.validate(ability);
        for(int count:List.of(-1,0,1,3)){
            var m=ModernEnergyRepairTest.game(DATA.trainer("swsh5_en_124").orElseThrow(),List.of(ability),GeneratedRuleBatch8Test.hit(10,false),EnergyType.FIGHTING,0);
            check(m.triggerOrderPrompt()==null,"Opening triggered Dig Dig Dig");var fromDeck=m.privateView(A).deck().stream().filter(c->c.definitionId().equals("basic")).findFirst().orElseThrow();m.putBasicPokemonFromOwnDeckToBenchByEffect(A,fromDeck.instanceId());check(m.triggerOrderPrompt()==null,"Deck entry triggered hand-only Ability");
            m.playBasicPokemonToBench(A,ModernRuleRepair2Test.hand(m,A,"basic").instanceId());var order=m.triggerOrderPrompt();check(order!=null&&order.optionalDecision(),"Hand entry omitted optional trigger");m.decideTriggeredEffect(A,order.id(),count>=0);
            if(count>=0){var q=m.effectCardPrompt(A);check(q.candidates().stream().allMatch(c->c.definitionId().equals("energy"))&&q.minimum()==0&&q.maximum()==3,"Dig Dig Dig filter/count");var selected=q.candidates().stream().limit(count).toList();check(selected.size()==count,"Dig fixture lacks cards");m.submitEffectCards(A,q.resolutionId(),q.step(),selected.stream().map(CardInstance::instanceId).toList());var reveal=m.effectCardReveal();if(reveal!=null)m.acknowledgeEffectReveal(reveal.confirmingPlayer(),reveal.id());check(m.privateView(A).discard().containsAll(selected),"Dig sent Energy to hand instead of discard");}
            check(!m.hasPendingEffectChoice()&&m.triggerOrderPrompt()==null&&m.currentPlayer().equals(A),"Dig decline/selection stalled");
        }
    }
}
