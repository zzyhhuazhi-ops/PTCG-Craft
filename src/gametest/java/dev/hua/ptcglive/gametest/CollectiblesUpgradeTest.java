package dev.hua.ptcglive.gametest;

import dev.hua.ptcglive.*;
import dev.hua.ptcglive.collectibles.*;
import dev.hua.ptcglive.rental.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.*;
import net.minecraft.item.*;
import net.minecraft.util.math.*;
import net.minecraft.state.property.Properties;
import net.minecraft.world.GameMode;
import net.minecraft.entity.ItemEntity;
import net.minecraft.nbt.*;
import java.util.*;

public final class CollectiblesUpgradeTest implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void handheldAlbumRoundTripAndLockedBookSlot(TestContext c){
        var p=c.createMockPlayer(GameMode.SURVIVAL);p.setUuid(UUID.randomUUID());
        var book=new ItemStack(CollectibleBlocks.ALBUM_ITEM);p.getInventory().setStack(0,book);
        var storage=new AlbumBookItem.Storage(p,0,book);var h=new CollectibleHandler(81,p.getInventory(),storage);
        var card=new ItemStack(CardItems.cards().getFirst());CardCondition.apply(card,CardCondition.PERFECT);
        h.setCursorStack(card);h.onSlotClick(0,0,net.minecraft.screen.slot.SlotActionType.PICKUP,p);
        c.assertTrue(storage.get(0).getCount()==1&&h.getCursorStack().isEmpty(),"book insert did not transfer physical card");
        h.onSlotClick(45,0,net.minecraft.screen.slot.SlotActionType.PICKUP,p);
        c.assertTrue(p.getInventory().getStack(0)==book&&h.getCursorStack().isEmpty(),"open book can be taken");
        h.onSlotClick(1,0,net.minecraft.screen.slot.SlotActionType.SWAP,p);
        c.assertTrue(p.getInventory().getStack(0)==book&&storage.get(1).isEmpty(),"hotkey can nest open book");
        for(int i=0;i<49;i++)h.onButtonClick(p,21);
        h.setCursorStack(new ItemStack(CardItems.cards().get(1)));h.onSlotClick(17,0,net.minecraft.screen.slot.SlotActionType.PICKUP,p);
        var encoded=book.encodeAllowEmpty(c.getWorld().getRegistryManager());var restored=ItemStack.fromNbtOrEmpty(c.getWorld().getRegistryManager(),(NbtCompound)encoded);
        p.getInventory().setStack(0,restored);c.assertTrue(!h.canUse(p),"stale book session still usable");
        var reopened=new AlbumBookItem.Storage(p,0,restored);
        c.assertTrue(CardCondition.of(reopened.get(0))==CardCondition.PERFECT&&reopened.get(899).getCount()==1,"book page/quality lost after restart");
        c.assertTrue(!((net.minecraft.item.Item)CollectibleBlocks.ALBUM_ITEM instanceof net.minecraft.item.BlockItem),"album remains placeable");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void legacyAlbumConvertsWithoutCopying(TestContext c){
        var p=c.createMockPlayer(GameMode.SURVIVAL);p.setUuid(UUID.randomUUID());var pos=c.getAbsolutePos(new BlockPos(1,1,1));
        c.getWorld().setBlockState(pos,CollectibleBlocks.ALBUM.getDefaultState());var e=(CollectibleEntity)c.getWorld().getBlockEntity(pos);e.owner(p.getUuid());
        var original=new ItemStack(CardItems.cards().getFirst());CardCondition.apply(original,CardCondition.TREASURE);e.card(899,original);
        var book=e.takeAlbumBook(p);p.getInventory().setStack(0,book);var store=new AlbumBookItem.Storage(p,0,book);
        c.assertTrue(ItemStack.areItemsAndComponentsEqual(store.get(899),original)&&e.card(899).isEmpty(),"conversion did not preserve original components");
        boolean blocked=false;try{e.takeAlbumBook(p);}catch(IllegalStateException expected){blocked=true;}
        c.assertTrue(blocked,"legacy conversion can repeat");e.dropContents();c.getWorld().removeBlock(pos,false);c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void exchangeResizePreservesInputsAndObstacles(TestContext c){
        var world=c.getWorld();var root=c.getAbsolutePos(new BlockPos(3,1,3));var state=CollectibleBlocks.EXCHANGE.getDefaultState();world.setBlockState(root,state);
        for(int x=0;x<4;x++)for(int y=0;y<2;y++)for(int z=0;z<2;z++)if(x+y+z>0)world.setBlockState(CollectibleStructure.cell(root,Direction.NORTH,x,y,z),state.with(CollectibleStructure.X,x).with(CollectibleStructure.Y,y).with(CollectibleStructure.Z,z));
        var e=(CollectibleEntity)world.getBlockEntity(root);var card=new ItemStack(CardItems.cards().getFirst());e.card(0,card);
        var obstacle=CollectibleStructure.cell(root,Direction.NORTH,1,3,2);world.setBlockState(obstacle,net.minecraft.block.Blocks.DIAMOND_BLOCK.getDefaultState());
        e.ensureStructure();c.assertTrue(world.getBlockState(obstacle).isOf(net.minecraft.block.Blocks.DIAMOND_BLOCK)&&e.card(0)==card,"resize overwrote obstacle or input");
        world.removeBlock(obstacle,false);e.ensureStructure();
        for(int x=0;x<4;x++)for(int y=0;y<4;y++)for(int z=0;z<3;z++)c.assertTrue(CollectibleStructure.entity(world,CollectibleStructure.cell(root,Direction.NORTH,x,y,z))==e,"missing 4x3x4 member");
        c.assertTrue(e.card(0)==card&&!e.isRemoved(),"resize destroyed controller");world.breakBlock(root,true);c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void exchangeVersionOneWidensWithoutLosingInventory(TestContext c){
        var world=c.getWorld();var root=c.getAbsolutePos(new BlockPos(3,1,3));
        for(var face:Direction.Type.HORIZONTAL){
            var state=CollectibleBlocks.EXCHANGE.getDefaultState().with(Properties.HORIZONTAL_FACING,face);world.setBlockState(root,state);
            for(int x=0;x<2;x++)for(int y=0;y<4;y++)for(int z=0;z<3;z++)if(x+y+z>0)
                world.setBlockState(CollectibleStructure.cell(root,face,x,y,z),state.with(CollectibleStructure.X,x).with(CollectibleStructure.Y,y).with(CollectibleStructure.Z,z));
            var e=(CollectibleEntity)world.getBlockEntity(root);var card=new ItemStack(CardItems.cards().getFirst());e.card(0,card);
            var nbt=e.createNbt(world.getRegistryManager());nbt.putBoolean("structureBuilt",true);nbt.putInt("structureVersion",1);e.read(nbt,world.getRegistryManager());
            var obstacle=CollectibleStructure.cell(root,face,3,2,2);world.setBlockState(obstacle,net.minecraft.block.Blocks.GOLD_BLOCK.getDefaultState());
            e.ensureStructure();c.assertTrue(world.getBlockState(obstacle).isOf(net.minecraft.block.Blocks.GOLD_BLOCK),"widen overwrites building");
            world.removeBlock(obstacle,false);e.ensureStructure();
            for(int x=0;x<4;x++)for(int y=0;y<4;y++)for(int z=0;z<3;z++)c.assertTrue(CollectibleStructure.entity(world,CollectibleStructure.cell(root,face,x,y,z))==e,"version one migration missing cell");
            c.assertTrue(ItemStack.areItemsAndComponentsEqual(card,e.card(0))&&e.createNbt(world.getRegistryManager()).getInt("structureVersion")==2,"migration lost inventory/version");
            world.breakBlock(root,true);
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void rarityPricingNeverPromotesUnknowns(TestContext c){
        c.assertTrue(RentalBlockEntity.rate("未分类")==1&&RentalBlockEntity.rate("特典")==1,"unknown/promotional defaults to highest tier");
        var fossil=CardItems.byDefinitionId("zsv10-5_en_080").card();
        c.assertTrue(fossil.rarityZh().equals("C")&&RentalBlockEntity.rate(fossil.id(),fossil.rarityZh())==1,"Antique Cover Fossil is not common");
        var price=dev.hua.ptcglive.economy.RecyclingService.price(dev.hua.ptcglive.economy.RecyclingService.rarity(fossil.id(),fossil.rarityZh()));
        c.assertTrue(price==1,"recycling/exchange price mismatch");
        for(var item:CardItems.cards()){
            var card=item.card();String tier=CardRarity.economy(card.id(),card.rarityZh());int rate=RentalBlockEntity.rate(card.id(),card.rarityZh());
            c.assertTrue(rate>=1&&rate<=20,"unpriced card "+card.id());
            c.assertTrue(rate<20||tier.equals("SPECIAL"),"unreviewed top price "+card.id());
            c.assertTrue(!card.rarityZh().equals("未分类"),"unresolved catalog rarity "+card.id());
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void placementRejectsOccupiedCellsAndConsumesOne(TestContext c){
        var world=c.getWorld();var anchor=c.getAbsolutePos(new BlockPos(3,1,3));var p=c.createMockPlayer(GameMode.SURVIVAL);p.setUuid(UUID.randomUUID());p.setPosition(Vec3d.ofCenter(anchor.add(0,0,-3)));p.setYaw(0);
        world.setBlockState(anchor.down(),net.minecraft.block.Blocks.STONE.getDefaultState());
        var obstruction=anchor.add(1,1,1);world.setBlockState(obstruction,net.minecraft.block.Blocks.STONE.getDefaultState());
        p.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,new ItemStack(CollectibleBlocks.HOLOGRAM));
        var hit=new net.minecraft.util.hit.BlockHitResult(Vec3d.ofCenter(anchor.down()).add(0,.5,0),Direction.UP,anchor.down(),false);
        var use=new ItemUsageContext(p,net.minecraft.util.Hand.MAIN_HAND,hit);
        c.assertTrue(!CollectibleBlocks.HOLOGRAM.asItem().useOnBlock(use).isAccepted()&&p.getMainHandStack().getCount()==1,"occupied volume consumed machine");
        world.removeBlock(obstruction,false);
        c.assertTrue(CollectibleBlocks.HOLOGRAM.asItem().useOnBlock(use).isAccepted()&&p.getMainHandStack().isEmpty(),"placing 2x2x2 projector did not consume exactly one");
        var e=(CollectibleEntity)world.getBlockEntity(anchor);c.assertTrue(e!=null&&e.mayAccess(p),"owner missing");
        for(int x=0;x<2;x++)for(int y=0;y<2;y++)for(int z=0;z<2;z++)c.assertTrue(CollectibleStructure.entity(world,anchor.add(x,y,z))==e,"placement missing member");
        world.breakBlock(anchor,true);c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void legacyExpansionPreservesObstaclesAndCards(TestContext c){
        var world=c.getWorld();var anchor=c.getAbsolutePos(new BlockPos(3,1,3));world.setBlockState(anchor,CollectibleBlocks.HOLOGRAM.getDefaultState());
        var e=(CollectibleEntity)world.getBlockEntity(anchor);var card=new ItemStack(CardItems.cards().getFirst());e.card(0,card);
        var obstacle=anchor.add(1,0,1);world.setBlockState(obstacle,net.minecraft.block.Blocks.DIAMOND_BLOCK.getDefaultState());
        e.ensureStructure();c.assertTrue(world.getBlockState(obstacle).isOf(net.minecraft.block.Blocks.DIAMOND_BLOCK)&&e.card(0)==card,"legacy expansion overwrote world or card");
        world.removeBlock(obstacle,false);e.ensureStructure();c.assertTrue(CollectibleStructure.entity(world,obstacle)==e&&e.card(0)==card,"legacy expansion failed in free volume");
        world.breakBlock(anchor,true);c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void albumPagesAndLegacyStorage(TestContext c){
        var p=c.createMockPlayer(GameMode.SURVIVAL);p.setUuid(UUID.randomUUID());
        var pos=c.getAbsolutePos(new BlockPos(1,1,1));c.getWorld().setBlockState(pos,CollectibleBlocks.ALBUM.getDefaultState());
        var e=(CollectibleEntity)c.getWorld().getBlockEntity(pos);e.owner(p.getUuid());p.setPosition(Vec3d.ofCenter(pos));
        var first=new ItemStack(CardItems.cards().getFirst());CardCondition.apply(first,CardCondition.PERFECT);
        var last=new ItemStack(CardItems.cards().get(1));e.card(0,first);e.card(899,last);
        var h=new CollectibleHandler(31,p.getInventory(),e);
        c.assertTrue(h.getSlot(0).getStack()==first,"first page mapping");
        for(int i=0;i<99;i++)c.assertTrue(h.onButtonClick(p,21),"next page rejected");
        c.assertTrue(h.page()==98&&h.getSlot(17).getStack()==last,"last two-page spread mapping");
        h.onButtonClick(p,21);c.assertTrue(h.page()==98,"page overflow");
        var second=new CollectibleHandler(32,p.getInventory(),e);c.assertTrue(second.page()==0&&second.getSlot(0).getStack()==first,"sessions share page state");
        h.getSlot(17).setStack(ItemStack.EMPTY);c.assertTrue(e.card(899).isEmpty()&&e.card(0)==first,"wrong page removal");e.card(899,last);
        h.setCursorStack(first.copy());c.assertTrue(!h.onButtonClick(p,20)&&h.page()==98,"page turned while holding physical card");h.setCursorStack(ItemStack.EMPTY);
        second.setCursorStack(last.copy());second.onSlotClick(9,0,net.minecraft.screen.slot.SlotActionType.PICKUP,p);
        c.assertTrue(e.card(9).getCount()==1&&second.getCursorStack().isEmpty(),"right leaf cannot insert");
        second.onSlotClick(9,0,net.minecraft.screen.slot.SlotActionType.PICKUP,p);c.assertTrue(e.card(9).isEmpty(),"right leaf cannot extract");second.setCursorStack(ItemStack.EMPTY);
        var registries=c.getWorld().getRegistryManager();var restored=new CollectibleEntity(pos,e.getCachedState());restored.read(e.createNbt(registries),registries);
        c.assertTrue(restored.slots()==900&&ItemStack.areItemsAndComponentsEqual(first,restored.card(0))&&ItemStack.areItemsAndComponentsEqual(last,restored.card(899)),"900-slot restart loses data");
        var publicData=e.toInitialChunkDataNbt(registries);c.assertTrue(!publicData.contains("cards")&&!publicData.contains("physicalSlots"),"album private inventory leaked");
        var old=new NbtCompound();var list=new NbtList();list.add(first.encodeAllowEmpty(registries));old.put("cards",list);restored.read(old,registries);
        c.assertTrue(CardCondition.of(restored.card(0))==CardCondition.PERFECT&&restored.card(899).isEmpty(),"legacy nine-slot migration");
        e.dropContents();e.dropContents();c.assertTrue(e.card(0).isEmpty()&&e.card(899).isEmpty(),"break leaves live inventory");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void rentalTierRecipesAndCapacity(TestContext c){
        var p=c.createMockPlayer(GameMode.SURVIVAL);p.setUuid(UUID.randomUUID());
        var card=CardItems.cards().stream().filter(i->RentalBlockEntity.rate(i.card().rarityZh())==20).findFirst().orElseThrow();
        var variants=List.of(RentalBlocks.BLOCK,RentalBlocks.V2,RentalBlocks.V3);int[] factors={1,3,9};
        for(int i=0;i<3;i++){
            var pos=c.getAbsolutePos(new BlockPos(1+i,1,1));c.getWorld().setBlockState(pos,variants.get(i).getDefaultState());
            var e=(RentalBlockEntity)c.getWorld().getBlockEntity(pos);e.setOwner(p.getUuid());p.getInventory().setStack(0,new ItemStack(card));e.insert(p,0);
            c.assertTrue(e.capacity()==1200*factors[i]&&e.rate()==20,"tier changes production speed or wrong capacity");
            var copy=new RentalBlockEntity(pos,e.getCachedState());copy.read(e.createNbt(c.getWorld().getRegistryManager()),c.getWorld().getRegistryManager());
            c.assertTrue(copy.capacity()==e.capacity()&&copy.card().getCount()==1,"tier reload");
            if(i>0){
                var previous=variants.get(i-1);
                var recipe=c.getWorld().getRecipeManager().get(net.minecraft.util.Identifier.of("ptcglive","card_rental_v"+(i+1))).orElseThrow().value();
                c.assertTrue(recipe.getIngredients().size()==2&&recipe.getIngredients().stream().allMatch(v->v.test(new ItemStack(previous))) ,"upgrade must consume two previous-tier blocks");
                c.assertTrue(recipe.getResult(c.getWorld().getRegistryManager()).isOf(variants.get(i).asItem()),"upgrade output");
            }
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void multiblockFourDirectionsAndSingleReturn(TestContext c){
        var world=c.getWorld();var anchor=c.getAbsolutePos(new BlockPos(3,1,3));var p=c.createMockPlayer(GameMode.SURVIVAL);p.setUuid(UUID.randomUUID());
        for(var block:List.of(CollectibleBlocks.EXCHANGE,CollectibleBlocks.HOLOGRAM))for(var face:Direction.Type.HORIZONTAL){
            var state=block.getDefaultState().with(Properties.HORIZONTAL_FACING,face);world.setBlockState(anchor,state);
            var e=(CollectibleEntity)world.getBlockEntity(anchor);e.owner(p.getUuid());CollectibleStructure.fill(world,anchor,state);e.structureBuilt();
            int width=CollectibleStructure.width(block.kind);var card=new ItemStack(CardItems.cards().getFirst());CardCondition.apply(card,CardCondition.TREASURE);e.card(0,card);
            for(int x=0;x<width;x++)for(int y=0;y<CollectibleStructure.height(block.kind);y++)for(int z=0;z<CollectibleStructure.depth(block.kind);z++){
                var cell=CollectibleStructure.cell(anchor,face,x,y,z);c.assertTrue(CollectibleStructure.entity(world,cell)==e,"part does not resolve same storage");
                c.assertTrue(CollectibleStructure.root(cell,world.getBlockState(cell)).equals(anchor),"facing root transform");
                if(block==CollectibleBlocks.HOLOGRAM){
                    var outline=world.getBlockState(cell).getOutlineShape(world,cell);
                    c.assertTrue(y==1?outline.isEmpty():!outline.isEmpty()&&outline.getMax(Direction.Axis.Y)==.5,"hologram outline must be base only");
                }
            }
            var area=new Box(anchor).expand(8);world.getEntitiesByClass(ItemEntity.class,area,item->true).forEach(ItemEntity::discard);
            world.breakBlock(CollectibleStructure.cell(anchor,face,width-1,1,1),true);
            c.assertTrue(world.getBlockState(anchor).isAir(),"child break did not remove controller");
            long machineCount=0,cardCount=0;
            for(var item:world.getEntitiesByClass(ItemEntity.class,area,it->true)){if(item.getStack().isOf(block.asItem()))machineCount+=item.getStack().getCount();if(item.getStack().getItem() instanceof CardItem)cardCount+=item.getStack().getCount();}
            c.assertTrue(machineCount==1&&cardCount==1,"multiblock duplicated or lost drops: "+machineCount+" / "+cardCount);
            for(int x=0;x<width;x++)for(int y=0;y<CollectibleStructure.height(block.kind);y++)for(int z=0;z<CollectibleStructure.depth(block.kind);z++)c.assertTrue(!world.getBlockState(CollectibleStructure.cell(anchor,face,x,y,z)).isOf(block),"orphan part");
        }c.complete();
    }
}
