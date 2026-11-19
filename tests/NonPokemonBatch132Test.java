import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch132Test {
    static final List<String> IDS=List.of("sm12_en_208","sc_csm2ac_179_sr");
    static final UUID A=WillCoinMatchTest.A,B=WillCoinMatchTest.B;
    static TrainerProfile profile(String id){return NonPokemonBatch2Test.DATA.trainer(id).orElseThrow();}
    static ServerMatch aiGame(String id){var c=new GeneratedRuleBatch28Test.Config();c.trainer=profile(id);c.attack=AttackCoinMatchTest.nativeAttack("\"baseDamage\":400,\"coinDamage\":{\"flips\":1,\"perHeads\":600}");c.defenderAttack=c.attack;return GeneratedRuleBatch28Test.fixture(c);}
    public static void main(String[] args){
        for(String id:IDS){var p=profile(id);WillCoinMatchTest.check(p.category()==TrainerCategory.SUPPORTER&&p.composed(),"Will profile missing");
            WillCoinMatchTest.check(p.effectProgram().triggers().size()==1&&p.effectProgram().triggers().getFirst().effects().size()==1&&p.effectProgram().triggers().getFirst().effects().getFirst().type().equals("turn_choose_next_coin"),"Will mapped to wrong operation");
            for(UUID player:List.of(A,B))for(boolean heads:List.of(false,true)){var m=aiGame(id);if(player.equals(B))m.endTurn(A);WillCoinMatchTest.play(m,player);int n=m.randomTranscript().size();
                m.attack(player,"hit");WillCoinMatchTest.check(m.randomTranscript().size()==n,"Actual Will selected or flipped early");WillCoinMatchTest.choose(m,player,heads);NonPokemonBatch103Test.settle(m);WillCoinMatchTest.check(m.randomTranscript().get(n).result()==(heads?0:1),"Actual Will outcome wrong");
            }
            for(var level:AiDifficulty.values()){
                var m=aiGame(id);WillCoinMatchTest.play(m,A);m.attack(A,"hit");var session=MatchSession.multiplayer(m,A,B);AiCardOperationTest.settle(session,level);AiCardOperationTest.finish(session,level);
                WillCoinMatchTest.check(m.phase()==MatchPhase.FINISHED,"Will did not finish a match: "+id+" "+level);
            }
        }
        WillCoinMatchTest.check(profile(IDS.getFirst()).effectProgram().equals(profile(IDS.getLast()).effectProgram()),"CN printing diverges from exact English counterpart");
        System.out.println("NonPokemonBatch132Test passed: English/CN Will profiles, actual outcome timing and Supporter use, equivalent program, four AI difficulties through complete matches.");
    }
}
