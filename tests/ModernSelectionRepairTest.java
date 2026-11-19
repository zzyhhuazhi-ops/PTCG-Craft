import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernSelectionRepairTest {
    static final UUID A=ModernRuleRepair2Test.A,B=ModernRuleRepair2Test.B;
    static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean ok,String why){CopyAttackTest.check(ok,why);}
    static void reject(Runnable action){CopyAttackTest.reject(action);}
    static CardInstance hand(ServerMatch m,UUID p,String id){return ModernRuleRepair2Test.hand(m,p,id);}
    public static void main(String[] args){penny();chains();lisia();ledian();hyperBlower();System.out.println("ModernSelectionRepairTest passed: Penny four, Pecharunt five, Lisia three, Ledian two, Iron Bundle three; target filters, attached returns, shared usage, forced switch protection, status, stale selection");}
    static void penny(){
        for(String id:List.of("sv1_en_183","sv1_en_239","sv1_en_252","sv4-5_en_239"))for(boolean active:List.of(false,true)){
            var m=GeneratedRuleBatch8Test.fixture(List.of(),EnergyType.GRASS,EnergyType.GRASS,DATA.trainer(id).orElseThrow(),GeneratedRuleBatch8Test.hit(40,false));
            UUID selected=active?m.activePokemonView(A).pokemonId():m.benchPokemonView(A).getFirst().pokemonId();UUID evolved=active?m.benchPokemonView(A).getFirst().pokemonId():m.activePokemonView(A).pokemonId();
            m.evolveByEffect(A,evolved,hand(m,A,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
            var energy=hand(m,A,"energy");m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,energy.instanceId(),A,selected);
            var tool=hand(m,A,"tool");m.playTrainer(A,tool.instanceId(),selected);
            var trainer=hand(m,A,"trainer");m.playTrainer(A,trainer.instanceId(),null);var p=m.effectPokemonPrompt(A);
            check(p.targets().stream().noneMatch(t->t.pokemonId().equals(evolved))&&p.targets().stream().anyMatch(t->t.pokemonId().equals(selected)),"Penny basic-only scope");
            reject(()->m.chooseEffectPokemon(A,p.resolutionId(),p.step(),evolved));reject(()->m.chooseEffectPokemon(B,p.resolutionId(),p.step(),selected));
            m.chooseEffectPokemon(A,p.resolutionId(),p.step(),selected);
            if(active)m.chooseReplacement(A,evolved);
            reject(()->m.chooseEffectPokemon(A,p.resolutionId(),p.step(),selected));
            check(m.privateView(A).hand().containsAll(List.of(energy,tool))&&m.benchPokemonView(A).stream().noneMatch(v->v.pokemonId().equals(selected))&&m.currentPlayer().equals(A)&&!m.hasPendingEffectChoice(),"Penny attached return/continuation");
        }
    }
    static void chains(){
        for(String id:List.of("sv6-5_en_039","sv6-5_en_085","sv6-5_en_093","sv6-5_en_095","sv8-5_en_163")){
            var ability=DATA.pokemon(id).orElseThrow().abilities().getFirst();var m=ModernEnergyRepairTest.game(DATA.trainer("swsh5_en_124").orElseThrow(),List.of(ability),GeneratedRuleBatch8Test.hit(40,false),EnergyType.DARKNESS,0,Map.of("evolved","Pecharunt ex"));
            UUID source=m.activePokemonView(A).pokemonId(),excluded=m.benchPokemonView(A).getFirst().pokemonId();
            m.evolveByEffect(A,excluded,hand(m,A,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
            m.playBasicPokemonToBench(A,hand(m,A,"basic").instanceId());m.playBasicPokemonToBench(A,hand(m,A,"wrongmon").instanceId());
            var desired=m.benchPokemonView(A).stream().filter(v->v.topCardId().equals("basic")).findFirst().orElseThrow().pokemonId();
            m.useAbility(A,source,ability.id());var p=m.effectPokemonPrompt(A);
            check(p.targets().size()==1&&p.targets().getFirst().pokemonId().equals(desired),"Chains includes Pecharunt ex or non-Darkness");
            reject(()->m.chooseEffectPokemon(A,p.resolutionId(),p.step(),excluded));m.chooseEffectPokemon(A,p.resolutionId(),p.step(),desired);
            check(m.activePokemonView(A).pokemonId().equals(desired)&&m.activePokemonView(A).conditions().contains(SpecialCondition.POISONED),"Chains switch/poison");
            reject(()->m.useAbility(A,excluded,ability.id()));reject(()->m.useAbility(A,desired,ability.id()));
            check(!m.hasPendingEffectChoice(),"Chains left pending");
        }
    }
    static void lisia(){
        for(String id:List.of("sv8_en_179","sv8_en_234","sv8_en_246")){
            var m=ModernEnergyRepairTest.game(DATA.trainer(id).orElseThrow(),List.of(),GeneratedRuleBatch8Test.hit(40,false),EnergyType.GRASS,0);
            m.endTurn(A);while(m.privateView(B).hand().stream().noneMatch(c->c.definitionId().equals("evolved"))||m.privateView(B).hand().stream().noneMatch(c->c.definitionId().equals("basic")))m.drawCardsByEffect(B,B,1);
            UUID evolved=m.benchPokemonView(B).getFirst().pokemonId();m.evolveByEffect(B,evolved,hand(m,B,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
            m.playBasicPokemonToBench(B,hand(m,B,"basic").instanceId());m.endTurn(B);
            UUID target=m.benchPokemonView(B).stream().filter(v->v.topCardId().equals("basic")).findFirst().orElseThrow().pokemonId();
            m.playTrainer(A,hand(m,A,"trainer").instanceId(),null);var p=m.effectPokemonPrompt(A);
            check(p.targets().size()==1&&p.targets().getFirst().pokemonId().equals(target),"Lisia not limited to Benched Basics");
            reject(()->m.chooseEffectPokemon(A,p.resolutionId(),p.step(),evolved));reject(()->m.chooseEffectPokemon(B,p.resolutionId(),p.step(),target));
            m.chooseEffectPokemon(A,p.resolutionId(),p.step(),target);reject(()->m.chooseEffectPokemon(A,p.resolutionId(),p.step(),target));
            check(m.activePokemonView(B).pokemonId().equals(target)&&m.activePokemonView(B).conditions().contains(SpecialCondition.CONFUSED)&&!m.hasPendingEffectChoice(),"Lisia switch/confusion continuation");
        }
    }
    static void ledian(){
        for(String id:List.of("sv7_en_003","sv7_en_144"))for(int mode:List.of(0,1,2)){
            var ability=DATA.pokemon(id).orElseThrow().abilities().getFirst();var m=ModernRuleRepair2Test.selectionGame("sv10_en_175",List.of(ability),0);
            UUID target=m.benchPokemonView(B).getFirst().pokemonId(),old=m.activePokemonView(B).pokemonId();
            if(mode!=2)m.placeDamageCountersByEffect(A,B,target,3);
            m.placeDamageCountersByEffect(A,B,old,7);
            while(m.privateView(A).hand().stream().noneMatch(c->c.definitionId().equals("evolved")))m.drawCardsByEffect(A,A,1);
            m.evolveByEffect(A,m.activePokemonView(A).pokemonId(),hand(m,A,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
            var trigger=m.triggerOrderPrompt();
            if(trigger!=null)m.decideTriggeredEffect(A,trigger.id(),mode==1);
            if(mode==1){var p=m.effectPokemonPrompt(A);check(p.targets().size()==1&&p.targets().getFirst().pokemonId().equals(target),"Ledian must use remaining HP and only Bench");m.chooseEffectPokemon(A,p.resolutionId(),p.step(),target);}
            check(m.activePokemonView(B).pokemonId().equals(mode==1?target:old)&&!m.hasPendingEffectChoice(),"Ledian optional switch continuation");
        }
    }
    static void hyperBlower(){
        for(String id:List.of("sv4_en_056","svbsp_en_058","svbsp_en_066"))for(boolean protectedTarget:List.of(false,true)){
            var ability=DATA.pokemon(id).orElseThrow().abilities().getFirst();
            var guard=ResumableAbilityTest.ability("guard",EffectOriginProtectionTest.shield("ABILITY","EFFECTS_ONLY","SELF","SOURCE_NEXT_TURN_START"));
            var m=GeneratedRuleBatch8Test.fixture(List.of(ability,guard),EnergyType.WATER,EnergyType.WATER,DATA.trainer("swsh5_en_124").orElseThrow(),GeneratedRuleBatch8Test.hit(40,false));
            UUID source=m.benchPokemonView(A).getFirst().pokemonId(),old=m.activePokemonView(B).pokemonId(),replacement=m.benchPokemonView(B).getFirst().pokemonId();
            m.endTurn(A);if(protectedTarget)m.useAbility(B,old,guard.id());m.endTurn(B);
            var energy=hand(m,A,"energy");var tool=hand(m,A,"tool");m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,energy.instanceId(),A,source);m.playTrainer(A,tool.instanceId(),source);
            reject(()->m.useAbility(A,m.activePokemonView(A).pokemonId(),ability.id()));m.useAbility(A,source,ability.id());
            var p=m.effectPokemonPrompt(B);reject(()->m.chooseEffectPokemon(A,p.resolutionId(),p.step(),replacement));m.chooseEffectPokemon(B,p.resolutionId(),p.step(),replacement);
            check(m.activePokemonView(B).pokemonId().equals(protectedTarget?old:replacement),"Hyper Blower protection/switch");
            check(m.benchPokemonView(A).stream().anyMatch(v->v.pokemonId().equals(source))==protectedTarget,"Hyper Blower discarded without actual switch");
            check(m.privateView(A).discard().contains(energy)!=protectedTarget&&m.privateView(A).discard().contains(tool)!=protectedTarget,"Hyper Blower attached-card ownership");
            check(!m.hasPendingEffectChoice()&&m.currentPlayer().equals(A),"Hyper Blower continuation");
        }
    }
}
