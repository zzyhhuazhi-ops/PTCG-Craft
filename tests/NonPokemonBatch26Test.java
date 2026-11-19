import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

public final class NonPokemonBatch26Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static ServerMatch game(String id,EnergyType type,int damage){return GeneratedRuleBatch8Test.fixture(List.of(),type,type,NonPokemonBatch2Test.DATA.trainer(id).orElseThrow(),GeneratedRuleBatch8Test.hit(damage,false));}
    public static void main(String[] args){retaliation();barrier();party();spinner();System.out.println("NonPokemonBatch26Test passed: exact damage threshold and lethal reactions, opponent-turn Tool expiry, typed damage reduction, sleep-only cure/heal and second-player first-turn Energy search");}
    static void retaliation(){
        for(String id:List.of("xy9_en_097","sm11_en_196"))for(int damage:List.of(170,180,500)){
            var m=game(id,EnergyType.GRASS,damage);var tool=NonPokemonBatch2Test.trainer(m);m.playTrainer(A,tool.instanceId(),m.activePokemonView(A).pokemonId());m.endTurn(A);
            check(m.activePokemonView(A).attachedTools().contains(tool),"Tool expired at owner's turn end");m.attack(B,"hit");
            int expected=id.equals("xy9_en_097")?60:damage<180?0:100;check(m.activePokemonView(B).damage()==expected,"Retaliation ignored actual damage threshold or lethal event");NonPokemonBatch2Test.settle(m);
            check(m.privateView(A).discard().contains(tool),"Temporary Tool remained after opponent turn");
        }
        var m=game("sm11_en_196",EnergyType.GRASS,200);var tool=NonPokemonBatch2Test.trainer(m);m.playTrainer(A,tool.instanceId(),m.benchPokemonView(A).getFirst().pokemonId());m.endTurn(A);m.endTurn(B);NonPokemonBatch2Test.settle(m);check(m.privateView(A).discard().contains(tool),"Benched Tool did not expire without attack");
    }
    static void barrier(){
        for(EnergyType type:List.of(EnergyType.METAL,EnergyType.GRASS)){
            var m=game("sm10_en_180",type,100);var tool=NonPokemonBatch2Test.trainer(m);m.playTrainer(A,tool.instanceId(),m.activePokemonView(A).pokemonId());m.endTurn(A);m.attack(B,"hit");NonPokemonBatch2Test.settle(m);
            check(m.activePokemonView(A).damage()==(type==EnergyType.METAL?30:100)&&m.privateView(A).discard().contains(tool),"Metal Core Barrier ignored type, damage or expiry");
            m.endTurn(A);m.attack(B,"hit");NonPokemonBatch2Test.settle(m);check(m.activePokemonView(A).damage()==(type==EnergyType.METAL?130:200),"Discarded Barrier still reduced damage");
        }
    }
    static void party(){
        var m=game("xy9_en_096",EnergyType.GRASS,10);m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);var host=m.activePokemonView(A).pokemonId();m.placeDamageCountersByEffect(A,A,host,5);
        check(!m.canActivateStadium(A),"Party healed an awake Pokemon");m.applySpecialConditionByEffect(A,A,SpecialCondition.ASLEEP);m.applySpecialConditionByEffect(A,A,SpecialCondition.POISONED);
        m.activateStadium(A,m.clientView(A).stadium().instanceId(),m.stadiumEntrySequence());NonPokemonBatch2Test.settle(m);
        check(m.activePokemonView(A).damage()==20&&!m.activePokemonView(A).conditions().contains(SpecialCondition.ASLEEP)&&m.activePokemonView(A).conditions().contains(SpecialCondition.POISONED),"Party cured unrelated status or skipped heal after curing sleep");
        m.applySpecialConditionByEffect(A,A,SpecialCondition.ASLEEP);check(!m.canActivateStadium(A),"Party ignored once-per-player-turn limit");
    }
    static void spinner(){
        for(int stage:List.of(0,1,2)){
            var m=GeneratedRuleBatch8Test.fixture(List.of(),EnergyType.GRASS,EnergyType.GRASS,NonPokemonBatch2Test.DATA.trainer("sm10_en_170").orElseThrow(),GeneratedRuleBatch8Test.hit(10,false),1,Set.of(),true);
            if(stage>0)m.endTurn(A);if(stage>1)m.endTurn(B);UUID owner=stage==1?B:A;
            m.playTrainer(owner,DynamicPassiveModifierTest.hand(m,owner,"trainer").instanceId(),null);var q=m.effectCardPrompt(owner);
            check(q.minimum()==0&&q.maximum()==Math.min(stage==1?3:1,q.candidates().size()),"Spinner opening limit wrong");check(q.candidates().stream().allMatch(c->m.cardDefinition(c.definitionId()).basicEnergy()),"Spinner offered Special Energy");
            var chosen=q.candidates().stream().limit(q.maximum()).toList();m.submitEffectCards(owner,q.resolutionId(),q.step(),chosen.stream().map(c->c.instanceId()).toList());NonPokemonBatch2Test.settle(m);check(m.privateView(owner).hand().containsAll(chosen),"Spinner lost selected Energy");
        }
    }
}
