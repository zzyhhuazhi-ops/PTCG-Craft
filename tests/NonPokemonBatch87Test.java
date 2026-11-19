import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Actual Crystal Tools; synthetic named hosts isolate the printed name, damage and HP rules. */
public final class NonPokemonBatch87Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final List<String> IDS=List.of("bw7_en_138","bw7_en_139");
    static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    static UUID other(UUID p){return NonPokemonBatch67Test.other(p);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch28Test.hand(m,p,id);}
    static GeneratedRuleBatch28Test.Config config(String id,String name,AttackDefinition attack){
        var c=new GeneratedRuleBatch28Test.Config();c.trainer=NonPokemonBatch2Test.DATA.trainer(id).orElseThrow();c.attack=attack;c.defenderAttack=attack;
        c.names=Map.of("attacker",name,"defender",name,"evolved",name,"defender_evolved",name);return c;
    }
    static ServerMatch game(GeneratedRuleBatch28Test.Config c,UUID p){var m=GeneratedRuleBatch28Test.fixture(c);if(p.equals(B))m.endTurn(A);return m;}
    static CardInstance equip(ServerMatch m,UUID p,UUID host){var card=hand(m,p,"trainer");m.playTrainer(p,card.instanceId(),host);return card;}
    static ServerMatch aiGame(String id){var c=config(id,id.equals(IDS.getFirst())?"White Kyurem-EX":"Black Kyurem-EX",DamageTriggeredAbilityTest.attack(300,""));c.ownHp=180;c.defenderHp=180;return game(c,A);}
    public static void main(String[] args){edge();edgePositions();wall();wallLayers();wallLoss();schema();System.out.println("NonPokemonBatch87Test passed: exact current names, both owners/Active/Bench, pre-weakness recoil and mixed damage, scope and suppression; HP set to 300 before other modifiers, repeated setters, damage preservation, evolution identity and source-loss KOs");}
    static void edge(){for(UUID p:List.of(A,B))for(String mode:List.of("native","self_damage","recoil"))for(String name:List.of("White Kyurem-EX","White Kyurem","Black Kyurem-EX"))for(boolean weak:List.of(false,true))for(boolean disabled:List.of(false,true)){
        var c=config(IDS.getFirst(),name,AttackSelfDamageCalculationTest.attack(mode,30,EnergyType.COLORLESS,""));c.ownWeakness=weak?EnergyType.COLORLESS:null;c.weakness=weak;var m=game(c,p);equip(m,p,active(m,p));if(disabled)m.disableToolEffectsByEffect(p,p,active(m,p));m.attack(p,"hit");int bonus=name.equals("White Kyurem-EX")&&!disabled?50:0,multiplier=weak?2:1;
        check(m.activePokemonView(p).damage()==(30+bonus)*multiplier&&m.activePokemonView(other(p)).damage()==(40+bonus)*multiplier,"Crystal Edge wrong name/owner/suppression or damage layer");
    }}
    static void edgePositions(){for(UUID p:List.of(A,B))for(String mode:List.of("all","bench-attacker","bench-tool","evolve","zero")){
        var attack=mode.equals("all")?DamageTriggeredAbilityTest.attack(40,",\"damageTarget\":\"ALL_POKEMON\""):mode.equals("zero")?GeneratedRuleBatch8Test.hit(0,false):AttackSelfDamageCalculationTest.attack("native",30,EnergyType.COLORLESS,mode.equals("bench-attacker")?",\"mayUseFromBench\":true":"");
        var c=config(IDS.getFirst(),"White Kyurem-EX",attack);if(mode.equals("evolve"))c.names=Map.of("attacker","White Kyurem-EX","defender","White Kyurem-EX","evolved","Changed","defender_evolved","Changed");
        var m=game(c,p);UUID host=mode.startsWith("bench")?bench(m,p):active(m,p);equip(m,p,host);
        if(mode.equals("evolve"))m.evolveByEffect(p,host,hand(m,p,p.equals(A)?"evolved":"defender_evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
        if(mode.equals("bench-attacker")){var o=m.benchAttackOptions(p).getFirst();m.attackFromBench(p,o.pokemonId(),o.entry(),o.topCardId(),o.attack().id());check(m.activePokemonView(p).damage()==0&&m.benchPokemonView(p).getFirst().damage()==30&&m.activePokemonView(other(p)).damage()==90,"Crystal Edge Bench source scope wrong");}
        else {m.attack(p,"hit");int expected=mode.equals("all")?90:mode.equals("zero")?0:40;check(m.activePokemonView(other(p)).damage()==expected,"Crystal Edge persisted on wrong host/name or created zero damage");if(mode.equals("all"))check(m.activePokemonView(p).damage()==90&&m.benchPokemonView(p).getFirst().damage()==40&&m.benchPokemonView(other(p)).getFirst().damage()==40,"Crystal Edge mixed-target scope wrong");}
    }}
    static void wall(){for(UUID p:List.of(A,B))for(boolean benched:List.of(false,true))for(int printed:List.of(180,240,350))for(String name:List.of("Black Kyurem-EX","Black Kyurem","White Kyurem-EX")){
        var c=config(IDS.getLast(),name,GeneratedRuleBatch8Test.hit(40,false));c.ownHp=printed;c.defenderHp=printed;var m=game(c,p);UUID host=benched?bench(m,p):active(m,p);m.placeDamageCountersByEffect(p,p,host,10);var card=equip(m,p,host);var view=benched?m.benchPokemonView(p).getFirst():m.activePokemonView(p);
        check(view.hitPoints()==(name.equals("Black Kyurem-EX")?300:printed)&&view.damage()==100,"Crystal Wall added fixed HP or changed damage/name/position");m.discardToolByEffect(p,p,host,card.instanceId());view=benched?m.benchPokemonView(p).getFirst():m.activePokemonView(p);check(view.hitPoints()==printed&&view.damage()==100,"Crystal Wall persisted after removal");
    }}
    static void wallLayers(){for(int modifier:List.of(-20,30))for(int count:List.of(1,2)){
        var c=config(IDS.getLast(),"Black Kyurem-EX",GeneratedRuleBatch8Test.hit(40,false));c.ownHp=180;c.ownAbility=NonPokemonBatch74Test.passive("{\"type\":\"tool_capacity\",\"target\":\"SELF\",\"amount\":2},{\"type\":\"hit_points_modifier\",\"target\":\"SELF\",\"amount\":"+modifier+"}");var m=game(c,A);for(int i=0;i<count;i++)equip(m,A,active(m,A));check(m.activePokemonView(A).hitPoints()==300+modifier,"HP setting incorrectly stacked or erased other modifiers");
    }}
    static void wallLoss(){for(UUID p:List.of(A,B))for(String mode:List.of("discard","return","disable","evolve")){
        var c=config(IDS.getLast(),"Black Kyurem-EX",GeneratedRuleBatch8Test.hit(40,false));c.ownHp=180;c.defenderHp=180;c.names=Map.of("attacker","Black Kyurem-EX","defender","Black Kyurem-EX","evolved","Changed","defender_evolved","Changed");var m=game(c,p);UUID host=active(m,p);var card=equip(m,p,host);m.placeDamageCountersByEffect(p,p,host,20);
        switch(mode){case "discard"->m.discardToolByEffect(p,p,host,card.instanceId());case "return"->m.returnAttachedToolToHandByEffect(p,p,host,card.instanceId());case "disable"->m.disableToolEffectsByEffect(p,p,host);case "evolve"->m.evolveByEffect(p,host,hand(m,p,p.equals(A)?"evolved":"defender_evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);}
        check(m.phase()==MatchPhase.PRIZE_SELECTION&&m.pendingChoicePlayer().equals(other(p)),"Crystal Wall source loss failed immediate KO: "+mode);NonPokemonBatch67Test.settle(m);check(m.privateView(other(p)).prizes().size()==5,"Crystal Wall loss gave incorrect Prize count");
    }}
    static void schema(){for(String e:List.of("{\"type\":\"attack_damage_modifier\",\"amount\":50,\"direction\":\"DEALT\",\"both_active_only\":false}","{\"type\":\"attack_damage_modifier\",\"amount\":50,\"direction\":\"DEALT\",\"both_active_only\":true,\"opponent_only\":true}","{\"type\":\"hit_points_modifier\",\"amount\":120,\"set_base_hp\":true}","{\"type\":\"hit_points_modifier\",\"amount\":300,\"set_base_hp\":false}"))GeneratedRuleBatch8Test.reject(()->DynamicPassiveModifierTest.trainer("TOOL",e));
        for(String e:List.of("{\"type\":\"attack_damage_modifier\",\"amount\":50,\"direction\":\"DEALT\",\"both_active_only\":true}","{\"type\":\"hit_points_modifier\",\"amount\":300,\"set_base_hp\":true}"))GeneratedRuleBatch8Test.reject(()->NonPokemonBatch74Test.passive(e));
    }
}
