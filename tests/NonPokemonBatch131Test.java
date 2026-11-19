import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Current supported setup, not an invented legacy opening procedure. */
public final class NonPokemonBatch131Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final String ID="bw6-5_en_019";
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    static TrainerProfile profile(){return NonPokemonBatch2Test.DATA.trainer(ID).orElseThrow();}
    static ServerMatch game(){var c=new GeneratedRuleBatch28Test.Config();c.trainer=profile();c.attack=GeneratedRuleBatch8Test.hit(1000,false);c.defenderAttack=c.attack;return GeneratedRuleBatch28Test.fixture(c);}
    public static void main(String[] args){setup();handAndOtherEffects();schema();for(var level:AiDifficulty.values())inactiveAi(level);System.out.println("NonPokemonBatch131Test passed: First Ticket current coin-before-hand setup (CN practice/international), no out-of-window activation or mutation, normal effect discard, four AI matches completed without inventing legacy setup");}
    static void setup(){
        var sample=game();var definitions=(CardDatabase)AiPracticeScenarios.field(sample,"definitions");var combat=(CombatCatalog)AiPracticeScenarios.field(sample,"combat");
        for(String rules:List.of("standard-international-2026","practice-cn-2026-0116")){
            var decks=new ArrayList<List<CardInstance>>();
            for(UUID p:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(p.getLeastSignificantBits(),i+1),p,i<4?"trainer":"attacker"));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),BundledRules.loadRulesets().get(rules),definitions,combat,new ServerRandom(131));
            check(m.phase()==MatchPhase.COIN_CALL&&m.privateView(A).hand().isEmpty()&&m.privateView(B).hand().isEmpty()&&m.randomTranscript().isEmpty(),"Ticket changed modern setup "+rules);
            var card=decks.getFirst().getFirst();var before=m.clientView(A);
            GeneratedRuleBatch8Test.reject(()->m.playTrainer(A,card.instanceId(),null));check(before.equals(m.clientView(A)),"Ticket played from deck or changed coin phase");
            m.callCoin(A,CoinSide.HEADS);check(m.privateView(A).hand().isEmpty()&&m.privateView(B).hand().isEmpty(),"Ticket caused early dealing");
            UUID winner=m.publicCoinResult()==CoinSide.HEADS?A:B;m.chooseWhoGoesFirst(winner,winner.equals(A));OpeningTestSupport.complete(m,A,B,0);
            check(m.currentPlayer().equals(A)&&m.turnNumber()==1,"Ticket altered chosen first player");
        }
    }
    static void handAndOtherEffects(){for(UUID p:List.of(A,B)){
        var m=game();if(p.equals(B))m.endTurn(A);var ticket=DynamicPassiveModifierTest.hand(m,p,"trainer");var before=m.clientView(p);int turn=m.turnNumber();
        check(!m.canPlayTrainer(p,ticket.instanceId(),null),"Ticket playable after setup");GeneratedRuleBatch8Test.reject(()->m.playTrainer(p,ticket.instanceId(),null));
        check(before.equals(m.clientView(p))&&turn==m.turnNumber(),"Rejected Ticket mutated match");
        m.discardHandCardsByEffect(p,p,List.of(ticket.instanceId()));check(m.privateView(p).discard().contains(ticket),"Inactive Ticket cannot be discarded by an effect");
    }}
    static void inactiveAi(AiDifficulty level){var m=game();for(var p:List.of(A,B))for(var card:m.privateView(p).hand())if(card.definitionId().equals("trainer"))check(!m.canPlayTrainer(p,card.instanceId(),null),"AI offered inactive Ticket");AiCardOperationTest.finish(MatchSession.multiplayer(m,A,B),level);check(m.phase()==MatchPhase.FINISHED,"Inactive Ticket stalled AI "+level);}
    static void schema(){GeneratedRuleBatch8Test.reject(()->new TrainerEffect(TrainerEffect.Opcode.SETUP_FIRST_TICKET,2,0));GeneratedRuleBatch8Test.reject(()->new TrainerProfile(TrainerCategory.SUPPORTER,new TrainerEffect(TrainerEffect.Opcode.SETUP_FIRST_TICKET,1,0)));check(AiEffects.trainer(profile()).roles().isEmpty(),"AI invented an active effect for Ticket");}
}
