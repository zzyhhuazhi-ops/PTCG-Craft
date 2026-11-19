package dev.hua.ptcglive.collection;

import dev.hua.ptcglive.CardItem;
import dev.hua.ptcglive.collectibles.CardCondition;
import dev.hua.ptcglive.CardItems;
import dev.hua.ptcglive.rules.CardBoxService;
import dev.hua.ptcglive.rules.CardInstance;
import java.util.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/** Server-thread-only moves. The physical inventory and world ledger never count the same copy twice. */
public final class InventoryCardTransfers {
    private InventoryCardTransfers() { }
    public static Map<String,Integer> counts(PlayerEntity player) {return counts(player,false);}
    public static Map<String,Integer> depositableCounts(PlayerEntity player){return counts(player,true);}
    private static Map<String,Integer> counts(PlayerEntity player,boolean onlyUngraded) {
        Map<String,Integer> result = new LinkedHashMap<>();
        for(int slot : slots()) {
            var stack=player.getInventory().getStack(slot);
            if(!stack.isEmpty() && stack.getItem() instanceof CardItem card && (!onlyUngraded||!CardCondition.graded(stack)))
                result.merge(card.card().id(), stack.getCount(), Math::addExact);
        }
        return result;
    }
    private static List<Integer> slots() {
        var slots=new ArrayList<Integer>(); for(int i=0;i<36;i++) slots.add(i); slots.add(40); return slots;
    }
    private static void validate(String id, int amount) {
        if(amount < 1 || amount > 64) throw new IllegalArgumentException("每次转移数量必须为 1–64 张");
        CardItems.byDefinitionId(id); // Registry-backed catalog identity, never a historical pull flag.
    }
    public static void deposit(PlayerEntity player, CardBoxService boxes, String id, int amount, UUID destinationBox) {
        validate(id,amount);
        if(destinationBox!=null) PlayerCardHoldings.requireBox(player,boxes,destinationBox);
        if((destinationBox==null?depositableCounts(player):counts(player)).getOrDefault(id,0)<amount) throw new IllegalArgumentException(destinationBox==null?"可存入图鉴的未鉴定卡不足；已鉴定卡只能放入卡盒或保留在背包":"背包中的实际卡牌数量不足");
        atomic(player,boxes,()->{
            if(destinationBox!=null) boxes.contentsForEditing(player.getUuid(),destinationBox);
            int remaining=amount;
            for(int slot:slots()) {
                var stack=player.getInventory().getStack(slot);
                if(stack.getItem() instanceof CardItem card && card.card().id().equals(id)
                    &&(destinationBox!=null||!CardCondition.graded(stack))) {
                    int n=Math.min(remaining,stack.getCount());
                    for(int i=0;i<n;i++) {
                        if(CardCondition.graded(stack)) boxes.depositPhysical(player.getUuid(),destinationBox,id,
                            stack.copyWithCount(1).encode(player.getRegistryManager()).toString());
                        else {var minted=boxes.mint(player.getUuid(),id);if(destinationBox!=null)boxes.put(player.getUuid(),destinationBox,minted.instanceId());}
                    }
                    stack.decrement(n); remaining-=n;
                    if(remaining==0) break;
                }
            }
            if(remaining!=0) throw new IllegalStateException("背包已发生变化，请刷新");
        });
    }
    public static void withdraw(PlayerEntity player, CardBoxService boxes, String id, int amount, UUID sourceBox) {
        validate(id,amount);
        if(sourceBox!=null) PlayerCardHoldings.requireBox(player,boxes,sourceBox);
        atomic(player,boxes,()->{
            var source=sourceBox==null?boxes.unboxedForOwner(player.getUuid()):boxes.contentsForEditing(player.getUuid(),sourceBox);
            var chosen=source.stream().filter(c->c.definitionId().equals(id)).limit(amount).toList();
            if(chosen.size()!=amount) throw new IllegalArgumentException("实际收藏数量不足");
            for(var card:chosen) returnPhysical(player,boxes,card,sourceBox);
            boxes.revokeUnboxed(player.getUuid(),chosen);
        });
    }
    private static void returnPhysical(PlayerEntity player,CardBoxService boxes,CardInstance card,UUID sourceBox) {
        String encoded=boxes.physicalData(player.getUuid(),card.instanceId());
        ItemStack stack;
        try {stack=encoded.isEmpty()?new ItemStack(CardItems.byDefinitionId(card.definitionId())):
            ItemStack.fromNbt(player.getRegistryManager(),net.minecraft.nbt.StringNbtReader.parse(encoded)).orElseThrow();}
        catch(Exception invalid){throw new IllegalStateException("卡牌原始数据无法读取，未取出",invalid);}
        if(stack.getCount()!=1||stack.getItem()!=CardItems.byDefinitionId(card.definitionId()))throw new IllegalStateException("卡牌原始数据不匹配");
        CardCondition.normalizeLegacyStack(stack);
        for(int slot:slots())CardCondition.normalizeLegacyStack(player.getInventory().getStack(slot));
        if(!player.getInventory().insertStack(stack)||!stack.isEmpty())throw new IllegalStateException("背包空间不足，未取出任何卡牌");
        if(sourceBox!=null)boxes.takeForInventory(player.getUuid(),sourceBox,card.instanceId());
    }
    public static void withdrawInstance(PlayerEntity player,CardBoxService boxes,UUID box,UUID instance) {
        PlayerCardHoldings.requireBox(player,boxes,box);
        atomic(player,boxes,()->{
            var card=boxes.contentsForEditing(player.getUuid(),box).stream().filter(c->c.instanceId().equals(instance)).findFirst().orElseThrow();
            returnPhysical(player,boxes,card,box);boxes.revokeUnboxed(player.getUuid(),List.of(card));
        });
    }
    /** Deposit all physical copies of the selected identities in one ledger/inventory transaction. */
    public static int depositBatch(PlayerEntity player, CardBoxService boxes, Set<String> selected) {
        var available = depositableCounts(player);
        if (selected.isEmpty() || selected.size() > 37) throw new IllegalArgumentException("没有可存入的背包卡牌");
        int total = 0;
        for (String id : selected) {
            CardItems.byDefinitionId(id);
            int count = available.getOrDefault(id, 0);
            if (count <= 0) continue;
            total = Math.addExact(total, count);
        }
        atomic(player, boxes, () -> {
            for (int slot : slots()) {
                var stack = player.getInventory().getStack(slot);
                if (stack.getItem() instanceof CardItem card && selected.contains(card.card().id()) && !CardCondition.graded(stack)) {
                    int count = stack.getCount();
                    stack.decrement(count);
                    for (int i = 0; i < count; i++) boxes.mint(player.getUuid(), card.card().id());
                }
            }
        });
        return total;
    }
    public static void addToBox(PlayerEntity player, CardBoxService boxes, UUID box, String id) {
        validate(id,1);
        PlayerCardHoldings.requireBox(player,boxes,box);
        var stored=boxes.unboxedForOwner(player.getUuid()).stream().filter(c->c.definitionId().equals(id)).findFirst();
        if(stored.isPresent()) boxes.transaction(()->{boxes.put(player.getUuid(),box,stored.get().instanceId());return null;});
        else deposit(player,boxes,id,1,box);
    }
    public static void atomic(PlayerEntity player,CardBoxService boxes,Runnable move) {
        var inventory=player.getInventory(); var before=new ArrayList<ItemStack>();
        for(int slot=0;slot<inventory.size();slot++) before.add(inventory.getStack(slot).copy());
        try { boxes.transaction(()->{move.run();return null;}); }
        catch(RuntimeException failure) {
            for(int slot=0;slot<before.size();slot++) inventory.setStack(slot,before.get(slot));
            throw failure;
        } finally { inventory.markDirty();player.currentScreenHandler.sendContentUpdates(); }
    }
}
