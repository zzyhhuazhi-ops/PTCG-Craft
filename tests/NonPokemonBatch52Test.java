import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch52Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final String LYS="xy4_en_099",ZONE="sv6-5_en_060";
    static final List<String> IDS=List.of(LYS);
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static CardInstance card(ServerMatch m,UUID p,String id){return DynamicPassiveModifierTest.hand(m,p,id);}
    static AbilityDefinition lock(String mode){return mode.equals("none")?null:NonPokemonBatch2Test.DATA.pokemon(mode.equals("hand")?"sv4_en_017":"sv3-5_en_027").orElseThrow().abilities().getFirst();}
    static ServerMatch game(String mode,TrainerProfile trainer){
        return game(mode,trainer,null);
    }
    static ServerMatch game(String mode,TrainerProfile trainer,AbilityDefinition extra){
        String[] ids={"basic","basic","basic","energy","special","item","tool","trainer",LYS,ZONE};var defs=new ArrayList<CardDefinition>();
        var draw=DeckDiscardTriggeredAbilityTest.trainer("ITEM","{\"type\":\"draw\",\"amount\":1}");
        var profiles=Map.of("trainer",trainer,LYS,NonPokemonBatch2Test.DATA.trainer(LYS).orElseThrow(),ZONE,NonPokemonBatch2Test.DATA.trainer(ZONE).orElseThrow(),"item",draw,"tool",DynamicPassiveModifierTest.plainTool());
        for(String id:Arrays.stream(ids).distinct().toList()){boolean pokemon=id.equals("basic"),energy=Set.of("energy","special").contains(id);defs.add(new CardDefinition(id,id,pokemon?CardCategory.POKEMON:energy?CardCategory.ENERGY:CardCategory.TRAINER,pokemon||energy?TrainerCategory.NONE:profiles.get(id).category(),"H",Set.of("international"),pokemon,id.equals("energy"),60,Map.of(),EffectSupport.SUPPORTED,null));}
        var ability=lock(mode);var abilities=new ArrayList<AbilityDefinition>();if(ability!=null)abilities.add(ability);if(extra!=null)abilities.add(extra);var combat=new CombatCatalog(Map.of("basic",new PokemonProfile(500,0,null,null,0,null,0,1,List.of(GeneratedRuleBatch8Test.hit(100,false)),abilities,EnergyType.GRASS)),Map.of("energy",EnergyType.GRASS),profiles,Map.of("special",EnergyProfile.fixed(EnergyType.COLORLESS,2)));
        for(int seed=0;seed<500;seed++){var decks=new ArrayList<List<CardInstance>>();for(UUID owner:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,ids[i%ids.length]));decks.add(cards);}var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),combat,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->c.definitionId().equals("basic")).count()<2))continue;OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,25);m.drawCardsByEffect(A,B,25);m.endTurn(A);m.endTurn(B);if(List.of(A,B).stream().allMatch(p->Arrays.stream(ids).allMatch(id->m.privateView(p).hand().stream().anyMatch(c->c.definitionId().equals(id)))))return m;}throw new AssertionError("Discard fixture unavailable");
    }
    static ServerMatch aiGame(String id){var m=game("none",NonPokemonBatch2Test.DATA.trainer(id).orElseThrow());for(UUID p:List.of(A,B))m.discardHandCardsByEffect(A,p,List.of(card(m,p,"item").instanceId(),card(m,p,"energy").instanceId()));return m;}
    static void play(ServerMatch m,UUID p){m.playTrainer(p,card(m,p,"trainer").instanceId(),null);}
    public static void main(String[] args){trump();destinations();bench();abilitySource();cost();emptySide();System.out.println("NonPokemonBatch52Test passed: both discard piles, excluded Supporter copies, immutable prohibition, hand versus deck/category/source locks, all destinations and Bench exception, blocked recovery still pays cost and empty opponent discard still shuffles");}
    static void trump(){for(String mode:List.of("none","hand","deck"))for(UUID actor:List.of(A,B)){
        var m=game(mode,NonPokemonBatch2Test.DATA.trainer(LYS).orElseThrow());if(actor.equals(B))m.endTurn(A);var decks=new HashMap<UUID,List<CardInstance>>();var discards=new HashMap<UUID,List<CardInstance>>();var hands=new HashMap<UUID,List<CardInstance>>();
        for(UUID p:List.of(A,B)){var cards=List.of("basic","energy","special","item","tool",LYS,ZONE).stream().map(id->card(m,p,id)).toList();m.discardHandCardsByEffect(actor,p,cards.stream().map(CardInstance::instanceId).toList());decks.put(p,List.copyOf(m.privateView(p).deck()));discards.put(p,cards);hands.put(p,List.copyOf(m.privateView(p).hand()));}
        var source=card(m,actor,"trainer");play(m,actor);NonPokemonBatch2Test.settle(m);
        for(UUID p:List.of(A,B)){var expectedDiscard=new HashSet<CardInstance>();var expectedDeck=new HashSet<>(decks.get(p));for(var c:discards.get(p)){
                boolean blocked=Set.of(LYS,ZONE).contains(c.definitionId())||mode.equals("deck")&&p.equals(actor)&&m.cardDefinition(c.definitionId()).category()==CardCategory.TRAINER;
                if(blocked)expectedDiscard.add(c);else expectedDeck.add(c);
            }if(p.equals(actor))expectedDiscard.add(source);check(new HashSet<>(m.privateView(p).discard()).equals(expectedDiscard)&&new HashSet<>(m.privateView(p).deck()).equals(expectedDeck),"Trump wrong owner/exclusion/recovery lock: "+mode+p);
            var expectedHand=new ArrayList<>(hands.get(p));if(p.equals(actor))expectedHand.remove(source);check(m.privateView(p).hand().equals(expectedHand),"Trump moved hands");
        }
    }}
    static void destinations(){for(String mode:List.of("hand","deck"))for(String destination:List.of("HAND","DECK_TOP","DECK_BOTTOM","DECK_SHUFFLED"))for(String kind:List.of("basic","energy","special","item","tool")){
        var trainer=DeckDiscardTriggeredAbilityTest.trainer("ITEM","{\"type\":\"search_discard_pile\",\"target\":\"DISCARD\",\"amount\":1,\"minimum\":1,\"destination\":\""+destination+"\"}");var m=game(mode,trainer);var selected=card(m,A,kind);m.discardHandCardsByEffect(A,A,List.of(selected.instanceId()));var source=card(m,A,"trainer");
        boolean blocked=mode.equals("hand")?destination.equals("HAND"):!destination.equals("HAND")&&Set.of("item","tool").contains(kind);
        check(m.canPlayTrainer(A,source.instanceId(),null),"Existing recovery target was treated as a play restriction: "+mode+destination+kind);
        if(blocked){play(m,A);NonPokemonBatch2Test.settle(m);check(m.privateView(A).discard().contains(selected)&&m.privateView(A).discard().contains(source),"Blocked recovery moved target or returned played card");}
        else{play(m,A);NonPokemonBatch37Test.choose(m,List.of(selected.instanceId()));NonPokemonBatch2Test.settle(m);check(!m.privateView(A).discard().contains(selected)&&(destination.equals("HAND")?m.privateView(A).hand():m.privateView(A).deck()).contains(selected),"Allowed recovery failed");}
    }}
    static void bench(){for(String mode:List.of("hand","deck")){
        var trainer=DeckDiscardTriggeredAbilityTest.trainer("ITEM","{\"type\":\"search_discard_pile\",\"target\":\"DISCARD\",\"amount\":1,\"minimum\":1,\"card_category\":\"POKEMON\",\"basic_pokemon_only\":true,\"destination\":\"BENCH\"}");var m=game(mode,trainer);var selected=card(m,A,"basic");m.discardHandCardsByEffect(A,A,List.of(selected.instanceId()));int before=m.benchPokemonView(A).size();play(m,A);NonPokemonBatch37Test.choose(m,List.of(selected.instanceId()));NonPokemonBatch2Test.settle(m);check(m.benchPokemonView(A).size()==before+1&&!m.privateView(A).discard().contains(selected),"Hand/deck recovery lock blocked direct Bench placement");
    }}
    static void abilitySource(){for(String mode:List.of("hand","deck"))for(String destination:List.of("HAND","DECK_SHUFFLED")){
        var ability=DeckDiscardTriggeredAbilityTest.active("{\"type\":\"search_discard_pile\",\"target\":\"DISCARD\",\"amount\":1,\"minimum\":1,\"destination\":\""+destination+"\"}");var m=game(mode,NonPokemonBatch2Test.DATA.trainer(LYS).orElseThrow(),ability);var selected=card(m,A,"item");m.discardHandCardsByEffect(A,A,List.of(selected.instanceId()));m.useAbility(A,m.activePokemonView(A).pokemonId(),ability.id());boolean blocked=mode.equals("hand")&&destination.equals("HAND");if(!blocked)NonPokemonBatch37Test.choose(m,List.of(selected.instanceId()));NonPokemonBatch2Test.settle(m);check(m.privateView(A).discard().contains(selected)==blocked,"Sand Screen blocked an Ability or Colony missed hand return");
    }}
    static void cost(){for(boolean empty:List.of(false,true)){
        var m=game("hand",NonPokemonBatch2Test.DATA.trainer("sv2_en_189").orElseThrow());var energy=card(m,A,"energy");if(!empty)m.discardHandCardsByEffect(A,A,List.of(energy.instanceId()));var source=card(m,A,"trainer");int hand=m.privateView(A).hand().size();
        if(empty){check(!m.canPlayTrainer(A,source.instanceId(),null),"Superior Retrieval playable with empty discard");continue;}
        check(m.canPlayTrainer(A,source.instanceId(),null),"Colony incorrectly prohibited paying Retrieval cost");play(m,A);var paid=List.of(card(m,A,"item"),card(m,A,"tool"));NonPokemonBatch37Test.choose(m,paid.stream().map(CardInstance::instanceId).toList());NonPokemonBatch2Test.settle(m);check(m.privateView(A).hand().size()==hand-3&&m.privateView(A).discard().containsAll(paid)&&m.privateView(A).discard().contains(energy),"Colony failed to retain Energy after cost payment");
    }}
    static void emptySide(){var m=game("none",NonPokemonBatch2Test.DATA.trainer(LYS).orElseThrow());m.discardHandCardsByEffect(A,A,List.of(card(m,A,"energy").instanceId()));var untouched=new HashSet<>(m.privateView(B).deck());int random=m.randomTranscript().size();play(m,A);NonPokemonBatch2Test.settle(m);check(new HashSet<>(m.privateView(B).deck()).equals(untouched)&&m.randomTranscript().subList(random,m.randomTranscript().size()).stream().anyMatch(e->e.operation().contains("effect-empty-trump-shuffle:"+B)),"Empty opponent discard did not retain/shuffle its deck");}
}
