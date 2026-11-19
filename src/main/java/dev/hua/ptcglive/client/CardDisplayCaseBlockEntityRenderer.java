package dev.hua.ptcglive.client;

import dev.hua.ptcglive.CardDisplayCaseBlock;
import dev.hua.ptcglive.CardDisplayCaseBlockEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

/** Renders the real card stack above the cabinet, against the upper metal stand. */
public final class CardDisplayCaseBlockEntityRenderer implements BlockEntityRenderer<CardDisplayCaseBlockEntity> {
    private final CardItemRenderer itemRenderer;

    public CardDisplayCaseBlockEntityRenderer(CardItemRenderer itemRenderer) {
        this.itemRenderer = itemRenderer;
    }

    @Override public void render(CardDisplayCaseBlockEntity cabinet, float tickDelta, MatrixStack matrices,
                                 VertexConsumerProvider consumers, int light, int overlay) {
        if (cabinet.displayedCard().isEmpty()) return;
        var facing = cabinet.getCachedState().get(CardDisplayCaseBlock.FACING);
        matrices.push();
        applyPlacement(matrices, facing);
        itemRenderer.renderOnStand(cabinet.displayedCard(), matrices, consumers, light, OverlayTexture.DEFAULT_UV);
        matrices.pop();
    }

    /** Shared placement for both thumbnail and HD artwork, independent of item-model transforms. */
    public static void applyPlacement(MatrixStack matrices, net.minecraft.util.math.Direction facing) {
        // Rotate the entire stand-local offset about the block centre. The
        // north-facing shelf is at y=3/16, in front of the rear support.
        matrices.translate(0.5, 0, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180 - facing.asRotation()));
        matrices.translate(0, 1 + 3.0 / 16, 0.10);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
        matrices.scale(0.58f, 0.80f, 0.04f);
        matrices.translate(-0.5, 0, -0.5);
    }

    @Override public boolean rendersOutsideBoundingBox(CardDisplayCaseBlockEntity cabinet) { return true; }
}
