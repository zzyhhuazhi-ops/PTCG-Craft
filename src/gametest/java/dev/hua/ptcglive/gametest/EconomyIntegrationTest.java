package dev.hua.ptcglive.gametest;

import dev.hua.ptcglive.PackItems;
import dev.hua.ptcglive.PtcgServices;
import java.util.LinkedHashMap;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;

public final class EconomyIntegrationTest implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void allMineralRatiosAndAccountsAreServerOwned(TestContext c) {
        var service = PtcgServices.economy(); var a = c.createMockPlayer(GameMode.SURVIVAL); var b = c.createMockPlayer(GameMode.SURVIVAL);
        var values = new LinkedHashMap<net.minecraft.item.Item, Long>();
        values.put(Items.COAL,1L); values.put(Items.IRON_INGOT,5L); values.put(Items.COPPER_INGOT,5L); values.put(Items.AMETHYST_SHARD,10L);
        values.put(Items.GOLD_INGOT,20L); values.put(Items.EMERALD,30L); values.put(Items.DIAMOND,100L); values.put(Items.NETHERITE_INGOT,500L);
        long expected = 0; int slot = 0;
        for (var row : values.entrySet()) {
            a.getInventory().setStack(slot++, new ItemStack(row.getKey())); expected += row.getValue();
            service.exchange(a, UUID.randomUUID(), Registries.ITEM.getId(row.getKey()).toString(), 1);
        }
        c.assertTrue(service.wallet().balance(a.getUuid()) == expected, "八种矿物兑换比例或扣物不正确");
        c.assertTrue(service.wallet().balance(b.getUuid()) == 0, "多人宝可币账户互相污染");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void packPurchaseAndFullInventoryRollback(TestContext c) {
        var service = PtcgServices.economy(); var buyer = c.createMockPlayer(GameMode.SURVIVAL);
        buyer.getInventory().setStack(0, new ItemStack(Items.EMERALD));
        service.exchange(buyer, UUID.randomUUID(), "minecraft:emerald", 1);
        String set = PtcgServices.packs().keySet().stream().filter(id -> service.config().packPrice(id) == 20).findFirst().orElseThrow();
        service.purchasePack(buyer, UUID.randomUUID(), set, 1);
        c.assertTrue(service.wallet().balance(buyer.getUuid()) == 10 && buyer.getInventory().count(PackItems.get(set)) == 1,
            "卡包没有按统一 20 币价格发放");
        try { service.purchasePack(buyer, UUID.randomUUID(), set, 1); throw new AssertionError("余额不足仍购买成功"); }
        catch (IllegalStateException expected) { }

        var full = c.createMockPlayer(GameMode.SURVIVAL); full.getInventory().setStack(0, new ItemStack(Items.DIAMOND));
        service.exchange(full, UUID.randomUUID(), "minecraft:diamond", 1);
        for (int i = 0; i < 36; i++) full.getInventory().setStack(i, new ItemStack(Items.DIRT, 64));
        long before = service.wallet().balance(full.getUuid());
        try { service.purchasePack(full, UUID.randomUUID(), set, 1); throw new AssertionError("背包满仍购买成功"); }
        catch (IllegalStateException expected) { }
        c.assertTrue(service.wallet().balance(full.getUuid()) == before && full.getInventory().count(PackItems.get(set)) == 0,
            "背包满时扣币或复制了卡包"); c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void supporterPackUsesOwnPriceInSingleAndMixedPurchases(TestContext c) {
        var service=PtcgServices.economy(); var buyer=c.createMockPlayer(GameMode.SURVIVAL);
        String special=dev.hua.ptcglive.economy.EconomyConfig.SUPPORTER_HIGH_RARITY_PACK;
        String normal="all_energy_pack";
        service.wallet().credit(UUID.randomUUID(),buyer.getUuid(),6020,dev.hua.ptcglive.economy.PokeCoinLedger.Kind.ADMIN_MIGRATION,"test",0);
        UUID purchase=UUID.randomUUID(); service.purchasePack(buyer,purchase,special,1);
        c.assertTrue(service.wallet().balance(buyer.getUuid())==4020,"单买未扣2000币");
        try {service.purchasePack(buyer,purchase,special,1);throw new AssertionError("重复购买被执行");}
        catch(IllegalStateException expected) { }
        c.assertTrue(buyer.getInventory().count(PackItems.get(special))==1 && service.wallet().balance(buyer.getUuid())==4020,"重放造成重复发卡或扣币");
        String exclusive=dev.hua.ptcglive.economy.EconomyConfig.SC_EXCLUSIVE_PACK;
        service.purchasePack(buyer,UUID.randomUUID(),exclusive,1);
        c.assertTrue(service.wallet().balance(buyer.getUuid())==2020 && buyer.getInventory().count(PackItems.get(exclusive))==1,"简中独占包单买未扣2000币");
        service.purchaseCart(buyer,UUID.randomUUID(),java.util.Map.of(exclusive,1,normal,1));
        c.assertTrue(service.wallet().balance(buyer.getUuid())==0 && buyer.getInventory().count(PackItems.get(exclusive))==2
            && buyer.getInventory().count(PackItems.get(normal))==1,"混合购物车没有按2000/20逐项结算");
        try {service.purchasePack(buyer,UUID.randomUUID(),special,1);throw new AssertionError("余额不足仍购买");}
        catch(IllegalStateException expected) { }
        c.assertTrue(buyer.getInventory().count(PackItems.get(special))==1,"余额不足仍发卡");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void replayAndRetiredTradingIdsAreSafe(TestContext c) {
        var player = c.createMockPlayer(GameMode.SURVIVAL); var service = PtcgServices.economy(); UUID request = UUID.randomUUID();
        player.getInventory().setStack(0, new ItemStack(Items.COAL, 2)); service.exchange(player, request, "minecraft:coal", 1);
        try { service.exchange(player, request, "minecraft:coal", 1); throw new AssertionError("重复请求被再次执行"); }
        catch (IllegalStateException expected) { }
        c.assertTrue(service.wallet().balance(player.getUuid()) == 1 && player.getInventory().count(Items.COAL) == 1, "重复提交造成复制或重复扣物");
        c.assertTrue(!Registries.ITEM.containsId(Identifier.of("ptcglive", "merchant_token")), "商人之证仍在注册表");
        var retired = dev.hua.ptcglive.shop.CardMerchants.TYPE.create(c.getWorld()); c.getWorld().spawnEntity(retired); retired.tick();
        c.assertTrue(retired.isRemoved(), "旧卡包商人实体加载后没有安全移除"); c.complete();
    }
}
