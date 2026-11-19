import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;
public final class ModernMimeRepairTest {
 static final UUID A=ModernRuleRepair2Test.A,B=ModernRuleRepair2Test.B;
 static void check(boolean ok,String s){ModernRuleRepair2Test.check(ok,s);}
 public static void main(String[] args){
  var data=BundledCombatData.load();var attack=ModernWindowDamageRepairTest.attack("sv5_en_063",0);
  var profiles=List.of(new TrainerProfile(TrainerCategory.SUPPORTER,new TrainerEffect(TrainerEffect.Opcode.DRAW,2,0)),
   new TrainerProfile(TrainerCategory.SUPPORTER,new TrainerEffect(TrainerEffect.Opcode.SWITCH_OPPONENT_ACTIVE,0,0)),
   new TrainerProfile(TrainerCategory.SUPPORTER,new TrainerEffect(TrainerEffect.Opcode.SEARCH_POKEMON_TO_HAND,1,0)),
   data.trainer("sv6_en_144").orElseThrow(),data.trainer("sv8-5_en_093").orElseThrow());
  for(int seed:List.of(0,30,80))for(int mode=0;mode<profiles.size();mode++)for(boolean use:List.of(false,true)){
   var m=ModernEnergyRepairTest.game(profiles.get(mode),List.of(),attack,EnergyType.PSYCHIC,seed);m.drawCardsByEffect(A,B,24);
   UUID active=m.activePokemonView(A).pokemonId();m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,ModernRuleRepair2Test.hand(m,A,"energy").instanceId(),A,active);
   var opponent=List.copyOf(m.privateView(B).hand());int before=m.privateView(A).hand().size();m.attack(A,attack.id());
   var reveal=m.effectCardReveal();check(reveal!=null&&new HashSet<>(reveal.cardIds()).equals(new HashSet<>(opponent.stream().map(CardInstance::definitionId).toList())),"Opponent hand not actually revealed");
   CopyAttackTest.reject(()->m.acknowledgeEffectReveal(B,reveal.id()));m.acknowledgeEffectReveal(A,reveal.id());
   var p=m.effectCardPrompt(A);check(p.minimum()==0&&p.maximum()==1&&p.candidates().stream().allMatch(c->c.definitionId().equals("trainer")),"Supporter-only optional choice");
   var chosen=use?List.of(p.candidates().getFirst().instanceId()):List.<UUID>of();
   CopyAttackTest.reject(()->m.submitEffectCards(B,p.resolutionId(),p.step(),chosen));m.submitEffectCards(A,p.resolutionId(),p.step(),chosen);
   CopyAttackTest.reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),chosen));
   if(use&&mode==1){var target=m.effectPokemonPrompt(A);m.chooseEffectPokemon(A,target.resolutionId(),target.step(),target.targets().getFirst().pokemonId());}
   if(use&&mode==2){var search=m.effectCardPrompt(A);check(search.candidates().stream().allMatch(c->Set.of("basic","wrongmon","evolved").contains(c.definitionId())),"Copied search wrong candidates");m.submitEffectCards(A,search.resolutionId(),search.step(),List.of(search.candidates().getFirst().instanceId()));}
   if(m.effectCardReveal()!=null){var found=m.effectCardReveal();m.acknowledgeEffectReveal(found.confirmingPlayer(),found.id());}
   check(!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Copied Supporter stalled: mode="+mode);
   check(m.privateView(B).hand().containsAll(opponent),"Copied Supporter was moved or consumed");
   int expected=!use||mode==1?before:mode==2?before+1:mode==4?0:before+2;
   check(m.privateView(A).hand().size()==expected,"Copied effect hand result: mode="+mode+" actual="+m.privateView(A).hand().size()+" expected="+expected);
   check(m.activePokemonView(A).pokemonId().equals(active),"Copied this-card clause removed attacker");
  }
  System.out.println("ModernMimeRepairTest passed: 30 cases; reveal, optional choice, draw, gust, search, Caretaker, Amarys delayed discard, physical card conservation and authority/replay");
 }
}
