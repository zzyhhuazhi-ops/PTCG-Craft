package dev.hua.ptcglive.gametest;

import dev.hua.ptcglive.*;
import dev.hua.ptcglive.collection.InventoryCardTransfers;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.engine.ServerRandom;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.*;
import net.minecraft.item.*;
import net.minecraft.world.GameMode;
import java.time.Clock;
import java.util.*;

/** Actual Minecraft inventory + world-ledger transfer regression tests. */
public final class CollectionTransfersIntegrationTest implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void liveImportMovesInventoryAndPersistsMissing(TestContext c) throws Exception {
        var player = c.createMockPlayer(GameMode.SURVIVAL);
        var owner = player.getUuid(); var ledger = new CardBoxService(Clock.systemUTC());
        var box = ledger.create(owner, "Live", "standard-international-2026");
        String id = "sve_en_005";
        player.getInventory().setStack(0, new ItemStack(CardItems.byDefinitionId(id), 2));
        player.getInventory().setStack(10, CardBoxItem.referenceTo(box));
        ledger.mint(owner, id);
        var editor = new dev.hua.ptcglive.collection.BoxEditorSession(ledger, new CardDatabase(List.of()),
            new CombatCatalog(Map.of(), Map.of()), Map.of(), Map.of(id, "基本超能量"), owner, box.id());
        editor.connectInventory(() -> InventoryCardTransfers.counts(player), card -> InventoryCardTransfers.deposit(player, ledger, card, 1, box.id()));
        editor.connectTransaction(move -> InventoryCardTransfers.atomic(player, ledger, move));
        var view = editor.view(owner);
        String text = "1 Psychic Energy SVE 5\n".repeat(60);
        var request = new dev.hua.ptcglive.network.BoxIntentPayload(editor.sessionId().toString(), view.revision(), "IMPORT_LIVE", text, 0, "");
        var buffer = new net.minecraft.network.RegistryByteBuf(io.netty.buffer.Unpooled.buffer(), c.getWorld().getRegistryManager());
        try {
            dev.hua.ptcglive.network.BoxIntentPayload.CODEC.encode(buffer, request);
            var decoded = dev.hua.ptcglive.network.BoxIntentPayload.CODEC.decode(buffer);
            c.assertTrue(decoded.equals(request), "完整多行卡表网络传输被截断");
            view = editor.submit(owner, decoded.revision(), decoded.action(), decoded.argument(), 0, "");
        } finally { buffer.release(); }
        c.assertTrue(view.totalCards() == 3 && InventoryCardTransfers.counts(player).isEmpty(), "Live导入未移动实际背包与图鉴卡牌");
        c.assertTrue(ledger.missingCards(owner, box.id()).get(id) == 57, "缺卡占位数量错误");
        // Inject a failure after a real physical transfer: both inventories and plan must roll back.
        player.getInventory().setStack(0, new ItemStack(CardItems.byDefinitionId(id), 1));
        var revision = view.revision();
        editor.connectInventory(() -> InventoryCardTransfers.counts(player), card -> {
            InventoryCardTransfers.deposit(player, ledger, card, 1, box.id()); throw new IllegalStateException("test rollback");
        });
        expectRejected(() -> editor.submit(owner, revision, "IMPORT_LIVE", "60 Psychic Energy SVE 5", 0, ""));
        c.assertTrue(InventoryCardTransfers.counts(player).get(id) == 1 && ledger.contentsForValidation(owner, box.id()).size() == 3,
            "失败导入丢失或复制了实体卡牌");
        c.assertTrue(ledger.missingCards(owner, box.id()).get(id) == 57, "失败导入未回滚占位");
        editor.connectInventory(() -> InventoryCardTransfers.counts(player), card -> InventoryCardTransfers.deposit(player, ledger, card, 1, box.id()));
        editor.submit(owner, revision, "FILL_MISSING", id, 0, "");
        c.assertTrue(ledger.missingCards(owner, box.id()).get(id) == 56, "手动补入未减少占位");
        var state = new dev.hua.ptcglive.persistence.PtcgPersistentState();
        state.bind(ledger, PtcgServices.economy(), PtcgServices.packOpeningAudit(), PtcgServices.starterDeck100(), PtcgServices.tables());
        var nbt = state.writeNbt(new net.minecraft.nbt.NbtCompound(), c.getWorld().getRegistryManager());
        var read = state.getClass().getDeclaredMethod("fromNbt", net.minecraft.nbt.NbtCompound.class, net.minecraft.registry.RegistryWrapper.WrapperLookup.class);
        read.setAccessible(true);
        var restored = (dev.hua.ptcglive.persistence.PtcgPersistentState) read.invoke(null, nbt, c.getWorld().getRegistryManager());
        var loaded = CardBoxService.restore(Clock.systemUTC(), restored.cardSnapshot());
        c.assertTrue(loaded.missingCards(owner, box.id()).get(id) == 56, "世界NBT往返丢失缺卡计划");
        expectRejected(() -> loaded.snapshotForMatch(owner, box.id(), UUID.randomUUID()));
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void staleWindowsAndReplayCannotDuplicate(TestContext c) {
        var p=c.createMockPlayer(GameMode.SURVIVAL);var ledger=new CardBoxService(Clock.systemUTC());
        String id=CardItems.cards().getFirst().card().id();
        p.getInventory().setStack(0,new ItemStack(CardItems.byDefinitionId(id),2));
        var gate=new dev.hua.ptcglive.collection.CollectionRevisionGate();
        if(gate.claim(0))InventoryCardTransfers.deposit(p,ledger,id,1,null);
        c.assertTrue(!gate.claim(0)&&gate.revision()==1,"同一请求重复执行");
        c.assertTrue(InventoryCardTransfers.counts(p).get(id)==1&&ledger.unboxedForOwner(p.getUuid()).size()==1,"连点产生副本");
        var a=ledger.create(p.getUuid(),"甲","standard-international-2026");
        var b=ledger.create(p.getUuid(),"乙","standard-international-2026");
        p.getInventory().setStack(10,CardBoxItem.referenceTo(a));
        p.getInventory().setStack(11,CardBoxItem.referenceTo(b));
        InventoryCardTransfers.addToBox(p,ledger,a.id(),id);
        InventoryCardTransfers.addToBox(p,ledger,a.id(),id);
        expectRejected(()->InventoryCardTransfers.addToBox(p,ledger,b.id(),id));
        c.assertTrue(ledger.contentsForValidation(p.getUuid(),a.id()).size()==2&&ledger.contentsForValidation(p.getUuid(),b.id()).isEmpty(),"另一个旧窗口绕过实时数量检查");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void inventoryCollectionBoxConservesCopies(TestContext c) {
        var p=c.createMockPlayer(GameMode.SURVIVAL);var ledger=new CardBoxService(Clock.systemUTC());
        String id=CardItems.cards().getFirst().card().id();var item=CardItems.byDefinitionId(id);
        p.getInventory().setStack(0,new ItemStack(item,2));
        var a=ledger.create(p.getUuid(),"甲","standard-international-2026");
        var b=ledger.create(p.getUuid(),"乙","standard-international-2026");
        p.getInventory().setStack(10,CardBoxItem.referenceTo(a));
        p.getInventory().setStack(11,CardBoxItem.referenceTo(b));
        InventoryCardTransfers.deposit(p,ledger,id,1,null);
        c.assertTrue(InventoryCardTransfers.counts(p).get(id)==1&&ledger.unboxedForOwner(p.getUuid()).size()==1,"非抽卡来源不能存入或转移后数量错误");
        InventoryCardTransfers.addToBox(p,ledger,a.id(),id);
        c.assertTrue(ledger.unboxedForOwner(p.getUuid()).isEmpty()&&a.contents().size()==1,"图鉴不能直接入盒");
        InventoryCardTransfers.addToBox(p,ledger,b.id(),id);
        c.assertTrue(InventoryCardTransfers.counts(p).isEmpty()&&b.contents().size()==1,"背包不能直接入盒");
        expectRejected(()->InventoryCardTransfers.addToBox(p,ledger,a.id(),id));
        c.assertTrue(ledger.contentsForValidation(p.getUuid(),a.id()).size()==1&&ledger.contentsForValidation(p.getUuid(),b.id()).size()==1,"一张实体卡被多个卡盒重复使用");
        var card=ledger.contentsForEditing(p.getUuid(),a.id()).getFirst();ledger.take(p.getUuid(),a.id(),card.instanceId());
        InventoryCardTransfers.withdraw(p,ledger,id,1,null);
        c.assertTrue(InventoryCardTransfers.counts(p).get(id)==1&&ledger.unboxedForOwner(p.getUuid()).isEmpty(),"图鉴取出未扣除收藏");
        InventoryCardTransfers.withdraw(p,ledger,id,1,b.id());
        c.assertTrue(InventoryCardTransfers.counts(p).get(id)==2&&ledger.contentsForValidation(p.getUuid(),b.id()).isEmpty(),"卡盒直接取回背包数量错误");
        c.assertTrue(ledger.discovered(p.getUuid(),id),"取出后发现记录消失");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void packAndTradeDoNotLeaveGhostOwnership(TestContext c) {
        var a=c.createMockPlayer(GameMode.SURVIVAL);var b=c.createMockPlayer(GameMode.SURVIVAL);
        var ledger=new CardBoxService(Clock.systemUTC());var opening=new dev.hua.ptcglive.packs.PackOpeningService(ledger);
        var pack=PtcgServices.packs().values().iterator().next();
        var drawn=opening.drawPhysical(a.getUuid(),pack,new ServerRandom(76));
        c.assertTrue(drawn.cards().size()==pack.cardsPerPack()&&ledger.unboxedForOwner(a.getUuid()).isEmpty(),"实体抽卡仍重复写入收藏");
        String id=drawn.cards().getFirst().definitionId();
        a.getInventory().setStack(0,new ItemStack(CardItems.byDefinitionId(id)));
        a.getInventory().setStack(0,ItemStack.EMPTY); // The old owner's physical copy left their inventory.
        expectRejected(()->InventoryCardTransfers.deposit(a,ledger,id,1,null));
        b.getInventory().setStack(0,new ItemStack(CardItems.byDefinitionId(id)));
        InventoryCardTransfers.deposit(b,ledger,id,1,null);
        c.assertTrue(ledger.unboxedForOwner(b.getUuid()).size()==1&&ledger.unboxedForOwner(a.getUuid()).isEmpty(),"新持有者错误依赖原抽卡玩家历史");
        a.getInventory().setStack(0,new ItemStack(CardItems.byDefinitionId(id)));
        InventoryCardTransfers.deposit(a,ledger,id,1,null);
        c.assertTrue(ledger.unboxedForOwner(a.getUuid()).size()==1,"重新获得后没有按当前数量入库");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void fullInventoryInvalidAmountAndLocksRollback(TestContext c) {
        var p=c.createMockPlayer(GameMode.SURVIVAL);var ledger=new CardBoxService(Clock.systemUTC());
        String id=CardItems.cards().getFirst().card().id();
        p.getInventory().setStack(0,new ItemStack(CardItems.byDefinitionId(id),2));
        for(int amount:new int[]{0,-1,65,Integer.MAX_VALUE,4})expectRejected(()->InventoryCardTransfers.deposit(p,ledger,id,amount,null));
        expectRejected(()->InventoryCardTransfers.deposit(p,ledger,"not_a_real_card",1,null));
        c.assertTrue(InventoryCardTransfers.counts(p).get(id)==2&&ledger.unboxedForOwner(p.getUuid()).isEmpty(),"非法请求改变数量");
        InventoryCardTransfers.deposit(p,ledger,id,2,null);
        for(int slot=0;slot<36;slot++)p.getInventory().setStack(slot,new ItemStack(Items.STONE,64));
        expectRejected(()->InventoryCardTransfers.withdraw(p,ledger,id,1,null));
        c.assertTrue(ledger.unboxedForOwner(p.getUuid()).size()==2,"背包满时吞卡");
        p.getInventory().setStack(0,ItemStack.EMPTY);
        var box=ledger.create(p.getUuid(),"锁定卡盒","standard-international-2026");
        p.getInventory().setStack(40,CardBoxItem.referenceTo(box));
        var lock=UUID.randomUUID();ledger.snapshotForMatch(p.getUuid(),box.id(),lock);
        expectRejected(()->InventoryCardTransfers.addToBox(p,ledger,box.id(),id));
        c.assertTrue(ledger.unboxedForOwner(p.getUuid()).size()==2&&ledger.isLocked(p.getUuid(),box.id()),"失败回滚丢失锁或卡牌");
        ledger.unlock(lock);
        ledger.onChanged(()->{throw new IllegalStateException("模拟存档标脏失败");});
        expectRejected(()->InventoryCardTransfers.withdraw(p,ledger,id,1,null));
        c.assertTrue(p.getInventory().getStack(0).isEmpty()&&ledger.unboxedForOwner(p.getUuid()).size()==2,"异常时背包与收藏未共同回滚");
        ledger.onChanged(()->{});c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void repeatedMovesAndWorldNbtMigration(TestContext c) throws Exception {
        var p=c.createMockPlayer(GameMode.SURVIVAL);var ledger=new CardBoxService(Clock.systemUTC());
        String id=CardItems.cards().getFirst().card().id();
        p.getInventory().setStack(0,new ItemStack(CardItems.byDefinitionId(id)));
        InventoryCardTransfers.deposit(p,ledger,id,1,null);
        expectRejected(()->InventoryCardTransfers.deposit(p,ledger,id,1,null));
        InventoryCardTransfers.withdraw(p,ledger,id,1,null);
        expectRejected(()->InventoryCardTransfers.withdraw(p,ledger,id,1,null));
        InventoryCardTransfers.deposit(p,ledger,id,1,null);
        var box=ledger.createPopulated(p.getUuid(),"保留卡组","standard-international-2026",List.of(id));
        var state=new dev.hua.ptcglive.persistence.PtcgPersistentState();
        state.bind(ledger,PtcgServices.economy(),PtcgServices.packOpeningAudit(),PtcgServices.starterDeck100(),PtcgServices.tables());
        var nbt=state.writeNbt(new net.minecraft.nbt.NbtCompound(),c.getWorld().getRegistryManager());
        var read=state.getClass().getDeclaredMethod("fromNbt",net.minecraft.nbt.NbtCompound.class,net.minecraft.registry.RegistryWrapper.WrapperLookup.class);
        read.setAccessible(true);
        var restored=(dev.hua.ptcglive.persistence.PtcgPersistentState)read.invoke(null,nbt,c.getWorld().getRegistryManager());
        var current=CardBoxService.restore(Clock.systemUTC(),restored.cardSnapshot());
        c.assertTrue(current.unboxedForOwner(p.getUuid()).size()==1&&current.discovered(p.getUuid(),id),"世界 NBT 往返丢失新收藏或发现记录");
        nbt.putInt("format",3);
        var legacy=(dev.hua.ptcglive.persistence.PtcgPersistentState)read.invoke(null,nbt,c.getWorld().getRegistryManager());
        var migrated=CardBoxService.restore(Clock.systemUTC(),legacy.cardSnapshot());
        c.assertTrue(migrated.unboxedForOwner(p.getUuid()).isEmpty()&&migrated.contentsForValidation(p.getUuid(),box.id()).size()==1,"迁移未按要求废除旧收藏或错误删除卡盒");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void droppedCardsAndBoxesDisappearWithoutLosingContents(TestContext c) {
        var p=c.createMockPlayer(GameMode.SURVIVAL);var ledger=new CardBoxService(Clock.systemUTC());
        String id=CardItems.cards().getFirst().card().id();
        var box=ledger.createPopulated(p.getUuid(),"丢弃测试","standard-international-2026",List.of(id));
        p.getInventory().setStack(0,new ItemStack(CardItems.byDefinitionId(id),2));
        p.getInventory().setStack(10,CardBoxItem.referenceTo(box));
        ledger.observe(p.getUuid(),Set.of(id));
        var before=dev.hua.ptcglive.collection.PlayerCardHoldings.snapshot(p,ledger);
        c.assertTrue(before.inventory().get(id)==2&&before.boxed().get(id)==1&&before.boxes().size()==1,"持有数量不正确");
        var droppedBox=p.getInventory().removeStack(10);
        var droppedCards=p.getInventory().removeStack(0);
        c.getWorld().spawnEntity(new net.minecraft.entity.ItemEntity(c.getWorld(),p.getX(),p.getY(),p.getZ(),droppedBox));
        c.getWorld().spawnEntity(new net.minecraft.entity.ItemEntity(c.getWorld(),p.getX(),p.getY(),p.getZ(),droppedCards));
        var after=dev.hua.ptcglive.collection.PlayerCardHoldings.snapshot(p,ledger);
        c.assertTrue(after.inventory().isEmpty()&&after.boxed().isEmpty()&&after.boxes().isEmpty(),"图鉴仍计入地面物品");
        expectRejected(()->InventoryCardTransfers.deposit(p,ledger,id,1,null));
        expectRejected(()->InventoryCardTransfers.withdraw(p,ledger,id,1,box.id()));
        ledger.mint(p.getUuid(),id);
        expectRejected(()->InventoryCardTransfers.addToBox(p,ledger,box.id(),id));
        c.assertTrue(ledger.unboxedForOwner(p.getUuid()).size()==1&&ledger.contentsForValidation(p.getUuid(),box.id()).size()==1,"旧窗口吞卡或改了地面卡盒");
        c.assertTrue(dev.hua.ptcglive.collection.PlayerCardHoldings.snapshot(p,ledger).collection().get(id)==1,"错误删除真实图鉴存卡");
        p.getInventory().setStack(40,CardBoxItem.referenceTo(box));
        InventoryCardTransfers.withdraw(p,ledger,id,1,box.id());
        c.assertTrue(ledger.contentsForValidation(p.getUuid(),box.id()).isEmpty(),"拾回后不能正常取卡");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void onlyOwnedPhysicalBoxCredentialsAuthorizeTransfers(TestContext c) {
        var p=c.createMockPlayer(GameMode.SURVIVAL);var other=c.createMockPlayer(GameMode.SURVIVAL);
        var ledger=new CardBoxService(Clock.systemUTC());String id=CardItems.cards().getFirst().card().id();
        var box=ledger.create(p.getUuid(),"凭证测试","standard-international-2026");
        var credential=CardBoxItem.referenceTo(box);
        var fake=new ItemStack(Items.PAPER);
        fake.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,credential.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA));
        p.getInventory().setStack(0,fake);
        c.assertTrue(!dev.hua.ptcglive.collection.PlayerCardHoldings.hasBox(p,box.id()),"纸伪造卡盒凭证");
        p.getInventory().setStack(36,credential.copy());
        p.currentScreenHandler.setCursorStack(credential.copy());
        p.getEnderChestInventory().setStack(0,credential.copy());
        c.assertTrue(!dev.hua.ptcglive.collection.PlayerCardHoldings.hasBox(p,box.id()),"装备栏、鼠标或末影箱被当作背包");
        other.getInventory().setStack(0,credential.copy());
        c.assertTrue(dev.hua.ptcglive.collection.PlayerCardHoldings.heldBoxes(other,ledger).isEmpty(),"拾到他人卡盒绕过所有权");
        expectRejected(()->dev.hua.ptcglive.collection.PlayerCardHoldings.requireBox(other,ledger,box.id()));
        p.getInventory().setStack(10,credential.copy());p.getInventory().setStack(11,credential.copy());
        c.assertTrue(dev.hua.ptcglive.collection.PlayerCardHoldings.heldBoxes(p,ledger).size()==1,"重复物品凭证重复统计卡盒");
        p.getInventory().setStack(1,new ItemStack(CardItems.byDefinitionId(id)));
        p.getInventory().removeStack(10);p.getInventory().removeStack(11);
        expectRejected(()->InventoryCardTransfers.deposit(p,ledger,id,1,box.id()));
        c.assertTrue(InventoryCardTransfers.counts(p).get(id)==1&&ledger.unboxedForOwner(p.getUuid()).isEmpty(),"直接存入遗失卡盒吞卡");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void tableStartRechecksRealInventoriesAndStartupAdapter(TestContext c) {
        var a=c.createMockPlayer(GameMode.SURVIVAL);var b=c.createMockPlayer(GameMode.SURVIVAL);
        var ledger=new CardBoxService(Clock.systemUTC());String rule="standard-international-2026";
        var deck=new ArrayList<String>();for(int i=0;i<60;i++)deck.add(i<4?"fixture-basic-h":"fixture-energy-h");
        var first=ledger.createPopulated(a.getUuid(),"甲",rule,deck);
        var second=ledger.createPopulated(b.getUuid(),"乙",rule,deck);
        a.getInventory().setStack(10,CardBoxItem.referenceTo(first));
        b.getInventory().setStack(40,CardBoxItem.referenceTo(second));
        var players=Map.of(a.getUuid(),a,b.getUuid(),b);
        var tables=new dev.hua.ptcglive.table.CardTableManager(PtcgServices.rulesets(),ledger,PtcgServices.cardDatabase(),BundledCombatData.load(),()->new ServerRandom(9));
        tables.requireBoxPossession((owner,box)->dev.hua.ptcglive.collection.PlayerCardHoldings.hasBox(players.get(owner),box));
        var key=new dev.hua.ptcglive.table.CardTableKey("minecraft:overworld",789);
        tables.open(key,a.getUuid(),rule);tables.join(key,b.getUuid());
        tables.selectBox(key,a.getUuid(),first.id());tables.selectBox(key,b.getUuid(),second.id());
        tables.setReady(key,a.getUuid(),true);tables.setReady(key,b.getUuid(),true);
        var dropped=b.getInventory().removeStack(40);
        expectRejected(()->tables.start(key,a.getUuid()));
        c.assertTrue(!tables.publicTableView(key).guestReady()&&!ledger.isLocked(a.getUuid(),first.id()),"丢弃后开局成功或残留锁");
        b.getInventory().setStack(40,dropped);
        expectRejected(()->tables.start(key,a.getUuid()));
        tables.selectBox(key,b.getUuid(),second.id());tables.setReady(key,b.getUuid(),true);
        var match=tables.start(key,a.getUuid());tables.endMatch(match.id());
        // Real server service must fail closed for an offline owner, even with valid ledger decks.
        UUID offline=UUID.randomUUID();
        var storedA=PtcgServices.cardBoxes().createPopulated(offline,"无背包甲",rule,deck);
        var storedB=PtcgServices.cardBoxes().createPopulated(offline,"无背包乙",rule,deck);
        boolean blocked=false;
        try { PtcgServices.tables().startPractice(offline,storedA.id(),storedB.id(),rule); }
        catch(IllegalStateException expected){blocked=expected.getMessage().contains("背包");}
        c.assertTrue(blocked,"服务器启动时未接入实体卡盒校验");
        c.complete();
    }
    private static void expectRejected(Runnable task){
        try{task.run();}catch(IllegalArgumentException|IllegalStateException expected){return;}
        throw new AssertionError("非法操作未被拒绝");
    }
}
