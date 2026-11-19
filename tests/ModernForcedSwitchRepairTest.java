import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernForcedSwitchRepairTest {
    static final UUID A=ModernRuleRepair2Test.A,B=ModernRuleRepair2Test.B;
    static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean ok,String why){ModernRuleRepair2Test.check(ok,why);}
    static CardInstance hand(ServerMatch m,UUID p,String id){return ModernRuleRepair2Test.hand(m,p,id);}
    public static void main(String[] args){forcedSleep();roar();energy();System.out.println("ModernForcedSwitchRepairTest passed: five prints; opponent-owned switch choice, no-Bench sleep boundary, active-only Ability, two Basic Water Energy to one Basic host, privacy and continuation");}
    static void forcedSleep(){
        for(String id:List.of("sv1_en_082","sv1_en_210"))for(boolean bench:List.of(false,true)){
            var attack=DATA.pokemon(id).orElseThrow().attacks().getFirst();var m=ModernQuantityRepairTest.game(attack,List.of(),EnergyType.PSYCHIC,"swsh5_en_124");
            var old=m.activePokemonView(B).pokemonId();var replacement=m.benchPokemonView(B).getFirst().pokemonId();if(!bench)m.movePokemonByEffect(A,B,replacement,LeavePlayDestination.HAND);
            ModernRuleRepair2Test.pay(m,A,m.activePokemonView(A).pokemonId(),"energy",1);int logs=m.replayTranscript().size();m.attack(A,attack.id());
            if(bench){var p=m.effectPokemonPrompt(B);CopyAttackTest.reject(()->m.chooseEffectPokemon(A,p.resolutionId(),p.step(),replacement));m.chooseEffectPokemon(B,p.resolutionId(),p.step(),replacement);}
            check(m.activePokemonView(B).pokemonId().equals(bench?replacement:old)&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Forced Sleep switch/continuation");
            check(m.replayTranscript().subList(logs,m.replayTranscript().size()).stream().anyMatch(e->e.operation().startsWith("已审核效果附加异常状态"))==bench,"Forced Sleep must affect only a successfully switched new Active");
        }
    }
    static void roar(){
        var ability=DATA.pokemon("sv2_en_068").orElseThrow().abilities().getFirst();var m=ModernCountRepairTest.game("swsh5_en_124",List.of(ability));
        CopyAttackTest.reject(()->m.useAbility(A,m.benchPokemonView(A).getFirst().pokemonId(),ability.id()));var source=m.activePokemonView(A).pokemonId();m.useAbility(A,source,ability.id());var p=m.effectPokemonPrompt(B);var target=m.benchPokemonView(B).getFirst().pokemonId();
        CopyAttackTest.reject(()->m.chooseEffectPokemon(A,p.resolutionId(),p.step(),target));m.chooseEffectPokemon(B,p.resolutionId(),p.step(),target);
        check(m.activePokemonView(B).pokemonId().equals(target)&&!m.hasPendingEffectChoice()&&!m.canUseAbility(A,source,ability.id())&&m.currentPlayer().equals(A),"Big Roar selection/usage/continuation");
    }
    static void energy(){
        for(String id:List.of("sv1_en_062","sv4-5_en_127"))for(int count:List.of(0,1,2)){
            var attack=DATA.pokemon(id).orElseThrow().attacks().getFirst();var m=ModernQuantityRepairTest.game(attack,List.of(),EnergyType.WATER,"swsh5_en_124");var source=m.activePokemonView(A).pokemonId();var target=m.benchPokemonView(A).getFirst().pokemonId();
            m.evolveByEffect(A,source,hand(m,A,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);ModernRuleRepair2Test.pay(m,A,source,"energy",1);
            ModernWindowTimingRepairTest.window(m,List.of("energy","energy","wrong","special"),true);var before=m.privateView(A).deck();m.attack(A,attack.id());var p=m.effectCardPrompt(A);
            check(p.minimum()==0&&p.maximum()==2&&p.candidates().size()==2&&p.candidates().stream().allMatch(c->c.definitionId().equals("energy"))&&m.clientView(B).effectSelection()==null,"Mise en Place basic water/private search");
            var ids=p.candidates().stream().limit(count).map(CardInstance::instanceId).toList();m.submitEffectCards(A,p.resolutionId(),p.step(),ids);
            if(count>0){var q=m.effectAttachmentPrompt(A);check(!q.targets().contains(source)&&q.targets().contains(target),"Mise en Place accepted evolved host");CopyAttackTest.reject(()->m.chooseEffectAttachmentTarget(A,q.resolutionId(),q.step(),source));m.chooseEffectAttachmentTarget(A,q.resolutionId(),q.step(),target);}
            check(m.benchPokemonView(A).getFirst().attachedEnergyIds().containsAll(ids)&&m.privateView(A).deck().size()==before.size()-count&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Mise en Place split/lost Energy or stalled");
        }
    }
}
