import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Published Mega Dragalge ex prints; synthetic high-HP hosts expose checkup and protection boundaries. */
public final class GeneratedRuleBatch97Test {
    static final UUID A=new UUID(0,9711),B=new UUID(0,9712);
    static final List<String> PRINTS=List.of("me4_en_065","me4_en_104","me4_en_118");
    static final String BASIC="me4_en_058",ROCKY="me3_en_087",TOOL="me5_en_104",CROBAT="me4_en_051";
    static final CardDatabase DB=BundledRules.loadCardDefinitions();
    static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static CardInstance obtain(ServerMatch m,UUID p,String id){return GeneratedRuleBatch96Test.obtain(m,p,id);}
    static CardInstance attach(ServerMatch m,UUID p,UUID host,String id){
        var c=obtain(m,p,id);m.attachEnergyByEffect(m.currentPlayer(),p,EffectSourceZone.HAND,c.instanceId(),p,host);return c;
    }
    static CardInstance tool(ServerMatch m,UUID p,UUID host){
        var c=obtain(m,p,TOOL);m.attachToolByEffect(m.currentPlayer(),p,EffectSourceZone.HAND,c.instanceId(),p,host);return c;
    }
    static ServerMatch game(EnergyType type){
        var defs=new ArrayList<CardDefinition>();var pokemon=new HashMap<String,PokemonProfile>();
        for(String id:List.of(BASIC,PRINTS.get(0),PRINTS.get(1),PRINTS.get(2))){defs.add(DB.find(id).orElseThrow());pokemon.put(id,DATA.pokemon(id).orElseThrow());}
        for(String id:List.of(ROCKY,TOOL))defs.add(DB.find(id).orElseThrow());
        for(String id:List.of("host","water","dark"))defs.add(new CardDefinition(id,"Explicit synthetic "+id,id.equals("host")?CardCategory.POKEMON:CardCategory.ENERGY,TrainerCategory.NONE,"J",Set.of("international"),id.equals("host"),!id.equals("host"),60,Map.of(),EffectSupport.SUPPORTED,null));
        // Crobat's real printed attack on a synthetic host isolates re-poisoning from evolution and Ability setup.
        pokemon.put("host",new PokemonProfile(1000,0,null,null,0,null,0,1,List.of(GeneratedRuleBatch8Test.hit(10,false),CopyAttackTest.copier(),DATA.pokemon(CROBAT).orElseThrow().attacks().getFirst()),List.of(),type));
        var catalog=new CombatCatalog(pokemon,Map.of("water",EnergyType.WATER,"dark",EnergyType.DARKNESS),Map.of(TOOL,DATA.trainer(TOOL).orElseThrow()),Map.of(ROCKY,DATA.energyProfile(ROCKY).orElseThrow()));
        var pool=new ArrayList<String>();for(int i=0;i<16;i++)pool.add("host");
        pool.addAll(List.of(BASIC,BASIC,PRINTS.get(0),PRINTS.get(1),PRINTS.get(2),ROCKY,ROCKY,TOOL,TOOL));
        while(pool.size()<60)pool.add(pool.size()%2==0?"water":"dark");
        for(int seed=0;seed<1000;seed++){
            var decks=new ArrayList<List<CardInstance>>();
            for(UUID p:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(p.getLeastSignificantBits(),i+1),p,pool.get(i)));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.getFirst(),decks.getLast(),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),catalog,new ServerRandom(seed));
            OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->c.definitionId().equals("host")).count()<2))continue;
            for(UUID p:List.of(A,B)){m.chooseOpeningActive(p,obtain(m,p,"host").instanceId());m.placeOpeningBench(p,obtain(m,p,"host").instanceId());m.startAfterOpening(p);}
            OpeningTestSupport.complete(m,A,B,0);m.endTurn(A);m.endTurn(B);return m;
        }throw new AssertionError("No legal opening");
    }
    static UUID evolve(ServerMatch m,String id){
        var basic=obtain(m,A,BASIC);m.playBasicPokemonToBench(A,basic.instanceId());var card=obtain(m,A,id);
        reject(()->m.evolve(A,basic.instanceId(),card.instanceId()));
        m.endTurn(A);m.endTurn(B);m.evolve(A,basic.instanceId(),card.instanceId());
        m.switchActiveByEffect(A,A,basic.instanceId());return active(m,A);
    }
    public static void main(String[] args){
        printedAndCosts();rockyProtection();poisonAndOverwrite();copiedPoison();prizes();
        System.out.println("GeneratedRuleBatch97Test passed: three real prints, normal Skrelp evolution, printed costs, Rocky Fighting guard, repeated sixteen-counter checkup, real Crobat re-poisoning, copied poison, three prizes");
    }
    static void printedAndCosts(){
        for(String id:PRINTS){
            var p=DATA.pokemon(id).orElseThrow();
            check(DB.find(id).orElseThrow().effectSupport()==EffectSupport.SUPPORTED&&p.hitPoints()==330&&p.retreatCost()==2&&p.prizeCards()==3&&p.pokemonType()==EnergyType.DRAGON&&p.weaknessType()==null&&p.resistanceType()==null&&p.evolvesFromName().equals("Skrelp"),"Printed profile "+id);
            check(p.attacks().size()==2&&p.attacks().stream().allMatch(a->a.baseDamage()==0&&a.effectSupport()==EffectSupport.SUPPORTED),"Printed zero-damage attacks");
            for(var a:p.attacks())AttackProgramRules.validate(a);
            check(p.attacks().getFirst().energyCost().equals(Map.of(EnergyType.COLORLESS,2))&&p.attacks().getLast().energyCost().equals(Map.of(EnergyType.WATER,1,EnergyType.DARKNESS,1)),"Printed CC / WD costs");
            var m=game(EnergyType.COLORLESS);UUID h=evolve(m,id);
            check(m.activePokemonView(A).evolutionCardIds().equals(List.of(BASIC,id)),"Normal named evolution");
            reject(()->m.attack(A,"corrosive-liquid"));attach(m,A,h,"water");reject(()->m.attack(A,"corrosive-liquid"));
            attach(m,A,h,"water");reject(()->m.attack(A,"pernicious-poison"));
            var lost=tool(m,B,active(m,B));var special=attach(m,B,bench(m,B),ROCKY);var kept=attach(m,B,active(m,B),"dark");
            m.attack(A,"corrosive-liquid");
            check(m.privateView(B).discard().containsAll(List.of(lost,special))&&m.activePokemonView(B).attachedEnergyIds().contains(kept.instanceId())&&m.activePokemonView(B).damage()==0,"Actual printed discard "+id);
        }
    }
    static void rockyProtection(){
        var m=game(EnergyType.FIGHTING);UUID h=evolve(m,PRINTS.getFirst());attach(m,A,h,"water");attach(m,A,h,"dark");
        var rocky=attach(m,B,active(m,B),ROCKY);var kept=tool(m,B,active(m,B));var lost=tool(m,B,bench(m,B));
        m.attack(A,"corrosive-liquid");
        check(m.activePokemonView(B).attachedTools().contains(kept)&&m.activePokemonView(B).attachedEnergyIds().contains(rocky.instanceId())&&m.privateView(B).discard().contains(lost),"Actual Rocky must protect its Tool and itself, not another host");
        m.endTurn(B);m.attack(A,"pernicious-poison");
        check(m.activePokemonView(B).damage()==0&&!m.activePokemonView(B).conditions().contains(SpecialCondition.POISONED),"Rocky must prevent poison too");
    }
    static void poisonAndOverwrite(){
        for(String id:PRINTS){
            var m=game(EnergyType.COLORLESS);UUID h=evolve(m,id);attach(m,A,h,"water");attach(m,A,h,"dark");m.attack(A,"pernicious-poison");
            check(m.activePokemonView(B).damage()==160&&m.activePokemonView(B).conditions().contains(SpecialCondition.POISONED)&&m.lastAttack().finalDamage()==0,"Poison is sixteen checkup counters, not attack damage");
            m.endTurn(B);check(m.activePokemonView(B).damage()==320,"Poison must persist at each checkup");
            if(!id.equals(PRINTS.getFirst()))continue;
            UUID next=bench(m,A);attach(m,A,next,"dark");m.switchActiveByEffect(A,A,next);m.attack(A,"poison-sound-wave");
            check(m.activePokemonView(B).damage()==410,"Crobat must overwrite sixteen with one: 320 + 80 + 10");
            m.endTurn(B);check(m.activePokemonView(B).damage()==420,"Normal poison must remain one counter");
        }
    }
    static void copiedPoison(){
        var a=DATA.pokemon(PRINTS.getFirst()).orElseThrow().attacks().getLast();
        var m=CopyAttackTest.fixture(CopyAttackTest.copier(),a,EnergyType.COLORLESS,0);CopyAttackTest.pay(m);
        var c=m.copyAttackOptions(CopyAttackTest.A,"copy").stream().filter(x->x.attackId().equals(a.id())).findFirst().orElseThrow();
        CopyAttackTest.copy(m,c,List.of(),List.of());
        check(m.activePokemonView(CopyAttackTest.B).damage()==160&&m.currentPlayer().equals(CopyAttackTest.B),"Copied poison must use actual attacker and declared copy cost");
    }
    static void prizes(){
        var m=game(EnergyType.COLORLESS);UUID h=evolve(m,PRINTS.getFirst());m.placeDamageCountersByEffect(A,A,h,32);m.endTurn(A);m.attack(B,"hit");
        check(m.phase()==MatchPhase.PRIZE_SELECTION,"Mega knockout prize selection missing");
        int count=0;while(m.phase()==MatchPhase.PRIZE_SELECTION&&count<4){m.takePrizeAt(B,0);count++;}
        check(count==3&&m.privateView(B).prizes().size()==3,"Mega Dragalge ex must give three prizes");
    }
}
