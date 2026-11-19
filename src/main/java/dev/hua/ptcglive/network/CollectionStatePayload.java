package dev.hua.ptcglive.network;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
public record CollectionStatePayload(boolean openScreen,String json,String message) implements CustomPayload {
    public static final Id<CollectionStatePayload> ID=new Id<>(Identifier.of("ptcglive","collection_state"));
    public static final PacketCodec<RegistryByteBuf,CollectionStatePayload> CODEC=PacketCodec.tuple(
        PacketCodecs.BOOL,CollectionStatePayload::openScreen,PacketCodecs.string(200000),CollectionStatePayload::json,
        PacketCodecs.string(1024),CollectionStatePayload::message,CollectionStatePayload::new);
    @Override public Id<? extends CustomPayload> getId(){return ID;}
}
