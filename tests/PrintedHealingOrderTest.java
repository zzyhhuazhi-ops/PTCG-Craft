import com.google.gson.*;
import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.nio.file.*;
import java.util.*;

/** Actual source programs: uncapped healing and printed draw/heal before self Sleep. */
public final class PrintedHealingOrderTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final Map<String,JsonObject> RAW=load();
    static final List<String> BENCH=List.of("bw3_en_005","mebsp_en_019","smbsp_en_237","sv3_en_021","swsh10-5_en_052","swsh4-5_en_015","swshalt_en_106");
    static final List<String> SLEEP=List.of("bw10_en_040","bw11r_en_004","xybsp_en_112","xybsp_en_195");
    static Map<String,JsonObject> load(){try{var out=new HashMap<String,JsonObject>();for(var line:Files.readAllLines(Path.of("outputs/pokemon-heal-spread-programs.jsonl"))){var r=JsonParser.parseString(line).getAsJsonObject();out.put(r.get("id").getAsString(),r.getAsJsonObject("conversion").getAsJsonObject("combatEntry"));}return out;}catch(Exception e){throw new RuntimeException(e);}}
    static void check(boolean b,String why){GeneratedRuleBatch8Test.check(b,why);}
    static UUID other(UUID p){return p.equals(A)?B:A;}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static AttackDefinition attack(String id,boolean pause){
        var a=RAW.get(id).getAsJsonArray("attacks").get(0).getAsJsonObject().deepCopy();a.add("energyCost",new JsonObject());
        if(pause){
            // Test-only checkpoint between the original operations; neither original operation is replaced.
            var effects=a.getAsJsonArray("triggers").get(0).getAsJsonObject().getAsJsonArray("effects");
            check(effects.size()==2&&effects.get(1).getAsJsonObject().get("type").getAsString().equals("sleep"),"Printed final Sleep missing "+id);
            var sleep=effects.remove(1);effects.add(JsonParser.parseString("{\"type\":\"choice\",\"options\":[{\"type\":\"draw\",\"amount\":1},{\"type\":\"draw\",\"amount\":2}]}"));effects.add(sleep);
        }
        return BundledCombatData.attack(a,EffectRegistry.standard());
    }
    static ServerMatch game(String id,UUID p,boolean pause){var c=new GeneratedRuleBatch28Test.Config();c.ownHp=2000;c.defenderHp=2000;c.attack=attack(id,pause);c.defenderAttack=c.attack;var m=GeneratedRuleBatch28Test.fixture(c);if(p.equals(B))m.endTurn(A);return m;}
    static void damage(ServerMatch m,UUID p,UUID host,int counters){while(counters>0){int n=Math.min(counters,60);m.placeDamageCountersByEffect(m.currentPlayer(),p,host,n);counters-=n;}}
    static void atomic(ServerMatch m,Runnable r){var before=m.clientView(null);var rng=m.randomTranscript();GeneratedRuleBatch8Test.reject(r);check(before.equals(m.clientView(null))&&rng.equals(m.randomTranscript()),"Rejected healing selection mutated state/RNG");}
    public static void main(String[] args){allBench();sleepOrder();emptyAndShortDraw();ai();System.out.println("PrintedHealingOrderTest passed: seven full Bench-heal sources, four draw/heal-before-Sleep sources, both seats, 1500 damage boundary, healthy/absent targets, foreign/stale rejection, intermediate order, empty/short decks, normal Checkup and 24 four-tier matches");}
    static void allBench(){for(String id:BENCH)for(UUID p:List.of(A,B))for(int hurt:List.of(0,150)){
        var m=game(id,p,false);UUID target=bench(m,p);damage(m,p,target,hurt);damage(m,p,active(m,p),7);damage(m,other(p),bench(m,other(p)),5);m.attack(p,attack(id,false).id());
        if(m.hasPendingPokemonSelection()){
            var q=m.effectPokemonPrompt(p);atomic(m,()->m.chooseEffectPokemon(p,q.resolutionId(),q.step(),active(m,p)));atomic(m,()->m.chooseEffectPokemon(other(p),q.resolutionId(),q.step(),target));
            m.chooseEffectPokemon(p,q.resolutionId(),q.step(),target);atomic(m,()->m.chooseEffectPokemon(p,q.resolutionId(),q.step(),target));
        }
        check(m.benchPokemonView(p).getFirst().damage()==0&&m.activePokemonView(p).damage()==70&&m.benchPokemonView(other(p)).getFirst().damage()==50,"Full heal capped or escaped chosen Bench "+id);
        check(m.currentPlayer().equals(other(p)),"Bench heal failed to finish turn");
    }
        for(UUID p:List.of(A,B)){var m=game(BENCH.getFirst(),p,false);m.movePokemonByEffect(p,p,bench(m,p),LeavePlayDestination.HAND);m.attack(p,attack(BENCH.getFirst(),false).id());check(m.currentPlayer().equals(other(p))&&!m.hasPendingPokemonSelection(),"No Bench stranded healing attack");}
    }
    static void sleepOrder(){for(String id:SLEEP)for(UUID p:List.of(A,B)){
        boolean heal=id.equals("bw11r_en_004");var m=game(id,p,true);damage(m,p,active(m,p),150);int held=m.privateView(p).hand().size(),rng=m.randomTranscript().size();m.attack(p,attack(id,true).id());
        check(m.hasPendingEffectChoice()&&m.currentPlayer().equals(p),"Missing intermediate test checkpoint");
        check(!m.activePokemonView(p).conditions().contains(SpecialCondition.ASLEEP),"Sleep happened before printed draw/heal "+id);
        check(m.privateView(p).hand().size()==held+(heal?0:3)&&m.activePokemonView(p).damage()==(heal?0:1500),"First printed operation missing or capped "+id);
        var q=m.effectChoicePrompt(p);m.chooseEffectOption(p,q.resolutionId(),q.step(),0);
        check(m.currentPlayer().equals(other(p)),"Sleep continuation lost end of turn");
        var rolls=m.randomTranscript().subList(rng,m.randomTranscript().size()).stream().filter(e->e.operation().startsWith("checkup-asleep:"+p+":")).toList();
        check(rolls.size()==1&&m.activePokemonView(p).conditions().contains(SpecialCondition.ASLEEP)==(rolls.getFirst().result()==1),"Self Sleep skipped normal single Checkup recovery");
    }}
    static void emptyAndShortDraw(){for(UUID p:List.of(A,B))for(int available:List.of(0,1,2)){
        var m=game("bw10_en_040",p,false);var deck=PairedTrainerPlayTest.zone(m,p,"deck");var hand=PairedTrainerPlayTest.zone(m,p,"hand");while(deck.size()>available)hand.add(deck.removeFirst());int held=hand.size(),rng=m.randomTranscript().size();m.attack(p,"precognitive-dream");
        check(m.privateView(p).hand().size()==held+available&&m.phase()==MatchPhase.TURN&&m.currentPlayer().equals(other(p)),"Partial effect draw caused premature loss or skipped continuation");
        check(m.randomTranscript().subList(rng,m.randomTranscript().size()).stream().anyMatch(e->e.operation().startsWith("checkup-asleep:"+p+":")),"Empty effect draw skipped self Sleep");
    }}
    static void ai(){for(String id:List.of("bw3_en_005","bw10_en_040","bw11r_en_004"))for(UUID p:List.of(A,B))for(var level:AiDifficulty.values()){
        var m=game(id,p,false);damage(m,p,active(m,p),15);damage(m,p,bench(m,p),150);var s=MatchSession.multiplayer(m,A,B);var v=s.view(p,p);var option=v.options().stream().filter(o->o.enabled()&&o.type()==ActionType.ATTACK).findFirst().orElseThrow();s.submit(p,p,v.revision(),option.index());AiCardOperationTest.settle(s,level);AiCardOperationTest.finish(s,level);check(m.phase()==MatchPhase.FINISHED,"Healing/Sleep AI stalled "+level);
    }}
}
