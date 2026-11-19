import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Hidden physical windows, unrestricted printed stages, placement restrictions and entry events. */
public final class AnyStageDeckPlacementTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B,WATCH=new UUID(0,5800);
    static final String PORTAL="""
        {"type":"search_deck","target":"DECK","top_count":9,"amount":9,"minimum":0,"card_category":"POKEMON","destination":"BENCH","any_stage":true}
        """;
    static final String FRUIT="""
        {"type":"search_deck","target":"DECK","top_count":6,"amount":6,"minimum":0,"card_category":"POKEMON","reveal":true}
        """;
    static final String DELIVERY="""
        {"type":"coin_flip","flips":2,"effects":[{"type":"conditional","conditions":[{"type":"COIN_RESULT","value":"ALL_HEADS"}],"effects":[{"type":"search_deck","target":"DECK","amount":1,"minimum":0,"card_category":"POKEMON","destination":"BENCH","any_stage":true}]}]}
        """;
    static void check(boolean b,String s){GeneratedRuleBatch8Test.check(b,s);}
    static void atomic(ServerMatch m,Runnable r){GeneratedRuleBatch44Test.assertRejectedAtomic(m,r);}
    static AttackDefinition attack(String effects){return ResumableAttackTest.attack(effects);}
    static List<CardInstance> top(ServerMatch m,int n){return GeneratedRuleBatch44Test.top(m,n);}
    static Set<UUID> ids(Collection<CardInstance> c){return GeneratedRuleBatch44Test.ids(c);}
    static void submit(ServerMatch m,List<CardInstance> c){var p=m.effectCardPrompt(A);m.submitEffectCards(A,p.resolutionId(),p.step(),c.stream().map(CardInstance::instanceId).toList());}
    static ServerMatch game(AttackDefinition attack,int first,List<AbilityDefinition> abilities){
        String[] kinds={"basic","basic","basic","basic","stage1","stage2","restricted","energy","energy","item"};
        var defs=new ArrayList<CardDefinition>();var profiles=new HashMap<String,PokemonProfile>();
        for(String id:Arrays.stream(kinds).distinct().toList()){
            boolean pokemon=!Set.of("energy","item").contains(id);
            defs.add(new CardDefinition(id,id,pokemon?CardCategory.POKEMON:id.equals("energy")?CardCategory.ENERGY:CardCategory.TRAINER,id.equals("item")?TrainerCategory.ITEM:TrainerCategory.NONE,"J",Set.of("international"),id.equals("basic"),id.equals("energy"),60,Map.of(),EffectSupport.SUPPORTED,null));
            if(pokemon)profiles.put(id,new PokemonProfile(700,0,id.equals("basic")?null:id.equals("stage2")?"stage1":"basic",null,0,null,0,1,List.of(attack),id.startsWith("stage")?abilities:List.of(),EnergyType.COLORLESS,id,id.equals("basic")?null:id.equals("stage2")?"stage1":"basic",id.equals("restricted")?PokemonPlacementRule.ZERO_TO_HERO_ONLY:PokemonPlacementRule.ANY_EFFECT));
        }
        var combat=new CombatCatalog(profiles,Map.of("energy",EnergyType.FIRE),Map.of("item",GeneratedRuleBatch8Test.trainer("swsh5_en_124")));
        for(int seed=first;seed<first+1000;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID owner:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,kinds[i%kinds.length]));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),combat,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().noneMatch(c->c.definitionId().equals("basic"))))continue;
            OpeningTestSupport.complete(m,A,B,0);m.drawCardsByEffect(A,A,10);m.endTurn(A);m.endTurn(B);
            int needed=attack.energyCost().values().stream().mapToInt(Integer::intValue).sum();var energies=m.privateView(A).hand().stream().filter(c->c.definitionId().equals("energy")).toList();
            if(energies.size()<needed)continue;
            for(var c:energies.subList(0,needed))m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,c.instanceId(),A,m.activePokemonView(A).pokemonId());
            return m;
        }throw new AssertionError("No deck window fixture");
    }
    static ServerMatch game(AttackDefinition a){return game(a,0,List.of());}
    static void privacy(ServerMatch m,List<CardInstance> window){
        var session=MatchSession.multiplayer(m,A,B);session.addSpectator(WATCH);
        for(var v:List.of(session.view(B,B),session.view(WATCH,null))){check(v.game().effectSelection()==null,"Private selection leaked");String json=new Gson().toJson(v);for(var c:window)check(!json.contains(c.instanceId().toString()),"Hidden physical ID leaked");}
        var p=m.effectCardPrompt(A);check(ids(p.inspectedCards()).equals(ids(window)),"Inspected window was filtered/reordered before capture");
    }
    static void portal(AttackDefinition a){
        boolean stage1=false,stage2=false,restricted=false;
        for(int seed=0;seed<12;seed++){
            var m=game(a,seed,List.of());var before=List.copyOf(m.privateView(A).deck());var window=top(m,9);int rng=m.randomTranscript().size();m.attack(A,a.id());privacy(m,window);var p=m.effectCardPrompt(A);
            var legal=window.stream().filter(c->Set.of("basic","stage1","stage2").contains(c.definitionId())).toList();check(ids(legal).equals(ids(p.candidates()))&&p.maximum()==5&&p.minimum()==0,"Wrong any-stage/window/capacity candidates");
            check(before.equals(m.privateView(A).deck())&&rng==m.randomTranscript().size(),"Inspection moved/shuffled deck");
            var outside=before.stream().filter(c->!window.contains(c)).findFirst().orElseThrow();atomic(m,()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(outside.instanceId())));
            for(var c:window)if(!legal.contains(c)){atomic(m,()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(c.instanceId())));restricted|=c.definitionId().equals("restricted");}
            var chosen=legal.stream().sorted(Comparator.comparing(c->c.definitionId().equals("basic")?1:0)).limit(5).toList();
            if(!chosen.isEmpty()){var id=chosen.getFirst().instanceId();atomic(m,()->m.submitEffectCards(B,p.resolutionId(),p.step(),List.of(id)));atomic(m,()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(id,id)));atomic(m,()->m.submitEffectCards(A,p.resolutionId(),p.step()+1,List.of(id)));}
            submit(m,chosen);check(m.effectCardReveal()==null&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Direct placement awaited reveal or failed to finish");
            check(m.benchPokemonView(A).size()==chosen.size(),"Wrong Bench placement count");
            for(var c:chosen){stage1|=c.definitionId().equals("stage1");stage2|=c.definitionId().equals("stage2");var v=m.benchPokemonView(A).stream().filter(x->x.pokemonId().equals(PokemonIdentityTestSupport.logical(m,A,c.instanceId()))).findFirst().orElseThrow();check(v.evolutionCardIds().size()==1&&v.damage()==0&&v.conditions().isEmpty(),"Direct placement was evolution or inherited state");}
            var remainder=new HashSet<>(before);remainder.removeAll(chosen);check(remainder.equals(new HashSet<>(m.privateView(A).deck()))&&m.randomTranscript().size()>rng,"Remainder not conserved/shuffled");atomic(m,()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of()));
        }check(stage1&&stage2&&restricted,"Fixture did not exercise Stage1/Stage2/restricted card");
    }
    static void fruit(AttackDefinition a){
        boolean multiple=false;
        for(int count:List.of(0,1,6)){
            var m=game(a);var before=List.copyOf(m.privateView(A).deck());var window=top(m,6);int hand=m.privateView(A).hand().size(),rng=m.randomTranscript().size();m.attack(A,a.id());privacy(m,window);var p=m.effectCardPrompt(A);
            var legal=window.stream().filter(c->m.cardDefinition(c.definitionId()).category()==CardCategory.POKEMON).toList();check(ids(legal).equals(ids(p.candidates()))&&p.maximum()==6,"Top six hand filter wrong");var chosen=legal.stream().limit(count).toList();multiple|=chosen.size()>1;submit(m,chosen);
            if(!chosen.isEmpty()){var reveal=m.effectCardReveal();check(reveal!=null&&reveal.cardIds().size()==chosen.size()&&before.equals(m.privateView(A).deck()),"Reveal moved cards prematurely");atomic(m,()->m.acknowledgeEffectReveal(A,reveal.id()));m.acknowledgeEffectReveal(B,reveal.id());}
            check(m.privateView(A).hand().size()==hand+chosen.size()&&m.privateView(A).hand().containsAll(chosen)&&m.randomTranscript().size()>rng&&!m.hasPendingEffectChoice(),"Optional hand transfer/shuffle incomplete");
            var rest=new HashSet<>(before);rest.removeAll(chosen);check(rest.equals(new HashSet<>(m.privateView(A).deck())),"Hand search lost unchosen cards");
        }check(multiple,"No multi-card hand reveal tested");
    }
    static void boundaries(AttackDefinition a,boolean bench){
        for(int remaining:List.of(0,1,3)){
            var m=game(a);m.discardTopDeckCardsByEffect(A,A,m.privateView(A).deck().size()-remaining);var before=m.privateView(A);int rng=m.randomTranscript().size();m.attack(A,a.id());
            if(remaining>0){check(m.effectCardPrompt(A).inspectedCards().size()==remaining,"Short deck window wrong");submit(m,List.of());}
            check(!m.hasPendingEffectChoice()&&new HashSet<>(m.privateView(A).deck()).equals(new HashSet<>(before.deck()))&&m.currentPlayer().equals(B),"Short/empty attack did not finish");
            check(remaining>1?m.randomTranscript().size()>rng:m.randomTranscript().size()==rng,"Wrong short/empty shuffle RNG");
        }
        if(bench)for(int free:List.of(0,1)){
            var m=game(a);int fill=m.benchLimit()-free;var chosen=m.privateView(A).deck().stream().filter(c->c.definitionId().equals("basic")).limit(fill).map(CardInstance::instanceId).toList();m.putBasicPokemonFromOwnDeckToBenchByEffect(A,new ServerMatch.CardSelectionPolicy(fill,fill,CardCategory.POKEMON,null,true,Set.of()),chosen);var deck=m.privateView(A).deck();int rng=m.randomTranscript().size();m.attack(A,a.id());
            if(free==0)check(!m.hasPendingEffectChoice()&&deck.equals(m.privateView(A).deck())&&rng==m.randomTranscript().size(),"Full Bench inspected/shuffled hidden deck");
            else {var p=m.effectCardPrompt(A);check(p.maximum()==1,"One free slot did not limit selection");if(p.candidates().size()>1)atomic(m,()->m.submitEffectCards(A,p.resolutionId(),p.step(),p.candidates().subList(0,2).stream().map(CardInstance::instanceId).toList()));submit(m,p.candidates().stream().limit(1).toList());}
        }
    }
    static void coins(AttackDefinition a){
        var outcomes=new HashSet<Integer>();boolean evolution=false;
        for(int seed=0;seed<150&&(outcomes.size()<3||!evolution);seed++){
            var m=game(a,seed,List.of());var deck=m.privateView(A).deck();int rng=m.randomTranscript().size();m.attack(A,a.id());var flips=m.randomTranscript().subList(rng,m.randomTranscript().size());check(flips.size()==2,"Coin branch consumed extra RNG before selection");int heads=(int)flips.stream().filter(x->x.result()==0).count();outcomes.add(heads);
            if(heads==2){var p=m.effectCardPrompt(A);check(p.inspectedCards().isEmpty()&&p.maximum()==1,"Whole deck search misreported a top window");var card=p.candidates().stream().filter(c->c.definitionId().equals("stage2")).findFirst().orElseThrow();submit(m,List.of(card));evolution=true;check(m.benchPokemonView(A).size()==1&&m.randomTranscript().size()>rng+2,"Two heads did not place Evolution and shuffle");}
            else check(!m.hasPendingEffectChoice()&&deck.equals(m.privateView(A).deck())&&m.benchPokemonView(A).isEmpty(),"Failed coin branch inspected/moved/shuffled deck");
        }check(outcomes.size()==3&&evolution,"Coin outcomes not covered");
    }
    static AbilityDefinition event(String trigger,String source){return BundledCombatData.ability(JsonParser.parseString("{\"id\":\"entry\",\"nameZh\":\"entry\",\"kind\":\"TRIGGERED\",\"effectSupport\":\"SUPPORTED\",\"eventPolicy\":{\"trigger\":\""+trigger+"\",\"watch\":\"SELF\",\"source\":\""+source+"\",\"optional\":false,\"usage\":\"NONE\"},\"triggers\":[{\"trigger\":\""+trigger+"\",\"effects\":[{\"type\":\"draw\",\"amount\":1}]}]}").getAsJsonObject(),EffectRegistry.standard());}
    static void events(){for(String source:List.of("DECK","HAND","EVOLVE")){
        var a=attack(PORTAL.replace("\"top_count\":9,",""));var m=game(a,0,List.of(event(source.equals("EVOLVE")?"ON_EVOLVE":"ON_ENTER_PLAY",source.equals("EVOLVE")?"ANY":source)));int hand=m.privateView(A).hand().size();m.attack(A,a.id());var selected=m.effectCardPrompt(A).candidates().stream().filter(c->c.definitionId().equals("stage1")).limit(2).toList();check(selected.size()==2,"Missing entry candidates");submit(m,selected);DamageTriggeredAbilityTest.finishTriggers(m);check(m.privateView(A).hand().size()==hand+(source.equals("DECK")?2:0),"Entry triggered wrong source or evolution");
    }}
    static void schema(){var reg=EffectRegistry.standard();var good=JsonParser.parseString(PORTAL).getAsJsonObject();var effect=reg.parse(good);check(ZoneSelectionEffect.forProgram(effect,true).placesAnyStage(),"Any-stage attack rejected");GeneratedRuleBatch8Test.reject(()->ZoneSelectionEffect.forProgram(effect,false));
        for(var kv:Map.of("any_stage","false","destination","HAND","target","DISCARD","card_category","ENERGY","basic_pokemon_only","true","source_owner","OPPONENT","allow_evolution","true","reveal","true").entrySet()){var bad=good.deepCopy();bad.addProperty(kv.getKey(),kv.getValue());GeneratedRuleBatch8Test.reject(()->ZoneSelectionEffect.forProgram(reg.parse(bad),true));}
        var m=game(attack(PORTAL));atomic(m,()->m.openEffectZoneSelection(A,effect,1));var evolution=m.privateView(A).deck().stream().filter(c->c.definitionId().equals("stage1")).findFirst().orElseThrow();atomic(m,()->m.putBasicPokemonFromOwnDeckToBenchByEffect(A,evolution.instanceId()));
    }
    public static void main(String[] args){portal(attack(PORTAL));fruit(attack(FRUIT));boundaries(attack(PORTAL),true);boundaries(attack(FRUIT),false);coins(attack(DELIVERY));events();schema();System.out.println("AnyStageDeckPlacementTest passed: private physical windows, Stage1/Stage2 direct entry, restricted placement rejection, optional multiple selection/reveal, short/empty/full decks and Bench, coin branches, entry source events and server authority");}
}
