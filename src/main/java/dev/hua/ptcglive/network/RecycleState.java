package dev.hua.ptcglive.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record RecycleState(boolean openScreen, String json, String messageZh) implements CustomPayload {
    public static final Id<RecycleState> ID = new Id<>(Identifier.of("ptcglive", "recycle_state"));
    public static final PacketCodec<RegistryByteBuf, RecycleState> CODEC = PacketCodec.tuple(
        PacketCodecs.BOOL, RecycleState::openScreen, PacketCodecs.string(500_000), RecycleState::json,
        PacketCodecs.string(2048), RecycleState::messageZh, RecycleState::new);
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
