package dev.hua.ptcglive;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import dev.hua.ptcglive.table.CardTableKey;
import java.util.*;
import java.util.function.Consumer;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class PtcgMod implements ModInitializer {
    private static final String STARTER_DECK_100_GRANTED_TAG = "ptcglive_starter_deck_100_selector_granted";
    private static final String PHONE_GRANTED_TAG = "ptcglive_phone_granted";
    private static final String FIELD_GUIDE_GRANTED_TAG = "ptcglive_field_guide_granted";
    private static final Map<UUID, Battle> GAMES = new HashMap<>();
    private static UUID waiting;
    @Override public void onInitialize() {
        PtcgServices.initialize();
        RegistryAliases.registerPackMigrations(PtcgServices.packMigration());
        RegistryAliases.registerRetiredTradingMigrations();
        dev.hua.ptcglive.network.PtcgNetworking.initialize();
        PtcgContent.initialize();
        LegacyTutorialCleanup.initialize();
        CardItems.initialize();
        dev.hua.ptcglive.network.CardHistoryNetworking.initialize();
        if (FabricLoader.getInstance().isModLoaded("cobblemon"))
            dev.hua.ptcglive.compat.cobblemon.CobblemonIntegration.initialize();
        DebugCommands.initialize();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            PtcgServices.attachPersistentState(dev.hua.ptcglive.persistence.PtcgPersistentState.get(server.getOverworld()));
            PtcgServices.tables().requireBoxPossession((owner, box) ->
                dev.hua.ptcglive.collection.PlayerCardHoldings.hasBox(server.getPlayerManager().getPlayer(owner), box));
        });
        // Player command tags persist in the world save, including for existing players.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            giveStartingItem(player, FIELD_GUIDE_GRANTED_TAG, new ItemStack(PtcgContent.FIELD_GUIDE),
                "[PTCG Craft] 已获得《玩家手册》。手持右键查阅玩法、物品用途与工作台配方。");
            giveStartingItem(player, STARTER_DECK_100_GRANTED_TAG,
                new ItemStack(PtcgContent.STARTER_DECK_100_SELECTOR),
                "[PTCG Craft] 已获得“新手随机卡组”。手持右键消耗 1 个，随机领取两套不同的完整卡组，每套 60 张。");
            giveStartingItem(player, PHONE_GRANTED_TAG, new ItemStack(PtcgContent.PHONE),
                "[PTCG Craft] 已获得宝可梦手机。右键打开商城、图鉴与卡盒。");
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(literal("ptcg")
                .then(literal("cards").executes(c -> {
                    send(c, "已加载 " + CardItems.cards().size() + " 张独立卡牌。创造模式中打开 PTCG 分类，或使用 /give @s ptcglive:<卡牌编号>。手持卡牌右键查看大图。");
                    return Command.SINGLE_SUCCESS;
                }))
                .then(literal("collection").executes(PtcgMod::collection))
                .then(literal("box")
                    .then(literal("list").executes(PtcgMod::boxList))
                    .then(literal("cards").then(argument("boxId", StringArgumentType.word()).executes(PtcgMod::boxCards)))
                    .then(literal("add").then(argument("boxId", StringArgumentType.word())
                        .then(argument("cardId", StringArgumentType.word()).executes(PtcgMod::boxAdd))))
                    .then(literal("take").then(argument("boxId", StringArgumentType.word())
                        .then(argument("cardId", StringArgumentType.word()).executes(PtcgMod::boxTake))))
                    .then(literal("rename").then(argument("boxId", StringArgumentType.word())
                        .then(argument("name", StringArgumentType.greedyString()).executes(PtcgMod::boxRename))))
                    .then(literal("ruleset").then(argument("boxId", StringArgumentType.word())
                        .then(argument("rulesetId", StringArgumentType.word()).executes(PtcgMod::boxRuleset))))
                    .then(literal("check").then(argument("boxId", StringArgumentType.word())
                        .then(argument("rulesetId", StringArgumentType.word()).executes(PtcgMod::boxCheck))))
                )
                .then(literal("table")
                    .then(literal("open").executes(PtcgMod::tableOpen))
                    .then(literal("join").executes(PtcgMod::tableJoin))
                    .then(literal("spectate").executes(PtcgMod::tableSpectate))
                    .then(literal("leave").executes(PtcgMod::tableLeave))
                    .then(literal("status").executes(PtcgMod::tableStatus))
                    .then(literal("select").then(argument("boxId", StringArgumentType.word()).executes(PtcgMod::tableSelectBox)))
                    .then(literal("ready").executes(c -> tableReady(c, true)))
                    .then(literal("cancel_ready").executes(c -> tableReady(c, false)))
                    .then(literal("ruleset").then(argument("rulesetId", StringArgumentType.word()).executes(PtcgMod::tableRuleset)))
                    .then(literal("start").executes(PtcgMod::tableStart))
                    .then(literal("end").executes(PtcgMod::tableEnd))
                )
                .then(literal("join").executes(PtcgMod::join))
                .then(literal("status").executes(c -> use(c, b -> {
                    Battle.Player me = b.player(c.getSource().getPlayer().getUuid());
                    send(c, "回合玩家: " + b.currentPlayer() + " | 牌库 " + me.deck.size() + " | 奖赏卡 " + me.prizes.size()
                        + " | 战斗宝可梦 " + (me.active == null ? "未选择" : me.active.name() + " HP " + me.remainingHp() + " 能量 " + me.activeEnergy.size())
                        + " | 备战区 " + me.bench.size() + (me.mustPromote ? " | 需要选择替补" : "")
                        + (b.isOver() ? " | 胜者 " + b.winner() : ""));
                })))
                .then(literal("hand").executes(c -> use(c, b -> {
                    Battle.Player me = b.player(c.getSource().getPlayer().getUuid());
                    StringJoiner list = new StringJoiner("、");
                    for (int i = 0; i < me.hand.size(); i++) list.add((i + 1) + ":" + me.hand.get(i).name());
                    send(c, "手牌 " + list);
                })))
                .then(literal("active").then(argument("index", IntegerArgumentType.integer(1))
                    .executes(c -> use(c, b -> b.chooseActive(c.getSource().getPlayer().getUuid(), IntegerArgumentType.getInteger(c, "index") - 1)))))
                .then(literal("bench").then(argument("index", IntegerArgumentType.integer(1))
                    .executes(c -> use(c, b -> b.bench(c.getSource().getPlayer().getUuid(), IntegerArgumentType.getInteger(c, "index") - 1)))))
                .then(literal("promote").then(argument("index", IntegerArgumentType.integer(1))
                    .executes(c -> use(c, b -> b.promote(c.getSource().getPlayer().getUuid(), IntegerArgumentType.getInteger(c, "index") - 1)))))
                .then(literal("benchlist").executes(c -> use(c, b -> {
                    Battle.Player me = b.player(c.getSource().getPlayer().getUuid());
                    StringJoiner list = new StringJoiner("、");
                    for (int i = 0; i < me.bench.size(); i++) list.add((i + 1) + ":" + me.bench.get(i).name());
                    send(c, "备战区 " + list);
                })))
                .then(literal("attach").then(argument("index", IntegerArgumentType.integer(1))
                    .executes(c -> use(c, b -> b.attach(c.getSource().getPlayer().getUuid(), IntegerArgumentType.getInteger(c, "index") - 1)))))
                .then(literal("attack").executes(c -> use(c, b -> b.attack(c.getSource().getPlayer().getUuid()))))
                .then(literal("end").executes(c -> use(c, b -> b.endTurn(c.getSource().getPlayer().getUuid()))))
            ));
    }
    private static void giveStartingItem(ServerPlayerEntity player, String tag, ItemStack stack, String message) {
        if (player.getCommandTags().contains(tag)) return;
        if (!player.giveItemStack(stack) && player.dropItem(stack, false) == null) return;
        player.addCommandTag(tag);
        player.sendMessage(Text.literal(message), false);
    }
    private static int collection(CommandContext<ServerCommandSource> c) {
        try {
            ServerPlayerEntity player = c.getSource().getPlayerOrThrow();
            var cards = PtcgServices.cardBoxes().unboxedForOwner(player.getUuid());
            if (cards.isEmpty()) { send(c, "图鉴中暂无卡牌。手持图鉴右键，可将背包实体卡存入；开包不会额外增加图鉴副本。"); return Command.SINGLE_SUCCESS; }
            StringJoiner list = new StringJoiner("；");
            for (var card : cards.stream().limit(12).toList()) list.add(shortId(card.instanceId()) + "=" + card.definitionId());
            send(c, "未入盒收藏 " + cards.size() + " 张：" + list + (cards.size() > 12 ? "；其余请先放入卡盒或等待图形界面。" : ""));
            return Command.SINGLE_SUCCESS;
        } catch (Exception ex) { send(c, ex.getMessage()); return 0; }
    }
    private static int boxList(CommandContext<ServerCommandSource> c) {
        try {
            ServerPlayerEntity player = c.getSource().getPlayerOrThrow();
            var boxes = dev.hua.ptcglive.collection.PlayerCardHoldings.heldBoxes(player, PtcgServices.cardBoxes());
            if (boxes.isEmpty()) { send(c, "你还没有卡盒。使用 /give @s ptcglive:card_box 后右键创建。 "); return Command.SINGLE_SUCCESS; }
            StringJoiner list = new StringJoiner("；");
            for (var box : boxes) list.add(shortId(box.id()) + "=" + box.nameZh() + "（" + box.contents().size() + "/60，" + box.selectedRulesetId() + "）");
            send(c, "你的卡盒：" + list + "。需要完整 UUID 时使用 /ptcg box cards <短 ID>。");
            return Command.SINGLE_SUCCESS;
        } catch (Exception ex) { send(c, ex.getMessage()); return 0; }
    }
    private static int boxCards(CommandContext<ServerCommandSource> c) {
        try {
            ServerPlayerEntity player = c.getSource().getPlayerOrThrow(); UUID boxId = ownedBoxId(c, player);
            var box = PtcgServices.cardBoxes().getOwned(player.getUuid(), boxId); var cards = PtcgServices.cardBoxes().contentsForEditing(player.getUuid(), boxId);
            StringJoiner list = new StringJoiner("；"); for (var card : cards.stream().limit(20).toList()) list.add(card.instanceId() + "=" + card.definitionId());
            send(c, "卡盒“" + box.nameZh() + "”完整 ID：" + box.id() + "；卡牌 " + cards.size() + " 张：" + (cards.isEmpty() ? "（空）" : list));
            return Command.SINGLE_SUCCESS;
        } catch (Exception ex) { send(c, ex.getMessage()); return 0; }
    }
    private static int boxAdd(CommandContext<ServerCommandSource> c) { return changeBoxCard(c, true); }
    private static int boxTake(CommandContext<ServerCommandSource> c) { return changeBoxCard(c, false); }
    private static int changeBoxCard(CommandContext<ServerCommandSource> c, boolean add) {
        try {
            ServerPlayerEntity player = c.getSource().getPlayerOrThrow(); UUID box = ownedBoxId(c, player); UUID card = UUID.fromString(StringArgumentType.getString(c, "cardId"));
            if (add) PtcgServices.cardBoxes().put(player.getUuid(), box, card); else PtcgServices.cardBoxes().take(player.getUuid(), box, card);
            send(c, add ? "卡牌已放入卡盒，所有权账本已更新。" : "卡牌已取出卡盒，所有权账本已更新。"); return Command.SINGLE_SUCCESS;
        } catch (Exception ex) { send(c, ex.getMessage()); return 0; }
    }
    private static int boxRename(CommandContext<ServerCommandSource> c) {
        try { ServerPlayerEntity p = c.getSource().getPlayerOrThrow(); PtcgServices.cardBoxes().rename(p.getUuid(), ownedBoxId(c, p), StringArgumentType.getString(c, "name")); send(c, "卡盒名称已更新。"); return Command.SINGLE_SUCCESS; }
        catch (Exception ex) { send(c, ex.getMessage()); return 0; }
    }
    private static int boxRuleset(CommandContext<ServerCommandSource> c) {
        try { ServerPlayerEntity p = c.getSource().getPlayerOrThrow(); String ruleset = StringArgumentType.getString(c, "rulesetId"); if (!PtcgServices.rulesets().containsKey(ruleset)) throw new IllegalArgumentException("未安装的规则包: " + ruleset); PtcgServices.cardBoxes().selectRuleset(p.getUuid(), ownedBoxId(c, p), ruleset); send(c, "卡盒规则包已更新。"); return Command.SINGLE_SUCCESS; }
        catch (Exception ex) { send(c, ex.getMessage()); return 0; }
    }
    private static int boxCheck(CommandContext<ServerCommandSource> c) {
        try {
            ServerPlayerEntity p = c.getSource().getPlayerOrThrow(); UUID box = ownedBoxId(c, p); String ruleset = StringArgumentType.getString(c, "rulesetId"); var report = PtcgServices.validateBox(p.getUuid(), box, ruleset);
            if (report.issues().isEmpty()) send(c, "可用：此卡盒可在“" + PtcgServices.rulesets().get(ruleset).displayNameZh() + "”开局。");
            else { StringJoiner issues = new StringJoiner("；"); report.issues().forEach(issue -> issues.add("[" + issue.severity() + "] " + issue.messageZh())); send(c, report.statusZh() + "：" + issues); }
            return Command.SINGLE_SUCCESS;
        } catch (Exception ex) { send(c, ex.getMessage()); return 0; }
    }
    private static int tableOpen(CommandContext<ServerCommandSource> c) {
        try {
            ServerPlayerEntity player = c.getSource().getPlayerOrThrow(); CardTableKey key = tableKey(player);
            var table = PtcgServices.tables().open(key, player.getUuid(), "standard-international-2026");
            if (table.isHost(player.getUuid())) send(c, "已创建卡桌大厅：国际 Standard（2026 赛季）。使用 /ptcg table select <卡盒短 ID>、/ptcg table ready。 ");
            else send(c, "此卡桌已有大厅。使用 /ptcg table join 加入对局，或 /ptcg table spectate 旁观公开信息。");
            return Command.SINGLE_SUCCESS;
        } catch (Exception ex) { send(c, ex.getMessage()); return 0; }
    }
    private static int tableJoin(CommandContext<ServerCommandSource> c) {
        try { ServerPlayerEntity p = c.getSource().getPlayerOrThrow(); PtcgServices.tables().join(tableKey(p), p.getUuid()); send(c, "已加入对局席位。选择卡盒并准备后，房主可开始对局。"); return Command.SINGLE_SUCCESS; }
        catch (Exception ex) { send(c, ex.getMessage()); return 0; }
    }
    private static int tableSpectate(CommandContext<ServerCommandSource> c) {
        try { ServerPlayerEntity p = c.getSource().getPlayerOrThrow(); PtcgServices.tables().spectate(tableKey(p), p.getUuid()); send(c, "已登记为旁观者。旁观者只能接收公开信息，不能读取手牌、牌库和奖赏卡。 "); return Command.SINGLE_SUCCESS; }
        catch (Exception ex) { send(c, ex.getMessage()); return 0; }
    }
    private static int tableLeave(CommandContext<ServerCommandSource> c) {
        try { ServerPlayerEntity p = c.getSource().getPlayerOrThrow(); PtcgServices.tables().leave(tableKey(p), p.getUuid()); send(c, "已离开卡桌大厅。 "); return Command.SINGLE_SUCCESS; }
        catch (Exception ex) { send(c, ex.getMessage()); return 0; }
    }
    private static int tableStatus(CommandContext<ServerCommandSource> c) {
        try {
            ServerPlayerEntity p = c.getSource().getPlayerOrThrow(); var view = PtcgServices.tables().publicTableView(tableKey(p));
            String opponent = view.guest() == null ? "等待对手" : "已有对手"; String role = p.getUuid().equals(view.host()) ? "房主" : p.getUuid().equals(view.guest()) ? "对手" : "旁观者／访客";
            send(c, "卡桌状态：" + view.status().labelZh() + "，你的身份：" + role + "，规则包：" + view.rulesetId() + "，" + opponent + "，准备：房主=" + (view.hostReady() ? "是" : "否") + "、对手=" + (view.guestReady() ? "是" : "否") + "，旁观者=" + view.spectatorCount() + "。 ");
            return Command.SINGLE_SUCCESS;
        } catch (Exception ex) { send(c, ex.getMessage()); return 0; }
    }
    private static int tableSelectBox(CommandContext<ServerCommandSource> c) {
        try { ServerPlayerEntity p = c.getSource().getPlayerOrThrow(); PtcgServices.tables().selectBox(tableKey(p), p.getUuid(), ownedBoxId(c, p)); send(c, "已选择并通过服务器卡组检查；卡组变更后须重新准备。 "); return Command.SINGLE_SUCCESS; }
        catch (Exception ex) { send(c, ex.getMessage()); return 0; }
    }
    private static int tableReady(CommandContext<ServerCommandSource> c, boolean ready) {
        try { ServerPlayerEntity p = c.getSource().getPlayerOrThrow(); PtcgServices.tables().setReady(tableKey(p), p.getUuid(), ready); send(c, ready ? "已准备；服务器会在开局时重新检查卡盒。" : "已取消准备。"); return Command.SINGLE_SUCCESS; }
        catch (Exception ex) { send(c, ex.getMessage()); return 0; }
    }
    private static int tableRuleset(CommandContext<ServerCommandSource> c) {
        try { ServerPlayerEntity p = c.getSource().getPlayerOrThrow(); String id = StringArgumentType.getString(c, "rulesetId"); PtcgServices.tables().chooseRuleset(tableKey(p), p.getUuid(), id); send(c, "卡桌规则包已更新；双方准备状态已取消。 "); return Command.SINGLE_SUCCESS; }
        catch (Exception ex) { send(c, ex.getMessage()); return 0; }
    }
    private static int tableStart(CommandContext<ServerCommandSource> c) {
        try { ServerPlayerEntity p = c.getSource().getPlayerOrThrow(); var match = PtcgServices.tables().start(tableKey(p), p.getUuid()); send(c, "对局已由服务器创建，卡盒已锁定。当前阶段：" + match.phase() + "。图形对局面板和意图包将在后续版本接入；当前禁止从此接口读取隐藏区域。 "); return Command.SINGLE_SUCCESS; }
        catch (Exception ex) { send(c, ex.getMessage()); return 0; }
    }
    private static int tableEnd(CommandContext<ServerCommandSource> c) {
        try { ServerPlayerEntity p = c.getSource().getPlayerOrThrow(); PtcgServices.tables().endTable(tableKey(p), p.getUuid()); send(c, "卡桌已结束；涉及的卡盒已安全解除锁定。 "); return Command.SINGLE_SUCCESS; }
        catch (Exception ex) { send(c, ex.getMessage()); return 0; }
    }
    private static CardTableKey tableKey(ServerPlayerEntity player) {
        BlockPos position = player.getBlockPos();
        if (!player.getServerWorld().getBlockState(position).isOf(PtcgContent.CARD_TABLE)) position = position.down();
        if (!player.getServerWorld().getBlockState(position).isOf(PtcgContent.CARD_TABLE)) throw new IllegalStateException("请站在宝可梦卡牌对战桌上执行此命令");
        return new CardTableKey(player.getServerWorld().getRegistryKey().getValue().toString(), position.asLong());
    }
    private static UUID ownedBoxId(CommandContext<ServerCommandSource> c, ServerPlayerEntity player) {
        String requested = StringArgumentType.getString(c, "boxId"); var boxes = dev.hua.ptcglive.collection.PlayerCardHoldings.heldBoxes(player, PtcgServices.cardBoxes());
        return boxes.stream().map(dev.hua.ptcglive.rules.DeckBox::id).filter(id -> id.toString().equals(requested) || shortId(id).equals(requested)).findFirst().orElseThrow(() -> new IllegalArgumentException("找不到你的卡盒 ID: " + requested));
    }
    private static String shortId(UUID id) { return id.toString().substring(0, 8); }
    private static void send(CommandContext<ServerCommandSource> c, String message) {
        c.getSource().sendFeedback(() -> Text.literal("[PTCG] " + message), false);
    }
    private static int join(CommandContext<ServerCommandSource> c) {
        try {
            ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
            UUID id = p.getUuid();
            if (GAMES.containsKey(id)) { send(c, "你已在对战中；使用 /ptcg hand 和 /ptcg status"); return Command.SINGLE_SUCCESS; }
            if (waiting == null || waiting.equals(id) || c.getSource().getServer().getPlayerManager().getPlayer(waiting) == null) {
                waiting = id; send(c, "已等待另一名玩家输入 /ptcg join"); return Command.SINGLE_SUCCESS;
            }
            Battle b = new Battle(waiting, id, Battle.sampleDeck(), Battle.sampleDeck(), new Random());
            GAMES.put(waiting, b); GAMES.put(id, b);
            ServerPlayerEntity first = c.getSource().getServer().getPlayerManager().getPlayer(waiting);
            waiting = null;
            if (first != null) first.sendMessage(Text.literal("[PTCG] 对战开始！输入 /ptcg hand 查看手牌，/ptcg active <序号> 选择战斗宝可梦。"), false);
            send(c, "对战开始！输入 /ptcg hand 查看手牌，/ptcg active <序号> 选择战斗宝可梦。");
            return Command.SINGLE_SUCCESS;
        } catch (Exception ex) { send(c, ex.getMessage()); return 0; }
    }
    private static int use(CommandContext<ServerCommandSource> c, Consumer<Battle> action) {
        try {
            UUID id = c.getSource().getPlayerOrThrow().getUuid();
            Battle b = GAMES.get(id);
            if (b == null) throw new IllegalStateException("请先输入 /ptcg join");
            action.accept(b); send(c, "完成。使用 /ptcg status 查看状态。");
            return Command.SINGLE_SUCCESS;
        } catch (Exception ex) { send(c, ex.getMessage()); return 0; }
    }
}
