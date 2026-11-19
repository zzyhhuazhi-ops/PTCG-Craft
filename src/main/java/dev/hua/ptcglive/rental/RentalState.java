package dev.hua.ptcglive.rental;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
public record RentalState(boolean open,String json,String message) implements CustomPayload {
    public static final Id<RentalState> ID=new Id<>(Identifier.of("ptcglive","rental_state"));
    public static final PacketCodec<RegistryByteBuf,RentalState> CODEC=PacketCodec.tuple(
        PacketCodecs.BOOL,RentalState::open,PacketCodecs.string(50000),RentalState::json,
        PacketCodecs.string(1024),RentalState::message,RentalState::new);
    @Override public Id<? extends CustomPayload> getId(){return ID;}
}
