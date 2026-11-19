import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Printed activation costs must be complete, private, and distinct from their reward. */
public final class StarterDeck100CostTest {
    static final UUID A=StarterDeck100PokemonTest.A,B=StarterDeck100PokemonTest.B;
    static String id(int n){return StarterDeck100PokemonTest.id(n);}
    static void check(boolean b,String s){StarterDeck100PokemonTest.check(b,s);}
    static void select(ServerMatch m,int n){StarterDeck100PokemonTest.select(m,n);}
    static void ability(ServerMatch m){StarterDeck100PokemonTest.ability(m,m.activePokemonView(A).pokemonId());}
    public static void main(String[] args){zinnia();reconstitute();malformed();System.out.println("StarterDeck100CostTest passed: paid hand-discard costs, excluded Supporter, forgery/partial rejection, private selection and source-scoped limits");}
    static void zinnia(){for(int available:List.of(0,1,2)){var m=StarterDeck100CommonTest.game(405,0);var trainer=StarterDeck100CombatTest.hand(m,id(405));var extras=m.privateView(A).hand().stream().filter(c->!c.equals(trainer)).toList();if(extras.size()>available)m.discardHandCardsByEffect(A,A,extras.subList(available,extras.size()).stream().map(CardInstance::instanceId).toList());var before=m.privateView(A);if(available<2){check(!m.canPlayTrainer(A,trainer.instanceId(),null),"Zinnia accepted insufficient other cards");TopDeckLookTest.rejects(()->m.playTrainer(A,trainer.instanceId(),null));check(m.privateView(A).equals(before)&&m.mayPlaySupporter(A),"failed cost moved cards or consumed Supporter");continue;}
        m.playTrainer(A,trainer.instanceId(),null);var p=m.effectCardPrompt(A);check(p.minimum()==2&&p.maximum()==2&&p.candidates().size()==2&&!p.candidates().contains(trainer),"cost included resolving Supporter or allowed partial payment");TopDeckLookTest.rejects(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(p.candidates().getFirst().instanceId())));TopDeckLookTest.rejects(()->m.submitEffectCards(B,p.resolutionId(),p.step(),p.candidates().stream().map(CardInstance::instanceId).toList()));check(m.privateView(A).deck().size()==before.deck().size(),"Zinnia drew before full cost");select(m,2);int opposing=m.benchPokemonView(B).size()+1;check(m.privateView(A).hand().size()==opposing&&m.privateView(A).discard().containsAll(before.hand())&&!m.mayPlaySupporter(A),"Zinnia reward did not count opposing field or consume Supporter");}}
    static void reconstitute(){for(int n:List.of(169,420)){var m=StarterDeck100PokemonTest.game(n);StarterDeck100PokemonTest.trim(m,1);var before=m.privateView(A);check(!m.canUseAbility(A,m.activePokemonView(A).pokemonId(),"reconstitute"),"Reconstitute payable with only one card");TopDeckLookTest.rejects(()->ability(m));check(m.privateView(A).equals(before),"failed ability cost mutated hand");StarterDeck100PokemonTest.hand(m,"sd100_cn_grass_energy");if(m.privateView(A).hand().size()<2)StarterDeck100PokemonTest.hand(m,"sd100_cn_psychic_energy");var paid=List.copyOf(m.privateView(A).hand());ability(m);var p=m.effectCardPrompt(A);check(p.minimum()==2&&p.maximum()==2,"Reconstitute cost not exact");select(m,2);check(m.privateView(A).hand().size()==paid.size()-1&&m.privateView(A).discard().containsAll(paid)&&!m.canUseAbility(A,m.activePokemonView(A).pokemonId(),"reconstitute"),"Reconstitute reward or once-per-turn wrong");StarterDeck100PokemonTest.attack(m,0);check(m.lastAttack().finalDamage()==110&&m.activePokemonView(B).conditions().contains(SpecialCondition.CONFUSED),"Psyray damage/confusion not implemented");}}
    static void malformed(){for(String effects:List.of(
        "{\"type\":\"discard\",\"target\":\"HAND\",\"amount\":2,\"minimum\":1,\"as_cost\":true}",
        "{\"type\":\"draw\",\"amount\":1},{\"type\":\"discard\",\"target\":\"HAND\",\"amount\":2,\"minimum\":2,\"as_cost\":true}",
        "{\"type\":\"choice\",\"optional\":true,\"effects\":[{\"type\":\"discard\",\"target\":\"HAND\",\"amount\":2,\"minimum\":2,\"as_cost\":true}]}"))TopDeckLookTest.rejects(()->TrainerProgramRules.validate(ComposedTrainerIntegrationTest.item(effects).effectProgram()));}
}
