package dev.hua.ptcglive.client;

import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;

/** Face-only presentation; all identities and confirmation choices remain server-owned. */
final class CardRevealAnimation {
    static final long READ_NS=1_000_000_000L;
    static double progress(double ms,int index){double p=Math.clamp((ms-50-index*45)/350,0,1);return 1-Math.pow(1-p,3);}
    static Rect area(int width,int height,int ui){
        int margin=Math.max(8*ui,width/16),top=Math.max(8*ui,height/20);
        return new Rect(margin,top,width-2*margin,height-2*top);
    }
}
