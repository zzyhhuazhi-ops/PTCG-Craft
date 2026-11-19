package dev.hua.ptcglive.client;

import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;
import dev.hua.ptcglive.rules.CardInstance;
import net.minecraft.client.gui.DrawContext;
import java.util.*;
import static dev.hua.ptcglive.client.BattleEffects.alpha;

/** Public tool status badge and its local contact/removal light. Never mutates an attachment. */
final class ToolAttachmentAnimation {
    private ToolAttachmentAnimation(){}
    static double discardStart(){return BattleAnimationEngine.tool().sample("discard.start.ms",0,1150);}
    static double discardEnd(){return BattleAnimationEngine.tool().sample("discard.end.ms",0,1367);}
    static double discardProgress(double ms){return BattleAnimationEngine.tool().sample("discard.progress",ms,1);}
    static double discardAngle(double ms){return BattleAnimationEngine.tool().sample("discard.angle",ms,0);}
    static double returnStart(){return BattleAnimationEngine.tool().sample("return.start.ms",0,100);}
    static double returnEnd(){return BattleAnimationEngine.tool().sample("return.end.ms",0,300);}
    static double returnStagger(){return BattleAnimationEngine.tool().sample("return.stagger.ms",0,100);}
    static double returnProgress(double ms){return BattleAnimationEngine.tool().sample("return.progress",ms,1);}
    static PocketTableCamera.Pose discardPose(PocketTableCamera.Pose pile,double ms){
        double left=1-discardProgress(ms);
        return pile.move(0,pile.height()*2*left,0).scale(1+.06*left).angles(pile.tilt(),pile.yaw()-discardAngle(ms));
    }
    static Rect icon(Rect target,int index){
        int w=Math.max(8,(int)Math.round(target.width()*.36)),h=Math.max(6,(int)Math.round(w*.75));
        return new Rect((int)Math.round(target.x()+target.width()*.08-w*.5),
            (int)Math.round(target.y()+target.height()*.38-h*.5)+Math.max(0,index)*(h+2),w,h);
    }
    static int slot(List<CardInstance> before,List<CardInstance> after,String cardId,boolean adding,Set<UUID> claimed){
        var source=adding?after:before;var other=adding?before:after;
        for(int i=0;i<source.size();i++){
            var card=source.get(i);
            if(card.definitionId().equals(cardId)&&!claimed.contains(card.instanceId())
                &&other.stream().noneMatch(c->c.instanceId().equals(card.instanceId()))){claimed.add(card.instanceId());return i;}
        }
        return 0;
    }
    static void badge(DrawContext c,CardArt art,Rect r){
        CardPainter.roundRect(c,r.x()-1,r.y()-1,r.width()+2,r.height()+2,0xA548385E);
        CardPainter.roundRect(c,r.x(),r.y(),r.width(),r.height(),0xFFF1EBF6);
        c.drawBorder(r.x()+1,r.y()+1,Math.max(1,r.width()-2),Math.max(1,r.height()-2),0xFFD0C8D9);
        if(art!=null)c.drawTexture(art.texture(),r.x()+2,r.y()+2,Math.max(1,r.width()-4),Math.max(1,r.height()-4),
            art.width()*.12f,art.height()*.18f,(int)(art.width()*.76),(int)(art.height()*.36),art.width(),art.height());
    }
    static void draw(DrawContext c,Rect icon,double ms,boolean remove,BattleAnimationEngine.Quality quality){
        draw(c,icon,ms,remove?"remove.":"attach.",quality);
    }
    static void drawReturn(DrawContext c,Rect icon,double ms,BattleAnimationEngine.Quality quality){
        draw(c,returnIcon(icon),ms,"return.",quality);
    }
    private static Rect returnIcon(Rect icon){
        int h=Math.max(6,(int)Math.round(icon.height()*BattleAnimationEngine.tool().sample("return.heightRatio",0,.83)));
        return new Rect(icon.x(),icon.y()+(icon.height()-h)/2,icon.width(),h);
    }
    private static void draw(DrawContext c,Rect icon,double ms,String prefix,BattleAnimationEngine.Quality quality){
        if(quality==BattleAnimationEngine.Quality.OFF)return;
        boolean shaded=TrainerPlayAnimation.toolBadge(c,icon,ms,prefix);
        mesh(BattleEffects.mesh(c),icon,ms,prefix,quality,!shaded);
    }
    static void mesh(BattleEffectMesh.Sink out,Rect icon,double ms,boolean remove,BattleAnimationEngine.Quality quality){
        mesh(out,icon,ms,remove?"remove.":"attach.",quality,true);
    }
    static void returnMesh(BattleEffectMesh.Sink out,Rect icon,double ms,BattleAnimationEngine.Quality quality){
        mesh(out,returnIcon(icon),ms,"return.",quality,true);
    }
    private static void mesh(BattleEffectMesh.Sink out,Rect icon,double ms,String prefix,BattleAnimationEngine.Quality quality,boolean body){
        if(quality==BattleAnimationEngine.Quality.OFF||ms<0||ms>=900)return;
        var t=BattleAnimationEngine.tool();boolean remove=!prefix.equals("attach.");
        double core=t.sample(prefix+"core",ms,0),frame=t.sample(prefix+"frame",ms,0),spark=t.sample(prefix+"sparks",ms,0);
        double scale=t.sample(prefix+"scale",ms,1),opening=t.sample(prefix+"opening",ms,0);
        double x=icon.x()+icon.width()/2.,y=icon.y()+icon.height()/2.,w=icon.width()*scale,h=icon.height()*scale;
        int hue=remove?0xFFD8CBE9:0xFFDB9CFF;
        if(body)for(int i=8;i>=1;i--){
            double spread=icon.width()*.035*i;
            box(out,x,y,w+spread*2,h+spread*2,icon.width()*.04+spread*.5,alpha(hue,frame*.025*(9-i)));
            box(out,x,y,w+spread*.6,h+spread*.6,Math.max(.3,icon.width()*.025),alpha(0xFFFFFFFF,core*.06));
        }
        if(body&&core>0){
            if(opening<=.01)rect(out,x-w*.5,y-h*.5,w,h,alpha(0xFFFFFFFF,core));
            else box(out,x,y,w,h,Math.max(.3,Math.min(w,h)*.5*(1-opening)),alpha(0xFFFFFFFF,core));
        }
        if(body)box(out,x,y,w,h,Math.max(.6,icon.width()*.045),alpha(0xFFFFFFFF,frame));
        double burst=t.sample(prefix+"burst",ms,0);
        for(int i=0;burst>0&&i<11;i++){
            double angle=i*2*Math.PI/11+.12,dx=Math.cos(angle),dy=Math.sin(angle);
            double radius=icon.width()*(1.2+((i*.618)%1)*.55),base=icon.width()*.42;
            double half=icon.width()*(.07+((i*.414)%1)*.06);
            if(remove){
                base=Math.min(w*.5/Math.max(.001,Math.abs(dx)),h*.5/Math.max(.001,Math.abs(dy)));
                radius=base+icon.width()*(.14+((i*.618)%1)*.12);
                half=icon.width()*(.03+((i*.414)%1)*.02);
            }
            int rayColor=prefix.equals("return.")?0xFFE3E0E5:0xFFFFFFFF;
            ray(out,x,y,dx,dy,base,radius,half*1.6,burst*.2,rayColor);
            ray(out,x,y,dx,dy,base,radius,half,burst,rayColor);
        }
        int count=quality==BattleAnimationEngine.Quality.HIGH?9:5;
        for(int i=0;i<count;i++){
            double seed=(i*.618034+.23)%1,angle=i*Math.PI*2/count+.16;
            double age=Math.max(0,ms-30-seed*70),radius=icon.width()*(.55+age*.0012);
            double a=spark*(1-seed*.35),px=x+Math.cos(angle)*radius,py=y+Math.sin(angle)*radius*.8;
            double size=icon.width()*(.04+seed*.025);
            BattleEffectMesh.line(out,px-size*.4,py,px+size*.4,py,size*2,alpha(hue,a*.18));
            BattleEffectMesh.line(out,px-size,py,px+size,py,Math.max(.5,size*.2),alpha(0xFFFFFFFF,a));
            BattleEffectMesh.line(out,px,py-size*1.4,px,py+size*1.4,Math.max(.5,size*.2),alpha(0xFFFFFFFF,a));
        }
    }
    private static void ray(BattleEffectMesh.Sink out,double x,double y,double dx,double dy,double base,double radius,double half,double a,int color){
        double px=-dy*half,py=dx*half,mid=base+(radius-base)*.72;
        int white=alpha(color,a),edge=alpha(color,a*.85),clear=color&0xFFFFFF;
        out.vertex(x+dx*base-px,y+dy*base-py,white);
        out.vertex(x+dx*base+px,y+dy*base+py,white);
        out.vertex(x+dx*mid+px*.22,y+dy*mid+py*.22,edge);
        out.vertex(x+dx*mid-px*.22,y+dy*mid-py*.22,edge);
        out.vertex(x+dx*mid-px*.22,y+dy*mid-py*.22,edge);
        out.vertex(x+dx*mid+px*.22,y+dy*mid+py*.22,edge);
        out.vertex(x+dx*radius,y+dy*radius,clear);
        out.vertex(x+dx*radius,y+dy*radius,clear);
    }
    private static void box(BattleEffectMesh.Sink out,double x,double y,double w,double h,double thickness,int color){
        double d=Math.min(thickness,Math.min(w,h)*.5),left=x-w*.5,top=y-h*.5;
        rect(out,left,top,w,d,color);rect(out,left,top+h-d,w,d,color);
        rect(out,left,top+d,d,Math.max(0,h-2*d),color);rect(out,left+w-d,top+d,d,Math.max(0,h-2*d),color);
    }
    private static void rect(BattleEffectMesh.Sink out,double x,double y,double w,double h,int color){
        if(w<=0||h<=0||(color>>>24)==0)return;
        out.vertex(x,y,color);out.vertex(x,y+h,color);out.vertex(x+w,y+h,color);out.vertex(x+w,y,color);
    }
}
