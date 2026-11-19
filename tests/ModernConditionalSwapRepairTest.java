import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernConditionalSwapRepairTest {
    static final UUID A=ModernRuleRepair2Test.A,B=ModernRuleRepair2Test.B;
    static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean b,String why){ModernRuleRepair2Test.check(b,why);}
    static CardInstance hand(ServerMatch m,String id){return ModernRuleRepair2Test.hand(m,A,id);}
    public static void main(String[] args){malamar();mask();System.out.println("ModernConditionalSwapRepairTest passed: completed Supporter hand play, turn reset, chosen new Active damage, missing Bench, Ogerpon ex identity filters and complete state inheritance across three Mask prints");}
    static void malamar(){
        var attack=DATA.pokemon("sv6-5_en_034").orElseThrow().attacks().getFirst();AttackProgramRules.validate(attack);
        for(String supporter:List.of("sv6-5_en_064","sv6-5_en_089"))for(int played:List.of(0,1,2))for(boolean bench:List.of(false,true)){
            var m=ModernSupporterEnergyRepairTest.game(supporter,supporter,attack);
            if(played>0){m.drawCardsByEffect(A,B,6);m.playTrainer(A,hand(m,supporter).instanceId(),null);var q=m.effectCardPrompt(B);
                check(m.clientView(A).effectSelection()==null,"Xerosic exposed private discard candidates");m.submitEffectCards(B,q.resolutionId(),q.step(),q.candidates().stream().limit(q.maximum()).map(CardInstance::instanceId).toList());
                check(m.privateView(B).hand().size()==3&&!m.hasPendingEffectChoice(),"Xerosic incomplete before attack");
                if(played==2){m.endTurn(A);m.endTurn(B);}
            }
            UUID old=m.activePokemonView(B).pokemonId(),next=m.benchPokemonView(B).getFirst().pokemonId();if(!bench)m.movePokemonByEffect(A,B,next,LeavePlayDestination.HAND);
            ModernRuleRepair2Test.pay(m,A,m.activePokemonView(A).pokemonId(),"energy",1);m.attack(A,attack.id());boolean succeeds=played==1&&bench;
            if(succeeds){var q=m.effectPokemonPrompt(A);check(m.activePokemonView(B).damage()==0,"Colluding Tentacles damaged before switch");CopyAttackTest.reject(()->m.chooseEffectPokemon(B,q.resolutionId(),q.step(),next));m.chooseEffectPokemon(A,q.resolutionId(),q.step(),next);}
            check(!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B)&&m.activePokemonView(B).pokemonId().equals(succeeds?next:old)&&m.activePokemonView(B).damage()==(succeeds?120:0),"Colluding Tentacles ignored hand-play history, turn reset or failed switch");
            if(succeeds)check(m.benchPokemonView(B).getFirst().damage()==0,"Colluding Tentacles damaged old Active");
        }
    }
    static ServerMatch maskGame(String mask){
        String[] ids={"sv6_en_025","sv6_en_025","sv6_en_025","sv6_en_025","sv6_en_040","sv6_en_024","sv1_en_001","energy","energy","tool","trainer"};
        var db=BundledRules.loadCardDefinitions();var defs=new ArrayList<CardDefinition>();var profiles=new HashMap<String,PokemonProfile>();
        for(String id:Arrays.stream(ids).distinct().toList()){
            if(id.startsWith("sv")){defs.add(db.all().stream().filter(d->d.id().equals(id)).findFirst().orElseThrow());profiles.put(id,DATA.pokemon(id).orElseThrow());}
            else defs.add(new CardDefinition(id,id,id.equals("energy")?CardCategory.ENERGY:CardCategory.TRAINER,id.equals("energy")?TrainerCategory.NONE:id.equals("tool")?TrainerCategory.TOOL:TrainerCategory.ITEM,"H",Set.of("international"),false,id.equals("energy"),60,Map.of(),EffectSupport.SUPPORTED,null));
        }
        var combat=new CombatCatalog(profiles,Map.of("energy",EnergyType.GRASS),Map.of("trainer",DATA.trainer(mask).orElseThrow(),"tool",DynamicPassiveModifierTest.plainTool()));
        for(int seed=0;seed<200;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID owner:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,ids[i%ids.length]));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),combat,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().noneMatch(c->c.definitionId().equals("sv6_en_025"))))continue;
            for(UUID p:List.of(A,B)){m.chooseOpeningActive(p,m.privateView(p).hand().stream().filter(c->c.definitionId().equals("sv6_en_025")).findFirst().orElseThrow().instanceId());m.startAfterOpening(p);}OpeningTestSupport.complete(m,A,B,0);
            m.drawCardsByEffect(A,A,24);m.endTurn(A);m.endTurn(B);
            if(Arrays.stream(ids).allMatch(id->m.privateView(A).hand().stream().anyMatch(c->c.definitionId().equals(id))))return m;
        }throw new AssertionError("Mask fixture");
    }
    static void mask(){
        for(String id:List.of("sv6_en_159","sv8-5_en_118","svalt_en_147"))for(boolean active:List.of(false,true)){
            var m=maskGame(id);m.playBasicPokemonToBench(A,hand(m,"sv6_en_025").instanceId());UUID host=active?m.activePokemonView(A).pokemonId():m.benchPokemonView(A).getFirst().pokemonId();
            m.playBasicPokemonToBench(A,hand(m,"sv6_en_024").instanceId());UUID nonEx=m.benchPokemonView(A).getLast().pokemonId();m.playBasicPokemonToBench(A,hand(m,"sv1_en_001").instanceId());UUID other=m.benchPokemonView(A).getLast().pokemonId();
            var replacement=hand(m,"sv6_en_040");var illegal=List.of(hand(m,"sv6_en_024"),hand(m,"sv1_en_001"));var discarded=new ArrayList<>(illegal);discarded.add(replacement);m.discardHandCardsByEffect(A,A,discarded.stream().map(CardInstance::instanceId).toList());
            ModernRuleRepair2Test.pay(m,A,host,"energy",1);m.playTrainer(A,hand(m,"tool").instanceId(),host);var field=EventTargetBindingTest.field(m);field.placeDamageCounters(A,host,4);if(active)m.applySpecialConditionByEffect(A,A,SpecialCondition.CONFUSED);
            var before=field.printedPokemonView(A,host);var old=field.topCard(A,host);long entry=field.pokemonEntrySequence(A,host);
            m.playTrainer(A,hand(m,"trainer").instanceId(),null);var q=m.effectPokemonPrompt(A);
            CopyAttackTest.reject(()->m.chooseEffectPokemon(A,q.resolutionId(),q.step(),nonEx));CopyAttackTest.reject(()->m.chooseEffectPokemon(A,q.resolutionId(),q.step(),other));m.chooseEffectPokemon(A,q.resolutionId(),q.step(),host);
            var selection=m.effectCardPrompt(A);check(selection.candidates().equals(List.of(replacement)),"Mask accepted non-ex or unrelated replacement");for(var c:illegal)CopyAttackTest.reject(()->m.submitEffectCards(A,selection.resolutionId(),selection.step(),List.of(c.instanceId())));
            m.submitEffectCards(A,selection.resolutionId(),selection.step(),List.of(replacement.instanceId()));CopyAttackTest.reject(()->m.submitEffectCards(A,selection.resolutionId(),selection.step(),List.of(replacement.instanceId())));
            var after=field.printedPokemonView(A,host);check(after.topCardId().equals(replacement.definitionId())&&after.damage()==before.damage()&&after.conditions().equals(before.conditions())&&after.attachedEnergyIds().equals(before.attachedEnergyIds())&&after.attachedTools().equals(before.attachedTools())&&field.pokemonEntrySequence(A,host)==entry,"Mask did not preserve in-play state");
            check(m.privateView(A).discard().contains(old)&&!m.privateView(A).discard().contains(replacement)&&m.currentPlayer().equals(A)&&!m.hasPendingEffectChoice(),"Mask duplicated cards or left a waiting step");
        }
    }
}
