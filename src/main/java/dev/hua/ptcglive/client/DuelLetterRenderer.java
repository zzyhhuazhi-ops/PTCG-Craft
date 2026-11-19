package dev.hua.ptcglive.client;

import dev.hua.ptcglive.duel.*;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** A flat spinning envelope aligned to its velocity, never a camera-facing snowball sprite. */
public final class DuelLetterRenderer extends EntityRenderer<DuelLetterEntity> {
    private final ItemRenderer items;
    private final ItemStack letter=new ItemStack(DuelLetters.ITEM);
    public DuelLetterRenderer(EntityRendererFactory.Context context){super(context);items=context.getItemRenderer();}
    @Override public Identifier getTexture(DuelLetterEntity e){return net.minecraft.screen.PlayerScreenHandler.BLOCK_ATLAS_TEXTURE;}
    @Override public void render(DuelLetterEntity e,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider vertices,int light){
        var v=e.getVelocity();
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotation((float)Math.atan2(-v.x,v.z)));
        matrices.multiply(RotationAxis.POSITIVE_X.rotation((float)-Math.atan2(v.y,Math.sqrt(v.x*v.x+v.z*v.z))));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((e.age+delta)*48));
        matrices.scale(0.55f,0.55f,0.55f);
        items.renderItem(letter,ModelTransformationMode.NONE,light,OverlayTexture.DEFAULT_UV,matrices,vertices,e.getWorld(),e.getId());
        matrices.pop();super.render(e,yaw,delta,matrices,vertices,light);
    }
}
