package dev.hua.ptcglive.rental;

import dev.hua.ptcglive.*;
import dev.hua.ptcglive.economy.*;
import java.util.UUID;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;

/** No Inventory/SidedInventory interface: hoppers and transfer APIs cannot extract or insert cards. */
public final class RentalBlockEntity extends BlockEntity {
    private UUID machine=UUID.randomUUID(),owner;
    private ItemStack card=ItemStack.EMPTY;
    private ItemStack fuel=ItemStack.EMPTY, reservedFuel=ItemStack.EMPTY;
    private int half; // eighth-coin remainder; legacy half-coin saves are migrated
    public int fraction(){return half;}
    public int conditionPercent(){return dev.hua.ptcglive.collectibles.CardCondition.percent(card);}
    public int capacityMultiplier(){return ((RentalBlock)getCachedState().getBlock()).capacityMultiplier();}
    public int capacity(){return rate()*60*conditionPercent()*capacityMultiplier()/100;}
    private long earned,since;
    private boolean disposed;
    public RentalBlockEntity(BlockPos pos,BlockState state) { super(RentalBlocks.ENTITY,pos,state); }
    public UUID machineId(){return machine;}
    public UUID owner(){return owner;}
    public ItemStack card(){return card.copy();}
    public ItemStack fuel(){return fuel.copy();}
    public ItemStack reservedFuel(){return reservedFuel.copy();}
    public int half(){return half/4;}
    public int multiplier(){return multiplier(reservedFuel);}
    /** Only the owner-bound ScreenHandler adapter exposes slots; the BE remains inaccessible to hoppers. */
    ItemStack slotStack(int slot,boolean editing){
        if(slot==0)return editing?card:card.copy();
        if(editing)return fuel;
        var result=fuel.copy();
        if(result.isEmpty())return reservedFuel.copy();
        result.increment(reservedFuel.getCount());return result;
    }
    void setSlotStack(int slot,ItemStack stack){if(slot==0)card=stack;else fuel=stack;markDirty();}
    void beginSlotEdit(){settle();releaseReservation();}
    void endSlotEdit(ItemStack previousCard,int previousMultiplier){
        if(card.isEmpty()){
            card=ItemStack.EMPTY;
            if(!previousCard.isEmpty())since=now();
        }else if(previousCard.isEmpty()||!ItemStack.areItemsAndComponentsEqual(previousCard,card)){
            earned=Math.max(earned,wallet().rentalPaid(machine));since=now();reserveNext();
        }else if((previousMultiplier>2&&multiplier(fuel)==previousMultiplier)||since==now()){
            reserveNext();
        }
        sync();
    }
    public static int multiplier(ItemStack stack){return stack.isOf(Items.REDSTONE_BLOCK)?3:stack.isOf(Items.LAPIS_BLOCK)?4:2;}
    public void setOwner(UUID owner){this.owner=owner;sync();}
    public boolean mayAccess(PlayerEntity player){return owner!=null&&owner.equals(player.getUuid());}
    public long now(){return RentalClock.get(world.getServer()).now();}
    public long stored(){return Math.max(0,earned-wallet().rentalPaid(machine));}
    public int rate(){return card.getItem() instanceof CardItem item?rate(item.card().id(),item.card().rarityZh()):0;}
    public static int rate(String printed) {return rateCode(RecyclingService.rarity(printed));}
    public static int rate(String id,String printed){return rateCode(RecyclingService.rarity(id,printed));}
    private static int rateCode(String rarity){
        int rank=switch(rarity){case "C","PROMO","UNKNOWN"->0;case "U"->1;case "R"->2;case "RR"->3;case "RRR"->4;
            case "SR","AR"->5;case "SAR"->6;case "UR"->7;case "SPECIAL"->8;default->0;};
        return RentalProduction.rate(rank,9);
    }
    private PokeCoinLedger wallet(){return PtcgServices.economy().wallet();}
    private void requireOwner(PlayerEntity player) {
        if(world==null||world.isClient||disposed||!mayAccess(player)||player.isSpectator()||!player.isAlive())
            throw new IllegalStateException("只有租赁机所有者可以操作");
    }
    public void settle() {
        if(world==null||world.isClient||disposed||card.isEmpty())return;
        long time=now();boolean completed=time>=since&&time-since>=RentalProduction.MINUTE&&earned>=wallet().rentalPaid(machine)&&stored()<capacity();
        var next=RentalProduction.settleConditioned(earned,wallet().rentalPaid(machine),since,time,rate(),conditionPercent(),half,
            fuel.getCount(),multiplier(fuel),multiplier(),capacityMultiplier());
        if(completed){
            reservedFuel=next.cycleMultiplier()==2?ItemStack.EMPTY:fuel.copyWithCount(1);
            fuel.setCount(next.stock());
        }
        if(next.total()!=earned||next.since()!=since||next.eighth()!=half){earned=next.total();since=next.since();half=next.eighth();markDirty();}
    }
    private void reserveNext(){
        if(!card.isEmpty()&&stored()<capacity()&&reservedFuel.isEmpty()&&!fuel.isEmpty()){
            reservedFuel=fuel.copyWithCount(1);fuel.decrement(1);markDirty();
        }
    }
    private void releaseReservation(){
        if(reservedFuel.isEmpty())return;
        if(fuel.isEmpty())fuel=reservedFuel;else fuel.increment(1);
        reservedFuel=ItemStack.EMPTY;
    }
    public int addFuel(PlayerEntity player,boolean lapis){
        requireOwner(player);settle();var wanted=lapis?Items.LAPIS_BLOCK:Items.REDSTONE_BLOCK;
        if((!fuel.isEmpty()&&!fuel.isOf(wanted))||(!reservedFuel.isEmpty()&&!reservedFuel.isOf(wanted)))
            throw new IllegalStateException("请先取出原有燃料，再更换加速材料");
        int room=64-fuel.getCount()-reservedFuel.getCount(),added=0;
        for(int slot=0;slot<=40&&room>0;slot++){
            if(slot>=36&&slot<40)continue;var stack=player.getInventory().getStack(slot);
            if(!stack.isOf(wanted))continue;
            var template=fuel.isEmpty()?reservedFuel:fuel;
            if(!template.isEmpty()&&!ItemStack.areItemsAndComponentsEqual(template,stack))continue;
            int count=Math.min(room,stack.getCount());
            if(fuel.isEmpty())fuel=stack.copyWithCount(count);else fuel.increment(count);
            stack.decrement(count);room-=count;added+=count;
        }
        if(added==0)throw new IllegalStateException(room==0?"燃料仓已满（64 块）":"背包中没有对应矿物块");
        // Existing minute keeps its original multiplier; no retroactive boosting of earlier elapsed time.
        if(!card.isEmpty()&&since==now())reserveNext();
        player.getInventory().markDirty();player.currentScreenHandler.sendContentUpdates();sync();return added;
    }
    public void takeFuel(PlayerEntity player){
        requireOwner(player);settle();releaseReservation();var returned=fuel;fuel=ItemStack.EMPTY;sync();returnStack(player,returned);
    }
    private void returnStack(PlayerEntity player,ItemStack returned){
        player.getInventory().insertStack(returned);if(!returned.isEmpty())Block.dropStack(world,player.getBlockPos(),returned);
        player.getInventory().markDirty();player.currentScreenHandler.sendContentUpdates();
    }
    public void insert(PlayerEntity player,int inventorySlot) {
        requireOwner(player);
        if(!card.isEmpty())throw new IllegalStateException("每台租赁机只能放入一张卡牌");
        if(stored()>0)throw new IllegalStateException("请先提取上一张卡牌的收益，再放入新卡");
        if(inventorySlot<0||inventorySlot>40||(inventorySlot>=36&&inventorySlot<40))throw new IllegalArgumentException("背包槽位无效");
        var held=player.getInventory().getStack(inventorySlot);
        if(!(held.getItem() instanceof CardItem item)||held.isEmpty()||CardItems.byDefinitionId(item.card().id())!=held.getItem())
            throw new IllegalArgumentException("请选择背包中的有效 PTCG 卡牌");
        earned=Math.max(earned,wallet().rentalPaid(machine));
        card=held.copyWithCount(1);held.decrement(1);since=now();reserveNext();
        player.getInventory().markDirty();player.currentScreenHandler.sendContentUpdates();sync();
    }
    public long extract(PlayerEntity player) {
        requireOwner(player);settle();long amount=stored();boolean full=rate()>0&&amount>=capacity();
        if(amount==0)return 0;
        long result=wallet().claimRental(machine,owner,earned,world.getTime());
        if(full){since=now();reserveNext();}
        sync();return result;
    }
    public void take(PlayerEntity player) {
        requireOwner(player);settle();
        if(card.isEmpty())return;
        var returned=card;card=ItemStack.EMPTY;since=now();releaseReservation();sync();returnStack(player,returned);
    }
    public void dropContents() {
        if(world==null||world.isClient||disposed)return;
        settle();
        if(owner!=null&&(stored()>0||half>0)) {
            // Register the claim before clearing the block. Wallet and receipt are one atomic persistent snapshot.
            wallet().queueFinalRentalEighth(machine,owner,earned,half);
            try{wallet().claimRental(machine,owner,earned,world.getTime());}
            catch(RuntimeException failure){org.slf4j.LoggerFactory.getLogger("ptcglive").warn("租赁收益保留待领取：{}",machine,failure);}
        }
        releaseReservation();var returnedFuel=fuel;fuel=ItemStack.EMPTY;
        disposed=true;var returned=card;card=ItemStack.EMPTY;half=0;markDirty();
        if(!returned.isEmpty())Block.dropStack(world,pos,returned);
        if(!returnedFuel.isEmpty())Block.dropStack(world,pos,returnedFuel);
    }
    private void sync() {
        markDirty();
        if(world!=null) {
            var state=getCachedState();
            if(state.contains(RentalBlock.RUNNING)&&state.get(RentalBlock.RUNNING)!=!card.isEmpty())
                world.setBlockState(pos,state.with(RentalBlock.RUNNING,!card.isEmpty()),Block.NOTIFY_ALL);
            world.updateListeners(pos,getCachedState(),getCachedState(),Block.NOTIFY_LISTENERS);
        }
    }
    @Override protected void writeNbt(NbtCompound nbt,RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt,registries);nbt.putUuid("machine",machine);
        if(owner!=null)nbt.putUuid("owner",owner);
        nbt.putLong("earned",earned);nbt.putLong("since",since);nbt.putBoolean("disposed",disposed);
        nbt.putInt("eighthCoin",half);
        if(!fuel.isEmpty())nbt.put("fuel",fuel.encode(registries));else nbt.remove("fuel");
        if(!reservedFuel.isEmpty())nbt.put("reservedFuel",reservedFuel.encode(registries));else nbt.remove("reservedFuel");
        nbt.putBoolean("hasCard",!card.isEmpty());if(!card.isEmpty())nbt.put("card",card.encode(registries));else nbt.remove("card");
    }
    @Override protected void readNbt(NbtCompound nbt,RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt,registries);
        if(nbt.containsUuid("machine"))machine=nbt.getUuid("machine");
        owner=nbt.containsUuid("owner")?nbt.getUuid("owner"):null;
        earned=Math.max(0,nbt.getLong("earned"));since=Math.max(0,nbt.getLong("since"));disposed=nbt.getBoolean("disposed");
        card=nbt.contains("card")?ItemStack.fromNbt(registries,nbt.get("card")).orElse(ItemStack.EMPTY):ItemStack.EMPTY;
        if(!(card.getItem() instanceof CardItem)||disposed)card=ItemStack.EMPTY;else card.setCount(1);
        half=nbt.contains("eighthCoin")?Math.clamp(nbt.getInt("eighthCoin"),0,7):Math.clamp(nbt.getInt("halfCoin"),0,1)*4;
        fuel=readFuel(nbt,"fuel",registries,64);
        reservedFuel=readFuel(nbt,"reservedFuel",registries,1);
        if(!fuel.isEmpty()&&!reservedFuel.isEmpty()&&!ItemStack.areItemsAndComponentsEqual(fuel,reservedFuel))
            throw new IllegalStateException("租赁燃料数据类型冲突");
        if(fuel.getCount()+reservedFuel.getCount()>64)throw new IllegalStateException("租赁燃料超过容量");
        if(disposed){fuel=ItemStack.EMPTY;reservedFuel=ItemStack.EMPTY;half=0;}
    }
    private static ItemStack readFuel(NbtCompound nbt,String key,RegistryWrapper.WrapperLookup registries,int maximum){
        var stack=nbt.contains(key)?ItemStack.fromNbt(registries,nbt.get(key)).orElse(ItemStack.EMPTY):ItemStack.EMPTY;
        if(multiplier(stack)==2)return ItemStack.EMPTY;
        stack.setCount(Math.min(maximum,stack.getCount()));return stack;
    }
    @Override public BlockEntityUpdateS2CPacket toUpdatePacket(){return BlockEntityUpdateS2CPacket.create(this);}
    @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries){return createNbt(registries);}
}
