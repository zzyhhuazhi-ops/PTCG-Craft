import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.CardInstance;
import dev.hua.ptcglive.rules.BattleStyle;
import java.util.*;

/** Actual transfers and play gates, beyond successful profile parsing. */
public final class NonPokemonBatch3Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static ServerMatch game(String id,EnergyType type){return NonPokemonBatch2Test.game(id,type,1);}
    static CardInstance card(ServerMatch m){return NonPokemonBatch2Test.trainer(m);}
    static void settle(ServerMatch m){NonPokemonBatch2Test.settle(m);}
    public static void main(String[] args){erika();blaine();blender();beach();mustard();delinquent();System.out.println("NonPokemonBatch3Test passed: hand gates, typed field count, complete Lost Zone cost, Stadium draw and end turn, Mustard evolution placement and draw, conditional opponent discard");}
    static void erika(){
        for(int size:List.of(5,6)){
            var m=game("sm9_en_140",EnergyType.GRASS);var c=card(m);NonPokemonBatch2Test.trim(m,A,size,c);
            check(m.canPlayTrainer(A,c.instanceId(),null)==(size==5),"Erika must count OTHER hand cards");
            if(size==5){int count=m.benchPokemonView(B).size()+1;m.playTrainer(A,c.instanceId(),null);settle(m);check(m.privateView(A).hand().size()==4+count,"Erika must count opponent active and bench");}
        }
    }
    static void blaine(){
        for(EnergyType type:List.of(EnergyType.FIRE,EnergyType.GRASS)){
            var m=game("sm7-5_en_058",type);var c=card(m);NonPokemonBatch2Test.trim(m,A,2,c);
            check(!m.canPlayTrainer(A,c.instanceId(),null),"Blaine accepted another hand card");
            NonPokemonBatch2Test.trim(m,A,1,c);check(m.canPlayTrainer(A,c.instanceId(),null)==(type==EnergyType.FIRE),"Blaine ignored Fire count");
            if(type==EnergyType.FIRE){int expected=2*(m.benchPokemonView(A).size()+1);m.playTrainer(A,c.instanceId(),null);settle(m);check(m.privateView(A).hand().size()==expected,"Blaine wrong draw");}
        }
    }
    static void blender(){
        for(int size:List.of(2,3)){
            var m=game("sm8_en_181",EnergyType.GRASS);var c=card(m);NonPokemonBatch2Test.trim(m,A,size,c);
            check(m.canPlayTrainer(A,c.instanceId(),null)==(size==3),"Lost Blender accepted partial cost");
            if(size==3){var paid=m.privateView(A).hand().stream().filter(x->!x.equals(c)).toList();m.playTrainer(A,c.instanceId(),null);settle(m);
                check(m.privateView(A).hand().size()==1,"Lost Blender wrong draw");
                check(m.privateView(A).lostZone().containsAll(paid),"Lost Blender cost did not enter Lost Zone");}
        }
    }
    static void beach(){
        var m=game("bwbsp_en_028",EnergyType.GRASS);m.playTrainer(A,card(m).instanceId(),null);
        check(!m.canActivateStadium(A),"Tropical Beach accepted hand above seven");NonPokemonBatch2Test.trim(m,A,3,null);
        m.activateStadium(A,m.clientView(A).stadium().instanceId(),m.stadiumEntrySequence());settle(m);
        check(m.privateView(A).hand().size()==7&&m.currentPlayer().equals(B),"Tropical Beach did not draw to seven and end turn");
    }
    static void mustard(){
        for(String id:List.of("swsh5_en_132","swsh5_en_134")){
            var m=game(id,EnergyType.GRASS);var c=card(m);NonPokemonBatch2Test.trim(m,A,2,c);
            check(!m.canPlayTrainer(A,c.instanceId(),null),"Mustard accepted another hand card");
            NonPokemonBatch2Test.trim(m,A,1,c);
            if(id.endsWith("132"))check(!m.canPlayTrainer(A,c.instanceId(),null),"Rapid Strike Mustard accepted no matching discard");
            else {m.playTrainer(A,c.instanceId(),null);settle(m);check(m.privateView(A).hand().size()==5,"Single Strike Mustard must draw after failed hidden-deck search");}
        }
        for(BattleStyle style:List.of(BattleStyle.RAPID_STRIKE,BattleStyle.SINGLE_STRIKE)){
            String id=style==BattleStyle.RAPID_STRIKE?"swsh5_en_132":"swsh5_en_134";
            var m=GeneratedRuleBatch8Test.fixture(List.of(),EnergyType.GRASS,EnergyType.GRASS,
                NonPokemonBatch2Test.DATA.trainer(id).orElseThrow(),GeneratedRuleBatch8Test.hit(10,false),1,Set.of(style));
            var c=card(m);NonPokemonBatch2Test.trim(m,A,1,c);int bench=m.benchPokemonView(A).size();
            m.playTrainer(A,c.instanceId(),null);var p=m.effectCardPrompt(A);
            check(p!=null,"Mustard omitted selection");
            var evolved=p.candidates().stream().filter(x->x.definitionId().equals("evolved")).findFirst().orElseThrow();
            m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(evolved.instanceId()));settle(m);
            check(m.benchPokemonView(A).size()==bench+1&&m.privateView(A).hand().size()==5,"Mustard must place evolution then draw five");
        }
    }
    static void delinquent(){
        var m=GeneratedRuleBatch23Test.fixture(NonPokemonBatch2Test.DATA.trainer("xy9_en_098").orElseThrow(),0,EnergyType.GRASS);
        var c=card(m);check(!m.canPlayTrainer(A,c.instanceId(),null),"Delinquent accepted absent Stadium");
        m.playTrainer(A,DynamicPassiveModifierTest.hand(m,A,"stadium").instanceId(),null);
        int before=m.privateView(B).hand().size();m.playTrainer(A,c.instanceId(),null);settle(m);
        check(m.clientView(A).stadium()==null&&m.privateView(B).hand().size()==before-3,"Delinquent omitted conditional opponent discard");
    }
}
