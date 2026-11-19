package dev.hua.ptcglive.client;

import dev.hua.ptcglive.engine.EffectCardPrompt;
import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Recipient-private choice presentation. Never changes the server's choice or its order. */
final class CardSelectionAnimation {
    static final long ENTRY_NS=220_000_000L, FLIGHT_NS=180_000_000L;
    private UUID resolution, flying;
    private long step, started, duration;
    private List<UUID> selected=List.of();

    boolean pending(EffectCardPrompt prompt) {
        return prompt!=null&&(!Objects.equals(resolution,prompt.resolutionId())||step!=prompt.step()||!selected.equals(prompt.selected()));
    }
    void sync(EffectCardPrompt prompt,long now) {
        if(prompt==null){resolution=null;selected=List.of();flying=null;duration=0;return;}
        if(!pending(prompt))return;
        boolean entering=!Objects.equals(resolution,prompt.resolutionId())||step!=prompt.step();
        flying=entering?null:prompt.selected().stream().filter(id->!selected.contains(id)).findFirst().orElse(null);
        duration=entering?ENTRY_NS:flying!=null?FLIGHT_NS:0;
        resolution=prompt.resolutionId();step=prompt.step();selected=List.copyOf(prompt.selected());started=now;
    }
    boolean locked(EffectCardPrompt prompt,long now){return pending(prompt)||prompt!=null&&now-started<duration;}
    double progress(long now){double p=duration==0?1:Math.clamp((now-started)/(double)duration,0,1);return 1-Math.pow(1-p,3);}
    double entry(long now){return duration==ENTRY_NS?progress(now):1;}
    boolean flying(UUID id,long now){return Objects.equals(id,flying)&&now-started<duration;}
    static Rect area(int width,int height){return new Rect(0,Math.round(height*.06f),width,height-Math.round(height*.06f));}
    static Rect tray(int width,int height){return new Rect(0,Math.round(height*.40f),width,Math.round(height*.30f));}
    static Rect candidates(int width,int height,int ui){return new Rect(24*ui,Math.round(height*.79f),Math.max(10,width-48*ui),Math.max(10,Math.round(height*.18f)));}
    static Rect slot(int width,int height,int ui,int count,int index){
        var tray=tray(width,height);int gap=6*ui;
        int h=Math.max(10,Math.round(height*.22f)),w=Math.round(h*63/88f);
        w=Math.min(w,Math.max(10,(width-48*ui-gap*(count-1))/count));h=Math.round(w*88/63f);
        int span=w*count+gap*(count-1);
        return new Rect((width-span)/2+index*(w+gap),tray.y()+(tray.height()-h)/2-3*ui,w,h);
    }
}
