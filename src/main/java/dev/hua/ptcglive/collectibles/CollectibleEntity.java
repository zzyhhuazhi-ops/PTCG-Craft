package dev.hua.ptcglive.collectibles;

import dev.hua.ptcglive.*;
import dev.hua.ptcglive.economy.*;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;

/** Physical inventories deliberately do not implement Inventory/SidedInventory: no remote collection or hopper access. */
public final class CollectibleEntity extends BlockEntity {
    private UUID owner;
    public static final int ALBUM_PAGES=100, PAGE_SLOTS=9;
    private final List<ItemStack> cards;
    private final List<List<String>> orders=new ArrayList<>();
    private final int[] rewards=new int[3];
    private final boolean[] completed=new boolean[3];
    private long refreshAt;
    private int selected,fx=2,background=2,lighting=0,zoom=0;
    private boolean disposed;
    private boolean structureBuilt;
    private int structureVersion;
    boolean resizing;
    public void structureBuilt(){structureBuilt=true;structureVersion=2;markDirty();}
    public void ensureStructure(){if((!structureBuilt||kind()==CollectibleBlocks.Kind.EXCHANGE&&structureVersion<2)&&!disposed&&CollectibleStructure.expandLegacy(world,pos,getCachedState()))structureBuilt();}
    public CollectibleEntity(BlockPos pos,BlockState state){super(CollectibleBlocks.ENTITY,pos,state);cards=new ArrayList<>(Collections.nCopies(((CollectibleBlock)state.getBlock()).kind==CollectibleBlocks.Kind.ALBUM?ALBUM_PAGES*PAGE_SLOTS:9,ItemStack.EMPTY));}
    public CollectibleBlocks.Kind kind(){return ((CollectibleBlock)getCachedState().getBlock()).kind;}
    public void owner(UUID id){owner=id;sync();}
    public boolean mayAccess(PlayerEntity player){return owner!=null&&owner.equals(player.getUuid())&&!player.isSpectator();}
    public ItemStack card(int index){return cards.get(index);}
    public void card(int index,ItemStack stack){cards.set(index,stack);sync();}
    public int slots(){return switch(kind()){case ALBUM->ALBUM_PAGES*PAGE_SLOTS;case EXCHANGE->4;default->1;};}
    public int selected(){return selected;}
    public int fx(){return fx;}
    public int background(){return background;}
    public int lighting(){return lighting;}
    public int zoom(){return zoom;}
    public List<String> order(int row){return row<orders.size()?orders.get(row):List.of();}
    public int reward(int row){return rewards[row];}
    public boolean completed(int row){return completed[row];}
    public long now(){return dev.hua.ptcglive.rental.RentalClock.get(world.getServer()).now();}
    public int refreshSeconds(){return (int)Math.max(0,(refreshAt-now()+999)/1000);}
    public void refresh(){
        if(kind()!=CollectibleBlocks.Kind.EXCHANGE||world==null||world.isClient||disposed)return;
        if(orders.size()==3&&now()<refreshAt)return;
        // Existing input slots are never cleared by a refresh.
        orders.clear();Arrays.fill(completed,false);
        var catalog=CardItems.cards();var rng=world.getRandom();
        for(int row=0;row<3;row++){
            var ids=new ArrayList<String>();int value=0,n=1+rng.nextInt(4);
            for(int i=0;i<n;i++){
                var card=catalog.get(rng.nextInt(catalog.size())).card();ids.add(card.id());
                value+=RecyclingService.price(RecyclingService.rarity(card.id(),card.rarityZh()));
            }
            orders.add(List.copyOf(ids));rewards[row]=value*(8+rng.nextInt(9));
        }
        refreshAt=now()+(5+rng.nextInt(11))*60_000L;sync();
    }
    public long quote(){
        if(order(selected).isEmpty()||completed[selected])return 0;
        long weighted=0,total=0;
        for(int i=0;i<order(selected).size();i++){
            String id=order(selected).get(i);var card=CardItems.byDefinitionId(id).card();
            int value=RecyclingService.price(RecyclingService.rarity(card.id(),card.rarityZh()));total+=value;
            var stack=cards.get(i);
            weighted+=(long)value*(stack.getItem() instanceof CardItem c&&c.card().id().equals(id)?CardCondition.percent(stack):100);
        }
        return rewards[selected]*weighted/(100*total);
    }
    public void act(PlayerEntity player,int button){
        if(world==null||world.isClient||disposed||!mayAccess(player))throw new IllegalStateException("无权操作此设备");
        var wallet=PtcgServices.economy().wallet();
        if(kind()==CollectibleBlocks.Kind.APPRAISER&&button==0){
            var original=cards.getFirst();if(original.isEmpty())throw new IllegalArgumentException("请放入一张未鉴定卡牌");
            if(CardCondition.graded(original))throw new IllegalStateException("这张卡已经鉴定，品质不可更改");
            var graded=original.copy();
            if(wallet.balance(player.getUuid())<100)throw new IllegalStateException("鉴定需要100宝可币，余额不足");
            CardCondition.apply(graded,CardCondition.values()[world.getRandom().nextInt(6)]);
            try{wallet.transaction(()->{wallet.debit(UUID.randomUUID(),player.getUuid(),100,PokeCoinLedger.Kind.CARD_APPRAISAL,"卡牌品质鉴定",world.getTime());cards.set(0,graded);return null;});}
            catch(RuntimeException ex){cards.set(0,original);throw ex;}
            sync();return;
        }
        if(kind()==CollectibleBlocks.Kind.EXCHANGE){
            if(orders.size()==3&&now()>=refreshAt){refresh();throw new IllegalStateException("订单已刷新，请重新查看并选择");}
            refresh();
            if(button>=10&&button<13){selected=button-10;sync();return;}
            if(button==1){fill(player);return;}
            if(button==0){
                var order=order(selected);if(completed[selected]||order.isEmpty())throw new IllegalStateException("此订单已完成或已过期");
                for(int i=0;i<order.size();i++)if(!(cards.get(i).getItem() instanceof CardItem c)||!c.card().id().equals(order.get(i))||cards.get(i).getCount()!=1)
                    throw new IllegalStateException("请按订单逐格放入对应卡牌，或从图鉴填充");
                long coins=quote();var before=new ArrayList<>(cards);
                try{wallet.transaction(()->{wallet.credit(UUID.randomUUID(),player.getUuid(),coins,PokeCoinLedger.Kind.CARD_ORDER,"卡牌交易所订单",world.getTime());
                    for(int i=0;i<order.size();i++)cards.set(i,ItemStack.EMPTY);completed[selected]=true;return null;});}
                catch(RuntimeException ex){for(int i=0;i<9;i++)cards.set(i,before.get(i));completed[selected]=false;throw ex;}
                sync();return;
            }
        }
        if(kind()==CollectibleBlocks.Kind.HOLOGRAM){switch(button){case 0->zoom=1-zoom;case 1->fx=(fx+1)%3;case 2->background=(background+1)%3;case 3->lighting=(lighting+1)%3;default->throw new IllegalArgumentException("设置无效");}sync();return;}
        throw new IllegalArgumentException("操作无效");
    }
    private void fill(PlayerEntity player){
        if(completed[selected])throw new IllegalStateException("此订单已完成");
        var ledger=PtcgServices.cardBoxes();var available=new ArrayList<>(ledger.unboxedForOwner(player.getUuid()));
        var chosen=new LinkedHashMap<Integer,dev.hua.ptcglive.rules.CardInstance>();
        for(int i=0;i<order(selected).size();i++){
            String id=order(selected).get(i);var current=cards.get(i);
            if(!current.isEmpty()){if(!(current.getItem() instanceof CardItem c)||!c.card().id().equals(id))throw new IllegalStateException("请先取出不符合订单的卡牌");continue;}
            var found=available.stream().filter(c->c.definitionId().equals(id)).findFirst().orElseThrow(()->new IllegalStateException("图鉴中缺少订单卡牌，未移动任何卡牌"));
            chosen.put(i,found);available.remove(found);
        }
        var before=new ArrayList<>(cards);
        try{ledger.transaction(()->{ledger.revokeUnboxed(player.getUuid(),new ArrayList<>(chosen.values()));
            chosen.forEach((slot,c)->cards.set(slot,new ItemStack(CardItems.byDefinitionId(c.definitionId()))));return null;});}
        catch(RuntimeException ex){for(int i=0;i<9;i++)cards.set(i,before.get(i));throw ex;}
        sync();
    }
    public ItemStack takeAlbumBook(PlayerEntity player){
        if(kind()!=CollectibleBlocks.Kind.ALBUM||disposed||!mayAccess(player))throw new IllegalStateException("无权取走卡册");
        var book=AlbumBookItem.fromLegacy(this,player);Collections.fill(cards,ItemStack.EMPTY);disposed=true;sync();return book;
    }
    public void dropContents(){if(disposed||world==null||world.isClient)return;disposed=true;for(int i=0;i<cards.size();i++){var stack=cards.get(i);cards.set(i,ItemStack.EMPTY);if(!stack.isEmpty())Block.dropStack(world,pos,stack);}sync();}
    public void sync(){markDirty();if(world!=null)world.updateListeners(pos,getCachedState(),getCachedState(),Block.NOTIFY_LISTENERS);}
    @Override protected void writeNbt(NbtCompound nbt,RegistryWrapper.WrapperLookup registries){
        super.writeNbt(nbt,registries);if(owner!=null)nbt.putUuid("owner",owner);
        var stored=new NbtList();for(int i=0;i<cards.size();i++)if(!cards.get(i).isEmpty()){
            var entry=new NbtCompound();entry.putInt("slot",i);entry.put("stack",cards.get(i).encodeAllowEmpty(registries));stored.add(entry);
        }nbt.put("physicalSlots",stored);
        nbt.putBoolean("disposed",disposed);nbt.putLong("refreshAt",refreshAt);nbt.putInt("pricingVersion",2);nbt.putInt("selected",selected);
        nbt.putBoolean("structureBuilt",structureBuilt);nbt.putInt("structureVersion",structureVersion);
        nbt.putInt("fx",fx);nbt.putInt("background",background);nbt.putInt("lighting",lighting);nbt.putInt("zoom",zoom);
        var list=new NbtList();for(int i=0;i<orders.size();i++){var row=new NbtCompound();var ids=new NbtList();for(String id:orders.get(i))ids.add(NbtString.of(id));row.put("ids",ids);row.putInt("reward",rewards[i]);row.putBoolean("completed",completed[i]);list.add(row);}nbt.put("orders",list);
    }
    @Override protected void readNbt(NbtCompound nbt,RegistryWrapper.WrapperLookup registries){
        super.readNbt(nbt,registries);owner=nbt.containsUuid("owner")?nbt.getUuid("owner"):null;disposed=nbt.getBoolean("disposed");
        structureBuilt=nbt.getBoolean("structureBuilt");structureVersion=nbt.getInt("structureVersion");
        Collections.fill(cards,ItemStack.EMPTY);
        if(!disposed){
            if(nbt.contains("physicalSlots")){var stored=nbt.getList("physicalSlots",NbtElement.COMPOUND_TYPE);for(int i=0;i<stored.size();i++){
                var entry=stored.getCompound(i);int slot=entry.getInt("slot");if(slot>=0&&slot<cards.size())cards.set(slot,ItemStack.fromNbtOrEmpty(registries,entry.getCompound("stack")));
            }}else{var stored=nbt.getList("cards",NbtElement.COMPOUND_TYPE);for(int i=0;i<Math.min(9,stored.size());i++)cards.set(i,ItemStack.fromNbtOrEmpty(registries,stored.getCompound(i)));}
            cards.forEach(CardCondition::normalizeLegacyStack);
        }
        refreshAt=nbt.getInt("pricingVersion")>=2?Math.max(0,nbt.getLong("refreshAt")):0;selected=Math.clamp(nbt.getInt("selected"),0,2);
        fx=Math.clamp(nbt.getInt("fx"),0,2);background=Math.clamp(nbt.getInt("background"),0,2);lighting=Math.clamp(nbt.getInt("lighting"),0,2);zoom=Math.clamp(nbt.getInt("zoom"),0,1);
        orders.clear();var list=nbt.getList("orders",NbtElement.COMPOUND_TYPE);for(int i=0;i<Math.min(3,list.size());i++){var row=list.getCompound(i);var ids=row.getList("ids",NbtElement.STRING_TYPE);var order=new ArrayList<String>();for(int j=0;j<Math.min(4,ids.size());j++)order.add(ids.getString(j));orders.add(List.copyOf(order));rewards[i]=Math.clamp(row.getInt("reward"),1,100000);completed[i]=row.getBoolean("completed");}
    }
    /** Only a public hologram card and its display settings leave the server. */
    @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries){
        var nbt=new NbtCompound();nbt.putInt("fx",fx);nbt.putInt("background",background);nbt.putInt("lighting",lighting);nbt.putInt("zoom",zoom);
        if(kind()==CollectibleBlocks.Kind.HOLOGRAM){var list=new NbtList();list.add(cards.getFirst().encodeAllowEmpty(registries));nbt.put("cards",list);}return nbt;
    }
    @Override public BlockEntityUpdateS2CPacket toUpdatePacket(){return BlockEntityUpdateS2CPacket.create(this);}
}
