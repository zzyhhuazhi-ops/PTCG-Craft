package dev.hua.ptcglive.client;

import dev.hua.ptcglive.engine.MatchSession;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;
import java.util.UUID;

/** Development-only read-only view. Server updates keep flowing to the parent match. */
final class ElectricImpactDebugScreen extends Screen implements MatchDisplay {
    static final double[] KEY_TIMES={-67,0,50,83,116,150,183,216,250,283,316,350,383,450,516,583};
    private final MatchTableScreen parent;
    private final String cardId;
    private final int confirmedDamage;
    private final long seed;
    private final CardBoxArtCache artwork=new CardBoxArtCache(4,512,true);
    private final LightningAnimationRenderer.State effect=new LightningAnimationRenderer.State();
    private ElectricImpactProfile profile;
    private TextFieldWidget time,arcs,color,power,duration;
    private double ms,speed=1;
    private long last=System.nanoTime();
    private boolean playing,screenshot;
    private int layerMask=0xFF,batch=-1;
    private Path output;
    private String message="数值来自最近一次已确认的电属性伤害；调试不发送操作。";
    ElectricImpactDebugScreen(MatchTableScreen parent,String cardId,int damage,long seed,ElectricImpactProfile profile){
        super(Text.literal("电击逐帧调试"));this.parent=parent;this.cardId=cardId==null?"unknown":cardId;confirmedDamage=damage;this.seed=seed;this.profile=profile;effect.reset(profile,seed);
    }
    Screen parentScreen(){return parent;}
    public UUID matchId(){return parent.matchId();}
    public void update(MatchSession.Snapshot next,String message){parent.update(next,message);if(next==null&&client!=null)client.setScreen(null);}
    public boolean shouldPause(){return false;}
    @Override public void renderBackground(DrawContext context,int mouseX,int mouseY,float delta){}
    private void button(int x,int y,int w,String label,Runnable action){addDrawableChild(ButtonWidget.builder(Text.literal(label),b->action.run()).dimensions(x,y,w,18).build());}
    private TextFieldWidget field(int x,int y,int w,String label,String value){var f=new TextFieldWidget(textRenderer,x,y,w,18,Text.literal(label));f.setText(value);addDrawableChild(f);return f;}
    protected void init(){
        time=field(8,23,62,"毫秒",String.format(java.util.Locale.ROOT,"%.1f",ms));
        button(74,23,40,"定位",()->{try{seek(Double.parseDouble(time.getText()));}catch(RuntimeException e){message="请输入有效毫秒数。";}});
        button(118,23,60,"播放/暂停",()->playing=!playing);
        button(182,23,46,"前一帧",()->seek(ms-1000/60.0));button(232,23,46,"后一帧",()->seek(ms+1000/60.0));
        button(282,23,58,"1×/0.25×",()->speed=speed==1?.25:1);
        button(344,23,48,"截图",()->screenshot=true);button(396,23,64,"导出16帧",()->{playing=false;batch=0;});
        arcs=field(8,66,42,"主闪电数量",Integer.toString(profile.mainArcCount()));
        color=field(54,66,70,"外缘RGB",String.format("%06X",profile.outerColor()&0xFFFFFF));
        power=field(128,66,52,"强度",Double.toString(profile.intensity()));
        duration=field(184,66,58,"总时长",Double.toString(profile.durationMs()));
        button(246,66,48,"应用",()->{try{profile=profile.edited(Integer.parseInt(arcs.getText()),0xFF000000|Integer.parseInt(color.getText().replace("#",""),16),Double.parseDouble(power.getText()),Double.parseDouble(duration.getText()));effect.reset(profile,seed);message="参数已应用，仅影响调试预览。";}catch(RuntimeException e){message="参数无效：主闪电1～8、RGB十六进制、强度0～2、时长不超过3000ms。";}});
        button(298,66,50,"全部层",()->layerMask=0xFF);
        for(int i=0;i<8;i++){final int layer=i;button(8+i*47,91,43,"仅层"+i,()->layerMask=1<<layer);}
        button(Math.max(8,width-54),Math.max(120,height-24),46,"返回",this::close);
    }
    private void seek(double value){if(!Double.isFinite(value))throw new IllegalArgumentException();ms=Math.clamp(value,-67,profile.durationMs()+1100);playing=false;last=System.nanoTime();}
    public void render(DrawContext c,int mx,int my,float delta){
        long now=System.nanoTime();if(playing)ms+=Math.max(0,now-last)/1e6*speed;last=now;
        if(batch>=0)ms=KEY_TIMES[batch];
        if(ms>profile.durationMs()+1100){ms=profile.durationMs()+1100;playing=false;}
        c.fill(0,0,width,height,0xFFE2EDF4);
        double size=Math.max(28,Math.min(width*.16,(height-130)*.40)),x=width*.5,y=130+(height-130)*.48;
        if((layerMask&1)!=0){
            c.fill(0,125,width,height,0xFFB7D6E3);
            BattleUi.card(c,textRenderer,artwork.get(cardId),"目标卡牌",(int)(x-size/2),(int)(y-size*.7),(int)size,(int)(size*1.4),0xFFFFFFFF);
        }
        effect.sample(ms);
        for(int layer=1;layer<=6;layer++)if((layerMask&(1<<layer))!=0){
            if(layer==4&&BattleGlowShader.draw(c,x,y,size*1.4,effect,ms))continue;
            LightningAnimationRenderer.layer(BattleEffects.mesh(c),effect,x,y,size,ms,BattleAnimationEngine.Quality.HIGH,layer);
        }
        if((layerMask&128)!=0&&profile.numberOpacity(ms)>0){
            var m=c.getMatrices();m.push();m.translate(x,y,0);float scale=(float)(profile.numberScale(ms)*1.7);m.scale(scale,scale,1);
            var text=Text.literal(Integer.toString(confirmedDamage)).styled(s->s.withFont(net.minecraft.util.Identifier.of("ptcglive_mc","battle_numbers")));
            int left=-textRenderer.getWidth(text)/2;
            double opacity=profile.numberOpacity(ms);
            for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)if(dx!=0||dy!=0)c.drawText(textRenderer,text,left+dx,dy,BattleEffects.alpha(0xFF233747,opacity),false);
            c.drawText(textRenderer,text,left,0,BattleEffects.alpha(0xFFFFFFFF,opacity),false);m.pop();
        }
        c.drawText(textRenderer,"电击逐帧调试  |  "+String.format(java.util.Locale.ROOT,"%.1f ms · %.2f×",ms,speed),8,8,0xFF183544,false);
        c.drawText(textRenderer,"数量    外缘RGB    强度      时长(ms)",8,53,0xFF183544,false);
        c.drawText(textRenderer,textRenderer.trimToWidth(message,width-16),8,114,0xFF183544,false);
        if(!time.isFocused())time.setText(String.format(java.util.Locale.ROOT,"%.1f",ms));
        super.render(c,mx,my,delta);c.draw();
        if(screenshot||batch>=0){
            try{
                if(output==null){output=Path.of("D:/CODEX/PTCG-Craft-vfx-debug",Long.toString(System.currentTimeMillis()));Files.createDirectories(output);}
                try(var shot=ScreenshotRecorder.takeScreenshot(client.getFramebuffer())){shot.writeTo(output.resolve(String.format(java.util.Locale.ROOT,"%07.1fms-layer%02x.png",ms,layerMask)));}
                message="截图已保存："+output;
            }catch(Exception e){message="截图失败："+e.getMessage();}
            screenshot=false;if(batch>=0&&++batch>=KEY_TIMES.length)batch=-1;
        }
    }
    public boolean keyPressed(int key,int scan,int modifiers){
        if(getFocused() instanceof TextFieldWidget)return super.keyPressed(key,scan,modifiers);
        if(key==GLFW.GLFW_KEY_SPACE){playing=!playing;return true;}
        if(key==GLFW.GLFW_KEY_RIGHT){seek(ms+1000/60.0);return true;}
        if(key==GLFW.GLFW_KEY_LEFT){seek(ms-1000/60.0);return true;}
        return super.keyPressed(key,scan,modifiers);
    }
    public void close(){client.setScreen(parent);}
    public void removed(){artwork.close();}
}
