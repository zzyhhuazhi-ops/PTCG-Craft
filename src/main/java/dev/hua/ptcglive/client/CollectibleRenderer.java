package dev.hua.ptcglive.client;

import dev.hua.ptcglive.collectibles.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;

/** Shares card art cache with inventory/display frames; no images or textures created per frame. */
public final class CollectibleRenderer implements BlockEntityRenderer<CollectibleEntity> {
    private static final Identifier WHITE=Identifier.ofVanilla("textures/block/white_concrete.png");
    private final CardItemRenderer cards;
    public CollectibleRenderer(CardItemRenderer cards){this.cards=cards;}
    @Override public void render(CollectibleEntity be,float delta,MatrixStack m,VertexConsumerProvider out,int light,int overlay){
        if(be.kind()!=CollectibleBlocks.Kind.HOLOGRAM||!CollectibleStructure.origin(be.getCachedState())||be.card(0).isEmpty())return;
        double distance=MinecraftClient.getInstance().gameRenderer.getCamera().getPos().squaredDistanceTo(Vec3d.ofCenter(be.getPos()));if(distance>40*40)return;
        float time=be.getWorld()==null?0:be.getWorld().getTime()+delta;
        int color=switch(be.lighting()){case 1->0xFFFFD35C;case 2->0xFF5BFFD7;default->0xFF6F9AFF;};
        var face=be.getCachedState().get(net.minecraft.state.property.Properties.HORIZONTAL_FACING);
        var right=face.rotateYClockwise();var back=face.getOpposite();
        float scale=be.zoom()==1?1.95f:1.5f;
        m.push();m.translate(.5+(right.getOffsetX()+back.getOffsetX())*.5,.55+scale/2,.5+(right.getOffsetZ()+back.getOffsetZ())*.5);
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time*1.2f));
        if(be.background()>0&&distance<24*24){
            m.push();m.scale(scale,scale,scale);
            // A translucent nebula lives behind the card, never across the printed rules.
            for(int i=0;i<32;i++){
                float x=(float)Math.sin(i*17.31+time*.006)*.65f,y=(float)Math.cos(i*6.14+time*.01)*.52f;
                int alpha=65+(int)(55*Math.sin(time*.04+i));
                double size=be.background()==2?.06+.035*Math.sin(i+time*.02):.012;
                quad(m,out,x,y,size,size,(alpha<<24)|(color&0xFFFFFF),-.07-Math.abs(Math.sin(i))*.12);
            }
            m.pop();
        }
        for(int side=0;side<2;side++){
            m.push();if(side==1)m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
            m.scale(scale,scale,.015f);m.translate(-.5,-.5,0);cards.renderInFrame(be.card(0),m,out,LightmapTextureManager.MAX_LIGHT_COORDINATE,distance<16*16);m.pop();
        }
        if(be.fx()>0&&distance<24*24){
            for(int ring=0;ring<(be.fx()==2?3:1);ring++)for(int i=0;i<48;i++){
                double angle=i*Math.PI*2/48+time*.018*(ring%2==0?1:-1);
                m.push();m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(ring==1?24:ring==2?-24:0));
                m.translate(Math.cos(angle)*.72,ring==0?-.53:Math.sin(angle*2+time*.018)*.2,Math.sin(angle)*.72);
                m.multiply(RotationAxis.POSITIVE_Y.rotation((float)-angle));
                quad(m,out,-.042,-.012,.084,.024,(0x90<<24)|(color&0xFFFFFF),0);
                quad(m,out,-.052,-.03,.104,.06,(0x22<<24)|(color&0xFFFFFF),-.002);m.pop();
            }
            // Two counter-rotating rising streams connect the emitter to the projection.
            for(int i=0;i<32;i++){
                double progress=(i/32.0+time*.003)%1,angle=i*2.399+time*.025;
                m.push();m.translate(Math.cos(angle)*(.35+progress*.35),-.68+progress*1.35,Math.sin(angle)*(.35+progress*.35));
                quad(m,out,-.012,-.012,.024,.024,(0x70<<24)|(color&0xFFFFFF),0);m.pop();
            }
        }
        m.pop();
    }
    private static void quad(MatrixStack m,VertexConsumerProvider out,double x,double y,double w,double h,int color,double z){
        var v=out.getBuffer(RenderLayer.getEntityTranslucent(WHITE));var e=m.peek();
        vertex(v,e,x,y,z,color);vertex(v,e,x+w,y,z,color);vertex(v,e,x+w,y+h,z,color);vertex(v,e,x,y+h,z,color);
    }
    private static void vertex(VertexConsumer v,MatrixStack.Entry m,double x,double y,double z,int color){v.vertex(m,(float)x,(float)y,(float)z).color(color).texture(0,0).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(m,0,0,1);}
    @Override public boolean rendersOutsideBoundingBox(CollectibleEntity be){return true;}
    @Override public int getRenderDistance(){return 48;}
}
