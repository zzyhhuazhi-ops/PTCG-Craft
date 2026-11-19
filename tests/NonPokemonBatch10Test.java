import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch10Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){curry();mirage();retrieval();townMap();looker();System.out.println("NonPokemonBatch10Test passed: burn/heal, Lost Zone threshold, distinct energy attachment, recovery errata, persistent public own prizes, actual bottom-deck draw");}
    static void looker(){
        var m=NonPokemonBatch2Test.game("sm5_en_126",EnergyType.GRASS,1);var expected=List.copyOf(m.privateView(A).deck().subList(0,3));
        var before=m.privateView(A).hand().size();m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);NonPokemonBatch2Test.settle(m);
        check(m.privateView(A).hand().size()==before+2&&m.privateView(A).hand().containsAll(expected),"Looker skipped bottom-deck drawing or drew from top");
    }
    static void townMap(){
        var m=NonPokemonBatch2Test.game("bw7_en_136",EnergyType.GRASS,1);var before=List.copyOf(m.privateView(A).prizes());
        m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);NonPokemonBatch2Test.settle(m);
        for(var viewer:List.of(A,B))for(var p:m.clientView(viewer).players())check(p.faceUpPrizes().size()==(p.id().equals(A)?before.size():0),"Town Map visibility leaked opponent prizes or hid own prizes");
        m.endTurn(A);m.endTurn(B);
        check(m.privateView(A).prizes().equals(before)&&m.clientView(B).players().stream().filter(p->p.id().equals(A)).findFirst().orElseThrow().faceUpPrizes().size()==before.size(),"Town Map changed prize order or visibility expired");
    }
    static void curry(){
        var m=NonPokemonBatch2Test.game("swsh10_en_151",EnergyType.GRASS,1);var host=m.activePokemonView(A).pokemonId();m.placeDamageCountersByEffect(A,A,host,10);
        m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);NonPokemonBatch2Test.settle(m);
        check(m.activePokemonView(A).damage()==60&&m.activePokemonView(A).conditions().contains(SpecialCondition.BURNED),"Curry healing or Burn missing");
    }
    static void mirage(){
        for(int size:List.of(6,7)){
            var m=NonPokemonBatch2Test.game("swsh11_en_163",EnergyType.GRASS,1);var card=NonPokemonBatch2Test.trainer(m);
            var energyCards=new ArrayList<CardInstance>();
            energyCards.addAll(m.privateView(A).hand().stream().filter(c->c.definitionId().equals("energy")).limit(2).toList());
            energyCards.add(DynamicPassiveModifierTest.hand(m,A,"wrong"));
            m.discardHandCardsByEffect(A,A,energyCards.stream().map(CardInstance::instanceId).toList());
            for(var energy:energyCards)m.recoverDiscardCardByEffect(A,A,energy.instanceId(),CardEffectDestination.DECK_SHUFFLED);
            var cards=m.privateView(A).hand().stream().filter(c->!c.equals(card)).limit(size).toList();
            for(var c:cards)m.sendToLostZoneByEffect(A,EffectSourceZone.HAND,c.instanceId());
            check(m.canPlayTrainer(A,card.instanceId(),null)==(size==7),"Mirage Gate ignored seven-card Lost Zone requirement");
            if(size==6)continue;
            m.playTrainer(A,card.instanceId(),null);var q=m.effectCardPrompt(A);check(q!=null,"Mirage Gate omitted energy selection");
            var same=q.candidates().stream().filter(c->c.definitionId().equals("energy")).limit(2).map(CardInstance::instanceId).toList();
            check(same.size()==2,"Fixture lacks duplicate-type energy");GeneratedRuleBatch8Test.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),same));
            var chosen=new ArrayList<UUID>();for(String id:List.of("energy","wrong"))chosen.add(q.candidates().stream().filter(c->c.definitionId().equals(id)).findFirst().orElseThrow().instanceId());
            m.submitEffectCards(A,q.resolutionId(),q.step(),chosen);NonPokemonBatch2Test.settle(m);
            var attached=new HashSet<>(m.activePokemonView(A).attachedEnergyIds());m.benchPokemonView(A).forEach(p->attached.addAll(p.attachedEnergyIds()));
            check(attached.containsAll(chosen),"Mirage Gate did not attach both chosen types");
        }
    }
    static void retrieval(){
        var m=NonPokemonBatch2Test.game("bw1_en_092",EnergyType.GRASS,1);var energy=m.privateView(A).hand().stream().filter(c->c.definitionId().equals("energy")).limit(2).toList();
        m.discardHandCardsByEffect(A,A,energy.stream().map(CardInstance::instanceId).toList());
        m.playTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null);var q=m.effectCardPrompt(A);
        check(q!=null&&q.minimum()==1&&q.maximum()==2,"Old Energy Retrieval must choose one or two public cards");
        GeneratedRuleBatch8Test.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of()));
        m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(energy.getFirst().instanceId()));NonPokemonBatch2Test.settle(m);
        check(m.privateView(A).hand().contains(energy.getFirst())&&!m.privateView(A).hand().contains(energy.getLast()),"Partial recovery did not preserve unselected energy");
    }
}
