package dev.hua.ptcglive.spectator;

import com.google.gson.Gson;
import dev.hua.ptcglive.PtcgServices;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import java.util.*;

public final class ScreenNetworking {
    public record Broadcast(ScreenMatch match,Map<UUID,String> names){}
    public record Choice(UUID id,String label,int turn,String phase){}
    public record Menu(int width,int height,boolean valid,UUID connected,boolean names,boolean animations,boolean damage,
                       List<Choice> matches,Broadcast current,String message){}
    private record Cached(long revision,Broadcast view,String json){}
    private static final Gson JSON=new Gson();
    private static final Map<UUID,Cached> cache=new HashMap<>();
    private static final Map<UUID,Map<UUID,Long>> delivered=new HashMap<>();
    private static final Map<UUID,Integer> requests=new HashMap<>();
    public static void initialize(){
        ScreenChat.initialize();
        PayloadTypeRegistry.playC2S().register(ScreenIntent.ID,ScreenIntent.CODEC);
        PayloadTypeRegistry.playS2C().register(ScreenState.ID,ScreenState.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ScreenIntent.ID,(payload,ctx)->handle(ctx.player(),payload));
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{delivered.remove(handler.player.getUuid());requests.remove(handler.player.getUuid());});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{cache.clear();delivered.clear();requests.clear();});
        ServerTickEvents.END_SERVER_TICK.register(ScreenNetworking::tick);
    }
    private static boolean throttle(ServerPlayerEntity player){
        int now=player.getServer().getTicks();Integer last=requests.put(player.getUuid(),now);return last!=null&&now-last<3;
    }
    private static SpectatorScreenEntity accessible(ServerPlayerEntity player,BlockPos pos){
        if(player.squaredDistanceTo(Vec3d.ofCenter(pos))>64||!player.getServerWorld().isChunkLoaded(pos))return null;
        return ScreenStructures.root(player.getServerWorld(),pos);
    }
    public static boolean canManage(ServerPlayerEntity player,BlockPos clicked,SpectatorScreenEntity root){
        if(player.isSpectator()||!player.getWorld().canPlayerModifyAt(player,clicked))return false;
        var face=root.getCachedState().get(SpectatorScreenBlock.FACING);
        for(int y=0;y<root.height;y++)for(int x=0;x<root.width;x++)
            if(!player.getWorld().canPlayerModifyAt(player,ScreenStructure.cell(root.getPos(),face,x,y)))return false;
        return true;
    }
    public static int connectNearby(ServerPlayerEntity player,BlockPos table){
        var key=new dev.hua.ptcglive.table.CardTableKey(player.getServerWorld().getRegistryKey().getValue().toString(),table.asLong());
        var lobby=PtcgServices.tables().table(key).orElseThrow(()->new IllegalArgumentException("请先开桌"));
        if(!lobby.isHost(player.getUuid())||lobby.status()!=dev.hua.ptcglive.table.TableLobbyStatus.WAITING)throw new IllegalArgumentException("仅房主可在准备阶段连接屏幕");
        ScreenStructures.tick();int count=0;
        for(var root:ScreenStructures.controllers()){
            if(root.getWorld()!=player.getWorld()||!canManage(player,root.getPos(),root))continue;
            var face=root.getCachedState().get(SpectatorScreenBlock.FACING);boolean near=false;
            for(int y=0;y<root.height&&!near;y++)for(int x=0;x<root.width;x++)if(ScreenStructure.cell(root.getPos(),face,x,y).getSquaredDistance(table)<=900){near=true;break;}
            if(!near)continue;
            root.pendingHost=player.getUuid();root.pendingTable=table.asLong();root.match=null;root.finishedAt=-1;root.sync();count++;
        }return count;
    }
    public static void open(ServerPlayerEntity player,BlockHitResult hit){
        if(throttle(player))return;
        var root=accessible(player,hit.getBlockPos());if(root==null)return;
        var face=root.getCachedState().get(SpectatorScreenBlock.FACING);
        if(root.controller()&&root.match!=null&&hit.getSide()==face&&!player.isSneaking()){
            var data=cached(player.getServer(),root.match);
            if(data!=null){
                var offset=hit.getPos().subtract(Vec3d.ofCenter(root.getPos()));var right=ScreenStructure.right(face);
                double x=offset.x*right.getOffsetX()+offset.z*right.getOffsetZ()+.5,y=root.height-.5-offset.y;
                var fit=ScreenLayout.fit(root.width,root.height);String card=ScreenLayout.hit(data.view.match(),fit.x(x),fit.y(y));
                if(card!=null){ServerPlayNetworking.send(player,new ScreenState("card",hit.getBlockPos(),card));return;}
            }
        }
        menu(player,hit.getBlockPos(),root,"");
    }
    private static void handle(ServerPlayerEntity player,ScreenIntent intent){
        if(throttle(player))return;
        var root=accessible(player,intent.pos());if(root==null)return;
        if(ScreenChat.isAction(intent.action())){ScreenChat.handle(player,intent.pos(),root,intent.action(),intent.argument());return;}
        String message="";
        try{
            if(!intent.action().equals("refresh")){
                if(!canManage(player,intent.pos(),root))throw new IllegalArgumentException("不能修改此屏幕设置");
                switch(intent.action()){
                    case "connect"->{
                        if(!root.controller())throw new IllegalArgumentException("请先拼成完整的 3×2 至 11×7 矩形");
                        UUID id=UUID.fromString(intent.argument());
                        if(!PtcgServices.tables().publicScreenMatches().contains(id))throw new IllegalArgumentException("对局已结束或不可公开观战");
                        root.match=id;root.pendingHost=null;root.finishedAt=-1;
                    }
                    case "disconnect"->{root.match=null;root.pendingHost=null;root.finishedAt=-1;}
                    case "names"->root.names=!root.names;
                    case "animations"->root.animations=!root.animations;
                    case "damage"->root.damage=!root.damage;
                    default->throw new IllegalArgumentException("未知屏幕操作");
                }
                root.sync();
            }
        }catch(IllegalArgumentException ex){message=ex.getMessage();}
        menu(player,intent.pos(),root,message);
    }
    private static Cached cached(MinecraftServer server,UUID id){
        long revision=PtcgServices.tables().screenRevision(id);if(revision<0){cache.remove(id);return null;}
        var old=cache.get(id);if(old!=null&&old.revision==revision)return old;
        var match=PtcgServices.tables().screenView(id);var names=new LinkedHashMap<UUID,String>();
        for(var p:match.players()){
            var online=server.getPlayerManager().getPlayer(p.id());
            names.put(p.id(),online!=null?online.getName().getString():PtcgServices.tables().screenComputerSeat(id,p.id())?"电脑对手":"玩家 "+p.id().toString().substring(0,8));
        }
        var view=new Broadcast(match,Map.copyOf(names));var result=new Cached(revision,view,JSON.toJson(view));cache.put(id,result);return result;
    }
    private static void menu(ServerPlayerEntity player,BlockPos clicked,SpectatorScreenEntity root,String message){
        var choices=new ArrayList<Choice>();
        for(UUID id:PtcgServices.tables().publicScreenMatches()){
            var data=cached(player.getServer(),id);if(data==null)continue;
            choices.add(new Choice(id,String.join(" VS ",data.view.match().players().stream().map(p->data.view.names().get(p.id())).toList()),data.view.match().turn(),data.view.match().phase().name()));
        }
        var current=root.match==null?null:cached(player.getServer(),root.match);
        ServerPlayNetworking.send(player,new ScreenState("menu",clicked,JSON.toJson(new Menu(root.width,root.height,root.valid,root.match,root.names,root.animations,root.damage,
            List.copyOf(choices),current==null?null:current.view,message))));
    }
    private static void tick(MinecraftServer server){
        if(server.getTicks()%5!=0)return;
        var needed=new HashMap<UUID,Set<UUID>>();var used=new HashSet<UUID>();
        for(var root:ScreenStructures.controllers()){
            if(root.pendingHost!=null&&root.getWorld() instanceof ServerWorld pendingWorld){
                var table=BlockPos.fromLong(root.pendingTable);
                var key=new dev.hua.ptcglive.table.CardTableKey(pendingWorld.getRegistryKey().getValue().toString(),root.pendingTable);
                var lobby=PtcgServices.tables().table(key).orElse(null);
                if(lobby==null||!lobby.isHost(root.pendingHost)||pendingWorld.isChunkLoaded(table)&&!pendingWorld.getBlockState(table).isOf(dev.hua.ptcglive.PtcgContent.CARD_TABLE)){
                    root.pendingHost=null;root.sync();
                }else if(lobby.match().isPresent()&&PtcgServices.tables().publicScreenMatches().contains(lobby.match().get().id())){
                    root.match=lobby.match().get().id();root.pendingHost=null;root.finishedAt=-1;root.sync();
                }
            }
            if(root.match==null||!(root.getWorld() instanceof ServerWorld world))continue;
            var data=cached(server,root.match);
            if(data==null){root.match=null;root.finishedAt=-1;root.sync();continue;}
            if(data.view.match().result()!=null){
                if(root.finishedAt<0)root.finishedAt=server.getTicks();
                if(server.getTicks()-root.finishedAt>=160){root.match=null;root.finishedAt=-1;root.sync();continue;}
            }
            used.add(root.match);
            for(var player:PlayerLookup.tracking(root))if(player.getServerWorld()==world&&player.squaredDistanceTo(Vec3d.ofCenter(root.getPos()))<72*72)
                needed.computeIfAbsent(player.getUuid(),p->new HashSet<>()).add(root.match);
        }
        for(var player:server.getPlayerManager().getPlayerList()){
            var subscriptions=needed.getOrDefault(player.getUuid(),Set.of());
            var sent=delivered.computeIfAbsent(player.getUuid(),p->new HashMap<>());
            for(var id:subscriptions){var data=cache.get(id);if(data!=null&&!Objects.equals(sent.get(id),data.revision)){
                ServerPlayNetworking.send(player,new ScreenState("match",BlockPos.ORIGIN,data.json));sent.put(id,data.revision);
            }}
            for(var id:List.copyOf(sent.keySet()))if(!subscriptions.contains(id)){
                ServerPlayNetworking.send(player,new ScreenState("forget",BlockPos.ORIGIN,id.toString()));sent.remove(id);
            }
        }
        cache.keySet().retainAll(used);
    }
    private ScreenNetworking(){}
}
