package dev.hua.ptcglive.network;

import com.google.gson.Gson;
import dev.hua.ptcglive.PackItem;
import dev.hua.ptcglive.PtcgServices;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.packs.*;
import dev.hua.ptcglive.rules.CardInstance;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import org.slf4j.LoggerFactory;
import java.util.*;

/** Owner-only, single-use session. Client selects a supported batch size, never results or odds. */
public final class PackNetworking {
    private static final long SESSION_TIMEOUT_TICKS = 6_000L;
    private record OpenPack(UUID sessionId, SetPackDefinition pack, Hand hand, PackUiSnapshot preview, long openedAtTick) { }
    private static final Map<UUID, OpenPack> open = new LinkedHashMap<>();
    private static final Gson JSON = new Gson();
    private PackNetworking() { }
    public static void initialize() {
        PayloadTypeRegistry.playC2S().register(PackIntentPayload.ID, PackIntentPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PackStatePayload.ID, PackStatePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(PackIntentPayload.ID, (payload, context) -> handle(context.player(), payload));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> open.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> open.clear());
    }
    public static synchronized void open(ServerPlayerEntity player, SetPackDefinition pack, Hand hand) {
        PhysicalPackTransaction.requirePacks(player, hand, pack.setId(), 1);
        UUID token = UUID.randomUUID();
        var preview = PackUiSnapshot.preview(token, pack, PtcgServices.cardCatalog(), PtcgServices.cardDatabase(), PackItem.variantOf(player.getStackInHand(hand)));
        open.put(player.getUuid(), new OpenPack(token, pack, hand, preview, player.getServer().getTicks()));
        send(player, preview, "持有 " + PhysicalPackTransaction.available(player, pack.setId()) + " 包；可选 1／4／5／8／10／16／32／64 包，每包 " + pack.cardsPerPack() + " 张。确认前不消耗。");
    }
    private static synchronized void handle(ServerPlayerEntity player, PackIntentPayload payload) {
        // Read-only browsing never creates an opening credential or grants cards.
        if(payload.action().equals("BROWSE")||payload.action().equals("SHOP")) {
            var pack=PtcgServices.packs().get(payload.sessionId());if(pack==null)return;
            if(payload.action().equals("SHOP"))PhoneNetworking.openShop(player,pack.setId());
            else send(player,PackUiSnapshot.preview(UUID.randomUUID(),pack,PtcgServices.cardCatalog(),PtcgServices.cardDatabase()).browseOnly(),"仅查看卡池，不消耗卡包。勾选罕度只影响显示，不改变概率。");
            return;
        }
        OpenPack session = open.get(player.getUuid());
        if (session == null || !session.sessionId().toString().equals(payload.sessionId())) return;
        if (player.getServer().getTicks() - session.openedAtTick() > SESSION_TIMEOUT_TICKS) {
            close(player, "抽卡确认已超时，未消耗卡包。"); return;
        }
        if ("CANCEL".equals(payload.action())) { close(player, "已取消抽取，未消耗卡包。"); return; }
        int count = PackBatchPolicy.countForAction(payload.action());
        if (count == 0) return;
        var destination=PackDestination.forAction(payload.action());
        try {
            if(destination==PackDestination.INVENTORY)PhysicalPackTransaction.requireGuaranteedCapacity(player, session.hand(), session.pack(), count);
            else PhysicalPackTransaction.requirePacks(player,session.hand(),session.pack().setId(),count);
        } catch (IllegalArgumentException | IllegalStateException failure) {
            player.sendMessage(net.minecraft.text.Text.literal("[PTCG] " + failure.getMessage()), false);
            send(player, session.preview(), failure.getMessage()); return;
        }
        // Consume before drawing, staging or persisting: retries cannot execute a second transaction.
        open.remove(player.getUuid());
        PackUiSnapshot outcome;
        List<PackOpeningService.Result> draws = new ArrayList<>();
        try {
            var random = new ServerRandom();
            for (int i = 0; i < count; i++) draws.add(PtcgServices.packOpening().drawPhysical(player.getUuid(), session.pack(), random));
            var ids = draws.stream().flatMap(r -> r.cards().stream()).map(CardInstance::definitionId).toList();
            outcome = session.preview().withDestination(destination).withBatchResults(ids, count, PtcgServices.cardCatalog(), PtcgServices.cardDatabase());
            if (JSON.toJson(outcome).length() > 250000) throw new IllegalStateException("卡包结果数据过大，未消耗卡包");
            PhysicalPackTransaction.commit(player, session.hand(), session.pack(), count, draws, destination, PtcgServices.packOpening(),
                () -> PtcgServices.packOpeningAudit().record(player.getUuid(), draws, player.getServerWorld().getTime()));
        } catch (RuntimeException failure) {
            LoggerFactory.getLogger("ptcglive").warn("批量开包未提交：player={} pack={} count={}", player.getUuid(), session.pack().setId(), count, failure);
            close(player, "本次未发卡、未扣包：" + (failure.getMessage() == null ? "服务器数据异常，请重新打开卡包" : failure.getMessage()));
            return;
        }
        // Already committed: notification failures must not be reported as transaction rollbacks.
        for (int i = 0; i < count; i++) {
            int start = i * session.pack().cardsPerPack();
            String text = resultText(outcome.results().subList(start, start + session.pack().cardsPerPack()));
            player.sendMessage(net.minecraft.text.Text.literal("[PTCG] 你打开了「" + session.pack().displayNameZh() + "」（" + (i + 1) + "/" + count + "）：" + text), false);
            LoggerFactory.getLogger("ptcglive").info("开包成功：player={} pack={} time={} dataVersion={} batch={}/{} results={}",
                player.getUuid(), session.pack().setId(), java.time.Instant.now(), session.pack().dataVersion(), i + 1, count, text);
        }
        send(player, outcome, "已打开 " + count + " 包，共 " + outcome.results().size() + " 张全部进入"+destination.labelZh()+"；滚轮翻页，右键看大图。");
    }
    private static void close(ServerPlayerEntity player, String message) {
        open.remove(player.getUuid());
        player.sendMessage(net.minecraft.text.Text.literal("[PTCG] " + message), false);
        ServerPlayNetworking.send(player, new PackStatePayload(false, "", message));
    }
    private static String resultText(List<PackUiSnapshot.CardRow> cards) {
        return cards.stream().map(card -> card.nameZh() + "（" + card.printedNumber() + "／" + card.rarityZh() + "）")
            .collect(java.util.stream.Collectors.joining("、"));
    }
    private static void send(ServerPlayerEntity player, PackUiSnapshot snapshot, String message) {
        ServerPlayNetworking.send(player, new PackStatePayload(true, JSON.toJson(snapshot), message == null ? "" : message));
    }
}
