package dev.hua.ptcglive.client;

import dev.hua.ptcglive.CardItem;
import dev.hua.ptcglive.rental.RentalScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

/** Native inventory controls. The background is drawn first and never blurs the foreground. */
public final class RentalScreen extends HandledScreen<RentalScreenHandler> {
    private ButtonWidget claim;
    public RentalScreen(RentalScreenHandler handler,PlayerInventory inventory,Text title){
        super(handler,inventory,title);backgroundWidth=230;backgroundHeight=208;
        titleX=8;titleY=6;playerInventoryTitleX=34;playerInventoryTitleY=115;
    }
    @Override protected void init(){
        super.init();
        claim=addDrawableChild(ButtonWidget.builder(Text.literal("提取宝可币"),button->{
            if(client!=null&&client.interactionManager!=null)client.interactionManager.clickButton(handler.syncId,0);
        }).dimensions(x+62,y+98,104,18).build());
    }
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta){
        claim.active=handler.coins()>0;
        super.render(context,mouseX,mouseY,delta);
        drawMouseoverTooltip(context,mouseX,mouseY);
        if(mouseX>=x+24&&mouseX<x+44&&mouseY>=y+74&&mouseY<y+94&&handler.getSlot(1).getStack().isEmpty())
            context.drawTooltip(textRenderer,Text.literal("红石块 1.5 倍 / 青金石块 2 倍；每分钟 1 块"),mouseX,mouseY);
    }
    @Override public void renderBackground(DrawContext context,int mouseX,int mouseY,float delta){
        // HandledScreen dispatches its panel through this method before items/widgets.
        // Draw that panel directly, omitting only the world's blur pass.
        drawBackground(context,delta,mouseX,mouseY);
    }
    @Override protected void drawBackground(DrawContext context,float delta,int mouseX,int mouseY){
        context.fill(0,0,width,height,0x99000000);
        context.fill(x,y,x+backgroundWidth,y+backgroundHeight,0xFF373737);
        context.fill(x+1,y+1,x+backgroundWidth-1,y+backgroundHeight-1,0xFF555555);
        context.fill(x+2,y+2,x+backgroundWidth-2,y+backgroundHeight-2,0xFFFFFFFF);
        context.fill(x+4,y+4,x+backgroundWidth-4,y+backgroundHeight-4,0xFFC6C6C6);
        context.fill(x+backgroundWidth-4,y+4,x+backgroundWidth-2,y+backgroundHeight-2,0xFF8B8B8B);
        context.fill(x+4,y+backgroundHeight-4,x+backgroundWidth-2,y+backgroundHeight-2,0xFF8B8B8B);
        for(var slot:handler.slots)slot(context,x+slot.x,y+slot.y);
    }
    private static void slot(DrawContext context,int x,int y){
        context.fill(x-1,y-1,x+17,y+17,0xFFFFFFFF);
        context.fill(x-1,y-1,x+16,y+16,0xFF373737);
        context.fill(x,y,x+16,y+16,0xFF8B8B8B);
    }
    private void line(DrawContext context,String text,int x,int y,int maxWidth){context.drawText(textRenderer,textRenderer.trimToWidth(text,maxWidth),x,y,0x404040,false);}
    @Override protected void drawForeground(DrawContext context,int mouseX,int mouseY){
        super.drawForeground(context,mouseX,mouseY);
        line(context,"卡牌",22,21,28);line(context,"燃料",22,65,28);
        var stack=handler.getSlot(0).getStack();var card=stack.getItem() instanceof CardItem item?item.card():null;
        line(context,card==null?"将卡牌放入左侧格子":card.nameZh(),58,22,162);
        line(context,"品质："+(card==null?"—":card.rarityZh()),58,35,162);
        int units=handler.rate()*handler.multiplier()*handler.conditionPercent()/25;
        line(context,"产量："+new java.math.BigDecimal(units).divide(java.math.BigDecimal.valueOf(8)).stripTrailingZeros().toPlainString()+" 币/分钟",58,48,162);
        line(context,"累计："+java.math.BigDecimal.valueOf(handler.coins()*8L+handler.fraction()).divide(java.math.BigDecimal.valueOf(8)).stripTrailingZeros().toPlainString()+" / "+handler.capacity(),58,61,162);
        line(context,"状态："+switch(handler.status()){case 1->"生产中";case 2->"已满，停止消耗燃料";default->"空闲";},58,74,162);
        line(context,"钱包："+handler.walletBalance(),58,87,162);
    }
}
