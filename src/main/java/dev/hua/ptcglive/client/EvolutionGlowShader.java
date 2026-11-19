package dev.hua.ptcglive.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.util.Identifier;

/** Original turbulent blue-white shell; no sampled game frames or texture allocation. */
final class EvolutionGlowShader {
    private static ShaderProgram shader;
    static void register(){CoreShaderRegistrationCallback.EVENT.register(c->c.register(Identifier.of("ptcglive","battle_evolution"),VertexFormats.POSITION_TEXTURE_COLOR,p->shader=p));}
    static boolean draw(DrawContext c,double x,double y,double rx,double ry,double seconds,double opacity,double radius,double focus,double focusBottom){
        if(shader==null||(opacity<=0&&focus<=0)||rx<=0||ry<=0)return false;
        float bottom=(float)((focusBottom-y)/ry);
        // Extend the procedural canvas without enlarging the shell itself.
        // UVs outside [0,1] preserve its original world-to-shader scale.
        double canvas=1.6;rx*=canvas;ry*=canvas;
        float uvMin=(float)((1-canvas)/2),uvMax=1-uvMin;
        // Premultiplied coverage plus emission: the shell retains its material
        // color on pale mats while the low-coverage halo still adds light.
        c.draw();RenderSystem.enableBlend();RenderSystem.blendFunc(GlStateManager.SrcFactor.ONE,GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);RenderSystem.disableDepthTest();RenderSystem.disableCull();
        try{
            RenderSystem.setShader(()->shader);
            shader.getUniformOrDefault("Phase").set((float)seconds,(float)opacity,(float)radius,(float)focus);
            shader.getUniformOrDefault("FocusBottom").set(bottom);
            var m=c.getMatrices().peek().getPositionMatrix();
            var b=Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_TEXTURE_COLOR);
            b.vertex(m,(float)(x-rx),(float)(y-ry),0).texture(uvMin,uvMin).color(-1);
            b.vertex(m,(float)(x-rx),(float)(y+ry),0).texture(uvMin,uvMax).color(-1);
            b.vertex(m,(float)(x+rx),(float)(y+ry),0).texture(uvMax,uvMax).color(-1);
            b.vertex(m,(float)(x+rx),(float)(y-ry),0).texture(uvMax,uvMin).color(-1);
            BufferRenderer.drawWithGlobalProgram(b.end());
        }finally{
            RenderSystem.enableCull();RenderSystem.enableDepthTest();RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        }
        return true;
    }
}
