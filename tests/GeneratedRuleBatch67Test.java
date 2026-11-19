import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Actual complete Jynx and older identical-text Grimer, with unchanged printed costs and other attacks. */
public final class GeneratedRuleBatch67Test {
    static final UUID A=GeneratedRuleBatch63Test.A,B=GeneratedRuleBatch63Test.B;
    static final String JYNX="me5_en_032",GRIMER="sv10_en_123";
    static void check(boolean b,String why){GeneratedRuleBatch8Test.check(b,why);}
    static String attack(String id){return id.equals(JYNX)?"intense-kiss":"corrosive-sludge";}
    static ServerMatch game(String id){return GeneratedRuleBatch63Test.game(id);}
    static UUID active(ServerMatch m,UUID p){return GeneratedRuleBatch63Test.active(m,p);}
    static void pay(ServerMatch m,String id){GeneratedRuleBatch63Test.pay(m,id.equals(JYNX)?"wild":"basic_darkness",id.equals(JYNX)?1:2);}
    static void atomic(ServerMatch m,Runnable r){GeneratedRuleBatch44Test.assertRejectedAtomic(m,r);}
    public static void main(String[] args){printed();nativeUse();costAndAuthority();clearAndLeave();copied();psyBolt();System.out.println("GeneratedRuleBatch67Test passed: complete actual Jynx/Team Rocket Grimer, printed costs and zero damage, delayed original-target discard without prizes, cleanup/source departure, replacement before draw, server authority/stale replay, real Thievul copy and unchanged Psy Bolt");}
    static void printed(){for(String id:List.of(JYNX,GRIMER)){
        var d=GeneratedRuleBatch63Test.DATABASE.find(id).orElseThrow();var p=GeneratedRuleBatch63Test.BUNDLED.pokemon(id).orElseThrow();boolean j=id.equals(JYNX);
        check(d.effectSupport()==EffectSupport.SUPPORTED&&d.basicPokemon()&&d.copyLimit()==4&&d.regulationMark().equals(j?"J":"I"),"Actual metadata changed "+id);
        check(p.hitPoints()==(j?100:80)&&p.retreatCost()==(j?1:3)&&p.evolvesFromDefinitionId()==null&&p.prizeCards()==1&&p.pokemonType()==(j?EnergyType.PSYCHIC:EnergyType.DARKNESS)&&p.weaknessType()==(j?EnergyType.DARKNESS:EnergyType.FIGHTING)&&p.weaknessMultiplier()==2&&p.resistanceType()==(j?EnergyType.FIGHTING:null)&&p.resistanceAmount()==(j?30:0),"Actual printed profile changed "+id);
        var a=p.attack(attack(id)).orElseThrow();check(a.baseDamage()==0&&a.energyCost().equals(j?Map.of(EnergyType.PSYCHIC,1):Map.of(EnergyType.DARKNESS,1,EnergyType.COLORLESS,1)),"Actual delayed attack cost/damage wrong");
        var bindings=a.effectProgram().triggers();check(bindings.size()==1&&bindings.getFirst().trigger()==EffectTrigger.AFTER_ATTACK&&bindings.getFirst().effects().size()==1,"Delay scheduled in wrong phase");
        var e=bindings.getFirst().effects().getFirst();var delay=DelayedEffectProgram.from(e);check(e.target()==EffectTarget.OPPONENT_ACTIVE&&delay.when()==DelayedEffectProgram.When.OPPONENT_NEXT_TURN_END&&e.effects().getFirst().type().equals("discard_pokemon")&&e.effects().getFirst().target()==EffectTarget.TRIGGER_POKEMON,"Delayed body must bind exact original defender");
    }}
    static void finish(ServerMatch m,UUID target,int turn,int size){
        check(m.phase()==MatchPhase.CHOOSE_REPLACEMENT&&m.currentPlayer().equals(B)&&m.turnNumber()==turn&&m.privateView(A).hand().size()==size,"Discard replacement must precede next-turn draw");
        check(m.privateView(A).prizes().size()==6&&m.privateView(B).prizes().size()==6,"Discard awarded a Prize");
        UUID replacement=m.benchPokemonView(B).getFirst().pokemonId();atomic(m,()->m.chooseReplacement(A,replacement));m.chooseReplacement(B,replacement);
        check(m.currentPlayer().equals(A)&&m.turnNumber()==turn+1&&m.privateView(A).hand().size()==size+1&&!active(m,B).equals(target),"Replacement failed exact next-turn transition");
    }
    static void nativeUse(){for(String id:List.of(JYNX,GRIMER)){
        var m=game(id);pay(m,id);UUID target=active(m,B),physical=PokemonIdentityTestSupport.physical(m,B,target);
        var energy=GeneratedRuleBatch63Test.attach(m,B,target,"wild");var prizes=m.privateView(A).prizes();var rng=m.randomTranscript();m.attack(A,attack(id));
        check(m.lastAttack().finalDamage()==0&&active(m,B).equals(target)&&m.activePokemonView(B).continuousEffectZh().contains("延迟")&&m.privateView(A).prizes().equals(prizes),"Actual delay removed or damaged too early");
        int turn=m.turnNumber(),size=m.privateView(A).hand().size();m.endTurn(B);check(m.privateView(B).discard().stream().anyMatch(c->c.instanceId().equals(physical))&&m.privateView(B).discard().contains(energy)&&m.randomTranscript().equals(rng),"Actual full-stack discard missed card or consumed random");finish(m,target,turn,size);
    }}
    static void costAndAuthority(){for(String id:List.of(JYNX,GRIMER)){
        var m=game(id);atomic(m,()->m.attack(A,attack(id)));GeneratedRuleBatch63Test.pay(m,"basic_darkness",1);atomic(m,()->m.attack(A,attack(id)));pay(m,id);
        atomic(m,()->m.attack(B,attack(id)));var session=MatchSession.multiplayer(m,A,B);var view=session.view(A,A);var label=id.equals(JYNX)?"强烈之吻":"侵蚀污泥";var option=view.options().stream().filter(o->o.labelZh().contains(label)).findFirst().orElseThrow();session.submit(A,A,view.revision(),option.index());atomic(m,()->session.submit(A,A,view.revision(),option.index()));m.endTurn(B);check(m.phase()==MatchPhase.CHOOSE_REPLACEMENT,"Server option did not schedule actual effect");
    }}
    static void clearAndLeave(){for(String id:List.of(JYNX,GRIMER))for(boolean sourceLeaves:List.of(false,true)){
        var m=game(id);pay(m,id);UUID target=active(m,B),source=active(m,A);m.attack(A,attack(id));
        if(sourceLeaves){m.movePokemonByEffect(B,A,source,LeavePlayDestination.HAND);m.chooseReplacement(A,m.benchPokemonView(A).getFirst().pokemonId());}
        else{m.switchActiveByEffect(B,B,m.benchPokemonView(B).getFirst().pokemonId());m.switchActiveByEffect(B,B,target);}
        m.endTurn(B);if(sourceLeaves)check(m.phase()==MatchPhase.CHOOSE_REPLACEMENT,"Already-applied actual effect depended on original attacker");else check(m.currentPlayer().equals(A)&&active(m,B).equals(target),"Actual delay survived a switch");
    }}
    static void copied(){for(String id:List.of(JYNX,GRIMER)){
        var m=GeneratedRuleBatch63Test.game(GeneratedRuleBatch63Test.THIEVUL,id,EnergyType.DARKNESS);GeneratedRuleBatch63Test.pay(m,"basic_darkness",2);ConditionalCopyAttackTest.empty(m,A);
        var c=m.copyAttackOptions(A,"skill-thief").stream().filter(x->x.attackId().equals(attack(id))).findFirst().orElseThrow();m.copyAttackWithEnergyDiscards(A,"skill-thief",c.pokemonId(),c.entrySequence(),c.attackId(),List.of(),List.of());
        check(m.currentPlayer().equals(B)&&m.lastAttack().finalDamage()==0,"Copied actual delay declaration wrong");m.endTurn(B);check(m.phase()==MatchPhase.CHOOSE_REPLACEMENT&&m.pendingChoicePlayer().equals(B)&&m.privateView(A).prizes().size()==6,"Copied delayed effect retained provider's owner or awarded prizes");
    }}
    static void psyBolt(){var p=GeneratedRuleBatch63Test.BUNDLED.pokemon(JYNX).orElseThrow();var a=p.attack("psy-bolt").orElseThrow();check(a.baseDamage()==50&&a.energyCost().equals(Map.of(EnergyType.PSYCHIC,1,EnergyType.COLORLESS,1))&&a.effectProgram().isEmpty(),"Other printed attack changed");var m=game(JYNX);pay(m,JYNX);atomic(m,()->m.attack(A,"psy-bolt"));GeneratedRuleBatch63Test.pay(m,"basic_darkness",1);int rng=m.randomTranscript().size();m.attack(A,"psy-bolt");check(m.lastAttack().finalDamage()==50&&m.activePokemonView(B).damage()==50&&m.randomTranscript().size()==rng+1&&m.activePokemonView(B).conditions().contains(SpecialCondition.PARALYZED)==(m.randomTranscript().get(rng).result()==0)&&!m.activePokemonView(B).continuousEffectZh().contains("延迟"),"Psy Bolt inherited delayed discard or lost coin");}
}
