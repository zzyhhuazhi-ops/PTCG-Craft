package dev.hua.ptcglive.network;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
public record RecycleIntent(String session,int revision,String action,String argument) implements CustomPayload {
 public static final Id<RecycleIntent> ID=new Id<>(Identifier.of("ptcglive","recycle_intent"));
 public static final PacketCodec<RegistryByteBuf,RecycleIntent> CODEC=PacketCodec.tuple(
  PacketCodecs.string(64),RecycleIntent::session,PacketCodecs.INTEGER,RecycleIntent::revision,
  PacketCodecs.string(24),RecycleIntent::action,PacketCodecs.string(24000),RecycleIntent::argument,RecycleIntent::new);
 @Override public Id<? extends CustomPayload> getId(){return ID;}
}
