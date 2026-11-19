package dev.hua.ptcglive.network;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.List;

public record BattleSocialState(boolean open,String json,String message) implements CustomPayload {
    public record Player(String id,String name,boolean busy) {}
    public record Invite(String id,String sender,String senderName,String recipient,String recipientName,String rule,String location,boolean outgoing) {}
    public record View(List<Player> players,List<Invite> invitations) {}
    public static final Id<BattleSocialState> ID=new Id<>(Identifier.of("ptcglive","battle_social_state"));
    public static final PacketCodec<RegistryByteBuf,BattleSocialState> CODEC=PacketCodec.tuple(
        PacketCodecs.BOOL,BattleSocialState::open,PacketCodecs.string(250000),BattleSocialState::json,
        PacketCodecs.string(1024),BattleSocialState::message,BattleSocialState::new);
    @Override public Id<? extends CustomPayload> getId(){return ID;}
}
