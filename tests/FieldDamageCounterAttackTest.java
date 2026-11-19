import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Counter-derived attack damage reads the declared public group at the damage checkpoint. */
public final class FieldDamageCounterAttackTest {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B;
    static String count(String target){return "{\"type\":\"damage_per_damage_counter\",\"target\":\""+target+"\",\"amount\":10}";}
    static AttackDefinition attack(String target,String before,String after){
        var raw=JsonParser.parseString("{\"id\":\"hit\",\"nameZh\":\"场上指示物计数\",\"energyCost\":{},\"baseDamage\":60,\"damageType\":\"COLORLESS\",\"effectSupport\":\"SUPPORTED\"}").getAsJsonObject();
        var phases=new ArrayList<TriggeredEffects>();if(!before.isEmpty())phases.add(BeforeDamageAttackTest.binding(EffectTrigger.BEFORE_ATTACK,before));
        phases.add(BeforeDamageAttackTest.binding(EffectTrigger.ON_ATTACK,count(target)));if(!after.isEmpty())phases.add(BeforeDamageAttackTest.binding(EffectTrigger.AFTER_ATTACK,after));
        return BundledCombatData.attack(raw,EffectRegistry.standard()).withEffectProgram(new EffectProgram(phases));
    }
    static ServerMatch game(String target,String before,String after){return BeforeDamageAttackTest.fixture(attack(target,before,after));}
    static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    public static void main(String[]args){zeroAndEmpty();multipleAndOwner();timing();copied();System.out.println("FieldDamageCounterAttackTest passed");}
    static void zeroAndEmpty(){
        var zero=game("ALL_OPPONENT_POKEMON","","");zero.attack(A,"hit");check(zero.lastAttack().finalDamage()==60&&zero.activePokemonView(B).damage()==60,"zero-counter group counted the current hit");
        var empty=game("OPPONENT_BENCH","","");var bench=empty.benchPokemonView(B).getFirst().pokemonId();empty.placeDamageCountersByEffect(A,B,bench,4);empty.knockOutPokemonByEffect(A,B,bench);
        while(empty.phase()==MatchPhase.PRIZE_SELECTION)empty.takePrizeAt(empty.pendingChoicePlayer(),0);
        check(empty.benchPokemonView(B).isEmpty(),"empty Bench fixture failed");empty.attack(A,"hit");check(empty.lastAttack().finalDamage()==60,"empty public group rejected or counted departed Pokemon");
    }
    static void multipleAndOwner(){
        var m=game("ALL_OPPONENT_POKEMON","","");m.endTurn(A);var extra=DynamicPassiveModifierTest.hand(m,B,"basic");m.playBasicPokemonToBench(B,extra.instanceId());m.endTurn(B);
        m.placeDamageCountersByEffect(A,B,m.activePokemonView(B).pokemonId(),2);m.placeDamageCountersByEffect(A,B,m.benchPokemonView(B).getFirst().pokemonId(),3);m.placeDamageCountersByEffect(A,B,m.benchPokemonView(B).getLast().pokemonId(),4);
        m.placeDamageCountersByEffect(A,A,m.activePokemonView(A).pokemonId(),5);m.placeDamageCountersByEffect(A,A,m.benchPokemonView(A).getFirst().pokemonId(),6);
        m.attack(A,"hit");check(m.lastAttack().finalDamage()==150&&m.activePokemonView(B).damage()==170,"count must sum9 opponent counters, not3 Pokemon or own counters");
        var own=game("ALL_OWN_POKEMON","","");own.placeDamageCountersByEffect(A,A,own.activePokemonView(A).pokemonId(),2);own.placeDamageCountersByEffect(A,A,own.benchPokemonView(A).getFirst().pokemonId(),3);own.placeDamageCountersByEffect(A,B,own.activePokemonView(B).pokemonId(),7);own.attack(A,"hit");check(own.lastAttack().finalDamage()==110,"declared own field scope changed to opponent");
    }
    static void timing(){
        var m=game("ALL_OPPONENT_POKEMON","{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":2}","{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":3}");
        m.placeDamageCountersByEffect(A,B,m.activePokemonView(B).pokemonId(),3);var rng=m.randomTranscript();var before=m.privateView(A);
        check(m.activeAttacks(A).stream().anyMatch(a->a.id().equals("hit")),"preflight rejected field counter scope");check(before.equals(m.privateView(A))&&rng.equals(m.randomTranscript()),"preflight mutated state");
        m.attack(A,"hit");check(m.lastAttack().finalDamage()==110&&m.activePokemonView(B).damage()==190,"count must read after BEFORE, before hit and AFTER counters");
    }
    static void copied(){
        var selected=attack("ALL_OPPONENT_POKEMON","","");var m=CopyAttackTest.fixture(selected);UUID actor=CopyAttackTest.A,opponent=CopyAttackTest.B;
        m.placeDamageCountersByEffect(actor,actor,m.activePokemonView(actor).pokemonId(),8);m.placeDamageCountersByEffect(actor,actor,m.benchPokemonView(actor).getFirst().pokemonId(),7);
        m.placeDamageCountersByEffect(actor,opponent,m.activePokemonView(opponent).pokemonId(),3);m.placeDamageCountersByEffect(actor,opponent,m.benchPokemonView(opponent).getFirst().pokemonId(),2);
        CopyAttackTest.pay(m);var choice=CopyAttackTest.choice(m);check(choice.unsupportedReasonZh()==null,"copy preflight rejected public counter group");CopyAttackTest.copy(m,choice,List.of(),List.of());
        check(m.lastAttack().attacker().equals(actor)&&m.lastAttack().finalDamage()==220&&m.activePokemonView(opponent).damage()==250,"copy counted donor opponent or lost actual attacker weakness");
    }
}
