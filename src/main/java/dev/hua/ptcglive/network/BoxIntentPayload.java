package dev.hua.ptcglive.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** No owner, quantity, card instance or inventory data is accepted from the client. */
public record BoxIntentPayload(String sessionId, int revision, String action, String argument, int page, String query) implements CustomPayload {
    public static final Id<BoxIntentPayload> ID = new Id<>(Identifier.of("ptcglive", "box_intent"));
    public static final PacketCodec<RegistryByteBuf, BoxIntentPayload> CODEC = PacketCodec.tuple(
        PacketCodecs.string(36), BoxIntentPayload::sessionId,
        PacketCodecs.VAR_INT, BoxIntentPayload::revision,
        PacketCodecs.string(20), BoxIntentPayload::action,
        PacketCodecs.string(dev.hua.ptcglive.collection.LiveDeckParser.MAX_TEXT), BoxIntentPayload::argument,
        PacketCodecs.VAR_INT, BoxIntentPayload::page,
        PacketCodecs.string(40), BoxIntentPayload::query, BoxIntentPayload::new);
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
