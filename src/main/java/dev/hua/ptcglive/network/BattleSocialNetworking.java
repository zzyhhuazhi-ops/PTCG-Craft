package dev.hua.ptcglive.network;

import com.google.gson.Gson;
import dev.hua.ptcglive.*;
import dev.hua.ptcglive.table.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.text.Text;
import java.util.*;

public final class BattleSocialNetworking {
    private static final Gson JSON=new Gson();
    private BattleSocialNetworking() {}
    public static void initialize() {
        PayloadTypeRegistry.playC2S().register(BattleSocialIntent.ID,BattleSocialIntent.CODEC);
        PayloadTypeRegistry.playS2C().register(BattleSocialState.ID,BattleSocialState.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(BattleSocialIntent.ID,(p,c)->handle(c.player(),p));
        ServerLifecycleEvents.SERVER_STARTED.register(BattleSocialNetworking::bind);
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(server.getTicks()%20!=0)return;
            for(var i:PtcgServices.tables().expireInvitations()) {
                notify(server,i.sender(),"对战邀请已过期或因玩家、卡桌、卡盒状态变化失效。");
                notify(server,i.recipient(),"对战邀请已失效。");
            }
        });
    }
    public static void bind(MinecraftServer server) {
        PtcgServices.tables().socialPresence(id->server.getPlayerManager().getPlayer(id)!=null,
            key->{if(dev.hua.ptcglive.duel.DuelLetters.exists(key))return true;var w=world(server,key);var pos=BlockPos.fromLong(key.packedBlockPos());return w!=null&&w.isChunkLoaded(pos)&&w.getBlockState(pos).isOf(PtcgContent.CARD_TABLE);},
            (id,key)->{var p=server.getPlayerManager().getPlayer(id);return p!=null&&(dev.hua.ptcglive.duel.DuelLetters.exists(key)
                ?dev.hua.ptcglive.duel.DuelLetters.accessible(p,key):p.getServerWorld()==world(server,key)&&p.squaredDistanceTo(Vec3d.ofCenter(BlockPos.fromLong(key.packedBlockPos())))<=64);});
    }
    private static ServerWorld world(MinecraftServer server,CardTableKey key) {
        for(var w:server.getWorlds())if(w.getRegistryKey().getValue().toString().equals(key.dimensionId()))return w;
        return null;
    }
    private static void handle(ServerPlayerEntity player,BattleSocialIntent payload) {
        if(payload.action().equals("OPEN")&&dev.hua.ptcglive.duel.DuelLetters.resume(player))return;
        var server=player.getServer();bind(server);var tables=PtcgServices.tables();UUID id=player.getUuid();
        try {
            String message;
            switch(payload.action()) {
                case "OPEN","REFRESH" -> message="邀请有效期 60 秒；接受后请到卡桌选择卡盒并准备。";
                case "INVITE" -> {
                    var i=tables.invite(new CardTableKey(payload.dimension(),payload.table().asLong()),id,UUID.fromString(payload.argument()));
                    notify(server,i.recipient(),player.getName().getString()+" 邀请你对战，请打开手机的对战邀请。");message="邀请已发送；不会锁定卡盒。";
                }
                case "ACCEPT","REJECT" -> {
                    boolean accept=payload.action().equals("ACCEPT");var i=tables.answerInvitation(id,UUID.fromString(payload.argument()),accept);
                    notify(server,i.sender(),player.getName().getString()+(accept?" 已接受约战，请双方到卡桌选择卡盒并准备。":" 拒绝了邀请。"));
                    message=accept?"已接受；请到 "+location(i.table())+"，右键卡桌选择自己的卡盒并准备。":"已拒绝邀请。";
                }
                case "CANCEL" -> {
                    UUID token=UUID.fromString(payload.argument());
                    var outgoing=tables.invitationsFor(id).stream().filter(i->i.id().equals(token)).findFirst();
                    tables.cancelInvitation(id,token);
                    outgoing.ifPresent(i->notify(server,i.recipient(),"发起者已取消邀请。"));message="邀请已取消。";
                }
                case "REMATCH" -> {
                    var match=tables.requestRematch(UUID.fromString(payload.argument()),id);
                    if(match.isPresent()) {
                        for(var p:match.get().clientView(null).players()) {var online=server.getPlayerManager().getPlayer(p.id());if(online!=null)MatchNetworking.open(online,match.get().id());}
                        return;
                    }
                    message="已同意同卡组再战，等待对手同意；可点离开取消。";
                }
                case "CHANGE" -> {
                    UUID matchId=UUID.fromString(payload.argument());var key=tables.changeRematchDeck(matchId,id);
                    notifyOpponent(server,matchId,id,"对手选择更换卡组，请重新右键卡桌选卡盒并准备。");
                    PtcgNetworking.sendLobby(player,BlockPos.fromLong(key.packedBlockPos()),key,true,"请重新选卡盒；双方重新准备后才开局。");return;
                }
                case "LEAVE" -> {
                    UUID matchId=UUID.fromString(payload.argument());tables.cancelRematch(matchId,id);
                    notifyOpponent(server,matchId,id,"对手已取消再战等待。");message="已取消再战等待。";
                }
                default -> throw new IllegalArgumentException("未知的邀战操作");
            }
            send(player,payload.action().equals("OPEN"),message);
        } catch(IllegalArgumentException|IllegalStateException e){send(player,false,e.getMessage()==null?"请求失败，请刷新。":e.getMessage());}
    }
    private static void notifyOpponent(MinecraftServer server,UUID matchId,UUID player,String message) {
        for(var p:PtcgServices.tables().session(matchId).view(player,player).game().players())
            if(!p.id().equals(player))notify(server,p.id(),message);
    }
    private static String name(MinecraftServer server,UUID id){var p=server.getPlayerManager().getPlayer(id);return p==null?"已离线玩家":p.getName().getString();}
    private static String location(CardTableKey key){var p=BlockPos.fromLong(key.packedBlockPos());return key.dimensionId()+" "+p.getX()+", "+p.getY()+", "+p.getZ();}
    private static void notify(MinecraftServer server,UUID id,String message){var p=server.getPlayerManager().getPlayer(id);if(p!=null){p.sendMessage(Text.literal(message),false);send(p,false,message);}}
    private static void send(ServerPlayerEntity player,boolean open,String message) {
        var server=player.getServer();var tables=PtcgServices.tables();
        var players=server.getPlayerManager().getPlayerList().stream().filter(p->!p.getUuid().equals(player.getUuid()))
            .map(p->new BattleSocialState.Player(p.getUuidAsString(),p.getName().getString(),tables.busy(p.getUuid()))).toList();
        var invites=tables.invitationsFor(player.getUuid()).stream().map(i->new BattleSocialState.Invite(i.id().toString(),i.sender().toString(),name(server,i.sender()),
            i.recipient().toString(),name(server,i.recipient()),PtcgServices.rulesets().get(i.rulesetId()).displayNameZh(),location(i.table()),i.sender().equals(player.getUuid()))).toList();
        ServerPlayNetworking.send(player,new BattleSocialState(open,JSON.toJson(new BattleSocialState.View(players,invites)),message));
    }
}
