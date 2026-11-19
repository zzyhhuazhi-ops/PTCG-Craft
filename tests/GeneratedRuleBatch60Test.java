import com.google.gson.Gson;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** All three actual Gwynn programs: filtered effect discard and one draw from its committed count. */
public final class GeneratedRuleBatch60Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B,WATCH=new UUID(0,6000);
    static final List<String> PRINTS=List.of("me5_en_078","me5_en_109","me5_en_119");
    static void check(boolean b,String why){GeneratedRuleBatch8Test.check(b,why);}
    static void atomic(ServerMatch m,Runnable r){GeneratedRuleBatch44Test.assertRejectedAtomic(m,r);}
    static CardInstance hand(ServerMatch m,String id){return GeneratedRuleBatch13Test.hand(m,A,id);}
    static ServerMatch game(String id){return GeneratedRuleBatch59Test.game(id);}
    static CardInstance play(ServerMatch m){return GeneratedRuleBatch59Test.play(m);}
    static boolean eligible(ServerMatch m,CardInstance c){var d=m.cardDefinition(c.definitionId());return d.category()==CardCategory.POKEMON&&!d.pokemonFamilies().contains(PokemonFamily.RULE_BOX);}
    static void keep(ServerMatch m,Collection<CardInstance> cards){m.discardHandCardsByEffect(A,A,m.privateView(A).hand().stream().filter(c->!cards.contains(c)).map(CardInstance::instanceId).toList());}
    static void deck(ServerMatch m,int n){int size=m.privateView(A).deck().size();check(size>=n,"Fixture deck too short");m.discardTopDeckCardsByEffect(A,A,size-n);}
    static void choose(ServerMatch m,List<CardInstance> cards){var p=m.effectCardPrompt(A);m.submitEffectCards(A,p.resolutionId(),p.step(),cards.stream().map(CardInstance::instanceId).toList());}
    static void unavailable(ServerMatch m){GeneratedRuleBatch59Test.unavailable(m);}
    public static void main(String[] args){printed();drawCounts();eligibility();oneCandidate();authority();staleCard();privacy();reuse();concession();System.out.println("GeneratedRuleBatch60Test passed: three actual Gwynn prints, mandatory1..2 non-RuleBox Basic/evolved Pokemon effect discards, exact actual-count single draw, empty/short deck without defeat, no eligible/last-Hand-card rejection, RuleBox and category filters, private choices, atomic foreign/duplicate/stale submissions, independent later counts and Supporter quota");}
    static void printed(){var db=BundledRules.loadCardDefinitions();for(String id:PRINTS){var d=db.find(id).orElseThrow();var t=GeneratedRuleBatch8Test.trainer(id);check(d.effectSupport()==EffectSupport.SUPPORTED&&t.category()==TrainerCategory.SUPPORTER&&TrainerProgramRules.unsupportedReason(t.effectProgram()).isEmpty(),"Actual Gwynn unsupported "+id);}}
    static void drawCounts(){for(String id:PRINTS)for(int count:List.of(1,2))for(int left:List.of(0,1,3,5,8)){
        var m=game(id);deck(m,left);var before=m.privateView(A);var other=m.privateView(B);int rng=m.randomTranscript().size(),turn=m.turnNumber();var trainer=play(m);var p=m.effectCardPrompt(A);
        var candidates=before.hand().stream().filter(c->eligible(m,c)).toList();check(p.minimum()==1&&p.maximum()==2&&new HashSet<>(p.candidates()).equals(new HashSet<>(candidates)),"Printed selection limits/filter "+id);
        check(m.privateView(A).deck().equals(before.deck())&&m.privateView(A).hand().size()==before.hand().size()-1,"Cards drawn/discarded before selection");
        var chosen=count==1?List.of(hand(m,"evolved")):List.of(hand(m,"basic"),hand(m,"evolved"));choose(m,chosen);int drawn=Math.min(left,count*3);
        check(m.privateView(A).hand().size()==before.hand().size()-1-count+drawn,"Draw used requested maximum or multiple branches "+id+"/"+count+"/"+left);
        check(m.privateView(A).deck().equals(before.deck().subList(0,left-drawn)),"Wrong top cards drawn or deck shuffled");
        check(m.privateView(A).discard().containsAll(chosen)&&m.privateView(A).discard().contains(trainer)&&m.privateView(A).discard().size()==before.discard().size()+count+1,"Wrong physical cards discarded");
        check(m.privateView(A).hand().containsAll(before.deck().subList(left-drawn,left))&&m.privateView(B).equals(other)&&m.randomTranscript().size()==rng,"Draw missed cards or changed opponent/RNG");
        check(m.phase()==MatchPhase.TURN&&m.currentPlayer().equals(A)&&m.turnNumber()==turn&&!m.hasPendingEffectChoice(),"Empty deck caused defeat or Supporter ended turn");unavailable(m);
    }}
    static void eligibility(){
        for(String id:PRINTS)for(String mode:List.of("LAST","NON_POKEMON","RULE_BOX")){
            var c=new GeneratedRuleBatch59Test.Config(id);if(mode.equals("RULE_BOX")){c.family=Set.of(PokemonFamily.RULE_BOX,PokemonFamily.EX);c.evolvedFamily=Set.of(PokemonFamily.RULE_BOX,PokemonFamily.V);}
            var m=GeneratedRuleBatch59Test.game(c);if(mode.equals("LAST"))keep(m,List.of(hand(m,"trainer")));if(mode.equals("NON_POKEMON"))keep(m,m.privateView(A).hand().stream().filter(x->m.cardDefinition(x.definitionId()).category()!=CardCategory.POKEMON).toList());unavailable(m);
        }
        for(var family:List.of(PokemonFamily.EX,PokemonFamily.V,PokemonFamily.GX,PokemonFamily.RADIANT,PokemonFamily.EX_LEGACY)){
            var c=new GeneratedRuleBatch59Test.Config(PRINTS.getFirst());c.family=Set.of(PokemonFamily.RULE_BOX,family);var m=GeneratedRuleBatch59Test.game(c);var excluded=hand(m,"basic");play(m);var p=m.effectCardPrompt(A);check(p.candidates().stream().allMatch(x->x.definitionId().equals("evolved")),"RuleBox filter allowed "+family);atomic(m,()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(excluded.instanceId())));choose(m,List.of(hand(m,"evolved")));
        }
        var c=new GeneratedRuleBatch59Test.Config(PRINTS.getFirst());c.family=Set.of(PokemonFamily.FUTURE);c.evolvedFamily=Set.of(PokemonFamily.ULTRA_BEAST);var m=GeneratedRuleBatch59Test.game(c);play(m);choose(m,List.of(hand(m,"basic"),hand(m,"evolved")));check(!m.hasPendingEffectChoice(),"Non-RuleBox family labels excluded");
    }
    static void oneCandidate(){var m=game(PRINTS.getFirst());var only=hand(m,"evolved");keep(m,List.of(only,hand(m,"trainer")));int n=m.privateView(A).deck().size();play(m);var p=m.effectCardPrompt(A);check(p.minimum()==1&&p.maximum()==2&&p.candidates().equals(List.of(only)),"One eligible card prevented using up-to2");atomic(m,()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of()));choose(m,List.of(only));check(m.privateView(A).hand().size()==3&&m.privateView(A).deck().size()==n-3,"One actual card did not draw3");}
    static void authority(){var m=game(PRINTS.getFirst());var trainer=play(m);var p=m.effectCardPrompt(A);var chosen=p.candidates().getFirst();var id=chosen.instanceId();var foreign=GeneratedRuleBatch13Test.hand(m,B,"opponent").instanceId();
        for(Runnable r:List.<Runnable>of(()->m.submitEffectCards(B,p.resolutionId(),p.step(),List.of(id)),()->m.submitEffectCards(A,UUID.randomUUID(),p.step(),List.of(id)),()->m.submitEffectCards(A,p.resolutionId(),p.step()+1,List.of(id)),()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of()),()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(id,id)),()->m.submitEffectCards(A,p.resolutionId(),p.step(),p.candidates().stream().limit(3).map(CardInstance::instanceId).toList()),()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(foreign)),()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(trainer.instanceId())),()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(hand(m,"energy").instanceId())),()->m.endTurn(A)))atomic(m,r);
        check(m.effectCardPrompt(A).equals(p),"Rejected request changed prompt");choose(m,List.of(chosen));atomic(m,()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(id)));
    }
    static void staleCard(){var m=game(PRINTS.getFirst());play(m);var p=m.effectCardPrompt(A);var stale=p.candidates().getFirst();check(GeneratedRuleBatch47Test.zone(m,A,"hand").remove(stale),"Missing stale card");GeneratedRuleBatch47Test.zone(m,A,"discard").add(stale);atomic(m,()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(stale.instanceId())));var before=m.privateView(A);choose(m,List.of(p.candidates().getLast()));check(m.privateView(A).hand().size()==before.hand().size()-1+3,"Rejected stale card contaminated actual discard count");}
    static void privacy(){var m=game(PRINTS.getFirst());var session=MatchSession.multiplayer(m,A,B);session.addSpectator(WATCH);play(m);var p=m.effectCardPrompt(A);var hidden=List.copyOf(m.privateView(A).hand());for(var view:List.of(session.view(B,B),session.view(WATCH,null))){String wire=new Gson().toJson(view);for(var c:hidden)check(!wire.contains(c.instanceId().toString()),"Hand selection leaked physical card identity");}check(m.clientView(B).effectSelection()==null&&session.view(WATCH,null).options().isEmpty(),"Opponent/spectator controls private discard");choose(m,List.of(p.candidates().getFirst()));}
    static void reuse(){for(int first:List.of(1,2)){var m=game(PRINTS.getFirst());play(m);var old=m.effectCardPrompt(A);choose(m,old.candidates().subList(0,first));unavailable(m);m.endTurn(A);m.endTurn(B);play(m);var next=m.effectCardPrompt(A);check(!old.resolutionId().equals(next.resolutionId()),"Later card reused old resolution");atomic(m,()->m.submitEffectCards(A,old.resolutionId(),old.step(),List.of(next.candidates().getFirst().instanceId())));int hand=m.privateView(A).hand().size(),deck=m.privateView(A).deck().size(),count=3-first;choose(m,next.candidates().subList(0,count));check(m.privateView(A).hand().size()==hand-count+Math.min(deck,count*3),"Count leaked between Supporter resolutions");}}
    static void concession(){var m=game(PRINTS.getFirst());var trainer=play(m);var p=m.effectCardPrompt(A);var before=m.privateView(A);m.concede(A);var after=m.privateView(A);check(m.phase()==MatchPhase.FINISHED&&!m.hasPendingEffectChoice()&&after.hand().equals(before.hand())&&after.deck().equals(before.deck())&&after.discard().size()==before.discard().size()+1&&after.discard().contains(trainer),"Concession failed to discard resolving Trainer or moved selection/draw cards");atomic(m,()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(p.candidates().getFirst().instanceId())));}
}
