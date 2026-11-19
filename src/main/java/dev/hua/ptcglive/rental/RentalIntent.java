package dev.hua.ptcglive.rental;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
public record RentalIntent(String session,int revision,String action,int slot) implements CustomPayload {
    public static final Id<RentalIntent> ID=new Id<>(Identifier.of("ptcglive","rental_intent"));
    public static final PacketCodec<RegistryByteBuf,RentalIntent> CODEC=PacketCodec.tuple(
        PacketCodecs.string(64),RentalIntent::session,PacketCodecs.INTEGER,RentalIntent::revision,
        PacketCodecs.string(16),RentalIntent::action,PacketCodecs.INTEGER,RentalIntent::slot,RentalIntent::new);
    @Override public Id<? extends CustomPayload> getId(){return ID;}
}
