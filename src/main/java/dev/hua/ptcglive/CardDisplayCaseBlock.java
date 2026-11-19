package dev.hua.ptcglive;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

/** Two-block metal cabinet whose lower block entity owns the displayed physical card. */
public final class CardDisplayCaseBlock extends BlockWithEntity {
    public static final MapCodec<CardDisplayCaseBlock> CODEC = createCodec(CardDisplayCaseBlock::new);
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = Properties.DOUBLE_BLOCK_HALF;
    private static final VoxelShape LOWER_SHAPE = VoxelShapes.union(
        Block.createCuboidShape(2, 0, 2, 14, 2, 14),
        Block.createCuboidShape(3, 2, 3, 13, 14, 13),
        Block.createCuboidShape(2, 14, 2, 14, 16, 14));
    private static final VoxelShape UPPER_SHAPE = VoxelShapes.union(
        Block.createCuboidShape(4, 0, 5, 12, 2, 13),
        Block.createCuboidShape(7, 0, 11, 9, 11, 13),
        Block.createCuboidShape(3, 2, 8, 13, 3, 11));

    public CardDisplayCaseBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(HALF, DoubleBlockHalf.LOWER));
    }

    @Override protected MapCodec<? extends BlockWithEntity> getCodec() { return CODEC; }

    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return state.get(HALF) == DoubleBlockHalf.LOWER ? new CardDisplayCaseBlockEntity(pos, state) : null;
    }

    @Override protected BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }

    @Override public BlockState getPlacementState(ItemPlacementContext context) {
        BlockPos pos = context.getBlockPos();
        if (pos.getY() >= context.getWorld().getTopY() - 1
            || !context.getWorld().getBlockState(pos.up()).canReplace(context)) return null;
        return getDefaultState().with(FACING, context.getHorizontalPlayerFacing().getOpposite());
    }

    @Override public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        world.setBlockState(pos.up(), state.with(HALF, DoubleBlockHalf.UPPER), Block.NOTIFY_ALL);
    }

    @Override protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
                                                              WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        Direction pairedDirection = state.get(HALF) == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN;
        if (direction == pairedDirection && (!neighborState.isOf(this) || neighborState.get(HALF) == state.get(HALF)))
            return Blocks.AIR.getDefaultState();
        return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                                        PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (player.isSpectator()) return ItemActionResult.FAIL;
        if (player.isSneaking()) { toggleLock(world, state, pos, player); return ItemActionResult.SUCCESS; }
        if (!(stack.getItem() instanceof CardItem)) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!world.isClient) {
            CardDisplayCaseBlockEntity cabinet = cabinet(world, state, pos);
            if (cabinet == null) return ItemActionResult.FAIL;
            if (cabinet.locked()) { lockedMessage(player); return ItemActionResult.FAIL; }
            ItemStack previous = cabinet.replaceCard(stack, player.getUuid());
            stack.decrementUnlessCreative(1, player);
            if (!previous.isEmpty()) player.getInventory().offerOrDrop(previous);
        }
        return ItemActionResult.SUCCESS;
    }

    @Override protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (player.isSpectator()) return ActionResult.PASS;
        if (player.isSneaking()) { toggleLock(world, state, pos, player); return ActionResult.SUCCESS; }
        if (!player.getMainHandStack().isEmpty() || !player.getOffHandStack().isEmpty()) return ActionResult.PASS;
        CardDisplayCaseBlockEntity cabinet = cabinet(world, state, pos);
        if (cabinet == null || cabinet.displayedCard().isEmpty()) return ActionResult.PASS;
        if (cabinet.locked()) { if (!world.isClient) lockedMessage(player); return ActionResult.FAIL; }
        if (!world.isClient) player.getInventory().offerOrDrop(cabinet.takeCard());
        return ActionResult.SUCCESS;
    }

    private static void lockedMessage(PlayerEntity player) {
        player.sendMessage(net.minecraft.text.Text.literal("展示柜已上锁；请由放卡玩家 Shift＋右键解锁。"), true);
    }

    private static void toggleLock(World world, BlockState state, BlockPos pos, PlayerEntity player) {
        if (world.isClient || player.isSpectator()) return;
        var cabinet = cabinet(world, state, pos);
        if (cabinet == null || cabinet.displayedCard().isEmpty()) {
            player.sendMessage(net.minecraft.text.Text.literal("请先放入一张卡牌。"), true);
        } else if (cabinet.toggleLock(player.getUuid())) {
            player.sendMessage(net.minecraft.text.Text.literal(cabinet.locked() ? "展示柜已上锁。" : "展示柜已解锁。"), true);
        } else {
            player.sendMessage(net.minecraft.text.Text.literal(cabinet.cardOwner() == null
                ? "旧展示卡未记录放卡玩家；请取出后重新放入再上锁。" : "只有放入这张卡的玩家可以上锁或解锁。"), true);
        }
    }

    public static void registerInteractions() {
        // Sneaking with an item bypasses vanilla block interaction. Consume it here once, on the main hand.
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            var state = world.getBlockState(hit.getBlockPos());
            if (!state.isOf(PtcgContent.CARD_DISPLAY_CASE) || !player.isSneaking() || player.isSpectator()) return ActionResult.PASS;
            if (hand == Hand.MAIN_HAND) toggleLock(world, state, hit.getBlockPos(), player);
            return ActionResult.SUCCESS;
        });
        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (!state.isOf(PtcgContent.CARD_DISPLAY_CASE)) return true;
            var cabinet = cabinet(world, state, pos);
            if (cabinet == null || !cabinet.locked()) return true;
            lockedMessage(player);
            return false;
        });
    }

    @Override protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!newState.isOf(this) && !world.isClient) {
            CardDisplayCaseBlockEntity cabinet = cabinet(world, state, pos);
            if (cabinet != null) {
                ItemStack card = cabinet.takeCard();
                if (!card.isEmpty()) Block.dropStack(world, lowerPos(state, pos).up(), card);
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(HALF) == DoubleBlockHalf.LOWER ? LOWER_SHAPE : UPPER_SHAPE;
    }

    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    private static BlockPos lowerPos(BlockState state, BlockPos pos) {
        return state.get(HALF) == DoubleBlockHalf.UPPER ? pos.down() : pos;
    }

    private static CardDisplayCaseBlockEntity cabinet(World world, BlockState state, BlockPos pos) {
        BlockEntity blockEntity = world.getBlockEntity(lowerPos(state, pos));
        return blockEntity instanceof CardDisplayCaseBlockEntity cabinet ? cabinet : null;
    }
}
