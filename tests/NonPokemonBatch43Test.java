import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch43Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final List<String> IDS=List.of("xy6_en_089","xy3_en_097","sm7_en_144","swsh3_en_157");
    static final Set<String> AREAS=Set.of("sv7_en_131","sv8-5_en_094");
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static TrainerProfile profile(String id){return NonPokemonBatch2Test.DATA.trainer(id).orElseThrow();}
    static CardInstance card(ServerMatch m,UUID p,String id){return DynamicPassiveModifierTest.hand(m,p,id);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static String counter(String target){return "{\"type\":\"select_pokemon\",\"target\":\"SELECT_"+target+"_POKEMON\",\"position\":\"BENCH\",\"effects\":[{\"type\":\"damage_counter\",\"target\":\"CHOSEN_POKEMON\",\"amount\":2}]}";}
    static void settle(ServerMatch m){NonPokemonBatch2Test.settle(m);}
    static void fill(ServerMatch m,UUID p){while(m.benchPokemonView(p).size()<8){if(m.privateView(p).hand().stream().noneMatch(c->c.definitionId().equals("basic")))m.drawCardsByEffect(p,p,3);m.playBasicPokemonToBench(p,card(m,p,"basic").instanceId());}}
    public static void main(String[] args){skyField();areaZero();benchProtection();parasol();System.out.println("NonPokemonBatch43Test passed: eight-slot shared Bench, departing owner's ordered no-prize discards, attack damage versus effects/own attacks/Abilities, and active-only Tool field protection");}
    static void skyField(){for(UUID owner:List.of(A,B)){
        var m=NonPokemonBatch2Test.game("xy6_en_089",EnergyType.GRASS,1);if(owner.equals(B))m.endTurn(A);m.playTrainer(owner,card(m,owner,"trainer").instanceId(),null);check(m.benchLimit(A)==8&&m.benchLimit(B)==8,"Sky Field failed shared expansion");fill(m,owner);GeneratedRuleBatch8Test.reject(()->m.playBasicPokemonToBench(owner,card(m,owner,"rulebox").instanceId()));m.endTurn(owner);UUID other=owner.equals(A)?B:A;fill(m,other);int ownPrizes=m.privateView(A).prizes().size(),otherPrizes=m.privateView(B).prizes().size();m.discardStadiumByEffect(other);
        for(UUID p:List.of(owner,other))for(int i=0;i<3;i++){var q=m.toolOverflowPrompt();check(q!=null&&q.bench()&&q.owner().equals(p),"Sky Field did not discard Stadium owner's Bench first");UUID selected=q.tools().getFirst().instanceId();GeneratedRuleBatch8Test.reject(()->m.discardOverflowTool(p.equals(A)?B:A,q.id(),selected));m.discardOverflowTool(p,q.id(),selected);}
        check(m.toolOverflowPrompt()==null&&m.benchPokemonView(A).size()==5&&m.benchPokemonView(B).size()==5&&m.benchLimit(A)==5&&m.benchLimit(B)==5&&m.privateView(A).prizes().size()==ownPrizes&&m.privateView(B).prizes().size()==otherPrizes,"Sky Field departure changed prizes or retained expansion");m.playTrainer(other,card(m,other,"trainer").instanceId(),null);check(m.benchLimit(A)==8&&m.benchLimit(B)==8,"Replayed Sky Field retained leave limit");
    }}
    static ServerMatch areaGame(String id){var c=new GeneratedRuleBatch28Test.Config();c.trainer=profile(id);c.ownFamilies=Set.of(PokemonFamily.TERA,PokemonFamily.EX);return GeneratedRuleBatch28Test.fixture(c);}
    static void areaZero(){for(String id:AREAS){var m=areaGame(id);m.playTrainer(A,card(m,A,"trainer").instanceId(),null);check(m.benchLimit(A)==8&&m.benchLimit(B)==5,"Area Zero ignored individual Tera ownership");while(m.benchPokemonView(A).size()<8){if(m.privateView(A).hand().stream().noneMatch(c->c.definitionId().equals("attacker")))m.drawCardsByEffect(A,A,3);m.playBasicPokemonToBench(A,card(m,A,"attacker").instanceId());}m.attack(A,"hit");check(m.activePokemonView(B).damage()==100,"Eight-Pokemon Bench broke damage-context bounds");m.discardStadiumByEffect(B);for(int i=0;i<3;i++){var q=m.toolOverflowPrompt();check(q!=null&&q.owner().equals(A)&&q.bench(),"Area Zero departure lost mandatory discard");m.discardOverflowTool(A,q.id(),q.tools().getFirst().instanceId());}check(m.toolOverflowPrompt()==null&&m.benchLimit(A)==5&&m.benchPokemonView(A).size()==5,"Area Zero expansion persisted");}}
    static void benchProtection(){String damage="{\"type\":\"bench_damage\",\"target\":\"OWN_BENCH\",\"amount\":40},{\"type\":\"bench_damage\",\"target\":\"OPPONENT_BENCH\",\"amount\":40},"+counter("OWN")+","+counter("OPPONENT");var attack=BeforeDamageAttackTest.attack("","",damage);var ability=ResumableAbilityTest.ability("counters",counter("OPPONENT"));
        for(String id:List.of("xy3_en_097","sm7_en_144"))for(UUID owner:List.of(A,B)){
            var m=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),profile(id),EnergyProfile.fixed(EnergyType.GRASS,2),attack,List.of(ability));if(owner.equals(B))m.endTurn(A);m.playTrainer(owner,card(m,owner,"stadium").instanceId(),null);if(owner.equals(B))m.endTurn(B);m.attack(A,"hit");settle(m);boolean pillar=id.equals("sm7_en_144");check(m.activePokemonView(B).damage()==80&&m.benchPokemonView(A).getFirst().damage()==(pillar?60:20)&&m.benchPokemonView(B).getFirst().damage()==(pillar?0:20),"Bench guard confused owner, own attack, damage and effects: "+id);m.useAbility(B,active(m,B),ability.id());settle(m);check(m.benchPokemonView(A).getFirst().damage()==(pillar?80:40),"Bench Stadium incorrectly prevented Ability counters");m.discardStadiumByEffect(B);m.attack(B,"hit");settle(m);check(m.benchPokemonView(B).getFirst().damage()==(pillar?60:80),"Bench protection remained after Stadium departure");
        }
    }
    static void parasol(){var attack=BeforeDamageAttackTest.attack("","","{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":2},"+counter("OPPONENT"));var ability=ResumableAbilityTest.ability("counters",counter("OPPONENT"));
        for(String mode:List.of("active","bench","disabled","switched")){
            var m=DynamicPassiveModifierTest.fixture(profile("swsh3_en_157"),DynamicPassiveModifierTest.plainStadium(),EnergyProfile.fixed(EnergyType.GRASS,2),attack,List.of(ability));UUID host=mode.equals("bench")?bench(m,B):active(m,B);m.applySpecialConditionByEffect(A,B,SpecialCondition.CONFUSED);m.attachToolByEffect(A,B,EffectSourceZone.HAND,card(m,B,"tool").instanceId(),B,host);check(m.activePokemonView(B).conditions().contains(SpecialCondition.CONFUSED),"Big Parasol removed an existing condition");if(mode.equals("disabled"))m.disableToolEffectsByEffect(A,B,host);if(mode.equals("switched"))m.switchActiveByEffect(A,B,bench(m,B));m.attack(A,"hit");settle(m);boolean guard=mode.equals("active");check(m.activePokemonView(B).damage()==(guard?80:100)&&m.benchPokemonView(B).getFirst().damage()==(guard?0:20),"Big Parasol activation/position/damage scope: "+mode);m.endTurn(B);m.useAbility(A,active(m,A),ability.id());settle(m);check(m.benchPokemonView(B).getFirst().damage()==(guard?20:40),"Big Parasol blocked an Ability");
        }
    }
}
