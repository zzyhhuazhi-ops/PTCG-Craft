import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Actual Mandibuzz complete profile/evolution/costs on controlled 60-card server fixtures. */
public final class GeneratedRuleBatch64Test {
    static final UUID A=GeneratedRuleBatch63Test.A,B=GeneratedRuleBatch63Test.B;
    static final String CARD="me5_en_050",ATTACK="bone-sniper";
    static void check(boolean b,String why){GeneratedRuleBatch8Test.check(b,why);}
    static UUID active(ServerMatch m,UUID p){return GeneratedRuleBatch63Test.active(m,p);}
    static UUID bench(ServerMatch m,UUID p){return GeneratedRuleBatch63Test.bench(m,p);}
    static ServerMatch game(){return GeneratedRuleBatch63Test.game(CARD);}
    static CardInstance attach(ServerMatch m,UUID p,UUID host,String id){return GeneratedRuleBatch63Test.attach(m,p,host,id);}
    static void pay(ServerMatch m,int n){GeneratedRuleBatch63Test.pay(m,"basic_darkness",n);}
    static void atomic(ServerMatch m,Runnable r){GeneratedRuleBatch44Test.assertRejectedAtomic(m,r);}
    public static void main(String[] args){printed();nativeTargets();missingAndCosts();liveChoices();actualProtection();copied();copiedCost();secondAttack();System.out.println("GeneratedRuleBatch64Test passed: actual Mandibuzz complete profile and Vullaby evolution, printed Dark1/70 recipient restriction, empty/basic/own exclusions, live Energy movement, actual Shadowy protection/suppression independent of eligibility, real Thievul copy with declared cost and actual source, unchanged Dark2Colorless1/120 Blasting Wind");}
    static void printed(){check(GeneratedRuleBatch63Test.DATABASE.find(CARD).orElseThrow().effectSupport()==EffectSupport.SUPPORTED,"Actual Mandibuzz incomplete");var p=GeneratedRuleBatch63Test.BUNDLED.pokemon(CARD).orElseThrow();check(p.hitPoints()==120&&p.retreatCost()==2&&p.evolvesFromDefinitionId().equals("me5_en_049")&&p.pokemonType()==EnergyType.DARKNESS&&p.weaknessType()==EnergyType.LIGHTNING&&p.weaknessMultiplier()==2&&p.resistanceType()==EnergyType.FIGHTING&&p.resistanceAmount()==30&&p.prizeCards()==1,"Printed Mandibuzz profile changed");var a=p.attack(ATTACK).orElseThrow();check(a.baseDamage()==70&&a.damageType()==EnergyType.DARKNESS&&a.energyCost().equals(Map.of(EnergyType.DARKNESS,1))&&a.damageTarget()==AttackDefinition.DamageTarget.OPPONENT_ANY&&a.targetCount()==1&&a.targetHasSpecialEnergy()&&!a.mayUseFromBench()&&a.effectProgram().isEmpty(),"Bone Sniper semantic representation incorrect");var b=p.attack("blasting-wind").orElseThrow();check(b.baseDamage()==120&&b.energyCost().equals(Map.of(EnergyType.DARKNESS,2,EnergyType.COLORLESS,1))&&!b.targetHasSpecialEnergy(),"Second attack gained target restriction");}
    static void nativeTargets(){for(boolean bench:List.of(false,true))for(String energy:List.of("wild",GeneratedRuleBatch63Test.VOLTAIC)){
        var m=game();pay(m,1);UUID target=bench?bench(m,B):active(m,B);attach(m,B,target,energy);m.attack(A,ATTACK,target);check((bench?m.benchPokemonView(B).getFirst().damage():m.activePokemonView(B).damage())==70&&m.lastAttack().finalDamage()==70&&m.currentPlayer().equals(B),"Actual target did not take70");
    }}
    static void missingAndCosts(){for(String mode:List.of("EMPTY","BASIC","OWN")){
        var m=game();pay(m,1);if(mode.equals("BASIC")){attach(m,B,active(m,B),"basic_darkness");attach(m,B,bench(m,B),"basic_lightning");}if(mode.equals("OWN"))attach(m,A,bench(m,A),"wild");
        atomic(m,()->m.attack(A,ATTACK,active(m,B)));atomic(m,()->m.attack(A,ATTACK,bench(m,A)));m.attack(A,ATTACK);check(m.lastAttack().finalDamage()==0&&m.activePokemonView(B).damage()==0&&m.benchPokemonView(B).getFirst().damage()==0&&m.currentPlayer().equals(B),"No eligible target fell back to Active");
    }
        var m=game();attach(m,B,active(m,B),"wild");atomic(m,()->m.attack(A,ATTACK,active(m,B)));attach(m,A,active(m,A),"basic_lightning");atomic(m,()->m.attack(A,ATTACK,active(m,B)));pay(m,1);m.attack(A,ATTACK,active(m,B));check(m.lastAttack().finalDamage()==70,"Printed Darkness cost changed");
    }
    static void liveChoices(){var m=game();pay(m,1);var c=attach(m,B,active(m,B),"wild");m.moveAttachedEnergyByEffect(A,B,active(m,B),B,bench(m,B),List.of(c.instanceId()));atomic(m,()->m.attack(A,ATTACK,active(m,B)));var s=MatchSession.multiplayer(m,A,B);var v=s.view(A,A);var options=v.options().stream().filter(o->o.labelZh().contains("骨棒狙击")).toList();check(options.size()==1&&options.getFirst().cardKeys().contains("field:"+bench(m,B)),"Actual attack UI lost recipient restriction");s.submit(A,A,v.revision(),options.getFirst().index());check(m.benchPokemonView(B).getFirst().damage()==70&&m.activePokemonView(B).damage()==0,"Live recipient hit wrong field slot");}
    static void actualProtection(){for(boolean suppressed:List.of(false,true)){
        var m=game();pay(m,1);attach(m,B,bench(m,B),GeneratedRuleBatch63Test.SHADOW);if(suppressed)GeneratedRuleBatch63Test.override(m);var a=GeneratedRuleBatch63Test.attack(CARD,ATTACK);check(m.matchesAttackDamageTargetFilter(B,bench(m,B),a),"Shadowy damage prevention wrongly removed legal target");m.attack(A,ATTACK,bench(m,B));check(m.lastAttack().finalDamage()==(suppressed?70:0),"Actual Shadowy protection/synthetic override changed physical eligibility");
    }}
    static void copied(){for(String mode:List.of("ACTIVE","BENCH","NONE")){
        var m=GeneratedRuleBatch63Test.game(GeneratedRuleBatch63Test.THIEVUL,CARD,EnergyType.DARKNESS);pay(m,2);UUID target=mode.equals("BENCH")?bench(m,B):active(m,B);if(!mode.equals("NONE"))attach(m,B,target,"wild");ConditionalCopyAttackTest.empty(m,A);var choice=m.copyAttackOptions(A,"skill-thief").stream().filter(c->c.attackId().equals(ATTACK)).findFirst().orElseThrow();var targets=mode.equals("NONE")?List.<UUID>of():List.of(target);check(choice.effectiveAttack().targetHasSpecialEnergy()&&choice.effectiveAttack().energyCost().equals(Map.of(EnergyType.COLORLESS,2)),"Real copied body lost filter or declared cost");
        atomic(m,()->m.copyAttackWithEnergyDiscards(A,"skill-thief",choice.pokemonId(),choice.entrySequence(),choice.attackId(),List.of(active(m,A)),List.of()));m.copyAttackWithEnergyDiscards(A,"skill-thief",choice.pokemonId(),choice.entrySequence(),choice.attackId(),targets,List.of());check(m.lastAttack().finalDamage()==(mode.equals("NONE")?0:70)&&m.currentPlayer().equals(B),"Real Thievul copied target body failed");
    }}
    static void copiedCost(){var m=GeneratedRuleBatch63Test.game(GeneratedRuleBatch63Test.THIEVUL,CARD,EnergyType.DARKNESS);pay(m,1);attach(m,B,active(m,B),"wild");ConditionalCopyAttackTest.empty(m,A);var c=m.copyAttackOptions(A,"skill-thief").stream().filter(x->x.attackId().equals(ATTACK)).findFirst().orElseThrow();atomic(m,()->m.copyAttackWithEnergyDiscards(A,"skill-thief",c.pokemonId(),c.entrySequence(),c.attackId(),List.of(active(m,B)),List.of()));}
    static void secondAttack(){var m=game();pay(m,2);atomic(m,()->m.attack(A,"blasting-wind"));attach(m,A,active(m,A),"basic_lightning");m.attack(A,"blasting-wind");check(m.lastAttack().finalDamage()==120&&m.activePokemonView(B).attachedEnergyIds().isEmpty(),"Blasting Wind changed or inherited special target filter");}
}
