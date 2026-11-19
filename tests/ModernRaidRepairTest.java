import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;
public final class ModernRaidRepairTest {
 static final UUID A=ModernRuleRepair2Test.A,B=ModernRuleRepair2Test.B;
 static void check(boolean b,String s){ModernRuleRepair2Test.check(b,s);}
    static ServerMatch game(AttackDefinition attack,int start,String name){
        var defs=new CardDatabase(List.of(StadiumActivationTest.definition("basic",CardCategory.POKEMON,TrainerCategory.NONE,true),StadiumActivationTest.definition("evolved",CardCategory.POKEMON,TrainerCategory.NONE,false),StadiumActivationTest.definition("energy",CardCategory.ENERGY,TrainerCategory.NONE,false),StadiumActivationTest.definition("tool",CardCategory.TRAINER,TrainerCategory.TOOL,false)));
        var unit=new EnergyProfile.Unit(EnumSet.allOf(EnergyType.class));var energy=new EnergyProfile(List.of(unit,unit,unit));
        var combat=new CombatCatalog(Map.of("basic",new PokemonProfile(500,0,null,null,0,null,0,1,List.of(attack),List.of(),EnergyType.PSYCHIC,name,null),"evolved",new PokemonProfile(500,0,"basic",null,0,null,0,1,List.of(attack),List.of(),EnergyType.PSYCHIC,name,null)),Map.of(),Map.of("tool",DynamicPassiveModifierTest.plainTool()),Map.of("energy",energy));
        for(int seed=start;seed<start+1000;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID p:List.of(A,B)){var deck=new ArrayList<CardInstance>();for(int i=0;i<60;i++)deck.add(new CardInstance(new UUID(p.getLeastSignificantBits(),i+1),p,i%10<4?"basic":i%10<6?"evolved":i%10<9?"energy":"tool"));decks.add(deck);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,defs,combat,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->c.definitionId().equals("basic")).count()<2))continue;
            OpeningTestSupport.complete(m,A,B,1);for(UUID p:List.of(A,B))m.drawCardsByEffect(A,p,20);
            if(List.of(A,B).stream().allMatch(p->List.of("energy","evolved","tool","basic").stream().allMatch(id->m.privateView(p).hand().stream().anyMatch(c->c.definitionId().equals(id)))))return m;
        }throw new AssertionError("shuffle fixture");
    }

 public static void main(String[] args){
  for(int seed=0;seed<3;seed++)for(String name:List.of("Combee","Combee ex","Other"))for(boolean available:List.of(false,true)){
   var a=ModernWindowDamageRepairTest.attack("sv2_en_009",1);var m=game(a,seed,name);
   UUID bench=m.benchPokemonView(A).getFirst().pokemonId();
   var stack=ModernJointDepartureRepairTest.dress(m,A,bench);
   ModernShuffleScopeRepairTest.pay(m,A,m.activePokemonView(A).pokemonId());
   m.endTurn(A);m.endTurn(B);
   if(!available)m.movePokemonByEffect(A,A,bench,LeavePlayDestination.HAND);
   int old=m.activePokemonView(B).damage();m.attack(A,a.id());
   boolean success=available&&name.equals("Combee");
   if(success){
    var p=m.effectPokemonPrompt(A);check(p.targets().size()==1,"Exact named Bench filter");
    CopyAttackTest.reject(()->m.chooseEffectPokemon(B,p.resolutionId(),p.step(),bench));
    CopyAttackTest.reject(()->m.chooseEffectPokemon(A,p.resolutionId(),p.step(),m.activePokemonView(A).pokemonId()));
    m.chooseEffectPokemon(A,p.resolutionId(),p.step(),bench);
    CopyAttackTest.reject(()->m.chooseEffectPokemon(A,p.resolutionId(),p.step(),bench));
    check(m.privateView(A).deck().containsAll(stack),"Whole stack not shuffled");
   }
   check(m.activePokemonView(B).damage()-old==(success?120:0),"Damage requires actual return");
   check(!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Attack stuck");
  }
  System.out.println("ModernRaidRepairTest passed: 18 scenarios, exact names, Bench only, whole stack, failure does zero, authority/replay");
 }
}
