import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernDamageScopeRepairTest {
    static final UUID A=ModernRuleRepair2Test.A,B=ModernRuleRepair2Test.B;
    static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean b,String why){ModernRuleRepair2Test.check(b,why);}
    static ServerMatch game(AttackDefinition attack,EnergyType weakness,EnergyType resistance){
        var defs=new CardDatabase(List.of(StadiumActivationTest.definition("basic",CardCategory.POKEMON,TrainerCategory.NONE,true),StadiumActivationTest.definition("energy",CardCategory.ENERGY,TrainerCategory.NONE,false)));
        var unit=new EnergyProfile.Unit(EnumSet.allOf(EnergyType.class));
        var combat=new CombatCatalog(Map.of("basic",new PokemonProfile(500,0,null,weakness,weakness==null?0:2,resistance,resistance==null?0:30,1,List.of(attack),List.of(),attack.damageType())),Map.of(),Map.of(),Map.of("energy",new EnergyProfile(List.of(unit,unit,unit))));
        for(int seed=0;seed<100;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID p:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(p.getLeastSignificantBits(),i+1),p,i%2==0?"basic":"energy"));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,defs,combat,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->c.definitionId().equals("basic")).count()<2))continue;
            OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,20);m.endTurn(A);m.endTurn(B);
            ModernRuleRepair2Test.pay(m,A,m.activePokemonView(A).pokemonId(),"energy",1);return m;
        }throw new AssertionError("Damage scope fixture");
    }
    public static void main(String[] args){palafin();raichu();copiedRaichu();System.out.println("ModernDamageScopeRepairTest passed: counter-scaled recoil, zero/weakness/resistance/KO, all damaged field targets, healthy exclusion, actual copied attacker exclusion and per-target W/R");}
    static void palafin(){
        var a=DATA.pokemon("sv5_en_049").orElseThrow().attacks().getFirst();AttackProgramRules.validate(a);
        for(int counters:List.of(0,1,3,20,30))for(int mode:List.of(0,1,2)){
            var m=game(a,mode==1?EnergyType.WATER:null,mode==2?EnergyType.WATER:null);UUID host=m.activePokemonView(A).pokemonId();
            if(counters>0)EventTargetBindingTest.field(m).placeDamageCounters(A,host,counters);
            m.attack(A,a.id());int recoil=Math.max(0,counters*10*(mode==1?2:1)-(mode==2?30:0));
            check(m.lastAttack().selfDamage()==recoil&&m.lastAttack().finalDamage()==(mode==1?260:mode==2?100:130),"Palafin counters or damage layers incorrect");
            if(counters*10+recoil>=500){check(m.phase()==MatchPhase.PRIZE_SELECTION&&m.pendingChoicePlayer().equals(B),"Recoil KO stalled or rewarded wrong player");m.takePrizeAt(B,0);check(!m.privateView(A).discard().isEmpty(),"Recoil KO did not discard host");}
            else check(m.activePokemonView(A).damage()==counters*10+recoil&&m.currentPlayer().equals(B)&&!m.hasPendingEffectChoice(),"Palafin recoil repeated or attack stalled");
        }
    }
    static void raichu(){
        var a=DATA.pokemon("sv5_en_052").orElseThrow().attacks().getFirst();AttackProgramRules.validate(a);
        for(int mask=0;mask<8;mask++)for(int mode:List.of(0,1,2)){
            var m=game(a,mode==1?EnergyType.LIGHTNING:null,mode==2?EnergyType.LIGHTNING:null);var f=EventTargetBindingTest.field(m);UUID host=m.activePokemonView(A).pokemonId();f.placeDamageCounters(A,host,1);
            UUID own=m.benchPokemonView(A).getFirst().pokemonId(),front=m.activePokemonView(B).pokemonId(),bench=m.benchPokemonView(B).getFirst().pokemonId();
            if((mask&1)!=0)f.placeDamageCounters(A,own,1);if((mask&2)!=0)f.placeDamageCounters(B,front,1);if((mask&4)!=0)f.placeDamageCounters(B,bench,1);
            var before=m.clientView(A);var rng=m.randomTranscript();CopyAttackTest.reject(()->m.attack(A,a.id(),host));check(before.equals(m.clientView(A))&&rng.equals(m.randomTranscript()),"Forged area target mutated state");
            m.attack(A,a.id());check(m.activePokemonView(A).damage()==10,"Raichu damaged actual attacker");
            check(m.benchPokemonView(A).getFirst().damage()==((mask&1)!=0?60:0)&&m.benchPokemonView(B).getFirst().damage()==((mask&4)!=0?60:0),"Raichu omitted own Bench, damaged healthy target or applied Bench W/R");
            check(m.activePokemonView(B).damage()==((mask&2)!=0?10+(mode==1?100:mode==2?20:50):0)&&m.currentPlayer().equals(B)&&!m.hasPendingEffectChoice(),"Raichu Active W/R or continuation failed");
        }
    }
    static void copiedRaichu(){
        var a=DATA.pokemon("sv5_en_052").orElseThrow().attacks().getFirst();var m=CopyAttackTest.fixture(CopyAttackTest.copier(),a,EnergyType.PSYCHIC,0);
        for(UUID p:List.of(CopyAttackTest.A,CopyAttackTest.B)){EventTargetBindingTest.field(m).placeDamageCounters(p,m.activePokemonView(p).pokemonId(),1);EventTargetBindingTest.field(m).placeDamageCounters(p,m.benchPokemonView(p).getFirst().pokemonId(),1);}
        CopyAttackTest.pay(m);CopyAttackTest.copy(m,m.copyAttackOptions(CopyAttackTest.A,"copy").stream().filter(c->c.attackId().equals(a.id())).findFirst().orElseThrow(),List.of(),List.of());
        check(m.activePokemonView(CopyAttackTest.A).damage()==10&&m.activePokemonView(CopyAttackTest.B).damage()==110&&m.benchPokemonView(CopyAttackTest.A).getFirst().damage()==60&&m.benchPokemonView(CopyAttackTest.B).getFirst().damage()==60,"Copied Raichu excluded original card instead of actual attacker, or used original type");
    }
}
