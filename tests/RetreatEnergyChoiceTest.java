import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.rules.CardInstance;
import dev.hua.ptcglive.view.EffectCardPage;
import java.util.*;

/** Human retreat uses the existing card gallery and authoritative option indices. */
public final class RetreatEnergyChoiceTest {
    static final UUID A=new UUID(0,6361),B=new UUID(0,6362);
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void reject(Runnable run){try{run.run();}catch(IllegalArgumentException|IllegalStateException expected){return;}throw new AssertionError("Invalid request accepted");}
    static void choose(MatchSession session,UUID player,String prefix) {
        var snapshot=session.view(player,player);
        var option=snapshot.options().stream().filter(o->o.enabled()&&o.labelZh().startsWith(prefix)).findFirst().orElseThrow();
        session.submit(player,player,snapshot.revision(),option.index());
    }
    static void energy(MatchSession session,UUID player,UUID energy) {
        var snapshot=session.view(player,player);
        var option=snapshot.options().stream().filter(o->o.enabled()&&o.cardKeys().equals(List.of("effect:"+energy))).findFirst().orElseThrow();
        session.submit(player,player,snapshot.revision(),option.index());
    }
    static void confirmSuggested(MatchSession session,UUID player) {
        if(session.view(player,player).game().effectSelection()==null)return;
        for(UUID energy:session.match().suggestedRetreatPayment(player).orElseThrow())energy(session,player,energy);
        choose(session,player,"确认撤退");
    }
    static ServerMatch fixture() {
        var match=SpecialEnergyRetreatPaymentTest.fixture();var active=match.activePokemonView(A).pokemonId();
        var chosen=new ArrayList<CardInstance>();
        chosen.add(match.privateView(A).hand().stream().filter(c->c.definitionId().equals("double")).findFirst().orElseThrow());
        chosen.addAll(match.privateView(A).hand().stream().filter(c->c.definitionId().equals("single")).limit(2).toList());
        for(var card:chosen)match.attachEnergyByEffect(A,A,EffectSourceZone.HAND,card.instanceId(),A,active);
        return match;
    }
    public static void main(String[] args) {
        policyRetreat();
        for(int variant=0;variant<3;variant++) {
            var match=fixture();var session=MatchSession.multiplayer(match,A,B);
            var attached=match.retreatEnergyCards(A);UUID original=match.activePokemonView(A).pokemonId();
            var rng=match.randomTranscript();
            choose(session,A,"撤退 →");var initial=session.view(A,A);
            check(initial.game().effectSelection()!=null&&EffectCardPage.from(initial,0).cards().size()==3,"Attached energy gallery missing");
            check(session.view(B,B).game().effectSelection()==null,"Opponent received private selection");
            check(match.activePokemonView(A).pokemonId().equals(original)&&!match.retreated(),"Opening selector paid or moved early");
            var confirm=initial.options().stream().filter(o->o.labelZh().startsWith("确认撤退")).findFirst().orElseThrow();
            check(!confirm.enabled(),"Empty payment can be confirmed");reject(()->session.submit(A,A,initial.revision(),confirm.index()));
            var chosen=variant==0?List.of(attached.get(1),attached.get(2)):variant==1?List.of(attached.get(0)):List.of(attached.get(0),attached.get(1));
            for(var card:chosen)energy(session,A,card.instanceId());
            reject(()->session.submit(A,A,initial.revision(),confirm.index()));
            choose(session,A,"确认撤退");
            check(match.retreated()&&!match.activePokemonView(A).pokemonId().equals(original),"Confirmation did not retreat");
            for(var card:attached)check(match.privateView(A).discard().contains(card)==chosen.contains(card),"Server discarded an unselected energy");
            check(match.randomTranscript().equals(rng),"Choice unexpectedly consumed match RNG");
            check(session.view(A,A).game().effectSelection()==null,"Completed selector did not close");
        }
        var match=fixture();var session=MatchSession.multiplayer(match,A,B);var original=match.activePokemonView(A);
        choose(session,A,"撤退 →");var energy=match.retreatEnergyCards(A).get(1);
        energy(session,A,energy.instanceId());
        check(session.view(A,A).options().stream().noneMatch(o->o.enabled()&&o.labelZh().startsWith("确认撤退")),"Underpayment accepted");
        energy(session,A,energy.instanceId());check(session.view(A,A).game().effectSelection().selected().isEmpty(),"Unable to deselect");
        choose(session,A,"取消撤退");check(match.activePokemonView(A).equals(original)&&!match.retreated(),"Cancel consumed energy or retreat");
        match.modifyRetreatCostByEffect(A,A,original.pokemonId(),-2);choose(session,A,"撤退 →");
        check(session.view(A,A).game().effectSelection()==null&&match.privateView(A).discard().isEmpty(),"Free retreat discarded energy or opened selector");
        var ai=fixture();var aiSession=MatchSession.multiplayer(ai,A,B);
        aiSession.computerOpponent(new MatchSession.ComputerOpponent(A,"fixture","电脑","","",0));
        choose(aiSession,A,"撤退 →");check(ai.retreated()&&aiSession.view(A,A).game().effectSelection()==null,"AI got stuck in human UI selection");
        System.out.println("RetreatEnergyChoiceTest passed: explicit cards, multi-unit payment, cancel, stale requests, recipient isolation, free retreat and AI");
    }
    static void policyRetreat() {
        for(boolean singles:List.of(false,true)) {
            var match=fixture();var session=MatchSession.multiplayer(match,A,B);
            var attached=match.retreatEnergyCards(A);var before=match.randomTranscript();
            choose(session,A,"撤退 →");
            var payment=singles?List.of(attached.get(1),attached.get(2)):List.of(attached.get(0));
            for(var card:payment)energy(session,A,card.instanceId());
            var view=AiCustomDeckTest.view(session,A,dev.hua.ptcglive.ai.AiDeckAnalysis.EMPTY);
            var decision=dev.hua.ptcglive.ai.AiPolicy.choose(view,913,Set.of()).orElseThrow();
            check(view.snapshot().options().get(decision.option()).labelZh().startsWith("确认撤退"),"AI failed to confirm paid retreat");
            session.submit(A,A,decision.revision(),decision.option());
            check(match.retreated()&&session.view(A,A).game().effectSelection()==null,"AI retreat unresolved");
            check(match.randomTranscript().equals(before),"AI retreat scoring consumed real randomness");
            for(var card:attached)check(match.privateView(A).discard().contains(card)==payment.contains(card),"AI discarded an unselected card");
            reject(()->session.submit(A,A,decision.revision(),decision.option()));
        }
    }
}
