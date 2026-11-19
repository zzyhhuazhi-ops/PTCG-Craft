package dev.hua.ptcglive.client;

import dev.hua.ptcglive.CardItems;
import dev.hua.ptcglive.spectator.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Management is separate from combat controls; no match action packets are ever sent here. */
public final class SpectatorScreenMenu extends Screen {
    private final BlockPos pos;
    private ScreenNetworking.Menu menu;
    private int scroll;
    private boolean publicCards;
    private int left,top,panelWidth,visibleRows;
    public SpectatorScreenMenu(BlockPos pos,ScreenNetworking.Menu menu){super(Text.literal("观战屏幕"));this.pos=pos;this.menu=menu;}
    public BlockPos position(){return pos;}
    public void update(ScreenNetworking.Menu menu){this.menu=menu;clearAndInit();}
    private void send(String action,String argument){ClientPlayNetworking.send(new ScreenIntent(pos,action,argument));}
    private void button(int x,int y,int w,String title,Runnable action){addDrawableChild(ButtonWidget.builder(Text.literal(title),b->action.run()).dimensions(x,y,w,20).build());}
    private List<ScreenLayout.Card> publicCards(){
        if(menu.current()==null)return List.of();var list=new ArrayList<>(ScreenLayout.cards(menu.current().match()));
        for(var p:menu.current().match().players()){
            for(var card:p.discard())list.add(new ScreenLayout.Card(0,0,0,0,card.definitionId(),"弃牌区",null));
            for(var card:p.lostZone())list.add(new ScreenLayout.Card(0,0,0,0,card.definitionId(),"放逐区",null));
            if(p.active()!=null)for(String card:p.active().evolutionCardIds())list.add(new ScreenLayout.Card(0,0,0,0,card,"战斗位进化链",null));
            for(var pokemon:p.bench())for(String card:pokemon.evolutionCardIds())list.add(new ScreenLayout.Card(0,0,0,0,card,"备战进化链",null));
        }
        return list;
    }
    @Override protected void init(){
        panelWidth=Math.min(580,width-16);left=(width-panelWidth)/2;top=16;
        int half=(panelWidth-8)/2,third=(panelWidth-12)/3;
        button(left,top+40,half,"刷新列表",()->send("refresh",""));
        button(left+half+8,top+40,half,"断开对战",()->send("disconnect",""));
        button(left,top+64,third,"玩家名称："+(menu.names()?"显示":"隐藏"),()->send("names",""));
        button(left+third+6,top+64,third,"攻击动画："+(menu.animations()?"开":"关"),()->send("animations",""));
        button(left+2*(third+6),top+64,third,"伤害特效："+(menu.damage()?"开":"关"),()->send("damage",""));
        button(left,top+88,panelWidth-100,publicCards?"返回对战列表":"查看公开卡牌 / 附着 / 弃牌区",()->{publicCards=!publicCards;scroll=0;clearAndInit();});
        button(left+panelWidth-96,top+88,96,"观战弹幕",()->send("chat_menu",""));
        visibleRows=Math.max(1,(height-top-170)/24);
        var cards=publicCards();int size=publicCards?cards.size():menu.matches().size();scroll=Math.clamp(scroll,0,Math.max(0,size-visibleRows));
        for(int i=0;i<visibleRows&&scroll+i<size;i++){
            int y=top+116+i*24;
            if(publicCards){var card=cards.get(scroll+i);String name=card.id();try{name=CardItems.byDefinitionId(card.id()).card().nameZh();}catch(IllegalArgumentException ignored){}
                button(left,y,panelWidth,card.label()+" · "+name,()->{try{client.setScreen(new CardViewScreen(CardItems.byDefinitionId(card.id()).card(),this));}catch(IllegalArgumentException ignored){}});
            }else{var row=menu.matches().get(scroll+i);
                String text=(Objects.equals(menu.connected(),row.id())?"● ":"连接 · ")+row.label()+" · 第 "+row.turn()+" 回合";
                button(left,y,panelWidth,text,()->send("connect",row.id().toString()));
            }
        }
        button(left,height-28,panelWidth,"关闭",this::close);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        scroll+=vertical<0?1:vertical>0?-1:0;clearAndInit();return true;
    }
    @Override public void render(DrawContext context,int mx,int my,float delta){
        context.fill(0,0,width,height,0xC0101620);context.fill(left-6,top-6,left+panelWidth+6,height-6,0xFF172738);
        context.drawTextWithShadow(textRenderer,title,left,top,0xFFFFFF);
        context.drawTextWithShadow(textRenderer,menu.width()+"×"+menu.height()+" · "+(menu.valid()?"完整屏幕":"待机：需要完整矩形，3×2 至 11×7"),left,top+16,0x8EDBFF);
        if(!menu.message().isEmpty())context.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(menu.message(),panelWidth),left,height-42,0xFFCC66);
        else if(!publicCards&&menu.matches().isEmpty())context.drawTextWithShadow(textRenderer,"当前没有可公开观看的对战",left,top+118,0xCCCCCC);
        super.render(context,mx,my,delta);
    }
    // Screen.render calls this after our panel/text; never blur that foreground.
    @Override public void renderBackground(DrawContext context,int mx,int my,float delta) { }
    @Override public boolean shouldPause(){return false;}
}
