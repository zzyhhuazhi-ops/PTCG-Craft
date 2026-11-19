package dev.hua.ptcglive.client;

import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;
import static dev.hua.ptcglive.client.BattleEffects.alpha;

/** Live's six face-down choices and confirmed reveal; no score or hidden card lookup. */
final class PrizeCardAnimation {
    static final double FLIP_MS=300, HOLD_MS=1600, END_MS=1767;
    static final double CHOICE_ENTER_MS=700;
    static final double SELECTION_FLASH_MS=650;
    private PrizeCardAnimation(){}
    // Live reference numbers run bottom/top in each column. Slots retain server identity.
    static Rect choice(int width,int height,int slot){
        int h=Math.max(20,Math.min((height-60)/2,(int)(height*.34))),w=h*5/7,gap=Math.max(3,h/8);
        int left=(width-3*w-2*gap)/2,top=(height-2*h-gap)/2-(int)(height*.036);
        return new Rect(left+(slot/2)*(w+gap),top+(1-slot%2)*(h+gap),w,h);
    }
    static Rect reveal(int width,int height){
        int h=(int)(height*.479),w=h*5/7;return new Rect((width-w)/2,(int)(height*.554)-h/2,w,h);
    }
    static Rect bar(int width,int height){return new Rect(width/6,(int)(height*.88),width-width/6,Math.max(16,(int)(height*.08)));}
    static Rect confirm(int width,int height){
        var bar=bar(width,height);int w=Math.max(40,width*8/100),h=Math.max(12,height*4/100);
        return new Rect(width*72/100,bar.y()+(bar.height()-h)/2,w,h);
    }
    static EvolutionAnimation.Pose choicePose(Rect source,Rect target,int slot,double ms){
        double p=smooth((ms-267-slot*20)/300);
        return new EvolutionAnimation.Pose(lerp(cx(source),cx(target),p),lerp(cy(source),cy(target),p)-Math.sin(Math.PI*p)*target.height()*.22,
            lerp(source.width(),target.width(),p),lerp(source.height(),target.height(),p),0,(1-p)*-12,0,0);
    }
    static EvolutionAnimation.Pose pose(Rect from,Rect center,Rect hand,double ms){
        double lift=out(ms/267),fly=smooth((ms-HOLD_MS)/(END_MS-HOLD_MS));
        double shrink=smooth((ms-HOLD_MS)/100);
        double x=lerp(cx(from),cx(center),lift),y;
        if(ms<200)y=lerp(cy(from),cy(center)+center.height()*.32,smooth(ms/200));
        else if(ms<233)y=cy(center)+center.height()*lerp(.32,.22,(ms-200)/33);
        else if(ms<267)y=cy(center)+center.height()*lerp(.22,.066,(ms-233)/34);
        else if(ms<300)y=cy(center)+center.height()*lerp(.066,-.111,(ms-267)/33);
        else y=cy(center)-center.height()*.111*(1-Math.pow(Math.clamp((ms-300)/333,0,1),2));
        double grow=ms<200?0:ms<233?lerp(0,.077,(ms-200)/33):ms<267?lerp(.077,.533,(ms-233)/34):ms<300?lerp(.533,1,(ms-267)/33):1;
        double w=lerp(from.width(),center.width(),grow),h=lerp(from.height(),center.height(),grow);
        double flip=ms<200?.499*out(ms/100):.5+.5*Math.sqrt(Math.clamp((ms-200)/100,0,1));
        return new EvolutionAnimation.Pose(lerp(x,cx(hand),fly),lerp(y,cy(hand),fly),
            lerp(w,hand.width(),shrink),lerp(h,hand.height(),shrink),flip,0,0,0);
    }
    static double dim(double ms){return ms<0||ms>=END_MS?0:.62*(1-smooth((ms-HOLD_MS)/134));}
    static void mesh(BattleEffectMesh.Sink sink,Rect center,double ms,BattleAnimationEngine.Quality q){
        if(q==BattleAnimationEngine.Quality.OFF||ms<0||ms>=HOLD_MS)return;
        if(ms<233){
            double flash=Math.sin(Math.PI*ms/233),x=cx(center),y=cy(center),w=center.width(),h=center.height();
            int petals=q==BattleAnimationEngine.Quality.LOW?5:10;
            for(int i=0;i<petals;i++){
                double angle=i*Math.PI*2/petals+.24,cs=Math.cos(angle),sn=Math.sin(angle);
                double px=x+cs*w*.85,py=y+sn*h*.4;
                BattleEffectMesh.glow((vx,vy,c)->sink.vertex(px+vx*cs-vy*sn,py+vx*sn+vy*cs,c),0,0,w*.6,w*.11,alpha(0xFFC0A6FF,flash*.45));
            }
        }
        ms=(ms-150)/2;if(ms<0||ms>=650)return;
        double a=out(ms/80)*(1-smooth((ms-330)/230)),x=cx(center),y=cy(center),w=center.width(),h=center.height();
        double warm=out((ms-90)/80)*(1-smooth((ms-260)/160));
        cloud(sink,x,y+h*.08,w*1.7,h*1.25,ms,alpha(0xFFBA7AE7,a*out((ms-150)/120)*.9));
        cloud(sink,x+w*.5,y-h*.44,w*1.6,h*1.15,ms+240,alpha(0xFFFFC987,warm*.80));
        BattleEffectMesh.glow(sink,x-w*.75,y+h*.45,w*1.4,h*.9,alpha(0xFFFFCE84,warm*.75));
        BattleEffectMesh.glow(sink,x-w*.35,y+h*.15,w*.62,h*.7,alpha(0xFFFFC895,warm*.58));
        BattleEffectMesh.glow(sink,x+w*.45,y-h*.7,w*.55,h*.48,alpha(0xFFFFD694,warm*.5));
        BattleEffectMesh.glow(sink,x,y,w*4.5,h*.25,alpha(mix(0xFFFFF1BD,0xFFD7A7FF,smooth((ms-180)/160)),a*(1-smooth((ms-340)/170))*.95));
        BattleEffectMesh.glow(sink,x,y,w*2.4,h*.10,alpha(0xFFFFF9DC,a*(1-smooth((ms-120)/160))*.55));
        if(q!=BattleAnimationEngine.Quality.LOW){
            double light=out((ms-130)/70)*(1-smooth((ms-340)/130));
            double drift=(ms-230)*.0003;
            curve(sink,3,x,y,w,h,drift,w*.28,alpha(0xFFFF87C9,light*.26));
            curve(sink,3,x,y,w,h,drift,w*.08,alpha(0xFFFF83BC,light*.30));
            curve(sink,3,x,y,w,h,drift-.07,w*.09,alpha(0xFFFFA1D5,light*.16));
        }
        if(q!=BattleAnimationEngine.Quality.LOW)for(int arc=0;arc<3;arc++){
            double appear=out((ms-50-arc*8)/55),fade=1-Math.clamp((ms-200)/200,0,1);
            int tint=arc==1?0xFFFFA1DA:0xFFFFECAE;
            double drift=(ms-100)/600.*(arc==1?-.5:.3);
            curve(sink,arc,x,y,w,h,drift,w*.028,alpha(tint,appear*fade*.30));
            curve(sink,arc,x,y,w,h,drift,w*.008,alpha(tint,appear*fade));
        }
        if(q!=BattleAnimationEngine.Quality.LOW){
            double light=out((ms-80)/80)*(1-smooth((ms-300)/100));
            for(int arc=0;arc<4;arc++)for(int i=0;i<40;i++){
                double t=i/40.,u=(i+1)/40.,angle=arc*Math.PI/2+.65,cs=Math.cos(angle),sn=Math.sin(angle);
                double v=wispy(t,ms,arc),z=wispy(u,ms,arc);
                double px=x+w*(cs*(.55+t*.65)-sn*v),py=y+h*(sn*(.35+t*.18)+cs*v);
                double qx=x+w*(cs*(.55+u*.65)-sn*z),qy=y+h*(sn*(.35+u*.18)+cs*z);
                double opacity=Math.sin(t*Math.PI)*light;
                BattleEffectMesh.line(sink,px,py,qx,qy,w*.018,alpha(0xFFFFE5AE,opacity*.25));
                BattleEffectMesh.line(sink,px,py,qx,qy,w*.006,alpha(0xFFFFFFF2,opacity*.85));
            }
        }
        int count=q==BattleAnimationEngine.Quality.LOW?8:q==BattleAnimationEngine.Quality.MEDIUM?40:64;
        for(int i=0;i<count;i++){
            double spreadScale=.78+.45*out(ms/500);
            double px=x+(((i*73+19)%101)/100.-.5)*w*3.5*spreadScale,py=y+(((i*47+7)%97)/96.-.5)*h*2*spreadScale-ms*.015;
            double light=out((ms-45-i*1.5)/40)*(1-smooth((ms-240-i*4)/200))*a,size=w*(.018+.008*(i%3));
            if(i<32&&i%3!=0){
                double spread=smooth((ms-90)/180);
                double startX=i<16?x-w*(.55+((i*31)%53)/53.*.7):x+(((i*29)%61)/60.-.5)*w*.9;
                double startY=i<16?y+(((i*47)%97)/96.-.5)*h*.35:y-h*(.08+((i*19)%73)/73.*.4);
                px=lerp(startX,px,spread);
                py=lerp(startY,py,spread);
                size*=lerp(2,1,spread);
                light=out((ms-25-i%16)/20)*(1-smooth((ms-240-i*4)/200))*a;
            }
            int c=alpha(0xFFFFFFFF,light);
            if(i<32?i%2==0:i%5==0)BattleEffectMesh.glow(sink,px,py,size*2,alpha(0xFFFFD782,light*.45));
            if(i%6==0)for(int point=0;point<10;point++){
                double theta=point*Math.PI/5-Math.PI/2,next=theta+Math.PI/5;
                double goldSize=w*(.028+.015*(i%4));
                double r0=goldSize*(point%2==0?1:.42),r1=goldSize*(point%2==0?.42:1);
                int gold=alpha(0xFFFFECA8,light);
                sink.vertex(px,py,gold);sink.vertex(px+Math.cos(next)*r1,py+Math.sin(next)*r1,gold);
                sink.vertex(px+Math.cos(theta)*r0,py+Math.sin(theta)*r0,gold);sink.vertex(px,py,gold);
                int clear=gold&0xFFFFFF;
                sink.vertex(px+Math.cos(theta)*r0,py+Math.sin(theta)*r0,gold);
                sink.vertex(px+Math.cos(next)*r1,py+Math.sin(next)*r1,gold);
                sink.vertex(px+Math.cos(next)*(r1+1),py+Math.sin(next)*(r1+1),clear);
                sink.vertex(px+Math.cos(theta)*(r0+1),py+Math.sin(theta)*(r0+1),clear);
            }
            else for(int ray=0;ray<4;ray++){
                double angleRay=ray*Math.PI/2+i*.41,dx=Math.cos(angleRay),dy=Math.sin(angleRay),length=size*(ray%2==0?1:1.4),base=size*.28;
                sink.vertex(px-dy*base,py+dx*base,c);sink.vertex(px+dx*length,py+dy*length,c);
                sink.vertex(px+dy*base,py-dx*base,c);sink.vertex(px,py,c);
            }
        }
        for(int i=0;i<(q==BattleAnimationEngine.Quality.LOW?3:15);i++){
            double angle=i*2.399963,r=.8+.17*(i%4),light=out((ms-110-i*7)/160)*(1-smooth((ms-370)/260));
            double px=x+Math.cos(angle)*w*r,py=y+Math.sin(angle)*h*.6+r*ms*.025,rad=w*(.022+.012*(i%4))*1.4;
            BattleEffectMesh.glow(sink,px,py,rad*1.3,alpha(0xFFECE3A2,light*.45));
            BattleEffectMesh.ellipse(sink,px,py,rad,rad,alpha(0xFFEEE9BE,light*.18));
        }
    }
    /** Broad, softly mottled volume rather than a narrow Gaussian spot hidden behind the card. */
    private static void cloud(BattleEffectMesh.Sink out,double x,double y,double rx,double ry,double ms,int tint){
        if((tint>>>24)==0)return;
        for(int row=0;row<22;row++)for(int col=0;col<20;col++){
            double a=col/10.-1,b=(col+1)/10.-1,c=row/11.-1,d=(row+1)/11.-1;
            out.vertex(x+a*rx,y+c*ry,cloudColor(a,c,ms,tint));
            out.vertex(x+a*rx,y+d*ry,cloudColor(a,d,ms,tint));
            out.vertex(x+b*rx,y+d*ry,cloudColor(b,d,ms,tint));
            out.vertex(x+b*rx,y+c*ry,cloudColor(b,c,ms,tint));
        }
    }
    private static int cloudColor(double x,double y,double ms,int tint){
        double dx=x+.07*Math.sin(y*6+ms*.003),dy=y+.05*Math.sin(x*7-ms*.004);
        double body=Math.pow(Math.max(0,1-dx*dx-dy*dy),1.6);
        double curl=.75+.25*Math.sin(x*11+Math.sin(y*9-ms*.007))*Math.cos(y*13+x*3+ms*.004);
        double edge=Math.clamp((1-Math.max(Math.abs(x),Math.abs(y)))*12,0,1);
        return alpha(tint,(tint>>>24)/255.*body*curl*edge);
    }
    static void frame(BattleEffectMesh.Sink out,Rect r,int color,boolean selected){
        for(int band=4;band>=0;band--){
            double width=1+band*(selected?4:2);int c=alpha(band==0?0xFFFFFBE8:color,band==0?.95:selected?.17:.08);
            BattleEffectMesh.line(out,r.x(),r.y(),r.right(),r.y(),width,c);
            BattleEffectMesh.line(out,r.right(),r.y(),r.right(),r.bottom(),width,c);
            BattleEffectMesh.line(out,r.right(),r.bottom(),r.x(),r.bottom(),width,c);
            BattleEffectMesh.line(out,r.x(),r.bottom(),r.x(),r.y(),width,c);
        }
    }
    /** Joined strip: adjacent segments share position, normal and opacity, including feather edges. */
    private static void curve(BattleEffectMesh.Sink out,int arc,double x,double y,double w,double h,double drift,double width,int tint){
        if((tint>>>24)==0)return;
        for(int i=0;i<64;i++){
            double t=i/64.,u=(i+1)/64.;
            double px=x+curveX(arc,t)*w,py=y+(curveY(arc,t)+drift)*h;
            double qx=x+curveX(arc,u)*w,qy=y+(curveY(arc,u)+drift)*h;
            double ax=(curveX(arc,t+.0001)-curveX(arc,t-.0001))*w,ay=(curveY(arc,t+.0001)-curveY(arc,t-.0001))*h;
            double bx=(curveX(arc,u+.0001)-curveX(arc,u-.0001))*w,by=(curveY(arc,u+.0001)-curveY(arc,u-.0001))*h;
            double al=Math.hypot(ax,ay),bl=Math.hypot(bx,by),anx=-ay/al,any=ax/al,bnx=-by/bl,bny=bx/bl;
            double a=Math.sin(t*Math.PI),b=Math.sin(u*Math.PI),ar=width*.5*a,br=width*.5*b;
            int ac=alpha(tint,a*(tint>>>24)/255.),bc=alpha(tint,b*(tint>>>24)/255.);
            for(int band=0;band<4;band++){
                double lo=band/4.*2-1,hi=(band+1)/4.*2-1;
                double loA=(ar+.7)*lo,hiA=(ar+.7)*hi,loB=(br+.7)*lo,hiB=(br+.7)*hi;
                double alo=1-Math.abs(lo),ahi=1-Math.abs(hi);
                out.vertex(px+anx*loA,py+any*loA,alpha(ac,(ac>>>24)/255.*alo));
                out.vertex(px+anx*hiA,py+any*hiA,alpha(ac,(ac>>>24)/255.*ahi));
                out.vertex(qx+bnx*hiB,qy+bny*hiB,alpha(bc,(bc>>>24)/255.*ahi));
                out.vertex(qx+bnx*loB,qy+bny*loB,alpha(bc,(bc>>>24)/255.*alo));
            }
        }
    }
    private static double curveX(int i,double t){return switch(i){case 0->bezier(-.45,-.5,-.1,.1,t);case 1->bezier(-1.4,-.25,.35,.75,t);case 2->bezier(1.4,1,.4,-.4,t);default->bezier(-2.2,2.8,3,-1.2,t);};}
    private static double curveY(int i,double t){return switch(i){case 0->bezier(-.95,-.1,0,.8,t);case 1->bezier(.45,.45,-.1,1.1,t);case 2->bezier(0,-.55,-.25,.65,t);default->bezier(-.8,-1.1,.55,.7,t);};}
    private static double wispy(double t,double ms,int arc){
        return .09*Math.sin(t*6+arc+ms*.003)+.014*Math.sin(t*22+arc*2-ms*.005)+.003*Math.sin(t*55+arc);
    }
    private static double bezier(double a,double b,double c,double d,double t){double q=1-t;return q*q*q*a+3*q*q*t*b+3*q*t*t*c+t*t*t*d;}
    private static int mix(int a,int b,double p){return 0xFF000000|(int)lerp((a>>16)&255,(b>>16)&255,p)<<16|(int)lerp((a>>8)&255,(b>>8)&255,p)<<8|(int)lerp(a&255,b&255,p);}
    private static double cx(Rect r){return r.x()+r.width()/2.;}
    private static double cy(Rect r){return r.y()+r.height()/2.;}
    private static double lerp(double a,double b,double p){return a+(b-a)*p;}
    private static double smooth(double p){p=Math.clamp(p,0,1);return p*p*(3-2*p);}
    private static double out(double p){return 1-Math.pow(1-Math.clamp(p,0,1),3);}
}
