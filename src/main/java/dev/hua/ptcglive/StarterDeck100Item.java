package dev.hua.ptcglive;

import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * New-player deck token. Keeps the original registry ID for existing saves.
 */
public final class StarterDeck100Item extends Item {
    /** Persisted player tag; exposed for the administrator-only reset command. */
    public static final String CLAIMED_TAG = "ptcglive_starter_deck_100_claimed";

    public StarterDeck100Item() { super(new Settings().maxCount(64)); }

    @Override public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient && user instanceof ServerPlayerEntity player) {
            try {
                var pair = redeem(player, hand);
                player.sendMessage(Text.literal("[PTCG Craft] 随机获得“" + pair.get(0).nameZh() + "”和“"
                    + pair.get(1).nameZh() + "”，每套 60 张，已放入背包。"), false);
                player.sendMessage(Text.literal("[PTCG Craft] 卡盒默认开放赛制；其他赛制及效果支持情况请在卡盒规则页检查。"), false);
            } catch (RuntimeException failure) {
                player.sendMessage(Text.literal("[PTCG Craft] 新手卡组未发放：" + failure.getMessage()), false);
                return TypedActionResult.fail(user.getStackInHand(hand));
            }
        }
        return TypedActionResult.success(user.getStackInHand(hand), world.isClient());
    }

    /** Inventory and both 60-card boxes commit together; never drop partial results. */
    public static List<dev.hua.ptcglive.rules.DeckBox> redeem(PlayerEntity player, Hand hand) {
        ItemStack token=player.getStackInHand(hand);
        if(player.getWorld().isClient || token.isEmpty() || !token.isOf(PtcgContent.STARTER_DECK_100_SELECTOR))
            throw new IllegalArgumentException("请手持新手随机卡组物品");
        if(player.getItemCooldownManager().isCoolingDown(token.getItem()))
            throw new IllegalStateException("请稍候再使用");
        int spaces=0;
        for(int slot=0;slot<36;slot++) if(player.getInventory().getStack(slot).isEmpty()) spaces++;
        if(hand==Hand.MAIN_HAND && token.getCount()==1) spaces++;
        if(spaces<2) throw new IllegalStateException("背包放不下两个卡盒，请腾出空间；物品未消耗");
        var boxes=PtcgServices.cardBoxes();
        var result=new java.util.ArrayList<dev.hua.ptcglive.rules.DeckBox>(2);
        dev.hua.ptcglive.collection.InventoryCardTransfers.atomic(player,boxes,()->{
            token.decrement(1);
            result.addAll(PtcgServices.newbieDecks().grantPair(player.getUuid(),boxes,new dev.hua.ptcglive.engine.ServerRandom()));
            for(var box:result) {
                var credential=CardBoxItem.referenceTo(box);
                if(!player.getInventory().insertStack(credential) || !credential.isEmpty())
                    throw new IllegalStateException("背包放不下两个卡盒，请腾出空间；物品未消耗");
            }
        });
        player.addCommandTag(CLAIMED_TAG);
        player.getItemCooldownManager().set(PtcgContent.STARTER_DECK_100_SELECTOR,10);
        return List.copyOf(result);
    }

    @Override public Text getName(ItemStack stack) { return Text.translatable("item.ptcglive.starter_deck_100_selector"); }

    @Override public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("右键消耗 1 个，从十套自定义卡组中随机获得两套不同卡组，每套 60 张。").formatted(Formatting.AQUA));
        tooltip.add(Text.literal("每套等概率；请为两个完整卡盒留出背包空间。").formatted(Formatting.GRAY));
    }
}
