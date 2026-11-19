import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Both actual Thievul prints retain full printed programs and costs; neutral 500HP hosts isolate copy semantics. */
public final class GeneratedRuleBatch62Test {
    static final UUID A=TemporaryReceivedDamageTest.A,B=TemporaryReceivedDamageTest.B;
    static final List<String> IDS=List.of("me5_en_054","me5_en_092");
    static void check(boolean b,String why){GeneratedRuleBatch8Test.check(b,why);}
    static AttackDefinition attack(String id,String name){return GeneratedRuleBatch57Test.attack(id,name);}
    static ServerMatch game(AttackDefinition a,AttackDefinition reply){return TemporaryReceivedDamageTest.game(a,reply);}
    static void pay(ServerMatch m,UUID p,AttackDefinition a){TemporaryReceivedDamageTest.pay(m,p,a);}
    static void empty(ServerMatch m){ConditionalCopyAttackTest.empty(m,A);}
    public static void main(String[] args){printed();declarations();actualCopiedSelf();System.out.println("GeneratedRuleBatch62Test passed: both actual Thievul prints and complete printed costs/profiles, empty/nonempty Hand with opponent Active/Bench copy, insufficient cost rejection, unchanged Sharp Fang and copied actual Mabosstiff self modifier on the actual actor");}
    static void printed(){var db=BundledRules.loadCardDefinitions();for(String id:IDS){var p=BundledCombatData.load().pokemon(id).orElseThrow();var a=attack(id,"skill-thief");var second=attack(id,"sharp-fang");
        check(db.find(id).orElseThrow().effectSupport()==EffectSupport.SUPPORTED&&p.attacks().size()==2&&p.abilities().isEmpty(),"Actual card incomplete");
        check(p.hitPoints()==100&&p.retreatCost()==1&&p.evolvesFromDefinitionId().equals("me5_en_053")&&p.pokemonType()==EnergyType.DARKNESS,"Printed metadata changed");
        check(a.baseDamage()==0&&a.energyCost().equals(Map.of(EnergyType.COLORLESS,2))&&CopyAttackProgram.sourceScope(a)==EffectTarget.ALL_OPPONENT_POKEMON,"Skill Thief printed cost/scope changed");
        check(CopyAttackProgram.conditionSatisfied(a,0)&&!CopyAttackProgram.conditionSatisfied(a,1),"Actual empty-Hand gate missing");
        check(second.baseDamage()==80&&second.energyCost().equals(Map.of(EnergyType.DARKNESS,1,EnergyType.COLORLESS,2))&&second.effectProgram().isEmpty(),"Sharp Fang changed");
    }}
    static void declarations(){for(String id:IDS)for(boolean empty:List.of(false,true))for(boolean bench:List.of(false,true)){
        var a=attack(id,"skill-thief");var m=game(a,TemporaryReceivedDamageTest.hit(140,EnergyType.WATER));TemporaryReceivedDamageTest.evolve(m,A);
        GeneratedRuleBatch44Test.assertRejectedAtomic(m,()->m.attack(A,a.id()));pay(m,A,a);if(empty)empty(m);
        if(empty){UUID target=bench?TemporaryReceivedDamageTest.bench(m,B):TemporaryReceivedDamageTest.active(m,B);var c=m.copyAttackOptions(A,a.id()).stream().filter(o->o.pokemonId().equals(target)).findFirst().orElseThrow();m.copyAttackWithEnergyDiscards(A,a.id(),c.pokemonId(),c.entrySequence(),c.attackId(),List.of(),List.of());}
        else m.attack(A,a.id());
        check(m.activePokemonView(B).damage()==(empty?140:0)&&m.currentPlayer().equals(B)&&m.activePokemonView(A).attachedEnergyIds().size()==2,"Actual conditional copy damage/cost/turn incorrect");
    }
        for(String id:IDS){var a=attack(id,"sharp-fang");var m=game(a,TemporaryReceivedDamageTest.hit(140,EnergyType.COLORLESS));pay(m,A,a);m.attack(A,a.id());check(m.lastAttack().finalDamage()==80,"Sharp Fang no longer works");}
    }
    static void actualCopiedSelf(){for(String id:IDS){var a=attack(id,"skill-thief");var body=attack("me5_en_058","plunging-headbutt");var m=game(a,body);TemporaryReceivedDamageTest.evolve(m,A);pay(m,A,a);empty(m);var c=m.copyAttackOptions(A,a.id()).getFirst();m.copyAttackWithEnergyDiscards(A,a.id(),c.pokemonId(),c.entrySequence(),c.attackId(),List.of(),List.of());
        check(m.lastAttack().finalDamage()==210&&!m.activePokemonView(A).continuousEffectZh().isEmpty()&&m.activePokemonView(B).continuousEffectZh().isEmpty(),"Conditional copied real SELF effect used provider");
        pay(m,B,body);m.attack(B,body.id());check(m.lastAttack().finalDamage()==310,"Copied real vulnerability missing");
    }}
}
