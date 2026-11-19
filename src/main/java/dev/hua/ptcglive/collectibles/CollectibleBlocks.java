package dev.hua.ptcglive.collectibles;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;

public final class CollectibleBlocks {
    public enum Kind { EXCHANGE, APPRAISER, HOLOGRAM, ALBUM }
    public static final CollectibleBlock EXCHANGE=block(Kind.EXCHANGE), APPRAISER=block(Kind.APPRAISER),
        HOLOGRAM=block(Kind.HOLOGRAM), ALBUM=block(Kind.ALBUM);
    public static BlockEntityType<CollectibleEntity> ENTITY;
    public static final AlbumBookItem ALBUM_ITEM=new AlbumBookItem();
    public static final ScreenHandlerType<CollectibleHandler> HANDLER=new ScreenHandlerType<>(CollectibleHandler::new,FeatureFlags.VANILLA_FEATURES);
    public static final ScreenHandlerType<CollectibleHandler> ALBUM_HANDLER=new ScreenHandlerType<>((id,inventory)->new CollectibleHandler(id,inventory,true),FeatureFlags.VANILLA_FEATURES);
    static CollectibleBlock block(Kind kind){return new CollectibleBlock(kind,AbstractBlock.Settings.create().strength(3).nonOpaque().pistonBehavior(PistonBehavior.BLOCK));}
    public static void initialize(){
        register("card_exchange",EXCHANGE);register("card_appraiser",APPRAISER);register("card_hologram",HOLOGRAM);
        Registry.register(Registries.BLOCK,Identifier.of("ptcglive","collection_album"),ALBUM);
        Registry.register(Registries.ITEM,Identifier.of("ptcglive","collection_album"),ALBUM_ITEM);
        ENTITY=Registry.register(Registries.BLOCK_ENTITY_TYPE,Identifier.of("ptcglive","collectible_machine"),
            FabricBlockEntityTypeBuilder.create(CollectibleEntity::new,EXCHANGE,APPRAISER,HOLOGRAM,ALBUM).build());
        Registry.register(Registries.SCREEN_HANDLER,Identifier.of("ptcglive","collectible_machine"),HANDLER);
        Registry.register(Registries.SCREEN_HANDLER,Identifier.of("ptcglive","collection_album"),ALBUM_HANDLER);
        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register((world,player,pos,state,be)->{
            if(!(state.getBlock() instanceof CollectibleBlock))return true;
            var machine=CollectibleStructure.entity(world,pos);
            return machine!=null&&machine.mayAccess(player)||player.hasPermissionLevel(2);
        });
    }
    private static void register(String id,Block block){var key=Identifier.of("ptcglive",id);Registry.register(Registries.BLOCK,key,block);Registry.register(Registries.ITEM,key,new BlockItem(block,new Item.Settings()));}
}
