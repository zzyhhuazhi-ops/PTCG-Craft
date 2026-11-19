import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

public final class WillAiChoiceTest {
    static final UUID A=WillCoinMatchTest.A,B=WillCoinMatchTest.B;
    static void check(boolean ok,String why){WillCoinMatchTest.check(ok,why);}
    static void decide(ServerMatch m,UUID owner,AiDifficulty level,ActionType expected){
        var session=MatchSession.multiplayer(m,A,B);var input=AiPracticeScenarios.view(session,owner);
        int rng=m.randomTranscript().size();var choice=AiPolicy.choose(input,73,Set.of(),level).orElseThrow();
        var option=input.snapshot().options().get(choice.option());
        check(option.type()==expected,"Wrong strategic coin side: "+level+" expected "+expected+" got "+option.type());
        Collections.reverse(NonPokemonBatch127Test.zone(m,NonPokemonBatch127Test.other(owner),"hand"));
        Collections.reverse(NonPokemonBatch127Test.zone(m,NonPokemonBatch127Test.other(owner),"deck"));
        check(choice.equals(AiPolicy.choose(AiPracticeScenarios.view(session,owner),73,Set.of(),level).orElseThrow()),"Coin strategy depends on hidden order");
        check(m.randomTranscript().size()==rng,"AI consumed game RNG");
        session.submit(owner,owner,choice.revision(),choice.option());
    }
    public static void main(String[] args){
        for(var level:AiDifficulty.values())for(UUID owner:List.of(A,B))for(String id:List.of("sm8_en_142","bw1_en_091")){
            var c=new GeneratedRuleBatch28Test.Config();c.trainer=NonPokemonBatch132Test.profile("sm12_en_208");
            c.attack=NonPokemonBatch2Test.DATA.pokemon(id).orElseThrow().attacks().getLast();c.defenderAttack=c.attack;
            c.energyType=id.equals("sm8_en_142")?EnergyType.FAIRY:EnergyType.COLORLESS;
            var m=GeneratedRuleBatch28Test.fixture(c);if(owner.equals(B))m.endTurn(A);
            int cost=c.attack.energyCost().values().stream().mapToInt(Integer::intValue).sum();
            for(int i=0;i<cost;i++){var card=NonPokemonBatch127Test.hand(m,owner,"energy");m.attachEnergyByEffect(owner,owner,EffectSourceZone.HAND,card.instanceId(),owner,m.activePokemonView(owner).pokemonId());}
            WillCoinMatchTest.play(m,owner);m.attack(owner,c.attack.id());
            decide(m,owner,level,id.equals("sm8_en_142")?ActionType.COIN_TAILS:ActionType.COIN_HEADS);
            if(id.equals("sm8_en_142"))check(m.activePokemonView(NonPokemonBatch127Test.other(owner)).conditions().contains(SpecialCondition.PARALYZED),"Dedenne did not inflict chosen paralysis");
            else check(m.activePokemonView(owner).damage()==0,"Bouffalant took avoidable recoil");
        }
        for(var level:AiDifficulty.values()){
            var c=new GeneratedRuleBatch28Test.Config();
            c.trainer=WillCoinMatchTest.trainer(WillCoinMatchTest.WILL+",{\"type\":\"coin_flip\",\"flips\":1,\"heads\":[{\"type\":\"draw\",\"amount\":3}],\"tails\":[{\"type\":\"draw\",\"amount\":1}]}");
            var m=GeneratedRuleBatch28Test.fixture(c);var deck=NonPokemonBatch127Test.zone(m,A,"deck");
            var removed=new ArrayList<>(deck.subList(3,deck.size()));deck.removeAll(removed);NonPokemonBatch127Test.zone(m,A,"discard").addAll(removed);
            WillCoinMatchTest.play(m,A);decide(m,A,level,ActionType.COIN_TAILS);
            check(m.privateView(A).deck().size()==2,"Coin draw exhausted avoidable deck resources");
        }
        System.out.println("WillAiChoiceTest passed: actual Dedenne paralysis vs confusion, Bouffalant recoil, both seats/four tiers, limited-deck draw conservation, hidden-order and game-RNG isolation.");
    }
}
