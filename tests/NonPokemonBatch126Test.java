import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch126Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final List<String> IDS=List.of("swsh3_en_158");
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static UUID other(UUID p){return p.equals(A)?B:A;}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return DynamicPassiveModifierTest.hand(m,p,id);}
    static TrainerProfile profile(){return NonPokemonBatch2Test.DATA.trainer(IDS.getFirst()).orElseThrow();}
    static void settle(ServerMatch m){NonPokemonBatch103Test.settle(m);}
    static void hurt(ServerMatch m,UUID p,UUID host,int damage){NonPokemonBatch75Test.hurt(m,p,host,damage);}
    static ServerMatch game(AttackDefinition attack,int prizes){var c=new GeneratedRuleBatch28Test.Config();c.trainer=profile();c.attack=attack;c.defenderAttack=attack;c.ownPrizes=c.defenderPrizes=prizes;return GeneratedRuleBatch28Test.fixture(c);}
    static ServerMatch aiGame(String id){return game(GeneratedRuleBatch8Test.hit(300,false),1);}
    static void attach(ServerMatch m,UUID p,UUID host){m.playTrainer(p,hand(m,p,"trainer").instanceId(),host);}
    static List<CardInstance> zone(ServerMatch m,UUID p,String zone){return PairedTrainerPlayTest.zone(m,p,zone);}
    static void prize(ServerMatch m,UUID p,CardInstance card){zone(m,p,"hand").remove(card);zone(m,p,"hand").add(zone(m,p,"prizes").set(0,card));}
    static void choose(ServerMatch m,UUID p,int n){var q=m.effectChoicePrompt(p);m.chooseEffectOption(p,q.resolutionId(),q.step(),n);}
    public static void main(String[] args){scope();causes();disabled();afterAttack();mixed();luckyBonus();prism();lastPrizes();ai();schema();System.out.println("NonPokemonBatch126Test passed: Billowing Smoke both owners/positions and multi-Prize KOs, attack damage only, live disable and post-attack removal, mixed simultaneous Prize destinations with private authenticated ordering, Lucky Bonus prevention, Prism replacement, last-Prize wins and four AI difficulties");}
    static void scope(){for(UUID owner:List.of(A,B))for(boolean onBench:List.of(false,true))for(int count:List.of(1,3)){
        var m=game(GeneratedRuleBatch8Test.hit(1000,onBench),count);if(owner.equals(B))m.endTurn(A);UUID host=onBench?bench(m,owner):active(m,owner);attach(m,owner,host);m.endTurn(owner);UUID taker=other(owner);
        var cards=List.copyOf(m.privateView(taker).prizes().subList(0,count));int hand=m.privateView(taker).hand().size();m.attack(taker,"hit");check(m.phase()==MatchPhase.PRIZE_SELECTION,"Smoke KO missing Prize phase");
        for(var card:cards){m.takePrize(taker,card.instanceId());check(m.privateView(taker).discard().contains(card)&&!m.privateView(taker).hand().contains(card),"Smoke prize entered wrong zone");}
        check(m.privateView(taker).prizes().size()==6-count&&m.privateView(taker).hand().size()==hand,"Smoke changed Prize count or added cards to hand");settle(m);
    }}
    static void causes(){for(String cause:List.of("counter","poison","direct","self")){
        var attack=cause.equals("counter")?DeckDiscardTriggeredAbilityTest.attack(0,"{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":10}"):
            cause.equals("self")?DeckDiscardTriggeredAbilityTest.attack(0,"{\"type\":\"self_damage\",\"target\":\"SELF\",\"amount\":100}"):GeneratedRuleBatch8Test.hit(100,false);
        var m=game(attack,1);attach(m,A,active(m,A));hurt(m,A,active(m,A),cause.equals("poison")?990:900);var card=m.privateView(B).prizes().getFirst();
        switch(cause){case "counter"->{m.endTurn(A);m.attack(B,"hit");}case "poison"->{m.applySpecialConditionByEffect(A,A,SpecialCondition.POISONED);m.endTurn(A);}case "direct"->m.knockOutPokemonByEffect(A,A,active(m,A));case "self"->m.attack(A,"hit");}
        check(m.phase()==MatchPhase.PRIZE_SELECTION,"Cause fixture "+cause);m.takePrize(B,card.instanceId());check(m.privateView(B).hand().contains(card),"Smoke applied to non-opposing-attack-damage "+cause);settle(m);
    }}
    static void disabled(){for(boolean disabled:List.of(false,true)){
        var m=game(GeneratedRuleBatch8Test.hit(1000,false),1);attach(m,A,active(m,A));if(disabled)m.disableToolEffectsByEffect(A,A,active(m,A));m.endTurn(A);var card=m.privateView(B).prizes().getFirst();m.attack(B,"hit");m.takePrize(B,card.instanceId());check(m.privateView(B).hand().contains(card)==disabled,"Disabled Smoke kept its rule");settle(m);
    }}
    static void afterAttack(){for(boolean remove:List.of(false,true)){
        var attack=BeforeDamageAttackTest.attack("","",remove?"{\"type\":\"discard_tool\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":1,\"minimum\":1}":"");var m=game(attack,1);m.endTurn(A);attach(m,B,active(m,B));hurt(m,B,active(m,B),960);m.endTurn(B);var card=m.privateView(A).prizes().getFirst();m.attack(A,"hit");
        while(m.hasPendingEffectChoice()){var q=m.effectCardPrompt(A);m.submitEffectCards(A,q.resolutionId(),q.step(),q.candidates().stream().map(CardInstance::instanceId).limit(q.maximum()).toList());}
        check(m.phase()==MatchPhase.PRIZE_SELECTION,"Post-attack fixture");m.takePrize(A,card.instanceId());check(m.privateView(A).hand().contains(card)==remove,"Smoke captured before attack text finished");settle(m);
    }}
    static ServerMatch mixedGame(){var attack=DeckDiscardTriggeredAbilityTest.attack(1000,"{\"type\":\"bench_damage\",\"target\":\"OPPONENT_BENCH\",\"amount\":100}");var m=game(attack,1);m.endTurn(A);attach(m,B,active(m,B));hurt(m,B,bench(m,B),900);m.playBasicPokemonToBench(B,hand(m,B,"defender").instanceId());m.endTurn(B);m.attack(A,"hit");return m;}
    static void mixed(){for(int option:List.of(0,1)){
        var m=mixedGame();check(m.hasPendingEffectChoice()&&m.pendingChoicePlayer().equals(A),"Mixed KO lost destination order");var q=m.effectChoicePrompt(A);var before=m.clientView(A);var privateBefore=m.privateView(A);
        reject(()->m.effectChoicePrompt(B));reject(()->m.chooseEffectOption(B,q.resolutionId(),q.step(),option));reject(()->m.chooseEffectOption(A,UUID.randomUUID(),q.step(),option));reject(()->m.chooseEffectOption(A,q.resolutionId(),q.step(),2));reject(()->m.takePrizeAt(A,0));check(before.equals(m.clientView(A))&&privateBefore.equals(m.privateView(A)),"Invalid mixed Prize operation mutated match");
        var s=MatchSession.multiplayer(m,A,B);check(s.view(A,A).options().stream().anyMatch(o->o.labelZh().contains("正常加入手牌")),"Prize order unavailable in session");check(s.view(B,B).options().stream().noneMatch(o->o.labelZh().contains("先拿取")),"Opponent saw actionable Prize order");
        var first=m.privateView(A).prizes().getFirst();var second=m.privateView(A).prizes().get(1);choose(m,A,option);m.takePrize(A,first.instanceId());reject(()->m.chooseEffectOption(A,q.resolutionId(),q.step(),option));m.takePrize(A,second.instanceId());
        check(m.privateView(A).hand().contains(option==0?first:second)&&m.privateView(A).discard().contains(option==1?first:second),"Smoke applied to another simultaneous KO's prize");check(m.privateView(A).prizes().size()==4,"Mixed KO count changed");settle(m);
        var abandoned=mixedGame();abandoned.concede(B);check(!abandoned.hasPendingEffectChoice(),"Concession retained Prize-order prompt");
    }}
    static void luckyBonus(){var c=new GeneratedRuleBatch28Test.Config();c.trainer=profile();c.ownAbility=NonPokemonBatch2Test.DATA.pokemon("sv3-5_en_113").orElseThrow().ability("lucky-bonus").orElseThrow();c.attack=GeneratedRuleBatch8Test.hit(1000,false);var m=GeneratedRuleBatch28Test.fixture(c);var card=hand(m,A,"attacker");prize(m,A,card);m.endTurn(A);attach(m,B,active(m,B));m.endTurn(B);m.attack(A,"hit");m.takePrize(A,card.instanceId());check(!m.hasPendingLuckyBonus(A)&&m.privateView(A).discard().contains(card),"Smoke allowed Prize pre-hand Ability");settle(m);}
    static void prism(){var m=NonPokemonBatch108Test.game(EnergyType.DARKNESS,EnergyType.DARKNESS,profile(),DynamicPassiveModifierTest.plainStadium(),GeneratedRuleBatch8Test.hit(500,false));var card=hand(m,A,"trainer");prize(m,A,card);m.endTurn(A);m.playTrainer(B,hand(m,B,"tool").instanceId(),active(m,B));m.endTurn(B);m.attack(A,"hit");m.takePrize(A,card.instanceId());check(m.privateView(A).lostZone().contains(card)&&!m.privateView(A).discard().contains(card),"Prism printed discard replacement lost");settle(m);}
    static void lastPrizes(){for(boolean mixed:List.of(false,true)){
        var m=mixed?mixedGame():game(GeneratedRuleBatch8Test.hit(1000,false),3);if(!mixed){m.endTurn(A);attach(m,B,active(m,B));m.endTurn(B);m.attack(A,"hit");}
        var extras=new ArrayList<>(zone(m,A,"prizes").subList(1,6));zone(m,A,"prizes").removeAll(extras);zone(m,A,"deck").addAll(extras);var last=m.privateView(A).prizes().getFirst();if(mixed)choose(m,A,1);m.takePrize(A,last.instanceId());check(m.phase()==MatchPhase.FINISHED&&m.privateView(A).prizes().isEmpty()&&m.privateView(A).discard().contains(last),"Discarded last Prize failed to win or left stale claims");check(!m.hasPendingEffectChoice(),"Finished match retained Prize order");
    }}
    static void ai(){for(var level:AiDifficulty.values())for(boolean mixed:List.of(false,true)){
        var m=mixed?mixedGame():aiGame(IDS.getFirst());if(!mixed)attach(m,m.currentPlayer(),active(m,m.currentPlayer()));var s=MatchSession.multiplayer(m,A,B);AiCardOperationTest.settle(s,level);AiCardOperationTest.finish(s,level);check(m.phase()==MatchPhase.FINISHED,"Smoke AI stalled "+level);
    }}
    static void schema(){var e=profile().ongoingProgram().triggers().getFirst().effects().getFirst();for(var source:List.of(PassiveModifierProgram.Source.ABILITY,PassiveModifierProgram.Source.ENERGY,PassiveModifierProgram.Source.STADIUM))reject(()->PassiveModifierProgram.validate(profile().ongoingProgram(),source));reject(()->PassiveRuleProgram.validate(new EffectDefinition(e.type(),EffectTarget.OPPONENT_ACTIVE,Map.of(),List.of(),List.of())));}
}
