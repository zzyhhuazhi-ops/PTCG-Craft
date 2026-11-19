package dev.hua.ptcglive.client;

import dev.hua.ptcglive.display.CardFrameBlock;
import dev.hua.ptcglive.display.CardFrameBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;

public final class CardFrameRenderer implements BlockEntityRenderer<CardFrameBlockEntity> {
    private static final Identifier WHITE=Identifier.of("minecraft","textures/block/white_concrete.png");
    // The backing front is 7/16 behind the cell center. Keep the card just
    // above it, behind the inset mounts (6/16), without coplanar flicker.
    private static final double CARD_DEPTH = -7.0 / 16.0 + 0.001;
    private final CardItemRenderer cards;
    public CardFrameRenderer(CardItemRenderer cards) { this.cards=cards; }
    @Override public void render(CardFrameBlockEntity frame,float tickDelta,MatrixStack matrices,
                                 VertexConsumerProvider consumers,int light,int overlay) {
        var state=frame.getCachedState();
        if(!(state.getBlock() instanceof CardFrameBlock block) || !CardFrameBlock.anchor(state)) return;
        var camera=MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        var face=state.get(CardFrameBlock.FACING);
        var center=Vec3d.ofCenter(frame.getPos());
        var toward=camera.subtract(center);
        if(toward.dotProduct(Vec3d.of(face.getVector())) < -0.1) return;
        matrices.push(); orientFrame(matrices,face);
        for(int row=0;row<block.rows();row++) for(int col=0;col<block.columns();col++) {
            if(block.enlarged()&&(col!=0||row!=0))continue;
            var stack=frame.card(row*block.columns()+col); if(stack.isEmpty()) continue;
            var part=CardFrameBlock.cell(frame.getPos(),face,col,row);
            double distance=camera.squaredDistanceTo(Vec3d.ofCenter(part));
            if(distance>48*48) continue;
            matrices.push(); matrices.translate(block.enlarged()?.5:col,block.enlarged()?.5:row,CARD_DEPTH);
            float cardScale=block.enlarged()?1.84f:.84f;
            matrices.scale(cardScale,cardScale,.02f); matrices.translate(-.5,-.5,-.5);
            int localLight=frame.getWorld()==null?light:WorldRenderer.getLightmapCoordinates(frame.getWorld(),part.offset(face));
            cards.renderInFrame(stack,matrices,consumers,localLight,distance<12*12);
            // Actual item glint metadata only. Rarity is deliberately not consulted.
            if(CardFrameClientOptions.dynamicEffects && stack.hasGlint() && distance<12*12) {
                float phase=(frame.getWorld().getTime()+tickDelta)*.045f + (float)Math.atan2(toward.x,toward.z);
                float x=.16f+.68f*(.5f+.5f*(float)Math.sin(phase));
                var buffer=consumers.getBuffer(RenderLayer.getEntityTranslucent(WHITE));
                var m=matrices.peek();
                shine(buffer,m,Math.max(.15f,x-.07f),0,0,localLight);
                shine(buffer,m,Math.min(.85f,x+.07f),0,20,localLight);
                shine(buffer,m,Math.min(.85f,x+.07f),1,0,localLight);
                shine(buffer,m,Math.max(.15f,x-.07f),1,20,localLight);
            }
            matrices.pop();
        }
        // One clear cover for the whole frame, including empty frames. Only
        // its bevel is drawn: no tinted sheet or streak crosses the card art.
        if(toward.lengthSquared()<32*32) {
            double facing=Math.abs(toward.dotProduct(Vec3d.of(face.getVector())))
                / Math.max(.001,toward.length());
            int alpha=CardFrameClientOptions.dynamicEffects ? 28+(int)(28*(1-facing)) : 36;
            int coverLight=frame.getWorld()==null?light:WorldRenderer.getLightmapCoordinates(frame.getWorld(),frame.getPos().offset(face));
            renderGlassEdge(matrices.peek(),consumers,block.columns(),block.rows(),alpha,coverLight);
        }
        matrices.pop();
    }
    static void orientFrame(MatrixStack matrices,Direction face) {
        matrices.translate(.5,.5,.5);
        // Direction yaw is clockwise, whereas the matrix's positive Y rotation is
        // counterclockwise. Local +Z must face outward and +X follow the frame cells.
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-face.asRotation()));
    }
    private static void renderGlassEdge(MatrixStack.Entry entry,VertexConsumerProvider consumers,
                                       int columns,int rows,int alpha,int light) {
        float left=-.4375f,right=columns-1+.4375f,bottom=-.4375f,top=rows-1+.4375f;
        renderGlassBevel(entry,consumers,left,right,bottom,top,-.3115f,alpha,light);
    }
    static void renderGlassBevel(MatrixStack.Entry entry,VertexConsumerProvider consumers,
                                 float left,float right,float bottom,float top,float front,int alpha,int light) {
        var buffer=consumers.getBuffer(RenderLayer.getEntityTranslucent(WHITE));
        float bevel=.012f,back=front-.012f;
        // The inner edge fades to zero, outside the card's maximum +/- .42 footprint.
        glassVertex(buffer,entry,left,bottom,front,alpha,light);
        glassVertex(buffer,entry,left+bevel,bottom,front,0,light);
        glassVertex(buffer,entry,left+bevel,top,front,0,light);
        glassVertex(buffer,entry,left,top,front,alpha,light);
        glassVertex(buffer,entry,right-bevel,bottom,front,0,light);
        glassVertex(buffer,entry,right,bottom,front,alpha,light);
        glassVertex(buffer,entry,right,top,front,alpha,light);
        glassVertex(buffer,entry,right-bevel,top,front,0,light);
        glassVertex(buffer,entry,left+bevel,top-bevel,front,0,light);
        glassVertex(buffer,entry,right-bevel,top-bevel,front,0,light);
        glassVertex(buffer,entry,right-bevel,top,front,alpha,light);
        glassVertex(buffer,entry,left+bevel,top,front,alpha,light);
        glassVertex(buffer,entry,left+bevel,bottom,front,alpha/2,light);
        glassVertex(buffer,entry,right-bevel,bottom,front,alpha/2,light);
        glassVertex(buffer,entry,right-bevel,bottom+bevel,front,0,light);
        glassVertex(buffer,entry,left+bevel,bottom+bevel,front,0,light);
        // Thin exposed edges give the cover thickness when viewed obliquely.
        glassVertex(buffer,entry,left,bottom,back,alpha/2,light);
        glassVertex(buffer,entry,left,bottom,front,alpha,light);
        glassVertex(buffer,entry,left,top,front,alpha,light);
        glassVertex(buffer,entry,left,top,back,alpha/2,light);
        glassVertex(buffer,entry,right,bottom,front,alpha,light);
        glassVertex(buffer,entry,right,bottom,back,alpha/2,light);
        glassVertex(buffer,entry,right,top,back,alpha/2,light);
        glassVertex(buffer,entry,right,top,front,alpha,light);
    }
    private static void glassVertex(VertexConsumer buffer,MatrixStack.Entry entry,float x,float y,float z,int alpha,int light) {
        buffer.vertex(entry,x,y,z).color(205,235,255,alpha).texture(0,0)
            .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry,0,0,1);
    }
    private static void shine(VertexConsumer buffer,MatrixStack.Entry entry,float x,float y,int alpha,int light) {
        buffer.vertex(entry,x,y,.52f).color(220,240,255,alpha).texture(0,0)
            .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry,0,0,1);
    }
    @Override public boolean rendersOutsideBoundingBox(CardFrameBlockEntity frame) { return true; }
    @Override public int getRenderDistance() { return 52; }
}
