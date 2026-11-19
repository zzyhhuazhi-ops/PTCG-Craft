package dev.hua.ptcglive.client;
import dev.hua.ptcglive.*;
import dev.hua.ptcglive.packs.*;
import dev.hua.ptcglive.network.PackIntentPayload;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Uses actual registered pools, never guesses a source from the card name or era. */
final class CardAcquisitionScreen extends Screen {
    static final String DECKS_URL="https://play.limitlesstcg.com/decks";
    private final CardCatalog.Entry card;private final Screen parent;private final List<CardViewScreen.ServerAction> actions;
    private final List<SetPackDefinition> sources;private final CardBoxArtCache art=new CardBoxArtCache(2,512);
    private double scroll;
    CardAcquisitionScreen(CardCatalog.Entry card,Screen parent,List<CardViewScreen.ServerAction> actions){
        super(Text.literal("卡牌资料与获取来源"));this.card=card;this.parent=parent;this.actions=List.copyOf(actions);
        sources=PackItems.all().stream().map(PackItem::pack).filter(p->p.pools().values().stream().anyMatch(pool->pool.containsKey(card.id())))
            .sorted(PackBrowseOrder.NEWEST).toList();
    }
    Screen parentScreen(){return parent;}
    private int split(){return Math.max(94,width*2/5);}
    private int bottom(){return height-58;}
    @Override protected void init(){
        addDrawableChild(ButtonWidget.builder(Text.literal("返回"),b->close()).dimensions(8,height-26,60,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("推荐卡组"),b->ConfirmLinkScreen.open(this,DECKS_URL)).dimensions(width-106,height-26,98,20).build());
        int w=Math.max(45,(width-16)/Math.max(1,actions.size()));
        for(int i=0;i<actions.size();i++) {var action=actions.get(i);addDrawableChild(ButtonWidget.builder(Text.literal(action.labelZh()),b->{client.setScreen(parent);action.submit().run();})
            .dimensions(8+i*w,height-50,w-4,18).build());}
    }
    @Override public void render(DrawContext c,int mx,int my,float delta){
        c.fill(0,0,width,height,PhoneUi.CANVAS);int x=split()+8,w=width-x-8;
        int h=Math.min(bottom()-34,(int)((split()-24)/.71875)),cw=(int)(h*.71875);
        CardPainter.card(c,textRenderer,art.get(card.id()),card.nameZh(),(split()-cw)/2,30,cw,h,PhoneUi.BLUE);
        c.drawText(textRenderer,Text.literal(textRenderer.trimToWidth(card.nameZh(),width-16)),8,10,PhoneUi.INK,false);
        c.drawText(textRenderer,textRenderer.trimToWidth(card.printedNumber()+" · "+card.rarityZh(),w),x,30,PhoneUi.INK,false);
        c.drawText(textRenderer,"赛制标记："+(card.regulationMark().isBlank()?"无／未核对":card.regulationMark()),x,43,PhoneUi.MUTED,false);
        c.drawText(textRenderer,"获取卡包（点击查看 · 右键购买）",x,60,PhoneUi.BLUE,false);
        c.enableScissor(x,76,width-6,bottom());
        for(int i=0;i<sources.size();i++) {
            var pack=sources.get(i);int y=76+i*33-(int)scroll;if(y+30<76||y>=bottom())continue;
            PhoneUi.card(c,x,y,w,30,PhoneUi.SURFACE);
            c.drawText(textRenderer,textRenderer.trimToWidth(pack.displayNameZh(),w-8),x+4,y+4,PhoneUi.INK,false);
            c.drawText(textRenderer,"查看卡池／前往商城",x+4,y+17,PhoneUi.BLUE,false);
        }
        if(sources.isEmpty())c.drawText(textRenderer,card.set().equals("sd100")?"来源：起始卡组100选择器":"当前卡包中无此印刷",x,82,PhoneUi.MUTED,false);
        c.disableScissor();super.render(c,mx,my,delta);
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(x<split()&&y>=28&&y<bottom()&&(button==0||button==1)){client.setScreen(new CardViewScreen(card,this));return true;}
        if(x>=split()+8&&x<width-8&&y>=76&&y<bottom()&&(button==0||button==1)) {
            int index=(int)((y-76+scroll)/33);if(index<sources.size())ClientPlayNetworking.send(new PackIntentPayload(sources.get(index).setId(),button==1?"SHOP":"BROWSE"));return true;
        }return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){scroll=Math.clamp(scroll-vertical*33,0,Math.max(0,sources.size()*33-(bottom()-76)));return true;}
    @Override public void renderBackground(DrawContext c,int x,int y,float delta){}
    @Override public boolean shouldPause(){return false;}
    @Override public void close(){client.setScreen(parent);}
    @Override public void removed(){ScreenArtLifetime.removed(this, art::close);}
}
