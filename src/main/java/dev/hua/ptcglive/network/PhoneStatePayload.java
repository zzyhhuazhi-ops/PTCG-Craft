package dev.hua.ptcglive.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record PhoneStatePayload(boolean openScreen, String json, String messageZh) implements CustomPayload {
    public static final Id<PhoneStatePayload> ID = new Id<>(Identifier.of("ptcglive", "phone_state"));
    public static final PacketCodec<RegistryByteBuf, PhoneStatePayload> CODEC = PacketCodec.tuple(
        PacketCodecs.BOOL, PhoneStatePayload::openScreen, PacketCodecs.string(500_000), PhoneStatePayload::json,
        PacketCodecs.string(2048), PhoneStatePayload::messageZh, PhoneStatePayload::new);
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
