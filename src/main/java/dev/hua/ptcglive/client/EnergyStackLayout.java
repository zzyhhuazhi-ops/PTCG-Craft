package dev.hua.ptcglive.client;

import java.util.*;
import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;

/** Compact public attachments, wholly inside the original hit rectangle. */
public record EnergyStackLayout(Rect front, List<Rect> layers) {
    public static EnergyStackLayout of(int x,int y,int width,int height,int count) {
        int shown=Math.min(2,Math.max(0,count));
        int energyHeight=Math.max(5,height/2), reveal=Math.max(1,energyHeight/5);
        int frontHeight=height-shown*reveal;
        int frontWidth=Math.max(1,(int)Math.round(width*(double)frontHeight/height));
        var front=new Rect(x+(width-frontWidth)/2,y,frontWidth,frontHeight);
        int energyWidth=Math.max(1,(int)Math.round(energyHeight*PtcgLiveTheme.CARD_ASPECT));
        var layers=new ArrayList<Rect>();
        for(int i=0;i<shown;i++) layers.add(new Rect(x+(width-energyWidth)/2,
            y+frontHeight-energyHeight+(i+1)*reveal,energyWidth,energyHeight));
        return new EnergyStackLayout(front,List.copyOf(layers));
    }
}
