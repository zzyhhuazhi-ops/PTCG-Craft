package dev.hua.ptcglive.display;

import dev.hua.ptcglive.CardItems;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CardFrames {
    public static final Map<String, CardFrameBlock> BLOCKS = new LinkedHashMap<>();
    public static BlockEntityType<CardFrameBlockEntity> ENTITY;
    private CardFrames() { }
    public static void initialize() {
        for (String material : new String[]{"oak", "dark_wood", "black_metal", "white_metal", "gold", "acrylic"}) {
            for (int size : new int[]{1,3,9,4}) {
                String name = "card_frame_" + material + "_" + size;
                var block = new CardFrameBlock(size, AbstractBlock.Settings.create().strength(2.0f).nonOpaque()
                    .pistonBehavior(PistonBehavior.BLOCK).sounds(material.contains("wood") || material.equals("oak")
                        ? BlockSoundGroup.WOOD : BlockSoundGroup.METAL));
                var id = Identifier.of(CardItems.MOD_ID, name);
                Registry.register(Registries.BLOCK, id, block);
                Registry.register(Registries.ITEM, id, new BlockItem(block, new Item.Settings()));
                BLOCKS.put(name, block);
            }
        }
        ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, Identifier.of(CardItems.MOD_ID, "card_frame"),
            FabricBlockEntityTypeBuilder.create(CardFrameBlockEntity::new, BLOCKS.values().toArray(Block[]::new)).build());
        CardFrameBlock.registerInteractions();
    }
}
