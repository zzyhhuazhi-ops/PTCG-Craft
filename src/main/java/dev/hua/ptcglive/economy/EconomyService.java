package dev.hua.ptcglive.economy;

import dev.hua.ptcglive.PackItems;
import dev.hua.ptcglive.PtcgServices;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

/** Server-thread transaction boundary for exchange, shopping and recycling. */
public final class EconomyService {
    public record Outcome(long balance, String messageZh) { }
    private final EconomyConfig config;
    private final PokeCoinLedger wallet;

    public EconomyService(EconomyConfig config) {
        this(config, new PokeCoinLedger(Clock.systemUTC()));
    }
    public EconomyService(EconomyConfig config, PokeCoinLedger wallet) {
        this.config = config; this.wallet = wallet;
    }
    public EconomyConfig config() { return config; }
    public PokeCoinLedger wallet() { return wallet; }

    /** amount == 0 means all matching items. */
    public synchronized Outcome exchange(PlayerEntity player, UUID requestId, String itemId, int amount) {
        Item item = Registries.ITEM.get(Identifier.of(itemId));
        if (item == net.minecraft.item.Items.AIR || !config.mineralValues().containsKey(itemId))
            throw new IllegalArgumentException("该物品不能兑换宝可币");
        int available = countItem(player, item);
        int chosen = amount == 0 ? available : amount;
        if (chosen < 1 || chosen > available) throw new IllegalArgumentException("矿物数量不足或兑换数量无效");
        long coins = Math.multiplyExact(config.mineralValue(itemId), chosen);
        return inventoryAtomic(player, () -> wallet.transaction(() -> {
            removeItem(player, item, chosen);
            wallet.credit(requestId, player.getUuid(), coins, PokeCoinLedger.Kind.MINERAL_EXCHANGE,
                itemId + " x" + chosen, player.getWorld().getTime());
            return new Outcome(wallet.balance(player.getUuid()), "已兑换 " + coins + " 宝可币");
        }));
    }

    public synchronized Outcome purchasePack(PlayerEntity player, UUID requestId, String setId, int quantity) {
        if (quantity < 1) throw new IllegalArgumentException("购买数量必须为正整数");
        if (!PtcgServices.packs().containsKey(setId)) throw new IllegalArgumentException("卡包不存在或已下架");
        long price = Math.multiplyExact(config.packPrice(setId), quantity);
        return inventoryAtomic(player, () -> wallet.transaction(() -> {
            addItem(player, PackItems.get(setId), quantity);
            wallet.debit(requestId, player.getUuid(), price, PokeCoinLedger.Kind.PACK_PURCHASE,
                setId + " x" + quantity, player.getWorld().getTime());
            return new Outcome(wallet.balance(player.getUuid()), "购买成功：" + quantity + " 包已放入背包");
        }));
    }

    public synchronized Outcome purchaseCart(PlayerEntity player, UUID requestId, Map<String, Integer> cart) {
        if (cart == null || cart.isEmpty()) throw new IllegalArgumentException("购物车为空");
        long packs = 0;
        for (var row : cart.entrySet()) {
            if (!PtcgServices.packs().containsKey(row.getKey()) || row.getValue() == null || row.getValue() < 1)
                throw new IllegalArgumentException("购物车包含无效卡包或数量");
            packs = Math.addExact(packs, row.getValue());
        }
        long price = config.cartPrice(cart);
        long count = packs;
        return inventoryAtomic(player, () -> wallet.transaction(() -> {
            cart.forEach((setId, quantity) -> addItem(player, PackItems.get(setId), quantity));
            wallet.debit(requestId, player.getUuid(), price, PokeCoinLedger.Kind.PACK_PURCHASE,
                "购物车 " + cart, player.getWorld().getTime());
            return new Outcome(wallet.balance(player.getUuid()), "购买成功：" + count + " 包已放入背包");
        }));
    }

    public PokeCoinLedger.Snapshot snapshot() { return wallet.snapshot(); }
    public void onChanged(Runnable listener) { wallet.onChanged(listener); }

    private static int countItem(PlayerEntity player, Item item) {
        int result = 0; for (int slot : inventorySlots()) {
            var stack = player.getInventory().getStack(slot); if (stack.isOf(item)) result = Math.addExact(result, stack.getCount());
        } return result;
    }
    private static void removeItem(PlayerEntity player, Item item, int amount) {
        int remaining = amount; for (int slot : inventorySlots()) {
            var stack = player.getInventory().getStack(slot); if (stack.isOf(item)) {
                int used = Math.min(remaining, stack.getCount()); stack.decrement(used); remaining -= used;
            } if (remaining == 0) return;
        } throw new IllegalStateException("矿物数量已变化，请刷新后重试");
    }
    private static void addItem(PlayerEntity player, Item item, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            ItemStack stack = new ItemStack(item, Math.min(remaining, item.getMaxCount()));
            int offered = stack.getCount();
            if (!player.getInventory().insertStack(stack) || !stack.isEmpty()) throw new IllegalStateException("背包空间不足，未扣除宝可币");
            remaining -= offered;
        }
    }
    private static <T> T inventoryAtomic(PlayerEntity player, java.util.function.Supplier<T> action) {
        var inventory = player.getInventory(); var before = new ArrayList<ItemStack>();
        for (int i = 0; i < inventory.size(); i++) before.add(inventory.getStack(i).copy());
        try { return action.get(); }
        catch (RuntimeException failure) {
            for (int i = 0; i < before.size(); i++) inventory.setStack(i, before.get(i));
            throw failure;
        } finally { inventory.markDirty(); player.currentScreenHandler.sendContentUpdates(); }
    }
    private static List<Integer> inventorySlots() {
        var result=new ArrayList<Integer>();for(int i=0;i<36;i++)result.add(i);result.add(40);return result;
    }
}
