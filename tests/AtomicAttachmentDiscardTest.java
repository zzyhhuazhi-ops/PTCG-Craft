import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Synthetic hosts isolate simultaneous field mutation; no unreviewed card is enabled. */
public final class AtomicAttachmentDiscardTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final String DISCARD="{\"type\":\"discard_tools_and_special_energy\",\"target\":\"ALL_OPPONENT_POKEMON\"}";
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    static void reject(Runnable action){GeneratedRuleBatch8Test.reject(action);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static CardInstance obtain(ServerMatch m,UUID p,String id){return GeneratedRuleBatch96Test.obtain(m,p,id);}
    static CardInstance energy(ServerMatch m,UUID owner,UUID hostOwner,UUID host,String id){
        var c=obtain(m,owner,id);m.attachEnergyByEffect(m.currentPlayer(),owner,EffectSourceZone.HAND,c.instanceId(),hostOwner,host);return c;
    }
    static CardInstance tool(ServerMatch m,UUID p,UUID host){
        var c=obtain(m,p,"tool");m.attachToolByEffect(m.currentPlayer(),p,EffectSourceZone.HAND,c.instanceId(),p,host);return c;
    }
    static AttackDefinition attack(){return PlannedEnergyDiscardTest.program("","",DISCARD);}
    static ServerMatch game(List<AbilityDefinition> abilities){
        return GeneratedRuleBatch8Test.fixture(abilities,EnergyType.WATER,EnergyType.FIGHTING,GeneratedRuleBatch8Test.trainer("swsh5_en_124"),attack());
    }
    public static void main(String[] args){
        physicalCards();originalFieldProtection();hitPointSettlement();copiedActor();closedContext();
        System.out.println("AtomicAttachmentDiscardTest passed: whole-field snapshot, physical Special/Basic distinction, owner discard, HP settlement, copied actor and closed attack phase");
    }
    static void physicalCards(){
        var m=game(List.of());var expected=new ArrayList<CardInstance>();
        for(UUID host:List.of(active(m,B),bench(m,B))){expected.add(tool(m,B,host));expected.add(energy(m,B,B,host,"special"));energy(m,B,B,host,"energy");}
        var foreign=energy(m,A,B,bench(m,B),"special");var own=tool(m,A,active(m,A));var ownEnergy=energy(m,A,A,active(m,A),"special");
        var deck=m.privateView(A).deck();m.attack(A,"hit");
        check(m.privateView(B).discard().containsAll(expected)&&m.privateView(A).discard().contains(foreign),"Discard must preserve physical owners");
        for(var v:List.of(m.activePokemonView(B),m.benchPokemonView(B).getFirst()))
            check(v.attachedTools().isEmpty()&&v.attachedEnergyIds().size()==1,"Basic Energy must survive on each host");
        check(m.activePokemonView(A).attachedTools().contains(own)&&m.activePokemonView(A).attachedEnergyIds().contains(ownEnergy.instanceId()),"Own attachments were touched");
        check(m.privateView(A).deck().equals(deck)&&m.triggerOrderPrompt()==null,"Field discard invented a deck-discard reaction");
        var empty=game(List.of());empty.attack(A,"hit");
        check(empty.currentPlayer().equals(B)&&!empty.hasPendingEffectChoice(),"Empty union must be a legal no-op");
    }
    static void originalFieldProtection(){
        var guard=DiscardAbilityCountTest.passive("fixture-guard","{\"type\":\"passive_effect_guard\",\"target\":\"SELF\",\"source\":\"ATTACK\",\"from\":\"OPPONENT\",\"scope\":\"EFFECTS_ONLY\"}");
        var profile=new PokemonProfile(1000,0,null,null,0,null,0,1,List.of(attack()),List.of(),EnergyType.COLORLESS);
        var m=DiscardAbilityCountTest.game(null,profile,guard);m.endTurn(A);
        UUID source=bench(m,B);
        m.evolve(B,source,obtain(m,B,"global").instanceId());m.switchActiveByEffect(B,B,source);
        var guarded=obtain(m,B,"hide-a");m.playBasicPokemonToBench(B,guarded.instanceId());
        m.endTurn(B);
        var removed=tool(m,B,source);var other=tool(m,B,guarded.instanceId());
        m.attack(A,"hit");
        check(m.privateView(B).discard().containsAll(List.of(removed,other)),"Removing the evolved host's suppression Tool must not reactivate another host's guard halfway through the simultaneous discard");
        m.endTurn(B);var kept=tool(m,B,guarded.instanceId());m.attack(A,"hit");
        check(m.benchPokemonView(B).stream().anyMatch(v->v.attachedTools().contains(kept)),"Reactivated guard must protect on the next attack");
    }
    static void hitPointSettlement(){
        var hp=DynamicPassiveModifierTest.trainer("TOOL","{\"type\":\"hit_points_modifier\",\"target\":\"SELF\",\"amount\":100}");
        var m=DynamicPassiveModifierTest.fixture(hp,DynamicPassiveModifierTest.plainStadium(),EnergyProfile.fixed(EnergyType.WATER,2),attack());
        var a=tool(m,B,active(m,B));var b=tool(m,B,bench(m,B));
        var x=energy(m,B,B,active(m,B),"special");var y=energy(m,B,B,bench(m,B),"special");
        m.placeDamageCountersByEffect(A,B,active(m,B),30);m.placeDamageCountersByEffect(A,B,bench(m,B),30);
        m.attack(A,"hit");
        check(m.privateView(B).discard().containsAll(List.of(a,b,x,y)),"HP loss settled before the full attachment union was detached");
        check(m.privateView(B).discard().stream().filter(c->c.definitionId().equals("basic")).count()==2,"Both lethal HP changes must settle");
    }
    static void copiedActor(){
        var m=CopyAttackTest.fixture(CopyAttackTest.copier(),attack(),EnergyType.COLORLESS,0);
        CopyAttackTest.pay(m);var c=obtain(m,CopyAttackTest.B,"energy");
        m.attachEnergyByEffect(CopyAttackTest.A,CopyAttackTest.B,EffectSourceZone.HAND,c.instanceId(),CopyAttackTest.B,active(m,CopyAttackTest.B));
        CopyAttackTest.copy(m,CopyAttackTest.choice(m),List.of(),List.of());
        check(m.currentPlayer().equals(CopyAttackTest.B)&&m.activePokemonView(CopyAttackTest.B).attachedEnergyIds().contains(c.instanceId()),"Copy used provider identity or discarded Basic Energy");
    }
    static void closedContext(){
        AttackProgramRules.validate(attack());
        AttackProgramRules.validate(PlannedEnergyDiscardTest.program(DISCARD,"",""));
        reject(()->AttackProgramRules.validate(PlannedEnergyDiscardTest.program("",DISCARD,"")));
        for(String bad:List.of(DISCARD.replace("ALL_OPPONENT_POKEMON","SELF"),DISCARD.replace("}",",\"amount\":1}"),DISCARD.replace("}",",\"conditions\":[{\"type\":\"IS_ACTIVE\"}]}"),DISCARD.replace("}",",\"effects\":[{\"type\":\"draw\",\"amount\":1}]}")))
            reject(()->AttackProgramRules.validate(PlannedEnergyDiscardTest.program("","",bad)));
        ResumableProgramRules.validate(new EffectProgram(List.of(BeforeDamageAttackTest.binding(EffectTrigger.ON_CARD_PLAYED,DISCARD))),false,EffectTrigger.ON_CARD_PLAYED);
        for(var trigger:List.of(EffectTrigger.ONCE_PER_TURN,EffectTrigger.PASSIVE))
            reject(()->ResumableProgramRules.validate(new EffectProgram(List.of(BeforeDamageAttackTest.binding(trigger,DISCARD))),trigger!=EffectTrigger.ON_CARD_PLAYED,trigger));
        var m=game(List.of());var before=m.clientView(A);var rng=m.randomTranscript();
        reject(()->m.executeEffectProgramByRule(A,active(m,A),new EffectProgram(List.of(BeforeDamageAttackTest.binding(EffectTrigger.AFTER_ATTACK,DISCARD))),EffectTrigger.AFTER_ATTACK));
        check(before.equals(m.clientView(A))&&rng.equals(m.randomTranscript()),"Rule invocation mutated state without an attack checkpoint");
    }
}
