package dev.hua.ptcglive.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Encodes MatchSession.Snapshot only, never the server's private deck/prize inspection view. */
public record MatchStatePayload(boolean openScreen, String snapshotJson, String messageZh) implements CustomPayload {
    public static final Id<MatchStatePayload> ID = new Id<>(Identifier.of("ptcglive", "match_state"));
    public static final PacketCodec<RegistryByteBuf, MatchStatePayload> CODEC = PacketCodec.tuple(
        PacketCodecs.BOOL, MatchStatePayload::openScreen,
        PacketCodecs.string(250000), MatchStatePayload::snapshotJson,
        PacketCodecs.string(1024), MatchStatePayload::messageZh,
        MatchStatePayload::new);
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
