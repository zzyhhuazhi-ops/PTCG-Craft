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

public final class PhoneItem extends Item {
    public PhoneItem() { super(new Settings().maxCount(1)); }
    @Override public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient && user instanceof net.minecraft.server.network.ServerPlayerEntity player)
            dev.hua.ptcglive.network.PhoneNetworking.open(player);
        return TypedActionResult.success(user.getStackInHand(hand), world.isClient());
    }
    @Override public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("右键打开卡包商城与矿物兑换").formatted(Formatting.AQUA));
        tooltip.add(Text.literal("余额与全部交易由服务器验证并保存").formatted(Formatting.GRAY));
    }
}
