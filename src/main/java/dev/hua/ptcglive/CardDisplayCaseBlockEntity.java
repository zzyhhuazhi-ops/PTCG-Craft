package dev.hua.ptcglive;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;

/** Stores exactly one physical card and synchronises it to nearby clients for rendering. */
public final class CardDisplayCaseBlockEntity extends BlockEntity {
    private static final String CARD_KEY = "card";
    private ItemStack displayedCard = ItemStack.EMPTY;
    private java.util.UUID cardOwner;
    private boolean locked;

    public CardDisplayCaseBlockEntity(BlockPos pos, BlockState state) {
        super(PtcgContent.CARD_DISPLAY_CASE_ENTITY, pos, state);
    }

    public ItemStack displayedCard() { return displayedCard; }

    public boolean locked() { return locked; }
    public java.util.UUID cardOwner() { return cardOwner; }

    public boolean toggleLock(java.util.UUID player) {
        if (displayedCard.isEmpty() || !player.equals(cardOwner)) return false;
        locked = !locked;
        sync();
        return true;
    }

    public ItemStack replaceCard(ItemStack incoming, java.util.UUID depositor) {
        ItemStack previous = displayedCard;
        displayedCard = incoming.copyWithCount(1);
        cardOwner = depositor;
        locked = false;
        sync();
        return previous;
    }

    public ItemStack takeCard() {
        ItemStack previous = displayedCard;
        displayedCard = ItemStack.EMPTY;
        cardOwner = null;
        locked = false;
        if (!previous.isEmpty()) sync();
        return previous;
    }

    private void sync() {
        markDirty();
        if (world != null) world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
    }

    @Override protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        // The client skips empty block-entity update tags. Send an explicit
        // empty state too, so taking the last card clears the rendered stack.
        nbt.putBoolean("hasCard", !displayedCard.isEmpty());
        if (!displayedCard.isEmpty()) nbt.put(CARD_KEY, displayedCard.encode(registries));
        else nbt.remove(CARD_KEY);
        if (cardOwner != null) nbt.putUuid("cardOwner", cardOwner);
        else nbt.remove("cardOwner");
        nbt.putBoolean("locked", locked);
    }

    @Override protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        displayedCard = nbt.contains(CARD_KEY)
            ? ItemStack.fromNbt(registries, nbt.get(CARD_KEY)).orElse(ItemStack.EMPTY)
            : ItemStack.EMPTY;
        if (!(displayedCard.getItem() instanceof CardItem)) displayedCard = ItemStack.EMPTY;
        else displayedCard.setCount(1);
        cardOwner = !displayedCard.isEmpty() && nbt.containsUuid("cardOwner") ? nbt.getUuid("cardOwner") : null;
        locked = cardOwner != null && nbt.getBoolean("locked");
    }

    @Override public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }
}
