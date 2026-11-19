package dev.hua.ptcglive.rental;

import dev.hua.ptcglive.*;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.*;
import net.minecraft.item.*;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.*;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

/** Vanilla cursor/slot transactions, backed by the existing one-card machine and fuel reservation. */
public final class RentalScreenHandler extends ScreenHandler {
    public static final int PROPERTY_COUNT=12;
    private final RentalBlockEntity machine;
    private final PlayerEntity owner;
    private final PropertyDelegate properties;
    private boolean editing;

    public RentalScreenHandler(int syncId,PlayerInventory player){this(syncId,player,null);}
    public RentalScreenHandler(int syncId,PlayerInventory player,RentalBlockEntity machine){
        super(RentalBlocks.SCREEN_HANDLER,syncId);this.machine=machine;this.owner=player.player;
        Inventory inventory=machine==null?new SimpleInventory(2):new MachineInventory();
        properties=machine==null?new ArrayPropertyDelegate(PROPERTY_COUNT):new PropertyDelegate(){
            @Override public int size(){return PROPERTY_COUNT;}
            @Override public void set(int index,int value){}
            @Override public int get(int index){return switch(index){
                case 0->(int)machine.stored();case 1->machine.half();case 2->machine.rate();case 3->machine.multiplier();
                case 4->machine.capacity();case 5->machine.card().isEmpty()?0:machine.stored()>=machine.capacity()?2:1;
                case 10->machine.conditionPercent();case 11->machine.fraction();
                default->(int)((PtcgServices.economy().wallet().balance(owner.getUuid())>>>((index-6)*16))&0xFFFF);
            };}
        };
        addSlot(new Slot(inventory,0,26,32){
            @Override public int getMaxItemCount(){return 1;}
            @Override public boolean canInsert(ItemStack stack){return stack.getItem() instanceof CardItem c
                &&CardItems.byDefinitionId(c.card().id())==stack.getItem()
                &&(RentalScreenHandler.this.machine==null||(!hasStack()&&RentalScreenHandler.this.machine.stored()==0));}
        });
        addSlot(new Slot(inventory,1,26,76){
            @Override public boolean canInsert(ItemStack stack){return validFuel(stack);}
        });
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(player,col+row*9+9,34+col*18,126+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(player,col,34+col*18,184));
        addProperties(properties);
    }
    public static boolean validFuel(ItemStack stack){return stack.isOf(Items.REDSTONE_BLOCK)||stack.isOf(Items.LAPIS_BLOCK);}
    public int conditionPercent(){return properties.get(10);}
    public int fraction(){return properties.get(11);}
    public int coins(){return properties.get(0);}
    public int half(){return properties.get(1);}
    public int rate(){return properties.get(2);}
    public int multiplier(){return properties.get(3);}
    public int capacity(){return properties.get(4);}
    public int status(){return properties.get(5);}
    public long walletBalance(){long balance=0;for(int i=0;i<4;i++)balance|=(long)(properties.get(6+i)&0xFFFF)<<(i*16);return balance;}
    @Override public boolean canUse(PlayerEntity player){
        if(machine==null)return true;
        var world=machine.getWorld();return !machine.isRemoved()&&world==player.getWorld()&&player.isAlive()&&!player.isSpectator()
            &&machine.mayAccess(player)&&world.isChunkLoaded(machine.getPos())&&world.getBlockEntity(machine.getPos())==machine
            &&player.squaredDistanceTo(Vec3d.ofCenter(machine.getPos()))<=64&&world.canPlayerModifyAt(player,machine.getPos());
    }
    private void edit(Runnable action){
        if(machine==null||editing){action.run();return;}
        machine.settle();var before=machine.card();int multiplier=machine.multiplier();
        machine.beginSlotEdit();editing=true;
        try{action.run();}finally{editing=false;machine.endSlotEdit(before,multiplier);}
    }
    @Override public void onSlotClick(int slot,int button,SlotActionType action,PlayerEntity player){
        if(!canUse(player))return;
        edit(()->super.onSlotClick(slot,button,action,player));
    }
    @Override public ItemStack quickMove(PlayerEntity player,int index){
        if(!canUse(player)||index<0||index>=slots.size())return ItemStack.EMPTY;
        if(machine!=null&&!editing){var result=new ItemStack[]{ItemStack.EMPTY};edit(()->result[0]=quickMove(player,index));return result[0];}
        var slot=slots.get(index);if(!slot.hasStack())return ItemStack.EMPTY;
        var stack=slot.getStack();var original=stack.copy();
        if(index<2){if(!insertItem(stack,2,38,true))return ItemStack.EMPTY;}
        else if(stack.getItem() instanceof CardItem){if(!insertItem(stack,0,1,false))return ItemStack.EMPTY;}
        else if(validFuel(stack)){if(!insertItem(stack,1,2,false))return ItemStack.EMPTY;}
        else if(index<29){if(!insertItem(stack,29,38,false))return ItemStack.EMPTY;}
        else if(!insertItem(stack,2,29,false))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.setStack(ItemStack.EMPTY);else slot.markDirty();
        if(stack.getCount()==original.getCount())return ItemStack.EMPTY;
        slot.onTakeItem(player,stack);return original;
    }
    @Override public void sendContentUpdates(){if(machine!=null&&!editing&&canUse(owner))machine.settle();super.sendContentUpdates();}
    @Override public boolean onButtonClick(PlayerEntity player,int id){
        if(machine==null||id!=0||!canUse(player))return false;
        try{long amount=machine.extract(player);player.sendMessage(Text.literal(amount>0?"已提取 "+amount+" 宝可币至手机钱包":"暂无可提取的整数收益"),true);}
        catch(ArithmeticException overflow){player.sendMessage(Text.literal("钱包余额空间不足，收益仍保留在机内"),true);}
        sendContentUpdates();return true;
    }
    private final class MachineInventory implements Inventory {
        @Override public int size(){return 2;}
        @Override public boolean isEmpty(){return getStack(0).isEmpty()&&getStack(1).isEmpty();}
        @Override public ItemStack getStack(int slot){return machine.slotStack(slot,editing);}
        @Override public ItemStack removeStack(int slot,int amount){var stack=getStack(slot);var result=stack.split(amount);setStack(slot,stack);return result;}
        @Override public ItemStack removeStack(int slot){var stack=getStack(slot);setStack(slot,ItemStack.EMPTY);return stack;}
        @Override public void setStack(int slot,ItemStack stack){machine.setSlotStack(slot,stack);}
        @Override public void markDirty(){machine.markDirty();}
        @Override public boolean canPlayerUse(PlayerEntity player){return RentalScreenHandler.this.canUse(player);}
        @Override public boolean isValid(int slot,ItemStack stack){return slot==0?stack.getItem() instanceof CardItem:validFuel(stack);}
        @Override public void clear(){setStack(0,ItemStack.EMPTY);setStack(1,ItemStack.EMPTY);}
    }
}
