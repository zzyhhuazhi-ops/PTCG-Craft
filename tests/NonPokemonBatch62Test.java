import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch62Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final List<String> IDS=List.of("xy4_en_102","sm12_en_197");
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static CardInstance hand(ServerMatch m,UUID p,String id){return DynamicPassiveModifierTest.hand(m,p,id);}
    static ServerMatch aiGame(String id){return NonPokemonBatch61Test.fixture(id);}
    static UUID play(ServerMatch m,UUID p){m.playTrainer(p,hand(m,p,"trainer").instanceId(),null);return m.benchPokemonView(p).getLast().pokemonId();}
    static ServerMatch departureGame(String id){var m=aiGame(id);UUID host=play(m,A);m.switchActiveByEffect(A,A,host);return m;}
    static void depart(ServerMatch m,UUID p,UUID host,boolean doll){if(doll)m.returnPokeDoll(p,host);else m.discardFossil(p,host);}
    public static void main(String[] args){identity();departure();knockout();lastPokemon();ai();System.out.println("NonPokemonBatch62Test passed: both substitute Items, printed status/retreat rules, active-only Doll bottom return versus Robo discard, actual card and attachment conservation without shuffle, wrong-player rejection, last-Pokemon loss, no prizes including extra-prize attacks and non-attack KOs, AI voluntary departure");}
    static void identity(){for(String id:IDS){
        var profile=NonPokemonBatch2Test.DATA.trainer(id).orElseThrow().inPlayPokemon();
        check(profile.hitPoints()==30&&profile.prizeCards()==0&&profile.printedRules().preventsPrizeTake(),"Wrong substitute form");
        check(NonPokemonBatch2Test.DATA.pokemon(id).isEmpty(),"Item became hidden-zone Pokemon");
        GeneratedRuleBatch8Test.reject(()->new CombatCatalog(Map.of("wrong",profile),Map.of()));
        var f=new BattlefieldState(A,B,Map.of("item",profile,"base",FossilFormTest.named(200,null,"Base",null)),new ServerRandom(62));
        var source=new CardInstance(new UUID(0,6201),A,"item");var bench=new CardInstance(new UUID(0,6202),A,"base");
        f.placeInitialActive(A,source);f.placeOnBench(A,bench);f.placeInitialActive(B,new CardInstance(new UUID(0,6203),B,"base"));f.beginTurn(A);
        f.modifyRetreatCostUntilOwnerNextTurn(B,A,source.instanceId(),3);check(f.activeRetreatCost(A)==0,"Retreat increase created absent retreat cost");
        GeneratedRuleBatch8Test.reject(()->f.retreat(A,bench.instanceId(),List.of()));
        for(var condition:SpecialCondition.values()){f.applySpecialCondition(A,condition);check(f.activeView(A).conditions().contains(condition),"Substitute incorrectly immune: "+condition);}
    }}
    static void departure(){for(String id:IDS)for(UUID p:List.of(A,B))for(boolean emptyDeck:List.of(false,true)){
        boolean doll=id.equals(IDS.getLast());var m=aiGame(id);if(p.equals(B))m.endTurn(A);UUID other=p.equals(A)?B:A;
        var source=hand(m,p,"trainer");GeneratedRuleBatch8Test.reject(()->m.playBasicPokemonToBench(p,source.instanceId()));UUID host=play(m,p);
        check(m.canDiscardFossil(p,host)==!doll&&!m.canReturnPokeDoll(p,host),"Bench departure restriction");
        if(doll)GeneratedRuleBatch8Test.reject(()->m.returnPokeDoll(p,host));
        m.switchActiveByEffect(p,p,host);var energy=hand(m,p,"energy");m.attachEnergy(p,energy.instanceId(),host);
        m.disableAbilityEffectsByEffect(p,p,host);m.applySpecialConditionByEffect(p,p,SpecialCondition.ASLEEP);
        check(m.canReturnPokeDoll(p,host)==doll&&m.canDiscardFossil(p,host)==!doll,"Printed departure disabled by sleep/suppression");
        GeneratedRuleBatch8Test.reject(()->depart(m,other,host,doll));
        if(emptyDeck)m.drawCardsByEffect(p,p,m.privateView(p).deck().size());
        var before=List.copyOf(m.privateView(p).deck());int rng=m.randomTranscript().size(),prizes=m.privateView(other).prizes().size();
        depart(m,p,host,doll);NonPokemonBatch2Test.settle(m);
        check(m.currentPlayer().equals(p)&&m.phase()==MatchPhase.TURN&&!m.activePokemonView(p).pokemonId().equals(host),"Departure failed to replace Active on same turn");
        check(m.privateView(p).discard().contains(energy)&&m.privateView(other).prizes().size()==prizes,"Departure lost attachment or awarded prizes");
        var expected=new ArrayList<>(before);if(doll)expected.add(0,source);
        check(m.privateView(p).deck().equals(expected)&&m.randomTranscript().size()==rng,"Doll bottom return shuffled/changed deck order");
        check(m.privateView(p).discard().contains(source)==!doll,"Top Item went to wrong zone");
        GeneratedRuleBatch8Test.reject(()->depart(m,p,host,doll));
    }
        for(UUID p:List.of(A,B)){var m=aiGame(IDS.getFirst());if(p.equals(B))m.endTurn(A);var source=hand(m,p,"trainer");UUID host=play(m,p);m.discardFossil(p,host);check(m.privateView(p).discard().contains(source),"Robo could not leave Bench");}
    }
    static void knockout(){for(String id:IDS)for(UUID p:List.of(A,B))for(String cause:List.of("attack","bonus","bench","counters","poison","effect")){
        var attack=cause.equals("bonus")||cause.equals("bench")?UnrestrictedAttackPrizeTest.attack(false,cause.equals("bench"),""):GeneratedRuleBatch8Test.hit(100,false);
        var m=NonPokemonBatch61Test.fixture(id,attack);if(p.equals(B))m.endTurn(A);UUID other=p.equals(A)?B:A;var source=hand(m,p,"trainer");UUID host=play(m,p);
        if(!cause.equals("bench"))m.switchActiveByEffect(p,p,host);
        int before=m.privateView(other).prizes().size();
        if(cause.equals("counters"))m.placeDamageCountersByEffect(p,p,host,3);
        else if(cause.equals("effect"))m.knockOutPokemonByEffect(p,p,host);
        else if(cause.equals("poison")){m.placeDamageCountersByEffect(p,p,host,2);m.applySpecialConditionByEffect(p,p,SpecialCondition.POISONED);m.endTurn(p);}
        else {m.endTurn(p);m.attack(other,attack.id());}
        NonPokemonBatch2Test.settle(m);check(m.privateView(p).discard().contains(source)&&m.privateView(other).prizes().size()==before,"Substitute awarded Prize: "+id+cause);
    }
        var attack=UnrestrictedAttackPrizeTest.attack(false,false,"");var m=NonPokemonBatch61Test.fixture(IDS.getFirst(),attack);m.placeDamageCountersByEffect(A,B,m.activePokemonView(B).pokemonId(),46);int before=m.privateView(A).prizes().size();m.attack(A,attack.id());NonPokemonBatch2Test.settle(m);check(before-m.privateView(A).prizes().size()==2,"Extra-Prize control did not award two");
    }
    static void lastPokemon(){for(String id:IDS){var m=departureGame(id);UUID host=m.activePokemonView(A).pokemonId();for(var other:List.copyOf(m.benchPokemonView(A)))m.movePokemonByEffect(A,A,other.pokemonId(),LeavePlayDestination.DISCARD);int prizes=m.privateView(B).prizes().size();depart(m,A,host,id.equals(IDS.getLast()));NonPokemonBatch2Test.settle(m);check(m.phase()==MatchPhase.FINISHED&&m.privateView(B).prizes().size()==prizes,"Last substitute did not lose without awarding Prize");}}
    static void ai(){for(String id:IDS)for(var level:AiDifficulty.values()){
        var m=departureGame(id);m.discardHandCardsByEffect(A,A,m.privateView(A).hand().stream().map(CardInstance::instanceId).toList());
        var session=MatchSession.multiplayer(m,A,B);var input=AiPracticeScenarios.view(session,A);var choice=AiPolicy.choose(input,62,Set.of(),level).orElseThrow();var option=input.snapshot().options().stream().filter(o->o.index()==choice.option()).findFirst().orElseThrow();
        check(option.type()==(id.equals(IDS.getFirst())?ActionType.FOSSIL_DISCARD:ActionType.DOLL_RETURN),"AI did not clear inactive substitute: "+level);
        session.submit(A,A,choice.revision(),choice.option());AiCardOperationTest.settle(session,level);check(!m.activePokemonView(A).topCardId().equals("trainer"),"AI failed replacement");
    }}
}
