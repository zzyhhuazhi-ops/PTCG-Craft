import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernBuddyPulseRepairTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    static final CombatCatalog DATA=BundledCombatData.load();
    public static void main(String[] args){
        for(String id:List.of("sv4_en_061","sv4_en_194"))for(boolean plus:List.of(false,true))for(boolean duplicate:List.of(false,true))
        for(EffectSourceZone zone:EffectSourceZone.values())for(boolean special:List.of(false,true))for(boolean opposing:List.of(false,true)){
            var ability=DATA.pokemon(id).orElseThrow().abilities().getFirst();TriggeredAbilityRules.validate(ability);
            var m=ModernEnergyRepairTest.game(TrainerPlayLockTest.item(),List.of(ability),GeneratedRuleBatch8Test.hit(10,false),EnergyType.LIGHTNING,0,Map.of("wrongmon","Plusle","basic","Minun"));
            if(!duplicate)m.movePokemonByEffect(A,A,m.benchPokemonView(A).getFirst().pokemonId(),LeavePlayDestination.HAND);
            if(plus)m.playBasicPokemonToBench(A,ModernRuleRepair2Test.hand(m,A,"wrongmon").instanceId());
            UUID owner=opposing?B:A,target=m.activePokemonView(owner).pokemonId();String energyId=special?"special":"energy";
            m.drawCardsByEffect(A,B,20);
            var energy=(zone==EffectSourceZone.DECK?m.privateView(owner).deck():m.privateView(owner).hand()).stream().filter(c->c.definitionId().equals(energyId)).findFirst().orElseThrow();
            if(zone==EffectSourceZone.DISCARD)m.discardHandCardsByEffect(A,owner,List.of(energy.instanceId()));
            int before=m.activePokemonView(owner).damage();m.attachEnergyByEffect(A,owner,zone,energy.instanceId(),owner,target);
            check(m.activePokemonView(owner).damage()==before+(plus&&opposing&&zone==EffectSourceZone.HAND?20:0),"Buddy Pulse wrong scope, source, physical energy count or stacking");
            check(m.triggerOrderPrompt()==null&&!m.hasPendingEffectChoice(),"Buddy Pulse left pending selection");
        }
        for(String id:List.of("sv4_en_061","sv4_en_194")){
            var ability=DATA.pokemon(id).orElseThrow().abilities().getFirst();
            var m=ModernEnergyRepairTest.game(TrainerPlayLockTest.item(),List.of(ability),GeneratedRuleBatch8Test.hit(10,false),EnergyType.LIGHTNING,0,Map.of("wrongmon","Plusle"));
            m.playBasicPokemonToBench(A,ModernRuleRepair2Test.hand(m,A,"wrongmon").instanceId());m.drawCardsByEffect(A,B,20);
            UUID target=m.activePokemonView(B).pokemonId();m.endTurn(A);m.attachEnergy(B,ModernRuleRepair2Test.hand(m,B,"energy").instanceId(),target);
            check(m.activePokemonView(B).damage()==20,"Buddy Pulse manual attachment or nonstack");
            m.attachEnergyByEffect(B,B,EffectSourceZone.HAND,ModernRuleRepair2Test.hand(m,B,"special").instanceId(),B,target);
            check(m.activePokemonView(B).damage()==40,"Buddy Pulse incorrectly once per turn");
        }
        System.out.println("ModernBuddyPulseRepairTest passed: both prints, partner requirement, duplicates, all source zones, Basic/Special, both owners, manual/effect attachments and repeated events");
    }
}
