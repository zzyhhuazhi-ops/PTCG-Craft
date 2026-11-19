package dev.hua.ptcglive.gametest;

import com.google.gson.Gson;
import dev.hua.ptcglive.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.network.*;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.table.*;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Runs inside the actual headless Minecraft 1.21.1 Fabric GameTest server, not a plain JVM. */
public final class ServerIntegrationTest implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void newbieDeckPairRedemption(TestContext context) {
        var player=context.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(PtcgContent.STARTER_DECK_100_SELECTOR,3));
        var pair=StarterDeck100Item.redeem(player,Hand.MAIN_HAND);
        context.assertTrue(pair.size()==2 && !pair.get(0).nameZh().equals(pair.get(1).nameZh()),"新手卡组必须两套不同");
        context.assertTrue(player.getMainHandStack().getCount()==2,"必须只消耗一个物品");
        var ids=new HashSet<UUID>();
        for(var box:pair) {
            var cards=PtcgServices.cardBoxes().contentsForValidation(player.getUuid(),box.id());
            context.assertTrue(cards.size()==60,"卡组必须完整60张");
            cards.forEach(card -> context.assertTrue(ids.add(card.instanceId()),"卡组间共享了卡牌实例"));
            boolean found=false;
            for(int slot=0;slot<36;slot++) found |= box.id().equals(CardBoxItem.boxId(player.getInventory().getStack(slot)));
            context.assertTrue(found,"背包缺少卡盒凭证");
        }
        try { StarterDeck100Item.redeem(player,Hand.MAIN_HAND); throw new AssertionError("重复使用未被冷却拦截"); }
        catch(IllegalStateException expected) { }
        context.assertTrue(player.getMainHandStack().getCount()==2,"拦截重复使用时消耗了物品");
        context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void newbieDeckFullInventoryRollback(TestContext context) {
        var player=context.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        for(int slot=0;slot<36;slot++) player.getInventory().setStack(slot,new ItemStack(net.minecraft.item.Items.COBBLESTONE,64));
        // Consuming the token frees just one slot: do not issue a partial pair.
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(PtcgContent.STARTER_DECK_100_SELECTOR));
        var before=PtcgServices.cardBoxes().snapshot();
        try { StarterDeck100Item.redeem(player,Hand.MAIN_HAND); throw new AssertionError("背包不足却成功发放"); }
        catch(IllegalStateException expected) { }
        var after=PtcgServices.cardBoxes().snapshot();
        context.assertTrue(after.cards().size()==before.cards().size() && after.boxes().size()==before.boxes().size(),"失败后遗留卡牌或卡盒");
        context.assertTrue(player.getMainHandStack().isOf(PtcgContent.STARTER_DECK_100_SELECTOR) && player.getMainHandStack().getCount()==1,"失败后物品未返还");
        for(int slot=0;slot<36;slot++) if(slot!=player.getInventory().selectedSlot)
            context.assertTrue(player.getInventory().getStack(slot).isOf(net.minecraft.item.Items.COBBLESTONE) && player.getInventory().getStack(slot).getCount()==64,"失败后背包未恢复");
        context.assertTrue(!player.getItemCooldownManager().isCoolingDown(PtcgContent.STARTER_DECK_100_SELECTOR),"失败后不应冷却");
        context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void newbieDeckOffhandCreativeAndLegacyId(TestContext context) {
        var player=context.createMockPlayer(net.minecraft.world.GameMode.CREATIVE);
        var legacy=Registries.ITEM.get(Identifier.of("ptcglive","starter_deck_100_selector"));
        player.setStackInHand(Hand.OFF_HAND,new ItemStack(legacy,2));
        var pair=StarterDeck100Item.redeem(player,Hand.OFF_HAND);
        context.assertTrue(pair.size()==2 && player.getOffHandStack().getCount()==1,"旧物品ID/创造副手发放错误");
        context.assertTrue(pair.stream().allMatch(box->box.selectedRulesetId().equals(CurrentFormats.OPEN)),"新手卡组误用预组100赛制");
        context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void newbieDeckLastTokenReusesHandSlot(TestContext context) {
        var player=context.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        for(int slot=0;slot<36;slot++) player.getInventory().setStack(slot,new ItemStack(net.minecraft.item.Items.COBBLESTONE,64));
        player.getInventory().setStack(8,ItemStack.EMPTY);
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(PtcgContent.STARTER_DECK_100_SELECTOR));
        var pair=StarterDeck100Item.redeem(player,Hand.MAIN_HAND);
        var first=CardBoxItem.boxId(player.getMainHandStack());
        var second=CardBoxItem.boxId(player.getInventory().getStack(8));
        context.assertTrue(first!=null && second!=null && !first.equals(second),"最后一个物品腾出的主手格应放入卡盒");
        context.assertTrue(pair.stream().allMatch(b->b.id().equals(first)||b.id().equals(second)),"两个发放卡盒凭证不匹配");
        context.complete();
    }
    private static final String RULE = "standard-international-2026";    @GameTest(templateName = EMPTY_STRUCTURE)
    public void requestedCraftingRecipes(TestContext context) {
        var world = context.getWorld();
        for (var id : List.of("card_table", "card_display_case", "card_box"))
            context.assertTrue(world.getServer().getAdvancementLoader().get(Identifier.of("ptcglive", "recipes/" + id)) != null, "配方书解锁配置未被服务器接受：" + id);
        var phone = assertCraft(context, "phone", PtcgContent.PHONE,
            net.minecraft.item.Items.COPPER_INGOT, net.minecraft.item.Items.GLASS_PANE, net.minecraft.item.Items.COPPER_INGOT,
            net.minecraft.item.Items.GLASS_PANE, net.minecraft.item.Items.REDSTONE, net.minecraft.item.Items.GLASS_PANE,
            net.minecraft.item.Items.COPPER_INGOT, net.minecraft.item.Items.COPPER_INGOT, net.minecraft.item.Items.COPPER_INGOT);
        context.assertTrue(phone.getMaxCount() == 1, "手机应为单件道具");
        context.assertTrue(world.getRecipeManager().get(Identifier.of("ptcglive", "merchant_token")).isEmpty(), "商人之证配方仍存在");
        context.assertTrue(!Registries.ITEM.containsId(Identifier.of("ptcglive", "merchant_token")), "商人之证仍注册为物品");
        context.complete();
    }
    private static net.minecraft.recipe.input.CraftingRecipeInput craftingInput(net.minecraft.item.Item... items) {
        return net.minecraft.recipe.input.CraftingRecipeInput.create(3, 3, Arrays.stream(items).map(i -> i == null ? ItemStack.EMPTY : new ItemStack(i)).toList());
    }
    private static ItemStack assertCraft(TestContext context, String id, net.minecraft.item.Item expected, net.minecraft.item.Item... items) {
        var world = context.getWorld(); var input = craftingInput(items);
        var match = world.getRecipeManager().getFirstMatch(net.minecraft.recipe.RecipeType.CRAFTING, input, world).orElseThrow();
        context.assertTrue(match.id().equals(Identifier.of("ptcglive", id)), "配方未匹配指定物品：" + id);
        var result = match.value().craft(input, world.getRegistryManager());
        context.assertTrue(result.isOf(expected) && result.getCount() == 1, "合成结果或数量错误：" + id);
        context.assertTrue(match.value().getRemainder(input).stream().allMatch(ItemStack::isEmpty), "材料产生了意外返还：" + id);
        return result;
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void physicalCardTable(TestContext context) {
        var item = PtcgContent.CARD_TABLE.asItem();
        context.assertTrue(item instanceof net.minecraft.item.BlockItem, "卡桌不是背包方块物品");
        var pos = new BlockPos(1,1,1);
        context.setBlockState(pos.down(), net.minecraft.block.Blocks.STONE.getDefaultState());
        var player = context.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(item));
        var absolute = context.getAbsolutePos(pos);
        var hit = new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofBottomCenter(absolute),
            net.minecraft.util.math.Direction.UP, absolute.down(), false);
        var placed = item.useOnBlock(new net.minecraft.item.ItemUsageContext(player, Hand.MAIN_HAND, hit));
        context.assertTrue(placed.isAccepted() && player.getMainHandStack().isEmpty(), "背包卡桌不能通过放置动作使用");
        var state = context.getBlockState(pos);
        context.assertTrue(state.isOf(PtcgContent.CARD_TABLE), "放置后不是卡桌");
        state.onUse(context.getWorld(), player, new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(absolute),
            net.minecraft.util.math.Direction.UP, absolute, false));
        var key = new CardTableKey(context.getWorld().getRegistryKey().getValue().toString(), absolute.asLong());
        context.assertTrue(PtcgServices.tables().table(key).isPresent(), "右键实体卡桌未创建独立桌实例");
        context.assertTrue(!state.isFullCube(context.getWorld(), context.getAbsolutePos(pos)), "卡桌仍是完整实心方块");
        var drops = net.minecraft.block.Block.getDroppedStacks(state, context.getWorld(), context.getAbsolutePos(pos), null);
        context.assertTrue(drops.size() == 1 && drops.getFirst().isOf(item), "卡桌无法掉落回背包");
        context.assertTrue(!Registries.POINT_OF_INTEREST_TYPE.containsId(Identifier.of("ptcglive", "card_pack_merchant_station")),
            "旧卡包商人工作站仍在注册表中");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void cardDisplayCaseStoresSwapsRemovesAndDropsCard(TestContext context) {
        var player = context.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        var local = new BlockPos(1, 1, 1); var absolute = context.getAbsolutePos(local);
        context.setBlockState(local.down(), net.minecraft.block.Blocks.STONE.getDefaultState());
        var cabinetItem = PtcgContent.CARD_DISPLAY_CASE.asItem();
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(cabinetItem));
        var placementHit = new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofBottomCenter(absolute),
            net.minecraft.util.math.Direction.UP, absolute.down(), false);
        context.assertTrue(cabinetItem.useOnBlock(new net.minecraft.item.ItemUsageContext(player, Hand.MAIN_HAND, placementHit)).isAccepted(),
            "展示柜物品无法放置");
        var lower = context.getBlockState(local); var upper = context.getBlockState(local.up());
        context.assertTrue(lower.isOf(PtcgContent.CARD_DISPLAY_CASE) && upper.isOf(PtcgContent.CARD_DISPLAY_CASE), "展示柜没有占用两格");
        context.assertTrue(lower.get(CardDisplayCaseBlock.HALF) == net.minecraft.block.enums.DoubleBlockHalf.LOWER
            && upper.get(CardDisplayCaseBlock.HALF) == net.minecraft.block.enums.DoubleBlockHalf.UPPER, "展示柜上下半格状态错误");
        var first = CardItems.cards().get(0); var second = CardItems.cards().get(1);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(first));
        var upperHit = new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(absolute.up()),
            net.minecraft.util.math.Direction.NORTH, absolute.up(), false);
        context.assertTrue(upper.onUseWithItem(player.getMainHandStack(), context.getWorld(), player, Hand.MAIN_HAND, upperHit).isAccepted(),
            "持卡右键没有放入展示柜");
        var entity = (CardDisplayCaseBlockEntity) context.getWorld().getBlockEntity(absolute);
        context.assertTrue(entity != null && entity.displayedCard().isOf(first), "展示柜没有保存第一张卡");
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(second));
        upper.onUseWithItem(player.getMainHandStack(), context.getWorld(), player, Hand.MAIN_HAND, upperHit);
        context.assertTrue(entity.displayedCard().isOf(second) && player.getInventory().count(first) == 1, "替换卡牌时旧卡没有安全返还");
        var clientCopy = new CardDisplayCaseBlockEntity(absolute, lower);
        var registries = context.getWorld().getRegistryManager();
        clientCopy.read(entity.toInitialChunkDataNbt(registries), registries);
        context.assertTrue(clientCopy.displayedCard().isOf(second), "初始区块同步没有发送展示卡");
        player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY); player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
        context.assertTrue(upper.onUse(context.getWorld(), player, upperHit).isAccepted(), "空手右键没有取出卡牌");
        context.assertTrue(entity.displayedCard().isEmpty() && player.getInventory().count(second) == 1, "取出的卡牌没有回到玩家背包");
        var emptyUpdate = ((net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket) entity.toUpdatePacket()).getNbt();
        context.assertTrue(emptyUpdate != null && !emptyUpdate.isEmpty(), "空柜更新不能是空NBT，否则客户端跳过清空");
        clientCopy.read(emptyUpdate, registries);
        context.assertTrue(clientCopy.displayedCard().isEmpty(), "取出卡牌后客户端展示卡未清除");
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(second));
        upper.onUseWithItem(player.getMainHandStack(), context.getWorld(), player, Hand.MAIN_HAND, upperHit);
        context.getWorld().breakBlock(absolute.up(), true, player);
        context.assertTrue(context.getBlockState(local).isAir() && context.getBlockState(local.up()).isAir(), "挖掉一半后展示柜另一半仍残留");
        var drops = context.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class,
            new net.minecraft.util.math.Box(absolute).expand(2), e -> true).stream().map(net.minecraft.entity.ItemEntity::getStack).toList();
        context.assertTrue(drops.stream().anyMatch(s -> s.isOf(cabinetItem)) && drops.stream().anyMatch(s -> s.isOf(second)),
            "挖掉展示柜没有同时掉落柜体和展示卡");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void displayCaseOwnerLockAndLegacySave(TestContext context) {
        var world=context.getWorld();var registries=world.getRegistryManager();
        var local=new BlockPos(1,1,1);var pos=context.getAbsolutePos(local);
        var lower=PtcgContent.CARD_DISPLAY_CASE.getDefaultState();
        context.setBlockState(local,lower);
        context.setBlockState(local.up(),lower.with(CardDisplayCaseBlock.HALF,net.minecraft.block.enums.DoubleBlockHalf.UPPER));
        var upper=world.getBlockState(pos.up());
        var owner=context.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        var stranger=context.createMockPlayer(net.minecraft.world.GameMode.CREATIVE);
        owner.setUuid(new UUID(73,1));stranger.setUuid(new UUID(73,2));
        var card=CardItems.cards().getFirst();var other=CardItems.cards().get(1);
        var hit=new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(pos.up()),net.minecraft.util.math.Direction.NORTH,pos.up(),false);
        owner.setStackInHand(Hand.MAIN_HAND,new ItemStack(card));
        upper.onUseWithItem(owner.getMainHandStack(),world,owner,Hand.MAIN_HAND,hit);
        var cabinet=(CardDisplayCaseBlockEntity)world.getBlockEntity(pos);
        context.assertTrue(owner.getUuid().equals(cabinet.cardOwner()),"未记录实际放卡玩家");
        owner.setSneaking(true);owner.setStackInHand(Hand.MAIN_HAND,new ItemStack(net.minecraft.item.Items.STICK));
        var use=net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.invoker();
        use.interact(owner,world,Hand.MAIN_HAND,hit);
        context.assertTrue(cabinet.locked(),"持其他物品 Shift 右键未上锁");
        use.interact(owner,world,Hand.OFF_HAND,hit);
        context.assertTrue(cabinet.locked(),"副手导致一次点击切换两次");
        stranger.setSneaking(true);use.interact(stranger,world,Hand.MAIN_HAND,hit);
        context.assertTrue(cabinet.locked(),"其他玩家/创造模式解开了锁");
        stranger.setSneaking(false);stranger.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);
        upper.onUse(world,stranger,hit);
        stranger.setStackInHand(Hand.MAIN_HAND,new ItemStack(other));
        upper.onUseWithItem(stranger.getMainHandStack(),world,stranger,Hand.MAIN_HAND,hit);
        context.assertTrue(cabinet.displayedCard().isOf(card)&&stranger.getMainHandStack().getCount()==1,"锁定时仍可取卡或换卡");
        var breaks=net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.invoker();
        for(var target:List.of(pos,pos.up())) for(var player:List.of(owner,stranger))
            context.assertTrue(!breaks.beforeBlockBreak(world,player,target,world.getBlockState(target),world.getBlockEntity(target)),"锁定柜上下任一半仍可被采掘");
        var saved=cabinet.toInitialChunkDataNbt(registries);
        var restored=new CardDisplayCaseBlockEntity(pos,lower);restored.read(saved,registries);
        context.assertTrue(restored.locked()&&owner.getUuid().equals(restored.cardOwner())&&restored.displayedCard().isOf(card),"重载丢失锁、归属或卡牌");
        var legacy=saved.copy();legacy.remove("cardOwner");legacy.remove("locked");restored.read(legacy,registries);
        context.assertTrue(!restored.locked()&&restored.cardOwner()==null&&restored.displayedCard().isOf(card),"旧存档兼容失败");
        context.assertTrue(!restored.toggleLock(stranger.getUuid()),"无归属旧卡被冒领");
        use.interact(owner,world,Hand.MAIN_HAND,hit);
        context.assertTrue(!cabinet.locked()&&breaks.beforeBlockBreak(world,owner,pos,lower,cabinet),"放卡人解锁后仍无法采掘");
        stranger.setStackInHand(Hand.MAIN_HAND,new ItemStack(other));
        upper.onUseWithItem(stranger.getMainHandStack(),world,stranger,Hand.MAIN_HAND,hit);
        context.assertTrue(stranger.getUuid().equals(cabinet.cardOwner()),"换卡后所有者没有更新");
        stranger.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);stranger.setStackInHand(Hand.OFF_HAND,ItemStack.EMPTY);
        upper.onUse(world,stranger,hit);
        restored.read(cabinet.toInitialChunkDataNbt(registries),registries);
        context.assertTrue(restored.displayedCard().isEmpty()&&restored.cardOwner()==null&&!restored.locked(),"空柜客户端残留卡牌或锁");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void recyclingStationRecipeAndBlock(TestContext context) {
        var cobble=net.minecraft.item.Items.COBBLESTONE;var item=PtcgContent.CARD_RECYCLING_STATION.asItem();
        assertCraft(context,"card_recycling_station",item,cobble,cobble,cobble,cobble,PtcgContent.CARD_BOX,cobble,cobble,cobble,cobble);
        var local=new BlockPos(1,1,1);var pos=context.getAbsolutePos(local);var world=context.getWorld();
        context.setBlockState(local.down(),net.minecraft.block.Blocks.STONE.getDefaultState());
        var player=context.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(item));
        var hit=new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofBottomCenter(pos),net.minecraft.util.math.Direction.UP,pos.down(),false);
        context.assertTrue(item.useOnBlock(new net.minecraft.item.ItemUsageContext(player,Hand.MAIN_HAND,hit)).isAccepted(),"回收站无法放置");
        var state=world.getBlockState(pos);
        context.assertTrue(state.isOf(PtcgContent.CARD_RECYCLING_STATION)&&world.getBlockState(pos.up()).isAir()&&state.isFullCube(world,pos),"回收站不是一格高");
        context.assertTrue(world.getBlockEntity(pos)==null,"仅方块版回收站不应创建回收数据");
        var drops=net.minecraft.block.Block.getDroppedStacks(state,world,pos,null,player,new ItemStack(net.minecraft.item.Items.IRON_PICKAXE));
        context.assertTrue(drops.size()==1&&drops.getFirst().isOf(item),"回收站不能掉落自身");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void persistentTrainerPacketsAndFirstTurn(TestContext context) {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), observer = UUID.randomUUID();
        var standard = PtcgServices.rulesets().get(RULE);
        // Test-only historical allowance: never registered or offered as current Standard.
        var historical = new Ruleset("fixture-g-h-i", "test-only", "测试历史卡池（非当前 Standard）", "international",
            standard.effectiveDate(), Set.of("G", "H", "I", "J"), Set.of(), Map.of(), standard.sources(), List.of(), Set.of("sve_en_001"));
        var decks = new ArrayList<List<CardInstance>>();
        for (UUID owner : List.of(a, b)) {
            var cards = new ArrayList<CardInstance>();
            for (int i = 0; i < 60; i++) cards.add(new CardInstance(UUID.randomUUID(), owner,
                i < 4 ? "sv5_en_014" : i < 8 ? "zsv10-5_en_079" : i < 12 ? "sv1_en_167" : "sve_en_001"));
            var validation=new DeckValidator(BundledRules.loadCardDefinitions(), BundledCombatData.load()).validate(historical,cards);
            context.assertTrue(validation.canStartOfficialMatch(), "真实效果测试卡组不合法："+validation.issues().stream().distinct().limit(2).toList());
            context.assertTrue(!new DeckValidator(BundledRules.loadCardDefinitions(), BundledCombatData.load()).validate(standard, cards).canStartOfficialMatch(), "G 标记场地被错误放行当前 Standard");
            decks.add(cards);
        }
        ServerMatch selected = null;
        var definitions = BundledRules.loadCardDefinitions(); var combat = BundledCombatData.load();
        for (int seed = 0; seed < 20000; seed++) {
            var candidate = new ServerMatch(a, b, decks.get(0), decks.get(1), historical, definitions, combat, new ServerRandom(seed));
            candidate.callCoin(a, CoinSide.HEADS); UUID winner = candidate.publicCoinResult() == CoinSide.HEADS ? a : b;
            candidate.chooseWhoGoesFirst(winner, winner.equals(a));
            if (!candidate.needsMulligan(a) && !candidate.needsMulligan(b)
                && List.of("zsv10-5_en_079", "sv1_en_167").stream().allMatch(id -> candidate.privateView(a).hand().stream().anyMatch(c -> c.definitionId().equals(id)))) { selected = candidate; break; }
        }
        context.assertTrue(selected != null, "找不到工具／场地起手测试");
        var m = selected; var session = MatchSession.multiplayer(m, a, b); session.addSpectator(observer);
        completeSetup(session, a, b, a, b);
        for (String label : List.of("使用 气球 →", "放置场地：海滩场地")) {
            var view = session.view(a, a); var action = view.options().stream().filter(o -> o.labelZh().startsWith(label)).findFirst().orElseThrow();
            var buffer = new RegistryByteBuf(Unpooled.buffer(), context.getWorld().getRegistryManager());
            try {
                var packet = new MatchIntentPayload(m.id().toString(), a.toString(), Math.toIntExact(view.revision()), action.index());
                MatchIntentPayload.CODEC.encode(buffer, packet); var decoded = MatchIntentPayload.CODEC.decode(buffer);
                session.submit(a, UUID.fromString(decoded.seat()), decoded.revision(), decoded.option());
                boolean duplicate = false;
                try { session.submit(a, a, decoded.revision(), decoded.option()); } catch (IllegalStateException expected) { duplicate = true; }
                context.assertTrue(duplicate, "重复工具／场地请求没有被拒绝");
            } finally { buffer.release(); }
        }
        context.assertTrue(m.turnNumber() == 1 && !m.mayPlaySupporter(a), "工具／场地错误影响首回合限制");
        context.assertTrue(m.activeRetreatCost(a) == 0 && m.activeRetreatCost(b) == 0, "持续效果未在服务器双方生效");
        var buffer = new RegistryByteBuf(Unpooled.buffer(), context.getWorld().getRegistryManager());
        try {
            var gson = new Gson(); String json = gson.toJson(session.view(observer, null));
            MatchStatePayload.CODEC.encode(buffer, new MatchStatePayload(false, json, "场地／工具"));
            var wire = MatchStatePayload.CODEC.decode(buffer);
            var decoded = gson.fromJson(wire.snapshotJson(), MatchSession.Snapshot.class);
            var regions = dev.hua.ptcglive.view.MatchRegionModel.from(decoded);
            context.assertTrue(regions.zone("stadium").cards().size() == 1 && regions.zone(a + ":attachments").cards().size() == 1, "场地／工具卡面未进入公开区域");
            context.assertTrue(decoded.game().hand().isEmpty() && decoded.options().isEmpty(), "旁观者收到私有手牌／意图");
            for (UUID seat : List.of(a, b)) for (var cards : List.of(m.privateView(seat).hand(), m.privateView(seat).deck(), m.privateView(seat).prizes()))
                for (var card : cards) context.assertTrue(!wire.snapshotJson().contains(card.instanceId().toString()), "持续效果数据包泄露隐藏卡牌");
        } finally { buffer.release(); }
        context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void regionViewsAndPublicEnergy(TestContext context) {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), observer = UUID.randomUUID();
        var m = preparedRegionMatch(a, b);
        var session = MatchSession.multiplayer(m, a, b); session.addSpectator(observer);
        m.chooseOpeningActive(a, m.privateView(a).hand().stream().filter(c -> c.definitionId().equals("sv5_en_014")).findFirst().orElseThrow().instanceId());
        context.assertTrue(dev.hua.ptcglive.view.MatchRegionModel.from(session.view(observer, null)).visibleCards().isEmpty(), "开局盖牌被区域视图泄露");
        m.chooseOpeningActive(b, m.privateView(b).hand().stream().filter(c -> c.definitionId().equals("sv5_en_014")).findFirst().orElseThrow().instanceId());
        m.startAfterOpening(a); m.startAfterOpening(b);
        var energy = m.privateView(a).hand().stream().filter(c -> c.definitionId().equals("sve_en_001")).findFirst().orElseThrow();
        m.attachEnergy(a, energy.instanceId(), m.activePokemonView(a).pokemonId());
        var view = session.view(observer, null); var gson = new Gson(); String json = gson.toJson(view);
        var buffer = new RegistryByteBuf(Unpooled.buffer(), context.getWorld().getRegistryManager());
        try {
            MatchStatePayload.CODEC.encode(buffer, new MatchStatePayload(false, json, "公开区域"));
            var received = MatchStatePayload.CODEC.decode(buffer);
            var decoded = gson.fromJson(received.snapshotJson(), MatchSession.Snapshot.class);
            var regions = dev.hua.ptcglive.view.MatchRegionModel.from(decoded);
            context.assertTrue(regions.zone(a + ":attachments").cards().getFirst().definitionId().equals("sve_en_001"), "公开能量卡面未通过真实数据包传输");
            for (UUID seat : List.of(a, b)) {
                context.assertTrue(regions.zone(seat + ":hand").cards().isEmpty(), "旁观者区域视图泄露手牌");
                context.assertTrue(regions.zone(seat + ":deck").cards().isEmpty() && regions.zone(seat + ":prizes").cards().isEmpty(), "牌库或奖赏暴露卡面");
                var hidden = m.privateView(seat);
                for (var cards : List.of(hidden.hand(), hidden.deck(), hidden.prizes())) for (var card : cards)
                    context.assertTrue(!received.snapshotJson().contains(card.instanceId().toString()), "隐藏卡牌实例进入网络包");
            }
        } finally { buffer.release(); }
        session.removeSpectator(observer);
        boolean rejected = false;
        try { session.view(observer, null); } catch (IllegalArgumentException expected) { rejected = true; }
        context.assertTrue(rejected, "离开观战后仍可请求区域内容");
        context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void dynamicPersistentSourcePackets(TestContext context) {
        dynamicPersistentSourcePackets(context,false);
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void dynamicEnergyProvisionPackets(TestContext context) {
        dynamicPersistentSourcePackets(context,true);
    }
    private void dynamicPersistentSourcePackets(TestContext context,boolean energyOverride) {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),watch=UUID.randomUUID();
        var registry=dev.hua.ptcglive.engine.effect.EffectRegistry.standard();
        var tool=BundledCombatData.trainer(com.google.gson.JsonParser.parseString("""
            {"trainerCategory":"TOOL","effects":[{"type":"retreat_cost_modifier","amount":-1},
              {"type":"attack_damage_modifier","direction":"DEALT","amount":20}]}
            """).getAsJsonObject(),registry);
        var stadiumJson=com.google.gson.JsonParser.parseString("""
            {"trainerCategory":"STADIUM","effects":[
              {"type":"retreat_cost_modifier","target":"ALL_OWN_POKEMON","amount":-1},
              {"type":"retreat_cost_modifier","target":"ALL_OPPONENT_POKEMON","amount":-1},
              {"type":"attack_damage_modifier","target":"ALL_OPPONENT_POKEMON","direction":"RECEIVED","amount":-10}]}
            """).getAsJsonObject();
        if(energyOverride)for(String target:List.of("ALL_OWN_POKEMON","ALL_OPPONENT_POKEMON"))
            stadiumJson.getAsJsonArray("effects").add(com.google.gson.JsonParser.parseString("{\"type\":\"special_energy_override\",\"target\":\""+target+"\",\"amount\":1,\"energy_type\":\"COLORLESS\",\"suppress_other_effects\":true}"));
        var stadiumProfile=BundledCombatData.trainer(stadiumJson,registry);
        var energyJson=com.google.gson.JsonParser.parseString("""
            {"energyUnits":[["COLORLESS"],["COLORLESS"]],"effects":[{"type":"attack_damage_modifier","direction":"DEALT","amount":-20}]}
            """).getAsJsonObject();
        if(energyOverride) {
            energyJson.add("energyUnits",com.google.gson.JsonParser.parseString("[[\"COLORLESS\"]]"));
            energyJson.add("provisionRules",com.google.gson.JsonParser.parseString("[{\"conditions\":[{\"type\":\"IS_BASIC\"}],\"energyUnits\":[[\"COLORLESS\"],[\"COLORLESS\"]]}]"));
        }
        var energy=BundledCombatData.specialEnergy(energyJson,registry);
        var attack=BundledCombatData.attack(com.google.gson.JsonParser.parseString("""
            {"id":"hit","nameZh":"动态修正测试攻击","energyCost":{"COLORLESS":2},"baseDamage":40,"damageType":"GRASS","effectSupport":"SUPPORTED"}
            """).getAsJsonObject(),registry);
        var definitions=new CardDatabase(List.of(
            new CardDefinition("passive-basic","持续测试宝可梦",CardCategory.POKEMON,TrainerCategory.NONE,"H",Set.of("international"),true,false,60,Map.of(),EffectSupport.SUPPORTED,null),
            new CardDefinition("passive-tool","持续测试工具",CardCategory.TRAINER,TrainerCategory.TOOL,"H",Set.of("international"),false,false,60,Map.of(),EffectSupport.SUPPORTED,null),
            new CardDefinition("passive-stadium","持续测试场地",CardCategory.TRAINER,TrainerCategory.STADIUM,"H",Set.of("international"),false,false,60,Map.of(),EffectSupport.SUPPORTED,null),
            new CardDefinition("passive-energy","持续测试特殊能量",CardCategory.ENERGY,TrainerCategory.NONE,"H",Set.of("international"),false,false,60,Map.of(),EffectSupport.SUPPORTED,null)));
        var combat=new CombatCatalog(Map.of("passive-basic",new dev.hua.ptcglive.engine.field.PokemonProfile(300,3,null,
            dev.hua.ptcglive.engine.field.EnergyType.GRASS,2,null,0,1,List.of(attack))),Map.of(),Map.of("passive-tool",tool,"passive-stadium",stadiumProfile),Map.of("passive-energy",energy));
        var decks=new ArrayList<List<CardInstance>>();
        for(UUID owner:List.of(a,b)) {var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(UUID.randomUUID(),owner,i<30?"passive-basic":i<40?"passive-tool":i<50?"passive-stadium":"passive-energy"));decks.add(cards);}
        ServerMatch selected=null;
        for(int seed=0;seed<1000;seed++) {
            var m=new ServerMatch(a,b,decks.get(0),decks.get(1),PtcgServices.rulesets().get(RULE),definitions,combat,new ServerRandom(seed));
            m.callCoin(a,CoinSide.HEADS);UUID winner=m.publicCoinResult()==CoinSide.HEADS?a:b;m.chooseWhoGoesFirst(winner,winner.equals(a));
            if(List.of(a,b).stream().allMatch(p->m.privateView(p).hand().stream().filter(c->c.definitionId().equals("passive-basic")).count()>=2)){selected=m;break;}
        }
        context.assertTrue(selected!=null,"未找到动态持续夹具");var m=selected;
        for(UUID seat:List.of(a,b)) {var basics=m.privateView(seat).hand().stream().filter(c->c.definitionId().equals("passive-basic")).toList();m.chooseOpeningActive(seat,basics.getFirst().instanceId());m.placeOpeningBench(seat,basics.get(1).instanceId());}
        var session=MatchSession.multiplayer(m,a,b);session.addSpectator(watch);completeSetup(session,a,b,a,b);m.drawCardsByEffect(a,a,25);m.endTurn(a);m.endTurn(b);
        UUID active=m.activePokemonView(a).pokemonId();var played=new HashMap<String,CardInstance>();
        var wire=new RegistryByteBuf(Unpooled.buffer(),context.getWorld().getRegistryManager());
        try {
            for(String id:List.of("passive-tool","passive-stadium","passive-energy")) {
                var card=m.privateView(a).hand().stream().filter(c->c.definitionId().equals(id)).findFirst().orElseThrow();played.put(id,card);
                var view=session.view(a,a);var option=view.options().stream().filter(o->o.cardKeys().contains("hand:"+card.instanceId())
                    &&(id.equals("passive-stadium")||o.cardKeys().contains("field:"+active))).findFirst().orElseThrow();
                wire.clear();MatchIntentPayload.CODEC.encode(wire,new MatchIntentPayload(m.id().toString(),a.toString(),Math.toIntExact(view.revision()),option.index()));
                var intent=MatchIntentPayload.CODEC.decode(wire);boolean forbidden=false;try{session.submit(b,a,intent.revision(),intent.option());}catch(IllegalArgumentException expected){forbidden=true;}
                context.assertTrue(forbidden,"对手伪造持续来源出牌成功");session.submit(a,a,intent.revision(),intent.option());
                boolean stale=false;try{session.submit(a,a,intent.revision(),intent.option());}catch(IllegalStateException expected){stale=true;}context.assertTrue(stale,"重复出牌叠加持续来源");
            }
            context.assertTrue(m.activeRetreatCost(a)==1&&m.activeRetreatCost(b)==2,"工具与场地持续修正不正确");var random=m.randomTranscript();
            for(UUID recipient:List.of(a,b,watch)) {
                wire.clear();MatchStatePayload.CODEC.encode(wire,new MatchStatePayload(false,new Gson().toJson(session.view(recipient,recipient.equals(watch)?null:recipient)),"动态持续来源"));
                String json=MatchStatePayload.CODEC.decode(wire).snapshotJson();var snapshot=new Gson().fromJson(json,MatchSession.Snapshot.class);
                context.assertTrue(snapshot.game().stadium().equals(played.get("passive-stadium")),"公开场地身份丢失");
                context.assertTrue(!json.contains("passiveProgram")&&!json.contains("effectProgram")&&!json.contains("provisionRules"),"服务端程序泄露");
                for(UUID owner:List.of(a,b)) {
                    if(!recipient.equals(owner))for(var hidden:m.privateView(owner).hand())context.assertTrue(!json.contains(hidden.instanceId().toString()),"他人手牌泄露");
                    for(var hidden:m.privateView(owner).deck())context.assertTrue(!json.contains(hidden.instanceId().toString()),"牌库泄露");
                    for(var hidden:m.privateView(owner).prizes())context.assertTrue(!json.contains(hidden.instanceId().toString()),"奖赏泄露");
                }
            }
            context.assertTrue(random.equals(m.randomTranscript())&&m.activeRetreatCost(a)==1,"刷新造成随机或修正累积");
            if(energyOverride) {
                var blocked=session.view(a,a);var attackOption=blocked.options().stream().filter(o->o.labelZh().startsWith("攻击：")).findFirst().orElseThrow();
                wire.clear();MatchIntentPayload.CODEC.encode(wire,new MatchIntentPayload(m.id().toString(),a.toString(),Math.toIntExact(blocked.revision()),attackOption.index()));
                var invalid=MatchIntentPayload.CODEC.decode(wire);boolean denied=false;
                try{session.submit(a,a,invalid.revision(),invalid.option());}catch(IllegalStateException expected){denied=true;}
                context.assertTrue(denied&&m.currentPlayer().equals(a)&&m.activePokemonView(b).damage()==0&&random.equals(m.randomTranscript()),"动态能量不足仍接受客户端攻击或消耗随机数");
                // Audited server-side removal hook, not a client-authored field change.
                m.discardStadiumByEffect(a);context.assertTrue(m.activeRetreatCost(a)==2,"场地离场后数值未恢复");
            }
            var view=session.view(a,a);var action=view.options().stream().filter(o->o.labelZh().startsWith("攻击：")).findFirst().orElseThrow();
            wire.clear();MatchIntentPayload.CODEC.encode(wire,new MatchIntentPayload(m.id().toString(),a.toString(),Math.toIntExact(view.revision()),action.index()));
            var intent=MatchIntentPayload.CODEC.decode(wire);session.submit(a,a,intent.revision(),intent.option());
            context.assertTrue(m.activePokemonView(b).damage()==(energyOverride?80:70)&&m.currentPlayer().equals(b),"动态供能恢复或持续伤害顺序不正确");
            m.endTurn(b);
            if(energyOverride) {
                var retreatView=session.view(a,a);var retreat=retreatView.options().stream().filter(o->o.labelZh().startsWith("撤退 →")).findFirst().orElseThrow();
                wire.clear();MatchIntentPayload.CODEC.encode(wire,new MatchIntentPayload(m.id().toString(),a.toString(),Math.toIntExact(retreatView.revision()),retreat.index()));
                var payment=MatchIntentPayload.CODEC.decode(wire);session.submit(a,a,payment.revision(),payment.option());
                var energyView=session.view(a,a);
                var select=energyView.options().stream().filter(o->o.enabled()&&o.cardKeys().equals(List.of("effect:"+played.get("passive-energy").instanceId()))).findFirst().orElseThrow();
                wire.clear();MatchIntentPayload.CODEC.encode(wire,new MatchIntentPayload(m.id().toString(),a.toString(),Math.toIntExact(energyView.revision()),select.index()));
                var retreatEnergyIntent=MatchIntentPayload.CODEC.decode(wire);session.submit(a,a,retreatEnergyIntent.revision(),retreatEnergyIntent.option());
                var confirmedView=session.view(a,a);var confirm=confirmedView.options().stream().filter(o->o.enabled()&&o.labelZh().startsWith("确认撤退")).findFirst().orElseThrow();
                wire.clear();MatchIntentPayload.CODEC.encode(wire,new MatchIntentPayload(m.id().toString(),a.toString(),Math.toIntExact(confirmedView.revision()),confirm.index()));
                var confirmed=MatchIntentPayload.CODEC.decode(wire);session.submit(a,a,confirmed.revision(),confirmed.option());
                context.assertTrue(!m.activePokemonView(a).pokemonId().equals(active)&&m.privateView(a).discard().contains(played.get("passive-energy")),"真实撤退消息未用一张动态双能量支付两点费用");
            }
            m.discardToolByEffect(a,a,active,played.get("passive-tool").instanceId());
            if(!energyOverride) {
                m.discardStadiumByEffect(a);
                m.returnAttachedEnergyToHandByEffect(a,a,active,List.of(played.get("passive-energy").instanceId()));
            }
            context.assertTrue(m.activeRetreatCost(a)==3&&m.activeRetreatCost(b)==3,"移除来源后残留持续修正");
        } finally {wire.release();}
        context.complete();
    }
    private static List<CardInstance> regionDeck(UUID owner) {
        List<CardInstance> cards = new ArrayList<>();
        for (int i = 0; i < 60; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, i < 4 ? "sv5_en_014" : "sve_en_001"));
        return cards;
    }
    private static ServerMatch preparedRegionMatch(UUID a, UUID b) {
        var aDeck = regionDeck(a); var bDeck = regionDeck(b);
        for (int seed = 0; seed < 1000; seed++) {
            var m = new ServerMatch(a, b, aDeck, bDeck, PtcgServices.rulesets().get(RULE),
                BundledRules.loadCardDefinitions(), BundledCombatData.load(), new ServerRandom(seed));
            m.callCoin(a, CoinSide.HEADS); UUID winner = m.publicCoinResult() == CoinSide.HEADS ? a : b;
            m.chooseWhoGoesFirst(winner, winner.equals(a));
            if (!m.needsMulligan(a) && !m.needsMulligan(b)) return m;
        }
        throw new AssertionError("找不到公开区域测试的有效起手牌");
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void openingMulliganPacketIsolation(TestContext context) {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), watcher = UUID.randomUUID();
        ServerMatch selected = null;
        for (int seed = 0; seed < 1000; seed++) {
            var m = new ServerMatch(a, b, regionDeck(a), regionDeck(b), PtcgServices.rulesets().get(RULE),
                BundledRules.loadCardDefinitions(), BundledCombatData.load(), new ServerRandom(seed));
            context.assertTrue(m.privateView(a).hand().isEmpty() && m.randomTranscript().isEmpty(), "猜硬币前已经洗牌发牌");
            m.callCoin(a, CoinSide.HEADS); UUID winner = m.publicCoinResult() == CoinSide.HEADS ? a : b;
            m.chooseWhoGoesFirst(winner, winner.equals(a));
            if (m.phase() == MatchPhase.MULLIGAN_REVEAL) { selected = m; break; }
        }
        if (selected == null) throw new AssertionError("找不到双方重抽测试夹具");
        var m = selected; var session = MatchSession.multiplayer(m, a, b); session.addSpectator(watcher);
        var view = session.view(watcher, null); String json = new Gson().toJson(view);
        var buffer = new RegistryByteBuf(Unpooled.buffer(), context.getWorld().getRegistryManager());
        try {
            MatchStatePayload.CODEC.encode(buffer, new MatchStatePayload(false, json, "重抽展示"));
            var received = MatchStatePayload.CODEC.decode(buffer);
            var decoded = new Gson().fromJson(received.snapshotJson(), MatchSession.Snapshot.class);
            context.assertTrue(decoded.game().mulliganReveals().size() == 2, "真实数据包未携带双方公开重抽证明");
            for (var reveal : decoded.game().mulliganReveals()) {
                context.assertTrue(reveal.cardIds().size() == 7, "重抽展示不是七张");
                context.assertTrue(reveal.cardIds().stream().noneMatch(id -> m.cardDefinition(id).basicPokemon()), "错误公开有效起手牌");
            }
            for (UUID p : List.of(a, b)) for (var cards : List.of(m.privateView(p).hand(), m.privateView(p).deck()))
                for (var card : cards) context.assertTrue(!received.snapshotJson().contains(card.instanceId().toString()), "重抽证明泄露可追踪的隐藏卡 UUID");
            var actor = session.view(a, a);
            var option = actor.options().stream().filter(o -> o.labelZh().startsWith("确认无基础")).findFirst().orElseThrow();
            var request = new MatchIntentPayload(m.id().toString(), a.toString(), Math.toIntExact(actor.revision()), option.index());
            MatchIntentPayload.CODEC.encode(buffer, request); var intent = MatchIntentPayload.CODEC.decode(buffer);
            int randomCount = m.randomTranscript().size();
            session.submit(a, UUID.fromString(intent.seat()), intent.revision(), intent.option());
            context.assertTrue(m.randomTranscript().size() == randomCount && m.phase() == MatchPhase.MULLIGAN_REVEAL, "单方确认使对方来不及查看便重抽");
            boolean staleRejected = false;
            try { session.submit(a, a, intent.revision(), intent.option()); } catch (IllegalStateException expected) { staleRejected = true; }
            context.assertTrue(staleRejected, "重复的重抽确认数据包被接受");
        } finally { buffer.release(); }
        completeSetup(session, a, b, a, b);
        context.assertTrue(m.phase() == MatchPhase.TURN && m.turnNumber() == 1, "真实服务器重抽后无法开局");
        context.assertTrue(m.privateView(a).prizes().size() == 6 && m.privateView(b).prizes().size() == 6, "奖赏设置次数错误");
        context.assertTrue(session.view(watcher, null).game().hand().isEmpty(), "开局后旁观者拿到了新手牌");
        context.complete();
    }
    /** The same finite option list the real GUI receives; no direct hand mutation or guessed randomness. */
    private static void completeSetup(MatchSession session, UUID connectionA, UUID connectionB, UUID a, UUID b) {
        var m = session.match();
        for (int step = 0; step < 1000 && m.phase() != MatchPhase.TURN; step++) {
            if (m.phase() == MatchPhase.FINISHED) throw new AssertionError("开局意外结束");
            boolean progressed = false;
            for (UUID seat : List.of(a, b)) {
                if (m.phase() == MatchPhase.TURN) break;
                UUID connection = seat.equals(a) ? connectionA : connectionB;
                var view = session.view(connection, seat);
                var options = view.options().stream().filter(o -> !o.labelZh().startsWith("认输")).toList();
                if (options.isEmpty()) continue;
                String preferred = m.phase() == MatchPhase.TURN_ORDER ? (seat.equals(a) ? "选择自己先手" : "选择对方先手") : "确认开局设置";
                var option = options.stream().filter(o -> o.labelZh().equals(preferred)).findFirst().orElse(options.getFirst());
                session.submit(connection, seat, view.revision(), option.index()); progressed = true;
            }
            if (!progressed) throw new AssertionError("开局没有可执行的服务器操作：" + m.phase());
        }
        if (m.phase() != MatchPhase.TURN) throw new AssertionError("开局未能在有限步骤完成");
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void cardBoxEditorAndPacketIsolation(TestContext context) {
        UUID owner = UUID.randomUUID(), stranger = UUID.randomUUID();
        var boxes = PtcgServices.cardBoxes();
        var box = DebugCommands.createReviewedBox(owner, "编辑界面服务端测试");
        var credential = CardBoxItem.referenceTo(box);
        context.assertTrue(box.id().equals(CardBoxItem.boxId(credential)), "物品凭证未指向账本卡盒");
        var foreign = boxes.mint(stranger, "sv1_en_188");
        var editor = PtcgServices.openBoxEditor(owner, box.id());
        var view = editor.view(owner);
        context.assertTrue(view.totalCards() == 60 && view.rules().stream().anyMatch(r -> r.id().equals(RULE) && r.canStart()), "合法性检查未复用规则核心");
        var gson = new Gson(); var json = gson.toJson(view);
        context.assertTrue(!json.contains(foreign.instanceId().toString()) && !json.contains("sv1_en_188"), "编辑视图泄露他人收藏");
        var buffer = new RegistryByteBuf(Unpooled.buffer(), context.getWorld().getRegistryManager());
        try {
            var state = new BoxStatePayload(true, json, "编辑测试");
            BoxStatePayload.CODEC.encode(buffer, state);
            context.assertTrue(BoxStatePayload.CODEC.decode(buffer).equals(state), "卡盒状态编解码错误");
            var request = new BoxIntentPayload(editor.sessionId().toString(), view.revision(), "TAKE", "sv5_en_014", 0, "");
            BoxIntentPayload.CODEC.encode(buffer, request);
            context.assertTrue(BoxIntentPayload.CODEC.decode(buffer).equals(request), "卡盒意图编解码错误");
            view = editor.submit(owner, request.revision(), request.action(), request.argument(), request.page(), request.query());
            context.assertTrue(view.totalCards() == 59 && view.rules().stream().noneMatch(r -> r.canStart()), "编辑后合法性没有更新");
            boolean repeated = false;
            try { editor.submit(owner, request.revision(), request.action(), request.argument(), 0, ""); }
            catch (IllegalStateException expected) { repeated = true; }
            context.assertTrue(repeated && boxes.getOwned(owner, box.id()).contents().size() == 59, "重复包执行了第二次取出");
            view = editor.submit(owner, view.revision(), "SOURCE", "COLLECTION", 0, "");
            view = editor.submit(owner, view.revision(), "ADD", "sv5_en_014", 0, "");
            context.assertTrue(view.totalCards() == 60, "取出的实际卡未能放回");
        } finally { buffer.release(); }
        boolean foreignBlocked = false;
        try { PtcgServices.openBoxEditor(stranger, box.id()); } catch (IllegalArgumentException expected) { foreignBlocked = true; }
        context.assertTrue(foreignBlocked, "他人可以开启卡盒编辑器");
        UUID match = UUID.randomUUID(); boxes.snapshotForMatch(owner, box.id(), match);
        boolean locked = false;
        try { editor.view(owner); } catch (IllegalStateException expected) { locked = true; }
        context.assertTrue(locked, "卡桌锁定后仍可刷新卡表"); boxes.unlock(match);
        var restored = CardBoxService.restore(java.time.Clock.systemUTC(), boxes.snapshot());
        context.assertTrue(restored.getOwned(owner, box.id()).contents().equals(boxes.getOwned(owner, box.id()).contents()), "编辑未保持存档不变量");
        context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void tableSessionsAndPacketIsolation(TestContext context) {
        context.setBlockState(new BlockPos(1, 1, 1), PtcgContent.CARD_TABLE.getDefaultState());
        context.assertTrue(context.getBlockState(new BlockPos(1, 1, 1)).isOf(PtcgContent.CARD_TABLE), "卡桌未注册");
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), observer = UUID.randomUUID();
        var boxes = PtcgServices.cardBoxes();
        DeckBox aBox = box(a), bBox = box(b);
        var tables = inventoryTables(context, aBox, bBox);
        var key = new CardTableKey(context.getWorld().getRegistryKey().getValue().toString(), context.getAbsolutePos(new BlockPos(1, 1, 1)).asLong());
        tables.open(key, a, RULE); tables.join(key, b);
        tables.selectBox(key, a, aBox.id()); tables.selectBox(key, b, bBox.id());
        tables.setReady(key, a, true); tables.setReady(key, b, true); tables.spectate(key, observer);
        var match = tables.start(key, a); var session = tables.session(match.id());
        var gson = new Gson();
        String publicJson = gson.toJson(session.view(observer, null));
        for (UUID owner : List.of(a, b)) for (CardInstance hidden : match.privateView(owner).hand())
            context.assertTrue(!publicJson.contains(hidden.instanceId().toString()), "旁观数据泄露手牌");
        var buffer = new RegistryByteBuf(Unpooled.buffer(), context.getWorld().getRegistryManager());
        try {
            var outgoing = new MatchStatePayload(false, publicJson, "联机序列化测试");
            MatchStatePayload.CODEC.encode(buffer, outgoing);
            context.assertTrue(MatchStatePayload.CODEC.decode(buffer).equals(outgoing), "对局状态包往返失败");
            var action = new MatchIntentPayload(match.id().toString(), a.toString(), 0, 0);
            MatchIntentPayload.CODEC.encode(buffer, action);
            context.assertTrue(MatchIntentPayload.CODEC.decode(buffer).equals(action), "玩家意图包往返失败");
        } finally { buffer.release(); }
        boolean blocked = false;
        try { boxes.rename(a, aBox.id(), "非法修改"); } catch (IllegalStateException expected) { blocked = true; }
        context.assertTrue(blocked, "对局中卡盒未锁定");
        var current = session.view(a, a);
        var concede = current.options().stream().filter(o -> o.labelZh().startsWith("认输")).findFirst().orElseThrow();
        tables.submit(match.id(), a, a, current.revision(), concede.index());
        boxes.rename(a, aBox.id(), "正常结束已解锁");
        context.assertTrue(match.result().winner().equals(b), "认输胜负错误");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void reviewedCardsUseServerEffects(TestContext context) {
        UUID owner = UUID.randomUUID();
        var first = DebugCommands.createReviewedBox(owner, "真实卡测试甲");
        var second = DebugCommands.createReviewedBox(owner, "真实卡测试乙");
        var tables = inventoryTables(context, first, second); var practice = tables.startPractice(owner, first.id(), second.id(), RULE);
        var m = practice.match(); UUID a = practice.firstSeat(), b = practice.secondSeat();
        completeSetup(tables.session(m.id()), owner, owner, a, b);
        boolean played = false;
        for (int turn = 0; turn < 110 && m.result() == null; turn++) {
            var session = tables.session(m.id()); var view = session.view(owner, m.currentPlayer());
            var option = view.options().stream().filter(o -> o.labelZh().equals("使用：" + CardNameLocalization.name("me1_en_119", "莉莉艾的决心"))).findFirst();
            if (option.isPresent()) {
                tables.submit(m.id(), owner, m.currentPlayer(), view.revision(), option.get().index());
                context.assertTrue(m.privateView(m.currentPlayer()).hand().size() == 8, "真实支援者没有按剩余奖赏抽八张");
                context.assertTrue(!m.mayPlaySupporter(m.currentPlayer()), "真实支援者未消耗当回合次数");
                played = true; break;
            }
            m.endTurn(m.currentPlayer());
        }
        context.assertTrue(played, "未能通过服务器动作选择使用真实卡牌");
        tables.endMatch(m.id()); context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void abilityIntentUsesServerAuthority(TestContext context) {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        List<CardInstance> aDeck = abilityDeck(a, "fixture-ability-draw-h"), bDeck = abilityDeck(b, "fixture-basic-h");
        ServerMatch selected = null;
        for (int seed = 0; seed < 2000; seed++) {
            var candidate = new ServerMatch(a, b, aDeck, bDeck, PtcgServices.rulesets().get(RULE),
                BundledRules.loadCardDefinitions(), BundledCombatData.load(), new ServerRandom(seed));
            candidate.callCoin(a, CoinSide.HEADS); UUID winner = candidate.publicCoinResult() == CoinSide.HEADS ? a : b;
            candidate.chooseWhoGoesFirst(winner, winner.equals(a));
            if (candidate.currentPlayer().equals(a)) {
                selected = new ServerMatch(a, b, aDeck, bDeck, PtcgServices.rulesets().get(RULE),
                    BundledRules.loadCardDefinitions(), BundledCombatData.load(), new ServerRandom(seed));
                break;
            }
        }
        context.assertTrue(selected != null, "找不到特性服务端测试所需的 A 先手随机序列");
        var m = selected; var session = MatchSession.multiplayer(m, a, b);
        completeSetup(session, a, b, a, b);
        var view = session.view(a, a);
        var action = view.options().stream().filter(o -> o.labelZh().contains("使用特性：") && o.labelZh().contains("测试抽牌")).findFirst().orElseThrow();
        context.assertTrue(session.view(b, b).options().stream().noneMatch(o -> o.labelZh().contains("测试抽牌")), "非行动方收到可用特性意图");
        int hand = m.privateView(a).hand().size();
        var buffer = new RegistryByteBuf(Unpooled.buffer(), context.getWorld().getRegistryManager());
        try {
            var sent = new MatchIntentPayload(m.id().toString(), a.toString(), Math.toIntExact(view.revision()), action.index());
            MatchIntentPayload.CODEC.encode(buffer, sent); var decoded = MatchIntentPayload.CODEC.decode(buffer);
            session.submit(a, UUID.fromString(decoded.seat()), decoded.revision(), decoded.option());
        } finally { buffer.release(); }
        context.assertTrue(m.privateView(a).hand().size() == hand + 1 && !m.canUseAbility(a, m.activePokemonView(a).pokemonId(), "fixture-draw"),
            "真实 Fabric 服务器未以单次服务器意图结算并限制特性");
        context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void dudunsparceRealAbilityUsesServerIntent(TestContext context) {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        ServerMatch selected = null;
        for (int seed = 0; seed < 4000; seed++) {
            var candidate = new ServerMatch(a, b, dudunsparceDeck(a), dudunsparceDeck(b), PtcgServices.rulesets().get(RULE),
                BundledRules.loadCardDefinitions(), BundledCombatData.load(), new ServerRandom(seed));
            candidate.callCoin(a, CoinSide.HEADS); UUID winner = candidate.publicCoinResult() == CoinSide.HEADS ? a : b;
            candidate.chooseWhoGoesFirst(winner, winner.equals(a));
            if (candidate.currentPlayer().equals(a)
                && candidate.privateView(a).hand().stream().filter(c -> c.definitionId().equals("sv5_en_128")).count() >= 2
                && candidate.privateView(a).hand().stream().anyMatch(c -> c.definitionId().equals("sv5_en_129"))) {
                selected = new ServerMatch(a, b, dudunsparceDeck(a), dudunsparceDeck(b), PtcgServices.rulesets().get(RULE),
                    BundledRules.loadCardDefinitions(), BundledCombatData.load(), new ServerRandom(seed));
                break;
            }
        }
        context.assertTrue(selected != null, "找不到土龙节节真实特性测试所需的随机开局");
        var m = selected; var session = MatchSession.multiplayer(m, a, b); completeSetup(session, a, b, a, b);
        submitLabel(session, a, a, "备战：土龙弟弟");
        m.endTurn(a); m.endTurn(b);
        submitLabel(session, a, a, "进化：战斗位 土龙弟弟 → 土龙节节");
        int hand = m.privateView(a).hand().size();
        var view = session.view(a, a);
        var option = view.options().stream().filter(o -> o.labelZh().contains("使用特性：") && o.labelZh().contains("逃跑抽牌")).findFirst().orElseThrow();
        session.submit(a, a, view.revision(), option.index());
        context.assertTrue(m.privateView(a).hand().size() == hand + 3 && m.phase() == MatchPhase.CHOOSE_REPLACEMENT,
            "土龙节节的逃跑抽牌未由真实 Fabric 服务器意图抽三张并要求替补");
        context.assertTrue(m.privateView(a).discard().isEmpty() && m.privateView(a).deck().stream().anyMatch(c -> c.definitionId().equals("sv5_en_129")),
            "逃跑抽牌没有将宝可梦与附属牌洗回服务器牌库");
        context.complete();
    }
    private static void submitLabel(MatchSession session, UUID connection, UUID seat, String label) {
        var view = session.view(connection, seat);
        int option = view.options().stream().filter(o -> o.labelZh().equals(label)).findFirst().orElseThrow().index();
        session.submit(connection, seat, view.revision(), option);
    }
    private static List<CardInstance> dudunsparceDeck(UUID owner) {
        var cards = new ArrayList<CardInstance>();
        for (int i = 0; i < 24; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, "sv5_en_128"));
        for (int i = 0; i < 24; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, "sv5_en_129"));
        for (int i = 48; i < 60; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, "sve_en_001"));
        return cards;
    }
    private static List<CardInstance> abilityDeck(UUID owner, String id) {
        var cards = new ArrayList<CardInstance>();
        for (int i = 0; i < 60; i++) cards.add(new CardInstance(UUID.randomUUID(), owner, id));
        return cards;
    }
    /** Synthetic seat IDs still get real mock inventories and the production possession predicate. */
    private static dev.hua.ptcglive.table.CardTableManager inventoryTables(TestContext context, DeckBox... decks) {
        var players = new java.util.HashMap<UUID, net.minecraft.entity.player.PlayerEntity>();
        for (var deck : decks) {
            var player = players.computeIfAbsent(deck.ownerId(), id -> context.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL));
            player.getInventory().insertStack(dev.hua.ptcglive.CardBoxItem.referenceTo(deck));
        }
        var tables = new dev.hua.ptcglive.table.CardTableManager(PtcgServices.rulesets(), PtcgServices.cardBoxes(),
            PtcgServices.cardDatabase(), dev.hua.ptcglive.rules.BundledCombatData.load(), ServerRandom::new);
        tables.requireBoxPossession((owner, box) -> dev.hua.ptcglive.collection.PlayerCardHoldings.hasBox(players.get(owner), box));
        return tables;
    }
    private static DeckBox box(UUID player) {
        var boxes = PtcgServices.cardBoxes(); var box = boxes.create(player, "集成测试卡盒", RULE);
        for (int i = 0; i < 60; i++) boxes.put(player, box.id(), boxes.mint(player, i < 4 ? "fixture-basic-h" : "fixture-energy-h").instanceId());
        return box;
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void practiceSessionsAndAdminFixtureGuard(TestContext context) {
        UUID owner = UUID.randomUUID(), stranger = UUID.randomUUID();
        var one = DebugCommands.createFixtureBox(owner, "测试一");
        var two = DebugCommands.createFixtureBox(owner, "测试二");
        var three = DebugCommands.createFixtureBox(owner, "测试三");
        var four = DebugCommands.createFixtureBox(owner, "测试四");
        var tables = inventoryTables(context, one, two, three, four);
        var first = tables.startPractice(owner, one.id(), two.id(), RULE);
        var second = tables.startPractice(owner, three.id(), four.id(), RULE);
        var session = tables.session(first.matchId());
        context.assertTrue(session.view(owner, first.firstSeat()).practice(), "练习未明确标注");
        context.assertTrue(session.view(owner, first.secondSeat()).game().viewingSeat().equals(first.secondSeat()), "无法切换练习席位");
        boolean forbidden = false;
        try { session.view(stranger, first.firstSeat()); } catch (IllegalArgumentException expected) { forbidden = true; }
        context.assertTrue(forbidden, "其他玩家能读取练习隐藏视角");
        tables.endMatch(first.matchId());
        PtcgServices.cardBoxes().rename(owner, one.id(), "已解锁");
        boolean stillLocked = false;
        try { PtcgServices.cardBoxes().rename(owner, three.id(), "不应解锁"); } catch (IllegalStateException expected) { stillLocked = true; }
        context.assertTrue(stillLocked, "结束一个对局影响了另一对局锁定");
        tables.endMatch(second.matchId());
        var debug = context.getWorld().getServer().getCommandManager().getDispatcher().getRoot().getChild("ptcg").getChild("debug");
        var source = context.getWorld().getServer().getCommandSource();
        context.assertTrue(!debug.canUse(source.withLevel(0)) && debug.canUse(source.withLevel(2)), "测试卡组命令缺少管理员权限");
        context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void regionalOpenRulesAuthority(TestContext context) {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), observer = UUID.randomUUID();
        var aBox = box(a); var bBox = box(b); var other = box(a);
        var boxes = PtcgServices.cardBoxes(); var tables = inventoryTables(context, aBox, bBox, other);
        var key = new CardTableKey(context.getWorld().getRegistryKey().getValue().toString(), context.getAbsolutePos(new BlockPos(1, 1, 1)).asLong());
        tables.open(key, a, "custom-open-all"); tables.join(key, b);
        tables.selectBox(key, a, aBox.id()); tables.selectBox(key, b, bBox.id());
        tables.setReady(key, a, true); tables.setReady(key, b, true); tables.spectate(key, observer);
        var match = tables.start(key, a); var session = tables.session(match.id());
        completeSetup(session, a, b, a, b);
        context.assertTrue(match.ruleset().edition().casualOnly(), "开放卡桌未冻结休闲规则标识");
        var buffer = new RegistryByteBuf(Unpooled.buffer(), context.getWorld().getRegistryManager());
        try {
            var payload = new MatchStatePayload(false, new Gson().toJson(session.view(observer, null)), "开放卡池旁观");
            MatchStatePayload.CODEC.encode(buffer, payload);
            String json = MatchStatePayload.CODEC.decode(buffer).snapshotJson();
            for (UUID owner : List.of(a, b)) {
                var hidden = match.privateView(owner);
                for (var zone : List.of(hidden.hand(), hidden.deck(), hidden.prizes())) for (var card : zone)
                    context.assertTrue(!json.contains(card.instanceId().toString()), "开放卡池向旁观包泄露隐藏身份");
            }
            boolean locked = false;
            try { boxes.rename(a, aBox.id(), "不能改名"); } catch (IllegalStateException expected) { locked = true; }
            context.assertTrue(locked, "开放卡池绕过卡盒锁");
        } finally { buffer.release(); tables.endMatch(match.id()); }
        var editor = PtcgServices.openBoxEditor(a, aBox.id());
        context.assertTrue(editor.view(a).rules().stream().anyMatch(r -> r.id().equals("custom-open-all")
            && r.canStart() && r.statusZh().equals("仅休闲可用")), "真实服务中的卡盒未显示休闲可用");
        var practice = tables.startPractice(a, aBox.id(), other.id(), "custom-open-all");
        context.assertTrue(practice.match().ruleset().id().equals(match.ruleset().id()), "开放练习未复用同一规则包");
        tables.endMatch(practice.matchId());
        boolean denied = false;
        try { tables.startPractice(a, aBox.id(), other.id(), "standard-cn-2025"); }
        catch (IllegalStateException | IllegalArgumentException expected) { denied = expected.getMessage().contains("核验")||expected.getMessage().contains("未安装"); }
        context.assertTrue(denied, "未核验简中历史档案被服务端放行");
        boxes.rename(a, aBox.id(), "失败开局未残留锁");
        context.complete();
    }

    /** Synthetic JSON, real match intents and registry-backed packet codecs inside the Fabric server. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void composedTrainerPacketsAndResolution(TestContext context) {
        composedTrainerPacketsAndResolution(context, 0);
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void resumableTrainerPacketsAndResolution(TestContext context) {
        composedTrainerPacketsAndResolution(context, 1);
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void privateZoneTrainerPacketsAndResolution(TestContext context) {
        composedTrainerPacketsAndResolution(context, 2);
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void revealedSearchTrainerPacketsAndResolution(TestContext context) {
        composedTrainerPacketsAndResolution(context, 3);
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void energyAttachmentTrainerPacketsAndResolution(TestContext context) {
        composedTrainerPacketsAndResolution(context, 4);
    }
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void energyTriggeredAbilityPacketsAndResolution(TestContext context) {
        composedTrainerPacketsAndResolution(context, 5);
    }
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void checkupTriggeredAbilityPacketsAndResolution(TestContext context) {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),watch=UUID.randomUUID();
        var ability=BundledCombatData.ability(com.google.gson.JsonParser.parseString("""
            {"id":"checkup","nameZh":"回合间验证（非真实卡）","kind":"TRIGGERED","effectSupport":"SUPPORTED",
             "eventPolicy":{"trigger":"ON_POKEMON_CHECKUP","optional":true,"usage":"NONE"},
             "triggers":[{"trigger":"ON_POKEMON_CHECKUP","conditions":[{"type":"IS_ACTIVE","target":"SELF"}],"effects":[
               {"type":"damage_counter","target":"OPPONENT_ACTIVE","amount":1},{"type":"draw","amount":1}]}]}
            """).getAsJsonObject(),dev.hua.ptcglive.engine.effect.EffectRegistry.standard());
        var database=new CardDatabase(List.of(new CardDefinition("checkup-basic","回合间测试卡",CardCategory.POKEMON,TrainerCategory.NONE,
            "H",Set.of("international"),true,false,60,Map.of(),EffectSupport.SUPPORTED,null)));
        var combat=new CombatCatalog(Map.of("checkup-basic",new dev.hua.ptcglive.engine.field.PokemonProfile(120,0,null,null,0,null,0,1,List.of(),List.of(ability))),Map.of());
        var decks=new ArrayList<List<CardInstance>>();
        for(UUID owner:List.of(a,b)) {
            var deck=new ArrayList<CardInstance>();for(int i=0;i<60;i++)deck.add(new CardInstance(UUID.randomUUID(),owner,"checkup-basic"));decks.add(deck);
        }
        var m=new ServerMatch(a,b,decks.get(0),decks.get(1),PtcgServices.rulesets().get(RULE),database,combat,new ServerRandom(99));
        m.callCoin(a,CoinSide.HEADS);UUID winner=m.publicCoinResult()==CoinSide.HEADS?a:b;m.chooseWhoGoesFirst(winner,winner.equals(a));
        for(UUID seat:List.of(a,b)) { m.chooseOpeningActive(seat,m.privateView(seat).hand().getFirst().instanceId());m.placeOpeningBench(seat,m.privateView(seat).hand().getFirst().instanceId()); }
        var session=MatchSession.multiplayer(m,a,b);session.addSpectator(watch);completeSetup(session,a,b,a,b);
        m.placeDamageCountersByEffect(a,b,m.activePokemonView(b).pokemonId(),11);
        var wire=new RegistryByteBuf(Unpooled.buffer(),context.getWorld().getRegistryManager());
        try {
            var start=session.view(a,a);var end=start.options().stream().filter(o->o.labelZh().equals("结束回合")).findFirst().orElseThrow();
            MatchIntentPayload.CODEC.encode(wire,new MatchIntentPayload(m.id().toString(),a.toString(),Math.toIntExact(start.revision()),end.index()));
            var intent=MatchIntentPayload.CODEC.decode(wire);session.submit(a,a,intent.revision(),intent.option());
            context.assertTrue(m.phase()==MatchPhase.POKEMON_CHECKUP&&m.triggerOrderPrompt().choosingPlayer().equals(b),"实际消息未进入下一玩家排序的检查阶段");
            int safety=20;
            while(m.triggerOrderPrompt()!=null&&safety-->0) {
                var prompt=m.triggerOrderPrompt();UUID owner=prompt.choosingPlayer();var view=session.view(owner,owner);var random=m.randomTranscript();
                for(UUID recipient:List.of(a,b,watch)) {
                    wire.clear();MatchStatePayload.CODEC.encode(wire,new MatchStatePayload(false,new Gson().toJson(session.view(recipient,recipient.equals(watch)?null:recipient)),"回合间检查"));
                    String json=MatchStatePayload.CODEC.decode(wire).snapshotJson();var parsed=new Gson().fromJson(json,MatchSession.Snapshot.class);
                    context.assertTrue(parsed.game().phase()==MatchPhase.POKEMON_CHECKUP&&prompt.equals(parsed.game().triggerOrder()),"回合间状态消息丢失公开排序");
                    UUID hiddenOwner=recipient.equals(a)?b:a;
                    for(var hidden:m.privateView(hiddenOwner).hand())context.assertTrue(!json.contains(hidden.instanceId().toString()),"回合间泄露手牌实例");
                    for(var hidden:m.privateView(hiddenOwner).deck())context.assertTrue(!json.contains(hidden.instanceId().toString()),"回合间泄露牌库顺序");
                    for(var hidden:m.privateView(hiddenOwner).prizes())context.assertTrue(!json.contains(hidden.instanceId().toString()),"回合间泄露奖赏内容");
                }
                context.assertTrue(random.equals(m.randomTranscript()),"重新同步重放了检查随机数");
                var action=view.options().stream().filter(o->o.labelZh().startsWith(prompt.optionalDecision()?"使用触发特性":"优先结算触发")).findFirst().orElseThrow();
                wire.clear();MatchIntentPayload.CODEC.encode(wire,new MatchIntentPayload(m.id().toString(),owner.toString(),Math.toIntExact(view.revision()),action.index()));
                var decoded=MatchIntentPayload.CODEC.decode(wire);boolean forged=false;
                try{session.submit(owner.equals(a)?b:a,owner,decoded.revision(),decoded.option());}catch(IllegalArgumentException expected){forged=true;}
                context.assertTrue(forged,"另一方可伪造回合间排序／确认");session.submit(owner,owner,decoded.revision(),decoded.option());
                boolean replay=false;try{session.submit(owner,owner,decoded.revision(),decoded.option());}catch(IllegalStateException expected){replay=true;}
                context.assertTrue(replay,"重复消息再次结算回合间效果");
            }
            context.assertTrue(safety>0&&m.phase()==MatchPhase.PRIZE_SELECTION&&m.activePokemonView(a).damage()==10,"回合间未完整结算双方触发再击倒");
            m.takePrizeAt(a,0);m.chooseReplacement(b,m.benchPokemonView(b).getFirst().pokemonId());
            context.assertTrue(m.currentPlayer().equals(b)&&m.turnNumber()==2&&m.triggerOrderPrompt()==null,"拿奖替补后没有恰好进入下一回合");
        } finally { wire.release(); }
        context.complete();
    }
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void damageAllocationPacketsAndResolution(TestContext context) {
        composedTrainerPacketsAndResolution(context, 6);
    }
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void damageTriggeredAbilityPacketsAndResolution(TestContext context) {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),watch=UUID.randomUUID();
        var registry=dev.hua.ptcglive.engine.effect.EffectRegistry.standard();
        var json=com.google.gson.JsonParser.parseString("""
            {"id":"save","nameZh":"受伤回复验证（非真实卡）","kind":"TRIGGERED","effectSupport":"SUPPORTED",
             "eventPolicy":{"trigger":"ON_DAMAGE","from":"OPPONENT_ATTACK","activeOnly":true,"lethalOnly":true,"optional":true,"usage":"NONE"},
             "effects":[{"type":"heal","target":"SELF","mode":"MINIMUM_HP","amount":10}]}
            """).getAsJsonObject();
        var save=BundledCombatData.ability(json,registry);
        json.addProperty("id","draw");json.addProperty("nameZh","受伤抽牌验证（非真实卡）");json.getAsJsonObject("eventPolicy").addProperty("optional",false);
        json.add("effects",com.google.gson.JsonParser.parseString("[{\"type\":\"draw\",\"amount\":1}]"));
        var draw=BundledCombatData.ability(json,registry);
        var attack=BundledCombatData.attack(com.google.gson.JsonParser.parseString("""
            {"id":"hit","nameZh":"伤害验证","energyCost":{},"baseDamage":500,"effectSupport":"SUPPORTED"}
            """).getAsJsonObject(),registry);
        var database=new CardDatabase(List.of(new CardDefinition("damage-basic","受伤测试卡",CardCategory.POKEMON,TrainerCategory.NONE,
            "H",Set.of("international"),true,false,60,Map.of(),EffectSupport.SUPPORTED,null)));
        var combat=new CombatCatalog(Map.of("damage-basic",new dev.hua.ptcglive.engine.field.PokemonProfile(300,0,null,null,0,null,0,1,List.of(attack),List.of(save,draw))),Map.of());
        var decks=new ArrayList<List<CardInstance>>();
        for(UUID owner:List.of(a,b)) {var deck=new ArrayList<CardInstance>();for(int i=0;i<60;i++)deck.add(new CardInstance(UUID.randomUUID(),owner,"damage-basic"));decks.add(deck);}
        var m=new ServerMatch(a,b,decks.get(0),decks.get(1),PtcgServices.rulesets().get(RULE),database,combat,new ServerRandom(99));
        m.callCoin(a,CoinSide.HEADS);UUID winner=m.publicCoinResult()==CoinSide.HEADS?a:b;m.chooseWhoGoesFirst(winner,winner.equals(a));
        for(UUID seat:List.of(a,b)) {m.chooseOpeningActive(seat,m.privateView(seat).hand().getFirst().instanceId());m.placeOpeningBench(seat,m.privateView(seat).hand().getFirst().instanceId());}
        var session=MatchSession.multiplayer(m,a,b);session.addSpectator(watch);completeSetup(session,a,b,a,b);m.endTurn(a);m.endTurn(b);
        int hand=m.privateView(b).hand().size();UUID original=m.activePokemonView(b).pokemonId();
        var wire=new RegistryByteBuf(Unpooled.buffer(),context.getWorld().getRegistryManager());
        try {
            var start=session.view(a,a);var option=start.options().stream().filter(o->o.labelZh().startsWith("攻击")&&o.labelZh().contains("伤害验证")).findFirst().orElseThrow();
            MatchIntentPayload.CODEC.encode(wire,new MatchIntentPayload(m.id().toString(),a.toString(),Math.toIntExact(start.revision()),option.index()));
            var initial=MatchIntentPayload.CODEC.decode(wire);session.submit(a,a,initial.revision(),initial.option());
            context.assertTrue(m.activePokemonView(b).damage()==500&&m.triggerOrderPrompt()!=null&&m.triggerOrderPrompt().choosingPlayer().equals(b),"致命伤害未在KO前等待受伤方");
            int safety=10;
            while(m.triggerOrderPrompt()!=null&&safety-->0) {
                var prompt=m.triggerOrderPrompt();var view=session.view(b,b);var random=m.randomTranscript();
                context.assertTrue(prompt.choosingPlayer().equals(b)&&prompt.trigger()==dev.hua.ptcglive.engine.effect.EffectTrigger.ON_DAMAGE,"排序者或公开事件类型错误");
                for(UUID recipient:List.of(a,b,watch)) {
                    wire.clear();MatchStatePayload.CODEC.encode(wire,new MatchStatePayload(false,new Gson().toJson(session.view(recipient,recipient.equals(watch)?null:recipient)),"受到攻击伤害"));
                    String body=MatchStatePayload.CODEC.decode(wire).snapshotJson();var snapshot=new Gson().fromJson(body,MatchSession.Snapshot.class);
                    context.assertTrue(prompt.equals(snapshot.game().triggerOrder()),"消息重建丢失受伤确认／排序");
                    if(!recipient.equals(b))for(var hidden:m.privateView(b).hand())context.assertTrue(!body.contains(hidden.instanceId().toString()),"受伤等待泄露手牌");
                    for(var hidden:m.privateView(b).deck())context.assertTrue(!body.contains(hidden.instanceId().toString()),"受伤等待泄露牌库");
                }
                context.assertTrue(random.equals(m.randomTranscript()),"刷新受伤提示消耗随机");
                var chosen=view.options().stream().filter(o->o.labelZh().startsWith(prompt.optionalDecision()?"使用触发特性":"优先结算触发")&&o.labelZh().contains("回复")).findFirst().orElseThrow();
                wire.clear();MatchIntentPayload.CODEC.encode(wire,new MatchIntentPayload(m.id().toString(),b.toString(),Math.toIntExact(view.revision()),chosen.index()));
                var decoded=MatchIntentPayload.CODEC.decode(wire);boolean forged=false;
                try{session.submit(a,b,decoded.revision(),decoded.option());}catch(IllegalArgumentException expected){forged=true;}
                context.assertTrue(forged,"攻击者替受伤方决定效果");session.submit(b,b,decoded.revision(),decoded.option());
                boolean replay=false;try{session.submit(b,b,decoded.revision(),decoded.option());}catch(IllegalStateException expected){replay=true;}
                context.assertTrue(replay,"重复消息再次回复或抽牌");
            }
            context.assertTrue(safety>0&&m.phase()==MatchPhase.TURN&&m.currentPlayer().equals(b)&&m.turnNumber()==4,"受伤触发后未推进一次");
            context.assertTrue(m.activePokemonView(b).pokemonId().equals(original)&&m.activePokemonView(b).damage()==290&&!m.lastAttack().knockedOut(),"过量伤害没有保留10HP或仍标记击倒");
            context.assertTrue(m.privateView(b).hand().size()==hand+2,"触发及回合抽牌数量错误");
        } finally {wire.release();}
        context.complete();
    }
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void knockoutTriggeredAbilityPacketsAndResolution(TestContext context) {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),watch=UUID.randomUUID();
        var registry=dev.hua.ptcglive.engine.effect.EffectRegistry.standard();
        var json=com.google.gson.JsonParser.parseString("""
            {"id":"reflect","nameZh":"击倒反击验证（非真实卡）","kind":"TRIGGERED","effectSupport":"SUPPORTED",
             "eventPolicy":{"trigger":"ON_KNOCKOUT","cause":"ANY","activeOnly":true,"optional":true,"usage":"NONE"},
             "effects":[{"type":"damage_counter","target":"OPPONENT_ACTIVE","amount":30}]}
            """).getAsJsonObject();
        var reflect=BundledCombatData.ability(json,registry);
        json.addProperty("id","draw");json.addProperty("nameZh","击倒抽牌验证（非真实卡）");json.getAsJsonObject("eventPolicy").addProperty("optional",false);
        json.add("effects",com.google.gson.JsonParser.parseString("[{\"type\":\"draw\",\"amount\":1}]"));
        var draw=BundledCombatData.ability(json,registry);
        var attack=BundledCombatData.attack(com.google.gson.JsonParser.parseString("""
            {"id":"hit","nameZh":"KO验证攻击","energyCost":{},"baseDamage":300,"effectSupport":"SUPPORTED"}
            """).getAsJsonObject(),registry);
        var database=new CardDatabase(List.of(new CardDefinition("ko-basic","击倒测试卡",CardCategory.POKEMON,TrainerCategory.NONE,
            "H",Set.of("international"),true,false,60,Map.of(),EffectSupport.SUPPORTED,null)));
        var combat=new CombatCatalog(Map.of("ko-basic",new dev.hua.ptcglive.engine.field.PokemonProfile(300,0,null,null,0,null,0,1,List.of(attack),List.of(reflect,draw))),Map.of());
        var decks=new ArrayList<List<CardInstance>>();
        for(UUID owner:List.of(a,b)) {var deck=new ArrayList<CardInstance>();for(int i=0;i<60;i++)deck.add(new CardInstance(UUID.randomUUID(),owner,"ko-basic"));decks.add(deck);}
        var m=new ServerMatch(a,b,decks.get(0),decks.get(1),PtcgServices.rulesets().get(RULE),database,combat,new ServerRandom(103));
        m.callCoin(a,CoinSide.HEADS);UUID winner=m.publicCoinResult()==CoinSide.HEADS?a:b;m.chooseWhoGoesFirst(winner,winner.equals(a));
        for(UUID seat:List.of(a,b)) {m.chooseOpeningActive(seat,m.privateView(seat).hand().getFirst().instanceId());m.placeOpeningBench(seat,m.privateView(seat).hand().getFirst().instanceId());}
        var session=MatchSession.multiplayer(m,a,b);session.addSpectator(watch);completeSetup(session,a,b,a,b);m.endTurn(a);m.endTurn(b);
        int aHand=m.privateView(a).hand().size(),bHand=m.privateView(b).hand().size();
        var wire=new RegistryByteBuf(Unpooled.buffer(),context.getWorld().getRegistryManager());
        try {
            var start=session.view(a,a);var attackOption=start.options().stream().filter(o->o.labelZh().startsWith("攻击")&&o.labelZh().contains("KO验证攻击")).findFirst().orElseThrow();
            MatchIntentPayload.CODEC.encode(wire,new MatchIntentPayload(m.id().toString(),a.toString(),Math.toIntExact(start.revision()),attackOption.index()));
            var intent=MatchIntentPayload.CODEC.decode(wire);session.submit(a,a,intent.revision(),intent.option());
            context.assertTrue(m.triggerOrderPrompt()!=null&&m.triggerOrderPrompt().choosingPlayer().equals(b)&&m.activePokemonView(b).damage()==300,"击倒来源在效果前离场或排序者错误");
            int safety=12;
            while(m.triggerOrderPrompt()!=null&&safety-->0) {
                var prompt=m.triggerOrderPrompt();UUID owner=prompt.choosingPlayer();var view=session.view(owner,owner);var random=m.randomTranscript();
                context.assertTrue(prompt.trigger()==dev.hua.ptcglive.engine.effect.EffectTrigger.ON_KNOCKOUT,"公开提示丢失KO事件");
                context.assertTrue(m.privateView(a).discard().isEmpty()&&m.privateView(b).discard().isEmpty(),"同时KO来源未等待全部触发");
                for(UUID recipient:List.of(a,b,watch)) {
                    wire.clear();MatchStatePayload.CODEC.encode(wire,new MatchStatePayload(false,new Gson().toJson(session.view(recipient,recipient.equals(watch)?null:recipient)),"击倒反应"));
                    String body=MatchStatePayload.CODEC.decode(wire).snapshotJson();var decoded=new Gson().fromJson(body,MatchSession.Snapshot.class);
                    context.assertTrue(prompt.equals(decoded.game().triggerOrder()),"KO提示消息往返不一致");
                    for(UUID seat:List.of(a,b)) {
                        if(!recipient.equals(seat))for(var hidden:m.privateView(seat).hand())context.assertTrue(!body.contains(hidden.instanceId().toString()),"KO等待泄露对手手牌");
                        for(var hidden:m.privateView(seat).deck())context.assertTrue(!body.contains(hidden.instanceId().toString()),"KO等待泄露牌库");
                        for(var hidden:m.privateView(seat).prizes())context.assertTrue(!body.contains(hidden.instanceId().toString()),"KO等待泄露奖赏");
                    }
                }
                context.assertTrue(random.equals(m.randomTranscript()),"KO刷新重放随机");
                var option=view.options().stream().filter(o->prompt.optionalDecision()
                    ? o.labelZh().startsWith(owner.equals(b)?"使用触发特性":"放弃触发特性")
                    : o.labelZh().startsWith("优先结算触发")&&o.labelZh().contains("抽牌")).findFirst().orElseThrow();
                wire.clear();MatchIntentPayload.CODEC.encode(wire,new MatchIntentPayload(m.id().toString(),owner.toString(),Math.toIntExact(view.revision()),option.index()));
                var decoded=MatchIntentPayload.CODEC.decode(wire);boolean forged=false;
                try{session.submit(owner.equals(a)?b:a,owner,decoded.revision(),decoded.option());}catch(IllegalArgumentException expected){forged=true;}
                context.assertTrue(forged,"KO确认可伪造席位");session.submit(owner,owner,decoded.revision(),decoded.option());
                boolean replay=false;try{session.submit(owner,owner,decoded.revision(),decoded.option());}catch(IllegalStateException expected){replay=true;}
                context.assertTrue(replay,"旧KO请求可重复反击或抽牌");
            }
            context.assertTrue(safety>0&&m.phase()==MatchPhase.PRIZE_SELECTION&&m.privateView(a).discard().size()==1&&m.privateView(b).discard().size()==1,"触发后未同时移场与拿奖");
            context.assertTrue(m.privateView(a).hand().size()==aHand+1&&m.privateView(b).hand().size()==bHand+1,"被KO来源的抽牌未恰好一次");
            int steps=6;while(m.phase()==MatchPhase.PRIZE_SELECTION&&steps-->0)m.takePrizeAt(m.pendingChoicePlayer(),0);
            while(m.phase()==MatchPhase.CHOOSE_REPLACEMENT&&steps-->0) {UUID seat=m.pendingChoicePlayer();m.chooseReplacement(seat,m.benchPokemonView(seat).getFirst().pokemonId());}
            context.assertTrue(steps>0&&m.phase()==MatchPhase.TURN&&m.currentPlayer().equals(b)&&m.turnNumber()==4,"反击双方替补后未恰好推进一次");
        } finally {wire.release();}
        context.complete();
    }
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void eventTargetBindingPacketsAndResolution(TestContext context) {
        eventTargetBindingPacketsAndResolution(context,false);
    }
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void departedTriggerSourcePacketsAndResolution(TestContext context) {
        eventTargetBindingPacketsAndResolution(context,true);
    }
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void automaticTriggerContinuationPacketsAndResolution(TestContext context) {
        eventTargetBindingPacketsAndResolution(context,false,true);
    }
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void automaticPokemonSelectionPacketsAndResolution(TestContext context) {
        eventTargetBindingPacketsAndResolution(context,false,false,true);
    }
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void scopedPokemonSelectionPacketsAndResolution(TestContext context) {
        eventTargetBindingPacketsAndResolution(context,false,false,true,true);
    }
    private void eventTargetBindingPacketsAndResolution(TestContext context,boolean reenterSource) {
        eventTargetBindingPacketsAndResolution(context,reenterSource,false);
    }
    private void eventTargetBindingPacketsAndResolution(TestContext context,boolean reenterSource,boolean resumable) {
        eventTargetBindingPacketsAndResolution(context,reenterSource,resumable,false);
    }
    private void eventTargetBindingPacketsAndResolution(TestContext context,boolean reenterSource,boolean resumable,boolean selectedPokemon) {
        eventTargetBindingPacketsAndResolution(context,reenterSource,resumable,selectedPokemon,false);
    }
    private void eventTargetBindingPacketsAndResolution(TestContext context,boolean reenterSource,boolean resumable,boolean selectedPokemon,boolean scoped) {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),watch=UUID.randomUUID();
        var registry=dev.hua.ptcglive.engine.effect.EffectRegistry.standard();
        var reaction=BundledCombatData.ability(com.google.gson.JsonParser.parseString("""
            {"id":"watch","nameZh":"己方击倒监听（非真实卡）","kind":"TRIGGERED","effectSupport":"SUPPORTED",
             "eventPolicy":{"trigger":"ON_KNOCKOUT","watch":"OWN_FIELD","cause":"OPPONENT_ATTACK_DAMAGE","activeOnly":true,"optional":true,"usage":"NONE"},
             "triggers":[{"trigger":"ON_KNOCKOUT","conditions":[{"type":"IS_ACTIVE","target":"TRIGGER_POKEMON"}],
             "effects":[{"type":"damage_counter","target":"ATTACKING_POKEMON","amount":1}%s]}]}
            """.formatted(reenterSource?",{\"type\":\"heal\",\"target\":\"SELF\",\"amount\":10}":resumable?"""
                ,{"type":"choice","effects":[{"type":"search_deck","target":"DECK","amount":1,"minimum":1},{"type":"draw","amount":1}]},
                {"type":"draw","amount":1}
                """:scoped?"""
                ,{"type":"select_pokemon","target":"SELECT_OPPONENT_POKEMON","position":"BENCH","effects":[
                  {"type":"damage_counter","target":"CHOSEN_POKEMON","amount":1},
                  {"type":"select_pokemon","target":"SELECT_OWN_POKEMON","position":"BENCH","effects":[{"type":"damage_counter","target":"CHOSEN_POKEMON","amount":1}]},
                  {"type":"damage_counter","target":"CHOSEN_POKEMON","amount":1},
                  {"type":"conditional","conditions":[{"type":"DAMAGE_COUNTER_COUNT","target":"CHOSEN_POKEMON","value":2}],"effects":[{"type":"draw","amount":1}]}]}
                """:selectedPokemon?"""
                ,{"type":"damage_counter","target":"SELECT_OPPONENT_POKEMON","amount":1},
                {"type":"damage_counter","target":"SELECT_OWN_POKEMON","amount":1},
                {"type":"damage_counter","target":"SELECT_OPPONENT_POKEMON","amount":1},
                {"type":"draw","amount":1}
                """:"")).getAsJsonObject(),registry);
        var attack=BundledCombatData.attack(com.google.gson.JsonParser.parseString("""
            {"id":"hit","nameZh":"事件绑定验证攻击","energyCost":{},"baseDamage":300,"effectSupport":"SUPPORTED"}
            """).getAsJsonObject(),registry);
        var database=new CardDatabase(List.of(new CardDefinition("event-basic","事件测试卡",CardCategory.POKEMON,TrainerCategory.NONE,
            "H",Set.of("international"),true,false,60,Map.of(),EffectSupport.SUPPORTED,null)));
        var combat=new CombatCatalog(Map.of("event-basic",new dev.hua.ptcglive.engine.field.PokemonProfile(300,0,null,null,0,null,0,1,List.of(attack),List.of(reaction))),Map.of());
        var decks=new ArrayList<List<CardInstance>>();
        for(UUID owner:List.of(a,b)) {var deck=new ArrayList<CardInstance>();for(int i=0;i<60;i++)deck.add(new CardInstance(UUID.randomUUID(),owner,"event-basic"));decks.add(deck);}
        var m=new ServerMatch(a,b,decks.get(0),decks.get(1),PtcgServices.rulesets().get(RULE),database,combat,new ServerRandom(107));
        m.callCoin(a,CoinSide.HEADS);UUID winner=m.publicCoinResult()==CoinSide.HEADS?a:b;m.chooseWhoGoesFirst(winner,winner.equals(a));
        for(UUID seat:List.of(a,b)) {m.chooseOpeningActive(seat,m.privateView(seat).hand().getFirst().instanceId());m.placeOpeningBench(seat,m.privateView(seat).hand().getFirst().instanceId());}
        var session=MatchSession.multiplayer(m,a,b);session.addSpectator(watch);completeSetup(session,a,b,a,b);m.endTurn(a);m.endTurn(b);
        UUID attacker=m.activePokemonView(a).pokemonId(),victim=m.activePokemonView(b).pokemonId();
        var wire=new RegistryByteBuf(Unpooled.buffer(),context.getWorld().getRegistryManager());
        try {
            var start=session.view(a,a);var attackOption=start.options().stream().filter(o->o.labelZh().startsWith("攻击")&&o.labelZh().contains("事件绑定验证攻击")).findFirst().orElseThrow();
            MatchIntentPayload.CODEC.encode(wire,new MatchIntentPayload(m.id().toString(),a.toString(),Math.toIntExact(start.revision()),attackOption.index()));
            var intent=MatchIntentPayload.CODEC.decode(wire);session.submit(a,a,intent.revision(),intent.option());
            context.assertTrue(m.triggerOrderPrompt()!=null&&m.triggerOrderPrompt().entries().size()==2,"击倒未捕获同方战斗与备战监听者");
            UUID oldSource=m.benchPokemonView(b).getFirst().pokemonId();
            if(reenterSource) {
                // Test-only field transition, not a client-authorized action or a claim about a particular card.
                try {
                    var member=ServerMatch.class.getDeclaredField("battlefield");member.setAccessible(true);
                    var field=(dev.hua.ptcglive.engine.field.BattlefieldState)member.get(m);
                    var removed=field.removePokemonByEffect(b,oldSource);
                    field.placeOnBench(b,removed.discardedCards().getFirst());
                    UUID reentered=field.benchView(b).getLast().pokemonId();
                    context.assertTrue(!reentered.equals(oldSource),"同一张卡重新登场复用了旧登场身份");
                    field.placeDamageCounters(b,reentered,2);
                } catch(ReflectiveOperationException invalid) {throw new AssertionError(invalid);}
            }
            int safety=24,confirmations=0,interactions=0;int handBefore=m.privateView(b).hand().size();
            while((m.triggerOrderPrompt()!=null||m.hasPendingEffectChoice())&&safety-->0) {
                if(m.hasPendingEffectChoice()) {
                    context.assertTrue((resumable||selectedPokemon)&&m.pendingChoicePlayer().equals(b)&&m.currentPlayer().equals(a)&&m.phase()==MatchPhase.TURN,"自动续接错误转移回合或选择权");
                    context.assertTrue(m.activePokemonView(b).pokemonId().equals(victim),"选择结束前提前移除KO来源");
                    var view=session.view(b,b);var random=m.randomTranscript();
                    for(UUID recipient:List.of(a,b,watch)) {
                        wire.clear();MatchStatePayload.CODEC.encode(wire,new MatchStatePayload(false,new Gson().toJson(session.view(recipient,recipient.equals(watch)?null:recipient)),"自动特性选择"));
                        String body=MatchStatePayload.CODEC.decode(wire).snapshotJson();var snapshot=new Gson().fromJson(body,MatchSession.Snapshot.class);
                        context.assertTrue(snapshot.game().choicePlayer().equals(b),"公开选择席位仍为当前回合玩家");
                        if(selectedPokemon) {
                            context.assertTrue(!body.contains("entrySequence")&&!body.contains("effectProgram"),"宝可梦选择泄露内部账本或程序");
                            context.assertTrue(dev.hua.ptcglive.view.MatchActionMatcher.hasPokemonChoice(snapshot)==recipient.equals(b),"卡桌点选标记未正确限定拥有者");
                            if(!recipient.equals(b))context.assertTrue(snapshot.options().stream().noneMatch(o->o.labelZh().startsWith("选择效果目标")),"非拥有者收到可执行目标列表");
                        }
                        for(UUID seat:List.of(a,b)) {
                            if(!recipient.equals(seat))for(var hidden:m.privateView(seat).hand())context.assertTrue(!body.contains(hidden.instanceId().toString()),"自动续接泄露手牌");
                            if(selectedPokemon||!recipient.equals(seat))for(var hidden:m.privateView(seat).deck())context.assertTrue(!body.contains(hidden.instanceId().toString()),"自动续接泄露私有检索候选");
                            for(var hidden:m.privateView(seat).prizes())context.assertTrue(!body.contains(hidden.instanceId().toString()),"自动续接泄露奖赏");
                        }
                    }
                    context.assertTrue(random.equals(m.randomTranscript()),"刷新自动续接改变随机记录");
                    UUID selectedTarget=scoped?(interactions%2==0?m.benchPokemonView(a).getFirst().pokemonId():m.benchPokemonView(b).getFirst().pokemonId()):
                        interactions%3==0?m.benchPokemonView(a).getFirst().pokemonId():interactions%3==1?m.benchPokemonView(b).getFirst().pokemonId():attacker;
                    var next=view.options().stream().filter(o->selectedPokemon?
                        o.labelZh().startsWith("选择效果目标")&&o.cardKeys().contains("field:"+selectedTarget):
                        o.labelZh().startsWith(m.hasPendingCardSelection()?
                        (m.effectCardPrompt(b).selected().isEmpty()?"选择卡牌":"确认选牌"):"选择效果 1")).findFirst().orElseThrow();
                    wire.clear();MatchIntentPayload.CODEC.encode(wire,new MatchIntentPayload(m.id().toString(),b.toString(),Math.toIntExact(view.revision()),next.index()));
                    var decision=MatchIntentPayload.CODEC.decode(wire);boolean forged=false;
                    try{session.submit(a,b,decision.revision(),decision.option());}catch(IllegalArgumentException expected){forged=true;}
                    context.assertTrue(forged,"攻击者伪造自动特性选择成功");
                    if(selectedPokemon) {
                        boolean spectatorRejected=false;
                        try{session.submit(watch,b,decision.revision(),decision.option());}catch(IllegalArgumentException expected){spectatorRejected=true;}
                        context.assertTrue(spectatorRejected,"旁观者伪造宝可梦选择成功");
                    }
                    session.submit(b,b,decision.revision(),decision.option());
                    boolean stale=false;try{session.submit(b,b,decision.revision(),decision.option());}catch(IllegalStateException expected){stale=true;}
                    context.assertTrue(stale,"自动续接重复消息重复执行步骤");interactions++;continue;
                }
                var prompt=m.triggerOrderPrompt();UUID chooser=prompt.choosingPlayer();var view=session.view(chooser,chooser);var random=m.randomTranscript();
                for(var entry:prompt.entries()) {
                    context.assertTrue(entry.owner().equals(b)&&entry.triggerPokemon().owner().equals(b)&&entry.triggerPokemon().pokemonId().equals(victim),"触发对象被误绑为监听来源");
                    context.assertTrue(entry.attackingPokemon().owner().equals(a)&&entry.attackingPokemon().pokemonId().equals(attacker),"实际攻击者绑定错误");
                }
                for(UUID recipient:List.of(a,b,watch)) {
                    var sent=session.view(recipient,recipient.equals(watch)?null:recipient);
                    wire.clear();MatchStatePayload.CODEC.encode(wire,new MatchStatePayload(false,new Gson().toJson(sent),"事件对象"));
                    String body=MatchStatePayload.CODEC.decode(wire).snapshotJson();var decoded=new Gson().fromJson(body,MatchSession.Snapshot.class);
                    context.assertTrue(sent.game().triggerOrder().equals(decoded.game().triggerOrder()),"消息往返丢失公开事件对象");
                    var departedEntry=prompt.entries().stream().filter(source->oldSource.equals(source.pokemonId())).findFirst();
                    if(reenterSource&&departedEntry.isPresent())context.assertTrue(decoded.game().triggerOrder().entries().stream()
                        .filter(entry->entry.id().equals(departedEntry.get().id())).allMatch(entry->entry.pokemonId()==null),
                        "已离场来源的旧登场身份仍通过公共数据包暴露");
                    context.assertTrue(!body.contains("sourceEntrySequence")&&!body.contains("effectProgram"),"公开提示泄露内部登场账本或效果程序");
                    for(UUID seat:List.of(a,b)) {
                        if(!recipient.equals(seat))for(var hidden:m.privateView(seat).hand())context.assertTrue(!body.contains(hidden.instanceId().toString()),"事件对象同步泄露手牌");
                        for(var hidden:m.privateView(seat).deck())context.assertTrue(!body.contains(hidden.instanceId().toString()),"事件对象同步泄露牌库");
                        for(var hidden:m.privateView(seat).prizes())context.assertTrue(!body.contains(hidden.instanceId().toString()),"事件对象同步泄露奖赏");
                    }
                }
                context.assertTrue(random.equals(m.randomTranscript()),"事件提示刷新重复随机");
                var option=view.options().stream().filter(o->o.labelZh().startsWith(prompt.optionalDecision()?"使用触发特性":"优先结算触发")).findFirst().orElseThrow();
                context.assertTrue(option.labelZh().contains("事件对象"),"中文操作缺少被监听对象说明");
                if(reenterSource&&prompt.optionalDecision()&&prompt.entries().getFirst().pokemonId().equals(oldSource))
                    context.assertTrue(option.labelZh().contains("原特性来源已离场"),"同一卡重新登场掩盖旧来源离场事实");
                if(prompt.optionalDecision()) {context.assertTrue(chooser.equals(b),"监听的可选确认未归拥有者");confirmations++;}
                else context.assertTrue(chooser.equals(b),"受伤宝可梦的同时触发未归拥有者排序");
                wire.clear();MatchIntentPayload.CODEC.encode(wire,new MatchIntentPayload(m.id().toString(),chooser.toString(),Math.toIntExact(view.revision()),option.index()));
                var decoded=MatchIntentPayload.CODEC.decode(wire);boolean forged=false;
                try{session.submit(chooser.equals(a)?b:a,chooser,decoded.revision(),decoded.option());}catch(IllegalArgumentException expected){forged=true;}
                context.assertTrue(forged,"事件对象可通过伪造席位替换");session.submit(chooser,chooser,decoded.revision(),decoded.option());
                boolean replay=false;try{session.submit(chooser,chooser,decoded.revision(),decoded.option());}catch(IllegalStateException expected){replay=true;}
                context.assertTrue(replay,"旧事件选择可重复结算");
            }
            context.assertTrue(safety>0&&confirmations==2&&m.activePokemonView(a).damage()==(scoped?20:selectedPokemon?40:20)&&m.benchPokemonView(a).getFirst().damage()==(scoped?40:selectedPokemon?20:0),"两个监听者未各命中真正攻击者和独立目标一次");
            context.assertTrue(m.phase()==MatchPhase.PRIZE_SELECTION&&m.privateView(b).discard().size()==1,"监听完成后未继续统一击倒");
            if(resumable)context.assertTrue(interactions==6&&m.privateView(b).hand().size()==handBefore+4,"两来源的三步选择／抽牌未各执行一次");
            if(selectedPokemon)context.assertTrue(interactions==(scoped?4:6)&&m.privateView(b).hand().size()==handBefore+2&&m.benchPokemonView(b).getFirst().damage()==20,"独立选择或嵌套目标恢复错误，或中断后续抽牌");
            if(reenterSource)context.assertTrue(m.benchPokemonView(b).getFirst().damage()==20,"旧SELF效果错误回复新登场实例");
            m.takePrizeAt(a,0);m.chooseReplacement(b,m.benchPokemonView(b).getFirst().pokemonId());
            context.assertTrue(m.turnNumber()==4&&m.currentPlayer().equals(b),"事件绑定破坏替补后的回合推进");
        } finally {wire.release();}
        context.complete();
    }
    private void composedTrainerPacketsAndResolution(TestContext context, int selectionMode) {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), observer = UUID.randomUUID();
        var trainerJson = com.google.gson.JsonParser.parseString("""
            {"trainerCategory":"ITEM","effects":[
              {"type":"heal","target":"SELECT_OWN_POKEMON","amount":10},
              {"type":"damage_counter","target":"OPPONENT_ACTIVE","amount":12},
              {"type":"repeat","count":2,"effects":[{"type":"draw","amount":1}]}]}
            """).getAsJsonObject();
        if (selectionMode == 1) {
            var effects = trainerJson.getAsJsonArray("effects"); effects.remove(2);
            effects.add(com.google.gson.JsonParser.parseString("""
                {"type":"choice","options":[
                  {"type":"repeat","count":2,"effects":[{"type":"draw","amount":1}]},
                  {"type":"draw","amount":3}]}
                """));
        }
        if (selectionMode >= 2) {
            var effects = trainerJson.getAsJsonArray("effects"); effects.remove(2);
            effects.add(com.google.gson.JsonParser.parseString("""
                {"type":"search_deck","target":"DECK","amount":2,"minimum":2,"card_category":"POKEMON"}
                """));
            if (selectionMode == 3) effects.get(2).getAsJsonObject().addProperty("reveal", true);
            if (selectionMode >= 4 && selectionMode <= 5) {
                effects.remove(2);
                effects.add(com.google.gson.JsonParser.parseString("""
                    {"type":"attach_energy","target":"SELECT_OWN_POKEMON","source":"DECK",
                     "amount":2,"minimum":2,"basic_energy_only":true,"energy_type":"GRASS","target_scope":"OWN_BENCH"}
                    """));
                effects.add(com.google.gson.JsonParser.parseString("{\"type\":\"draw\",\"amount\":2}"));
            }
            if (selectionMode == 6) {
                effects.remove(2);
                effects.add(com.google.gson.JsonParser.parseString("""
                    {"type":"damage_counter","distribution":"EXACT","amount":6,"target":"SELECT_OPPONENT_POKEMON",
                     "target_scope":"OPPONENT_FIELD","min_targets":1,"max_targets":6}
                    """));
                effects.add(com.google.gson.JsonParser.parseString("{\"type\":\"draw\",\"amount\":2}"));
            }
        }
        var trainer = BundledCombatData.trainer(trainerJson, dev.hua.ptcglive.engine.effect.EffectRegistry.standard());
        var database = new CardDatabase(List.of(
            new CardDefinition("test-composed-basic", "组合测试基础（非真实卡）", CardCategory.POKEMON, TrainerCategory.NONE,
                "H", Set.of("international"), true, false, 60, Map.of(), EffectSupport.SUPPORTED, null),
            new CardDefinition("test-composed-trainer", "组合测试物品（非真实卡）", CardCategory.TRAINER, TrainerCategory.ITEM,
                "H", Set.of("international"), false, false, 60, Map.of(), EffectSupport.SUPPORTED, null),
            new CardDefinition("test-composed-energy", "组合测试能量（非真实卡）", CardCategory.ENERGY, TrainerCategory.NONE,
                "H", Set.of("international"), false, true, 60, Map.of(), EffectSupport.SUPPORTED, null)));
        var abilities = new ArrayList<dev.hua.ptcglive.engine.field.AbilityDefinition>();
        if (selectionMode == 5) abilities.add(BundledCombatData.ability(com.google.gson.JsonParser.parseString("""
            {"id":"test-energy-trigger","nameZh":"附能触发夹具（非真实卡）","kind":"TRIGGERED","effectSupport":"SUPPORTED",
             "eventPolicy":{"target":"SELF","source":"DECK","cause":"EFFECT","optional":true,"usage":"ONCE_PER_TURN"},
             "triggers":[{"trigger":"ON_ENERGY_ATTACHED","effects":[{"type":"draw","amount":1}]}]}
            """).getAsJsonObject(), dev.hua.ptcglive.engine.effect.EffectRegistry.standard()));
        var combat = new CombatCatalog(Map.of("test-composed-basic",
            new dev.hua.ptcglive.engine.field.PokemonProfile(120, 0, null, null, 0, null, 0, 1, List.of(), abilities)),
            Map.of("test-composed-energy", dev.hua.ptcglive.engine.field.EnergyType.GRASS), Map.of("test-composed-trainer", trainer));
        var decks = new ArrayList<List<CardInstance>>();
        for (UUID owner : List.of(a, b)) {
            var cards = new ArrayList<CardInstance>();
            for (int i = 0; i < 60; i++) cards.add(new CardInstance(UUID.randomUUID(), owner,
                i < 36 ? "test-composed-basic" : selectionMode >= 4 && selectionMode <= 5 && i >= 48 ? "test-composed-energy" : "test-composed-trainer"));
            decks.add(cards);
        }
        ServerMatch selected = null;
        for (int seed = 0; seed < 1000; seed++) {
            var candidate = new ServerMatch(a, b, decks.get(0), decks.get(1), PtcgServices.rulesets().get(RULE), database, combat, new ServerRandom(seed));
            candidate.callCoin(a, CoinSide.HEADS); UUID winner = candidate.publicCoinResult() == CoinSide.HEADS ? a : b;
            candidate.chooseWhoGoesFirst(winner, winner.equals(a));
            if (List.of(a, b).stream().allMatch(p -> candidate.privateView(p).hand().stream().filter(c -> c.definitionId().equals("test-composed-basic")).count() >= 2)
                && candidate.privateView(a).hand().stream().anyMatch(c -> c.definitionId().equals("test-composed-trainer"))) { selected = candidate; break; }
        }
        context.assertTrue(selected != null, "组合训练家缺少开局夹具");
        var m = selected;
        for (UUID seat : List.of(a, b)) {
            var basics = m.privateView(seat).hand().stream().filter(c -> c.definitionId().equals("test-composed-basic")).toList();
            m.chooseOpeningActive(seat, basics.getFirst().instanceId()); m.placeOpeningBench(seat, basics.get(1).instanceId());
        }
        var session = MatchSession.multiplayer(m, a, b); session.addSpectator(observer); completeSetup(session, a, b, a, b);
        UUID target = m.benchPokemonView(a).getFirst().pokemonId();
        m.placeDamageCountersByEffect(a, a, target, 1);
        var card = m.privateView(a).hand().stream().filter(c -> c.definitionId().equals("test-composed-trainer")).findFirst().orElseThrow();
        var view = session.view(a, a);
        var option = view.options().stream().filter(o -> o.cardKeys().contains("hand:" + card.instanceId())
            && o.cardKeys().contains("field:" + target)).findFirst().orElseThrow();
        int before = m.privateView(a).hand().size();
        var buffer = new RegistryByteBuf(Unpooled.buffer(), context.getWorld().getRegistryManager());
        try {
            MatchIntentPayload.CODEC.encode(buffer, new MatchIntentPayload(m.id().toString(), a.toString(), Math.toIntExact(view.revision()), option.index()));
            var decoded = MatchIntentPayload.CODEC.decode(buffer);
            boolean denied = false;
            try { session.submit(b, UUID.fromString(decoded.seat()), decoded.revision(), decoded.option()); }
            catch (IllegalArgumentException expected) { denied = true; }
            context.assertTrue(denied && m.privateView(a).hand().size() == before, "对手伪造组合训练家意图被接受");
            session.submit(a, UUID.fromString(decoded.seat()), decoded.revision(), decoded.option());
            if (selectionMode == 1) {
                context.assertTrue(m.hasPendingEffectChoice() && m.phase() == MatchPhase.TURN
                    && m.privateView(a).hand().size() == before - 1 && !m.privateView(a).discard().contains(card), "暂停期间错误重复效果或提前完成训练家");
                var prompt = m.effectChoicePrompt(a); var random = m.randomTranscript(); var state = m.privateView(a);
                var waiting = session.view(a, a);
                buffer.clear();
                MatchStatePayload.CODEC.encode(buffer, new MatchStatePayload(false, new Gson().toJson(waiting), "等待选择效果"));
                var reopened = new Gson().fromJson(MatchStatePayload.CODEC.decode(buffer).snapshotJson(), MatchSession.Snapshot.class);
                context.assertTrue(reopened.options().stream().filter(o -> o.labelZh().startsWith("选择效果")).count() == 2
                    && m.effectChoicePrompt(a).equals(prompt) && m.privateView(a).equals(state) && m.randomTranscript().equals(random), "重新同步选择丢失步骤或重放前缀");
                boolean blocked = false;
                try { m.endTurn(a); } catch (IllegalStateException expected) { blocked = true; }
                context.assertTrue(blocked && session.view(b, b).options().stream().noneMatch(o -> o.labelZh().startsWith("选择效果")), "暂停可被越权操作");
                var chosen = reopened.options().stream().filter(o -> o.labelZh().startsWith("选择效果 1")).findFirst().orElseThrow();
                buffer.clear();
                MatchIntentPayload.CODEC.encode(buffer, new MatchIntentPayload(m.id().toString(), a.toString(), Math.toIntExact(reopened.revision()), chosen.index()));
                var resume = MatchIntentPayload.CODEC.decode(buffer);
                session.submit(a, UUID.fromString(resume.seat()), resume.revision(), resume.option());
                context.assertTrue(!m.hasPendingEffectChoice(), "效果续接完成后仍被锁定");
            }
            if (selectionMode == 6) {
                var prompt = m.damageAllocationPrompt(a); var random = m.randomTranscript();
                context.assertTrue(prompt.candidates().size() == 2 && m.phase() == MatchPhase.TURN
                    && m.activePokemonView(b).damage() == 120, "分配暂停前提前移除致命目标");
                for (UUID recipient : List.of(a,b,observer)) {
                    buffer.clear(); MatchStatePayload.CODEC.encode(buffer,new MatchStatePayload(false,
                        new Gson().toJson(session.view(recipient,recipient.equals(observer) ? null : recipient)),"等待分配"));
                    String json = MatchStatePayload.CODEC.decode(buffer).snapshotJson();
                    var received = new Gson().fromJson(json,MatchSession.Snapshot.class);
                    context.assertTrue(recipient.equals(a) ? prompt.equals(received.game().damageAllocation()) : received.game().damageAllocation() == null,
                        "分配草案同步给了无权接收者");
                    if (!recipient.equals(a)) for (var hidden : m.privateView(a).hand()) context.assertTrue(!json.contains(hidden.instanceId().toString()),"分配状态泄露手牌");
                }
                for (var selectedTarget : prompt.candidates()) for (int i=0;i<3;i++) {
                    var current = session.view(a,a); var action = current.options().stream().filter(o -> o.cardKeys().contains("counter:add")
                        && o.cardKeys().contains("field:"+selectedTarget.pokemonId())).findFirst().orElseThrow();
                    buffer.clear(); MatchIntentPayload.CODEC.encode(buffer,new MatchIntentPayload(m.id().toString(),a.toString(),Math.toIntExact(current.revision()),action.index()));
                    var intent = MatchIntentPayload.CODEC.decode(buffer); boolean forged=false;
                    try { session.submit(b,a,intent.revision(),intent.option()); } catch (IllegalArgumentException expected) { forged=true; }
                    context.assertTrue(forged,"对手伪造分配草案");
                    session.submit(a,a,intent.revision(),intent.option()); boolean replayed=false;
                    try { session.submit(a,a,intent.revision(),intent.option()); } catch (IllegalStateException expected) { replayed=true; }
                    context.assertTrue(replayed,"重复包重复增加指示物");
                }
                var draft = session.view(a,a); buffer.clear();
                MatchStatePayload.CODEC.encode(buffer,new MatchStatePayload(false,new Gson().toJson(draft),"重开分配"));
                var reopened = new Gson().fromJson(MatchStatePayload.CODEC.decode(buffer).snapshotJson(),MatchSession.Snapshot.class);
                context.assertTrue(reopened.game().damageAllocation().allocated()==6 && m.activePokemonView(b).damage()==120
                    && m.benchPokemonView(b).getFirst().damage()==0 && random.equals(m.randomTranscript()) && m.privateView(a).hand().size()==before-1,
                    "草案提前结算、消耗随机或刷新丢失");
                var confirm = reopened.options().stream().filter(o->o.labelZh().startsWith("确认分配")).findFirst().orElseThrow();
                session.submit(a,a,reopened.revision(),confirm.index());
                context.assertTrue(!m.hasPendingDamageAllocation() && m.benchPokemonView(b).getFirst().damage()==30,"未执行完整分配或未清除草案");
            }
            if (selectionMode >= 2 && selectionMode <= 5) {
                context.assertTrue(m.hasPendingCardSelection() && m.phase() == MatchPhase.TURN, "选牌期间提前击倒");
                var prompt = m.effectCardPrompt(a); var state = m.privateView(a); var random = m.randomTranscript();
                buffer.clear();
                MatchStatePayload.CODEC.encode(buffer, new MatchStatePayload(false, new Gson().toJson(session.view(a, a)), "等待私有选牌"));
                var reopened = new Gson().fromJson(MatchStatePayload.CODEC.decode(buffer).snapshotJson(), MatchSession.Snapshot.class);
                context.assertTrue(prompt.equals(reopened.game().effectSelection()) && state.equals(m.privateView(a)) && random.equals(m.randomTranscript()),
                    "重同步没有保留私有选牌或重复前缀");
                for (UUID recipient : List.of(b, observer)) {
                    buffer.clear();
                    MatchStatePayload.CODEC.encode(buffer, new MatchStatePayload(false, new Gson().toJson(session.view(recipient, recipient.equals(b) ? b : null)), "等待对方选择"));
                    String json = MatchStatePayload.CODEC.decode(buffer).snapshotJson();
                    for (var hidden : prompt.candidates()) context.assertTrue(!json.contains(hidden.instanceId().toString()), "选牌身份泄露到对方数据包");
                }
                for (var found : prompt.candidates().subList(0, 2)) {
                    var current = session.view(a, a);
                    var choose = current.options().stream().filter(o -> o.cardKeys().contains("effect:" + found.instanceId())).findFirst().orElseThrow();
                    buffer.clear();
                    MatchIntentPayload.CODEC.encode(buffer, new MatchIntentPayload(m.id().toString(), a.toString(), Math.toIntExact(current.revision()), choose.index()));
                    var intent = MatchIntentPayload.CODEC.decode(buffer);
                    session.submit(a, UUID.fromString(intent.seat()), intent.revision(), intent.option());
                    boolean stale = false;
                    try { session.submit(a, UUID.fromString(intent.seat()), intent.revision(), intent.option()); }
                    catch (IllegalStateException expected) { stale = true; }
                    context.assertTrue(stale, "重复切换选择意图未拒绝");
                }
                var ready = session.view(a, a);
                var confirm = ready.options().stream().filter(o -> o.labelZh().startsWith("确认选牌")).findFirst().orElseThrow();
                buffer.clear();
                MatchIntentPayload.CODEC.encode(buffer, new MatchIntentPayload(m.id().toString(), a.toString(), Math.toIntExact(ready.revision()), confirm.index()));
                var intent = MatchIntentPayload.CODEC.decode(buffer);
                session.submit(a, UUID.fromString(intent.seat()), intent.revision(), intent.option());
                if (selectionMode >= 4) {
                    var attachment = m.effectAttachmentPrompt(a);
                    context.assertTrue(m.phase() == MatchPhase.TURN && m.privateView(a).hand().size() == before - 1,
                        "附能目标选择前提前抽牌或结算击倒");
                    for (UUID recipient : List.of(a, b, observer)) {
                        var stateView = session.view(recipient, recipient.equals(observer) ? null : recipient);
                        buffer.clear();
                        MatchStatePayload.CODEC.encode(buffer, new MatchStatePayload(false, new Gson().toJson(stateView), "等待附能目标"));
                        String json = MatchStatePayload.CODEC.decode(buffer).snapshotJson();
                        var received = new Gson().fromJson(json, MatchSession.Snapshot.class);
                        context.assertTrue(recipient.equals(a) ? attachment.equals(received.game().effectAttachment()) : received.game().effectAttachment() == null,
                            "附能步骤未正确按接收者过滤");
                        if (!recipient.equals(a)) for (var hidden : m.privateView(a).deck())
                            context.assertTrue(!json.contains(hidden.instanceId().toString()), "附能选择泄露牌库实例");
                    }
                    var next = session.view(a, a);
                    var attach = next.options().stream().filter(o -> o.cardKeys().contains("field:" + target)).findFirst().orElseThrow();
                    buffer.clear();
                    MatchIntentPayload.CODEC.encode(buffer, new MatchIntentPayload(m.id().toString(), a.toString(), Math.toIntExact(next.revision()), attach.index()));
                    var attachIntent = MatchIntentPayload.CODEC.decode(buffer);
                    boolean forged = false;
                    try { session.submit(b, a, attachIntent.revision(), attachIntent.option()); }
                    catch (IllegalArgumentException expected) { forged = true; }
                    context.assertTrue(forged && m.benchPokemonView(a).getFirst().attachedEnergyIds().isEmpty(), "对手能伪造附能目标");
                    session.submit(a, a, attachIntent.revision(), attachIntent.option());
                    context.assertTrue(m.benchPokemonView(a).getFirst().attachedEnergyIds().size() == 2 && !m.energyAttached(a)
                        && m.clientView(a).effectAttachment() == null, "能量未正确附着或错误占用手动次数");
                }
                if (selectionMode == 3) {
                    var reveal = m.effectCardReveal();
                    context.assertTrue(reveal != null && m.phase() == MatchPhase.TURN && m.privateView(a).hand().size() == before - 1,
                        "展示前移牌或提前处理击倒");
                    for (UUID recipient : List.of(a, b, observer)) {
                        var showing = session.view(recipient, recipient.equals(observer) ? null : recipient);
                        buffer.clear();
                        MatchStatePayload.CODEC.encode(buffer, new MatchStatePayload(false, new Gson().toJson(showing), "公开展示"));
                        String json = MatchStatePayload.CODEC.decode(buffer).snapshotJson();
                        var roundtrip = new Gson().fromJson(json, MatchSession.Snapshot.class);
                        context.assertTrue(reveal.equals(roundtrip.game().effectReveal()) && roundtrip.game().effectSelection() == null,
                            "展示包混入私有选择或丢失卡面");
                        for (var hidden : m.privateView(a).deck()) context.assertTrue(!json.contains(hidden.instanceId().toString()), "展示泄露隐藏实例身份");
                    }
                    var see = session.view(b, b); var ack = see.options().stream().filter(o -> o.labelZh().startsWith("确认已查看")).findFirst().orElseThrow();
                    buffer.clear();
                    MatchIntentPayload.CODEC.encode(buffer, new MatchIntentPayload(m.id().toString(), b.toString(), Math.toIntExact(see.revision()), ack.index()));
                    var acknowledgment = MatchIntentPayload.CODEC.decode(buffer);
                    boolean forged = false;
                    try { session.submit(a, UUID.fromString(acknowledgment.seat()), acknowledgment.revision(), acknowledgment.option()); }
                    catch (IllegalArgumentException expected) { forged = true; }
                    context.assertTrue(forged && m.effectCardReveal().equals(reveal), "出牌人冒用对手确认展示");
                    session.submit(b, UUID.fromString(acknowledgment.seat()), acknowledgment.revision(), acknowledgment.option());
                    context.assertTrue(m.effectCardReveal() == null, "展示确认后残留公开面板");
                }
                context.assertTrue(!m.hasPendingEffectChoice() && session.view(a, a).game().effectSelection() == null, "选牌完成未清理私有信息");
            }
            context.assertTrue(m.phase() == MatchPhase.PRIZE_SELECTION && m.privateView(a).hand().size() == before + 1
                && m.privateView(a).discard().contains(card) && m.benchPokemonView(a).getFirst().damage() == 0,
                "组合训练家未完成回复、击倒后抽牌和最终弃置");
            denied = false;
            try { session.submit(a, a, decoded.revision(), decoded.option()); } catch (IllegalStateException expected) { denied = true; }
            context.assertTrue(denied, "重复请求再次执行组合训练家");
        } finally { buffer.release(); }
        for (UUID recipient : List.of(b, observer)) {
            var wire = new RegistryByteBuf(Unpooled.buffer(), context.getWorld().getRegistryManager());
            try {
                var snapshot = session.view(recipient, recipient.equals(b) ? b : null);
                MatchStatePayload.CODEC.encode(wire, new MatchStatePayload(false, new Gson().toJson(snapshot), "组合训练家结算"));
                String json = MatchStatePayload.CODEC.decode(wire).snapshotJson();
                for (UUID owner : List.of(a, b)) {
                    var state = m.privateView(owner);
                    var hidden = new ArrayList<CardInstance>(state.deck()); hidden.addAll(state.prizes());
                    if (!recipient.equals(owner)) hidden.addAll(state.hand());
                    for (var hiddenCard : hidden) context.assertTrue(!json.contains(hiddenCard.instanceId().toString()), "组合训练家同步泄露隐藏区域");
                }
            } finally { wire.release(); }
        }
        m.takePrizeAt(a, 0); m.chooseReplacement(b, m.benchPokemonView(b).getFirst().pokemonId());
        context.assertTrue(m.phase() == MatchPhase.TURN && m.currentPlayer().equals(a), "物品击倒后不应结束当前回合");
        if (selectionMode == 5) {
            var order = m.triggerOrderPrompt();
            context.assertTrue(order != null && !order.optionalDecision() && order.entries().size() == 2,
                "击倒及替补后没有恢复两张附能触发");
            int count = m.privateView(a).hand().size();
            var wire = new RegistryByteBuf(Unpooled.buffer(), context.getWorld().getRegistryManager());
            try {
                for (UUID recipient : List.of(a, b, observer)) {
                    wire.clear(); var state = session.view(recipient, recipient.equals(observer) ? null : recipient);
                    MatchStatePayload.CODEC.encode(wire, new MatchStatePayload(false, new Gson().toJson(state), "触发顺序"));
                    var json = MatchStatePayload.CODEC.decode(wire).snapshotJson();
                    var received = new Gson().fromJson(json, MatchSession.Snapshot.class);
                    context.assertTrue(order.equals(received.game().triggerOrder()), "触发顺序公开包丢失状态");
                    if (!recipient.equals(a)) for (var hidden : m.privateView(a).hand())
                        context.assertTrue(!json.contains(hidden.instanceId().toString()), "自动触发公开包泄露手牌");
                    if (!recipient.equals(a)) context.assertTrue(received.options().stream().noneMatch(o -> o.labelZh().startsWith("优先结算触发")), "非当前玩家收到排序意图");
                }
                for (String label : List.of("优先结算触发", "使用触发特性")) {
                    var state = session.view(a, a); var action = state.options().stream().filter(o -> o.labelZh().startsWith(label)).findFirst().orElseThrow();
                    wire.clear(); MatchIntentPayload.CODEC.encode(wire,
                        new MatchIntentPayload(m.id().toString(), a.toString(), Math.toIntExact(state.revision()), action.index()));
                    var intent = MatchIntentPayload.CODEC.decode(wire);
                    boolean forged = false;
                    try { session.submit(b, a, intent.revision(), intent.option()); } catch (IllegalArgumentException expected) { forged = true; }
                    context.assertTrue(forged && m.privateView(a).hand().size() == count, "对手伪造触发请求");
                    session.submit(a, a, intent.revision(), intent.option());
                    boolean stale = false;
                    try { session.submit(a, a, intent.revision(), intent.option()); } catch (IllegalStateException expected) { stale = true; }
                    context.assertTrue(stale, "重复触发意图通过");
                }
                context.assertTrue(m.triggerOrderPrompt() == null && m.privateView(a).hand().size() == count + 1,
                    "每回合一次没有裁掉重复触发或未正确抽牌");
            } finally { wire.release(); }
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void resumableAbilityPacketsAndResolution(TestContext context) {
        resumableAbilityPacketsAndResolution(context,false);
    }
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void effectOriginProtectionPackets(TestContext context) {
        resumableAbilityPacketsAndResolution(context,true);
    }
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void sharedPowerQualificationPackets(TestContext context) {
        resumableAbilityPacketsAndResolution(context,false,true);
    }
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void resumableAttackPacketsAndResolution(TestContext context) {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),observer=UUID.randomUUID();
        var attack=BundledCombatData.attack(com.google.gson.JsonParser.parseString("""
            {"id":"test-attack-search","nameZh":"攻击续接夹具（非真实卡）","energyCost":{},"baseDamage":120,"effectSupport":"SUPPORTED",
              "triggers":[{"trigger":"AFTER_ATTACK","effects":[
                {"type":"search_deck","target":"DECK","amount":2,"minimum":2},{"type":"draw","amount":1}]}]}
            """).getAsJsonObject(),dev.hua.ptcglive.engine.effect.EffectRegistry.standard());
        var database=new CardDatabase(List.of(new CardDefinition("attack-basic","攻击夹具",CardCategory.POKEMON,TrainerCategory.NONE,
            "H",Set.of("international"),true,false,60,Map.of(),EffectSupport.SUPPORTED,null)));
        var combat=new CombatCatalog(Map.of("attack-basic",new dev.hua.ptcglive.engine.field.PokemonProfile(120,0,null,null,0,null,0,1,List.of(attack))),Map.of());
        var decks=new ArrayList<List<CardInstance>>();
        for(UUID owner:List.of(a,b)) {var deck=new ArrayList<CardInstance>();for(int i=0;i<60;i++)deck.add(new CardInstance(UUID.randomUUID(),owner,"attack-basic"));decks.add(deck);}
        var m=new ServerMatch(a,b,decks.get(0),decks.get(1),PtcgServices.rulesets().get(RULE),database,combat,new ServerRandom(99));
        m.callCoin(a,CoinSide.HEADS);UUID winner=m.publicCoinResult()==CoinSide.HEADS?a:b;m.chooseWhoGoesFirst(winner,winner.equals(a));
        for(UUID seat:List.of(a,b)) {m.chooseOpeningActive(seat,m.privateView(seat).hand().getFirst().instanceId());m.placeOpeningBench(seat,m.privateView(seat).hand().getFirst().instanceId());}
        var session=MatchSession.multiplayer(m,a,b);session.addSpectator(observer);completeSetup(session,a,b,a,b);m.endTurn(a);m.endTurn(b);
        int turn=m.turnNumber(),hand=m.privateView(a).hand().size();UUID source=m.activePokemonView(a).pokemonId();
        var view=session.view(a,a);var start=view.options().stream().filter(o->o.labelZh().startsWith("攻击：")).findFirst().orElseThrow();
        var wire=new RegistryByteBuf(Unpooled.buffer(),context.getWorld().getRegistryManager());
        try {
            MatchIntentPayload.CODEC.encode(wire,new MatchIntentPayload(m.id().toString(),a.toString(),Math.toIntExact(view.revision()),start.index()));
            var intent=MatchIntentPayload.CODEC.decode(wire);boolean forged=false;
            try{session.submit(b,a,intent.revision(),intent.option());}catch(IllegalArgumentException expected){forged=true;}
            context.assertTrue(forged,"对手伪造攻击被接受");session.submit(a,a,intent.revision(),intent.option());
            var prompt=m.effectCardPrompt(a);var random=m.randomTranscript();
            context.assertTrue(m.phase()==MatchPhase.TURN&&m.activePokemonView(b).damage()==120&&m.turnNumber()==turn,"攻击提前KO／推进回合");
            for(UUID recipient:List.of(a,b,observer)) {
                wire.clear();MatchStatePayload.CODEC.encode(wire,new MatchStatePayload(false,new Gson().toJson(session.view(recipient,recipient.equals(observer)?null:recipient)),"等待攻击选牌"));
                String json=MatchStatePayload.CODEC.decode(wire).snapshotJson();var packet=new Gson().fromJson(json,MatchSession.Snapshot.class);
                context.assertTrue(packet.game().resolvingAttack()!=null&&packet.game().resolvingAttack().pokemonId().equals(source)
                    &&packet.game().resolvingAbility()==null&&packet.game().resolvingTrainer()==null,"攻击公共来源错误");
                if(recipient.equals(a))context.assertTrue(prompt.equals(packet.game().effectSelection()),"刷新更换攻击选择");
                else {context.assertTrue(packet.game().effectSelection()==null,"攻击检索候选泄露");for(var card:prompt.candidates())context.assertTrue(!json.contains(card.instanceId().toString()),"牌库实例泄露");}
            }
            context.assertTrue(random.equals(m.randomTranscript())&&hand==m.privateView(a).hand().size(),"刷新重复执行前缀");
            for(var card:prompt.candidates().subList(0,2)) {
                var current=session.view(a,a);var choice=current.options().stream().filter(o->o.cardKeys().contains("effect:"+card.instanceId())).findFirst().orElseThrow();
                wire.clear();MatchIntentPayload.CODEC.encode(wire,new MatchIntentPayload(m.id().toString(),a.toString(),Math.toIntExact(current.revision()),choice.index()));
                var selection=MatchIntentPayload.CODEC.decode(wire);session.submit(a,a,selection.revision(),selection.option());
                boolean stale=false;try{session.submit(a,a,selection.revision(),selection.option());}catch(IllegalStateException expected){stale=true;}
                context.assertTrue(stale,"重复攻击选择通过");
            }
            var current=session.view(a,a);var confirm=current.options().stream().filter(o->o.labelZh().startsWith("确认选牌")).findFirst().orElseThrow();
            wire.clear();MatchIntentPayload.CODEC.encode(wire,new MatchIntentPayload(m.id().toString(),a.toString(),Math.toIntExact(current.revision()),confirm.index()));
            var choice=MatchIntentPayload.CODEC.decode(wire);session.submit(a,a,choice.revision(),choice.option());
            context.assertTrue(m.phase()==MatchPhase.PRIZE_SELECTION&&m.privateView(a).hand().size()==hand+3&&!m.hasPendingEffectChoice()
                &&m.clientView(a).resolvingAttack()==null&&m.privateView(a).discard().isEmpty(),"攻击全文后拿奖／来源清理错误");
            m.takePrizeAt(a,0);m.chooseReplacement(b,m.benchPokemonView(b).getFirst().pokemonId());
            context.assertTrue(m.currentPlayer().equals(b)&&m.turnNumber()==turn+1,"攻击拿奖替补后未唯一推进回合");
        } finally {wire.release();}
        context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void scopedActivatedAbilityPackets(TestContext context) { playerPokemonScopePackets(context, "ability"); }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void scopedTrainerPackets(TestContext context) { playerPokemonScopePackets(context, "trainer"); }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void scopedAttackPackets(TestContext context) { playerPokemonScopePackets(context, "attack"); }
    /** The same nested program travels through all three production entry points and the real packet codecs. */
    private void playerPokemonScopePackets(TestContext context, String entry) {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),watch=UUID.randomUUID();
        var registry=dev.hua.ptcglive.engine.effect.EffectRegistry.standard();
        String effects="""
            [{"type":"select_pokemon","target":"SELECT_OPPONENT_POKEMON","position":"BENCH","effects":[
              {"type":"damage_counter","target":"CHOSEN_POKEMON","amount":1},
              {"type":"select_pokemon","target":"SELECT_OWN_POKEMON","position":"BENCH","effects":[
                {"type":"damage_counter","target":"CHOSEN_POKEMON","amount":1}]},
              {"type":"damage_counter","target":"CHOSEN_POKEMON","amount":1},
              {"type":"draw","amount":1}]}]
            """;
        var ability=BundledCombatData.ability(com.google.gson.JsonParser.parseString(
            "{\"id\":\"scope-ability\",\"nameZh\":\"固定目标特性夹具\",\"effectSupport\":\"SUPPORTED\",\"effects\":"+effects+"}").getAsJsonObject(),registry);
        var trainer=BundledCombatData.trainer(com.google.gson.JsonParser.parseString(
            "{\"trainerCategory\":\"ITEM\",\"effects\":"+effects+"}").getAsJsonObject(),registry);
        var attack=BundledCombatData.attack(com.google.gson.JsonParser.parseString(
            "{\"id\":\"scope-attack\",\"nameZh\":\"固定目标攻击夹具\",\"energyCost\":{},\"baseDamage\":120,\"effectSupport\":\"SUPPORTED\",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":"+effects+"}]}").getAsJsonObject(),registry);
        var database=new CardDatabase(List.of(
            new CardDefinition("scope-basic","固定目标测试宝可梦",CardCategory.POKEMON,TrainerCategory.NONE,"H",Set.of("international"),true,false,60,Map.of(),EffectSupport.SUPPORTED,null),
            new CardDefinition("scope-trainer","固定目标测试物品",CardCategory.TRAINER,TrainerCategory.ITEM,"H",Set.of("international"),false,false,60,Map.of(),EffectSupport.SUPPORTED,null)));
        var combat=new CombatCatalog(Map.of("scope-basic",new dev.hua.ptcglive.engine.field.PokemonProfile(120,0,null,null,0,null,0,1,List.of(attack),List.of(ability))),Map.of(),Map.of("scope-trainer",trainer));
        var decks=new ArrayList<List<CardInstance>>();
        for(UUID owner:List.of(a,b)) {var deck=new ArrayList<CardInstance>();for(int i=0;i<60;i++)deck.add(new CardInstance(UUID.randomUUID(),owner,i<36?"scope-basic":"scope-trainer"));decks.add(deck);}
        ServerMatch selected=null;
        for(int seed=0;seed<1000;seed++) {
            var candidate=new ServerMatch(a,b,decks.get(0),decks.get(1),PtcgServices.rulesets().get(RULE),database,combat,new ServerRandom(seed));
            candidate.callCoin(a,CoinSide.HEADS);UUID winner=candidate.publicCoinResult()==CoinSide.HEADS?a:b;candidate.chooseWhoGoesFirst(winner,winner.equals(a));
            if(List.of(a,b).stream().allMatch(p->candidate.privateView(p).hand().stream().filter(c->c.definitionId().equals("scope-basic")).count()>=2)
                &&candidate.privateView(a).hand().stream().anyMatch(c->c.definitionId().equals("scope-trainer"))) {selected=candidate;break;}
        }
        context.assertTrue(selected!=null,"未找到固定目标开局夹具");var m=selected;
        for(UUID seat:List.of(a,b)) {var basics=m.privateView(seat).hand().stream().filter(c->c.definitionId().equals("scope-basic")).toList();m.chooseOpeningActive(seat,basics.getFirst().instanceId());m.placeOpeningBench(seat,basics.get(1).instanceId());}
        var session=MatchSession.multiplayer(m,a,b);session.addSpectator(watch);completeSetup(session,a,b,a,b);m.endTurn(a);m.endTurn(b);
        var card=m.privateView(a).hand().stream().filter(c->c.definitionId().equals("scope-trainer")).findFirst().orElseThrow();
        UUID source=m.activePokemonView(a).pokemonId();int hand=m.privateView(a).hand().size(),turn=m.turnNumber();
        var start=session.view(a,a);var option=start.options().stream().filter(o->switch(entry) {
            case "ability" -> o.labelZh().startsWith("使用特性")&&o.cardKeys().contains("field:"+source);
            case "trainer" -> o.cardKeys().contains("hand:"+card.instanceId());
            default -> o.labelZh().startsWith("攻击：");
        }).findFirst().orElseThrow();
        var wire=new RegistryByteBuf(Unpooled.buffer(),context.getWorld().getRegistryManager());
        try {
            MatchIntentPayload.CODEC.encode(wire,new MatchIntentPayload(m.id().toString(),a.toString(),Math.toIntExact(start.revision()),option.index()));
            var activate=MatchIntentPayload.CODEC.decode(wire);session.submit(a,a,activate.revision(),activate.option());
            for(UUID target:List.of(m.benchPokemonView(b).getFirst().pokemonId(),m.benchPokemonView(a).getFirst().pokemonId())) {
                context.assertTrue(m.hasPendingPokemonSelection()&&m.phase()==MatchPhase.TURN&&m.turnNumber()==turn,"固定目标选择中提前击倒或结束回合");
                var random=m.randomTranscript();
                for(UUID recipient:List.of(a,b,watch)) {
                    wire.clear();MatchStatePayload.CODEC.encode(wire,new MatchStatePayload(false,new Gson().toJson(session.view(recipient,recipient.equals(watch)?null:recipient)),"等待固定目标选择"));
                    String json=MatchStatePayload.CODEC.decode(wire).snapshotJson();var snapshot=new Gson().fromJson(json,MatchSession.Snapshot.class);
                    context.assertTrue(dev.hua.ptcglive.view.MatchActionMatcher.hasPokemonChoice(snapshot)==recipient.equals(a),"主动效果选择权发送给错误玩家");
                    context.assertTrue((snapshot.game().resolvingAbility()!=null)==entry.equals("ability")
                        &&(snapshot.game().resolvingTrainer()!=null)==entry.equals("trainer")
                        &&(snapshot.game().resolvingAttack()!=null)==entry.equals("attack"),"目标上下文改变了效果来源类别");
                    context.assertTrue(!json.contains("entrySequence")&&!json.contains("effectProgram"),"固定目标内部状态泄露");
                    for(UUID seat:List.of(a,b)) {
                        if(!recipient.equals(seat))for(var hidden:m.privateView(seat).hand())context.assertTrue(!json.contains(hidden.instanceId().toString()),"他人手牌泄露");
                        for(var hidden:m.privateView(seat).deck())context.assertTrue(!json.contains(hidden.instanceId().toString()),"牌库顺序泄露");
                        for(var hidden:m.privateView(seat).prizes())context.assertTrue(!json.contains(hidden.instanceId().toString()),"奖赏内容泄露");
                    }
                }
                context.assertTrue(random.equals(m.randomTranscript()),"刷新目标面板重放了随机数");
                var current=session.view(a,a);var choice=current.options().stream().filter(o->o.labelZh().startsWith("选择效果目标")&&o.cardKeys().contains("field:"+target)).findFirst().orElseThrow();
                wire.clear();MatchIntentPayload.CODEC.encode(wire,new MatchIntentPayload(m.id().toString(),a.toString(),Math.toIntExact(current.revision()),choice.index()));
                var intent=MatchIntentPayload.CODEC.decode(wire);
                for(UUID intruder:List.of(b,watch)) {boolean denied=false;try{session.submit(intruder,a,intent.revision(),intent.option());}catch(IllegalArgumentException expected){denied=true;}context.assertTrue(denied,"对手或旁观者可代选");}
                session.submit(a,a,intent.revision(),intent.option());boolean stale=false;
                try{session.submit(a,a,intent.revision(),intent.option());}catch(IllegalStateException expected){stale=true;}
                context.assertTrue(stale,"旧选择请求重复执行");
            }
            context.assertTrue(!m.hasPendingEffectChoice()&&m.benchPokemonView(b).getFirst().damage()==20&&m.benchPokemonView(a).getFirst().damage()==10,"内层结束后没有恢复外层目标");
            context.assertTrue(m.privateView(a).hand().size()==hand+(entry.equals("trainer")?0:1),"选择后抽牌或训练家消耗错误");
            context.assertTrue(m.privateView(a).discard().contains(card)==entry.equals("trainer"),"训练家没有只在全文后进入弃牌区");
            if(entry.equals("ability"))context.assertTrue(!m.canUseAbility(a,source,ability.id()),"成功特性未消耗次数");
            if(entry.equals("attack")) {
                context.assertTrue(m.phase()==MatchPhase.PRIZE_SELECTION,"攻击全文后没有统一击倒");m.takePrizeAt(a,0);m.chooseReplacement(b,m.benchPokemonView(b).getFirst().pokemonId());
                context.assertTrue(m.currentPlayer().equals(b)&&m.turnNumber()==turn+1,"拿奖替补后没有唯一推进回合");
            } else context.assertTrue(m.currentPlayer().equals(a)&&m.turnNumber()==turn,"非攻击效果错误结束回合");
        } finally {wire.release();}
        context.complete();
    }
    private void resumableAbilityPacketsAndResolution(TestContext context, boolean protectedTarget) {
        resumableAbilityPacketsAndResolution(context,protectedTarget,false);
    }
    private void resumableAbilityPacketsAndResolution(TestContext context, boolean protectedTarget, boolean sharedPower) {
        resumableAbilityPacketsAndResolution(context,protectedTarget,sharedPower,false);
    }
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void unlimitedAbilityActivationPackets(TestContext context) {
        resumableAbilityPacketsAndResolution(context,false,false,true);
    }
    private void resumableAbilityPacketsAndResolution(TestContext context, boolean protectedTarget, boolean sharedPower, boolean unlimited) {
        UUID a=UUID.randomUUID(), b=UUID.randomUUID(), observer=UUID.randomUUID();
        var abilityJson=com.google.gson.JsonParser.parseString("""
            {"id":"test-active-search","nameZh":"主动续接夹具（非真实卡）","effectSupport":"SUPPORTED","effects":[
              {"type":"damage_counter","target":"OPPONENT_ACTIVE","amount":12},
              {"type":"search_deck","target":"DECK","amount":2,"minimum":2},
              {"type":"draw","amount":1}]}
            """).getAsJsonObject();
        if(unlimited)abilityJson.addProperty("kind","ACTIVATED_UNLIMITED");
        var ability=BundledCombatData.ability(abilityJson,dev.hua.ptcglive.engine.effect.EffectRegistry.standard());
        var database=new CardDatabase(List.of(new CardDefinition("active-basic","主动特性夹具",CardCategory.POKEMON,TrainerCategory.NONE,
            "H",Set.of("international"),true,false,60,Map.of(),EffectSupport.SUPPORTED,null)));
        if(sharedPower)ability=new dev.hua.ptcglive.engine.field.AbilityDefinition(ability.id(),ability.nameZh(),ability.effect(),ability.effectSupport(),
            ability.unsupportedReasonZh(),ability.kind(),ability.effectProgram(),ability.eventPolicy(),new dev.hua.ptcglive.engine.effect.SharedUsageLimit(
                dev.hua.ptcglive.engine.effect.SharedUsageLimit.VSTAR,dev.hua.ptcglive.engine.effect.SharedUsageLimit.Period.GAME));
        var activeAbility=ability;
        var abilities=new ArrayList<dev.hua.ptcglive.engine.field.AbilityDefinition>();abilities.add(ability);
        if(protectedTarget) abilities.add(BundledCombatData.ability(com.google.gson.JsonParser.parseString("""
            {"id":"test-origin-guard","nameZh":"来源防护夹具","effectSupport":"SUPPORTED","effects":[
              {"type":"prevent_effects","target":"OPPONENT_ACTIVE","source":"ABILITY","from":"OPPONENT",
               "scope":"EFFECTS_ONLY","duration":"LEAVES_PLAY"}]}
            """).getAsJsonObject(),dev.hua.ptcglive.engine.effect.EffectRegistry.standard()));
        var combat=new CombatCatalog(Map.of("active-basic",new dev.hua.ptcglive.engine.field.PokemonProfile(120,0,null,null,0,null,0,1,List.of(),abilities)),Map.of());
        var decks=new ArrayList<List<CardInstance>>();
        for(UUID owner:List.of(a,b)) {
            var deck=new ArrayList<CardInstance>();for(int i=0;i<60;i++)deck.add(new CardInstance(UUID.randomUUID(),owner,"active-basic"));decks.add(deck);
        }
        var m=new ServerMatch(a,b,decks.get(0),decks.get(1),PtcgServices.rulesets().get(RULE),database,combat,new ServerRandom(99));
        m.callCoin(a,CoinSide.HEADS);UUID winner=m.publicCoinResult()==CoinSide.HEADS?a:b;
        m.chooseWhoGoesFirst(winner,winner.equals(a));
        for(UUID seat:List.of(a,b)) {
            var hand=m.privateView(seat).hand();m.chooseOpeningActive(seat,hand.getFirst().instanceId());
            m.placeOpeningBench(seat,m.privateView(seat).hand().getFirst().instanceId());
        }
        var session=MatchSession.multiplayer(m,a,b);session.addSpectator(observer);completeSetup(session,a,b,a,b);
        UUID source=m.activePokemonView(a).pokemonId();int before=m.privateView(a).hand().size();
        if(protectedTarget) m.useAbility(a,source,"test-origin-guard");
        var view=session.view(a,a);
        var start=view.options().stream().filter(o->o.labelZh().startsWith("使用特性")&&o.labelZh().contains(activeAbility.nameZh())&&o.cardKeys().contains("field:"+source)).findFirst().orElseThrow();
        var wire=new RegistryByteBuf(Unpooled.buffer(),context.getWorld().getRegistryManager());
        try {
            MatchIntentPayload.CODEC.encode(wire,new MatchIntentPayload(m.id().toString(),a.toString(),Math.toIntExact(view.revision()),start.index()));
            var intent=MatchIntentPayload.CODEC.decode(wire);
            boolean forged=false;try{session.submit(b,a,intent.revision(),intent.option());}catch(IllegalArgumentException expected){forged=true;}
            context.assertTrue(forged,"对手可伪造主动特性发动");
            session.submit(a,a,intent.revision(),intent.option());
            var prompt=m.effectCardPrompt(a);var random=m.randomTranscript();
            context.assertTrue(m.phase()==MatchPhase.TURN&&m.activePokemonView(b).damage()==(protectedTarget?0:120),"特性前缀没有应用来源防护或提前击倒");
            context.assertTrue(m.abilityOptions(a).stream().anyMatch(o->o.pokemonId().equals(source)&&o.usedThisTurn()),"等待时未记录使用次数");
            for(UUID recipient:List.of(a,b,observer)) {
                wire.clear();
                MatchStatePayload.CODEC.encode(wire,new MatchStatePayload(false,new Gson().toJson(session.view(recipient,recipient.equals(observer)?null:recipient)),"等待特性选牌"));
                String json=MatchStatePayload.CODEC.decode(wire).snapshotJson();
                var packet=new Gson().fromJson(json,MatchSession.Snapshot.class);
                context.assertTrue(packet.game().resolvingAbility()!=null&&packet.game().resolvingAbility().pokemonId().equals(source)
                    &&packet.game().resolvingTrainer()==null,"公共特性来源提示没有正确同步");
                if(protectedTarget) context.assertTrue(packet.game().players().stream().filter(p->p.id().equals(b)).findFirst().orElseThrow().active().continuousEffectZh().contains("特性"),"防护公共说明丢失");
                if(sharedPower) {
                    context.assertTrue(packet.game().players().stream().filter(p->p.id().equals(a)).findFirst().orElseThrow().vstarUsed(),"等待期间VSTAR标记未公开同步");
                    context.assertTrue(!packet.game().players().stream().filter(p->p.id().equals(b)).findFirst().orElseThrow().vstarUsed(),"共享资格错误跨方");
                }
                if(recipient.equals(a)) context.assertTrue(prompt.equals(packet.game().effectSelection()),"刷新丢失特性选择");
                else {
                    context.assertTrue(packet.game().effectSelection()==null,"候选泄露给对手");
                    for(var hidden:prompt.candidates())context.assertTrue(!json.contains(hidden.instanceId().toString()),"特性包泄露牌库实例");
                }
            }
            context.assertTrue(random.equals(m.randomTranscript())&&before==m.privateView(a).hand().size(),"刷新重复执行前缀");
            for(var card:prompt.candidates().subList(0,2)) {
                var current=session.view(a,a);var choice=current.options().stream().filter(o->o.cardKeys().contains("effect:"+card.instanceId())).findFirst().orElseThrow();
                wire.clear();MatchIntentPayload.CODEC.encode(wire,new MatchIntentPayload(m.id().toString(),a.toString(),Math.toIntExact(current.revision()),choice.index()));
                var selection=MatchIntentPayload.CODEC.decode(wire);session.submit(a,a,selection.revision(),selection.option());
                boolean stale=false;try{session.submit(a,a,selection.revision(),selection.option());}catch(IllegalStateException expected){stale=true;}
                context.assertTrue(stale,"重复选择请求未拒绝");
            }
            var current=session.view(a,a);var confirm=current.options().stream().filter(o->o.labelZh().startsWith("确认选牌")).findFirst().orElseThrow();
            wire.clear();MatchIntentPayload.CODEC.encode(wire,new MatchIntentPayload(m.id().toString(),a.toString(),Math.toIntExact(current.revision()),confirm.index()));
            var selection=MatchIntentPayload.CODEC.decode(wire);session.submit(a,a,selection.revision(),selection.option());
            context.assertTrue(m.phase()==(protectedTarget?MatchPhase.TURN:MatchPhase.PRIZE_SELECTION)&&m.privateView(a).hand().size()==before+3
                &&m.privateView(a).discard().isEmpty()&&!m.hasPendingEffectChoice()&&m.clientView(a).resolvingAbility()==null,"特性整段结算或来源清理错误");
            if(!protectedTarget) {m.takePrizeAt(a,0);m.chooseReplacement(b,m.benchPokemonView(b).getFirst().pokemonId());}
            else context.assertTrue(m.activePokemonView(b).damage()==0,"受保护目标被错误修改");
            context.assertTrue(m.currentPlayer().equals(a)&&m.canUseAbility(a,source,ability.id())==unlimited,"特性击倒结束回合或频率规则错误");
            if(unlimited) {
                var again=session.view(a,a);var activate=again.options().stream().filter(o->o.labelZh().startsWith("使用特性")&&o.cardKeys().contains("field:"+source)).findFirst().orElseThrow();
                context.assertTrue(activate.labelZh().contains("可多次"),"任意次特性没有中文频率说明");
                wire.clear();MatchIntentPayload.CODEC.encode(wire,new MatchIntentPayload(m.id().toString(),a.toString(),Math.toIntExact(again.revision()),activate.index()));
                var second=MatchIntentPayload.CODEC.decode(wire);session.submit(a,a,second.revision(),second.option());
                var next=m.effectCardPrompt(a);context.assertTrue(!next.resolutionId().equals(prompt.resolutionId()),"第二次发动复用了旧选择事务");
                boolean oldPrompt=false;try{m.submitEffectCards(a,prompt.resolutionId(),prompt.step(),prompt.candidates().subList(0,2).stream().map(CardInstance::instanceId).toList());}catch(IllegalStateException expected){oldPrompt=true;}
                context.assertTrue(oldPrompt,"旧发动的私有选牌请求被重放");
                m.submitEffectCards(a,next.resolutionId(),next.step(),next.candidates().subList(0,2).stream().map(CardInstance::instanceId).toList());
                // The first Knock Out promoted B's only Benched Pokémon.  The
                // second one therefore leaves B with no Pokémon in play, which
                // is an immediate loss rather than a Prize-selection prompt.
                context.assertTrue(m.phase()==MatchPhase.FINISHED&&m.result().winner().equals(a)
                    &&m.result().reason()==MatchResult.Reason.NO_POKEMON,"第二次特性未按无宝可梦规则结束对局");
            }
            if(sharedPower) {
                UUID bench=m.benchPokemonView(a).getFirst().pokemonId();
                context.assertTrue(!m.canUseAbility(a,bench,ability.id()),"另一只宝可梦绕过VSTAR次数");
                m.endTurn(a);m.endTurn(b);
                context.assertTrue(!m.canUseAbility(a,bench,ability.id()),"新回合重置VSTAR次数");
                context.assertTrue(session.view(a,a).options().stream().noneMatch(o->o.labelZh().startsWith("使用特性")),"重同步后可重复使用共享力量");
            }
        } finally {wire.release();}
        context.complete();
    }
}
