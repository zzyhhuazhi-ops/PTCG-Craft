package dev.hua.ptcglive.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;

/** Projects only the public reveal card. The field layout and hit boxes stay unchanged. */
final class EvolutionCardRenderer {
    private EvolutionCardRenderer(){}
    @FunctionalInterface interface Vertex { void emit(double x,double y,float u,float v); }
    // A small fixed grid approximates perspective-correct UVs with the existing GUI shader.
    static final int GRID=12;
    record Projection(EvolutionAnimation.Pose pose,double cy,double sy,double cx,double sx,double cz,double sz,double focal) {
        static Projection of(EvolutionAnimation.Pose p){
            return of(p,false,p.height()*4.5);
        }
        static Projection trainer(EvolutionAnimation.Pose p,double focalDistance){
            return of(p,true,focalDistance);
        }
        private static Projection of(EvolutionAnimation.Pose p,boolean trainer,double focalDistance){
            // Evolution makes two turns. Trainers use a single flip and must not
            // inherit the evolution-only gate that suppresses its early yaw.
            double face=trainer?1:Math.clamp((p.flip()-2.5)*2,0,1);
            double yaw=Math.toRadians((p.flip()-1)*180+p.yaw()*face);
            double pitch=Math.toRadians(p.pitch()*face),roll=Math.toRadians(p.tilt());
            return new Projection(p,Math.cos(yaw),Math.sin(yaw),Math.cos(pitch),Math.sin(pitch),Math.cos(roll),Math.sin(roll),focalDistance);
        }
        void vertex(Vertex sink,double u,double v){
            double x=(u-.5)*pose.width(),y=(v-.5)*pose.height();
            double rx=x*cy,z=-x*sy,ry=y*cx-z*sx;
            z=y*sx+z*cx;
            double scale=1/(1-z/focal);
            sink.emit(pose.x()+(rx*cz-ry*sz)*scale,pose.y()+(rx*sz+ry*cz)*scale,(float)u,(float)v);
        }
    }
    static void mesh(EvolutionAnimation.Pose pose,Vertex sink){
        mesh(Projection.of(pose),sink);
    }
    static void mesh(Projection p,Vertex sink){
        for(int row=0;row<GRID;row++)for(int col=0;col<GRID;col++){
            double u=col/(double)GRID,v=row/(double)GRID,du=1.0/GRID;
            p.vertex(sink,u,v);p.vertex(sink,u,v+du);p.vertex(sink,u+du,v+du);p.vertex(sink,u+du,v);
        }
    }
    static boolean draw(DrawContext c,CardArt art,EvolutionAnimation.Pose pose){
        return draw(c,art,Projection.of(pose));
    }
    static boolean drawTrainer(DrawContext c,CardArt art,EvolutionAnimation.Pose pose,double focalDistance){
        return draw(c,art,Projection.trainer(pose,focalDistance));
    }
    private static boolean draw(DrawContext c,CardArt art,Projection projection){
        boolean back=!projection.pose().front();
        if(back)art=BattleCosmetics.cardBack();
        if(art==null)return false;
        c.draw();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableCull();
        try{
            RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
            RenderSystem.setShaderTexture(0,art.texture());
            var matrix=c.getMatrices().peek().getPositionMatrix();
            var b=Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_TEXTURE_COLOR);
            mesh(projection,(x,y,u,v)->b.vertex(matrix,(float)x,(float)y,0).texture(back?1-u:u,v).color(-1));
            BufferRenderer.drawWithGlobalProgram(b.end());
        }finally{RenderSystem.enableCull();RenderSystem.disableBlend();}
        return true;
    }
}
