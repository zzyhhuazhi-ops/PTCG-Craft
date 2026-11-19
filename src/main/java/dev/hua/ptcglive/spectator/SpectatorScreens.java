package dev.hua.ptcglive.spectator;

import dev.hua.ptcglive.CardItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

public final class SpectatorScreens {
    public static final SpectatorScreenBlock BLOCK=new SpectatorScreenBlock(AbstractBlock.Settings.create().strength(3).sounds(BlockSoundGroup.METAL)
        .pistonBehavior(PistonBehavior.BLOCK).luminance(s->2));
    public static BlockEntityType<SpectatorScreenEntity> ENTITY;
    public static void initialize(){
        var id=Identifier.of(CardItems.MOD_ID,"spectator_screen");
        Registry.register(Registries.BLOCK,id,BLOCK);Registry.register(Registries.ITEM,id,new BlockItem(BLOCK,new Item.Settings()));
        ENTITY=Registry.register(Registries.BLOCK_ENTITY_TYPE,id,FabricBlockEntityTypeBuilder.create(SpectatorScreenEntity::new,BLOCK).build());
        ServerTickEvents.END_SERVER_TICK.register(server->ScreenStructures.tick());
        ServerLifecycleEvents.SERVER_STOPPED.register(server->ScreenStructures.clear());
        ScreenNetworking.initialize();
    }
    private SpectatorScreens(){}
}
