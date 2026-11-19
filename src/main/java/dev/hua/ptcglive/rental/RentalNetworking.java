package dev.hua.ptcglive.rental;

import com.google.gson.Gson;
import dev.hua.ptcglive.*;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;

public final class RentalNetworking {
    private static final Gson JSON=new Gson();
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final class Session {
        final String id=UUID.randomUUID().toString();final UUID machine;final ServerWorld world;final BlockPos pos;
        int revision;long lastTick;Map<Integer,String> slots=Map.of();
        Session(ServerPlayerEntity player,RentalBlockEntity entity){world=player.getServerWorld();pos=entity.getPos().toImmutable();machine=entity.machineId();lastTick=player.getServer().getTicks();}
    }
    private RentalNetworking(){}
    public static void initialize(){
        PayloadTypeRegistry.playC2S().register(RentalIntent.ID,RentalIntent.CODEC);
        PayloadTypeRegistry.playS2C().register(RentalState.ID,RentalState.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(RentalIntent.ID,(payload,context)->handle(context.player(),payload));
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->SESSIONS.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server->SESSIONS.clear());
    }
    public static boolean accessible(ServerPlayerEntity player,ServerWorld world,BlockPos pos){
        return player.isAlive()&&!player.isSpectator()&&player.getWorld()==world&&world.isChunkLoaded(pos)
            &&player.squaredDistanceTo(Vec3d.ofCenter(pos))<=64&&world.canPlayerModifyAt(player,pos)
            &&world.getBlockEntity(pos) instanceof RentalBlockEntity entity&&entity.mayAccess(player);
    }
    public static void open(ServerPlayerEntity player,BlockPos pos){
        if(!accessible(player,player.getServerWorld(),pos)){
            player.sendMessage(net.minecraft.text.Text.literal("只有租赁机所有者可以操作"),true);return;
        }
        var entity=(RentalBlockEntity)player.getWorld().getBlockEntity(pos);
        var session=new Session(player,entity);SESSIONS.put(player.getUuid(),session);send(player,session,entity,true,"");
    }
    public static void handle(ServerPlayerEntity player,RentalIntent intent){
        var session=SESSIONS.get(player.getUuid());if(session==null||!session.id.equals(intent.session()))return;
        if(intent.action().equals("CLOSE")){SESSIONS.remove(player.getUuid());return;}
        long tick=player.getServer().getTicks();
        if(tick-session.lastTick>1200||!accessible(player,session.world,session.pos)
            ||!((RentalBlockEntity)session.world.getBlockEntity(session.pos)).machineId().equals(session.machine)){
            SESSIONS.remove(player.getUuid());ServerPlayNetworking.send(player,new RentalState(false,"","租赁机已失效，请重新打开"));return;
        }
        var entity=(RentalBlockEntity)session.world.getBlockEntity(session.pos);
        if(intent.revision()!=session.revision){send(player,session,entity,false,"旧请求未执行");return;}
        session.revision++;session.lastTick=tick;String message="";
        try {
            switch(intent.action()){
                case "REFRESH"->{}
                case "INSERT"->{
                    String expected=session.slots.get(intent.slot());
                    if(expected==null||!(player.getInventory().getStack(intent.slot()).getItem() instanceof CardItem item)||!item.card().id().equals(expected))
                        throw new IllegalStateException("背包卡牌已变化，请刷新后选择");
                    entity.insert(player,intent.slot());message="已放入卡牌，开始生产";
                }
                case "CLAIM"->{long amount=entity.extract(player);message=amount==0?"暂无可提取收益":"提取成功：+"+amount+" 宝可币，已存入手机钱包";}
                case "TAKE"->{entity.take(player);message="卡牌已返还；背包满时掉落在脚边";}
                case "REDSTONE","LAPIS"->{int added=entity.addFuel(player,intent.action().equals("LAPIS"));message="已存入 "+added+" 块燃料；新燃料从下一生产周期生效";}
                case "TAKE_FUEL"->{entity.takeFuel(player);message="剩余燃料已返还；未完成的加速周期恢复基础产量";}
                default->throw new IllegalArgumentException("无效操作");
            }
        }catch(ArithmeticException overflow){message="钱包余额空间不足，收益仍保留在租赁机";}
        catch(RuntimeException failure){message=failure.getMessage()==null?"操作失败，请刷新":failure.getMessage();}
        send(player,session,entity,false,message);
    }
    private static void send(ServerPlayerEntity player,Session session,RentalBlockEntity entity,boolean open,String message){
        entity.settle();var held=new ArrayList<RentalView.HeldCard>();var slots=new HashMap<Integer,String>();
        for(int i=0;i<=40;i++){
            if(i>=36&&i<40)continue;var stack=player.getInventory().getStack(i);
            if(stack.getItem() instanceof CardItem card&&!stack.isEmpty()){
                var c=card.card();slots.put(i,c.id());held.add(new RentalView.HeldCard(i,c.id(),c.nameZh(),c.rarityZh(),RentalBlockEntity.rate(c.id(),c.rarityZh()),stack.getCount()));
            }
        }
        session.slots=Map.copyOf(slots);var stack=entity.card();var card=stack.getItem() instanceof CardItem c?c.card():null;
        var fuel=entity.fuel();var reserved=entity.reservedFuel();var shownFuel=fuel.isEmpty()?reserved:fuel;
        String fuelName=shownFuel.isEmpty()?"无":shownFuel.isOf(net.minecraft.item.Items.REDSTONE_BLOCK)?"红石块":"青金石块";
        var view=new RentalView(session.id,session.revision,card==null?"":card.id(),card==null?"未放入卡牌":card.nameZh(),
            card==null?"":card.rarityZh(),entity.rate(),entity.stored(),PtcgServices.economy().wallet().balance(player.getUuid()),
            entity.multiplier(),entity.half(),fuelName,fuel.getCount(),reserved.getCount(),List.copyOf(held));
        ServerPlayNetworking.send(player,new RentalState(open,JSON.toJson(view),message));
    }
}
