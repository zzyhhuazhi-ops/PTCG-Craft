package dev.hua.ptcglive.gametest;

import dev.hua.ptcglive.*;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.starter.StarterDeck100Catalog;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.world.GameMode;
import java.util.*;

public final class StarterDeckDebugIntegrationTest implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void ownedBoxesCanBeOpenedRepeatedly(TestContext context) {
        var player=context.createMockCreativeServerPlayerInWorld();player.changeGameMode(GameMode.SURVIVAL);
        var item=PtcgContent.STARTER_DECK_100_SELECTOR;var stack=new net.minecraft.item.ItemStack(item,3);
        player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,stack);
        player.addCommandTag(StarterDeck100Item.CLAIMED_TAG);
        var ids=new HashSet<UUID>();
        try {
            for(int i=0;i<3;i++){
                var result=item.use(context.getWorld(),player,net.minecraft.util.Hand.MAIN_HAND);
                context.assertTrue(stack.getCount()==2-i,"未逐次消耗持有的新手卡组物品");
                context.assertTrue(result.getValue()==player.getMainHandStack(),"使用结果覆盖了新发放到主手的卡盒");
                // Three intentional uses, not repeated packets during the same cooldown.
                for(int tick=0;tick<10;tick++) player.getItemCooldownManager().update();
            }
            for(var held:player.getInventory().main){var id=CardBoxItem.boxId(held);if(id!=null)ids.add(id);}
            context.assertTrue(ids.size()==6,"使用三个物品应生成六个独立卡盒");
            var cards=new HashSet<UUID>();for(var id:ids)for(var card:PtcgServices.cardBoxes().contentsForValidation(player.getUuid(),id))cards.add(card.instanceId());
            context.assertTrue(cards.size()==360,"不同盒共享卡牌实例或缺卡");
        } finally {player.networkHandler.disconnect(net.minecraft.text.Text.literal("测试完成"));}
        context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void numberedDebugDecks(TestContext context) throws Exception {
        var server = context.getWorld().getServer(); var dispatcher = server.getCommandManager().getDispatcher();
        var source = server.getCommandSource().withLevel(2).withSilent();
        var debug = dispatcher.getRoot().getChild("ptcg").getChild("debug");
        context.assertTrue(!debug.canUse(source.withLevel(0)) && debug.canUse(source), "编号发卡没有管理员权限限制");
        for (String number : List.of("001", "053", "066", "102", "53")) {
            var parsed = dispatcher.parse("ptcg debug " + number, source);
            context.assertTrue(parsed.getExceptions().isEmpty() && parsed.getContext().getCommand() != null
                && !parsed.getReader().canRead(), "合法编号无法解析: " + number);
        }
        for (String invalid : List.of("0", "000", "103", "-1", "abc", "53.5")) {
            var parsed = dispatcher.parse("ptcg debug " + invalid, source);
            context.assertTrue(!parsed.getExceptions().isEmpty() || parsed.getContext().getCommand() == null || parsed.getReader().canRead(),
                "无效编号可执行: " + invalid);
        }
        var catalog = StarterDeck100Catalog.loadBundled();
        var player = context.createMockPlayer(GameMode.SURVIVAL);
        var boxes = PtcgServices.cardBoxes();
        for (String number : List.of("053", "066", "001", "102", "021")) {
            var box = DebugCommands.giveNumberedStarterDeck(player, number);
            context.assertTrue(player.getInventory().main.stream().anyMatch(stack -> box.id().equals(CardBoxItem.boxId(stack))), "背包未收到指定卡盒");
            var actual = boxes.contentsForValidation(player.getUuid(), box.id());
            context.assertTrue(actual.size() == 60 && actual.stream().map(CardInstance::definitionId).sorted().toList()
                .equals(catalog.deck(number).expandedCardIds().stream().sorted().toList()), "发放的不是对应完整预组: " + number);
            var validator = new DeckValidator(BundledRules.loadCardDefinitions(), BundledCombatData.load());
            var report = validator.validate(BundledRules.loadCurrentRulesets().get(catalog.rulesetId()), actual);
            context.assertTrue(report.canStartMatch(), "预组开局支持范围错误: " + number);
        }
        context.assertTrue(!player.getCommandTags().contains(StarterDeck100Item.CLAIMED_TAG), "管理员指定发卡错误占用了新玩家随机领取资格");
        int before = PtcgServices.starterDeck100().auditForAdministration().size();
        try { dispatcher.execute("ptcg debug 053", source); throw new AssertionError("控制台没有玩家也能领卡"); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { }
        context.assertTrue(PtcgServices.starterDeck100().auditForAdministration().size() == before, "被拒绝的控制台命令仍生成了卡牌");
        context.complete();
    }
}
