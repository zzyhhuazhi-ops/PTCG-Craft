package dev.hua.ptcglive.gametest;

import dev.hua.ptcglive.CardItems;
import dev.hua.ptcglive.display.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import java.util.UUID;

/** Server-side Minecraft operations, not simulations of the inventory logic. */
public final class CardFrameIntegrationTest implements FabricGameTest {
    private static final BlockPos LOCAL=new BlockPos(3,1,3);
    private static PlayerEntity player(TestContext c,int id) {
        var player=c.createMockPlayer(GameMode.SURVIVAL); player.setUuid(new UUID(994,id));
        player.setPosition(Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(7,1,7)))); return player;
    }
    private static CardFrameBlockEntity place(TestContext c,CardFrameBlock block,Direction face,PlayerEntity player) {
        var world=c.getWorld();var origin=c.getAbsolutePos(LOCAL);
        for(int y=0;y<block.rows();y++) for(int x=0;x<block.columns();x++) {
            var part=CardFrameBlock.cell(origin,face,x,y);
            world.setBlockState(part,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
            world.setBlockState(part.offset(face.getOpposite()),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
        }
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(block));
        var wall=origin.offset(face.getOpposite());
        var hit=new BlockHitResult(Vec3d.ofCenter(wall).add(Vec3d.of(face.getVector()).multiply(.5)),face,wall,false);
        var result=block.asItem().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit));
        c.assertTrue(result.isAccepted()&&player.getMainHandStack().isEmpty(),"Frame item placement did not consume one item");
        for(int y=0;y<block.rows();y++) for(int x=0;x<block.columns();x++) {
            var part=CardFrameBlock.cell(origin,face,x,y);var state=world.getBlockState(part);
            c.assertTrue(state.isOf(block)&&state.get(CardFrameBlock.COLUMN)==x&&state.get(CardFrameBlock.ROW)==y
                &&CardFrameBlock.origin(state,part).equals(origin),"Wrong multiblock part or facing");
            c.assertTrue((world.getBlockEntity(part)!=null)==(x==0&&y==0),"More than one storage owner");
        }
        return (CardFrameBlockEntity)world.getBlockEntity(origin);
    }
    private static int dropped(TestContext c,Item item) {
        return c.getWorld().getEntitiesByClass(ItemEntity.class,new Box(c.getAbsolutePos(LOCAL)).expand(6),e->e.getStack().isOf(item))
            .stream().mapToInt(e->e.getStack().getCount()).sum();
    }
    private static void clearDrops(TestContext c) {
        c.getWorld().getEntitiesByClass(ItemEntity.class,new Box(c.getAbsolutePos(LOCAL)).expand(6),e->true).forEach(ItemEntity::discard);
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=200)
    public void allMaterialsSizesAndFourFacings(TestContext c) {
        var player=player(c,1);int count=0;
        for(var block:CardFrames.BLOCKS.values()) for(var face:Direction.Type.HORIZONTAL) {
            var inv=place(c,block,face,player);count++;
            for(int row=0;row<block.rows();row++) for(int col=0;col<block.columns();col++) {
                var part=CardFrameBlock.cell(inv.getPos(),face,col,row);var state=c.getWorld().getBlockState(part);
                int slot=block.enlarged()?0:row*block.columns()+col;
                var card=CardItems.cards().get(slot);player.setStackInHand(Hand.MAIN_HAND,new ItemStack(card,2));
                var hit=new BlockHitResult(Vec3d.ofCenter(part).add(Vec3d.of(face.getVector()).multiply(-.32)),face,part,false);
                state.onUseWithItem(player.getMainHandStack(),c.getWorld(),player,Hand.MAIN_HAND,hit);
                c.assertTrue(inv.card(slot).isOf(card)&&player.getMainHandStack().getCount()==1,"Hit position addressed wrong slot");
                if(block.enlarged()){
                    c.assertTrue(inv.size()==1&&!inv.insert(1,new ItemStack(card),player),"Large frame must store one card only");
                    var saved=inv.createNbt(c.getWorld().getRegistryManager());var copy=new CardFrameBlockEntity(inv.getPos(),inv.getCachedState());copy.read(saved,c.getWorld().getRegistryManager());
                    c.assertTrue(copy.size()==1&&copy.card(0).isOf(card),"Large frame restart changed card");
                    player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);player.setSneaking(true);
                    block.interact(c.getWorld(),part,state,player,Hand.MAIN_HAND,hit);player.setSneaking(false);
                    c.assertTrue(inv.card(0).isEmpty(),"Large frame quadrant cannot extract shared card");
                }
            }
            c.getWorld().removeBlock(c.getAbsolutePos(LOCAL),false);
            c.assertTrue(dropped(c,block.asItem())==1,"Frame did not drop exactly once");clearDrops(c);
        }
        c.assertTrue(count==96,"Missing a material, size or facing");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void independentSlotsDuplicateRequestsAndPersistence(TestContext c) {
        var owner=player(c,1);var block=CardFrames.BLOCKS.get("card_frame_gold_9");var inv=place(c,block,Direction.NORTH,owner);
        for(int slot=0;slot<9;slot++) {
            var stack=new ItemStack(CardItems.cards().get(slot),2);stack.set(DataComponentTypes.CUSTOM_NAME,Text.literal("Original "+slot));
            c.assertTrue(inv.insert(slot,stack,owner)&&stack.getCount()==1,"Insert failed to transfer one card");
            c.assertTrue(!inv.insert(slot,stack,owner)&&stack.getCount()==1,"Duplicate insertion consumed/replaced a card");
        }
        var registries=c.getWorld().getRegistryManager();var saved=inv.createNbt(registries);
        var restored=new CardFrameBlockEntity(inv.getPos(),inv.getCachedState());restored.read(saved,registries);
        for(int slot=0;slot<9;slot++) c.assertTrue(ItemStack.areEqual(inv.card(slot),restored.card(slot)),"NBT reload changed a card component");
        c.assertTrue(restored.owner().equals(owner.getUuid()),"Owner did not persist");
        c.assertTrue(inv.extract(4,owner)&&!inv.extract(4,owner),"Duplicate extraction succeeded");
        c.assertTrue(owner.getInventory().count(CardItems.cards().get(4))==1,"Extraction gave the wrong number");
        restored.read(inv.toUpdatePacket().getNbt(),registries);
        c.assertTrue(restored.card(4).isEmpty()&&!restored.card(3).isEmpty(),"Slot clear did not synchronize");
        c.getWorld().removeBlock(inv.getPos().up(2).offset(Direction.WEST,2),false);
        c.assertTrue(dropped(c,block.asItem())==1,"Breaking a non-anchor did not return one frame");
        for(int slot=0;slot<9;slot++) c.assertTrue(dropped(c,CardItems.cards().get(slot))==(slot==4?0:1),"Destruction lost or duplicated a card");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void lockPermissionsRimAndBackingWall(TestContext c) {
        var owner=player(c,1);var stranger=player(c,2);var block=CardFrames.BLOCKS.get("card_frame_oak_3");
        var inv=place(c,block,Direction.NORTH,owner);var origin=inv.getPos();
        var stack=new ItemStack(CardItems.cards().getFirst());inv.insert(0,stack,owner);
        owner.setSneaking(true);owner.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);
        var rim=new BlockHitResult(Vec3d.of(origin).add(.99,.5,.82),Direction.NORTH,origin,false);
        block.interact(c.getWorld(),origin,inv.getCachedState(),owner,Hand.MAIN_HAND,rim);
        c.assertTrue(inv.locked(),"Outer rim did not toggle lock");
        c.assertTrue(!inv.toggleLock(stranger)&&!inv.extract(0,stranger)&&!inv.insert(1,new ItemStack(CardItems.cards().getFirst()),stranger),"Non-owner bypassed lock");
        var before=PlayerBlockBreakEvents.BEFORE.invoker();
        for(int x=0;x<3;x++) {
            var pos=origin.offset(Direction.WEST,x);
            for(var target:new BlockPos[]{pos,pos.south()}) c.assertTrue(!before.beforeBlockBreak(c.getWorld(),stranger,target,c.getWorld().getBlockState(target),c.getWorld().getBlockEntity(target)),"Mining bypassed the lock");
        }
        var restored=new CardFrameBlockEntity(origin,inv.getCachedState());restored.read(inv.createNbt(c.getWorld().getRegistryManager()),c.getWorld().getRegistryManager());
        c.assertTrue(restored.locked()&&!restored.mayAccess(stranger),"Lock did not persist");
        var center=new BlockHitResult(Vec3d.of(origin).add(.5,.5,.82),Direction.NORTH,origin,false);
        block.interact(c.getWorld(),origin,inv.getCachedState(),owner,Hand.MAIN_HAND,center);
        c.assertTrue(inv.card(0).isEmpty()&&inv.locked(),"Slot extraction confused with rim lock");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void allRecipesProduceCorrectEmptyFrames(TestContext c) throws Exception {
        for(var entry:CardFrames.BLOCKS.entrySet()) {
            var id=net.minecraft.util.Identifier.of("ptcglive",entry.getKey());
            try(var stream=getClass().getResourceAsStream("/data/ptcglive/recipe/"+entry.getKey()+".json")) {
                var json=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                var ingredients=new java.util.ArrayList<ItemStack>();
                for(var line:json.getAsJsonArray("pattern")) for(char symbol:line.getAsString().toCharArray()) {
                    String name=json.getAsJsonObject("key").getAsJsonObject(String.valueOf(symbol)).get("item").getAsString();
                    ingredients.add(new ItemStack(net.minecraft.registry.Registries.ITEM.get(net.minecraft.util.Identifier.of(name))));
                }
                var pattern=json.getAsJsonArray("pattern");
                var input=net.minecraft.recipe.input.CraftingRecipeInput.create(pattern.get(0).getAsString().length(),pattern.size(),ingredients);
                var match=c.getWorld().getRecipeManager().getFirstMatch(net.minecraft.recipe.RecipeType.CRAFTING,input,c.getWorld()).orElseThrow();
                c.assertTrue(match.id().equals(id),"Ambiguous recipe: "+entry.getKey());
                var output=match.value().craft(input,c.getWorld().getRegistryManager());
                c.assertTrue(output.isOf(entry.getValue().asItem())&&output.getCount()==1,"Incorrect recipe output");
            }
        }
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void obstructedPlacementDoesNotConsumeOrOverwrite(TestContext c) {
        var owner=player(c,1);var block=CardFrames.BLOCKS.get("card_frame_oak_9");
        var origin=c.getAbsolutePos(LOCAL);
        for(int y=0;y<3;y++) for(int x=0;x<3;x++) c.getWorld().setBlockState(origin.west(x).up(y).south(),Blocks.STONE.getDefaultState());
        c.getWorld().setBlockState(origin.west(2).up(2),Blocks.DIAMOND_BLOCK.getDefaultState());
        owner.setStackInHand(Hand.MAIN_HAND,new ItemStack(block));
        var hit=new BlockHitResult(Vec3d.ofCenter(origin.south()).add(0,0,-.5),Direction.NORTH,origin.south(),false);
        var result=block.asItem().useOnBlock(new ItemUsageContext(owner,Hand.MAIN_HAND,hit));
        c.assertTrue(!result.isAccepted()&&owner.getMainHandStack().getCount()==1&&c.getWorld().getBlockState(origin).isAir()
            &&c.getWorld().getBlockState(origin.west(2).up(2)).isOf(Blocks.DIAMOND_BLOCK),"Obstructed frame partially placed or consumed item");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void adjacentFramesKeepSeparateOwnersAndCards(TestContext c) {
        var a=player(c,1);var b=player(c,2);var block=CardFrames.BLOCKS.get("card_frame_dark_wood_1");
        var first=place(c,block,Direction.NORTH,a);var other=first.getPos().west();var state=block.getDefaultState();
        c.getWorld().setBlockState(other.south(),Blocks.STONE.getDefaultState());
        c.getWorld().setBlockState(other,state);block.onPlaced(c.getWorld(),other,state,b,new ItemStack(block));
        var second=(CardFrameBlockEntity)c.getWorld().getBlockEntity(other);
        first.insert(0,new ItemStack(CardItems.cards().get(0)),a);second.insert(0,new ItemStack(CardItems.cards().get(1)),b);second.toggleLock(b);
        c.getWorld().removeBlock(first.getPos(),false);
        c.assertTrue(c.getWorld().getBlockEntity(other)==second&&second.locked()&&second.owner().equals(b.getUuid())&&second.card(0).isOf(CardItems.cards().get(1)),"Adjacent independent frame was altered");
        c.assertTrue(dropped(c,CardItems.cards().get(0))==1&&dropped(c,CardItems.cards().get(1))==0,"Adjacent cards were mixed");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void fullInventoryCreativeAndSupportRemoval(TestContext c) {
        var player=player(c,1);var block=CardFrames.BLOCKS.get("card_frame_acrylic_1");var inv=place(c,block,Direction.SOUTH,player);
        var item=CardItems.cards().getFirst();inv.insert(0,new ItemStack(item),player);
        for(int i=0;i<player.getInventory().size();i++) player.getInventory().setStack(i,new ItemStack(Items.STONE,64));
        c.assertTrue(inv.extract(0,player)&&dropped(c,item)==1,"Full inventory did not drop safely");clearDrops(c);
        inv.insert(0,new ItemStack(item),player);
        var creative=c.createMockPlayer(GameMode.CREATIVE);block.onBreak(c.getWorld(),inv.getPos(),inv.getCachedState(),creative);
        c.getWorld().removeBlock(inv.getPos(),false);
        c.assertTrue(dropped(c,item)==1&&dropped(c,block.asItem())==0,"Creative teardown duplicated a frame or lost a real card");clearDrops(c);
        inv=place(c,block,Direction.SOUTH,player);inv.insert(0,new ItemStack(item),player);
        c.getWorld().removeBlock(inv.getPos().north(),false);
        c.assertTrue(c.getWorld().getBlockState(inv.getPos()).isAir()&&dropped(c,item)==1&&dropped(c,block.asItem())==1,"Removing support lost contents");
        c.complete();
    }
}
