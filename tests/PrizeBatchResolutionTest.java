import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Simultaneous Prize cards have one private reveal window before optional pre-hand reactions. */
public final class PrizeBatchResolutionTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static CardInstance hand(ServerMatch m,String id){return DynamicPassiveModifierTest.hand(m,A,id);}
    static List<CardInstance> zone(ServerMatch m,String z){return PairedTrainerPlayTest.zone(m,A,z);}
    static ServerMatch game(){var c=new GeneratedRuleBatch28Test.Config();var lucky=NonPokemonBatch2Test.DATA.pokemon("sv3-5_en_113").orElseThrow().ability("lucky-bonus").orElseThrow();c.ownAbility=c.source=lucky;c.defenderPrizes=2;c.attack=GeneratedRuleBatch8Test.hit(1000,false);return GeneratedRuleBatch28Test.fixture(c);}
    static CardInstance prize(ServerMatch m,int slot,String id){var card=hand(m,id);zone(m,"hand").remove(card);zone(m,"hand").add(zone(m,"prizes").set(slot,card));return card;}
    static void selectOrder(ServerMatch m,int n){var q=m.effectChoicePrompt(A);m.chooseEffectOption(A,q.resolutionId(),q.step(),n);}
    public static void main(String[] args){staging();reactionOrder();bonusResumption();checkup();lastPrize();concession();ai();System.out.println("PrizeBatchResolutionTest passed: simultaneous private selection before pre-hand effects, both reaction orders, nested bonus prize resumption, Checkup/final-Prize exclusions, stale/foreign rejection and concession card conservation, all four AI levels");}
    static void staging(){for(boolean luckyFirst:List.of(false,true)){
        var m=game();var lucky=prize(m,luckyFirst?0:1,"attacker");var ordinary=prize(m,luckyFirst?1:0,"energy");int before=m.privateView(A).hand().size();m.attack(A,"hit");m.takePrizeAt(A,0);
        check(!m.hasPendingLuckyBonus(A)&&!m.hasPendingEffectChoice()&&m.privateView(A).hand().size()==before,"First of multiple prizes was shown or reacted early");check(m.pendingChoicePlayer().equals(A),"Prize selection lost owner");m.takePrizeAt(A,0);
        check(m.hasPendingLuckyBonus(A)&&m.privateView(A).hand().contains(ordinary)&&!m.privateView(A).hand().contains(lucky),"Whole batch failed pre-hand window");m.decideLuckyBonus(A,false);check(m.privateView(A).hand().contains(lucky),"Declined prize not added to hand");NonPokemonBatch103Test.settle(m);
    }}
    static void reactionOrder(){for(int index:List.of(0,1)){
        var m=game();var first=prize(m,0,"attacker");var second=prize(m,1,"source");m.attack(A,"hit");m.takePrizeAt(A,0);m.takePrizeAt(A,0);check(m.hasPendingEffectChoice()&&!m.hasPendingLuckyBonus(A),"Two reactions lack owner ordering");var q=m.effectChoicePrompt(A);check(q.optionsZh().size()==2,"Wrong Prize reaction count");var before=m.privateView(A);reject(()->m.effectChoicePrompt(B));reject(()->m.chooseEffectOption(B,q.resolutionId(),q.step(),index));reject(()->m.chooseEffectOption(A,q.resolutionId(),q.step(),2));reject(()->m.takePrizeAt(A,0));check(before.equals(m.privateView(A)),"Invalid Prize order mutated cards");
        selectOrder(m,index);reject(()->m.chooseEffectOption(A,q.resolutionId(),q.step(),index));m.decideLuckyBonus(A,false);check(m.privateView(A).hand().contains(index==0?first:second)&&!m.privateView(A).hand().contains(index==0?second:first),"Chosen Prize reaction was not first");check(m.hasPendingLuckyBonus(A),"Second reaction was lost");m.decideLuckyBonus(A,false);check(m.privateView(A).hand().containsAll(List.of(first,second)),"Remaining Prize not restored");NonPokemonBatch103Test.settle(m);
    }}
    static void bonusResumption(){var outcomes=new HashSet<Boolean>();for(int shuffles=0;shuffles<16&&outcomes.size()<2;shuffles++){
        var m=game();prize(m,0,"attacker");var second=prize(m,1,"source");var extra=prize(m,2,"energy");for(int i=0;i<shuffles;i++)m.flipCoinsByEffect(A,1);m.attack(A,"hit");m.takePrizeAt(A,0);m.takePrizeAt(A,0);selectOrder(m,0);m.decideLuckyBonus(A,true);
        boolean bonus=!m.hasPendingLuckyBonus(A);outcomes.add(bonus);if(bonus){check(!m.hasPendingEffectChoice()&&m.phase()==MatchPhase.PRIZE_SELECTION,"Bonus Prize did not precede remaining old reaction");m.takePrizeAt(A,0);check(m.privateView(A).hand().contains(extra),"Bonus Prize failed to enter hand");}
        check(m.hasPendingLuckyBonus(A)&&!m.privateView(A).hand().contains(second),"Bonus lost the original second Prize reaction");m.decideLuckyBonus(A,false);check(m.privateView(A).hand().contains(second),"Original batch failed to resume");NonPokemonBatch103Test.settle(m);
    }check(outcomes.size()==2,"Lucky Bonus test did not cover both coin sides");}
    static void checkup(){var m=game();var first=prize(m,0,"attacker");var second=prize(m,1,"source");NonPokemonBatch75Test.hurt(m,B,m.activePokemonView(B).pokemonId(),990);m.applySpecialConditionByEffect(A,B,SpecialCondition.POISONED);m.endTurn(A);check(m.phase()==MatchPhase.PRIZE_SELECTION,"Checkup fixture");m.takePrizeAt(A,0);m.takePrizeAt(A,0);check(!m.hasPendingLuckyBonus(A)&&!m.hasPendingEffectChoice()&&m.privateView(A).hand().containsAll(List.of(first,second)),"Checkup treated as current player's turn for pre-hand Ability");NonPokemonBatch103Test.settle(m);}
    static void lastPrize(){var m=game();var first=prize(m,0,"attacker");var second=prize(m,1,"source");var extras=new ArrayList<>(zone(m,"prizes").subList(2,6));zone(m,"prizes").removeAll(extras);zone(m,"deck").addAll(extras);m.attack(A,"hit");m.takePrizeAt(A,0);m.takePrizeAt(A,0);check(m.phase()==MatchPhase.FINISHED&&!m.hasPendingLuckyBonus(A)&&!m.hasPendingEffectChoice(),"Final Prize opened post-win reactions");check(m.privateView(A).hand().containsAll(List.of(first,second)),"Final-prize cards vanished");}
    static void concession(){for(int step:List.of(0,1,2)){
        var m=game();var first=prize(m,0,"attacker");var second=prize(m,1,"source");m.attack(A,"hit");m.takePrizeAt(A,0);if(step>0)m.takePrizeAt(A,0);if(step>1)selectOrder(m,0);m.concede(B);check(!m.hasPendingEffectChoice()&&!m.hasPendingLuckyBonus(A),"Concession stranded reaction window");check(m.privateView(A).hand().contains(first)&&(step==0?m.privateView(A).prizes():m.privateView(A).hand()).contains(second),"Concession lost unclaimed or held Prize");for(var card:List.of(first,second)){long count=List.of(m.privateView(A).hand(),m.privateView(A).prizes(),m.privateView(A).discard()).stream().flatMap(Collection::stream).filter(c->c.instanceId().equals(card.instanceId())).count();check(count==1,"Concession duplicated Prize");}
    }}
    static void ai(){for(var level:AiDifficulty.values()){var m=game();prize(m,0,"attacker");prize(m,1,"source");m.attack(A,"hit");var s=MatchSession.multiplayer(m,A,B);AiCardOperationTest.settle(s,level);AiCardOperationTest.finish(s,level);check(m.phase()==MatchPhase.FINISHED,"AI stalled in Prize window "+level);}}
}
