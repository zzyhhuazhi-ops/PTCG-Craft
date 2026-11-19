package dev.hua.ptcglive.client;

import java.util.*;
import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;
import static dev.hua.ptcglive.client.BattleEffects.alpha;

/** Public energy instances link the old source slot to the confirmed destination slot. */
final class EnergyTransferAnimation {
    private EnergyTransferAnimation(){}
    static final double LEAVE_MS=16.666667;
    record Link(UUID source,int sourceIndex,int sourceCount,int targetIndex,String cardId,Rect previousBounds){}
    static Link link(UUID source,List<UUID> beforeIds,List<String> beforeCards,List<UUID> afterSourceIds,
                     List<UUID> targetIds,String cardId,Set<UUID> claimed,Rect previousBounds){
        for(int i=0;i<Math.min(beforeIds.size(),beforeCards.size());i++){
            UUID id=beforeIds.get(i);int destination=targetIds.indexOf(id);
            if(destination>=0&&!afterSourceIds.contains(id)&&Objects.equals(beforeCards.get(i),cardId)&&claimed.add(id))
                return new Link(source,i,beforeIds.size(),destination,cardId,previousBounds);
        }
        return null;
    }
    static double contactMs(){return BattleAnimationEngine.energyTransfer().impactMs;}
    static void mesh(BattleEffectMesh.Sink out,Rect source,Rect target,double cardWidth,double ms,BattleAnimationEngine.Quality quality){
        var t=BattleAnimationEngine.energyTransfer();
        if(ms<0||ms>=t.durationMs||quality==BattleAnimationEngine.Quality.OFF)return;
        double sx=source.x()+source.width()/2.,sy=source.y()+source.height()/2.;
        double tx=target.x()+target.width()/2.,ty=target.y()+target.height()/2.;
        double d=Math.max(3,target.width()),size=Math.max(d,cardWidth),length=Math.max(1,Math.hypot(tx-sx,ty-sy));
        double nx=-(ty-sy)/length,ny=(tx-sx)/length;
        double launch=t.sample("source.alpha",ms,0),trail=t.sample("trail.alpha",ms,0),orb=t.sample("orb.alpha",ms,0);
        int cyan=0xFF78FFF2,white=0xFFF3FFFF;
        BattleEffectMesh.glow(out,sx,sy,d*2.3,alpha(cyan,launch*.7));
        if(ms<200){
            double reach=size*(.42+.05*BattleMotion.smooth(ms/100));
            double ray=Math.max(launch,.55)*(1-ms/200);
            BattleEffectMesh.line(out,sx-nx*reach,sy-ny*reach,sx+nx*reach*.6,sy+ny*reach*.6,d*.18,alpha(cyan,ray));
            BattleEffectMesh.line(out,sx,sy,sx-(tx-sx)/length*reach,sy-(ty-sy)/length*reach,d*.14,alpha(cyan,ray));
            BattleEffectMesh.line(out,sx-nx*reach*.85,sy-ny*reach*.85,sx+nx*reach*.4,sy+ny*reach*.4,d*.06,alpha(white,ray));
        }
        int strands=quality==BattleAnimationEngine.Quality.HIGH?3:quality==BattleAnimationEngine.Quality.MEDIUM?2:1;
        int segments=quality==BattleAnimationEngine.Quality.LOW?10:20;
        for(int strand=0;strand<strands;strand++){
            double bend=(strand==0?-.23:strand==1?.28:-.08)*size;
            for(int i=0;i<segments;i++){
                double age=i*180./segments,nextAge=(i+1)*180./segments;
                double p=t.sample("flight",Math.max(0,ms-age-strand*7),0),q=t.sample("flight",Math.max(0,ms-nextAge-strand*7),0);
                double offset=bend*Math.sin(Math.PI*p),prior=bend*Math.sin(Math.PI*q);
                double x=sx+(tx-sx)*p+nx*offset,y=sy+(ty-sy)*p+ny*offset;
                double px=sx+(tx-sx)*q+nx*prior,py=sy+(ty-sy)*q+ny*prior;
                double fade=trail*(1-i/(double)segments);
                BattleEffectMesh.line(out,px,py,x,y,size*.07,alpha(cyan,fade*.16));
                BattleEffectMesh.line(out,px,py,x,y,size*.024,alpha(cyan,fade*.9));
                BattleEffectMesh.line(out,px,py,x,y,size*.010,alpha(white,fade));
            }
        }
        double p=t.sample("flight",ms,0),bend=-.23*size*Math.sin(Math.PI*p);
        double x=sx+(tx-sx)*p+nx*bend,y=sy+(ty-sy)*p+ny*bend;
        BattleEffectMesh.glow(out,x,y,d*1.6,alpha(cyan,orb*.55));
        BattleEffectMesh.ellipse(out,x,y,d*.45,d*.45,alpha(white,orb));
        double flash=t.sample("contact.alpha",ms,0),ring=t.sample("ring.alpha",ms,0);
        BattleEffectMesh.glow(out,tx,ty,d*2.6,alpha(cyan,flash*.55));
        double coreRadius=d*.75*(1-.35*Math.clamp((ms-533)/100,0,1));
        BattleEffectMesh.ellipse(out,tx,ty,coreRadius,coreRadius,alpha(white,flash));
        for(int i=0;i<(quality==BattleAnimationEngine.Quality.LOW?1:2);i++){
            double radius=d*(.6+Math.clamp((ms-500)/167,0,1)*.3+i*.16);
            BattleEffectMesh.ring(out,tx,ty,radius,1,Math.max(.25,d*.035),alpha(cyan,ring*.4/(1+i*.3)));
            BattleEffectMesh.ring(out,tx,ty,radius,1,Math.max(.2,d*.014),alpha(white,ring*.65/(1+i*.3)));
        }
    }
}
