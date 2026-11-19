import com.google.gson.JsonParser;
import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch92Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final List<String> IDS=List.of("sm5_en_129");
    static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    static UUID other(UUID p){return NonPokemonBatch67Test.other(p);}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch28Test.hand(m,p,id);}
    static List<UUID> four(ServerMatch m,UUID p){return m.privateView(p).hand().stream().filter(c->c.definitionId().equals("trainer")).limit(4).map(CardInstance::instanceId).toList();}
    static ServerMatch game(UUID actor,boolean watch){
        var actual=NonPokemonBatch2Test.DATA.trainer(IDS.getFirst()).orElseThrow();var c=new GeneratedRuleBatch28Test.Config();
        c.trainer=new TrainerProfile(actual.category(),actual.effect(),actual.effectProgram(),actual.automaticEffects(),actual.endsTurn(),actual.mayPlayFirstTurn(),actual.inPlayPokemon(),actual.activation(),actual.toolActivation(),new PairedTrainerPlay(4,Set.of("trainer"),actual.pairedPlay().program()));
        c.attack=GeneratedRuleBatch8Test.hit(300,false);c.defenderAttack=c.attack;
        if(watch)c.ownAbility=BundledCombatData.ability(JsonParser.parseString("{\"id\":\"clover-watch\",\"nameZh\":\"Item watch\",\"kind\":\"TRIGGERED\",\"effectSupport\":\"SUPPORTED\",\"eventPolicy\":{\"trigger\":\"ON_CARD_PLAYED\",\"player\":\"SELF\",\"card\":\"ITEM\",\"optional\":false,\"usage\":\"NONE\"},\"triggers\":[{\"trigger\":\"ON_CARD_PLAYED\",\"effects\":[{\"type\":\"draw\",\"amount\":1}]}]}").getAsJsonObject(),EffectRegistry.standard());
        var m=GeneratedRuleBatch28Test.fixture(c);if(actor.equals(B))m.endTurn(A);
        while(four(m,actor).size()<4)m.drawCardsByEffect(actor,actor,1);
        if(watch)m.movePokemonByEffect(actor,actor,m.benchPokemonView(actor).getFirst().pokemonId(),LeavePlayDestination.HAND);
        return m;
    }
    static ServerMatch aiGame(String id){return game(A,false);}
    static void take(ServerMatch m,UUID p){var q=m.prizeTakePrompt(p);m.takeEffectPrizeAt(p,q.resolutionId(),q.step(),q.selection(),0);}
    public static void main(String[] args){single();combined();atomic();completion();ai();schema();System.out.println("NonPokemonBatch92Test passed: one private unmoved top card or exactly four for one Prize, both owners, empty deck, one Item event, atomic/stale choices, concession/last-Prize cleanup and combination preservation/win priority across all AI difficulties");}
    static void single(){for(UUID p:List.of(A,B))for(int remaining:List.of(0,1,3)){
        var m=game(p,false);m.drawCardsByEffect(p,p,m.privateView(p).deck().size()-remaining);var source=hand(m,p,"trainer");var before=m.privateView(p);var rng=m.randomTranscript();
        check(m.canPlayTrainer(p,source.instanceId(),null)==(remaining>0),"Single Clover empty deck gate");
        if(remaining==0){GeneratedRuleBatch8Test.reject(()->m.playTrainer(p,source.instanceId(),null));check(before.equals(m.privateView(p)),"Illegal peek mutated zones");continue;}
        m.playTrainer(p,source.instanceId(),null);var q=m.effectCardPrompt(p);check(q.candidates().equals(List.of(before.deck().getLast()))&&m.clientView(other(p)).effectSelection()==null&&m.effectCardReveal()==null,"Peek exposed hidden cards or inspected wrong end");
        GeneratedRuleBatch8Test.reject(()->m.submitEffectCards(other(p),q.resolutionId(),q.step(),List.of(before.deck().getLast().instanceId())));
        m.submitEffectCards(p,q.resolutionId(),q.step(),List.of(before.deck().getLast().instanceId()));
        GeneratedRuleBatch8Test.reject(()->m.submitEffectCards(p,q.resolutionId(),q.step(),List.of(before.deck().getLast().instanceId())));
        check(m.privateView(p).deck().equals(before.deck())&&m.privateView(p).prizes().equals(before.prizes())&&m.privateView(p).hand().size()==before.hand().size()-1&&m.privateView(p).discard().contains(source)&&rng.equals(m.randomTranscript()),"Single Clover moved top, took Prize or shuffled");
    }}
    static void combined(){for(UUID p:List.of(A,B))for(boolean empty:List.of(false,true)){
        var m=game(p,false);if(empty)m.drawCardsByEffect(p,p,m.privateView(p).deck().size());var ids=four(m,p);var before=m.privateView(p);var opponent=m.clientView(other(p)).players();var rng=m.randomTranscript();
        check(m.canPlayTrainerPair(p,ids),"Four Clover incorrectly require deck");m.playTrainerPair(p,ids);var q=m.prizeTakePrompt(p);
        check(q.remaining()==1&&q.slotCount()==6&&m.clientView(other(p)).resolvingTrainerCards().size()==4&&m.privateView(p).discard().stream().noneMatch(c->ids.contains(c.instanceId())),"Four copies not held, or multiple Prize awards");
        var waiting=m.clientView(p);GeneratedRuleBatch8Test.reject(()->m.takeEffectPrizeAt(other(p),q.resolutionId(),q.step(),q.selection(),0));GeneratedRuleBatch8Test.reject(()->m.takeEffectPrizeAt(p,q.resolutionId(),q.step(),q.selection(),6));GeneratedRuleBatch8Test.reject(()->m.takeEffectPrizeAt(p,q.resolutionId(),q.step(),q.selection()+1,0));check(waiting.equals(m.clientView(p))&&rng.equals(m.randomTranscript()),"Invalid Prize intent mutated/RNG");
        take(m,p);GeneratedRuleBatch8Test.reject(()->m.takeEffectPrizeAt(p,q.resolutionId(),q.step(),q.selection(),0));
        check(m.privateView(p).prizes().size()==5&&m.privateView(p).hand().size()==before.hand().size()-3&&m.privateView(p).hand().contains(before.prizes().getFirst())&&m.privateView(p).discard().stream().filter(c->ids.contains(c.instanceId())).count()==4&&m.clientView(p).resolvingTrainerCards().isEmpty(),"Four-card Prize or disposal count");
        check(m.privateView(p).deck().equals(before.deck())&&rng.equals(m.randomTranscript())&&m.activePokemonView(other(p)).damage()==0&&m.benchPokemonView(other(p)).size()==1,"Four-card mode ran peek, shuffled or knocked out a Pokemon");
    }}
    static void atomic(){for(UUID p:List.of(A,B)){
        var m=game(p,false);var ids=four(m,p);var before=m.clientView(p);var rng=m.randomTranscript();var five=new ArrayList<>(ids);five.add(hand(m,p,"energy").instanceId());
        for(var bad:List.of(ids.subList(0,1),ids.subList(0,2),ids.subList(0,3),five,List.of(ids.get(0),ids.get(1),ids.get(2),ids.get(2)),List.of(ids.get(0),ids.get(1),ids.get(2),hand(m,other(p),"trainer").instanceId()),List.of(ids.get(0),ids.get(1),ids.get(2),hand(m,p,"energy").instanceId()))){check(!m.canPlayTrainerPair(p,bad),"Invalid four-card declaration accepted");GeneratedRuleBatch8Test.reject(()->m.playTrainerPair(p,bad));}
        check(before.equals(m.clientView(p))&&rng.equals(m.randomTranscript()),"Rejected combination changed zones/RNG");
        var session=MatchSession.multiplayer(m,A,B);var view=session.view(p,p);var choices=view.options().stream().filter(o->o.type()==ActionType.TRAINER&&o.cardKeys().size()==4).toList();check(choices.size()==1,"Missing/duplicate four-card action");var option=choices.getFirst();GeneratedRuleBatch8Test.reject(()->session.submit(other(p),p,view.revision(),option.index()));session.submit(p,p,view.revision(),option.index());GeneratedRuleBatch8Test.reject(()->session.submit(p,p,view.revision(),option.index()));take(m,p);
    }}
    static void lastPrize(ServerMatch m,UUID p){UUID opponent=other(p);m.endTurn(p);while(m.benchPokemonView(opponent).size()<5)m.playBasicPokemonToBench(opponent,hand(m,opponent,opponent.equals(A)?"attacker":"defender").instanceId());m.endTurn(opponent);for(int i=0;i<5;i++)NonPokemonBatch67Test.prize(m,p);check(m.privateView(p).prizes().size()==1,"Last Prize fixture");}
    static void completion(){
        var watched=game(A,true);int held=watched.privateView(A).hand().size();watched.playTrainerPair(A,four(watched,A));take(watched,A);NonPokemonBatch67Test.settle(watched);check(watched.privateView(A).hand().size()==held-2&&watched.replayTranscript().stream().filter(e->e.operation().contains("结算触发特性：clover-watch")).count()==1,"Four Clover triggered Item listener other than once");
        for(UUID p:List.of(A,B))for(boolean concede:List.of(false,true)){var m=game(p,false);if(!concede)lastPrize(m,p);var ids=four(m,p);m.playTrainerPair(p,ids);if(concede)m.concede(p);else take(m,p);check(m.phase()==MatchPhase.FINISHED&&m.clientView(p).resolvingTrainerCards().isEmpty()&&!m.hasPendingEffectChoice()&&m.privateView(p).discard().stream().filter(c->ids.contains(c.instanceId())).count()==4,"Finished match lost resolving Clover copies");if(!concede)check(m.privateView(p).prizes().isEmpty()&&m.activePokemonView(other(p))!=null,"Final Prize did not finish independently of KO");}
    }
    static void ai(){for(var difficulty:AiDifficulty.values()){
        for(boolean combined:List.of(false,true)){var m=game(A,false);if(combined)m.playTrainerPair(A,four(m,A));else m.playTrainer(A,hand(m,A,"trainer").instanceId(),null);AiCardOperationTest.settle(MatchSession.multiplayer(m,A,B),difficulty);check(!m.hasPendingEffectChoice()&&m.phase()==MatchPhase.TURN,"AI stuck in Clover mode: "+difficulty);}
        for(int pieces=1;pieces<=3;pieces++){var m=game(A,false);var extra=m.privateView(A).hand().stream().filter(c->c.definitionId().equals("trainer")).skip(pieces).toList();PairedTrainerPlayTest.zone(m,A,"hand").removeAll(extra);PairedTrainerPlayTest.zone(m,A,"discard").addAll(extra);var session=MatchSession.multiplayer(m,A,B);var v=AiPracticeScenarios.view(session,A);var decision=AiPolicy.choose(v,92,Set.of(),difficulty).orElseThrow();var selected=v.snapshot().options().get(decision.option());check(selected.type()!=ActionType.TRAINER,"AI spent incomplete Prize combination: "+difficulty+" "+pieces);}
        var m=game(A,false);lastPrize(m,A);var session=MatchSession.multiplayer(m,A,B);var v=AiPracticeScenarios.view(session,A);var d=AiPolicy.choose(v,92,Set.of(),difficulty).orElseThrow();var action=v.snapshot().options().get(d.option());check(action.type()==ActionType.TRAINER&&action.cardKeys().size()==4,"AI ignored guaranteed final Prize: "+difficulty+" "+action);session.submit(A,A,v.snapshot().revision(),action.index());AiCardOperationTest.settle(session,difficulty);check(m.phase()==MatchPhase.FINISHED&&m.privateView(A).prizes().isEmpty(),"AI failed immediate Clover win");
        var p=NonPokemonBatch2Test.DATA.trainer(IDS.getFirst()).orElseThrow();check(!AiEffects.trainer(p,1).has(AiEffects.Role.PRIZE)&&AiEffects.trainer(p,4).has(AiEffects.Role.PRIZE),"AI mixed single/four outcomes");
    }}
    static void schema(){for(String count:List.of("1","3","5","4.0","\"4\"")){var j=JsonParser.parseString("{\"count\":"+count+",\"definitionIds\":[\"one\"],\"effects\":[{\"type\":\"take_prize_cards\",\"target\":\"SELF\",\"amount\":1,\"bind\":\"prize\"}]}").getAsJsonObject();GeneratedRuleBatch8Test.reject(()->PairedTrainerPlay.parse(j));}GeneratedRuleBatch8Test.reject(()->new PairedTrainerPlay(4,Set.of("one")));}
}
