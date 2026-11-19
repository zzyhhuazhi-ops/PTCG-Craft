package dev.hua.ptcglive.client;

import com.google.gson.Gson;
import dev.hua.ptcglive.ai.AiDifficulty;
import dev.hua.ptcglive.network.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Two owner-authorized box selectors; editing reuses the existing editor. */
public final class AiPracticeScreen extends Screen {
    private final BlockPos table;private AiPracticeState.View view;
    private String mine="",computer="",rule="",message="";private boolean pending;
    private AiDifficulty difficulty=AiDifficulty.NORMAL;
    private double scroll,target;private int contentHeight;
    public AiPracticeScreen(AiPracticeState state){super(Text.literal("挑战电脑"));table=state.tablePos();update(state);}
    public BlockPos tablePos(){return table;}
    public void update(AiPracticeState state){
        view=new Gson().fromJson(state.json(),AiPracticeState.View.class);message=state.message();pending=false;
        if(box(mine)==null)mine=view.boxes().isEmpty()?"":view.boxes().getFirst().id();
        if(box(computer)==null)computer=mine;
        if(!view.rules().containsKey(rule))rule=view.defaultRule();
        if(client!=null)clearAndInit();
    }
    private AiPracticeState.Box box(String id){return view==null?null:view.boxes().stream().filter(b->b.id().equals(id)).findFirst().orElse(null);}
    private boolean checked(){var c=view.check();return c!=null&&mine.equals(c.playerBox().toString())&&computer.equals(c.computerBox().toString())&&rule.equals(c.ruleset())&&difficulty==c.difficulty();}
    private int left(){return Math.max(8,(width-650)/2);}private int panelWidth(){return width-2*left();}
    private int top(){return 138;}private int bottom(){return Math.max(top()+20,height-56);}
    private void send(String action){pending=true;message="等待服务器检查…";String token=checked()?view.check().token():"";
        ClientPlayNetworking.send(new TableIntentPayload(table,action,mine+","+computer+","+rule+","+token+","+difficulty.name()));clearAndInit();}
    public void refresh(){pending=true;message="刷新卡盒，旧检查不再使用…";ClientPlayNetworking.send(new TableIntentPayload(table,"AI_MENU",""));clearAndInit();}
    private void button(String label,int x,int y,int w,boolean enabled,Runnable action){var b=ButtonWidget.builder(Text.literal(textRenderer.trimToWidth(label,w-8)),v->action.run()).dimensions(x,y,w,20).build();b.active=enabled;addDrawableChild(b);}
    private String nextBox(String id){for(int i=0;i<view.boxes().size();i++)if(view.boxes().get(i).id().equals(id))return view.boxes().get((i+1)%view.boxes().size()).id();return view.boxes().getFirst().id();}
    private void edit(String id){pending=true;ClientPlayNetworking.send(new TableIntentPayload(table,"AI_EDIT",id));clearAndInit();}
    @Override protected void init(){
        if(view==null)return;int x=left(),w=panelWidth();
        button("返回卡桌",x,8,80,true,()->client.setScreen(new CardTableScreen(table)));
        button("刷新卡盒",x+w-80,8,80,!pending,this::refresh);
        var my=box(mine);var ai=box(computer);
        button(my==null?"我的卡组：请携带卡盒":"我的卡组："+my.name()+"（"+my.count()+"/60） ▾",x,34,w-60,!pending&&my!=null,()->{mine=nextBox(mine);clearAndInit();});
        button("编辑",x+w-56,34,56,!pending&&my!=null,()->edit(mine));
        button(ai==null?"AI 的卡组：请携带卡盒":"AI 的卡组："+ai.name()+"（"+ai.count()+"/60） ▾",x,60,w-60,!pending&&ai!=null,()->{computer=nextBox(computer);scroll=target=0;clearAndInit();});
        button("编辑",x+w-56,60,56,!pending&&ai!=null,()->edit(computer));
        button("规则包："+view.rules().getOrDefault(rule,rule)+" ▾",x,86,w,!pending&&!view.rules().isEmpty(),()->{var ids=new ArrayList<>(view.rules().keySet());rule=ids.get((ids.indexOf(rule)+1)%ids.size());clearAndInit();});
        int half=(w-6)/2;
        button("难度："+difficulty.label()+" ▾",x,112,w,!pending,()->{var all=AiDifficulty.values();difficulty=all[(difficulty.ordinal()+1)%all.length];clearAndInit();});
        button("检查并分析",x,height-26,half,!pending&&my!=null&&ai!=null,()->{scroll=target=0;send("AI_CHECK");});
        button("确认开始",x+half+6,height-26,w-half-6,!pending&&checked()&&view.check().allowed(),()->send("AI_START"));
    }
    private List<String> lines(){
        var lines=new ArrayList<String>();var ai=box(computer);if(ai==null){lines.add("请选择现有卡盒；允许双方选择同一份配置。");return lines;}
        lines.add(mine.equals(computer)?"镜像练习：使用同一份卡表，双方对局实例独立。":"AI 只使用所选卡盒的临时快照，不转移或发放收藏卡。");
        lines.add(difficulty.description());
        if(checked()){
            var c=view.check();lines.add("规则支持 · 我的卡组："+c.playerRules().statusZh()+" / AI："+c.computerRules().statusZh());
            c.playerRules().issues().forEach(i->lines.add("我的卡组："+i.messageZh()));c.computerRules().issues().forEach(i->lines.add("AI 卡组："+i.messageZh()));
            lines.add("操作支持："+(c.operations().isEmpty()?"已知必要选择检查通过":"无法完成以下步骤"));lines.addAll(c.operations());
            lines.add("策略理解："+(c.analysis().warnings().isEmpty()?"已识别基础作用":"部分效果策略评价较弱（允许合法练习）"));
            var a=c.analysis();lines.addAll(a.tendency());
            lines.add("可提供能量："+a.energyTypes().stream().sorted().map(AiPracticeScreen::energyName).collect(java.util.stream.Collectors.joining("、")));
            if(!a.attackers().isEmpty())lines.add("攻击手候选（随局面调整）："+a.attackers().stream().limit(5).map(id->view.names().getOrDefault(id,id)).collect(java.util.stream.Collectors.joining("、")));
            for(var edge:a.evolutionLines()){String[] ids=edge.split(" → ");lines.add("进化："+view.names().getOrDefault(ids[0],ids[0])+" → "+view.names().getOrDefault(ids[1],ids[1]));}
            lines.add("检查不能穷尽全部卡牌组合；运行时不可完成的强制选择会安全中止。");
        }else lines.add("规则 / 操作 / 策略：尚未检查当前选择。编辑卡盒后须重新检查。");
        lines.add("AI 卡表 · "+ai.name()+" · "+ai.count()+" 张");ai.cards().forEach((id,n)->lines.add(n+" × "+view.names().getOrDefault(id,id)+" ["+id+"]"));
        if(checked()&&!view.check().analysis().warnings().isEmpty()){lines.add("策略限制明细");lines.addAll(view.check().analysis().warnings());}return lines;
    }
    private static String energyName(dev.hua.ptcglive.engine.field.EnergyType t){return switch(t){case GRASS->"草";case FIRE->"火";case WATER->"水";case LIGHTNING->"雷";case PSYCHIC->"超";case FIGHTING->"斗";case DARKNESS->"恶";case METAL->"钢";case FAIRY->"妖";case DRAGON->"龙";case COLORLESS->"无色";};}
    @Override public void render(DrawContext c,int mx,int my,float delta){
        renderInGameBackground(c);int x=left(),w=panelWidth();c.fill(x-4,4,x+w+4,height-4,0xee172638);c.drawCenteredTextWithShadow(textRenderer,title,width/2,13,0xffeed079);
        c.fill(x,top(),x+w,bottom(),0xffe8eef5);c.enableScissor(x,top(),x+w,bottom());scroll+=(target-scroll)*.28;int y=top()+7-(int)scroll;
        for(var line:lines())for(var row:textRenderer.wrapLines(Text.literal(line),w-18)){c.drawText(textRenderer,row,x+7,y,0xff20344c,false);y+=13;}
        contentHeight=y-top()+(int)scroll+7;target=Math.max(0,Math.min(target,Math.max(0,contentHeight-bottom()+top())));c.disableScissor();
        if(contentHeight>bottom()-top()){int bar=Math.max(12,(bottom()-top())*(bottom()-top())/contentHeight);int sy=top()+(int)(scroll/Math.max(1,contentHeight-bottom()+top())*(bottom()-top()-bar));c.fill(x+w-3,sy,x+w,sy+bar,0xff3b84df);}
        int messageY=height-49;for(var row:textRenderer.wrapLines(Text.literal(message),w-8)){if(messageY>height-35)break;c.drawText(textRenderer,row,x+4,messageY,0xffffd484,false);messageY+=11;}super.render(c,mx,my,delta);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(y>=top()&&y<=bottom()){target=Math.max(0,Math.min(Math.max(0,contentHeight-bottom()+top()),target-vertical*34));return true;}return super.mouseScrolled(x,y,horizontal,vertical);}
    @Override public boolean shouldPause(){return false;}
    /** Keep the background already drawn before the panel; do not blur the completed content. */
    @Override public void renderBackground(DrawContext context,int mouseX,int mouseY,float delta) { }
    @Override public void close(){client.setScreen(new CardTableScreen(table));}
}
