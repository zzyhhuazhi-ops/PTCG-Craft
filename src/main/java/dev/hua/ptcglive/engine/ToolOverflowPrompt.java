package dev.hua.ptcglive.engine;

import dev.hua.ptcglive.rules.CardInstance;
import java.util.*;

/** Only already-public attachments; selection authority belongs to their owner. */
public record ToolOverflowPrompt(UUID id, UUID owner, UUID pokemonId, int capacity, List<CardInstance> tools,boolean bench) {
    public ToolOverflowPrompt(UUID id,UUID owner,UUID pokemonId,int capacity,List<CardInstance> tools){this(id,owner,pokemonId,capacity,tools,false);}
    public ToolOverflowPrompt {
        Objects.requireNonNull(id);Objects.requireNonNull(owner);Objects.requireNonNull(pokemonId);
        tools=List.copyOf(tools);
        if(capacity<1||capacity>(bench?8:4)||tools.size()<=capacity)throw new IllegalArgumentException("无效的超量规则选择");
    }
    public int excess() {return tools.size()-capacity;}
}
