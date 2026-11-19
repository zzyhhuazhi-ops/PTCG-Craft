import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch63Test {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B;
    static final List<String> IDS=List.of("xy4_en_108","swsh3_en_162");
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static TrainerProfile profile(String id){return NonPokemonBatch2Test.DATA.trainer(id).orElseThrow();}
    static AttackDefinition attack(){return AttackCoinMatchTest.nativeAttack("\"coinDamage\":{\"flips\":2,\"perHeads\":30}");}
    static ServerMatch aiGame(String id){var c=new GeneratedRuleBatch28Test.Config();c.trainer=profile(id);c.attack=attack();c.defenderAttack=c.attack;return GeneratedRuleBatch28Test.fixture(c);}
    static CardInstance hand(ServerMatch m,UUID p,String id){return DynamicPassiveModifierTest.hand(m,p,id);}
    static void choose(ServerMatch m,UUID p,int option){var q=m.effectChoicePrompt(p);m.chooseEffectOption(p,q.resolutionId(),q.step(),option);}
    static long uses(ServerMatch m){return m.replayTranscript().stream().filter(e->e.operation().startsWith("发动重投")).count();}
    static ServerMatch fixture(EnergyType type,TrainerProfile stadium,List<AbilityDefinition> abilities,String name){return DynamicPassiveModifierTest.fixture(profile(IDS.getFirst()),stadium,EnergyProfile.basic(EnergyType.COLORLESS),attack(),abilities,0,null,Map.of("basic",type,"evolved",type),name);}
    public static void main(String[] args){typesAndOwners();suppression();newMoon();sharedTurnLimit();bothTurns();nonAttack();System.out.println("NonPokemonBatch63Test passed: both printed retry sources, all host types and both players, optional whole-sequence retry and discarded old results, wrong chooser rejection, shared source/turn limit across two attacks, turn reset, Tool suppression versus Stadium immunity, source removal and non-attack/confusion exclusion");}
    static void typesAndOwners(){for(var type:EnergyType.values())for(UUID p:List.of(A,B))for(String source:List.of("tool","stadium","both"))for(boolean retry:List.of(false,true)){
        var m=fixture(type,profile(IDS.getLast()),List.of(),null);if(!source.equals("tool"))m.playTrainer(A,hand(m,A,"stadium").instanceId(),null);if(p.equals(B))m.endTurn(A);
        if(!source.equals("stadium"))m.playTrainer(p,hand(m,p,"tool").instanceId(),m.activePokemonView(p).pokemonId());UUID other=p.equals(A)?B:A;
        int n=m.randomTranscript().size();m.attack(p,"hit");check(m.hasPendingEffectChoice()&&m.randomTranscript().size()==n+1,"Retry missing first-coin pause");
        var q=m.effectChoicePrompt(p);GeneratedRuleBatch8Test.reject(()->m.chooseEffectOption(other,q.resolutionId(),q.step(),0));
        choose(m,p,2);check(m.randomTranscript().size()==n+2&&m.activePokemonView(other).damage()==0,"Original coins prematurely committed");
        choose(m,p,retry?0:1);int heads=AttackCoinMatchTest.newHeads(m,retry?n+2:n);
        check(!m.hasPendingEffectChoice()&&m.randomTranscript().size()==n+(retry?4:2)&&m.activePokemonView(other).damage()==(20+30*heads)*2&&uses(m)==(retry?1:0),"Whole sequence retry used wrong results: "+type+p+source);
    }}
    static void suppression(){for(boolean stadium:List.of(false,true))for(boolean pending:List.of(false,true)){
        var m=fixture(EnergyType.FIRE,profile(IDS.getLast()),List.of(),null);UUID host=m.activePokemonView(A).pokemonId();m.playTrainer(A,hand(m,A,"tool").instanceId(),host);if(stadium)m.playTrainer(A,hand(m,A,"stadium").instanceId(),null);
        if(pending){m.attack(A,"hit");var q=m.effectChoicePrompt(A);var rng=m.randomTranscript();GeneratedRuleBatch8Test.reject(()->m.disableToolEffectsByEffect(A,A,host));check(q.equals(m.effectChoicePrompt(A))&&rng.equals(m.randomTranscript()),"Concurrent Tool suppression changed pending coins");choose(m,A,0);check(uses(m)==1,"Retry failed after rejected concurrent effect");continue;}
        m.disableToolEffectsByEffect(A,A,host);m.attack(A,"hit");
        if(stadium){check(m.hasPendingEffectChoice(),"Tool suppression disabled Stadium");choose(m,A,0);check(m.replayTranscript().stream().anyMatch(e->e.operation().startsWith("发动重投场地：")),"Suppressed Tool supplied retry");}
        else check(!m.hasPendingEffectChoice()&&uses(m)==0,"Disabled Tool opened retry");
    }
        for(boolean pending:List.of(false,true)){var m=fixture(EnergyType.WATER,profile(IDS.getLast()),List.of(),null);m.playTrainer(A,hand(m,A,"stadium").instanceId(),null);if(pending){m.attack(A,"hit");var q=m.effectChoicePrompt(A);var rng=m.randomTranscript();GeneratedRuleBatch8Test.reject(()->m.discardStadiumByEffect(A));check(q.equals(m.effectChoicePrompt(A))&&rng.equals(m.randomTranscript()),"Concurrent Stadium removal changed pending coins");choose(m,A,0);check(uses(m)==1,"Retry failed after rejected removal");}else{m.discardStadiumByEffect(A);m.attack(A,"hit");check(uses(m)==0&&!m.hasPendingEffectChoice(),"Departed Stadium supplied retry");}}
    }
    static void newMoon(){var abilities=NonPokemonBatch2Test.DATA.pokemon("sv3_en_092").orElseThrow().abilities();check(!abilities.isEmpty(),"Missing actual New Moon");var m=fixture(EnergyType.PSYCHIC,profile(IDS.getLast()),abilities,"Solrock");m.playTrainer(A,hand(m,A,"stadium").instanceId(),null);m.attack(A,"hit");check(m.hasPendingEffectChoice(),"New Moon blocked player instruction");choose(m,A,0);check(uses(m)==1,"New Moon blocked Stadium retry");}
    static void sharedTurnLimit(){for(boolean firstRetry:List.of(false,true)){
        var m=fixture(EnergyType.GRASS,profile("sv6_en_149"),NonPokemonBatch2Test.DATA.pokemon("sv6_en_018").orElseThrow().abilities(),null);
        UUID host=m.activePokemonView(A).pokemonId();var tool=hand(m,A,"tool");m.playTrainer(A,tool.instanceId(),host);m.playTrainer(A,hand(m,A,"stadium").instanceId(),null);
        m.attack(A,"hit");choose(m,A,2);choose(m,A,firstRetry?0:1);check(m.currentPlayer().equals(A)&&m.phase()==MatchPhase.TURN&&!m.hasPendingEffectChoice(),"Festival fixture did not grant second attack");
        m.discardToolByEffect(A,A,host,tool.instanceId());m.playTrainer(A,hand(m,A,"tool").instanceId(),host);m.healDamageByEffect(A,B,m.activePokemonView(B).pokemonId(),200);m.attack(A,"hit");
        check(m.hasPendingEffectChoice()==!firstRetry,"Replacing Tool reset turn retry or declining consumed it");if(!firstRetry)choose(m,A,0);check(uses(m)==1&&m.currentPlayer().equals(B),"Second attack shared retry limit failed");
    }}
    static void bothTurns(){var m=fixture(EnergyType.DARKNESS,profile(IDS.getLast()),List.of(),null);m.playTrainer(A,hand(m,A,"stadium").instanceId(),null);for(UUID p:List.of(A,B,A)){if(m.activePokemonView(p.equals(A)?B:A).damage()>0)m.healDamageByEffect(p,p.equals(A)?B:A,m.activePokemonView(p.equals(A)?B:A).pokemonId(),200);m.attack(p,"hit");check(m.hasPendingEffectChoice(),"Stadium retry failed player/turn reset");choose(m,p,0);}check(uses(m)==3,"Stadium did not allow both players and next turn");}
    static void nonAttack(){boolean pass=false,fail=false;for(int skip=0;skip<32&&!(pass&&fail);skip++){
        var m=fixture(EnergyType.METAL,profile(IDS.getLast()),List.of(),null);m.playTrainer(A,hand(m,A,"stadium").instanceId(),null);m.flipCoinsByEffect(A,2);check(!m.hasPendingEffectChoice(),"Non-attack coin offered Stadium retry");for(int i=0;i<skip;i++)m.flipCoinsByEffect(A,1);m.applySpecialConditionByEffect(A,A,SpecialCondition.CONFUSED);int n=m.randomTranscript().size();m.attack(A,"hit");
        if(m.hasPendingEffectChoice()){pass=true;check(m.randomTranscript().size()==n+2,"Confusion coin entered attack sequence");choose(m,A,0);check(m.randomTranscript().size()==n+4&&uses(m)==1,"Retry replayed confusion check");}
        else {fail=true;check(m.randomTranscript().size()==n+1&&uses(m)==0&&m.lastAttack().selfDamage()==30,"Confusion failure offered retry");}
    }check(pass&&fail,"Missing confusion branches");}
}
