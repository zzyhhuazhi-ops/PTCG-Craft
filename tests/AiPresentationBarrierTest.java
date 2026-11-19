import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.table.*;
import java.time.Clock;
import java.util.*;

/** Real server scheduler: delayed, stale and foreign acknowledgements across a complete game. */
public final class AiPresentationBarrierTest {
    static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    public static void main(String[] args){
        AiPracticeTest.cards=BundledRules.loadCardDefinitions();AiPracticeTest.combat=BundledCombatData.load();AiPracticeTest.rules=BundledRules.loadRulesets();
        var boxes=new CardBoxService(Clock.systemUTC());var tables=AiPracticeLifecycle.manager(boxes);
        var owner=AiPracticeTest.A;var stranger=AiPracticeTest.C;var preset=AiPresets.all().getFirst();
        var box=AiPracticeLifecycle.box(boxes,owner,preset);var key=new CardTableKey("minecraft:overworld",1714);
        tables.open(key,owner,preset.rule());var id=AiPracticeLifecycle.start(tables,key,owner,box.id(),box.id(),preset.rule());
        long revision=-1,release=0;int heldTicks=0,actions=0,turn=-1,mulliganWaits=0;boolean reopened=false,closed=false;
        var used=new HashSet<String>();var attackEvents=new HashSet<Long>();
        for(long tick=0;tick<100_000&&tables.computerRunning(id);tick++){
            var view=tables.matchView(id,owner,owner);
            for(var event:view.events())if(event.cue().kind()==dev.hua.ptcglive.view.MatchPresentation.Kind.ATTACK)attackEvents.add(event.sequence());
            if(view.revision()!=revision){
                revision=view.revision();release=tick+(revision==0?700:90);
                tables.beginComputerPresentation(id,owner,revision,false);
            }
            if(tick<release){
                var rng=tables.session(id).match().randomTranscript();
                var audit=tables.session(id).audit();
                tables.finishComputerPresentation(id,stranger,revision);
                tables.finishComputerPresentation(id,owner,revision-1);
                tables.finishComputerPresentation(id,owner,revision+1);
                check(tables.tickComputers(tick).isEmpty(),"AI acted before actual presentation acknowledgement");
                check(tables.tickReadAcknowledgements(tick).isEmpty(),"automatic reveal acknowledgement bypassed presentation");
                check(tables.session(id).publicRevision()==revision&&tables.session(id).audit().equals(audit),"waiting changed authoritative game");
                check(tables.session(id).match().randomTranscript().equals(rng),"waiting consumed battle randomness");
                heldTicks++;continue;
            }
            if(closed){
                closed=false;tables.beginComputerPresentation(id,owner,revision,true);release=tick+100;continue;
            }
            tables.finishComputerPresentation(id,owner,revision);
            tables.finishComputerPresentation(id,owner,revision); // duplicate is inert
            tables.beginComputerPresentation(id,owner,revision,false); // unchanged refresh must not re-arm
            tables.beginComputerPresentation(id,stranger,revision,true); // spectator cannot hold it either
            if(!reopened&&revision>2){
                reopened=true;closed=true;tables.suspendComputerPresentation(id,owner);release=tick+100;continue;
            }
            var beforeBot=tables.session(id).view(view.computer().seat(),view.computer().seat());
            if(beforeBot.game().phase()==MatchPhase.OPENING_POKEMON&&tables.session(id).match().needsMulligan(view.computer().seat()))mulliganWaits++;
            var updates=tables.tickComputers(tick);actions+=updates.size();
            if(updates.stream().anyMatch(CardTableManager.AiUpdate::aborted)){
                try{java.nio.file.Files.writeString(java.nio.file.Path.of("outputs/pocket-animation/global/fire-review-20261114/barrier-failure.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(beforeBot));}
                catch(java.io.IOException e){throw new AssertionError(e);}
                throw new AssertionError("acknowledged AI aborted at tick="+tick+" revision="+revision+" phase="+beforeBot.game().phase()+" choice="+beforeBot.game().choicePlayer()+" options="+beforeBot.options());
            }
            if(!tables.computerRunning(id))break;
            if(tables.session(id).publicRevision()!=revision)continue;
            var session=tables.session(id);if(!AiPracticeLifecycle.humanCanAct(session.match(),owner))continue;
            if(turn!=session.match().turnNumber()){turn=session.match().turnNumber();used.clear();}
            var input=AiPracticeScenarios.view(session,owner);var decision=AiPolicy.choose(input,31314L+tick,used);
            if(decision.isPresent()){
                var d=decision.get();var choice=input.snapshot().options().get(d.option());
                tables.submit(id,owner,owner,d.revision(),d.option());var optional=AiPolicy.optionalKey(choice);if(!optional.isEmpty())used.add(optional);
            }
        }
        check(!tables.computerRunning(id)&&tables.matchView(id,owner,owner).game().result()!=null,"AI never resumed or game deadlocked");
        check(heldTicks>700&&actions>10&&!attackEvents.isEmpty()&&reopened&&mulliganWaits>0,"insufficient real lifecycle coverage");
        check(!boxes.isLocked(owner,box.id()),"result left assets locked");
        var again=tables.rematchComputer(id,owner);var current=tables.matchView(again,owner,owner);
        tables.beginComputerPresentation(again,owner,current.revision(),true);
        tables.finishComputerPresentation(id,owner,revision); // old match cannot release new match
        check(tables.tickComputers(200_000).isEmpty(),"old match acknowledgement released rematch");
        tables.disconnectPlayer(owner);
        check(!tables.computerRunning(again)&&!boxes.isLocked(owner,box.id()),"disconnect left pending AI/box lock");
        System.out.println("AiPresentationBarrierTest passed: full match, "+actions+" AI steps, "+heldTicks+" held ticks, "+mulliganWaits+" mulligan waits, stale/foreign/future/duplicate ACK, refresh, close after ACK, reopen, rematch and disconnect");
    }
}
