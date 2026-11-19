package dev.hua.ptcglive.display;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.*;

/** A real 1x1 / 3x1 / 3x3 structure, each occupied block is independently raycastable. */
public final class CardFrameBlock extends BlockWithEntity {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final IntProperty COLUMN = IntProperty.of("column", 0, 2);
    public static final IntProperty ROW = IntProperty.of("row", 0, 2);
    public static final MapCodec<CardFrameBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        com.mojang.serialization.Codec.INT.fieldOf("slots").forGetter(CardFrameBlock::layout),
        createSettingsCodec()).apply(instance, CardFrameBlock::new));
    private final int slots;
    public CardFrameBlock(int slots, Settings settings) {
        super(settings);
        if (slots!=1 && slots!=3 && slots!=4 && slots!=9) throw new IllegalArgumentException("Invalid frame size");
        this.slots=slots;
        setDefaultState(getStateManager().getDefaultState().with(FACING,Direction.NORTH).with(COLUMN,0).with(ROW,0));
    }
    public int layout() { return slots; }
    public boolean enlarged() { return slots==4; }
    public int slots() { return enlarged()?1:slots; }
    public int columns() { return enlarged()?2:slots==1?1:3; }
    public int rows() { return enlarged()?2:slots==9?3:1; }
    public static Direction right(Direction facing) { return facing.rotateYCounterclockwise(); }
    public static BlockPos origin(BlockState state, BlockPos pos) {
        return pos.offset(right(state.get(FACING)), -state.get(COLUMN)).down(state.get(ROW));
    }
    public static BlockPos cell(BlockPos origin, Direction facing, int column, int row) {
        return origin.offset(right(facing), column).up(row);
    }
    public static boolean anchor(BlockState state) { return state.get(COLUMN)==0 && state.get(ROW)==0; }
    @Override protected MapCodec<? extends BlockWithEntity> getCodec() { return CODEC; }
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder) { builder.add(FACING,COLUMN,ROW); }
    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return anchor(state)?new CardFrameBlockEntity(pos,state):null; }
    @Override protected BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }
    @Override public BlockState getPlacementState(ItemPlacementContext context) {
        Direction face=context.getSide();
        if (face.getAxis().isVertical()) return null;
        World world=context.getWorld(); BlockPos origin=context.getBlockPos();
        for(int y=0;y<rows();y++) for(int x=0;x<columns();x++) {
            BlockPos part=cell(origin,face,x,y);
            if(!world.isInBuildLimit(part) || !world.getWorldBorder().contains(part)
                || !world.canPlayerModifyAt(context.getPlayer(),part)
                || !world.getBlockState(part).canReplace(context) || !supported(world,part,face)
                || !world.canPlace(getDefaultState().with(FACING,face),part,ShapeContext.absent())) return null;
        }
        return getDefaultState().with(FACING,face);
    }
    private static boolean supported(WorldView world,BlockPos pos,Direction face) {
        return world.getBlockState(pos.offset(face.getOpposite())).isSideSolidFullSquare(world,pos.offset(face.getOpposite()),face);
    }
    @Override public void onPlaced(World world,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack) {
        if(world.isClient) return;
        if(world.getBlockEntity(pos) instanceof CardFrameBlockEntity entity && placer!=null) entity.setOwner(placer.getUuid());
        for(int y=0;y<rows();y++) for(int x=0;x<columns();x++) if(x!=0||y!=0)
            world.setBlockState(cell(pos,state.get(FACING),x,y),state.with(COLUMN,x).with(ROW,y),Block.NOTIFY_ALL);
    }
    @Override protected BlockState getStateForNeighborUpdate(BlockState state,Direction direction,BlockState neighbor,
            WorldAccess world,BlockPos pos,BlockPos neighborPos) {
        if(direction==state.get(FACING).getOpposite() && !neighbor.isSideSolidFullSquare(world,neighborPos,state.get(FACING)))
            return Blocks.AIR.getDefaultState();
        return super.getStateForNeighborUpdate(state,direction,neighbor,world,pos,neighborPos);
    }
    public static CardFrameBlockEntity inventory(World world,BlockPos pos,BlockState state) {
        var root=origin(state,pos);
        // Resolve on the server even across chunk boundaries; never discard a card just because a neighbor chunk unloaded.
        var rootState=world.getBlockState(root);
        if(!rootState.isOf(state.getBlock()) || !anchor(rootState) || rootState.get(FACING)!=state.get(FACING)) return null;
        return world.getBlockEntity(root) instanceof CardFrameBlockEntity inventory ? inventory : null;
    }
    public boolean rim(BlockState state,BlockHitResult hit) {
        var v=hit.getPos().subtract(Vec3d.of(hit.getBlockPos()));
        double horizontal=switch(state.get(FACING)) {case NORTH->1-v.x;case SOUTH->v.x;case EAST->1-v.z;default->v.z;};
        double x=state.get(COLUMN)+horizontal,y=state.get(ROW)+v.y;
        return x<0.09 || x>columns()-0.09 || y<0.09 || y>rows()-0.09;
    }
    public ActionResult interact(World world,BlockPos pos,BlockState state,PlayerEntity player,Hand hand,BlockHitResult hit) {
        if(player.isSpectator() || hand!=Hand.MAIN_HAND || hit.getSide()!=state.get(FACING)) return ActionResult.PASS;
        if(world.isClient) return ActionResult.SUCCESS;
        if(!world.canPlayerModifyAt(player,pos)) return ActionResult.FAIL;
        var inv=inventory(world,pos,state); if(inv==null) return ActionResult.FAIL;
        var held=player.getStackInHand(hand);
        if(player.isSneaking() && rim(state,hit)) {
            boolean changed=inv.toggleLock(player);
            player.sendMessage(Text.translatable(changed?(inv.locked()?"message.ptcglive.frame.locked":"message.ptcglive.frame.unlocked")
                :"message.ptcglive.frame.owner_only"),true);
            return changed?ActionResult.SUCCESS:ActionResult.FAIL;
        }
        if(!inv.mayAccess(player)) { player.sendMessage(Text.translatable("message.ptcglive.frame.locked"),true); return ActionResult.FAIL; }
        int slot=enlarged()?0:state.get(ROW)*columns()+state.get(COLUMN);
        if(player.isSneaking() && held.isEmpty()) return inv.extract(slot,player)?ActionResult.SUCCESS:ActionResult.PASS;
        if(!player.isSneaking() && inv.insert(slot,held,player)) return ActionResult.SUCCESS;
        player.sendMessage(Text.translatable("message.ptcglive.frame.help"),true);
        return ActionResult.SUCCESS;
    }
    @Override protected ItemActionResult onUseWithItem(ItemStack stack,BlockState state,World world,BlockPos pos,PlayerEntity player,Hand hand,BlockHitResult hit) {
        return interact(world,pos,state,player,hand,hit).isAccepted()?ItemActionResult.SUCCESS:ItemActionResult.FAIL;
    }
    @Override protected ActionResult onUse(BlockState state,World world,BlockPos pos,PlayerEntity player,BlockHitResult hit) {
        return interact(world,pos,state,player,Hand.MAIN_HAND,hit);
    }
    @Override public BlockState onBreak(World world,BlockPos pos,BlockState state,PlayerEntity player) {
        var inv=inventory(world,pos,state); if(inv!=null) inv.suppressFrameDrop=player.isCreative();
        return super.onBreak(world,pos,state,player);
    }
    @Override protected void onStateReplaced(BlockState state,World world,BlockPos pos,BlockState replacement,boolean moved) {
        if(!world.isClient && !replacement.isOf(this)) {
            BlockPos root=origin(state,pos);
            if(anchor(state)) {
                if(world.getBlockEntity(pos) instanceof CardFrameBlockEntity inv) {
                    inv.dropContents();
                    if(!inv.suppressFrameDrop) Block.dropStack(world,pos,new ItemStack(asItem()));
                }
                for(int y=0;y<rows();y++) for(int x=0;x<columns();x++) if(x!=0||y!=0) {
                    var part=cell(root,state.get(FACING),x,y); var actual=world.getBlockState(part);
                    if(actual.isOf(this)&&actual.get(FACING)==state.get(FACING)&&actual.get(COLUMN)==x&&actual.get(ROW)==y)
                        world.removeBlock(part,false);
                }
            } else if(inventory(world,pos,state)!=null) world.removeBlock(root,false);
        }
        super.onStateReplaced(state,world,pos,replacement,moved);
    }
    @Override protected VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context) {
        return switch(state.get(FACING)) {
            case NORTH->Block.createCuboidShape(0,0,13,16,16,16);
            case SOUTH->Block.createCuboidShape(0,0,0,16,16,3);
            case EAST->Block.createCuboidShape(0,0,0,3,16,16);
            default->Block.createCuboidShape(13,0,0,16,16,16);
        };
    }
    public static void registerInteractions() {
        UseBlockCallback.EVENT.register((player,world,hand,hit)->{
            var state=world.getBlockState(hit.getBlockPos());
            if(state.getBlock() instanceof CardFrameBlock block && player.isSneaking())
                return block.interact(world,hit.getBlockPos(),state,player,hand,hit);
            return ActionResult.PASS;
        });
        PlayerBlockBreakEvents.BEFORE.register((world,player,pos,state,entity)->{
            if(state.getBlock() instanceof CardFrameBlock) {
                var inv=inventory(world,pos,state);
                if(inv!=null && !inv.mayAccess(player)) {
                    player.sendMessage(Text.translatable("message.ptcglive.frame.locked"),true); return false;
                }
            }
            // Removing the backing wall must not bypass a locked frame's mining permission.
            for(Direction direction:Direction.Type.HORIZONTAL) {
                var adjacent=pos.offset(direction); var adjacentState=world.getBlockState(adjacent);
                if(adjacentState.getBlock() instanceof CardFrameBlock && adjacentState.get(FACING)==direction) {
                    var inv=inventory(world,adjacent,adjacentState);
                    if(inv!=null && !inv.mayAccess(player)) {
                        player.sendMessage(Text.translatable("message.ptcglive.frame.locked"),true); return false;
                    }
                }
            }
            return true;
        });
    }
}
