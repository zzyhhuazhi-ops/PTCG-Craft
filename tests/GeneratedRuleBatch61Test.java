import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Actual printed attacks/costs on neutral high-HP hosts isolate the new received-damage effects. */
public final class GeneratedRuleBatch61Test {
    static final UUID A=TemporaryReceivedDamageTest.A,B=TemporaryReceivedDamageTest.B;
    static final Map<String,String> TARGETS=Map.of("me5_en_058","plunging-headbutt","me5_en_064","metal-block");
    static void check(boolean b,String why){GeneratedRuleBatch8Test.check(b,why);}
    static AttackDefinition attack(String id,String name){return GeneratedRuleBatch57Test.attack(id,name);}
    static ServerMatch game(AttackDefinition a,AttackDefinition reply){var c=new TemporaryReceivedDamageTest.Config(a);c.reply=reply;c.energy=a.damageType();return TemporaryReceivedDamageTest.game(c);}
    static void pay(ServerMatch m,UUID p,AttackDefinition a){TemporaryReceivedDamageTest.pay(m,p,a);}
    static void evolve(ServerMatch m,UUID p){TemporaryReceivedDamageTest.evolve(m,p);}
    public static void main(String[] args){printed();actualPrograms();copies();System.out.println("GeneratedRuleBatch61Test passed: actual Mabosstiff and Bronzong complete attacks with printed Energy costs/base damage, existing first attacks, Basic/Evolution attackers, source-bound +100/-100, switch and expiry, copied real attack on actual recipient with declared copy cost");}
    static void printed(){var db=BundledRules.loadCardDefinitions();for(var row:TARGETS.entrySet()){var id=row.getKey();var json=GeneratedRuleBatch8Test.entry(id);var a=attack(id,row.getValue());check(db.find(id).orElseThrow().effectSupport()==EffectSupport.SUPPORTED&&AttackProgramRules.unsupportedReason(a).isEmpty(),"Actual card not fully supported");boolean mab=id.endsWith("058");check(a.baseDamage()==(mab?210:120)&&a.energyCost().equals(mab?Map.of(EnergyType.DARKNESS,3):Map.of(EnergyType.METAL,2,EnergyType.COLORLESS,1)),"Printed main attack damage/cost changed");check(json.get("hitPoints").getAsInt()==(mab?140:130)&&json.get("retreatCost").getAsInt()==3&&json.get("evolvesFromDefinitionId").getAsString().equals(mab?"me5_en_057":"me5_en_063"),"Printed Pokemon metadata changed");var first=attack(id,mab?"bite":"gentle-slap");check(first.baseDamage()==(mab?60:40)&&first.energyCost().equals(Map.of(mab?EnergyType.DARKNESS:EnergyType.METAL,mab?2:1)),"First attack changed");}}
    static void actualPrograms(){for(var row:TARGETS.entrySet())for(String mode:List.of("BASIC","EVOLUTION","SWITCH","EXPIRED")){
        var a=attack(row.getKey(),row.getValue());var m=game(a,TemporaryReceivedDamageTest.hit(140,EnergyType.COLORLESS));evolve(m,A);pay(m,A,a);m.attack(A,a.id());check(m.lastAttack().finalDamage()==a.baseDamage(),"Actual attack damage lost");
        if(mode.equals("EVOLUTION"))evolve(m,B);
        if(mode.equals("SWITCH")){var old=TemporaryReceivedDamageTest.active(m,A);m.switchActiveByEffect(B,A,TemporaryReceivedDamageTest.bench(m,A));m.switchActiveByEffect(B,A,old);}
        if(mode.equals("EXPIRED")){m.endTurn(B);m.endTurn(A);}
        m.attack(B,"hit");int expected=Set.of("SWITCH","EXPIRED").contains(mode)?140:row.getKey().endsWith("058")?240:mode.equals("EVOLUTION")?40:140;check(m.lastAttack().finalDamage()==expected,"Actual next-turn modifier "+row.getKey()+"/"+mode+" "+m.lastAttack().finalDamage());
    }
        for(var row:TARGETS.entrySet()){var a=attack(row.getKey(),row.getKey().endsWith("058")?"bite":"gentle-slap");var m=game(a,TemporaryReceivedDamageTest.hit(140,EnergyType.COLORLESS));evolve(m,A);pay(m,A,a);m.attack(A,a.id());check(m.lastAttack().finalDamage()==a.baseDamage()&&m.activePokemonView(A).continuousEffectZh().isEmpty(),"Unmodified first attack acquired temporary effect");}
    }
    static void copies(){for(var row:TARGETS.entrySet())for(boolean evolved:List.of(false,true)){
        var real=attack(row.getKey(),row.getValue());var c=new TemporaryReceivedDamageTest.Config(CopyAttackTest.copier());c.reply=real;c.energy=real.damageType();var m=TemporaryReceivedDamageTest.game(c);pay(m,A,CopyAttackTest.copier());TemporaryReceivedDamageTest.copy(m,A,real.id());check(!m.activePokemonView(A).continuousEffectZh().isEmpty()&&m.activePokemonView(B).continuousEffectZh().isEmpty(),"Copied effect granted to original source");
        check(m.activePokemonView(A).attachedEnergyIds().size()==1&&m.lastAttack().finalDamage()==real.baseDamage(),"Copy used printed provider cost instead of declared cost");
        if(evolved)evolve(m,B);pay(m,B,real);m.attack(B,real.id());int expected=real.baseDamage()+(row.getKey().endsWith("058")?100:evolved?-100:0);check(m.lastAttack().finalDamage()==expected,"Copied real self modifier/filter did not apply to actual recipient");
    }}
}
