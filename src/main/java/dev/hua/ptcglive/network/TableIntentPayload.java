package dev.hua.ptcglive.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** A client may request an action and one bounded identifier, never lobby state, card data or random outcomes. */
public record TableIntentPayload(BlockPos tablePos, String action, String argument) implements CustomPayload {
    public static final Id<TableIntentPayload> ID = new Id<>(Identifier.of("ptcglive", "table_intent"));
    public static final PacketCodec<RegistryByteBuf, TableIntentPayload> CODEC = PacketCodec.tuple(
        BlockPos.PACKET_CODEC, TableIntentPayload::tablePos,
        PacketCodecs.string(32), TableIntentPayload::action,
        PacketCodecs.string(256), TableIntentPayload::argument,
        TableIntentPayload::new);
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
