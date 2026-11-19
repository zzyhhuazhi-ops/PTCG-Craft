package dev.hua.ptcglive.gametest;
import dev.hua.ptcglive.*;
import dev.hua.ptcglive.collection.*;
import dev.hua.ptcglive.economy.*;
import dev.hua.ptcglive.network.*;
import dev.hua.ptcglive.rules.*;
import java.time.Clock;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.*;
import net.minecraft.item.ItemStack;
import net.minecraft.world.GameMode;
import net.minecraft.util.math.BlockPos;

public final class RecyclingIntegrationTest implements FabricGameTest {
 @GameTest(templateName=EMPTY_STRUCTURE)
 public void phoneOpensOnlyNearbyStationWithoutRecycling(TestContext c)throws Exception{
  var p=c.createMockCreativeServerPlayerInWorld();var local=new BlockPos(1,1,1);var pos=c.getAbsolutePos(local);
  c.setBlockState(local,PtcgContent.CARD_RECYCLING_STATION);p.setPosition(net.minecraft.util.math.Vec3d.ofCenter(pos));
  var card=common();p.getInventory().setStack(0,new ItemStack(card,12));
  PhoneNetworking.open(p);
  var phones=PhoneNetworking.class.getDeclaredField("SESSIONS");phones.setAccessible(true);
  var phone=((Map<?,?>)phones.get(null)).get(p.getUuid());
  String token=field(phone,"id").toString();
  var handle=PhoneNetworking.class.getDeclaredMethod("handle",net.minecraft.server.network.ServerPlayerEntity.class,PhoneIntentPayload.class);handle.setAccessible(true);
  var request=new PhoneIntentPayload(token,"OPEN_RECYCLE","",0,UUID.randomUUID().toString());
  handle.invoke(null,p,request);
  var sessions=RecyclingNetworking.class.getDeclaredField("SESSIONS");sessions.setAccessible(true);
  var recycler=((Map<?,?>)sessions.get(null)).get(p.getUuid());
  c.assertTrue(recycler!=null&&field(recycler,"pos").equals(pos),"手机没有打开附近真实回收站");
  c.assertTrue(p.getInventory().count(card)==12&&field(recycler,"quote")==null,"打开入口不应回收卡或生成确认");
  intent(p,(String)field(recycler,"id"),(int)field(recycler,"revision"),"CLOSE","");
  p.setPosition(net.minecraft.util.math.Vec3d.ofCenter(pos).add(40,0,0));
  handle.invoke(null,p,request);
  c.assertTrue(!((Map<?,?>)sessions.get(null)).containsKey(p.getUuid()),"手机绕过了回收站距离限制");
  handle.invoke(null,p,new PhoneIntentPayload(token,"CLOSE","",0,UUID.randomUUID().toString()));
  p.networkHandler.disconnect(net.minecraft.text.Text.literal("测试完成"));c.complete();
 }
 @GameTest(templateName=EMPTY_STRUCTURE)
 public void categoryThresholdsKeep64Energy16Trainers8Pokemon(TestContext c){
  for(var category:CardCategory.values()){
   var card=CardItems.cards().stream().filter(k->CollectionSearch.facts(k.card().id()).category().equals(category.name())).findFirst().orElseThrow();
   int keep=switch(category){case ENERGY->64;case TRAINER->16;case POKEMON->8;};
   c.assertTrue(RecyclingService.keepCount(card.card().id())==keep,"分类保留数错误");
   var p=c.createMockPlayer(GameMode.SURVIVAL);var b=boxes();var w=wallet();
   deposit(b,p.getUuid(),card.card().id(),keep);reject(()->quote(p,b,RecyclingService.Source.BOTH));
   p.getInventory().setStack(0,new ItemStack(card,2));var q=quote(p,b,RecyclingService.Source.BOTH);
   c.assertTrue(q.count()==2,"回收未按背包加图鉴共同保留");RecyclingService.confirm(p,b,w,q);
   c.assertTrue(b.unboxedForOwner(p.getUuid()).size()==keep&&p.getInventory().count(card)==0,"回收破坏分类保留量");
  }c.complete();
 }
 static CardItem common(){return CardItems.cards().stream().filter(c->RecyclingService.rarity(c.card().rarityZh()).equals("C")&&RecyclingService.keepCount(c.card().id())==8).findFirst().orElseThrow();}
 static void reject(Runnable action){try{action.run();throw new AssertionError("应拒绝的回收被执行");}catch(RuntimeException expected){}}
 static CardBoxService boxes(){return new CardBoxService(Clock.systemUTC());}
 static PokeCoinLedger wallet(){return new PokeCoinLedger(Clock.systemUTC());}
 static void deposit(CardBoxService boxes,UUID owner,String id,int count){for(int i=0;i<count;i++)boxes.mint(owner,id);}
 static RecyclingService.Quote quote(net.minecraft.entity.player.PlayerEntity p,CardBoxService b,RecyclingService.Source source){return RecyclingService.quote(p,b,source,new HashSet<>(RecyclingService.RARITIES),null);}

