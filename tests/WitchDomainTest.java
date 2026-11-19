import com.google.gson.*;
import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class WitchDomainTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final List<String> IDS=List.of("swsh12-5_en_066","swsh12-5a_en_047");
    static final CombatCatalog DATA=BundledCombatData.load();
    static AbilityDefinition ability(String id){return DATA.pokemon(id).orElseThrow().abilities().getFirst();}
    static UUID other(UUID p){return p.equals(A)?B:A;}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static void check(boolean b,String why){GeneratedRuleBatch8Test.check(b,why);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static int damage(ServerMatch m,UUID p,UUID id){return EventTargetBindingTest.field(m).pokemonView(p,id).damage();}
    static ServerMatch game(String id,UUID owner,boolean ban){var c=new TurnDamageReductionTest.Config();c.hit=TurnDamageReductionTest.hit(300,EnergyType.COLORLESS,"");c.abilities=ban?List.of(ability(id),CounterTransferRulesTest.ban()):List.of(ability(id));var m=TurnDamageReductionTest.game(c);if(owner.equals(B))m.endTurn(A);return m;}
    static ServerMatch.DamageCounterPlacement pick(UUID owner,UUID id,int n){return new ServerMatch.DamageCounterPlacement(owner,id,n);}
    static void choose(ServerMatch m,UUID p,ServerMatch.DamageCounterPlacement... picks){var q=m.damageAllocationPrompt(p);m.submitDamageAllocation(p,q.resolutionId(),q.step(),List.of(picks));}
    public static void main(String[] args){quantities();protection();knockout();schema();ai();System.out.println("WitchDomainTest passed: both printings/seats, single or split sources, lone Active, 1..2 total, no-effect/foreign/stale rejection, movement prohibition, destination immunity, non-healing semantics, KO and sixteen complete four-tier AI matches");}
    static void quantities(){for(String id:IDS)for(UUID p:List.of(A,B))for(String mode:List.of("ONE","TWO","SPLIT","ALONE")){
        var m=game(id,p,false);UUID source=active(m,p),second=bench(m,p),opponent=active(m,other(p));var a=ability(id);
        check(!m.canUseAbility(p,source,a.id()),"Undamaged field may activate Witch Domain");
        boolean split=mode.equals("SPLIT");int available=mode.equals("ONE")?1:3;int moved=mode.equals("ONE")?1:2;
        if(mode.equals("ALONE"))m.movePokemonByEffect(p,p,second,LeavePlayDestination.HAND);
        m.placeDamageCountersByEffect(p,p,source,split?1:available);if(split)m.placeDamageCountersByEffect(p,p,second,1);
        m.useAbility(p,source,a.id());var q=m.damageAllocationPrompt(p);check(q.budget()==moved&&q.minimumTargets()==1&&!q.canConfirm(),"Wrong public 1..2 counter range");
        reject(()->choose(m,p));reject(()->choose(m,p,pick(p,source,3)));reject(()->choose(m,p,pick(other(p),opponent,1)));reject(()->m.submitDamageAllocation(other(p),q.resolutionId(),q.step(),List.of(pick(p,source,1))));
        reject(()->choose(m,p,pick(p,source,1),pick(p,source,1)));check(damage(m,p,source)==(split?10:available*10),"Invalid allocation partially changed source");
        choose(m,p,split?new ServerMatch.DamageCounterPlacement[]{pick(p,source,1),pick(p,second,1)}:new ServerMatch.DamageCounterPlacement[]{pick(p,source,moved)});
        check(damage(m,p,source)==(split?0:(available-moved)*10)&&damage(m,other(p),opponent)==moved*10,"Wrong source/destination counts");
        if(split)check(damage(m,p,second)==0,"Second selected source ignored");
        check(!EventTargetBindingTest.field(m).healedThisTurn(p,source)&&m.currentPlayer().equals(p)&&!m.hasPendingEffectChoice(),"Movement healed, ended turn or stalled");
        reject(()->m.submitDamageAllocation(p,q.resolutionId(),q.step(),List.of(pick(p,source,1))));check(!m.canUseAbility(p,source,a.id()),"Same Witch Domain source reused");
    }}
    static void protection(){for(String mode:List.of("SOURCE","DESTINATION","BAN")){
        var m=game(IDS.getFirst(),A,mode.equals("BAN"));UUID source=active(m,A),opponent=active(m,B);m.placeDamageCountersByEffect(A,A,source,2);
        if(mode.equals("SOURCE"))StarterDeck100CounterMoveTest.shield(m,A,source,"ABILITY");
        if(mode.equals("DESTINATION")){m.endTurn(A);StarterDeck100CounterMoveTest.shield(m,B,opponent,"ABILITY");m.endTurn(B);}
        if(!mode.equals("DESTINATION")){check(!m.canUseAbility(A,source,ability(IDS.getFirst()).id()),"Protected or prohibited source offered for movement: "+mode);continue;}
        m.useAbility(A,source,ability(IDS.getFirst()).id());choose(m,A,pick(A,source,2));check(damage(m,A,source)==0&&damage(m,B,opponent)==0,"Immune destination restored removed counters or received them");
    }}
    static void knockout(){var m=game(IDS.getFirst(),A,false);UUID source=active(m,A),opponent=active(m,B);m.placeDamageCountersByEffect(A,A,source,2);m.placeDamageCountersByEffect(A,B,opponent,60);m.placeDamageCountersByEffect(A,B,opponent,38);m.useAbility(A,source,ability(IDS.getFirst()).id());choose(m,A,pick(A,source,2));NonPokemonBatch67Test.settle(m);check(m.privateView(A).prizes().size()==5&&damage(m,A,source)==0,"Witch Domain KO/prize checkpoint failed");}
    static void schema(){for(String id:IDS)ActivatedAbilityRules.validate(ability(id));var raw=JsonParser.parseString("{\"type\":\"redistribute_damage_counters\",\"target\":\"ALL_OWN_POKEMON\",\"maximum\":2,\"destination\":\"OPPONENT_ACTIVE\"}").getAsJsonObject();for(var e:Map.of("maximum","3","target","SELF","destination","OPPONENT_BENCH","optional","true").entrySet()){var bad=raw.deepCopy();bad.addProperty(e.getKey(),e.getValue());reject(()->EffectRegistry.standard().parse(bad));}var effect=EffectRegistry.standard().parse(raw);for(var trigger:List.of(EffectTrigger.ON_CARD_PLAYED,EffectTrigger.AFTER_ATTACK,EffectTrigger.ONCE_PER_GAME))reject(()->ResumableProgramRules.validate(new EffectProgram(List.of(new TriggeredEffects(trigger,List.of(),List.of(effect)))),true,trigger));}
    static void ai(){for(String id:IDS)for(UUID p:List.of(A,B))for(var level:AiDifficulty.values()){var m=game(id,p,false);m.placeDamageCountersByEffect(p,p,active(m,p),1);m.placeDamageCountersByEffect(p,p,bench(m,p),1);m.useAbility(p,active(m,p),ability(id).id());var session=MatchSession.multiplayer(m,A,B);AiCardOperationTest.settle(session,level);check(damage(m,p,active(m,p))==0&&damage(m,p,bench(m,p))==0&&damage(m,other(p),active(m,other(p)))==20,"AI failed split source choice: "+level);AiCardOperationTest.finish(session,level);check(m.phase()==MatchPhase.FINISHED,"Witch Domain AI stalled: "+level);}}
}
