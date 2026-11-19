package dev.hua.ptcglive;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.block.entity.BlockEntityType;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.util.Identifier;

/** Registered once on both dedicated server and integrated server; all text is supplied in Chinese lang data. */
public final class PtcgContent {
    public static final Block CARD_TABLE = new CardTableBlock(AbstractBlock.Settings.create().strength(2.5f).nonOpaque());
    public static final Block CARD_DISPLAY_CASE = new CardDisplayCaseBlock(AbstractBlock.Settings.create()
        .strength(3.5f).requiresTool().nonOpaque().pistonBehavior(net.minecraft.block.piston.PistonBehavior.BLOCK)
        .sounds(net.minecraft.sound.BlockSoundGroup.METAL));
    public static BlockEntityType<CardDisplayCaseBlockEntity> CARD_DISPLAY_CASE_ENTITY;
    public static final Block CARD_RECYCLING_STATION = new CardRecyclingStationBlock(AbstractBlock.Settings.create().strength(2.5f).requiresTool());
    public static final Item CARD_BOX = new CardBoxItem();
    public static final Item CARD_ENCYCLOPEDIA = new CardEncyclopediaItem();
    public static final Item STARTER_DECK_100_SELECTOR = new StarterDeck100Item();
    public static final Item PHONE = new PhoneItem();
    public static final Item FIELD_GUIDE = new Item(new Item.Settings().maxCount(1));
    private static boolean initialized;
    private PtcgContent() { }
    public static synchronized void initialize() {
        if (initialized) return;
        Registry.register(Registries.BLOCK, Identifier.of(CardItems.MOD_ID, "card_table"), CARD_TABLE);
        Registry.register(Registries.ITEM, Identifier.of(CardItems.MOD_ID, "card_table"), new BlockItem(CARD_TABLE, new Item.Settings()));
        Registry.register(Registries.BLOCK, Identifier.of(CardItems.MOD_ID, "card_display_case"), CARD_DISPLAY_CASE);
        Registry.register(Registries.ITEM, Identifier.of(CardItems.MOD_ID, "card_display_case"), new BlockItem(CARD_DISPLAY_CASE, new Item.Settings()));
        Registry.register(Registries.BLOCK, Identifier.of(CardItems.MOD_ID, "card_recycling_station"), CARD_RECYCLING_STATION);
        Registry.register(Registries.ITEM, Identifier.of(CardItems.MOD_ID, "card_recycling_station"), new BlockItem(CARD_RECYCLING_STATION, new Item.Settings()));
        CardDisplayCaseBlock.registerInteractions();
        CARD_DISPLAY_CASE_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(CardItems.MOD_ID, "card_display_case"),
            FabricBlockEntityTypeBuilder.create(CardDisplayCaseBlockEntity::new, CARD_DISPLAY_CASE).build());
        dev.hua.ptcglive.display.CardFrames.initialize();
        dev.hua.ptcglive.rental.RentalBlocks.initialize();
        dev.hua.ptcglive.spectator.SpectatorScreens.initialize();
        dev.hua.ptcglive.collectibles.CollectibleBlocks.initialize();
        Registry.register(Registries.ITEM, Identifier.of(CardItems.MOD_ID, "card_box"), CARD_BOX);
        Registry.register(Registries.ITEM, Identifier.of(CardItems.MOD_ID, "card_encyclopedia"), CARD_ENCYCLOPEDIA);
        Registry.register(Registries.ITEM, Identifier.of(CardItems.MOD_ID, "starter_deck_100_selector"), STARTER_DECK_100_SELECTOR);
        Registry.register(Registries.ITEM, Identifier.of(CardItems.MOD_ID, "phone"), PHONE);
        Registry.register(Registries.ITEM, Identifier.of(CardItems.MOD_ID, "field_guide"), FIELD_GUIDE);
        dev.hua.ptcglive.duel.DuelLetters.initialize();
        PackItems.initialize();
        dev.hua.ptcglive.shop.CardMerchants.initialize();
        Registry.register(Registries.ITEM_GROUP, Identifier.of(CardItems.MOD_ID, "equipment"), net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup.builder()
            .displayName(net.minecraft.text.Text.literal("卡牌用具"))
            .icon(() -> new net.minecraft.item.ItemStack(CARD_TABLE))
            .entries((context, entries) -> { entries.add(FIELD_GUIDE); entries.add(dev.hua.ptcglive.spectator.SpectatorScreens.BLOCK);
                    entries.add(dev.hua.ptcglive.duel.DuelLetters.ITEM);
                    entries.add(dev.hua.ptcglive.collectibles.CollectibleBlocks.EXCHANGE);
                    entries.add(dev.hua.ptcglive.collectibles.CollectibleBlocks.APPRAISER);
                    entries.add(dev.hua.ptcglive.collectibles.CollectibleBlocks.ALBUM_ITEM); entries.add(PHONE); entries.add(CARD_TABLE); entries.add(CARD_RECYCLING_STATION); entries.add(dev.hua.ptcglive.rental.RentalBlocks.BLOCK); entries.add(dev.hua.ptcglive.rental.RentalBlocks.V2); entries.add(dev.hua.ptcglive.rental.RentalBlocks.V3); entries.add(CARD_BOX); entries.add(CARD_ENCYCLOPEDIA); entries.add(STARTER_DECK_100_SELECTOR); }).build());
        Registry.register(Registries.ITEM_GROUP, Identifier.of(CardItems.MOD_ID, "card_displays"), net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup.builder()
            .displayName(net.minecraft.text.Text.translatable("itemGroup.ptcglive.card_displays"))
            .icon(() -> new net.minecraft.item.ItemStack(CARD_DISPLAY_CASE))
            .entries((context, entries) -> {
                entries.add(CARD_DISPLAY_CASE);
                dev.hua.ptcglive.display.CardFrames.BLOCKS.values().forEach(entries::add);
                entries.add(dev.hua.ptcglive.collectibles.CollectibleBlocks.HOLOGRAM);
            }).build());
        initialized = true;
    }
}
