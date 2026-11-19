import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernAttackFlowRepairTest {
    static final UUID A=ModernRuleRepair2Test.A,B=ModernRuleRepair2Test.B;
    static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean b,String why){ModernRuleRepair2Test.check(b,why);}
    static AttackDefinition attack(String id,int slot){var a=DATA.pokemon(id).orElseThrow().attacks().get(slot);AttackProgramRules.validate(a);return a;}
    static ServerMatch game(AttackDefinition a,int seed){var m=ModernShuffleScopeRepairTest.game(a,seed);m.endTurn(A);m.endTurn(B);ModernShuffleScopeRepairTest.pay(m,A,m.activePokemonView(A).pokemonId());return m;}
    public static void main(String[] args){retreat();thrash();tail();dialga();chinchou();luxray();grimm();melmetal();rotom();ironTreads();System.out.println("ModernAttackFlowRepairTest passed: retreat costs, coin branches, optional costs, ordered searches, hand discard, forced switch, all-Tool discard, revealed Item/Tool discard and 72 named-Capsule reduction scenarios");}
    static void ironTreads(){
        var sets=List.of(List.<String>of(),List.of("wrongtool"),List.of("sv4_en_164"),List.of("sv4_en_250"),List.of("sv4_en_164","sv4_en_250"),List.of("sv4_en_164","wrongtool"));
        var capacity=DATA.pokemon("sm8_en_130").orElseThrow().abilities().getFirst();
        for(String id:List.of("sv4-5_en_066","svbsp_en_073"))for(var tools:sets)for(boolean accept:List.of(false,true))for(int after=0;after<3;after++){
            var a=attack(id,0);var m=ModernTargetRuleRepairTest.toolGame(a,List.of(capacity));UUID host=m.activePokemonView(A).pokemonId();
            m.playBasicPokemonToBench(A,ModernRuleRepair2Test.hand(m,A,"basic").instanceId());UUID bench=m.benchPokemonView(A).getFirst().pokemonId();
            m.drawCardsByEffect(A,B,6);ModernRuleRepair2Test.pay(m,B,m.activePokemonView(B).pokemonId(),"energy",1);
            for(String tool:tools)m.playTrainer(A,ModernRuleRepair2Test.hand(m,A,tool).instanceId(),host);
            int matching=(int)tools.stream().filter(t->!t.equals("wrongtool")).count();m.attack(A,a.id());
            check(m.activePokemonView(B).damage()==150,"Iron-Clad Roll chose cost before damage");
            if(m.hasPendingEffectChoice()){var q=m.effectChoicePrompt(A);m.chooseEffectOption(A,q.resolutionId(),q.step(),accept?0:1);}
            if(accept&&matching>0){var q=m.effectCardPrompt(A);check(q.minimum()==matching&&q.maximum()==matching&&q.candidates().stream().noneMatch(c->c.definitionId().equals("wrongtool")),"Iron-Clad Roll did not require all matching Tools only");
                if(matching>1)CopyAttackTest.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(q.candidates().getFirst().instanceId())));
                m.submitEffectCards(A,q.resolutionId(),q.step(),q.candidates().stream().map(CardInstance::instanceId).toList());}
            check(m.activePokemonView(A).attachedTools().size()==tools.size()-(accept?matching:0)&&m.currentPlayer().equals(B),"Iron-Clad Roll discarded wrong Tools or stalled");
            if(after==1){m.switchActiveByEffect(B,A,bench);m.switchActiveByEffect(B,A,host);}
            if(after==2){m.endTurn(B);m.endTurn(A);}
            m.attack(B,a.id());if(m.hasPendingEffectChoice()){var q=m.effectChoicePrompt(B);m.chooseEffectOption(B,q.resolutionId(),q.step(),1);}
            check(m.activePokemonView(A).damage()==(accept&&matching>0&&after==0?0:150)&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(A),"Iron-Clad Roll wrong conditional reduction, switch clearing or expiry");
        }
    }
    static void rotom(){
        var a=attack("sv8_en_061",0);
        for(String trainer:List.of("sv4_en_170","swsh5_en_124"))for(int mode:List.of(0,1,2)){
            var m=GeneratedRuleBatch8Test.fixture(List.of(),EnergyType.LIGHTNING,EnergyType.LIGHTNING,DATA.trainer(trainer).orElseThrow(),a);
            if(mode>0)m.discardHandCardsByEffect(A,B,m.privateView(B).hand().stream().filter(c->mode==2||c.definitionId().equals("tool")).map(CardInstance::instanceId).toList());
            var before=m.privateView(B).hand();var discard=m.privateView(B).discard();var draw=m.privateView(B).deck().getLast();var removed=before.stream().filter(c->Set.of(TrainerCategory.ITEM,TrainerCategory.TOOL).contains(m.cardDefinition(c.definitionId()).trainerCategory())).toList();
            ModernRuleRepair2Test.pay(m,A,m.activePokemonView(A).pokemonId(),"energy",1);m.attack(A,a.id());var reveal=m.effectCardReveal();check((reveal!=null)==!before.isEmpty(),"Rotom hand reveal missing or offered for empty hand");if(reveal!=null){check(m.privateView(B).hand().equals(before),"Rotom discarded before reveal acknowledgment");m.acknowledgeEffectReveal(reveal.confirmingPlayer(),reveal.id());}
            var expected=new ArrayList<>(before);expected.removeAll(removed);expected.add(draw);check(m.privateView(B).hand().equals(expected)&&m.privateView(B).discard().size()==discard.size()+removed.size()&&m.privateView(B).discard().containsAll(removed)&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Rotom missed items/tools, discarded a Supporter or stalled");
        }
    }
    static void grimm(){
        var a=attack("sv7_en_096",0);
        for(boolean bench:List.of(false,true)){
            var m=game(a,0);UUID old=m.activePokemonView(B).pokemonId(),replacement=m.benchPokemonView(B).getFirst().pokemonId();if(!bench)m.movePokemonByEffect(A,B,replacement,LeavePlayDestination.HAND);
            m.attack(A,a.id());if(bench){check(m.activePokemonView(B).damage()==0,"Goad damage happened before replacement");var q=m.effectPokemonPrompt(B);CopyAttackTest.reject(()->m.chooseEffectPokemon(A,q.resolutionId(),q.step(),replacement));m.chooseEffectPokemon(B,q.resolutionId(),q.step(),replacement);}
            check(m.activePokemonView(B).pokemonId().equals(bench?replacement:old)&&m.activePokemonView(B).damage()==(bench?160:0)&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Goad damaged old Active or damaged after failed switch");
            if(bench)check(m.benchPokemonView(B).getFirst().damage()==0,"Former Active took Goad damage");
        }
    }
    static void melmetal(){for(String id:List.of("sv7_en_104","svalt_en_167"))melmetal(id);}
    static void melmetal(String id){
        var a=attack(id,1);var capacity=DATA.pokemon("sm8_en_130").orElseThrow().abilities().getFirst();
        for(int count:List.of(0,1,2)){
            var m=GeneratedRuleBatch8Test.fixture(List.of(capacity),EnergyType.METAL,EnergyType.METAL,DATA.trainer("swsh5_en_124").orElseThrow(),a);UUID host=m.activePokemonView(A).pokemonId();ModernRuleRepair2Test.pay(m,A,host,"energy",3);
            for(int i=0;i<count;i++)m.playTrainer(A,ModernRuleRepair2Test.hand(m,A,"tool").instanceId(),host);
            m.attack(A,a.id());if(count>0){var q=m.effectCardPrompt(A);check(q.minimum()==count&&q.candidates().size()==count&&m.activePokemonView(B).damage()==0,"Tool discard must be complete and before damage");if(count==2)CopyAttackTest.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(q.candidates().getFirst().instanceId())));m.submitEffectCards(A,q.resolutionId(),q.step(),q.candidates().stream().map(CardInstance::instanceId).toList());}
            check(m.activePokemonView(B).damage()==(count==0?0:250)&&m.activePokemonView(A).attachedTools().isEmpty()&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Reforged Axe wrong count gate or discard");
        }
    }
    static void chinchou(){
        var a=attack("sv4-5_en_020",0);
        for(boolean bottom:List.of(false,true)){
            var m=game(a,0);var before=m.privateView(A).deck();var rng=m.randomTranscript();var top=before.getLast();m.attack(A,a.id());var q=m.effectCardPrompt(A);
            check(q.candidates().equals(List.of(top))&&m.clientView(B).effectSelection()==null,"Top inspection disclosed or offered wrong cards");
            CopyAttackTest.reject(()->m.submitEffectCards(B,q.resolutionId(),q.step(),List.of(top.instanceId())));
            m.submitEffectCards(A,q.resolutionId(),q.step(),bottom?List.of(top.instanceId()):List.of());var expected=new ArrayList<>(before);if(bottom)expected.addFirst(expected.removeLast());
            check(m.privateView(A).deck().equals(expected)&&rng.equals(m.randomTranscript())&&!m.hasPendingEffectChoice(),"Inspection shuffled, discarded or stalled");
        }
    }
    static void luxray(){for(String id:List.of("sv6_en_068","sv6_en_195"))luxray(id);}
    static void luxray(String id){
        var a=attack(id,0);
        for(boolean empty:List.of(false,true)){
            var m=game(a,0);if(empty)m.discardHandCardsByEffect(A,B,m.privateView(B).hand().stream().map(CardInstance::instanceId).toList());
            var before=m.privateView(B).hand();var deck=m.privateView(B).deck();m.attack(A,a.id());var r=m.effectCardReveal();if(r!=null)m.acknowledgeEffectReveal(r.confirmingPlayer(),r.id());
            if(!empty){var q=m.effectCardPrompt(A);check(new HashSet<>(q.candidates()).equals(new HashSet<>(before)),"Luxray restricted arbitrary hand selection");var chosen=q.candidates().getLast();
                CopyAttackTest.reject(()->m.submitEffectCards(B,q.resolutionId(),q.step(),List.of(chosen.instanceId())));m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(chosen.instanceId()));
                check(m.privateView(B).discard().contains(chosen)&&!m.privateView(B).hand().contains(chosen),"Luxray did not discard chosen physical card");}
            // Opponent draws once on the next turn, after the discard finishes.
            check(m.privateView(B).hand().size()==before.size()-(empty?0:1)+1&&m.privateView(B).deck().size()==deck.size()-1&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Luxray empty hand or continuation failed");
        }
    }
    static void retreat(){
        for(String id:List.of("sv3-5_en_088","sv3-5_en_089","sv6_en_016"))for(boolean switched:List.of(false,true)){
            var a=attack(id,0);var m=game(a,0);int before=m.activeRetreatCost(B);UUID old=m.activePokemonView(B).pokemonId(),bench=m.benchPokemonView(B).getFirst().pokemonId();
            m.attack(A,a.id());check(m.activeRetreatCost(B)==before+1,"Retreat cost not applied: "+id);
            if(switched){m.switchActiveByEffect(B,B,bench);m.switchActiveByEffect(B,B,old);check(m.activeRetreatCost(B)==before,"Retreat modifier survived Bench round trip");}
            m.endTurn(B);check(m.activeRetreatCost(B)==before,"Retreat modifier survived next owner turn");
        }
    }
    static void thrash(){
        var a=attack("sv3-5_en_056",0);Set<Integer> seen=new HashSet<>();
        for(int seed=0;seed<16;seed++){
            var m=game(a,seed);int rng=m.randomTranscript().size();m.attack(A,a.id());int damage=m.lastAttack().finalDamage();seen.add(damage);
            check((damage==20||damage==40)&&m.activePokemonView(A).damage()==(damage==20?20:0),"Thrash reused opposite or separate coin result");
            check(m.randomTranscript().size()==rng+1&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Thrash rolled twice or stalled");
        }check(seen.equals(Set.of(20,40)),"Both coin branches were not tested");
    }
    static void tail(){
        var a=attack("svbsp_en_016",1);
        for(boolean accept:List.of(false,true)){
            var m=ModernQuantityRepairTest.game(a,List.of(),EnergyType.LIGHTNING,"swsh5_en_124");UUID source=m.activePokemonView(A).pokemonId();ModernRuleRepair2Test.pay(m,A,source,"energy",3);
            m.attack(A,a.id());var choice=m.effectChoicePrompt(A);check(m.activePokemonView(B).damage()==0,"Tail damage dealt before payment");m.chooseEffectOption(A,choice.resolutionId(),choice.step(),accept?0:1);
            if(accept){var q=m.effectCardPrompt(A);CopyAttackTest.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),q.candidates().stream().limit(1).map(CardInstance::instanceId).toList()));m.submitEffectCards(A,q.resolutionId(),q.step(),q.candidates().stream().limit(2).map(CardInstance::instanceId).toList());}
            check(m.activePokemonView(B).damage()==(accept?240:140)&&m.activePokemonView(A).attachedEnergyIds().size()==(accept?1:3)&&!m.hasPendingEffectChoice(),"Tail cost, extra damage or continuation failed");
        }
    }
    static void dialga(){
        var a=attack("sv8_en_135",0);
        for(int size:List.of(1,6)){
            var m=game(a,0);m.drawCardsByEffect(A,A,m.privateView(A).deck().size()-size);var before=m.privateView(A).deck();m.attack(A,a.id());var q=m.effectCardPrompt(A);
            check(q.minimum()==Math.min(2,size)&&m.clientView(B).effectSelection()==null,"Dialga wrong short-deck minimum or leaked search");
            var selected=new ArrayList<>(q.candidates().subList(0,Math.min(size,2)));Collections.reverse(selected);
            CopyAttackTest.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of()));m.submitEffectCards(A,q.resolutionId(),q.step(),selected.stream().map(CardInstance::instanceId).toList());
            var deck=m.privateView(A).deck();for(int i=0;i<selected.size();i++)check(deck.get(deck.size()-1-i).equals(selected.get(i)),"Dialga lost chosen top-first order");
            check(new HashSet<>(deck).equals(new HashSet<>(before))&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Dialga lost cards or stalled");
        }
    }
}
