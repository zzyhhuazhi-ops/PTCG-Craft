package dev.hua.ptcglive.network;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record BattleSocialIntent(String action,String argument,String dimension,BlockPos table) implements CustomPayload {
    public static final Id<BattleSocialIntent> ID=new Id<>(Identifier.of("ptcglive","battle_social"));
    public static final PacketCodec<RegistryByteBuf,BattleSocialIntent> CODEC=PacketCodec.tuple(
        PacketCodecs.string(24),BattleSocialIntent::action,PacketCodecs.string(64),BattleSocialIntent::argument,
        PacketCodecs.string(128),BattleSocialIntent::dimension,BlockPos.PACKET_CODEC,BattleSocialIntent::table,BattleSocialIntent::new);
    @Override public Id<? extends CustomPayload> getId(){return ID;}
}
