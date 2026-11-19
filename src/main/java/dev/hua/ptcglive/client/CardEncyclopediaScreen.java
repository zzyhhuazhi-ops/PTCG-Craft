package dev.hua.ptcglive.client;

import dev.hua.ptcglive.collection.CollectionView;
import dev.hua.ptcglive.collection.CollectionProgress;
import dev.hua.ptcglive.network.CollectionIntentPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import java.util.List;

/** Card-art browser for the existing world ledger. All buttons submit server intents only. */
public final class CardEncyclopediaScreen extends Screen {
    private CollectionView view;
    private TextFieldWidget search;
    private String selected="",message="";
    private boolean pending;
    private boolean overview;
    private int overviewPage;
    private int ticks;
    private final CardBoxArtCache art=new CardBoxArtCache(24,256);
    public CardEncyclopediaScreen(CollectionView view){super(Text.literal("卡牌图鉴"));this.view=view;}
    public String sessionId(){return view.sessionId();}
    public void update(CollectionView next,String notice){
        if(next==null){if(client.player!=null)client.player.sendMessage(Text.literal(notice),false);close();return;}
        String draft=search==null?next.query():search.getText();
        view=next;pending=false;ticks=0;message=notice;clearAndInit();search.setText(draft);
    }
    private void button(String label,int x,int y,int w,Runnable action){
        addDrawableChild(ButtonWidget.builder(Text.literal(label),b->action.run()).dimensions(x,y,w,18).build());
    }
    @Override protected void init(){
        button(overview?"返回卡牌":"收藏进度",width-94,3,86,()->{overview=!overview;overviewPage=0;clearAndInit();});
        search=new TextFieldWidget(textRenderer,8,24,Math.max(80,width-166),18,Text.literal("名称 编号 系列 稀有度 类型"));
        search.setMaxLength(80);search.setText(view.query());addDrawableChild(search);
        button("搜索",width-154,24,44,()->{if(overview)overviewPage=0;else request("PAGE","",0);});
        button(overview?"全部系列":progressMode()?view.progressFilter().labelZh():view.allCards()?"全部卡牌":"当前持有",width-106,24,98,()->{
            if(overview){search.setText("");overviewPage=0;}
            else if(progressMode())request("FILTER",CollectionProgress.Filter.values()[(view.progressFilter().ordinal()+1)%4].name(),0);
            else request("ALL","",0);
        });
        if(overview){
            button("刷新",8,height-40,60,()->request("REFRESH","",0));
            button("关闭",width-68,height-40,60,this::close);return;
        }
        if(progressMode())button("全部卡牌",width-76,46,68,()->{search.setText("");request("PROGRESS","",0);});
        int third=(width-24)/3;
        button("存入 1 张",8,height-62,third,()->request("DEPOSIT",selected,1));
        button("取出 1 张",12+third,height-62,third,()->request("WITHDRAW",selected,1));
        button("加入卡盒",16+2*third,height-62,third,()->request("ADD",selected,1));
        var box=view.boxes().stream().filter(b->b.id().equals(view.selectedBox())).findFirst();
        button("卡盒："+box.map(CollectionView.Box::name).orElse("暂无"),8,height-40,Math.max(60,width-192),()->{
            if(view.boxes().isEmpty())return;
            int index=0;for(int i=0;i<view.boxes().size();i++)if(view.boxes().get(i).id().equals(view.selectedBox()))index=i;
            request("BOX",view.boxes().get((index+1)%view.boxes().size()).id(),0);
        });
        button("盒→背包",width-180,height-40,76,()->request("BOX_TAKE",selected,1));
        button("刷新",width-100,height-40,44,()->request("REFRESH","",0));
        button("关闭",width-52,height-40,44,this::close);
    }
    private void request(String action,String id,int amount){
        if(pending)return;
        if(action.equals("PROGRESS"))selected="";
        if(List.of("DEPOSIT","WITHDRAW","ADD","BOX_TAKE").contains(action)&&id.isEmpty()){message="请先左键选择卡牌";return;}
        pending=true;ticks=0;
        ClientPlayNetworking.send(new CollectionIntentPayload(view.sessionId(),view.revision(),action,id,amount,search.getText()));
    }
    private int cellWidth(){return Math.max(1,(width-16)/6);}
    private boolean progressMode(){return view.progressPack()!=null&&!view.progressPack().isEmpty();}
    private int gridTop(){return progressMode()?80:48;}
    private int cellHeight(){return Math.max(20,(height-76-gridTop())/2);}
    private int overviewCapacity(){return Math.max(1,(height-100)/36);}
    private List<CollectionProgress.Summary> matchingPacks(){
        String q=search==null?"":search.getText().strip().toLowerCase(java.util.Locale.ROOT);
        return view.progress().stream().filter(p->(p.nameZh()+" "+p.setId()).toLowerCase(java.util.Locale.ROOT).contains(q)).toList();
    }
    private int hit(double x,double y){
        if(overview||x<8||x>=8+cellWidth()*6||y<gridTop()||y>=gridTop()+cellHeight()*2)return -1;
        int index=(int)((y-gridTop())/cellHeight())*6+(int)((x-8)/cellWidth());
        return index<view.rows().size()?index:-1;
    }
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta){
        String title=overview?"收藏进度册":"卡牌图鉴 · "+(view.page()+1)+"/"+view.pages()+" 页";
        context.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(title,Math.max(1,width-112)),8,7,0xffe1bd67);
        if(overview){renderProgress(context,mouseX,mouseY);super.render(context,mouseX,mouseY,delta);return;}
        if(progressMode()){
            var summary=view.progress().stream().filter(p->p.setId().equals(view.progressPack())).findFirst();
            summary.ifPresent(p->{
                context.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(p.nameZh(),Math.max(1,width-92)),8,49,0xffffd46a);
                String text="已收集 "+p.collected()+"/"+p.total()+" · 缺 "+p.missing()+" · 重复 "+p.duplicates()+" · "+String.format(java.util.Locale.ROOT,"%.1f%%",p.percent());
                context.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(text,width-16),8,68,0xffffffff);
            });
        }
        for(int i=0;i<view.rows().size();i++){
            var row=view.rows().get(i);int x=8+(i%6)*cellWidth(),y=gridTop()+(i/6)*cellHeight();
            int held=row.inventory()+row.collection()+row.boxed();
            CardPainter.card(context,textRenderer,art.get(row.card().id()),row.card().nameZh(),x+2,y,cellWidth()-4,Math.max(1,cellHeight()-24),
                row.card().id().equals(selected)?0xffffd46a:0xff717680);
            String label=textRenderer.trimToWidth(row.card().nameZh(),cellWidth()-4);
            context.drawTextWithShadow(textRenderer,label,x+2,y+cellHeight()-22,0xffffffff);
            context.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(held==0?"未收集":"持有 "+held+" 张",cellWidth()-4),x+2,y+cellHeight()-12,held==0?0xffffaaaa:0xffaaffaa);
        }
        super.render(context,mouseX,mouseY,delta);
        String footer=message.isBlank()?"滚轮翻页 · 右键大图 · 只统计背包、图鉴存卡和随身卡盒":message;
        context.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(footer,width-16),8,height-16,0xffffdb90);
        int index=hit(mouseX,mouseY);
        if(index>=0){var row=view.rows().get(index);context.drawTooltip(textRenderer,CardRentalInfo.append(List.of(
            Text.literal(row.card().label()),Text.literal(row.type()+" · "+row.card().set()),
            Text.literal(dev.hua.ptcglive.rules.PrintedRegulationMarks.labelZh(row.card().id())),
            Text.literal("背包 "+row.inventory()+" · 图鉴 "+row.collection()+" · 已装盒 "+row.boxed()),
            Text.literal("可加入："+(row.inventory()+row.collection())+" 张 · "+(row.discovered()?"曾发现（不代表持有）":"未发现"))),row.card()),mouseX,mouseY);}
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(overview&&button==0&&x>=8&&x<width-8&&y>=48&&y<48+overviewCapacity()*36){
            var packs=matchingPacks();int index=overviewPage*overviewCapacity()+(int)((y-48)/36);
            if(index<packs.size()&&!pending){String id=packs.get(index).setId();overview=false;search.setText("");request("PROGRESS",id,0);return true;}
        }
        int index=hit(x,y);
        if(index>=0){var row=view.rows().get(index);if(button==0){selected=row.card().id();return true;}
            if(button==1){client.setScreen(new CardAcquisitionScreen(row.card(),this,java.util.List.of()));return true;}}
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(overview&&vertical!=0){int pages=Math.max(1,(matchingPacks().size()+overviewCapacity()-1)/overviewCapacity());overviewPage=Math.max(0,Math.min(pages-1,overviewPage+(vertical>0?-1:1)));return true;}
        if(vertical!=0){int page=view.page()+(vertical>0?-1:1);if(page>=0&&page<view.pages())request("PAGE","",page);return true;}
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public void tick(){super.tick();if(++ticks>=(pending?80:20)){pending=false;request("REFRESH","",0);}}
    @Override public void renderBackground(DrawContext context,int x,int y,float delta){ }
    @Override public boolean shouldPause(){return false;}
    @Override public void removed(){ScreenArtLifetime.removed(this, art::close);super.removed();}
    @Override public void close(){ClientPlayNetworking.send(new CollectionIntentPayload(view.sessionId(),view.revision(),"CLOSE","",0,""));super.close();}
    private void renderProgress(DrawContext context,int mouseX,int mouseY){
        var packs=matchingPacks();int capacity=overviewCapacity(),pages=Math.max(1,(packs.size()+capacity-1)/capacity);
        overviewPage=Math.min(overviewPage,pages-1);
        for(int i=0;i<capacity;i++){
            int index=overviewPage*capacity+i;if(index>=packs.size())break;
            var p=packs.get(index);int y=48+i*36;
            context.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(p.nameZh(),width-20),10,y,0xffffd46a);
            String text="已收集 "+p.collected()+"/"+p.total()+" · 缺 "+p.missing()+" · 重复 "+p.duplicates()+" · "+String.format(java.util.Locale.ROOT,"%.1f%%",p.percent());
            context.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(text,width-20),10,y+11,0xffffffff);
            context.fill(10,y+23,width-10,y+27,0xff343a40);
            context.fill(10,y+23,10+(int)((width-20)*p.percent()/100),y+27,0xffcaa64b);
        }
        String hint="第 "+(overviewPage+1)+"/"+pages+" 页 · 滚轮翻页 · 点击卡包查看缺卡";
        context.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(hint,width-16),8,height-56,0xffffffff);
        context.drawTextWithShadow(textRenderer,textRenderer.trimToWidth("仅统计当前持有；同卡在不同卡包的进度分别展示，不叠加为总收藏。",width-16),8,height-16,0xffffdb90);
    }
}
