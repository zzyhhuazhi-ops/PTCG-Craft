import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernEnergyCountRepairTest {
    static final UUID A=ModernRuleRepair2Test.A,B=ModernRuleRepair2Test.B;
    static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean b,String why){ModernRuleRepair2Test.check(b,why);}
    public static void main(String[] args){grafaiai();whiscash();zebstrika();System.out.println("ModernEnergyCountRepairTest passed: Grafaiai distinct discarded Basic types, Whiscash live typed units/deck-out, and Zebstrika actual opposing Prize-taken attachment limits/distribution");}
    static void zebstrika(){
        var a=DATA.pokemon("sv4_en_063").orElseThrow().attacks().getFirst();AttackProgramRules.validate(a);
        for(int taken:List.of(0,1,3))for(int available:List.of(0,2))for(boolean take:List.of(false,true))for(boolean split:List.of(false,true)){
            var m=ModernFieldRuleRepairTest.game(a,List.of(),EnergyType.LIGHTNING);m.drawCardsByEffect(A,A,8);UUID source=m.activePokemonView(A).pokemonId();ModernRuleRepair2Test.pay(m,A,source,"energy",1);
            for(int i=0;i<taken;i++){if(m.benchPokemonView(A).isEmpty())m.playBasicPokemonToBench(A,ModernRuleRepair2Test.hand(m,A,"basic").instanceId());m.knockOutPokemonByEffect(A,A,m.benchPokemonView(A).getFirst().pokemonId());m.takePrizeAt(B,0);}
            if(m.benchPokemonView(A).isEmpty())m.playBasicPokemonToBench(A,ModernRuleRepair2Test.hand(m,A,"basic").instanceId());UUID bench=m.benchPokemonView(A).getFirst().pokemonId();
            var energy=m.privateView(A).hand().stream().filter(c->c.definitionId().equals("energy")).limit(available).toList();check(energy.size()==available,"Zebstrika fixture");
            var discarded=new ArrayList<>(energy);discarded.add(ModernRuleRepair2Test.hand(m,A,"wrong"));discarded.add(ModernRuleRepair2Test.hand(m,A,"special"));m.discardHandCardsByEffect(A,A,discarded.stream().map(CardInstance::instanceId).toList());var before=m.privateView(A).discard();
            check(m.privateView(B).prizes().size()==6-taken,"Zebstrika prize fixture");m.attack(A,a.id());int count=take?Math.min(taken,available):0;
            if(m.hasPendingEffectChoice()){
                var q=m.effectCardPrompt(A);check(taken>0&&q.minimum()==0&&q.maximum()<=taken&&q.candidates().stream().allMatch(c->c.definitionId().equals("energy")),"Zebstrika used remaining/own prizes or wrong energy");
                if(q.candidates().size()>taken)CopyAttackTest.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),q.candidates().stream().map(CardInstance::instanceId).toList()));
                var selected=q.candidates().stream().limit(count).toList();m.submitEffectCards(A,q.resolutionId(),q.step(),selected.stream().map(CardInstance::instanceId).toList());
                if(count>0){var distribution=m.effectAttachmentPrompt(A);check(distribution.targets().containsAll(List.of(source,bench))&&!distribution.targets().contains(m.activePokemonView(B).pokemonId()),"Zebstrika target scope");
                    for(int i=0;i<count;i++)m.assignEffectEnergyDistribution(A,distribution.resolutionId(),distribution.step(),selected.get(i).instanceId(),split&&i%2==1?bench:source);
                    m.confirmEffectEnergyDistribution(A,distribution.resolutionId(),distribution.step());}
            }else check(count==0,"Zebstrika skipped legal attachments");
            check(m.privateView(A).discard().size()==before.size()-count&&m.activePokemonView(A).attachedEnergyIds().size()+m.benchPokemonView(A).getFirst().attachedEnergyIds().size()==1+count&&m.currentPlayer().equals(B)&&!m.hasPendingEffectChoice(),"Zebstrika duplicated/missed attachments or stalled");
        }
    }
    static void grafaiai(){
        for(String id:List.of("sv2_en_146","sv2_en_223","sv4-5_en_190"))for(int mode=0;mode<4;mode++){
            var attack=DATA.pokemon(id).orElseThrow().attacks().get(1);AttackProgramRules.validate(attack);var m=ModernFieldRuleRepairTest.game(attack,List.of(),EnergyType.DARKNESS);
            ModernRuleRepair2Test.pay(m,A,m.activePokemonView(A).pokemonId(),"energy",1);var before=m.privateView(A);m.attack(A,attack.id());var q=m.effectCardPrompt(A);
            check(q.minimum()==0&&q.candidates().stream().allMatch(c->Set.of("energy","wrong").contains(c.definitionId()))&&m.clientView(B).effectSelection()==null,"Grafaiai filter or privacy");
            for(String forbidden:List.of("special","basic"))CopyAttackTest.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(ModernRuleRepair2Test.hand(m,A,forbidden).instanceId())));
            var selected=new ArrayList<UUID>();if(mode>0)selected.addAll(q.candidates().stream().filter(c->c.definitionId().equals("energy")).limit(mode==1?1:2).map(CardInstance::instanceId).toList());if(mode==3)selected.add(ModernRuleRepair2Test.hand(m,A,"wrong").instanceId());
            check(selected.size()==mode,"Grafaiai fixture missing duplicates");m.submitEffectCards(A,q.resolutionId(),q.step(),selected);
            check(m.lastAttack().finalDamage()==(mode==0?0:mode==3?80:40)&&m.privateView(A).hand().size()==before.hand().size()-mode&&m.privateView(A).discard().size()==before.discard().size()+mode&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Grafaiai counted physical cards instead of distinct discarded types, or stalled");
            CopyAttackTest.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),selected));
        }
    }
    static void whiscash(){
        var attack=DATA.pokemon("sv3_en_109").orElseThrow().attacks().getFirst();AttackProgramRules.validate(attack);
        for(int basic:List.of(1,3))for(boolean special:List.of(false,true))for(int remaining:List.of(0,1,3,10)){
            var m=ModernFieldRuleRepairTest.game(attack,List.of(),EnergyType.FIGHTING);m.drawCardsByEffect(A,A,8);UUID source=m.activePokemonView(A).pokemonId();
            ModernRuleRepair2Test.pay(m,A,source,"energy",basic);ModernRuleRepair2Test.pay(m,A,source,"wrong",1);ModernRuleRepair2Test.pay(m,A,m.benchPokemonView(A).getFirst().pokemonId(),"energy",1);if(special)ModernRuleRepair2Test.pay(m,A,source,"special",1);
            m.drawCardsByEffect(A,B,m.privateView(B).deck().size()-remaining);var before=m.privateView(B);m.attack(A,attack.id());int count=Math.min(remaining,basic+(special?2:0));
            var milled=before.deck().subList(before.deck().size()-count,before.deck().size());check(m.privateView(B).discard().size()==before.discard().size()+count&&m.privateView(B).discard().containsAll(milled)&&!m.hasPendingEffectChoice(),"Whiscash ignored live units/type/source or did not discard actual top cards");
            if(remaining<=count)check(m.result()!=null&&A.equals(m.result().winner()),"Whiscash deck-out did not end normally");
            else check(m.privateView(B).deck().size()==remaining-count-1&&m.currentPlayer().equals(B),"Whiscash duplicated next-turn draw or stalled");
        }
    }
}
