package dev.hua.ptcglive.spectator;

import com.google.gson.Gson;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.nbt.*;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.PersistentState;
import java.util.*;

/** Nearby screen-only messages. No battle actions, private state or user-supplied sender identity. */
public final class ScreenChat {
    public record Message(UUID sender,String name,String text,UUID match,long time){}
    public record Panel(boolean enabled,Map<UUID,String> blocked,List<Message> recent,String notice){}
    private record Key(String dimension,long root){}
    private static final Map<UUID,Long> last=new HashMap<>();
    private static final Map<Key,ArrayDeque<Message>> messages=new LinkedHashMap<>();
    private static final Map<Key,Long> screenLast=new HashMap<>();
    private static final Gson JSON=new Gson();
    public static final class Preferences extends PersistentState {
        private final Set<UUID> disabled=new HashSet<>();
        private final Map<UUID,Map<UUID,String>> blocked=new HashMap<>();
        public boolean allows(UUID viewer,UUID sender){return !disabled.contains(viewer)&&!blocked.getOrDefault(viewer,Map.of()).containsKey(sender);}
        public void toggle(UUID viewer){if(!disabled.remove(viewer))disabled.add(viewer);markDirty();}
        public void block(UUID viewer,UUID sender,String name){
            var list=blocked.computeIfAbsent(viewer,k->new LinkedHashMap<>());
            if(!list.containsKey(sender)&&list.size()>=128)throw new IllegalArgumentException("屏蔽列表已满（128 人）");
            list.put(sender,name);markDirty();
        }
        public void unblock(UUID viewer,UUID sender){var list=blocked.get(viewer);if(list!=null){list.remove(sender);markDirty();}}
        public NbtCompound writeNbt(NbtCompound nbt,RegistryWrapper.WrapperLookup lookup){
            var rows=new NbtList();var owners=new HashSet<>(blocked.keySet());owners.addAll(disabled);
            for(UUID owner:owners){var row=new NbtCompound();row.putUuid("owner",owner);row.putBoolean("disabled",disabled.contains(owner));var list=new NbtList();
                blocked.getOrDefault(owner,Map.of()).forEach((id,name)->{var r=new NbtCompound();r.putUuid("id",id);r.putString("name",name);list.add(r);});row.put("blocked",list);rows.add(row);
            }nbt.put("viewers",rows);return nbt;
        }
        public static Preferences read(NbtCompound nbt,RegistryWrapper.WrapperLookup lookup){
            var result=new Preferences();var rows=nbt.getList("viewers",NbtElement.COMPOUND_TYPE);
            for(int i=0;i<rows.size();i++){var row=rows.getCompound(i);if(!row.containsUuid("owner"))continue;UUID owner=row.getUuid("owner");if(row.getBoolean("disabled"))result.disabled.add(owner);
                var list=row.getList("blocked",NbtElement.COMPOUND_TYPE);for(int j=0;j<Math.min(128,list.size());j++){var r=list.getCompound(j);if(r.containsUuid("id"))result.block(owner,r.getUuid("id"),r.getString("name"));}
            }return result;
        }
    }
    private static final PersistentState.Type<Preferences> TYPE=new PersistentState.Type<>(Preferences::new,Preferences::read,null);
    private static Preferences preferences(ServerPlayerEntity p){return p.getServer().getOverworld().getPersistentStateManager().getOrCreate(TYPE,"ptcglive_screen_chat");}
    public static String sanitize(String input){
        String text=input.strip();if(text.isEmpty()||text.codePointCount(0,text.length())>40)throw new IllegalArgumentException("弹幕限 1–40 字");
        if(text.codePoints().anyMatch(c->Character.isISOControl(c)||Character.getType(c)==Character.FORMAT||c=='§'||c==0x2028||c==0x2029))throw new IllegalArgumentException("弹幕不能包含控制符或格式代码");
        return text;
    }
    public static boolean isAction(String action){return Set.of("chat","chat_menu","chat_toggle","mute","unmute").contains(action);}
    public static void handle(ServerPlayerEntity player,BlockPos clicked,SpectatorScreenEntity root,String action,String argument){
        if(player.squaredDistanceTo(Vec3d.ofCenter(clicked))>64||!player.getServerWorld().isChunkLoaded(clicked)
            ||ScreenStructures.root(player.getServerWorld(),clicked)!=root)return;
        var prefs=preferences(player);var key=new Key(player.getServerWorld().getRegistryKey().getValue().toString(),root.getPos().asLong());String notice="";
        long now=player.getServer().getTicks();
        try{
            switch(action){
                case "chat_toggle"->prefs.toggle(player.getUuid());
                case "mute"->{UUID id=UUID.fromString(argument);var candidate=messages.getOrDefault(key,new ArrayDeque<>()).stream().filter(m->m.sender().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("请从当前弹幕列表选择发送者"));prefs.block(player.getUuid(),id,candidate.name());}
                case "unmute"->prefs.unblock(player.getUuid(),UUID.fromString(argument));
                case "chat"->{
                    if(!root.controller()||root.match==null||!dev.hua.ptcglive.PtcgServices.tables().publicScreenMatches().contains(root.match))throw new IllegalArgumentException("请连接正在进行的公开对战");
                    if(!player.isAlive())throw new IllegalArgumentException("当前无法发送弹幕");
                    String text=sanitize(argument);
                    last.entrySet().removeIf(e->now-e.getValue()>=60);
                    if(now-last.getOrDefault(player.getUuid(),-100L)<60)throw new IllegalArgumentException("每人每 3 秒可发送一条");
                    if(now-screenLast.getOrDefault(key,-100L)<10)throw new IllegalArgumentException("屏幕弹幕较多，请稍后发送");
                    var message=new Message(player.getUuid(),player.getName().getString(),text,root.match,now);
                    last.put(player.getUuid(),now);screenLast.put(key,now);
                    var recent=messages.computeIfAbsent(key,k->new ArrayDeque<>());recent.addLast(message);while(recent.size()>12)recent.removeFirst();
                    while(messages.size()>128){var oldest=messages.keySet().iterator().next();messages.remove(oldest);screenLast.remove(oldest);}
                    for(var viewer:PlayerLookup.tracking(root))if(viewer.getWorld()==player.getWorld()&&viewer.squaredDistanceTo(Vec3d.ofCenter(root.getPos()))<72*72&&prefs.allows(viewer.getUuid(),player.getUuid()))
                        ServerPlayNetworking.send(viewer,new ScreenState("chat",root.getPos(),JSON.toJson(message)));
                    notice="已发送";
                }
                case "chat_menu"->{}
                default->throw new IllegalArgumentException("未知弹幕操作");
            }
        }catch(IllegalArgumentException ex){notice=ex.getMessage();}
        var recent=messages.getOrDefault(key,new ArrayDeque<>()).stream().filter(m->now-m.time()<1200&&Objects.equals(m.match(),root.match)&&prefs.allows(player.getUuid(),m.sender())).toList();
        ServerPlayNetworking.send(player,new ScreenState("chat_menu",clicked,JSON.toJson(new Panel(!prefs.disabled.contains(player.getUuid()),Map.copyOf(prefs.blocked.getOrDefault(player.getUuid(),Map.of())),recent,notice))));
    }
    public static void initialize(){
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{last.clear();messages.clear();screenLast.clear();});
    }
    private ScreenChat(){}
}
