import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Signed self-applied received damage, scoped to the next opponent turn and actual attack source. */
public final class TemporaryReceivedDamageTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean b,String why){GeneratedRuleBatch8Test.check(b,why);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static void atomic(ServerMatch m,Runnable r){GeneratedRuleBatch44Test.assertRejectedAtomic(m,r);}
    static String policy(int amount,boolean evolution){return "{\"type\":\"modify_damage_taken\",\"target\":\"SELF\",\"amount\":"+amount+",\"duration\":\"OPPONENT_NEXT_TURN\""+(evolution?",\"attacker_filter\":\"EVOLUTION\"":"")+"}";}
    static AttackDefinition attack(String id,int damage,EnergyType type,String extra){return BundledCombatData.attack(JsonParser.parseString("{\"id\":\""+id+"\",\"nameZh\":\""+id+"\",\"energyCost\":{},\"baseDamage\":"+damage+",\"damageType\":\""+type+"\",\"effectSupport\":\"SUPPORTED\""+extra+"}").getAsJsonObject(),EffectRegistry.standard());}
    static AttackDefinition guard(int amount,boolean evolution){return attack("guard",0,EnergyType.COLORLESS,",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":["+policy(amount,evolution)+"]}]");}
    static AttackDefinition hit(int damage,EnergyType type){return attack("hit",damage,type,"");}
    static class Config {List<AttackDefinition> own;AttackDefinition reply=hit(140,EnergyType.COLORLESS);EnergyType energy=EnergyType.DARKNESS;int seedStart=0;List<AbilityDefinition> opponentAbilities=List.of();Config(AttackDefinition own){this.own=List.of(own);}}
    static ServerMatch game(AttackDefinition own,AttackDefinition reply){var c=new Config(own);c.reply=reply;return game(c);}
    static ServerMatch game(Config c){
        var defs=new ArrayList<CardDefinition>();var profiles=new HashMap<String,PokemonProfile>();
        for(String id:List.of("own","own_evolved","opponent","opponent_evolved","energy")){
            boolean energy=id.equals("energy"),opponent=id.startsWith("opponent"),evolved=id.endsWith("evolved");
            defs.add(new CardDefinition(id,id,energy?CardCategory.ENERGY:CardCategory.POKEMON,TrainerCategory.NONE,"J",Set.of("international"),!energy&&!evolved,energy,60,Map.of(),EffectSupport.SUPPORTED,null,Set.of(),energy?Set.of():Set.of(opponent?EnergyType.WATER:EnergyType.DARKNESS)));
            if(!energy)profiles.put(id,new PokemonProfile(500,0,evolved?(opponent?"opponent":"own"):null,opponent?null:EnergyType.GRASS,opponent?0:2,opponent?null:EnergyType.FIGHTING,opponent?0:30,1,opponent?List.of(c.reply):c.own,opponent?c.opponentAbilities:List.of(),opponent?EnergyType.WATER:EnergyType.DARKNESS));
        }
        var catalog=new CombatCatalog(profiles,Map.of("energy",c.energy));
        for(int seed=c.seedStart;seed<c.seedStart+1000;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID p:List.of(A,B)){String basic=p.equals(A)?"own":"opponent";var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(p.getLeastSignificantBits(),i+1),p,i%10<4?basic:i%10<6?basic+"_evolved":"energy"));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),catalog,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(x->m.cardDefinition(x.definitionId()).basicPokemon()).count()<2))continue;
            OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,28);m.drawCardsByEffect(A,B,28);m.endTurn(A);m.endTurn(B);
            if(List.of(A,B).stream().allMatch(p->m.privateView(p).hand().stream().anyMatch(x->x.definitionId().equals(p.equals(A)?"own_evolved":"opponent_evolved"))))return m;
        }throw new AssertionError("No temporary modifier fixture");
    }
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch13Test.hand(m,p,id);}
    static void evolve(ServerMatch m,UUID p){m.evolveByEffect(p,active(m,p),hand(m,p,p.equals(A)?"own_evolved":"opponent_evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);}
    static void pay(ServerMatch m,UUID p,AttackDefinition attack){for(int i=0;i<attack.energyCost().values().stream().mapToInt(Integer::intValue).sum();i++){var card=hand(m,p,"energy");m.attachEnergyByEffect(m.currentPlayer(),p,EffectSourceZone.HAND,card.instanceId(),p,active(m,p));}}
    static void copy(ServerMatch m,UUID p,String attackId){var c=m.copyAttackOptions(p,"copy").stream().filter(x->x.attackId().equals(attackId)).findFirst().orElseThrow();m.copyAttackWithEnergyDiscards(p,"copy",c.pokemonId(),c.entrySequence(),c.attackId(),List.of(),List.of());}
    static BattlefieldState field(ServerMatch m){try{var f=ServerMatch.class.getDeclaredField("battlefield");f.setAccessible(true);return (BattlefieldState)f.get(m);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    static void shield(ServerMatch m,UUID player,String relation,String scope){var e=EffectRegistry.standard().parse(JsonParser.parseString("{\"type\":\"prevent_effects\",\"target\":\"OWN_ACTIVE\",\"source\":\"ATTACK\",\"from\":\""+relation+"\",\"scope\":\""+scope+"\",\"duration\":\"LEAVES_PLAY\"}").getAsJsonObject());m.executeEffectProgramByRule(player,null,new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_CARD_PLAYED,List.of(),List.of(e)))),EffectTrigger.ON_CARD_PLAYED);}
    public static void main(String[] args){ordering();zeroCoins();filtersAndCopies();expiryAndClear();benchSourceAndDamage();protection();schema();System.out.println("TemporaryReceivedDamageTest passed: signed after-W/R change, zero/negative intermediates, real evolution attacker and copies, source-bound main/Bench damage, next opponent end including extra turns, switch/evolution/leave cleanup, own/source protection and strict schema/entry authority");}
    static void ordering(){for(int amount:List.of(100,-100))for(String mode:List.of("PLAIN","WEAK","RESIST","NEGATIVE","ZERO","BYPASS","COUNTERS")){
        var type=mode.equals("WEAK")?EnergyType.GRASS:Set.of("RESIST","NEGATIVE").contains(mode)?EnergyType.FIGHTING:EnergyType.COLORLESS;int base=mode.equals("ZERO")||mode.equals("COUNTERS")?0:mode.equals("NEGATIVE")?20:140;
        var reply=attack("hit",base,type,mode.equals("BYPASS")?",\"ignoresActiveDefenderDamageEffects\":true":mode.equals("COUNTERS")?",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":2}]}]":"");
        var m=game(guard(amount,false),reply);m.attack(A,"guard");m.attack(B,"hit");int expected=mode.equals("COUNTERS")?20:(mode.equals("ZERO")||mode.equals("NEGATIVE"))?0:mode.equals("BYPASS")?140:Math.max(0,(mode.equals("WEAK")?280:mode.equals("RESIST")?110:mode.equals("NEGATIVE")?-10:140)+amount);check(m.activePokemonView(A).damage()==expected,"Damage order/zero/bypass "+amount+"/"+mode+" was "+m.activePokemonView(A).damage());
    }}
    static void zeroCoins(){boolean sawZero=false;for(int seed=0;seed<30&&!sawZero;seed++){
        var c=new Config(guard(100,false));c.seedStart=seed;c.reply=attack("hit",0,EnergyType.COLORLESS,",\"coinDamage\":{\"flips\":2,\"perHeads\":140}");var m=game(c);m.attack(A,"guard");m.attack(B,"hit");if(m.lastAttack().finalDamage()==0)sawZero=true;
    }check(sawZero,"All-tail zero coin result acquired100 damage");}
    static void filtersAndCopies(){for(boolean evolved:List.of(false,true)){var m=game(guard(-100,true),hit(140,EnergyType.COLORLESS));m.attack(A,"guard");if(evolved)evolve(m,B);m.attack(B,"hit");check(m.lastAttack().finalDamage()==(evolved?40:140),"Evolution filter wrong");}
        for(boolean evolved:List.of(false,true)){
            var actual=attack("guard",120,EnergyType.COLORLESS,",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":["+policy(-100,true)+"]}]");var m=game(actual,CopyAttackTest.copier());if(!evolved)evolve(m,A);m.attack(A,"guard");if(evolved)evolve(m,B);pay(m,B,CopyAttackTest.copier());copy(m,B,"guard");check(m.lastAttack().finalDamage()==(evolved?20:120),"Copied source stage replaced actual attacker");
        }
        var m=game(CopyAttackTest.copier(),attack("guard",140,EnergyType.COLORLESS,",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":["+policy(100,false)+"]}]"));pay(m,A,CopyAttackTest.copier());copy(m,A,"guard");check(m.activePokemonView(A).continuousEffectZh().contains("增加 100")&&m.activePokemonView(B).continuousEffectZh().isEmpty(),"Copied self effect went to original provider");m.attack(B,"guard");check(m.lastAttack().finalDamage()==240,"Copied self modifier missing");
    }
    static void expiryAndClear(){
        var m=game(guard(100,false),hit(40,EnergyType.COLORLESS));m.attack(A,"guard");m.attack(B,"hit");check(m.lastAttack().finalDamage()==140&&m.activePokemonView(A).continuousEffectZh().isEmpty(),"Modifier did not expire at opponent end");m.endTurn(A);m.attack(B,"hit");check(m.lastAttack().finalDamage()==40,"Expired modifier leaked");
        for(String mode:List.of("SWITCH","LEAVE")){
            var g=game(guard(100,false),hit(40,EnergyType.COLORLESS));g.attack(A,"guard");var original=active(g,A);
            if(mode.equals("SWITCH")){g.switchActiveByEffect(B,A,bench(g,A));g.switchActiveByEffect(B,A,original);}
            if(mode.equals("LEAVE")){g.movePokemonByEffect(B,A,original,LeavePlayDestination.HAND);StarterDeck100CombatTest.settleKnockouts(g);}
            check(!g.activePokemonView(A).continuousEffectZh().contains("增加 100"),"Effect survived "+mode);g.attack(B,"hit");check(g.lastAttack().finalDamage()==40,"Cleared modifier still changed damage "+mode);
        }
        // Exercise the same field lifecycle used by the already-tested extra-turn scheduler.
        var g=game(guard(100,false),hit(40,EnergyType.COLORLESS));var f=field(g);var p=AttackDamageReceivedModifier.from(EffectRegistry.standard().parse(JsonParser.parseString(policy(100,false)).getAsJsonObject()));f.modifyDamageReceivedNextOpponentTurn(A,active(g,A),B,p);f.endTurn(A);f.beginTurn(A);check(f.activeView(A).continuousEffectZh().contains("增加 100"),"Extra own turn prematurely expired modifier");f.endTurn(A);f.beginTurn(B);check(f.activeView(A).continuousEffectZh().contains("增加 100"),"First opponent turn lost modifier");f.endTurn(B);f.beginTurn(B);check(f.activeView(A).continuousEffectZh().isEmpty(),"Second consecutive opponent turn retained modifier");
        var e=game(guard(100,false),hit(40,EnergyType.COLORLESS));field(e).modifyDamageReceivedNextOpponentTurn(A,active(e,A),B,p);evolve(e,A);check(e.activePokemonView(A).continuousEffectZh().isEmpty(),"Evolution retained modifier before expiry");e.endTurn(A);e.attack(B,"hit");check(e.lastAttack().finalDamage()==40,"Evolved Pokemon retained prior modifier");
    }
    static void benchSourceAndDamage(){for(boolean composed:List.of(false,true))for(int amount:List.of(100,-100)){
        var guardJson=JsonParser.parseString("{\"id\":\"guard\",\"nameZh\":\"guard\",\"energyCost\":{},\"baseDamage\":0,\"effectSupport\":\"SUPPORTED\",\"mayUseFromBench\":true,\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":["+policy(amount,true)+"]}]}").getAsJsonObject();var own=BundledCombatData.attack(guardJson,EffectRegistry.standard());
        var reply=attack("hit",composed?0:140,EnergyType.GRASS,composed?",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[{\"type\":\"bench_damage\",\"target\":\"OPPONENT_BENCH\",\"amount\":140}]}]":",\"damageTarget\":\"ALL_OPPONENT_POKEMON\"");var m=game(own,reply);var source=m.benchAttackOptions(A).getFirst();m.attackFromBench(A,source.pokemonId(),source.entry(),source.topCardId(),source.attack().id());check(m.activePokemonView(A).continuousEffectZh().isEmpty()&&m.benchPokemonView(A).getFirst().continuousEffectZh().contains("进化"),"Bench self effect attached to Active");evolve(m,B);m.attack(B,"hit");check(!m.hasPendingEffectChoice(),"Whole-Bench damage left an unexpected selection");check(m.benchPokemonView(A).getFirst().damage()==Math.max(0,140+amount),"Bench damage path missed signed filter");check(m.activePokemonView(A).damage()==(composed?0:280),"Bench modifier spread to unrelated Active");
    }}
    static void protection(){
        var m=game(guard(100,false),hit(40,EnergyType.COLORLESS));shield(m,A,"OWN","EFFECTS_ONLY");m.attack(A,"guard");check(!m.activePokemonView(A).continuousEffectZh().contains("增加 100"),"Own-source immunity ignored");m.attack(B,"hit");check(m.lastAttack().finalDamage()==40,"Blocked self modifier installed");
        var c=new Config(guard(100,false));c.reply=hit(40,EnergyType.COLORLESS);c.opponentAbilities=List.of(GeneratedRuleBatch57Test.guard("me5_en_034"));m=game(c);m.attack(A,"guard");m.attack(B,"hit");check(m.lastAttack().finalDamage()==140,"Opponent Hide n Sneak wrongly stopped self effect");
    }
    static void schema(){var valid=JsonParser.parseString(policy(-100,true)).getAsJsonObject();for(var pair:Map.of("amount","0","duration","OWNER_NEXT_TURN_END","target","OPPONENT_ACTIVE","attacker_filter","BASIC","unknown","true").entrySet()){var bad=valid.deepCopy();bad.addProperty(pair.getKey(),pair.getValue());reject(()->EffectRegistry.standard().parse(bad));}for(int n:List.of(1,-11,1001,Integer.MIN_VALUE)){var bad=valid.deepCopy();bad.addProperty("amount",n);reject(()->EffectRegistry.standard().parse(bad));}var effect=EffectRegistry.standard().parse(valid);for(var trigger:List.of(EffectTrigger.BEFORE_ATTACK,EffectTrigger.ON_ATTACK,EffectTrigger.ONCE_PER_TURN,EffectTrigger.ON_CARD_PLAYED,EffectTrigger.PASSIVE)){var program=new EffectProgram(List.of(new TriggeredEffects(trigger,List.of(),List.of(effect))));reject(()->ResumableProgramRules.validate(program,trigger!=EffectTrigger.ON_CARD_PLAYED,trigger));}var m=game(guard(100,false),hit(40,EnergyType.COLORLESS));atomic(m,()->m.executeEffectProgramByRule(A,active(m,A),new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.AFTER_ATTACK,List.of(),List.of(effect)))),EffectTrigger.AFTER_ATTACK));}
}
