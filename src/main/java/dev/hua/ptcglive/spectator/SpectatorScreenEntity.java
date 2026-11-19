package dev.hua.ptcglive.spectator;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import java.util.UUID;

/** Controller owns settings; members only carry the structural pointer. No cards or battle internals are stored. */
public final class SpectatorScreenEntity extends BlockEntity {
    private boolean registered;
    public BlockPos origin;
    public int width=1,height=1;
    public boolean valid;
    public UUID match;
    public UUID pendingHost;
    public long pendingTable;
    public boolean names=true,animations=true,damage=true;
    public long finishedAt=-1;
    public SpectatorScreenEntity(BlockPos pos,BlockState state){super(SpectatorScreens.ENTITY,pos,state);origin=pos.toImmutable();}
    public boolean controller(){return valid&&origin.equals(pos);}
    public void register(){if(!registered){registered=true;ScreenStructures.add(this);}}
    @Override public void markRemoved(){if(world!=null&&!world.isClient)ScreenStructures.remove(this);registered=false;super.markRemoved();}
    @Override public void cancelRemoval(){super.cancelRemoval();registered=false;}
    public void sync(){markDirty();if(world!=null)world.updateListeners(pos,getCachedState(),getCachedState(),Block.NOTIFY_LISTENERS);}
    @Override protected void writeNbt(NbtCompound nbt,RegistryWrapper.WrapperLookup registries){
        super.writeNbt(nbt,registries);nbt.putLong("origin",origin.asLong());nbt.putInt("width",width);nbt.putInt("height",height);nbt.putBoolean("valid",valid);
        if(match!=null)nbt.putUuid("match",match);nbt.putBoolean("names",names);nbt.putBoolean("animations",animations);nbt.putBoolean("damage",damage);
        if(pendingHost!=null){nbt.putUuid("pendingHost",pendingHost);nbt.putLong("pendingTable",pendingTable);}
    }
    @Override protected void readNbt(NbtCompound nbt,RegistryWrapper.WrapperLookup registries){
        super.readNbt(nbt,registries);origin=nbt.contains("origin")?BlockPos.fromLong(nbt.getLong("origin")):pos;
        width=Math.clamp(nbt.getInt("width"),1,11);height=Math.clamp(nbt.getInt("height"),1,7);valid=nbt.getBoolean("valid");
        match=nbt.containsUuid("match")?nbt.getUuid("match"):null;
        pendingHost=nbt.containsUuid("pendingHost")?nbt.getUuid("pendingHost"):null;pendingTable=nbt.getLong("pendingTable");
        names=!nbt.contains("names")||nbt.getBoolean("names");animations=!nbt.contains("animations")||nbt.getBoolean("animations");damage=!nbt.contains("damage")||nbt.getBoolean("damage");
    }
    @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries){return createNbt(registries);}
    @Override public BlockEntityUpdateS2CPacket toUpdatePacket(){return BlockEntityUpdateS2CPacket.create(this);}
}
