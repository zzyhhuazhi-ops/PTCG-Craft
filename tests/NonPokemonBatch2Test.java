import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch2Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    static ServerMatch game(String id,EnergyType type,int retreat){return GeneratedRuleBatch8Test.fixture(List.of(),type,type,DATA.trainer(id).orElseThrow(),GeneratedRuleBatch8Test.hit(10,false),retreat);}
    static CardInstance trainer(ServerMatch m){return DynamicPassiveModifierTest.hand(m,A,"trainer");}
    static void settle(ServerMatch m){AiCardOperationTest.settle(MatchSession.multiplayer(m,A,B),AiDifficulty.NORMAL);}
    public static void main(String[] args){heavyBall();zisu();festival();cave();village();System.out.println("NonPokemonBatch2Test passed: printed retreat filter, opponent-hand draw target, six-Pokemon healing, typed healing, Stadium allowance and turn ending");}
    static void heavyBall(){
        var m=game("bw4_en_088",EnergyType.GRASS,3);m.playTrainer(A,trainer(m).instanceId(),null);
        var q=m.effectCardPrompt(A);check(q!=null&&!q.candidates().isEmpty(),"Heavy Ball omitted matching printed retreat cost");
        check(q.candidates().stream().allMatch(c->Set.of("basic","rulebox").contains(c.definitionId())),"Heavy Ball accepted a lower-retreat card");
        var chosen=q.candidates().getFirst();m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(chosen.instanceId()));settle(m);
        check(m.privateView(A).hand().contains(chosen),"Heavy Ball failed to transfer selected Pokemon");
    }
    static void trim(ServerMatch m,UUID owner,int size,CardInstance keep){
        var discard=m.privateView(owner).hand().stream().filter(c->!c.equals(keep)).limit(m.privateView(owner).hand().size()-size).map(CardInstance::instanceId).toList();
        m.discardHandCardsByEffect(A,owner,discard);
    }
    static void zisu(){
        for(int own:List.of(2,6)) {
            var m=game("swsh10_en_159",EnergyType.GRASS,1);var card=trainer(m);trim(m,A,own,card);trim(m,B,4,null);
            if(own==6){check(!m.canPlayTrainer(A,card.instanceId(),null),"Zisu accepted a hand already at the target after playing");continue;}
            m.playTrainer(A,card.instanceId(),null);settle(m);
            check(m.privateView(A).hand().size()==5&&m.privateView(B).hand().size()==4,"Zisu did not draw exactly one more than opponent");
        }
    }
    static void festival(){
        var m=game("smbsp_en_078",EnergyType.GRASS,1);m.playTrainer(A,trainer(m).instanceId(),null);
        var active=m.activePokemonView(A).pokemonId();m.placeDamageCountersByEffect(A,A,active,3);
        check(!m.canActivateStadium(A),"Champions Festival activated with fewer than six Pokemon");
        while(m.benchPokemonView(A).size()<5)m.playBasicPokemonToBench(A,DynamicPassiveModifierTest.hand(m,A,"basic").instanceId());
        m.activateStadium(A,m.clientView(A).stadium().instanceId(),m.stadiumEntrySequence());settle(m);
        check(m.activePokemonView(A).damage()==20&&!m.canActivateStadium(A),"Festival healing or once-per-turn allowance wrong");
    }
    static void cave(){
        for(EnergyType type:List.of(EnergyType.METAL,EnergyType.GRASS)) {
            var m=game("swsh7_en_144",type,1);m.playTrainer(A,trainer(m).instanceId(),null);m.placeDamageCountersByEffect(A,A,m.activePokemonView(A).pokemonId(),4);
            if(type==EnergyType.GRASS){check(!m.canActivateStadium(A),"Crystal Cave healed Grass Pokemon");continue;}
            m.activateStadium(A,m.clientView(A).stadium().instanceId(),m.stadiumEntrySequence());settle(m);
            check(m.activePokemonView(A).damage()==10,"Crystal Cave did not heal exactly 30");
        }
    }
    static void village(){
        var m=game("swsh10_en_148",EnergyType.GRASS,1);m.playTrainer(A,trainer(m).instanceId(),null);
        m.activateStadium(A,m.clientView(A).stadium().instanceId(),m.stadiumEntrySequence());settle(m);
        check(m.privateView(A).hand().size()==5&&m.currentPlayer().equals(B),"Jubilife Village did not draw five and end the turn");
    }
}
