import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch58Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final List<String> IDS=List.of("sm6_en_117","sm5_en_136");
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static EnergyProfile energy(String id){return NonPokemonBatch2Test.DATA.energyProfile(id).orElseThrow();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return DynamicPassiveModifierTest.hand(m,p,id);}
    static List<UUID> field(ServerMatch m,UUID p){var result=new ArrayList<UUID>();result.add(m.activePokemonView(p).pokemonId());m.benchPokemonView(p).forEach(x->result.add(x.pokemonId()));return result;}
    static void evolve(ServerMatch m,UUID p,UUID host,int stage){for(int i=1;i<=stage;i++)m.evolveByEffect(p,host,hand(m,p,"stage"+i).instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);}
    static void count(ServerMatch m,UUID p,UUID host,int amount,boolean rainbow){for(var type:EnergyType.values())check(m.matchesEffectCondition(p,host,new ConditionDefinition(ConditionType.ENERGY_COUNT,Map.of("energy_type",type.name(),"count_mode","UNITS","value",""+(rainbow||type==EnergyType.COLORLESS?amount:0),"comparison","="),List.of())),"Wrong live provision for "+type+" owner "+p);}
    static ServerMatch fixture(String id,boolean beast,AttackDefinition attack){
        var defs=new ArrayList<CardDefinition>();var profiles=new HashMap<String,PokemonProfile>();
        var printed=BundledRules.loadCardDefinitions().find(id).orElseThrow();
        for(String key:List.of("basic","stage1","stage2","special","stadium")){
            boolean pokemon=key.equals("basic")||key.startsWith("stage");
            defs.add(new CardDefinition(key,key,pokemon?CardCategory.POKEMON:key.equals("special")?CardCategory.ENERGY:CardCategory.TRAINER,key.equals("stadium")?TrainerCategory.STADIUM:TrainerCategory.NONE,"H",Set.of("international"),key.equals("basic"),false,60,key.equals("special")?printed.specialDeckLimits():Map.of(),EffectSupport.SUPPORTED,null,pokemon&&beast?Set.of(PokemonFamily.ULTRA_BEAST):Set.of(),pokemon?Set.of(EnergyType.COLORLESS):Set.of()));
            if(pokemon)profiles.put(key,new PokemonProfile(1000,3,key.equals("stage1")?"basic":key.equals("stage2")?"stage1":null,EnergyType.COLORLESS,2,null,0,1,List.of(attack),List.of(),EnergyType.COLORLESS));
        }
        var combat=new CombatCatalog(profiles,Map.of(),Map.of("stadium",DynamicEnergyProvisionTest.stadium()),Map.of("special",energy(id)));
        for(int seed=0;seed<500;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID p:List.of(A,B)){String[] pattern={"basic","basic","basic","basic","stage1","stage1","stage2","stage2","special","stadium"};var deck=new ArrayList<CardInstance>();for(int i=0;i<60;i++)deck.add(new CardInstance(new UUID(p.getLeastSignificantBits(),i+1),p,pattern[i%pattern.length]));decks.add(deck);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),combat,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(x->x.definitionId().equals("basic")).count()<3))continue;
            OpeningTestSupport.complete(m,A,B,2);m.drawCardsByEffect(A,A,30);m.drawCardsByEffect(A,B,30);m.endTurn(A);m.endTurn(B);
            if(List.of(A,B).stream().allMatch(p->List.of("stage1","stage2").stream().allMatch(k->m.privateView(p).hand().stream().filter(x->x.definitionId().equals(k)).count()>=3)&&List.of("basic","special","stadium").stream().allMatch(k->m.privateView(p).hand().stream().anyMatch(x->x.definitionId().equals(k)))))return m;
        }throw new AssertionError("No Prism Energy fixture");
    }
    static ServerMatch aiGame(String id){var m=fixture(id,true,GeneratedRuleBatch8Test.hit(100,false));if(id.equals(IDS.get(1)))for(UUID host:field(m,A))evolve(m,A,host,2);return m;}
    public static void main(String[] args){beast();boost();movement();prism();System.out.println("NonPokemonBatch58Test passed: Beast host and Active-only bonus before Weakness; Super Boost host stage and own field threshold; all Energy types, live evolution/devolution/movement, Energy suppression and Prism Lost Zone replacement; both owners");}
    static void beast(){for(UUID p:List.of(A,B))for(boolean beast:List.of(false,true))for(boolean bench:List.of(false,true))for(boolean suppressed:List.of(false,true)){
        var m=fixture(IDS.get(0),beast,GeneratedRuleBatch8Test.hit(100,bench));if(p.equals(B))m.endTurn(A);UUID host=m.activePokemonView(p).pokemonId(),opp=p.equals(A)?B:A;var card=hand(m,p,"special");m.attachEnergy(p,card.instanceId(),host);if(suppressed)m.playTrainer(p,hand(m,p,"stadium").instanceId(),null);count(m,p,host,1,beast&&!suppressed);m.attack(p,"hit");NonPokemonBatch2Test.settle(m);int expected=bench?100:(100+(beast&&!suppressed?30:0))*2;check((bench?m.benchPokemonView(opp).getFirst().damage():m.activePokemonView(opp).damage())==expected,"Beast damage family/suppression/Bench/Weakness wrong");
    }}
    static void boost(){for(UUID p:List.of(A,B))for(int stage=0;stage<=2;stage++)for(int total=0;total<=3;total++){
        var m=fixture(IDS.get(1),false,GeneratedRuleBatch8Test.hit(100,false));UUID opp=p.equals(A)?B:A;if(p.equals(B))m.endTurn(A);var field=field(m,p);UUID host=field.getFirst();evolve(m,p,host,stage);for(int i=0;i<Math.min(total,2);i++)evolve(m,p,field.get(i+1),2);
        // Three Stage 2 on the opponent's field never satisfy the owner's threshold.
        m.endTurn(p);for(UUID other:field(m,opp))evolve(m,opp,other,2);m.endTurn(opp);
        var card=hand(m,p,"special");m.attachEnergy(p,card.instanceId(),host);int amount=stage==2&&total>=2?4:1;count(m,p,host,amount,stage==2);
        if(stage==2){m.devolvePokemonToHandByEffect(p,p,host);count(m,p,host,1,false);m.evolveByEffect(p,host,hand(m,p,"stage2").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);count(m,p,host,amount,true);if(total>=2){m.devolvePokemonToHandByEffect(p,p,field.get(1));count(m,p,host,1,true);}}
        m.playTrainer(p,hand(m,p,"stadium").instanceId(),null);count(m,p,host,1,false);m.discardStadiumByEffect(p);count(m,p,host,stage==2&&total>=2?1:amount,stage==2);
    }}
    static void movement(){for(UUID p:List.of(A,B)){
        var m=fixture(IDS.get(1),false,GeneratedRuleBatch8Test.hit(100,false));if(p.equals(B))m.endTurn(A);var hosts=field(m,p);for(UUID host:hosts)evolve(m,p,host,2);var extra=hand(m,p,"basic");m.playBasicPokemonToBench(p,extra.instanceId());UUID basic=m.benchPokemonView(p).getLast().pokemonId();var card=hand(m,p,"special");m.attachEnergy(p,card.instanceId(),hosts.getFirst());count(m,p,hosts.getFirst(),4,true);m.moveAttachedEnergyByEffect(p,p,hosts.getFirst(),p,basic,List.of(card.instanceId()));count(m,p,basic,1,false);m.moveAttachedEnergyByEffect(p,p,basic,p,hosts.getFirst(),List.of(card.instanceId()));count(m,p,hosts.getFirst(),4,true);m.retreat(p,hosts.get(1),List.of(card.instanceId()));lost(m,p,card);
    }}
    static void lost(ServerMatch m,UUID p,CardInstance c){check(m.clientView(null).players().stream().filter(x->x.id().equals(p)).findFirst().orElseThrow().lostZone().contains(c)&&!m.privateView(p).discard().contains(c),"Prism card went to discard instead of its owner's Lost Zone");}
    static void prism(){for(String id:IDS)for(UUID p:List.of(A,B))for(boolean attached:List.of(false,true)){
        check(BundledRules.loadCardDefinitions().find(id).orElseThrow().specialDeckLimits().getOrDefault("棱镜之星",0)==1,"Prism deck limit missing");var m=fixture(id,false,GeneratedRuleBatch8Test.hit(100,false));if(p.equals(B))m.endTurn(A);var card=hand(m,p,"special");UUID host=m.activePokemonView(p).pokemonId();if(attached){m.attachEnergy(p,card.instanceId(),host);m.discardAttachedEnergyByEffect(p,p,host,List.of(card.instanceId()));}else m.discardHandCardsByEffect(p,p,List.of(card.instanceId()));lost(m,p,card);
    }}
}
