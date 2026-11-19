import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Next-opponent-turn player storage, with recipient effects evaluated live in both damage paths. */
public final class TurnDamageReductionTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final String EFFECT="{\"type\":\"turn_damage_reduction\",\"target\":\"SELF\",\"amount\":30,\"pokemon_types\":\"METAL\",\"duration\":\"OPPONENT_NEXT_TURN\"}";
    static void check(boolean b,String s){GeneratedRuleBatch8Test.check(b,s);}
    static void atomic(ServerMatch m,Runnable r){TrainerEndTurnTest.atomic(m,r);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch13Test.hand(m,p,id);}
    static AttackDefinition hit(int damage,EnergyType type,String extra){return TemporaryReceivedDamageTest.attack("hit",damage,type,extra);}
    static TrainerProfile trainer(){return BundledCombatData.trainer(JsonParser.parseString("{\"trainerCategory\":\"ITEM\",\"effects\":["+EFFECT+"]}").getAsJsonObject(),EffectRegistry.standard());}
    static class Config {TrainerProfile trainer=trainer();String id="item";CardDefinition definition;Set<EnergyType> types=Set.of(EnergyType.METAL),evolvedTypes=Set.of(EnergyType.METAL);AttackDefinition hit=hit(100,EnergyType.COLORLESS,"");List<AbilityDefinition> abilities=List.of();int seed=0;boolean later=true;}
    static ServerMatch game(){return game(new Config());}
    static ServerMatch game(Config c){
        var q=GeneratedRuleBatch8Test.entry("me5-5_en_084").getAsJsonArray("attacks").get(0).getAsJsonObject().deepCopy();q.add("energyCost",new JsonObject());
        var extra=TemporaryReceivedDamageTest.attack("extra",40,EnergyType.COLORLESS,",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[{\"type\":\"damage_counter\",\"target\":\"SELECT_OPPONENT_POKEMON\",\"amount\":50,\"extra_turn_on_ko\":true,\"previous_turn_attack_ids\":\"extra\"}]}]");
        var attacks=List.of(c.hit,extra,BundledCombatData.attack(q,EffectRegistry.standard()),CopyAttackTest.copier());var defs=new ArrayList<CardDefinition>();var profiles=new HashMap<String,PokemonProfile>();
        for(String id:List.of("own","own_evolved","opponent","opponent_evolved","energy",c.id)){
            boolean energy=id.equals("energy"),trainer=id.equals(c.id),evolved=id.endsWith("evolved"),opponent=id.startsWith("opponent");var types=evolved?c.evolvedTypes:c.types;
            defs.add(trainer&&c.definition!=null?c.definition:new CardDefinition(id,id,trainer?CardCategory.TRAINER:energy?CardCategory.ENERGY:CardCategory.POKEMON,trainer?TrainerCategory.ITEM:TrainerCategory.NONE,"J",Set.of("international"),!energy&&!trainer&&!evolved,energy,60,Map.of(),EffectSupport.SUPPORTED,null,Set.of(),energy||trainer?Set.of():types));
            if(!energy&&!trainer)profiles.put(id,new PokemonProfile(1000,0,evolved?(opponent?"opponent":"own"):null,EnergyType.GRASS,2,EnergyType.FIGHTING,30,1,attacks,c.abilities,types.iterator().next()));
        }
        var catalog=new CombatCatalog(profiles,Map.of("energy",EnergyType.COLORLESS),Map.of(c.id,c.trainer));
        for(int seed=c.seed;seed<c.seed+1000;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID owner:List.of(A,B)){var cards=new ArrayList<CardInstance>();String basic=owner.equals(A)?"own":"opponent";for(int i=0;i<60;i++){String id=i<16?basic:i<24?basic+"_evolved":i<28?c.id:"energy";cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,id));}decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),catalog,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(x->m.cardDefinition(x.definitionId()).basicPokemon()).count()<2))continue;
            OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,26);m.drawCardsByEffect(A,B,26);if(c.later){m.endTurn(A);m.endTurn(B);}
            if(List.of(A,B).stream().allMatch(p->m.privateView(p).hand().stream().filter(x->x.definitionId().equals(c.id)).count()>=2&&m.privateView(p).hand().stream().anyMatch(x->x.definitionId().endsWith("evolved"))&&m.privateView(p).hand().stream().anyMatch(x->m.cardDefinition(x.definitionId()).basicPokemon())))return m;
        }throw new AssertionError("No player defense fixture");
    }
    static CardInstance play(ServerMatch m,UUID owner,String id){var card=hand(m,owner,id);m.playTrainer(owner,card.instanceId(),null);return card;}
    static void evolve(ServerMatch m,UUID owner){m.evolveByEffect(m.currentPlayer(),active(m,owner),hand(m,owner,owner.equals(A)?"own_evolved":"opponent_evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);}
    public static void main(String[] args){layers();liveRecipients();ownershipAndExpiry();extraTurns();attempts();schema();System.out.println("TurnDamageReductionTest passed: player-held next-opponent-turn protection, stacked after-WR reduction, Active/Bench/secondary paths, live types/evolution/reentry, no-Metal use, damage counters/own damage exclusion, bypass, extra-turn expiry, Trainer-attempt gate and strict entry validation");}
    static void layers(){for(String mode:List.of("PLAIN","STACK","WEAK","RESIST","ZERO","LOW","BYPASS","BENCH","SECONDARY","COUNTERS")){
        var c=new Config();var type=mode.equals("WEAK")?EnergyType.GRASS:mode.equals("RESIST")?EnergyType.FIGHTING:EnergyType.COLORLESS;int base=mode.equals("ZERO")||mode.equals("COUNTERS")?0:mode.equals("LOW")?20:100;
        String extra=switch(mode){case "BYPASS"->",\"ignoresActiveDefenderDamageEffects\":true";case "BENCH"->",\"damageTarget\":\"ALL_OPPONENT_POKEMON\"";case "SECONDARY"->",\"ignoresActiveDefenderDamageEffects\":true,\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[{\"type\":\"bench_damage\",\"target\":\"SELECT_OPPONENT_POKEMON\",\"amount\":100}]}]";case "COUNTERS"->",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":10}]}]";default->"";};c.hit=hit(base,type,extra);var m=game(c);play(m,A,c.id);if(mode.equals("STACK"))play(m,A,c.id);m.endTurn(A);m.attack(B,"hit");if(mode.equals("SECONDARY")){var p=m.effectPokemonPrompt(B);m.chooseEffectPokemon(B,p.resolutionId(),p.step(),bench(m,A));}
        int expected=switch(mode){case "STACK","RESIST"->40;case "WEAK"->170;case "ZERO","LOW"->0;case "BYPASS","SECONDARY","COUNTERS"->100;default->70;};check(m.activePokemonView(A).damage()==expected,"Wrong damage layer "+mode+" "+m.activePokemonView(A).damage());if(Set.of("BENCH","SECONDARY").contains(mode))check(m.benchPokemonView(A).getFirst().damage()==70,"Bench missed global reduction or inherited Active bypass");
    }}
    static void liveRecipients(){for(String mode:List.of("NONMETAL","EVOLVE_TO_METAL","EVOLVE_AWAY","DUAL","SWITCH","LATE","REENTER")){
        var c=new Config();if(Set.of("NONMETAL","EVOLVE_TO_METAL").contains(mode))c.types=Set.of(EnergyType.FIRE);if(mode.equals("EVOLVE_AWAY"))c.evolvedTypes=Set.of(EnergyType.FIRE);if(mode.equals("DUAL"))c.types=Set.of(EnergyType.FIRE,EnergyType.METAL);var m=game(c);play(m,A,c.id);
        if(mode.startsWith("EVOLVE"))evolve(m,A);
        if(mode.equals("SWITCH")){UUID old=active(m,A);m.switchActiveByEffect(A,A,bench(m,A));m.switchActiveByEffect(A,A,old);}
        if(mode.equals("LATE")){var card=hand(m,A,"own");m.playBasicPokemonToBench(A,card.instanceId());m.switchActiveByEffect(A,A,PokemonIdentityTestSupport.logical(m,A,card.instanceId()));}
        if(mode.equals("REENTER")){UUID old=bench(m,A),physical=PokemonIdentityTestSupport.physical(m,A,old);m.movePokemonByEffect(A,A,old,LeavePlayDestination.HAND);m.playBasicPokemonToBench(A,physical);m.switchActiveByEffect(A,A,PokemonIdentityTestSupport.logical(m,A,physical));}
        m.endTurn(A);m.attack(B,"hit");check(m.activePokemonView(A).damage()==(Set.of("NONMETAL","EVOLVE_AWAY").contains(mode)?100:70),"Recipient snapshot/type/position mistake "+mode);
    }}
    static void ownershipAndExpiry(){var c=new Config();c.hit=hit(100,EnergyType.COLORLESS,",\"damageTarget\":\"ALL_POKEMON\"");var m=game(c);play(m,A,c.id);m.endTurn(A);play(m,B,c.id);m.attack(B,"hit");check(m.activePokemonView(A).damage()==70&&m.activePokemonView(B).damage()==100,"Own damage or player ownership wrong");m.attack(A,"hit");check(m.activePokemonView(A).damage()==170&&m.activePokemonView(B).damage()==170,"Expiry/new opposing window wrong");
        var n=game();play(n,A,"item");n.endTurn(A);n.endTurn(B);n.endTurn(A);n.attack(B,"hit");check(n.activePokemonView(A).damage()==100,"Unused reduction survived opponent end");}
    static void extraTurns(){var m=game();play(m,A,"item");m.placeDamageCountersByEffect(A,B,bench(m,B),50);GeneratedRuleBatch48Test.extra(m,A,bench(m,B));m.endTurn(A);m.attack(B,"hit");check(m.activePokemonView(A).damage()==70,"Own extra turn consumed future protection");var n=game();play(n,A,"item");n.endTurn(A);n.placeDamageCountersByEffect(B,A,bench(n,A),50);GeneratedRuleBatch48Test.extra(n,B,bench(n,A));check(n.activePokemonView(A).damage()==10,"First opposing turn lacks reduction");n.attack(B,"hit");check(n.activePokemonView(A).damage()==110,"Second opposing extra turn retained expired protection");}
    static void attempts(){boolean heads=false,tails=false;for(int seed=0;seed<1000&&!(heads&&tails);seed++){var c=new Config();c.seed=seed;var m=game(c);m.endTurn(A);m.attack(B,"quaking-fist");int initial=m.activePokemonView(A).damage(),rng=m.randomTranscript().size();play(m,A,c.id);boolean ok=GeneratedRuleBatch52Test.heads(m,rng);heads|=ok;tails|=!ok;m.endTurn(A);m.attack(B,"hit");check(m.activePokemonView(A).damage()-initial==(ok?70:100),"Rejected Trainer attempt installed protection");}check(heads&&tails,"Missing attempt outcomes");}
    static void schema(){var original=JsonParser.parseString(EFFECT).getAsJsonObject();for(var row:Map.of("amount","0","pokemon_types","","duration","THIS_TURN","target","ALL_OWN_POKEMON","extra","true").entrySet()){var j=original.deepCopy();j.addProperty(row.getKey(),row.getValue());GeneratedRuleBatch8Test.reject(()->EffectRegistry.standard().parse(j));}for(int amount:List.of(-30,5,1001)){var j=original.deepCopy();j.addProperty("amount",amount);GeneratedRuleBatch8Test.reject(()->EffectRegistry.standard().parse(j));}var effect=EffectRegistry.standard().parse(original);for(var trigger:List.of(EffectTrigger.BEFORE_ATTACK,EffectTrigger.AFTER_ATTACK,EffectTrigger.ONCE_PER_TURN)){var p=new EffectProgram(List.of(new TriggeredEffects(trigger,List.of(),List.of(effect))));GeneratedRuleBatch8Test.reject(()->ResumableProgramRules.validate(p,true,trigger));}var m=game();atomic(m,()->m.executeEffectProgramByRule(A,null,new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_CARD_PLAYED,List.of(),List.of(effect)))),EffectTrigger.ON_CARD_PLAYED));}
}
