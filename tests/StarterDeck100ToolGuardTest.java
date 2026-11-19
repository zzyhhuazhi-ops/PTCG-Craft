import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class StarterDeck100ToolGuardTest {
    static final UUID A=StarterDeck100PokemonTest.A,B=StarterDeck100PokemonTest.B;
    static final CombatCatalog COMBAT=StarterDeck100CombatTest.COMBAT;
    static String id(int n){return StarterDeck100PokemonTest.id(n);}
    static void check(boolean b,String s){StarterDeck100PokemonTest.check(b,s);}
    static void tool(ServerMatch m,int number,UUID host){m.playTrainer(A,StarterDeck100PokemonTest.hand(m,id(number)).instanceId(),host);}
    public static void main(String[] args){chestplate();faceGuard();abilityRemoval();ownDamage();benchDamage();cycleRejected();System.out.println("StarterDeck100ToolGuardTest passed: canonical Galarian names, Ability possession/removal, source-side damage, Tool disable and recursion rejection");}
    static void chestplate(){for(int host:List.of(254,253))for(boolean disabled:List.of(false,true)){var m=StarterDeck100PokemonTest.game(host,id(384));tool(m,384,m.activePokemonView(A).pokemonId());if(disabled)m.disableToolEffectsByEffect(A,A,m.activePokemonView(A).pokemonId());m.endTurn(A);StarterDeck100GuardTest.attackOpponent(m,1,0);check(m.lastAttack().finalDamage()==(host==254?(disabled?100:70):50),"Chestplate name or Tool disable gate failed: host="+host+", disabled="+disabled+", actual="+m.lastAttack().finalDamage());}}
    static void faceGuard(){for(int host:List.of(1,56)){var m=StarterDeck100PokemonTest.game(host,id(385));tool(m,385,m.activePokemonView(A).pokemonId());m.endTurn(A);StarterDeck100GuardTest.attackOpponent(m,1,0);check(m.lastAttack().finalDamage()==(host==1?30:50),"Full Face Guard failed printed Ability presence");}}
    static void abilityRemoval(){var a=DynamicPassiveModifierTest.A;var b=DynamicPassiveModifierTest.B;
        var guard=COMBAT.trainer(id(385)).orElseThrow();var stadium=DynamicPassiveModifierTest.trainer("STADIUM","{\"type\":\"suppress_abilities\",\"scope\":\"ALL_IN_PLAY\"}");var ability=ResumableAbilityTest.ability("test-draw","{\"type\":\"draw\",\"amount\":1}");
        for(int variant:List.of(0,1,2,3,4)){var m=DynamicPassiveModifierTest.fixture(guard,stadium,EnergyProfile.fixed(EnergyType.COLORLESS,2),DynamicPassiveModifierTest.attack(40,false,false),List.of(ability));UUID host=m.activePokemonView(a).pokemonId();m.playTrainer(a,DynamicPassiveModifierTest.hand(m,a,"tool").instanceId(),host);
            if(variant>=1&&variant<=3)m.playTrainer(a,DynamicPassiveModifierTest.hand(m,a,"stadium").instanceId(),null);
            if(variant==2)m.discardStadiumByEffect(a);if(variant==3)m.disableToolEffectsByEffect(a,a,host);if(variant==4)m.disableAbilityEffectsByEffect(a,a,host);
            m.endTurn(a);m.attack(b,"hit");check(m.lastAttack().finalDamage()==(variant==1?60:80),"Ability removal/restore or mere activation lock changed possession incorrectly: "+variant);
        }
    }
    static void ownDamage(){var m=StarterDeck100PokemonTest.game(298,id(385));tool(m,385,m.activePokemonView(A).pokemonId());tool(m,385,m.benchPokemonView(A).getFirst().pokemonId());StarterDeck100PokemonTest.attack(m,1);check(m.activePokemonView(A).damage()==30&&m.benchPokemonView(A).getFirst().damage()==30,"Full Face Guard reduced own attack damage");}
    static void benchDamage(){var m=StarterDeck100PokemonTest.game(1,104,id(385));UUID host=m.benchPokemonView(A).getFirst().pokemonId();tool(m,385,host);m.endTurn(A);StarterDeck100GuardTest.attackOpponent(m,104,0);StarterDeck100GuardTest.benchChoice(m,B,host);check(StarterDeck100PokemonTest.view(m,A,host).damage()==10,"Full Face Guard did not reduce opponent Bench damage by20");}
    static void cycleRejected(){TopDeckLookTest.rejects(()->DynamicPassiveModifierTest.trainer("STADIUM","{\"type\":\"conditional\",\"conditions\":[{\"type\":\"HAS_ABILITIES\"}],\"effects\":[{\"type\":\"suppress_abilities\",\"scope\":\"ALL_IN_PLAY\"}]}"));}
}
