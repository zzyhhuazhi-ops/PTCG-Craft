import com.google.gson.JsonParser;
import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch100Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final List<String> IDS=List.of("sm11_en_205");
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static UUID other(UUID p){return p.equals(A)?B:A;}
    static CardInstance hand(ServerMatch m,UUID p,String id){return DynamicPassiveModifierTest.hand(m,p,id);}
    static ServerMatch aiGame(String id){return game();}
    static ServerMatch game(){
        var kinds=List.of("basic","basic","basic","basic","stage1","second","stage2","wrong","restricted","energy","trainer","fossil");
        var defs=new ArrayList<CardDefinition>();var profiles=new HashMap<String,PokemonProfile>();
        var attack=BundledCombatData.attack(JsonParser.parseString("{\"id\":\"hit\",\"nameZh\":\"hit\",\"energyCost\":{},\"baseDamage\":100,\"damageType\":\"COLORLESS\",\"effectSupport\":\"SUPPORTED\"}").getAsJsonObject(),EffectRegistry.standard());
        for(String id:new LinkedHashSet<>(kinds)){
            boolean pokemon=!Set.of("energy","trainer","fossil").contains(id);
            defs.add(new CardDefinition(id,id,pokemon?CardCategory.POKEMON:id.equals("energy")?CardCategory.ENERGY:CardCategory.TRAINER,id.equals("trainer")?TrainerCategory.STADIUM:id.equals("fossil")?TrainerCategory.ITEM:TrainerCategory.NONE,"H",Set.of("international"),id.equals("basic"),id.equals("energy"),60,Map.of(),EffectSupport.SUPPORTED,null));
            if(pokemon){String parent=id.equals("basic")?null:id.equals("stage2")?"stage1":"fossil";String parentName=id.equals("basic")?null:id.equals("stage2")?"stage1":id.equals("wrong")?"Helix Fossil":"Unidentified Fossil";
                profiles.put(id,new PokemonProfile(300,0,parent,null,0,null,0,1,List.of(attack),List.of(),EnergyType.COLORLESS,id,parentName,id.equals("restricted")?PokemonPlacementRule.ZERO_TO_HERO_ONLY:PokemonPlacementRule.ANY_EFFECT));}
        }
        var combat=new CombatCatalog(profiles,Map.of("energy",EnergyType.COLORLESS),Map.of("trainer",NonPokemonBatch2Test.DATA.trainer(IDS.getFirst()).orElseThrow(),"fossil",NonPokemonBatch2Test.DATA.trainer("sm11_en_210").orElseThrow()));
        for(int seed=0;seed<300;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID p:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(p.getLeastSignificantBits(),i+1),p,kinds.get(i%kinds.size())));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),combat,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().noneMatch(c->c.definitionId().equals("basic"))))continue;
            OpeningTestSupport.complete(m,A,B,0);m.drawCardsByEffect(A,A,10);m.drawCardsByEffect(A,B,10);m.endTurn(A);m.endTurn(B);
            if(List.of(A,B).stream().allMatch(p->m.privateView(p).hand().stream().anyMatch(c->c.definitionId().equals("trainer"))&&m.privateView(p).deck().stream().filter(c->c.definitionId().equals("basic")).count()>=5&&m.privateView(p).deck().stream().filter(c->Set.of("stage1","second").contains(c.definitionId())).count()>=2))return m;
        }throw new AssertionError("Research Lab fixture unavailable");
    }
    static void start(ServerMatch m,UUID controller,UUID actor){if(controller.equals(B))m.endTurn(A);m.playTrainer(controller,hand(m,controller,"trainer").instanceId(),null);if(!controller.equals(actor))m.endTurn(controller);}
    static void activate(ServerMatch m,UUID p){NonPokemonBatch42Test.activate(m,p);}
    static void select(ServerMatch m,UUID p,List<CardInstance> cards){var q=m.effectCardPrompt(p);m.submitEffectCards(p,q.resolutionId(),q.step(),cards.stream().map(CardInstance::instanceId).toList());}
    public static void main(String[] args){placements();boundaries();authority();ai();schema();System.out.println("NonPokemonBatch100Test passed: Research Lab immediate Fossil evolution filter, both controllers/actors, zero-one-two choices and mandatory turn end, physical placement/stage/capacity/restricted cards, hidden searches and atomic stale/foreign choices, all AI difficulties");}
    static void placements(){for(UUID controller:List.of(A,B))for(UUID actor:List.of(A,B))for(int count:List.of(0,1,2)){
        var m=game();start(m,controller,actor);var deck=List.copyOf(m.privateView(actor).deck());int rng=m.randomTranscript().size();activate(m,actor);var q=m.effectCardPrompt(actor);
        check(q!=null&&q.minimum()==0&&q.maximum()==2,"Wrong search bounds");var legal=deck.stream().filter(c->Set.of("stage1","second").contains(c.definitionId())).toList();check(new HashSet<>(q.candidates()).equals(new HashSet<>(legal)),"Search admitted indirect/wrong/restricted/Fossil or omitted direct evolutions");
        check(m.clientView(other(actor)).effectSelection()==null&&m.privateView(actor).deck().equals(deck),"Search leaked control or moved cards before choice");var chosen=q.candidates().stream().limit(count).toList();select(m,actor,chosen);
        check(m.currentPlayer().equals(other(actor))&&!m.hasPendingEffectChoice()&&m.benchPokemonView(actor).size()==count,"Optional search did not end turn or place exact count");check(m.randomTranscript().size()>rng,"Research Lab did not shuffle");var rest=new HashSet<>(deck);rest.removeAll(chosen);check(rest.equals(new HashSet<>(m.privateView(actor).deck())),"Search lost other cards");
        for(var card:chosen){var v=m.benchPokemonView(actor).stream().filter(x->x.topCardId().equals(card.definitionId())).findFirst().orElseThrow();check(!m.cardDefinition(v.topCardId()).basicPokemon()&&v.evolutionCardIds().size()==1&&v.damage()==0,"Direct placement changed printed stage or fabricated lower Fossil");GeneratedRuleBatch8Test.reject(()->m.devolvePokemonToHandByEffect(other(actor),actor,v.pokemonId()));}
    }}
    static void boundaries(){for(int free:List.of(0,1)){var m=game();start(m,A,A);int fill=m.benchLimit()-free;var cards=m.privateView(A).deck().stream().filter(c->c.definitionId().equals("basic")).limit(fill).map(CardInstance::instanceId).toList();m.putBasicPokemonFromOwnDeckToBenchByEffect(A,new ServerMatch.CardSelectionPolicy(fill,fill,CardCategory.POKEMON,null,true,Set.of()),cards);
        if(free==0){check(!m.canActivateStadium(A),"Full Bench can activate");GeneratedRuleBatch44Test.assertRejectedAtomic(m,()->activate(m,A));}else{activate(m,A);var q=m.effectCardPrompt(A);check(q.maximum()==1,"One free slot did not cap choice");GeneratedRuleBatch44Test.assertRejectedAtomic(m,()->select(m,A,q.candidates().subList(0,2)));select(m,A,q.candidates().subList(0,1));check(m.benchPokemonView(A).size()==5&&m.currentPlayer().equals(B),"One-slot resolution failed");}}
        for(boolean empty:List.of(false,true)){var m=game();start(m,A,A);var deck=PairedTrainerPlayTest.zone(m,A,"deck");var discard=PairedTrainerPlayTest.zone(m,A,"discard");var removed=deck.stream().filter(c->empty||!c.definitionId().equals("energy")).toList();deck.removeAll(removed);discard.addAll(removed);
            if(empty){check(!m.canActivateStadium(A),"Empty deck activation allowed");GeneratedRuleBatch44Test.assertRejectedAtomic(m,()->activate(m,A));}else{check(m.canActivateStadium(A),"Hidden deck with no matching card disallowed");activate(m,A);if(m.hasPendingEffectChoice())select(m,A,List.of());check(m.currentPlayer().equals(B)&&m.benchPokemonView(A).isEmpty(),"Failed search did not end turn");}}
    }
    static void authority(){var m=game();start(m,A,A);activate(m,A);var q=m.effectCardPrompt(A);var first=q.candidates().getFirst();var invalid=m.privateView(A).deck().stream().filter(c->!q.candidates().contains(c)).findFirst().orElseThrow();for(Runnable action:List.<Runnable>of(()->m.submitEffectCards(B,q.resolutionId(),q.step(),List.of(first.instanceId())),()->m.submitEffectCards(A,q.resolutionId(),q.step()+1,List.of(first.instanceId())),()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(first.instanceId(),first.instanceId())),()->select(m,A,List.of(invalid)),()->select(m,A,List.of(hand(m,B,"trainer"))),()->activate(m,A)))GeneratedRuleBatch44Test.assertRejectedAtomic(m,action);select(m,A,List.of(first));GeneratedRuleBatch44Test.assertRejectedAtomic(m,()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(first.instanceId())));
        var stale=game();start(stale,A,A);var stadium=stale.clientView(A).stadium();long entry=stale.stadiumEntrySequence();stale.discardStadiumByEffect(A);GeneratedRuleBatch44Test.assertRejectedAtomic(stale,()->stale.activateStadium(A,stadium.instanceId(),entry));
    }
    static void ai(){for(var d:AiDifficulty.values()){var m=game();start(m,A,A);activate(m,A);var s=MatchSession.multiplayer(m,A,B);AiCardOperationTest.settle(s,d);check(!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"AI stalled Research Lab: "+d);}}
    static void schema(){var effect=NonPokemonBatch2Test.DATA.trainer(IDS.getFirst()).orElseThrow().activation().program().triggers().getFirst().effects().getFirst();check(ZoneSelectionEffect.researchLabPlacement(effect),"Printed program changed");for(var kv:Map.of("evolves_from_name","Fossil","amount","3","minimum","1","destination","HAND","any_stage","false").entrySet()){var args=new HashMap<>(effect.arguments());args.put(kv.getKey(),kv.getValue());var bad=new EffectDefinition(effect.type(),effect.target(),args,List.of(),List.of());GeneratedRuleBatch8Test.reject(()->ZoneSelectionEffect.forProgram(bad,true));}}
}
