package dev.hua.ptcglive.client;

import dev.hua.ptcglive.engine.field.EnergyType;
import java.util.Arrays;

/** Public active-card types only. Interrupted transitions continue from the visible mixture. */
final class BattleTableEnvironment {
    static final long TRANSITION_NS=650_000_000L;
    static final int TYPES=EnergyType.values().length;
    static final class Side {
        private EnergyType target=EnergyType.COLORLESS;
        private long started;
        private final float[] from=new float[TYPES];
        final float[] weights=new float[TYPES];
        Side(){weights[EnergyType.COLORLESS.ordinal()]=1;System.arraycopy(weights,0,from,0,TYPES);}
        void update(EnergyType type,long now){
            if(type==null)type=EnergyType.COLORLESS;
            sample(now);
            if(type!=target){System.arraycopy(weights,0,from,0,TYPES);target=type;started=now;}
        }
        private void sample(long now){
            double t=Math.clamp((now-started)/(double)TRANSITION_NS,0,1),smooth=t*t*(3-2*t);
            if(t>=1){Arrays.fill(weights,0);weights[target.ordinal()]=1;return;}
            for(int i=0;i<TYPES;i++)weights[i]=(float)(from[i]*(1-smooth)+(i==target.ordinal()?smooth:0));
        }
    }
    final Side own=new Side(),opponent=new Side();
    void update(EnergyType ownType,EnergyType opponentType,long now){own.update(ownType,now);opponent.update(opponentType,now);}
}
