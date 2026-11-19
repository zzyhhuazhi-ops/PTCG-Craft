package dev.hua.ptcglive.shop;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradeOffer;
import net.minecraft.world.World;

/** Save-compatibility shell for the retired entity ID. */
public final class CardMerchantEntity extends MerchantEntity {
    public CardMerchantEntity(EntityType<? extends CardMerchantEntity> type, World world) { super(type, world); }
    @Override protected void fillRecipes() { offers = new TradeOfferList(); }
    @Override protected Text getDefaultName() { return Text.literal("已停用的卡包商人"); }
    @Override public ActionResult interactMob(PlayerEntity player, Hand hand) { return ActionResult.FAIL; }
    @Override public boolean isLeveledMerchant() { return false; }
    @Override protected void afterUsing(TradeOffer offer) { }
    @Override public PassiveEntity createChild(ServerWorld world, PassiveEntity mate) { return null; }
    @Override public void tick() { if (!getWorld().isClient) discard(); else super.tick(); }
}
