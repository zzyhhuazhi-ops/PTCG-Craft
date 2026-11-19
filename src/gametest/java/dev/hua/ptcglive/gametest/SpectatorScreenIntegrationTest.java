package dev.hua.ptcglive.gametest;

import dev.hua.ptcglive.spectator.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import net.minecraft.block.Blocks;

public final class SpectatorScreenIntegrationTest implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void preparationConnectsNearbyScreensOnly(TestContext c){
        var world=c.getWorld();var table=c.getAbsolutePos(new BlockPos(240,1,240));world.getChunk(table);
        world.setBlockState(table,dev.hua.ptcglive.PtcgContent.CARD_TABLE.getDefaultState());
        var p=c.createMockCreativeServerPlayerInWorld();p.setUuid(java.util.UUID.randomUUID());p.setPosition(Vec3d.ofCenter(table));
        var key=new dev.hua.ptcglive.table.CardTableKey(world.getRegistryKey().getValue().toString(),table.asLong());
        dev.hua.ptcglive.PtcgServices.tables().open(key,p.getUuid(),"custom-open-all");
        var origins=java.util.List.of(table.add(3,0,0),table.add(33,0,0));
        for(var origin:origins)for(int y=0;y<2;y++)for(int x=0;x<3;x++){
            var pos=ScreenStructure.cell(origin,Direction.NORTH,x,y);world.getChunk(pos);
            world.setBlockState(pos,SpectatorScreens.BLOCK.getDefaultState());ScreenStructures.add((SpectatorScreenEntity)world.getBlockEntity(pos));
        }
        for(int i=0;i<50;i++)ScreenStructures.tick();
        c.assertTrue(ScreenNetworking.connectNearby(p,table)==1,"30-block scan included far screen or skipped near screen");
        var near=(SpectatorScreenEntity)world.getBlockEntity(origins.getFirst());var far=(SpectatorScreenEntity)world.getBlockEntity(origins.getLast());
        c.assertTrue(p.getUuid().equals(near.pendingHost)&&near.pendingTable==table.asLong()&&far.pendingHost==null,"wrong preparation binding");
        var saved=near.createNbt(world.getRegistryManager());var reload=new SpectatorScreenEntity(near.getPos(),near.getCachedState());reload.read(saved,world.getRegistryManager());
        c.assertTrue(p.getUuid().equals(reload.pendingHost)&&reload.pendingTable==table.asLong(),"pending binding persistence");
        dev.hua.ptcglive.PtcgServices.tables().endTable(key,p.getUuid());
        for(var origin:origins)for(int y=0;y<2;y++)for(int x=0;x<3;x++)world.removeBlock(ScreenStructure.cell(origin,Direction.NORTH,x,y),false);
        world.removeBlock(table,false);p.discard();c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void rectangularControllersInFourDirections(TestContext c){
        // Isolated region, so the 11x7 case never overlaps neighbouring GameTest templates.
        var origin=c.getAbsolutePos(new BlockPos(100,1,100));var world=c.getWorld();
        for(var face:Direction.Type.HORIZONTAL)for(int[] size:new int[][]{{3,2},{5,3},{7,4},{11,7}}){
            for(int y=0;y<size[1];y++)for(int x=0;x<size[0];x++){
                var p=ScreenStructure.cell(origin,face,x,y);world.getChunk(p);
                world.setBlockState(p,SpectatorScreens.BLOCK.getDefaultState().with(SpectatorScreenBlock.FACING,face));
                ScreenStructures.add((SpectatorScreenEntity)world.getBlockEntity(p));
            }
            for(int i=0;i<50;i++)ScreenStructures.tick();
            var root=(SpectatorScreenEntity)world.getBlockEntity(origin);
            c.assertTrue(root.controller()&&root.width==size[0]&&root.height==size[1],"wrong controller/dimensions "+face);
            int controllers=0;for(int y=0;y<size[1];y++)for(int x=0;x<size[0];x++){
                var e=(SpectatorScreenEntity)world.getBlockEntity(ScreenStructure.cell(origin,face,x,y));
                c.assertTrue(e.valid&&e.origin.equals(origin),"split structure");if(e.controller())controllers++;
            }
            c.assertTrue(controllers==1,"multiple full render controllers");
            root.names=false;root.animations=false;
            var saved=root.createNbt(world.getRegistryManager());var loaded=new SpectatorScreenEntity(origin,root.getCachedState());loaded.read(saved,world.getRegistryManager());
            c.assertTrue(loaded.valid&&loaded.width==size[0]&&!loaded.names&&!loaded.animations,"NBT settings lost");
            var hole=ScreenStructure.cell(origin,face,1,1);world.setBlockState(hole,Blocks.AIR.getDefaultState());
            ScreenStructures.dirty(world,hole);for(int i=0;i<50;i++)ScreenStructures.tick();c.assertTrue(!root.valid,"hole remains active");
            for(int y=0;y<size[1];y++)for(int x=0;x<size[0];x++)world.setBlockState(ScreenStructure.cell(origin,face,x,y),Blocks.AIR.getDefaultState());
            for(int i=0;i<50;i++)ScreenStructures.tick();
        }
        c.complete();
    }
}
