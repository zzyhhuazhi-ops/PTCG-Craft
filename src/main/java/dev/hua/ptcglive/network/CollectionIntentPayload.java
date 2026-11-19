package dev.hua.ptcglive.network;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
public record CollectionIntentPayload(String sessionId,int revision,String action,String argument,int amount,String query) implements CustomPayload {
    public static final Id<CollectionIntentPayload> ID=new Id<>(Identifier.of("ptcglive","collection_intent"));
    public static final PacketCodec<RegistryByteBuf,CollectionIntentPayload> CODEC=PacketCodec.tuple(
        PacketCodecs.string(36),CollectionIntentPayload::sessionId,PacketCodecs.VAR_INT,CollectionIntentPayload::revision,
        PacketCodecs.string(20),CollectionIntentPayload::action,PacketCodecs.string(8192),CollectionIntentPayload::argument,
        PacketCodecs.VAR_INT,CollectionIntentPayload::amount,PacketCodecs.string(80),CollectionIntentPayload::query,CollectionIntentPayload::new);
    @Override public Id<? extends CustomPayload> getId(){return ID;}
}
