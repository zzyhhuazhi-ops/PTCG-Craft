package dev.hua.ptcglive.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.util.Identifier;
import dev.hua.ptcglive.engine.field.EnergyType;

/** Original layered fire/water material, sampled in seconds; one bounded GUI draw. */
final class ElementalImpactShader {
    private static ShaderProgram shader;
    static void register(){CoreShaderRegistrationCallback.EVENT.register(c->c.register(Identifier.of("ptcglive","battle_elemental"),VertexFormats.POSITION_TEXTURE_COLOR,p->shader=p));}
    static boolean draw(DrawContext c,EnergyType type,double x,double y,double size,double seconds,double core,double shell,double spread){
        if(shader==null||size<=0)return false;
        if(core<=0&&shell<=0)return true;
        c.draw();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableDepthTest();RenderSystem.disableCull();
        try{
            RenderSystem.setShader(()->shader);
            shader.getUniformOrDefault("Phase").set((float)seconds,(float)core,(float)shell,(float)spread);
            shader.getUniformOrDefault("Water").set(type==EnergyType.WATER?1f:0f);
            var m=c.getMatrices().peek().getPositionMatrix();float r=(float)(size*1.5);
            var b=Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_TEXTURE_COLOR);
            b.vertex(m,(float)x-r,(float)y-r,0).texture(0,0).color(-1);
            b.vertex(m,(float)x-r,(float)y+r,0).texture(0,1).color(-1);
            b.vertex(m,(float)x+r,(float)y+r,0).texture(1,1).color(-1);
            b.vertex(m,(float)x+r,(float)y-r,0).texture(1,0).color(-1);
            BufferRenderer.drawWithGlobalProgram(b.end());
        }finally{
            RenderSystem.enableCull();RenderSystem.enableDepthTest();RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        }
        return true;
    }
}
