import com.google.gson.*;
import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.nio.file.*;
import java.util.*;

/** Real printed programs; synthetic boards isolate private windows and turn-ending entry timing. */
public final class PokemonWindowEntryTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final Map<String,JsonObject> RAW=load();
    static Map<String,JsonObject> load(){try{var result=new HashMap<String,JsonObject>();for(var line:Files.readAllLines(Path.of("outputs/pokemon-window-entry-programs.jsonl"))){var row=JsonParser.parseString(line).getAsJsonObject();result.put(row.get("id").getAsString(),row.getAsJsonObject("conversion").getAsJsonObject("combatEntry"));}return result;}catch(Exception e){throw new RuntimeException(e);}}
    static AbilityDefinition gate(){return BundledCombatData.pokemon(RAW.get("swsh12-5_en_105"),EffectRegistry.standard()).abilities().getFirst();}
    static AttackDefinition horoscope(){return BundledCombatData.pokemon(RAW.get("swsh12-5_en_065"),EffectRegistry.standard()).attacks().getFirst();}
    static UUID other(UUID p){return p.equals(A)?B:A;}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static void check(boolean b,String why){GeneratedRuleBatch8Test.check(b,why);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static CardInstance hand(ServerMatch m,UUID p,String id){return DynamicPassiveModifierTest.hand(m,p,id);}
    static List<CardInstance> zone(ServerMatch m,UUID p,String name){return PairedTrainerPlayTest.zone(m,p,name);}
    static void choose(ServerMatch m,UUID p,List<CardInstance> cards){var q=m.effectCardPrompt(p);m.submitEffectCards(p,q.resolutionId(),q.step(),cards.stream().map(CardInstance::instanceId).toList());}
    static ServerMatch windowGame(UUID p,boolean special){var c=new GeneratedRuleBatch28Test.Config();c.attack=horoscope();c.defenderAttack=c.attack;c.energyType=EnergyType.PSYCHIC;if(special)c.specialEnergy=AiCardOperationTest.rainbow();var m=GeneratedRuleBatch28Test.fixture(c);if(p.equals(B))m.endTurn(A);m.attachEnergyByEffect(p,p,EffectSourceZone.HAND,hand(m,p,"energy").instanceId(),p,active(m,p));return m;}
    // Return a top-first window, preserving each physical card and all unseen deck cards.
    static List<CardInstance> window(ServerMatch m,UUID p,List<String> ids,boolean shortDeck){
        var result=new ArrayList<CardInstance>();for(var id:ids){var card=hand(m,p,id);zone(m,p,"hand").remove(card);result.add(card);}
        if(shortDeck){zone(m,p,"discard").addAll(zone(m,p,"deck"));zone(m,p,"deck").clear();}
        for(int i=result.size()-1;i>=0;i--)zone(m,p,"deck").add(result.get(i));return result;
    }
    static void privateTo(ServerMatch m,UUID p,List<CardInstance> cards){var session=MatchSession.multiplayer(m,A,B);String json=new Gson().toJson(session.view(other(p),other(p)));for(var card:cards)check(!json.contains(card.instanceId().toString()),"Private deck identity leaked");reject(()->m.effectCardPrompt(other(p)));}
    public static void main(String[] args){windows();gatePlacement();gateUnavailable();entryTiming();schema();ai();System.out.println("PokemonWindowEntryTest passed: Horoscope basic/special Energy, exact 0..3 windows and remainder order, no shuffle/hidden leak, stale/foreign rejection; Climactic Gate VMAX placement, decline/fail-to-find, exact HAND own-turn entry, full/empty gates, Gapejaw Bog both orders, complete four-tier matches");}
    static void windows(){for(UUID p:List.of(A,B))for(boolean special:List.of(false,true))for(int count=0;count<=3;count++){
        var m=windowGame(p,special);var looked=window(m,p,List.of("energy","energy","energy"),false);var deck=List.copyOf(zone(m,p,"deck"));var unseen=deck.subList(0,deck.size()-3);int rng=m.randomTranscript().size(),attached=m.activePokemonView(p).attachedEnergyIds().size();
        m.attack(p,"horoscope");var q=m.effectCardPrompt(p);check(q.minimum()==0&&q.maximum()==3&&new HashSet<>(q.candidates()).equals(new HashSet<>(looked)),"Horoscope window/energy range wrong");privateTo(m,p,looked);
        reject(()->m.submitEffectCards(other(p),q.resolutionId(),q.step(),List.of()));reject(()->m.submitEffectCards(p,UUID.randomUUID(),q.step(),List.of()));reject(()->m.submitEffectCards(p,q.resolutionId(),q.step(),List.of(looked.getFirst().instanceId(),looked.getFirst().instanceId())));reject(()->m.submitEffectCards(p,q.resolutionId(),q.step(),List.of(unseen.getFirst().instanceId())));
        var chosen=looked.subList(0,count);choose(m,p,chosen);var rest=new ArrayList<>(looked.subList(count,3));Collections.reverse(rest);
        if(rest.size()>1){var order=m.effectCardPrompt(p);check(order.step()!=q.step()&&order.minimum()==rest.size()&&new HashSet<>(order.candidates()).equals(new HashSet<>(rest)),"Remainder peeked at new cards or kept old token");privateTo(m,p,rest);reject(()->m.submitEffectCards(p,q.resolutionId(),q.step(),List.of()));reject(()->m.submitEffectCards(p,order.resolutionId(),order.step(),List.of()));choose(m,p,rest);}
        var expected=new ArrayList<>(unseen);for(int i=rest.size()-1;i>=0;i--)expected.add(rest.get(i));
        check(zone(m,p,"deck").equals(expected)&&m.randomTranscript().size()==rng,"Horoscope shuffled or changed uninspected deck order");check(m.activePokemonView(p).attachedEnergyIds().size()==attached+count&&m.currentPlayer().equals(other(p)),"Attachment count/attack end wrong");
    }
        for(int size=0;size<=2;size++){var m=windowGame(A,false);var ids=new ArrayList<String>();for(int i=0;i<size;i++)ids.add("trainer");var looked=window(m,A,ids,true);int rng=m.randomTranscript().size();m.attack(A,"horoscope");if(m.hasPendingCardSelection()){var q=m.effectCardPrompt(A);if(q.minimum()==0)choose(m,A,List.of());}if(m.hasPendingCardSelection())choose(m,A,looked);check(zone(m,A,"deck").equals(looked.reversed())&&m.randomTranscript().size()==rng&&!m.hasPendingEffectChoice(),"Short/nonenergy window failed size="+size);}
    }
    static ServerMatch gateGame(UUID p){return gateGame(p,false);}
    static ServerMatch gateGame(UUID p,boolean forcedEntry){var c=new GeneratedRuleBatch28Test.Config();c.source=gate();c.attack=GeneratedRuleBatch8Test.hit(300,false);c.defenderAttack=forcedEntry?DeckDiscardTriggeredAbilityTest.attack(0,"{\"type\":\"reveal_hand\",\"target\":\"OPPONENT_ACTIVE\"},{\"type\":\"opponent_hand_basic_to_bench\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":1,\"minimum\":1}"):c.attack;c.ownEvolutionFamilies=Set.of(PokemonFamily.V,PokemonFamily.VMAX);c.defenderEvolutionFamilies=c.ownEvolutionFamilies;var m=GeneratedRuleBatch28Test.fixture(c);if(p.equals(B))m.endTurn(A);if(p.equals(B))NonPokemonBatch133Test.put(m,p,"source");return m;}
    static CardInstance enter(ServerMatch m,UUID p){var card=hand(m,p,"source");m.playBasicPokemonToBench(p,card.instanceId());return card;}
    static void accept(ServerMatch m,UUID p,boolean use){var q=m.triggerOrderPrompt();check(q!=null&&q.optionalDecision()&&q.choosingPlayer().equals(p),"Missing private Gate decision");m.decideTriggeredEffect(p,q.id(),use);}
    static List<CardInstance> vmax(ServerMatch m,UUID p){var result=new ArrayList<CardInstance>();String id=p.equals(A)?"evolved":"defender_evolved";for(int i=0;i<2;i++){var card=NonPokemonBatch130Test.toDeck(m,p,id);result.add(card);}return result;}
    static void gatePlacement(){for(UUID p:List.of(A,B))for(int count=-1;count<=2;count++){
        var m=gateGame(p);var targets=vmax(m,p);int bench=m.benchPokemonView(p).size(),deck=zone(m,p,"deck").size(),rng=m.randomTranscript().size();enter(m,p);privateToTrigger(m,p);accept(m,p,count>=0);
        if(count<0){check(m.currentPlayer().equals(p)&&zone(m,p,"deck").size()==deck&&m.randomTranscript().size()==rng,"Declined Gate shuffled/ended turn");continue;}
        var q=m.effectCardPrompt(p);check(q.minimum()==0&&q.maximum()==2&&q.candidates().containsAll(targets)&&q.candidates().stream().allMatch(c->m.cardDefinition(c.definitionId()).pokemonFamilies().contains(PokemonFamily.VMAX)),"VMAX/evolution private search wrong");privateTo(m,p,zone(m,p,"deck"));
        reject(()->m.submitEffectCards(p,q.resolutionId(),q.step(),List.of(hand(m,p,p.equals(A)?"attacker":"defender").instanceId())));choose(m,p,targets.subList(0,count));NonPokemonBatch103Test.settle(m);
        check(m.currentPlayer().equals(other(p))&&m.benchPokemonView(p).size()==bench+1+count&&zone(m,p,"deck").size()==deck-count,"Gate did not place VMAX/end turn");check(m.randomTranscript().size()==rng+Math.max(0,deck-count-1),"Gate did not shuffle exactly once");
        for(var card:targets.subList(0,count)){var mon=EventTargetBindingTest.field(m).pokemonView(p,card.instanceId());check(mon.evolutionCardIds().equals(List.of(card.definitionId())),"VMAX gained a fictional pre-evolution");}
    }}
    static void privateToTrigger(ServerMatch m,UUID p){var q=m.triggerOrderPrompt();reject(()->m.decideTriggeredEffect(other(p),q.id(),true));reject(()->m.decideTriggeredEffect(p,UUID.randomUUID(),true));}
    static void gateUnavailable(){for(String mode:List.of("FULL","EMPTY","NO_VMAX","DECK","DISCARD","OPPONENT_TURN")){
        var m=gateGame(A,mode.equals("OPPONENT_TURN"));if(mode.equals("FULL"))while(m.benchPokemonView(A).size()<4)m.playBasicPokemonToBench(A,hand(m,A,"attacker").instanceId());
        if(mode.equals("EMPTY")){zone(m,A,"hand").addAll(zone(m,A,"deck"));zone(m,A,"deck").clear();}
        if(mode.equals("NO_VMAX")){var cards=zone(m,A,"deck").stream().filter(c->m.cardDefinition(c.definitionId()).pokemonFamilies().contains(PokemonFamily.VMAX)).toList();zone(m,A,"deck").removeAll(cards);zone(m,A,"hand").addAll(cards);}
        if(mode.equals("DECK")){var card=hand(m,A,"source");NonPokemonBatch118Test.top(m,A,card);m.putBasicPokemonFromOwnDeckToBenchByEffect(A,card.instanceId());}
        else if(mode.equals("DISCARD")){var card=hand(m,A,"source");m.discardHandCardsByEffect(A,A,List.of(card.instanceId()));m.putBasicPokemonFromDiscardToBenchByEffect(A,A,card.instanceId());}
        else if(mode.equals("OPPONENT_TURN")){m.endTurn(A);m.attack(B,"hit");var reveal=m.effectCardReveal();m.acknowledgeEffectReveal(reveal.confirmingPlayer(),reveal.id());choose(m,B,List.of(hand(m,A,"source")));}
        else enter(m,A);
        if(mode.equals("NO_VMAX")){int deck=zone(m,A,"deck").size(),rng=m.randomTranscript().size();accept(m,A,true);if(m.hasPendingCardSelection()){check(m.effectCardPrompt(A).candidates().isEmpty(),"Hidden search guessed available VMAX");choose(m,A,List.of());}check(m.currentPlayer().equals(B)&&zone(m,A,"deck").size()==deck&&m.randomTranscript().size()==rng+Math.max(0,deck-1),"Failed hidden search did not shuffle/end turn");}
        else check(m.triggerOrderPrompt()==null&&!m.hasPendingEffectChoice(),"Illegal Gate entry offered: "+mode);
    }}
    static void entryTiming(){for(UUID p:List.of(A,B))for(boolean gateFirst:List.of(false,true))for(boolean oppositeStadium:List.of(false,true)){
        var m=gateGame(p);if(oppositeStadium)m.endTurn(p);LuckyBonusEntryTest.stadium(m,oppositeStadium?other(p):p,"swsh10_en_142");if(oppositeStadium)m.endTurn(other(p));vmax(m,p);var card=enter(m,p);var q=m.triggerOrderPrompt();check(q!=null&&!q.optionalDecision()&&q.entries().size()==2,"Gapejaw and Gate not simultaneously orderable");var first=q.entries().stream().filter(e->(e.sourceKind()==EffectOrigin.Kind.ABILITY)==gateFirst).findFirst().orElseThrow();m.chooseTriggeredEffect(p,q.id(),first.id());
        if(!gateFirst)check(EventTargetBindingTest.field(m).pokemonView(p,card.instanceId()).damage()==20,"Bog did not resolve first");accept(m,p,true);choose(m,p,List.of());NonPokemonBatch103Test.settle(m);check(EventTargetBindingTest.field(m).pokemonView(p,card.instanceId()).damage()==20&&m.currentPlayer().equals(other(p)),"Gate skipped simultaneous Bog or failed to end turn");
    }}
    static void schema(){TriggeredAbilityRules.validate(gate());for(String change:List.of("mandatory","wrong-turn","wrong-family","source","event")){var raw=RAW.get("swsh12-5_en_105").getAsJsonArray("abilities").get(0).getAsJsonObject().deepCopy();switch(change){case "mandatory"->raw.getAsJsonObject("eventPolicy").addProperty("optional",false);case "wrong-turn"->raw.getAsJsonArray("triggers").get(0).getAsJsonObject().remove("conditions");case "wrong-family"->raw.getAsJsonArray("triggers").get(0).getAsJsonObject().getAsJsonArray("effects").get(0).getAsJsonObject().addProperty("pokemon_families","V");case "source"->raw.getAsJsonObject("eventPolicy").addProperty("source","DECK");case "event"->raw.getAsJsonObject("eventPolicy").addProperty("watch","OWN_FIELD");}reject(()->TriggeredAbilityRules.validate(BundledCombatData.ability(raw,EffectRegistry.standard())));}}
    static void ai(){for(UUID p:List.of(A,B))for(var level:AiDifficulty.values()){
        for(boolean knockoutReady:List.of(false,true)) {
            var choice=gateGame(p);vmax(choice,p);if(knockoutReady)NonPokemonBatch75Test.hurt(choice,other(p),active(choice,other(p)),700);enter(choice,p);
            var s=MatchSession.multiplayer(choice,A,B);var input=AiPracticeScenarios.view(s,p);var decision=AiPolicy.choose(input,29,Set.of(),level).orElseThrow();
            check(input.snapshot().options().get(decision.option()).type()==(knockoutReady?ActionType.TRIGGER_SKIP:ActionType.TRIGGER_USE),"AI ignored turn-ending trigger cost: "+level);
            int rng=choice.randomTranscript().size();Collections.reverse(zone(choice,other(p),"deck"));Collections.reverse(zone(choice,other(p),"hand"));
            check(decision.equals(AiPolicy.choose(AiPracticeScenarios.view(s,p),29,Set.of(),level).orElseThrow())&&choice.randomTranscript().size()==rng,"Gate strategy read hidden order/game RNG");
        }
        var m=gateGame(p);vmax(m,p);enter(m,p);accept(m,p,true);var session=MatchSession.multiplayer(m,A,B);AiCardOperationTest.settle(session,level);check(m.currentPlayer().equals(other(p)),"AI stalled in Gate choice "+level);AiCardOperationTest.finish(session,level);check(m.phase()==MatchPhase.FINISHED,"AI Gate match did not finish");
        var n=windowGame(p,true);window(n,p,List.of("energy","trainer","trainer"),false);n.attack(p,"horoscope");var s=MatchSession.multiplayer(n,A,B);AiCardOperationTest.settle(s,level);check(n.currentPlayer().equals(other(p)),"AI stalled in Horoscope order "+level);AiCardOperationTest.finish(s,level);check(n.phase()==MatchPhase.FINISHED,"AI Horoscope match did not finish");
    }}
}
