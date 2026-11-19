package dev.hua.ptcglive.client;

import dev.hua.ptcglive.shop.CardMerchantEntity;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.render.entity.model.*;
import net.minecraft.util.Identifier;

/** Original 64x64 player UVs, including hat, sleeves, jacket and trousers; no villager UV remapping. */
public final class CardMerchantRenderer extends MobEntityRenderer<CardMerchantEntity, PlayerEntityModel<CardMerchantEntity>> {
    private static final Identifier SKIN = Identifier.of("ptcglive", "textures/entity/card_merchant.png");
    public CardMerchantRenderer(EntityRendererFactory.Context context) {
        // Uploaded skin's unused arm columns are transparent: it is the three-pixel slim layout.
        super(context, new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER_SLIM), true), 0.5f);
        model.setVisible(true);
    }
    @Override public Identifier getTexture(CardMerchantEntity entity) { return SKIN; }
}
