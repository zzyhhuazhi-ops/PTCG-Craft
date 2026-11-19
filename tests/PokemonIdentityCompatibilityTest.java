import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;
import com.google.gson.Gson;

/** Current physical-root compatibility must capture canonical, stable field identities. */
public final class PokemonIdentityCompatibilityTest {
    static void check(boolean b,String s){GeneratedRuleBatch8Test.check(b,s);}
    public static void main(String[] args){energyAlias();sourceContinuation();System.out.println("PokemonIdentityCompatibilityTest passed: physical-root attachment captures canonical SELF/event targets; physical replacement retains paused actual-source effects and conceals the returned hidden card");}
    static void energyAlias(){
        UUID a=EnergyTriggeredAbilityTest.A;
        var ability=EnergyTriggeredAbilityTest.ability("alias","SELF",true,"NONE","{\"type\":\"damage_counter\",\"target\":\"TRIGGER_POKEMON\",\"amount\":1}");
        var m=EnergyTriggeredAbilityTest.fixture(List.of(ability),List.of(),null);
        UUID logical=m.benchPokemonView(a).getFirst().pokemonId(),physical=EventTargetBindingTest.field(m).baseCard(a,logical).instanceId();
        var energy=m.privateView(a).hand().stream().filter(c->c.definitionId().equals("energy")).findFirst().orElseThrow();
        m.attachEnergy(a,energy.instanceId(),physical);var prompt=m.triggerOrderPrompt();
        check(prompt!=null&&prompt.entries().stream().anyMatch(e->e.pokemonId().equals(logical)),"Physical-root compatibility lost its own SELF attachment event");
        check(prompt.entries().stream().allMatch(e->e.triggerPokemon().pokemonId().equals(logical)),"Captured attachment subject is a physical card instead of canonical Pokemon");
        DamageTriggeredAbilityTest.finishTriggers(m);
        check(m.benchPokemonView(a).getFirst().damage()==10,"Captured attachment subject did not receive exactly one reaction");
    }
    static void sourceContinuation(){
        UUID a=BeforeDamageAttackTest.A,b=BeforeDamageAttackTest.B;
        var attack=BeforeDamageAttackTest.attack("","","{\"type\":\"choice\",\"effects\":[{\"type\":\"heal\",\"target\":\"SELF\",\"amount\":10},{\"type\":\"draw\",\"amount\":1}]}");
        var m=BeforeDamageAttackTest.fixture(attack);var f=EventTargetBindingTest.field(m);UUID logical=m.activePokemonView(a).pokemonId();
        long life=f.pokemonEntrySequence(a,logical);m.placeDamageCountersByEffect(a,a,logical,3);m.attack(a,attack.id());var choice=m.effectChoicePrompt(a);
        var next=m.privateView(a).hand().stream().filter(c->c.definitionId().equals("evolved")).findFirst().orElseThrow();
        GeneratedRuleBatch49Test.zone(m,a,"hand").remove(next);var returned=f.replaceTopCardByEffect(a,logical,next);GeneratedRuleBatch49Test.zone(m,a,"deck").add(returned);
        var session=MatchSession.multiplayer(m,a,b);
        check(!new Gson().toJson(session.view(b,b)).contains(returned.instanceId().toString()),"Returned hidden deck card leaked through public field or continuation DTO");
        m.chooseEffectOption(a,choice.resolutionId(),choice.step(),0);
        check(m.activePokemonView(a).pokemonId().equals(logical)&&m.activePokemonView(a).topCardId().equals("evolved")&&m.activePokemonView(a).damage()==20&&f.pokemonEntrySequence(a,logical)==life,"Paused source rebound, renewed entry or dropped original SELF effect after replacement");
        check(m.lastAttack().finalDamage()==80&&m.currentPlayer().equals(b),"Replacement repeated damage or prevented attack completion");
    }
}
