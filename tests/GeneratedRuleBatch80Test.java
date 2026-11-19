import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Actual Octillery: printed costs/evolution and retained self-Confusion. */
public final class GeneratedRuleBatch80Test {
    static final UUID A=new UUID(0,8011),B=new UUID(0,8012);
    static final String BASIC="me4_en_016",CARD="me4_en_017";
    static final CardDatabase DB=BundledRules.loadCardDefinitions();static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean b,String s){GeneratedRuleBatch8Test.check(b,s);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch13Test.hand(m,p,id);}
    static ServerMatch game(){
        var defs=new ArrayList<CardDefinition>();var profiles=new HashMap<String,PokemonProfile>();
        for(String id:List.of(BASIC,CARD)){defs.add(DB.find(id).orElseThrow());profiles.put(id,DATA.pokemon(id).orElseThrow());}
        for(String id:List.of("base","water","wrong"))defs.add(new CardDefinition(id,id,id.equals("base")?CardCategory.POKEMON:CardCategory.ENERGY,TrainerCategory.NONE,"J",Set.of("international"),id.equals("base"),!id.equals("base"),60,Map.of(),EffectSupport.SUPPORTED,null));
        var strike=BundledCombatData.attack(JsonParser.parseString("{\"id\":\"strike\",\"nameZh\":\"Strike\",\"energyCost\":{\"COLORLESS\":1},\"baseDamage\":20,\"damageType\":\"COLORLESS\",\"effectSupport\":\"SUPPORTED\"}").getAsJsonObject(),EffectRegistry.standard());
        profiles.put("base",new PokemonProfile(1000,1,null,null,0,null,0,1,List.of(strike),List.of(),EnergyType.COLORLESS));var catalog=new CombatCatalog(profiles,Map.of("water",EnergyType.WATER,"wrong",EnergyType.FIRE));String[] pool={BASIC,CARD,"base","base","water","water","wrong"};
        for(int seed=0;seed<1000;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID p:List.of(A,B)){var cs=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cs.add(new CardInstance(new UUID(p.getLeastSignificantBits(),i+1),p,pool[i%pool.length]));decks.add(cs);}
            var m=new ServerMatch(A,B,decks.getFirst(),decks.getLast(),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),catalog,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(m.privateView(A).hand().stream().noneMatch(c->c.definitionId().equals(BASIC))||List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->c.definitionId().equals("base")).count()<2))continue;
            for(UUID p:List.of(A,B)){m.chooseOpeningActive(p,hand(m,p,p.equals(A)?BASIC:"base").instanceId());m.placeOpeningBench(p,hand(m,p,"base").instanceId());m.startAfterOpening(p);}OpeningTestSupport.complete(m,A,B,0);m.drawCardsByEffect(A,A,30);m.drawCardsByEffect(A,B,30);m.endTurn(A);m.endTurn(B);
            if(List.of(A,B).stream().anyMatch(p->Arrays.stream(pool).anyMatch(id->m.privateView(p).hand().stream().noneMatch(c->c.definitionId().equals(id)))))continue;
            m.evolve(A,active(m,A),hand(m,A,CARD).instanceId());return m;
        }throw new AssertionError("No actual Octillery fixture");
    }
    static void attach(ServerMatch m,UUID p,String id){m.attachEnergyByEffect(m.currentPlayer(),p,EffectSourceZone.HAND,hand(m,p,id).instanceId(),p,active(m,p));}
    public static void main(String[] args){printed();jet();tantrum();knockout();System.out.println("GeneratedRuleBatch80Test passed: actual Octillery normal Remoraid evolution,110HP/retreat2/Lightning weakness/one prize, W30 Jet of Ink with two all-heads attempt coins after legal cost, WC120 Tantrum self-Confusion and KO replacement free of old gate");}
    static void printed(){check(DB.find(CARD).orElseThrow().effectSupport()==EffectSupport.SUPPORTED,"Octillery still unsupported");var p=DATA.pokemon(CARD).orElseThrow();check(p.hitPoints()==110&&p.retreatCost()==2&&p.prizeCards()==1&&p.pokemonType()==EnergyType.WATER&&p.weaknessType()==EnergyType.LIGHTNING&&p.weaknessMultiplier()==2&&p.resistanceType()==null&&p.evolvesFromDefinitionId().equals(BASIC)&&p.evolvesFromName().equals("Remoraid")&&p.abilities().isEmpty(),"Printed Octillery stats");
        check(p.attacks().size()==2&&p.attacks().getFirst().id().equals("jet-of-ink")&&p.attacks().getFirst().baseDamage()==30&&p.attacks().getFirst().energyCost().equals(Map.of(EnergyType.WATER,1))&&p.attacks().getLast().id().equals("tantrum")&&p.attacks().getLast().baseDamage()==120&&p.attacks().getLast().energyCost().equals(Map.of(EnergyType.WATER,1,EnergyType.COLORLESS,1)),"Printed attacks/costs");p.attacks().forEach(AttackProgramRules::validate);
    }
    static void jet(){var m=game();reject(()->m.attack(A,"jet-of-ink"));attach(m,A,"wrong");reject(()->m.attack(A,"jet-of-ink"));attach(m,A,"water");m.attack(A,"jet-of-ink");check(m.activePokemonView(B).damage()==30,"Jet of Ink damage");int start=m.randomTranscript().size();var before=m.clientView(B);reject(()->m.attack(B,"strike"));check(before.equals(m.clientView(B))&&m.randomTranscript().size()==start,"Unpayable gated declaration flipped coins");attach(m,B,"water");m.attack(B,"strike");var flips=AttackCoinGateTest.events(m,start,"attack-coin-gate:");check(flips.size()==2&&m.activePokemonView(A).damage()==(flips.stream().allMatch(e->e.result()==0)?20:0)&&m.currentPlayer().equals(A),"Actual two-coin all-heads gate");}
    static void tantrum(){var m=game();attach(m,A,"water");reject(()->m.attack(A,"tantrum"));attach(m,A,"wrong");m.attack(A,"tantrum");check(m.activePokemonView(B).damage()==120&&m.activePokemonView(A).conditions().contains(SpecialCondition.CONFUSED)&&m.currentPlayer().equals(B),"Tantrum Colorless payment/damage/self-Confusion");}
    static void knockout(){var m=game();m.placeDamageCountersByEffect(A,B,active(m,B),60);m.placeDamageCountersByEffect(A,B,active(m,B),37);attach(m,A,"water");m.attack(A,"jet-of-ink");int limit=10;while(m.phase()==MatchPhase.PRIZE_SELECTION&&limit-->0)m.takePrizeAt(m.pendingChoicePlayer(),0);while(m.phase()==MatchPhase.CHOOSE_REPLACEMENT&&limit-->0){UUID p=m.pendingChoicePlayer();m.chooseReplacement(p,m.benchPokemonView(p).getFirst().pokemonId());}check(limit>0&&m.currentPlayer().equals(B)&&m.privateView(A).prizes().size()==5,"Jet of Ink KO/prize flow");attach(m,B,"water");int from=m.randomTranscript().size();m.attack(B,"strike");check(AttackCoinGateTest.events(m,from,"attack-coin-gate:").isEmpty()&&m.activePokemonView(A).damage()==20,"Replacement inherited defeated Pokemon gate");}
}
