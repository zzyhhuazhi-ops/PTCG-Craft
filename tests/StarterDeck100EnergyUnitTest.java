import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Unit choices move indivisible Energy cards; two selected units may come from two multi-unit cards. */
public final class StarterDeck100EnergyUnitTest {
    static final UUID A=StarterDeck100PokemonTest.A,B=StarterDeck100PokemonTest.B;
    static final String DOUBLE="sm1_en_136",GRASS="sd100_cn_grass_energy";
    static void check(boolean b,String s){StarterDeck100PokemonTest.check(b,s);}
    static CardInstance attach(ServerMatch m,String id){return StarterDeck100EnergyTest.attach(m,A,A,id,m.activePokemonView(A).pokemonId());}
    public static void main(String[] args){superbeam(0);superbeam(1);superbeam(2);bugTransmission();bodySplash();System.out.println("StarterDeck100EnergyUnitTest passed: unit/card distinctions, underpayment atomicity, shared tails and exact attachment origins");}
    static void superbeam(int variant){
        var m=StarterDeck100PokemonTest.game(340,DOUBLE);var d1=attach(m,DOUBLE);var basic=attach(m,GRASS);var d2=attach(m,DOUBLE);
        check(m.triggerOrderPrompt()==null,"Deck attachment triggered Bug Transmission");m.attack(A,"superbeam");var p=m.effectCardPrompt(A);
        check(p.minimum()==1&&p.maximum()==2&&p.candidates().size()==3,"unit choice exposed wrong physical bounds");
        var before=m.activePokemonView(A);TopDeckLookTest.rejects(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(basic.instanceId())));
        check(m.activePokemonView(A).equals(before)&&m.effectCardPrompt(A).equals(p),"underpayment changed cards or invalidated prompt");
        var picked=variant==0?List.of(d1.instanceId()):variant==1?List.of(d1.instanceId(),basic.instanceId()):List.of(d1.instanceId(),d2.instanceId());
        TopDeckLookTest.rejects(()->m.submitEffectCards(B,p.resolutionId(),p.step(),picked));m.submitEffectCards(A,p.resolutionId(),p.step(),picked);
        check(m.lastAttack().finalDamage()==170&&m.activePokemonView(A).attachedEnergyIds().size()==3-picked.size(),"Superbeam failed legal unit payment");
        check(m.privateView(A).discard().stream().filter(c->picked.contains(c.instanceId())).count()==picked.size(),"Energy did not reach owner's discard");
    }
    static void bugTransmission(){for(boolean manual:List.of(false,true)){
        var m=StarterDeck100PokemonTest.game(340);UUID host=m.activePokemonView(A).pokemonId();var energy=StarterDeck100PokemonTest.hand(m,GRASS);
        if(manual)m.attachEnergy(A,energy.instanceId(),host);else m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,energy.instanceId(),A,host);
        StarterDeck100EntryTest.accept(m);check(m.activePokemonView(B).conditions().contains(SpecialCondition.CONFUSED),"HAND attachment failed Bug Transmission");
        m.clearSpecialConditionsByEffect(A,B,m.activePokemonView(B).pokemonId(),Set.of(SpecialCondition.CONFUSED));
        m.discardAttachedEnergyByEffect(A,A,host,List.of(energy.instanceId()));m.attachEnergyByEffect(A,A,EffectSourceZone.DISCARD,energy.instanceId(),A,host);
        check(m.triggerOrderPrompt()==null&&!m.activePokemonView(B).conditions().contains(SpecialCondition.CONFUSED),"DISCARD attachment triggered Bug Transmission");
        var another=StarterDeck100PokemonTest.hand(m,GRASS);m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,another.instanceId(),A,host);StarterDeck100EntryTest.accept(m);
        check(m.activePokemonView(B).conditions().contains(SpecialCondition.CONFUSED),"Bug Transmission incorrectly limited to once per turn");
    }}
    static void bodySplash(){var seen=new HashSet<Integer>();for(int seed=0;seed<160&&seen.size()<4;seed++){
        var m=StarterDeck100PokemonTest.gameSeed(54,1,seed);StarterDeck100PokemonTest.charge(m,1);int before=m.randomTranscript().size();m.attack(A,"body-splash");
        var flips=m.randomTranscript().subList(before,m.randomTranscript().size()).stream().filter(e->e.operation().startsWith("effect-coin:")).toList();
        check(flips.size()==3,"Body Splash must flip exactly three times");int tails=(int)flips.stream().filter(e->e.result()==1).count();seen.add(tails);
        if(tails>0){var p=m.effectCardPrompt(A);check(p!=null&&p.maximum()==tails,"discard choice did not reuse the actual tails");StarterDeck100PokemonTest.select(m,tails);}
        check(m.activePokemonView(A).attachedEnergyIds().size()==3-tails&&m.lastAttack().finalDamage()==300,"Body Splash damage or tails discard wrong");
    }check(seen.size()==4,"Body Splash fixture did not cover 0/1/2/3 tails");}
}
