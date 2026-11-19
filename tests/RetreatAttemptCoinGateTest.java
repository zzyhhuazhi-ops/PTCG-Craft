import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Synthetic sources isolate the player-level retreat attempt and its ordinary movement callbacks. */
public final class RetreatAttemptCoinGateTest {
    static final UUID A=new UUID(0,9811),B=new UUID(0,9812);
    static final String GATE="{\"type\":\"retreat_attempt_coin_gate\",\"target\":\"OPPONENT_ACTIVE\"}";
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
    static UUID put(ServerMatch m,UUID p,String id){var c=obtain(m,p,id);m.playBasicPokemonToBench(p,c.instanceId());return PokemonIdentityTestSupport.logical(m,p,c.instanceId());}
    static void cycle(ServerMatch m){UUID p=m.currentPlayer();m.endTurn(p);m.endTurn(p.equals(A)?B:A);}
    static AbilityDefinition gate(){return DiscardAbilityCountTest.passive("fixture-gate",GATE);}
    static AbilityDefinition movement(){return BundledCombatData.ability(JsonParser.parseString("""
        {"id":"fixture-retreat","nameZh":"Synthetic retreat callback","kind":"TRIGGERED","effectSupport":"SUPPORTED",
         "eventPolicy":{"trigger":"ON_RETREAT","watch":"SELF","optional":false,"usage":"NONE"},
         "triggers":[{"trigger":"ON_RETREAT","effects":[{"type":"draw","amount":1}]}]}
        """).getAsJsonObject(),EffectRegistry.standard());}
    static void atomic(ServerMatch m,Runnable action){
        var a=m.clientView(A);var b=m.clientView(B);var rng=m.randomTranscript();
        reject(action);check(a.equals(m.clientView(A))&&b.equals(m.clientView(B))&&rng.equals(m.randomTranscript()),"Rejected retreat changed state or RNG");
    }
    static ServerMatch game(int firstSeed,int retreat,List<String> actual){
        var defs=new ArrayList<CardDefinition>();var pokemon=new HashMap<String,PokemonProfile>();
        for(String id:List.of("base","gate","water","psychic","special","tool","field","switch")){
            boolean p=id.equals("base")||id.equals("gate"),e=Set.of("water","psychic","special").contains(id);
            var category=p||e?TrainerCategory.NONE:id.equals("tool")?TrainerCategory.TOOL:id.equals("field")?TrainerCategory.STADIUM:TrainerCategory.ITEM;
            defs.add(new CardDefinition(id,"Explicit synthetic "+id,p?CardCategory.POKEMON:e?CardCategory.ENERGY:CardCategory.TRAINER,category,"J",Set.of("international"),p,e&&!id.equals("special"),60,Map.of(),EffectSupport.SUPPORTED,null));
        }
        pokemon.put("base",new PokemonProfile(1000,retreat,null,null,0,null,0,1,List.of(GeneratedRuleBatch8Test.hit(10,false)),List.of(movement()),EnergyType.COLORLESS));
        pokemon.put("gate",new PokemonProfile(1000,0,null,null,0,null,0,1,List.of(GeneratedRuleBatch8Test.hit(10,false)),List.of(gate()),EnergyType.COLORLESS));
        for(String id:new LinkedHashSet<>(actual)){defs.add(DB.find(id).orElseThrow());pokemon.put(id,DATA.pokemon(id).orElseThrow());}
        var trainers=new HashMap<String,TrainerProfile>();
        trainers.put("tool",DynamicPassiveModifierTest.plainTool());
        trainers.put("field",DynamicPassiveModifierTest.trainer("STADIUM","{\"type\":\"suppress_abilities\",\"target\":\"SELF\",\"scope\":\"ALL_IN_PLAY\"}"));
        trainers.put("switch",GeneratedRuleBatch96Test.trainer(TrainerCategory.ITEM,"{\"type\":\"switch\",\"target\":\"SELECT_OWN_POKEMON\"}"));
        var catalog=new CombatCatalog(pokemon,Map.of("water",EnergyType.WATER,"psychic",EnergyType.PSYCHIC),trainers,Map.of("special",EnergyProfile.fixed(EnergyType.COLORLESS,2)));
        var pool=new ArrayList<String>();for(int i=0;i<16;i++)pool.add("base");
        pool.addAll(List.of("gate","gate","gate","tool","field","switch","special","special"));pool.addAll(actual);
        while(pool.size()<60)pool.add(pool.size()%2==0?"water":"psychic");
        check(pool.size()==60,"Physical60-card fixture");
        for(int seed=firstSeed;seed<firstSeed+1000;seed++){
            var decks=new ArrayList<List<CardInstance>>();
            for(UUID p:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(p.getLeastSignificantBits(),i+1),p,pool.get(i)));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.getFirst(),decks.getLast(),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),catalog,new ServerRandom(seed));
            OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->c.definitionId().equals("base")).count()<2))continue;
            for(UUID p:List.of(A,B)){m.chooseOpeningActive(p,obtain(m,p,"base").instanceId());m.placeOpeningBench(p,obtain(m,p,"base").instanceId());m.startAfterOpening(p);}
            OpeningTestSupport.complete(m,A,B,0);m.endTurn(A);m.endTurn(B);return m;
        }throw new AssertionError("No fixture opening");
    }
    static List<UUID> sources(ServerMatch m,int count,boolean active){
        m.endTurn(A);var ids=new ArrayList<UUID>();for(int i=0;i<count;i++)ids.add(put(m,B,"gate"));
        if(active)m.switchActiveByEffect(B,B,ids.getFirst());m.endTurn(B);return ids;
    }
    static long coins(ServerMatch m){return m.randomTranscript().stream().filter(e->e.operation().equals("retreat-attempt-coin")).count();}
    static boolean heads(ServerMatch m){return m.randomTranscript().stream().filter(e->e.operation().equals("retreat-attempt-coin")).reduce((a,b)->b).orElseThrow().result()==0;}
    public static void main(String[] args){
        outcomes();invalidIntents();sourceLifecycle();switches();schema();
        System.out.println("RetreatAttemptCoinGateTest passed: one nonstacking player coin after validation, both outcomes, zero/multi-unit costs, preserved tails state, spent quota, callback exclusion, live source/suppression, effect-switch exclusion and closed schema");
    }
    static void outcomes(){
        for(int cost:List.of(0,1,2))for(int count:List.of(1,2)){
            var seen=new HashSet<Boolean>();
            for(int seed=0;seed<100&&seen.size()<2;seed++){
                var m=game(seed,cost,List.of());sources(m,count,count==1);
                UUID old=active(m,A),next=bench(m,A);var paid=cost==0?List.<UUID>of():List.of(attach(m,A,old,cost==2?"special":"water").instanceId());
                m.applySpecialConditionByEffect(A,A,SpecialCondition.POISONED);m.applySpecialConditionByEffect(A,A,SpecialCondition.CONFUSED);
                var before=m.activePokemonView(A);int hand=m.privateView(A).hand().size();var rng=m.randomTranscript();
                m.retreat(A,next,paid);boolean success=heads(m);seen.add(success);
                check(coins(m)==1&&m.randomTranscript().size()==rng.size()+1,"Multiple sources or cost produced extra coins");
                check(active(m,A).equals(success?next:old),"Retreat outcome position");
                check(m.privateView(A).hand().size()==hand+(success?1:0),"Failed retreat emitted successful movement callback");
                if(!success)check(m.activePokemonView(A).equals(before),"Tails changed attachments, damage, conditions or entry");
                else check(m.benchPokemonView(A).stream().filter(v->v.pokemonId().equals(old)).findFirst().orElseThrow().conditions().isEmpty(),"Successful retreat did not clear conditions");
                UUID back=success?old:next;
                var repeat=success&&cost>0?List.of(attach(m,A,next,cost==2?"special":"water").instanceId()):paid;
                atomic(m,()->m.retreat(A,back,repeat));
                if(!success){
                    m.switchActiveByEffect(A,A,next);attach(m,A,next,"water");if(cost==2)attach(m,A,next,"water");
                    var payments=m.activePokemonView(A).attachedEnergyIds();atomic(m,()->m.retreat(A,old,cost==0?List.of():payments));
                }
                cycle(m);UUID host=active(m,A);var p=cost==0?List.<UUID>of():List.of(attach(m,A,host,cost==2?"special":"water").instanceId());
                m.retreat(A,bench(m,A),p);check(coins(m)==2,"New own turn failed to restore retreat allowance");
            }check(seen.size()==2,"Both actual server outcomes must be exercised");
        }
    }
    static void invalidIntents(){
        for(String mode:List.of("WRONG_OWNER","BAD_TARGET","DUPLICATE","UNATTACHED","INSUFFICIENT","ASLEEP","PARALYZED","LOCKED")){
            var m=game(0,1,List.of());sources(m,1,false);UUID host=active(m,A),next=bench(m,A);var c=attach(m,A,host,"water");
            if(mode.equals("ASLEEP")||mode.equals("PARALYZED"))m.applySpecialConditionByEffect(A,A,SpecialCondition.valueOf(mode));
            if(mode.equals("LOCKED"))m.preventRetreatByEffect(A,A,host);
            atomic(m,()->m.retreat(mode.equals("WRONG_OWNER")?B:A,mode.equals("BAD_TARGET")?active(m,B):next,
                mode.equals("DUPLICATE")?List.of(c.instanceId(),c.instanceId()):mode.equals("UNATTACHED")?List.of(new UUID(1,1)):mode.equals("INSUFFICIENT")?List.of():List.of(c.instanceId())));
            check(coins(m)==0,"Invalid intent consumed coin: "+mode);
        }
    }
    static void sourceLifecycle(){
        for(String mode:List.of("NONE","OWN","DISABLED","SUPPRESSED","RESTORED")){
            var m=game(0,0,List.of());
            if(mode.equals("OWN"))put(m,A,"gate");
            else if(!mode.equals("NONE")){
                var ids=sources(m,1,false);
                if(mode.equals("DISABLED"))m.disableAbilityEffectsByEffect(A,B,ids.getFirst());
                else {m.playTrainer(A,obtain(m,A,"field").instanceId(),null);if(mode.equals("RESTORED"))m.discardStadiumByEffect(A);}
            }
            m.retreat(A,bench(m,A),List.of());check(coins(m)==(mode.equals("RESTORED")?1:0),"Live source scope/suppression: "+mode);
        }
    }
    static void switches(){
        var m=game(0,0,List.of());sources(m,1,false);UUID old=active(m,A);
        m.playTrainer(A,obtain(m,A,"switch").instanceId(),null);var prompt=m.effectPokemonPrompt(A);m.chooseEffectPokemon(A,prompt.resolutionId(),prompt.step(),bench(m,A));
        check(coins(m)==0&&!active(m,A).equals(old),"Trainer switch must not use manual retreat gate");
        m.switchActiveByEffect(A,A,old);check(coins(m)==0,"Rule/effect switch must not use manual retreat gate");
        m.retreat(A,bench(m,A),List.of());check(coins(m)==1,"Effect switches consumed retreat quota");
    }
    static void schema(){
        gate();
        for(String bad:List.of(GATE.replace("OPPONENT_ACTIVE","SELF"),GATE.replace("}",",\"amount\":1}"),GATE.replace("}",",\"conditions\":[{\"type\":\"IS_ACTIVE\"}]}")))
            reject(()->DiscardAbilityCountTest.passive("bad",bad));
        reject(()->DynamicPassiveModifierTest.trainer("TOOL",GATE));
        reject(()->DynamicPassiveModifierTest.trainer("STADIUM",GATE));
        reject(()->AttackProgramRules.validate(PlannedEnergyDiscardTest.program("","",GATE)));
        reject(()->ResumableProgramRules.validate(new EffectProgram(List.of(BeforeDamageAttackTest.binding(EffectTrigger.ONCE_PER_TURN,GATE))),true,EffectTrigger.ONCE_PER_TURN));
    }
}
