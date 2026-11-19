import dev.hua.ptcglive.rules.CardBoxService;
import dev.hua.ptcglive.view.HistoryRequestState;
import java.time.Clock;
import java.util.*;

public final class CompletedHistoryTest {
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] args){
        var owner=UUID.randomUUID();var service=new CardBoxService(Clock.systemUTC());
        service.addHistory(owner,"card",1,0,0);check(service.history(owner,"card").winRate().equals("—"),"Interrupted match counted in rate");
        service.addHistory(owner,"card",1,0,0);service.completeHistory(owner,"card",true);
        service.addHistory(owner,"card",1,0,0);service.completeHistory(owner,"card",false);
        var h=service.history(owner,"card");check(h.matches()==3&&h.completedMatches()==2&&h.wins()==1&&h.winRate().equals("50.0%"),"Completed denominator wrong");
        check(CardBoxService.restore(Clock.systemUTC(),service.snapshot()).history(owner,"card").equals(h),"Completion lost on reload");
        var old=new CardBoxService.CardHistory(owner,"old",0,10,4,6);
        var migrated=CardBoxService.restore(Clock.systemUTC(),new CardBoxService.Snapshot(List.of(),List.of(),List.of(),List.of(old)));
        check(migrated.history(owner,"old").winRate().equals("—")&&old.legacyResultsUnknown(),"Invented old completion results");
        migrated.completeHistory(owner,"old",true);check(migrated.history(owner,"old").winRate().equals("100.0%")&&migrated.history(owner,"old").wins()==5,"Old wins mixed into new denominator");
        var request=new HistoryRequestState();int retries=0;for(int i=0;i<400;i++)if(request.tick())retries++;
        check(retries==2&&request.timedOut(),"Unbounded retry");request.retry();check(!request.timedOut(),"Manual retry failed");request.receive();
        for(int i=0;i<200;i++)check(!request.tick(),"Request after response");check(request.received(),"Missing response");
        System.out.println("CompletedHistoryTest passed: interrupted/finished denominator, legacy unknown, save/reload, timeout and manual retry");
    }
}
