import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class StarterDeck100PeoniaTest {
    static final UUID A=StarterDeck100PokemonTest.A,B=StarterDeck100PokemonTest.B;
    static void check(boolean ok,String why){StarterDeck100PokemonTest.check(ok,why);}
    public static void main(String[] args){sessionFlow();for(int n:List.of(1,2,3))exchange(n,false,false);exchange(2,true,false);exchange(3,false,true);concede();System.out.println("StarterDeck100PeoniaTest passed: blind slots, mandatory1..3, exact ordered private return, selected-Prize reuse, stable counts, all-remaining Prizes, privacy, forgery/replay, no RNG and concession cleanup");}
    static void exchange(int count,boolean allRemaining,boolean putSameBack){
        var m=StarterDeck100CommonTest.game(397,allRemaining?2:0);if(allRemaining)for(int i=0;i<2;i++){m.placeDamageCountersByEffect(A,B,m.activePokemonView(B).pokemonId(),22);StarterDeck100CombatTest.settleKnockouts(m);}
        var prizes=List.copyOf(m.privateView(A).prizes());var rng=m.randomTranscript();var played=StarterDeck100PokemonTest.hand(m,StarterDeck100PokemonTest.id(397));
        StarterDeck100CommonTest.play(m,397);var hand=List.copyOf(m.privateView(A).hand());var p=m.prizeSwapPrompt(A);
        check(p.maximum()==Math.min(3,prizes.size())&&p.selectedSlots().isEmpty(),"wrong blind slot range");
        TopDeckLookTest.rejects(()->m.confirmPrizeSwapSlots(A,p.resolutionId(),p.step()));TopDeckLookTest.rejects(()->m.prizeSwapPrompt(B));
        TopDeckLookTest.rejects(()->m.togglePrizeSwapSlot(B,p.resolutionId(),p.step(),0));TopDeckLookTest.rejects(()->m.togglePrizeSwapSlot(A,p.resolutionId(),p.step(),99));
        var session=MatchSession.multiplayer(m,A,B);String visible=new com.google.gson.Gson().toJson(session.view(A,A));for(var c:prizes)check(!visible.contains(c.instanceId().toString()),"blind choice leaked Prize identity");
        check(visible.contains("暗面奖赏位置"),"Minecraft action list lacks blind Prize choices");
        for(int i=0;i<count;i++)m.togglePrizeSwapSlot(A,p.resolutionId(),p.step(),i);
        m.confirmPrizeSwapSlots(A,p.resolutionId(),p.step());var q=m.effectCardPrompt(A);
        TopDeckLookTest.rejects(()->m.confirmPrizeSwapSlots(A,p.resolutionId(),p.step()));
        check(q.minimum()==count&&q.maximum()==count&&q.candidates().containsAll(prizes.subList(0,count))&&!q.candidates().contains(played),"private return pool wrong");
        for(var c:prizes.subList(count,prizes.size()))check(!q.candidates().contains(c),"unchosen Prize revealed");
        for(UUID viewer:Arrays.asList(B,null)){String other=new com.google.gson.Gson().toJson(m.clientView(viewer));for(var c:q.candidates())check(!other.contains(c.instanceId().toString()),"exchange private pool leaked");}
        check(m.publicView().get(A).prizeCount()==prizes.size()&&m.phase()!=MatchPhase.FINISHED,"temporary empty Prizes caused win");
        var chosen=new ArrayList<>(putSameBack?prizes.subList(0,count):hand.subList(0,count));Collections.reverse(chosen);
        var before=m.clientView(A);TopDeckLookTest.rejects(()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of()));
        TopDeckLookTest.rejects(()->m.submitEffectCards(A,q.resolutionId(),q.step(),Collections.nCopies(count+1,chosen.getFirst().instanceId())));
        TopDeckLookTest.rejects(()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(played.instanceId())));
        check(before.equals(m.clientView(A)),"invalid return mutated zones");
        m.submitEffectCards(A,q.resolutionId(),q.step(),chosen.stream().map(CardInstance::instanceId).toList());
        check(!m.hasPendingEffectChoice()&&m.phase()==MatchPhase.TURN&&m.publicView().get(A).prizeCount()==prizes.size()&&m.privateView(A).hand().size()==hand.size(),"exchange changed counts or failed to complete");
        for(int i=0;i<count;i++)check(m.privateView(A).prizes().get(i).equals(chosen.get(i)),"return order was shuffled");
        check(m.privateView(A).prizes().subList(count,prizes.size()).equals(prizes.subList(count,prizes.size())),"untouched Prize positions changed");
        var expected=new HashSet<>(hand);expected.addAll(prizes);var actual=new HashSet<>(m.privateView(A).hand());actual.addAll(m.privateView(A).prizes());check(actual.equals(expected)&&rng.equals(m.randomTranscript()),"card conservation or no-shuffle rule failed");
        TopDeckLookTest.rejects(()->m.submitEffectCards(A,q.resolutionId(),q.step(),chosen.stream().map(CardInstance::instanceId).toList()));
        var another=m.privateView(A).hand().stream().filter(c->c.definitionId().equals(StarterDeck100PokemonTest.id(397))).findFirst();if(another.isPresent())TopDeckLookTest.rejects(()->m.playTrainer(A,another.get().instanceId(),null));
    }
    static void sessionFlow(){var m=StarterDeck100CommonTest.game(397,0);StarterDeck100CommonTest.play(m,397);var session=MatchSession.multiplayer(m,A,B);var watch=new UUID(0,77771);session.addSpectator(watch);var before=session.view(A,A);var pick=before.options().stream().filter(o->o.labelZh().equals("选择暗面奖赏位置 1")).findFirst().orElseThrow();session.submit(A,A,before.revision(),pick.index());TopDeckLookTest.rejects(()->session.submit(A,A,before.revision(),pick.index()));submit(session,"确认选择");var privatePool=m.effectCardPrompt(A).candidates();String spectator=new com.google.gson.Gson().toJson(session.view(watch,null));for(var c:privatePool)check(!spectator.contains(c.instanceId().toString()),"network spectator saw private pool");submit(session,"选择卡牌：");submit(session,"确认选牌");check(!m.hasPendingEffectChoice()&&m.privateView(A).prizes().size()==6,"Minecraft issued actions could not complete Prize exchange");}
    static void submit(MatchSession session,String prefix){var view=session.view(A,A);var option=view.options().stream().filter(o->o.labelZh().startsWith(prefix)).findFirst().orElseThrow();session.submit(A,A,view.revision(),option.index());}
    static void concede(){var m=StarterDeck100CommonTest.game(397,0);StarterDeck100CommonTest.play(m,397);var p=m.prizeSwapPrompt(A);m.togglePrizeSwapSlot(A,p.resolutionId(),p.step(),0);m.confirmPrizeSwapSlots(A,p.resolutionId(),p.step());var prizes=List.copyOf(m.privateView(A).prizes());var hand=List.copyOf(m.privateView(A).hand());m.concede(A);check(m.phase()==MatchPhase.FINISHED&&!m.hasPendingPrizeSwapSelection()&&!m.hasPendingCardSelection()&&prizes.equals(m.privateView(A).prizes())&&hand.equals(m.privateView(A).hand()),"concession leaked/duplicated staged Prize exchange");}
}
