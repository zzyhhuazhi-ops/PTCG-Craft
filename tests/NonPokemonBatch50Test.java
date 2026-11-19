import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch50Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final String ICE="swsh7_en_150";
    static final List<String> IDS=List.of("sm8_en_179",ICE);
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static ServerMatch aiGame(String id){var m=NonPokemonBatch37Test.game(id);if(id.equals(ICE))m.placeDamageCountersByEffect(A,A,m.activePokemonView(A).pokemonId(),4);return m;}
    static List<RandomAuditEvent> coins(ServerMatch m,int before){return m.randomTranscript().subList(before,m.randomTranscript().size()).stream().filter(e->e.operation().startsWith("effect-coin:")).toList();}
    public static void main(String[] args){kahili();ice();blocked();copied();System.out.println("NonPokemonBatch50Test passed: both coin results/owners, return of the exact played card, consumed Supporter allowance, deck-empty rejection and partial draw, actual Active healing, no-heal rejection, copied Supporter does not return another physical card");}
    static void kahili(){for(String id:List.of(IDS.getFirst()))for(UUID owner:List.of(A,B))for(boolean shortDeck:List.of(false,true)){
        var outcomes=new HashSet<Integer>();for(int shift=0;shift<24&&outcomes.size()<2;shift++){
            var m=aiGame(id);if(owner.equals(B))m.endTurn(A);if(shortDeck)m.drawCardsByEffect(owner,owner,m.privateView(owner).deck().size()-1);if(shift>0)m.flipCoinsByEffect(owner,shift);
            var source=NonPokemonBatch37Test.card(m,owner,"trainer");int hand=m.privateView(owner).hand().size(),before=m.randomTranscript().size();m.playTrainer(owner,source.instanceId(),null);NonPokemonBatch2Test.settle(m);var flips=coins(m,before);check(flips.size()==1,"Kahili wrong coin count");int result=flips.getFirst().result();outcomes.add(result);boolean heads=result==0;
            check(m.privateView(owner).hand().size()==hand-1+(shortDeck?1:2)+(heads?1:0),"Kahili draw/return count");check(m.privateView(owner).hand().contains(source)==heads&&m.privateView(owner).discard().contains(source)!=heads,"Kahili returned wrong physical card");
            if(heads)check(!m.canPlayTrainer(owner,source.instanceId(),null),"Returned Supporter bypassed once-per-turn use");
        }check(outcomes.size()==2,"Missing Kahili coin branch");
        var m=aiGame(id);if(owner.equals(B))m.endTurn(A);m.drawCardsByEffect(owner,owner,m.privateView(owner).deck().size());var source=NonPokemonBatch37Test.card(m,owner,"trainer");int random=m.randomTranscript().size();check(!m.canPlayTrainer(owner,source.instanceId(),null),"Kahili playable with empty deck");GeneratedRuleBatch8Test.reject(()->m.playTrainer(owner,source.instanceId(),null));check(m.privateView(owner).hand().contains(source)&&m.randomTranscript().size()==random,"Empty-deck rejection consumed card/randomness");
    }}
    static void ice(){for(UUID owner:List.of(A,B))for(int damage:List.of(1,2,5)){
        var outcomes=new HashSet<Integer>();for(int shift=0;shift<24&&outcomes.size()<2;shift++){
            var m=NonPokemonBatch37Test.game(ICE);if(owner.equals(B))m.endTurn(A);m.placeDamageCountersByEffect(owner,owner,m.activePokemonView(owner).pokemonId(),damage);m.placeDamageCountersByEffect(owner,owner,m.benchPokemonView(owner).getFirst().pokemonId(),8);if(shift>0)m.flipCoinsByEffect(owner,shift);
            var source=NonPokemonBatch37Test.card(m,owner,"trainer");int before=m.randomTranscript().size();m.playTrainer(owner,source.instanceId(),null);NonPokemonBatch2Test.settle(m);var flips=coins(m,before);check(flips.size()==1,"Ice Pop wrong coin count");int result=flips.getFirst().result();outcomes.add(result);boolean heads=result==0;
            check(m.activePokemonView(owner).damage()==Math.max(0,damage*10-20)&&m.benchPokemonView(owner).getFirst().damage()==80,"Ice Pop healed wrong amount/position");check(m.privateView(owner).hand().contains(source)==heads&&m.privateView(owner).discard().contains(source)!=heads,"Ice Pop return/discard wrong");
        }check(outcomes.size()==2,"Missing Ice Pop coin branch");
    }}
    static void blocked(){for(boolean forbidden:List.of(false,true)){
        var stadium=NonPokemonBatch2Test.DATA.trainer("swsh6_en_135").orElseThrow();var item=NonPokemonBatch2Test.DATA.trainer(ICE).orElseThrow();
        var m=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),stadium,EnergyProfile.fixed(EnergyType.COLORLESS,2),DynamicPassiveModifierTest.attack(40,false,false),List.of(),0,item);UUID owner=DynamicPassiveModifierTest.A;
        if(forbidden){m.placeDamageCountersByEffect(owner,owner,m.activePokemonView(owner).pokemonId(),4);m.playTrainer(owner,DynamicPassiveModifierTest.hand(m,owner,"stadium").instanceId(),null);}else m.placeDamageCountersByEffect(owner,owner,m.benchPokemonView(owner).getFirst().pokemonId(),4);
        var source=DynamicPassiveModifierTest.hand(m,owner,"item");int random=m.randomTranscript().size();check(!m.canPlayTrainer(owner,source.instanceId(),null),"Ice Pop playable with no effective Active healing");GeneratedRuleBatch8Test.reject(()->m.playTrainer(owner,source.instanceId(),null));check(m.randomTranscript().size()==random&&m.privateView(owner).hand().contains(source),"No-heal rejection spent card/coin");
    }}
    static void copied(){var outcomes=new HashSet<Integer>();for(int shift=0;shift<24&&outcomes.size()<2;shift++){
        var m=NonPokemonBatch37Test.game("sm9_en_154",List.of(),NonPokemonBatch2Test.DATA.trainer(IDS.getFirst()).orElseThrow());if(shift>0)m.flipCoinsByEffect(A,shift);var source=NonPokemonBatch37Test.card(m,A,"trainer");var copied=NonPokemonBatch37Test.card(m,B,"supporter");int hand=m.privateView(A).hand().size(),before=m.randomTranscript().size();m.playTrainer(A,source.instanceId(),null);NonPokemonBatch37Test.reveal(m);NonPokemonBatch37Test.choose(m,List.of(copied.instanceId()));NonPokemonBatch2Test.settle(m);
        outcomes.add(coins(m,before).getFirst().result());check(m.privateView(A).hand().size()==hand+1&&m.privateView(A).discard().contains(source)&&m.privateView(B).hand().contains(copied),"Copied Kahili returned Sabrina or opponent's physical card");
    }check(outcomes.size()==2,"Missing copied Kahili coin branch");}
}
