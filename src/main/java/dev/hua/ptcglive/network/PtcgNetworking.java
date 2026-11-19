package dev.hua.ptcglive.network;

import com.google.gson.Gson;
import com.mojang.authlib.GameProfile;
import dev.hua.ptcglive.PtcgContent;
import dev.hua.ptcglive.PtcgServices;
import dev.hua.ptcglive.table.CardTableKey;
import dev.hua.ptcglive.table.TableLobbyStatus;
import dev.hua.ptcglive.table.TableLobbyView;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.UUID;

/**
 * Common, server-authoritative networking for the interactive table lobby.  Every
 * reply is a recipient-specific {@link TableLobbyView.Lobby}; the client only ever
 * sends an action and one bounded identifier.
 */
public final class PtcgNetworking {
    private static final Gson JSON = new Gson();
    /** A new table opened from the block starts with this ruleset; the host can change it in the lobby settings. */
    private static final String DEFAULT_RULESET = "standard-international-2026";
    private PtcgNetworking() { }
    public static void initialize() {
        MatchNetworking.initialize();
        BoxNetworking.initialize();
        CollectionNetworking.initialize();
        PackNetworking.initialize();
        PhoneNetworking.initialize();
        RecyclingNetworking.initialize();
        dev.hua.ptcglive.rental.RentalNetworking.initialize();
        BattleSocialNetworking.initialize();
        PayloadTypeRegistry.playC2S().register(TableIntentPayload.ID, TableIntentPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TableLobbyPayload.ID, TableLobbyPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TableHistoryPayload.ID, TableHistoryPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AiPracticeState.ID, AiPracticeState.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TableIntentPayload.ID, (payload, context) -> handle(context.player(), payload));
    }
    private static void handle(ServerPlayerEntity player, TableIntentPayload payload) {
        CardTableKey key = null;
        try {
            key = new CardTableKey(player.getServerWorld().getRegistryKey().getValue().toString(), payload.tablePos().asLong());
            if(dev.hua.ptcglive.duel.DuelLetters.virtualPosition(payload.tablePos())){
                if(!dev.hua.ptcglive.duel.DuelLetters.accessible(player,key))throw new IllegalArgumentException("临时卡桌已关闭或不属于你");
                if(!java.util.Set.of("OPEN","STATUS","REFRESH","JOIN","LEAVE","RULESET","SELECT_BOX","READY","CANCEL_READY","START","END","HISTORY","MATCH").contains(payload.action()))
                    throw new IllegalArgumentException("战书临时卡桌仅用于双方决斗；其他功能请使用实体卡桌。");
                if(payload.action().equals("LEAVE")||payload.action().equals("END")){
                    dev.hua.ptcglive.duel.DuelLetters.close(player.getServer(),key);
                    sendLobby(player,payload.tablePos(),key,true,"已离开临时卡桌");return;
                }
            }else{
                if (!player.getServerWorld().getBlockState(payload.tablePos()).isOf(PtcgContent.CARD_TABLE)) throw new IllegalArgumentException("该位置不是宝可梦卡牌对战桌");
                if (player.squaredDistanceTo(Vec3d.ofCenter(payload.tablePos())) > 64) throw new IllegalArgumentException("请靠近卡桌后操作");
            }
            UUID id = player.getUuid();
            var tables = PtcgServices.tables();
            BattleSocialNetworking.bind(player.getServer());
            String message = switch (payload.action()) {
                // OPEN creates the table (the opener becomes host) when none is active; REFRESH only reads.
                case "OPEN", "STATUS" -> { tables.open(key, id, DEFAULT_RULESET); yield ""; }
                case "REFRESH" -> "";
                case "JOIN" -> { tables.join(key, id); yield "已入座，请选择卡组并准备"; }
                case "SPECTATE" -> { tables.spectate(key, id); yield "已登记观战"; }
                case "LEAVE" -> { tables.leave(key, id); yield "已离开"; }
                case "RULESET" -> { tables.chooseRuleset(key, id, payload.argument()); yield "规则包已更换，双方需要重新准备"; }
                case "SELECT_BOX" -> { tables.selectBox(key, id, UUID.fromString(payload.argument())); yield "已选择卡组"; }
                case "READY" -> { tables.setReady(key, id, true); yield "已准备"; }
                case "CANCEL_READY" -> { tables.setReady(key, id, false); yield "已取消准备"; }
                case "CONNECT_SCREENS" -> {
                    int count=dev.hua.ptcglive.spectator.ScreenNetworking.connectNearby(player,payload.tablePos());
                    yield count==0?"30 格内没有完整且可管理的屏幕":"已连接 "+count+" 块屏幕，开局后自动播放公开对战画面";
                }
                case "START" -> {
                    var seated = tables.table(key).orElseThrow();
                    UUID host = seated.host(), guest = seated.guest().orElse(null);
                    var match = tables.start(key, id);
                    // Both players are taken straight to the match; spectators use “观看对局”.
                    for (UUID seat : new UUID[]{host, guest}) {
                        if (seat == null) continue;
                        ServerPlayerEntity online = player.getServer().getPlayerManager().getPlayer(seat);
                        if (online != null) MatchNetworking.open(online, match.id());
                    }
                    yield "对局开始";
                }
                case "END" -> { tables.endTable(key, id); yield "本桌已结束"; }
                case "HISTORY" -> {
                    var history = TableLobbyView.history(tables.completedHistoryFor(id), id, uuid -> nameOf(player.getServer(), uuid));
                    ServerPlayNetworking.send(player, new TableHistoryPayload(payload.tablePos(), JSON.toJson(history)));
                    yield null;
                }
                case "MATCH" -> {
                    MatchNetworking.open(player, tables.table(key).orElseThrow()
                        .match().orElseThrow(() -> new IllegalStateException("卡桌尚未开始对局")).id());
                    yield null;
                }
                case "RESUME_PRACTICE" -> { MatchNetworking.open(player, tables.latestPractice(id)); yield null; }
                case "AI_MENU" -> { sendComputer(player,payload,null,"分别选择我的卡组与 AI 的卡组；编辑后重新检查。");yield null; }
                case "AI_EDIT" -> { BoxNetworking.openFromTable(player,UUID.fromString(payload.argument()));yield null; }
                case "AI_CHECK", "AI_START" -> {
                    String[] choice=payload.argument().split(",",-1);
                    if(choice.length!=5)throw new IllegalArgumentException("请选择双方卡盒、规则包和 AI 难度");
                    var difficulty=dev.hua.ptcglive.ai.AiDifficulty.parse(choice[4]);
                    if(payload.action().equals("AI_START")) {
                        MatchNetworking.open(player,tables.startComputer(key,id,UUID.fromString(choice[0]),UUID.fromString(choice[1]),choice[2],choice[3],difficulty));
                    }else {var check=tables.checkComputer(key,id,UUID.fromString(choice[0]),UUID.fromString(choice[1]),choice[2],difficulty);
                        sendComputer(player,payload,check,check.allowed()?check.analysis().warnings().isEmpty()?"检查通过，可以开始练习。":"允许练习；部分效果策略评价较弱。":"尚不能开始：请查看下方具体卡牌和步骤。");}
                    yield null;
                }
                case "AI_AGAIN", "AI_CHANGE", "AI_LEAVE" -> {
                    UUID old=UUID.fromString(payload.argument());
                    if(!key.equals(tables.computerTable(old,id)))throw new IllegalArgumentException("请回到原来的卡桌");
                    if(payload.action().equals("AI_AGAIN"))MatchNetworking.open(player,tables.rematchComputer(old,id));
                    else {
                        if(tables.computerRunning(old))throw new IllegalStateException("请先结束当前对局");
                        tables.endMatch(old);
                        if(payload.action().equals("AI_CHANGE")){tables.open(key,id,DEFAULT_RULESET);sendComputer(player,payload,null,"请重新选择双方卡盒。");}
                    }yield null;
                }
                case "PRACTICE" -> {
                    var lobby = tables.table(key).orElseThrow();
                    if (!lobby.isHost(id) || lobby.guest().isPresent() || lobby.status() != TableLobbyStatus.WAITING)
                        throw new IllegalStateException("请在自己创建、尚无对手的空闲卡桌中开始练习");
                    String[] boxes = payload.argument().split(",", -1);
                    if (boxes.length != 2) throw new IllegalArgumentException("请选择两个不同卡盒");
                    var practice = tables.startPractice(id, UUID.fromString(boxes[0]), UUID.fromString(boxes[1]), lobby.rulesetId());
                    MatchNetworking.open(player, practice.matchId());
                    yield null;
                }
                default -> throw new IllegalArgumentException("不允许的卡桌操作");
            };
            // Actions that opened another screen do not answer with a lobby view.
            if (message != null) sendLobby(player, payload.tablePos(), key, true, message);
        } catch (Exception ex) {
            String reason = ex.getMessage() == null ? "卡桌操作失败" : ex.getMessage();
            if(payload.action().startsWith("AI_")){
                if(payload.action().equals("AI_AGAIN")||payload.action().equals("AI_LEAVE"))ServerPlayNetworking.send(player,new BattleSocialState(false,"",reason));
                else sendComputer(player,payload,null,reason);
                return;
            }
            if (key == null || dev.hua.ptcglive.duel.DuelLetters.virtualPosition(payload.tablePos())&&!dev.hua.ptcglive.duel.DuelLetters.accessible(player,key))
                ServerPlayNetworking.send(player, new TableLobbyPayload(payload.tablePos(), false, reason, ""));
            else sendLobby(player, payload.tablePos(), key, false, reason);
        }
    }
    private static void sendComputer(ServerPlayerEntity player,TableIntentPayload payload,dev.hua.ptcglive.ai.AiConfiguration check,String message) {
        var names=new java.util.LinkedHashMap<String,String>();var rules=new java.util.LinkedHashMap<String,String>();
        PtcgServices.rulesets().values().stream().sorted(java.util.Comparator.comparingInt(r->dev.hua.ptcglive.rules.CurrentFormats.order(r.id())))
            .forEach(r->rules.put(r.id(),r.displayNameZh()));
        var boxes=dev.hua.ptcglive.collection.PlayerCardHoldings.heldBoxes(player,PtcgServices.cardBoxes()).stream()
            .map(b->{var counts=new java.util.TreeMap<String,Integer>();PtcgServices.cardBoxes().contentsForValidation(player.getUuid(),b.id()).forEach(c->counts.merge(c.definitionId(),1,Integer::sum));
                counts.keySet().forEach(card->names.put(card,PtcgServices.cardDatabase().find(card).map(dev.hua.ptcglive.rules.CardDefinition::nameZh).orElse(card)));
                return new AiPracticeState.Box(b.id().toString(),b.nameZh(),b.contents().size(),b.selectedRulesetId(),counts);}).toList();
        var key=new CardTableKey(player.getServerWorld().getRegistryKey().getValue().toString(),payload.tablePos().asLong());
        var defaultRule=PtcgServices.tables().table(key).map(l->l.rulesetId()).orElse(DEFAULT_RULESET);
        var view=new AiPracticeState.View(boxes,names,rules,defaultRule,check);
        ServerPlayNetworking.send(player,new AiPracticeState(payload.tablePos(),JSON.toJson(view),message));
    }
    public static void sendLobby(ServerPlayerEntity player, BlockPos pos, CardTableKey key, boolean success, String message) {
        UUID id = player.getUuid();
        var tables = PtcgServices.tables();
        var lobby = TableLobbyView.of(tables.table(key).orElse(null), id, PtcgServices.rulesets(), uuid -> nameOf(player.getServer(), uuid),
            dev.hua.ptcglive.collection.PlayerCardHoldings.heldBoxes(player, PtcgServices.cardBoxes()), (box, ruleset) -> PtcgServices.validateBox(id, box, ruleset),
            tables.recoveryNoticeFor(id));
        ServerPlayNetworking.send(player, new TableLobbyPayload(pos, success, message, JSON.toJson(lobby)));
    }
    /** Online name first, then the server's profile cache, then a short id for unknown players. */
    private static String nameOf(MinecraftServer server, UUID id) {
        if (id == null) return "未知玩家";
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(id);
        if (online != null) return online.getGameProfile().getName();
        var cache = server.getUserCache();
        return cache == null ? id.toString().substring(0, 8) : cache.getByUuid(id).map(GameProfile::getName).orElse(id.toString().substring(0, 8));
    }
}
