package dev.hua.ptcglive.duel;

import dev.hua.ptcglive.PtcgServices;
import dev.hua.ptcglive.network.PtcgNetworking;
import dev.hua.ptcglive.network.MatchNetworking;
import dev.hua.ptcglive.table.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.text.Text;
import java.util.*;

/** Server-only ephemeral seats; no world block, item copies or alternate battle engine. */
public final class DuelLetters {
    public static final DuelLetterItem ITEM=new DuelLetterItem();
    public static final EntityType<DuelLetterEntity> TYPE=EntityType.Builder.<DuelLetterEntity>create(DuelLetterEntity::new,SpawnGroup.MISC)
        .dimensions(0.25f,0.12f).maxTrackingRange(64).trackingTickInterval(1).disableSaving().build("ptcglive:duel_letter");
    private record Invitation(CardTableKey key,UUID sender,UUID target){boolean contains(UUID id){return sender.equals(id)||target.equals(id);}}
    private static final Map<CardTableKey,Invitation> ACTIVE=new HashMap<>();
    private static int next;
    private DuelLetters(){}
    public static void initialize(){
        Registry.register(Registries.ITEM,Identifier.of("ptcglive","duel_letter"),ITEM);
        Registry.register(Registries.ENTITY_TYPE,Identifier.of("ptcglive","duel_letter"),TYPE);
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->closeFor(server,handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{for(var key:List.copyOf(ACTIVE.keySet()))close(server,key);});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{ACTIVE.clear();next=0;});
        ServerTickEvents.END_SERVER_TICK.register(server->{if(server.getTicks()%20!=0)return;
            for(var i:List.copyOf(ACTIVE.values())){
                var a=server.getPlayerManager().getPlayer(i.sender());var b=server.getPlayerManager().getPlayer(i.target());
                var lobby=PtcgServices.tables().table(i.key()).orElse(null);
                if(a==null||b==null||!a.isAlive()||!b.isAlive()||a.getServerWorld()!=b.getServerWorld()
                    ||!a.getServerWorld().getRegistryKey().getValue().toString().equals(i.key().dimensionId())
                    ||lobby==null||lobby.status()==TableLobbyStatus.ENDED||lobby.guest().isEmpty())close(server,i.key());
            }
        });
    }
    public static boolean exists(CardTableKey key){return ACTIVE.containsKey(key);}
    public static boolean accessible(ServerPlayerEntity player,CardTableKey key){
        var i=ACTIVE.get(key);return i!=null&&i.contains(player.getUuid())&&player.isAlive()&&!player.isSpectator()
            &&player.getServerWorld().getRegistryKey().getValue().toString().equals(key.dimensionId());
    }
    public static boolean virtualPosition(BlockPos pos){return pos.getY()==-2048;}
    public static boolean resume(ServerPlayerEntity player){
        var found=ACTIVE.values().stream().filter(i->i.contains(player.getUuid())).findFirst();
        if(found.isEmpty())return false;
        var i=found.get();var lobby=PtcgServices.tables().table(i.key()).orElse(null);
        if(!accessible(player,i.key())||lobby==null||lobby.status()==TableLobbyStatus.ENDED){close(player.getServer(),i.key());return false;}
        if(lobby.match().isPresent())MatchNetworking.open(player,lobby.match().orElseThrow().id());else open(player,i.key());
        return true;
    }
    public static void hit(ServerPlayerEntity sender,ServerPlayerEntity target){
        var tables=PtcgServices.tables();
        if(sender==target||!sender.isAlive()||!target.isAlive()||target.isSpectator()||sender.getServerWorld()!=target.getServerWorld())return;
        if(tables.busy(sender.getUuid())||tables.busy(target.getUuid())||ACTIVE.values().stream().anyMatch(i->i.contains(sender.getUuid())||i.contains(target.getUuid()))){
            sender.sendMessage(Text.literal("对方或你已有对局／待准备的战书。"),true);return;
        }
        var key=new CardTableKey(sender.getServerWorld().getRegistryKey().getValue().toString(),new BlockPos(++next,-2048,0).asLong());
        var i=new Invitation(key,sender.getUuid(),target.getUuid());ACTIVE.put(key,i);
        try{
            tables.openTemporary(key,sender.getUuid(),target.getUuid(),"standard-international-2026");
            dev.hua.ptcglive.network.BattleSocialNetworking.bind(sender.getServer());
            open(sender,key);open(target,key);
        }catch(RuntimeException error){
            close(sender.getServer(),key);
            org.slf4j.LoggerFactory.getLogger("ptcglive").error("Cannot open duel-letter lobby",error);
            sender.sendMessage(Text.literal("战书邀战失败，临时状态已清理，请重试。"),false);
        }
    }
    private static void open(ServerPlayerEntity player,CardTableKey key){
        player.closeHandledScreen();
        PtcgNetworking.sendLobby(player,BlockPos.fromLong(key.packedBlockPos()),key,true,"战书邀战：请选择卡组并准备；双方准备后才会开战。");
    }
    public static void closeFor(MinecraftServer server,UUID id){
        ACTIVE.values().stream().filter(i->i.contains(id)).map(Invitation::key).toList().forEach(k->close(server,k));
    }
    public static void close(MinecraftServer server,CardTableKey key){
        var i=ACTIVE.remove(key);if(i==null)return;
        PtcgServices.tables().removeTemporaryTable(key);
        for(var id:List.of(i.sender(),i.target())){var p=server.getPlayerManager().getPlayer(id);if(p!=null)p.sendMessage(Text.literal("临时卡桌已关闭；卡盒锁定已释放。"),false);}
    }
}
