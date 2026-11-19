package dev.hua.ptcglive.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** The requesting participant's own finished table matches ({@code TableLobbyView.History} as JSON). */
public record TableHistoryPayload(BlockPos tablePos, String historyJson) implements CustomPayload {
    public static final Id<TableHistoryPayload> ID = new Id<>(Identifier.of("ptcglive", "table_history"));
    public static final PacketCodec<RegistryByteBuf, TableHistoryPayload> CODEC = PacketCodec.tuple(
        BlockPos.PACKET_CODEC, TableHistoryPayload::tablePos,
        PacketCodecs.string(300000), TableHistoryPayload::historyJson,
        TableHistoryPayload::new);
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
