package dev.hua.ptcglive.economy;

import dev.hua.ptcglive.*;
import dev.hua.ptcglive.collection.PlayerCardHoldings;
import dev.hua.ptcglive.rules.CardBoxService;
import java.util.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/** Server-thread recycling of real inventory or deposited copies only. Never scans a deck box. */
public final class RecyclingService {
    private RecyclingService() { }
    public enum Source { INVENTORY, COLLECTION, BOTH }
    public static final List<String> RARITIES=List.of("C","U","R","RR","RRR","SR","AR","SAR","UR","SPECIAL","PROMO","UNKNOWN");
    public static String rarity(String printed) {
        return CardRarity.economy(printed);
    }
    public static String rarity(String id,String printed){return CardRarity.economy(id,printed);}
    public static int price(String code) {
        return switch(code) {
            case "C","PROMO","UNKNOWN"->1;case "U"->2;case "R"->3;case "RR"->4;case "RRR"->5;
            case "SR","AR"->6;case "SAR"->7;case "UR"->8;case "SPECIAL"->20;
            default->throw new IllegalArgumentException("未知罕度");
        };
    }
    public static int keepCount(String id) {
        var facts=dev.hua.ptcglive.collection.CollectionSearch.facts(id);
        if(facts==null)throw new IllegalArgumentException("未知卡牌类型："+id);
        return switch(facts.category()) {case "ENERGY"->64;case "TRAINER"->16;case "POKEMON"->8;default->throw new IllegalArgumentException("未知卡牌类型："+id);};
    }
    public record Row(String id,String rarity,int inventory,int collection,int amount,int unitValue,int keepCount) { }
    public record Quote(UUID token,UUID owner,Source source,List<Row> rows,long coins,int count) {
        public Quote {rows=List.copyOf(rows);}
    }
    public static List<Row> rows(PlayerEntity player,CardBoxService boxes,Source source,Set<String> rarities) {
        if(!new HashSet<>(RARITIES).containsAll(rarities))throw new IllegalArgumentException("罕度筛选无效");
        var held=PlayerCardHoldings.snapshot(player,boxes);
        var ids=new TreeSet<String>();ids.addAll(held.inventory().keySet());ids.addAll(held.collection().keySet());
        var result=new ArrayList<Row>();
        for(String id:ids) {
            var card=CardItems.byDefinitionId(id).card();String rarity=rarity(card.id(),card.rarityZh());
            if(!rarities.contains(rarity))continue;
            int i=held.inventory().getOrDefault(id,0),c=held.collection().getOrDefault(id,0),total=Math.addExact(i,c);
            int available=switch(source){case INVENTORY->i;case COLLECTION->c;case BOTH->total;};
            int keep=keepCount(id);
            if(available>0)result.add(new Row(id,rarity,i,c,Math.min(available,Math.max(0,total-keep)),price(rarity),keep));
        }
        return List.copyOf(result);
    }
    public static Quote quote(PlayerEntity player,CardBoxService boxes,Source source,Set<String> rarities,Set<String> selected) {
        var eligible=rows(player,boxes,source,rarities).stream().filter(r->r.amount()>0&&(selected==null||selected.contains(r.id()))).toList();
        if(eligible.isEmpty())throw new IllegalArgumentException("没有多余卡牌；同一卡牌能量留64、训练家留16、宝可梦留8张");
        if(selected!=null&&eligible.size()!=selected.size())throw new IllegalStateException("所选卡牌已变化或未超过保留数量，请刷新");
        long coins=0;int count=0;
        for(var row:eligible){coins=Math.addExact(coins,pricedRow(player,source,row));count=Math.addExact(count,row.amount());}
        coins/=100;
        if(coins<1)throw new IllegalArgumentException("品质折算后的总收益不足1宝可币，请增加回收数量；卡牌未扣除");
        return new Quote(UUID.randomUUID(),player.getUuid(),source,eligible,coins,count);
    }
    private static long pricedRow(PlayerEntity player,Source source,Row row){
        int remaining=row.amount();long hundredths=0;
        if(source!=Source.COLLECTION)for(int slot=0;slot<=40&&remaining>0;slot++){
            if(slot>=36&&slot<40)continue;var stack=player.getInventory().getStack(slot);
            if(stack.getItem() instanceof CardItem card&&card.card().id().equals(row.id())){
                int n=Math.min(remaining,stack.getCount());remaining-=n;
                hundredths=Math.addExact(hundredths,(long)n*row.unitValue()*dev.hua.ptcglive.collectibles.CardCondition.percent(stack));
            }
        }
        return Math.addExact(hundredths,(long)remaining*row.unitValue()*100);
    }
    public static void confirm(PlayerEntity player,CardBoxService boxes,PokeCoinLedger wallet,Quote quote) {
        if(quote==null||!player.getUuid().equals(quote.owner()))throw new IllegalArgumentException("回收确认不属于此玩家");
        var ids=new HashSet<String>();for(var row:quote.rows())if(!ids.add(row.id()))throw new IllegalArgumentException("重复卡牌条目");
        var now=quote(player,boxes,quote.source(),new HashSet<>(RARITIES),ids);
        if(!now.rows().equals(quote.rows())||now.coins()!=quote.coins()||now.count()!=quote.count())
            throw new IllegalStateException("卡牌数量或来源已变化，未回收；请重新预览确认");
        var inventory=player.getInventory();var before=new ArrayList<ItemStack>();
        for(int slot=0;slot<inventory.size();slot++)before.add(inventory.getStack(slot).copy());
        var moneyBefore=wallet.snapshot();
        try {
            boxes.transaction(()->{
                // Select actual unboxed instances once. Discoveries and boxed/locked cards are never candidates.
                var unboxed=new HashMap<String,List<dev.hua.ptcglive.rules.CardInstance>>();
                for(var card:boxes.unboxedForOwner(player.getUuid()))unboxed.computeIfAbsent(card.definitionId(),key->new ArrayList<>()).add(card);
                var revoked=new ArrayList<dev.hua.ptcglive.rules.CardInstance>();
                for(var row:quote.rows()) {
                    int remaining=row.amount();
                    if(quote.source()!=Source.COLLECTION)for(int slot=0;slot<=40&&remaining>0;slot++) {
                        if(slot>=36&&slot<40)continue;
                        var stack=inventory.getStack(slot);
                        if(stack.getItem() instanceof CardItem card&&card.card().id().equals(row.id())) {
                            int taken=Math.min(remaining,stack.getCount());stack.decrement(taken);remaining-=taken;
                        }
                    }
                    if(remaining>0) {
                        var cards=unboxed.getOrDefault(row.id(),List.of());
                        if(quote.source()==Source.INVENTORY||cards.size()<remaining)throw new IllegalStateException("实际卡牌不足，回收取消");
                        revoked.addAll(cards.subList(0,remaining));
                    }
                }
                if(!revoked.isEmpty())boxes.revokeUnboxed(player.getUuid(),revoked);
                if(quote.coins()>0)wallet.credit(quote.token(),player.getUuid(),quote.coins(),PokeCoinLedger.Kind.CARD_RECYCLE,
                    "回收 "+quote.count()+" 张；来源 "+switch(quote.source()){case INVENTORY->"背包";case COLLECTION->"图鉴";case BOTH->"背包＋图鉴";}+"；各罕度数量 "+rarityCounts(quote.rows()),player.getWorld().getTime());
                return null;
            });
        } catch(RuntimeException failure) {
            for(int slot=0;slot<before.size();slot++)inventory.setStack(slot,before.get(slot));
            wallet.restoreSnapshot(moneyBefore);
            throw failure;
        } finally {inventory.markDirty();}
        // Packet delivery is outside the atomic change; a disconnected viewer cannot replay the consumed quote.
        player.currentScreenHandler.sendContentUpdates();
    }
    private static Map<String,Integer> rarityCounts(List<Row> rows) {
        var result=new TreeMap<String,Integer>();for(var row:rows)result.merge(row.rarity().equals("SPECIAL")?"特殊":row.rarity(),row.amount(),Math::addExact);return result;
    }
}
