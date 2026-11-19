import com.google.gson.JsonParser;
import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch66Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final List<String> IDS=List.of("sm1_en_131","sm4_en_099","xy3_en_094","xy3_en_098");
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static TrainerProfile profile(String id){return NonPokemonBatch2Test.DATA.trainer(id).orElseThrow();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return DynamicPassiveModifierTest.hand(m,p,id);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static void settle(ServerMatch m){AiCardOperationTest.settle(MatchSession.multiplayer(m,A,B),AiDifficulty.NORMAL);}
    static GeneratedRuleBatch28Test.Config config(String id){var c=new GeneratedRuleBatch28Test.Config();c.trainer=profile(id);if(id.equals("xy3_en_094")||id.equals("xy3_en_098"))c.names=Map.of("evolved",id.equals("xy3_en_094")?"Tyrunt":"Amaura","defender_evolved",id.equals("xy3_en_094")?"Tyrunt":"Amaura");if(id.equals("xy3_en_094")||id.equals("xy3_en_098")){var restored=NonPokemonBatch2Test.DATA.pokemon(id.equals("xy3_en_094")?"xy3_en_061":"xy3_en_025").orElseThrow();c.restoredProfiles=Map.of("evolved",restored,"defender_evolved",restored);}return c;}
    static ServerMatch aiGame(String id){var m=GeneratedRuleBatch28Test.fixture(config(id));if(id.startsWith("xy3")){var card=hand(m,A,"evolved");bottom(m,A,List.of(card));}return m;}
    static void bottom(ServerMatch m,UUID p,List<CardInstance> cards){var ids=cards.stream().map(CardInstance::instanceId).toList();m.discardHandCardsByEffect(p,p,ids);m.recoverDiscardCardsByEffect(p,p,new ServerMatch.CardSelectionPolicy(ids.size(),ids.size(),null,null,false,Set.of()),ids,CardEffectDestination.DECK_BOTTOM);}
    static void play(ServerMatch m,UUID p){m.playTrainer(p,hand(m,p,"trainer").instanceId(),null);}
    public static void main(String[] args){rotom();sea();seaLiveImmunity();fossils();schema();System.out.println("NonPokemonBatch66Test passed: whole-deck prize shuffle, exact physical counts and hidden replacement, legacy bottom mode; both players' five conditions across hand/deck evolution and devolution, live Stadium immunity, ordinary switch recovery; private bottom-seven fossil selection, optional failures, actual Restored printings and named placement and reveal/shuffle");}
    static void rotom(){for(UUID p:List.of(A,B))for(int remaining:List.of(0,1,4,12))for(boolean bottom:List.of(false,true)){
        var c=config(IDS.getFirst());if(bottom)c.trainer=BundledCombatData.trainer(JsonParser.parseString("{\"trainerCategory\":\"ITEM\",\"effects\":[{\"type\":\"reset_prizes\",\"target\":\"SELF\"}]}").getAsJsonObject(),EffectRegistry.standard());
        var m=GeneratedRuleBatch28Test.fixture(c);UUID other=p.equals(A)?B:A;if(p.equals(B))m.endTurn(A);m.drawCardsByEffect(p,p,Math.max(0,m.privateView(p).deck().size()-remaining));
        var before=new ArrayList<>(m.privateView(p).deck());var prizes=m.privateView(p).prizes();int deckSize=before.size(),count=prizes.size();var opponent=m.privateView(other);var source=hand(m,p,"trainer");int hand=m.privateView(p).hand().size(),random=m.randomTranscript().size();play(m,p);settle(m);
        var events=m.randomTranscript().subList(random,m.randomTranscript().size());var expected=new ArrayList<CardInstance>();if(bottom){expected.addAll(prizes);for(var event:events){check(event.operation().contains("reset-prizes-bottom"),"Legacy prize rule shuffled whole deck");Collections.swap(expected,event.bound()-1,event.result());}expected.addAll(before);}else{expected.addAll(before);expected.addAll(prizes);for(var event:events){check(event.operation().contains("reset-prizes-deck"),"Rotom shuffled only prizes");Collections.swap(expected,event.bound()-1,event.result());}check(events.size()==Math.max(0,deckSize+count-1),"Rotom did not shuffle all combined cards");}
        var replacement=new ArrayList<CardInstance>();for(int i=0;i<count;i++)replacement.add(expected.removeLast());check(m.privateView(p).prizes().equals(replacement)&&m.privateView(p).deck().equals(expected),"Prize reset wrong shuffle or top draw order");check(m.privateView(p).hand().size()==hand-1&&m.privateView(p).discard().contains(source)&&m.privateView(other).equals(opponent),"Prize reset changed unrelated zones/player");
        for(UUID viewer:List.of(A,B))check(m.clientView(viewer).players().stream().filter(v->v.id().equals(p)).findFirst().orElseThrow().faceUpPrizes().isEmpty(),"New prizes exposed");
    }}
    static void sea(){for(UUID p:List.of(A,B))for(UUID controller:List.of(A,B))for(SpecialCondition condition:SpecialCondition.values())for(String mode:List.of("hand","deck","devolve","removed","switch")){
        var c=config(IDS.get(1));if(mode.equals("deck"))c.ownAbility=ResumableAbilityTest.ability("deck-evolve",DeckEvolutionEffectTest.EVOLVE);var m=GeneratedRuleBatch28Test.fixture(c);if(controller.equals(B))m.endTurn(A);play(m,controller);if(!m.currentPlayer().equals(p))m.endTurn(m.currentPlayer());UUID host=active(m,p);String evolution=p.equals(A)?"evolved":"defender_evolved";
        if(mode.equals("devolve"))m.evolve(p,host,hand(m,p,evolution).instanceId());if(mode.equals("removed"))m.discardStadiumByEffect(p);m.applySpecialConditionByEffect(p,p,condition);
        if(mode.equals("switch"))m.switchActiveByEffect(p,p,m.benchPokemonView(p).getFirst().pokemonId());
        else if(mode.equals("devolve"))m.devolvePokemonToHandByEffect(p,p,host);
        else if(mode.equals("deck")&&p.equals(A)){NonPokemonBatch54Test.top(m,p,hand(m,p,evolution));m.useAbility(p,host,"deck-evolve");var q=m.effectCardPrompt(p);m.submitEffectCards(p,q.resolutionId(),q.step(),List.of(q.candidates().getFirst().instanceId()));}
        else m.evolve(p,host,hand(m,p,evolution).instanceId());settle(m);
        var view=mode.equals("switch")?m.benchPokemonView(p).stream().filter(v->v.pokemonId().equals(host)).findFirst().orElseThrow():m.activePokemonView(p);check(view.conditions().contains(condition)==!Set.of("removed","switch").contains(mode),"Sea wrong recovery: "+p+controller+condition+mode);
    }}
    static void seaLiveImmunity(){var moon=NonPokemonBatch2Test.DATA.pokemon("sv3_en_092").orElseThrow().abilities().getFirst();
        for(String mode:List.of("gained","lost","other","devolve-gained","devolve-lost")){
            var c=config(IDS.get(1));c.ownName="Solrock";if(Set.of("lost","devolve-gained").contains(mode))c.ownAbility=moon;else if(!mode.equals("other"))c.evolvedAbility=moon;else c.source=moon;
            var m=GeneratedRuleBatch28Test.fixture(c);if(c.ownAbility!=null)m.movePokemonByEffect(A,A,m.benchPokemonView(A).getFirst().pokemonId(),LeavePlayDestination.DISCARD);if(mode.equals("other"))m.playBasicPokemonToBench(A,hand(m,A,"source").instanceId());play(m,A);UUID host=active(m,A);
            if(mode.startsWith("devolve"))m.evolve(A,host,hand(m,A,"evolved").instanceId());m.applySpecialConditionByEffect(A,A,SpecialCondition.POISONED);
            if(mode.startsWith("devolve"))m.devolvePokemonToHandByEffect(A,A,host);else m.evolve(A,host,hand(m,A,"evolved").instanceId());settle(m);
            check(m.activePokemonView(A).conditions().contains(SpecialCondition.POISONED)==Set.of("lost","devolve-lost").contains(mode),"Sea read pre-evolution Stadium protection: "+mode);
        }
    }
    static void fossils(){for(String id:IDS.subList(2,4))for(UUID p:List.of(A,B))for(String mode:List.of("take","skip","outside","short","full")){
        var m=GeneratedRuleBatch28Test.fixture(config(id));if(p.equals(B))m.endTurn(A);String evolution=p.equals(A)?"evolved":"defender_evolved";var selected=hand(m,p,evolution);
        if(mode.equals("short"))m.drawCardsByEffect(p,p,m.privateView(p).deck().size());else m.drawCardsByEffect(p,p,Math.max(0,m.privateView(p).deck().size()-8));
        // Build a known bottom window without another matching Pokemon.
        var old=m.privateView(p).deck();if(!old.isEmpty())m.drawCardsByEffect(p,p,old.size());var filler=m.privateView(p).hand().stream().filter(v->v.definitionId().equals("energy")||v.definitionId().equals("trainer")).limit(mode.equals("short")?1:8).toList();bottom(m,p,filler);
        if(mode.equals("outside"))NonPokemonBatch54Test.top(m,p,selected);else bottom(m,p,List.of(selected));
        if(mode.equals("full"))while(m.benchPokemonView(p).size()<5){var card=m.privateView(p).hand().stream().filter(v->m.cardDefinition(v.definitionId()).basicPokemon()).findFirst().orElseThrow();m.playBasicPokemonToBench(p,card.instanceId());}
        var before=m.privateView(p).deck();var window=before.subList(0,Math.min(7,before.size()));int bench=m.benchPokemonView(p).size(),random=m.randomTranscript().size();if(mode.equals("full")){check(!m.canPlayTrainer(p,hand(m,p,"trainer").instanceId(),null),"Fossil usable with full Bench");continue;}play(m,p);
        var q=m.effectCardPrompt(p);check(new HashSet<>(q.inspectedCards()).equals(new HashSet<>(window)),"Fossil looked outside bottom seven");GeneratedRuleBatch8Test.reject(()->m.effectCardPrompt(p.equals(A)?B:A));check(q.candidates().stream().allMatch(v->v.equals(selected))&&q.candidates().contains(selected)==!mode.equals("outside"),"Fossil admitted wrong name/top cards");boolean take=Set.of("take","short").contains(mode);m.submitEffectCards(p,q.resolutionId(),q.step(),take?List.of(selected.instanceId()):List.of());
        if(take)check(m.effectCardReveal()==null&&m.benchPokemonView(p).size()==bench+1,"Fossil did not publicly place the selected card");settle(m);check(m.benchPokemonView(p).size()==bench+(take?1:0)&&m.privateView(p).deck().contains(selected)!=take,"Fossil failed placement/skip");check(m.randomTranscript().size()>random||m.privateView(p).deck().size()<2,"Fossil failed to shuffle");if(take)check(m.benchPokemonView(p).stream().anyMatch(v->v.topCardId().equals(evolution)&&v.evolutionCardIds().size()==1),"Restored Pokemon incorrectly evolved a host");
    }}
    static void schema(){for(String destination:List.of("HAND","DECK_TOP","LOST_ZONE"))GeneratedRuleBatch8Test.reject(()->EffectRegistry.standard().parse(JsonParser.parseString("{\"type\":\"reset_prizes\",\"target\":\"SELF\",\"destination\":\""+destination+"\"}").getAsJsonObject()));GeneratedRuleBatch8Test.reject(()->DynamicPassiveModifierTest.trainer("TOOL","{\"type\":\"retain_conditions_on_evolution\",\"target\":\"SELF\"}"));}
}
