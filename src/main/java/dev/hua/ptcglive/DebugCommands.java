package dev.hua.ptcglive;

import dev.hua.ptcglive.rules.DeckBox;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import java.util.UUID;
import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.server.command.CommandManager.argument;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.command.CommandSource;

/** Administrator-only deck grants and development fixtures. */
public final class DebugCommands {
    private DebugCommands() { }
    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
            dispatcher.register(literal("ptcg").then(literal("debug").requires(source -> source.hasPermissionLevel(2))
                .then(argument("number", IntegerArgumentType.integer(1, 102))
                    .suggests((context, builder) -> CommandSource.suggestMatching(java.util.stream.IntStream.rangeClosed(1, 102)
                        .mapToObj(number -> String.format(java.util.Locale.ROOT, "%03d", number)), builder))
                    .executes(context -> {
                        var player = context.getSource().getPlayerOrThrow();
                        String number = String.format(java.util.Locale.ROOT, "%03d", IntegerArgumentType.getInteger(context, "number"));
                        giveNumberedStarterDeck(player, number);
                        context.getSource().sendFeedback(() -> Text.literal("已发放起始卡组100 " + number + " 号完整卡盒（60 张）。对战前会检查该卡组的效果支持情况。"), false);
                        return 1;
                    }))
                .then(literal("decks").executes(context -> {
                    var player = context.getSource().getPlayerOrThrow();
                    for (String name : new String[] {"测试卡组甲（非官方）", "测试卡组乙（非官方）"}) {
                        DeckBox box = createFixtureBox(player.getUuid(), name);
                        player.giveItemStack(CardBoxItem.referenceTo(box));
                    }
                    context.getSource().sendFeedback(() -> Text.literal("已创建两盒非官方测试卡组。右键卡桌选择两盒开始练习；这些不是中国官方预组，也不代表真实卡牌效果已完成。"), false);
                    return 1;
                })).then(literal("reviewed_decks").executes(context -> {
                    var player = context.getSource().getPlayerOrThrow();
                    for (String name : new String[] {"真实卡验证甲（非官方预组）", "真实卡验证乙（非官方预组）"})
                        player.giveItemStack(CardBoxItem.referenceTo(createReviewedBox(player.getUuid(), name)));
                    context.getSource().sendFeedback(() -> Text.literal("已创建两盒真实卡验证卡组：4 木棉球、4 莉莉艾的决意、52 基本草能量。仅用于验证流程，不是官方预组或推荐竞技卡组。"), false);
                    return 1;
                })).then(literal("starter_deck_100_reset").executes(context -> {
                    var player = context.getSource().getPlayerOrThrow();
                    player.removeCommandTag(StarterDeck100Item.CLAIMED_TAG);
                    player.giveItemStack(new ItemStack(PtcgContent.STARTER_DECK_100_SELECTOR));
                    context.getSource().sendFeedback(() -> Text.literal("已重置并补发“新手随机卡组”。仅管理员测试用途；右键消耗一个，随机创建两套不同卡组。"), false);
                    return 1;
                })))));
    }
    /** Shared with server integration tests; the command checks operator permission and requires a player first. */
    public static DeckBox giveNumberedStarterDeck(net.minecraft.entity.player.PlayerEntity player, String number) {
        if (player.getWorld().isClient) throw new IllegalStateException("预组卡组只能由服务器发放");
        var grant = PtcgServices.starterDeck100().grantNumbered(player.getUuid(), number, player.getWorld().getTime());
        var box = PtcgServices.cardBoxes().getOwned(player.getUuid(), grant.boxId());
        player.giveItemStack(CardBoxItem.referenceTo(box));
        return box;
    }
    public static DeckBox createFixtureBox(UUID owner, String name) {
        var boxes = PtcgServices.cardBoxes();
        DeckBox box = boxes.create(owner, name, "standard-international-2026");
        for (int i = 0; i < 60; i++) {
            String card = i < 4 ? "fixture-attacker-h" : i < 8 ? "fixture-defender-h" : i < 12 ? "fixture-basic-h" : "fixture-energy-h";
            boxes.put(owner, box.id(), boxes.mint(owner, card).instanceId());
        }
        return box;
    }
    public static DeckBox createReviewedBox(UUID owner, String name) {
        var boxes = PtcgServices.cardBoxes();
        DeckBox box = boxes.create(owner, name, "standard-international-2026");
        for (int i = 0; i < 60; i++) {
            String card = i < 4 ? "sv5_en_014" : i < 8 ? "me1_en_119" : "sve_en_001";
            boxes.put(owner, box.id(), boxes.mint(owner, card).instanceId());
        }
        return box;
    }
}
