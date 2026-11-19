import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernDiscardSearchRepairTest {
    static final UUID A=ModernRuleRepair2Test.A,B=ModernRuleRepair2Test.B;
    static final CombatCatalog DATA=BundledCombatData.load();
    static final List<String> PRINTS=List.of("sv4-5_en_085","sv4-5_en_086","sv4-5_en_230","sv4-5_en_231");
    static void check(boolean b,String why){ModernRuleRepair2Test.check(b,why);}
    static CardInstance hand(ServerMatch m,UUID player,String id){return ModernRuleRepair2Test.hand(m,player,id);}
    static ServerMatch game(){
        var ids=new ArrayList<>(List.of("basic","basic","basic","basic","basic","evolved","ex","energy"));ids.addAll(PRINTS);
        var defs=new ArrayList<CardDefinition>();var profiles=new HashMap<String,PokemonProfile>();var trainers=new HashMap<String,TrainerProfile>();
        for(String id:new LinkedHashSet<>(ids)){
            boolean pokemon=Set.of("basic","evolved","ex").contains(id),supporter=PRINTS.contains(id);
            defs.add(new CardDefinition(id,id,pokemon?CardCategory.POKEMON:supporter?CardCategory.TRAINER:CardCategory.ENERGY,supporter?TrainerCategory.SUPPORTER:TrainerCategory.NONE,"H",Set.of("international"),pokemon&&!id.equals("evolved"),id.equals("energy"),60,Map.of(),EffectSupport.SUPPORTED,null,id.equals("ex")?Set.of(PokemonFamily.EX,PokemonFamily.RULE_BOX):Set.of()));
            if(pokemon)profiles.put(id,new PokemonProfile(500,0,id.equals("evolved")?"basic":null));if(supporter)trainers.put(id,DATA.trainer(id).orElseThrow());
        }
        var combat=new CombatCatalog(profiles,Map.of("energy",EnergyType.GRASS),trainers);
        for(int seed=0;seed<200;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID owner:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,ids.get(i%ids.size())));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),combat,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().noneMatch(c->c.definitionId().equals("basic"))))continue;
            for(UUID p:List.of(A,B)){m.chooseOpeningActive(p,hand(m,p,"basic").instanceId());m.startAfterOpening(p);}OpeningTestSupport.complete(m,A,B,0);
            m.drawCardsByEffect(A,A,22);m.drawCardsByEffect(A,B,18);m.endTurn(A);m.endTurn(B);
            if(List.of(A,B).stream().allMatch(p->ids.stream().allMatch(id->m.privateView(p).hand().stream().anyMatch(c->c.definitionId().equals(id))))&&m.privateView(A).deck().stream().anyMatch(c->c.definitionId().equals("ex")))return m;
        }throw new AssertionError("Student fixture");
    }
    public static void main(String[] args){
        for(String print:PRINTS)for(int count:List.of(0,1,3))for(boolean take:List.of(false,true)){
            var m=game();var source=hand(m,A,print);var matching=PRINTS.stream().filter(id->!id.equals(print)).limit(count).map(id->hand(m,A,id)).toList();var costs=new ArrayList<>(matching);costs.add(hand(m,A,"energy"));m.discardHandCardsByEffect(A,A,costs.stream().map(CardInstance::instanceId).toList());
            m.discardHandCardsByEffect(A,B,List.of(hand(m,B,print).instanceId()));var before=m.privateView(A);m.playTrainer(A,source.instanceId(),null);var q=m.effectCardPrompt(A);
            check(q.minimum()==0&&q.maximum()==1+count&&m.clientView(B).effectSelection()==null,"Student counted current/foreign/unrelated card, or leaked search");
            check(q.candidates().stream().allMatch(c->Set.of("basic","evolved").contains(c.definitionId())),"Student accepts a Rule Box or non-Pokemon");
            var forbidden=before.deck().stream().filter(c->c.definitionId().equals("ex")).findFirst().orElseThrow();CopyAttackTest.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(forbidden.instanceId())));
            if(q.candidates().size()>q.maximum())CopyAttackTest.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),q.candidates().stream().limit(q.maximum()+1).map(CardInstance::instanceId).toList()));
            var chosen=take?q.candidates().stream().limit(q.maximum()).toList():List.<CardInstance>of();check(!take||chosen.size()==count+1,"Student fixture lacks candidates");m.submitEffectCards(A,q.resolutionId(),q.step(),chosen.stream().map(CardInstance::instanceId).toList());
            if(take){var reveal=m.effectCardReveal();check(reveal!=null,"Student skipped public reveal");m.acknowledgeEffectReveal(reveal.confirmingPlayer(),reveal.id());}
            check(m.privateView(A).hand().containsAll(chosen)&&m.privateView(A).hand().size()==before.hand().size()-1+chosen.size()&&m.privateView(A).deck().size()==before.deck().size()-chosen.size()&&m.privateView(A).discard().contains(source)&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(A),"Student did not complete reveal/search/shuffle");
            CopyAttackTest.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of()));
        }
        trimmer();absol();
        System.out.println("ModernDiscardSearchRepairTest passed: four Student printings, own-discard-only count, source exclusion, non-Rule-Box filters, reveal, 25 Hand Trimmer combinations and 45 Absol optional discard/draw scenarios");
    }
    static void absol(){
        var a=DATA.pokemon("sv4_en_113").orElseThrow().attacks().getFirst();AttackProgramRules.validate(a);
        for(int held:List.of(0,2,4,5,8))for(int remaining:List.of(0,2,20))for(int mode:List.of(0,1,2)){
            var m=ModernFieldRuleRepairTest.game(a,List.of(),EnergyType.DARKNESS);ModernRuleRepair2Test.pay(m,A,m.activePokemonView(A).pokemonId(),"energy",1);
            m.drawCardsByEffect(A,A,m.privateView(A).deck().size()-remaining);m.discardHandCardsByEffect(A,A,m.privateView(A).hand().stream().skip(held).map(CardInstance::instanceId).toList());
            var before=m.privateView(A);m.attack(A,a.id());var choice=m.hasPendingEffectChoice()?m.effectChoicePrompt(A):null;
            if(choice!=null)m.chooseEffectOption(A,choice.resolutionId(),choice.step(),mode==0?1:0);
            int count=mode==0?0:mode==1?Math.max(0,held-4):held;
            var q=m.clientView(A).effectSelection()==null?null:m.effectCardPrompt(A);if(mode!=0&&held>0){check(q!=null&&q.minimum()==Math.max(0,held-4)&&q.maximum()==held&&m.clientView(B).effectSelection()==null,"Absol discard range or privacy failed");
                if(q.minimum()>0)CopyAttackTest.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),q.candidates().stream().limit(q.minimum()-1).map(CardInstance::instanceId).toList()));
                var selected=q.candidates().stream().limit(count).map(CardInstance::instanceId).toList();CopyAttackTest.reject(()->m.submitEffectCards(B,q.resolutionId(),q.step(),selected));m.submitEffectCards(A,q.resolutionId(),q.step(),selected);
                CopyAttackTest.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),selected));
            }else check(q==null,"Absol forced discard after declining/empty hand");
            int draw=Math.min(remaining,Math.max(0,5-held+count));check(m.privateView(A).hand().size()==held-count+draw&&m.privateView(A).deck().size()==remaining-draw&&m.privateView(A).discard().size()==before.discard().size()+count&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Absol discarded wrong count, drew too many or stalled");
        }
    }
    static void trimmer(){
        for(int ownSize:List.of(0,4,5,7,8))for(int opponentSize:List.of(0,4,5,7,8)){
            var m=GeneratedRuleBatch8Test.fixture(List.of(),EnergyType.GRASS,EnergyType.GRASS,DATA.trainer("sv5_en_150").orElseThrow(),GeneratedRuleBatch8Test.hit(10,false));var source=hand(m,A,"trainer");
            m.discardHandCardsByEffect(A,A,m.privateView(A).hand().stream().filter(c->!c.equals(source)).skip(ownSize).map(CardInstance::instanceId).toList());m.discardHandCardsByEffect(A,B,m.privateView(B).hand().stream().skip(opponentSize).map(CardInstance::instanceId).toList());
            var beforeA=m.privateView(A);var beforeB=m.privateView(B);
            if(ownSize<=5&&opponentSize<=5){check(!m.canPlayTrainer(A,source.instanceId(),null),"Trimmer offered with no excess cards");CopyAttackTest.reject(()->m.playTrainer(A,source.instanceId(),null));check(beforeA.equals(m.privateView(A))&&beforeB.equals(m.privateView(B)),"Rejected Trimmer mutated cards");continue;}
            m.playTrainer(A,source.instanceId(),null);
            for(UUID chooser:List.of(B,A)){
                int count=(chooser.equals(A)?ownSize:opponentSize)-5;if(count<=0)continue;
                var q=m.effectCardPrompt(chooser);UUID other=chooser.equals(A)?B:A;check(q.minimum()==count&&q.maximum()==count&&m.pendingChoicePlayer().equals(chooser)&&m.clientView(other).effectSelection()==null,"Trimmer wrong order/count or leaked private hand");
                CopyAttackTest.reject(()->m.submitEffectCards(chooser,q.resolutionId(),q.step(),List.of()));if(chooser.equals(A))CopyAttackTest.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(source.instanceId())));
                var selected=q.candidates().stream().limit(count).map(CardInstance::instanceId).toList();CopyAttackTest.reject(()->m.submitEffectCards(other,q.resolutionId(),q.step(),selected));m.submitEffectCards(chooser,q.resolutionId(),q.step(),selected);
            }
            check(m.privateView(A).hand().size()==Math.min(5,ownSize)&&m.privateView(B).hand().size()==Math.min(5,opponentSize)&&m.privateView(A).discard().size()==beforeA.discard().size()+Math.max(0,ownSize-5)+1&&m.privateView(B).discard().size()==beforeB.discard().size()+Math.max(0,opponentSize-5)&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(A),"Trimmer failed final counts or continuation");
        }
    }
}
