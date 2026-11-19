package dev.hua.ptcglive.network;

import com.google.gson.Gson;
import dev.hua.ptcglive.*;
import dev.hua.ptcglive.collection.*;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.network.ServerPlayerEntity;

/** One owner-only window token; every accepted intent advances revision before any move. */
public final class CollectionNetworking {
    private static final int BATCH_SIZE=120;
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final Gson JSON=new Gson();
    private static final class Session {
        final boolean heldItemRequired;
        final String token=UUID.randomUUID().toString(); final CollectionRevisionGate gate=new CollectionRevisionGate(); int page; long lastTick=-100;
        String query="",box="",progressPack="";
        CollectionFilters filters=CollectionFilters.DEFAULT,previewFilters;
        String previewQuery="";
        CollectionProgress.Filter progressFilter=CollectionProgress.Filter.ALL;
        Session(boolean heldItemRequired){this.heldItemRequired=heldItemRequired;}
    }
    public static void initialize() {
        PayloadTypeRegistry.playC2S().register(CollectionIntentPayload.ID,CollectionIntentPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CollectionStatePayload.ID,CollectionStatePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(CollectionIntentPayload.ID,(payload,context)->handle(context.player(),payload));
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->SESSIONS.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server->SESSIONS.clear());
    }
    private static void requireHeld(ServerPlayerEntity player) {
        if(!player.getMainHandStack().isOf(PtcgContent.CARD_ENCYCLOPEDIA)&&!player.getOffHandStack().isOf(PtcgContent.CARD_ENCYCLOPEDIA))
            throw new IllegalStateException("请手持卡牌图鉴");
    }
    public static void open(ServerPlayerEntity player) {
        requireHeld(player);open(player,true);
    }
    public static void openFromPhone(ServerPlayerEntity player) {
        open(player,false);
    }
    private static void open(ServerPlayerEntity player,boolean heldItemRequired) {
        var session=new Session(heldItemRequired);SESSIONS.put(player.getUuid(),session);send(player,session,true,"");
    }
    private static void handle(ServerPlayerEntity player,CollectionIntentPayload request) {
        var session=SESSIONS.get(player.getUuid());
        if(session==null||!session.token.equals(request.sessionId()))return;
        PhoneNetworking.touch(player);
        if(request.action().equals("CLOSE")){SESSIONS.remove(player.getUuid());return;}
        if(session.heldItemRequired)try {requireHeld(player);}catch(IllegalStateException ex){SESSIONS.remove(player.getUuid());ServerPlayNetworking.send(player,new CollectionStatePayload(false,"",ex.getMessage()));return;}
        if(session.gate.revision()!=request.revision()){send(player,session,false,"界面已更新，重复请求未执行。");return;}
        long tick=player.getServer().getTicks();
        if(tick-session.lastTick<2){send(player,session,false,"操作过快，请稍后重试。");return;}
        if(!session.gate.claim(request.revision()))return;
        session.lastTick=tick;
        String notice="";
        Set<String> changed = new HashSet<>();
        try {
            var boxes=PtcgServices.cardBoxes();String id=request.argument();
            switch(request.action()) {
                case "REFRESH" -> session.page=0;
                case "PAGE" -> {
                    if(request.amount()<0||request.amount()>100000)throw new IllegalArgumentException("搜索或页码无效");
                    CollectionSearch.queryTerms(request.query());
                    session.query=request.query().strip();session.page=request.amount();
                }
                case "SEARCH", "PREVIEW" -> {
                    CollectionFilters next=CollectionSearch.parseFilters(id);
                    CollectionSearch.queryTerms(request.query());
                    if(request.action().equals("SEARCH")) {
                        session.filters=next;session.query=request.query().strip();session.page=0;session.previewFilters=null;
                    } else {session.previewFilters=next;session.previewQuery=request.query().strip();}
                }
                case "ALL" -> {session.filters=session.filters.withOwnership(session.filters.ownership().equals("OWNED")?"ALL":"OWNED");session.page=0;}
                case "PROGRESS" -> {
                    if(!id.isEmpty()&&!PtcgServices.packs().containsKey(id))throw new IllegalArgumentException("未注册的卡包");
                    session.progressPack=id;session.page=0;session.query="";session.progressFilter=CollectionProgress.Filter.ALL;
                    session.filters=session.filters.withOwnership("ALL");
                }
                case "FILTER" -> {session.progressFilter=CollectionProgress.Filter.valueOf(id);session.page=0;}
                case "BOX" -> {PlayerCardHoldings.requireBox(player,boxes,UUID.fromString(id));session.box=id;}
                case "DEPOSIT" -> { changed.add(id); InventoryCardTransfers.deposit(player,boxes,id,request.amount(),null); }
                case "DEPOSIT_SELECTED", "DEPOSIT_ALL" -> {
                    if(request.amount()!=0)throw new IllegalArgumentException("批量存入数量由服务器计算");
                    if(request.action().equals("DEPOSIT_ALL")) changed.addAll(InventoryCardTransfers.counts(player).keySet());
                    else {
                        var array=com.google.gson.JsonParser.parseString(id);
                        if(!array.isJsonArray()||array.getAsJsonArray().size()>37)throw new IllegalArgumentException("批量选择无效");
                        for(var entry:array.getAsJsonArray()) {
                            if(!entry.isJsonPrimitive()||!entry.getAsJsonPrimitive().isString()||!changed.add(entry.getAsString()))
                                throw new IllegalArgumentException("批量选择包含无效或重复卡牌");
                        }
                    }
                    int deposited=InventoryCardTransfers.depositBatch(player,boxes,changed);
                    notice="已存入 "+deposited+" 张未鉴定卡牌；已鉴定卡自动跳过";
                }
                case "WITHDRAW" -> { changed.add(id); InventoryCardTransfers.withdraw(player,boxes,id,request.amount(),null); }
                case "ADD" -> {
                    if(request.amount()!=1)throw new IllegalArgumentException("每次加入一张卡");
                    if(session.box.isBlank())throw new IllegalStateException("请先创建并选择自己的卡盒");
                    changed.add(id); InventoryCardTransfers.addToBox(player,boxes,UUID.fromString(session.box),id);
                }
                case "BOX_TAKE" -> {
                    if(session.box.isBlank())throw new IllegalStateException("请先创建并选择自己的卡盒");
                    changed.add(id); InventoryCardTransfers.withdraw(player,boxes,id,request.amount(),UUID.fromString(session.box));
                }
                default -> throw new IllegalArgumentException("不允许的图鉴操作");
            }
        }catch(IllegalArgumentException|IllegalStateException|com.google.gson.JsonParseException ex){notice=ex.getMessage()==null?"操作参数无效":ex.getMessage();}
        send(player,session,false,notice,changed);
    }
    private static void send(ServerPlayerEntity player,Session session,boolean open,String notice) {
        send(player,session,open,notice,Set.of());
    }
    private static void send(ServerPlayerEntity player,Session session,boolean open,String notice,Set<String> changed) {
        var service=PtcgServices.cardBoxes();UUID owner=player.getUuid();
        var holdings=PlayerCardHoldings.snapshot(player,service);
        var inventory=holdings.inventory();
        service.observe(owner,inventory.keySet());
        var collection=holdings.collection();
        // Encyclopedia ownership/progress only includes inventory and explicit
        // encyclopedia deposits. Box contents stay in the separate box screen.
        Map<String,Integer> boxed=Map.of();
        var boxes=holdings.boxes();
        if(boxes.stream().noneMatch(b->b.id().toString().equals(session.box)))session.box=boxes.isEmpty()?"":boxes.getFirst().id().toString();
        var totals=CollectionProgress.combine(inventory,collection,boxed);
        var summaries=PtcgServices.packs().values().stream().map(pack->CollectionProgress.summarize(pack,totals))
            .sorted(Comparator.comparing(CollectionProgress.Summary::nameZh).thenComparing(CollectionProgress.Summary::setId)).toList();
        var progressPack=PtcgServices.packs().get(session.progressPack);
        Set<String> pool=progressPack==null?Set.of():CollectionProgress.poolIds(progressPack);
        List<CollectionView.Row> rows=new ArrayList<>();
        List<CollectionView.Row> updates=new ArrayList<>();
        var terms=CollectionSearch.queryTerms(session.query);
        var previewTerms=CollectionSearch.queryTerms(session.previewQuery);int previewCount=0;
        for(var card:PtcgServices.cardCatalog()) {
            int i=inventory.getOrDefault(card.id(),0),c=collection.getOrDefault(card.id(),0),b=boxed.getOrDefault(card.id(),0);
            String type=CollectionSearch.typeLabel(card.id());
            var row=new CollectionView.Row(card,type,i,c,b,service.discovered(owner,card.id())||b>0);
            if(changed.contains(card.id()))updates.add(row);
            if(progressPack!=null) {
                if(!pool.contains(card.id())||!session.progressFilter.accepts(totals.getOrDefault(card.id(),0)))continue;
            }
            if(session.previewFilters!=null&&CollectionSearch.matches(card,i,c,session.previewFilters,previewTerms))previewCount++;
            if(!CollectionSearch.matches(card,i,c,session.filters,terms))continue;
            rows.add(row);
        }
        rows.sort(Comparator.comparing(CollectionView.Row::card,CollectionSearch.comparator(session.filters)));
        int pages=Math.max(1,(rows.size()+BATCH_SIZE-1)/BATCH_SIZE);session.page=Math.min(session.page,pages-1);
        var view=new CollectionView(session.token,session.gate.revision(),session.page,pages,session.query,!session.filters.ownership().equals("OWNED"),session.box,
            boxes.stream().limit(100).map(b->new CollectionView.Box(b.id().toString(),b.nameZh())).toList(),
            List.copyOf(rows.subList(session.page*BATCH_SIZE,Math.min(rows.size(),(session.page+1)*BATCH_SIZE))),
            summaries,session.progressPack,session.progressFilter,List.copyOf(updates),session.filters,CollectionSearch.facets(),CollectionSearch.formats(),rows.size(),
            session.previewFilters,session.previewQuery,previewCount);
        ServerPlayNetworking.send(player,new CollectionStatePayload(open,JSON.toJson(view),notice));
    }
}
