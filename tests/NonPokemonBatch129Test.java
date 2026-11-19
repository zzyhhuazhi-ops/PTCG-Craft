import com.google.gson.JsonParser;
import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch129Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final String ID="swsh7_en_165";
    static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static GeneratedRuleBatch28Test.Config config(){var c=new GeneratedRuleBatch28Test.Config();c.specialEnergy=NonPokemonBatch2Test.DATA.energyProfile(ID).orElseThrow();c.attack=GeneratedRuleBatch8Test.hit(1000,false);c.defenderAttack=c.attack;return c;}
    static ServerMatch game(){return GeneratedRuleBatch28Test.fixture(config());}
    static CardInstance prize(ServerMatch m,UUID p,int slot){return NonPokemonBatch128Test.prize(m,p,slot,"energy");}
    static void choose(ServerMatch m,UUID p,int n){NonPokemonBatch128Test.choose(m,p,n);}
    static ServerMatch aiGame(){var m=game();prize(m,A,0);m.attack(A,"hit");m.takePrizeAt(A,0);return m;}
    public static void main(String[] args){attachAndDecline();exclusive();sourceTriggers();nestedKnockout();direct();authority();concession();smoke();ai();schema();System.out.println("NonPokemonBatch129Test passed: Treasure Energy own-turn face-down pre-hand attachment, both seats/hosts, manual quota and true PRIZE provenance, HAND/DECK/DISCARD exclusion and PRIZE/ANY reactions, nested KO with remaining Prize/replacement preservation, direct attack continuation, Smoke/final-Prize/Checkup/face-up exclusions, authority/concession and four AI difficulties");}
    static void attachAndDecline(){for(UUID p:List.of(A,B))for(int target=0;target<3;target++)for(boolean manual:List.of(false,true)){
        var m=game();if(p.equals(B))m.endTurn(A);if(manual)m.attachEnergy(p,DynamicPassiveModifierTest.hand(m,p,"energy").instanceId(),m.activePokemonView(p).pokemonId());var card=prize(m,p,0);var host=target==1?m.benchPokemonView(p).getFirst().pokemonId():m.activePokemonView(p).pokemonId();int held=m.privateView(p).hand().size();m.attack(p,"hit");m.takePrizeAt(p,0);check(m.effectChoicePrompt(p).optionsZh().size()==3&&!m.privateView(p).hand().contains(card),"Treasure wrong hosts/pre-hand window");choose(m,p,target);var chosen=target==1?m.benchPokemonView(p).getFirst():m.activePokemonView(p);check(chosen.pokemonId().equals(host)&&chosen.attachedEnergyIds().contains(card.instanceId())==(target!=2),"Treasure wrong target/destination");check(m.privateView(p).hand().size()==held+(target==2?1:0)&&m.energyAttached(p)==manual,"Treasure altered manual quota/hand");NonPokemonBatch103Test.settle(m);
    }}
    static void exclusive(){for(String mode:List.of("faceup","checkup","last")){
        var m=game();var card=prize(m,A,0);if(mode.equals("faceup"))NonPokemonBatch116Test.faces(m,A).add(card.instanceId());if(mode.equals("last"))GeneratedRuleBatch47Test.remainingPrizes(m,1);
        if(mode.equals("checkup")){NonPokemonBatch75Test.hurt(m,B,m.activePokemonView(B).pokemonId(),990);m.applySpecialConditionByEffect(A,B,SpecialCondition.POISONED);m.endTurn(A);}else m.attack(A,"hit");m.takePrizeAt(A,0);check(!m.hasPendingEffectChoice()&&m.privateView(A).hand().contains(card),"Treasure opened illegal "+mode+" window");if(!mode.equals("last"))NonPokemonBatch103Test.settle(m);
    }}
    static AbilityDefinition trigger(String source,String effects){return BundledCombatData.ability(JsonParser.parseString("{\"id\":\"prize-watch\",\"nameZh\":\"奖赏附能事件测试\",\"kind\":\"TRIGGERED\",\"effectSupport\":\"SUPPORTED\",\"eventPolicy\":{\"target\":\"SELF\",\"source\":\""+source+"\",\"cause\":\"ANY\",\"optional\":false,\"usage\":\"NONE\"},\"triggers\":[{\"trigger\":\"ON_ENERGY_ATTACHED\",\"effects\":["+effects+"]}]}").getAsJsonObject(),EffectRegistry.standard());}
    static void sourceTriggers(){for(String source:List.of("HAND","DECK","DISCARD","PRIZE","ANY")){
        var c=config();c.ownAbility=trigger(source,"{\"type\":\"draw\",\"amount\":1}");var m=GeneratedRuleBatch28Test.fixture(c);var card=prize(m,A,0);int held=m.privateView(A).hand().size(),turn=m.turnNumber();m.attack(A,"hit");m.takePrizeAt(A,0);choose(m,A,0);boolean match=Set.of("PRIZE","ANY").contains(source);check(m.privateView(A).hand().size()==held+(match?1:0)&&m.activePokemonView(A).attachedEnergyIds().contains(card.instanceId()),"Attachment source mismatch "+source);check(m.phase()==MatchPhase.CHOOSE_REPLACEMENT&&!m.hasPendingEffectChoice()&&m.turnNumber()==turn,"Child trigger lost original KO replacement "+source);NonPokemonBatch103Test.settle(m);
    }}
    static void nestedKnockout(){
        var c=config();c.defenderPrizes=2;c.ownHp=100;c.ownAbility=trigger("PRIZE","{\"type\":\"damage_counter\",\"target\":\"SELF\",\"amount\":10}");var m=GeneratedRuleBatch28Test.fixture(c);var first=prize(m,A,0);var second=prize(m,A,1);m.attack(A,"hit");m.takePrizeAt(A,0);m.takePrizeAt(A,0);choose(m,A,0);choose(m,A,0);
        check(m.phase()==MatchPhase.PRIZE_SELECTION&&m.pendingChoicePlayer().equals(B)&&m.privateView(A).discard().contains(first),"Attachment reaction KO did not create child Prize");m.takePrizeAt(B,0);
        check(m.phase()==MatchPhase.CHOOSE_REPLACEMENT&&m.pendingChoicePlayer().equals(A),"Child KO promoted original defender before remaining parent Prize");m.chooseReplacement(A,m.benchPokemonView(A).getFirst().pokemonId());
        check(m.hasPendingEffectChoice()&&m.pendingChoicePlayer().equals(A)&&!m.privateView(A).hand().contains(second),"Nested KO lost remaining parent Treasure");choose(m,A,1);
        check(m.privateView(A).hand().contains(second)&&m.phase()==MatchPhase.CHOOSE_REPLACEMENT&&m.pendingChoicePlayer().equals(B),"Parent KO replacement did not resume");NonPokemonBatch103Test.settle(m);
    }
    static void direct(){var c=config();c.attack=NonPokemonBatch128Test.directAttack();c.ownAbility=trigger("PRIZE","{\"type\":\"draw\",\"amount\":1}");var m=GeneratedRuleBatch28Test.fixture(c);var card=prize(m,A,0);NonPokemonBatch128Test.prize(m,A,1,"trainer");int held=m.privateView(A).hand().size(),turn=m.turnNumber();m.attack(A,c.attack.id());GeneratedRuleBatch47Test.take(m,A,0);GeneratedRuleBatch47Test.take(m,A,0);choose(m,A,0);check(m.currentPlayer().equals(B)&&m.turnNumber()==turn+1&&m.privateView(A).hand().size()==held+7&&m.activePokemonView(A).attachedEnergyIds().contains(card.instanceId()),"Direct attack lost/replayed prefix/tail around Energy reaction");}
    static void authority(){var m=aiGame();var q=m.effectChoicePrompt(A);var before=m.clientView(A);reject(()->m.effectChoicePrompt(B));reject(()->m.chooseEffectOption(B,q.resolutionId(),0,0));reject(()->m.chooseEffectOption(A,q.resolutionId(),1,0));reject(()->m.chooseEffectOption(A,q.resolutionId(),0,3));reject(()->m.takePrizeAt(A,0));check(before.equals(m.clientView(A)),"Invalid Treasure intent changed state");choose(m,A,0);reject(()->m.chooseEffectOption(A,q.resolutionId(),0,0));NonPokemonBatch103Test.settle(m);}
    static void concession(){
        var m=aiGame();var held=m.privateView(A).hand().size();m.concede(B);check(m.privateView(A).hand().size()==held+1&&!m.hasPendingEffectChoice(),"Held Treasure lost on concession");
        var c=config();c.defenderPrizes=2;c.ownAbility=trigger("PRIZE","{\"type\":\"choice\",\"effects\":[{\"type\":\"draw\",\"amount\":1},{\"type\":\"draw\",\"amount\":2}]}");var n=GeneratedRuleBatch28Test.fixture(c);var first=prize(n,A,0);var second=prize(n,A,1);n.attack(A,"hit");n.takePrizeAt(A,0);n.takePrizeAt(A,0);choose(n,A,0);choose(n,A,0);check(n.hasPendingEffectChoice()&&n.phase()==MatchPhase.TURN,"Nested reaction did not pause");reject(()->n.endTurn(A));n.concede(B);check(n.activePokemonView(A).attachedEnergyIds().contains(first.instanceId())&&n.privateView(A).hand().contains(second)&&!n.hasPendingEffectChoice(),"Concession lost attached or parent held Energy");
    }
    static void smoke(){var c=config();c.trainer=NonPokemonBatch2Test.DATA.trainer("swsh3_en_158").orElseThrow();var m=GeneratedRuleBatch28Test.fixture(c);m.attachToolByEffect(A,B,EffectSourceZone.HAND,DynamicPassiveModifierTest.hand(m,B,"trainer").instanceId(),B,m.activePokemonView(B).pokemonId());var card=prize(m,A,0);m.attack(A,"hit");m.takePrizeAt(A,0);check(m.privateView(A).discard().contains(card)&&!m.hasPendingEffectChoice(),"Smoke permitted Treasure attachment");NonPokemonBatch103Test.settle(m);}
    static void ai(){for(var level:AiDifficulty.values()){var m=aiGame();var s=MatchSession.multiplayer(m,A,B);AiCardOperationTest.settle(s,level);AiCardOperationTest.finish(s,level);check(m.phase()==MatchPhase.FINISHED,"Treasure AI stalled "+level);}}
    static void schema(){var marker=GeneratedRuleBatch28Test.effect("{\"type\":\"prize_energy_attach\",\"target\":\"SELF\"}");PassiveRuleProgram.validate(marker);reject(()->GeneratedRuleBatch28Test.effect("{\"type\":\"prize_energy_attach\",\"target\":\"OPPONENT_ACTIVE\"}"));reject(()->DeckDiscardTriggeredAbilityTest.trainer("TOOL","{\"type\":\"prize_energy_attach\",\"target\":\"SELF\"}"));}
}
