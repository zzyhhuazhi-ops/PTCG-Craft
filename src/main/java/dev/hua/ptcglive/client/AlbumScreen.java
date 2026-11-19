package dev.hua.ptcglive.client;

import dev.hua.ptcglive.CardItem;
import dev.hua.ptcglive.collectibles.CollectibleHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

/** Open binder presentation; all physical transfers still use vanilla slot transactions. */
public final class AlbumScreen extends HandledScreen<CollectibleHandler> {
    private final CardBoxArtCache art=new CardBoxArtCache(36,512);
    private ButtonWidget previous,next;
    private float scale=1;
    private int shownPage=-1;
    private long turnedAt;
    private int turnDirection=1;
    public AlbumScreen(CollectibleHandler handler,PlayerInventory inventory,Text title){
        super(handler,inventory,title);backgroundWidth=420;backgroundHeight=352;
    }
    @Override protected void init(){
        super.init();scale=Math.min(1.5f,Math.min((width-12)/420f,(height-12)/352f));
        scale=Math.max(.1f,scale);x=Math.round((width/scale-backgroundWidth)/2);y=Math.round((height/scale-backgroundHeight)/2);
        previous=addDrawableChild(ButtonWidget.builder(Text.literal("← 上一页"),b->turn(-1)).dimensions(x+16,y+240,90,18).build());
        next=addDrawableChild(ButtonWidget.builder(Text.literal("下一页 →"),b->turn(1)).dimensions(x+314,y+240,90,18).build());
    }
    private boolean turning(){return Util.getMeasuringTimeMs()-turnedAt<280;}
    private void turn(int direction){
        if(turning()||!handler.getCursorStack().isEmpty()||direction<0&&handler.page()==0||direction>0&&handler.page()==98)return;
        if(client!=null&&client.interactionManager!=null)client.interactionManager.clickButton(handler.syncId,direction<0?20:21);
    }
    @Override public void renderBackground(DrawContext c,int mx,int my,float delta){drawBackground(c,delta,mx,my);}
    @Override protected void drawBackground(DrawContext c,float delta,int mx,int my){
        c.fill(0,0,(int)Math.ceil(width/scale),(int)Math.ceil(height/scale),0x77000000);
        // Leather cover, page edges, dark transparent pockets and a stitched spine.
        c.fill(x+3,y+7,x+417,y+240,0xFF17120F);c.fill(x,y,x+420,y+235,0xFF4A3324);
        c.drawBorder(x+3,y+3,414,229,0xFF9C7852);
        for(int line=0;line<3;line++)c.fill(x+10,y+234+line*2,x+410,y+235+line*2,0xFFB3A181);
        for(int leaf=0;leaf<2;leaf++){
            int lx=x+12+205*leaf;c.fill(lx,y+14,lx+190,y+228,0xFF181916);
            c.drawBorder(lx+2,y+16,186,210,0xFF3A352B);
            for(int yy=22;yy<224;yy+=6){c.fill(lx+4,y+yy,lx+6,y+yy+2,0xFF665342);c.fill(lx+184,y+yy,lx+186,y+yy+2,0xFF665342);}
        }
        c.fill(x+202,y+12,x+218,y+229,0xFF8B765C);c.fill(x+204,y+12,x+208,y+229,0xFFB49A77);
        for(int yy:new int[]{62,126,190}){
            c.fill(x+195,y+yy-3,x+224,y+yy+5,0xFF141310);c.fill(x+195,y+yy-2,x+224,y+yy+2,0xFF9AA2A5);
            c.fill(x+197,y+yy-2,x+222,y+yy,0xFFE2E3DE);
        }
        for(int i=0;i<18;i++)pocket(c,x+CollectibleHandler.albumX(i),y+CollectibleHandler.albumY(i),false);
        c.fill(x+121,y+263,x+299,y+351,0xFF272B30);c.drawBorder(x+121,y+263,178,88,0xFF8B9099);
        for(int i=18;i<handler.slots.size();i++){
            Slot s=handler.getSlot(i);c.fill(x+s.x-1,y+s.y-1,x+s.x+17,y+s.y+17,0xFF93999F);c.fill(x+s.x,y+s.y,x+s.x+16,y+s.y+16,0xFF44494E);
        }
    }
    private void pocket(DrawContext c,int px,int py,boolean hover){
        c.fill(px-2,py-2,px+47,py+63,0xFF0C1012);c.drawBorder(px-2,py-2,49,65,hover?0xFFFFD677:0xFF515252);
        c.fill(px-1,py-1,px+46,py,0xFF777B7C);c.fill(px-1,py+61,px+46,py+62,0xFF383E40);
    }
    @Override protected void drawSlot(DrawContext c,Slot slot){
        if(slot.id>=18){super.drawSlot(c,slot);return;}
        var stack=slot.getStack();
        if(stack.getItem() instanceof CardItem item){
            var image=art.get(item.card().id());if(image!=null)CardPainter.art(c,image,slot.x+1,slot.y+1,43,59);
            else c.drawItem(stack,slot.x+14,slot.y+22);
        }else c.drawCenteredTextWithShadow(textRenderer,Text.literal("+"),slot.x+22,slot.y+26,0x555E64);
    }
    @Override protected void drawForeground(DrawContext c,int mx,int my){
        c.drawCenteredTextWithShadow(textRenderer,title,210,2,0xFFF0D5);
        c.drawCenteredTextWithShadow(textRenderer,Text.literal((handler.page()+1)+" / 100"),105,224,0xCFC4AE);
        c.drawCenteredTextWithShadow(textRenderer,Text.literal((handler.page()+2)+" / 100"),310,224,0xCFC4AE);
        c.drawCenteredTextWithShadow(textRenderer,Text.literal("手动放取 · 900 卡位"),210,245,0xFFF0D5);
        c.drawText(textRenderer,"A / D 翻页",12,277,0xFFFFFF,true);
        c.drawText(textRenderer,"右键 查看大图",12,294,0xFFFFFF,true);
        c.drawText(textRenderer,"左键 放入 / 取回",12,311,0xFFFFFF,true);
        c.drawText(textRenderer,"独立收藏",315,277,0xFFF0D5,true);
        c.drawText(textRenderer,"不连接图鉴",315,294,0xC7C7C7,true);
    }
    @Override public void render(DrawContext c,int mx,int my,float delta){
        if(shownPage!=handler.page()){
            if(shownPage>=0){turnDirection=Integer.compare(handler.page(),shownPage);turnedAt=Util.getMeasuringTimeMs();}
            shownPage=handler.page();
        }
        previous.active=handler.page()>0&&handler.getCursorStack().isEmpty()&&!turning();
        next.active=handler.page()<98&&handler.getCursorStack().isEmpty()&&!turning();
        c.getMatrices().push();c.getMatrices().scale(scale,scale,1);
        int px=(int)(mx/scale),py=(int)(my/scale);super.render(c,px,py,delta);
        if(focusedSlot!=null&&focusedSlot.id<18)c.drawBorder(x+focusedSlot.x-2,y+focusedSlot.y-2,49,65,0xFFFFD677);
        if(turning()){
            float t=(Util.getMeasuringTimeMs()-turnedAt)/280f;
            // A leaf folds about the binder rings; interaction waits until it settles.
            int fold=(int)(184*Math.sin(Math.PI*t));int spine=x+210;
            int left=turnDirection>0?spine-fold:spine;
            c.fill(left,y+17,left+fold,y+215,0xEE282924);
            c.fill(left,y+17,left+2,y+215,0xFFB1A88D);
        }else drawMouseoverTooltip(c,px,py);
        c.getMatrices().pop();
    }
    // Native slot hit-testing expands only album pockets, never backpack cells.
    @Override protected boolean isPointWithinBounds(int sx,int sy,int w,int h,double mx,double my){
        if(w==16&&h==16)for(int i=0;i<18;i++)if(sx==CollectibleHandler.albumX(i)&&sy==CollectibleHandler.albumY(i))
            return mx>=x+sx-2&&mx<x+sx+47&&my>=y+sy-2&&my<y+sy+63;
        return super.isPointWithinBounds(sx,sy,w,h,mx,my);
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(turning())return true;
        if(button==1&&handler.getCursorStack().isEmpty())for(int i=0;i<18;i++){
            Slot s=handler.getSlot(i);
            if(isPointWithinBounds(s.x,s.y,16,16,mx/scale,my/scale)&&s.getStack().getItem() instanceof CardItem card){
                client.setScreen(new CardViewScreen(card.card(),this));return true;
            }
        }
        return super.mouseClicked(mx/scale,my/scale,button);
    }
    @Override public boolean mouseReleased(double mx,double my,int button){return super.mouseReleased(mx/scale,my/scale,button);}
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){return super.mouseDragged(mx/scale,my/scale,button,dx/scale,dy/scale);}
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){if(vertical!=0)turn(vertical>0?-1:1);return true;}
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==GLFW.GLFW_KEY_A||key==GLFW.GLFW_KEY_LEFT){turn(-1);return true;}
        if(key==GLFW.GLFW_KEY_D||key==GLFW.GLFW_KEY_RIGHT){turn(1);return true;}
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public void removed(){ScreenArtLifetime.removed(this, art::close);super.removed();}
}
