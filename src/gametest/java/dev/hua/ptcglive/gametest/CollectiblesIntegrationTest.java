package dev.hua.ptcglive.gametest;

import dev.hua.ptcglive.*;
import dev.hua.ptcglive.collectibles.*;
import dev.hua.ptcglive.collection.*;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.economy.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.world.GameMode;
import net.minecraft.util.math.BlockPos;
import java.util.*;
import java.time.Clock;

public final class CollectiblesIntegrationTest implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void rarityPricingRefreshKeepsDepositedCards(TestContext c){
        var p=player(c);var e=place(c,p,CollectibleBlocks.EXCHANGE);e.refresh();
        var input=new ItemStack(CardItems.byDefinitionId("bwbsp_en_001"));
        CardCondition.apply(input,CardCondition.TREASURE);e.card(0,input);
        var nbt=e.createNbt(c.getWorld().getRegistryManager());
        nbt.putInt("pricingVersion",1);nbt.putLong("refreshAt",Long.MAX_VALUE);
        var oldOrders=nbt.getList("orders",NbtElement.COMPOUND_TYPE);
        for(int i=0;i<oldOrders.size();i++)oldOrders.getCompound(i).putInt("reward",100000);
        var restored=new CollectibleEntity(e.getPos(),e.getCachedState());restored.setWorld(c.getWorld());
        restored.read(nbt,c.getWorld().getRegistryManager());restored.refresh();
        c.assertTrue(ItemStack.areItemsAndComponentsEqual(input,restored.card(0))&&restored.card(0).getCount()==1,"migration consumed deposited card/grade");
        for(int row=0;row<3;row++){
            int value=0;for(String id:restored.order(row)){
                var card=CardItems.byDefinitionId(id).card();value+=RecyclingService.price(CardRarity.economy(id,card.rarityZh()));
            }
            c.assertTrue(restored.reward(row)>=value*8&&restored.reward(row)<=value*16,"old incorrect price survived migration");
        }
        c.assertTrue(RecyclingService.price(CardRarity.economy("bwbsp_en_001",""))==3,"ordinary promo overpriced");
        c.assertTrue(dev.hua.ptcglive.rental.RentalBlockEntity.rate("bwbsp_en_001","")==6,"rental promo disagrees");
        c.assertTrue(RecyclingService.price(CardRarity.economy("bw1_en_115",""))==1,"unresolved rarity overpriced");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void sameGradeStacksAndLegacyMigration(TestContext c){
        var p=player(c);var item=CardItems.cards().getFirst();
        for(var grade:CardCondition.values()){
            p.getInventory().clear();
            var a=new ItemStack(item);var b=new ItemStack(item);CardCondition.apply(a,grade);CardCondition.apply(b,grade);
            c.assertTrue(ItemStack.areItemsAndComponentsEqual(a,b)&&a.getMaxCount()==64,"same card/grade must stack");
            a.setCount(63);p.getInventory().setStack(0,a);c.assertTrue(p.getInventory().insertStack(b)&&b.isEmpty()&&a.getCount()==64,"64-card stacking");
            var one=a.split(1);c.assertTrue(one.getCount()==1&&a.getCount()==63&&CardCondition.of(one)==grade,"split changed grade/count");
            reject(()->CardCondition.apply(one,grade));
            for(var other:CardCondition.values())if(other!=grade){var different=new ItemStack(item);CardCondition.apply(different,other);c.assertTrue(!ItemStack.areItemsAndComponentsEqual(one,different),"different grades merged");}
            c.assertTrue(!ItemStack.areItemsAndComponentsEqual(one,new ItemStack(item)),"ungraded mixed with graded");
            var otherCard=new ItemStack(CardItems.cards().get(1));CardCondition.apply(otherCard,grade);
            c.assertTrue(!ItemStack.areItemsAndComponentsEqual(one,otherCard),"different card identities merged");
            var old=one.copy();var data=old.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA).copyNbt();
            data.putUuid("ptcglive_condition_id",UUID.randomUUID());old.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,net.minecraft.component.type.NbtComponent.of(data));
            var legacyNbt=new NbtCompound();var legacyCards=new NbtList();legacyCards.add(old.encodeAllowEmpty(c.getWorld().getRegistryManager()));legacyNbt.put("cards",legacyCards);
            var restored=new CollectibleEntity(BlockPos.ORIGIN,CollectibleBlocks.ALBUM.getDefaultState());restored.read(legacyNbt,c.getWorld().getRegistryManager());
            c.assertTrue(ItemStack.areItemsAndComponentsEqual(one,restored.card(0)),"legacy album card did not migrate");
            item.inventoryTick(old,c.getWorld(),p,1,false);
            c.assertTrue(ItemStack.areItemsAndComponentsEqual(one,old)&&!CardCondition.normalizeLegacyStack(old),"legacy migration not stable");
            var named=old.copy();named.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,net.minecraft.text.Text.literal("kept"));
            CardCondition.normalizeLegacyStack(named);c.assertTrue(!ItemStack.areItemsAndComponentsEqual(one,named),"unrelated components erased");
        }
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void legacyGradedBoxReturnsIntoFullInventoryStack(TestContext c){
        var p=player(c);var ledger=new CardBoxService(Clock.systemUTC());var box=ledger.create(p.getUuid(),"stack","standard-international-2026");
        var item=CardItems.cards().getFirst();var old=new ItemStack(item);CardCondition.apply(old,CardCondition.PRESERVED);
        var data=old.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA).copyNbt();data.putUuid("ptcglive_condition_id",UUID.randomUUID());
        old.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,net.minecraft.component.type.NbtComponent.of(data));
        p.getInventory().setStack(0,old);p.getInventory().setStack(10,CardBoxItem.referenceTo(box));
        InventoryCardTransfers.deposit(p,ledger,item.card().id(),1,box.id());
        var instance=ledger.contentsForEditing(p.getUuid(),box.id()).getFirst().instanceId();
        for(int i=0;i<36;i++)if(i!=10)p.getInventory().setStack(i,new ItemStack(Items.STONE,64));
        var existing=new ItemStack(item);CardCondition.apply(existing,CardCondition.PRESERVED);existing.setCount(63);p.getInventory().setStack(0,existing);
        InventoryCardTransfers.withdrawInstance(p,ledger,box.id(),instance);
        c.assertTrue(existing.getCount()==64&&CardCondition.of(existing)==CardCondition.PRESERVED&&ledger.contentsForEditing(p.getUuid(),box.id()).isEmpty(),"legacy box return required empty slot or duplicated card");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void gradedStackAlbumManualTransfer(TestContext c){
        var p=player(c);var e=place(c,p,CollectibleBlocks.ALBUM);p.setPos(e.getPos().getX()+.5,e.getPos().getY(),e.getPos().getZ()+.5);
        var handler=new CollectibleHandler(51,p.getInventory(),e);var cards=new ItemStack(CardItems.cards().getFirst());
        CardCondition.apply(cards,CardCondition.PERFECT);cards.setCount(12);handler.setCursorStack(cards);
        handler.onSlotClick(9,0,net.minecraft.screen.slot.SlotActionType.PICKUP,p);
        c.assertTrue(e.card(9).getCount()==1&&handler.getCursorStack().getCount()==11,"album pocket must take only one graded card");
        var remainder=handler.getCursorStack();handler.setCursorStack(ItemStack.EMPTY);
        handler.onSlotClick(9,0,net.minecraft.screen.slot.SlotActionType.PICKUP,p);
        var returned=handler.getCursorStack();p.getInventory().setStack(0,remainder);
        c.assertTrue(p.getInventory().insertStack(returned)&&returned.isEmpty()&&remainder.getCount()==12&&e.card(9).isEmpty(),"album return did not recombine graded cards");
        c.complete();
    }
    private static void reject(Runnable r){try{r.run();}catch(IllegalStateException|IllegalArgumentException ex){return;}throw new AssertionError("unsafe operation accepted");}
    private static net.minecraft.entity.player.PlayerEntity player(TestContext c){var p=c.createMockPlayer(GameMode.SURVIVAL);p.setUuid(UUID.randomUUID());return p;}
    private static CollectibleEntity place(TestContext c,net.minecraft.entity.player.PlayerEntity p,CollectibleBlock block){c.setBlockState(new BlockPos(2,1,2),block);var e=(CollectibleEntity)c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(2,1,2)));e.owner(p.getUuid());return e;}
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void recipesAndPhysicalStorageRoundTrip(TestContext c){
        for(String id:List.of("card_exchange","card_appraiser","card_hologram","collection_album","spectator_screen")){
            var recipe=c.getWorld().getRecipeManager().get(net.minecraft.util.Identifier.of("ptcglive",id)).orElseThrow().value();
            var result=recipe.getResult(c.getWorld().getRegistryManager());
            c.assertTrue(result.getCount()==(id.equals("spectator_screen")?4:1),"recipe output quantity");
            c.assertTrue(net.minecraft.registry.Registries.ITEM.getId(result.getItem()).getPath().equals(id),"recipe output namespace/id");
        }
        var p=player(c);var e=place(c,p,CollectibleBlocks.ALBUM);
        for(int i=0;i<9;i++){var stack=new ItemStack(CardItems.cards().get(i));CardCondition.apply(stack,CardCondition.values()[i%6]);e.card(i,stack);}
        var copy=new CollectibleEntity(e.getPos(),e.getCachedState());copy.setWorld(c.getWorld());copy.read(e.createNbt(c.getWorld().getRegistryManager()),c.getWorld().getRegistryManager());
        for(int i=0;i<9;i++)c.assertTrue(ItemStack.areItemsAndComponentsEqual(e.card(i),copy.card(i)),"album slot NBT altered");
        c.assertTrue(!e.toInitialChunkDataNbt(c.getWorld().getRegistryManager()).contains("cards"),"private album exposed to chunk viewers");
        e.dropContents();e.dropContents();for(int i=0;i<9;i++)c.assertTrue(e.card(i).isEmpty(),"break retained card");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void appraisalOnceAndWalletRollback(TestContext c){
        var p=player(c);var e=place(c,p,CollectibleBlocks.APPRAISER);var stack=new ItemStack(CardItems.cards().getFirst());e.card(0,stack);
        reject(()->e.act(p,0));c.assertTrue(!CardCondition.graded(e.card(0)),"unfunded appraisal mutated card");
        var wallet=PtcgServices.economy().wallet();wallet.credit(UUID.randomUUID(),p.getUuid(),200,PokeCoinLedger.Kind.ADMIN_MIGRATION,"test",0);
        reject(()->e.act(player(c),0));e.act(p,0);var q=CardCondition.of(e.card(0));
        c.assertTrue(q!=null&&wallet.balance(p.getUuid())==100,"appraisal fee/result");reject(()->e.act(p,0));
        c.assertTrue(CardCondition.of(e.card(0))==q&&wallet.balance(p.getUuid())==100,"repeat appraisal changed price/fee");
        var saved=e.createNbt(c.getWorld().getRegistryManager());var copy=new CollectibleEntity(e.getPos(),e.getCachedState());copy.read(saved,c.getWorld().getRegistryManager());
        c.assertTrue(ItemStack.areItemsAndComponentsEqual(copy.card(0),e.card(0)),"appraisal restart lost metadata");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void gradedBoxRoundTripAndCollectionProtection(TestContext c)throws Exception{
        var p=player(c);var owner=p.getUuid();var ledger=new CardBoxService(Clock.systemUTC());var box=ledger.create(owner,"test","standard-international-2026");
        var item=CardItems.cards().getFirst();var id=item.card().id();var graded=new ItemStack(item);CardCondition.apply(graded,CardCondition.TREASURE);
        graded.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,net.minecraft.text.Text.literal("实体品质"));
        p.getInventory().setStack(0,graded.copy());p.getInventory().setStack(1,new ItemStack(item,2));p.getInventory().setStack(10,CardBoxItem.referenceTo(box));
        c.assertTrue(InventoryCardTransfers.depositBatch(p,ledger,Set.of(id))==2,"batch did not skip graded card");
        c.assertTrue(CardCondition.graded(p.getInventory().getStack(0))&&ledger.unboxedForOwner(owner).size()==2,"graded card entered encyclopedia");
        reject(()->InventoryCardTransfers.deposit(p,ledger,id,1,null));InventoryCardTransfers.deposit(p,ledger,id,1,box.id());
        var stored=ledger.contentsForEditing(owner,box.id()).getFirst();reject(()->ledger.take(owner,box.id(),stored.instanceId()));
        var state=new dev.hua.ptcglive.persistence.PtcgPersistentState();state.bind(ledger,PtcgServices.economy(),PtcgServices.packOpeningAudit(),PtcgServices.starterDeck100(),PtcgServices.tables());
        var read=state.getClass().getDeclaredMethod("fromNbt",NbtCompound.class,net.minecraft.registry.RegistryWrapper.WrapperLookup.class);read.setAccessible(true);
        var saved=state.writeNbt(new NbtCompound(),c.getWorld().getRegistryManager());var reload=(dev.hua.ptcglive.persistence.PtcgPersistentState)read.invoke(null,saved,c.getWorld().getRegistryManager());
        var loaded=CardBoxService.restore(Clock.systemUTC(),reload.cardSnapshot());var match=UUID.randomUUID();loaded.snapshotForMatch(owner,box.id(),match);
        reject(()->InventoryCardTransfers.withdrawInstance(p,loaded,box.id(),stored.instanceId()));loaded.unlock(match);
        for(int i=0;i<36;i++)if(p.getInventory().getStack(i).isEmpty())p.getInventory().setStack(i,new ItemStack(Items.STONE,64));
        reject(()->InventoryCardTransfers.withdrawInstance(p,loaded,box.id(),stored.instanceId()));c.assertTrue(loaded.contentsForEditing(owner,box.id()).size()==1,"full bag lost card");
        p.getInventory().setStack(0,ItemStack.EMPTY);InventoryCardTransfers.withdrawInstance(p,loaded,box.id(),stored.instanceId());
        c.assertTrue(ItemStack.areItemsAndComponentsEqual(p.getInventory().getStack(0),graded),"box altered physical data");c.assertTrue(loaded.contentsForEditing(owner,box.id()).isEmpty()&&loaded.unboxedForOwner(owner).size()==2,"withdraw duplicated ledger copy");
        InventoryCardTransfers.deposit(p,loaded,id,1,box.id());
        var editor=new BoxEditorSession(loaded,new CardDatabase(List.of()),new CombatCatalog(Map.of(),Map.of()),Map.of(),Map.of(id,"test"),owner,box.id());
        editor.connectPhysicalReturn(instance->InventoryCardTransfers.withdrawInstance(p,loaded,box.id(),instance));
        var view=editor.view(owner);editor.submit(owner,view.revision(),"TAKE",id,0,"");
        c.assertTrue(ItemStack.areItemsAndComponentsEqual(p.getInventory().getStack(0),graded)&&loaded.unboxedForOwner(owner).size()==2,"editor TAKE sent graded card to encyclopedia");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void albumManualSlotsAndNoLedger(TestContext c){
        var p=player(c);var e=place(c,p,CollectibleBlocks.ALBUM);p.setPos(e.getPos().getX()+.5,e.getPos().getY(),e.getPos().getZ()+.5);
        var before=PtcgServices.cardBoxes().snapshot();var handler=new CollectibleHandler(1,p.getInventory(),e);
        var card=new ItemStack(CardItems.cards().getFirst());CardCondition.apply(card,CardCondition.PERFECT);p.getInventory().setStack(0,card.copy());
        c.assertTrue(handler.quickMove(p,48).isEmpty()&&e.card(0).isEmpty(),"album permits bulk insert");
        handler.setCursorStack(card.copy());handler.onSlotClick(0,0,net.minecraft.screen.slot.SlotActionType.PICKUP,p);
        c.assertTrue(e.card(0).getCount()==1&&handler.getCursorStack().isEmpty(),"manual insert");
        handler.onSlotClick(0,0,net.minecraft.screen.slot.SlotActionType.PICKUP,p);
        c.assertTrue(e.card(0).isEmpty()&&ItemStack.areItemsAndComponentsEqual(handler.getCursorStack(),card),"manual withdraw lost data");
        c.assertTrue(PtcgServices.cardBoxes().snapshot().equals(before),"album wrote collection ledger");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void exchangeFillSubmitAndNoReplay(TestContext c){
        var p=player(c);var e=place(c,p,CollectibleBlocks.EXCHANGE);e.refresh();var order=e.order(0);c.assertTrue(order.size()>=1&&order.size()<=4,"order count");
        var ledger=PtcgServices.cardBoxes();for(String id:order)ledger.mint(p.getUuid(),id);
        e.act(p,1);c.assertTrue(ledger.unboxedForOwner(p.getUuid()).isEmpty(),"autofill retained collection copies");
        for(int i=0;i<order.size();i++)c.assertTrue(e.card(i).getItem()==CardItems.byDefinitionId(order.get(i)),"wrong filled card");
        CardCondition.apply(e.card(0),CardCondition.TREASURE);long quote=e.quote();c.assertTrue(quote>=e.reward(0),"graded order not adjusted");
        var wallet=PtcgServices.economy().wallet();long balance=wallet.balance(p.getUuid());e.act(p,0);reject(()->e.act(p,0));
        c.assertTrue(wallet.balance(p.getUuid())==balance+quote&&e.card(0).isEmpty(),"replayed order or lost reward");
        var saved=e.createNbt(c.getWorld().getRegistryManager());var reloaded=new CollectibleEntity(e.getPos(),e.getCachedState());reloaded.setWorld(c.getWorld());reloaded.read(saved,c.getWorld().getRegistryManager());reject(()->reloaded.act(p,0));
        c.assertTrue(reloaded.completed(0),"completed order not persisted");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void gradedRecyclingQuoteMatchesConsumedCards(TestContext c){
        var p=player(c);var item=CardItems.cards().stream().filter(i->RecyclingService.rarity(i.card().rarityZh()).equals("C")).findFirst().orElseThrow();
        int keep=RecyclingService.keepCount(item.card().id());var graded=new ItemStack(item);CardCondition.apply(graded,CardCondition.TREASURE);
        p.getInventory().setStack(0,graded);p.getInventory().setStack(1,new ItemStack(item,keep));
        var ledger=new CardBoxService(Clock.systemUTC());var wallet=new PokeCoinLedger(Clock.systemUTC());var q=RecyclingService.quote(p,ledger,RecyclingService.Source.INVENTORY,Set.of("C"),null);
        c.assertTrue(q.count()==1&&q.coins()==2,"quality recycling quote");RecyclingService.confirm(p,ledger,wallet,q);c.assertTrue(wallet.balance(p.getUuid())==2&&p.getInventory().getStack(0).isEmpty(),"quality recycling payout");
        var ruined=new ItemStack(item);CardCondition.apply(ruined,CardCondition.RUINED);p.getInventory().setStack(0,ruined);
        reject(()->RecyclingService.quote(p,ledger,RecyclingService.Source.INVENTORY,Set.of("C"),null));
        c.assertTrue(!p.getInventory().getStack(0).isEmpty(),"sub-coin recycle destroyed card");c.complete();
    }
}
