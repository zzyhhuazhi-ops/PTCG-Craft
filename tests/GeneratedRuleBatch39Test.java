import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Real continuous card rules must query source/partner presence, suppression and recipient position live. */
public final class GeneratedRuleBatch39Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch13Test.hand(m,p,id);}
    static UUID add(ServerMatch m,UUID p,String id){var c=hand(m,p,id);m.playBasicPokemonToBench(p,c.instanceId());return c.instanceId();}
    static void evolve(ServerMatch m,UUID p,UUID host,String id){m.evolveByEffect(p,host,hand(m,p,id).instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);}
    static AttackDefinition hit(String extra){return BundledCombatData.attack(JsonParser.parseString("{\"id\":\"hit\",\"nameZh\":\"Hit\",\"energyCost\":{},\"baseDamage\":30,\"damageType\":\"FIRE\",\"effectSupport\":\"SUPPORTED\""+extra+"}").getAsJsonObject(),EffectRegistry.standard());}
    public static void main(String[] args){weakness();weaknessExceptions();healing();healingEntries();healAttacks();schema();System.out.println("GeneratedRuleBatch39Test passed: exact live partner and both Active weakness, duplicate/suppressed/removed sources, resistance order, immunity and no-weakness, Active-only healing block across manual/composed/attack paths, real-heal consequences and counter-movement exception");}
    static void weakness(){for(UUID attacker:List.of(A,B))for(boolean benched:List.of(false,true))for(String mode:List.of("missing","wrong","opponent","present","evolved","suppressed","removed","duplicate")){
        var m=fixture("me5-5_en_004",hit(""));UUID source=active(m,A);
        if(benched)m.switchActiveByEffect(A,A,add(m,A,"foe"));
        if(mode.equals("wrong"))add(m,A,"wrong");
        if(!Set.of("missing","wrong","opponent").contains(mode)){UUID partner=add(m,A,"partner");if(mode.equals("evolved"))evolve(m,A,partner,"evolved");if(mode.equals("removed"))m.movePokemonByEffect(A,A,partner,LeavePlayDestination.HAND);}
        if(mode.equals("suppressed"))m.disableAbilityEffectsByEffect(A,A,source);if(mode.equals("duplicate"))add(m,A,"source");
        if(mode.equals("opponent")){m.endTurn(A);add(m,B,"partner");m.endTurn(B);}if(attacker.equals(B))m.endTurn(A);
        m.attack(attacker,"hit");check(m.lastAttack().finalDamage()==(Set.of("present","duplicate").contains(mode)?70:40),"Weakness must be x3 before Resistance, both Active, exact own partner; must not multiply duplicate sources: "+mode);
    }}
    static void weaknessExceptions(){for(String mode:List.of("bench","ignore","none","immune","mismatch")){
        String extra=mode.equals("bench")?",\"damageTarget\":\"ALL_OPPONENT_BENCH\"":mode.equals("ignore")?",\"ignoresWeakness\":true":"";var attack=hit(extra);if(mode.equals("mismatch")){var e=JsonParser.parseString("{\"id\":\"hit\",\"nameZh\":\"Hit\",\"energyCost\":{},\"baseDamage\":30,\"damageType\":\"WATER\",\"effectSupport\":\"SUPPORTED\"}").getAsJsonObject();attack=BundledCombatData.attack(e,EffectRegistry.standard());}
        var m=fixture("me5-5_en_004",attack);add(m,A,"partner");m.endTurn(A);UUID bench=add(m,B,"guard");if(mode.equals("immune"))m.switchActiveByEffect(B,B,bench);if(mode.equals("none")){var card=hand(m,B,"special");m.attachEnergyByEffect(B,B,EffectSourceZone.HAND,card.instanceId(),B,active(m,B));}m.endTurn(B);m.attack(A,"hit");check(m.lastAttack().finalDamage()==(mode.equals("immune")?40:Set.of("bench","mismatch").contains(mode)?30:10),"Weakness aura bypassed damage scope, type, immunity or no-weakness: "+mode);
    }}
    static void healing(){for(String mode:List.of("active","source-benched","suppressed","removed","evolved","guarded","target-benched","duplicate")){
        var m=fixture("me5-5_en_100",hit(""));UUID source=active(m,A);if(mode.equals("source-benched")||mode.equals("removed")||mode.equals("evolved"))m.switchActiveByEffect(A,A,add(m,A,"foe"));if(mode.equals("duplicate")){add(m,A,"source");m.disableAbilityEffectsByEffect(A,A,source);}if(mode.equals("suppressed"))m.disableAbilityEffectsByEffect(A,A,source);if(mode.equals("removed"))m.movePokemonByEffect(A,A,source,LeavePlayDestination.HAND);if(mode.equals("evolved"))evolve(m,A,source,"source_evolved");
        m.endTurn(A);UUID bench=add(m,B,mode.equals("guarded")?"guard":"foe");if(mode.equals("guarded"))m.switchActiveByEffect(B,B,bench);UUID target=active(m,B);m.placeDamageCountersByEffect(B,B,target,10);if(mode.equals("target-benched"))m.switchActiveByEffect(B,B,bench);m.healDamageByEffect(B,B,target,30);
        var view=mode.equals("target-benched")?m.benchPokemonView(B).stream().filter(v->v.pokemonId().equals(target)).findFirst().orElseThrow():m.activePokemonView(B);check(view.damage()==(Set.of("active","source-benched","duplicate").contains(mode)?100:70),"Healing lock ignored source/target lifecycle or Ability guard: "+mode);
        m.placeDamageCountersByEffect(B,A,active(m,A),6);m.healDamageByEffect(B,A,active(m,A),20);check(m.activePokemonView(A).damage()==40,"Opponent-only healing lock also blocked its owner's Pokemon");
    }}
    static void healingEntries(){var m=fixture("me5-5_en_100",hit(""));m.endTurn(A);UUID target=active(m,B),helper=add(m,B,"healer"),bench=add(m,B,"foe");m.placeDamageCountersByEffect(B,B,target,10);var potion=hand(m,B,"potion");var max=hand(m,B,"max");
        reject(()->m.playTrainer(B,potion.instanceId(),target));reject(()->m.playTrainer(B,max.instanceId(),target));check(m.privateView(B).hand().containsAll(List.of(potion,max)),"No-effect healing consumed Trainer");check(!m.canUseAbility(B,helper,"legacy",target),"Legacy healing Ability preflight ignored lock");reject(()->m.useAbility(B,helper,"legacy",target));
        m.switchActiveByEffect(B,B,helper);m.placeDamageCountersByEffect(B,B,helper,10);check(!m.canUseAbility(B,helper,"healing"),"Composed self-heal preflight ignored lock");reject(()->m.useAbility(B,helper,"healing"));m.moveDamageCountersByEffect(B,B,helper,B,bench,2);check(m.activePokemonView(B).damage()==80,"Moving counters was incorrectly treated as healing");
        m.disableAbilityEffectsByEffect(B,A,active(m,A));check(m.canUseAbility(B,helper,"legacy",target)&&m.canUseAbility(B,helper,"healing"),"Healing did not become available after suppression or rejected activation consumed quota");m.useAbility(B,helper,"healing");check(m.activePokemonView(B).damage()==50,"Restored composed healing failed");
    }
    static void healAttacks(){for(boolean composed:List.of(false,true)){
        var a=hit(composed?",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[{\"type\":\"heal\",\"target\":\"ALL_OWN_POKEMON\",\"mode\":\"ALL\",\"discard_energy_if_healed\":true}]}]":",\"selfHealAfterAttack\":30");var m=fixture("me5-5_en_100",a);m.endTurn(A);UUID bench=add(m,B,"foe");m.placeDamageCountersByEffect(B,B,active(m,B),10);m.placeDamageCountersByEffect(B,B,bench,10);for(UUID host:List.of(active(m,B),bench)){var c=hand(m,B,"energy");m.attachEnergyByEffect(B,B,EffectSourceZone.HAND,c.instanceId(),B,host);}m.attack(B,"hit");check(m.activePokemonView(B).damage()==100&&m.activePokemonView(B).attachedEnergyIds().size()==1,"Attack healing bypassed lock or discarded Energy despite zero healing");check(m.benchPokemonView(B).getFirst().damage()==(composed?0:100)&&m.benchPokemonView(B).getFirst().attachedEnergyIds().size()==(composed?0:1),"Whole-field heal must still heal Bench and apply consequence only to actually healed hosts");
    }}
    static void schema(){for(String effect:List.of("{\"type\":\"prevent_healing\",\"target\":\"SELF\"}","{\"type\":\"prevent_healing\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":1}","{\"type\":\"weakness_multiplier\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":5}","{\"type\":\"weakness_multiplier\",\"target\":\"ALL_OPPONENT_POKEMON\",\"amount\":3}"))reject(()->EffectRegistry.standard().parse(JsonParser.parseString(effect).getAsJsonObject()));}
    static ServerMatch fixture(String id,AttackDefinition attack){
        var ability=GeneratedRuleBatch8Test.ability(id);var heal=BundledCombatData.ability(JsonParser.parseString("{\"id\":\"healing\",\"nameZh\":\"Heal\",\"kind\":\"ACTIVATED_ONCE_PER_TURN\",\"effectSupport\":\"SUPPORTED\",\"effects\":[{\"type\":\"heal\",\"target\":\"SELF\",\"amount\":30}]}").getAsJsonObject(),EffectRegistry.standard());
        var legacy=new AbilityDefinition("legacy","Heal target",new AbilityEffect(AbilityEffect.Opcode.HEAL_OWN_POKEMON,20),EffectSupport.SUPPORTED,null);var guard=BundledCombatData.ability(JsonParser.parseString("{\"id\":\"guard\",\"nameZh\":\"Guard\",\"kind\":\"PASSIVE\",\"effectSupport\":\"SUPPORTED\",\"effects\":[{\"type\":\"passive_effect_guard\",\"source\":\"ABILITY\",\"from\":\"OPPONENT\",\"scope\":\"DAMAGE_AND_EFFECTS\"}]}").getAsJsonObject(),EffectRegistry.standard());
        var potion=BundledCombatData.trainer(JsonParser.parseString("{\"trainerCategory\":\"ITEM\",\"effects\":[{\"type\":\"heal\",\"target\":\"SELECT_OWN_POKEMON\",\"amount\":30}]}").getAsJsonObject(),EffectRegistry.standard());
        String[] ids={"source","foe","partner","wrong","healer","guard","evolved","source_evolved","potion","max","energy","energy","special","source","foe"};var names=Map.of("source",id.endsWith("004")?"Illumise":"Yveltal","foe","Other","partner","Volbeat","wrong","Volbeat ex","healer","Healer","guard","Guard","evolved","Other Evolution","source_evolved","Source Evolution");
        var defs=new ArrayList<CardDefinition>();var profiles=new HashMap<String,PokemonProfile>();for(String x:Arrays.stream(ids).distinct().toList()){
            boolean pokemon=names.containsKey(x),trainer=x.equals("potion")||x.equals("max");defs.add(new CardDefinition(x,x,pokemon?CardCategory.POKEMON:trainer?CardCategory.TRAINER:CardCategory.ENERGY,trainer?TrainerCategory.ITEM:TrainerCategory.NONE,"J",Set.of("international"),pokemon&&!x.equals("evolved")&&!x.equals("source_evolved"),x.equals("energy"),60,Map.of(),EffectSupport.SUPPORTED,null));
            if(pokemon)profiles.put(x,new PokemonProfile(500,1,x.equals("evolved")?"partner":x.equals("source_evolved")?"source":null,EnergyType.FIRE,2,EnergyType.FIRE,20,1,List.of(attack),x.equals("source")?List.of(ability):x.equals("healer")?List.of(heal,legacy):x.equals("guard")?List.of(guard):List.of(),EnergyType.FIRE,names.get(x),x.equals("evolved")?"Volbeat":x.equals("source_evolved")?names.get("source"):null));
        }
        var special=BundledCombatData.specialEnergy(JsonParser.parseString("{\"energyUnits\":[[\"COLORLESS\"]],\"effects\":[{\"type\":\"no_weakness\"}]}").getAsJsonObject(),EffectRegistry.standard());var catalog=new CombatCatalog(profiles,Map.of("energy",EnergyType.FIRE),Map.of("potion",potion,"max",GeneratedRuleBatch8Test.trainer("sm2_en_128")),Map.of("special",special));
        for(int seed=0;seed<1000;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID owner:List.of(A,B)){var cs=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cs.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,ids[i%ids.length]));decks.add(cs);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),catalog,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().noneMatch(c->c.definitionId().equals(p.equals(A)?"source":"foe"))))continue;
            for(UUID p:List.of(A,B)){m.chooseOpeningActive(p,hand(m,p,p.equals(A)?"source":"foe").instanceId());m.startAfterOpening(p);}OpeningTestSupport.complete(m,A,B,0);m.drawCardsByEffect(A,A,30);m.drawCardsByEffect(A,B,30);m.endTurn(A);m.endTurn(B);
            if(List.of(A,B).stream().allMatch(p->Arrays.stream(ids).allMatch(x->m.privateView(p).hand().stream().anyMatch(c->c.definitionId().equals(x)))&&m.privateView(p).hand().stream().filter(c->c.definitionId().equals("energy")).count()>=2))return m;
        }throw new AssertionError("No batch39 fixture");
    }
}
