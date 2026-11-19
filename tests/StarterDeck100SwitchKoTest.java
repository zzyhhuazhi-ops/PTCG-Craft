import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

public final class StarterDeck100SwitchKoTest {
    static final UUID A=StarterDeck100PokemonTest.A,B=StarterDeck100PokemonTest.B;
    static void check(boolean b,String s){StarterDeck100PokemonTest.check(b,s);}
    static void shield(ServerMatch m,UUID owner,UUID target,String source,String relation){var e=EffectRegistry.standard().parse(JsonParser.parseString(EffectOriginProtectionTest.shield(source,"EFFECTS_ONLY","SELF","SOURCE_NEXT_TURN_START").replace("OPPONENT",relation)).getAsJsonObject());m.executeEffectProgramByRule(owner,target,new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_CARD_PLAYED,List.of(),List.of(e)))),EffectTrigger.ON_CARD_PLAYED);}
    public static void main(String[] args){witch();claw();dynamicHp();System.out.println("StarterDeck100SwitchKoTest passed: actual switch-bound continuation, opponent authority, absent Benches, HP threshold, protection and KO prizes");}
    static void witch(){for(String mode:List.of("NORMAL","OWN_EMPTY","OPPONENT_EMPTY","OWN_BLOCKED","OPPONENT_BLOCKED")){
        var m=StarterDeck100PokemonTest.game(201);UUID source=m.activePokemonView(A).pokemonId(),mine=m.benchPokemonView(A).getFirst().pokemonId(),theirs=m.benchPokemonView(B).getFirst().pokemonId(),opponent=m.activePokemonView(B).pokemonId();
        if(mode.equals("OWN_EMPTY"))m.movePokemonByEffect(A,A,mine,LeavePlayDestination.HAND);
        if(mode.equals("OPPONENT_EMPTY"))m.movePokemonByEffect(A,B,theirs,LeavePlayDestination.HAND);
        if(mode.equals("OWN_BLOCKED"))shield(m,A,source,"ABILITY","OWN");
        if(mode.equals("OPPONENT_BLOCKED")){m.endTurn(A);shield(m,B,opponent,"ABILITY","OPPONENT");m.endTurn(B);}
        if(mode.equals("OWN_EMPTY")){check(!m.canUseAbility(A,source,"witch-rondo"),"Witch Rondo activated with no own Bench");continue;}
        StarterDeck100PokemonTest.ability(m,source);StarterDeck100PokemonTest.choosePokemon(m,mine);
        if(!Set.of("OWN_BLOCKED","OPPONENT_EMPTY").contains(mode)){var p=m.effectPokemonPrompt(B);TopDeckLookTest.rejects(()->m.chooseEffectPokemon(A,p.resolutionId(),p.step(),theirs));m.chooseEffectPokemon(B,p.resolutionId(),p.step(),theirs);}
        check(m.activePokemonView(A).pokemonId().equals(mode.equals("OWN_BLOCKED")?source:mine),"Witch Rondo own switch wrong");
        check(m.activePokemonView(B).pokemonId().equals(mode.equals("NORMAL")?theirs:opponent),"Witch Rondo opponent switch ignored actual result/protection");
        check(!m.hasPendingPokemonSelection()&&!m.canUseAbility(A,source,"witch-rondo"),"Witch Rondo did not finish/consume use");
    }
        var m=StarterDeck100PokemonTest.game(201);StarterDeck100EnergyTest.attach(m,A,B,"sd100_cn_grass_energy",m.activePokemonView(B).pokemonId());StarterDeck100PokemonTest.attack(m,0);check(m.lastAttack().finalDamage()==80,"Hatterene Psychic wrong Energy bonus");
    }
    static void claw(){for(String mode:List.of("ACTIVE","BENCH","BLOCKED","NONE")){
        var m=StarterDeck100PokemonTest.game(235);UUID target=mode.equals("BENCH")?m.benchPokemonView(B).getFirst().pokemonId():m.activePokemonView(B).pokemonId();UUID physical=PokemonIdentityTestSupport.physical(m,B,target);var view=StarterDeck100PokemonTest.view(m,B,target);if(!mode.equals("NONE"))m.placeDamageCountersByEffect(A,B,target,(view.hitPoints()-60)/10);
        if(mode.equals("BLOCKED")){m.endTurn(A);shield(m,B,target,"ATTACK","OPPONENT");m.endTurn(B);}
        StarterDeck100PokemonTest.attack(m,0);
        if(mode.equals("NONE")){check(!m.hasPendingPokemonSelection()&&m.currentPlayer().equals(B),"Hunting Claw should do nothing without eligible HP");continue;}
        var p=m.effectPokemonPrompt(A);check(p.targets().size()==1&&p.targets().getFirst().pokemonId().equals(target),"Hunting Claw HP filter wrong");StarterDeck100PokemonTest.choosePokemon(m,target);
        StarterDeck100CombatTest.settleKnockouts(m);
        if(mode.equals("BLOCKED"))check(m.privateView(A).prizes().size()==6&&m.activePokemonView(B).pokemonId().equals(target),"protected target was directly KOed");
        else check(m.privateView(A).prizes().size()==4&&m.privateView(B).discard().stream().anyMatch(c->c.instanceId().equals(physical)),"direct KO failed prize/owner discard");
    }}
    static void dynamicHp(){var attack=StarterDeck100CombatTest.COMBAT.pokemon(StarterDeck100PokemonTest.id(235)).orElseThrow().attacks().getFirst();var m=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.trainer("TOOL","{\"type\":\"hit_points_modifier\",\"amount\":100}"),DynamicPassiveModifierTest.plainStadium(),EnergyProfile.fixed(EnergyType.FIGHTING,1),attack);UUID a=DynamicPassiveModifierTest.A,b=DynamicPassiveModifierTest.B;UUID target=m.activePokemonView(b).pokemonId();m.placeDamageCountersByEffect(a,b,target,24);m.attachEnergy(a,DynamicPassiveModifierTest.hand(m,a,"special").instanceId(),m.activePokemonView(a).pokemonId());m.endTurn(a);m.playTrainer(b,DynamicPassiveModifierTest.hand(m,b,"tool").instanceId(),target);m.endTurn(b);m.attack(a,attack.id());check(!m.hasPendingPokemonSelection(),"Hunting Claw compared printed instead of dynamic HP");m.endTurn(b);m.disableToolEffectsByEffect(a,b,target);m.attack(a,attack.id());check(m.effectPokemonPrompt(a).targets().stream().anyMatch(t->t.pokemonId().equals(target)),"Hunting Claw did not refresh after HP modifier removal");}
}
