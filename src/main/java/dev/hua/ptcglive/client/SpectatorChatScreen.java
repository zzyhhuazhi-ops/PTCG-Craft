package dev.hua.ptcglive.client;

import dev.hua.ptcglive.spectator.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Flat, unblurred chat controls. All identities, proximity checks and rate limits are server-owned. */
public final class SpectatorChatScreen extends Screen {
    private final BlockPos pos;
    private final Screen parent;
    private ScreenChat.Panel panel;
    private TextFieldWidget input;
    private boolean blockedList;
    private int scroll,left,panelWidth,rows;
    public SpectatorChatScreen(BlockPos pos,ScreenChat.Panel panel,Screen parent){super(Text.literal("观战弹幕"));this.pos=pos;this.panel=panel;this.parent=parent;}
    public BlockPos position(){return pos;}
    public void update(ScreenChat.Panel next){panel=next;if("已发送".equals(next.notice())&&input!=null)input.setText("");clearAndInit();}
    private void send(String action,String argument){ClientPlayNetworking.send(new ScreenIntent(pos,action,argument));}
    private void submit(){if(!input.getText().isBlank()){send("chat",input.getText());}}
    private void button(int x,int y,int w,String label,Runnable action){addDrawableChild(ButtonWidget.builder(Text.literal(label),b->action.run()).dimensions(x,y,w,20).build());}
    @Override protected void init(){
        String draft=input==null?"":input.getText();panelWidth=Math.min(560,width-16);left=(width-panelWidth)/2;
        button(left,32,panelWidth/2-2,blockedList?"返回弹幕":"屏蔽名单（"+panel.blocked().size()+"）",()->{blockedList=!blockedList;scroll=0;clearAndInit();});
        button(left+panelWidth/2+2,32,panelWidth/2-2,"刷新",()->send("chat_menu",""));
        rows=Math.max(0,(height-180)/24);
        int size=blockedList?panel.blocked().size():panel.recent().size();scroll=Math.clamp(scroll,0,Math.max(0,size-rows));
        var blocked=new ArrayList<>(panel.blocked().entrySet());
        for(int i=0;i<rows&&i+scroll<size;i++){
            int y=60+i*24;
            if(blockedList){var entry=blocked.get(scroll+i);button(left+panelWidth-64,y,64,"解除屏蔽",()->send("unmute",entry.getKey().toString()));}
            else {var message=panel.recent().get(size-1-scroll-i);button(left+panelWidth-64,y,64,"屏蔽此人",()->send("mute",message.sender().toString()));}
        }
        input=addDrawableChild(new TextFieldWidget(textRenderer,left,height-94,panelWidth-70,20,Text.literal("弹幕内容，最多40字")));input.setMaxLength(40);input.setText(draft);
        button(left+panelWidth-66,height-94,66,"发送",this::submit);
        button(left,height-68,panelWidth,"我的弹幕显示："+(panel.enabled()?"开":"关"),()->send("chat_toggle",""));
        button(left,height-28,panelWidth,"返回",this::close);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){if((key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER)&&input.isFocused()){submit();return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){scroll+=vertical<0?1:vertical>0?-1:0;clearAndInit();return true;}
    @Override public void renderBackground(DrawContext c,int mx,int my,float delta){}
    @Override public void render(DrawContext c,int mx,int my,float delta){
        c.fill(0,0,width,height,0xF0122030);c.drawTextWithShadow(textRenderer,title,left,12,0xFFE17A);
        var blocked=new ArrayList<>(panel.blocked().entrySet());int size=blockedList?blocked.size():panel.recent().size();
        for(int i=0;i<rows&&i+scroll<size;i++){
            String line=blockedList?blocked.get(scroll+i).getValue():panel.recent().get(size-1-scroll-i).name()+"："+panel.recent().get(size-1-scroll-i).text();
            c.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(line,panelWidth-72),left,66+i*24,0xE3F3FF);
        }
        c.drawTextWithShadow(textRenderer,textRenderer.trimToWidth("附近8格内发送 · 40字以内 · 每3秒一条",panelWidth),left,height-110,0xA7D4EF);
        c.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(panel.notice(),panelWidth),left,height-42,0xFFCC66);
        super.render(c,mx,my,delta);
    }
    @Override public void close(){client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
