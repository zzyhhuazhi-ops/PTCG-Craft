package dev.hua.ptcglive.network;

import com.google.gson.Gson;
import dev.hua.ptcglive.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.*;

/** Queries are always for the authenticated player's own per-print history. */
public final class CardHistoryNetworking {
    public record Query(String card) implements CustomPayload {
        public static final Id<Query> ID=new Id<>(Identifier.of("ptcglive","card_history_query"));
        public static final PacketCodec<RegistryByteBuf,Query> CODEC=PacketCodec.tuple(PacketCodecs.string(128),Query::card,Query::new);
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    public record Reply(String card,String json) implements CustomPayload {
        public static final Id<Reply> ID=new Id<>(Identifier.of("ptcglive","card_history_reply"));
        public static final PacketCodec<RegistryByteBuf,Reply> CODEC=PacketCodec.tuple(PacketCodecs.string(128),Reply::card,PacketCodecs.string(2048),Reply::json,Reply::new);
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    private static final Map<UUID,Integer> last=new HashMap<>();
    public static void initialize(){
        PayloadTypeRegistry.playC2S().register(Query.ID,Query.CODEC);PayloadTypeRegistry.playS2C().register(Reply.ID,Reply.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Query.ID,(q,ctx)->{
            var p=ctx.player();int now=p.getServer().getTicks();Integer before=last.get(p.getUuid());if(before!=null&&now-before<5)return;
            last.put(p.getUuid(),now);try{CardItems.byDefinitionId(q.card());}catch(IllegalArgumentException missing){return;}
            ServerPlayNetworking.send(p,new Reply(q.card(),new Gson().toJson(PtcgServices.cardBoxes().history(p.getUuid(),q.card()))));
        });
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->{
            var p=handler.player;for(int slot=0;slot<p.getInventory().size();slot++)if(p.getInventory().getStack(slot).getItem() instanceof CardItem card)
                PtcgServices.cardBoxes().acquired(p.getUuid(),card.card().id(),true);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->last.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server->last.clear());
    }
    private CardHistoryNetworking(){}
}
