import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernSupporterEnergyRepairTest {
    static final UUID A=ModernRuleRepair2Test.A,B=ModernRuleRepair2Test.B;
    static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean b,String why){ModernRuleRepair2Test.check(b,why);}
    public static void main(String[] args){
        for(String id:List.of("sv4_en_170","sv4_en_239","sv4_en_256","sv8-5_en_120","sv6-5_en_059","sv6-5_en_088","sv8-5_en_112","sv8-5_en_173"))for(int count:List.of(0,1,2))for(boolean active:List.of(false,true))run(id,count,active);
        System.out.println("ModernSupporterEnergyRepairTest passed: eight Sada/Janine prints, zero/one/two attachments, exact family/type filters, distinct hosts, Active-only poison, actual-attachment draw and atomic invalid assignment");
    }
    static void run(String id,int count,boolean useActive){
        boolean sada=id.startsWith("sv4_")||id.equals("sv8-5_en_120");var m=game(id);UUID active=m.activePokemonView(A).pokemonId(),bench=m.benchPokemonView(A).getFirst().pokemonId();
        var extra=ModernRuleRepair2Test.hand(m,A,"basic");m.playBasicPokemonToBench(A,extra.instanceId());UUID second=m.benchPokemonView(A).getLast().pokemonId();
        m.playBasicPokemonToBench(A,ModernRuleRepair2Test.hand(m,A,"plain").instanceId());UUID wrong=m.benchPokemonView(A).getLast().pokemonId();
        if(sada){var discarded=m.privateView(A).hand().stream().filter(c->Set.of("energy","water","special").contains(c.definitionId())).map(CardInstance::instanceId).toList();m.discardHandCardsByEffect(A,A,discarded);}
        int hand=m.privateView(A).hand().size();m.playTrainer(A,ModernRuleRepair2Test.hand(m,A,"trainer").instanceId(),null);var q=m.effectCardPrompt(A);
        check(q.minimum()==0&&q.maximum()==2&&q.candidates().stream().noneMatch(c->c.definitionId().equals("special"))&&(sada||q.candidates().stream().allMatch(c->c.definitionId().equals("energy"))),"Wrong source, count or Energy filter: "+id);
        var selected=q.candidates().stream().limit(count).map(CardInstance::instanceId).toList();check(selected.size()==count,"Supporter fixture");m.submitEffectCards(A,q.resolutionId(),q.step(),selected);
        if(count>0){var d=m.effectAttachmentPrompt(A);UUID first=useActive?active:bench;
            CopyAttackTest.reject(()->m.assignEffectEnergyDistribution(A,d.resolutionId(),d.step(),selected.getFirst(),wrong));
            for(UUID energy:selected)m.assignEffectEnergyDistribution(A,d.resolutionId(),d.step(),energy,first);
            if(count==2){var before=m.privateView(A);CopyAttackTest.reject(()->m.confirmEffectEnergyDistribution(A,d.resolutionId(),d.step()));check(m.privateView(A).equals(before),"Duplicate-host rejection consumed cards");m.assignEffectEnergyDistribution(A,d.resolutionId(),d.step(),selected.getLast(),second);}
            m.confirmEffectEnergyDistribution(A,d.resolutionId(),d.step());CopyAttackTest.reject(()->m.confirmEffectEnergyDistribution(A,d.resolutionId(),d.step()));
        }
        check(m.privateView(A).hand().size()==hand-1+(sada&&count>0?3:0),"Sada drew without attaching, or failed to draw: "+id);
        check(m.activePokemonView(A).conditions().contains(SpecialCondition.POISONED)==(!sada&&count>0&&useActive),"Janine poisoned the wrong host/path: "+id);
        int attached=m.activePokemonView(A).attachedEnergyIds().size()+m.benchPokemonView(A).stream().mapToInt(p->p.attachedEnergyIds().size()).sum();check(attached==count&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(A),"Supporter distribution stalled or duplicated");
    }
    static ServerMatch game(String trainer){
        return game(trainer,"trainer",GeneratedRuleBatch8Test.hit(10,false));
    }
    static ServerMatch game(String trainer,String trainerKey,AttackDefinition attack){
        String[] ids={"basic","basic","basic","basic","basic","basic","plain","energy","energy","energy","water","special",trainerKey,trainerKey};
        var defs=new ArrayList<CardDefinition>();for(String id:Arrays.stream(ids).distinct().toList()){
            boolean pokemon=id.equals("basic")||id.equals("plain"),energy=Set.of("energy","water","special").contains(id);
            defs.add(new CardDefinition(id,id,pokemon?CardCategory.POKEMON:energy?CardCategory.ENERGY:CardCategory.TRAINER,id.equals(trainerKey)?TrainerCategory.SUPPORTER:TrainerCategory.NONE,"H",Set.of("international"),pokemon,energy&&!id.equals("special"),60,Map.of(),EffectSupport.SUPPORTED,null,id.equals("basic")?Set.of(PokemonFamily.ANCIENT):Set.of(),pokemon?Set.of(id.equals("basic")?EnergyType.DARKNESS:EnergyType.FIRE):Set.of()));
        }
        var profile=new PokemonProfile(500,0,null,null,0,null,0,1,List.of(attack),List.of(),EnergyType.DARKNESS);var plain=new PokemonProfile(500,0,null,null,0,null,0,1,List.of(),List.of(),EnergyType.FIRE);
        var combat=new CombatCatalog(Map.of("basic",profile,"plain",plain),Map.of("energy",EnergyType.DARKNESS,"water",EnergyType.WATER),Map.of(trainerKey,DATA.trainer(trainer).orElseThrow()),Map.of("special",EnergyProfile.fixed(EnergyType.DARKNESS,2)));
        for(int seed=0;seed<100;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID p:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(p.getLeastSignificantBits(),i+1),p,ids[i%ids.length]));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),combat,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->{var basics=m.privateView(p).hand().stream().filter(c->m.cardDefinition(c.definitionId()).basicPokemon()).limit(2).toList();return basics.size()<2||basics.stream().anyMatch(c->!c.definitionId().equals("basic"));}))continue;
            OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,22);m.endTurn(A);m.endTurn(B);
            if(Arrays.stream(ids).allMatch(id->m.privateView(A).hand().stream().anyMatch(c->c.definitionId().equals(id))))return m;
        }throw new AssertionError("No supporter fixture");
    }
}
