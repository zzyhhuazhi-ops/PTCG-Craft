import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch104Test {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B;
    static final List<String> IDS=List.of("swsh5_en_136");
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static UUID other(UUID p){return p.equals(A)?B:A;}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String key){return DynamicPassiveModifierTest.hand(m,p,key);}
    static TrainerProfile profile(String id){return NonPokemonBatch2Test.DATA.trainer(id).orElseThrow();}
    static void settle(ServerMatch m){AiCardOperationTest.settle(MatchSession.multiplayer(m,A,B),AiDifficulty.MASTER);}
    static ServerMatch game(TrainerProfile item,AttackDefinition attack){return DynamicPassiveModifierTest.fixture(profile(IDS.getFirst()),profile("sm6_en_111"),EnergyProfile.fixed(EnergyType.COLORLESS,2),attack,List.of(ToolCapacityResolutionTest.capacity(2)),0,item,Map.of("basic",EnergyType.GRASS,"evolved",EnergyType.GRASS));}
    static ServerMatch game(TrainerProfile item){return game(item,GeneratedRuleBatch8Test.hit(40,false));}
    static CardInstance attach(ServerMatch m,UUID owner,UUID host,String key){var card=hand(m,owner,key);m.attachToolByEffect(m.currentPlayer(),owner,EffectSourceZone.HAND,card.instanceId(),owner,host);return card;}
    static ServerMatch aiGame(String id){return NonPokemonBatch102Test.game(id);}
    public static void main(String[] args){positionAndControllers();jammerException();globalSuppression();damageTiming();knockoutTiming();automaticExpiry();hitPointLoss();ai();schema();System.out.println("NonPokemonBatch104Test passed: live opposing Active Tool suppression, mutual Jammer exception and physical possession, Bench/entry/switch/removal/global suppression, damage-time eligibility versus post-attack KO triggers, turn-end expiry and HP loss, four AI difficulties");}
    static void positionAndControllers(){for(UUID p:List.of(A,B)){
        var m=game(DynamicPassiveModifierTest.plainTool());if(p.equals(B))m.endTurn(A);UUID opp=other(p),ownActive=active(m,p),oppActive=active(m,opp);var jammer=attach(m,p,bench(m,p),"tool");attach(m,p,ownActive,"item");attach(m,opp,oppActive,"item");attach(m,opp,bench(m,opp),"item");
        check(m.activeRetreatCost(p)==2&&m.activeRetreatCost(opp)==2,"Benched Jammer suppressed effects");UUID source=bench(m,p);m.switchActiveByEffect(p,p,source);check(m.activeRetreatCost(opp)==3,"Active Jammer failed opposing Active");
        m.switchActiveByEffect(p,opp,bench(m,opp));check(m.activeRetreatCost(opp)==3,"New Active escaped Jammer");m.switchActiveByEffect(p,p,ownActive);check(m.activeRetreatCost(opp)==2,"Benched source left suppression");
        m.switchActiveByEffect(p,p,source);m.discardToolByEffect(p,p,source,jammer.instanceId());check(m.activeRetreatCost(opp)==2,"Discarded Jammer lingered");
    }}
    static void jammerException(){var m=game(DynamicPassiveModifierTest.plainTool());for(UUID p:List.of(A,B)){attach(m,p,active(m,p),"tool");attach(m,p,active(m,p),"item");}
        check(m.activeRetreatCost(A)==3&&m.activeRetreatCost(B)==3,"Mutual Jammers disabled each other");
        for(UUID p:List.of(A,B))check(m.matchesEffectCondition(p,active(m,p),new ConditionDefinition(ConditionType.HAS_TOOL,Map.of("target","SELF"),List.of())),"Suppressed Tool lost printed possession");
        m.disableToolEffectsByEffect(A,A,active(m,A));check(m.activeRetreatCost(B)==2&&m.activeRetreatCost(A)==3,"Non-Tool suppression failed to disable a Jammer");
    }
    static void globalSuppression(){var m=game(DynamicPassiveModifierTest.plainTool());attach(m,A,active(m,A),"tool");attach(m,B,active(m,B),"item");m.playTrainer(A,hand(m,A,"stadium").instanceId(),null);check(m.activeRetreatCost(B)==3,"Labs left other Tool enabled");
        m.switchActiveByEffect(A,A,bench(m,A));check(m.activeRetreatCost(B)==3,"Labs depended on Jammer position");m.discardStadiumByEffect(A);check(m.activeRetreatCost(B)==2,"Labs departure left Tool disabled");
    }
    static void damageTiming(){for(UUID actor:List.of(A,B))for(String mode:List.of("stay","switch","discard-before","discard-after")){
        String discard="{\"type\":\"discard_tool\",\"target\":\"SELF\",\"amount\":60,\"minimum\":60}";
        String after=mode.equals("switch")?"{\"type\":\"switch\",\"target\":\"SELECT_OWN_POKEMON\"}":mode.equals("discard-after")?discard:"";
        var attack=BeforeDamageAttackTest.attack(mode.equals("discard-before")?discard:"","",after);var m=game(profile("swsh4_en_159"),attack);if(actor.equals(B))m.endTurn(A);UUID holder=active(m,actor),opp=other(actor);attach(m,actor,holder,"tool");attach(m,opp,active(m,opp),"item");m.attack(actor,"hit");settle(m);
        var field=EventTargetBindingTest.field(m).pokemonView(actor,holder);check(field.damage()==(mode.equals("discard-before")?20:0),"Helmet eligibility wrong at damage: "+mode);
    }}
    static void knockoutTiming(){for(boolean switchAfter:List.of(false,true)){
        var attack=BeforeDamageAttackTest.attack("","",switchAfter?"{\"type\":\"switch\",\"target\":\"SELECT_OWN_POKEMON\"}":"");var m=game(profile("swsh1_en_167"),attack);attach(m,A,active(m,A),"tool");attach(m,B,active(m,B),"item");
        m.placeDamageCountersByEffect(A,B,active(m,B),22);var cards=m.privateView(B).hand().stream().skip(2).map(CardInstance::instanceId).toList();m.discardHandCardsByEffect(A,B,cards);m.attack(A,"hit");settle(m);
        check(m.privateView(B).hand().size()==(switchAfter?8:3),"Lucky Egg used damage-time instead of actual KO-time source state");
    }}
    static void automaticExpiry(){var m=game(profile("sm11_en_196"));attach(m,A,active(m,A),"tool");var bomb=attach(m,B,active(m,B),"item");m.endTurn(A);settle(m);check(m.activePokemonView(B).attachedTools().contains(bomb),"Jammer did not suppress Tool expiry");m.switchActiveByEffect(B,A,bench(m,A));m.endTurn(B);m.endTurn(A);settle(m);check(m.privateView(B).discard().contains(bomb),"Tool failed restored next opponent-turn expiry");}
    static void hitPointLoss(){var hp=DynamicPassiveModifierTest.trainer("TOOL","{\"type\":\"hit_points_modifier\",\"target\":\"SELF\",\"amount\":60}");var m=game(hp);UUID defender=active(m,B);attach(m,B,defender,"item");m.placeDamageCountersByEffect(A,B,defender,33);int prizes=m.privateView(A).prizes().size();m.playTrainer(A,hand(m,A,"tool").instanceId(),active(m,A));settle(m);check(!active(m,B).equals(defender)&&m.privateView(A).prizes().size()==prizes-1,"Jammer HP reduction did not settle ordinary KO");}
    static void ai(){for(var level:AiDifficulty.values()){var m=aiGame(IDS.getFirst());UUID p=m.currentPlayer();m.playTrainer(p,hand(m,p,"trainer").instanceId(),active(m,p));var ps=NonPokemonBatch103Test.seats(m);var session=MatchSession.multiplayer(m,ps.get(0),ps.get(1));AiCardOperationTest.settle(session,level);AiCardOperationTest.finish(session,level);check(m.phase()==MatchPhase.FINISHED,"Jammer AI stalled");}}
    static void schema(){var e=new EffectDefinition("disable_opponent_active_tools",EffectTarget.SELF,Map.of(),List.of(),List.of());var p=new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.PASSIVE,List.of(),List.of(e))));GeneratedRuleBatch8Test.reject(()->PassiveModifierProgram.validate(p,PassiveModifierProgram.Source.STADIUM));GeneratedRuleBatch8Test.reject(()->PassiveRuleProgram.validate(new EffectDefinition(e.type(),EffectTarget.OPPONENT_ACTIVE,Map.of(),List.of(),List.of())));}
}
