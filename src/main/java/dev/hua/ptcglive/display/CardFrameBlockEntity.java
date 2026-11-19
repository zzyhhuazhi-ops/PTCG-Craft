package dev.hua.ptcglive.display;

import dev.hua.ptcglive.CardItem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import java.util.UUID;

/** Only the bottom-left cell owns inventory. No inventory API/hopper extraction bypass. */
public final class CardFrameBlockEntity extends BlockEntity {
    private final ItemStack[] cards = new ItemStack[9];
    private UUID owner;
    private boolean locked;
    boolean suppressFrameDrop;
    public CardFrameBlockEntity(BlockPos pos, BlockState state) {
        super(CardFrames.ENTITY, pos, state);
        java.util.Arrays.fill(cards, ItemStack.EMPTY);
    }
    public int size() { return ((CardFrameBlock)getCachedState().getBlock()).slots(); }
    public ItemStack card(int slot) { return valid(slot) ? cards[slot].copy() : ItemStack.EMPTY; }
    private boolean valid(int slot) { return slot >= 0 && slot < size(); }
    public boolean locked() { return locked; }
    public UUID owner() { return owner; }
    public void setOwner(UUID value) { if (owner == null) { owner = value; sync(); } }
    public boolean mayManage(PlayerEntity player) { return player.getUuid().equals(owner) || player.hasPermissionLevel(2); }
    public boolean mayAccess(PlayerEntity player) { return !locked || mayManage(player); }
    public boolean toggleLock(PlayerEntity player) {
        if (!mayManage(player)) return false;
        locked = !locked; sync(); return true;
    }
    public boolean insert(int slot, ItemStack stack, PlayerEntity player) {
        if (world == null || world.isClient || player.isSpectator() || !mayAccess(player)
            || !valid(slot) || !cards[slot].isEmpty() || stack.isEmpty() || !(stack.getItem() instanceof CardItem)) return false;
        // Even creative insertion transfers a physical stack; taking it cannot create a second one.
        cards[slot] = stack.split(1);
        sync(); return true;
    }
    public boolean extract(int slot, PlayerEntity player) {
        if (world == null || world.isClient || player.isSpectator() || !mayAccess(player)
            || !valid(slot) || cards[slot].isEmpty()) return false;
        ItemStack taken = cards[slot]; cards[slot] = ItemStack.EMPTY;
        sync();
        var inventory=player.getInventory();
        if(inventory.getEmptySlot()>=0 || inventory.getOccupiedSlotWithRoomForStack(taken)>=0) inventory.offerOrDrop(taken);
        else Block.dropStack(world,pos,taken);
        return true;
    }
    /** Clear before spawning entities: repeated replacement callbacks cannot duplicate contents. */
    public void dropContents() {
        if (world == null || world.isClient) return;
        for (int i = 0; i < cards.length; i++) {
            ItemStack old = cards[i]; cards[i] = ItemStack.EMPTY;
            if (!old.isEmpty()) Block.dropStack(world, pos, old);
        }
        markDirty();
    }
    private void sync() {
        markDirty();
        if (world != null) world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
    }
    @Override protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        for (int i=0; i<cards.length; i++) {
            if (!cards[i].isEmpty()) nbt.put("card"+i, cards[i].encode(registries));
            else nbt.remove("card"+i);
        }
        if (owner != null) nbt.putUuid("owner", owner);
        nbt.putBoolean("locked", locked);
        nbt.putInt("slots", size()); // Nonempty update even after the last card is removed.
    }
    @Override protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        java.util.Arrays.fill(cards, ItemStack.EMPTY);
        for (int i=0; i<size(); i++) if (nbt.contains("card"+i)) {
            ItemStack stack = ItemStack.fromNbt(registries, nbt.get("card"+i)).orElse(ItemStack.EMPTY);
            if (stack.getItem() instanceof CardItem && stack.getCount()==1) cards[i] = stack;
        }
        owner = nbt.containsUuid("owner") ? nbt.getUuid("owner") : null;
        locked = owner != null && nbt.getBoolean("locked");
    }
    @Override public BlockEntityUpdateS2CPacket toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }
    @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) { return createNbt(registries); }
}
