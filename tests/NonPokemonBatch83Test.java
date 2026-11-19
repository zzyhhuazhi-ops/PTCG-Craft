import com.google.gson.JsonParser;
import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch83Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final List<String> IDS=List.of("swsh11_en_156");
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return DynamicPassiveModifierTest.hand(m,p,id);}
    static TrainerProfile profile(){return NonPokemonBatch2Test.DATA.trainer(IDS.getFirst()).orElseThrow();}
    static ServerMatch game(UUID p){var m=GeneratedRuleBatch23Test.fixture(profile(),0,EnergyType.GRASS);if(p.equals(B))m.endTurn(A);return m;}
    static void hurt(ServerMatch m,UUID p,UUID id,int n){NonPokemonBatch75Test.hurt(m,p,id,n*10);}
    static int damage(ServerMatch m,UUID p,UUID id){return EventTargetBindingTest.field(m).pokemonView(p,id).damage();}
    static void choose(ServerMatch m,UUID p,ServerMatch.DamageCounterPlacement... placements){var q=m.damageAllocationPrompt(p);m.submitDamageAllocation(p,q.resolutionId(),q.step(),List.of(placements));}
    static ServerMatch.DamageCounterPlacement pick(UUID p,UUID id,int n){return new ServerMatch.DamageCounterPlacement(p,id,n);}
    static void play(ServerMatch m,UUID p){m.playTrainer(p,hand(m,p,"trainer").instanceId(),null);}
    static ServerMatch aiGame(String id){var m=game(A);hurt(m,A,active(m,A),2);hurt(m,B,active(m,B),2);return m;}
    public static void main(String[] args){movement();authority();unavailable();prohibition();knockout();ai();schema();System.out.println("NonPokemonBatch83Test passed: both owners/positions, 1..2 from exactly one source, split destinations, no self/opponent transfer or healing, atomic wrong/stale/empty/excess rejection, live movement ban, own KO and AI preserving healthy recipients");}
    static void movement(){for(UUID p:List.of(A,B))for(boolean activeSource:List.of(false,true))for(int available:List.of(1,2,4))for(int moved=1;moved<=Math.min(2,available);moved++)for(boolean split:List.of(false,true)){
        var m=game(p);var extra=hand(m,p,"basic");m.playBasicPokemonToBench(p,extra.instanceId());UUID third=PokemonIdentityTestSupport.logical(m,p,extra.instanceId());UUID source=activeSource?active(m,p):bench(m,p),dest=activeSource?bench(m,p):active(m,p);hurt(m,p,source,available);int rng=m.randomTranscript().size();var card=hand(m,p,"trainer");play(m,p);var q=m.damageAllocationPrompt(p);
        check(q.optional()&&!q.canConfirm()&&q.minimumTargets()==1&&q.maximumTargets()==1&&q.budget()==Math.min(2,available)&&q.candidates().equals(List.of(new DamageAllocationPrompt.Target(p,source))),"Wrong source selection limits");
        choose(m,p,pick(p,source,moved));var r=m.damageAllocationPrompt(p);check(!r.optional()&&r.budget()==moved&&r.candidates().stream().noneMatch(t->t.pokemonId().equals(source))&&damage(m,p,source)==available*10,"Source moved before destination or remained a candidate");
        boolean divide=split&&moved==2;choose(m,p,divide?new ServerMatch.DamageCounterPlacement[]{pick(p,dest,1),pick(p,third,1)}:new ServerMatch.DamageCounterPlacement[]{pick(p,dest,moved)});
        check(damage(m,p,source)==(available-moved)*10&&damage(m,p,dest)==(divide?10:moved*10)&&damage(m,p,third)==(divide?10:0),"Counters not conserved across declared sources/destinations");
        check(!EventTargetBindingTest.field(m).healedThisTurn(p,source)&&m.randomTranscript().size()==rng&&m.currentPlayer().equals(p)&&!m.hasPendingEffectChoice()&&m.privateView(p).discard().contains(card),"Movement healed, shuffled, ended turn or stalled");
    }}
    static void authority(){var m=game(A);UUID from=active(m,A),to=bench(m,A);hurt(m,A,from,4);hurt(m,A,to,1);play(m,A);var q=m.damageAllocationPrompt(A);
        GeneratedRuleBatch8Test.reject(()->choose(m,A));GeneratedRuleBatch8Test.reject(()->m.submitDamageAllocation(B,q.resolutionId(),q.step(),List.of(pick(A,from,1))));GeneratedRuleBatch8Test.reject(()->choose(m,A,pick(A,from,3)));GeneratedRuleBatch8Test.reject(()->choose(m,A,pick(A,from,1),pick(A,to,1)));GeneratedRuleBatch8Test.reject(()->choose(m,A,pick(A,from,1),pick(A,from,1)));GeneratedRuleBatch8Test.reject(()->choose(m,A,pick(B,active(m,B),1)));
        choose(m,A,pick(A,from,2));var r=m.damageAllocationPrompt(A);check(!q.resolutionId().equals(r.resolutionId()),"Source token reused for destination");GeneratedRuleBatch8Test.reject(()->m.submitDamageAllocation(A,q.resolutionId(),q.step(),List.of(pick(A,to,2))));GeneratedRuleBatch8Test.reject(()->choose(m,A,pick(A,to,1)));GeneratedRuleBatch8Test.reject(()->choose(m,A,pick(A,from,2)));GeneratedRuleBatch8Test.reject(()->choose(m,A,pick(B,active(m,B),2)));check(damage(m,A,from)==40&&damage(m,A,to)==10,"Rejected choice partially moved counters");choose(m,A,pick(A,to,2));GeneratedRuleBatch8Test.reject(()->m.submitDamageAllocation(A,r.resolutionId(),r.step(),List.of(pick(A,to,2))));check(damage(m,A,from)==20&&damage(m,A,to)==30,"Stale destination moved twice");
    }
    static void unavailable(){for(boolean alone:List.of(false,true)){var m=game(A);if(alone){hurt(m,A,active(m,A),2);m.movePokemonByEffect(A,A,bench(m,A),LeavePlayDestination.HAND);}var card=hand(m,A,"trainer");var before=m.privateView(A);check(!m.canPlayTrainer(A,card.instanceId(),null),"Pump playable without damage or another Pokémon");GeneratedRuleBatch8Test.reject(()->play(m,A));check(m.privateView(A).equals(before),"Rejected Pump consumed card");}}
    static void prohibition(){var c=new GeneratedRuleBatch28Test.Config();c.trainer=profile();c.ownAbility=CounterTransferRulesTest.ban();var m=GeneratedRuleBatch28Test.fixture(c);UUID from=active(m,A);hurt(m,A,from,2);check(!m.canPlayTrainer(A,hand(m,A,"trainer").instanceId(),null),"Movement ban ignored before play");m.disableAbilityEffectsByEffect(A,A,from);check(!m.canPlayTrainer(A,hand(m,A,"trainer").instanceId(),null),"Second live movement ban lost");m.disableAbilityEffectsByEffect(A,A,bench(m,A));play(m,A);choose(m,A,pick(A,from,2));choose(m,A,pick(A,bench(m,A),2));check(damage(m,A,from)==0&&damage(m,A,bench(m,A))==20,"Disabled ban still blocked movement");}
    static void knockout(){for(UUID p:List.of(A,B)){var m=game(p);UUID source=active(m,p),dest=bench(m,p);hurt(m,p,source,2);hurt(m,p,dest,(EventTargetBindingTest.field(m).pokemonView(p,dest).hitPoints()-10)/10);play(m,p);choose(m,p,pick(p,source,2));choose(m,p,pick(p,dest,2));NonPokemonBatch67Test.settle(m);UUID opp=p.equals(A)?B:A;check(damage(m,p,source)==0&&m.benchPokemonView(p).isEmpty()&&m.privateView(opp).prizes().size()==5,"Overkill destination did not resolve own KO/Prize");}}
    static void ai(){for(AiDifficulty level:AiDifficulty.values()){var m=game(A);var extra=hand(m,A,"basic");m.playBasicPokemonToBench(A,extra.instanceId());UUID healthy=PokemonIdentityTestSupport.logical(m,A,extra.instanceId()),endangered=bench(m,A);hurt(m,A,active(m,A),2);hurt(m,A,endangered,(EventTargetBindingTest.field(m).pokemonView(A,endangered).hitPoints()-10)/10);play(m,A);choose(m,A,pick(A,active(m,A),2));AiCardOperationTest.settle(MatchSession.multiplayer(m,A,B),level);check(damage(m,A,healthy)==20&&m.benchPokemonView(A).size()==2&&m.privateView(B).prizes().size()==6,"AI moved counters onto a dying recipient: "+level);}}
    static void schema(){String json="{\"type\":\"move_own_damage_counters\",\"target\":\"SELF\"}";for(String bad:List.of(json.replace("SELF","OPPONENT_ACTIVE"),json.replace("}",",\"amount\":3}"),json.replace("}",",\"optional\":true}")))GeneratedRuleBatch8Test.reject(()->EffectRegistry.standard().parse(JsonParser.parseString(bad).getAsJsonObject()));}
}
