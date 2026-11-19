package dev.hua.ptcglive.gametest;

import dev.hua.ptcglive.*;
import dev.hua.ptcglive.collection.*;
import dev.hua.ptcglive.packs.*;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.shop.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.util.Hand;
import net.minecraft.world.GameMode;
import java.util.*;
import java.time.Clock;

/** Real Minecraft inventories, including stacking, occupied slots and persistence rollback. */
public final class PackBatchIntegrationTest implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void browseDoesNotGrantOpeningAuthority(TestContext c) throws Exception {
        var p=c.createMockCreativeServerPlayerInWorld();
        var handle=dev.hua.ptcglive.network.PackNetworking.class.getDeclaredMethod("handle",net.minecraft.server.network.ServerPlayerEntity.class,dev.hua.ptcglive.network.PackIntentPayload.class);handle.setAccessible(true);
        var field=dev.hua.ptcglive.network.PackNetworking.class.getDeclaredField("open");field.setAccessible(true);
        var before=snapshot(p);var ledger=PtcgServices.cardBoxes().snapshot();var pack=PtcgServices.packs().values().iterator().next();
        try {
            handle.invoke(null,p,new dev.hua.ptcglive.network.PackIntentPayload(pack.setId(),"BROWSE"));
            c.assertTrue(!((Map<?,?>)field.get(null)).containsKey(p.getUuid()),"浏览生成了开包授权");
            handle.invoke(null,p,new dev.hua.ptcglive.network.PackIntentPayload(pack.setId(),"ALBUM_64"));
            handle.invoke(null,p,new dev.hua.ptcglive.network.PackIntentPayload(UUID.randomUUID().toString(),"CONFIRM_64"));
            same(c,p,before);c.assertTrue(ledger.equals(PtcgServices.cardBoxes().snapshot()),"浏览或伪造确认发出了卡牌");
        } finally {p.networkHandler.disconnect(net.minecraft.text.Text.literal("测试完成"));}
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void allBatchNetworkRequestsAreSingleUse(TestContext c) throws Exception {
        // A small real pool permits even eight packs in an empty vanilla inventory.
        var pack=PtcgServices.packs().values().stream()
            .filter(p->p.cardsPerPack()==5 && CollectionProgress.poolIds(p).size()<=30)
            .findFirst().orElseThrow();
        var sessions=dev.hua.ptcglive.network.PackNetworking.class.getDeclaredField("open");sessions.setAccessible(true);
        var handle=dev.hua.ptcglive.network.PackNetworking.class.getDeclaredMethod("handle",
            net.minecraft.server.network.ServerPlayerEntity.class,dev.hua.ptcglive.network.PackIntentPayload.class);
        handle.setAccessible(true);
        for(int count:List.of(4,8,16,32,64))for(var destination:PackDestination.values()) {
            var p=c.createMockCreativeServerPlayerInWorld();
            try {
                p.setStackInHand(Hand.MAIN_HAND,new ItemStack(PackItems.get(pack.setId()),count));
                p.getInventory().setStack(1,new ItemStack(PackItems.get(pack.setId()),count));
                dev.hua.ptcglive.network.PackNetworking.open(p,pack,Hand.MAIN_HAND);
                var session=((Map<?,?>)sessions.get(null)).get(p.getUuid());
                var token=session.getClass().getDeclaredMethod("sessionId");token.setAccessible(true);
                var request=new dev.hua.ptcglive.network.PackIntentPayload(token.invoke(session).toString(),destination.action(count));
                handle.invoke(null,p,request);
                int physical=InventoryCardTransfers.counts(p).values().stream().mapToInt(Integer::intValue).sum();
                int collected=PtcgServices.cardBoxes().unboxedForOwner(p.getUuid()).size();
                c.assertTrue(physical==(destination==PackDestination.INVENTORY?count*5:0),"背包发卡数量错误");
                c.assertTrue(collected==(destination==PackDestination.COLLECTION?count*5:0),"图鉴发卡数量错误");
                c.assertTrue(PhysicalPackTransaction.available(p,pack.setId())==count,"批量扣包数量错误");
                var inventory=snapshot(p);var ledger=PtcgServices.cardBoxes().snapshot();
                handle.invoke(null,p,request);
                same(c,p,inventory);
                c.assertTrue(ledger.equals(PtcgServices.cardBoxes().snapshot()),"重复请求再次发卡");
            } finally {p.networkHandler.disconnect(net.minecraft.text.Text.literal("测试完成"));}
        }
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void collectionDestinationFullBagAndAtomicRollback(TestContext c) {
        var pack=PtcgServices.packs().get("surging_sparks");
        String id=CollectionProgress.poolIds(pack).iterator().next();
        for(int count:List.of(1,4,5,8,10,16,32,64))for(String failure:List.of("NONE","LEDGER","AUDIT")) {
            var p=c.createMockPlayer(GameMode.SURVIVAL);
            for(int i=0;i<36;i++)p.getInventory().setStack(i,new ItemStack(Items.DIRT,64));
            p.setStackInHand(Hand.MAIN_HAND,new ItemStack(PackItems.get(pack.setId()),count));
            var before=snapshot(p);var ledger=new CardBoxService(Clock.systemUTC());var initial=ledger.snapshot();
            var opening=new PackOpeningService(ledger);var audit=new PackOpeningAuditService(Map.of(pack.setId(),pack));
            var results=draws(p,pack,count,id);
            if(failure.equals("LEDGER"))ledger.onChanged(()->{throw new IllegalStateException("ledger failure");});
            if(failure.equals("AUDIT"))audit.onChanged(()->{throw new IllegalStateException("audit failure");});
            Runnable commit=()->PhysicalPackTransaction.commit(p,Hand.MAIN_HAND,pack,count,results,PackDestination.COLLECTION,opening,
                ()->audit.record(p.getUuid(),results,1));
            if(failure.equals("NONE")) {
                commit.run();
                c.assertTrue(PhysicalPackTransaction.available(p,pack.setId())==0,"collection did not consume packs");
                c.assertTrue(InventoryCardTransfers.counts(p).isEmpty(),"collection duplicated physical cards");
                c.assertTrue(ledger.unboxedForOwner(p.getUuid()).size()==count*pack.cardsPerPack(),"collection count incorrect");
                c.assertTrue(audit.recordsForAdministration().size()==count,"collection audit incomplete");
                var after=ledger.snapshot();reject(commit);c.assertTrue(after.equals(ledger.snapshot()),"replay duplicated cards");
                for(int i=1;i<36;i++)c.assertTrue(ItemStack.areEqual(before.get(i),p.getInventory().getStack(i)),"unrelated inventory changed");
            } else {
                reject(commit);same(c,p,before);
                c.assertTrue(initial.equals(ledger.snapshot())&&audit.recordsForAdministration().isEmpty(),"failed commit leaked collection or audit");
            }
        }
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void capacityAdmissionCannotFilterHiddenDraws(TestContext c) {
        var pack=PtcgServices.packs().get("surging_sparks");var p=c.createMockPlayer(GameMode.SURVIVAL);
        String id=CollectionProgress.poolIds(pack).iterator().next();
        for(int i=0;i<36;i++)p.getInventory().setStack(i,new ItemStack(Items.DIRT,64));
        p.setStackInHand(Hand.MAIN_HAND,new ItemStack(PackItems.get(pack.setId()),11));
        p.getInventory().setStack(1,new ItemStack(CardItems.byDefinitionId(id),1));
        var before=snapshot(p);
        // The desired card fits, other outcomes do not. Reject BEFORE any draw.
        reject(()->PhysicalPackTransaction.requireGuaranteedCapacity(p,Hand.MAIN_HAND,pack,1));same(c,p,before);
        var singlePool=new SetPackDefinition(pack.setId(),"单卡池测试",pack.dataVersion(),Map.of("one",Map.of(id,1)),List.of(new PackSlot("one",5,"五张")));
        PhysicalPackTransaction.requireGuaranteedCapacity(p,Hand.MAIN_HAND,singlePool,10);same(c,p,before);
        p.getInventory().setStack(1,new ItemStack(Items.DIRT,64));
        p.setStackInHand(Hand.MAIN_HAND,new ItemStack(PackItems.get(pack.setId()),10));
        PhysicalPackTransaction.requireGuaranteedCapacity(p,Hand.MAIN_HAND,singlePool,10);
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void batchCountsAndVariantsConserveCards(TestContext c) {
        var pack=PtcgServices.packs().get("surging_sparks");
        String id=CollectionProgress.poolIds(pack).iterator().next();
        for(int count:List.of(1,4,5,8,10,16,32,64)) {
            var p=c.createMockPlayer(GameMode.SURVIVAL);
            var held=PackItems.get(pack.setId()).stack(1);held.setCount(1);p.setStackInHand(Hand.MAIN_HAND,held);
            if(count>1){var others=PackItems.get(pack.setId()).stack(2);others.setCount(count-1);p.getInventory().setStack(1,others);}
            var result=draws(p,pack,count,id);var audit=new PackOpeningAuditService(Map.of(pack.setId(),pack));
            PhysicalPackTransaction.commit(p,Hand.MAIN_HAND,pack,count,result,()->audit.record(p.getUuid(),result,1));
            c.assertTrue(PhysicalPackTransaction.available(p,pack.setId())==0,"跨封面整批扣包不正确");
            c.assertTrue(InventoryCardTransfers.counts(p).getOrDefault(id,0)==count*pack.cardsPerPack(),"整批结果丢失或重复");
            c.assertTrue(audit.recordsForAdministration().size()==count,"逐包日志不完整");
            c.assertTrue(p.getInventory().getStack(40).isEmpty(),"发卡不应占用副手");
        }
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void fullInventoryRejectsAllWithoutUsingArmor(TestContext c) {
        var pack=PtcgServices.packs().get("surging_sparks");var p=c.createMockPlayer(GameMode.SURVIVAL);
        for(int i=0;i<36;i++)p.getInventory().setStack(i,new ItemStack(Items.DIRT,64));
        p.setStackInHand(Hand.MAIN_HAND,new ItemStack(PackItems.get(pack.setId()),11));
        var before=snapshot(p);String id=CollectionProgress.poolIds(pack).iterator().next();
        reject(()->PhysicalPackTransaction.commit(p,Hand.MAIN_HAND,pack,10,draws(p,pack,10,id),()->{throw new AssertionError("不应写日志");}));
        same(c,p,before);c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void lastPackSlotAndExistingStacksCanReceive(TestContext c) {
        var pack=PtcgServices.packs().get("surging_sparks");var p=c.createMockPlayer(GameMode.SURVIVAL);
        for(int i=0;i<36;i++)p.getInventory().setStack(i,new ItemStack(Items.DIRT,64));
        p.setStackInHand(Hand.MAIN_HAND,new ItemStack(PackItems.get(pack.setId()),10));
        String id=CollectionProgress.poolIds(pack).iterator().next();
        PhysicalPackTransaction.commit(p,Hand.MAIN_HAND,pack,10,draws(p,pack,10,id),()->{});
        c.assertTrue(InventoryCardTransfers.counts(p).get(id)==50,"没有利用最后卡包释放的格子和重复卡堆叠");
        p.setStackInHand(Hand.OFF_HAND,new ItemStack(PackItems.get(pack.setId())));
        PhysicalPackTransaction.commit(p,Hand.OFF_HAND,pack,1,draws(p,pack,1,id),()->{});
        c.assertTrue(InventoryCardTransfers.counts(p).get(id)==55&&p.getOffHandStack().isEmpty(),"副手开包未合入已有卡堆");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void auditFailureRollsBackWholeBatch(TestContext c) {
        var pack=PtcgServices.packs().get("surging_sparks");var p=c.createMockPlayer(GameMode.SURVIVAL);
        p.setStackInHand(Hand.MAIN_HAND,new ItemStack(PackItems.get(pack.setId()),10));
        var before=snapshot(p);var audit=new PackOpeningAuditService(Map.of(pack.setId(),pack));var result=draws(p,pack,10,CollectionProgress.poolIds(pack).iterator().next());
        audit.onChanged(()->{throw new IllegalStateException("模拟日志保存失败");});
        reject(()->PhysicalPackTransaction.commit(p,Hand.MAIN_HAND,pack,10,result,()->audit.record(p.getUuid(),result,1)));
        same(c,p,before);c.assertTrue(audit.recordsForAdministration().isEmpty(),"回滚后留下部分日志");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void missingPacksInvalidResultsAndQuantitiesDoNotMutate(TestContext c) {
        var pack=PtcgServices.packs().get("surging_sparks");var p=c.createMockPlayer(GameMode.SURVIVAL);
        String id=CollectionProgress.poolIds(pack).iterator().next();
        p.setStackInHand(Hand.MAIN_HAND,new ItemStack(PackItems.get(pack.setId()),4));var before=snapshot(p);
        reject(()->PhysicalPackTransaction.commit(p,Hand.MAIN_HAND,pack,5,draws(p,pack,5,id),()->{}));
        reject(()->PhysicalPackTransaction.commit(p,Hand.MAIN_HAND,pack,2,draws(p,pack,2,id),()->{}));
        reject(()->PhysicalPackTransaction.commit(p,Hand.MAIN_HAND,pack,1,List.of(),()->{}));
        same(c,p,before);
        p.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);before=snapshot(p);
        reject(()->PhysicalPackTransaction.commit(p,Hand.MAIN_HAND,pack,1,draws(p,pack,1,id),()->{}));same(c,p,before);c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void collectionProgressFollowsTransfersAndDoesNotLeakOwners(TestContext c) {
        var pack=PtcgServices.packs().get("surging_sparks");var a=c.createMockPlayer(GameMode.SURVIVAL);var b=c.createMockPlayer(GameMode.SURVIVAL);
        String id=CollectionProgress.poolIds(pack).iterator().next();var ledger=new CardBoxService(Clock.systemUTC());
        a.getInventory().setStack(0,new ItemStack(CardItems.byDefinitionId(id),3));
        var box=ledger.create(a.getUuid(),"收藏统计测试","standard-international-2026");
        a.getInventory().setStack(10,CardBoxItem.referenceTo(box));
        InventoryCardTransfers.deposit(a,ledger,id,1,null);InventoryCardTransfers.addToBox(a,ledger,box.id(),id);
        var owned=new HashMap<String,Integer>();ledger.unboxedForOwner(a.getUuid()).forEach(card->owned.merge(card.definitionId(),1,Integer::sum));
        var boxed=new HashMap<String,Integer>();ledger.contentsForValidation(a.getUuid(),box.id()).forEach(card->boxed.merge(card.definitionId(),1,Integer::sum));
        var progress=CollectionProgress.summarize(pack,CollectionProgress.combine(InventoryCardTransfers.counts(a),owned,boxed));
        c.assertTrue(progress.collected()==1&&progress.copies()==3&&progress.duplicates()==2,"转移造成重复计数或失去收集进度");
        c.assertTrue(CollectionProgress.summarize(pack,InventoryCardTransfers.counts(b)).copies()==0,"其他玩家获得了持有数量");c.complete();
    }
    private static List<PackOpeningService.Result> draws(PlayerEntity p,SetPackDefinition pack,int count,String id){
        var results=new ArrayList<PackOpeningService.Result>();
        for(int i=0;i<count;i++){
            var cards=new ArrayList<CardInstance>();for(int j=0;j<pack.cardsPerPack();j++)cards.add(new CardInstance(UUID.randomUUID(),p.getUuid(),id));
            results.add(new PackOpeningService.Result(pack.setId(),pack.dataVersion(),List.copyOf(cards),List.of()));
        }return List.copyOf(results);
    }
    private static List<ItemStack> snapshot(PlayerEntity p){var list=new ArrayList<ItemStack>();for(int i=0;i<p.getInventory().size();i++)list.add(p.getInventory().getStack(i).copy());return list;}
    private static void same(TestContext c,PlayerEntity p,List<ItemStack> before){for(int i=0;i<before.size();i++)c.assertTrue(ItemStack.areEqual(before.get(i),p.getInventory().getStack(i)),"失败后槽位变化："+i);}
    private static void reject(Runnable action){try{action.run();throw new AssertionError("应拒绝操作");}catch(IllegalArgumentException|IllegalStateException expected){}}
}
