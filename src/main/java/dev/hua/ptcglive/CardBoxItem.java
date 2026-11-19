package dev.hua.ptcglive;

import dev.hua.ptcglive.rules.DeckBox;
import java.util.List;
import java.util.UUID;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Physical pointer to one server-owned card box. The stack contains only a box
 * UUID; card ownership and contents never live in client-editable item data.
 */
public final class CardBoxItem extends Item {
    private static final String BOX_ID = "ptcglive_box_id";
    /** Undyed crafted boxes use this neutral-white display color. */
    public static final int DEFAULT_BOX_COLOR = DyeColor.WHITE.getFireworkColor();
    public CardBoxItem() { super(new Settings().maxCount(1)); }

    public static ItemStack referenceTo(DeckBox box) {
        return referenceTo(box, DyeColor.RED);
    }
    /** Server-issued starter and administrator fixtures are visibly distinct from crafted boxes. */
    public static ItemStack referenceTo(DeckBox box, DyeColor color) {
        ItemStack stack = new ItemStack(PtcgContent.CARD_BOX);
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> nbt.putUuid(BOX_ID, box.id()));
        stack.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(color.getFireworkColor(), false));
        return stack;
    }

    @Override public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient && user instanceof net.minecraft.server.network.ServerPlayerEntity player) {
            try {
            UUID boxId = boxId(stack);
            DeckBox box;
            if (boxId == null) {
                box = PtcgServices.cardBoxes().create(user.getUuid(), "未命名卡盒", "standard-international-2026");
                NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> nbt.putUuid(BOX_ID, box.id()));
                user.sendMessage(Text.literal("[PTCG Craft] 已创建卡盒“未命名卡盒”。卡组数据由服务器保存。"), false);
            } else {
                    box = PtcgServices.cardBoxes().getOwned(user.getUuid(), boxId);
                    if (PtcgServices.cardBoxes().isLocked(user.getUuid(), boxId)) {
                        user.sendMessage(Text.literal("[PTCG Craft] 此卡盒正在对局中，无法开启或编辑；请返回卡桌对局面板。"), false);
                        return TypedActionResult.success(stack, false);
                    }
            }
            dev.hua.ptcglive.network.BoxNetworking.open(player, box.id());
            } catch (IllegalArgumentException | IllegalStateException ex) {
                user.sendMessage(Text.literal("[PTCG Craft] 无法打开卡盒：" + ex.getMessage()), false);
            }
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    @Override public Text getName(ItemStack stack) {
        return Text.literal("PTCG 卡盒");
    }
    @Override public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("最多 60 张；卡牌所有权和卡盒内容均由服务器保存。").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("卡盒颜色：" + colorName(color(stack)) + "；工作台放入任意一个染料即可重染。").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("手持右键：卡组编辑／收藏／规则检查。").formatted(Formatting.AQUA));
    }
    public static int color(ItemStack stack) { return DyedColorComponent.getColor(stack, DEFAULT_BOX_COLOR); }
    private static String colorName(int rgb) {
        for (DyeColor dye : DyeColor.values()) if (dye.getFireworkColor() == rgb) return switch (dye) {
            case WHITE -> "白色"; case ORANGE -> "橙色"; case MAGENTA -> "品红色"; case LIGHT_BLUE -> "淡蓝色";
            case YELLOW -> "黄色"; case LIME -> "黄绿色"; case PINK -> "粉红色"; case GRAY -> "灰色";
            case LIGHT_GRAY -> "淡灰色"; case CYAN -> "青色"; case PURPLE -> "紫色"; case BLUE -> "蓝色";
            case BROWN -> "棕色"; case GREEN -> "绿色"; case RED -> "红色"; case BLACK -> "黑色";
        };
        return "自定义颜色";
    }
    public static UUID boxId(ItemStack stack) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        return component != null && component.getNbt().containsUuid(BOX_ID) ? component.getNbt().getUuid(BOX_ID) : null;
    }
}
