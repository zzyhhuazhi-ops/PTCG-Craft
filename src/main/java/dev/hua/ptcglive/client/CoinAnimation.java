package dev.hua.ptcglive.client;

import dev.hua.ptcglive.view.MatchPresentation;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.RotationAxis;
import java.util.List;
import static dev.hua.ptcglive.client.BattleEffects.*;

/** Confirmed effect tosses only. Pocket iRKxcQ3DWo0 10.2..18.55s; no random outcomes. */
final class CoinAnimation {
    static final double FIRST_STEP_MS=1600, STEP_MS=700, FLIGHT_MS=550, HOLD_MS=2000;
    static long duration(int count){return (long)((count<=1?3000:start(count-1)+FLIGHT_MS+HOLD_MS)*1e6);}
    static double start(int index){return index==0?0:FIRST_STEP_MS+(index-1)*STEP_MS;}
    static double landing(int index){return start(index)+(index==0?700:FLIGHT_MS);}
    static int groupSize(List<MatchPresentation.Cue> cues,int first){
        int end=first;
        while(end<cues.size()&&cues.get(end).kind()==MatchPresentation.Kind.COIN)end++;
        return end-first;
    }
    static int visiblePage(double ms,int count){
        int current=0;
        for(int i=1;i<count&&ms>=start(i);i++)current=i;
        return current/9;
    }
    static double opacity(double ms,int count){return smooth(ms/100)*(1-smooth((ms-duration(count)/1e6+300)/300));}
    static boolean landed(double ms,int index){return ms>=landing(index);}
    static void background(DrawContext c,int width,int height,double ms,int count,BattleAnimationEngine.Quality quality){
        if(quality==BattleAnimationEngine.Quality.OFF)return;
        double a=opacity(ms,count),settled=smooth((ms-(duration(count)/1e6-HOLD_MS))/180);
        c.fill(0,0,width,height,alpha(0xFF344663,a*.64));
        c.fillGradient(0,0,width,height,alpha(0xFF00CDE7,a*.24*settled),alpha(0xFF159DFF,a*.34*settled));
        c.draw();com.mojang.blaze3d.systems.RenderSystem.clear(256,false);
    }
    static double spacing(int width,int height){return Math.min(height*.20,width*.26);}
    static void opening(DrawContext c,net.minecraft.client.font.TextRenderer font,int width,int height,int k,
                        double ms,int result,BattleAnimationEngine.Quality quality){
        double a=opacity(ms,1),settled=smooth((ms-1400)/180);
        if(a<=0)return;
        int cx=width/2,cy=(int)(height*.48);
        int panelWidth=Math.min(width/3,124*k);
        double spread=Math.min(width*.19,height*.29)*settled;
        if(quality!=BattleAnimationEngine.Quality.OFF){
            c.fill(0,0,width,height,alpha(0xFFD9E5FA,.87*a));
            c.fillGradient(0,0,width,height,alpha(0xFF24DDEA,.82*a*settled),alpha(0xFF3AA4F1,.78*a*settled));
            c.draw();
        }
        var matrices=c.getMatrices();
        matrices.push();matrices.translate(cx-spread,cy,0);
        float scale=(float)(1.75-.75*smooth(ms/700)+.55*settled);
        matrices.scale(scale,scale,1);matrices.translate(-cx,-cy,0);
        coin(c,cx,width,height,ms,0,1,result,quality);
        matrices.pop();
        if(settled>0){
            int labelX=cx+(int)spread,y=cy-8*k;
            BattleUi.round(c,labelX-panelWidth/2,y,panelWidth,17*k,8*k,alpha(0xFF147ACE,settled*a));
            String label=result<0?"决定先后手":(result==1?"正面":"反面")+" · 决定先后手";
            BattleUi.centeredText(c,font,label,labelX,y+4*k,alpha(0xFFFFFFFF,settled*a),true,k);
        }
    }
    static void coin(DrawContext c,int cx,int width,int height,double ms,int index,int count,int result,BattleAnimationEngine.Quality quality){
        double age=ms-start(index);if(age<0||visiblePage(ms,count)!=index/9)return;
        double a=opacity(ms,count);if(a<=0)return;
        double step=spacing(width,height),r=step*.40;
        int local=index%9,columns=Math.min(3,count),rows=Math.min(3,(count+2)/3);
        double gridX=cx+(local%3-(columns-1)/2.)*step;
        double gridY=height*.48+(local/3-(rows-1)/2.)*step;
        double x=gridX,y=gridY;
        if(index==0){double p=count==1?0:smooth((ms-1400)/200);x=cx+(gridX-cx)*p;y=height*.48+(gridY-height*.48)*p;}
        double flight=index==0?700:FLIGHT_MS,p=Math.clamp(age/flight,0,1);
        y+=Math.pow(1-p,2)*height*.28-Math.sin(Math.PI*p)*height*.10;
        double angle=6*Math.PI*(1-Math.pow(1-p,2)),narrow=Math.max(.055,Math.abs(Math.cos(angle)));
        boolean heads=p>=1?result!=0:(Math.cos(angle)>=0)==(result!=0);
        double rotation=index==0?0:(index*47%180)*smooth(p);
        var matrices=c.getMatrices();matrices.push();matrices.translate(x,y,0);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float)rotation));matrices.scale(1,(float)narrow,1);
        int radius=Math.max(2,(int)(r*(.82+.18*smooth(p))));
        disc(c,0,Math.max(1,radius/14),radius,alpha(0xFF39424C,a));
        var art=result<0?null:BattleCosmetics.coin(heads);
        if(art!=null){
            // Cosmetics remain replaceable through the existing resource-pack manifest.
            c.draw();
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1,1,1,(float)a);
            try{BattleCosmetics.draw(c,art,-radius,-radius,2*radius,2*radius);c.draw();}
            finally{com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1,1,1,1);}
        }else if(result>=0)face(c,radius,heads,a);
        else {disc(c,0,0,radius,alpha(0xFF48556A,a));ring(c,0,0,radius*.78,1,alpha(0xFFC3D0DF,a));}
        matrices.pop();
        if(quality!=BattleAnimationEngine.Quality.OFF&&age>=flight&&age<flight+200){
            double flash=1-(age-flight)/200;
            BattleSceneEffects.glint(c,x-r*.8,y-r*.55,r*.14*flash,alpha(0xFFFFFFFF,a*flash));
        }
    }
    private static void face(DrawContext c,int r,boolean heads,double a){
        disc(c,0,0,r,alpha(0xFF090D13,a));
        disc(c,0,0,Math.max(1,r-2),alpha(heads?0xFFF4F7F8:0xFF171D28,a));
        if(heads){
            // Red upper hemisphere, black divider and inset button; original procedural art.
            var sink=mesh(c);double radius=r*.86;
            for(int i=0;i<24;i++){
                double t=Math.PI+i*Math.PI/24,u=t+Math.PI/24;
                int color=alpha(0xFFE52236,a);
                sink.vertex(0,0,color);sink.vertex(Math.cos(u)*radius,Math.sin(u)*radius,color);
                sink.vertex(Math.cos(t)*radius,Math.sin(t)*radius,color);sink.vertex(0,0,color);
            }
            line(c,-r*.88,0,r*.88,0,Math.max(2,r*.13),alpha(0xFF090D13,a));
            disc(c,0,0,Math.max(2,(int)(r*.29)),alpha(0xFF090D13,a));
            disc(c,0,0,Math.max(1,(int)(r*.18)),alpha(0xFFFFFFFF,a));
        }else{
            ring(c,0,0,r*.76,1,alpha(0xFF343E50,a));ring(c,0,0,r*.61,1,alpha(0xFF252F40,a));
            line(c,-r*.54,0,r*.54,0,Math.max(1,r*.06),alpha(0xFF394358,a));
            disc(c,0,0,Math.max(2,(int)(r*.21)),alpha(0xFF394358,a));
            disc(c,0,0,Math.max(1,(int)(r*.13)),alpha(0xFF171D28,a));
        }
    }
    private static double smooth(double p){p=Math.clamp(p,0,1);return p*p*(3-2*p);}
}
