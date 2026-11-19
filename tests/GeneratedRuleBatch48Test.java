import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Counter uses actual opponent-turn attack damage, independent of remaining counters or ongoing attack effects. */
public final class GeneratedRuleBatch48Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final Ruleset RULES=GeneratedRuleBatch8Test.RULES;
    static void check(boolean b,String why){GeneratedRuleBatch8Test.check(b,why);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch13Test.hand(m,p,id);}
    static AttackDefinition counter(String id){return BundledCombatData.attack(GeneratedRuleBatch8Test.entry(id).getAsJsonArray("attacks").get(0).getAsJsonObject(),EffectRegistry.standard());}
    static AttackDefinition counter(){return counter("me5-5_en_085");}
    static AttackDefinition synthetic(String id,int damage,String type,String extra){return BundledCombatData.attack(JsonParser.parseString("{\"id\":\""+id+"\",\"nameZh\":\""+id+"\",\"energyCost\":{},\"baseDamage\":"+damage+",\"damageType\":\""+type+"\",\"effectSupport\":\"SUPPORTED\""+extra+"}").getAsJsonObject(),EffectRegistry.standard());}
    static AttackDefinition hit(){return synthetic("hit",70,"COLORLESS","");}
    static AbilityDefinition guard(boolean prevent){return BundledCombatData.ability(JsonParser.parseString("{\"id\":\"guard\",\"nameZh\":\"guard\",\"kind\":\"PASSIVE\",\"effectSupport\":\"SUPPORTED\",\"triggers\":[{\"trigger\":\"PASSIVE\",\"effects\":[{\"type\":\"passive_attack_guard\",\"target\":\"SELF\",\"mode\":\""+(prevent?"PREVENT":"REDUCE")+"\""+(prevent?"":",\"amount\":20")+"}]}]}").getAsJsonObject(),EffectRegistry.standard());}
    static ServerMatch fixture(){return fixture(counter(),hit(),false,false,List.of());}
    static ServerMatch fixture(AttackDefinition counter,AttackDefinition incoming,boolean weak,boolean resist,List<AbilityDefinition> guards){
        EnergyType energy=counter.energyCost().keySet().stream().filter(t->t!=EnergyType.COLORLESS).findFirst().orElse(EnergyType.FIGHTING);
        var snipe=synthetic("snipe",70,"FIRE",",\"damageTarget\":\"OPPONENT_BENCH\"");
        var counters=synthetic("placed",0,"COLORLESS",",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":7}]}]");
        var self=synthetic("self",0,"COLORLESS",",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[{\"type\":\"self_damage\",\"target\":\"SELF\",\"amount\":30}]}]");
        var extra=synthetic("extra",40,"COLORLESS",",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[{\"type\":\"damage_counter\",\"target\":\"SELECT_OPPONENT_POKEMON\",\"amount\":50,\"extra_turn_on_ko\":true,\"previous_turn_attack_ids\":\"extra\"}]}]");
        var attacks=List.of(counter,incoming,snipe,counters,self,extra,CopyAttackTest.copier());var defs=new ArrayList<CardDefinition>();var profiles=new HashMap<String,PokemonProfile>();
        for(String id:List.of("a","b","plain","evolved","energy")){
            boolean pokemon=!id.equals("energy");defs.add(new CardDefinition(id,id,pokemon?CardCategory.POKEMON:CardCategory.ENERGY,TrainerCategory.NONE,"J",Set.of("international"),pokemon&&!id.equals("evolved"),!pokemon,60,Map.of(),EffectSupport.SUPPORTED,null));
            if(pokemon)profiles.put(id,new PokemonProfile(500,0,id.equals("evolved")?"a":null,id.equals("a")&&weak?EnergyType.FIRE:null,id.equals("a")&&weak?2:0,id.equals("a")&&resist?EnergyType.FIRE:null,id.equals("a")&&resist?20:0,1,attacks,id.equals("a")?guards:List.of(),energy));
        }
        var catalog=new CombatCatalog(profiles,Map.of("energy",energy));
        for(int seed=0;seed<1000;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID owner:List.of(A,B)){String[] ids={owner.equals(A)?"a":"b",owner.equals(A)?"a":"b",owner.equals(A)?"a":"b","plain","evolved","energy","energy","energy"};var cs=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cs.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,ids[i%ids.length]));decks.add(cs);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),RULES,new CardDatabase(defs),catalog,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->c.definitionId().equals(p.equals(A)?"a":"b")).count()<2))continue;
            for(UUID p:List.of(A,B)){m.chooseOpeningActive(p,hand(m,p,p.equals(A)?"a":"b").instanceId());m.placeOpeningBench(p,hand(m,p,p.equals(A)?"a":"b").instanceId());m.startAfterOpening(p);}OpeningTestSupport.complete(m,A,B,0);m.drawCardsByEffect(A,A,20);m.drawCardsByEffect(A,B,20);m.endTurn(A);m.endTurn(B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->c.definitionId().equals("energy")).count()<4)||m.privateView(A).hand().stream().noneMatch(c->c.definitionId().equals("evolved")))continue;
            pay(m,A,active(m,A),counter.energyCost().values().stream().mapToInt(Integer::intValue).sum());return m;
        }throw new AssertionError("No Counter fixture");
    }
    static void pay(ServerMatch m,UUID owner,UUID host,int n){for(int i=0;i<n;i++){var c=hand(m,owner,"energy");m.attachEnergyByEffect(m.currentPlayer(),owner,EffectSourceZone.HAND,c.instanceId(),owner,host);}}
    static void incoming(ServerMatch m){m.endTurn(A);m.attack(B,"hit");check(m.currentPlayer().equals(A),"Incoming attack did not finish before Counter");}
    static void respond(ServerMatch m,AttackDefinition a,int expected){m.attack(A,a.id());check(m.lastAttack().finalDamage()==expected,"Counter damage expected "+expected+" but was "+m.lastAttack().finalDamage());}
    public static void main(String[] args){allPrints();partialPrints();damageLayersAndHealing();nonAttackDamage();movement();formsAndReentry();splitDamageAndAttackDevolution();extraTurns();copiedHistory();schema();System.out.println("GeneratedRuleBatch48Test passed: eight full and three partial actual Counter-family prints and their printed costs, positive final attack damage history including two hits, incoming weakness/resistance/reduction, healing-independent bonus, no counter/checkup/self-damage contamination, Bench/Active movement, evolution/devolution/re-entry resets including damage-then-devolve, latest extra opponent turn and extra own turn, copied actual source, strict phase/schema validation");}
    static void allPrints(){for(String id:List.of("me5-5_en_085","me5-5_en_138","me2_en_004","me2_en_108","swsh2_en_036","swsh2_en_194","swsh4-5_en_019","swsh8_en_006")){
        var a=counter(id);AttackProgramRules.validate(a);var no=fixture(a,hit(),false,false,List.of());respond(no,a,a.baseDamage());var m=fixture(a,hit(),false,false,List.of());incoming(m);respond(m,a,a.baseDamage()+70);
        if(id.startsWith("me5-5"))check(a.energyCost().equals(Map.of(EnergyType.FIGHTING,1))&&a.baseDamage()==10,"Lycanroc printed cost/damage changed");
    }}
    static void damageLayersAndHealing(){for(String mode:List.of("normal","heal","full_heal","extra_counters","weak","resist","both","reduce","prevent")){
        boolean weak=Set.of("weak","both").contains(mode),resist=Set.of("resist","both").contains(mode);var guards=mode.equals("reduce")?List.of(guard(false)):mode.equals("prevent")?List.of(guard(true)):List.<AbilityDefinition>of();
        var m=fixture(counter(),synthetic("hit",70,"FIRE",""),weak,resist,guards);incoming(m);
        int damage=mode.equals("prevent")?0:(weak?140:70)-(resist?20:0)-(mode.equals("reduce")?20:0);
        check(m.activePokemonView(A).damage()==damage,"Incoming damage calculation wrong");
        if(mode.equals("heal"))m.healDamageByEffect(A,A,active(m,A),30);if(mode.equals("full_heal"))m.healDamageByEffect(A,A,active(m,A),1000);if(mode.equals("extra_counters"))m.placeDamageCountersByEffect(A,A,active(m,A),5);
        respond(m,counter(),10+damage);
    }}
    static void partialPrints(){var db=BundledRules.loadCardDefinitions();for(String id:List.of("swsh10-5_en_040","swsh10-5_en_073","swsh10-5_en_074")){
        check(db.find(id).orElseThrow().effectSupport()==EffectSupport.UNSUPPORTED,"Partial Counter upgrade incorrectly enabled the full card");
        var a=counter(id);check(a.effectSupport()==EffectSupport.SUPPORTED&&a.baseDamage()==20&&a.energyCost().equals(Map.of(EnergyType.FIGHTING,1)),"Partial component lost its printed contract");
        var m=fixture(a,hit(),false,false,List.of());incoming(m);respond(m,a,90);
    }}
    static void nonAttackDamage(){for(String mode:List.of("counters","attack_counters","poison","burn","self")){
        var m=fixture();if(mode.equals("self"))m.attack(A,"self");else m.endTurn(A);
        if(mode.equals("counters"))m.placeDamageCountersByEffect(B,A,active(m,A),7);
        if(mode.equals("poison")||mode.equals("burn"))m.applySpecialConditionByEffect(B,A,mode.equals("poison")?SpecialCondition.POISONED:SpecialCondition.BURNED);
        if(mode.equals("attack_counters"))m.attack(B,"placed");else m.endTurn(B);
        check(m.activePokemonView(A).damage()>0,"Non-attack fixture did not cause any damage counters");respond(m,counter(),10);
    }}
    static void movement(){for(boolean fromBench:List.of(false,true)){
        var m=fixture();UUID target=fromBench?bench(m,A):active(m,A);if(fromBench)pay(m,A,target,1);
        m.endTurn(A);if(fromBench)m.attack(B,"snipe",target);else m.attack(B,"hit");
        if(fromBench)m.switchActiveByEffect(A,A,target);else{m.switchActiveByEffect(A,A,bench(m,A));m.switchActiveByEffect(A,A,target);}
        respond(m,counter(),80);
    }}
    static void formsAndReentry(){for(String mode:List.of("evolve","devolve","devolve_reevolve","reentry","other_pokemon")){
        var m=fixture();UUID host=active(m,A);UUID physical=PokemonIdentityTestSupport.physical(m,A,host);var evolution=hand(m,A,"evolved");if(mode.startsWith("devolve"))m.evolveByEffect(A,host,evolution.instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
        incoming(m);
        if(mode.equals("evolve"))m.evolveByEffect(A,host,evolution.instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
        if(mode.startsWith("devolve")){m.devolvePokemonToHandByEffect(A,A,host);if(mode.equals("devolve_reevolve"))m.evolveByEffect(A,host,evolution.instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);}
        if(mode.equals("reentry")){m.switchActiveByEffect(A,A,bench(m,A));m.movePokemonByEffect(A,A,host,LeavePlayDestination.HAND);m.playBasicPokemonToBench(A,physical);UUID returned=PokemonIdentityTestSupport.logical(m,A,physical);check(!returned.equals(host),"Reentry reused previous stay identity");m.switchActiveByEffect(A,A,returned);pay(m,A,returned,1);}
        if(mode.equals("other_pokemon")){UUID other=bench(m,A);pay(m,A,other,1);m.switchActiveByEffect(A,A,other);}
        respond(m,counter(),10);
    }}
    static void extra(ServerMatch m,UUID actor,UUID victim){m.attack(actor,"extra");var p=m.effectPokemonPrompt(actor);m.chooseEffectPokemon(actor,p.resolutionId(),p.step(),victim);while(m.phase()==MatchPhase.PRIZE_SELECTION)m.takePrizeAt(actor,0);check(m.currentPlayer().equals(actor)&&m.phase()==MatchPhase.TURN,"Extra turn fixture failed");}
    static void splitDamageAndAttackDevolution(){
        var split=synthetic("hit",70,"FIRE",",\"damageTarget\":\"OPPONENT_BENCH\",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[{\"type\":\"bench_damage\",\"target\":\"SELECT_OPPONENT_POKEMON\",\"amount\":30}]}]");
        var m=fixture(counter(),split,false,false,List.of());UUID target=bench(m,A);pay(m,A,target,1);m.endTurn(A);m.attack(B,"hit",target);var prompt=m.effectPokemonPrompt(B);m.chooseEffectPokemon(B,prompt.resolutionId(),prompt.step(),target);m.switchActiveByEffect(A,A,target);check(m.activePokemonView(A).damage()==100,"Two attack hits did not both apply");respond(m,counter(),110);
        var devolve=synthetic("hit",70,"COLORLESS",",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[{\"type\":\"devolve\",\"target\":\"OPPONENT_ACTIVE\"}]}]");
        var n=fixture(counter(),devolve,false,false,List.of());n.evolveByEffect(A,active(n,A),hand(n,A,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);incoming(n);check(n.activePokemonView(A).topCardId().equals("a")&&n.activePokemonView(A).damage()==70,"Damage-then-devolve fixture wrong");respond(n,counter(),10);
    }
    static void extraTurns(){for(boolean hitAgain:List.of(false,true)){
        var m=fixture();m.endTurn(A);extra(m,B,bench(m,A));if(hitAgain)m.attack(B,"hit");else m.endTurn(B);respond(m,counter(),hitAgain?80:10);
    }var own=fixture();incoming(own);extra(own,A,bench(own,B));respond(own,counter(),80);}
    static void copiedHistory(){
        var m=fixture();pay(m,B,active(m,B),1);m.attack(A,counter().id());m.attack(B,counter().id());
        check(m.activePokemonView(A).damage()==20&&m.activePokemonView(B).damage()==10,"Copy fixture did not create distinct real attack histories");
        var choice=m.copyAttackOptions(A,"copy").stream().filter(c->c.attackId().equals(counter().id())).findFirst().orElseThrow();
        m.copyAttackWithEnergyDiscards(A,"copy",choice.pokemonId(),choice.entrySequence(),choice.attackId(),List.of(),List.of());
        check(m.lastAttack().finalDamage()==30&&m.lastAttack().attacker().equals(A),"Copied Counter used original source owner's history");
    }
    static void schema(){var e=JsonParser.parseString("{\"type\":\"damage_from_last_opponent_attack\",\"target\":\"SELF\"}").getAsJsonObject();for(var kv:Map.of("amount","70","turn","4","target","OPPONENT_ACTIVE").entrySet()){var bad=e.deepCopy();bad.addProperty(kv.getKey(),kv.getValue());reject(()->EffectRegistry.standard().parse(bad));}var child=e.deepCopy();child.add("effects",JsonParser.parseString("[{\"type\":\"draw\",\"amount\":1}]"));reject(()->EffectRegistry.standard().parse(child));var effect=EffectRegistry.standard().parse(e);for(var t:List.of(EffectTrigger.BEFORE_ATTACK,EffectTrigger.AFTER_ATTACK,EffectTrigger.ONCE_PER_TURN,EffectTrigger.ON_CARD_PLAYED)){var p=new EffectProgram(List.of(new TriggeredEffects(t,List.of(),List.of(effect))));reject(()->ResumableProgramRules.validate(p,t!=EffectTrigger.ON_CARD_PLAYED,t));}
        var m=fixture();var before=m.clientView(A);var rng=m.randomTranscript();reject(()->m.attack(A,"forged"));check(m.clientView(A).equals(before)&&m.randomTranscript().equals(rng),"Invalid attack changed history/field");
    }
}
