package dev.hua.ptcglive.client;

/** Analytic seconds-based poses: render rate and server random state never affect the trajectory. */
final class BattleMotion {
    private BattleMotion(){}
    static final class ButtonHover {
        private boolean focused;
        private long changed;
        private double start;
        double sample(boolean next,long now){
            double value=start+((focused?1:0)-start)*out((now-changed)/120_000_000.);
            if(next!=focused){
                start=value;
                focused=next;
                changed=now;
            }
            return value;
        }
        boolean tooltipReady(long now){return focused&&now-changed>550_000_000L;}
    }
    static double unit(double p){return Math.clamp(p,0,1);}
    static double smooth(double p){p=unit(p);return p*p*p*(p*(p*6-15)+10);}
    static double out(double p){p=unit(p);return 1-Math.pow(1-p,4);}
    static double envelope(double seconds,double duration,double enter,double exit){
        if(seconds<0||seconds>=duration||duration<=0)return 0;
        return smooth(seconds/Math.max(.001,enter))*smooth((duration-seconds)/Math.max(.001,exit));
    }
    static double attack(double seconds){
        if(seconds<0||seconds>=1.02)return 0;
        if(seconds<.16)return -.12*smooth(seconds/.16);
        if(seconds<.60)return -.12+1.12*out((seconds-.16)/.44);
        if(seconds<.68)return 1; // The confirmed damage cue begins at .65 seconds.
        return 1-smooth((seconds-.68)/.34);
    }
    static double attack(double seconds,double impactSeconds){
        // Align the contact pose to the confirmed damage event, including slower AI presentation.
        double contact=Math.max(.05,impactSeconds);
        return attack(seconds<=contact?seconds*.65/contact:.65+seconds-contact);
    }
    static double recoil(double seconds){
        if(seconds<0||seconds>=.38)return 0;
        return Math.sin(seconds*48)*Math.pow(1-seconds/.38,3);
    }
    static double hitScale(double seconds){
        if(seconds<0||seconds>=.38)return 1;
        return 1-.055*Math.sin(Math.PI*unit(seconds/.18))*Math.max(0,1-seconds/.38);
    }
    static double numberScale(double seconds){
        if(seconds<0)return 0;
        if(seconds<.12)return .68+.5*out(seconds/.12);
        if(seconds<.30)return 1.18-.18*smooth((seconds-.12)/.18);
        return 1;
    }
    static double numberOpacity(double seconds){return unit(seconds/.05)*unit((1.10-seconds)/.24);}
    static double hpProgress(AnimationTimeline timeline,double hitMs){
        double start=timeline.sample("hp.startMs",0,0),end=timeline.sample("hp.endMs",0,0);
        return end<=start?(hitMs>=end?1:0):smooth((hitMs-start)/(end-start));
    }
    static double flight(double seconds,double duration){return smooth(seconds/duration);}
    /** Pocket turn ribbon entry/exit; the separate slow glide continues while the title is readable. */
    static double turnSlide(double seconds,double duration){
        return turnSlide(seconds,duration,false);
    }
    static double turnSlide(double seconds,double duration,boolean own){return turnTrack(own?"slide.own":"slide.opponent",seconds,duration);}
    static double turnTitleOpacity(double seconds,double duration){
        return turnTrack("title.alpha",seconds,duration);
    }
    static double turnDrift(double seconds,double duration){
        return turnTrack("drift",seconds,duration);
    }
    static double turnSubtitleOpacity(double seconds,double duration){return turnTrack("subtitle.alpha",seconds,duration);}
    private static double turnTrack(String name,double seconds,double duration){
        var t=BattleAnimationEngine.turn();return t.sample(name,unit(seconds/duration)*t.durationMs,0);
    }
    static double stagger(int count,int index){return index*Math.min(.065,.38/Math.max(1,count-1));}
    static double landing(double p){return p<.68?0:Math.sin((p-.68)/.32*Math.PI*2)*Math.pow(1-unit((p-.68)/.32),2);}
}
