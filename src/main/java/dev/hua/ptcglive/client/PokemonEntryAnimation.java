package dev.hua.ptcglive.client;

import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;
import dev.hua.ptcglive.view.MatchPresentation;

/** Confirmed entry only: inspection/dragging is not replayed as an automatic reveal. */
final class PokemonEntryAnimation {
    static final double OWN_CONTACT_MS=350, OPPONENT_CONTACT_MS=1500, TAIL_MS=200;
    static boolean applies(MatchPresentation.Cue cue){
        return cue.kind()==MatchPresentation.Kind.PLAY&&cue.pokemon()!=null&&!cue.text().equals("能量附着");
    }
    static boolean reveal(MatchPresentation.Cue cue,boolean opponent){
        return opponent&&cue.text().equals("从手牌上场");
    }
    static boolean setupReveal(MatchPresentation.Cue cue){
        return applies(cue)&&cue.text().equals("开局亮牌");
    }
    static double contact(boolean reveal){return reveal?OPPONENT_CONTACT_MS:OWN_CONTACT_MS;}
    static long duration(boolean reveal){return (long)((contact(reveal)+TAIL_MS)*1e6);}
    static double landing(boolean reveal,double ms){return smooth((ms-(reveal?1200:0))/(reveal?300:OWN_CONTACT_MS));}
    static Rect lifted(Rect target){
        int h=(int)(target.height()*1.35),w=(int)(target.width()*1.35);
        return new Rect(target.x()+target.width()/2-w/2,target.y()-target.height()-h/2,w,h);
    }
    static EvolutionAnimation.Pose revealPose(Rect hand,Rect stage,Rect lifted,double ms){
        double launch=smooth(ms/180),flip=smooth((ms-80)/220),toss=smooth((ms-1000)/200);
        return new EvolutionAnimation.Pose(mix(mix(cx(hand),cx(stage),launch),cx(lifted),toss),
            mix(mix(cy(hand),cy(stage),launch),cy(lifted),toss),
            mix(mix(stage.width()*.25,stage.width(),launch),lifted.width(),toss),
            mix(mix(stage.height()*.25,stage.height(),launch),lifted.height(),toss),
            flip,(1-flip)*-8,0,0);
    }
    static EvolutionAnimation.Pose flatPose(Rect origin,Rect target,boolean reveal,double ms){
        double p=landing(reveal,ms);
        return new EvolutionAnimation.Pose(mix(cx(origin),cx(target),p),mix(cy(origin),cy(target),p),
            mix(origin.width(),target.width(),p),mix(origin.height(),target.height(),p),1,0,0,0);
    }
    private static double cx(Rect r){return r.x()+r.width()/2.;}
    private static double cy(Rect r){return r.y()+r.height()/2.;}
    private static double mix(double a,double b,double p){return a+(b-a)*p;}
    private static double smooth(double p){p=Math.clamp(p,0,1);return p*p*(3-2*p);}
}
