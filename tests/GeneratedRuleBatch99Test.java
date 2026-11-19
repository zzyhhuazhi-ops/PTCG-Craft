import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Both printed Target Together attacks with actual Paldean Tauros and Tauros-GX field members. */
public final class GeneratedRuleBatch99Test {
    static final UUID A=NamedFieldCoinAttackTest.A,B=NamedFieldCoinAttackTest.B;
    static final List<String> PRINTS=List.of("me4_en_069","me4_en_096");
    static final CardDatabase DB=NamedFieldCoinAttackTest.DB;
    static final CombatCatalog DATA=NamedFieldCoinAttackTest.DATA;
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    static AttackDefinition attack(String id){return DATA.pokemon(id).orElseThrow().attacks().getFirst();}
    public static void main(String[] args){actualPrints();copiedZero();prizes();System.out.println("GeneratedRuleBatch99Test passed: both real Tauros profiles/CC costs, own Tauros plus actual Paldean/GX counts, chosen Active/Bench before coins, actual copied zero case and one prize");}
    static void actualPrints(){
        for(String id:PRINTS){
            var p=DATA.pokemon(id).orElseThrow();var a=attack(id);
            check(DB.find(id).orElseThrow().effectSupport()==EffectSupport.SUPPORTED&&DB.find(id).orElseThrow().basicPokemon()&&p.hitPoints()==130&&p.retreatCost()==2&&p.prizeCards()==1&&p.pokemonType()==EnergyType.COLORLESS&&p.weaknessType()==EnergyType.FIGHTING&&p.weaknessMultiplier()==2&&p.resistanceType()==null,"Actual Tauros profile");
            check(p.abilities().isEmpty()&&p.attacks().size()==1&&a.id().equals("target-together")&&a.baseDamage()==0&&a.energyCost().equals(Map.of(EnergyType.COLORLESS,2))&&a.damageTarget()==AttackDefinition.DamageTarget.OPPONENT_ANY&&a.targetCount()==1,"Printed Target Together contract");
            AttackProgramRules.validate(a);
            for(int count:List.of(1,3,6))for(boolean back:List.of(false,true)){
                var m=NamedFieldCoinAttackTest.game(a,id,true,count,"NORMAL");
                for(int i=1;i<count;i++)NamedFieldCoinAttackTest.put(m,A,i==1?NamedFieldCoinAttackTest.GX:NamedFieldCoinAttackTest.PALDEAN);
                UUID target=back?NamedFieldCoinAttackTest.bench(m,B):NamedFieldCoinAttackTest.active(m,B);
                NamedFieldCoinAttackTest.atomic(m,()->m.attack(A,a.id(),target));NamedFieldCoinAttackTest.pay(m,1);
                NamedFieldCoinAttackTest.atomic(m,()->m.attack(A,a.id(),target));NamedFieldCoinAttackTest.pay(m,1);
                NamedFieldCoinAttackTest.atomic(m,()->m.attack(A,a.id()));
                int from=m.randomTranscript().size();m.attack(A,a.id(),target);var coins=NamedFieldCoinAttackTest.coins(m,from);
                check(coins.size()==count&&(back?m.benchPokemonView(B).getFirst():m.activePokemonView(B)).damage()==50*NamedFieldCoinAttackTest.heads(coins),"Actual Tauros name count/declared target");
                check(m.currentPlayer().equals(B)&&!m.hasPendingEffectChoice(),"Actual attack continuation");
            }
        }
    }
    static void copiedZero(){
        for(String id:PRINTS){
            var a=attack(id);var m=CopyAttackTest.fixture(CopyAttackTest.copier(),a,EnergyType.PSYCHIC,0);CopyAttackTest.pay(m);
            var choice=m.copyAttackOptions(CopyAttackTest.A,"copy").stream().filter(c->c.attackId().equals(a.id())).findFirst().orElseThrow();
            var rng=m.randomTranscript();CopyAttackTest.copy(m,choice,List.of(m.benchPokemonView(CopyAttackTest.B).getFirst().pokemonId()),List.of());
            check(rng.equals(m.randomTranscript())&&m.benchPokemonView(CopyAttackTest.B).getFirst().damage()==0&&m.currentPlayer().equals(CopyAttackTest.B),"Copied actual attack must count actual controller's names, not original provider");
        }
    }
    static void prizes(){
        var m=NamedFieldCoinAttackTest.game(attack(PRINTS.getFirst()),PRINTS.getFirst(),true,0,"NORMAL");
        m.placeDamageCountersByEffect(A,A,NamedFieldCoinAttackTest.active(m,A),12);NamedFieldCoinAttackTest.put(m,A,"base");m.endTurn(A);m.attack(B,"hit");
        check(m.phase()==MatchPhase.PRIZE_SELECTION,"Actual Tauros knockout");m.takePrizeAt(B,0);
        check(m.privateView(B).prizes().size()==5&&m.phase()!=MatchPhase.PRIZE_SELECTION,"Actual Tauros should give one prize");
    }
}
