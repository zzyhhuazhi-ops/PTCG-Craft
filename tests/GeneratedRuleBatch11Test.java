import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Actual generated attacks with controlled hosts: public counts, zone choices and whole-card departure. */
public final class GeneratedRuleBatch11Test {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void reject(Runnable action){BeforeDamageAttackTest.reject(action);}
    static AttackDefinition attack(String id,int slot){return GeneratedRuleBatch6Test.attack(id,slot);}
    static ServerMatch fixture(AttackDefinition attack){return GeneratedRuleBatch9Test.fixture(attack);}
    static UUID active(ServerMatch m,UUID owner){return m.activePokemonView(owner).pokemonId();}
    static UUID bench(ServerMatch m,UUID owner){return m.benchPokemonView(owner).getFirst().pokemonId();}
    static CardInstance hand(ServerMatch m,UUID owner,String id){return DynamicPassiveModifierTest.hand(m,owner,id);}
    static void turn(ServerMatch m,UUID owner){GeneratedRuleBatch9Test.turn(m,owner);}
    static void attach(ServerMatch m,UUID owner,UUID host,String id){TypedEnergyCountTest.attach(m,owner,host,id);}
    static void select(ServerMatch m,List<UUID> ids){GeneratedRuleBatch10Test.select(m,ids);}
    static void optional(ServerMatch m,boolean use){var p=m.effectChoicePrompt(A);check(p.optionsZh().size()==2,"missing optional decision");reject(()->m.chooseEffectOption(B,p.resolutionId(),p.step(),0));m.chooseEffectOption(A,p.resolutionId(),p.step(),use?0:1);reject(()->m.chooseEffectOption(A,p.resolutionId(),p.step(),0));}
    public static void main(String[] args){fieldCounts();energyCounts();prizesAndCounters();directCounters();typedSearch();discardRecovery();handDraw();cure();departures();optionalStadium();System.out.println("GeneratedRuleBatch11Test passed: actual count ownership, multi-unit Energy, public recovery, typed search, whole-hand draw, cure, attached-stack departure and optional Stadium");}
    static void fieldCounts(){
        for(String id:List.of("bw1_en_089","rsv10-5_en_062","me2_en_044"))for(int own:List.of(0,2))for(int opp:List.of(0,2)){
            var a=attack(id,id.equals("bw1_en_089")?1:0);var m=fixture(a);
            for(UUID owner:List.of(A,B)){turn(m,owner);int n=owner.equals(A)?own:opp;if(n==0)m.movePokemonByEffect(owner,owner,bench(m,owner),LeavePlayDestination.HAND);else m.playBasicPokemonToBench(owner,hand(m,owner,"basic").instanceId());}
            turn(m,A);m.attack(A,a.id());int expected=id.equals("bw1_en_089")?20*own:id.equals("rsv10-5_en_062")?30*opp:20*(own+1);check(m.lastAttack().finalDamage()==expected,"field count mixed owners or Active/Bench: "+id);
        }
    }
    static void energyCounts(){
        for(String id:List.of("sm1_en_059","svbsp_en_004"))for(boolean charged:List.of(false,true)){
            var a=attack(id,id.equals("svbsp_en_004")?1:0);var m=fixture(a);attach(m,A,active(m,A),"energy");attach(m,A,bench(m,A),"special");attach(m,B,bench(m,B),"special");if(charged){attach(m,B,active(m,B),"special");attach(m,B,active(m,B),"energy");}
            m.attack(A,a.id());check(m.lastAttack().finalDamage()==(id.equals("sm1_en_059")?10*(charged?3:0):30*(charged?4:1)),"Energy query counted cards or Bench units");
        }
    }
    static void prizesAndCounters(){
        for(int n:List.of(0,1,2)){
            var a=attack("sv1_en_109",0);var m=fixture(a);GeneratedRuleBatch9Test.take(m,A,1);GeneratedRuleBatch9Test.take(m,B,n);m.attack(A,a.id());check(m.lastAttack().finalDamage()==70*n,"Rage Fist counted own or remaining Prizes");
            a=attack("xy2_en_045",1);m=fixture(a);if(n>0)m.placeDamageCountersByEffect(A,A,active(m,A),n);m.placeDamageCountersByEffect(A,B,active(m,B),4);m.attack(A,a.id());check(m.lastAttack().finalDamage()==10*n,"Flail used opponent damage or damage points");
        }
    }
    static void directCounters(){var a=attack("sm8_en_099",0);var m=fixture(a);m.attack(A,a.id());check(m.lastAttack().finalDamage()==0&&m.activePokemonView(B).damage()==10&&m.benchPokemonView(B).getFirst().damage()==0,"Haunt became attack damage or hit wrong target");}
    static void typedSearch(){
        var a=attack("rsv10-5_en_055",0);
        for(boolean find:List.of(false,true)){
            var m=GeneratedRuleBatch8Test.fixture(List.of(),EnergyType.GRASS,EnergyType.DARKNESS,GeneratedRuleBatch8Test.trainer("swsh5_en_124"),a);GeneratedRuleBatch10Test.seedDeck(m,"basic",1);m.attack(A,a.id());var p=m.effectCardPrompt(A);
            check(p.minimum()==0&&p.maximum()==3&&p.candidates().stream().allMatch(c->Set.of("basic","rulebox").contains(c.definitionId())),"Invite Evil admitted wrong type/category");int size=m.privateView(A).hand().size();select(m,find?List.of(p.candidates().getFirst().instanceId()):List.of());check(m.privateView(A).hand().size()==size+(find?1:0)&&m.currentPlayer().equals(B),"typed search did not reveal/shuffle/end attack");
        }
    }
    static void discardRecovery(){
        var a=attack("sm1_en_012",1);
        for(int count:List.of(0,2,4)){
            var m=fixture(a);var discarded=m.privateView(A).hand().stream().limit(count).map(CardInstance::instanceId).toList();if(count>0)m.discardHandCardsByEffect(A,A,discarded);int before=m.privateView(A).hand().size();m.attack(A,a.id());
            if(count>0){var p=m.effectCardPrompt(A);check(p.minimum()==Math.min(3,count)&&p.maximum()==3,"Hollow Hunt selection bounds: "+p.minimum()+".."+p.maximum());reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of()));select(m,discarded.subList(0,Math.min(3,count)));}
            check(m.privateView(A).hand().size()==before+Math.min(3,count)&&m.currentPlayer().equals(B),"Hollow Hunt wrong recovery or empty discard behavior");m.endTurn(B);reject(()->m.attack(A,a.id()));
        }
    }
    static void handDraw(){
        var a=attack("svbsp_en_201",0);
        for(boolean empty:List.of(false,true)){
            var m=fixture(a);if(empty)m.discardHandCardsByEffect(A,A,m.privateView(A).hand().stream().map(CardInstance::instanceId).toList());int before=m.privateView(A).hand().size(),discard=m.privateView(A).discard().size(),deck=m.privateView(A).deck().size();m.attack(A,a.id());
            check(m.privateView(A).hand().size()==6&&m.privateView(A).discard().size()==discard+before&&m.privateView(A).deck().size()==deck-6,"Full Speed whole-hand order or empty hand");
        }
    }
    static void cure(){var a=attack("swsh12_en_016",0);var m=fixture(a);m.placeDamageCountersByEffect(A,A,active(m,A),2);m.applySpecialConditionByEffect(A,A,SpecialCondition.POISONED);m.applySpecialConditionByEffect(A,A,SpecialCondition.BURNED);m.attack(A,a.id());check(m.activePokemonView(A).conditions().isEmpty()&&m.activePokemonView(A).damage()==20&&m.lastAttack().finalDamage()==90,"Aroma Shot cured after Checkup or changed existing damage");}
    static void departures(){
        for(String id:List.of("me4_en_030","me2_en_010","me5-5_en_073"))for(boolean use:List.of(false,true)){
            boolean may=id.equals("me5-5_en_073"),toHand=id.equals("me4_en_030");if(!may&&!use)continue;var a=attack(id,may?0:1);var m=fixture(a);UUID host=active(m,A),physical=PokemonIdentityTestSupport.physical(m,A,host),replacement=bench(m,A);var evolved=hand(m,A,"evolved");var energy=hand(m,A,"energy");var tool=hand(m,A,"tool");
            m.evolveByEffect(A,host,evolved.instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);attach(m,A,host,"energy");m.attachToolByEffect(A,A,EffectSourceZone.HAND,tool.instanceId(),A,host);int before=m.turnNumber();m.attack(A,a.id());if(may)optional(m,use);
            if(use){check(m.phase()==MatchPhase.CHOOSE_REPLACEMENT&&m.pendingChoicePlayer().equals(A),"departure skipped own replacement");var zone=toHand?m.privateView(A).hand():m.privateView(A).deck();check(zone.stream().map(CardInstance::instanceId).toList().containsAll(List.of(physical,evolved.instanceId(),energy.instanceId(),tool.instanceId())),"departure lost bottom/evolution/energy/tool");m.chooseReplacement(A,replacement);check(active(m,A).equals(replacement),"departure replacement not installed");}
            else check(active(m,A).equals(host)&&m.activePokemonView(A).attachedEnergyIds().size()==1,"declined departure still moved stack");
            check(m.currentPlayer().equals(B)&&m.turnNumber()==before+1&&m.privateView(B).prizes().size()==6,"departure awarded prizes or advanced turn incorrectly");
        }
    }
    static void optionalStadium(){
        var a=attack("sv7_en_124",0);
        for(String state:List.of("none","own","opponent"))for(boolean use:List.of(false,true)){
            var m=BeforeDamageAttackTest.fixture(a);CardInstance stadium=null;UUID owner=state.equals("opponent")?B:A;
            if(!state.equals("none")){turn(m,owner);stadium=hand(m,owner,"stadium");m.playTrainer(owner,stadium.instanceId(),null);turn(m,A);}m.attack(A,a.id());optional(m,use);
            check((m.clientView(null).stadium()!=null)==(!state.equals("none")&&!use),"optional Stadium state wrong");if(stadium!=null&&use)check(m.privateView(owner).discard().contains(stadium),"Stadium discarded to wrong owner");check(m.currentPlayer().equals(B),"optional Stadium attack did not finish");
        }
    }
}
