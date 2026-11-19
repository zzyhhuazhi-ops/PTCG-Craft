import dev.hua.ptcglive.economy.PokeCoinLedger;
import dev.hua.ptcglive.rules.CardBoxService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

/** Core snapshots preserve ownership and UUID wallets while deliberately clearing transient match locks. */
public final class PersistenceSnapshotTest {
    public static void main(String[] args) {
        Clock clock = Clock.fixed(Instant.parse("2026-09-29T00:00:00Z"), ZoneOffset.UTC);
        UUID owner = UUID.randomUUID();
        var original = new CardBoxService(clock);
        var first = original.mint(owner, "card-a"); var second = original.mint(owner, "card-b");
        var box = original.create(owner, "存档测试卡盒", "standard-international-2026"); original.put(owner, box.id(), first.instanceId());
        original.snapshotForMatch(owner, box.id(), UUID.randomUUID());
        var restored = CardBoxService.restore(clock, original.snapshot());
        if (restored.contentsForValidation(owner, box.id()).size() != 1 || restored.unboxedForOwner(owner).size() != 1)
            throw new AssertionError("卡牌所有权或卡盒内容没有被快照恢复");
        restored.put(owner, box.id(), second.instanceId()); // A server restart safely cancels an unfinished match lock.

        var wallet = new PokeCoinLedger(clock); UUID request = UUID.randomUUID();
        wallet.credit(request, owner, 500, PokeCoinLedger.Kind.MINERAL_EXCHANGE, "diamond x5", 100);
        var restoredWallet = PokeCoinLedger.restore(clock, wallet.snapshot());
        if (restoredWallet.balance(owner) != 500 || restoredWallet.history(owner).size() != 1)
            throw new AssertionError("宝可币余额或交易流水没有被快照恢复");
        try { restoredWallet.credit(request, owner, 1, PokeCoinLedger.Kind.MINERAL_EXCHANGE, "replay", 101); throw new AssertionError("重启后接受了重复交易"); }
        catch (IllegalStateException expected) { }
        System.out.println("PersistenceSnapshotTest passed: 卡牌所有权、卡盒、宝可币和防重放记录可恢复；重启安全解除对局锁定");
    }
}
