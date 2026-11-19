package dev.hua.ptcglive.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import java.util.*;

/** Small faces decode thumbnails off-thread; full-size callers retain optional HD art. */
final class CardBoxArtCache implements AutoCloseable {
    record Art(Identifier texture,int width,int height,boolean highDefinition) implements CardArt{}
    private final String prefix="box/"+UUID.randomUUID()+"/";
    private final int limit,maximumHeight;
    private final boolean smoothFiltering;
    private final LinkedHashMap<String,Art> loaded=new LinkedHashMap<>(24,.75f,true);
    private final Map<String,CardArtLoader.Ticket> pending=new HashMap<>();
    private final Set<String> failed=new HashSet<>();
    private long generation=-1;
    CardBoxArtCache(){this(12,0);}
    CardBoxArtCache(int limit,int maximumHeight){this(limit,maximumHeight,false);}
    CardBoxArtCache(int limit,int maximumHeight,boolean smoothFiltering){
        if(limit<1||maximumHeight<0)throw new IllegalArgumentException("卡图缓存参数无效");
        this.limit=limit;this.maximumHeight=maximumHeight;this.smoothFiltering=smoothFiltering;
    }
    Art get(String cardId){
        if(generation!=CardArtResources.generation()){close();generation=CardArtResources.generation();}
        var known=loaded.get(cardId);if(known!=null)return known;
        if(pending.containsKey(cardId)||failed.contains(cardId)||!cardId.matches("[a-z0-9_-]+"))return null;
        var resources=MinecraftClient.getInstance().getResourceManager();var thumb=CardArtResources.thumbnail(resources,cardId);
        var selected=maximumHeight>0&&maximumHeight<=256?new CardArtResources.Selection(thumb,false):CardArtResources.select(resources,cardId);
        var ticket=CardArtLoader.request(resources,selected.resource(),thumb,selected.highDefinition(),maximumHeight,
            Identifier.of("ptcglive",prefix+cardId),art->{
                if(smoothFiltering)MinecraftClient.getInstance().getTextureManager().getTexture(art.texture()).setFilter(true,false);
                pending.remove(cardId);loaded.put(cardId,new Art(art.texture(),art.width(),art.height(),art.highDefinition()));
                if(loaded.size()>limit){var it=loaded.entrySet().iterator();var oldest=it.next();MinecraftClient.getInstance().getTextureManager().destroyTexture(oldest.getValue().texture());it.remove();}
            },()->{pending.remove(cardId);failed.add(cardId);});
        if(ticket!=null)pending.put(cardId,ticket);return null;
    }
    @Override public void close(){
        pending.values().forEach(CardArtLoader.Ticket::cancel);pending.clear();
        var manager=MinecraftClient.getInstance().getTextureManager();loaded.values().forEach(a->manager.destroyTexture(a.texture()));loaded.clear();failed.clear();
    }
}
