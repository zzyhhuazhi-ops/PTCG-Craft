import dev.hua.ptcglive.packs.*;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.engine.ServerRandom;
import java.time.Clock;
import java.util.*;

public final class PackDestinationTest {
    static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    static void reject(Runnable r){try{r.run();throw new AssertionError("Expected rejection");}catch(IllegalArgumentException|IllegalStateException expected){}}
    public static void main(String[] args) {
        var pack=new SetPackDefinition("test","测试","1",Map.of("p",Map.of("a",3,"b",1)),List.of(new PackSlot("p",5,"五张")));
        UUID owner=new UUID(0,1),other=new UUID(0,2);
        for(int count:List.of(1,4,5,8,10,16,32,64))for(int seed:List.of(7,19,43)) {
            var ledger=new CardBoxService(Clock.systemUTC());var service=new PackOpeningService(ledger);
            var draws=new ArrayList<PackOpeningService.Result>();var random=new ServerRandom(seed);
            for(int i=0;i<count;i++)draws.add(service.drawPhysical(owner,pack,random));
            var transcript=random.transcript();var before=ledger.snapshot();
            var audit=new PackOpeningAuditService(Map.of(pack.setId(),pack));
            ledger.onChanged(()->{throw new IllegalStateException("ledger failure");});
            reject(()->service.storeInCollection(owner,pack,draws,()->audit.record(owner,draws,1)));
            check(before.equals(ledger.snapshot())&&audit.recordsForAdministration().isEmpty(),"ledger failure leaked cards/discoveries/audit");
            ledger.onChanged(()->{});audit.onChanged(()->{throw new IllegalStateException("audit failure");});
            reject(()->service.storeInCollection(owner,pack,draws,()->audit.record(owner,draws,1)));
            check(before.equals(ledger.snapshot())&&audit.recordsForAdministration().isEmpty(),"audit failure leaked cards/discoveries");
            audit.onChanged(()->{});
            reject(()->service.storeInCollection(other,pack,draws,()->{}));
            service.storeInCollection(owner,pack,draws,()->audit.record(owner,draws,2));
            var expected=draws.stream().flatMap(d->d.cards().stream()).map(CardInstance::definitionId).sorted().toList();
            check(ledger.unboxedForOwner(owner).stream().map(CardInstance::definitionId).sorted().toList().equals(expected),"collection changed draw results");
            check(ledger.unboxedForOwner(other).isEmpty()&&audit.recordsForAdministration().size()==count,"owner isolation or audit count");
            check(transcript.equals(random.transcript()),"deposit consumed random numbers");
            var restored=CardBoxService.restore(Clock.systemUTC(),ledger.snapshot());
            check(restored.snapshot().equals(ledger.snapshot()),"save/reload lost collection");
            for(var destination:PackDestination.values()) {
                check(PackBatchPolicy.countForAction(destination.action(count))==count,"intent count");
                check(PackDestination.forAction(destination.action(count))==destination,"intent destination");
            }
        }
        for(String invalid:List.of("ALBUM_0","ALBUM_-1","ALBUM_100","ALBUM_01","ALBUM_1x","CONFIRM_COLLECTION","INVENTORY_1"))
            reject(()->PackDestination.forAction(invalid));
        var real=PackCatalog.loadBundled().values().iterator().next();
        var catalog=dev.hua.ptcglive.CardCatalog.load();var db=BundledRules.loadCardDefinitions();
        var preview=PackUiSnapshot.preview(UUID.randomUUID(),real,catalog,db);
        var collection=preview.withDestination(PackDestination.COLLECTION).withResults(Collections.nCopies(real.cardsPerPack(),preview.cards().getFirst().definitionId()),catalog,db);
        check(collection.destination()==PackDestination.COLLECTION&&collection.cards().isEmpty() && collection.results().size()==real.cardsPerPack(),"result destination or compact payload changed");
        check(preview.destination()==PackDestination.INVENTORY,"default destination");
        System.out.println("PackDestinationTest passed: 1/4/5/8/10/16/32/64 x 3 seeds, unchanged draws/RNG, owner isolation, ledger/audit rollback, save/reload, action whitelist and server result destination");
    }
}
