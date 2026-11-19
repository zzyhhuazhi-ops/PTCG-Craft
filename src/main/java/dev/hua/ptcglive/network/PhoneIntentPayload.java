package dev.hua.ptcglive.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record PhoneIntentPayload(String session, String action, String argument, int amount, String requestId) implements CustomPayload {
    public static final Id<PhoneIntentPayload> ID = new Id<>(Identifier.of("ptcglive", "phone_intent"));
    public static final PacketCodec<RegistryByteBuf, PhoneIntentPayload> CODEC = PacketCodec.tuple(
        PacketCodecs.string(64), PhoneIntentPayload::session, PacketCodecs.string(32), PhoneIntentPayload::action,
        PacketCodecs.string(512), PhoneIntentPayload::argument, PacketCodecs.INTEGER, PhoneIntentPayload::amount,
        PacketCodecs.string(64), PhoneIntentPayload::requestId, PhoneIntentPayload::new);
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
