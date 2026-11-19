import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

/** Official Glimmora/Froslass text: any KO cause, correct position, all prizes versus one fewer. */
public final class CoinPrizeKnockoutTest {
    static final UUID A=WillCoinMatchTest.A,B=WillCoinMatchTest.B;
    static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    static ServerMatch game(String id,int prizes){var c=new GeneratedRuleBatch28Test.Config();c.trainer=WillCoinMatchTest.trainer(WillCoinMatchTest.WILL);c.ownPrizes=prizes;c.ownHp=130;c.ownAbility=NonPokemonBatch2Test.DATA.pokemon(id).orElseThrow().abilities().getFirst();return GeneratedRuleBatch28Test.fixture(c);}
    public static void main(String[] args){glimmora();froslass();checkup();System.out.println("CoinPrizeKnockoutTest passed: real Glimmora/Froslass abilities, direct-effect Active and Bench KO, zero all prizes versus one fewer, Will before removal, Checkup exclusion and no duplicate awards.");}
    static void glimmora(){for(String id:List.of("sv2_en_126","svalt_en_051"))for(boolean bench:List.of(false,true))for(boolean heads:List.of(false,true)){
        var m=game(id,3);WillCoinMatchTest.play(m,A);UUID host=bench?m.benchPokemonView(A).getFirst().pokemonId():m.activePokemonView(A).pokemonId();int prizes=m.privateView(B).prizes().size(),n=m.randomTranscript().size();
        m.knockOutPokemonByEffect(A,A,host);check(m.hasPendingEffectChoice()&&m.randomTranscript().size()==n&&m.privateView(B).prizes().size()==prizes,"Glimmora KO lost coin window or awarded prizes early");
        WillCoinMatchTest.choose(m,A,heads);NonPokemonBatch103Test.settle(m);
        check(m.randomTranscript().size()==n+1&&m.privateView(B).prizes().size()==prizes-(heads?0:3),"Glimmora did not block all prizes for any-position direct KO");
    }}
    static void froslass(){for(String id:List.of("sv4_en_003","sv4_en_217"))for(boolean bench:List.of(false,true))for(boolean heads:List.of(false,true)){
        var m=game(id,2);WillCoinMatchTest.play(m,A);UUID host=bench?m.benchPokemonView(A).getFirst().pokemonId():m.activePokemonView(A).pokemonId();int prizes=m.privateView(B).prizes().size(),n=m.randomTranscript().size();
        m.knockOutPokemonByEffect(A,A,host);if(!bench)WillCoinMatchTest.choose(m,A,heads);NonPokemonBatch103Test.settle(m);
        check(m.randomTranscript().size()==n+(bench?0:1)&&m.privateView(B).prizes().size()==prizes-(!bench&&heads?1:2),"Froslass must work for any Active KO cause, never Bench KO");
    }}
    static void checkup(){var m=game("sv2_en_126",1);WillCoinMatchTest.play(m,A);UUID host=m.activePokemonView(A).pokemonId();
        var board=EventTargetBindingTest.field(m);int hp=m.activePokemonView(A).hitPoints();board.placeDamageCounters(A,host,(hp-10)/10);m.applySpecialConditionByEffect(A,A,SpecialCondition.POISONED);int n=m.randomTranscript().size();m.endTurn(A);
        check(m.randomTranscript().size()==n+1&&!m.hasPendingEffectChoice(),"Checkup Glimmora coin incorrectly used Will or failed to flip");int before=6;boolean heads=m.randomTranscript().get(n).result()==0;NonPokemonBatch103Test.settle(m);
        check(m.privateView(B).prizes().size()==before-(heads?0:1),"Checkup Glimmora prize prevention wrong");
    }
}
