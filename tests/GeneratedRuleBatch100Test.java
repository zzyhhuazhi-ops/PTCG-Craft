import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Real Watchog prints: three coins, public opposing-hand window, exact revealed-card return and one shuffle. */
public final class GeneratedRuleBatch100Test {
    static final UUID A=NamedFieldCoinAttackTest.A,B=NamedFieldCoinAttackTest.B;
    static final List<String> PRINTS=List.of("me4_en_071","me4_en_097");
    static final CardDatabase DB=NamedFieldCoinAttackTest.DB;
    static final CombatCatalog DATA=NamedFieldCoinAttackTest.DATA;
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static AttackDefinition attack(String id){return DATA.pokemon(id).orElseThrow().attacks().getFirst();}
    static List<CardInstance> zone(ServerMatch m,UUID owner,String name){return GeneratedRuleBatch96Test.zone(m,owner,name);}
    static void pay(ServerMatch m){NamedFieldCoinAttackTest.pay(m,1);}
    static ServerMatch watchogGame(int seed){
        for(int attempt=seed;attempt<seed+1000;attempt++){
        var defs=new ArrayList<CardDefinition>();var pokemon=new HashMap<String,PokemonProfile>();
        for(String id:List.of("me4_en_070","me4_en_071","sv2_en_028","sm1_en_100")){defs.add(DB.find(id).orElseThrow());pokemon.put(id,DATA.pokemon(id).orElseThrow());}
        defs.add(DB.find("me5_en_074").orElseThrow());
        for(String id:List.of("base","energy"))defs.add(new CardDefinition(id,"Synthetic "+id,id.equals("energy")?CardCategory.ENERGY:CardCategory.POKEMON,TrainerCategory.NONE,"J",Set.of("international"),id.equals("base"),id.equals("energy"),60,Map.of(),EffectSupport.SUPPORTED,null));
        pokemon.put("base",new PokemonProfile(1000,0,null,null,0,null,0,1,List.of(GeneratedRuleBatch8Test.hit(10,false)),List.of(),EnergyType.METAL,"Synthetic base",null));
        var catalog=new CombatCatalog(pokemon,Map.of("energy",EnergyType.COLORLESS),Map.of("me5_en_074",DATA.trainer("me5_en_074").orElseThrow()));
        var pool=new ArrayList<String>();for(int i=0;i<12;i++)pool.add("base");for(int i=0;i<10;i++)pool.add("me4_en_070");for(int i=0;i<10;i++)pool.add("me4_en_071");for(int i=0;i<5;i++)pool.add("sv2_en_028");pool.add("sm1_en_100");pool.add("me5_en_074");while(pool.size()<60)pool.add("energy");
        var decks=new ArrayList<List<CardInstance>>();for(UUID owner:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,pool.get(i)));decks.add(cards);}
        var m=new ServerMatch(A,B,decks.getFirst(),decks.getLast(),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),catalog,new ServerRandom(attempt));
        OpeningTestSupport.decideOrder(m,A,B);var pa=zone(m,A,"hand").stream().filter(c->c.definitionId().equals("me4_en_070")).findFirst();var bb=zone(m,B,"hand").stream().filter(c->c.definitionId().equals("base")).findFirst();if(pa.isEmpty()||bb.isEmpty())continue;
        m.chooseOpeningActive(A,pa.get().instanceId());m.startAfterOpening(A);m.chooseOpeningActive(B,bb.get().instanceId());m.startAfterOpening(B);OpeningTestSupport.complete(m,A,B,0);m.endTurn(A);m.endTurn(B);
        UUID host=m.activePokemonView(A).pokemonId();m.endTurn(A);m.endTurn(B);var evolution=GeneratedRuleBatch96Test.obtain(m,A,"me4_en_071");if(evolution==null)continue;m.evolve(A,host,evolution.instanceId());return m;
        }
        throw new AssertionError("No Watchog evolution fixture");
    }
    static List<RandomAuditEvent> coins(ServerMatch m,int from){return m.randomTranscript().subList(from,m.randomTranscript().size()).stream().filter(e->e.operation().startsWith("attack-effect-coin:")).toList();}
    static int heads(List<RandomAuditEvent> events){return (int)events.stream().filter(e->e.result()==0).count();}
    public static void main(String[] args){printedAndReturn();emptyHand();invalidChoices();copyUsesActualController();System.out.println("GeneratedRuleBatch100Test passed: both real Watchog prints, exact hand return, zero-head/empty-hand boundaries, atomic invalid choices and copied-attack controller coin binding");}
    static void printedAndReturn(){
        for(String id:PRINTS){
            var p=DATA.pokemon(id).orElseThrow();var a=attack(id);
            check(DB.find(id).orElseThrow().effectSupport()==EffectSupport.SUPPORTED&&p.hitPoints()==100&&p.retreatCost()==1&&p.prizeCards()==1&&p.pokemonType()==EnergyType.COLORLESS&&p.evolvesFromName().equals("Patrat")&&p.weaknessType()==EnergyType.FIGHTING&&p.weaknessMultiplier()==2&&p.resistanceType()==null,"Watchog metadata "+id);
            check(p.attacks().size()==2&&a.id().equals("snap-inspection")&&a.energyCost().equals(Map.of(EnergyType.COLORLESS,1))&&a.baseDamage()==0&&p.attacks().get(1).baseDamage()==50&&p.attacks().get(1).energyCost().equals(Map.of(EnergyType.COLORLESS,2)),"Printed Watchog attack contract "+id);
            AttackProgramRules.validate(a);
            boolean sawZero=false,sawHeads=false;
            for(int seed=0;seed<120&&(!sawZero||!sawHeads);seed++){
                var m=watchogGame(seed);pay(m);
                var beforeHand=new ArrayList<>(m.privateView(B).hand());var beforeDeck=new ArrayList<>(m.privateView(B).deck());
                int from=m.randomTranscript().size();m.attack(A,a.id());var flips=coins(m,from);int h=heads(flips);
                check(flips.size()==3,"Snap Inspection must flip exactly three coins");
                if(h==0){sawZero=true;check(m.effectCardReveal()==null&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Zero heads must skip reveal and selection");check(m.privateView(B).hand().size()==beforeHand.size()+1&&m.privateView(B).deck().size()==beforeDeck.size()-1,"Zero heads changed zones beyond the normal next-turn draw");continue;}
                sawHeads=true;var reveal=m.effectCardReveal();check(reveal!=null&&reveal.confirmingPlayer().equals(A),"Positive heads must open public hand reveal");check(reveal.cardIds().equals(beforeHand.stream().map(CardInstance::definitionId).sorted().toList()),"Reveal must show the exact current opposing hand");m.acknowledgeEffectReveal(A,reveal.id());
                var prompt=m.effectCardPrompt(A);int want=Math.min(h,beforeHand.size());check(prompt!=null&&prompt.minimum()==want&&prompt.maximum()==want,"Selection must equal min(heads, hand size)");
                var chosen=prompt.candidates().subList(0,want);var chosenIds=chosen.stream().map(CardInstance::instanceId).toList();int handSize=m.privateView(B).hand().size(),deckSize=m.privateView(B).deck().size();
                m.submitEffectCards(A,prompt.resolutionId(),prompt.step(),chosenIds);
                check(m.privateView(B).hand().size()==handSize-want+1&&m.privateView(B).deck().size()==deckSize+want-1&&m.currentPlayer().equals(B),"Selected physical cards must return to opponent deck after the normal next-turn draw");
                for(UUID card:chosenIds)check(m.privateView(B).deck().stream().anyMatch(c->c.instanceId().equals(card)),"Selected card was not conserved in deck");
                long shuffles=m.randomTranscript().stream().filter(e->e.operation().startsWith("opponent-revealed-hand-return:"+B)).count();check(shuffles>0,"Returning selected cards must shuffle once");
            }
            check(sawZero&&sawHeads,"Need both zero-head and positive-head Watchog outcomes");
        }
    }
    static void emptyHand(){
        var a=attack(PRINTS.getFirst());var m=watchogGame(0);pay(m);var hand=zone(m,B,"hand");var deck=zone(m,B,"deck");deck.addAll(hand);hand.clear();int before=m.randomTranscript().size();m.attack(A,a.id());check(coins(m,before).size()==3&&m.effectCardReveal()==null&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Empty hand still flips three but does not reveal or select");
    }
    static void invalidChoices(){
        var a=attack(PRINTS.getFirst());var m=watchogGame(1);pay(m);m.attack(A,a.id());if(m.effectCardReveal()!=null){m.acknowledgeEffectReveal(A,m.effectCardReveal().id());var p=m.effectCardPrompt(A);if(p!=null&&p.maximum()>0){var rng=m.randomTranscript();var view=m.clientView(A);reject(()->m.submitEffectCards(B,p.resolutionId(),p.step(),p.candidates().stream().map(CardInstance::instanceId).toList()));check(rng.equals(m.randomTranscript())&&view.equals(m.clientView(A)),"Foreign selector changed Watchog state");reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(new UUID(7,7))));}}
    }
    static void copyUsesActualController(){
        var copied=BundledCombatData.attack(JsonParser.parseString("{\"id\":\"hit\",\"nameZh\":\"复制临检\",\"energyCost\":{},\"baseDamage\":0,\"damageType\":\"COLORLESS\",\"effectSupport\":\"SUPPORTED\",\"effects\":[{\"type\":\"coin_flip\",\"flips\":3,\"bind\":\"inspection\",\"effects\":[]}],\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[{\"type\":\"conditional\",\"conditions\":[{\"type\":\"COIN_RESULT\",\"coin\":\"inspection\",\"value\":\"HEADS\"}],\"effects\":[{\"type\":\"reveal_hand\",\"target\":\"OPPONENT_ACTIVE\"},{\"type\":\"opponent_hand_to_deck\",\"target\":\"OPPONENT_ACTIVE\",\"coin\":\"inspection\"}]}]}]}" ).getAsJsonObject(),EffectRegistry.standard());
        var m=CopyAttackTest.fixture(copied);CopyAttackTest.pay(m);var choice=CopyAttackTest.choice(m);var before=m.randomTranscript().size();CopyAttackTest.copy(m,choice,List.of(),List.of());check(coins(m,before).size()==3||m.effectCardReveal()!=null,"Copied Watchog-style attack must use fresh three-coin context");
    }
}
