package dev.hua.ptcglive.client;

import dev.hua.ptcglive.collectibles.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import java.util.*;

public final class CollectibleScreen extends HandledScreen<CollectibleHandler> {
    private final List<ButtonWidget> actions=new ArrayList<>(),orders=new ArrayList<>();
    private ButtonWidget previousPage,nextPage;
    private float uiScale=1;
    public CollectibleScreen(CollectibleHandler handler,PlayerInventory inventory,Text title){super(handler,inventory,title);backgroundWidth=310;backgroundHeight=310;titleX=10;titleY=8;playerInventoryTitleX=75;playerInventoryTitleY=215;}
    private ButtonWidget button(String label,int px,int py,int w,int id){return addDrawableChild(ButtonWidget.builder(Text.literal(label),b->{if(client!=null&&client.interactionManager!=null)client.interactionManager.clickButton(handler.syncId,id);}).dimensions(x+px,y+py,w,18).build());}
    @Override protected void init(){super.init();uiScale=Math.max(.1f,Math.min(1f,Math.min((width-12)/310f,(height-12)/310f)));x=Math.round((width/uiScale-310)/2);y=Math.round((height/uiScale-310)/2);actions.clear();orders.clear();for(int i=0;i<4;i++)actions.add(button("",10+i%2*147,170+i/2*19,142,i));for(int i=0;i<3;i++)orders.add(button("选择",116,33+i*42,50,10+i));previousPage=button("← 上一页",12,120,91,20);nextPage=button("下一页 →",207,120,91,21);}
    @Override public void renderBackground(DrawContext c,int mx,int my,float delta){drawBackground(c,delta,mx,my);}
    @Override protected void drawBackground(DrawContext c,float delta,int mx,int my){
        c.fill(0,0,(int)Math.ceil(width/uiScale),(int)Math.ceil(height/uiScale),0x99000000);c.fill(x,y,x+310,y+310,0xFF38394A);c.fill(x+2,y+2,x+308,y+308,0xFFF3F2F5);c.fill(x+4,y+4,x+306,y+306,0xFFCBCAD5);
        c.fill(x+4,y+4,x+306,y+22,0xFF62617C);
        c.fill(x+6,y+25,x+174,y+149,0xFFB5B3C4);c.fill(x+178,y+25,x+303,y+149,0xFFB5B3C4);
        if(handler.kind()==CollectibleBlocks.Kind.ALBUM){
            c.fill(x+7,y+25,x+303,y+112,0xFF263455);c.fill(x+11,y+28,x+149,y+108,0xFFF4EDD8);c.fill(x+158,y+28,x+299,y+108,0xFFF4EDD8);
            c.fill(x+149,y+28,x+158,y+108,0xFFB5AA91);for(int yy=35;yy<104;yy+=18){c.fill(x+146,y+yy,x+161,y+yy+3,0xFF9B9FAA);c.fill(x+147,y+yy,x+160,y+yy+1,0xFFFFFFFF);}
        }
        for(var slot:handler.slots)if(slot.isEnabled()){int sx=x+slot.x,sy=y+slot.y;c.fill(sx-1,sy-1,sx+17,sy+17,0xFFFFFFFF);c.fill(sx-1,sy-1,sx+16,sy+16,0xFF494954);c.fill(sx,sy,sx+16,sy+16,0xFF8C8B99);}
    }
    @Override public void render(DrawContext c,int mx,int my,float delta){
        var kind=handler.kind();for(var b:actions)b.visible=false;for(var b:orders)b.visible=kind==CollectibleBlocks.Kind.EXCHANGE;
        previousPage.visible=nextPage.visible=kind==CollectibleBlocks.Kind.ALBUM;
        previousPage.active=handler.page()>0&&handler.getCursorStack().isEmpty();nextPage.active=handler.page()<99&&handler.getCursorStack().isEmpty();
        if(kind==CollectibleBlocks.Kind.EXCHANGE){label(0,"提交订单 · "+handler.value(6)+" 币");label(1,"从图鉴填充");for(int i=0;i<3;i++){orders.get(i).setMessage(Text.literal(handler.value(3+i)<0?"已完成":handler.value(1)==i?"✓ 已选":"选择"));orders.get(i).active=handler.value(3+i)>=0;}}
        if(kind==CollectibleBlocks.Kind.APPRAISER){label(0,"鉴定 · 100 宝可币");actions.getFirst().active=!handler.getSlot(0).getStack().isEmpty()&&!CardCondition.graded(handler.getSlot(0).getStack())&&handler.balance()>=100;}
        if(kind==CollectibleBlocks.Kind.HOLOGRAM){label(0,handler.value(10)==0?"原画放大":"恢复大小");label(1,"特效："+List.of("关闭","闪光","光环").get(handler.value(7)));label(2,"背景："+List.of("无","星空","极光").get(handler.value(8)));label(3,"灯光："+List.of("蓝紫","金色","青绿").get(handler.value(9)));}
        c.getMatrices().push();c.getMatrices().scale(uiScale,uiScale,1);int px=(int)(mx/uiScale),py=(int)(my/uiScale);super.render(c,px,py,delta);drawMouseoverTooltip(c,px,py);c.getMatrices().pop();
    }
    private void label(int i,String s){actions.get(i).visible=true;actions.get(i).active=true;actions.get(i).setMessage(Text.literal(s));}
    private void line(DrawContext c,String s,int px,int py,int width){c.drawText(textRenderer,textRenderer.trimToWidth(s,width),px,py,0x323244,false);}
    @Override protected void drawForeground(DrawContext c,int mx,int my){
        c.drawText(textRenderer,title,10,8,0xFFFFFF,false);line(c,"背包",75,215,50);line(c,"钱包 "+handler.balance(),177,8,125);
        switch(handler.kind()){
            case EXCHANGE->{for(int i=0;i<3;i++)line(c,handler.value(3+i)<0?"已完成":"基础奖励 "+handler.value(3+i),18,53+i*42,140);line(c,"刷新 "+handler.value(2)/60+"分"+handler.value(2)%60+"秒",10,154,280);line(c,"订单输入（逐格对应）",181,29,122);}
            case APPRAISER->{line(c,"品质永久固定，每张仅一次",10,33,160);line(c,"六种品质等概率",10,49,160);line(c,"已鉴定卡不可存入图鉴",10,65,160);line(c,"可入卡盒，对战后取回背包",10,81,165);var q=CardCondition.of(handler.getSlot(0).getStack());if(q!=null){line(c,q.label,181,62,122);line(c,"售价 / 租赁 "+q.percent+"%",181,79,122);}}
            case HOLOGRAM->{line(c,"将卡牌放入右侧槽位",10,34,165);line(c,"原画、旋转与光效实时显示",10,52,165);line(c,"取出后停止投影",10,70,165);}
            case ALBUM->{line(c,"收藏卡册 · 900 个卡位",17,34,128);line(c,"每页 9 格，共 100 页",17,52,128);line(c,"手动放取，独立收藏",17,70,128);line(c,"保留卡牌品质与数据",17,88,128);line(c,"第 "+(handler.page()+1)+" / 100 页",114,125,90);}
        }
    }
    @Override public boolean mouseClicked(double x,double y,int button){return super.mouseClicked(x/uiScale,y/uiScale,button);}
    @Override public boolean mouseReleased(double x,double y,int button){return super.mouseReleased(x/uiScale,y/uiScale,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return super.mouseDragged(x/uiScale,y/uiScale,button,dx/uiScale,dy/uiScale);}
    @Override public boolean mouseScrolled(double x,double y,double h,double v){return super.mouseScrolled(x/uiScale,y/uiScale,h,v);}

}
