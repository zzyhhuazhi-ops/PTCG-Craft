package dev.hua.ptcglive.spectator;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
public record ScreenIntent(BlockPos pos,String action,String argument) implements CustomPayload{
    public static final Id<ScreenIntent> ID=new Id<>(Identifier.of("ptcglive","screen_intent"));
    public static final PacketCodec<RegistryByteBuf,ScreenIntent> CODEC=PacketCodec.tuple(BlockPos.PACKET_CODEC,ScreenIntent::pos,
        PacketCodecs.string(16),ScreenIntent::action,PacketCodecs.string(40),ScreenIntent::argument,ScreenIntent::new);
    @Override public Id<? extends CustomPayload> getId(){return ID;}
}
