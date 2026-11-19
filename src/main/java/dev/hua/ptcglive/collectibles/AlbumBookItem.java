package dev.hua.ptcglive.collectibles;

import net.minecraft.item.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.minecraft.util.*;
import net.minecraft.text.Text;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.nbt.*;

/** A physical book: contents travel with this item, never enter the collection ledger. */
public final class AlbumBookItem extends Item {
    public AlbumBookItem(){super(new Item.Settings().maxCount(1));}
    @Override public TypedActionResult<ItemStack> use(World world,PlayerEntity player,Hand hand){
        var stack=player.getStackInHand(hand);
        if(!world.isClient){
            if(stack.getCount()!=1){player.sendMessage(Text.literal("请先将旧版叠放的卡册分开，再打开一本"),true);return TypedActionResult.fail(stack);}
            int slot=hand==Hand.MAIN_HAND?player.getInventory().selectedSlot:40;
            var book=new Storage(player,slot,stack);
            player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id,inv,p)->new CollectibleHandler(id,inv,book),Text.literal("收藏卡册")));
        }
        return TypedActionResult.success(stack,world.isClient);
    }
    public static final class Storage {
        private final PlayerEntity player;
        public final int inventorySlot;
        private final ItemStack book;
        private final ItemStack[] cards=new ItemStack[900];
        public Storage(PlayerEntity player,int slot,ItemStack book){
            this.player=player;this.inventorySlot=slot;this.book=book;java.util.Arrays.fill(cards,ItemStack.EMPTY);
            var data=book.getOrDefault(DataComponentTypes.CUSTOM_DATA,NbtComponent.DEFAULT).copyNbt();
            var list=data.getList("albumCards",NbtElement.COMPOUND_TYPE);
            for(int i=0;i<list.size();i++){var entry=list.getCompound(i);int index=entry.getInt("slot");
                if(index>=0&&index<cards.length){cards[index]=ItemStack.fromNbtOrEmpty(player.getRegistryManager(),entry.getCompound("stack"));CardCondition.normalizeLegacyStack(cards[index]);}}
        }
        public boolean canUse(PlayerEntity p){return p==player&&p.isAlive()&&!p.isSpectator()&&p.getInventory().getStack(inventorySlot)==book&&book.getCount()==1;}
        public ItemStack get(int i){return cards[i];}
        public void set(int i,ItemStack stack){cards[i]=stack;save();}
        public void save(){
            var data=book.getOrDefault(DataComponentTypes.CUSTOM_DATA,NbtComponent.DEFAULT).copyNbt();var list=new NbtList();
            for(int i=0;i<cards.length;i++)if(!cards[i].isEmpty()){
                var entry=new NbtCompound();entry.putInt("slot",i);entry.put("stack",cards[i].encodeAllowEmpty(player.getRegistryManager()));list.add(entry);}
            data.put("albumCards",list);book.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(data));player.getInventory().markDirty();
        }
    }
    public static ItemStack fromLegacy(CollectibleEntity machine,PlayerEntity player){
        var item=new ItemStack(CollectibleBlocks.ALBUM_ITEM);var storage=new Storage(player,-1,item);
        for(int i=0;i<900;i++)storage.cards[i]=machine.card(i).copy();storage.save();return item;
    }
}
