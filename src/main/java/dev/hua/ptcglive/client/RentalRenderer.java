package dev.hua.ptcglive.client;
import dev.hua.ptcglive.rental.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.*;
public final class RentalRenderer implements BlockEntityRenderer<RentalBlockEntity> {
    private final CardItemRenderer cards;
    public RentalRenderer(CardItemRenderer cards){this.cards=cards;}
    @Override public void render(RentalBlockEntity entity,float delta,MatrixStack matrices,VertexConsumerProvider consumers,int light,int overlay){
        var stack=entity.card();
        var camera=MinecraftClient.getInstance().gameRenderer.getCamera().getPos();var center=Vec3d.ofCenter(entity.getPos());
        double distance=camera.squaredDistanceTo(center);if(distance>32*32)return;
        var face=entity.getCachedState().get(RentalBlock.FACING);
        if(camera.subtract(center).dotProduct(Vec3d.of(face.getVector()))<0)return;
        int frontLight=entity.getWorld()==null?light:WorldRenderer.getLightmapCoordinates(entity.getWorld(),entity.getPos().offset(face));
        matrices.push();matrices.translate(.5,.525,.5);matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-face.asRotation()));
        if(!stack.isEmpty()) {
        matrices.push();
        // Inside the recessed front window, behind the gold/silver rim. Preserve the source aspect ratio.
        matrices.translate(0,0,.340);matrices.scale(.54f,.54f,.01f);matrices.translate(-.5,-.5,-.5);
        cards.renderInFrame(stack,matrices,consumers,frontLight,distance<12*12);matrices.pop();
        }
        double angle=Math.abs(camera.subtract(center).dotProduct(Vec3d.of(face.getVector())))/Math.max(.001,Math.sqrt(distance));
        CardFrameRenderer.renderGlassBevel(matrices.peek(),consumers,-.24f,.24f,-.28f,.28f,.365f,
            30+(int)(28*(1-angle)),frontLight);
        matrices.pop();
    }
}
