package dev.hua.ptcglive.network;

import com.google.gson.Gson;
import dev.hua.ptcglive.PtcgServices;
import dev.hua.ptcglive.economy.PhoneView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** Owner-bound phone sessions. The client sends intent only; every amount and asset is recomputed here. */
public final class PhoneNetworking {
    private static final long TIMEOUT_TICKS = 12_000;
    private static final String FAVORITE_TAG_PREFIX = "ptcglive_favorite_pack_";
    private static final Gson JSON = new Gson();
    private static final Map<UUID, Session> SESSIONS = new LinkedHashMap<>();
    private PhoneNetworking() { }

    public static void initialize() {
        PayloadTypeRegistry.playC2S().register(PhoneIntentPayload.ID, PhoneIntentPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PhoneStatePayload.ID, PhoneStatePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(PhoneIntentPayload.ID, (payload, context) -> handle(context.player(), payload));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SESSIONS.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SESSIONS.clear());
    }

    public static synchronized void open(ServerPlayerEntity player) {
        var session = new Session(UUID.randomUUID(), player.getServer().getTicks());
        player.getCommandTags().stream().filter(tag -> tag.startsWith(FAVORITE_TAG_PREFIX))
            .map(tag -> tag.substring(FAVORITE_TAG_PREFIX.length())).filter(PtcgServices.packs()::containsKey)
            .forEach(session.favorites::add);
        SESSIONS.put(player.getUuid(), session); send(player, session, true, "");
    }

    private static synchronized void handle(ServerPlayerEntity player, PhoneIntentPayload payload) {
        Session session = SESSIONS.get(player.getUuid());
        if (session == null || !session.id.toString().equals(payload.session())) return;
        if (player.getServer().getTicks() - session.lastActiveAt > TIMEOUT_TICKS) { close(player, "手机会话已超时，请重新打开"); return; }
        session.lastActiveAt = player.getServer().getTicks();
        String message = "";
        try {
            switch (payload.action()) {
                case "CLOSE" -> { close(player, ""); return; }
                case "OPEN_COLLECTION" -> {
                    CollectionNetworking.openFromPhone(player);
                    return;
                }
                case "OPEN_BOX" -> {
                    BoxNetworking.openFromPhone(player);
                    return;
                }
                case "OPEN_RECYCLE" -> {
                    net.minecraft.util.math.BlockPos closest = null;
                    double distance = Double.MAX_VALUE;
                    for (var pos : net.minecraft.util.math.BlockPos.iterateOutwards(player.getBlockPos(), 8, 8, 8)) {
                        if (!RecyclingNetworking.accessible(player, player.getWorld(), pos)) continue;
                        double next = player.squaredDistanceTo(net.minecraft.util.math.Vec3d.ofCenter(pos));
                        if (next < distance) { closest = pos.toImmutable(); distance = next; }
                    }
                    if (closest == null) throw new IllegalArgumentException("请先靠近卡牌回收站（8 格内），再从手机打开。");
                    RecyclingNetworking.open(player, closest);
                    return;
                }
                case "REFRESH" -> { }
                case "TAB" -> { session.tab = allowedTab(payload.argument()); session.page = 0; if(session.tab.equals("SHOP"))session.era=""; }
                case "SHOP_ERA" -> { if(!dev.hua.ptcglive.packs.PackBrowseOrder.CATEGORIES.contains(payload.argument()))throw new IllegalArgumentException("世代无效");session.tab="SHOP";session.era=payload.argument();session.search="";session.page=0; }
                case "SEARCH" -> { session.search = bounded(payload.argument(), 80); session.page = 0; }
                case "ERA" -> { session.era = bounded(payload.argument(), 32); session.page = 0; }
                case "PAGE" -> session.page = Math.max(0, payload.amount());
                case "FAVORITE" -> {
                    String set = requirePack(payload.argument()), tag = FAVORITE_TAG_PREFIX + set;
                    if (session.favorites.remove(set)) player.removeCommandTag(tag);
                    else { session.favorites.add(set); player.addCommandTag(tag); }
                }
                case "CART_ADD" -> {
                    String set = requirePack(payload.argument()); if (payload.amount() < 1) throw new IllegalArgumentException("加入数量必须为正整数");
                    session.cart.merge(set, payload.amount(), Math::addExact); message = "已加入购物车";
                }
                case "CART_REMOVE" -> { session.cart.remove(requirePack(payload.argument())); message = "已从购物车移除"; }
                case "CHECKOUT" -> {
                    var outcome = PtcgServices.economy().purchaseCart(player, request(payload), Map.copyOf(session.cart));
                    session.cart.clear(); message = outcome.messageZh();
                }
                case "EXCHANGE" -> message = PtcgServices.economy().exchange(player, request(payload), payload.argument(), payload.amount()).messageZh();
                default -> throw new IllegalArgumentException("不允许的手机操作");
            }
            send(player, session, false, message);
        } catch (RuntimeException error) {
            send(player, session, false, error.getMessage() == null ? "交易失败，请刷新后重试" : error.getMessage());
        }
    }
    public static synchronized void openShop(ServerPlayerEntity player,String setId) {
        var pack=PtcgServices.packs().get(setId);if(pack==null)return;
        var session=new Session(UUID.randomUUID(),player.getServer().getTicks());
        session.tab="SHOP";session.era=dev.hua.ptcglive.packs.PackBrowseOrder.category(pack);session.search=pack.displayNameZh();
        SESSIONS.put(player.getUuid(),session);send(player,session,true,"");
    }

