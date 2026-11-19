import com.google.gson.JsonParser;
import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch105Test {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B;
    static final List<String> IDS=List.of("swsh10_en_136");
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static UUID other(UUID p){return p.equals(A)?B:A;}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String key){return DynamicPassiveModifierTest.hand(m,p,key);}
    static TrainerProfile profile(){return NonPokemonBatch2Test.DATA.trainer(IDS.getFirst()).orElseThrow();}
    static AbilityDefinition draw(){return ResumableAbilityTest.ability("draw","{\"type\":\"draw\",\"target\":\"SELF\",\"amount\":1}");}
    static AbilityDefinition retreat(){return DiscardAbilityCountTest.passive("retreat","{\"type\":\"retreat_cost_modifier\",\"target\":\"SELF\",\"amount\":-1}");}
    static ServerMatch game(List<AbilityDefinition> abilities){return DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),EnergyProfile.fixed(EnergyType.COLORLESS,2),GeneratedRuleBatch8Test.hit(40,false),abilities,0,profile(),Map.of("basic",EnergyType.GRASS,"evolved",EnergyType.GRASS));}
    static ServerMatch game(){return game(List.of(draw(),retreat()));}
    static void settle(ServerMatch m){NonPokemonBatch103Test.settle(m);}
    static void play(ServerMatch m,UUID p){m.playTrainer(p,hand(m,p,"item").instanceId(),null);}
    static boolean has(ServerMatch m,UUID p,UUID host){return m.matchesEffectCondition(p,host,new ConditionDefinition(ConditionType.HAS_ABILITIES,Map.of("target","SELF"),List.of()));}
    static ServerMatch aiGame(String id){var c=new GeneratedRuleBatch28Test.Config();c.trainer=profile();c.ownAbility=draw();c.attack=GeneratedRuleBatch8Test.hit(200,false);c.defenderAttack=c.attack;return GeneratedRuleBatch28Test.fixture(c);}
    public static void main(String[] args){scopeAndExpiry();newEntries();immunityOrder();timing();knockout();noAbilitiesAndAuthority();ai();schema();System.out.println("NonPokemonBatch105Test passed: Canceling Cologne current/future opposing Active abilities, Bench/own exclusion, evolution/new entry and end-before-Checkup expiry, existing Unnerve versus later Active, once per turn/no-ability play, actual Jynx V knockout prizes and four AI difficulties");}
    static void scopeAndExpiry(){for(UUID actor:List.of(A,B)){
        var m=game();UUID opp=other(actor),old=active(m,opp),next=bench(m,opp);if(!m.currentPlayer().equals(opp))m.endTurn(m.currentPlayer());m.evolveByEffect(opp,next,hand(m,opp,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);m.endTurn(opp);check(m.activeRetreatCost(opp)==2,"Retreat fixture");play(m,actor);
        check(!has(m,opp,old)&&has(m,opp,next)&&has(m,actor,active(m,actor))&&m.activeRetreatCost(opp)==3,"Cologne wrong owner or position");check(m.abilityOptions(opp).stream().noneMatch(o->o.pokemonId().equals(old)),"Removed ability remained in options");
        m.switchActiveByEffect(actor,opp,next);check(!has(m,opp,next)&&has(m,opp,old),"Cologne failed new Active/restored Bench");m.devolvePokemonToHandByEffect(actor,opp,next);check(!has(m,opp,next),"Devolution cleared slot suppression");
        var second=hand(m,actor,"item");check(!m.canPlayTrainer(actor,second.instanceId(),null),"Cologne reusable same turn");var before=m.clientView(actor);GeneratedRuleBatch8Test.reject(()->m.playTrainer(actor,second.instanceId(),null));check(before.equals(m.clientView(actor)),"Duplicate play mutated state");
        m.endTurn(actor);settle(m);check(has(m,opp,next)&&m.canUseAbility(opp,next,"draw"),"Cologne persisted into opponent turn");m.endTurn(opp);settle(m);check(m.canPlayTrainer(actor,second.instanceId(),null),"Cologne did not reset for later turn");
    }}
    static void newEntries(){var m=game();play(m,A);UUID old=active(m,B);var returned=EventTargetBindingTest.field(m).topCard(B,old);m.movePokemonByEffect(A,B,old,LeavePlayDestination.HAND);settle(m);var next=active(m,B);check(!has(m,B,next),"Replacement escaped Cologne");m.discardHandCardsByEffect(A,B,List.of(returned.instanceId()));m.putBasicPokemonFromDiscardToBenchByEffect(A,B,returned.instanceId());settle(m);UUID reentry=PokemonIdentityTestSupport.logical(m,B,returned.instanceId());m.switchActiveByEffect(A,B,reentry);check(!has(m,B,reentry),"New physical field entry escaped Cologne");}
    static void immunityOrder(){var guard=NonPokemonBatch2Test.DATA.pokemon("sv6-5_en_045").orElseThrow().ability("unnerve").orElseThrow();for(boolean enabled:List.of(false,true)){
        var m=game(List.of(draw(),guard));UUID old=active(m,B),next=bench(m,B);if(!enabled)m.disableAbilityEffectsByEffect(A,B,old);play(m,A);check(has(m,B,old)==enabled,"Existing Unnerve timing wrong");check(!m.canPlayTrainer(A,hand(m,A,"item").instanceId(),null),"Prevented Cologne allowed same-turn repeat");m.switchActiveByEffect(A,B,next);check(!has(m,B,next),"Bench Unnerve beat already established Cologne");
    }}
    static AbilityDefinition boundary(String trigger){return BundledCombatData.ability(JsonParser.parseString("{\"id\":\"boundary-"+(trigger.equals("ON_TURN_END")?"end":"checkup")+"\",\"nameZh\":\"时点验证\",\"kind\":\"TRIGGERED\",\"effectSupport\":\"SUPPORTED\",\"eventPolicy\":{\"trigger\":\""+trigger+"\","+(trigger.equals("ON_TURN_END")?"\"turn\":\"ANY\",":"")+"\"optional\":false,\"usage\":\"NONE\"},\"triggers\":[{\"trigger\":\""+trigger+"\",\"conditions\":[{\"type\":\"IS_ACTIVE\",\"target\":\"SELF\"},{\"type\":\"HAS_ENERGY\",\"target\":\"SELF\"}],\"effects\":[{\"type\":\"draw\",\"target\":\"SELF\",\"amount\":1}]}]}").getAsJsonObject(),EffectRegistry.standard());}
    static void timing(){var m=game(List.of(boundary("ON_TURN_END"),boundary("ON_POKEMON_CHECKUP")));m.attachEnergyByEffect(A,B,EffectSourceZone.HAND,hand(m,B,"special").instanceId(),B,active(m,B));int before=m.privateView(B).hand().size();play(m,A);m.endTurn(A);settle(m);check(m.privateView(B).hand().size()==before+2,"Cologne must suppress turn-end but expire before Checkup");}
    static void knockout(){for(boolean cologne:List.of(false,true)){
        var c=new GeneratedRuleBatch28Test.Config();c.trainer=profile();c.ownAbility=NonPokemonBatch2Test.DATA.pokemon("swsh12_en_062").orElseThrow().ability("selfish-lips").orElseThrow();c.defenderFamilies=Set.of(PokemonFamily.V);c.defenderAttack=GeneratedRuleBatch8Test.hit(1000,false);var m=GeneratedRuleBatch28Test.fixture(c);UUID actor=GeneratedRuleBatch8Test.B;int prizes=m.privateView(actor).prizes().size();m.endTurn(GeneratedRuleBatch8Test.A);if(cologne)m.playTrainer(actor,hand(m,actor,"trainer").instanceId(),null);m.attack(actor,"hit");settle(m);check(m.privateView(actor).prizes().size()==prizes-(cologne?1:0),"Cologne failed actual Jynx zero-prize ability removal");
    }}
    static void noAbilitiesAndAuthority(){var m=game(List.of());var card=hand(m,A,"item");check(m.canPlayTrainer(A,card.instanceId(),null),"Cologne rejected future-only effect");var before=m.clientView(A);GeneratedRuleBatch8Test.reject(()->m.playTrainer(B,card.instanceId(),null));check(before.equals(m.clientView(A)),"Wrong actor Cologne mutated match");m.playTrainer(A,card.instanceId(),null);check(m.privateView(A).discard().contains(card),"Cologne did not consume Item");}
    static void ai(){for(var level:AiDifficulty.values()){var m=aiGame(IDS.getFirst());UUID p=m.currentPlayer();m.playTrainer(p,hand(m,p,"trainer").instanceId(),null);var ps=NonPokemonBatch103Test.seats(m);var s=MatchSession.multiplayer(m,ps.get(0),ps.get(1));AiCardOperationTest.settle(s,level);AiCardOperationTest.finish(s,level);check(m.phase()==MatchPhase.FINISHED,"Cologne AI stalled");}}
    static void schema(){var e=profile().effectProgram().triggers().getFirst().effects().getFirst();for(var kv:Map.of("scope","ALL_IN_PLAY","duration","OPPONENT_TURN_END").entrySet()){var a=new HashMap<>(e.arguments());a.put(kv.getKey(),kv.getValue());GeneratedRuleBatch8Test.reject(()->TurnAbilitySuppression.validate(new EffectDefinition(e.type(),e.target(),a,List.of(),List.of())));}GeneratedRuleBatch8Test.reject(()->AttackProgramRules.validate(GeneratedRuleBatch8Test.hit(10,false).withEffectProgram(new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.AFTER_ATTACK,List.of(),List.of(e)))))));}
}
