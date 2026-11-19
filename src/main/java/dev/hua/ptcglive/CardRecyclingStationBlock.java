package dev.hua.ptcglive;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
public final class CardRecyclingStationBlock extends Block {
 public static final MapCodec<CardRecyclingStationBlock> CODEC=createCodec(CardRecyclingStationBlock::new);
 public CardRecyclingStationBlock(Settings settings){super(settings);}
 @Override protected MapCodec<? extends Block> getCodec(){return CODEC;}
 @Override protected ActionResult onUse(BlockState state,World world,BlockPos pos,PlayerEntity player,BlockHitResult hit){
  if(player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer)
   dev.hua.ptcglive.network.RecyclingNetworking.open(serverPlayer,pos);
  return ActionResult.SUCCESS;
 }
}
