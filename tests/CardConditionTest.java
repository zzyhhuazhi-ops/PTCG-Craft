import dev.hua.ptcglive.rental.RentalProduction;
import dev.hua.ptcglive.economy.PokeCoinLedger;
import java.time.Clock;
import java.util.UUID;

public final class CardConditionTest {
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] args){
        for(int tier:new int[]{1,3,9})for(int rank=0;rank<9;rank++)for(int percent:new int[]{50,75,100,125,150,200})for(int multiplier=2;multiplier<=4;multiplier++){
            int rate=RentalProduction.rate(rank,9);long cap=rate*60L*percent*tier/100;
            var one=RentalProduction.settleConditioned(0,0,0,60000,rate,percent,0,multiplier==2?0:63,multiplier,multiplier,tier);
            check(one.total()*8+one.eighth()==rate*multiplier*percent/25,"fractional minute");
            var early=RentalProduction.settleConditioned(0,0,0,59999,rate,percent,0,multiplier==2?0:63,multiplier,multiplier,tier);check(early.total()==0&&early.stock()==(multiplier==2?0:63),"early payout/fuel");
            var full=RentalProduction.settleConditioned(0,0,0,86400000,rate,percent,0,multiplier==2?0:63,multiplier,multiplier,tier);
            check(full.total()==cap&&full.eighth()==0,"graded capacity");
            var again=RentalProduction.settleConditioned(full.total(),0,full.since(),86460000,rate,percent,0,full.stock(),multiplier,full.cycleMultiplier(),tier);
            check(again.total()==cap&&again.stock()==full.stock(),"full storage consumes fuel");
        }
        var ledger=new PokeCoinLedger(Clock.systemUTC());var owner=UUID.randomUUID();
        for(int i=0;i<8;i++)ledger.queueFinalRentalEighth(UUID.randomUUID(),owner,0,1);
        ledger=PokeCoinLedger.restore(Clock.systemUTC(),ledger.snapshot());ledger.retryRentals(0);ledger.retryRentals(1);check(ledger.balance(owner)==1,"fraction claims not persisted or duplicated");
        System.out.println("CardConditionTest passed: 486 tier/rate/condition/fuel combinations, exact fractions, caps, full storage, restart and replay.");
    }
}
