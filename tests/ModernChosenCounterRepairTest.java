import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernChosenCounterRepairTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    public static void main(String[] args){counters();eel();weavile();System.out.println("ModernChosenCounterRepairTest passed: Annihilape ex two prints and Walking Wake actual optional self counters before damage, own effect prevention, self-KO timing; Eelektross actual switched target and conditional coin/paralysis; Weavile two prints incoming optional own-turn Basic-only gust");}
    static void weavile(){
        for(String id:List.of("sv2_en_134","sv4-5_en_183"))for(boolean evolved:List.of(false,true))for(boolean accept:List.of(false,true))for(boolean ownTurn:List.of(false,true)){
            var ability=BundledCombatData.load().pokemon(id).orElseThrow().abilities().getFirst();TriggeredAbilityRules.validate(ability);
            var m=ModernEnergyRepairTest.game(TrainerPlayLockTest.item(),List.of(ability),GeneratedRuleBatch8Test.hit(10,false),EnergyType.DARKNESS,0);
            m.endTurn(A);m.drawCardsByEffect(B,B,20);UUID target=m.benchPokemonView(B).getFirst().pokemonId();if(evolved)m.evolveByEffect(B,target,ModernRuleRepair2Test.hand(m,B,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);if(ownTurn)m.endTurn(B);
            UUID old=m.activePokemonView(A).pokemonId(),incoming=m.benchPokemonView(A).getFirst().pokemonId(),opponentOld=m.activePokemonView(B).pokemonId();m.switchActiveByEffect(ownTurn?A:B,A,incoming);
            if(!ownTurn){check(m.triggerOrderPrompt()==null&&!m.hasPendingEffectChoice()&&m.activePokemonView(B).pokemonId().equals(opponentOld),"Weavile triggered during opposing turn");continue;}
            var order=m.triggerOrderPrompt();if(order!=null){check(order.choosingPlayer().equals(A),"Weavile wrong optional chooser");m.decideTriggeredEffect(A,order.id(),accept);}
            if(accept&&!evolved){var q=m.effectPokemonPrompt(A);check(q.targets().stream().map(EffectTargetResolution.PokemonTarget::pokemonId).toList().equals(List.of(target)),"Weavile offered Active or non-Basic replacement");CopyAttackTest.reject(()->m.chooseEffectPokemon(B,q.resolutionId(),q.step(),target));m.chooseEffectPokemon(A,q.resolutionId(),q.step(),target);}
            check(m.activePokemonView(B).pokemonId().equals(accept&&!evolved?target:opponentOld)&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(A),"Weavile basic filter, decline or continuation");
            if(accept&&!evolved){m.switchActiveByEffect(A,A,old);var unused=m.triggerOrderPrompt();if(unused!=null)m.decideTriggeredEffect(A,unused.id(),false);m.switchActiveByEffect(A,A,incoming);check(m.triggerOrderPrompt()==null&&!m.hasPendingEffectChoice(),"Weavile reused same-source once-per-turn trigger");}
        }
    }
    static void counters(){
        for(String id:List.of("sv2_en_242","svbsp_en_032","sv6_en_063")){int limit=id.equals("sv6_en_063")?9:12;var a=ModernWindowDamageRepairTest.attack(id,id.equals("sv6_en_063")?1:0);
            for(int count=0;count<=limit;count++)for(boolean guard:List.of(false,true))for(boolean lethal:List.of(false,true))for(boolean weak:List.of(false,true)){
                var m=ModernWindowDamageRepairTest.game(a,0,weak);UUID source=m.activePokemonView(A).pokemonId();var physical=EventTargetBindingTest.field(m).topCard(A,source);
                if(lethal){for(int i=0;i<3;i++)m.placeDamageCountersByEffect(A,A,source,60);m.placeDamageCountersByEffect(A,A,source,19);}
                if(guard)EventTargetBindingTest.field(m).addProtection(A,A,source,new ProtectionEffect(new EffectProtection(EffectProtection.Source.ATTACK,EffectProtection.Relation.OWN,EffectProtection.Scope.EFFECTS_ONLY),ContinuousEffect.Expiry.LEAVES_PLAY));
                var random=m.randomTranscript();m.attack(A,a.id());var q=m.effectChoicePrompt(A);check(q.optionsZh().size()==limit+1&&m.activePokemonView(B).damage()==0,"Counter choice must precede damage and include zero");
                int option=count==0?limit:count-1;CopyAttackTest.reject(()->m.chooseEffectOption(B,q.resolutionId(),q.step(),option));m.chooseEffectOption(A,q.resolutionId(),q.step(),option);
                int placed=guard?0:count;check(m.lastAttack().finalDamage()==placed*20*(weak?2:1)&&m.activePokemonView(B).damage()==placed*20*(weak?2:1),"Counter damage used requested/preexisting counters or self-KO cancelled damage");
                if(lethal&&placed>0)check(m.privateView(A).discard().contains(physical)&&m.phase()==MatchPhase.PRIZE_SELECTION,"Self-counter KO must finish attack before awarding prize");
                else check(m.activePokemonView(A).damage()==(lethal?1990:0)+placed*10&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Wrong actual self counters or continuation");
                check(m.randomTranscript().equals(random),"Counter choice consumed RNG");CopyAttackTest.reject(()->m.chooseEffectOption(A,q.resolutionId(),q.step(),option));
            }
        }
    }
    static void eel(){var a=ModernWindowDamageRepairTest.attack("sv3_en_069",0);Set<Boolean>headsSeen=new HashSet<>();
        for(int seed=0;seed<32;seed++)for(boolean bench:List.of(false,true)){
            var m=ModernWindowDamageRepairTest.game(a,seed,false);UUID old=m.activePokemonView(B).pokemonId(),next=m.benchPokemonView(B).getFirst().pokemonId();if(!bench)m.movePokemonByEffect(A,B,next,LeavePlayDestination.HAND);int rng=m.randomTranscript().size();
            m.attack(A,a.id());if(bench){var q=m.effectPokemonPrompt(A);CopyAttackTest.reject(()->m.chooseEffectPokemon(B,q.resolutionId(),q.step(),next));m.chooseEffectPokemon(A,q.resolutionId(),q.step(),next);}var random=m.randomTranscript().subList(rng,m.randomTranscript().size());
            check(m.activePokemonView(B).pokemonId().equals(bench?next:old)&&m.activePokemonView(B).damage()==(bench?60:0)&&random.size()==(bench?1:0)&&!m.hasPendingEffectChoice(),"Eelektross damaged/rolled without switch or targeted old Active");
            if(bench){boolean heads=random.getFirst().result()==0;headsSeen.add(heads);check(m.activePokemonView(B).conditions().contains(SpecialCondition.PARALYZED)==heads&&m.benchPokemonView(B).getFirst().damage()==0,"Eelektross paralysis target/result");}
        }check(headsSeen.size()==2,"Eelektross missing coin branch");
    }
}
