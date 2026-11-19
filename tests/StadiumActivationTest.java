import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Persistent Stadium activation with private Item-form search; no implicit play-from-hand side effects. */
public final class StadiumActivationTest {
    static final UUID A=FossilFormTest.A,B=FossilFormTest.B;
    static void check(boolean b,String text){FossilFormTest.check(b,text);}
    static void reject(Runnable action){FossilFormTest.reject(action);}
    static final String SEARCH="""
        {"type":"search_deck","target":"DECK","amount":2,"minimum":0,"card_category":"TRAINER",
         "trainer_category":"ITEM","trainer_name_contains":"Antique","destination":"ITEM_POKEMON_BENCH"}
        """;
    static JsonObject stadiumJson(){return JsonParser.parseString("""
        {"trainerCategory":"STADIUM","activation":{"usage":"ONCE_PER_PLAYER_TURN","effects":[%s]}}
        """.formatted(SEARCH)).getAsJsonObject();}
    static TrainerProfile stadium(){return BundledCombatData.trainer(stadiumJson(),EffectRegistry.standard());}
    static CardDefinition definition(String id,CardCategory category,TrainerCategory trainer,boolean basic){
        return new CardDefinition(id,id,category,trainer,"J",Set.of("international"),basic,category==CardCategory.ENERGY,60,Map.of(),EffectSupport.SUPPORTED,null);
    }
    static AbilityDefinition watcher(){return BundledCombatData.ability(JsonParser.parseString("""
        {"id":"stadium-watch","nameZh":"竞技场使用观察","kind":"TRIGGERED","effectSupport":"SUPPORTED",
         "eventPolicy":{"trigger":"ON_CARD_PLAYED","player":"SELF","card":"STADIUM","optional":false,"usage":"NONE"},
         "triggers":[{"trigger":"ON_CARD_PLAYED","effects":[{"type":"draw","target":"HAND","amount":1}]}]}
        """).getAsJsonObject(),EffectRegistry.standard());}
    static ServerMatch game(){
        var old=FossilFormTest.json();old.getAsJsonObject("inPlayPokemon").addProperty("pokemonName","Old Fossil");
        var base=new PokemonProfile(200,0,null,null,0,null,0,1,List.of(),List.of(watcher()),EnergyType.COLORLESS,"Antique Impostor",null);
        var combat=new CombatCatalog(Map.of("base",base),Map.of("energy",EnergyType.COLORLESS),
            Map.of("fossil",FossilFormTest.fossil(),"old_fossil",BundledCombatData.trainer(old,EffectRegistry.standard()),"quarry",stadium(),"other_quarry",stadium()));
        var defs=new CardDatabase(List.of(definition("base",CardCategory.POKEMON,TrainerCategory.NONE,true),
            definition("energy",CardCategory.ENERGY,TrainerCategory.NONE,false),definition("fossil",CardCategory.TRAINER,TrainerCategory.ITEM,false),
            definition("old_fossil",CardCategory.TRAINER,TrainerCategory.ITEM,false),definition("quarry",CardCategory.TRAINER,TrainerCategory.STADIUM,false),
            definition("other_quarry",CardCategory.TRAINER,TrainerCategory.STADIUM,false)));
        for(int seed=0;seed<300;seed++){
            var decks=new ArrayList<List<CardInstance>>();
            for(UUID owner:List.of(A,B)){
                var deck=new ArrayList<CardInstance>();
                for(int i=0;i<60;i++)deck.add(new CardInstance(new UUID(owner.equals(A)?7501:7502,i+1),owner,
                    i<28?"base":i<32?"quarry":i<36?"other_quarry":i<44?"fossil":i<52?"old_fossil":"energy"));
                decks.add(deck);
            }
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),BundledRules.loadRulesets().get("standard-international-2026"),defs,combat,new ServerRandom(seed));
            OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().noneMatch(c->c.definitionId().equals("base"))))continue;
            OpeningTestSupport.complete(m,A,B,0);m.drawCardsByEffect(A,A,20);m.drawCardsByEffect(A,B,20);
            if(List.of(A,B).stream().anyMatch(p->List.of("quarry","other_quarry").stream().anyMatch(id->m.privateView(p).hand().stream().noneMatch(c->c.definitionId().equals(id)))
                ||m.privateView(p).deck().stream().filter(c->c.definitionId().equals("fossil")).count()<3))continue;
            return m;
        }
        throw new AssertionError("No Stadium activation fixture");
    }
    static CardInstance hand(ServerMatch m,UUID player,String id){return m.privateView(player).hand().stream().filter(c->c.definitionId().equals(id)).findFirst().orElseThrow();}
    static void play(ServerMatch m,UUID player,String id){
        m.playTrainer(player,hand(m,player,id).instanceId(),null);
        for(int guard=0;m.triggerOrderPrompt()!=null&&guard<12;guard++){
            check(!m.canActivateStadium(player),"Activation bypassed pending card-play triggers");
            var prompt=m.triggerOrderPrompt();check(!prompt.optionalDecision(),"Unexpected optional fixture trigger");
            m.chooseTriggeredEffect(prompt.choosingPlayer(),prompt.id(),prompt.entries().getFirst().id());
        }
        check(m.triggerOrderPrompt()==null,"Card-play triggers did not finish");
    }
    static void activate(ServerMatch m,UUID player){m.activateStadium(player,m.clientView(player).stadium().instanceId(),m.stadiumEntrySequence());}
    static void finish(ServerMatch m,UUID player,int count){var p=m.effectCardPrompt(player);m.submitEffectCards(player,p.resolutionId(),p.step(),p.candidates().stream().limit(count).map(CardInstance::instanceId).toList());}
    public static void main(String[] args){schema();activationAndPlayers();capacityAndZero();noMatchingCards();concession();
        System.out.println("StadiumActivationTest passed: optional per-player own-turn activation, source entry, persistent card conservation, no repeated card-play triggers, private exact Item-name selection, zero/one/two choices, capacity, one shuffle and concession.");}
    static void schema(){
        check(stadium().activation()!=null&&stadium().ongoingProgram().isEmpty(),"Activation became a passive modifier");
        for(String usage:List.of("ONCE_PER_TURN","UNLIMITED","")){
            var bad=stadiumJson();bad.getAsJsonObject("activation").addProperty("usage",usage);reject(()->BundledCombatData.trainer(bad,EffectRegistry.standard()));
        }
        for(String category:List.of("ITEM","TOOL","SUPPORTER")){
            var bad=stadiumJson();bad.addProperty("trainerCategory",category);reject(()->BundledCombatData.trainer(bad,EffectRegistry.standard()));
        }
        for(String mutation:List.of(SEARCH.replace("Antique","Fossil"),SEARCH.replace("\"minimum\":0","\"minimum\":1"),
                SEARCH.replace("\"amount\":2","\"amount\":3"),SEARCH.replace("\"ITEM\"","\"TOOL\""),SEARCH.replace("\"DECK\"","\"DISCARD\""))){
            var bad=stadiumJson();bad.getAsJsonObject("activation").add("effects",JsonParser.parseString("["+mutation+"]"));
            reject(()->BundledCombatData.trainer(bad,EffectRegistry.standard()));
        }
    }
    static void activationAndPlayers(){
        var m=game();play(m,A,"quarry");var source=m.clientView(A).stadium();long entry=m.stadiumEntrySequence();
        int hand=m.privateView(A).hand().size(),random=m.randomTranscript().size();
        var before=m.clientView(A);reject(()->m.activateStadium(B,source.instanceId(),entry));reject(()->m.activateStadium(A,source.instanceId(),entry-1));
        check(before.equals(m.clientView(A))&&m.randomTranscript().size()==random&&m.canActivateStadium(A),"Rejected activation consumed state/usage");
        check(MatchSession.multiplayer(m,A,B).view(A,A).options().stream().anyMatch(o->o.labelZh().startsWith("使用竞技场：")),"No activation action");
        activate(m,A);var prompt=m.effectCardPrompt(A);
        check(prompt.minimum()==0&&prompt.maximum()==2&&prompt.candidates().stream().allMatch(c->c.definitionId().equals("fossil")),"Item canonical-name filter admitted a Pokemon or Old Fossil");
        check(m.clientView(B).effectSelection()==null,"Private deck candidates leaked to opponent");
        reject(()->m.effectCardPrompt(B));reject(()->m.endTurn(A));
        var chosen=prompt.candidates().stream().limit(2).map(CardInstance::instanceId).toList();
        var pending=m.clientView(A);reject(()->m.submitEffectCards(B,prompt.resolutionId(),prompt.step(),chosen));
        reject(()->m.submitEffectCards(A,prompt.resolutionId(),prompt.step(),List.of(chosen.getFirst(),chosen.getFirst())));
        check(pending.equals(m.clientView(A)),"Rejected selection changed state");
        m.submitEffectCards(A,prompt.resolutionId(),prompt.step(),chosen);
        check(m.clientView(A).stadium().equals(source)&&m.privateView(A).discard().stream().noneMatch(c->c.instanceId().equals(source.instanceId())),"Activation consumed or duplicated Stadium");
        check(m.privateView(A).hand().size()==hand,"Activation emitted another play-from-hand trigger");
        check(m.benchPokemonView(A).size()==2&&m.benchPokemonView(A).stream().allMatch(p->p.topCardId().equals("fossil")&&p.hitPoints()==60),"Selected Items did not become field Pokemon");
        var draws=m.randomTranscript().subList(random,m.randomTranscript().size());
        check(draws.size()==m.privateView(A).deck().size()-1&&draws.stream().allMatch(e->e.operation().startsWith("effect-deck-item-pokemon-to-bench:")),"Not exactly one deck shuffle");
        reject(()->m.submitEffectCards(A,prompt.resolutionId(),prompt.step(),chosen));check(!m.canActivateStadium(A),"Same player activated twice");
        m.endTurn(A);check(m.canActivateStadium(B),"Stadium owner incorrectly restricted opponent activation");activate(m,B);finish(m,B,0);
        check(!m.canActivateStadium(B),"Zero selection did not consume activation");
        play(m,B,"other_quarry");check(m.canActivateStadium(B),"Replacement Stadium inherited old source usage");activate(m,B);finish(m,B,1);
        m.endTurn(B);check(m.canActivateStadium(A),"Next own turn did not refresh usage");
    }
    static void capacityAndZero(){
        for(int filled:List.of(4,5)){
            var m=game();for(int i=0;i<filled;i++)m.playBasicPokemonToBench(A,hand(m,A,"base").instanceId());play(m,A,"quarry");
            if(filled==5){check(!m.canActivateStadium(A),"Full Bench offered activation");reject(()->activate(m,A));continue;}
            activate(m,A);check(m.effectCardPrompt(A).maximum()==1,"Free slot did not cap selection");finish(m,A,1);
            check(m.benchPokemonView(A).size()==5,"One free Bench slot was not filled exactly");
        }
        var m=game();play(m,A,"quarry");int random=m.randomTranscript().size();activate(m,A);finish(m,A,0);
        check(m.benchPokemonView(A).isEmpty()&&m.randomTranscript().size()-random==m.privateView(A).deck().size()-1,"Optional zero did not shuffle exactly once");
    }
    static void concession(){
        var m=game();play(m,A,"quarry");var source=m.clientView(A).stadium();activate(m,A);var prompt=m.effectCardPrompt(A);m.concede(A);
        check(m.phase()==MatchPhase.FINISHED&&m.clientView(A).stadium().equals(source)&&m.privateView(A).discard().stream().noneMatch(c->c.instanceId().equals(source.instanceId())),"Concession duplicated pending Stadium into discard");
        reject(()->m.submitEffectCards(A,prompt.resolutionId(),prompt.step(),List.of()));
    }
    static void noMatchingCards(){
        var m=game();play(m,A,"quarry");
        var fossils=m.privateView(A).deck().stream().filter(c->c.definitionId().equals("fossil")).map(CardInstance::instanceId).toList();
        m.searchOwnDeckToHandByEffect(A,fossils);int random=m.randomTranscript().size();
        check(m.canActivateStadium(A),"Hidden nonempty deck must permit fail-to-find");activate(m,A);
        if(m.hasPendingCardSelection())finish(m,A,0);
        check(!m.canActivateStadium(A)&&m.randomTranscript().size()-random==m.privateView(A).deck().size()-1,"No matching Items did not finish with one shuffle");
        var empty=game();play(empty,A,"quarry");empty.discardTopDeckCardsByEffect(A,A,empty.privateView(A).deck().size());
        check(!empty.canActivateStadium(A),"Empty deck offered an operation with no effect");reject(()->activate(empty,A));
    }
}
