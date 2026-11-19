package dev.hua.ptcglive.client;

import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;

/** Public card replacement. Real-time calibration: wQEQlaFtchA, 294.7..297.1s.
 * The original W_Y5ZtllqI8 short is approximately 2x speed; retain its shape curves only. */
final class DevolutionAnimation {
    private static final double TIME_SCALE=2;
    static final double REPLACE_MS=320*TIME_SCALE, LAND_MS=700*TIME_SCALE, DURATION_MS=1200*TIME_SCALE;
    static final long DURATION_NS=(long)(DURATION_MS*1_000_000);
    private DevolutionAnimation() { }
    static double lift(double ms){ms/=TIME_SCALE;return smooth(ms/140)*(1-smooth((ms-440)/(700-440)));}
    static boolean previous(double ms){return ms<REPLACE_MS;}
    static double veil(double ms){ms/=TIME_SCALE;return smooth((ms-100)/160)*(1-smooth((ms-410)/190));}
    static Rect lifted(Rect target,double ms){
        double lift=lift(ms);int grow=(int)(target.width()*.035*lift);
        return new Rect(target.x()-grow,target.y()-(int)(target.height()*.08*lift)-grow,
            target.width()+2*grow,target.height()+2*grow);
    }
    static void draw(BattleEffectMesh.Sink mesh,Rect card,double ms,boolean low){
        if(ms<0||ms>=LAND_MS)return;
        double coverage=veil(ms);ms/=TIME_SCALE;
        double w=card.width(),h=card.height(),x=card.x()+w/2,y=card.y()+h/2;
        double swirl=smooth(ms/70)*(1-smooth((ms-130)/150));
        if(swirl>0){
            int count=low?1:2;
            for(int arm=0;arm<count;arm++)for(int j=0;j<22;j++){
                double a=ms*.013+arm*Math.PI+j*.06,b=a+.06;
                double alpha=swirl*Math.sin(j*Math.PI/22)*.65;
                BattleEffectMesh.line(mesh,x+Math.cos(a)*w*.68,y+Math.sin(a)*h*.62,
                    x+Math.cos(b)*w*.68,y+Math.sin(b)*h*.62,w*.07,color(0x59E8FF,alpha));
            }
        }
        double a=coverage;if(a<=0)return;
        // Nested soft rectangular bands, rather than the old circular evolution rings.
        double clear=smooth((ms-430)/170),coverH=h*(1-clear);
        int bands=low?5:12;
        for(int i=bands;i>=0;i--){
            double edge=i/(double)bands,pad=edge*w*.15;
            quad(mesh,card.x()-pad,card.y()-pad,w+pad*2,coverH+pad*2,
                color(i==0?0x0650E8:0x3095FF,a*(i==0?.88:.055)));
        }
        BattleEffectMesh.glow(mesh,x,card.y()+coverH/2,w*.8,coverH*.8,color(0x0037E8,a*.7));
        // An upward clearing boundary reveals the lower card before the blue cap vanishes.
        if(clear>0){
            double lineY=card.y()+h*(1-clear);
            BattleEffectMesh.line(mesh,card.x(),lineY,card.right(),lineY,Math.max(1,w*.025),color(0xAEEDFF,a*.55));
        }
    }
    private static void quad(BattleEffectMesh.Sink out,double x,double y,double w,double h,int c){
        out.vertex(x,y,c);out.vertex(x,y+h,c);out.vertex(x+w,y+h,c);out.vertex(x+w,y,c);
    }
    private static int color(int rgb,double a){return ((int)(Math.clamp(a,0,1)*255)<<24)|rgb;}
    private static double smooth(double p){p=Math.clamp(p,0,1);return p*p*(3-2*p);}
}
