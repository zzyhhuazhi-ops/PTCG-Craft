package dev.hua.ptcglive.duel;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;

/** A swept, constant-speed card flight. No gravity, damage, loot or block mutation. */
public final class DuelLetterEntity extends ProjectileEntity {
    public DuelLetterEntity(EntityType<? extends DuelLetterEntity> type,World world){super(type,world);setNoGravity(true);}
    @Override protected void initDataTracker(DataTracker.Builder builder){}
    @Override public void tick(){
        super.tick();
        if(!getWorld().isClient && (age>40 || !(getOwner() instanceof ServerPlayerEntity owner) || !owner.isAlive())){discard();return;}
        var hit=ProjectileUtil.getCollision(this,this::canHit);
        if(hit.getType()!=HitResult.Type.MISS){
            if(!getWorld().isClient){
                if(hit instanceof EntityHitResult entity && entity.getEntity() instanceof ServerPlayerEntity target
                    && getOwner() instanceof ServerPlayerEntity sender)DuelLetters.hit(sender,target);
                discard();
            }
            return;
        }
        setPosition(getPos().add(getVelocity()));
        updateRotation();
    }
}
