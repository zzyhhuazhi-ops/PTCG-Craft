package dev.hua.ptcglive.gametest;

import dev.hua.ptcglive.*;
import dev.hua.ptcglive.rental.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.test.*;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import java.util.UUID;

public final class RentalIntegrationTest implements FabricGameTest {
    private static final BlockPos LOCAL=new BlockPos(3,1,3);
    private static net.minecraft.entity.player.PlayerEntity player(TestContext c){var p=c.createMockPlayer(GameMode.SURVIVAL);p.setUuid(UUID.randomUUID());return p;}
    private static RentalBlockEntity place(TestContext c,net.minecraft.entity.player.PlayerEntity p){
        c.setBlockState(LOCAL,RentalBlocks.BLOCK);var e=(RentalBlockEntity)c.getWorld().getBlockEntity(c.getAbsolutePos(LOCAL));e.setOwner(p.getUuid());return e;
    }
    private static CardItem common(){return CardItems.cards().stream().filter(c->RentalBlockEntity.rate(c.card().rarityZh())==1).findFirst().orElseThrow();}
    private static void advance(TestContext c,long millis){RentalClock.get(c.getWorld().getServer()).advance(millis);}
    private static void reject(Runnable action){try{action.run();throw new AssertionError("illegal action accepted");}catch(RuntimeException expected){}}

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void cabinetShapeAndStatePreserveStorage(TestContext c){
        var p=player(c);var e=place(c,p);p.getInventory().setStack(0,new ItemStack(common()));e.insert(p,0);
        var id=e.machineId();
        for(var facing:net.minecraft.util.math.Direction.Type.HORIZONTAL){
            var state=RentalBlocks.BLOCK.getDefaultState().with(RentalBlock.FACING,facing).with(RentalBlock.RUNNING,true);
            c.getWorld().setBlockState(e.getPos(),state);
            var shape=state.getCollisionShape(c.getWorld(),e.getPos()).getBoundingBox();
            c.assertTrue(shape.minX>0&&shape.maxX<1&&shape.minZ>0&&shape.maxZ<1,"machine still has full cube collision");
            c.assertTrue(shape.minY==0&&shape.maxY==1,"top lamp collision missing");
            var current=(RentalBlockEntity)c.getWorld().getBlockEntity(e.getPos());
            c.assertTrue(current.machineId().equals(id)&&current.card().getCount()==1,"visual rotation reset card storage");
        }
        e.take(p);c.assertTrue(!c.getWorld().getBlockState(e.getPos()).get(RentalBlock.RUNNING),"empty machine remains lit");
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void oneCardOwnerAndOriginalData(TestContext c){
        var p=player(c);var other=player(c);var e=place(c,p);var stack=new ItemStack(common(),2);
        stack.set(DataComponentTypes.CUSTOM_NAME,Text.literal("原始卡牌数据"));p.getInventory().setStack(0,stack);
        e.insert(p,0);c.assertTrue(stack.getCount()==1&&e.card().getCount()==1,"not exactly one");
        reject(()->e.insert(p,0));reject(()->e.take(other));reject(()->e.extract(other));
        c.assertTrue(!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(c.getWorld(),other,e.getPos(),e.getCachedState(),e),"non-owner break");
        c.assertTrue(e.card().get(DataComponentTypes.CUSTOM_NAME).getString().equals("原始卡牌数据"),"card data changed");
        e.take(p);c.assertTrue(e.card().isEmpty()&&p.getInventory().count(common())==2,"return duplicated/lost");
        advance(c,120000);e.settle();c.assertTrue(e.stored()==0,"empty machine produced");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void cappedUnloadedProductionAndWallet(TestContext c){
        var p=player(c);var e=place(c,p);p.getInventory().setStack(0,new ItemStack(common()));e.insert(p,0);
        advance(c,59_999);e.settle();c.assertTrue(e.stored()==0,"early production");advance(c,1);e.settle();
        c.assertTrue(e.stored()==1,"minute production");
        var saved=e.createNbt(c.getWorld().getRegistryManager());
        // Recreate the BE after a long inactive interval, as chunk loading does; no per-BE ticker is required.
        advance(c,86_400_000);var reloaded=new RentalBlockEntity(e.getPos(),e.getCachedState());reloaded.setWorld(c.getWorld());
        reloaded.read(saved,c.getWorld().getRegistryManager());reloaded.settle();c.assertTrue(reloaded.stored()==60,"unloaded cap");
        c.getWorld().addBlockEntity(reloaded);
        long before=PtcgServices.economy().wallet().balance(p.getUuid());
        c.assertTrue(reloaded.extract(p)==60&&reloaded.extract(p)==0,"duplicate claim");
        c.assertTrue(PtcgServices.economy().wallet().balance(p.getUuid())==before+60,"wallet mismatch");
        reloaded.settle();c.assertTrue(reloaded.stored()==0,"banked full time");
        advance(c,60_000);reloaded.settle();c.assertTrue(reloaded.stored()==1,"did not resume");
        reloaded.take(p);advance(c,600_000);reloaded.settle();c.assertTrue(reloaded.stored()==1,"produced after take");
        reloaded.extract(p);c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void destructionCreditsOwnerExactlyOnce(TestContext c){
        var p=player(c);var e=place(c,p);p.getInventory().setStack(0,new ItemStack(common()));e.insert(p,0);advance(c,120_000);
        long before=PtcgServices.economy().wallet().balance(p.getUuid());var id=e.machineId();
        c.getWorld().setBlockState(e.getPos(),Blocks.AIR.getDefaultState());e.dropContents();
        c.assertTrue(e.card().isEmpty(),"card remains in broken BE");
        c.assertTrue(PtcgServices.economy().wallet().balance(p.getUuid())==before+2,"break payment incorrect");
        c.assertTrue(PtcgServices.economy().wallet().rentalPaid(id)==2,"receipt missing");
        var items=c.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new net.minecraft.util.math.Box(e.getPos()).expand(1),i->i.getStack().isOf(common()));
        c.assertTrue(items.stream().mapToInt(i->i.getStack().getCount()).sum()==1,"drop missing or duplicated");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void walletAndReceiptsPersistTogether(TestContext c){
        var owner=UUID.randomUUID();var id=UUID.randomUUID();var wallet=PtcgServices.economy().wallet();wallet.claimRental(id,owner,20,0);
        var state=dev.hua.ptcglive.persistence.PtcgPersistentState.get(c.getWorld().getServer().getOverworld());
        var nbt=state.writeNbt(new net.minecraft.nbt.NbtCompound(),c.getWorld().getRegistryManager());
        c.assertTrue(nbt.getList("rentalReceipts",10).stream().anyMatch(raw->((net.minecraft.nbt.NbtCompound)raw).getUuid("machine").equals(id)),"receipt absent from wallet file");
        var restored=dev.hua.ptcglive.persistence.PtcgPersistentState.TYPE.deserializer().apply(nbt,c.getWorld().getRegistryManager());
        var ledger=dev.hua.ptcglive.economy.PokeCoinLedger.restore(java.time.Clock.systemUTC(),restored.economySnapshot());
        c.assertTrue(ledger.balance(owner)==20&&ledger.claimRental(id,owner,20,1)==0,"restart permits duplication");
        var clock=RentalClock.get(c.getWorld().getServer());var savedClock=clock.writeNbt(new net.minecraft.nbt.NbtCompound(),c.getWorld().getRegistryManager());
        var restartedClock=RentalClock.TYPE.deserializer().apply(savedClock,c.getWorld().getRegistryManager());
        c.assertTrue(restartedClock.now()==clock.now(),"restart added offline time");
        for(var card:CardItems.cards()){
            int price=dev.hua.ptcglive.economy.RecyclingService.price(dev.hua.ptcglive.economy.RecyclingService.rarity(card.card().rarityZh()));
            int rank=price==20?8:price-1;
            c.assertTrue(RentalBlockEntity.rate(card.card().rarityZh())==RentalProduction.rate(rank,9),"real card rarity mapping incorrect");
        }
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void brokenMachineKeepsOverflowPending(TestContext c){
        var p=player(c);var e=place(c,p);p.getInventory().setStack(0,new ItemStack(common()));e.insert(p,0);advance(c,60_000);
        var wallet=PtcgServices.economy().wallet();wallet.credit(UUID.randomUUID(),p.getUuid(),Long.MAX_VALUE,dev.hua.ptcglive.economy.PokeCoinLedger.Kind.ADMIN_MIGRATION,"test",0);
        var id=e.machineId();c.getWorld().setBlockState(e.getPos(),Blocks.AIR.getDefaultState());
        c.assertTrue(wallet.rentalPaid(id)==0&&wallet.snapshot().rentals().stream().anyMatch(r->r.machine().equals(id)&&r.pending()==1),"break discarded overflow income");
        wallet.debit(UUID.randomUUID(),p.getUuid(),1,dev.hua.ptcglive.economy.PokeCoinLedger.Kind.ADMIN_MIGRATION,"room",1);wallet.retryRentals(2);
        c.assertTrue(wallet.rentalPaid(id)==1&&wallet.balance(p.getUuid())==Long.MAX_VALUE,"pending did not pay");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void fullInventoryReturnsOriginalCardAsDrop(TestContext c){
        var p=player(c);var e=place(c,p);var card=new ItemStack(common());card.set(DataComponentTypes.CUSTOM_NAME,Text.literal("保留数据"));
        p.getInventory().setStack(0,card);e.insert(p,0);
        p.setPosition(net.minecraft.util.math.Vec3d.ofCenter(e.getPos()));
        for(int i=0;i<36;i++)p.getInventory().setStack(i,new ItemStack(net.minecraft.item.Items.STONE,64));
        e.take(p);e.take(p);
        var drops=c.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new net.minecraft.util.math.Box(e.getPos()).expand(3),i->i.getStack().isOf(common()));
        c.assertTrue(drops.size()==1&&drops.getFirst().getStack().getCount()==1,"full inventory loses/duplicates card");
        c.assertTrue(drops.getFirst().getStack().get(DataComponentTypes.CUSTOM_NAME).getString().equals("保留数据"),"drop metadata lost");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void sessionRejectsReplayedAndDistantRequests(TestContext c)throws Exception{
        var p=c.createMockCreativeServerPlayerInWorld();p.changeGameMode(GameMode.SURVIVAL);
        try {
            var e=place(c,p);p.setPosition(net.minecraft.util.math.Vec3d.ofCenter(e.getPos()));p.getInventory().setStack(0,new ItemStack(common(),2));
            RentalNetworking.open(p,e.getPos());var f=RentalNetworking.class.getDeclaredField("SESSIONS");f.setAccessible(true);
            var session=((java.util.Map<?,?>)f.get(null)).get(p.getUuid());var idField=session.getClass().getDeclaredField("id");idField.setAccessible(true);
            String token=(String)idField.get(session);
            RentalNetworking.handle(p,new RentalIntent(token,0,"INSERT",0));RentalNetworking.handle(p,new RentalIntent(token,0,"INSERT",0));
            c.assertTrue(p.getInventory().getStack(0).getCount()==1&&e.card().getCount()==1,"replayed deposit");
            advance(c,60_000);long before=PtcgServices.economy().wallet().balance(p.getUuid());
            RentalNetworking.handle(p,new RentalIntent(token,1,"CLAIM",-1));RentalNetworking.handle(p,new RentalIntent(token,1,"CLAIM",-1));
            c.assertTrue(PtcgServices.economy().wallet().balance(p.getUuid())==before+1,"replayed claim");
            p.setPosition(p.getPos().add(20,0,0));RentalNetworking.handle(p,new RentalIntent(token,2,"TAKE",-1));
            c.assertTrue(!e.card().isEmpty(),"remote take accepted");
        }finally{p.networkHandler.disconnect(Text.literal("租赁测试完成"));}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void fuelBoostCapAndRefund(TestContext c){
        var p=player(c);var e=place(c,p);p.getInventory().setStack(0,new ItemStack(common()));
        p.getInventory().setStack(1,new ItemStack(net.minecraft.item.Items.REDSTONE_BLOCK,64));
        c.assertTrue(e.addFuel(p,false)==64&&e.fuel().getCount()==64&&e.reservedFuel().isEmpty(),"idle consumes fuel");
        e.insert(p,0);c.assertTrue(e.fuel().getCount()==63&&e.reservedFuel().getCount()==1,"fuel not reserved");
        advance(c,60_000);e.settle();c.assertTrue(e.stored()==1&&e.half()==1,"1.5x not exact");
        advance(c,60_000);e.settle();c.assertTrue(e.stored()==3&&e.half()==0,"half lost");
        advance(c,86_400_000);e.settle();
        c.assertTrue(e.stored()==60&&e.fuel().getCount()==24&&e.reservedFuel().isEmpty(),"redstone cap/consumption wrong");
        advance(c,600_000);e.settle();c.assertTrue(e.fuel().getCount()==24,"full consumes fuel");
        e.extract(p);c.assertTrue(e.fuel().getCount()==23&&e.reservedFuel().getCount()==1,"claim did not resume");
        e.take(p);c.assertTrue(e.fuel().getCount()==24&&e.reservedFuel().isEmpty(),"unfinished fuel not refunded");
        e.takeFuel(p);c.assertTrue(p.getInventory().count(net.minecraft.item.Items.REDSTONE_BLOCK)==24,"unused fuel lost");
        p.getInventory().setStack(1,new ItemStack(net.minecraft.item.Items.LAPIS_BLOCK,64));e.addFuel(p,true);e.insert(p,0);
        advance(c,1_800_000);e.settle();c.assertTrue(e.stored()==60&&e.fuel().getCount()==34&&e.reservedFuel().isEmpty(),"lapis does not fill in 30min");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void fuelChangeAndPersistence(TestContext c){
        var p=player(c);var e=place(c,p);p.getInventory().setStack(0,new ItemStack(common()));e.insert(p,0);advance(c,30_000);
        p.getInventory().setStack(1,new ItemStack(net.minecraft.item.Items.REDSTONE_BLOCK,2));e.addFuel(p,false);
        advance(c,30_000);e.settle();c.assertTrue(e.stored()==1&&e.multiplier()==3,"fuel retroactively accelerated old time");
        var saved=e.createNbt(c.getWorld().getRegistryManager());var restored=new RentalBlockEntity(e.getPos(),e.getCachedState());restored.setWorld(c.getWorld());
        restored.read(saved,c.getWorld().getRegistryManager());c.getWorld().addBlockEntity(restored);
        advance(c,60_000);restored.settle();c.assertTrue(restored.stored()==2&&restored.half()==1,"fuel restart mismatch");
        p.getInventory().setStack(2,new ItemStack(net.minecraft.item.Items.LAPIS_BLOCK,3));reject(()->restored.addFuel(p,true));
        restored.takeFuel(p);c.assertTrue(p.getInventory().count(net.minecraft.item.Items.REDSTONE_BLOCK)==1&&restored.multiplier()==2,"fuel retrieval failed");
        restored.addFuel(p,true);restored.take(p);var pos=restored.getPos();
        c.getWorld().setBlockState(pos,Blocks.AIR.getDefaultState());
        var drops=c.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new net.minecraft.util.math.Box(pos).expand(1),i->i.getStack().isOf(net.minecraft.item.Items.LAPIS_BLOCK));
        c.assertTrue(drops.stream().mapToInt(i->i.getStack().getCount()).sum()==3,"broken fuel lost");
        c.assertTrue(PtcgServices.economy().wallet().snapshot().rentals().stream().anyMatch(r->r.machine().equals(restored.machineId())&&r.half()==1&&r.closed()),"broken half coin not retained");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void vanillaSlotsPickupSplitAndShiftTransfer(TestContext c){
        var p=player(c);var e=place(c,p);p.setPosition(net.minecraft.util.math.Vec3d.ofCenter(e.getPos()));
        var menu=new RentalScreenHandler(77,p.getInventory(),e);
        p.getInventory().setStack(9,new ItemStack(common(),4));
        menu.onSlotClick(2,0,net.minecraft.screen.slot.SlotActionType.QUICK_MOVE,p);
        c.assertTrue(e.card().getCount()==1&&p.getInventory().getStack(9).getCount()==3,"shift deposits more than one card");
        menu.onSlotClick(2,0,net.minecraft.screen.slot.SlotActionType.QUICK_MOVE,p);
        c.assertTrue(p.getInventory().getStack(9).getCount()==3,"full card slot accepted more");
        p.getInventory().setStack(10,new ItemStack(net.minecraft.item.Items.REDSTONE_BLOCK,8));
        menu.onSlotClick(3,0,net.minecraft.screen.slot.SlotActionType.PICKUP,p);
        c.assertTrue(menu.getCursorStack().getCount()==8,"cursor pickup failed");
        menu.onSlotClick(1,1,net.minecraft.screen.slot.SlotActionType.PICKUP,p);
        c.assertTrue(menu.getSlot(1).getStack().getCount()==1&&menu.getCursorStack().getCount()==7,"right click split failed");
        menu.onSlotClick(1,0,net.minecraft.screen.slot.SlotActionType.PICKUP,p);
        c.assertTrue(menu.getSlot(1).getStack().getCount()==8&&menu.getCursorStack().isEmpty(),"fuel slot merge failed");
        advance(c,60000);menu.sendContentUpdates();
        c.assertTrue(menu.getSlot(1).getStack().getCount()==7,"reservation not displayed as fuel / consumption wrong");
        menu.onSlotClick(1,0,net.minecraft.screen.slot.SlotActionType.QUICK_MOVE,p);
        c.assertTrue(e.fuel().isEmpty()&&e.reservedFuel().isEmpty()&&p.getInventory().count(net.minecraft.item.Items.REDSTONE_BLOCK)==7,"shift fuel extraction duplicates or loses reserved block");
        menu.onSlotClick(0,0,net.minecraft.screen.slot.SlotActionType.QUICK_MOVE,p);
        c.assertTrue(e.card().isEmpty()&&p.getInventory().count(common())==4,"shift card extraction incorrect");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void vanillaSlotsRejectItemsAndDistantEdits(TestContext c){
        var p=player(c);var e=place(c,p);p.setPosition(net.minecraft.util.math.Vec3d.ofCenter(e.getPos()));
        var menu=new RentalScreenHandler(78,p.getInventory(),e);
        menu.setCursorStack(new ItemStack(net.minecraft.item.Items.DIAMOND,8));
        menu.onSlotClick(0,0,net.minecraft.screen.slot.SlotActionType.PICKUP,p);
        menu.onSlotClick(1,0,net.minecraft.screen.slot.SlotActionType.PICKUP,p);
        c.assertTrue(e.card().isEmpty()&&e.fuel().isEmpty()&&menu.getCursorStack().getCount()==8,"invalid slot item accepted");
        menu.setCursorStack(new ItemStack(common(),2));menu.onSlotClick(0,0,net.minecraft.screen.slot.SlotActionType.PICKUP,p);
        c.assertTrue(e.card().getCount()==1&&menu.getCursorStack().getCount()==1,"card limit ignored");
        menu.setCursorStack(ItemStack.EMPTY);p.setPosition(p.getPos().add(20,0,0));
        menu.onSlotClick(0,0,net.minecraft.screen.slot.SlotActionType.PICKUP,p);
        c.assertTrue(!menu.canUse(p)&&!e.card().isEmpty()&&menu.getCursorStack().isEmpty(),"distant cursor take allowed");
        var thief=player(c);thief.setPosition(net.minecraft.util.math.Vec3d.ofCenter(e.getPos()));
        var foreign=new RentalScreenHandler(79,thief.getInventory(),e);
        foreign.onSlotClick(0,0,net.minecraft.screen.slot.SlotActionType.QUICK_MOVE,thief);
        c.assertTrue(!foreign.canUse(thief)&&!e.card().isEmpty()&&thief.getInventory().count(common())==0,"foreign slot access allowed");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void vanillaSlotsPreserveBoostOnUnrelatedClickAndHotbarSwap(TestContext c){
        var p=player(c);var e=place(c,p);p.setPosition(net.minecraft.util.math.Vec3d.ofCenter(e.getPos()));
        p.getInventory().setStack(0,new ItemStack(common()));p.getInventory().setStack(1,new ItemStack(net.minecraft.item.Items.REDSTONE_BLOCK,4));
        e.addFuel(p,false);e.insert(p,0);advance(c,30000);
        var menu=new RentalScreenHandler(80,p.getInventory(),e);
        p.getInventory().setStack(10,new ItemStack(net.minecraft.item.Items.STONE));
        menu.onSlotClick(3,0,net.minecraft.screen.slot.SlotActionType.PICKUP,p);
        c.assertTrue(e.multiplier()==3&&menu.getSlot(1).getStack().getCount()==4,"unrelated inventory click resets boost");
        menu.onSlotClick(3,0,net.minecraft.screen.slot.SlotActionType.PICKUP,p);
        advance(c,30000);menu.sendContentUpdates();c.assertTrue(e.stored()==1&&e.half()==1,"partial cycle lost on inventory click");
        p.getInventory().setStack(2,new ItemStack(net.minecraft.item.Items.LAPIS_BLOCK,5));
        menu.onSlotClick(1,2,net.minecraft.screen.slot.SlotActionType.SWAP,p);
        c.assertTrue(menu.getSlot(1).getStack().isOf(net.minecraft.item.Items.LAPIS_BLOCK)&&menu.getSlot(1).getStack().getCount()==5
            &&p.getInventory().getStack(2).isOf(net.minecraft.item.Items.REDSTONE_BLOCK)&&p.getInventory().getStack(2).getCount()==3,"hotbar fuel swap lost blocks");
        long before=PtcgServices.economy().wallet().balance(p.getUuid());menu.onButtonClick(p,0);menu.onButtonClick(p,0);
        c.assertTrue(PtcgServices.economy().wallet().balance(p.getUuid())==before+1,"native claim button repeated payment");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void vanillaBlockOpensMenuAndDragCloseKeepsItems(TestContext c){
        var p=c.createMockCreativeServerPlayerInWorld();p.changeGameMode(GameMode.SURVIVAL);
        try{
            var e=place(c,p);p.setPosition(net.minecraft.util.math.Vec3d.ofCenter(e.getPos()));
            var hit=new net.minecraft.util.hit.BlockHitResult(p.getPos(),net.minecraft.util.math.Direction.NORTH,e.getPos(),false);
            e.getCachedState().onUse(c.getWorld(),p,hit);
            c.assertTrue(p.currentScreenHandler instanceof RentalScreenHandler,"block still opens custom button screen");
            var menu=(RentalScreenHandler)p.currentScreenHandler;
            p.getInventory().setStack(9,new ItemStack(net.minecraft.item.Items.REDSTONE_BLOCK,7));
            menu.onSlotClick(2,0,net.minecraft.screen.slot.SlotActionType.PICKUP,p);
            menu.onSlotClick(-999,0,net.minecraft.screen.slot.SlotActionType.QUICK_CRAFT,p);
            menu.onSlotClick(1,1,net.minecraft.screen.slot.SlotActionType.QUICK_CRAFT,p);
            menu.onSlotClick(-999,2,net.minecraft.screen.slot.SlotActionType.QUICK_CRAFT,p);
            c.assertTrue(menu.getSlot(1).getStack().getCount()==7&&menu.getCursorStack().isEmpty(),"drag fuel insertion failed");
            p.getInventory().setStack(10,new ItemStack(common()));menu.onSlotClick(3,0,net.minecraft.screen.slot.SlotActionType.QUICK_MOVE,p);
            menu.onSlotClick(1,1,net.minecraft.screen.slot.SlotActionType.PICKUP,p);
            c.assertTrue(menu.getCursorStack().getCount()==4&&menu.getSlot(1).getStack().getCount()==3,"right click fuel take failed");
            p.closeHandledScreen();
            c.assertTrue(p.getInventory().count(net.minecraft.item.Items.REDSTONE_BLOCK)==4&&e.fuel().getCount()+e.reservedFuel().getCount()==3,"close loses cursor fuel");
            var clientMenu=new RentalScreenHandler(81,p.getInventory());long large=9876543210L;
            for(int i=0;i<4;i++)clientMenu.setProperty(6+i,(short)(large>>>(i*16)));
            c.assertTrue(clientMenu.walletBalance()==large,"wallet property truncates long balance");
        }finally{p.networkHandler.disconnect(Text.literal("原版容器测试完成"));}c.complete();
    }
}
