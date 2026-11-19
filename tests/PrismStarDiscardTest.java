import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Printed Prism Star replacement applies to physical cards, including costs and original ownership. */
public final class PrismStarDiscardTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final Set<String> PRISM=Set.of("prism","supporter","stadium","special");
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static CardInstance card(ServerMatch m,UUID owner,String id){return DynamicPassiveModifierTest.hand(m,owner,id);}
    static ServerMatch game(){
        var attack=BundledCombatData.attack(JsonParser.parseString("""
            {"id":"mill","nameZh":"弃置计数","energyCost":{},"baseDamage":0,"damageType":"GRASS","effectSupport":"SUPPORTED",
             "triggers":[{"trigger":"BEFORE_ATTACK","effects":[{"type":"discard_top_deck","target":"SELF","amount":1,"bind":"milled"}]},
             {"trigger":"ON_ATTACK","effects":[{"type":"damage_per_bound","target":"OPPONENT_ACTIVE","bind":"milled","amount":10}]}]}
            """).getAsJsonObject(),EffectRegistry.standard());
        String[] ids={"base","base","base","prism","supporter","stadium","special","energy","tool","item"};var defs=new ArrayList<CardDefinition>();
        for(String id:Arrays.stream(ids).distinct().toList()){
            boolean pokemon=Set.of("base","prism").contains(id),energy=Set.of("energy","special").contains(id);
            var category=id.equals("supporter")?TrainerCategory.SUPPORTER:id.equals("stadium")?TrainerCategory.STADIUM:id.equals("tool")?TrainerCategory.TOOL:id.equals("item")?TrainerCategory.ITEM:TrainerCategory.NONE;
            defs.add(new CardDefinition(id,id,pokemon?CardCategory.POKEMON:energy?CardCategory.ENERGY:CardCategory.TRAINER,category,"H",Set.of("international"),pokemon,id.equals("energy"),60,
                PRISM.contains(id)&&!pokemon?Map.of("棱镜之星",1):Map.of(),EffectSupport.SUPPORTED,null,id.equals("prism")?Set.of(PokemonFamily.PRISM_STAR,PokemonFamily.RULE_BOX):Set.of(),pokemon?Set.of(EnergyType.GRASS):Set.of()));
        }
        var p=new PokemonProfile(500,1,null,null,0,null,0,1,List.of(attack));
        var combat=new CombatCatalog(Map.of("base",p,"prism",p),Map.of("energy",EnergyType.GRASS),Map.of(
            "supporter",DeckDiscardTriggeredAbilityTest.trainer("SUPPORTER","{\"type\":\"draw\",\"amount\":1}"),
            "stadium",DynamicPassiveModifierTest.plainStadium(),"tool",DynamicPassiveModifierTest.plainTool(),
            "item",ComposedTrainerIntegrationTest.item("{\"type\":\"discard\",\"target\":\"HAND\",\"amount\":1,\"minimum\":1,\"as_cost\":true},{\"type\":\"draw\",\"amount\":1}")),Map.of("special",EnergyProfile.fixed(EnergyType.GRASS,1)));
        for(int seed=0;seed<500;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID owner:List.of(A,B)){var cs=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cs.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,ids[i%ids.length]));decks.add(cs);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),combat,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(o->m.privateView(o).hand().stream().filter(c->c.definitionId().equals("base")).count()<2))continue;
            for(UUID owner:List.of(A,B)){m.chooseOpeningActive(owner,card(m,owner,"base").instanceId());m.placeOpeningBench(owner,card(m,owner,"base").instanceId());m.startAfterOpening(owner);}OpeningTestSupport.complete(m,A,B,0);m.drawCardsByEffect(A,A,24);m.drawCardsByEffect(A,B,24);m.endTurn(A);m.endTurn(B);
            if(PRISM.contains(m.privateView(A).deck().getLast().definitionId())&&List.of(A,B).stream().allMatch(o->Arrays.stream(ids).allMatch(id->m.privateView(o).hand().stream().anyMatch(c->c.definitionId().equals(id)))))return m;
        }throw new AssertionError("Prism fixture unavailable");
    }
    static void lost(ServerMatch m,CardInstance c){check(m.privateView(c.ownerId()).lostZone().contains(c)&&!m.privateView(c.ownerId()).discard().contains(c),"Prism card reached discard: "+c.definitionId());}
    static void assertZones(ServerMatch m){for(UUID owner:List.of(A,B))check(m.privateView(owner).discard().stream().noneMatch(c->PRISM.contains(c.definitionId())),"Prism remained in discard");}
    static void handAndCost(){var m=game();var ids=m.privateView(A).hand().stream().filter(c->PRISM.contains(c.definitionId())).toList();m.discardHandCardsByEffect(A,A,ids.stream().map(CardInstance::instanceId).toList());ids.forEach(c->lost(m,c));var n=game();var cost=card(n,A,"supporter");n.playTrainer(A,card(n,A,"item").instanceId(),null);var q=n.effectCardPrompt(A);n.submitEffectCards(A,q.resolutionId(),q.step(),List.of(cost.instanceId()));NonPokemonBatch2Test.settle(n);lost(n,cost);assertZones(n);}
    static void playAndStadium(){var m=game();var supporter=card(m,A,"supporter");m.playTrainer(A,supporter.instanceId(),null);NonPokemonBatch2Test.settle(m);lost(m,supporter);var stadium=card(m,A,"stadium");m.playTrainer(A,stadium.instanceId(),null);m.discardStadiumByEffect(A);lost(m,stadium);}
    static void countedMill(){var m=game();var top=m.privateView(A).deck().getLast();m.attack(A,"mill");NonPokemonBatch2Test.settle(m);lost(m,top);check(m.activePokemonView(B).damage()==10,"Prism discard replacement lost the actual discarded-card count");}
    static void retreat(){var m=game();var energy=card(m,A,"special");m.attachEnergy(A,energy.instanceId(),m.activePokemonView(A).pokemonId());m.retreat(A,m.benchPokemonView(A).getFirst().pokemonId(),List.of(energy.instanceId()));lost(m,energy);}
    static void knockoutAndReturn(){var m=game();var prism=card(m,A,"prism");m.playBasicPokemonToBench(A,prism.instanceId());var host=m.benchPokemonView(A).getLast().pokemonId();var energy=card(m,B,"special");var basic=card(m,A,"energy");m.attachEnergyByEffect(A,B,EffectSourceZone.HAND,energy.instanceId(),A,host);m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,basic.instanceId(),A,host);m.knockOutPokemonByEffect(A,A,host);NonPokemonBatch2Test.settle(m);lost(m,prism);lost(m,energy);check(m.privateView(A).discard().contains(basic),"Prism host redirected ordinary attachments");assertZones(m);
        var n=game();var returned=card(n,A,"prism");n.playBasicPokemonToBench(A,returned.instanceId());n.movePokemonByEffect(A,A,n.benchPokemonView(A).getLast().pokemonId(),LeavePlayDestination.HAND);check(n.privateView(A).hand().contains(returned)&&!n.privateView(A).lostZone().contains(returned),"Returning Prism to hand incorrectly banished it");}
    public static void main(String[] args){for(var c:BundledRules.loadCardDefinitions().all())if(c.pokemonFamilies().contains(PokemonFamily.PRISM_STAR))check(c.pokemonFamilies().contains(PokemonFamily.RULE_BOX),"Prism Pokemon missing Rule Box metadata");handAndCost();playAndStadium();countedMill();retreat();knockoutAndReturn();System.out.println("PrismStarDiscardTest passed: hand/cost, used Trainer, Stadium, counted deck mill, retreat and mixed-owner Knock Out, while non-discard returns remain legal");}
}
