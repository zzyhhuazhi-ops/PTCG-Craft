import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernNamedCounterRepairTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static void add(ServerMatch m,UUID p,String id){GeneratedRuleBatch37Test.add(m,p,id);}
    public static void main(String[] args){gnaw();reactions();excitedPunch();System.out.println("ModernNamedCounterRepairTest passed: exact live own field names, independent target guards, fixed Hitmonchan and scaled Maushold reactions before KO, named attack bonus/expiry/switch");}
    static void gnaw(){
        for(String id:List.of("sv2_en_168","sv2_en_226"))for(int count:List.of(0,1,3))for(boolean evolve:List.of(false,true))for(boolean guard:List.of(false,true)){
            var a=DATA.pokemon(id).orElseThrow().attacks().get(1);AttackProgramRules.validate(a);
            var m=GeneratedRuleBatch37Test.fixture(count==0?"Maushold ex":"Maushold","Maushold","Maushold",EnergyType.COLORLESS,null,a);
            add(m,A,"wrong");if(count==3){add(m,A,"p1");add(m,A,"p2");if(evolve){UUID p=m.benchPokemonView(A).getLast().pokemonId();m.evolveByEffect(A,p,GeneratedRuleBatch37Test.hand(m,A,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);}}
            m.endTurn(A);add(m,B,"p1");add(m,B,"p2");m.endTurn(B);ModernRuleRepair2Test.pay(m,A,active(m,A),"energy",1);ModernRuleRepair2Test.pay(m,A,active(m,A),"special",1);
            UUID shield=m.benchPokemonView(B).getFirst().pokemonId();if(guard)EventTargetBindingTest.field(m).addProtection(B,B,shield,new ProtectionEffect(new EffectProtection(EffectProtection.Source.ATTACK,EffectProtection.Relation.OPPONENT,EffectProtection.Scope.EFFECTS_ONLY),ContinuousEffect.Expiry.LEAVES_PLAY));
            m.attack(A,a.id());int expected=(count==3&&evolve?2:count)*10;check(m.activePokemonView(B).damage()==expected,"Gnaw counted own covered, similar or opponent names");
            for(var p:m.benchPokemonView(B))check(p.damage()==(guard&&p.pokemonId().equals(shield)?0:expected),"Gnaw guard skipped wrong targets or ignored protection");
            check(m.activePokemonView(A).damage()==0&&m.benchPokemonView(A).stream().allMatch(p->p.damage()==0)&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Gnaw hit own field or stalled");
        }
    }
    static void reactions(){
        for(String id:List.of("sv3-5_en_107","sv4_en_155","sv4_en_233"))for(int count:List.of(1,3))for(String mode:List.of("NORMAL","LETHAL","ZERO","BENCH","GUARD","DISABLED","COUNTER")){
            boolean fixed=id.equals("sv3-5_en_107");var ability=DATA.pokemon(id).orElseThrow().abilities().getFirst();TriggeredAbilityRules.validate(ability);
            String extra=mode.equals("BENCH")?",\"damageTarget\":\"ALL_OPPONENT_BENCH\"":mode.equals("COUNTER")?",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":1}]}]":"";
            var a=DamageTriggeredAbilityTest.attack(mode.equals("LETHAL")?500:Set.of("ZERO","COUNTER").contains(mode)?0:10,extra);
            var m=GeneratedRuleBatch37Test.fixture(fixed?"Hitmonchan":"Maushold ex","Tandemaus","Maushold",EnergyType.COLORLESS,ability,a);add(m,A,"p1");m.endTurn(A);add(m,B,"wrong");
            if(count==3){add(m,B,"p1");add(m,B,"p2");}m.endTurn(B);UUID source=active(m,B);
            if(mode.equals("BENCH"))m.switchActiveByEffect(A,B,m.benchPokemonView(B).getFirst().pokemonId());
            if(mode.equals("GUARD"))EventTargetBindingTest.field(m).addProtection(A,A,active(m,A),new ProtectionEffect(new EffectProtection(EffectProtection.Source.ABILITY,EffectProtection.Relation.OPPONENT,EffectProtection.Scope.EFFECTS_ONLY),ContinuousEffect.Expiry.LEAVES_PLAY));
            if(mode.equals("DISABLED"))m.disableAbilityEffectsByEffect(A,B,source);
            m.attack(A,a.id());DamageTriggeredAbilityTest.finishTriggers(m);int expected=Set.of("NORMAL","LETHAL").contains(mode)?30*(fixed?1:count):0;
            check(m.activePokemonView(A).damage()==expected,"Reaction ignored actual damage, position, source before KO, exact names or shield: "+id+" "+mode+" "+count);
            int budget=10;while((m.phase()==MatchPhase.PRIZE_SELECTION||m.phase()==MatchPhase.CHOOSE_REPLACEMENT)&&budget-->0){UUID chooser=m.pendingChoicePlayer();if(m.phase()==MatchPhase.PRIZE_SELECTION)m.takePrizeAt(chooser,0);else m.chooseReplacement(chooser,m.benchPokemonView(chooser).getFirst().pokemonId());}
            check(budget>0&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Reaction prevented normal KO/replacement/turn continuation");
        }
    }
    static void excitedPunch(){
        var a=DATA.pokemon("sv3-5_en_107").orElseThrow().attacks().getFirst();AttackProgramRules.validate(a);
        for(int mode=0;mode<3;mode++){
            var m=GeneratedRuleBatch37Test.fixture("Hitmonchan","Other","Other",EnergyType.FIGHTING,null,a);add(m,A,"p1");UUID source=active(m,A);ModernRuleRepair2Test.pay(m,A,source,"special",1);
            m.attack(A,a.id());check(m.lastAttack().finalDamage()==60,"Excited Punch granted boost immediately");m.endTurn(B);
            if(mode==1){m.switchActiveByEffect(A,A,m.benchPokemonView(A).getFirst().pokemonId());m.switchActiveByEffect(A,A,source);}if(mode==2){m.endTurn(A);m.endTurn(B);}
            var field=EventTargetBindingTest.field(m);check(field.attackDamageBonus(A,source,"unrelated")==0&&field.attackDamageBonus(A,source,a.id())==(mode==0?60:0),"Excited Punch boosted other attacks or survived switch/expiry");
            m.attack(A,a.id());check(m.lastAttack().finalDamage()==(mode==0?120:60),"Excited Punch did not apply named next-turn bonus");
        }
    }
}
