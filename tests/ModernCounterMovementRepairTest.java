import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;
public final class ModernCounterMovementRepairTest {
 static final UUID A=ModernRuleRepair2Test.A,B=ModernRuleRepair2Test.B;
 static void check(boolean b,String s){ModernRuleRepair2Test.check(b,s);}
 static ServerMatch.DamageCounterPlacement pick(UUID id,int n){return new ServerMatch.DamageCounterPlacement(B,id,n);}
 public static void main(String[] args){
  legacyAlias();
  for(boolean hacking:List.of(false,true))for(int seed:List.of(0,30,80))for(int mode=0;mode<7;mode++){
   var attack=ModernWindowDamageRepairTest.attack(hacking?"sv6_en_082":"sv5_en_107",hacking?0:1);
   var m=ModernShuffleScopeRepairTest.game(attack,seed);m.endTurn(A);m.endTurn(B);
   UUID active=m.activePokemonView(B).pokemonId(),bench=m.benchPokemonView(B).getFirst().pokemonId();
   var field=EventTargetBindingTest.field(m);field.placeDamageCounters(B,bench,5);field.placeDamageCounters(B,active,mode==6?48:2);
   if(mode==3||mode==4)field.addProtection(B,B,mode==3?bench:active,new ProtectionEffect(new EffectProtection(EffectProtection.Source.ATTACK,EffectProtection.Relation.OPPONENT,EffectProtection.Scope.EFFECTS_ONLY),ContinuousEffect.Expiry.LEAVES_PLAY));
   ModernShuffleScopeRepairTest.pay(m,A,m.activePokemonView(A).pokemonId());m.attack(A,attack.id());
   if(mode==3){
    if(hacking){var p=m.damageAllocationPrompt(A);check(p.candidates().stream().noneMatch(t->t.pokemonId().equals(bench)),"Protected source allowed");m.submitDamageAllocation(A,p.resolutionId(),p.step(),List.of());}
    check(!m.hasPendingEffectChoice(),"Protected-only source stalled");continue;
   }
   var p=m.damageAllocationPrompt(A);check(p.optional()&&p.canConfirm(),"Optional zero choice missing");
   CopyAttackTest.reject(()->m.submitDamageAllocation(B,p.resolutionId(),p.step(),List.of()));
   CopyAttackTest.reject(()->m.submitDamageAllocation(A,p.resolutionId(),p.step(),List.of(pick(bench,6))));
   CopyAttackTest.reject(()->m.submitDamageAllocation(A,p.resolutionId(),p.step(),List.of(pick(bench,1),pick(bench,1))));
   int moved=mode==0?0:mode==2?5:3;
   m.submitDamageAllocation(A,p.resolutionId(),p.step(),moved==0?List.of():List.of(pick(bench,moved)));
   if(hacking&&moved>0){
    var dest=m.damageAllocationPrompt(A);check(!dest.optional()&&!dest.resolutionId().equals(p.resolutionId()),"Fresh phase token missing");
    CopyAttackTest.reject(()->m.submitDamageAllocation(A,p.resolutionId(),p.step(),List.of(pick(active,moved))));
    CopyAttackTest.reject(()->m.submitDamageAllocation(A,dest.resolutionId(),dest.step(),List.of(pick(bench,moved))));
    check(field.pokemonView(B,bench).damage()==50,"Source changed before destination confirmation");
    m.submitDamageAllocation(A,dest.resolutionId(),dest.step(),List.of(pick(active,moved)));
   }
   if(mode==6){check(A.equals(m.pendingChoicePlayer())||m.privateView(A).prizes().size()<6,"KO did not resolve");}
   else {
    check(field.pokemonView(B,bench).damage()==(5-moved)*10,"Wrong source amount");
    check(field.pokemonView(B,active).damage()==20+(mode==4?0:moved*10),"Wrong destination/protection: hacking="+hacking+", mode="+mode+", damage="+field.pokemonView(B,active).damage());
    check(!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Move stalled");
   }
  }
  System.out.println("ModernCounterMovementRepairTest passed: 42 cases; optional/partial/all movement, exact sources, fresh phase tokens, no self-transfer, immunity, KO and atomic confirmation");
 }
 static void legacyAlias(){var attack=dev.hua.ptcglive.rules.BundledCombatData.load().pokemon("svalt_en_143").orElseThrow().attacks().getFirst();var m=ModernShuffleScopeRepairTest.game(attack,0);m.endTurn(A);m.endTurn(B);UUID active=m.activePokemonView(B).pokemonId(),bench=m.benchPokemonView(B).getFirst().pokemonId();var field=EventTargetBindingTest.field(m);field.placeDamageCounters(B,bench,3);ModernShuffleScopeRepairTest.pay(m,A,m.activePokemonView(A).pokemonId());m.attack(A,attack.id());var from=m.damageAllocationPrompt(A);m.submitDamageAllocation(A,from.resolutionId(),from.step(),List.of(pick(bench,2)));var to=m.damageAllocationPrompt(A);m.submitDamageAllocation(A,to.resolutionId(),to.step(),List.of(pick(active,2)));check(field.pokemonView(B,bench).damage()==10&&field.pokemonView(B,active).damage()==20&&m.activePokemonView(B).conditions().contains(dev.hua.ptcglive.engine.field.SpecialCondition.CONFUSED),"Legacy Alakazam lost Confusion or redistribution");}
}
