package dev.hua.ptcglive.spectator;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
public record ScreenState(String kind,BlockPos pos,String json) implements CustomPayload{
    public static final Id<ScreenState> ID=new Id<>(Identifier.of("ptcglive","screen_state"));
    public static final PacketCodec<RegistryByteBuf,ScreenState> CODEC=PacketCodec.tuple(PacketCodecs.string(16),ScreenState::kind,
        BlockPos.PACKET_CODEC,ScreenState::pos,PacketCodecs.string(1048576),ScreenState::json,ScreenState::new);
    @Override public Id<? extends CustomPayload> getId(){return ID;}
}
