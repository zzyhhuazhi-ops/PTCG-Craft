package dev.hua.ptcglive.gametest;

import dev.hua.ptcglive.*;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.table.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.spectator.*;
import dev.hua.ptcglive.persistence.PtcgPersistentState;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.*;
import net.minecraft.nbt.*;
import net.minecraft.util.math.*;
import java.util.*;

public final class HistoryChatIntegrationTest implements FabricGameTest {
    private static final String RULE="standard-international-2026";
    private static DeckBox box(CardBoxService cards,UUID owner){
        var box=cards.create(owner,"履历测试",RULE);
        for(int i=0;i<60;i++)cards.put(owner,box.id(),cards.mint(owner,i<4?"fixture-basic-h":"fixture-energy-h").instanceId());
        return box;
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void historyAndBlockingSurviveNbt(TestContext c){
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();var raw=new NbtCompound();raw.putInt("format",8);var rows=new NbtList();var row=new NbtCompound();
        row.putUuid("owner",a);row.putString("definition","fixture-basic-h");row.putLong("first",123456);row.putLong("matches",7);row.putLong("wins",3);row.putLong("knockouts",9);rows.add(row);raw.put("cardHistories",rows);
        var lookup=c.getWorld().getRegistryManager();var state=PtcgPersistentState.TYPE.deserializer().apply(raw,lookup);
        var saved=state.writeNbt(new NbtCompound(),lookup);var reload=PtcgPersistentState.TYPE.deserializer().apply(saved,lookup);
        var cards=CardBoxService.restore(java.time.Clock.systemUTC(),reload.cardSnapshot());var h=cards.history(a,"fixture-basic-h");
        c.assertTrue(h.firstAcquired()==123456&&h.matches()==7&&h.wins()==3&&h.knockouts()==9&&cards.history(b,"fixture-basic-h")==null,"履历存档丢失或串玩家");
        c.assertTrue(h.legacyResultsUnknown()&&h.completedMatches()==0&&h.winRate().equals("—"),"旧存档虚构完赛记录");
        var stored=saved.getList("cardHistories",NbtElement.COMPOUND_TYPE).getCompound(0);
        stored.putLong("completedMatches",3);stored.putLong("completedWins",2);
        var current=PtcgPersistentState.TYPE.deserializer().apply(saved,lookup);
        var roundtrip=PtcgPersistentState.TYPE.deserializer().apply(current.writeNbt(new NbtCompound(),lookup),lookup).cardSnapshot().histories().getFirst();
        c.assertTrue(roundtrip.completedMatches()==3&&roundtrip.completedWins()==2&&roundtrip.winRate().equals("66.7%"),"完赛计数 NBT 往返失败");
        var prefs=new ScreenChat.Preferences();prefs.block(a,b,"发送者");prefs.toggle(b);
        var restored=ScreenChat.Preferences.read(prefs.writeNbt(new NbtCompound(),lookup),lookup);
        c.assertTrue(!restored.allows(a,b)&&restored.allows(a,a)&&!restored.allows(b,a),"屏蔽／总开关存档未恢复");
        restored.unblock(a,b);restored.toggle(b);c.assertTrue(restored.allows(a,b)&&restored.allows(b,a),"解除屏蔽未生效");
        for(String invalid:List.of("", "字".repeat(41),"x\ny","§c红字","x\u202Ey"))try{ScreenChat.sanitize(invalid);throw new AssertionError("非法弹幕通过");}catch(IllegalArgumentException expected){}
        c.assertTrue(ScreenChat.sanitize(" 正常弹幕 ").equals("正常弹幕"),"合法中文弹幕拒绝");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void matchCountsOnceAndAiNeverMintsHistory(TestContext c){
        var cards=new CardBoxService(java.time.Clock.systemUTC());var a=UUID.randomUUID();var b=UUID.randomUUID();var one=box(cards,a);var two=box(cards,b);
        var tables=new CardTableManager(PtcgServices.rulesets(),cards,PtcgServices.cardDatabase(),BundledCombatData.load(),()->new ServerRandom(18));
        var key=new CardTableKey("minecraft:overworld",0);tables.open(key,a,RULE);tables.join(key,b);tables.selectBox(key,a,one.id());tables.selectBox(key,b,two.id());tables.setReady(key,a,true);tables.setReady(key,b,true);
        var match=tables.start(key,a);
        c.assertTrue(cards.history(a,"fixture-basic-h").matches()==1&&cards.history(a,"fixture-energy-h").matches()==1,"按实体张数重复累计场次");
        var view=tables.matchView(match.id(),b,b);int surrender=view.options().stream().filter(o->o.enabled()&&o.labelZh().contains("认输")).findFirst().orElseThrow().index();
        tables.submit(match.id(),b,b,view.revision(),surrender);tables.endMatch(match.id());
        c.assertTrue(cards.history(a,"fixture-basic-h").wins()==1&&cards.history(b,"fixture-basic-h").wins()==0,"胜负／清理重复记胜");
        c.assertTrue(cards.history(a,"fixture-basic-h").completedMatches()==1&&cards.history(b,"fixture-basic-h").completedMatches()==1,"完赛计数未同时记录双方或重复记录");
        var aiKey=new CardTableKey("minecraft:overworld",2);tables.open(aiKey,a,RULE);var report=tables.checkComputer(aiKey,a,one.id(),one.id(),RULE);
        c.assertTrue(report.allowed(),"镜像AI测试构筑不支持");int cardCount=cards.snapshot().cards().size();var ai=tables.startComputer(aiKey,a,one.id(),one.id(),RULE,report.token());
        var bot=tables.session(ai).match().historyDecks().keySet().stream().filter(id->!id.equals(a)).findFirst().orElseThrow();
        c.assertTrue(cards.history(bot,"fixture-basic-h")==null&&cards.history(a,"fixture-basic-h").matches()==2&&cards.snapshot().cards().size()==cardCount,"AI临时卡污染履历或收藏");
        tables.endMatch(ai);c.assertTrue(cards.history(a,"fixture-basic-h").wins()==1&&cards.history(a,"fixture-basic-h").completedMatches()==1&&cards.history(a,"fixture-basic-h").winRate().equals("100.0%")&&!cards.isLocked(a,one.id()),"异常终止计入胜率或未解锁");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nearbyChatRejectsSpamAndRemoteRequests(TestContext c)throws Exception{
        var p=player(c,"chat-a");var q=player(c,"chat-b");var origin=c.getAbsolutePos(new BlockPos(130,1,130));var world=c.getWorld();world.getChunk(origin);
        var tablePos=origin.add(0,0,3);world.setBlockState(tablePos,PtcgContent.CARD_TABLE.getDefaultState());p.setPosition(Vec3d.ofCenter(tablePos));q.setPosition(Vec3d.ofCenter(tablePos));
        var cards=PtcgServices.cardBoxes();var a=box(cards,p.getUuid());var b=box(cards,q.getUuid());p.getInventory().insertStack(CardBoxItem.referenceTo(a));q.getInventory().insertStack(CardBoxItem.referenceTo(b));
        var tables=PtcgServices.tables();var key=new CardTableKey(world.getRegistryKey().getValue().toString(),tablePos.asLong());
        tables.open(key,p.getUuid(),RULE);tables.join(key,q.getUuid());tables.selectBox(key,p.getUuid(),a.id());tables.selectBox(key,q.getUuid(),b.id());tables.setReady(key,p.getUuid(),true);tables.setReady(key,q.getUuid(),true);var match=tables.start(key,p.getUuid());
        for(int y=0;y<2;y++)for(int x=0;x<3;x++){var pos=ScreenStructure.cell(origin,Direction.NORTH,x,y);world.getChunk(pos);world.setBlockState(pos,SpectatorScreens.BLOCK.getDefaultState());ScreenStructures.add((SpectatorScreenEntity)world.getBlockEntity(pos));}
        for(int i=0;i<50;i++)ScreenStructures.tick();var root=(SpectatorScreenEntity)world.getBlockEntity(origin);root.match=match.id();
        var field=ScreenChat.class.getDeclaredField("messages");field.setAccessible(true);var messages=(Map<?,ArrayDeque<ScreenChat.Message>>)field.get(null);String unique=UUID.randomUUID().toString();
        ScreenChat.handle(p,origin,root,"chat",unique);ScreenChat.handle(p,origin,root,"chat",unique);ScreenChat.handle(q,origin,root,"chat",unique);
        long count=messages.values().stream().flatMap(Collection::stream).filter(m->m.text().equals(unique)).count();c.assertTrue(count==1,"重复提交或屏幕限速失效");
        var sent=messages.values().stream().flatMap(Collection::stream).filter(m->m.text().equals(unique)).findFirst().orElseThrow();c.assertTrue(sent.sender().equals(p.getUuid())&&sent.match().equals(match.id()),"发送者或对局绑定错误");
        var far=origin.add(9,0,0);world.getChunk(far);q.setPosition(Vec3d.ofCenter(far));ScreenChat.handle(q,origin,root,"chat","remote");
        c.assertTrue(messages.values().stream().flatMap(Collection::stream).noneMatch(m->m.sender().equals(q.getUuid())),"远程发弹幕未拦截");
        var playerLimits=ScreenChat.class.getDeclaredField("last");playerLimits.setAccessible(true);((Map<?,?>)playerLimits.get(null)).clear();
        var screenLimits=ScreenChat.class.getDeclaredField("screenLast");screenLimits.setAccessible(true);((Map<?,?>)screenLimits.get(null)).clear();
        ScreenChat.handle(p,origin,root,"chat","second");c.assertTrue(messages.values().stream().flatMap(Collection::stream).filter(m->m.sender().equals(p.getUuid())).count()==2,"冷却结束后不能再次发送");
        tables.endMatch(match.id());ScreenChat.handle(p,origin,root,"chat","ended");c.assertTrue(messages.values().stream().flatMap(Collection::stream).noneMatch(m->m.text().equals("ended")),"结束对局仍可发送");
        for(int y=0;y<2;y++)for(int x=0;x<3;x++)world.removeBlock(ScreenStructure.cell(origin,Direction.NORTH,x,y),false);world.removeBlock(tablePos,false);
        p.networkHandler.disconnect(net.minecraft.text.Text.literal("测试完成"));q.networkHandler.disconnect(net.minecraft.text.Text.literal("测试完成"));c.complete();
    }
    private static net.minecraft.server.network.ServerPlayerEntity player(TestContext c,String name){
        var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),name);var data=net.minecraft.server.network.ConnectedClientData.createDefault(profile,false);
        var p=new net.minecraft.server.network.ServerPlayerEntity(c.getWorld().getServer(),c.getWorld(),profile,data.syncedOptions());
        var connection=new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND);new io.netty.channel.embedded.EmbeddedChannel(connection);
        c.getWorld().getServer().getPlayerManager().onPlayerConnect(connection,p,data);return p;
    }
}
