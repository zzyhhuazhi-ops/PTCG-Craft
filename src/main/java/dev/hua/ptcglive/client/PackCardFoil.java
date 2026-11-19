package dev.hua.ptcglive.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.hua.ptcglive.CardLabel;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

/** Opening-only foil material. Registered with the resource reload lifecycle. */
final class PackCardFoil {
    private static ShaderProgram shader;

    static void register() {
        CoreShaderRegistrationCallback.EVENT.register(context -> context.register(
            Identifier.of("ptcglive", "pack_card_foil"), VertexFormats.POSITION_TEXTURE_COLOR,
            program -> shader = program));
    }

    static int tier(String printedRarity) {
        return switch (CardLabel.rarityCode(printedRarity)) {
            case "U" -> 1;
            case "R", "PR", "K", "A" -> 2;
            case "RR", "RRR", "SR", "ACE" -> 3;
            case "AR", "CHR", "S", "SSR" -> 4;
            case "SAR", "CSR", "HR", "BWR", "FUR" -> 5;
            case "UR", "MUR", "金卡", "★★★" -> 6;
            default -> 0;
        };
    }

    static boolean draw(DrawContext context, CardArt art, int width, int height,
                        String rarity, float time, float tiltX, float tiltY, float alpha) {
        if (shader == null || art == null) return false;
        context.draw();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        try {
            RenderSystem.setShader(() -> shader);
            RenderSystem.setShaderTexture(0, art.texture());
            shader.getUniformOrDefault("FoilTime").set(time);
            shader.getUniformOrDefault("FoilTilt").set(tiltX / 22f, tiltY / 22f);
            shader.getUniformOrDefault("FoilTier").set((float) tier(rarity));
            shader.getUniformOrDefault("FoilAlpha").set(alpha);
            var matrix = context.getMatrices().peek().getPositionMatrix();
            var buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            buffer.vertex(matrix, 0, 0, 0).texture(0, 0).color(-1);
            buffer.vertex(matrix, 0, height, 0).texture(0, 1).color(-1);
            buffer.vertex(matrix, width, height, 0).texture(1, 1).color(-1);
            buffer.vertex(matrix, width, 0, 0).texture(1, 0).color(-1);
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        } finally {
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
            RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        }
        return true;
    }
}