    private static void send(ServerPlayerEntity player, Session session, boolean open, String message) {
        PhoneView view = view(player, session);
        String json = JSON.toJson(view);
        if (json.length() > 480_000) throw new IllegalStateException("手机数据过大，请缩小筛选范围");
        ServerPlayNetworking.send(player, new PhoneStatePayload(open, json, message));
    }

    private static PhoneView view(ServerPlayerEntity player, Session session) {
        var economy = PtcgServices.economy();
        var eras = PtcgServices.packs().values().stream().map(pack -> pack.identity().era().isBlank() ? (pack.custom() ? "custom" : "other") : pack.identity().era())
            .distinct().sorted().toList();
        var packRows = new ArrayList<PhoneView.PackRow>();
        for (var pack : PtcgServices.packs().values().stream().sorted(dev.hua.ptcglive.packs.PackBrowseOrder.NEWEST).toList()) {
            String era = dev.hua.ptcglive.packs.PackBrowseOrder.category(pack);
            if (!session.era.isBlank() && !session.era.equals(era)) continue;
            String needle = session.search.toLowerCase(Locale.ROOT);
            if (!needle.isBlank() && !pack.displayNameZh().toLowerCase(Locale.ROOT).contains(needle)
                && !pack.setId().toLowerCase(Locale.ROOT).contains(needle)) continue;
            packRows.add(new PhoneView.PackRow(pack.setId(), pack.displayNameZh(), era, economy.config().packPrice(pack.setId()),
                session.favorites.contains(pack.setId()), session.cart.getOrDefault(pack.setId(), 0)));
        }

        session.page = 0;
        List<PhoneView.PackRow> shownPacks = "SHOP".equals(session.tab) ? List.copyOf(packRows) : List.of();

        var minerals = new ArrayList<PhoneView.MineralRow>();
        economy.config().mineralValues().forEach((id, value) -> {
            Item item = Registries.ITEM.get(Identifier.of(id));
            int count = 0; for (int slot : inventorySlots()) if (player.getInventory().getStack(slot).isOf(item)) count += player.getInventory().getStack(slot).getCount();
            minerals.add(new PhoneView.MineralRow(id, new ItemStack(item).getName().getString(), count, value));
        });
        minerals.sort(java.util.Comparator.comparingLong(PhoneView.MineralRow::unitValue));

        var history = new ArrayList<PhoneView.HistoryRow>();
        var allHistory = economy.wallet().history(player.getUuid());
        for (int i = allHistory.size() - 1; i >= Math.max(0, allHistory.size() - 100); i--) {
            var row = allHistory.get(i); history.add(new PhoneView.HistoryRow(row.id().toString(), row.kind().name(), row.delta(), row.balanceAfter(), row.detail(), row.recordedAt().toString()));
        }
        return new PhoneView(session.id.toString(), economy.wallet().balance(player.getUuid()), session.tab, session.search, session.era,
            0, 1, shownPacks, List.copyOf(minerals), List.copyOf(history), Map.copyOf(session.cart),
            List.copyOf(session.favorites), eras);
    }
    private static UUID request(PhoneIntentPayload payload) {
        try { return UUID.fromString(payload.requestId()); } catch (IllegalArgumentException error) { throw new IllegalArgumentException("交易请求 ID 无效"); }
    }
    private static String requirePack(String id) {
        if (!PtcgServices.packs().containsKey(id)) throw new IllegalArgumentException("卡包不存在或已下架"); return id;
    }
    private static String allowedTab(String tab) {
        return switch (tab) { case "HOME", "SHOP", "EXCHANGE", "HISTORY", "BATTLE", "SETTINGS" -> tab; default -> throw new IllegalArgumentException("手机页面无效"); };
    }
    private static String bounded(String value, int max) {
        if (value == null || value.length() > max) throw new IllegalArgumentException("输入内容过长"); return value.trim();
    }
    private static List<Integer> inventorySlots(){var slots=new ArrayList<Integer>();for(int i=0;i<36;i++)slots.add(i);slots.add(40);return slots;}
    private static void close(ServerPlayerEntity player, String message) {
        SESSIONS.remove(player.getUuid()); ServerPlayNetworking.send(player, new PhoneStatePayload(false, "", message));
    }
    static synchronized void touch(ServerPlayerEntity player) {
        Session session = SESSIONS.get(player.getUuid());
        if (session != null) session.lastActiveAt = player.getServer().getTicks();
    }
    private static final class Session {
        final UUID id; long lastActiveAt; String tab = "HOME", search = "", era = ""; int page;
        final Map<String, Integer> cart = new LinkedHashMap<>(); final Set<String> favorites = new LinkedHashSet<>();
        Session(UUID id, long openedAt) { this.id = id; this.lastActiveAt = openedAt; }
    }
}
