package dev.hua.ptcglive;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;

/** Retire only the exact legacy starter book; unopened world containers are left unloaded. */
public final class LegacyTutorialCleanup {
    private LegacyTutorialCleanup() { }
    public static boolean isLegacyBook(ItemStack stack) {
        if (!stack.isOf(Items.WRITTEN_BOOK)) return false;
        var book = stack.get(DataComponentTypes.WRITTEN_BOOK_CONTENT);
        return book != null && book.title().raw().equals("PTCG Craft 零基础入门")
            && book.author().equals("PTCG Craft") && book.pages().size() == 13
            && book.pages().getFirst().raw().getString().equals(
                "欢迎来到 PTCG Craft\n\n首次进入时，你会收到宝可梦手机、新手随机卡组和这本书。\n\n先手持手机右键，看看商城、图鉴和卡盒。");
    }
    public static boolean removeFrom(Inventory inventory) {
        boolean changed = false;
        for (int i = 0; i < inventory.size(); i++) if (isLegacyBook(inventory.getStack(i))) {
            inventory.setStack(i, ItemStack.EMPTY); changed = true;
        }
        if (changed) inventory.markDirty();
        return changed;
    }
    private static void clean(ServerPlayerEntity player) {
        boolean changed = removeFrom(player.getInventory()) | removeFrom(player.getEnderChestInventory());
        if (player.currentScreenHandler != player.playerScreenHandler)
            for (var slot : player.currentScreenHandler.slots) if (isLegacyBook(slot.getStack())) {
                slot.setStack(ItemStack.EMPTY); slot.markDirty(); changed = true;
            }
        if (changed) player.currentScreenHandler.sendContentUpdates();
    }
    public static void initialize() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> clean(handler.player));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 == 0) server.getPlayerManager().getPlayerList().forEach(LegacyTutorialCleanup::clean);
        });
    }
}
