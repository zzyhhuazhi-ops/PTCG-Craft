package dev.hua.ptcglive.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client sends only a short-lived server-issued session ID and CONFIRM/CANCEL. */
public record PackIntentPayload(String sessionId, String action) implements CustomPayload {
    public static final Id<PackIntentPayload> ID = new Id<>(Identifier.of("ptcglive", "pack_intent"));
    public static final PacketCodec<RegistryByteBuf, PackIntentPayload> CODEC = PacketCodec.tuple(
        PacketCodecs.string(36), PackIntentPayload::sessionId,
        PacketCodecs.string(12), PackIntentPayload::action, PackIntentPayload::new);
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
