import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Declared single target and current printed-name coin count; synthetic hosts are explicit. */
public final class NamedFieldCoinAttackTest {
    static final UUID A=new UUID(0,9911),B=new UUID(0,9912);
    static final String PALDEAN="sv2_en_028",GX="sm1_en_100",TOOL="me5_en_074";
    static final String COIN="{\"type\":\"coin_flip\",\"flips\":0,\"flips_from\":\"POKEMON_COUNT\",\"flips_target\":\"ALL_OWN_POKEMON\",\"name_contains\":\"Tauros\",\"effects\":[{\"type\":\"repeat_per_heads\",\"effects\":[{\"type\":\"damage\",\"target\":\"OPPONENT_BENCH\",\"amount\":50}]}]}";
    static final CardDatabase DB=BundledRules.loadCardDefinitions();
    static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static CardInstance obtain(ServerMatch m,UUID p,String id){return GeneratedRuleBatch96Test.obtain(m,p,id);}
    static UUID put(ServerMatch m,UUID p,String id){var c=obtain(m,p,id);m.playBasicPokemonToBench(p,c.instanceId());return PokemonIdentityTestSupport.logical(m,p,c.instanceId());}
    static void pay(ServerMatch m,int count){for(int i=0;i<count;i++){var c=obtain(m,A,"energy");m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,c.instanceId(),A,active(m,A));}}
    static AttackDefinition attack(String body,String target,int count){
        return BundledCombatData.attack(JsonParser.parseString("{\"id\":\"hit\",\"nameZh\":\"Synthetic named coin attack\",\"energyCost\":{},\"baseDamage\":0,\"damageType\":\"COLORLESS\",\"effectSupport\":\"SUPPORTED\",\"damageTarget\":\""+target+"\",\"targetCount\":"+count+",\"triggers\":[{\"trigger\":\"ON_ATTACK\",\"effects\":["+body+"]}]}").getAsJsonObject(),EffectRegistry.standard());
    }
    static ServerMatch game(AttackDefinition attack,String actualId,boolean namedActor,int firstSeed,String mode){
        String actor=actualId==null?"actor":actualId;
        var defs=new ArrayList<CardDefinition>();var pokemon=new HashMap<String,PokemonProfile>();
        for(String id:List.of(PALDEAN,GX)){defs.add(DB.find(id).orElseThrow());pokemon.put(id,DATA.pokemon(id).orElseThrow());}
        defs.add(DB.find(TOOL).orElseThrow());
        for(String id:List.of("base","mimic","energy"))defs.add(new CardDefinition(id,id.equals("mimic")?"Tauros":"Explicit synthetic "+id,id.equals("energy")?CardCategory.ENERGY:CardCategory.POKEMON,TrainerCategory.NONE,"J",Set.of("international"),id.equals("base"),id.equals("energy"),60,Map.of(),EffectSupport.SUPPORTED,null));
        var guard=mode.equals("GUARD")?List.of(new AbilityDefinition("fixture-bench-guard","Synthetic Bench guard",new AbilityEffect(AbilityEffect.Opcode.PASSIVE_PREVENT_ATTACK_DAMAGE_WHILE_BENCHED,0),EffectSupport.SUPPORTED,null,AbilityDefinition.Kind.PASSIVE)):List.<AbilityDefinition>of();
        pokemon.put("base",new PokemonProfile(1000,0,null,mode.equals("WEAK")?EnergyType.COLORLESS:null,mode.equals("WEAK")?2:0,mode.equals("RESIST")?EnergyType.COLORLESS:null,mode.equals("RESIST")?30:0,1,List.of(GeneratedRuleBatch8Test.hit(10,false)),guard,EnergyType.METAL,"Synthetic target",null));
        pokemon.put("mimic",new PokemonProfile(1000,0,PALDEAN,null,0,null,0,1,List.of(attack),List.of(),EnergyType.COLORLESS,"Unrelated current name","Paldean Tauros"));
        if(actualId==null){
            defs.add(new CardDefinition(actor,"Synthetic actor",CardCategory.POKEMON,TrainerCategory.NONE,"J",Set.of("international"),true,false,60,Map.of(),EffectSupport.SUPPORTED,null));
            pokemon.put(actor,new PokemonProfile(1000,0,null,null,0,null,0,1,List.of(attack,CopyAttackTest.copier()),List.of(),EnergyType.COLORLESS,namedActor?"Tauros":"Unrelated copier",null));
        }else {defs.add(DB.find(actor).orElseThrow());pokemon.put(actor,DATA.pokemon(actor).orElseThrow());}
        var catalog=new CombatCatalog(pokemon,Map.of("energy",EnergyType.COLORLESS),Map.of(TOOL,DATA.trainer(TOOL).orElseThrow()));
        var pool=new ArrayList<String>();for(int i=0;i<12;i++)pool.add("base");for(int i=0;i<10;i++)pool.add(actor);
        for(int i=0;i<5;i++)pool.add(PALDEAN);pool.addAll(List.of(GX,GX,TOOL,"mimic"));while(pool.size()<60)pool.add("energy");
        for(int seed=firstSeed;seed<firstSeed+1000;seed++){
            var decks=new ArrayList<List<CardInstance>>();
            for(UUID p:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(p.getLeastSignificantBits(),i+1),p,pool.get(i)));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.getFirst(),decks.getLast(),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),catalog,new ServerRandom(seed));
            OpeningTestSupport.decideOrder(m,A,B);
            if(m.privateView(A).hand().stream().noneMatch(c->c.definitionId().equals(actor))||m.privateView(B).hand().stream().filter(c->c.definitionId().equals("base")).count()<2)continue;
            m.chooseOpeningActive(A,obtain(m,A,actor).instanceId());m.startAfterOpening(A);
            m.chooseOpeningActive(B,obtain(m,B,"base").instanceId());m.placeOpeningBench(B,obtain(m,B,"base").instanceId());m.startAfterOpening(B);
            OpeningTestSupport.complete(m,A,B,0);m.endTurn(A);m.endTurn(B);return m;
        }throw new AssertionError("No named-coin fixture");
    }
    static void atomic(ServerMatch m,Runnable action){var a=m.clientView(A);var b=m.clientView(B);var rng=m.randomTranscript();reject(action);check(a.equals(m.clientView(A))&&b.equals(m.clientView(B))&&rng.equals(m.randomTranscript()),"Invalid declaration/choice changed state or RNG");}
    static List<RandomAuditEvent> coins(ServerMatch m,int start){return m.randomTranscript().subList(start,m.randomTranscript().size()).stream().filter(e->e.operation().startsWith("attack-effect-coin:")).toList();}
    static int heads(List<RandomAuditEvent> coins){return (int)coins.stream().filter(e->e.result()==0).count();}
    public static void main(String[] args){
        countsAndTargets();currentTop();retry();copiedZero();schema();
        System.out.println("NamedFieldCoinAttackTest passed: current printed-name substring,0..6 hosts, declared single target before RNG, Active W/R and Bench exclusion/guard, actual retry Tool authority, copied zero and closed multi-target schema");
    }
    static void countsAndTargets(){
        var attack=attack(COIN,"OPPONENT_ANY",1);
        for(int count=0;count<=6;count++)for(String mode:List.of("NORMAL","WEAK","RESIST","GUARD"))for(boolean back:List.of(false,true)){
            var m=game(attack,null,count>0,count,mode);
            for(int i=1;i<count;i++)put(m,A,i==1?GX:PALDEAN);
            UUID target=back?bench(m,B):active(m,B);
            atomic(m,()->m.attack(A,"hit"));atomic(m,()->m.attack(A,"hit",active(m,A)));
            atomic(m,()->m.attackTargets(A,"hit",List.of(target,target)));
            atomic(m,()->m.attack(A,"hit",new UUID(1,1)));
            int from=m.randomTranscript().size();m.attack(A,"hit",target);var results=coins(m,from);
            check(results.size()==count&&m.randomTranscript().size()==from+count,"Physical current-top host count or extra RNG");
            int raw=heads(results)*50,expected=back?(mode.equals("GUARD")?0:raw):mode.equals("WEAK")?raw*2:mode.equals("RESIST")?Math.max(0,raw-30):raw;
            check((back?m.benchPokemonView(B).getFirst():m.activePokemonView(B)).damage()==expected,"Target-aware damage layer: "+mode+", count="+count+", bench="+back+", expected="+expected);
            check((back?m.activePokemonView(B):m.benchPokemonView(B).getFirst()).damage()==0,"Unchosen target took damage");
        }
    }
    static void currentTop(){
        var attack=attack(COIN,"OPPONENT_ANY",1);var m=game(attack,null,false,0,"NORMAL");
        UUID under=put(m,A,PALDEAN);m.evolveByEffect(A,under,obtain(m,A,"mimic").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
        m.endTurn(A);put(m,B,PALDEAN);m.endTurn(B);
        int before=m.randomTranscript().size();m.attack(A,"hit",bench(m,B));
        check(coins(m,before).isEmpty(),"Opponent, evolution-understack or translated label counted as current printed Tauros");
    }
    static void retry(){
        var attack=attack(COIN,"OPPONENT_ANY",1);
        for(boolean retry:List.of(false,true)){
            var m=game(attack,null,true,0,"NORMAL");put(m,A,PALDEAN);m.playTrainer(A,obtain(m,A,TOOL).instanceId(),active(m,A));
            UUID target=bench(m,B);int before=m.randomTranscript().size();m.attack(A,"hit",target);
            check(m.hasPendingEffectChoice()&&coins(m,before).size()==1&&m.benchPokemonView(B).getFirst().damage()==0,"Retry prompt must precede damage");
            var first=m.effectChoicePrompt(A);
            atomic(m,()->m.chooseEffectOption(B,first.resolutionId(),first.step(),0));
            atomic(m,()->m.attack(A,"hit",active(m,B)));
            m.chooseEffectOption(A,first.resolutionId(),first.step(),2);
            var finalChoice=m.effectChoicePrompt(A);m.chooseEffectOption(A,finalChoice.resolutionId(),finalChoice.step(),retry?0:1);
            var results=coins(m,before);check(results.size()==(retry?4:2),"Retry must replace the whole two-coin attempt");
            int raw=heads(results.subList(retry?2:0,results.size()))*50;
            check(m.benchPokemonView(B).getFirst().damage()==raw&&m.activePokemonView(B).damage()==0&&m.currentPlayer().equals(B),"Retry changed declared target or replayed damage");
            atomic(m,()->m.chooseEffectOption(A,first.resolutionId(),first.step(),0));
        }
    }
    static void copiedZero(){
        var a=attack(COIN,"OPPONENT_ANY",1);var m=CopyAttackTest.fixture(CopyAttackTest.copier(),a,EnergyType.PSYCHIC,0);CopyAttackTest.pay(m);
        var c=CopyAttackTest.choice(m);var rng=m.randomTranscript();
        reject(()->CopyAttackTest.copy(m,c,List.of(),List.of()));check(rng.equals(m.randomTranscript()),"Copied zero-count still requires declared target");
        CopyAttackTest.copy(m,c,List.of(m.benchPokemonView(CopyAttackTest.B).getFirst().pokemonId()),List.of());
        check(rng.equals(m.randomTranscript())&&m.benchPokemonView(CopyAttackTest.B).getFirst().damage()==0&&!m.hasPendingEffectChoice(),"Zero-name copy rolled coins or stalled");
    }
    static void schema(){
        AttackProgramRules.validate(attack(COIN,"OPPONENT_ANY",1));
        for(String bad:List.of(COIN.replace("\"Tauros\"","\"\""),COIN.replace("\"Tauros\"","\" Tauros\""),COIN.replace("\"Tauros\"","\""+"x".repeat(65)+"\""),COIN.replace("\"name_contains\"","\"unexpected\""),
                COIN.replace("\"name_contains\":\"Tauros\"","\"name_contains\":\"Tauros\",\"pokemon_names\":\"Tauros\""),
                COIN.replace("POKEMON_COUNT","ENERGY_COUNT"),COIN.replace("ALL_OWN_POKEMON","OPPONENT_ACTIVE"),
                COIN.replace("\"flips\":0,\"flips_from\":\"POKEMON_COUNT\",\"flips_target\":\"ALL_OWN_POKEMON\"","\"flips\":1")))
            reject(()->AttackProgramRules.validate(attack(bad,"OPPONENT_ANY",1)));
        for(String target:List.of("ALL_OPPONENT_POKEMON","ALL_POKEMON","ALL_OPPONENT_BENCH","OPPONENT_BENCH"))
            reject(()->AttackProgramRules.validate(attack(COIN,target,target.equals("OPPONENT_BENCH")?2:0)));
        reject(()->AttackProgramRules.validate(attack(COIN,"OPPONENT_ANY",2)));
    }
}
