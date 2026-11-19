package dev.hua.ptcglive.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Paginated, owner-only card box view. Never broadcast to a table or other players. */
public record BoxStatePayload(boolean openScreen, String snapshotJson, String messageZh) implements CustomPayload {
    public static final Id<BoxStatePayload> ID = new Id<>(Identifier.of("ptcglive", "box_state"));
    public static final PacketCodec<RegistryByteBuf, BoxStatePayload> CODEC = PacketCodec.tuple(
        PacketCodecs.BOOL, BoxStatePayload::openScreen,
        PacketCodecs.string(250000), BoxStatePayload::snapshotJson,
        PacketCodecs.string(1024), BoxStatePayload::messageZh, BoxStatePayload::new);
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
