package dev.hua.ptcglive.packs;

import dev.hua.ptcglive.CardItems;
import dev.hua.ptcglive.PackItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import java.util.*;

/** Server-thread transaction. Stage exact stacks first; never use armor slots or drop overflow. */
public final class PhysicalPackTransaction {
    private PhysicalPackTransaction() { }
    public static int available(PlayerEntity player, String setId) {
        int count = 0;
        for (int slot : storageSlots()) {
            var stack = player.getInventory().getStack(slot);
            if (!stack.isEmpty() && stack.getItem() instanceof PackItem pack && pack.setId().equals(setId)) count += stack.getCount();
        }
        return count;
    }
    public static void requirePacks(PlayerEntity player, Hand hand, String setId, int count) {
        PackBatchPolicy.validateCount(count);
        var held = player.getStackInHand(hand);
        if (held.isEmpty() || !(held.getItem() instanceof PackItem item) || !item.setId().equals(setId))
            throw new IllegalStateException("请仍手持原卡包；本次未消耗卡包。");
        if (available(player, setId) < count) throw new IllegalStateException("该卡包数量不足，需要 " + count + " 包；不同封面可以合并计数。");
    }
    public static void commit(PlayerEntity player, Hand hand, SetPackDefinition pack, int count,
                              List<PackOpeningService.Result> results, Runnable persistAudit) {
        commit(player,hand,pack,count,results,PackDestination.INVENTORY,null,persistAudit);
    }
    public static void commit(PlayerEntity player, Hand hand, SetPackDefinition pack, int count,
                              List<PackOpeningService.Result> results, PackDestination destination,
                              PackOpeningService opening, Runnable persistAudit) {
        Objects.requireNonNull(destination);
        if(destination==PackDestination.COLLECTION)Objects.requireNonNull(opening);
        requirePacks(player, hand, pack.setId(), count);
        if (results.size() != count) throw new IllegalStateException("整批开包数量无效");
        var pool = dev.hua.ptcglive.collection.CollectionProgress.poolIds(pack);
        var inventory = player.getInventory(); var before = new ArrayList<ItemStack>();
        for (int i = 0; i < inventory.size(); i++) before.add(inventory.getStack(i).copy());
        var staged = new ArrayList<ItemStack>(); before.forEach(s -> staged.add(s.copy()));
        var order = new ArrayList<Integer>();
        int heldSlot = hand == Hand.OFF_HAND ? 40 : inventory.selectedSlot;
        order.add(heldSlot); for (int slot : storageSlots()) if (slot != heldSlot) order.add(slot);
        int remaining = count;
        for (int slot : order) {
            var stack = staged.get(slot);
            if (stack.getItem() instanceof PackItem item && item.setId().equals(pack.setId())) {
                int used = Math.min(remaining, stack.getCount()); stack.decrement(used); remaining -= used;
            }
            if (remaining == 0) break;
        }
        if (remaining != 0) throw new IllegalStateException("卡包数量已变化，未进行抽取");
        for (var result : results) {
            if (!result.setId().equals(pack.setId()) || !result.dataVersion().equals(pack.dataVersion()) || result.cards().size() != pack.cardsPerPack())
                throw new IllegalStateException("开包数据无效，未扣除任何卡包");
            for (var card : result.cards()) {
                if (!card.ownerId().equals(player.getUuid()) || !pool.contains(card.definitionId()))
                    throw new IllegalStateException("开包卡牌不属于当前卡池或玩家");
                if(destination==PackDestination.COLLECTION)continue;
                ItemStack incoming = new ItemStack(CardItems.byDefinitionId(card.definitionId()));
                // Only merge into ordinary inventory. New cards never occupy armor or offhand.
                for (int slot = 0; slot < 36 && !incoming.isEmpty(); slot++) {
                    var target = staged.get(slot);
                    if (!target.isEmpty() && ItemStack.areItemsAndComponentsEqual(target, incoming)) {
                        int amount = Math.min(incoming.getCount(), Math.max(0, target.getMaxCount() - target.getCount()));
                        target.increment(amount); incoming.decrement(amount);
                    }
                }
                for (int slot = 0; slot < 36 && !incoming.isEmpty(); slot++) if (staged.get(slot).isEmpty()) {
                    staged.set(slot, incoming.copy()); incoming.setCount(0);
                }
                if (!incoming.isEmpty()) throw new IllegalStateException("背包空间不足以接收整批 " + (count * pack.cardsPerPack()) + " 张卡；未扣包、未发卡，请整理背包后重新右键。");
            }
        }
        try {
            for (int slot = 0; slot < staged.size(); slot++) inventory.setStack(slot, staged.get(slot));
            if(destination==PackDestination.COLLECTION)opening.storeInCollection(player.getUuid(),pack,results,persistAudit);
            else persistAudit.run();
        } catch (RuntimeException failure) {
            for (int slot = 0; slot < before.size(); slot++) inventory.setStack(slot, before.get(slot));
            throw failure;
        } finally {
            inventory.markDirty(); player.currentScreenHandler.sendContentUpdates();
        }
    }
    private static List<Integer> storageSlots() {
        var slots = new ArrayList<Integer>(); for (int i = 0; i < 36; i++) slots.add(i); slots.add(40); return slots;
    }
    /**
     * Admission must not depend on hidden draw results: otherwise a full bag of
     * desired cards becomes a free rarity filter. This conservative bound holds
     * for EVERY possible result, counting existing compatible stacks and slots
     * freed by consuming packs. Exact staging in commit remains the second check.
     */
    public static void requireGuaranteedCapacity(PlayerEntity player, Hand hand, SetPackDefinition pack, int count) {
        requirePacks(player, hand, pack.setId(), count);
        var inventory=player.getInventory();var staged=new ArrayList<ItemStack>();
        for(int i=0;i<inventory.size();i++)staged.add(inventory.getStack(i).copy());
        int heldSlot=hand==Hand.OFF_HAND?40:inventory.selectedSlot;
        var order=new ArrayList<Integer>();order.add(heldSlot);for(int slot:storageSlots())if(slot!=heldSlot)order.add(slot);
        int remaining=count;
        for(int slot:order){
            var stack=staged.get(slot);
            if(stack.getItem() instanceof PackItem item&&item.setId().equals(pack.setId())){
                int used=Math.min(remaining,stack.getCount());stack.decrement(used);remaining-=used;
            }
            if(remaining==0)break;
        }
        int empty=0;for(int i=0;i<36;i++)if(staged.get(i).isEmpty())empty++;
        long slotBound=0;int receivingTypes=0,minStack=Integer.MAX_VALUE;
        for(String id:dev.hua.ptcglive.collection.CollectionProgress.poolIds(pack)){
            var card=new ItemStack(CardItems.byDefinitionId(id));
            int maximum=0;
            for(var slot:pack.slots())if(pack.pools().get(slot.poolId()).containsKey(id))maximum=Math.addExact(maximum,Math.multiplyExact(slot.count(),count));
            int room=0;
            for(int i=0;i<36;i++){
                var stack=staged.get(i);
                if(!stack.isEmpty()&&ItemStack.areItemsAndComponentsEqual(stack,card))room+=Math.max(0,stack.getMaxCount()-stack.getCount());
            }
            int needed=Math.max(0,maximum-room);
            slotBound+=(needed+(long)card.getMaxCount()-1)/card.getMaxCount();
            if(needed>0){receivingTypes++;minStack=Math.min(minStack,card.getMaxCount());}
        }
        long total=Math.multiplyExact(pack.cardsPerPack(),count);
        // Each possible type may start one new stack with one card. Every further
        // stack costs at least minStack more cards. Bound by the shared draw total,
        // rather than pretending every type receives the entire batch at once.
        long sharedBound=total<receivingTypes?total:receivingTypes==0?0:receivingTypes+(total-receivingTypes)/minStack;
        long required=Math.min(sharedBound,slotBound);
        if(empty<required)throw new IllegalStateException("背包容量无法保证整批接收：需预留 "+required+" 格，扣包后可用 "+empty+" 格；请整理背包、减少开包数量或改存图鉴。未抽取、未扣包。");
    }
}
