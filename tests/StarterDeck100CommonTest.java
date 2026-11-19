import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Real SD100 cards: shared Trainer effects and Energy-count attacks, including decline and forged requests. */
public final class StarterDeck100CommonTest {
    static final UUID A=StarterDeck100CombatTest.A, B=StarterDeck100CombatTest.B;
    static final String ENERGY="sd100_cn_grass_energy";
    static void check(boolean ok,String text){StarterDeck100CombatTest.check(ok,text);}
    static void rejects(Runnable action){TopDeckLookTest.rejects(action);}
    static String id(int n){return "sd100_cn_%03d".formatted(n);}
    public static void main(String[] args) {
        teamYell(); recover(368); recover(392); levelBall(); gordie(); copycat(false); copycat(true);
        escapeRope(0); escapeRope(1); school(400); school(401); attacks(343,false); attacks(343,true); attacks(301,false); attacks(301,true);
        malformed();
        System.out.println("StarterDeck100CommonTest passed: recovery/filter unions, HP search, Energy look, hand-count draw, switch order, prizes and Energy-unit damage");
    }
    static ServerMatch game(int trainer,int benches,String... extras) {
        var entries=new ArrayList<String>(List.of(id(1),id(trainer),ENERGY)); entries.addAll(List.of(extras));
        for(int seed=0;seed<500;seed++) {
            var deck=new ArrayList<CardInstance>();for(int i=0;i<60;i++)deck.add(StarterDeck100CombatTest.card(A,entries.get(i%entries.size())));
            var m=new ServerMatch(A,B,deck,StarterDeck100CombatTest.syntheticDeck(B,id(1),null),StarterDeck100CombatTest.RULE,StarterDeck100CombatTest.CARDS,StarterDeck100CombatTest.COMBAT,new ServerRandom(seed));
            OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->m.cardDefinition(c.definitionId()).basicPokemon()).count()<benches+1))continue;
            OpeningTestSupport.complete(m,A,B,benches);m.endTurn(A);m.endTurn(B);
            if(m.privateView(A).hand().stream().anyMatch(c->c.definitionId().equals(id(trainer))))return m;
        }
        throw new AssertionError("No deterministic Trainer fixture");
    }
    static void play(ServerMatch m,int trainer){m.playTrainer(A,StarterDeck100CombatTest.hand(m,id(trainer)).instanceId(),null);}
    static void choose(ServerMatch m,List<CardInstance> cards){var p=m.effectCardPrompt(A);m.submitEffectCards(A,p.resolutionId(),p.step(),cards.stream().map(CardInstance::instanceId).toList());if(m.effectCardReveal()!=null)m.acknowledgeEffectReveal(B,m.effectCardReveal().id());}
    static void teamYell(){
        var m=game(388,0,id(398),id(373));m.discardTopDeckCardsByEffect(A,A,25);var before=m.privateView(A).deck();play(m,388);var p=m.effectCardPrompt(A);
        check(p.maximum()==3&&p.candidates().stream().anyMatch(c->c.definitionId().equals(id(398)))&&p.candidates().stream().anyMatch(c->c.definitionId().equals(id(1))),"mixed categories missing");
        check(p.candidates().stream().noneMatch(c->Set.of(id(388),id(373),ENERGY).contains(c.definitionId())),"self/Item/Energy incorrectly recoverable");
        var excluded=m.privateView(A).discard().stream().filter(c->c.definitionId().equals(id(388))).findFirst().orElseThrow();
        rejects(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(excluded.instanceId())));
        rejects(()->m.submitEffectCards(A,p.resolutionId(),p.step(),p.candidates().stream().limit(4).map(CardInstance::instanceId).toList()));
        var picked=new ArrayList<CardInstance>();picked.add(p.candidates().stream().filter(c->c.definitionId().equals(id(398))).findFirst().orElseThrow());picked.addAll(p.candidates().stream().filter(c->c.definitionId().equals(id(1))).limit(2).toList());
        int random=m.randomTranscript().size();choose(m,picked);
        check(m.privateView(A).deck().size()==before.size()+3&&m.privateView(A).deck().containsAll(picked)&&m.randomTranscript().size()>random,"mixed recovery did not shuffle exactly three");
    }
    static void recover(int trainer){
        var m=game(trainer,0);m.discardTopDeckCardsByEffect(A,A,24);int hand=m.privateView(A).hand().size();play(m,trainer);
        var first=m.effectCardPrompt(A);var picked=first.candidates().stream().limit(trainer==368?5:2).toList();
        check(first.candidates().stream().allMatch(c->trainer==368?m.cardDefinition(c.definitionId()).basicEnergy():m.cardDefinition(c.definitionId()).category()==CardCategory.POKEMON),"wrong recovery filter");
        choose(m,picked);
        if(trainer==392){var second=m.effectCardPrompt(A);check(second.maximum()==2&&second.candidates().stream().allMatch(c->m.cardDefinition(c.definitionId()).basicEnergy()),"Klara Energy half wrong");choose(m,second.candidates().stream().limit(2).toList());check(m.privateView(A).hand().size()==hand+3,"Klara failed to recover two of each");}
        else check(m.privateView(A).deck().containsAll(picked),"Recycler did not recover to deck");
        check(!m.hasPendingEffectChoice(),"recovery did not finish");
    }
    static void levelBall(){
        String boundary=StarterDeck100CombatTest.CARDS.all().stream().filter(c->c.id().startsWith("sd100_cn_")&&c.effectSupport()==EffectSupport.SUPPORTED&&c.category()==CardCategory.POKEMON&&StarterDeck100CombatTest.COMBAT.pokemon(c.id()).orElseThrow().hitPoints()==90).findFirst().orElseThrow().id();
        var m=game(381,0,boundary,id(5));m.placeDamageCountersByEffect(A,A,m.activePokemonView(A).pokemonId(),1);play(m,381);var p=m.effectCardPrompt(A);
        check(p.candidates().stream().anyMatch(c->c.definitionId().equals(boundary))&&p.candidates().stream().allMatch(c->StarterDeck100CombatTest.COMBAT.pokemon(c.definitionId()).orElseThrow().hitPoints()<=90),"Level Ball HP boundary wrong");
        var invalid=m.privateView(A).deck().stream().filter(c->c.definitionId().equals(id(1))).findFirst().orElseThrow();rejects(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(invalid.instanceId())));choose(m,List.of(p.candidates().getFirst()));
    }
    static void gordie(){var m=game(407,0);var top=TopDeckLookTest.top(m);play(m,407);var p=m.effectCardPrompt(A);check(new HashSet<>(p.inspectedCards()).equals(new HashSet<>(top))&&p.maximum()==7,"Gordie wrong window");check(p.candidates().stream().allMatch(c->m.cardDefinition(c.definitionId()).category()==CardCategory.ENERGY),"Gordie includes non-Energy");var selected=p.candidates();choose(m,selected);check(m.privateView(A).hand().containsAll(selected),"Gordie failed transfer");}
    static void copycat(boolean empty){var m=game(410,0);if(empty)m.discardHandCardsByEffect(A,B,m.privateView(B).hand().stream().map(CardInstance::instanceId).toList());int expected=m.privateView(B).hand().size();var opponent=m.privateView(B);play(m,410);check(m.privateView(A).hand().size()==expected&&m.privateView(B).equals(opponent),"Copycat wrong amount or modified opponent");}
    static void escapeRope(int benches){var m=game(364,benches);if(benches==0){var before=m.privateView(A);rejects(()->play(m,364));check(before.equals(m.privateView(A)),"no-bench Rope consumed");return;}var oldA=m.activePokemonView(A).pokemonId();var oldB=m.activePokemonView(B).pokemonId();play(m,364);var p=m.effectPokemonPrompt(B);check(p!=null&&m.activePokemonView(A).pokemonId().equals(oldA),"actor switched before opponent");rejects(()->m.chooseEffectPokemon(A,p.resolutionId(),p.step(),m.benchPokemonView(B).getFirst().pokemonId()));m.chooseEffectPokemon(B,p.resolutionId(),p.step(),m.benchPokemonView(B).getFirst().pokemonId());var q=m.effectPokemonPrompt(A);m.chooseEffectPokemon(A,q.resolutionId(),q.step(),m.benchPokemonView(A).getFirst().pokemonId());check(!oldA.equals(m.activePokemonView(A).pokemonId())&&!oldB.equals(m.activePokemonView(B).pokemonId())&&!m.hasPendingEffectChoice(),"Rope switches incomplete");}
    static void school(int trainer){var m=game(trainer,0);int before=m.privateView(A).hand().size();play(m,trainer);check(m.privateView(A).hand().size()==before-1+(trainer==400?4:2),"Schoolkid six-prize branch wrong");}
    static void attacks(int pokemon,boolean extra){
        var own=new ArrayList<CardInstance>();for(int i=0;i<60;i++)own.add(StarterDeck100CombatTest.card(A,i<20?id(pokemon):"sd100_cn_metal_energy"));
        var m=new ServerMatch(A,B,own,StarterDeck100CombatTest.syntheticDeck(B,id(1),null),StarterDeck100CombatTest.RULE,StarterDeck100CombatTest.CARDS,StarterDeck100CombatTest.COMBAT,new ServerRandom(31));OpeningTestSupport.complete(m,A,B,0);m.endTurn(A);m.endTurn(B);
        for(int i=0;i<2;i++){var energy=m.privateView(A).deck().stream().filter(c->c.definitionId().equals("sd100_cn_metal_energy")).findFirst().orElseThrow();m.attachEnergyByEffect(A,A,EffectSourceZone.DECK,energy.instanceId(),A,m.activePokemonView(A).pokemonId());}
        for(int i=0;i<(extra?3:2);i++){var energy=m.privateView(B).deck().stream().filter(c->c.definitionId().equals(ENERGY)).findFirst().orElseThrow();m.attachEnergyByEffect(A,B,EffectSourceZone.DECK,energy.instanceId(),B,m.activePokemonView(B).pokemonId());}
        m.attack(A,pokemon==343?"wild-dive":"synchro-hammer");check(m.lastAttack().finalDamage()==(pokemon==343?(extra?90:60):(extra?60:150)),"Energy-count attack incorrect");
    }
    static void malformed(){var registry=EffectRegistry.standard();for(String raw:List.of(
        "{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":1,\"minimum\":0,\"hp_max\":90}",
        "{\"type\":\"search_discard_pile\",\"target\":\"DISCARD\",\"amount\":3,\"minimum\":0,\"destination\":\"HAND\",\"card_kinds\":\"POKEMON|UNKNOWN\"}",
        "{\"type\":\"draw\",\"amount\":1,\"count_type\":\"ENERGY_COUNT\",\"count_target\":\"OPPONENT_ACTIVE\"}"))rejects(()->{var e=registry.parse(com.google.gson.JsonParser.parseString(raw).getAsJsonObject());if(ZoneSelectionEffect.handles(e))ZoneSelectionEffect.from(e);else EffectSchemaValidator.validate(e);});}
}
