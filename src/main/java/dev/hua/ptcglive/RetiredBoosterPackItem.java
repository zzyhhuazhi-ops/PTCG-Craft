package dev.hua.ptcglive;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.slf4j.LoggerFactory;
import java.util.List;

/**
 * Old pack IDs whose set has no real 美版 booster (promos, alternate prints,
 * energies, starter sets) resolve to this item.  Right click refunds the old
 * merchant price (4 iron ingots for every legacy pack) on the server.
 */
public final class RetiredBoosterPackItem extends Item {
    public static final int REFUND_IRON_INGOTS = 4;
    RetiredBoosterPackItem() { super(new Settings()); }

    @Override public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.success(stack, true);
        stack.decrementUnlessCreative(1, user);
        ItemStack refund = new ItemStack(Items.IRON_INGOT, REFUND_IRON_INGOTS);
        if (!user.getInventory().insertStack(refund)) user.dropItem(refund, false);
        user.sendMessage(Text.literal("[PTCG] 该卡包对应的系列没有现实美版补充包，已停用；退还 " + REFUND_IRON_INGOTS + " 个铁锭。"), false);
        LoggerFactory.getLogger(CardItems.MOD_ID).info("Retired booster pack refunded: player={} amount={}", user.getUuid(), REFUND_IRON_INGOTS);
        return TypedActionResult.success(stack, false);
    }

    @Override public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("tooltip.ptcglive.retired_booster_pack").formatted(Formatting.GRAY));
    }
}
