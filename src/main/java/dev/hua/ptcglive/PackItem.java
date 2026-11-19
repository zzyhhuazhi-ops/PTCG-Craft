package dev.hua.ptcglive;

import dev.hua.ptcglive.packs.SetPackDefinition;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * One item per 美版 booster pack ({@code ptcglive:<set>_booster_pack}).  The
 * Chinese display name comes from the pack catalog, keeping the internal ID
 * and publication metadata separate. Right click opens a
 * server-authenticated preview; it never opens instantly.
 */
public final class PackItem extends Item {
    private final SetPackDefinition pack;
    PackItem(SetPackDefinition pack) { super(new Settings()); this.pack = pack; }
    public String setId() { return pack.setId(); }
    public SetPackDefinition pack() { return pack; }
    @Override public Text getName(ItemStack stack) { return Text.literal(pack.displayNameZh()); }

    /** Pack art variant of a stack: its custom model data, or 0 for the default art. */
    public static int variantOf(ItemStack stack) {
        CustomModelDataComponent data = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
        return data == null ? 0 : data.value();
    }
    public ItemStack stack(int customModelData) {
        ItemStack stack = new ItemStack(this);
        if (customModelData > 0) stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(customModelData));
        return stack;
    }

    @Override public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient && user instanceof net.minecraft.server.network.ServerPlayerEntity player) {
            try {
                dev.hua.ptcglive.network.PackNetworking.open(player, pack, hand);
            } catch (IllegalArgumentException | IllegalStateException failure) {
                user.sendMessage(Text.literal("[PTCG] 无法打开抽卡界面：" + failure.getMessage()), false);
            }
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    /**
     * Packs created without a chosen design (merchant sales, /give, older saves)
     * get one of the real printed designs, chosen by the server once.
     */
    @Override public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        var variants = pack.identity().artVariants();
        if (world.isClient || variants.size() < 2 || stack.contains(DataComponentTypes.CUSTOM_MODEL_DATA)) return;
        var chosen = variants.get(world.getRandom().nextInt(variants.size()));
        stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(chosen.customModelData()));
    }

    @Override public void appendTooltip(ItemStack stack, TooltipContext context, java.util.List<Text> tooltip, TooltipType type) {
        var identity = pack.identity();
        if (pack.custom()) {
            tooltip.add(Text.literal("PTCG Craft 自定义收藏卡包 · " + identity.chineseName()).formatted(Formatting.GRAY));
            tooltip.add(Text.literal("卡池 " + identity.cardCount() + " 张 · 每包 " + pack.cardsPerPack() + " 张 · 各印刷版本等概率").formatted(Formatting.DARK_GRAY));
            tooltip.add(Text.literal("右键查看完整卡池与概率，确认后由服务器开包").formatted(Formatting.AQUA));
            return;
        }
        tooltip.add(Text.translatable("tooltip.ptcglive.booster_pack.set", identity.chineseName(), identity.setCode()).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.ptcglive.booster_pack.release", Text.translatable("era.ptcglive." + identity.era()),
            identity.releaseDate()).formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.translatable("tooltip.ptcglive.booster_pack.pool", identity.cardCount(), pack.cardsPerPack()).formatted(Formatting.DARK_GRAY));
        var variant = identity.variant(variantOf(stack));
        if (variant != null && identity.artVariants().size() > 1)
            tooltip.add(Text.translatable("tooltip.ptcglive.booster_pack.art", variant.label()).formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.translatable("tooltip.ptcglive.booster_pack.open").formatted(Formatting.AQUA));
    }
}
