package dev.hua.ptcglive;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.List;

public final class CardItem extends Item {
    private final CardCatalog.Entry card;

    public CardItem(CardCatalog.Entry card) {
        super(new Item.Settings().maxCount(64));
        this.card = card;
    }

    public CardCatalog.Entry card() { return card; }

    @Override public void inventoryTick(ItemStack stack,net.minecraft.world.World world,net.minecraft.entity.Entity entity,int slot,boolean selected){
        if(!world.isClient&&entity instanceof net.minecraft.server.network.ServerPlayerEntity p&&world.getTime()%20==0)PtcgServices.cardBoxes().acquired(p.getUuid(),card.id(),false);
        if(!world.isClient&&dev.hua.ptcglive.collectibles.CardCondition.normalizeLegacyStack(stack)
                &&entity instanceof net.minecraft.entity.player.PlayerEntity player)player.getInventory().markDirty();
    }

    @Override public Text getName(ItemStack stack) {
        return Text.translatable("item.ptcglive_mc.card", card.label());
    }

    @Override public Text getName() { return getName(getDefaultStack()); }

    @Override public void appendTooltip(ItemStack stack, TooltipContext context,
                                        List<Text> tooltip, TooltipType type) {
        var condition=dev.hua.ptcglive.collectibles.CardCondition.of(stack);
        if(condition!=null) tooltip.add(Text.literal("品质："+condition.label+"（售价/租赁 "+condition.percent+"%）· 不可存入图鉴").formatted(Formatting.GOLD));
        tooltip.add(Text.literal(card.id()).formatted(Formatting.GRAY));
        tooltip.add(Text.literal(CardRarity.classificationLabel(card.id())).formatted(Formatting.GRAY));
        tooltip.add(Text.literal(dev.hua.ptcglive.rules.PrintedRegulationMarks.labelZh(card.id())).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.ptcglive_mc.view").formatted(Formatting.AQUA));
    }
}
