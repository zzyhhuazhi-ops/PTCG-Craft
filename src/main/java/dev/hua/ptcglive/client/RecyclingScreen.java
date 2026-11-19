package dev.hua.ptcglive.client;

import dev.hua.ptcglive.CardItems;
import dev.hua.ptcglive.economy.*;
import dev.hua.ptcglive.network.RecycleIntent;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** A station panel: staged selection only, followed by a server-issued, one-use confirmation. */
public final class RecyclingScreen extends Screen {
 private RecyclingView view;
 private final PhoneScreen phoneParent;
 private final List<RecyclingService.Row> rows=new ArrayList<>();
 private final Set<String> selected=new LinkedHashSet<>();
 private final SmoothMotion scroll=new SmoothMotion();
 private final CardBoxArtCache art=new CardBoxArtCache(24,128);
 private boolean pending;private int waited,confirmDelay;private String message="",action="";private double renderedScroll;
 public RecyclingScreen(RecyclingView view){this(view,null);}
 public RecyclingScreen(RecyclingView view,PhoneScreen parent){super(Text.literal("卡牌回收站"));this.view=view;this.phoneParent=parent;rows.addAll(view.rows());}
 public String sessionId(){return view.session();}
 private boolean confirming(){return !view.confirmation().isEmpty();}
 private int left(){return (width-panelWidth())/2;}
 private int panelWidth(){return Math.min(720,width-16);}
 private int listTop(){return 149;}
 private int listBottom(){return Math.max(listTop()+10,height-66);}
 private int maxScroll(){return Math.max(0,rows.size()*44-(listBottom()-listTop()));}
 public void update(RecyclingView next,String notice) {
  if(next==null){if(client!=null&&client.player!=null&&!notice.isBlank())client.player.sendMessage(Text.literal(notice),false);super.close();return;}
  boolean changed=view.source()!=next.source()||!view.rarities().equals(next.rarities())||!view.confirmation().equals(next.confirmation());
  if(!next.confirmation().isEmpty()&&!next.confirmation().equals(view.confirmation()))confirmDelay=10;
  if(changed||action.equals("REFRESH")||action.equals("CONFIRM")){selected.clear();scroll.jump(0);}
  if(next.offset()==0)rows.clear();
  if(next.offset()==rows.size())rows.addAll(next.rows());
  else if(next.offset()!=0){rows.clear();rows.addAll(next.rows());scroll.jump(0);}
  view=next;message=notice==null?"":notice;pending=false;waited=0;scroll.clamp(0,maxScroll());clearAndInit();
 }
 private void request(String kind,String argument) {
  if(pending)return;pending=true;waited=0;action=kind;
  ClientPlayNetworking.send(new RecycleIntent(view.session(),view.revision(),kind,argument));clearAndInit();
 }
 private void button(String label,int x,int y,int w,int color,boolean enabled,Runnable run) {
  var button=new PhoneButton(x,y,w,20,label,color,color==PhoneUi.BLUE||color==PhoneUi.RED,ignored->run.run());
  button.active=enabled&&!pending;addDrawableChild(button);
 }
 @Override protected void init() {
  int x=left(),w=panelWidth(),part=(w-6)/3;
  if(!confirming()) {
   var sources=RecyclingService.Source.values();String[] labels={"背包取卡","图鉴取卡","背包＋图鉴"};
   for(int i=0;i<3;i++){var source=sources[i];button(labels[i],x+i*(part+3),34,part,view.source()==source?PhoneUi.BLUE:PhoneUi.SURFACE_ALT,true,()->request("SOURCE",source.name()));}
   int cell=(w-12)/5;
   for(int i=0;i<RecyclingService.RARITIES.size();i++) {
    String code=RecyclingService.RARITIES.get(i);String label=(switch(code){case "SPECIAL"->"特殊";case "PROMO"->"宣传";case "UNKNOWN"->"待核对";default->code;})+" "+RecyclingService.price(code)+"币";
    button(label,x+(i%5)*(cell+3),59+(i/5)*23,cell,view.rarities().contains(code)?PhoneUi.BLUE:PhoneUi.SURFACE_ALT,true,()->{
     var next=new LinkedHashSet<>(view.rarities());if(!next.remove(code))next.add(code);request("RARITIES",String.join(",",next));
    });
   }
   button("一键回收筛选卡",x,height-34,part,PhoneUi.BLUE,view.count()>0,()->request("PREVIEW_ALL",""));
   button("回收所选卡种",x+part+3,height-34,part,PhoneUi.BLUE,!selected.isEmpty(),()->request("PREVIEW_SELECTED",new com.google.gson.Gson().toJson(selected)));
   button("刷新",x+2*(part+3),height-34,(part-3)/2,PhoneUi.SURFACE_ALT,true,()->request("REFRESH",""));
   button("关闭",x+2*(part+3)+(part+3)/2,height-34,(part-3)/2,PhoneUi.SURFACE_ALT,true,this::close);
  } else {
   button(confirmDelay>0?"请先核对回收明细":"确认回收 · "+view.coins()+"币",x+(w+6)/2,height-34,(w-6)/2,PhoneUi.RED,confirmDelay==0,()->request("CONFIRM",view.confirmation()));
   button("取消，保留卡牌",x,height-34,(w-6)/2,PhoneUi.SURFACE_ALT,true,()->request("CANCEL",""));
  }
 }
 @Override public void tick() {
  scroll.tick();
  if(confirmDelay>0&&--confirmDelay==0)clearAndInit();
  if(pending&&++waited>100){pending=false;message="响应较慢，请刷新；服务端会拒绝重复确认。";clearAndInit();}
  if(!pending&&rows.size()<view.totalRows()&&scroll.target()>=maxScroll()-88)request("LOAD",Integer.toString(rows.size()));
 }
 @Override public void render(DrawContext context,int mouseX,int mouseY,float delta) {
  context.fill(0,0,width,height,0xDD0C182A);int x=left(),w=panelWidth();
  PhoneUi.panel(context,x-5,5,w+10,height-10,PhoneUi.CANVAS);
  context.drawText(textRenderer,confirming()?"二次确认：卡牌将永久回收":"卡牌回收站",x,13,confirming()?PhoneUi.RED:PhoneUi.INK,false);
  String balance="宝可币 "+view.balance();context.drawText(textRenderer,balance,x+w-textRenderer.getWidth(balance),13,PhoneUi.BLUE,false);
  if(confirming()) {
   line(context,"将回收 "+view.count()+" 张，获得 "+view.coins()+" 宝可币",x,39,PhoneUi.RED,w);
   line(context,"请核对下方卡牌、数量和金额；取消不会扣除任何卡牌。",x,58,PhoneUi.INK,w);
   line(context,"同一卡牌：能量留64／训练家留16／宝可梦留8；卡盒不参与。",x,77,PhoneUi.INK,w);
   line(context,"确认时重新核对资产；数量或来源变化将取消本次回收。",x,96,PhoneUi.MUTED,w);
  } else line(context,"多选罕度／卡种；能量留64、训练家留16、宝可梦留8",x,134,PhoneUi.MUTED,w);
  renderedScroll=scroll.render(delta);scroll.clamp(0,maxScroll());
  context.enableScissor(x,listTop(),x+w,listBottom());
  for(int i=Math.max(0,(int)renderedScroll/44);i<rows.size();i++) {
   int y=listTop()+i*44-(int)renderedScroll;if(y>=listBottom())break;
   var row=rows.get(i);var card=CardItems.byDefinitionId(row.id()).card();
   boolean chosen=confirming()||selected.contains(row.id());
   PhoneUi.panel(context,x,y,w-4,41,chosen?PhoneUi.SELECTED:PhoneUi.SURFACE);
   CardPainter.card(context,textRenderer,art.get(row.id()),card.nameZh(),x+4,y+2,25,36,PhoneUi.BLUE);
   String title=(chosen?"✓ ":"")+card.nameZh()+" · "+card.printedNumber()+" · "+card.rarityZh();
   line(context,title,x+35,y+4,PhoneUi.INK,w-45);
   line(context,"背包 "+row.inventory()+" / 图鉴 "+row.collection()+" · "+row.unitValue()+"币/张",x+35,y+16,PhoneUi.MUTED,w-45);
   line(context,(row.amount()==0?"未超保留数量（"+row.keepCount()+"张）":"回收 "+row.amount()+" 张 → "+((long)row.amount()*row.unitValue())+" 宝可币"),x+35,y+28,row.amount()==0?PhoneUi.MUTED:PhoneUi.BLUE,w-45);
  }
  if(rows.isEmpty())line(context,"没有符合筛选的实际卡牌",x+8,listTop()+10,PhoneUi.MUTED,w-16);
  context.disableScissor();
  if(maxScroll()>0){int h=Math.max(10,(listBottom()-listTop())*(listBottom()-listTop())/Math.max(1,rows.size()*44));int y=listTop()+(int)(renderedScroll/maxScroll()*(listBottom()-listTop()-h));context.fill(x+w-3,y,x+w,y+h,PhoneUi.BLUE);}
  long chosenCoins=rows.stream().filter(r->selected.contains(r.id())).mapToLong(r->(long)r.unitValue()*r.amount()).sum();
  line(context,"筛选可回收 "+view.count()+" 张 / "+view.coins()+"币"+(confirming()?"":" · 所选 "+chosenCoins+"币"),x,height-60,PhoneUi.INK,w);
  line(context,confirming()?"按类型合计保留64／16／8张；回收不可撤销。":"点击卡片选择；滚轮连续浏览。两种来源同时回收时优先扣背包。",x,height-48,PhoneUi.MUTED,w);
  line(context,pending?"正在请求服务器……":message,x,height-11,PhoneUi.RED,w);
  super.render(context,mouseX,mouseY,delta);
 }
 // Screen.render invokes the default blur after our card list. This in-world panel draws its own background.
 @Override public void renderBackground(DrawContext context,int mouseX,int mouseY,float delta) { }
 private void line(DrawContext context,String text,int x,int y,int color,int width){context.drawText(textRenderer,textRenderer.trimToWidth(text,Math.max(10,width)),x,y,color,false);}
 @Override public boolean mouseClicked(double x,double y,int button) {
  if(super.mouseClicked(x,y,button))return true;
  if(button==0&&!pending&&!confirming()&&x>=left()&&x<left()+panelWidth()&&y>=listTop()&&y<listBottom()) {
   int index=(int)((y-listTop()+renderedScroll)/44);
   if(index>=0&&index<rows.size()&&rows.get(index).amount()>0) {
    String id=rows.get(index).id();if(!selected.remove(id)){if(selected.size()>=500){message="更多卡种请使用按罕度一键回收";return true;}selected.add(id);}clearAndInit();
   }else if(index>=0&&index<rows.size())message="这张卡须至少保留 "+rows.get(index).keepCount()+" 张，当前没有多余数量。";
   return true;
  }return false;
 }
 @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
  if(y>=listTop()&&y<listBottom()){scroll.move(-vertical*44,0,maxScroll());return true;}return super.mouseScrolled(x,y,horizontal,vertical);
 }
 @Override public boolean shouldPause(){return false;}
 @Override public void close(){ClientPlayNetworking.send(new RecycleIntent(view.session(),view.revision(),"CLOSE",""));if(phoneParent!=null){phoneParent.resumeAfterRecycling();client.setScreen(phoneParent);}else super.close();}
 @Override public void removed(){art.close();super.removed();}
}
