import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.view.*;
import java.util.*;

/** A pair is an atomic declared use, not two plays or an activation discard cost. */
public final class PairedTrainerPlayTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B,WATCH=new UUID(0,9000);
    static final String REPLACE="{\"type\":\"replace_basic_from_discard\",\"target\":\"CHOSEN_POKEMON\",\"amount\":1,\"minimum\":1,\"card_category\":\"POKEMON\",\"basic_pokemon_only\":true,\"preserve_state\":\"ALL\",\"return_old_card\":\"DISCARD\"}";
    static final String SWAP="{\"type\":\"select_pokemon\",\"target\":\"SELECT_OWN_POKEMON\",\"stage\":\"BASIC\",\"effects\":["+REPLACE+"]}";
    static final String CHOICE="{\"type\":\"choice\",\"options\":[{\"type\":\"draw\",\"amount\":1},{\"type\":\"draw\",\"amount\":2}]}";
    static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    static void reject(Runnable r){FossilFormTest.reject(r);}
    static JsonObject json(String id,String effects){return JsonParser.parseString("{\"id\":\""+id+"\",\"trainerCategory\":\"ITEM\",\"pairedPlay\":{\"count\":2,\"definitionIds\":[\"pair-a\",\"pair-b\"]},\"effects\":["+effects+"]}").getAsJsonObject();}
    static TrainerProfile trainer(String id,String effects){return BundledCombatData.trainer(json(id,effects),EffectRegistry.standard());}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch53Test.hand(m,p,id);}
    static List<CardInstance> pair(ServerMatch m){return List.of(hand(m,A,"pair-a"),hand(m,A,"pair-b"));}
    static List<UUID> ids(List<CardInstance> cards){return cards.stream().map(CardInstance::instanceId).toList();}
    static List<CardInstance> zone(ServerMatch m,UUID p,String zone){return GeneratedRuleBatch49Test.zone(m,p,zone);}
    static void toDiscard(ServerMatch m,String id){var c=hand(m,A,id);zone(m,A,"hand").remove(c);zone(m,A,"discard").add(c);}
    static PokemonProfile profile(int hp,String id,String from,List<AbilityDefinition> abilities,PokemonPlacementRule placement){return new PokemonProfile(hp,1,from,null,0,null,0,1,List.of(DynamicPassiveModifierTest.attack(0,false,false)),abilities,EnergyType.COLORLESS,id,from,placement);}
    static ServerMatch fixture(String effects){for(int seed=0;seed<300;seed++){var m=fixture(effects,seed);if(m!=null)return m;}throw new AssertionError("fixture");}
    static ServerMatch fixture(String effects,int seed){
        String[] names={"base","base","high","blank","small","energy","pair-a","pair-b","ordinary","evolved","restricted","tool","fossil"};
        var defs=new ArrayList<CardDefinition>();for(var id:new LinkedHashSet<>(List.of(names))){boolean poke=Set.of("base","high","blank","small","evolved","restricted").contains(id);defs.add(new CardDefinition(id,id,poke?CardCategory.POKEMON:id.equals("energy")?CardCategory.ENERGY:CardCategory.TRAINER,poke||id.equals("energy")?TrainerCategory.NONE:id.equals("tool")?TrainerCategory.TOOL:TrainerCategory.ITEM,"J",Set.of("international"),poke&&!id.equals("evolved"),id.equals("energy"),60,Map.of(),EffectSupport.SUPPORTED,null));}
        var watcher=BundledCombatData.ability(JsonParser.parseString("{\"id\":\"pair-watch\",\"nameZh\":\"Pair watch fixture\",\"kind\":\"TRIGGERED\",\"effectSupport\":\"SUPPORTED\",\"eventPolicy\":{\"trigger\":\"ON_CARD_PLAYED\",\"player\":\"SELF\",\"card\":\"ITEM\",\"optional\":false,\"usage\":\"NONE\"},\"triggers\":[{\"trigger\":\"ON_CARD_PLAYED\",\"effects\":[{\"type\":\"draw\",\"amount\":1}]}]}").getAsJsonObject(),EffectRegistry.standard());
        var profiles=new HashMap<String,PokemonProfile>();profiles.put("base",profile(300,"base",null,List.of(GeneratedRuleBatch53Test.used("1"),watcher),PokemonPlacementRule.ANY_EFFECT));
        profiles.put("high",profile(400,"high",null,List.of(GeneratedRuleBatch53Test.used("2"),ResumableAbilityTest.ability("different","{\"type\":\"draw\",\"amount\":1}")),PokemonPlacementRule.ANY_EFFECT));
        profiles.put("blank",profile(300,"blank",null,List.of(),PokemonPlacementRule.ANY_EFFECT));profiles.put("small",profile(10,"small",null,List.of(),PokemonPlacementRule.ANY_EFFECT));profiles.put("evolved",profile(500,"evolved","base",List.of(),PokemonPlacementRule.ANY_EFFECT));profiles.put("restricted",profile(300,"restricted",null,List.of(),PokemonPlacementRule.ZERO_TO_HERO_ONLY));
        var trainers=Map.of("pair-a",trainer("pair-a",effects),"pair-b",trainer("pair-b",effects),"ordinary",new TrainerProfile(TrainerCategory.ITEM,new TrainerEffect(TrainerEffect.Opcode.DRAW,1,0)),"tool",DynamicPassiveModifierTest.plainTool(),"fossil",FossilFormTest.fossil());
        var decks=new ArrayList<List<CardInstance>>();for(UUID p:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(p.getLeastSignificantBits(),i+1),p,names[i%names.length]));decks.add(cards);}
        var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),new CombatCatalog(profiles,Map.of("energy",EnergyType.COLORLESS),trainers),new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
        for(UUID p:List.of(A,B)){var c=m.privateView(p).hand().stream().filter(x->x.definitionId().equals("base")).findFirst();if(c.isEmpty())return null;m.chooseOpeningActive(p,c.get().instanceId());m.startAfterOpening(p);}OpeningTestSupport.complete(m,A,B,0);
        m.drawCardsByEffect(A,A,20);m.drawCardsByEffect(A,B,20);m.playBasicPokemonToBench(A,hand(m,A,"high").instanceId());m.endTurn(A);m.playBasicPokemonToBench(B,hand(m,B,"high").instanceId());m.endTurn(B);return m;
    }
    static void finishChoice(ServerMatch m){var p=m.effectChoicePrompt(A);m.chooseEffectOption(A,p.resolutionId(),p.step(),0);}
    static void setGate(ServerMatch m){try{var f=ServerMatch.class.getDeclaredField("players");f.setAccessible(true);var p=((Map<?,?>)f.get(m)).get(A);var g=p.getClass().getDeclaredField("trainerAttemptCoinGated");g.setAccessible(true);g.setBoolean(p,true);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    public static void main(String[] args){schema();atomic();projection();session();gate();concedePair();System.out.println("PairedTrainerPlayTest passed: strict metadata, atomic two-card use, projected hand, one completion, public sources, session authority and one attempt coin");}
    static void schema(){
        check(trainer("pair-a",CHOICE).pairedPlay().count()==2,"pair metadata lost");
        for(String metadata:List.of("null","{}","{\"count\":1,\"definitionIds\":[\"pair-a\"]}","{\"count\":2.0,\"definitionIds\":[\"pair-a\"]}","{\"count\":\"2\",\"definitionIds\":[\"pair-a\"]}","{\"count\":2,\"definitionIds\":[]}","{\"count\":2,\"definitionIds\":[\"pair-a\",\"pair-a\"]}","{\"count\":2,\"definitionIds\":[\"other\"]}","{\"count\":2,\"definitionIds\":[\"pair-a\"],\"extra\":true}")){var j=json("pair-a",CHOICE);j.add("pairedPlay",JsonParser.parseString(metadata));reject(()->BundledCombatData.trainer(j,EffectRegistry.standard()));}
        for(String category:List.of("SUPPORTER","TOOL","STADIUM")){var j=json("pair-a",CHOICE);j.addProperty("trainerCategory",category);reject(()->BundledCombatData.trainer(j,EffectRegistry.standard()));}
        var ended=json("pair-a",CHOICE);ended.addProperty("endsTurn",true);reject(()->BundledCombatData.trainer(ended,EffectRegistry.standard()));
        check(new TrainerProfile(TrainerCategory.ITEM,new TrainerEffect(TrainerEffect.Opcode.DRAW,1,0)).pairedPlay()==null,"legacy constructor changed");
    }
    static void atomic(){var m=fixture(CHOICE);var cards=pair(m);var ids=ids(cards);var before=m.clientView(A);var rng=m.randomTranscript();
        for(var bad:List.of(List.<UUID>of(),List.of(ids.getFirst()),List.of(ids.getFirst(),ids.getFirst()),List.of(ids.getFirst(),hand(m,B,"pair-b").instanceId()),List.of(ids.getFirst(),hand(m,A,"ordinary").instanceId()),List.of(ids.getFirst(),ids.getLast(),UUID.randomUUID()))){check(!m.canPlayTrainerPair(A,bad),"invalid pair can play");reject(()->m.playTrainerPair(A,bad));}
        reject(()->m.playTrainer(A,ids.getFirst(),null));check(!m.canPlayTrainer(A,ids.getFirst(),null),"single route exposed pair");check(before.equals(m.clientView(A))&&rng.equals(m.randomTranscript()),"invalid pair mutated/RNG");
        int held=m.privateView(A).hand().size();m.playTrainerPair(A,ids);check(m.privateView(A).hand().size()==held-2&&m.clientView(B).resolvingTrainerCards().equals(cards)&&cards.stream().noneMatch(m.privateView(A).discard()::contains),"pair sources not held in resolution");
        check(!GeneratedRuleBatch53Test.history(m,"handPlays").contains("pair-a"),"history before complete");var prompt=m.effectChoicePrompt(A);reject(()->m.chooseEffectOption(B,prompt.resolutionId(),prompt.step(),0));finishChoice(m);
        check(m.privateView(A).discard().containsAll(cards)&&m.privateView(A).hand().size()==held,"effect or Item listener duplicated");check(GeneratedRuleBatch53Test.history(m,"handPlays").containsAll(List.of("pair-a","pair-b")),"both physical definitions not recorded");
        check(m.replayTranscript().stream().filter(e->e.operation().contains("结算触发特性：pair-watch")).count()==1,"more than one logical completion event");check(m.clientView(A).resolvingTrainerCards().isEmpty(),"pair public sources leaked after completion");
    }
    static void projection(){var m=fixture("{\"type\":\"draw_until\",\"amount\":1}");var cards=pair(m);var hand=zone(m,A,"hand");var other=new ArrayList<>(hand);other.removeAll(cards);hand.removeAll(other);zone(m,A,"discard").addAll(other);check(m.canPlayTrainerPair(A,ids(cards)),"preflight counted a resolving pair card as hand");m.playTrainerPair(A,ids(cards));check(m.privateView(A).hand().size()==2,"pair draw-until/listener count wrong");}
    static void session(){for(int n:List.of(2,3,4)){var m=fixture(CHOICE);var own=zone(m,A,"hand");var paired=own.stream().filter(c->c.definitionId().startsWith("pair-")).toList();check(paired.size()>=n,"fixture insufficient pairs");var extra=paired.subList(n,paired.size());own.removeAll(extra);zone(m,A,"discard").addAll(extra);var session=MatchSession.multiplayer(m,A,B);session.addSpectator(WATCH);var view=session.view(A,A);var options=view.options().stream().filter(o->o.labelZh().contains("×2")).toList();check(options.size()==n*(n-1)/2,"pair combinations not unique");for(var o:options){check(o.cardKeys().size()==2&&o.labelZh().startsWith("使用："),"pair UI anchors/arena prefix wrong");check(MatchActionMatcher.forCards(view,List.of(o.cardKeys().get(1),o.cardKeys().get(0))).equals(List.of(o)),"exact hand-to-hand drop not unique");}
        var action=options.getFirst();var before=m.clientView(A);reject(()->session.submit(B,A,view.revision(),action.index()));reject(()->session.submit(WATCH,A,view.revision(),action.index()));check(before.equals(m.clientView(A)),"foreign authority mutated");session.submit(A,A,view.revision(),action.index());for(UUID viewer:List.of(A,B,WATCH)){var v=session.view(viewer,viewer.equals(WATCH)?null:viewer);check(v.game().resolvingTrainerCards().size()==2&&MatchRegionModel.from(v).zone("resolving").cards().size()==2,"public pair display incomplete");}reject(()->session.submit(A,A,view.revision(),action.index()));m.concede(A);
    }}
    static void concedePair(){
        for(boolean discardPrompt:List.of(false,true))for(UUID loser:List.of(A,B))for(boolean foreignOwner:List.of(false,true)){
            var m=fixture(SWAP);toDiscard(m,"high");var cards=pair(m);
            if(foreignOwner){var own=cards.getLast();var foreign=hand(m,B,"pair-b");zone(m,A,"hand").remove(own);zone(m,A,"discard").add(own);zone(m,B,"hand").remove(foreign);zone(m,A,"hand").add(foreign);cards=List.of(cards.getFirst(),foreign);}
            int handSize=m.privateView(A).hand().size();var deckA=List.copyOf(zone(m,A,"deck"));var deckB=List.copyOf(zone(m,B,"deck"));
            var host=m.activePokemonView(A);var history=Set.copyOf(GeneratedRuleBatch53Test.history(m,"handPlays"));
            long events=m.replayTranscript().stream().filter(e->e.operation().contains("结算触发特性：pair-watch")).count();
            m.playTrainerPair(A,ids(cards));
            if(discardPrompt){var prompt=m.effectPokemonPrompt(A);m.chooseEffectPokemon(A,prompt.resolutionId(),prompt.step(),host.pokemonId());check(m.effectCardPrompt(A)!=null,"missing discard prompt");}
            else check(m.effectPokemonPrompt(A)!=null,"missing Basic prompt");
            m.concede(loser);
            check(m.phase()==MatchPhase.FINISHED&&m.clientView(A).resolvingTrainerCards().isEmpty(),"concession did not clear paired resolution");
            for(var card:cards){for(UUID owner:List.of(A,B)){long count=m.privateView(owner).discard().stream().filter(c->c.instanceId().equals(card.instanceId())).count();check(count==(owner.equals(card.ownerId())?1:0),"concession lost/duplicated paired card or changed actual owner");check(m.privateView(owner).hand().stream().noneMatch(c->c.instanceId().equals(card.instanceId())),"concession refunded paired card");}}
            check(m.privateView(A).hand().size()==handSize-2&&deckA.equals(zone(m,A,"deck"))&&deckB.equals(zone(m,B,"deck")),"concession triggered draw/completion");
            check(m.activePokemonView(A).equals(host)&&GeneratedRuleBatch53Test.history(m,"handPlays").equals(history),"aborted pair replaced host or recorded completed history");
            check(m.replayTranscript().stream().filter(e->e.operation().contains("结算触发特性：pair-watch")).count()==events,"aborted pair emitted completion event");
        }
    }
    static void gate(){var outcomes=new HashSet<Integer>();for(int seed=0;seed<80&&outcomes.size()<2;seed++){var m=fixture(CHOICE,seed);if(m==null)continue;var cards=pair(m);setGate(m);int rng=m.randomTranscript().size();m.playTrainerPair(A,ids(cards));check(m.randomTranscript().size()==rng+1,"pair rolled more than once");int result=m.randomTranscript().getLast().result();outcomes.add(result);if(result==0){var p=m.effectChoicePrompt(A);reject(()->m.chooseEffectOption(A,p.resolutionId(),p.step(),99));finishChoice(m);check(m.randomTranscript().size()==rng+1,"resume rerolled gate");}else check(!m.hasPendingEffectChoice()&&m.privateView(A).discard().containsAll(cards)&&!GeneratedRuleBatch53Test.history(m,"handPlays").contains("pair-a"),"tails did not discard both without use");}check(outcomes.size()==2,"both gate outcomes not covered");}
}
