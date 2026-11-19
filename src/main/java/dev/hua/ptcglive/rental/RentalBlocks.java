package dev.hua.ptcglive.rental;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

public final class RentalBlocks {
    public static final RentalBlock BLOCK=block(1), V2=block(3), V3=block(9);
    private static RentalBlock block(int capacity){return new RentalBlock(AbstractBlock.Settings.create().strength(5,1200).requiresTool().nonOpaque()
        .sounds(BlockSoundGroup.METAL).pistonBehavior(PistonBehavior.BLOCK).luminance(state->state.get(RentalBlock.RUNNING)?5:0),capacity);}
    public static BlockEntityType<RentalBlockEntity> ENTITY;
    public static final net.minecraft.screen.ScreenHandlerType<RentalScreenHandler> SCREEN_HANDLER=
        new net.minecraft.screen.ScreenHandlerType<>(RentalScreenHandler::new,net.minecraft.resource.featuretoggle.FeatureFlags.VANILLA_FEATURES);
    private RentalBlocks(){}
    public static void initialize(){
        var id=Identifier.of("ptcglive","card_rental");
        Registry.register(Registries.BLOCK,id,BLOCK);Registry.register(Registries.ITEM,id,new BlockItem(BLOCK,new Item.Settings()));
        for(var entry:java.util.Map.of("card_rental_v2",V2,"card_rental_v3",V3).entrySet()){
            var key=Identifier.of("ptcglive",entry.getKey());Registry.register(Registries.BLOCK,key,entry.getValue());
            Registry.register(Registries.ITEM,key,new BlockItem(entry.getValue(),new Item.Settings()));
        }
        Registry.register(Registries.SCREEN_HANDLER,id,SCREEN_HANDLER);
        ENTITY=Registry.register(Registries.BLOCK_ENTITY_TYPE,id,FabricBlockEntityTypeBuilder.create(RentalBlockEntity::new,BLOCK,V2,V3).build());
        PlayerBlockBreakEvents.BEFORE.register((world,player,pos,state,entity)->{
            if(!(entity instanceof RentalBlockEntity rental)||rental.mayAccess(player)||player.hasPermissionLevel(2))return true;
            player.sendMessage(net.minecraft.text.Text.literal("只有所有者可以拆除租赁机"),true);return false;
        });
        ServerTickEvents.END_SERVER_TICK.register(server->{
            RentalClock.get(server).tick(server.isPaused());
            if(server.getTicks()%600==0)dev.hua.ptcglive.PtcgServices.economy().wallet().retryRentals(server.getOverworld().getTime());
        });
    }
}