 @GameTest(templateName=EMPTY_STRUCTURE)
 public void everyRarityHasConfirmedPrice(TestContext c){
  int[] expected={1,2,3,4,5,6,6,7,8,20,1,1};
  for(int i=0;i<expected.length;i++)c.assertTrue(RecyclingService.price(RecyclingService.RARITIES.get(i))==expected[i],"罕度价格不符");
  for(var card:CardItems.cards())c.assertTrue(RecyclingService.price(RecyclingService.rarity(card.card().rarityZh()))>0,"存在无价卡");
  c.assertTrue(RecyclingService.rarity("普通").equals("C")&&RecyclingService.rarity("非凡").equals("U")&&RecyclingService.rarity("双重稀有").equals("RR"),"基本罕度归类错误");
  c.assertTrue(RecyclingService.price(RecyclingService.rarity("特典"))==1&&RecyclingService.price(RecyclingService.rarity("超级极稀有"))==20,"宣传卡不得默认为最高档；明确的最高档仍为20币");
  c.complete();
 }
 @GameTest(templateName=EMPTY_STRUCTURE)
 public void combinedCopiesKeepEightAndExcludeLockedBoxes(TestContext c){
  var p=c.createMockPlayer(GameMode.SURVIVAL);var b=boxes();var w=wallet();var card=common();String id=card.card().id();
  p.getInventory().setStack(0,new ItemStack(card,3));deposit(b,p.getUuid(),id,8);
  var deck=b.create(p.getUuid(),"保护卡组","open");for(int i=0;i<6;i++)b.put(p.getUuid(),deck.id(),b.mint(p.getUuid(),id).instanceId());
  b.snapshotForMatch(p.getUuid(),deck.id(),UUID.randomUUID());p.getInventory().setStack(1,CardBoxItem.referenceTo(deck));
  var q=quote(p,b,RecyclingService.Source.BOTH);
  c.assertTrue(q.count()==3&&q.coins()==3&&p.getInventory().count(card)==3&&w.balance(p.getUuid())==0,"预览改动资产或把卡盒算入可回收数量");
  RecyclingService.confirm(p,b,w,q);
  c.assertTrue(p.getInventory().count(card)==0&&b.unboxedForOwner(p.getUuid()).size()==8&&w.balance(p.getUuid())==3,"跨背包图鉴保留八张失败");
  c.assertTrue(b.contentsForValidation(p.getUuid(),deck.id()).size()==6&&b.isLocked(p.getUuid(),deck.id()),"回收改动锁定卡盒");
  reject(()->RecyclingService.confirm(p,b,w,q));c.assertTrue(w.balance(p.getUuid())==3,"重复确认重复入账");c.complete();
 }
 @GameTest(templateName=EMPTY_STRUCTURE)
 public void sourcesAndPrintingsAreIndependent(TestContext c){
  var p=c.createMockPlayer(GameMode.SURVIVAL);var b=boxes();var w=wallet();var card=common();String id=card.card().id();
  var other=CardItems.cards().stream().filter(k->!k.card().id().equals(id)&&RecyclingService.rarity(k.card().rarityZh()).equals("C")&&RecyclingService.keepCount(k.card().id())==8).findFirst().orElseThrow();
  p.getInventory().setStack(0,new ItemStack(card,RecyclingService.keepCount(card.card().id())+2));p.getInventory().setStack(1,new ItemStack(other,8));deposit(b,p.getUuid(),id,2);
  var onlyCollection=quote(p,b,RecyclingService.Source.COLLECTION);c.assertTrue(onlyCollection.count()==2,"图鉴来源回收了背包卡");
  RecyclingService.confirm(p,b,w,onlyCollection);c.assertTrue(p.getInventory().count(card)==RecyclingService.keepCount(card.card().id())+2&&b.unboxedForOwner(p.getUuid()).isEmpty(),"来源扣卡错误");
  var onlyInventory=quote(p,b,RecyclingService.Source.INVENTORY);c.assertTrue(onlyInventory.count()==2&&onlyInventory.rows().size()==1,"印刷ID的保留数量被合并");
  RecyclingService.confirm(p,b,w,onlyInventory);c.assertTrue(p.getInventory().count(card)==RecyclingService.keepCount(card.card().id())&&p.getInventory().count(other)==8,"保留八张失败");
  reject(()->quote(p,b,RecyclingService.Source.BOTH));c.complete();
 }
 @GameTest(templateName=EMPTY_STRUCTURE)
 public void changedQuoteDiscardedCardsAndWrongOwnerRejected(TestContext c){
  var p=c.createMockPlayer(GameMode.SURVIVAL);var other=c.createMockPlayer(GameMode.SURVIVAL);var b=boxes();var w=wallet();var card=common();
  p.getInventory().setStack(0,new ItemStack(card,RecyclingService.keepCount(card.card().id())+2));var q=quote(p,b,RecyclingService.Source.INVENTORY);
  reject(()->RecyclingService.confirm(other,b,w,q));
  p.getInventory().setStack(0,ItemStack.EMPTY);b.observe(p.getUuid(),Set.of(card.card().id()));
  c.assertTrue(RecyclingService.rows(p,b,RecyclingService.Source.BOTH,Set.of("C")).isEmpty(),"丢弃卡或历史图鉴发现被计入回收");
  reject(()->RecyclingService.confirm(p,b,w,q));c.assertTrue(w.balance(p.getUuid())==0,"过期预览产生收入");
  p.getInventory().setStack(0,new ItemStack(card,11));reject(()->RecyclingService.confirm(p,b,w,q));
  c.assertTrue(p.getInventory().count(card)==11,"数量变化后仍扣卡");c.complete();
 }
 @GameTest(templateName=EMPTY_STRUCTURE)
 public void overflowAndInterruptedCreditRollBackBothStores(TestContext c){
  var p=c.createMockPlayer(GameMode.SURVIVAL);var b=boxes();var w=wallet();var card=common();String id=card.card().id();
  p.getInventory().setStack(0,new ItemStack(card,2));deposit(b,p.getUuid(),id,9);var q=quote(p,b,RecyclingService.Source.BOTH);
  var before=b.snapshot();w.credit(UUID.randomUUID(),p.getUuid(),Long.MAX_VALUE,PokeCoinLedger.Kind.ADMIN_MIGRATION,"test",0);
  reject(()->RecyclingService.confirm(p,b,w,q));c.assertTrue(b.snapshot().equals(before)&&p.getInventory().count(card)==2&&w.balance(p.getUuid())==Long.MAX_VALUE,"余额溢出丢卡");
  var interrupted=wallet();interrupted.onChanged(()->{throw new IllegalStateException("模拟钱包提交失败");});
  reject(()->RecyclingService.confirm(p,b,interrupted,q));c.assertTrue(b.snapshot().equals(before)&&p.getInventory().count(card)==2&&interrupted.balance(p.getUuid())==0&&interrupted.history(p.getUuid()).isEmpty(),"中断后资产未回滚");
  interrupted.onChanged(()->{});b.onChanged(()->{throw new IllegalStateException("模拟卡账本提交失败");});
  reject(()->RecyclingService.confirm(p,b,interrupted,q));c.assertTrue(b.snapshot().equals(before)&&p.getInventory().count(card)==2&&interrupted.balance(p.getUuid())==0,"卡账本提交失败时钱包未回滚");c.complete();
 }
 @GameTest(templateName=EMPTY_STRUCTURE)
 public void filtersSpecialPriceFullInventoryAndOffhand(TestContext c){
  var p=c.createMockPlayer(GameMode.SURVIVAL);var b=boxes();var w=wallet();var common=common();
  var special=CardItems.cards().stream().filter(k->RecyclingService.rarity(k.card().rarityZh()).equals("SPECIAL")).findFirst().orElseThrow();
  for(int i=0;i<36;i++)p.getInventory().setStack(i,new ItemStack(net.minecraft.item.Items.DIRT,64));
  p.getInventory().setStack(40,new ItemStack(common,10));deposit(b,p.getUuid(),special.card().id(),RecyclingService.keepCount(special.card().id())+1);
  var q=RecyclingService.quote(p,b,RecyclingService.Source.BOTH,Set.of("SPECIAL"),null);
  c.assertTrue(q.count()==1&&q.coins()==20,"特殊罕度价格或筛选错误");RecyclingService.confirm(p,b,w,q);
  c.assertTrue(p.getInventory().getStack(40).getCount()==10&&b.unboxedForOwner(p.getUuid()).size()==RecyclingService.keepCount(special.card().id()),"回收影响未选择罕度或背包满导致失败");
  var next=RecyclingService.quote(p,b,RecyclingService.Source.INVENTORY,Set.of("C"),Set.of(common.card().id()));RecyclingService.confirm(p,b,w,next);
  c.assertTrue(p.getInventory().getStack(40).getCount()==8&&w.balance(p.getUuid())==22,"副手或单独选卡回收错误");c.complete();
 }
 @GameTest(templateName=EMPTY_STRUCTURE)
 public void recycleBalanceAndHistorySurviveWorldNbt(TestContext c)throws Exception{
  var p=c.createMockPlayer(GameMode.SURVIVAL);var b=boxes();var w=wallet();var card=common();deposit(b,p.getUuid(),card.card().id(),11);
  RecyclingService.confirm(p,b,w,quote(p,b,RecyclingService.Source.COLLECTION));
  var state=new dev.hua.ptcglive.persistence.PtcgPersistentState();
  state.bind(b,new EconomyService(EconomyConfig.loadBundled(),w),PtcgServices.packOpeningAudit(),PtcgServices.starterDeck100(),PtcgServices.tables());
  var nbt=state.writeNbt(new net.minecraft.nbt.NbtCompound(),c.getWorld().getRegistryManager());
  var read=state.getClass().getDeclaredMethod("fromNbt",net.minecraft.nbt.NbtCompound.class,net.minecraft.registry.RegistryWrapper.WrapperLookup.class);read.setAccessible(true);
  var loaded=(dev.hua.ptcglive.persistence.PtcgPersistentState)read.invoke(null,nbt,c.getWorld().getRegistryManager());
  var restored=PokeCoinLedger.restore(Clock.systemUTC(),loaded.economySnapshot());
  c.assertTrue(restored.balance(p.getUuid())==3&&restored.history(p.getUuid()).getFirst().kind()==PokeCoinLedger.Kind.CARD_RECYCLE&&loaded.cardSnapshot().cards().size()==8,"世界存档丢失余额、回收记录或保留卡");c.complete();
 }
 static Object field(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
 @GameTest(templateName=EMPTY_STRUCTURE)
 public void initialFilterIncludesRareCardsAndManualSelectionWorks(TestContext c)throws Exception{
  var p=c.createMockCreativeServerPlayerInWorld();var local=new BlockPos(1,1,1);var pos=c.getAbsolutePos(local);
  c.setBlockState(local,PtcgContent.CARD_RECYCLING_STATION);p.setPosition(net.minecraft.util.math.Vec3d.ofCenter(pos));
  var card=CardItems.cards().stream().filter(k->RecyclingService.rarity(k.card().rarityZh()).equals("SR")).findFirst().orElseThrow();
  p.getInventory().setStack(0,new ItemStack(card,RecyclingService.keepCount(card.card().id())+2));var wallet=PtcgServices.economy().wallet();long before=wallet.balance(p.getUuid());
  RecyclingNetworking.open(p,pos);var sf=RecyclingNetworking.class.getDeclaredField("SESSIONS");sf.setAccessible(true);
  var session=((Map<?,?>)sf.get(null)).get(p.getUuid());String token=(String)field(session,"id");
  c.assertTrue(((Set<?>)field(session,"rarities")).containsAll(RecyclingService.RARITIES),"打开默认筛选隐藏了稀有卡");
  var rows=(List<RecyclingService.Row>)field(session,"listed");
  c.assertTrue(rows.size()==1&&rows.getFirst().id().equals(card.card().id())&&rows.getFirst().amount()==2,"初始列表不能选择可回收卡");
  intent(p,token,(int)field(session,"revision"),"PREVIEW_SELECTED","[\""+card.card().id()+"\"]");
  var quote=(RecyclingService.Quote)field(session,"quote");
  c.assertTrue(quote.count()==2&&quote.coins()==12&&p.getInventory().count(card)==RecyclingService.keepCount(card.card().id())+2,"单独选卡报价或二次确认错误");
  intent(p,token,(int)field(session,"revision"),"CONFIRM",quote.token().toString());
  c.assertTrue(p.getInventory().count(card)==RecyclingService.keepCount(card.card().id())&&wallet.balance(p.getUuid())==before+12,"手动选择回收未保留八张或入账错误");
  intent(p,token,(int)field(session,"revision"),"CLOSE","");p.networkHandler.disconnect(net.minecraft.text.Text.literal("测试完成"));c.complete();
 }
 static void intent(net.minecraft.server.network.ServerPlayerEntity player,String token,int revision,String action,String arg)throws Exception{
  var m=RecyclingNetworking.class.getDeclaredMethod("handle",net.minecraft.server.network.ServerPlayerEntity.class,RecycleIntent.class);m.setAccessible(true);m.invoke(null,player,new RecycleIntent(token,revision,action,arg));
 }
 @GameTest(templateName=EMPTY_STRUCTURE)
 public void serverConfirmationIsRequiredOneUseAndBoundToStation(TestContext c)throws Exception{
  var p=c.createMockCreativeServerPlayerInWorld();var local=new BlockPos(1,1,1);var pos=c.getAbsolutePos(local);
  c.setBlockState(local,PtcgContent.CARD_RECYCLING_STATION);p.setPosition(net.minecraft.util.math.Vec3d.ofCenter(pos));
  var card=common();p.getInventory().setStack(0,new ItemStack(card,11));var wallet=PtcgServices.economy().wallet();long balance=wallet.balance(p.getUuid());
  RecyclingNetworking.open(p,pos);
  var sf=RecyclingNetworking.class.getDeclaredField("SESSIONS");sf.setAccessible(true);var sessions=(Map<?,?>)sf.get(null);var session=sessions.get(p.getUuid());String token=(String)field(session,"id");
  intent(p,token,(int)field(session,"revision"),"CONFIRM",UUID.randomUUID().toString());
  c.assertTrue(wallet.balance(p.getUuid())==balance&&p.getInventory().count(card)==11,"没有预览也能直接回收");
  intent(p,token,(int)field(session,"revision"),"PREVIEW_ALL","");var q=(RecyclingService.Quote)field(session,"quote");
  c.assertTrue(q!=null&&wallet.balance(p.getUuid())==balance,"预览提前扣卡入账");
  intent(p,token,(int)field(session,"revision")-1,"CONFIRM",q.token().toString());
  intent(p,UUID.randomUUID().toString(),(int)field(session,"revision"),"CONFIRM",q.token().toString());
  c.assertTrue(p.getInventory().count(card)==11,"过期或伪造会话生效");
  intent(p,token,(int)field(session,"revision"),"CANCEL","");
  intent(p,token,(int)field(session,"revision"),"CONFIRM",q.token().toString());c.assertTrue(p.getInventory().count(card)==11,"取消后旧确认仍能扣卡");
  intent(p,token,(int)field(session,"revision"),"PREVIEW_ALL","");q=(RecyclingService.Quote)field(session,"quote");
  intent(p,token,(int)field(session,"revision"),"CONFIRM",q.token().toString());
  intent(p,token,(int)field(session,"revision"),"CONFIRM",q.token().toString());
  c.assertTrue(wallet.balance(p.getUuid())==balance+3&&p.getInventory().count(card)==RecyclingService.keepCount(card.card().id()),"重复确认重复扣卡或入账");
  c.setBlockState(local,net.minecraft.block.Blocks.AIR);intent(p,token,(int)field(session,"revision"),"REFRESH","");
  c.assertTrue(!sessions.containsKey(p.getUuid()),"回收站移除后会话未关闭");
  p.networkHandler.disconnect(net.minecraft.text.Text.literal("测试完成"));c.complete();
 }
 @GameTest(templateName=EMPTY_STRUCTURE)
 public void bulkPreviewIncludesNotYetLoadedRows(TestContext c)throws Exception{
  var p=c.createMockCreativeServerPlayerInWorld();var local=new BlockPos(1,1,1);var pos=c.getAbsolutePos(local);
  c.setBlockState(local,PtcgContent.CARD_RECYCLING_STATION);p.setPosition(net.minecraft.util.math.Vec3d.ofCenter(pos));
  var cards=CardItems.cards().stream().filter(k->RecyclingService.rarity(k.card().id(),k.card().rarityZh()).equals("C")&&RecyclingService.keepCount(k.card().id())==8).limit(130).toList();
  for(var card:cards)deposit(PtcgServices.cardBoxes(),p.getUuid(),card.card().id(),9);
  RecyclingNetworking.open(p,pos);var sf=RecyclingNetworking.class.getDeclaredField("SESSIONS");sf.setAccessible(true);
  var session=((Map<?,?>)sf.get(null)).get(p.getUuid());String token=(String)field(session,"id");
  intent(p,token,(int)field(session,"revision"),"SOURCE","COLLECTION");
  intent(p,token,(int)field(session,"revision"),"PREVIEW_ALL","");var q=(RecyclingService.Quote)field(session,"quote");
  c.assertTrue(q.rows().size()==130&&q.count()==130&&q.coins()==130,"一键回收只包含当前已加载的一批");
  intent(p,token,(int)field(session,"revision"),"LOAD","120");
  c.assertTrue(q.equals(field(session,"quote")),"连续加载改变回收报价");
  intent(p,token,(int)field(session,"revision"),"CONFIRM",q.token().toString());
  c.assertTrue(PtcgServices.cardBoxes().unboxedForOwner(p.getUuid()).size()==1040&&PtcgServices.economy().wallet().balance(p.getUuid())==130,"批量回收未按全部卡种保留八张");
  intent(p,token,(int)field(session,"revision"),"CLOSE","");p.networkHandler.disconnect(net.minecraft.text.Text.literal("测试完成"));c.complete();
 }
 @GameTest(templateName=EMPTY_STRUCTURE)
 public void stationAccessAndPacketRoundTrip(TestContext c){
  var p=c.createMockPlayer(GameMode.SURVIVAL);var local=new BlockPos(1,1,1);var pos=c.getAbsolutePos(local);c.setBlockState(local,PtcgContent.CARD_RECYCLING_STATION);
  p.setPosition(net.minecraft.util.math.Vec3d.ofCenter(pos));c.assertTrue(RecyclingNetworking.accessible(p,c.getWorld(),pos),"近距离不能访问回收站");
  p.setPosition(net.minecraft.util.math.Vec3d.ofCenter(pos).add(20,0,0));c.assertTrue(!RecyclingNetworking.accessible(p,c.getWorld(),pos),"远距离仍可访问回收站");
  var buf=new net.minecraft.network.RegistryByteBuf(io.netty.buffer.Unpooled.buffer(),c.getWorld().getRegistryManager());
  var packet=new RecycleIntent(UUID.randomUUID().toString(),7,"PREVIEW_SELECTED","[\"test\"]");RecycleIntent.CODEC.encode(buf,packet);
  c.assertTrue(packet.equals(RecycleIntent.CODEC.decode(buf)),"回收请求编码不一致");buf.release();c.complete();
 }
}
