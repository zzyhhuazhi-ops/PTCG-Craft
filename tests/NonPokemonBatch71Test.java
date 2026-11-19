import com.google.gson.JsonParser;
import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch71Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final List<String> IDS=List.of("sm10_en_188","swsh5_en_130","swsh5_en_161","swsh5_en_175");
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static CardInstance hand(ServerMatch m,UUID p,String id){return DynamicPassiveModifierTest.hand(m,p,id);}
    static UUID other(UUID p){return p.equals(A)?B:A;}
    static PokemonFamily family(String id){return id.equals(IDS.getFirst())?PokemonFamily.ULTRA_BEAST:PokemonFamily.VMAX;}
    static Set<PokemonFamily> families(String id,boolean match){return match?Set.of(family(id)):Set.of(PokemonFamily.V);}
    static GeneratedRuleBatch28Test.Config config(String id,UUID p,boolean base,boolean evolved,AttackDefinition attack){var c=new GeneratedRuleBatch28Test.Config();c.trainer=NonPokemonBatch2Test.DATA.trainer(id).orElseThrow();c.attack=attack;c.defenderAttack=attack;if(p.equals(A)){c.ownFamilies=families(id,base);c.ownEvolutionFamilies=families(id,evolved);}else{c.defenderFamilies=families(id,base);c.defenderEvolutionFamilies=families(id,evolved);}return c;}
    static ServerMatch fixture(GeneratedRuleBatch28Test.Config c,UUID p){var m=GeneratedRuleBatch28Test.fixture(c);if(p.equals(B))m.endTurn(A);return m;}
    static void shield(ServerMatch m,UUID p,boolean own,boolean effects){var protection=ProtectionEffect.from(EffectRegistry.standard().parse(JsonParser.parseString("{\"type\":\"prevent_effects\",\"target\":\"SELF\",\"source\":\"ATTACK\",\"from\":\""+(own?"OWN":"OPPONENT")+"\",\"scope\":\""+(effects?"DAMAGE_AND_EFFECTS":"DAMAGE_ONLY")+"\",\"duration\":\"OPPONENT_TURN_END\"}").getAsJsonObject()));var field=EventTargetBindingTest.field(m);field.addProtection(p,p,m.activePokemonView(p).pokemonId(),protection);for(var view:m.benchPokemonView(p))field.addProtection(p,p,view.pokemonId(),protection);}
    static void play(ServerMatch m,UUID p){m.playTrainer(p,hand(m,p,"trainer").instanceId(),null);NonPokemonBatch67Test.settle(m);}
    static ServerMatch aiGame(String id){var m=fixture(config(id,A,true,true,GeneratedRuleBatch8Test.hit(100,false)),A);shield(m,B,false,false);return m;}
    public static void main(String[] args){scope();live();calculation();survival();copied();schema();System.out.println("NonPokemonBatch71Test passed: both owners and all printings, live family/new entry/evolution/switch, one-turn scope, Active-only bypass within mixed damage, preserved Bench/self/status/Weakness/attacker penalties, removed receiving modifiers, preserved Sturdy and Acme survival, copied Supporter");}
    static void scope(){for(String id:IDS)for(UUID p:List.of(A,B))for(boolean matching:List.of(false,true))for(boolean mixed:List.of(false,true)){
        var attack=mixed?DamageTriggeredAbilityTest.attack(100,",\"damageTarget\":\"ALL_OPPONENT_POKEMON\""):GeneratedRuleBatch8Test.hit(100,false);var m=fixture(config(id,p,matching,matching,attack),p);UUID opp=other(p);shield(m,opp,false,false);play(m,p);m.attack(p,"hit");NonPokemonBatch67Test.settle(m);int expected=matching?100:0;check(m.activePokemonView(opp).damage()==expected&&m.benchPokemonView(opp).getFirst().damage()==0,"Family/Active-only scope wrong "+id);
        shield(m,p,false,false);m.attack(opp,"hit");NonPokemonBatch67Test.settle(m);check(m.activePokemonView(p).damage()==0,"Opponent inherited permission");shield(m,opp,false,false);m.attack(p,"hit");NonPokemonBatch67Test.settle(m);check(m.activePokemonView(opp).damage()==expected,"Permission persisted next own turn");
    }}
    static void live(){for(String id:List.of(IDS.getFirst(),IDS.get(1)))for(UUID p:List.of(A,B))for(String mode:List.of("later","switch","gain","lose")){
        var m=fixture(config(id,p,!mode.equals("gain"),!mode.equals("lose"),GeneratedRuleBatch8Test.hit(100,false)),p);UUID opp=other(p);shield(m,opp,false,false);play(m,p);
        if(mode.equals("later")){m.playBasicPokemonToBench(p,hand(m,p,p.equals(A)?"attacker":"defender").instanceId());m.switchActiveByEffect(p,p,m.benchPokemonView(p).getLast().pokemonId());}
        if(mode.equals("switch"))m.switchActiveByEffect(p,p,m.benchPokemonView(p).getFirst().pokemonId());
        if(Set.of("gain","lose").contains(mode))m.evolveByEffect(p,m.activePokemonView(p).pokemonId(),hand(m,p,p.equals(A)?"evolved":"defender_evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
        m.attack(p,"hit");NonPokemonBatch67Test.settle(m);check(m.activePokemonView(opp).damage()==(mode.equals("lose")?0:100),"Cached family/field "+mode);
    }}
    static void calculation(){for(String id:List.of(IDS.getFirst(),IDS.get(1)))for(String mode:List.of("weakness","penalty","increase","decrease","status","self")){
        var attack=mode.equals("status")?DeckDiscardTriggeredAbilityTest.attack(100,"{\"type\":\"poison\",\"target\":\"OPPONENT_ACTIVE\"}"):DamageTriggeredAbilityTest.attack(100,",\"damageType\":\"COLORLESS\""+(mode.equals("self")?",\"selfDamage\":30":""));var c=config(id,A,true,true,attack);c.weakness=mode.equals("weakness");var m=fixture(c,A);var f=EventTargetBindingTest.field(m);
        if(mode.equals("decrease")){m.endTurn(A);f.reduceDamageFromOpponentNextTurn(B,m.activePokemonView(B).pokemonId(),A,30);m.endTurn(B);}
        else if(!mode.equals("increase"))shield(m,B,false,mode.equals("status"));
        if(mode.equals("penalty"))f.penalizeAttackDamageUntilOwnerNextTurn(B,A,m.activePokemonView(A).pokemonId(),20);
        if(mode.equals("increase"))f.increaseDamageTakenThroughOwnerNextTurn(A,B,m.activePokemonView(B).pokemonId(),30);
        if(mode.equals("self"))shield(m,A,true,false);
        play(m,A);m.attack(A,"hit");NonPokemonBatch67Test.settle(m);check(m.activePokemonView(B).damage()==(mode.equals("weakness")?200:mode.equals("penalty")?80:100),"Wrong damage calculation "+mode);if(mode.equals("status"))check(m.activePokemonView(B).conditions().isEmpty(),"Bypass cancelled attack-effect protection");if(mode.equals("self"))check(m.activePokemonView(A).damage()==0,"Bypass cancelled own recoil shield");
    }}
    static void survival(){for(String id:List.of(IDS.getFirst(),IDS.get(1)))for(boolean sturdy:List.of(false,true)){
        var c=config(id,B,true,true,GeneratedRuleBatch8Test.hit(1000,false));c.ownAbility=sturdy?GeneratedRuleBatch8Test.ability("sm8_en_112"):null;c.attack=DeckDiscardTriggeredAbilityTest.attack(0,"{\"type\":\"survive_next_turn_attack_damage\",\"target\":\"SELF\",\"remaining_hp\":10,\"requires_full_hp\":true,\"duration\":\"OPPONENT_NEXT_TURN\"}");var m=GeneratedRuleBatch28Test.fixture(c);UUID target=m.activePokemonView(A).pokemonId();if(sturdy)m.endTurn(A);else m.attack(A,"hit");play(m,B);m.attack(B,"hit");NonPokemonBatch67Test.settle(m);check(m.phase()==MatchPhase.TURN&&m.activePokemonView(A).pokemonId().equals(target)&&m.activePokemonView(A).damage()==990,"Trainer bypass erased post-damage survival "+sturdy);
    }}
    static void copied(){var c=config(IDS.getFirst(),A,true,true,DeckDiscardTriggeredAbilityTest.attack(10,"{\"type\":\"use_top_supporter\",\"target\":\"DECK\"}"));var m=fixture(c,A);NonPokemonBatch37Test.top(m,A,hand(m,A,"trainer"));m.attack(A,"hit");NonPokemonBatch67Test.settle(m);check(m.currentPlayer().equals(B)&&m.phase()==MatchPhase.TURN,"Copied turn bypass stalled");}
    static void schema(){String valid="{\"type\":\"turn_ignore_defender_damage_effects\",\"target\":\"SELF\",\"pokemon_families\":\"VMAX\"}";for(String bad:List.of(valid.replace("VMAX","V"),valid.replace("SELF","OWN_ACTIVE"),valid.replace("VMAX","ULTRA_BEAST|VMAX")))GeneratedRuleBatch8Test.reject(()->TurnAttackBypass.from(EffectRegistry.standard().parse(JsonParser.parseString(bad).getAsJsonObject())));}
}
