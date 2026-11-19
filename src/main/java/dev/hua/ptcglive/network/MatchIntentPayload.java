package dev.hua.ptcglive.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Bounded choice references only: no damage, card definitions, random seeds or results from the client. */
public record MatchIntentPayload(String matchId, String seat, int revision, int option) implements CustomPayload {
    public static final int PRESENTATION_FINISHED=-3;
    public static final Id<MatchIntentPayload> ID = new Id<>(Identifier.of("ptcglive", "match_intent"));
    public static final PacketCodec<RegistryByteBuf, MatchIntentPayload> CODEC = PacketCodec.tuple(
        PacketCodecs.string(36), MatchIntentPayload::matchId,
        PacketCodecs.string(36), MatchIntentPayload::seat,
        PacketCodecs.VAR_INT, MatchIntentPayload::revision,
        PacketCodecs.VAR_INT, MatchIntentPayload::option,
        MatchIntentPayload::new);
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
