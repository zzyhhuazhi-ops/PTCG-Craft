package dev.hua.ptcglive.network;

import com.google.gson.Gson;
import dev.hua.ptcglive.PtcgServices;
import dev.hua.ptcglive.engine.MatchSession;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.LoggerFactory;
import java.util.*;

public final class MatchNetworking {
    private record Subscription(UUID match, UUID seat,boolean computer) { }
    private static final Map<UUID, Subscription> subscriptions = new HashMap<>();
    private static final Gson JSON = new Gson();
    private static long aiTick;
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger("ptcglive-match");
    private MatchNetworking() { }
    public static void initialize() {
        PayloadTypeRegistry.playC2S().register(MatchIntentPayload.ID, MatchIntentPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MatchStatePayload.ID, MatchStatePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(MatchIntentPayload.ID, (payload, context) -> handle(context.player(), payload));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            subscriptions.remove(handler.player.getUuid());
            var ended = PtcgServices.tables().disconnectPlayer(handler.player.getUuid());
            for (var entry : List.copyOf(subscriptions.entrySet())) if (ended.contains(entry.getValue().match)) {
                subscriptions.remove(entry.getKey());
                var recipient = server.getPlayerManager().getPlayer(entry.getKey());
                if (recipient != null) ServerPlayNetworking.send(recipient, new MatchStatePayload(false, "",
                    "对局玩家已断线，本局安全中止，卡盒已解除锁定。请重新打开卡桌。"));
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> subscriptions.clear());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for(var update:PtcgServices.tables().tickComputers(++aiTick))broadcast(server,update.match(),update.notice(),update.aborted());
            for(var update:PtcgServices.tables().tickReadAcknowledgements(aiTick))broadcast(server,update.match(),update.notice(),update.aborted());
            if(aiTick%20==0)for(var sub:List.copyOf(subscriptions.values()))if(!PtcgServices.tables().hasMatch(sub.match))
                broadcast(server,sub.match,sub.computer?"电脑练习异常终止：卡桌或对局已失效；卡盒已解锁，未判定胜负。":"对局已中止，卡盒已解锁，请重新打开卡桌。",true);
        });
    }
    public static void open(ServerPlayerEntity player, UUID matchId) {
        var tables=PtcgServices.tables();
        UUID seat = tables.matchSeats(matchId,player.getUuid()).stream().findFirst().orElse(null);
        var view=tables.matchView(matchId,player.getUuid(), seat); // authorize before storing a subscription
        subscriptions.put(player.getUuid(), new Subscription(matchId, seat,view.computer()!=null));
        send(player, matchId, seat, true, "");
    }
    private static void handle(ServerPlayerEntity player, MatchIntentPayload payload) {
        try {
            UUID id = UUID.fromString(payload.matchId());
            UUID seat = payload.seat().isEmpty() ? null : UUID.fromString(payload.seat());
            Subscription active = subscriptions.get(player.getUuid());
            if (active == null || !active.match.equals(id)) throw new IllegalArgumentException("请先从卡桌打开这场对局");
            if (payload.option() == -2) {
                PtcgServices.tables().suspendComputerPresentation(id,player.getUuid());
                subscriptions.remove(player.getUuid()); return;
            } // close panel, not concede; reopening waits for the new screen's presentation
            if(payload.option()==MatchIntentPayload.PRESENTATION_FINISHED){
                PtcgServices.tables().finishComputerPresentation(id,player.getUuid(),payload.revision());
                return;
            }
            PtcgServices.tables().matchView(id,player.getUuid(), seat); // rejects attempts to impersonate an opposing seat
            if (payload.option() == -1) {
                subscriptions.put(player.getUuid(), new Subscription(id, seat,active.computer));
                send(player, id, seat, false, "");
                return;
            }
            PtcgServices.tables().submit(id, player.getUuid(), seat, payload.revision(), payload.option());
            broadcast(player.getServer(),id,"",false);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            Subscription active = subscriptions.get(player.getUuid());
            if (active != null) {
                try { send(player, active.match, active.seat, false, ex.getMessage()); return; }
                catch (IllegalArgumentException | IllegalStateException ignored) { subscriptions.remove(player.getUuid()); }
            }
            ServerPlayNetworking.send(player, new MatchStatePayload(false, "", "对局已结束或不可访问，请重新打开卡桌。"));
        } catch (RuntimeException ex) {
            // An unexpected resolver defect must never leave the client waiting forever for a reply.
            LOGGER.error("对局操作结算异常：player={} match={} revision={} option={}",
                player.getUuid(), payload.matchId(), payload.revision(), payload.option(), ex);
            Subscription active = subscriptions.get(player.getUuid());
            if (active != null) {
                if(PtcgServices.tables().computerRunning(active.match)){
                    PtcgServices.tables().endMatch(active.match);broadcast(player.getServer(),active.match,"电脑练习异常终止：规则结算出现异常；临时数据已清理，卡盒已解锁，未判定胜负。",true);return;
                }
                try { send(player, active.match, active.seat, false,
                    "服务器结算失败，已返回当前对局状态；请勿重复提交该操作。"); return; }
                catch (RuntimeException ignored) { subscriptions.remove(player.getUuid()); }
            }
            ServerPlayNetworking.send(player, new MatchStatePayload(false, "", "服务器结算失败，请重新打开卡桌。"));
        }
    }
    private static void broadcast(net.minecraft.server.MinecraftServer server,UUID match,String message,boolean aborted) {
        for(var entry:List.copyOf(subscriptions.entrySet()))if(entry.getValue().match.equals(match)) {
            var player=server.getPlayerManager().getPlayer(entry.getKey());
            if(aborted){subscriptions.remove(entry.getKey());if(player!=null)ServerPlayNetworking.send(player,new MatchStatePayload(false,"",message));}
            else if(player!=null)try{send(player,match,entry.getValue().seat,false,message);}
                catch(IllegalArgumentException|IllegalStateException e){subscriptions.remove(entry.getKey());}
        }
    }
    private static void send(ServerPlayerEntity player, UUID match, UUID seat, boolean open, String message) {
        // Build a fresh, recipient-specific DTO for EVERY recipient. Never reuse the acting player's JSON.
        var snapshot = PtcgServices.tables().matchView(match,player.getUuid(), seat);
        PtcgServices.tables().beginComputerPresentation(match,player.getUuid(),snapshot.revision(),open);
        ServerPlayNetworking.send(player, new MatchStatePayload(open, JSON.toJson(snapshot), message == null ? "操作未完成" : message));
    }
}
