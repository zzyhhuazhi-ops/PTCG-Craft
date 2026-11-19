import dev.hua.ptcglive.economy.EconomyConfig;
import dev.hua.ptcglive.economy.PokeCoinLedger;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

public final class EconomyCoreTest {
    public static void main(String[] args) {
        EconomyConfig config = EconomyConfig.loadBundled();
        Map<String, Long> expected = Map.of(
            "minecraft:coal",1L,"minecraft:iron_ingot",5L,"minecraft:copper_ingot",5L,"minecraft:amethyst_shard",10L,
            "minecraft:gold_ingot",20L,"minecraft:emerald",30L,"minecraft:diamond",100L,"minecraft:netherite_ingot",500L);
        if (!config.mineralValues().equals(expected)) throw new AssertionError("八种矿物兑换比例不一致");
        if (config.packPrice() != 20) throw new AssertionError("卡包售价不是 20 币");
        if (config.packPrice(EconomyConfig.SUPPORTER_HIGH_RARITY_PACK) != 2000
                || config.packPrice("all_energy_pack") != 20 || config.packPrice(EconomyConfig.SC_EXCLUSIVE_PACK) != 2000)
            throw new AssertionError("支援者与简中独占收藏包应为 2000，其他卡包应保持 20");
        if (config.cartPrice(Map.of(EconomyConfig.SUPPORTER_HIGH_RARITY_PACK, 3, "all_energy_pack", 5)) != 6100)
            throw new AssertionError("混合购物车没有按各卡包单价结算");
        if (config.cartPrice(Map.of(EconomyConfig.SC_EXCLUSIVE_PACK, 2, EconomyConfig.SUPPORTER_HIGH_RARITY_PACK, 1, "all_energy_pack", 1)) != 6020)
            throw new AssertionError("简中独占收藏包在混购中没有采用 2000 币价格");
        try { config.cartPrice(Map.of(EconomyConfig.SUPPORTER_HIGH_RARITY_PACK, -1)); throw new AssertionError("接受负数数量"); }
        catch (IllegalArgumentException expectedFailure) { }
        var ledger = new PokeCoinLedger(Clock.systemUTC()); UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        ledger.credit(UUID.randomUUID(), a, 100, PokeCoinLedger.Kind.MINERAL_EXCHANGE, "test", 1);
        if (ledger.balance(a) != 100 || ledger.balance(b) != 0) throw new AssertionError("多人账户不独立");
        try { ledger.debit(UUID.randomUUID(), b, 20, PokeCoinLedger.Kind.PACK_PURCHASE, "pack", 2); throw new AssertionError("余额不足仍购买"); }
        catch (IllegalStateException expectedFailure) { }
        UUID request = UUID.randomUUID(); ledger.debit(request, a, 20, PokeCoinLedger.Kind.PACK_PURCHASE, "pack", 3);
        try { ledger.debit(request, a, 20, PokeCoinLedger.Kind.PACK_PURCHASE, "replay", 4); throw new AssertionError("重复请求被执行"); }
        catch (IllegalStateException expectedFailure) { }
        long beforeInterrupted = ledger.balance(a); UUID interrupted = UUID.randomUUID();
        try {
            ledger.transaction(() -> {
                ledger.credit(interrupted, a, 7, PokeCoinLedger.Kind.ADMIN_MIGRATION, "interrupted", 5);
                throw new IllegalStateException("模拟交易中断");
            });
            throw new AssertionError("中断交易没有失败");
        } catch (IllegalStateException expectedFailure) { }
        if (ledger.balance(a) != beforeInterrupted || ledger.snapshot().processedRequests().contains(interrupted))
            throw new AssertionError("中断交易没有完整回滚余额和防重放状态");
        System.out.println("EconomyCoreTest passed: 固定价格、独立账户、余额校验、流水及请求防重放");
    }
}
