package dev.hua.ptcglive.client;

import com.google.gson.JsonObject;

/** Authored hit-local milliseconds. Independent of attack declarations and server damage. */
record ElectricImpactProfile(double durationMs,double flashStartMs,double peakStartMs,double damagePopupMs,
    double fadeStartMs,double mainFlashEndMs,int mainArcCount,int secondaryArcCount,int impactSpikeCount,
    double arcRefreshMs,double intensity,int outerColor,int coreColor) {
    static final ElectricImpactProfile DEFAULT=new ElectricImpactProfile(650,50,116,216,350,450,7,14,20,40,1,0xFFFFF04A,0xFFFFFFFF);
    ElectricImpactProfile {
        if(!Double.isFinite(durationMs)||!Double.isFinite(flashStartMs)||!Double.isFinite(peakStartMs)
            ||!Double.isFinite(damagePopupMs)||!Double.isFinite(fadeStartMs)||!Double.isFinite(mainFlashEndMs)
            ||!(0<=flashStartMs&&flashStartMs<peakStartMs&&peakStartMs<damagePopupMs&&damagePopupMs<fadeStartMs
                &&fadeStartMs<mainFlashEndMs&&mainFlashEndMs<durationMs&&durationMs<=3000)
            ||mainArcCount<1||mainArcCount>8||secondaryArcCount<0||secondaryArcCount>20||impactSpikeCount<1||impactSpikeCount>24
            ||!Double.isFinite(arcRefreshMs)||arcRefreshMs<16||arcRefreshMs>100
            ||!Double.isFinite(intensity)||intensity<0||intensity>2)
            throw new IllegalArgumentException("Invalid electric impact profile");
    }
    static ElectricImpactProfile parse(JsonObject p){
        if(p==null)return DEFAULT;
        var d=DEFAULT;
        return new ElectricImpactProfile(n(p,"durationMs",d.durationMs),n(p,"flashStartMs",d.flashStartMs),
            n(p,"peakStartMs",d.peakStartMs),n(p,"damagePopupMs",d.damagePopupMs),n(p,"fadeStartMs",d.fadeStartMs),
            n(p,"mainFlashEndMs",d.mainFlashEndMs),count(p,"mainArcCount",d.mainArcCount),count(p,"secondaryArcCount",d.secondaryArcCount),
            count(p,"impactSpikeCount",d.impactSpikeCount),n(p,"arcRefreshMs",d.arcRefreshMs),n(p,"intensity",d.intensity),
            color(p,"outerColor",d.outerColor),color(p,"coreColor",d.coreColor));
    }
    private static double n(JsonObject p,String k,double d){return p.has(k)?p.get(k).getAsDouble():d;}
    private static int count(JsonObject p,String k,int d){double v=n(p,k,d);if(v!=Math.rint(v))throw new IllegalArgumentException("Fractional count");return (int)v;}
    private static int color(JsonObject p,String k,int d){return p.has(k)?0xFF000000|Integer.parseInt(p.get(k).getAsString().replace("#",""),16):d;}
    ElectricImpactProfile edited(int arcs,int color,double power,double duration){
        double scale=duration/durationMs;
        return new ElectricImpactProfile(duration,flashStartMs*scale,peakStartMs*scale,damagePopupMs*scale,fadeStartMs*scale,
            mainFlashEndMs*scale,arcs,Math.min(20,arcs*2),impactSpikeCount,arcRefreshMs,power,color,coreColor);
    }
    double expansion(double ms){
        double phase=(ms-flashStartMs)/(peakStartMs-flashStartMs);
        return 1-Math.pow(2,-10*Math.clamp((phase-.25)/.35,0,1));
    }
    double core(double ms){
        if(ms<flashStartMs||ms>=mainFlashEndMs)return 0;
        if(ms<=damagePopupMs)return expansion(ms);
        if(ms<fadeStartMs){
            double q=(ms-damagePopupMs)/(fadeStartMs-damagePopupMs);
            return q<.25?1:q<.5?1-.85*BattleMotion.smooth((q-.25)/.25):.15*(1-.8*BattleMotion.smooth((q-.5)/.5));
        }
        return .03*Math.pow((mainFlashEndMs-ms)/(mainFlashEndMs-fadeStartMs),3);
    }
    double arc(double ms){if(ms<flashStartMs||ms>=mainFlashEndMs)return 0;return expansion(ms)*(1-.35*Math.clamp((ms-damagePopupMs)/(fadeStartMs-damagePopupMs),0,1))*Math.pow(Math.clamp((mainFlashEndMs-ms)/(mainFlashEndMs-fadeStartMs),0,1),1.8);}
    double tail(double ms){return Math.clamp((durationMs-ms)/(durationMs-fadeStartMs),0,1);}
    int glowColor(double ms){
        double blend=.5*BattleMotion.smooth((ms-damagePopupMs)/((fadeStartMs-damagePopupMs)*.5));
        int r=(outerColor>>16)&255,g=(outerColor>>8)&255,b=outerColor&255;
        return 0xFF000000|((int)(r+(255-r)*blend)<<16)|((int)(g+(255-g)*blend)<<8)|(int)(b+(255-b)*blend);
    }
    double shake(double ms){double t=ms-flashStartMs;return t<0||t>100?0:Math.sin(t*.14)*Math.pow(1-t/100,2);}
    double hpProgress(double ms){return ms>=mainFlashEndMs?1:0;}
    double numberOpacity(double ms){return ms<damagePopupMs?0:Math.clamp((damagePopupMs+1100-ms)/240,0,1);}
    double numberScale(double ms){double t=ms-damagePopupMs;return t<0?0:t<100?1.15-.18*BattleMotion.out(t/100):t<180?.97+.03*BattleMotion.smooth((t-100)/80):1;}
    /** Retimes the authored material phases without tying playback to game ticks. */
    double materialTime(double ms){
        if(ms<=flashStartMs)return ms*DEFAULT.flashStartMs/Math.max(.001,flashStartMs);
        if(ms<=peakStartMs)return DEFAULT.flashStartMs+(ms-flashStartMs)/(peakStartMs-flashStartMs)*(DEFAULT.peakStartMs-DEFAULT.flashStartMs);
        if(ms<=damagePopupMs)return DEFAULT.peakStartMs+(ms-peakStartMs)/(damagePopupMs-peakStartMs)*(DEFAULT.damagePopupMs-DEFAULT.peakStartMs);
        if(ms<=fadeStartMs)return DEFAULT.damagePopupMs+(ms-damagePopupMs)/(fadeStartMs-damagePopupMs)*(DEFAULT.fadeStartMs-DEFAULT.damagePopupMs);
        return DEFAULT.fadeStartMs+(ms-fadeStartMs)/(mainFlashEndMs-fadeStartMs)*(DEFAULT.mainFlashEndMs-DEFAULT.fadeStartMs);
    }
}
