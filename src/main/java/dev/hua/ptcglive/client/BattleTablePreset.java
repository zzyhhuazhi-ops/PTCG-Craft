package dev.hua.ptcglive.client;
import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;

/** Window-adaptive camera framing, not a change to the monitor resolution or game rules. */
enum BattleTablePreset {
    B("B 放大",62,1.8,.96), C("C 宽屏",62,1.95,.98), D("D 沉浸",50,1.8,.99);
    final String label;final double pitch,aspect,fill;
    BattleTablePreset(String label,double pitch,double aspect,double fill){this.label=label;this.pitch=pitch;this.aspect=aspect;this.fill=fill;}
    BattleTablePreset next(){return values()[(ordinal()+1)%values().length];}
    PocketTableCamera.Settings settings(){return PocketTableCamera.Settings.defaults().pitch(pitch).aspect(aspect);}
    Rect viewport(PtcgLiveTableLayout l){
        int right=this==B||l.screenHeight()>l.screenWidth()?l.rightColumnX():l.screenWidth();
        return new Rect(8,8,right-16,Math.max(40,l.handHintY()-16));
    }
}
