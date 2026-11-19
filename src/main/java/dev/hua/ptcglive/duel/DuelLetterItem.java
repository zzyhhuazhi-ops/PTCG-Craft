package dev.hua.ptcglive.duel;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

public final class DuelLetterItem extends Item {
    public DuelLetterItem(){super(new Settings().maxCount(64));}
    @Override public TypedActionResult<ItemStack> use(World world,PlayerEntity user,Hand hand){
        var stack=user.getStackInHand(hand);
        if(user.isSpectator()||user.getItemCooldownManager().isCoolingDown(this))return TypedActionResult.fail(stack);
        if(!world.isClient && user instanceof ServerPlayerEntity player){
            if(DuelLetters.resume(player))return TypedActionResult.success(stack);
            if(dev.hua.ptcglive.PtcgServices.tables().busy(player.getUuid())){
                player.sendMessage(Text.literal("请先结束当前对局。"),true);return TypedActionResult.fail(stack);
            }
            var letter=new DuelLetterEntity(DuelLetters.TYPE,world);letter.setOwner(user);
            letter.setPosition(user.getEyePos().add(0,-0.12,0));
            // Do not inherit walking/falling velocity: the card follows the aiming ray exactly.
            letter.setVelocity(user.getRotationVec(1).multiply(1.5));
            if(!world.spawnEntity(letter))return TypedActionResult.fail(stack);
            world.playSound(null,user.getX(),user.getY(),user.getZ(),SoundEvents.ENTITY_SNOWBALL_THROW,SoundCategory.PLAYERS,0.65f,1.6f);
            if(!user.isCreative())stack.decrement(1);
        }
        user.getItemCooldownManager().set(this,30);
        user.swingHand(hand);
        return TypedActionResult.success(stack,world.isClient);
    }
}
