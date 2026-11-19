package dev.hua.ptcglive;

import dev.hua.ptcglive.table.CardTableKey;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Right-click entry point. The block stores no hidden cards; all lobby and match state remains server-owned. */
public final class CardTableBlock extends Block {
    public CardTableBlock(Settings settings) { super(settings); }
    private static final net.minecraft.util.shape.VoxelShape SHAPE = net.minecraft.util.shape.VoxelShapes.union(
        Block.createCuboidShape(0, 12, 0, 16, 16, 16), Block.createCuboidShape(1, 0, 1, 3, 12, 3),
        Block.createCuboidShape(13, 0, 1, 15, 12, 3), Block.createCuboidShape(1, 0, 13, 3, 12, 15),
        Block.createCuboidShape(13, 0, 13, 15, 12, 15));
    @Override protected net.minecraft.util.shape.VoxelShape getOutlineShape(BlockState state, net.minecraft.world.BlockView world,
        BlockPos pos, net.minecraft.block.ShapeContext context) { return SHAPE; }
    @Override protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        var key = new CardTableKey(world.getRegistryKey().getValue().toString(), pos.asLong());
        PtcgServices.tables().open(key, player.getUuid(), "standard-international-2026");
        return ActionResult.SUCCESS;
    }
    @Override protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        super.onStateReplaced(state, world, pos, newState, moved);
        if (!world.isClient && !newState.isOf(this)) {
            PtcgServices.tables().removePhysicalTable(new CardTableKey(world.getRegistryKey().getValue().toString(), pos.asLong()));
        }
    }
}
