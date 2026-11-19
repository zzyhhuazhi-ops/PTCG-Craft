import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernPalafinRepairTest {
    static final UUID A=ModernRuleRepair2Test.A,B=ModernRuleRepair2Test.B;
    static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean ok,String why){ModernRuleRepair2Test.check(ok,why);}
    static ServerMatch fixture(int start){
        var defs=new ArrayList<CardDefinition>();var profiles=new HashMap<String,PokemonProfile>();
        for(String id:List.of("sv6_en_059","sv6_en_060","sv6_en_061","sv6_en_193")){
            defs.add(StadiumActivationTest.definition(id,CardCategory.POKEMON,TrainerCategory.NONE,id.equals("sv6_en_059")));
            profiles.put(id,DATA.pokemon(id).orElseThrow());
        }
        defs.add(StadiumActivationTest.definition("energy",CardCategory.ENERGY,TrainerCategory.NONE,false));
        defs.add(StadiumActivationTest.definition("tool",CardCategory.TRAINER,TrainerCategory.TOOL,false));
        var combat=new CombatCatalog(profiles,Map.of("energy",EnergyType.WATER),Map.of("tool",DynamicPassiveModifierTest.plainTool()));
        for(int seed=start;seed<start+1000;seed++){
            var decks=new ArrayList<List<CardInstance>>();String[] ids={"sv6_en_059","sv6_en_059","sv6_en_059","sv6_en_060","sv6_en_060","sv6_en_061","sv6_en_193","energy","energy","tool"};
            for(UUID p:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(p.getLeastSignificantBits(),i+1),p,ids[i%ids.length]));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),combat,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->c.definitionId().equals("sv6_en_059")).count()<2))continue;
            OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,10);m.endTurn(A);m.endTurn(B);
            if(List.of("sv6_en_060","energy","tool").stream().allMatch(id->m.privateView(A).hand().stream().anyMatch(c->c.definitionId().equals(id)))
                &&m.privateView(A).deck().stream().anyMatch(c->c.definitionId().equals("sv6_en_061"))&&m.privateView(A).deck().stream().anyMatch(c->c.definitionId().equals("sv6_en_193")))return m;
        }throw new AssertionError("Palafin fixture");
    }
    public static void main(String[] args){
        for(int seed:List.of(0,30,80))for(int choice:List.of(-1,0,1,2)){
            var m=fixture(seed);var f=EventTargetBindingTest.field(m);UUID host=m.activePokemonView(A).pokemonId(),bench=m.benchPokemonView(A).getFirst().pokemonId();
            var base=f.topCard(A,host);var palafin=ModernRuleRepair2Test.hand(m,A,"sv6_en_060");m.evolve(A,host,palafin.instanceId());
            var energy=ModernRuleRepair2Test.hand(m,A,"energy");var tool=ModernRuleRepair2Test.hand(m,A,"tool");
            m.attachEnergy(A,energy.instanceId(),host);m.attachToolByEffect(A,A,EffectSourceZone.HAND,tool.instanceId(),A,host);
            f.placeDamageCounters(A,host,3);long entry=f.pokemonEntrySequence(A,host);
            m.switchActiveByEffect(A,A,bench);var prompt=m.triggerOrderPrompt();check(prompt!=null&&prompt.optionalDecision(),"Missing own-turn optional trigger");
            CopyAttackTest.reject(()->m.decideTriggeredEffect(B,prompt.id(),true));m.decideTriggeredEffect(A,prompt.id(),choice!=-1);
            if(choice!=-1){var q=m.effectCardPrompt(A);check(q.candidates().stream().allMatch(c->Set.of("sv6_en_061","sv6_en_193").contains(c.definitionId())),"Palafin ex name filter failed");
                var selected=choice==0?List.<UUID>of():List.of(q.candidates().stream().filter(c->c.definitionId().equals(choice==1?"sv6_en_061":"sv6_en_193")).findFirst().orElseThrow().instanceId());
                CopyAttackTest.reject(()->m.submitEffectCards(B,q.resolutionId(),q.step(),selected));m.submitEffectCards(A,q.resolutionId(),q.step(),selected);
                CopyAttackTest.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),selected));
            }
            var result=f.pokemonView(A,host);check(!m.hasPendingEffectChoice()&&m.currentPlayer().equals(A),"Palafin stalled or ended turn");
            check(result.damage()==30&&f.pokemonEntrySequence(A,host)==entry&&f.attachedEnergy(A,host).contains(energy)&&result.attachedTools().contains(tool),"Palafin lost physical state");
            check(result.evolutionCardIds().contains(base.definitionId()),"Lower evolution card lost");
            check(result.topCardId().equals(choice>0?(choice==1?"sv6_en_061":"sv6_en_193"):"sv6_en_060"),"Wrong replacement");
            if(choice>0)check(m.privateView(A).deck().contains(palafin),"Old Palafin not shuffled back");
            m.endTurn(A);m.switchActiveByEffect(B,A,host);m.switchActiveByEffect(B,A,bench);check(m.triggerOrderPrompt()==null,"Opponent turn must not offer Zero to Hero");
        }
        System.out.println("ModernPalafinRepairTest passed: 12 scenarios; own-turn trigger, decline/search failure/two prints, private exact search, identity/attachments/damage preservation, authority/stale checks and no opponent-turn activation");
    }
}
