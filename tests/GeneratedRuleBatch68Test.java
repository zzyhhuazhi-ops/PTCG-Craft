import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Both complete printed Photon Cord cards, including native Thunder cost and recoil. */
public final class GeneratedRuleBatch68Test {
    static final UUID A=KnockoutEnergyTransferTest.A,B=KnockoutEnergyTransferTest.B;
    static final List<String> PRINTS=List.of("me5_en_028","mebsp_en_082");
    static void check(boolean ok,String why){KnockoutEnergyTransferTest.check(ok,why);}
    static KnockoutEnergyTransferTest.Config config(String id){var c=new KnockoutEnergyTransferTest.Config();c.id=id;c.definition=BundledRules.loadCardDefinitions().find(id).orElseThrow();c.profile=BundledCombatData.load().pokemon(id).orElseThrow();return c;}
    static UUID active(ServerMatch m,UUID owner){return KnockoutEnergyTransferTest.active(m,owner);}
    static void atomic(ServerMatch m,Runnable r){GeneratedRuleBatch44Test.assertRejectedAtomic(m,r);}
    public static void main(String[] args){printed();transfer();thunder();copy();System.out.println("GeneratedRuleBatch68Test passed: both complete actual Miraidon prints, printed metadata and LL Thunder90/self30, native KO transfer before discard/prizes, zero/two choices, unchanged recoil/no own-KO trigger, real Thievul copy and server authority/replay");}
    static void printed(){for(String id:PRINTS){var c=config(id);var p=c.profile;
        check(c.definition.effectSupport()==EffectSupport.SUPPORTED&&c.definition.basicPokemon()&&c.definition.copyLimit()==4&&c.definition.regulationMark().equals("J"),"Actual metadata changed "+id);
        check(p.hitPoints()==120&&p.retreatCost()==1&&p.evolvesFromDefinitionId()==null&&p.pokemonType()==EnergyType.LIGHTNING&&p.weaknessType()==EnergyType.FIGHTING&&p.weaknessMultiplier()==2&&p.resistanceType()==null&&p.prizeCards()==1,"Printed Pokemon profile changed");
        var a=p.attack("thunder").orElseThrow();check(a.baseDamage()==90&&a.selfDamage()==30&&a.energyCost().equals(Map.of(EnergyType.LIGHTNING,2))&&a.effectProgram().isEmpty(),"Thunder cost/recoil changed");
        check(p.abilities().size()==1,"Unexpected ability count");var b=p.abilities().getFirst();check(b.eventPolicy() instanceof KnockoutTriggerPolicy k&&k.cause()==KnockoutTriggerPolicy.Cause.OPPONENT_ATTACK_DAMAGE&&k.activeOnly()&&!k.optional()&&k.watch()==EventPokemonScope.SELF,"Photon Cord wrong event policy");
        var e=b.effectProgram().triggers().getFirst().effects().getFirst();check(e.type().equals("move_energy")&&e.target()==EffectTarget.SELF&&e.arguments().equals(Map.of("amount","2","minimum","0","energy_type","LIGHTNING","card_kind","BASIC","count_mode","CARDS","destination_scope","OWN_BENCH")),"Photon Cord transfer scope changed");
    }}
    static void transfer(){for(String id:PRINTS)for(int count:List.of(0,2)){
        var m=KnockoutEnergyTransferTest.game(config(id));var cards=KnockoutEnergyTransferTest.energy(m,3);UUID source=active(m,B),physical=PokemonIdentityTestSupport.physical(m,B,source),host=KnockoutEnergyTransferTest.bench(m,B);var rng=m.randomTranscript();m.attack(A,"hit");
        check(m.effectCardPrompt(B).candidates().size()==3&&m.privateView(B).discard().stream().noneMatch(c->c.instanceId().equals(physical))&&m.privateView(A).prizes().size()==6,"Actual source discarded before Energy choice");KnockoutEnergyTransferTest.choose(m,cards.subList(0,count),host);
        check(m.benchPokemonView(B).stream().filter(p->p.pokemonId().equals(host)).findFirst().orElseThrow().attachedEnergyIds().size()==count&&m.privateView(B).discard().stream().anyMatch(c->c.instanceId().equals(physical))&&m.randomTranscript().equals(rng),"Actual transfer lost source or used randomness");KnockoutEnergyTransferTest.finish(m);
    }}
    static void thunder(){for(String id:PRINTS)for(boolean lethal:List.of(false,true)){
        var m=KnockoutEnergyTransferTest.game(config(id));m.endTurn(A);atomic(m,()->m.attack(B,"thunder"));KnockoutEnergyTransferTest.attach(m,B,active(m,B),"water");KnockoutEnergyTransferTest.attach(m,B,active(m,B),"water");atomic(m,()->m.attack(B,"thunder"));var cards=KnockoutEnergyTransferTest.energy(m,2);if(lethal)m.placeDamageCountersByEffect(B,B,active(m,B),9);
        var session=MatchSession.multiplayer(m,A,B);var view=session.view(B,B);var option=view.options().stream().filter(o->o.labelZh().contains("打雷")).findFirst().orElseThrow();session.submit(B,B,view.revision(),option.index());atomic(m,()->session.submit(B,B,view.revision(),option.index()));
        check(m.lastAttack().finalDamage()==90&&m.lastAttack().selfDamage()==30&&m.activePokemonView(A).damage()==90&&!m.hasPendingCardSelection(),"Actual Thunder lost damage/recoil or triggered own KO");
        if(lethal){check(m.privateView(B).discard().containsAll(cards),"Own recoil rescued Energy");m.takePrizeAt(A,0);m.chooseReplacement(B,KnockoutEnergyTransferTest.bench(m,B));}else check(m.activePokemonView(B).damage()==30,"Nonlethal recoil missing");
        check(m.phase()==MatchPhase.TURN&&m.currentPlayer().equals(A),"Thunder failed to finish turn");
    }}
    static void copy(){for(String id:PRINTS){var m=GeneratedRuleBatch63Test.game(GeneratedRuleBatch63Test.THIEVUL,id,EnergyType.DARKNESS);GeneratedRuleBatch63Test.pay(m,"basic_darkness",2);ConditionalCopyAttackTest.empty(m,GeneratedRuleBatch63Test.A);var c=m.copyAttackOptions(GeneratedRuleBatch63Test.A,"skill-thief").stream().filter(x->x.attackId().equals("thunder")).findFirst().orElseThrow();m.copyAttackWithEnergyDiscards(GeneratedRuleBatch63Test.A,"skill-thief",c.pokemonId(),c.entrySequence(),c.attackId(),List.of(),List.of());check(m.lastAttack().finalDamage()==90&&m.lastAttack().selfDamage()==30&&m.activePokemonView(GeneratedRuleBatch63Test.A).damage()==30,"Copied Thunder used provider instead of attacking source");}}
}
