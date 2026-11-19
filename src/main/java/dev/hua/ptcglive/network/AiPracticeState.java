package dev.hua.ptcglive.network;

import dev.hua.ptcglive.ai.AiConfiguration;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Owner-authorized setup data, never a running opponent's private zones. */
public record AiPracticeState(BlockPos tablePos,String json,String message) implements CustomPayload {
    public record Box(String id,String name,int count,String rule,Map<String,Integer> cards){}
    public record View(List<Box> boxes,Map<String,String> names,Map<String,String> rules,String defaultRule,AiConfiguration check){}
    public static final Id<AiPracticeState> ID=new Id<>(Identifier.of("ptcglive","ai_practice_state"));
    public static final PacketCodec<RegistryByteBuf,AiPracticeState> CODEC=PacketCodec.tuple(
        BlockPos.PACKET_CODEC,AiPracticeState::tablePos,PacketCodecs.string(250000),AiPracticeState::json,
        PacketCodecs.string(16384),AiPracticeState::message,AiPracticeState::new);
    @Override public Id<? extends CustomPayload> getId(){return ID;}
}
