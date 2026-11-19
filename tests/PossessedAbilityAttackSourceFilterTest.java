import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Synthetic component fixtures: Ability possession is distinct from whether its effect can be used. */
public final class PossessedAbilityAttackSourceFilterTest {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B;
    static final String WALL="{\"type\":\"prevent_damage\",\"target\":\"SELF\",\"source\":\"ATTACK\",\"from\":\"OPPONENT\",\"scope\":\"DAMAGE_ONLY\",\"duration\":\"OPPONENT_TURN_END\",\"attacker_has_abilities\":true}";
    static final String GUARD="{\"type\":\"passive_attack_guard\",\"target\":\"ALL_OWN_POKEMON\",\"mode\":\"PREVENT\",\"source_position\":\"BENCH\",\"attacker_has_abilities\":true}";
    static final String BENCH="{\"type\":\"bench_damage\",\"target\":\"OPPONENT_BENCH\",\"amount\":90}";
    static final String DISCARD_STADIUM="{\"type\":\"discard_stadium\"}";
    static void check(boolean b,String s){GeneratedRuleBatch8Test.check(b,s);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return DynamicPassiveModifierTest.hand(m,p,id);}
    static AbilityDefinition ability(){return ResumableAbilityTest.ability("draw-proof","{\"type\":\"draw\",\"amount\":1}");}
    static AttackDefinition hit(String before,String after){return DynamicAttackSourceGuardTest.hit(before,after);}
    static ServerMatch game(AttackDefinition attack,TrainerProfile stadium,AbilityDefinition... abilities){
        return DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),stadium,EnergyProfile.fixed(EnergyType.COLORLESS,2),attack,List.of(abilities));
    }
    static ServerMatch game(AttackDefinition attack,AbilityDefinition... abilities){return game(attack,DynamicPassiveModifierTest.plainStadium(),abilities);}
    static TrainerProfile removal(String scope){return DynamicPassiveModifierTest.trainer("STADIUM","{\"type\":\"suppress_abilities\",\"scope\":\""+scope+"\"}");}
    static void stadium(ServerMatch m){m.playTrainer(A,hand(m,A,"stadium").instanceId(),null);}

    public static void main(String[] args){
        contracts(); possessionAndRemoval(); damageSnapshot(); sourceLeavesAfterDamage(); copiedActor(); lifecycleAndScope(); noRecursiveRemoval();
        System.out.println("PossessedAbilityAttackSourceFilterTest passed: authoritative possession, true removal vs disabled/used Abilities, actual copied actor, before-damage capture, stable secondary damage after restoration/source leave, compatibility, strict parsing and lifecycle");
    }
    static void contracts(){
        var card=CopyAttackTest.card("proof",true);
        var profile=new PokemonProfile(150,0,null,null,0,null,0,1,List.of(),List.of(ability()),EnergyType.COLORLESS);
        var f=AttackSourceFilter.from(Map.of("attacker_has_abilities","true"));
        var unknown=new AttackSourceFilter.Facts(card,"proof",false,profile,0,Set.of());
        var yes=new AttackSourceFilter.Facts(card,"proof",false,profile,0,Set.of(),true);
        var no=new AttackSourceFilter.Facts(card,"proof",false,profile,0,Set.of(),false);
        check(f.dynamicRestricted()&&f.matches(yes)&&!f.matches(no)&&!f.matches(unknown),"Unknown or printed-only Ability proof accepted");
        check(!f.matches(card,"proof",false)&&!f.matches(card,"proof",false,profile),"Legacy static overload bypassed required server fact");
        check(AttackSourceFilter.ANY.matches(unknown)&&AttackSourceFilter.ANY.matches(no),"Legacy unrestricted filter changed");
        var old=new AttackSourceFilter(AttackSourceFilter.Kind.ANY,Set.of(),PokemonTypeFilter.ANY,PokemonTypeFilter.ANY,2,null);
        check(old.matches(unknown)&&!old.requiresAbilities(),"Old six-argument filter constructor changed behavior");
        var combined=AttackSourceFilter.from(Map.of("attacker_has_abilities","true","attacker_energy_max","2","attacker_status","BURNED","attacker_pokemon_types","COLORLESS","attacker_filter","BASIC","except_pokemon_names","Excluded"));
        check(combined.matches(new AttackSourceFilter.Facts(card,"proof",false,profile,2,Set.of(SpecialCondition.BURNED),true)),"Combined source proof rejected");
        check(!combined.matches(new AttackSourceFilter.Facts(card,"proof",false,profile,3,Set.of(SpecialCondition.BURNED),true))
            &&!combined.matches(new AttackSourceFilter.Facts(card,"proof",false,profile,2,Set.of(),true))
            &&!combined.matches(new AttackSourceFilter.Facts(card,"Excluded",false,profile,2,Set.of(SpecialCondition.BURNED),true))
            &&!combined.matches(new AttackSourceFilter.Facts(card,"proof",false,profile,2,Set.of(SpecialCondition.BURNED),false)),"Source restrictions lost conjunction");
        for(String value:List.of("false","TRUE","yes","1",""," true "))reject(()->AttackSourceFilter.from(Map.of("attacker_has_abilities",value)));
        for(String bad:List.of(WALL.replace("true","false"),WALL.replace("true","1"),WALL.replace("true","null"),WALL.replace("attacker_has_abilities","attacker_has_ability"),WALL.replace("ATTACK","ABILITY"))){
            reject(()->ProtectionEffect.from(EffectRegistry.standard().parse(JsonParser.parseString(bad).getAsJsonObject())));
        }
        var parsed=EffectRegistry.standard().parse(JsonParser.parseString(WALL).getAsJsonObject());
        check(ProtectionEffect.from(parsed).policy().attackerFilter().requiresAbilities(),"Protection schema dropped source filter");
    }
    static void possessionAndRemoval(){
        for(String mode:List.of("absent","present","disabled","used","removed","restored")){
            var m=game(hit("",WALL),removal("ALL_IN_PLAY"),mode.equals("absent")?new AbilityDefinition[]{}:new AbilityDefinition[]{ability()});
            if(Set.of("removed","restored").contains(mode))stadium(m);
            if(mode.equals("restored"))m.discardStadiumByEffect(A);
            m.attack(A,"hit");
            if(mode.equals("disabled"))m.disableAbilityEffectsByEffect(B,B,active(m,B));
            if(mode.equals("used")){m.useAbility(B,active(m,B),"draw-proof");check(m.abilityOptions(B).stream().anyMatch(o->o.pokemonId().equals(active(m,B))&&o.usedThisTurn()),"Fixture did not consume Ability usage");}
            var rng=m.randomTranscript();m.attack(B,"hit");
            check(m.lastAttack().finalDamage()==(Set.of("absent","removed").contains(mode)?90:0),"Possession semantics wrong: "+mode);
            check(rng.equals(m.randomTranscript()),"Possession guard consumed randomness: "+mode);
        }
    }
    static void damageSnapshot(){
        for(boolean before:List.of(false,true)){
            var m=game(hit(before?DISCARD_STADIUM:"",(before?"":DISCARD_STADIUM+",")+BENCH),removal("OWN_IN_PLAY"),DynamicAttackSourceGuardTest.guard(GUARD));
            stadium(m);m.attack(A,"hit");int expected=before?0:90;
            check(m.lastAttack().finalDamage()==expected&&m.benchPokemonView(B).getFirst().damage()==expected,"Ability source fact captured at wrong time or recomputed after Stadium removal");
            // A later attack resamples restored possession; it cannot reuse the previous false fact.
            m.endTurn(B);m.attack(A,"hit");
            check(m.lastAttack().finalDamage()==0&&m.benchPokemonView(B).getFirst().damage()==expected,"Prior attack's possession snapshot leaked");
        }
    }
    static void sourceLeavesAfterDamage(){
        var m=game(hit("","{\"type\":\"return_to_hand\",\"target\":\"SELF\"},"+BENCH),DynamicAttackSourceGuardTest.guard(GUARD));
        long held=m.privateView(A).hand().stream().filter(c->c.definitionId().equals("basic")).count();m.attack(A,"hit");
        check(m.lastAttack().finalDamage()==0&&m.benchPokemonView(B).getFirst().damage()==0,"Secondary damage lost actual attacker Ability fact after source left play");
        check(m.privateView(A).hand().stream().filter(c->c.definitionId().equals("basic")).count()==held+1,"Source-leave fixture did not return the attacking card");
    }
    static void copiedActor(){
        for(boolean actorHasAbility:List.of(false,true)){
            var m=DynamicAttackSourceGuardTest.positionGame(List.of(CopyAttackTest.copier(),hit("",WALL)),ability());
            if(actorHasAbility)m.switchActiveByEffect(A,A,bench(m,A));
            m.endTurn(A);
            if(!actorHasAbility)m.switchActiveByEffect(B,B,bench(m,B));
            m.attack(B,"hit");
            DynamicAttackSourceGuardTest.attach(m,A,active(m,A),"energy");
            var choice=m.copyAttackOptions(A,"copy").stream().filter(c->c.pokemonId().equals(active(m,B))&&c.attackId().equals("hit")).findFirst().orElseThrow();
            m.copyAttackWithEnergyDiscards(A,"copy",choice.pokemonId(),choice.entrySequence(),choice.attackId(),List.of(),List.of());
            check(m.lastAttack().finalDamage()==(actorHasAbility?0:90),"Ability filter used copy donor instead of actual actor: "+actorHasAbility);
        }
    }
    static void lifecycleAndScope(){
        String counter="{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":2}";
        var effects=game(hit("",WALL+","+counter),ability());effects.attack(A,"hit");int before=effects.activePokemonView(A).damage();effects.attack(B,"hit");
        check(effects.lastAttack().finalDamage()==0&&effects.activePokemonView(A).damage()==before+20,"Damage-only shield blocked counters");
        effects.endTurn(A);effects.attack(B,"hit");check(effects.lastAttack().finalDamage()==90,"Possession shield survived opponent turn end");
        var switching=game(hit("",WALL),ability());UUID former=active(switching,A);switching.attack(A,"hit");switching.switchActiveByEffect(B,A,bench(switching,A));switching.switchActiveByEffect(B,A,former);switching.attack(B,"hit");
        check(switching.lastAttack().finalDamage()==90,"Bench round trip preserved temporary guard");
        var benchGuard=game(hit("",BENCH),DynamicAttackSourceGuardTest.guard(GUARD));benchGuard.attack(A,"hit");
        check(benchGuard.lastAttack().finalDamage()==0&&benchGuard.benchPokemonView(B).getFirst().damage()==0,"Bench recipient ignored ability source filter");
    }
    static void noRecursiveRemoval(){
        reject(()->DynamicPassiveModifierTest.trainer("STADIUM","{\"type\":\"conditional\",\"conditions\":[{\"type\":\"HAS_ABILITIES\"}],\"effects\":[{\"type\":\"suppress_abilities\",\"scope\":\"ALL_IN_PLAY\"}]}"));
        reject(()->DynamicPassiveModifierTest.trainer("STADIUM","{\"type\":\"suppress_abilities\",\"scope\":\"ALL_IN_PLAY\",\"attacker_has_abilities\":true}"));
    }
}
