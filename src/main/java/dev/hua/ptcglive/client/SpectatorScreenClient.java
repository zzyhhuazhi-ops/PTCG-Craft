package dev.hua.ptcglive.client;

import com.google.gson.Gson;
import dev.hua.ptcglive.CardItems;
import dev.hua.ptcglive.engine.BattleEventJournal;
import dev.hua.ptcglive.spectator.*;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.util.Util;
import java.util.*;

/** One public scene per match shared by all nearby screens. */
public final class SpectatorScreenClient {
    public static final class Scene {
        public final ScreenNetworking.Broadcast view;
        public final List<ScreenLayout.Card> cards;
        public final Map<UUID,Integer> oldHp=new HashMap<>();
        public final List<BattleEventJournal.Event> fresh;
        public final long received=Util.getMeasuringTimeMs();
        Scene(ScreenNetworking.Broadcast view,Scene old){
            this.view=view;cards=ScreenLayout.cards(view.match());
            if(old!=null)for(var card:old.cards)if(card.pokemon()!=null)oldHp.put(card.pokemon().pokemonId(),Math.max(0,card.pokemon().hitPoints()-card.pokemon().damage()));
            long sequence=old==null?Long.MAX_VALUE:old.view.match().events().stream().mapToLong(BattleEventJournal.Event::sequence).max().orElse(0);
            fresh=view.match().events().stream().filter(e->e.sequence()>sequence).toList();
        }
        public double age(){return (Util.getMeasuringTimeMs()-received)/1000.0;}
    }
    private static final Map<UUID,Scene> scenes=new HashMap<>();
    public record ChatLine(ScreenChat.Message message,long received){}
    private record ChatKey(String dimension,long position){}
    private static final Map<ChatKey,ArrayDeque<ChatLine>> chat=new LinkedHashMap<>();
    private static ChatKey chatKey(net.minecraft.util.math.BlockPos pos){
        var world=net.minecraft.client.MinecraftClient.getInstance().world;
        return new ChatKey(world==null?"":world.getRegistryKey().getValue().toString(),pos.asLong());
    }
    private static boolean chatEnabled=true;
    private static Set<UUID> blocked=Set.of();
    public static List<ChatLine> chat(net.minecraft.util.math.BlockPos root,UUID match){
        if(!chatEnabled||match==null)return List.of();long now=Util.getMeasuringTimeMs();
        var lines=chat.get(chatKey(root));if(lines==null)return List.of();
        lines.removeIf(line->now-line.received()>8000);
        return lines.stream().filter(line->line.message().match().equals(match)&&!blocked.contains(line.message().sender())).toList();
    }
    private static final Gson JSON=new Gson();
    public static Scene scene(UUID id){return id==null?null:scenes.get(id);}
    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(ScreenState.ID,(payload,ctx)->ctx.client().execute(()->{
            switch(payload.kind()){
                case "chat"->{
                    var message=JSON.fromJson(payload.json(),ScreenChat.Message.class);
                    if(chatEnabled&&!blocked.contains(message.sender())){
                        var lines=chat.computeIfAbsent(chatKey(payload.pos()),p->new ArrayDeque<>());
                        lines.addLast(new ChatLine(message,Util.getMeasuringTimeMs()));while(lines.size()>6)lines.removeFirst();
                        while(chat.size()>128)chat.remove(chat.keySet().iterator().next());
                    }
                }
                case "chat_menu"->{
                    var panel=JSON.fromJson(payload.json(),ScreenChat.Panel.class);chatEnabled=panel.enabled();blocked=Set.copyOf(panel.blocked().keySet());
                    if(!chatEnabled)chat.clear();else chat.values().forEach(lines->lines.removeIf(line->blocked.contains(line.message().sender())));
                    if(ctx.client().currentScreen instanceof SpectatorChatScreen shown&&shown.position().equals(payload.pos()))shown.update(panel);
                    else ctx.client().setScreen(new SpectatorChatScreen(payload.pos(),panel,ctx.client().currentScreen));
                }
                case "match"->{var view=JSON.fromJson(payload.json(),ScreenNetworking.Broadcast.class);var old=scenes.get(view.match().id());
                    if(old==null||old.view.match().revision()<view.match().revision())scenes.put(view.match().id(),new Scene(view,old));}
                case "forget"->scenes.remove(UUID.fromString(payload.json()));
                case "menu"->{var menu=JSON.fromJson(payload.json(),ScreenNetworking.Menu.class);
                    if(ctx.client().currentScreen instanceof SpectatorScreenMenu shown&&shown.position().equals(payload.pos()))shown.update(menu);
                    else ctx.client().setScreen(new SpectatorScreenMenu(payload.pos(),menu));}
                case "card"->{try{ctx.client().setScreen(new CardViewScreen(CardItems.byDefinitionId(payload.json()).card()));}catch(IllegalArgumentException ignored){}}
                default->{}
            }
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{scenes.clear();chat.clear();blocked=Set.of();chatEnabled=true;});
    }
    private SpectatorScreenClient(){}
}
