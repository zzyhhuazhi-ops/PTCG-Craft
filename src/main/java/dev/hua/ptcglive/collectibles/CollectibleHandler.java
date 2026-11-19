package dev.hua.ptcglive.collectibles;

import dev.hua.ptcglive.*;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.*;
import net.minecraft.text.Text;

/** Vanilla slot/cursor transactions; display-only order slots can never be taken. */
public final class CollectibleHandler extends ScreenHandler {
    private final CollectibleEntity machine;
    private final PlayerEntity player;
    private final Inventory inputs;
    private final SimpleInventory ghosts=new SimpleInventory(12);
    private final PropertyDelegate properties;
    private final boolean album;
    private AlbumBookItem.Storage book;
    private int page;
    private int physicalSlot(int index){return machine.kind()==CollectibleBlocks.Kind.ALBUM?page*CollectibleEntity.PAGE_SLOTS+index:index;}
    public CollectibleHandler(int id,PlayerInventory inventory,AlbumBookItem.Storage book){this(id,inventory,null,true,book);}
    public CollectibleHandler(int id,PlayerInventory player){this(id,player,(CollectibleEntity)null);}
    public CollectibleHandler(int id,PlayerInventory player,boolean album){this(id,player,null,album);}
    public CollectibleHandler(int id,PlayerInventory inventory,CollectibleEntity machine){
        this(id,inventory,machine,machine!=null&&machine.kind()==CollectibleBlocks.Kind.ALBUM);
    }
    private CollectibleHandler(int id,PlayerInventory inventory,CollectibleEntity machine,boolean album){
        this(id,inventory,machine,album,null);
    }
    private CollectibleHandler(int id,PlayerInventory inventory,CollectibleEntity machine,boolean album,AlbumBookItem.Storage book){
        super(album?CollectibleBlocks.ALBUM_HANDLER:CollectibleBlocks.HANDLER,id);this.machine=machine;this.player=inventory.player;this.album=album;
        this.book=book;int visible=album?18:9;
        inputs=machine==null&&book==null?new SimpleInventory(visible):new Inventory(){
            public int size(){return visible;}public boolean isEmpty(){for(int i=0;i<visible;i++)if(!getStack(i).isEmpty())return false;return true;}
            public ItemStack getStack(int i){return book!=null?book.get(page*9+i):machine.card(physicalSlot(i));}
            public ItemStack removeStack(int i,int n){var result=getStack(i).split(n);markDirty();return result;}
            public ItemStack removeStack(int i){var result=getStack(i);setStack(i,ItemStack.EMPTY);return result;}
            public void setStack(int i,ItemStack stack){if(book!=null)book.set(page*9+i,stack);else machine.card(physicalSlot(i),stack);}public void markDirty(){if(book!=null)book.save();else machine.sync();}
            public boolean canPlayerUse(PlayerEntity p){return canUse(p);}public void clear(){for(int i=0;i<visible;i++)setStack(i,ItemStack.EMPTY);}
        };
        properties=book!=null?new PropertyDelegate(){public int size(){return 16;}public void set(int i,int v){}public int get(int i){return i==15?page:i==0?3:0;}}:machine==null?new ArrayPropertyDelegate(16):new PropertyDelegate(){
            public int size(){return 16;}public void set(int i,int v){}
            public int get(int i){return switch(i){
                case 0->machine.kind().ordinal();case 1->machine.selected();case 2->machine.refreshSeconds();
                case 3,4,5->machine.completed(i-3)?-1:machine.reward(i-3);case 6->(int)machine.quote();
                case 7->machine.fx();case 8->machine.background();case 9->machine.lighting();case 10->machine.zoom();
                case 15->page;
                default->(int)((PtcgServices.economy().wallet().balance(player.getUuid())>>>((i-11)*16))&65535);
            };}
        };
        for(int i=0;i<visible;i++){final int index=i;addSlot(new Slot(inputs,i,album?albumX(i):200+i%2*26,album?albumY(i):51+i/2*26){
            @Override public int getMaxItemCount(){return 1;}
            @Override public boolean isEnabled(){return index<capacity();}
            @Override public boolean canInsert(ItemStack stack){return isEnabled()&&stack.getItem() instanceof CardItem;}
            @Override public boolean canTakeItems(PlayerEntity p){return isEnabled()&&canUse(p);}
        });}
        for(int i=0;!album&&i<12;i++){final int index=i;addSlot(new Slot(ghosts,i,18+i%4*20,34+i/4*42){
            @Override public boolean isEnabled(){return kind()==CollectibleBlocks.Kind.EXCHANGE;}
            @Override public boolean canInsert(ItemStack stack){return false;}@Override public boolean canTakeItems(PlayerEntity p){return false;}
        });}
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inventory,9+row*9+col,(album?129:75)+col*18,(album?272:226)+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,(album?129:75)+col*18,album?330:284));
        addProperties(properties);updateOrders();
    }
    public int value(int i){return properties.get(i);}
    public static int albumX(int i){return 24+(i/9)*205+(i%3)*54;}
    public static int albumY(int i){return 26+(i%9/3)*66;}
    public int page(){return value(15);}
    public CollectibleBlocks.Kind kind(){return album?CollectibleBlocks.Kind.ALBUM:CollectibleBlocks.Kind.values()[Math.clamp(value(0),0,3)];}
    private int capacity(){return switch(kind()){case EXCHANGE->4;case ALBUM->18;default->1;};}
    public long balance(){long n=0;for(int i=0;i<4;i++)n|=(long)(value(11+i)&65535)<<(16*i);return n;}
    @Override public boolean canUse(PlayerEntity p){return book!=null?book.canUse(p):machine==null||(!machine.isRemoved()&&p.isAlive()&&machine.mayAccess(p)&&p.getWorld()==machine.getWorld()
        &&p.squaredDistanceTo(net.minecraft.util.math.Vec3d.ofCenter(machine.getPos()))<=64&&p.getWorld().getBlockEntity(machine.getPos())==machine
        &&p.getWorld().canPlayerModifyAt(p,machine.getPos()));}
    @Override public void onSlotClick(int slot,int button,SlotActionType action,PlayerEntity p){
        if(book!=null&&(slot>=0&&slot<slots.size()&&slots.get(slot).inventory==p.getInventory()&&slots.get(slot).getIndex()==book.inventorySlot
            ||action==SlotActionType.SWAP&&button==book.inventorySlot))return;
        if(!canUse(p)||!album&&slot>=9&&slot<21)return;
        // The album intentionally has no shift-click or drag-across-slots bulk operations.
        if(kind()==CollectibleBlocks.Kind.ALBUM&&(action==SlotActionType.QUICK_MOVE||action==SlotActionType.QUICK_CRAFT||action==SlotActionType.PICKUP_ALL))return;
        super.onSlotClick(slot,button,action,p);
    }
    @Override public ItemStack quickMove(PlayerEntity p,int index){
        if(!canUse(p)||kind()==CollectibleBlocks.Kind.ALBUM||index<0||index>=slots.size()||index>=9&&index<21)return ItemStack.EMPTY;
        var slot=slots.get(index);if(!slot.hasStack())return ItemStack.EMPTY;var stack=slot.getStack();var before=stack.copy();
        if(index<9){if(!insertItem(stack,21,57,true))return ItemStack.EMPTY;}
        else if(!insertItem(stack,0,capacity(),false))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.setStack(ItemStack.EMPTY);else slot.markDirty();slot.onTakeItem(p,stack);return before;
    }
    private void updateOrders(){if(machine==null||album)return;machine.refresh();for(int i=0;i<12;i++){var row=machine.order(i/4);ghosts.setStack(i,i%4<row.size()?new ItemStack(CardItems.byDefinitionId(row.get(i%4))):ItemStack.EMPTY);}}
    @Override public void sendContentUpdates(){if(machine!=null&&canUse(player))updateOrders();super.sendContentUpdates();}
    @Override public boolean onButtonClick(PlayerEntity p,int id){
        if(machine==null&&book==null||!canUse(p))return false;
        if(kind()==CollectibleBlocks.Kind.ALBUM){
            if((id!=20&&id!=21)||!getCursorStack().isEmpty())return false;
            page=Math.clamp(page+(id==20?-2:2),0,CollectibleEntity.ALBUM_PAGES-2);sendContentUpdates();return true;
        }
        try{machine.act(p,id);}catch(IllegalArgumentException|IllegalStateException|ArithmeticException ex){p.sendMessage(Text.literal(ex.getMessage()),true);}
        sendContentUpdates();return true;
    }
}
