import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Real bundled programs on controlled server boards, with public target and cost submissions. */
public final class ModernQuantityRepairTest {
    static final UUID A=ModernRuleRepair2Test.A,B=ModernRuleRepair2Test.B;
    static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean ok,String why){CopyAttackTest.check(ok,why);}
    static void reject(Runnable r){CopyAttackTest.reject(r);}
    static CardInstance hand(ServerMatch m,UUID p,String id){return ModernRuleRepair2Test.hand(m,p,id);}
    static ServerMatch game(AttackDefinition attack,List<AbilityDefinition> abilities,EnergyType type,String trainer){return GeneratedRuleBatch8Test.fixture(abilities,type,type,DATA.trainer(trainer).orElseThrow(),attack);}
    public static void main(String[] args){ivy();scream();tachyon();morty();System.out.println("ModernQuantityRepairTest passed: Wo-Chien four, Scream Tail three, Iron Valiant six, Morty three; count orientation, target selection, entry/turn limits and exact costs");}
    static void ivy(){
        for(String id:List.of("sv2_en_027","sv2_en_232","sv2_en_257","sv4-5_en_240"))for(int taken:List.of(0,1,4)){
            var attack=DATA.pokemon(id).orElseThrow().attacks().getFirst();AttackProgramRules.validate(attack);
            var m=game(attack,List.of(),EnergyType.GRASS,"swsh5_en_124");
            // Fixture-only Prize movement sets different public counts without inventing a gameplay action.
            for(UUID p:List.of(A,B)){var prizes=GeneratedRuleBatch47Test.zone(m,p,"prizes");var h=GeneratedRuleBatch47Test.zone(m,p,"hand");while(prizes.size()>(p.equals(A)?4:6-taken))h.add(prizes.removeLast());}
            UUID a=m.activePokemonView(A).pokemonId(),b=m.benchPokemonView(B).getFirst().pokemonId();ModernRuleRepair2Test.pay(m,A,a,"energy",3);
            reject(()->m.attack(A,attack.id(),m.activePokemonView(B).pokemonId()));m.attack(A,attack.id(),b);
            check(m.benchPokemonView(B).getFirst().damage()==taken*60&&m.activePokemonView(B).damage()==0,"Ivy used own/remaining prizes or wrong target");
            check(m.currentPlayer().equals(B)&&!m.hasPendingEffectChoice(),"Ivy stalled");
        }
    }
    static void scream(){
        for(String id:List.of("sv4_en_086","sv8-5_en_042","svalt_en_040"))for(boolean bench:List.of(false,true))for(int count:List.of(0,1,4)){
            var attack=DATA.pokemon(id).orElseThrow().attacks().get(1);AttackProgramRules.validate(attack);
            var m=game(attack,List.of(),EnergyType.PSYCHIC,"swsh5_en_124");UUID a=m.activePokemonView(A).pokemonId();
            if(count>0)m.placeDamageCountersByEffect(A,A,a,count);m.placeDamageCountersByEffect(A,B,m.activePokemonView(B).pokemonId(),3);
            ModernRuleRepair2Test.pay(m,A,a,"energy",2);UUID target=bench?m.benchPokemonView(B).getFirst().pokemonId():m.activePokemonView(B).pokemonId();m.attack(A,attack.id(),target);
            check(m.lastAttack().finalDamage()==count*20,"Scream counted defender instead of source");
            check((bench?m.benchPokemonView(B).getFirst():m.activePokemonView(B)).damage()==count*20+(bench?0:30),"Scream wrong selected target");
            check(m.currentPlayer().equals(B)&&!m.hasPendingEffectChoice(),"Scream stalled");
        }
    }
    static void tachyon(){
        for(String id:List.of("sv4_en_089","sv4_en_225","sv4_en_249","sv4_en_261","sv8-5_en_157","svbsp_en_068"))for(boolean bench:List.of(false,true)){
            var ability=DATA.pokemon(id).orElseThrow().abilities().getFirst();TriggeredAbilityRules.validate(ability);
            var m=game(GeneratedRuleBatch8Test.hit(40,false),List.of(ability),EnergyType.PSYCHIC,"swsh5_en_124");UUID old=m.activePokemonView(A).pokemonId(),source=m.benchPokemonView(A).getFirst().pokemonId();
            check(m.triggerOrderPrompt()==null,"Direct setup must not trigger Tachyon");
            UUID target=bench?m.benchPokemonView(B).getFirst().pokemonId():m.activePokemonView(B).pokemonId();
            m.endTurn(A);m.evolveByEffect(B,target,hand(m,B,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);m.endTurn(B);
            m.switchActiveByEffect(A,A,source);var q=m.triggerOrderPrompt();check(q!=null&&q.optionalDecision(),"Tachyon missing optional entry trigger");
            reject(()->m.decideTriggeredEffect(B,q.id(),true));m.decideTriggeredEffect(A,q.id(),true);var p=m.effectPokemonPrompt(A);
            reject(()->m.chooseEffectPokemon(A,p.resolutionId(),p.step(),source));m.chooseEffectPokemon(A,p.resolutionId(),p.step(),target);
            check((bench?m.benchPokemonView(B).getFirst():m.activePokemonView(B)).damage()==20&&m.currentPlayer().equals(A),"Tachyon must place counters on chosen evolved target without ending turn");
            m.switchActiveByEffect(A,A,old);var skip=m.triggerOrderPrompt();m.decideTriggeredEffect(A,skip.id(),false);
            m.switchActiveByEffect(A,A,source);check(m.triggerOrderPrompt()==null,"Tachyon reused same source this turn");
            m.endTurn(A);m.switchActiveByEffect(B,A,old);check(m.triggerOrderPrompt()==null,"Tachyon triggered on opponent turn");m.endTurn(B);
            m.switchActiveByEffect(A,A,source);check(m.triggerOrderPrompt()!=null,"Tachyon failed to reset next turn");m.decideTriggeredEffect(A,m.triggerOrderPrompt().id(),false);
        }
    }
    static void morty(){
        for(String id:List.of("sv5_en_155","sv5_en_201","sv5_en_211"))for(int benches:List.of(1,3)){
            var m=game(GeneratedRuleBatch8Test.hit(40,false),List.of(),EnergyType.GRASS,id);m.endTurn(A);
            while(m.benchPokemonView(B).size()<benches)m.playBasicPokemonToBench(B,hand(m,B,"basic").instanceId());m.endTurn(B);
            var trainer=hand(m,A,"trainer");var before=m.privateView(A);m.playTrainer(A,trainer.instanceId(),null);var p=m.effectCardPrompt(A);
            check(p.minimum()==1&&p.maximum()==1&&!p.candidates().contains(trainer),"Morty exact cost must exclude itself");
            reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of()));reject(()->m.submitEffectCards(B,p.resolutionId(),p.step(),List.of(p.candidates().getFirst().instanceId())));
            check(m.privateView(A).deck().equals(before.deck()),"Morty drew before cost paid");
            var cost=p.candidates().getFirst();m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(cost.instanceId()));
            check(m.privateView(A).hand().size()==before.hand().size()-2+benches&&m.privateView(A).discard().containsAll(List.of(cost,trainer)),"Morty wrong draw count or cost conservation");
            check(!m.hasPendingEffectChoice()&&m.currentPlayer().equals(A)&&!m.mayPlaySupporter(A),"Morty continuation or Supporter limit");
        }
    }
}
