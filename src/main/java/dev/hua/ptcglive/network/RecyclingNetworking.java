package dev.hua.ptcglive.network;

import com.google.gson.*;
import dev.hua.ptcglive.*;
import dev.hua.ptcglive.economy.*;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.entity.player.PlayerEntity;

/** Owner + location + revision bound station sessions; confirmation consumes a server-generated quote once. */
public final class RecyclingNetworking {
 private static final Gson JSON=new Gson();
 private static final Map<UUID,Session> SESSIONS=new HashMap<>();
 private static final int BATCH=120;
 private static final class Session {
  final String id=UUID.randomUUID().toString();final World world;final BlockPos pos;
  int revision,offset;long lastTick;RecyclingService.Source source=RecyclingService.Source.INVENTORY;
  Set<String> rarities=Set.copyOf(RecyclingService.RARITIES);RecyclingService.Quote quote;List<RecyclingService.Row> listed;
  Session(ServerPlayerEntity player,BlockPos pos){world=player.getWorld();this.pos=pos.toImmutable();lastTick=player.getServer().getTicks();}
 }
 public static void initialize() {
  PayloadTypeRegistry.playC2S().register(RecycleIntent.ID,RecycleIntent.CODEC);
  PayloadTypeRegistry.playS2C().register(RecycleState.ID,RecycleState.CODEC);
  ServerPlayNetworking.registerGlobalReceiver(RecycleIntent.ID,(payload,context)->handle(context.player(),payload));
  ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->SESSIONS.remove(handler.player.getUuid()));
  ServerLifecycleEvents.SERVER_STOPPED.register(server->SESSIONS.clear());
 }
 public static boolean accessible(PlayerEntity player,World world,BlockPos pos) {
  return player.isAlive()&&!player.isSpectator()&&player.getWorld()==world&&player.squaredDistanceTo(net.minecraft.util.math.Vec3d.ofCenter(pos))<=64
   &&world.isChunkLoaded(pos)&&world.getBlockState(pos).isOf(PtcgContent.CARD_RECYCLING_STATION);
 }
 public static void open(ServerPlayerEntity player,BlockPos pos) {
  if(!accessible(player,player.getWorld(),pos))return;
  var session=new Session(player,pos);SESSIONS.put(player.getUuid(),session);send(player,session,true,"");
 }
 private static void close(ServerPlayerEntity player,String message) {
  SESSIONS.remove(player.getUuid());ServerPlayNetworking.send(player,new RecycleState(false,"",message));
 }
 private static void handle(ServerPlayerEntity player,RecycleIntent intent) {
  var session=SESSIONS.get(player.getUuid());
  if(session==null||!session.id.equals(intent.session()))return;
  if(intent.action().equals("CLOSE")){SESSIONS.remove(player.getUuid());return;}
  long tick=player.getServer().getTicks();
  if(tick-session.lastTick>1200||!accessible(player,session.world,session.pos)){close(player,"回收站会话已失效，请靠近方块重新打开");return;}
  if(session.revision!=intent.revision()){send(player,session,false,"旧请求未执行，请使用当前预览");return;}
  session.revision++;session.lastTick=tick;String message="";
  try {
   switch(intent.action()) {
    case "SOURCE"->{session.source=RecyclingService.Source.valueOf(intent.argument());session.quote=null;session.listed=null;session.offset=0;}
    case "RARITIES"->{
     var next=new LinkedHashSet<>(Arrays.asList(intent.argument().split(",")));next.remove("");
     if(!new HashSet<>(RecyclingService.RARITIES).containsAll(next))throw new IllegalArgumentException("罕度无效");
     session.rarities=Set.copyOf(next);session.quote=null;session.listed=null;session.offset=0;
    }
    case "LOAD"->{int offset=Integer.parseInt(intent.argument());if(offset<0||offset>20000)throw new IllegalArgumentException("列表位置无效");session.offset=offset;}
    case "REFRESH","CANCEL"->{session.quote=null;session.listed=null;session.offset=0;}
    case "PREVIEW_ALL","PREVIEW_SELECTED"->{
     Set<String> ids=null;
     if(intent.action().equals("PREVIEW_SELECTED")) {
      var array=JsonParser.parseString(intent.argument()).getAsJsonArray();
      if(array.size()>500)throw new IllegalArgumentException("单独选择最多 500 种卡；更多请按罕度一键回收");
      ids=new HashSet<>();for(var value:array)if(!ids.add(value.getAsString()))throw new IllegalArgumentException("重复选择");
     }
     session.quote=RecyclingService.quote(player,PtcgServices.cardBoxes(),session.source,session.rarities,ids);session.offset=0;
    }
    case "CONFIRM"->{
     var quote=session.quote;session.quote=null;session.listed=null;session.offset=0;
     if(quote==null||!quote.token().toString().equals(intent.argument()))throw new IllegalStateException("请先生成回收预览，再二次确认");
     RecyclingService.confirm(player,PtcgServices.cardBoxes(),PtcgServices.economy().wallet(),quote);
     message="已回收 "+quote.count()+" 张，获得 "+quote.coins()+" 宝可币";
    }
    default->throw new IllegalArgumentException("回收操作无效");
   }
  }catch(RuntimeException ex){session.quote=null;session.listed=null;session.offset=0;message=ex.getMessage()==null?"回收未完成，请刷新检查":ex.getMessage();}
  send(player,session,false,message);
 }
 private static void send(ServerPlayerEntity player,Session session,boolean open,String message) {
  if(session.quote==null&&session.listed==null)session.listed=RecyclingService.rows(player,PtcgServices.cardBoxes(),session.source,session.rarities);
  var rows=session.quote==null?session.listed:session.quote.rows();
  session.offset=Math.min(session.offset,Math.max(0,rows.size()-1));
  long coins=0;int count=0;for(var row:rows){coins=Math.addExact(coins,(long)row.amount()*row.unitValue());count=Math.addExact(count,row.amount());}
  var view=new RecyclingView(session.id,session.revision,session.source,session.rarities,
   List.copyOf(rows.subList(session.offset,Math.min(rows.size(),session.offset+BATCH))),session.offset,rows.size(),
   PtcgServices.economy().wallet().balance(player.getUuid()),count,coins,session.quote==null?"":session.quote.token().toString());
  ServerPlayNetworking.send(player,new RecycleState(open,JSON.toJson(view),message));
 }
}
