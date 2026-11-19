package dev.hua.ptcglive.spectator;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.*;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import org.jetbrains.annotations.Nullable;

public final class SpectatorScreenBlock extends BlockWithEntity {
    public static final MapCodec<SpectatorScreenBlock> CODEC=createCodec(SpectatorScreenBlock::new);
    public static final DirectionProperty FACING=Properties.HORIZONTAL_FACING;
    public SpectatorScreenBlock(Settings settings){super(settings);setDefaultState(getStateManager().getDefaultState().with(FACING,Direction.NORTH));}
    @Override protected MapCodec<? extends BlockWithEntity> getCodec(){return CODEC;}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder){builder.add(FACING);}
    @Override protected BlockRenderType getRenderType(BlockState state){return BlockRenderType.MODEL;}
    @Override public BlockState getPlacementState(ItemPlacementContext context){
        var side=context.getSide();return getDefaultState().with(FACING,side.getAxis().isHorizontal()?side:context.getHorizontalPlayerFacing().getOpposite());
    }
    @Override protected BlockState rotate(BlockState state,BlockRotation rotation){return state.with(FACING,rotation.rotate(state.get(FACING)));}
    @Override protected BlockState mirror(BlockState state,BlockMirror mirror){return rotate(state,mirror.getRotation(state.get(FACING)));}
    @Override public BlockEntity createBlockEntity(BlockPos pos,BlockState state){return new SpectatorScreenEntity(pos,state);}
    @Override protected ActionResult onUse(BlockState state,World world,BlockPos pos,net.minecraft.entity.player.PlayerEntity player,net.minecraft.util.hit.BlockHitResult hit){
        if(player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer)ScreenNetworking.open(serverPlayer,hit);
        return ActionResult.SUCCESS;
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world,BlockState state,BlockEntityType<T> type){
        return world.isClient?null:validateTicker(type,SpectatorScreens.ENTITY,(w,p,s,be)->be.register());
    }
    @Override public void onPlaced(World world,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){if(!world.isClient)ScreenStructures.dirty(world,pos);}
    @Override protected void onStateReplaced(BlockState state,World world,BlockPos pos,BlockState replacement,boolean moved){
        super.onStateReplaced(state,world,pos,replacement,moved);
        if(!world.isClient&&!state.equals(replacement))ScreenStructures.dirty(world,pos);
    }
}
