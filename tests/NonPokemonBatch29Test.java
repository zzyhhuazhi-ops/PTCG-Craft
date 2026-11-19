import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch29Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static CardInstance card(ServerMatch m,String id){return DynamicPassiveModifierTest.hand(m,A,id);}
    static boolean link(EffectDefinition e){return e.type().equals("skip_evolution_turn_end")||e.effects().stream().anyMatch(NonPokemonBatch29Test::link);}
    static ServerMatch game(TrainerProfile tool,String name,boolean legacy){
        String[] ids={"base","base","base","mega","trainer","stadium","evolver","energy"};var defs=new ArrayList<CardDefinition>();
        for(String id:Arrays.stream(ids).distinct().toList()){boolean pokemon=Set.of("base","mega").contains(id);defs.add(new CardDefinition(id,id,pokemon?CardCategory.POKEMON:id.equals("energy")?CardCategory.ENERGY:CardCategory.TRAINER,id.equals("trainer")?TrainerCategory.TOOL:id.equals("stadium")?TrainerCategory.STADIUM:id.equals("evolver")?TrainerCategory.ITEM:TrainerCategory.NONE,"H",Set.of("international"),id.equals("base"),id.equals("energy"),60,Map.of(),EffectSupport.SUPPORTED,null));}
        var handAbility=HandSelfEvolutionEffectTest.ability(HandSelfEvolutionEffectTest.EVOLVE+",{\"type\":\"draw\",\"target\":\"SELF\",\"amount\":2}");
        var deckAbility=ResumableAbilityTest.ability("deck-evolve",DeckEvolutionEffectTest.EVOLVE+",{\"type\":\"draw\",\"target\":\"SELF\",\"amount\":2}");
        var base=new PokemonProfile(500,1,null,null,0,null,0,2,List.of(GeneratedRuleBatch8Test.hit(10,false)),List.of(handAbility,deckAbility),EnergyType.LIGHTNING);
        var mega=new PokemonProfile(500,1,"base",null,0,null,0,2,List.of(GeneratedRuleBatch8Test.hit(10,false)),List.of(),EnergyType.LIGHTNING,name,null,PokemonPlacementRule.ANY_EFFECT,legacy?PokemonPrintedRules.EVOLUTION_ENDS_TURN:PokemonPrintedRules.STANDARD);
        var trainers=Map.of("trainer",tool,"stadium",DATA.trainer("sm6_en_111").orElseThrow(),"evolver",new TrainerProfile(TrainerCategory.ITEM,new TrainerEffect(TrainerEffect.Opcode.EVOLVE_OWN_POKEMON_FROM_HAND,0,0)));
        for(int seed=0;seed<200;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID owner:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,ids[i%ids.length]));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),new CombatCatalog(Map.of("base",base,"mega",mega),Map.of("energy",EnergyType.LIGHTNING),trainers),new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->c.definitionId().equals("base")).count()<2))continue;
            OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,25);m.drawCardsByEffect(A,B,25);m.endTurn(A);m.endTurn(B);
            if(List.of("trainer","stadium","evolver","mega").stream().allMatch(id->m.privateView(A).hand().stream().anyMatch(c->c.definitionId().equals(id)))&&m.privateView(A).deck().stream().anyMatch(c->c.definitionId().equals("mega")))return m;
        }throw new AssertionError("Mega fixture unavailable");
    }
    public static void main(String[] args){allLinks();restrictions();continuations();System.out.println("NonPokemonBatch29Test passed: every Spirit Link name, legacy Mega/Primal turn end, disabled/wrong-host/wrong-name Tools, modern exclusion and hand/deck/Trainer effect completion before turn end");}
    static void allLinks(){
        int count=0;for(var p:DATA.trainerProfiles().values())if(p.ongoingProgram().triggers().stream().flatMap(t->t.effects().stream()).anyMatch(NonPokemonBatch29Test::link)){
            String name=p.ongoingProgram().triggers().getFirst().effects().getFirst().conditions().getFirst().arguments().get("value");var m=game(p,name,true);UUID host=m.benchPokemonView(A).getFirst().pokemonId();m.playTrainer(A,card(m,"trainer").instanceId(),host);m.evolve(A,host,card(m,"mega").instanceId());check(m.currentPlayer().equals(A),"Matching Spirit Link ended turn: "+name);count++;
        }check(count>=35,"Spirit Link coverage incomplete");
        check(DATA.pokemon("xy4_en_024").orElseThrow().printedRules()==PokemonPrintedRules.EVOLUTION_ENDS_TURN,"Bundled legacy Mega lacks printed rule");
    }
    static void restrictions(){
        for(String mode:List.of("none","disabled","wrong-name","wrong-host","modern")){
            var m=game(DATA.trainer("xy4_en_100").orElseThrow(),mode.equals("wrong-name")?"M Gengar-EX":"M Manectric-EX",!mode.equals("modern"));var host=m.activePokemonView(A).pokemonId();
            if(!mode.equals("none"))m.playTrainer(A,card(m,"trainer").instanceId(),mode.equals("wrong-host")?m.benchPokemonView(A).getFirst().pokemonId():host);
            if(mode.equals("disabled"))m.playTrainer(A,card(m,"stadium").instanceId(),null);
            m.evolve(A,host,card(m,"mega").instanceId());check(m.currentPlayer().equals(mode.equals("modern")?A:B),"Mega turn-end restriction failed: "+mode);
        }
    }
    static void continuations(){
        for(String mode:List.of("hand","deck","trainer")){
            var m=game(DynamicPassiveModifierTest.plainTool(),"M Manectric-EX",true);var host=m.activePokemonView(A).pokemonId();int before=m.privateView(A).hand().size();
            if(mode.equals("trainer")){var item=card(m,"evolver");m.playTrainerWithEvolution(A,item.instanceId(),host,card(m,"mega").instanceId());check(m.privateView(A).discard().contains(item),"Legacy evolution Trainer not discarded before turn end");}
            else {m.useAbility(A,host,mode.equals("hand")?"ascend":"deck-evolve");var q=m.effectCardPrompt(A);var evolution=q.candidates().stream().filter(c->c.definitionId().equals("mega")).findFirst().orElseThrow();m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(evolution.instanceId()));NonPokemonBatch2Test.settle(m);check(m.privateView(A).hand().size()==before+(mode.equals("hand")?1:2),"Mega ended before ability's following draw");}
            check(m.currentPlayer().equals(B),"Mega effect did not end its owner's turn: "+mode);
        }
    }
}
