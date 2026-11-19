import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class StarterDeck100FieldCountTest {
    static final UUID A=StarterDeck100PokemonTest.A,B=StarterDeck100PokemonTest.B;
    static String id(int n){return StarterDeck100PokemonTest.id(n);}
    static void check(boolean b,String s){StarterDeck100PokemonTest.check(b,s);}
    static UUID bench(ServerMatch m,int n){var card=StarterDeck100PokemonTest.hand(m,id(n));m.playBasicPokemonToBench(A,card.instanceId());return PokemonIdentityTestSupport.logical(m,A,card.instanceId());}
    public static void main(String[] args){rollout();styles();types();preciousTouch();busybody();invalid();System.out.println("StarterDeck100FieldCountTest passed: current top-card attacks, Battle Styles, V family, distinct Bench types and live Rollout aura");}
    static void rollout(){for(int n:List.of(88,162,362,333,205,308)){
        var m=StarterDeck100PokemonTest.game(n,id(88),id(162),id(1));bench(m,88);bench(m,162);bench(m,1);
        int expected=n==333?40:60; // Chansey below the evolved Active is not a Benched Rollout user.
        StarterDeck100PokemonTest.attack(m,n==333||n==308?0:1);check(m.lastAttack().finalDamage()==expected,"Rollout counted the Active, a nonuser or a printed base: "+n);
    }
        var m=StarterDeck100PokemonTest.game(362,"swsh8_en_223");UUID host=m.benchPokemonView(A).getFirst().pokemonId();m.evolveByEffect(A,host,StarterDeck100PokemonTest.hand(m,"swsh8_en_223").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);StarterDeck100PokemonTest.attack(m,1);check(m.lastAttack().finalDamage()==0,"Rollout counted an attack below the top Evolution");
    }
    static void styles(){var m=StarterDeck100PokemonTest.game(245,id(1));bench(m,1);StarterDeck100PokemonTest.attack(m,0);check(m.lastAttack().finalDamage()==40,"Rapid Strike Squad excluded source or included non-Rapid V");
        m=StarterDeck100PokemonTest.game(228,104);m.endTurn(A);StarterDeck100GuardTest.prepareOpponent(m,104,0);m.endTurn(B);StarterDeck100PokemonTest.attack(m,1);check(m.lastAttack().finalDamage()==120,"Dynablade did not count VMAX exactly once and Benched V");
    }
    static void types(){var m=StarterDeck100PokemonTest.game(192,id(1),id(162));bench(m,1);bench(m,162);StarterDeck100PokemonTest.attack(m,1);check(m.lastAttack().finalDamage()==130,"Max Harmony counted duplicate Psychic twice or excluded Grass");}
    static void preciousTouch(){for(boolean empty:List.of(false,true)){var m=StarterDeck100PokemonTest.game(192);UUID host=m.benchPokemonView(A).getFirst().pokemonId();m.placeDamageCountersByEffect(A,A,host,15);StarterDeck100PokemonTest.charge(m,0);if(empty)StarterDeck100PokemonTest.trim(m,0);else StarterDeck100PokemonTest.hand(m,"sd100_cn_grass_energy");m.attack(A,"precious-touch");StarterDeck100PokemonTest.choosePokemon(m,host);if(!empty)StarterDeck100PokemonTest.select(m,1);check(StarterDeck100PokemonTest.view(m,A,host).damage()==(empty?150:30),"Precious Touch healed without attachment or wrong target");}}
    static void busybody(){for(int variant:List.of(0,1,2,3)){
        var m=StarterDeck100PokemonTest.game(162,104,id(332),id(333),id(1));UUID nurse=bench(m,332);m.evolveByEffect(A,nurse,StarterDeck100PokemonTest.hand(m,id(333)).instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);UUID matching=m.benchPokemonView(A).getFirst().pokemonId();UUID plain=bench(m,1);
        if(variant==1)m.disableAbilityEffectsByEffect(A,A,nurse);if(variant==2)m.movePokemonByEffect(A,A,nurse,LeavePlayDestination.HAND);
        m.endTurn(A);StarterDeck100GuardTest.attackOpponent(m,104,0);check(m.lastAttack().finalDamage()==(variant==1||variant==2?150:0),"Busybody Nurse source removal/suppression failed");StarterDeck100GuardTest.benchChoice(m,B,variant==3?plain:matching);
        StarterDeck100CombatTest.settleKnockouts(m);
        check(StarterDeck100PokemonTest.view(m,A,variant==3?plain:matching).damage()==(variant==0?0:30),"Busybody Nurse protected a non-Rollout or missed Bench user");
    }}
    static void invalid(){var registry=dev.hua.ptcglive.engine.effect.EffectRegistry.standard();for(String json:List.of("{\"type\":\"damage_per_pokemon\",\"target\":\"HAND\",\"amount\":20}","{\"type\":\"damage_per_pokemon\",\"target\":\"OWN_BENCH\",\"amount\":20,\"count_mode\":\"ENERGY\"}","{\"type\":\"damage_per_pokemon\",\"target\":\"OWN_BENCH\",\"amount\":20,\"attack_id\":\"\"}"))TopDeckLookTest.rejects(()->registry.parse(com.google.gson.JsonParser.parseString(json).getAsJsonObject()));}
}
