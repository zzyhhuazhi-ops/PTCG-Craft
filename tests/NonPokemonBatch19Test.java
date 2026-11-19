import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch19Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){hill();mine();cape();allister();rod();oldPC();saucer();camping();System.out.println("NonPokemonBatch19Test passed: healing lock both fields and removal, Active retreat modifiers, Basic/non-GX HP, actual partial draw then mandatory discard, dual optional recovery, two-head recovery, Metal Bench attachment and turn ending");}
    static void hill(){
        var m=NonPokemonBatch2Test.game("swsh6_en_135",EnergyType.GRASS,1);m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);
        for(UUID p:List.of(A,B))for(UUID host:List.of(m.activePokemonView(p).pokemonId(),m.benchPokemonView(p).getFirst().pokemonId())){m.placeDamageCountersByEffect(A,p,host,4);m.healDamageByEffect(A,p,host,20);}
        for(UUID p:List.of(A,B))check(m.activePokemonView(p).damage()==40&&m.benchPokemonView(p).getFirst().damage()==40,"Hill allowed healing on one field or Bench");
        m.discardStadiumByEffect(A);m.healDamageByEffect(A,A,m.activePokemonView(A).pokemonId(),20);check(m.activePokemonView(A).damage()==20,"Hill retained healing lock after removal");
    }
    static void mine(){
        var m=NonPokemonBatch2Test.game("swsh2_en_160",EnergyType.GRASS,1);m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);check(m.activeRetreatCost(A)==3&&m.activeRetreatCost(B)==3,"Mine did not affect both Active Pokemon");m.discardStadiumByEffect(A);check(m.activeRetreatCost(A)==1&&m.activeRetreatCost(B)==1,"Mine retreat cost persisted");
    }
    static void cape(){
        for(boolean gx:List.of(false,true)){
            var c=new GeneratedRuleBatch28Test.Config();c.trainer=NonPokemonBatch2Test.DATA.trainer("swsh3_en_160").orElseThrow();c.ownFamilies=gx?Set.of(PokemonFamily.GX):Set.of();var m=GeneratedRuleBatch28Test.fixture(c);GeneratedRuleBatch28Test.play(m);check(m.activePokemonView(A).hitPoints()==(gx?1000:1050),"Cape did not exclude GX or ignored Basic");
            if(!gx){m.evolveByEffect(A,m.activePokemonView(A).pokemonId(),GeneratedRuleBatch28Test.hand(m,A,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);check(m.activePokemonView(A).hitPoints()==1000,"Cape boosted Evolution");}
        }
    }
    static void allister(){
        for(int left:List.of(0,1,3)){
            var m=NonPokemonBatch2Test.game("swsh4_en_146",EnergyType.GRASS,1);m.drawCardsByEffect(A,A,m.privateView(A).deck().size()-left);var card=NonPokemonBatch2Test.trainer(m);if(left==0){check(!m.canPlayTrainer(A,card.instanceId(),null),"Allister played without drawing");continue;}int hand=m.privateView(A).hand().size();m.playTrainer(A,card.instanceId(),null);var q=m.effectCardPrompt(A);check(q.minimum()==1&&q.maximum()==3,"Allister omitted mandatory minimum");var id=q.candidates().getFirst().instanceId();m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(id));NonPokemonBatch2Test.settle(m);check(m.privateView(A).hand().size()==hand-2+left,"Allister partial draw/discard count wrong");
        }
    }
    static void rod(){
        var m=NonPokemonBatch2Test.game("swsh1_en_171",EnergyType.GRASS,1);var pokemon=DynamicPassiveModifierTest.hand(m,A,"basic");var basic=DynamicPassiveModifierTest.hand(m,A,"energy");var special=DynamicPassiveModifierTest.hand(m,A,"special");m.discardHandCardsByEffect(A,A,List.of(pokemon.instanceId(),basic.instanceId(),special.instanceId()));m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);var q=m.effectChoicePrompt(A);m.chooseEffectOption(A,q.resolutionId(),q.step(),2);
        for(var card:List.of(pokemon,basic)){var p=m.effectCardPrompt(A);check(p.minimum()==0&&p.maximum()==2&&p.candidates().equals(List.of(card)),"Rod mixed categories or allowed Special Energy");m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(card.instanceId()));}NonPokemonBatch2Test.settle(m);check(m.privateView(A).deck().containsAll(List.of(pokemon,basic))&&m.privateView(A).discard().contains(special),"Rod wrong recovery destination");
    }
    static void oldPC(){
        boolean success=false,failure=false;for(int seed=0;seed<50&&!(success&&failure);seed++){
            var m=GeneratedRuleBatch23Test.fixture(NonPokemonBatch2Test.DATA.trainer("swsh3_en_164").orElseThrow(),seed,EnergyType.GRASS);var card=DynamicPassiveModifierTest.hand(m,A,"tool");m.discardHandCardsByEffect(A,A,List.of(card.instanceId()));int before=m.randomTranscript().size();m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);var coins=m.randomTranscript().subList(before,m.randomTranscript().size()).stream().filter(e->e.operation().startsWith("effect-coin:")).toList();check(coins.size()==2,"Old PC failed to toss exactly two coins");boolean heads=coins.stream().allMatch(e->e.result()==0);success|=heads;failure|=!heads;if(heads){var p=m.effectCardPrompt(A);m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(card.instanceId()));}NonPokemonBatch2Test.settle(m);check(m.privateView(A).hand().contains(card)==heads,"Old PC recovered without two heads");
        }check(success&&failure,"Old PC fixture did not cover both outcomes");
    }
    static void saucer(){
        var m=NonPokemonBatch2Test.game("swsh1_en_170",EnergyType.METAL,1);var basic=DynamicPassiveModifierTest.hand(m,A,"energy");var special=DynamicPassiveModifierTest.hand(m,A,"special");m.discardHandCardsByEffect(A,A,List.of(basic.instanceId(),special.instanceId()));m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);NonPokemonBatch2Test.settle(m);check(m.benchPokemonView(A).getFirst().attachedEnergyIds().contains(basic.instanceId())&&m.activePokemonView(A).attachedEnergyIds().isEmpty()&&m.privateView(A).discard().contains(special),"Saucer attached Special Energy or wrong host");
    }
    static void camping(){var m=NonPokemonBatch2Test.game("swsh5_en_122",EnergyType.GRASS,1);m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);NonPokemonBatch2Test.settle(m);check(m.currentPlayer().equals(B),"Camping Gear did not end turn");}
}
