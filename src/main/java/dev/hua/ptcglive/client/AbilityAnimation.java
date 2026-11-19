package dev.hua.ptcglive.client;

import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import static dev.hua.ptcglive.client.BattleEffects.*;

/** Public ability reveal, sampled against Pocket259.075..260.650s. No effect settlement. */
final class AbilityAnimation {
    static final long DURATION_NS=1_600_000_000L;
    static final double RETURN_MS=1400, LAND_MS=1520;
    static double lift(double ms){return smooth(ms/120)*(1-smooth((ms-RETURN_MS)/(LAND_MS-RETURN_MS)));}
    static double opacity(double ms){return smooth(ms/90)*(1-smooth((ms-RETURN_MS)/120));}
    static Rect focus(Rect card,double ms){
        double p=lift(ms),scale=1+.12*p;
        int w=(int)Math.round(card.width()*scale),h=(int)Math.round(card.height()*scale);
        return new Rect(card.x()+(card.width()-w)/2,card.y()+(card.height()-h)/2-(int)(card.height()*.10*p),w,h);
    }
    static void backlight(DrawContext c,Rect card,double ms,BattleAnimationEngine.Quality quality){
        if(quality==BattleAnimationEngine.Quality.OFF)return;
        double strength=opacity(ms)*(1-smooth((ms-420)/500));
        if(strength<=0)return;
        for(int i=0;i<8;i++){
            double angle=i*Math.PI/4,rx=card.width()*.55,ry=card.height()*.5;
            glow(c,(int)(card.x()+card.width()/2.+Math.cos(angle)*rx),(int)(card.y()+card.height()/2.+Math.sin(angle)*ry),
                card.width()*.34,0xFFFFF36A,strength*(quality==BattleAnimationEngine.Quality.LOW?.3:.95));
        }
    }
    static void foreground(DrawContext c,Rect card,double ms,BattleAnimationEngine.Quality quality){
        if(quality==BattleAnimationEngine.Quality.OFF)return;
        double burst=Math.max(0,1-Math.abs(ms-225)/105)*opacity(ms);
        if(burst>0)BattleUi.round(c,card.x(),card.y(),card.width(),card.height(),2,alpha(0xFFFFFFDF,Math.min(1,burst*1.3)));
        double a=opacity(ms)*(1-smooth((ms-650)/750));
        int count=quality==BattleAnimationEngine.Quality.LOW?6:18;
        for(int i=0;i<count;i++){
            double age=ms-100-i*11;if(age<0)continue;
            double angle=i*2.399963,spread=.52+Math.min(1,age/850)*.16;
            double x=card.x()+card.width()*(.5+Math.cos(angle)*spread),y=card.y()+card.height()*(.5+Math.sin(angle)*spread);
            double blink=Math.pow(Math.max(0,Math.sin(age/83+i*1.7)),4);
            BattleSceneEffects.glint(c,x,y,card.width()*(.018+.032*blink),alpha(i%3==0?0xFFFFFF94:0xFFFFFFFF,a*(.25+.75*blink)));
        }
    }
    static void banner(DrawContext c,TextRenderer font,Rect card,String name,double ms,int screenWidth){
        double a=opacity(ms);if(a<.04)return;
        String label=name.equals("特性发动")?"发动":name;
        label=font.trimToWidth(label,Math.min(230,screenWidth-66));
        int tag=30,w=Math.max((int)(card.width()*1.6),font.getWidth(label)+tag+14),h=15;
        w=Math.min(screenWidth-12,w);
        int x=Math.clamp(card.x()+card.width()/2-w/2,6,Math.max(6,screenWidth-w-6)),y=Math.max(5,card.y()-h-7);
        BattleUi.round(c,x,y,w,h,2,alpha(0xFFFFE62F,a));
        BattleUi.round(c,x+3,y+3,tag,9,1,alpha(0xFFAB1734,a));
        c.drawText(font,"特性",x+tag/2-4,y+3,alpha(0xFFFFFFFF,a),false);
        c.drawText(font,label,x+tag+7,y+3,alpha(0xFF382F15,a),false);
    }
    private static double smooth(double p){p=Math.clamp(p,0,1);return p*p*(3-2*p);}
}
