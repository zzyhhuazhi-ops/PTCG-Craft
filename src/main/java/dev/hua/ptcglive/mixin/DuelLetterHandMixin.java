package dev.hua.ptcglive.mixin;

import dev.hua.ptcglive.client.DuelLetterAnimation;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Brief wrist wind-up, sideways snap and recovery, replacing the generic item swing. */
@Mixin(HeldItemRenderer.class)
abstract class DuelLetterHandMixin {
    @Shadow protected abstract void renderArmHoldingItem(MatrixStack matrices,VertexConsumerProvider vertices,int light,float equip,float swing,Arm arm);
    @Shadow public abstract void renderItem(LivingEntity entity,ItemStack stack,ModelTransformationMode mode,boolean left,MatrixStack matrices,VertexConsumerProvider vertices,int light);
    @Inject(method="renderFirstPersonItem",at=@At("HEAD"),cancellable=true)
    private void ptcg$flick(AbstractClientPlayerEntity player,float delta,float pitch,Hand hand,float swing,ItemStack item,float equip,MatrixStack matrices,VertexConsumerProvider vertices,int light,CallbackInfo ci){
        float t=DuelLetterAnimation.progress(player,hand,delta);if(t<0)return;
        Arm arm=hand==Hand.MAIN_HAND?player.getMainArm():player.getMainArm().getOpposite();int side=arm==Arm.RIGHT?1:-1;
        float motion=t<0.24f?-t/0.24f:t<0.55f?-1+2*(t-0.24f)/0.31f:(1-t)/0.45f;
        matrices.push();
        matrices.translate(side*motion*0.18,-Math.sin(t*Math.PI)*0.08,-Math.max(0,motion)*0.16);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side*motion*-32));
        if(!player.isInvisible())renderArmHoldingItem(matrices,vertices,light,0,0,arm);
        if(t<0.55f){
            matrices.translate(side*0.48,-0.32,-0.72);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side*20));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side*-28));
            matrices.scale(0.58f,0.58f,0.58f);
            renderItem(player,DuelLetterAnimation.stack(),ModelTransformationMode.NONE,arm==Arm.LEFT,matrices,vertices,light);
        }
        matrices.pop();ci.cancel();
    }
}
