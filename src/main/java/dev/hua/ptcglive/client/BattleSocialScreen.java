package dev.hua.ptcglive.client;

import com.google.gson.Gson;
import dev.hua.ptcglive.network.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** A thin view of server-owned invites. Remote acceptance never submits READY or START. */
public final class BattleSocialScreen extends Screen {
    private final Screen parent;
    private final BlockPos table;
    private final String dimension;
    private BattleSocialState.View view=new BattleSocialState.View(List.of(),List.of());
    private String message="正在读取在线玩家和邀请……";
    private int scroll;
    private boolean waiting;
    private record Row(String text,String primary,String secondary,Runnable first,Runnable second,boolean active) {}
    private List<Row> rows=List.of();
    private BattleSocialScreen(Screen parent,BlockPos table,String dimension){super(Text.literal("对战邀请"));this.parent=parent;this.table=table;this.dimension=dimension;}
    public static void open(Screen parent,BlockPos table) {
        var client=MinecraftClient.getInstance();
        var screen=new BattleSocialScreen(parent,table,client.world==null?"":client.world.getRegistryKey().getValue().toString());
        client.setScreen(screen);screen.send("REFRESH","");
    }
    public void update(BattleSocialState state) {
        waiting=false;message=state.message();view=new Gson().fromJson(state.json(),BattleSocialState.View.class);clearAndInit();
    }
    private void send(String action,String argument){if(waiting)return;waiting=true;ClientPlayNetworking.send(new BattleSocialIntent(action,argument,dimension,table==null?BlockPos.ORIGIN:table));clearAndInit();}
    public static void matchAction(String action,UUID match){ClientPlayNetworking.send(new BattleSocialIntent(action,match.toString(),"",BlockPos.ORIGIN));}
    private int left(){return Math.max(8,(width-390)/2);}
    private int span(){return Math.min(390,width-16);}
    private int capacity(){return Math.max(1,(height-112)/54);}
    @Override protected void init() {
        var list=new ArrayList<Row>();
        for(var i:view.invitations()) {
            String label=(i.outgoing()?"邀请 "+i.recipientName():i.senderName()+" 邀请你")+" · "+i.rule()+" · "+i.location();
            list.add(new Row(label,i.outgoing()?"取消":"接受",i.outgoing()?"":"拒绝",()->send(i.outgoing()?"CANCEL":"ACCEPT",i.id()),()->send("REJECT",i.id()),true));
        }
        if(table!=null)for(var p:view.players())list.add(new Row(p.name()+(p.busy()?" · 正在对局":" · 在线"),"邀请","",()->send("INVITE",p.id()),()->{},!p.busy()));
        rows=List.copyOf(list);scroll=Math.max(0,Math.min(scroll,Math.max(0,rows.size()-capacity())));
        for(int i=0;i<capacity()&&scroll+i<rows.size();i++) {
            var row=rows.get(scroll+i);int y=58+i*54;
            add(row.primary(),left()+span()-104,y+27,50,row.active()&&!waiting,row.first());
            if(!row.secondary().isEmpty())add(row.secondary(),left()+span()-50,y+27,50,!waiting,row.second());
        }
        add("刷新",left(),height-28,64,!waiting,()->send("REFRESH",""));
        add("返回",left()+span()-64,height-28,64,true,this::close);
    }
    private void add(String label,int x,int y,int w,boolean active,Runnable run){var b=ButtonWidget.builder(Text.literal(label),ignored->run.run()).dimensions(x,y,w,18).build();b.active=active;addDrawableChild(b);}
    @Override public void render(DrawContext context,int mx,int my,float delta) {
        renderInGameBackground(context);
        context.fill(left()-4,8,left()+span()+4,height-6,0xF0182940);
        context.drawCenteredTextWithShadow(textRenderer,title,width/2,17,0xFFFFFF);
        context.drawText(textRenderer,table==null?"远程接受仅约战；发起邀请请到空闲卡桌。":"在卡桌选择合法卡盒后，可邀请在线玩家。",left(),33,0xFFDA72,false);
        for(int i=0;i<capacity()&&scroll+i<rows.size();i++) {
            int y=58+i*54;context.fill(left(),y-3,left()+span(),y+49,0xFF243D58);
            var text=textRenderer.wrapLines(Text.literal(rows.get(scroll+i).text()),span()-8);
            for(int l=0;l<Math.min(2,text.size());l++)context.drawText(textRenderer,text.get(l),left()+4,y+l*11,0xFFFFFF,false);
        }
        if(rows.isEmpty()) {
            String empty=waiting?"正在读取邀请和在线玩家……":table==null
                ?"暂无待处理邀请。\n"+(view.players().isEmpty()?"当前没有其他在线玩家。":"请到自己的空闲卡桌选择合法卡盒，再发起邀请。")
                :"当前没有其他在线玩家。\n单人练习可返回卡桌，选择“挑战电脑”。";
            var lines=textRenderer.wrapLines(Text.literal(empty),span()-8);
            for(int i=0;i<Math.min(3,lines.size());i++)context.drawText(textRenderer,lines.get(i),left()+4,64+i*11,0xBFCFE0,false);
        }
        var notices=textRenderer.wrapLines(Text.literal(waiting?"等待服务器确认……":message),span());
        for(int i=0;i<Math.min(2,notices.size());i++)context.drawText(textRenderer,notices.get(i),left(),height-55+i*11,0xFFE285,false);
        super.render(context,mx,my,delta);
    }
    /** Background is drawn before the panel; vanilla's later blur would blur our text as well. */
    @Override public void renderBackground(DrawContext context,int mouseX,int mouseY,float delta) { }
    @Override public boolean mouseScrolled(double x,double y,double h,double v){scroll+=v<0?1:-1;clearAndInit();return true;}
    @Override public void close(){client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
