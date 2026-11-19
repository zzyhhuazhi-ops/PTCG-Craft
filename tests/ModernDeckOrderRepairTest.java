import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernDeckOrderRepairTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    public static void main(String[] args){onix();absol();gothitelle();hydreigon();System.out.println("ModernDeckOrderRepairTest passed: printed exact retreat cost after actual discard, either-player private ordered top three, top-two mandatory bottom choice, Hydreigon any Energy distribution/discard, short deck and authority");}
    static void hydreigon(){var a=BundledCombatData.load().pokemon("sv2_en_140").orElseThrow().abilities().getFirst();ActivatedAbilityRules.validate(a);boolean sawSpecial=false;
        for(int seed=0;seed<16;seed++)for(int size:List.of(0,1,2,3,8))for(boolean take:List.of(false,true)){
            var m=ModernEnergyRepairTest.game(TrainerPlayLockTest.item(),List.of(a),GeneratedRuleBatch8Test.hit(10,false),EnergyType.DARKNESS,seed);m.drawCardsByEffect(A,A,m.privateView(A).deck().size()-size);var before=m.privateView(A);var window=before.deck().subList(Math.max(0,size-3),size);var random=m.randomTranscript();UUID source=m.activePokemonView(A).pokemonId();
            if(size==0){check(!m.canUseAbility(A,source,a.id()),"Hydreigon offered empty window");continue;}
            m.useAbility(A,source,a.id());var q=m.effectCardPrompt(A);var expected=window.stream().filter(c->m.cardDefinition(c.definitionId()).category()==CardCategory.ENERGY).toList();
            check(new HashSet<>(q.candidates()).equals(new HashSet<>(expected))&&new HashSet<>(q.inspectedCards()).equals(new HashSet<>(window))&&m.clientView(B).effectSelection()==null,"Hydreigon lost Special Energy or leaked window");
            var chosen=take?q.candidates():List.<CardInstance>of();var ids=chosen.stream().map(CardInstance::instanceId).toList();m.submitEffectCards(A,q.resolutionId(),q.step(),ids);
            if(!chosen.isEmpty()){sawSpecial|=chosen.stream().anyMatch(c->c.definitionId().equals("special"));var d=m.effectAttachmentPrompt(A);UUID bench=m.benchPokemonView(A).getFirst().pokemonId();
                for(int i=0;i<chosen.size();i++)m.assignEffectEnergyDistribution(A,d.resolutionId(),d.step(),chosen.get(i).instanceId(),i%2==0?source:bench);
                m.confirmEffectEnergyDistribution(A,d.resolutionId(),d.step());
                for(var c:chosen)check(m.activePokemonView(A).attachedEnergyIds().contains(c.instanceId())||m.benchPokemonView(A).getFirst().attachedEnergyIds().contains(c.instanceId()),"Hydreigon lost actual attached Energy");}
            var rest=window.stream().filter(c->!chosen.contains(c)).toList();check(m.privateView(A).discard().containsAll(rest)&&m.privateView(A).discard().size()==before.discard().size()+rest.size()&&m.privateView(A).deck().equals(before.deck().subList(0,size-window.size()))&&m.privateView(A).hand().equals(before.hand())&&m.randomTranscript().equals(random)&&!m.hasPendingEffectChoice()&&!m.canUseAbility(A,source,a.id()),"Hydreigon remainder or continuation");
        }check(sawSpecial,"Hydreigon test missed Special Energy");
    }
    static void onix(){var a=ModernWindowDamageRepairTest.attack("sv3-5_en_095",0);Set<Integer>seen=new HashSet<>();
        for(int seed=0;seed<16;seed++)for(int size:List.of(0,1,3,5,8))for(int otherCost:List.of(0,3))for(boolean weak:List.of(false,true)){
            var m=ModernWindowDamageRepairTest.game(a,seed,weak,List.of(),Map.of("basic",4,"future-pokemon",otherCost));m.drawCardsByEffect(A,A,m.privateView(A).deck().size()-size);var w=ModernWindowDamageRepairTest.top(m,5);var before=m.privateView(A);var random=m.randomTranscript();int count=(int)w.stream().filter(c->c.definitionId().equals("basic")).count();seen.add(count);
            m.attack(A,a.id());check(m.lastAttack().finalDamage()==count*80*(weak?2:1)&&m.activePokemonView(B).damage()==count*80*(weak?2:1),"Onix wrong printed retreat count/damage timing");
            check(m.privateView(A).discard().size()==before.discard().size()+w.size()&&m.privateView(A).discard().containsAll(w)&&m.privateView(A).deck().size()==size-w.size()&&m.randomTranscript().equals(random)&&!m.hasPendingEffectChoice(),"Onix incorrect actual discard or stalled");
        }check(seen.contains(0)&&seen.stream().anyMatch(n->n>0),"Missing positive/zero retreat samples");
    }
    static void absol(){for(String id:List.of("sv3_en_135","sv3_en_214"))absol(id);}
    static void absol(String id){var a=ModernWindowDamageRepairTest.attack(id,0);
        for(UUID owner:List.of(A,B))for(int size:List.of(0,1,2,3,8)){
            var m=ModernWindowDamageRepairTest.game(a,0,false);m.drawCardsByEffect(A,owner,m.privateView(owner).deck().size()-size);var before=m.privateView(owner);var random=m.randomTranscript();m.attack(A,a.id());var choice=m.effectChoicePrompt(A);
            m.chooseEffectOption(A,choice.resolutionId(),choice.step(),owner.equals(A)?0:1);
            var expected=new ArrayList<>(before.deck());
            if(size>0){var q=m.effectCardPrompt(A);int n=Math.min(3,size);check(q.minimum()==n&&q.maximum()==n&&m.clientView(B).effectSelection()==null&&m.effectCardReveal()==null,"Absol incorrect short window/privacy: "+q.minimum()+"/"+q.maximum());
                var order=new ArrayList<>(q.candidates());Collections.reverse(order);var ids=order.stream().map(CardInstance::instanceId).toList();CopyAttackTest.reject(()->m.submitEffectCards(B,q.resolutionId(),q.step(),ids));
                if(n>1)CopyAttackTest.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),ids.subList(0,n-1)));
                expected.subList(expected.size()-n,expected.size()).clear();for(int i=n-1;i>=0;i--)expected.add(order.get(i));
                m.submitEffectCards(A,q.resolutionId(),q.step(),ids);CopyAttackTest.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),ids));
            }
            if(owner.equals(B)&&!expected.isEmpty())expected.removeLast();
            check(m.privateView(owner).deck().equals(expected)&&m.privateView(owner).discard().equals(before.discard())&&m.randomTranscript().equals(random)&&!m.hasPendingEffectChoice(),"Absol wrong actual order, extra movement or stall");
        }
    }
    static void gothitelle(){var a=BundledCombatData.load().pokemon("sv2_en_092").orElseThrow().abilities().getFirst();ActivatedAbilityRules.validate(a);
        for(int size:List.of(0,1,2,8))for(int selected:List.of(0,1)){
            if(size<2&&selected==1)continue;var m=ModernFieldRuleRepairTest.game(GeneratedRuleBatch8Test.hit(10,false),List.of(a),EnergyType.PSYCHIC);m.drawCardsByEffect(A,B,m.privateView(B).deck().size()-size);UUID source=m.activePokemonView(A).pokemonId();var before=m.privateView(B);var random=m.randomTranscript();
            if(size==0){check(!m.canUseAbility(A,source,a.id()),"Gothitelle offered empty inspection");continue;}
            m.useAbility(A,source,a.id());var q=m.effectCardPrompt(A);check(q.minimum()==1&&q.maximum()==1&&q.candidates().size()==Math.min(size,2)&&m.clientView(B).effectSelection()==null,"Gothitelle exact one/private window");
            CopyAttackTest.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of()));CopyAttackTest.reject(()->m.submitEffectCards(B,q.resolutionId(),q.step(),List.of(q.candidates().getFirst().instanceId())));
            var chosen=q.candidates().get(selected);var expected=new ArrayList<>(before.deck());expected.remove(chosen);expected.addFirst(chosen);m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(chosen.instanceId()));
            check(m.privateView(B).deck().equals(expected)&&m.privateView(B).hand().equals(before.hand())&&m.privateView(B).discard().equals(before.discard())&&!m.hasPendingEffectChoice()&&!m.canUseAbility(A,source,a.id())&&m.randomTranscript().equals(random),"Gothitelle wrong retained/bottom card or continuation");
        }
    }
}
