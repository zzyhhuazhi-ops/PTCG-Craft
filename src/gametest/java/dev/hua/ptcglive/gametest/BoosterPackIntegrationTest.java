package dev.hua.ptcglive.gametest;

import dev.hua.ptcglive.CardItems;
import dev.hua.ptcglive.PackItem;
import dev.hua.ptcglive.PackItems;
import dev.hua.ptcglive.PtcgServices;
import dev.hua.ptcglive.engine.ServerRandom;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.VillagerData;
import net.minecraft.village.VillagerProfession;

/** 美版 booster packs inside the real headless Fabric server: registry, legacy IDs, merchants, openings. */
public final class BoosterPackIntegrationTest implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void encyclopediaItemIsRegisteredAndNonDestructive(TestContext context) {
        var item = dev.hua.ptcglive.PtcgContent.CARD_ENCYCLOPEDIA;
        context.assertTrue(Registries.ITEM.get(Identifier.of("ptcglive", "card_encyclopedia")) == item,
            "图鉴物品没有正确注册");
        var player = context.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        var stack = new ItemStack(item);
        player.setStackInHand(Hand.MAIN_HAND, stack);
        var before = PtcgServices.cardBoxes().unboxedForOwner(player.getUuid()).size();
        var result = item.use(context.getWorld(), player, Hand.MAIN_HAND);
        context.assertTrue(result.getValue() == stack && stack.getCount() == 1, "右键意外消耗了图鉴");
        context.assertTrue(PtcgServices.cardBoxes().unboxedForOwner(player.getUuid()).size() == before,
            "外观道具不应改变卡牌所有权");
        context.assertTrue(item.getTranslationKey().equals("item.ptcglive.card_encyclopedia"), "图鉴名称键错误");
        context.assertTrue(stack.getMaxCount() == 1, "图鉴应为单本道具");
        var group = Registries.ITEM_GROUP.get(Identifier.of("ptcglive", "equipment"));
        context.assertTrue(group != null, "缺少卡牌用具创造栏");
        group.updateEntries(new ItemGroup.DisplayContext(context.getWorld().getEnabledFeatures(), false, context.getWorld().getRegistryManager()));
        context.assertTrue(group.getDisplayStacks().stream().anyMatch(entry -> entry.isOf(item)), "创造栏缺少图鉴");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void legacyItemIdsResolveToBoosterPacks(TestContext context) {
        var registries = context.getWorld().getRegistryManager();
        assertLegacyItem(context, registries, "ptcglive:sv8_pack", PackItems.get("surging_sparks"));
        assertLegacyItem(context, registries, "ptcglive:bw11r_pack", PackItems.get("legendary_treasures"));
        assertLegacyItem(context, registries, "ptcglive:gum_pack", PackItems.get("detective_pikachu"));
        assertLegacyItem(context, registries, "ptcglive:svbsp_pack", PackItems.RETIRED);
        context.assertTrue(PtcgServices.packMigration().entries().stream().allMatch(entry ->
            decode(registries, entry.oldItemId()).getItem() == Registries.ITEM.get(Identifier.of(entry.newItemId()))), "有旧卡包物品 ID 没有解析到迁移表中的新物品");
        context.assertTrue(decode(registries, "ptcglive:no_such_pack").isEmpty(), "未登记的 ID 不应被别名吞掉");
        context.assertTrue(decode(registries, "minecraft:paper").isOf(Items.PAPER), "原版物品解析被别名影响");
        context.assertTrue(Registries.ITEM.getIds().stream().noneMatch(id -> id.getNamespace().equals("ptcglive")
            && id.getPath().endsWith("_pack") && !id.getPath().endsWith("_booster_pack") && !id.getPath().equals("all_energy_pack")), "旧 <setId>_pack 物品仍在注册表");
        context.complete();
    }


    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void everyBoosterPackOpensItsOwnCards(TestContext context) {
        var player = context.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        int index = 0;
        for (var pack : PtcgServices.packs().values()) {
            var result = PtcgServices.packOpening().open(player.getUuid(), pack, new ServerRandom(index++));
            context.assertTrue(result.cards().size() == pack.cardsPerPack(), "开包张数与配置不一致：" + pack.setId());
            for (var card : result.cards()) {
                if (pack.custom()) context.assertTrue(pack.pools().values().stream().anyMatch(pool -> pool.containsKey(card.definitionId())), "自定义包开出了核对卡池外的卡：" + card.definitionId());
                else context.assertTrue(pack.identity().sourceSetIds().stream().anyMatch(source -> card.definitionId().startsWith(source + "_")), "开出了其他系列的卡：" + card.definitionId());
                context.assertTrue(CardItems.byDefinitionId(card.definitionId()) != null, "开出的卡没有物品：" + card.definitionId());
            }
            context.assertTrue(Registries.ITEM.getId(PackItems.get(pack.setId())).equals(Identifier.of("ptcglive", pack.itemPath())), "补充包物品 ID 不稳定：" + pack.setId());
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void packArtVariantsCreativeTabAndRefund(TestContext context) {
        var player = context.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        PackItem sparks = PackItems.get("surging_sparks");
        ItemStack stack = sparks.getDefaultStack();
        sparks.inventoryTick(stack, context.getWorld(), player, 0, false);
        int variant = PackItem.variantOf(stack);
        context.assertTrue(variant >= 1 && variant <= sparks.pack().identity().artVariants().size(), "服务器没有为卡包随机分配美版封面");
        var group = Registries.ITEM_GROUP.get(RegistryKey.of(RegistryKeys.ITEM_GROUP, Identifier.of("ptcglive", "packs")));
        group.updateEntries(new ItemGroup.DisplayContext(context.getWorld().getEnabledFeatures(), false, context.getWorld().getRegistryManager()));
        var shown = group.getDisplayStacks().stream().toList();
        int expected = PtcgServices.packs().values().stream().filter(p -> !p.custom()||p.setId().startsWith("collection_")).mapToInt(p -> p.identity().artVariants().size()).sum();
        context.assertTrue(shown.size() == expected, "创造模式标签页没有列出全部封面变体：" + shown.size() + "/" + expected);
        String previous = "9999-99-99";
        for (ItemStack shownStack : shown) {
            var item = (PackItem) shownStack.getItem();
            context.assertTrue(shownStack.contains(DataComponentTypes.CUSTOM_MODEL_DATA), "创造模式卡包缺少封面编号");
            context.assertTrue(item.pack().identity().releaseDate().compareTo(previous) <= 0, "创造模式没有按美版发售日从新到老排序");
            previous = item.pack().identity().releaseDate();
        }
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(PackItems.RETIRED));
        PackItems.RETIRED.use(context.getWorld(), player, Hand.MAIN_HAND);
        context.assertTrue(player.getInventory().count(PackItems.RETIRED) == 0 && player.getInventory().count(Items.IRON_INGOT) == 4, "已停用卡包没有退还 4 个铁锭");
        context.complete();
    }

    private static void assertLegacyItem(TestContext context, net.minecraft.registry.RegistryWrapper.WrapperLookup registries, String id, net.minecraft.item.Item expected) {
        context.assertTrue(decode(registries, id).isOf(expected), "旧物品 ID 没有迁移：" + id);
    }
    private static ItemStack decode(net.minecraft.registry.RegistryWrapper.WrapperLookup registries, String id) {
        NbtCompound nbt = new NbtCompound(); nbt.putString("id", id); nbt.putInt("count", 1);
        return ItemStack.fromNbt(registries, nbt).orElse(ItemStack.EMPTY);
    }
    private static VillagerProfession professionOf(String id) {
        NbtCompound nbt = new NbtCompound(); nbt.putString("type", "minecraft:plains"); nbt.putString("profession", id); nbt.putInt("level", 1);
        return VillagerData.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow().getProfession();
    }
}
