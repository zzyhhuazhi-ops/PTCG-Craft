package dev.hua.ptcglive.network;

import com.google.gson.Gson;
import dev.hua.ptcglive.CardBoxItem;
import dev.hua.ptcglive.PtcgServices;
import dev.hua.ptcglive.collection.BoxEditorSession;
import dev.hua.ptcglive.collection.PlayerCardHoldings;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.*;

public final class BoxNetworking {
    private record OpenEditor(BoxEditorSession session, long lastTick, boolean heldItemRequired) { }
    private static final Map<UUID, OpenEditor> editors = new HashMap<>();
    private static final Gson JSON = new Gson();
    private BoxNetworking() { }
    public static void initialize() {
        PayloadTypeRegistry.playC2S().register(BoxIntentPayload.ID, BoxIntentPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BoxStatePayload.ID, BoxStatePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(BoxIntentPayload.ID, (payload, context) -> handle(context.player(), payload));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> editors.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> editors.clear());
    }
    public static void open(ServerPlayerEntity player, UUID boxId) {
        requireHeld(player, boxId);
        open(player, boxId, true);
    }
    public static void openFromPhone(ServerPlayerEntity player) {
        var boxes = PlayerCardHoldings.heldBoxes(player, PtcgServices.cardBoxes());
        if (boxes.isEmpty()) throw new IllegalStateException("背包或副手中没有可管理的卡盒");
        open(player, boxes.getFirst().id(), false);
    }
    public static void openFromTable(ServerPlayerEntity player,UUID box){
        PlayerCardHoldings.requireBox(player,PtcgServices.cardBoxes(),box);open(player,box,false);
    }
    private static void open(ServerPlayerEntity player, UUID boxId, boolean heldItemRequired) {
        var editor = PtcgServices.openBoxEditor(player.getUuid(), boxId);
        editor.connectPhysicalReturn(id -> dev.hua.ptcglive.collection.InventoryCardTransfers.withdrawInstance(player,PtcgServices.cardBoxes(),boxId,id));
        editor.connectCreativeMode(player::isCreative);
        editor.connectInventory(() -> dev.hua.ptcglive.collection.InventoryCardTransfers.counts(player),
            id -> dev.hua.ptcglive.collection.InventoryCardTransfers.deposit(player, PtcgServices.cardBoxes(), id, 1, boxId));
        editor.connectTransaction(move -> dev.hua.ptcglive.collection.InventoryCardTransfers.atomic(player, PtcgServices.cardBoxes(), move));
        editors.put(player.getUuid(), new OpenEditor(editor, -100, heldItemRequired));
        send(player, editor.view(player.getUuid()), true, "选择背包或图鉴，分别取卡加入卡盒；未鉴定卡取出回图鉴，已鉴定卡只能取回背包。");
    }
    private static void handle(ServerPlayerEntity player, BoxIntentPayload payload) {
        OpenEditor open = editors.get(player.getUuid());
        // An old screen cannot operate on, close, or receive the newer session.
        if (open == null || !open.session.sessionId().toString().equals(payload.sessionId())) return;
        PhoneNetworking.touch(player);
        if (payload.action().equals("CLOSE")) { editors.remove(player.getUuid()); return; }
        long tick = player.getServer().getTicks();
        if (tick - open.lastTick < 2) return; // client refresh recovers dropped bursts; at most ten requests/second
        editors.put(player.getUuid(), new OpenEditor(open.session, tick, open.heldItemRequired));
        try {
            requireCredential(player, open);
            var snapshot = open.session.submit(player.getUuid(), payload.revision(), payload.action(), payload.argument(), payload.page(), payload.query());
            if (snapshot.deleted()) {
                // Core deletion succeeded only for an empty, unlocked, owned box. Consume its held credential.
                credential(player, open.session.boxId(), open.heldItemRequired).decrement(1);
                editors.remove(player.getUuid());
            }
            send(player, snapshot, false, snapshot.deleted() ? "已删除空卡盒；收藏中的卡牌不受影响。"
                : payload.action().equals("IMPORT_LIVE") ? player.isCreative()
                    ? "创造模式：已导入全部 " + snapshot.totalCards() + " 张实卡。"
                    : "已导入：实卡 " + snapshot.totalCards() + "/60 张；缺卡半透明显示，获得后点击补入。" : "");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            try {
                requireCredential(player, open);
                send(player, open.session.view(player.getUuid()), false, ex.getMessage());
            } catch (IllegalArgumentException | IllegalStateException closed) {
                editors.remove(player.getUuid());
                ServerPlayNetworking.send(player, new BoxStatePayload(false, "", "卡盒已锁定、凭证失效或已不在背包中，编辑界面已关闭。"));
            }
        }
    }
    private static void requireHeld(ServerPlayerEntity player, UUID box) {
        if (held(player, box).isEmpty()) throw new IllegalArgumentException("请手持对应卡盒后右键打开");
    }
    private static void requireCredential(ServerPlayerEntity player, OpenEditor open) {
        if (open.heldItemRequired) requireHeld(player, open.session.boxId());
        else PlayerCardHoldings.requireBox(player, PtcgServices.cardBoxes(), open.session.boxId());
    }
    private static ItemStack credential(ServerPlayerEntity player, UUID box, boolean heldItemRequired) {
        if (heldItemRequired) return held(player, box);
        for (int slot = 0; slot <= 40; slot++) {
            if (slot >= 36 && slot < 40) continue;
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.getItem() instanceof CardBoxItem && box.equals(CardBoxItem.boxId(stack))) return stack;
        }
        return ItemStack.EMPTY;
    }
    private static ItemStack held(ServerPlayerEntity player, UUID box) {
        for (ItemStack stack : List.of(player.getMainHandStack(), player.getOffHandStack()))
            if (stack.getItem() instanceof CardBoxItem && box.equals(CardBoxItem.boxId(stack))) return stack;
        return ItemStack.EMPTY;
    }
    private static void send(ServerPlayerEntity player, BoxEditorSession.Snapshot view, boolean open, String message) {
        ServerPlayNetworking.send(player, new BoxStatePayload(open, JSON.toJson(view), message == null ? "操作未完成" : message));
    }
}
