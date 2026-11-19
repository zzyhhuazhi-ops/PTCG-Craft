import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch54Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final List<String> IDS=List.of("sm9_en_149","sc_csm1_5c_070_sr");
    static final List<String> EN=List.of("sm9_en_137","sm9_en_141","sm9_en_151"),CN=List.of("sc_csm1_5c_071_sr","sc_csm1_5c_072_sr","sc_csm1_5c_073_sr");
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static CardInstance card(ServerMatch m,UUID p,String id){return DynamicPassiveModifierTest.hand(m,p,id);}
    static ServerMatch aiGame(String id){return game(id,false);}
    static ServerMatch game(String id,boolean cn){
        var trainer=NonPokemonBatch2Test.DATA.trainer(id).orElseThrow();var sisters=cn?CN:EN;
        String[] ids={"basic","basic","energy","energy","special","special","trainer",sisters.get(0),sisters.get(1),sisters.get(2)};
        var definitions=new ArrayList<CardDefinition>();var profiles=new HashMap<String,TrainerProfile>();profiles.put("trainer",trainer);for(String s:sisters)profiles.put(s,NonPokemonBatch2Test.DATA.trainer(s).orElseThrow());
        for(String key:Arrays.stream(ids).distinct().toList()){boolean pokemon=key.equals("basic"),energy=Set.of("energy","special").contains(key);definitions.add(new CardDefinition(key,key,pokemon?CardCategory.POKEMON:energy?CardCategory.ENERGY:CardCategory.TRAINER,pokemon||energy?TrainerCategory.NONE:TrainerCategory.SUPPORTER,"H",Set.of("international"),pokemon,key.equals("energy"),60,Map.of(),EffectSupport.SUPPORTED,null));}
        var combat=new CombatCatalog(Map.of("basic",new PokemonProfile(500,0,null,null,0,null,0,1,List.of(GeneratedRuleBatch8Test.hit(100,false)),List.of(),EnergyType.GRASS)),Map.of("energy",EnergyType.GRASS),profiles,Map.of("special",EnergyProfile.fixed(EnergyType.COLORLESS,2)));
        for(int seed=0;seed<500;seed++){var decks=new ArrayList<List<CardInstance>>();for(UUID p:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(p.getLeastSignificantBits(),i+1),p,ids[i%ids.length]));decks.add(cards);}var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(definitions),combat,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->c.definitionId().equals("basic")).count()<2))continue;OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,23);m.drawCardsByEffect(A,B,23);m.endTurn(A);m.endTurn(B);if(List.of(A,B).stream().allMatch(p->sisters.stream().allMatch(s->m.privateView(p).hand().stream().anyMatch(c->c.definitionId().equals(s)))))return m;}throw new AssertionError("Morgan fixture unavailable");
    }
    static void choose(ServerMatch m,UUID p,List<CardInstance> cards){var q=m.effectCardPrompt(p);m.submitEffectCards(p,q.resolutionId(),q.step(),cards.stream().map(CardInstance::instanceId).toList());}
    static void top(ServerMatch m,UUID owner,CardInstance c){m.discardHandCardsByEffect(owner,owner,List.of(c.instanceId()));m.recoverDiscardCardsByEffect(owner,owner,new ServerMatch.CardSelectionPolicy(1,1,null,null,false,Set.of()),List.of(c.instanceId()),CardEffectDestination.DECK_TOP);}
    static boolean energy(ServerMatch m,CardInstance c){return m.cardDefinition(c.definitionId()).category()==CardCategory.ENERGY;}
    public static void main(String[] args){windows();missing();System.out.println("NonPokemonBatch54Test passed: EN/CN source and cost identities, three distinct atomic payments, no early deck inspection, private top-12/short-deck windows, basic and special Energy distributed to own Active/Bench, optional decline and mandatory shuffle even when every inspected card attaches");}
    static void windows(){for(String id:IDS)for(boolean cn:List.of(false,true))for(UUID owner:List.of(A,B))for(int size:List.of(3,12))for(boolean take:List.of(false,true))for(boolean allEnergy:List.of(false,true)){
        var m=game(id,cn);if(owner.equals(B))m.endTurn(A);UUID other=owner.equals(A)?B:A;var sisters=cn?CN:EN;
        m.drawCardsByEffect(owner,owner,m.privateView(owner).deck().size());var source=card(m,owner,"trainer");var paid=sisters.stream().map(s->card(m,owner,s)).toList();
        var duplicate=m.privateView(owner).hand().stream().filter(c->c.definitionId().equals(sisters.getFirst())).limit(2).toList();check(duplicate.size()==2,"Missing duplicate cost test fixture");
        var available=m.privateView(owner).hand().stream().filter(c->!c.equals(source)&&!paid.contains(c)&&!duplicate.contains(c)).toList();var energies=available.stream().filter(c->energy(m,c)).toList();var others=available.stream().filter(c->!energy(m,c)).toList();
        var outside=energies.getLast();if(size==12)top(m,owner,outside);
        var window=new ArrayList<CardInstance>();for(int i=0;i<size;i++)window.add(allEnergy||i%2==0?energies.get(i):others.get(i/2));for(var c:window)top(m,owner,c);
        var beforeDeck=List.copyOf(m.privateView(owner).deck());var otherDeck=List.copyOf(m.privateView(other).deck());int random=m.randomTranscript().size();m.playTrainer(owner,source.instanceId(),null);var cost=m.effectCardPrompt(owner);
        check(cost.inspectedCards().isEmpty()&&cost.minimum()==3&&cost.maximum()==3&&cost.candidates().stream().allMatch(c->sisters.contains(c.definitionId())),"Morgan inspected deck before payment or offered wrong identities");
        var wrong=List.of(duplicate.get(0),duplicate.get(1),paid.get(2));GeneratedRuleBatch8Test.reject(()->choose(m,owner,wrong));check(m.privateView(owner).hand().containsAll(paid)&&m.privateView(owner).deck().equals(beforeDeck),"Duplicate cost partially committed or inspected deck");
        choose(m,owner,paid);var q=m.effectCardPrompt(owner);var candidates=window.stream().filter(c->energy(m,c)).toList();check(new HashSet<>(q.candidates()).equals(new HashSet<>(candidates))&&new HashSet<>(q.inspectedCards()).equals(new HashSet<>(window))&&q.minimum()==0,"Morgan filtered before taking window or lost special Energy");GeneratedRuleBatch8Test.reject(()->m.effectCardPrompt(other));
        var selected=take?candidates:List.<CardInstance>of();choose(m,owner,selected);if(take){var assignment=m.effectAttachmentPrompt(owner);var hosts=List.of(m.activePokemonView(owner).pokemonId(),m.benchPokemonView(owner).getFirst().pokemonId());for(int i=0;i<selected.size();i++)m.assignEffectEnergyDistribution(owner,assignment.resolutionId(),assignment.step(),selected.get(i).instanceId(),hosts.get(i%2));m.confirmEffectEnergyDistribution(owner,assignment.resolutionId(),assignment.step());}NonPokemonBatch2Test.settle(m);
        var attached=new HashSet<>(m.activePokemonView(owner).attachedEnergyIds());attached.addAll(m.benchPokemonView(owner).getFirst().attachedEnergyIds());check(attached.equals(new HashSet<>(selected.stream().map(CardInstance::instanceId).toList())),"Morgan attached wrong identities/number");
        var remaining=new HashSet<>(beforeDeck);remaining.removeAll(selected);check(new HashSet<>(m.privateView(owner).deck()).equals(remaining)&&m.privateView(other).deck().equals(otherDeck),"Morgan moved remainder or opponent deck incorrectly");check(m.privateView(owner).discard().containsAll(paid)&&m.privateView(owner).discard().contains(source)&&!m.energyAttached(owner),"Morgan missed payment/source discard or used manual Energy attachment");check(m.replayTranscript().stream().anyMatch(e->e.operation().equals("牌库顶附能结束，剩余牌已洗回牌库")),"Morgan did not finish shuffle with zero/one remaining card");if(remaining.size()>1)check(m.randomTranscript().subList(random,m.randomTranscript().size()).stream().anyMatch(e->e.operation().startsWith("effect-top-energy-remainder:")),"Morgan did not shuffle after attachment/decline");
    }}
    static void missing(){for(boolean cn:List.of(false,true))for(int absent=0;absent<3;absent++){
        var m=game(IDS.getFirst(),cn);var sisters=cn?CN:EN;String missing=sisters.get(absent);m.discardHandCardsByEffect(A,A,m.privateView(A).hand().stream().filter(c->c.definitionId().equals(missing)).map(CardInstance::instanceId).toList());var source=card(m,A,"trainer");check(!m.canPlayTrainer(A,source.instanceId(),null),"Repeated sisters replaced missing identity");GeneratedRuleBatch8Test.reject(()->m.playTrainer(A,source.instanceId(),null));check(m.privateView(A).hand().contains(source),"Rejected Morgan consumed source");
    }}
}
