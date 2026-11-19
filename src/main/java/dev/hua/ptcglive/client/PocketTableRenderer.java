package dev.hua.ptcglive.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import static dev.hua.ptcglive.client.PocketTableCamera.*;

/** Dedicated world-to-clip pass. Card UVs use the GPU's perspective interpolation. */
final class PocketTableRenderer {
    private static ShaderProgram shader;
    private static final Identifier WHITE=Identifier.of("minecraft","textures/block/white_concrete.png");
    static void register(){CoreShaderRegistrationCallback.EVENT.register(c->c.register(Identifier.of("ptcglive","pocket_table"),VertexFormats.POSITION_TEXTURE_COLOR,p->shader=p));}
    static boolean ready(){return shader!=null;}
    static void begin(DrawContext c,PocketTableCamera camera,BattleTableEnvironment environment,double seconds,BattleAnimationEngine.Quality quality){
        c.draw();RenderSystem.clear(256,false);RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();RenderSystem.depthFunc(515);RenderSystem.depthMask(true);RenderSystem.disableCull();
        RenderSystem.setShader(()->shader);
        upload("Own",environment.own.weights);upload("Opponent",environment.opponent.weights);
        shader.getUniformOrDefault("Environment").set((float)(quality==BattleAnimationEngine.Quality.OFF?0:seconds%3600),
            (float)camera.screenWidth/camera.screenHeight,quality==BattleAnimationEngine.Quality.HIGH?1f:quality==BattleAnimationEngine.Quality.MEDIUM?.6f:quality==BattleAnimationEngine.Quality.LOW?.25f:0f,(float)camera.tableDepth);
        shader.getUniformOrDefault("CameraMatrix").set(new Matrix4f());shader.getUniformOrDefault("Material").set(6f);
        RenderSystem.setShaderTexture(0,WHITE);
        var b=Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_TEXTURE_COLOR);
        b.vertex(-1,1,0).texture(0,0).color(-1);b.vertex(-1,-1,0).texture(0,1).color(-1);
        b.vertex(1,-1,0).texture(1,1).color(-1);b.vertex(1,1,0).texture(1,0).color(-1);
        BufferRenderer.drawWithGlobalProgram(b.end());
        RenderSystem.enableDepthTest();shader.getUniformOrDefault("CameraMatrix").set(camera.clip);
    }
    private static void upload(String side,float[] w){
        shader.getUniformOrDefault(side+"A").set(w[0],w[1],w[2],w[3]);
        shader.getUniformOrDefault(side+"B").set(w[4],w[5],w[6],w[7]);
        shader.getUniformOrDefault(side+"C").set(w[8],w[9],w[10]);
    }
    static void end(){
        RenderSystem.depthMask(true);RenderSystem.clear(256,false);RenderSystem.enableCull();RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
    }
    static void table(PocketTableCamera c){
        quad(new Pose(0,-.10,.025,TABLE_WIDTH+.10,c.tableDepth+.10,0,0),null,0xFF9CAFBF,0);
        var mat=BattleCosmetics.playmat();
        quad(new Pose(0,0,0,TABLE_WIDTH,c.tableDepth,0,0),mat,-1,mat==null?2:0);
    }
    static void slot(Pose p,int tint){quad(p.move(0,-.012,0).scale(1.05),null,tint,3);}
    static void focus(Pose pose,int tint){
        RenderSystem.depthMask(false);
        try{
            quad(pose.scale(1.10),null,tint,8);
        }finally{
            RenderSystem.depthMask(true);
        }
    }
    static void turnTiles(PocketTableCamera camera,double progress,boolean own){
        shader.getUniformOrDefault("TurnTiles").set((float)progress,own?1f:0f);
        RenderSystem.depthMask(false);
        try {
            quad(new Pose(0,.001,0,TABLE_WIDTH,camera.tableDepth,0,0),null,-1,7);
        } finally {
            RenderSystem.depthMask(true);
        }
    }
    static void flatTurnTiles(DrawContext context,PtcgLiveTableLayout layout,double progress,boolean own,BattleAnimationEngine.Quality quality){
        context.draw();
        var area=layout.arena();
        float strength=quality==BattleAnimationEngine.Quality.HIGH?1f:quality==BattleAnimationEngine.Quality.MEDIUM?.6f:.25f;
        float left=2f*area.x()/layout.screenWidth()-1,right=2f*area.right()/layout.screenWidth()-1;
        float top=1-2f*area.y()/layout.screenHeight(),bottom=1-2f*area.bottom()/layout.screenHeight();
        var previous=RenderSystem.getShader();
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.depthMask(false);
        try {
            RenderSystem.setShader(()->shader);
            shader.getUniformOrDefault("CameraMatrix").set(new Matrix4f());
            shader.getUniformOrDefault("Material").set(7f);
            shader.getUniformOrDefault("Environment").set(0f,1f,strength,18f*area.height()/area.width());
            shader.getUniformOrDefault("TurnTiles").set((float)progress,own?1f:0f);
            var b=Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_TEXTURE_COLOR);
            b.vertex(left,top,-.99f).texture(0,0).color(-1);b.vertex(left,bottom,-.99f).texture(0,1).color(-1);
            b.vertex(right,bottom,-.99f).texture(1,1).color(-1);b.vertex(right,top,-.99f).texture(1,0).color(-1);
            BufferRenderer.drawWithGlobalProgram(b.end());
        } finally {
            RenderSystem.depthMask(true);RenderSystem.disableBlend();RenderSystem.setShader(()->previous);
        }
    }
    static void card(Pose p,CardArt art,int border,double alpha){
        card(p,art,border,alpha,0xFFFFFF);
    }
    static void card(Pose p,CardArt art,int border,double alpha,int faceTint){
        if(alpha<=0)return;
        int opacity=(int)(Math.clamp(alpha,0,1)*255);
        RenderSystem.depthMask(false);
        quad(new Pose(p.x()+.035,.008,p.z()+.06,p.width()*1.14,p.height()*1.12,0,p.yaw()),null,((int)(55*alpha)<<24)|0x33435C,4);
        RenderSystem.depthMask(true);
        quad(p.scale(1.025),null,(opacity<<24)|(border&0xFFFFFF),0);
        quad(p.move(0,.004,0),art==null?BattleCosmetics.cardBack():art,(opacity<<24)|(faceTint&0xFFFFFF),0);
    }
    private static void quad(Pose p,CardArt art,int color,int material){
        RenderSystem.setShaderTexture(0,art==null?WHITE:art.texture());
        shader.getUniformOrDefault("Material").set((float)(art==null&&material==0?5:material));
        var b=Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_TEXTURE_COLOR);
        for(int i=0;i<4;i++){
            float u=i<2?0:1,v=i==0||i==3?0:1;var point=p.vertex(u,v);
            b.vertex((float)point.x(),(float)point.y(),(float)point.z()).texture(u,v).color(color);
        }
        BufferRenderer.drawWithGlobalProgram(b.end());
    }
    /** Lift a world plane into the HUD: both endpoints retain homogeneous perspective. */
    static void inspect(DrawContext c,PocketTableCamera camera,Pose origin,CardArt art,PtcgLiveTableLayout.Rect target,double progress){
        c.draw();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableDepthTest();RenderSystem.disableCull();
        RenderSystem.setShader(()->shader);shader.getUniformOrDefault("CameraMatrix").set(new Matrix4f());
        RenderSystem.setShaderTexture(0,(art==null?BattleCosmetics.cardBack():art).texture());shader.getUniformOrDefault("Material").set(0f);
        var b=Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_TEXTURE_COLOR);
        // Interpolate in view space, then project. A common target W turns the card face-on.
        double targetDepth=camera.project(origin).depth()*.68;
        for(int i=0;i<4;i++){
            float u=i<2?0:1,v=i==0||i==3?0:1;var p=origin.vertex(u,v);
            var q=new Vector4f((float)p.x(),(float)p.y(),(float)p.z(),1).mul(camera.clip);
            double tx=((target.x()+u*target.width())*2/camera.screenWidth-1)*targetDepth;
            double ty=(1-(target.y()+v*target.height())*2/camera.screenHeight)*targetDepth;
            double w=q.w+(targetDepth-q.w)*progress;
            // Preserve W in a dedicated uniform per vertex encoded through the normal-free Color input is unsuitable;
            // use camera-space Z with a small perspective matrix instead.
            double x=q.x+(tx-q.x)*progress,y=q.y+(ty-q.y)*progress;
            b.vertex((float)x,(float)y,(float)-w).texture(u,v).color(-1);
        }
        var projection=new Matrix4f().zero();projection.m00(1).m11(1).m22(-1).m23(-1);
        shader.getUniformOrDefault("CameraMatrix").set(projection);
        try{BufferRenderer.drawWithGlobalProgram(b.end());}finally{RenderSystem.enableCull();RenderSystem.enableDepthTest();RenderSystem.disableBlend();RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);}
    }
}
