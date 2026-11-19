package dev.hua.ptcglive.collectibles;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.util.shape.*;
import net.minecraft.text.Text;

public final class CollectibleBlock extends BlockWithEntity {
    public final CollectibleBlocks.Kind kind;
    public CollectibleBlock(CollectibleBlocks.Kind kind,Settings settings){super(settings);this.kind=kind;setDefaultState(getDefaultState().with(Properties.HORIZONTAL_FACING,Direction.NORTH).with(CollectibleStructure.X,0).with(CollectibleStructure.Y,0).with(CollectibleStructure.Z,0));}
    @Override protected MapCodec<? extends BlockWithEntity> getCodec(){return createCodec(settings->new CollectibleBlock(kind,settings));}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder){builder.add(Properties.HORIZONTAL_FACING,CollectibleStructure.X,CollectibleStructure.Y,CollectibleStructure.Z);}
    @Override public BlockState getPlacementState(ItemPlacementContext c){var s=getDefaultState().with(Properties.HORIZONTAL_FACING,c.getHorizontalPlayerFacing().getOpposite());return CollectibleStructure.fits(c,s)?s:null;}
    @Override protected BlockState rotate(BlockState state,BlockRotation rotation){return state.with(Properties.HORIZONTAL_FACING,rotation.rotate(state.get(Properties.HORIZONTAL_FACING)));}
    @Override protected BlockState mirror(BlockState state,BlockMirror mirror){return state.rotate(mirror.getRotation(state.get(Properties.HORIZONTAL_FACING)));}
    @Override public BlockEntity createBlockEntity(BlockPos pos,BlockState state){return new CollectibleEntity(pos,state);}
    @Override protected BlockRenderType getRenderType(BlockState state){return BlockRenderType.MODEL;}
    @Override protected VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){
        if(kind==CollectibleBlocks.Kind.HOLOGRAM)return state.get(CollectibleStructure.Y)==0?Block.createCuboidShape(0,0,0,16,8,16):VoxelShapes.empty();
        if(CollectibleStructure.width(kind)>1)return VoxelShapes.fullCube();
        return kind==CollectibleBlocks.Kind.ALBUM?Block.createCuboidShape(2,0,2,14,5,14):Block.createCuboidShape(1,0,1,15,16,15);
    }
    @Override protected VoxelShape getCollisionShape(BlockState s,BlockView world,BlockPos pos,ShapeContext context){
        if(kind==CollectibleBlocks.Kind.HOLOGRAM)return s.get(CollectibleStructure.Y)==0?Block.createCuboidShape(0,0,0,16,8,16):VoxelShapes.empty();
        if(kind==CollectibleBlocks.Kind.EXCHANGE){
            int y=s.get(CollectibleStructure.Y),z=s.get(CollectibleStructure.Z);
            if(y==3)return Block.createCuboidShape(0,0,0,16,16,16);
            if(y==0)return z==0?Block.createCuboidShape(0,0,0,16,16,16):Block.createCuboidShape(0,0,0,16,4,16);
            return VoxelShapes.empty();
        }return getOutlineShape(s,world,pos,context);
    }
    @Override public void onPlaced(World world,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){
        if(!world.isClient&&placer!=null&&world.getBlockEntity(pos) instanceof CollectibleEntity be){be.owner(placer.getUuid());CollectibleStructure.fill(world,pos,state);be.structureBuilt();}
    }
    @Override protected ActionResult onUse(BlockState state,World world,BlockPos pos,PlayerEntity player,BlockHitResult hit){
        var be=CollectibleStructure.entity(world,pos);
        if(!world.isClient&&be!=null){
            if(be.mayAccess(player)&&kind==CollectibleBlocks.Kind.ALBUM){
                var book=be.takeAlbumBook(player);world.removeBlock(pos,false);player.getInventory().offerOrDrop(book);
                player.sendMessage(Text.literal("已收起为手持卡册，右键打开；原有卡牌全部保留"),true);
            }else if(be.mayAccess(player))player.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory((id,inv,p)->new CollectibleHandler(id,inv,be),getName()));
            else player.sendMessage(Text.literal("只有放置者可以操作；其他玩家仍可观看展示"),true);
        }
        return ActionResult.SUCCESS;
    }
    @Override protected ItemActionResult onUseWithItem(ItemStack stack,BlockState state,World world,BlockPos pos,PlayerEntity player,Hand hand,BlockHitResult hit){onUse(state,world,pos,player,hit);return ItemActionResult.SUCCESS;}
    @Override protected void onStateReplaced(BlockState state,World world,BlockPos pos,BlockState next,boolean moved){
        if(!next.isOf(this)&&!world.isClient){
            if(CollectibleStructure.origin(state)&&world.getBlockEntity(pos) instanceof CollectibleEntity be)be.dropContents();
            CollectibleStructure.remove(world,pos,state);
        }
        super.onStateReplaced(state,world,pos,next,moved);
    }
    @Override public <T extends BlockEntity> net.minecraft.block.entity.BlockEntityTicker<T> getTicker(World world,BlockState state,net.minecraft.block.entity.BlockEntityType<T> type){
        if(world.isClient)return null;
        return validateTicker(type,CollectibleBlocks.ENTITY,(w,p,s,e)->{if(w.getTime()%40==0){
            if(CollectibleStructure.origin(s)){e.ensureStructure();return;}
            var root=CollectibleStructure.root(p,s);if(w.isChunkLoaded(root)&&!w.getBlockState(root).isOf(this))w.removeBlock(p,false);
        }});
    }
}
