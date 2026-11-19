import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch27Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void attach(ServerMatch m){m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),m.activePokemonView(A).pokemonId());}
    public static void main(String[] args){berries();scarf();helmet();badge();bell();cram();System.out.println("NonPokemonBatch27Test passed: turn-end berries and thresholds, typed burn, Rule Box protection, named V retreat/weakness, damage-only VMAX KO search and Item-only paid coin search");}
    static void berries(){
        for(boolean affected:List.of(false,true)){
            var m=NonPokemonBatch2Test.game("swsh1_en_168",EnergyType.GRASS,1);var tool=NonPokemonBatch2Test.trainer(m);attach(m);
            if(affected){m.applySpecialConditionByEffect(A,A,SpecialCondition.POISONED);m.applySpecialConditionByEffect(A,A,SpecialCondition.CONFUSED);}m.endTurn(A);NonPokemonBatch2Test.settle(m);
            check(m.activePokemonView(A).conditions().isEmpty()&&m.activePokemonView(A).damage()==0,"Lum Berry failed before checkup");check(m.privateView(A).discard().contains(tool)==affected,"Lum Berry discarded without a status");
        }
        for(int counters:List.of(2,3,5)){
            var m=NonPokemonBatch2Test.game("swsh1_en_182",EnergyType.GRASS,1);var tool=NonPokemonBatch2Test.trainer(m);attach(m);m.placeDamageCountersByEffect(A,A,m.activePokemonView(A).pokemonId(),counters);m.endTurn(A);NonPokemonBatch2Test.settle(m);
            check(m.activePokemonView(A).damage()==(counters<3?20:counters*10-30)&&m.privateView(A).discard().contains(tool)==(counters>=3),"Sitrus Berry threshold, healing or self-discard wrong");
        }
    }
    static void scarf(){
        for(EnergyType type:List.of(EnergyType.FIRE,EnergyType.GRASS)){
            var m=GeneratedRuleBatch8Test.fixture(List.of(),EnergyType.GRASS,type,NonPokemonBatch2Test.DATA.trainer("swsh2_en_155").orElseThrow(),GeneratedRuleBatch8Test.hit(500,false));attach(m);m.endTurn(A);m.attack(B,"hit");
            check(m.activePokemonView(B).conditions().contains(SpecialCondition.BURNED)==(type==EnergyType.FIRE),"Burning Scarf ignored Fire type or lethal damage");NonPokemonBatch2Test.settle(m);
        }
    }
    static void helmet(){
        for(boolean box:List.of(false,true)){
            var c=new GeneratedRuleBatch28Test.Config();c.trainer=NonPokemonBatch2Test.DATA.trainer("swsh9_en_146").orElseThrow();c.ownFamilies=box?Set.of(PokemonFamily.RULE_BOX,PokemonFamily.EX):Set.of();var m=GeneratedRuleBatch28Test.fixture(c);attach(m);m.endTurn(A);m.attack(B,"hit");check(m.activePokemonView(A).damage()==(box?100:70),"Pot Helmet ignored Rule Box");
        }
    }
    static void badge(){
        for(String name:List.of("Leafeon V","Glaceon VMAX","Eevee V","Leafeon")){
            var c=new GeneratedRuleBatch28Test.Config();c.trainer=NonPokemonBatch2Test.DATA.trainer("swsh7_en_159").orElseThrow();c.ownName=name;c.ownWeakness=EnergyType.COLORLESS;c.ownFamilies=name.contains(" V")?Set.of(PokemonFamily.V,PokemonFamily.RULE_BOX):Set.of();var m=GeneratedRuleBatch28Test.fixture(c);attach(m);
            boolean eligible=name.equals("Leafeon V")||name.equals("Glaceon VMAX");check(m.activeRetreatCost(A)==(eligible?0:1),"Snow Leaf Badge ignored name/V restriction");m.endTurn(A);m.attack(B,"hit");check(m.activePokemonView(A).damage()==(eligible?100:200),"Snow Leaf Badge failed to remove Weakness");
        }
    }
    static void bell(){
        for(boolean vmax:List.of(false,true))for(boolean attack:List.of(false,true)){
            var c=new GeneratedRuleBatch28Test.Config();c.trainer=NonPokemonBatch2Test.DATA.trainer("swsh8_en_234").orElseThrow();c.ownFamilies=vmax?Set.of(PokemonFamily.V,PokemonFamily.VMAX,PokemonFamily.RULE_BOX):Set.of();var m=GeneratedRuleBatch28Test.fixture(c);attach(m);var host=m.activePokemonView(A).pokemonId();m.placeDamageCountersByEffect(A,A,host,50);m.placeDamageCountersByEffect(A,A,host,40);m.endTurn(A);
            if(attack)m.attack(B,"hit");else m.knockOutPokemonByEffect(B,A,host);
            check(m.hasPendingCardSelection()==(vmax&&attack),"Farewell Bell ignored VMAX or attack-damage-only trigger");if(vmax&&attack){var q=m.effectCardPrompt(A);var chosen=q.candidates().getFirst();m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(chosen.instanceId()));check(m.privateView(A).hand().contains(chosen),"Farewell Bell lost selected card");}NonPokemonBatch2Test.settle(m);
        }
    }
    static void cram(){
        boolean heads=false,tails=false;for(int seed=0;seed<40&&!(heads&&tails);seed++){
            var m=GeneratedRuleBatch23Test.fixture(NonPokemonBatch2Test.DATA.trainer("swsh8_en_229").orElseThrow(),seed,EnergyType.GRASS);int before=m.randomTranscript().size();var card=NonPokemonBatch2Test.trainer(m);m.playTrainer(A,card.instanceId(),null);var q=m.effectCardPrompt(A);
            check(q.candidates().stream().allMatch(c->Set.of("trainer","item").contains(c.definitionId()))&&!q.candidates().contains(card),"Cram-o-matic allowed Tool or own source cost");var cost=q.candidates().getFirst();m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(cost.instanceId()));boolean head=GeneratedRuleBatch23Test.coinResult(m,before)==0;heads|=head;tails|=!head;NonPokemonBatch2Test.settle(m);check(m.privateView(A).discard().contains(cost),"Cram-o-matic failed to pay on tails");
        }check(heads&&tails,"Cram fixture missed coin branch");
    }
}
