import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** BW PlusPower and Iris refer to both Active Pokemon, including attack recoil. */
public final class NonPokemonBatch86Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final List<String> IDS=List.of("bw1_en_096","bw10_en_081");
    static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    static UUID other(UUID p){return NonPokemonBatch67Test.other(p);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch28Test.hand(m,p,id);}
    static GeneratedRuleBatch28Test.Config config(String id,AttackDefinition attack,boolean weak){
        var c=new GeneratedRuleBatch28Test.Config();c.trainer=NonPokemonBatch2Test.DATA.trainer(id).orElseThrow();
        c.attack=attack;c.defenderAttack=attack;c.ownTypes=Set.of(EnergyType.COLORLESS);c.defenderTypes=c.ownTypes;
        c.weakness=weak;c.ownWeakness=weak?EnergyType.COLORLESS:null;return c;
    }
    static ServerMatch game(String id,UUID p,AttackDefinition attack,boolean weak){var m=GeneratedRuleBatch28Test.fixture(config(id,attack,weak));if(p.equals(B))m.endTurn(A);return m;}
    static void play(ServerMatch m,UUID p){m.playTrainer(p,hand(m,p,"trainer").instanceId(),null);NonPokemonBatch67Test.settle(m);}
    static void prize(ServerMatch m,UUID p){if(m.benchPokemonView(p).isEmpty())m.playBasicPokemonToBench(p,hand(m,p,p.equals(A)?"attacker":"defender").instanceId());NonPokemonBatch67Test.prize(m,other(p));}
    static ServerMatch aiGame(String id){var m=game(id,A,DamageTriggeredAbilityTest.attack(300,",\"selfDamage\":30"),false);if(id.equals(IDS.getLast()))prize(m,A);return m;}
    public static void main(String[] args){damage();stackAndLive();expiry();positions();zero();schema();System.out.println("NonPokemonBatch86Test passed: both owners, native/self_damage/recoil, pre-weakness damage to both Active, Iris live opponent Prizes, stacking, turn expiry, evolving/switching/Bench attackers and mixed targets, zero damage and strict schema");}
    static void damage(){for(String id:IDS)for(UUID p:List.of(A,B))for(String mode:List.of("native","self_damage","recoil"))for(boolean weak:List.of(false,true))for(int taken:List.of(0,1,2,3)){
        var m=game(id,p,AttackSelfDamageCalculationTest.attack(mode,30,EnergyType.COLORLESS,""),weak);for(int i=0;i<taken;i++)prize(m,p);play(m,p);m.attack(p,"hit");int bonus=id.equals(IDS.getFirst())?10:10*taken,multiplier=weak?2:1;
        check(m.activePokemonView(p).damage()==(30+bonus)*multiplier&&m.activePokemonView(other(p)).damage()==(40+bonus)*multiplier,"Both-Active damage wrong: "+id+" "+p+" "+mode+" "+taken+" "+weak);
    }}
    static void stackAndLive(){for(String id:IDS)for(UUID p:List.of(A,B)){
        if(id.equals(IDS.getLast())){
            var m=GeneratedRuleBatch23Test.fixture(NonPokemonBatch2Test.DATA.trainer(id).orElseThrow(),0,EnergyType.COLORLESS,NonPokemonBatch2Test.DATA.trainer("sm10_en_178").orElseThrow());if(p.equals(B))m.endTurn(A);
            NonPokemonBatch67Test.prize(m,other(p));m.playTrainer(p,hand(m,p,"supporter").instanceId(),null);NonPokemonBatch67Test.settle(m);
            var iris=hand(m,p,"trainer");play(m,p);NonPokemonBatch67Test.reuse(m,p,iris);m.playTrainer(p,iris.instanceId(),null);NonPokemonBatch67Test.settle(m);
            m.playBasicPokemonToBench(p,hand(m,p,"basic").instanceId());NonPokemonBatch67Test.prize(m,other(p));m.attack(p,"hit");
            check(m.activePokemonView(other(p)).damage()==140,"Repeated Iris must count live opponent prizes after both uses");
        } else {
            var m=game(id,p,AttackSelfDamageCalculationTest.attack("native",30,EnergyType.COLORLESS,""),false);play(m,p);play(m,p);m.attack(p,"hit");
            check(m.activePokemonView(p).damage()==50&&m.activePokemonView(other(p)).damage()==60,"PlusPower stacking wrong");
        }
    }}
    static void expiry(){for(String id:IDS){var m=game(id,A,AttackSelfDamageCalculationTest.attack("native",30,EnergyType.COLORLESS,""),false);prize(m,A);play(m,A);m.attack(A,"hit");int a=m.activePokemonView(A).damage(),b=m.activePokemonView(B).damage();m.attack(B,"hit");check(m.activePokemonView(A).damage()==a+40&&m.activePokemonView(B).damage()==b+30,"Bonus leaked to opponent turn");a=m.activePokemonView(A).damage();b=m.activePokemonView(B).damage();m.attack(A,"hit");check(m.activePokemonView(A).damage()==a+30&&m.activePokemonView(B).damage()==b+40,"Bonus persisted into next own turn");}}
    static void positions(){for(String id:IDS)for(String mode:List.of("switch","evolve","bench","all")){
        var atk=mode.equals("all")?DamageTriggeredAbilityTest.attack(40,",\"damageType\":\"COLORLESS\",\"damageTarget\":\"ALL_POKEMON\""):AttackSelfDamageCalculationTest.attack("native",30,EnergyType.COLORLESS,mode.equals("bench")?",\"mayUseFromBench\":true":"");
        var m=game(id,A,atk,false);if(id.equals(IDS.getLast())){prize(m,A);m.playBasicPokemonToBench(A,hand(m,A,"attacker").instanceId());}play(m,A);
        if(mode.equals("switch"))m.switchActiveByEffect(A,A,m.benchPokemonView(A).getFirst().pokemonId());
        if(mode.equals("evolve"))m.evolveByEffect(A,active(m,A),hand(m,A,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
        if(mode.equals("bench")){var o=m.benchAttackOptions(A).getFirst();m.attackFromBench(A,o.pokemonId(),o.entry(),o.topCardId(),o.attack().id());check(m.activePokemonView(A).damage()==0&&m.benchPokemonView(A).getFirst().damage()==30,"Bench recoil received Active-only bonus");}
        else {m.attack(A,"hit");check(m.activePokemonView(A).damage()==(mode.equals("all")?50:40),"Bonus lost after entry/evolution or own mixed target");}
        check(m.activePokemonView(B).damage()==50,"Bonus lost on opposing Active");if(mode.equals("all"))check(m.benchPokemonView(A).getFirst().damage()==40&&m.benchPokemonView(B).getFirst().damage()==40,"Both-Active bonus leaked to Bench");
    }}
    static void zero(){for(String id:IDS){var m=game(id,A,AttackSelfDamageCalculationTest.attack("native",0,EnergyType.COLORLESS,""),false);play(m,A);m.attack(A,"hit");check(m.activePokemonView(A).damage()==0,"Created absent self damage");var n=game(id,A,GeneratedRuleBatch8Test.hit(0,false),false);play(n,A);n.attack(A,"hit");check(n.activePokemonView(B).damage()==0,"Created absent attack damage");}}
    static void schema(){for(String suffix:List.of("\"include_own_active\":false","\"include_own_active\":true,\"pokemon_types\":\"FIRE\"","\"include_own_active\":true,\"source_only\":true"))GeneratedRuleBatch8Test.reject(()->TurnAttackBonus.from(GeneratedRuleBatch28Test.effect("{\"type\":\"turn_attack_bonus\",\"target\":\"SELF\",\"amount\":10,"+suffix+"}")));}
}
