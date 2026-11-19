package dev.hua.ptcglive.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.util.Identifier;

/** Public departing stack only. This renderer cannot move or remove a game card. */
final class KnockoutAnimation {
    private static ShaderProgram shader;
    record Pose(double cardAlpha,double flash,double ring,double radius,double fragments){}
    static Pose sample(double ms){
        var t=BattleAnimationEngine.knockout();
        if(ms<0)return new Pose(1,0,0,0,0);
        if(ms>=t.durationMs)return new Pose(0,0,0,0,1);
        return new Pose(Math.clamp(t.sample("card.alpha",ms,0),0,1),Math.clamp(t.sample("flash",ms,0),0,1),
            Math.clamp(t.sample("ring.alpha",ms,0),0,1),Math.clamp(t.sample("ring.radius",ms,0),0,2),Math.clamp(t.sample("fragments",ms,0),0,1));
    }
    static void register(){CoreShaderRegistrationCallback.EVENT.register(c->c.register(
        Identifier.of("ptcglive","battle_knockout"),VertexFormats.POSITION_TEXTURE_COLOR,p->shader=p));}
    static void draw(DrawContext c,CardArt art,Rect card,double ms,BattleAnimationEngine.Quality quality){
        if(quality==BattleAnimationEngine.Quality.OFF)return;
        var p=sample(ms);double x=card.x()+card.width()/2.,y=card.y()+card.height()/2.,s=card.width();
        if(art!=null&&p.cardAlpha()>0){
            c.draw();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableCull();
            try{
                RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);RenderSystem.setShaderTexture(0,art.texture());
                quad(c,card.x(),card.y(),card.width(),card.height(),((int)(255*p.cardAlpha())<<24)|0xFFFFFF);
            }finally{RenderSystem.enableCull();RenderSystem.disableBlend();}
        }
        // Debris is behind the flash: it must not cut opaque gray holes into the peak.
        fragments(BattleEffects.mesh(c),x,y,s,p.fragments(),quality);
        if(p.flash()>0||p.ring()>0){
            if(shader!=null&&quality==BattleAnimationEngine.Quality.HIGH){
                c.draw();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableCull();RenderSystem.disableDepthTest();
                try{
                    RenderSystem.setShader(()->shader);
                    shader.getUniformOrDefault("Phase").set((float)p.flash(),(float)p.ring(),(float)p.radius(),(float)(ms/1000));
                    quad(c,x-s*1.8,y-s*1.8,s*3.6,s*3.6,-1);
                }finally{RenderSystem.enableCull();RenderSystem.enableDepthTest();RenderSystem.disableBlend();RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);}
            }else{
                var mesh=BattleEffects.mesh(c);
                BattleEffectMesh.glow(mesh,x,y,s*1.3,tint(0x60FFFF,p.flash()));
                BattleEffectMesh.ellipse(mesh,x,y,s*.5,s*.5,tint(0xFFFFFF,p.flash()));
                BattleEffectMesh.ring(mesh,x,y,s*p.radius(),1,s*.035,tint(0x50F5FF,p.ring()));
            }
        }
    }
    private static void quad(DrawContext c,double x,double y,double w,double h,int color){
        var m=c.getMatrices().peek().getPositionMatrix();
        var b=Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_TEXTURE_COLOR);
        b.vertex(m,(float)x,(float)y,0).texture(0,0).color(color);
        b.vertex(m,(float)x,(float)(y+h),0).texture(0,1).color(color);
        b.vertex(m,(float)(x+w),(float)(y+h),0).texture(1,1).color(color);
        b.vertex(m,(float)(x+w),(float)y,0).texture(1,0).color(color);
        BufferRenderer.drawWithGlobalProgram(b.end());
    }
    private static int tint(int rgb,double a){return ((int)(255*Math.clamp(a,0,1))<<24)|rgb;}
    static void fragments(BattleEffectMesh.Sink out,double x,double y,double size,double progress,BattleAnimationEngine.Quality quality){
        if(progress<=0||progress>=1||quality==BattleAnimationEngine.Quality.OFF)return;
        int count=quality==BattleAnimationEngine.Quality.HIGH?28:quality==BattleAnimationEngine.Quality.MEDIUM?16:8;
        for(int i=0;i<count;i++){
            double seed=(i*.61803398875)%1,scatter=(i*.41421356237+.19)%1,angle=i*2.39996323;
            double p=Math.clamp((progress-seed*.15)/(1-seed*.15),0,1);
            // Uneven clusters: a broad lower-left group and a smaller upper-right group.
            // Independent distances avoid the former perfectly circular debris necklace.
            boolean debris=i%3!=0;
            double orbit=Math.PI-(progress-.35)*3.5+(i%4==0?Math.PI:0);
            double cx=Math.cos(orbit)*.65,cy=Math.sin(orbit)*.60;
            double distance=.10+scatter*.25;
            double px=x+size*(cx+Math.cos(angle)*distance+(.10-scatter*.25)*p);
            double py=y+size*(cy+Math.sin(angle)*distance+(seed-.30)*p*.45);
            double a=debris?BattleMotion.smooth(Math.clamp((p-.12)/.16,0,1))*(1-BattleMotion.smooth(Math.clamp((p-.54)/.25,0,1)))
                :BattleMotion.smooth(Math.min(1,p/.10))*(1-BattleMotion.smooth(Math.clamp((p-.55)/.30,0,1)));
            double w=size*(debris?.085+seed*.065:.022+seed*.018)*(1-p*.18);
            int color=tint(debris?0x8CB5C0:0x83FFFF,a*(debris?.76:.95));
            // Concave eight-point chips form clusters, not a necklace of identical hexagons.
            for(int j=0;j<8;j++){
                double a0=angle-j*Math.PI/4,a1=angle-(j+1)*Math.PI/4;
                double r0=w*(.4+((i*7+j*3)%11)/12.),r1=w*(.4+((i*7+(j+1)%8*3)%11)/12.);
                out.vertex(px,py,color);out.vertex(px+Math.cos(a0)*r0,py+Math.sin(a0)*r0,color);
                out.vertex(px+Math.cos(a1)*r1,py+Math.sin(a1)*r1,color);out.vertex(px,py,color);
            }
        }
    }
}
