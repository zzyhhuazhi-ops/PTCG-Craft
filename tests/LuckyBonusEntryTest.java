import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class LuckyBonusEntryTest {
    static final UUID A=WillCoinMatchTest.A,B=WillCoinMatchTest.B;
    static void check(boolean ok,String why){WillCoinMatchTest.check(ok,why);}
    static AbilityDefinition lucky(){return NonPokemonBatch2Test.DATA.pokemon("sv3-5_en_113").orElseThrow().ability("lucky-bonus").orElseThrow();}
    static GeneratedRuleBatch28Test.Config config(){
        var c=new GeneratedRuleBatch28Test.Config();c.trainer=WillNativeEntryTest.will();
        c.attack=GeneratedRuleBatch8Test.hit(1000,false);c.defenderAttack=c.attack;c.ownAbility=lucky();
        c.restoredProfiles=Map.of("defender",new PokemonProfile(1000,1,null,null,0,null,0,1,List.of(c.attack),List.of(lucky()),EnergyType.WATER));
        return c;
    }
    static void stadium(ServerMatch m,UUID player,String id){try{
        var field=ServerMatch.class.getDeclaredField("combat");field.setAccessible(true);var old=(CombatCatalog)field.get(m);
        var trainers=new HashMap<>(old.trainerProfiles());trainers.put(id,NonPokemonBatch2Test.DATA.trainer(id).orElseThrow());
        field.set(m,new CombatCatalog(old.pokemonProfiles(),Map.of("energy",EnergyType.COLORLESS),trainers));
        var defs=new ArrayList<>(CedricQuizTest.database(m).all());defs.add(NonPokemonBatch133Test.PRINTINGS.find(id).orElseThrow());
        CedricQuizTest.replace(m,new CardDatabase(defs));
        var card=NonPokemonBatch133Test.put(m,player,id);m.playTrainer(player,card.instanceId(),null);
    }catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    static CardInstance begin(ServerMatch m,UUID owner){
        var card=NonPokemonBatch128Test.prize(m,owner,0,owner.equals(A)?"attacker":"defender");
        NonPokemonBatch128Test.prize(m,owner,1,"energy");WillCoinMatchTest.play(m,owner);
        m.attack(owner,"hit");m.takePrizeAt(owner,0);return card;
    }
    static void sources(){for(String source:List.of("ANY","PRIZE","HAND","DECK","DISCARD"))for(boolean heads:List.of(false,true)){
        var c=config();c.source=NonPokemonBatch130Test.watcher(source);var m=GeneratedRuleBatch28Test.fixture(c);
        m.playBasicPokemonToBench(A,NonPokemonBatch127Test.hand(m,A,"source").instanceId());
        var card=begin(m,A);int held=m.privateView(A).hand().size(),random=m.randomTranscript().size();
        m.decideLuckyBonus(A,true);check(m.randomTranscript().size()==random&&m.privateView(A).hand().size()==held,"Entry trigger interrupted coin decision");
        WillCoinMatchTest.choose(m,A,heads);
        if(heads){check(m.privateView(A).hand().size()==held,"Entry trigger preceded additional Prize");m.takePrizeAt(A,0);}
        NonPokemonBatch103Test.settle(m);
        check(m.privateView(A).hand().size()==held+(heads?1:0)+(Set.of("ANY","PRIZE").contains(source)?1:0),"Wrong prize-entry source or duplicate trigger: "+source);
        check(!m.privateView(A).hand().contains(card)&&m.currentPlayer().equals(B),"Prize Pokemon entered hand or parent attack did not end");
    }}
    static void stadiums(){for(UUID owner:List.of(A,B))for(String id:List.of("swsh10_en_142","me1_en_127"))for(boolean dark:List.of(false,true))for(boolean heads:List.of(false,true)){
        var c=config();if(dark){c.ownTypes=Set.of(EnergyType.DARKNESS);c.defenderTypes=Set.of(EnergyType.DARKNESS);}
        var m=GeneratedRuleBatch28Test.fixture(c);if(owner.equals(B))m.endTurn(A);stadium(m,owner,id);
        var card=begin(m,owner);m.decideLuckyBonus(owner,true);WillCoinMatchTest.choose(m,owner,heads);
        if(heads)m.takePrizeAt(owner,0);NonPokemonBatch103Test.settle(m);
        check(BenchToActiveEnergyAbilityTest.board(m).pokemonView(owner,card.instanceId()).damage()==(id.equals("me1_en_127")&&!dark?20:0),"Stadium wrong source/type/seat: "+id+" "+owner+" "+dark);
    }}
    // A 20-HP synthetic Lucky host verifies nesting; real Chansey retains its printed HP.
    static void nestedKnockout(){for(boolean heads:List.of(false,true)){
        var c=config();c.ownHp=20;c.restoredProfiles=Map.of();c.defenderPrizes=2;
        var m=GeneratedRuleBatch28Test.fixture(c);stadium(m,A,"me1_en_127");
        var card=NonPokemonBatch128Test.prize(m,A,0,"attacker");
        var ordinary=NonPokemonBatch128Test.prize(m,A,1,"trainer");
        var extra=NonPokemonBatch128Test.prize(m,A,2,"energy");
        WillCoinMatchTest.play(m,A);m.attack(A,"hit");m.takePrizeAt(A,0);m.takePrizeAt(A,0);m.decideLuckyBonus(A,true);
        check(!m.privateView(A).discard().contains(card),"Entrant knocked out before completing its Ability");
        WillCoinMatchTest.choose(m,A,heads);
        if(heads){check(m.pendingChoicePlayer().equals(A)&&!m.privateView(A).discard().contains(card),"Entry KO interrupted bonus award");m.takePrizeAt(A,0);}
        check(m.phase()==MatchPhase.PRIZE_SELECTION&&m.pendingChoicePlayer().equals(B)&&m.privateView(A).discard().contains(card),"Entry KO did not create nested opponent award");
        check(m.privateView(A).hand().contains(ordinary)&&(!heads||m.privateView(A).hand().contains(extra)),"Parent or extra Prize lost");
        m.takePrizeAt(B,0);check(m.phase()==MatchPhase.CHOOSE_REPLACEMENT&&m.pendingChoicePlayer().equals(B),"Parent promotion lost");
        NonPokemonBatch103Test.settle(m);check(m.currentPlayer().equals(B)&&m.turnNumber()==4,"Parent attack resumed more than once");
    }}
    public static void main(String[] args){sources();stadiums();nestedKnockout();System.out.println("LuckyBonusEntryTest passed: PRIZE vs HAND/DECK/DISCARD entry, delayed reactions after coin/additional award, actual Gapejaw Bog/Risky Ruins both seats and types, nested entry KO and parent promotion.");}
}
