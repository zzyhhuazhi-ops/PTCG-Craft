import com.google.gson.JsonParser;
import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch70Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final List<String> IDS=List.of("sm8_en_182");
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static CardInstance hand(ServerMatch m,UUID p,String id){return DynamicPassiveModifierTest.hand(m,p,id);}
    static UUID other(UUID p){return p.equals(A)?B:A;}
    static void bench(ServerMatch m,UUID p){if(m.benchPokemonView(p).isEmpty())m.playBasicPokemonToBench(p,hand(m,p,p.equals(A)?"attacker":"defender").instanceId());}
    static void prize(ServerMatch m,UUID p){bench(m,p);m.knockOutPokemonByEffect(p,p,m.benchPokemonView(p).getFirst().pokemonId());check(UnrestrictedAttackPrizeTest.take(m,other(p))==1,"Prize setup failed");}
    static ServerMatch fixture(UUID p,int prizes,boolean beast,boolean evolvedBeast,AttackDefinition attack){
        var c=new GeneratedRuleBatch28Test.Config();c.trainer=NonPokemonBatch2Test.DATA.trainer(IDS.getFirst()).orElseThrow();c.trainerLimits=Map.of("棱镜之星",1);c.attack=attack;c.defenderAttack=attack;var family=beast?Set.of(PokemonFamily.ULTRA_BEAST):Set.<PokemonFamily>of();var evolved=evolvedBeast?Set.of(PokemonFamily.ULTRA_BEAST):Set.<PokemonFamily>of();if(p.equals(A)){c.ownFamilies=family;c.ownEvolutionFamilies=evolved;}else{c.defenderFamilies=family;c.defenderEvolutionFamilies=evolved;}
        var m=GeneratedRuleBatch28Test.fixture(c);if(p.equals(B))m.endTurn(A);while(m.privateView(other(p)).prizes().size()>prizes)prize(m,p);bench(m,p);return m;
    }
    static ServerMatch aiGame(String id){return fixture(A,3,true,true,GeneratedRuleBatch8Test.hit(100,false));}
    static void play(ServerMatch m,UUID p){var source=hand(m,p,"trainer");m.playTrainer(p,source.instanceId(),null);NonPokemonBatch67Test.settle(m);check(m.privateView(p).lostZone().contains(source),"Prism source not banished");}
    static int damage(ServerMatch m,UUID p,boolean bench){return bench?m.benchPokemonView(p).getFirst().damage():m.activePokemonView(p).damage();}
    public static void main(String[] args){gates();protection();live();effects();legacy();schema();System.out.println("NonPokemonBatch70Test passed: exactly three opponent Prizes at play, both players, Active/Bench Ultra Beast damage protection only next opponent turn, live later entry/evolution/switch, unchanged by later Prizes, counter/status and damage-bypass boundaries; original source-bound EX guard retained");}
    static void gates(){for(UUID p:List.of(A,B))for(int n:List.of(2,3,4,6))for(boolean beast:List.of(false,true)){var m=fixture(p,n,beast,beast,GeneratedRuleBatch8Test.hit(100,false));var source=hand(m,p,"trainer");var before=m.privateView(p);check(m.canPlayTrainer(p,source.instanceId(),null)==(n==3),"Lusamine checked wrong Prize count/required existing Beast");if(n!=3){GeneratedRuleBatch8Test.reject(()->m.playTrainer(p,source.instanceId(),null));check(m.privateView(p).equals(before)&&m.mayPlaySupporter(p),"Illegal Lusamine consumed source");}else play(m,p);}}
    static void protection(){for(UUID p:List.of(A,B))for(boolean beast:List.of(false,true))for(boolean bench:List.of(false,true)){
        var m=fixture(p,3,beast,beast,GeneratedRuleBatch8Test.hit(100,bench));play(m,p);m.endTurn(p);m.attack(other(p),"hit");NonPokemonBatch67Test.settle(m);check(damage(m,p,bench)==(beast?0:100),"Guard family/field boundary wrong");m.endTurn(p);m.attack(other(p),"hit");NonPokemonBatch67Test.settle(m);check(damage(m,p,bench)==(beast?100:200),"Guard persisted after opponent turn");
    }}
    static void live(){for(UUID p:List.of(A,B))for(String mode:List.of("later","switch","evolve-gain","evolve-lose","prizes")){
        boolean base=!mode.equals("evolve-gain"),evolved=!mode.equals("evolve-lose");var m=fixture(p,3,base,evolved,GeneratedRuleBatch8Test.hit(100,false));play(m,p);
        if(mode.equals("later")){m.playBasicPokemonToBench(p,hand(m,p,p.equals(A)?"attacker":"defender").instanceId());m.switchActiveByEffect(p,p,m.benchPokemonView(p).getLast().pokemonId());}
        if(mode.equals("switch"))m.switchActiveByEffect(p,p,m.benchPokemonView(p).getFirst().pokemonId());
        if(mode.startsWith("evolve"))m.evolveByEffect(p,m.activePokemonView(p).pokemonId(),hand(m,p,p.equals(A)?"evolved":"defender_evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
        if(mode.equals("prizes")){prize(m,p);check(m.privateView(other(p)).prizes().size()==2,"Changed-Prize fixture failed");}
        m.endTurn(p);m.attack(other(p),"hit");NonPokemonBatch67Test.settle(m);check(m.activePokemonView(p).damage()==(mode.equals("evolve-lose")?100:0),"Guard cached play-time field/family/Prize count "+mode);
    }}
    static void effects(){for(UUID p:List.of(A,B))for(boolean bypass:List.of(false,true)){
        var attack=bypass?DamageTriggeredAbilityTest.attack(100,",\"ignoresActiveDefenderDamageEffects\":true"):DeckDiscardTriggeredAbilityTest.attack(100,"{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":2},{\"type\":\"poison\",\"target\":\"OPPONENT_ACTIVE\"}");var m=fixture(p,3,true,true,attack);play(m,p);m.endTurn(p);m.attack(other(p),attack.id());NonPokemonBatch67Test.settle(m);check(m.activePokemonView(p).damage()==(bypass?100:30),"Damage-only guard blocked counters/status or ignored bypass");if(!bypass)check(m.activePokemonView(p).conditions().contains(SpecialCondition.POISONED),"Guard blocked Poison");
    }}
    static void legacy(){for(boolean switchSource:List.of(false,true))for(boolean ex:List.of(false,true)){var c=new GeneratedRuleBatch28Test.Config();c.attack=GeneratedRuleBatch28Test.attack("sv8_en_069",0);c.ownFamilies=Set.of(PokemonFamily.FUTURE);c.defenderFamilies=ex?Set.of(PokemonFamily.EX):Set.of();var m=GeneratedRuleBatch28Test.fixture(c);m.attack(A,c.attack.id());if(switchSource)m.switchActiveByEffect(B,A,m.benchPokemonView(A).getFirst().pokemonId());m.attack(B,"hit");check(m.activePokemonView(A).damage()==(ex&&!switchSource?0:100),"Existing CODE protection lost source/family requirement");}}
    static void schema(){String valid="{\"type\":\"field_attack_protection\",\"target\":\"SELF\",\"pokemon_families\":\"ULTRA_BEAST\",\"attacker_filter\":\"ANY\",\"scope\":\"DAMAGE_ONLY\",\"duration\":\"OPPONENT_NEXT_TURN\",\"source_active_bound\":false}";for(String bad:List.of(valid.replace("ULTRA_BEAST","VMAX"),valid.replace("\"attacker_filter\":\"ANY\"","\"attacker_filter\":\"EX\""),valid.replace("DAMAGE_ONLY","DAMAGE_AND_EFFECTS"),valid.replace("OPPONENT_NEXT_TURN","THIS_TURN")))GeneratedRuleBatch8Test.reject(()->FieldAttackProtection.from(EffectRegistry.standard().parse(JsonParser.parseString(bad).getAsJsonObject())));}
}
