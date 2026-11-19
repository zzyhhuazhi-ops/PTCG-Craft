package dev.hua.ptcglive;

import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Opens the owner-only collection backed by the existing world ledger. */
public final class CardEncyclopediaItem extends Item {
    public CardEncyclopediaItem() { super(new Settings().maxCount(1)); }

    @Override public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("tooltip.ptcglive.card_encyclopedia.description").formatted(Formatting.GOLD));
        tooltip.add(Text.literal("右键：查看图鉴、存取卡牌、加入卡盒。").formatted(Formatting.GRAY));
    }

    @Override public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient && user instanceof net.minecraft.server.network.ServerPlayerEntity player)
            dev.hua.ptcglive.network.CollectionNetworking.open(player);
        return TypedActionResult.success(user.getStackInHand(hand), world.isClient());
    }
}
