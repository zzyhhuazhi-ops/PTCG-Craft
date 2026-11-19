import dev.hua.ptcglive.rental.RentalProduction;
import dev.hua.ptcglive.economy.PokeCoinLedger;
import java.time.Clock;
import java.util.UUID;

public final class RentalTest {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception {
        int[] rates={1,3,6,8,11,13,15,18,20};
        int previewCases=0;
        for(int rate:rates)for(int percent:new int[]{50,75,100,125,150,200})for(int multiplier:new int[]{2,3,4})for(int capacity:new int[]{1,3,9}){
            int expected=rate*percent*multiplier/25;
            check(RentalProduction.incomeEighthsPerMinute(rate,percent,multiplier)==expected,"income preview");
            var minute=RentalProduction.settleConditioned(0,0,0,60_000,rate,percent,0,0,multiplier,multiplier,capacity);
            check(minute.total()*8+minute.eighth()==expected,"preview differs from actual production");
            previewCases++;
        }
        System.out.println("Rental income previews match actual minute settlement: "+previewCases+" rarity/condition/fuel/tier combinations.");
        for(int i=0;i<rates.length;i++){
            int r=RentalProduction.rate(i,9);check(r==rates[i],"rank "+i);
            var before=RentalProduction.settle(0,0,100,60099,r);check(before.total()==0&&before.since()==100,"early payout");
            var one=RentalProduction.settle(0,0,100,60100,r);check(one.total()==r&&one.since()==60100,"exact minute");
            var partial=RentalProduction.settle(0,0,100,90100,r);check(partial.total()==r&&partial.since()==60100,"fraction lost");
            var resumed=RentalProduction.settle(partial.total(),0,partial.since(),120100,r);check(resumed.total()==2*r,"fraction resume");
            var full=RentalProduction.settle(0,0,100,86_400_100,r);check(full.total()==60*r&&full.since()==86_400_100,"cap");
            var claimed=RentalProduction.settle(full.total(),full.total(),full.since(),86_400_101,r);check(claimed.total()==full.total(),"overflow time banked");
            var next=RentalProduction.settle(claimed.total(),full.total(),claimed.since(),86_460_100,r);check(next.total()==61*r,"restart after full");
            var stale=RentalProduction.settle(0,60*r,500,500,r);check(stale.total()==60*r,"stale chunk can repay");
            var oldClock=RentalProduction.settle(r,60*r,60000,86_400_000,r);
            check(oldClock.total()==60*r&&oldClock.since()==86_400_000,"stale clock replays already paid intervals");
        }
        var owner=UUID.randomUUID();var machine=UUID.randomUUID();var wallet=new PokeCoinLedger(Clock.systemUTC());
        check(wallet.claimRental(machine,owner,1200,0)==1200,"first claim");
        check(wallet.claimRental(machine,owner,1200,1)==0&&wallet.balance(owner)==1200,"replay");
        var threads=new java.util.ArrayList<Thread>();
        for(int i=0;i<16;i++){var t=new Thread(()->wallet.claimRental(machine,owner,2400,2));threads.add(t);t.start();}
        for(var t:threads)t.join();check(wallet.balance(owner)==2400,"concurrent duplicate");
        var restored=PokeCoinLedger.restore(Clock.systemUTC(),wallet.snapshot());
        check(restored.claimRental(machine,owner,2400,3)==0,"restart duplicate");
        try{restored.claimRental(machine,UUID.randomUUID(),2500,3);throw new AssertionError("owner");}catch(IllegalArgumentException expected){}
        var pendingMachine=UUID.randomUUID();var fullOwner=UUID.randomUUID();
        restored.credit(UUID.randomUUID(),fullOwner,Long.MAX_VALUE,PokeCoinLedger.Kind.ADMIN_MIGRATION,"test",0);
        restored.queueRental(pendingMachine,fullOwner,1200);restored.retryRentals(1);
        check(restored.rentalPaid(pendingMachine)==0,"overflow claimed");
        restored=PokeCoinLedger.restore(Clock.systemUTC(),restored.snapshot());
        restored.debit(UUID.randomUUID(),fullOwner,1200,PokeCoinLedger.Kind.ADMIN_MIGRATION,"room",2);restored.retryRentals(3);
        check(restored.balance(fullOwner)==Long.MAX_VALUE&&restored.rentalPaid(pendingMachine)==1200,"pending lost");
        restored.retryRentals(4);check(restored.balance(fullOwner)==Long.MAX_VALUE,"pending duplicate");
        var failing=new PokeCoinLedger(Clock.systemUTC());failing.onChanged(()->{throw new IllegalStateException("save failure");});
        try{failing.claimRental(UUID.randomUUID(),owner,20,0);throw new AssertionError("rollback expected");}catch(IllegalStateException expected){}
        check(failing.balance(owner)==0&&failing.snapshot().rentals().isEmpty()&&failing.history(owner).isEmpty(),"non-atomic receipt");
        check(wallet.history(owner).stream().allMatch(t->t.kind()==PokeCoinLedger.Kind.CARD_RENTAL),"history kind");
        for(int rate:rates){
            for(int mult:new int[]{3,4}){
                var boosted=RentalProduction.settleBoosted(0,0,0,60_000,rate,0,63,mult,mult);
                check(boosted.total()==rate*mult/2&&boosted.half()==rate*mult%2,"multiplier truncated");
                check(boosted.stock()==62&&boosted.cycleMultiplier()==mult,"reservation count");
                var second=RentalProduction.settleBoosted(boosted.total(),0,boosted.since(),120_000,rate,boosted.half(),boosted.stock(),mult,boosted.cycleMultiplier());
                check(second.total()==rate*mult&&second.half()==0,"half coin lost");
                var cap=RentalProduction.settleBoosted(0,0,0,86_400_000,rate,0,63,mult,mult);
                check(cap.total()==rate*60&&cap.stock()==64-(mult==3?40:30)&&cap.cycleMultiplier()==2,"full burns extra fuel");
                var full=RentalProduction.settleBoosted(cap.total(),0,cap.since(),172_800_000,rate,cap.half(),cap.stock(),mult,2);
                check(full.stock()==cap.stock()&&full.total()==cap.total(),"idle full machine burns fuel");
            }
        }
        var fallback=RentalProduction.settleBoosted(0,0,0,180_000,1,0,0,2,4);
        check(fallback.total()==4&&fallback.cycleMultiplier()==2,"no fuel must fall back to base");
        var fractional=new PokeCoinLedger(Clock.systemUTC());var firstHalf=UUID.randomUUID();var secondHalf=UUID.randomUUID();
        fractional.queueFinalRental(firstHalf,owner,0,1);fractional.retryRentals(0);check(fractional.balance(owner)==0,"half rounded up");
        fractional=PokeCoinLedger.restore(Clock.systemUTC(),fractional.snapshot());
        fractional.queueFinalRental(secondHalf,owner,0,1);fractional.retryRentals(1);check(fractional.balance(owner)==1,"half claim lost");
        fractional.queueFinalRental(firstHalf,owner,0,1);fractional.queueFinalRental(secondHalf,owner,0,1);fractional.retryRentals(2);
        check(fractional.balance(owner)==1,"destroyed half replayed");
        System.out.println("RentalTest passed: 9 rates, minute boundaries, fractional time, caps, replay, concurrent claims, persistence, owner, overflow/pending, rollback.");
    }
}
