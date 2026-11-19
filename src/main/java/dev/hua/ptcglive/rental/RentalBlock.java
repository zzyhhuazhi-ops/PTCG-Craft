package dev.hua.ptcglive.rental;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

public final class RentalBlock extends BlockWithEntity {
    public static final MapCodec<RentalBlock> CODEC=createCodec(RentalBlock::new);
    public static final DirectionProperty FACING=Properties.HORIZONTAL_FACING;
    public static final BooleanProperty RUNNING=BooleanProperty.of("running");
    private static final net.minecraft.util.shape.VoxelShape SHAPE=net.minecraft.util.shape.VoxelShapes.union(
        Block.createCuboidShape(2,0,2,14,14.25,14),
        Block.createCuboidShape(3.1,14.25,3.1,12.9,15,12.9),
        Block.createCuboidShape(5.2,15,5.2,10.8,16,10.8));
    private final int capacityMultiplier;
    public RentalBlock(Settings settings){this(settings,1);}
    public RentalBlock(Settings settings,int capacityMultiplier){super(settings);this.capacityMultiplier=capacityMultiplier;setDefaultState(getStateManager().getDefaultState().with(FACING,Direction.NORTH).with(RUNNING,false));}
    public int capacityMultiplier(){return capacityMultiplier;}
    @Override protected MapCodec<? extends BlockWithEntity> getCodec(){return createCodec(settings->new RentalBlock(settings,capacityMultiplier));}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder){builder.add(FACING,RUNNING);}
    @Override public BlockEntity createBlockEntity(BlockPos pos,BlockState state){return new RentalBlockEntity(pos,state);}
    @Override protected BlockRenderType getRenderType(BlockState state){return BlockRenderType.MODEL;}
    @Override protected net.minecraft.util.shape.VoxelShape getOutlineShape(BlockState state,net.minecraft.world.BlockView world,BlockPos pos,ShapeContext context){return SHAPE;}
    @Override public BlockState getPlacementState(ItemPlacementContext context){return getDefaultState().with(FACING,context.getHorizontalPlayerFacing().getOpposite());}
    @Override public void onPlaced(World world,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){
        if(!world.isClient&&placer!=null&&world.getBlockEntity(pos) instanceof RentalBlockEntity entity)entity.setOwner(placer.getUuid());
    }
    @Override protected ActionResult onUse(BlockState state,World world,BlockPos pos,PlayerEntity player,BlockHitResult hit){
        if(player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer){
            if(RentalNetworking.accessible(serverPlayer,serverPlayer.getServerWorld(),pos)&&world.getBlockEntity(pos) instanceof RentalBlockEntity entity)
                serverPlayer.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
                    (syncId,inventory,user)->new RentalScreenHandler(syncId,inventory,entity),getName()));
            else player.sendMessage(net.minecraft.text.Text.literal("只有租赁机所有者可以操作"),true);
        }
        return ActionResult.SUCCESS;
    }
    @Override protected ItemActionResult onUseWithItem(ItemStack stack,BlockState state,World world,BlockPos pos,PlayerEntity player,Hand hand,BlockHitResult hit){
        onUse(state,world,pos,player,hit);return ItemActionResult.SUCCESS;
    }
    @Override protected void onStateReplaced(BlockState state,World world,BlockPos pos,BlockState next,boolean moved){
        if(!next.isOf(this)&&!world.isClient&&world.getBlockEntity(pos) instanceof RentalBlockEntity entity)entity.dropContents();
        super.onStateReplaced(state,world,pos,next,moved);
    }
}
