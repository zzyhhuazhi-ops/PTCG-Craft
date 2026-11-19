package dev.hua.ptcglive.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.util.Identifier;

/** Authored procedural flipbook radiance. No sampled Pocket texture or frame. */
final class BattleGlowShader {
    private static ShaderProgram shader;
    private static final Identifier CORE=Identifier.of("ptcglive_mc","textures/gui/battle/electric_core_v1.png");
    private static final double[] MATERIAL_TIMES={0,33,50,66,83,100,116,150,183,216,250,283,316,350,400,450};
    static void register(){PocketTableRenderer.register();EvolutionGlowShader.register();ElementalImpactShader.register();KnockoutAnimation.register();TrainerPlayAnimation.register();CoreShaderRegistrationCallback.EVENT.register(c->c.register(
        Identifier.of("ptcglive","battle_lightning"),VertexFormats.POSITION_TEXTURE_COLOR,p->shader=p));}
    static boolean draw(DrawContext context,double x,double y,double radius,LightningAnimationRenderer.State state,double ms) {
        var profile=state.profile;
        double intensity=profile.core(ms)*profile.intensity();
        if(shader==null||intensity<=0||radius<=0)return false;
        context.draw();
        // Radiance adds light: an outer gold texel must not tint an already-white spike.
        RenderSystem.enableBlend();RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA,GlStateManager.DstFactor.ONE);RenderSystem.disableDepthTest();RenderSystem.disableCull();
        try {
            RenderSystem.setShader(()->shader);
            RenderSystem.setShaderTexture(0,CORE);
            net.minecraft.client.MinecraftClient.getInstance().getTextureManager().getTexture(CORE).setFilter(true,false);
            double materialTime=profile.materialTime(ms);int frame=0;
            while(frame<MATERIAL_TIMES.length-2&&materialTime>=MATERIAL_TIMES[frame+1])frame++;
            double mix=Math.clamp((materialTime-MATERIAL_TIMES[frame])/(MATERIAL_TIMES[frame+1]-MATERIAL_TIMES[frame]),0,1);
            shader.getUniformOrDefault("AtlasFrames").set((float)frame,(float)(frame+1),(float)mix);
            shader.getUniformOrDefault("Intensity").set((float)profile.intensity());
            int gold=profile.glowColor(ms),white=profile.coreColor();
            shader.getUniformOrDefault("GoldColor").set(((gold>>16)&255)/255f,((gold>>8)&255)/255f,(gold&255)/255f);
            shader.getUniformOrDefault("CoreColor").set(((white>>16)&255)/255f,((white>>8)&255)/255f,(white&255)/255f);
            var matrix=context.getMatrices().peek().getPositionMatrix();
            var b=Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_TEXTURE_COLOR);
            float left=(float)(x-radius),right=(float)(x+radius),top=(float)(y-radius),bottom=(float)(y+radius);
            b.vertex(matrix,left,top,0).texture(0,0).color(-1);b.vertex(matrix,left,bottom,0).texture(0,1).color(-1);
            b.vertex(matrix,right,bottom,0).texture(1,1).color(-1);b.vertex(matrix,right,top,0).texture(1,0).color(-1);
            BufferRenderer.drawWithGlobalProgram(b.end());
        } finally {
            RenderSystem.defaultBlendFunc();RenderSystem.enableCull();RenderSystem.enableDepthTest();RenderSystem.disableBlend();
            RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        }
        return true;
    }
}
