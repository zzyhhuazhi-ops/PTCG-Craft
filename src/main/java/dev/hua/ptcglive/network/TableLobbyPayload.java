package dev.hua.ptcglive.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Recipient-specific lobby view ({@code TableLobbyView.Lobby} as JSON) plus the
 * outcome of the last request.  Public lobby facts and the recipient's own boxes only.
 */
public record TableLobbyPayload(BlockPos tablePos, boolean success, String messageZh, String snapshotJson) implements CustomPayload {
    public static final Id<TableLobbyPayload> ID = new Id<>(Identifier.of("ptcglive", "table_lobby"));
    public static final PacketCodec<RegistryByteBuf, TableLobbyPayload> CODEC = PacketCodec.tuple(
        BlockPos.PACKET_CODEC, TableLobbyPayload::tablePos,
        PacketCodecs.BOOL, TableLobbyPayload::success,
        PacketCodecs.string(4096), TableLobbyPayload::messageZh,
        PacketCodecs.string(250000), TableLobbyPayload::snapshotJson,
        TableLobbyPayload::new);
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
