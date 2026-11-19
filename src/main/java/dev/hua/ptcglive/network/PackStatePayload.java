package dev.hua.ptcglive.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Owner-only preview or already server-resolved opening result. */
public record PackStatePayload(boolean openScreen, String snapshotJson, String messageZh) implements CustomPayload {
    public static final Id<PackStatePayload> ID = new Id<>(Identifier.of("ptcglive", "pack_state"));
    public static final PacketCodec<RegistryByteBuf, PackStatePayload> CODEC = PacketCodec.tuple(
        PacketCodecs.BOOL, PackStatePayload::openScreen,
        PacketCodecs.string(250000), PackStatePayload::snapshotJson,
        PacketCodecs.string(1024), PackStatePayload::messageZh, PackStatePayload::new);
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
