import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernSwitchDrawRepairTest {
    static final UUID A=ModernRuleRepair2Test.A,B=ModernRuleRepair2Test.B;
    static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean ok,String why){ModernRuleRepair2Test.check(ok,why);}
    public static void main(String[] args){freedom();mirror();ball();System.out.println("ModernSwitchDrawRepairTest passed: position-dependent Ability switches, independent hosts and once-per-turn, no-Bench boundary, public hand counts, no discard when already ahead, short-deck draw, optional two-unit Lightning payment and paralysis");}
    static void freedom(){
        var a=DATA.pokemon("sv3_en_015").orElseThrow().abilities().getFirst();
        for(boolean fromBench:List.of(false,true)){
            var m=ModernCountRepairTest.game("swsh5_en_124",List.of(a));UUID front=m.activePokemonView(A).pokemonId(),bench=m.benchPokemonView(A).getFirst().pokemonId(),source=fromBench?bench:front;
            var extra=ModernRuleRepair2Test.hand(m,A,"basic");m.playBasicPokemonToBench(A,extra.instanceId());UUID extraHost=m.benchPokemonView(A).getLast().pokemonId();
            m.useAbility(A,source,a.id());var p=m.effectPokemonPrompt(A);
            check(p!=null&&p.targets().size()==(fromBench?1:2)&&p.targets().stream().anyMatch(t->t.pokemonId().equals(bench)),"Source-position filter offered wrong switch");
            if(fromBench)CopyAttackTest.reject(()->m.chooseEffectPokemon(A,p.resolutionId(),p.step(),extraHost));
            CopyAttackTest.reject(()->m.chooseEffectPokemon(B,p.resolutionId(),p.step(),bench));m.chooseEffectPokemon(A,p.resolutionId(),p.step(),bench);
            check(m.activePokemonView(A).pokemonId().equals(bench)&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(A),"Total Freedom double-switched or stalled");
            check(!m.canUseAbility(A,source,a.id()),"Source used twice after switching");
            UUID other=fromBench?front:bench;check(m.canUseAbility(A,other,a.id()),"Other host incorrectly locked");m.endTurn(A);m.endTurn(B);check(m.canUseAbility(A,source,a.id()),"Ability did not renew next turn");
        }
        var m=ModernCountRepairTest.game("swsh5_en_124",List.of(a));UUID bench=m.benchPokemonView(A).getFirst().pokemonId();m.movePokemonByEffect(A,A,bench,dev.hua.ptcglive.engine.field.LeavePlayDestination.HAND);
        check(!m.canUseAbility(A,m.activePokemonView(A).pokemonId(),a.id()),"Total Freedom accepted no Bench");
    }
    static void ball(){
        for(String id:List.of("sv2_en_079","sv2_en_237"))for(boolean accept:List.of(false,true)){
            var a=DATA.pokemon(id).orElseThrow().attacks().get(1);var m=ModernQuantityRepairTest.game(a,List.of(),EnergyType.LIGHTNING,"swsh5_en_124");
            UUID source=m.activePokemonView(A).pokemonId();ModernRuleRepair2Test.pay(m,A,source,"energy",3);
            m.attack(A,a.id());var q=m.effectChoicePrompt(A);check(q.optionsZh().size()==2,"Paralyzing Ball must offer whole cost/effect or decline");
            m.chooseEffectOption(A,q.resolutionId(),q.step(),accept?0:1);
            if(accept){var p=m.effectCardPrompt(A);check(p.candidates().size()==3,"Paralyzing Ball omitted available Energy");
                CopyAttackTest.reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),p.candidates().stream().limit(1).map(CardInstance::instanceId).toList()));
                CopyAttackTest.reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),p.candidates().stream().map(CardInstance::instanceId).toList()));
                m.submitEffectCards(A,p.resolutionId(),p.step(),p.candidates().stream().limit(2).map(CardInstance::instanceId).toList());}
            check(m.activePokemonView(A).attachedEnergyIds().size()==(accept?1:3),"Paralyzing Ball lost wrong Energy count");
            check(m.activePokemonView(B).damage()==160&&m.activePokemonView(B).conditions().contains(SpecialCondition.PARALYZED)==accept,"Paralyzing Ball status before cost, declined status or wrong damage");
            check(m.currentPlayer().equals(B)&&!m.hasPendingEffectChoice(),"Paralyzing Ball stalled");
        }
    }
    static void mirror(){
        for(String id:List.of("sv3_en_144"))for(int target:List.of(0,2,8))for(boolean shortDeck:List.of(false,true)){
            var a=DATA.pokemon(id).orElseThrow().attacks().getFirst();var m=ModernShuffleScopeRepairTest.game(a,0);m.endTurn(A);m.endTurn(B);ModernShuffleScopeRepairTest.pay(m,A,m.activePokemonView(A).pokemonId());
            for(UUID p:List.of(A,B)){int keep=p.equals(A)?4:target;m.discardHandCardsByEffect(A,p,m.privateView(p).hand().stream().skip(keep).map(CardInstance::instanceId).toList());}
            if(shortDeck)m.discardTopDeckCardsByEffect(A,A,m.privateView(A).deck().size()-1);
            int count=m.privateView(A).hand().size(),deck=m.privateView(A).deck().size();var opponent=m.privateView(B).hand();m.attack(A,a.id());
            check(m.privateView(A).hand().size()==count+Math.min(deck,Math.max(0,target-count)),"Mirror Draw wrong public hand count or discarded excess");
            check(m.privateView(B).hand().containsAll(opponent)&&m.currentPlayer().equals(B)&&!m.hasPendingEffectChoice(),"Mirror Draw changed opponent hand or stalled");
        }
    }
}
