import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch74Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final List<String> IDS=List.of("swsh10_en_152","swsh4_en_160","xy6_en_095");
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static UUID other(UUID p){return p.equals(A)?B:A;}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return DynamicPassiveModifierTest.hand(m,p,id);}
    static TrainerProfile profile(String id){return NonPokemonBatch2Test.DATA.trainer(id).orElseThrow();}
    static AbilityDefinition passive(String effects){return BundledCombatData.ability(JsonParser.parseString("{\"id\":\"passive\",\"nameZh\":\"Test\",\"kind\":\"PASSIVE\",\"effectSupport\":\"SUPPORTED\",\"effects\":["+effects+"]}").getAsJsonObject(),EffectRegistry.standard());}
    static GeneratedRuleBatch28Test.Config config(String id,AttackDefinition attack,Set<PokemonFamily> families){var c=new GeneratedRuleBatch28Test.Config();c.trainer=profile(id);c.attack=attack;c.defenderAttack=attack;c.ownFamilies=families;c.defenderFamilies=families;c.ownWeakness=EnergyType.COLORLESS;c.weakness=true;c.defenderWeakness=EnergyType.COLORLESS;return c;}
    static ServerMatch fixture(GeneratedRuleBatch28Test.Config c,UUID p){var m=GeneratedRuleBatch28Test.fixture(c);if(p.equals(B))m.endTurn(A);return m;}
    static void equip(ServerMatch m,UUID p,UUID host){m.playTrainer(p,hand(m,p,"trainer").instanceId(),host);}
    static ServerMatch aiGame(String id){return fixture(config(id,DamageTriggeredAbilityTest.attack(100,",\"damageType\":\"COLORLESS\",\"damageTarget\":\"ALL_OPPONENT_POKEMON\""),Set.of(PokemonFamily.V)),A);}
    public static void main(String[] args){scope();benchEffects();resistance();priority();stacking();zeroAndCounters();ownBench();schema();System.out.println("NonPokemonBatch74Test passed: both owners and positions, exact V/GX Bench targets, native/mixed/composed damage, no zero-damage or counter amplification, weakness/resistance order, x4 Ability priority, Tool suppression/removal and stacking");}
    static void scope(){for(String id:IDS)for(UUID p:List.of(A,B))for(boolean disabled:List.of(false,true))for(boolean sourceBench:List.of(false,true))for(Set<PokemonFamily> families:List.of(Set.<PokemonFamily>of(),Set.of(PokemonFamily.GX),Set.of(PokemonFamily.V,PokemonFamily.VMAX),Set.of(PokemonFamily.EX_LEGACY))){
        var attack=DamageTriggeredAbilityTest.attack(100,",\"damageType\":\"COLORLESS\",\"damageTarget\":\"ALL_OPPONENT_POKEMON\"");var m=fixture(config(id,attack,families),p);UUID host=sourceBench?bench(m,p):active(m,p);equip(m,p,host);if(disabled)m.disableToolEffectsByEffect(p,p,host);m.attack(p,"hit");NonPokemonBatch67Test.settle(m);boolean on=!disabled&&!sourceBench;int activeExpected=id.equals(IDS.getFirst())&&on?300:200;int benchExpected=id.equals(IDS.get(2))&&on?200:id.equals(IDS.get(1))&&on&&(families.contains(PokemonFamily.V)||families.contains(PokemonFamily.GX))?130:100;check(m.activePokemonView(other(p)).damage()==activeExpected&&m.benchPokemonView(other(p)).getFirst().damage()==benchExpected,"Wrong position/family/Tool availability: "+id+" "+p+" "+disabled+" "+sourceBench+" "+families);
    }}
    static void benchEffects(){for(String id:IDS)for(UUID p:List.of(A,B))for(boolean composed:List.of(false,true)){
        var attack=composed?DeckDiscardTriggeredAbilityTest.attack(10,"{\"type\":\"bench_damage\",\"target\":\"OPPONENT_BENCH\",\"amount\":30}"):GeneratedRuleBatch8Test.hit(30,true);var c=config(id,attack,Set.of(PokemonFamily.GX));if(composed){c.ownWeakness=EnergyType.METAL;c.defenderWeakness=EnergyType.METAL;}var m=fixture(c,p);equip(m,p,active(m,p));m.attack(p,"hit");NonPokemonBatch67Test.settle(m);int expected=id.equals(IDS.getFirst())?30:60;check(m.benchPokemonView(other(p)).getFirst().damage()==expected,"Native or composed Bench damage ignored attacking Tool: "+id+" "+composed);
    }}
    static void resistance(){for(boolean lens:List.of(false,true))for(boolean ignored:List.of(false,true)){
        var attack=DamageTriggeredAbilityTest.attack(50,",\"damageType\":\"GRASS\",\"damageTarget\":\"ALL_OPPONENT_BENCH\""+(ignored?",\"ignoresWeaknessAndResistance\":true":""));var m=DynamicPassiveModifierTest.fixture(profile(IDS.get(2)),DynamicPassiveModifierTest.plainStadium(),EnergyProfile.fixed(EnergyType.COLORLESS,2),attack,List.of(),20);if(lens)m.playTrainer(A,hand(m,A,"tool").instanceId(),active(m,A));m.attack(A,"hit");NonPokemonBatch67Test.settle(m);check(m.benchPokemonView(B).getFirst().damage()==(lens&&!ignored?80:50),"Wide Lens failed Weakness then Resistance or overrode explicit ignoring");
    }}
    static void priority(){for(boolean ability:List.of(false,true))for(boolean remove:List.of(false,true)){
        var c=config(IDS.getFirst(),GeneratedRuleBatch8Test.hit(100,false),Set.of());if(ability)c.ownAbility=passive("{\"type\":\"weakness_multiplier\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":4}");var m=fixture(c,A);equip(m,A,active(m,A));if(remove)m.discardToolByEffect(A,A,active(m,A),m.activePokemonView(A).attachedTools().getFirst().instanceId());m.attack(A,"hit");check(m.activePokemonView(B).damage()==(ability?400:remove?200:300),"Glasses overrode x4 Ability or persisted after removal");
    }}
    static void stacking(){var c=config(IDS.get(1),GeneratedRuleBatch8Test.hit(100,true),Set.of(PokemonFamily.GX));c.ownAbility=passive("{\"type\":\"tool_capacity\",\"target\":\"SELF\",\"amount\":2}");var m=fixture(c,A);equip(m,A,active(m,A));equip(m,A,active(m,A));m.attack(A,"hit");check(m.benchPokemonView(B).getFirst().damage()==160,"Two Telescopic Sights failed to stack");}
    static void zeroAndCounters(){for(String id:IDS){var c=config(id,DeckDiscardTriggeredAbilityTest.attack(0,"{\"type\":\"select_pokemon\",\"target\":\"SELECT_OPPONENT_POKEMON\",\"position\":\"BENCH\",\"effects\":[{\"type\":\"damage_counter\",\"target\":\"CHOSEN_POKEMON\",\"amount\":2}]}"),Set.of(PokemonFamily.V));var m=fixture(c,A);equip(m,A,active(m,A));m.attack(A,"hit");NonPokemonBatch67Test.settle(m);check(m.activePokemonView(B).damage()==0&&m.benchPokemonView(B).getFirst().damage()==20,"Offensive Tool amplified counters or zero base damage");}}
    static void ownBench(){for(String id:IDS){var m=fixture(config(id,DamageTriggeredAbilityTest.attack(100,",\"damageType\":\"COLORLESS\",\"damageTarget\":\"ALL_POKEMON\""),Set.of(PokemonFamily.V)),A);equip(m,A,active(m,A));m.attack(A,"hit");NonPokemonBatch67Test.settle(m);check(m.benchPokemonView(A).getFirst().damage()==100,"Attacking Tool affected own Bench");}}
    static void schema(){for(String effect:List.of("{\"type\":\"attack_weakness_multiplier\",\"target\":\"SELF\",\"amount\":4}","{\"type\":\"bench_weakness_resistance\",\"target\":\"ALL_OWN_POKEMON\"}","{\"type\":\"bench_attack_damage_bonus\",\"target\":\"SELF\",\"amount\":30,\"defender_pokemon_families\":\"EX\"}"))GeneratedRuleBatch8Test.reject(()->EffectRegistry.standard().parse(JsonParser.parseString(effect).getAsJsonObject()));}
}
