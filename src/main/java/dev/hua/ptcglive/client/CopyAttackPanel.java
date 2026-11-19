package dev.hua.ptcglive.client;

import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.util.function.DoubleSupplier;

/** Copied attack rows float over the attacking card; all submission goes through the table gate. */
final class CopyAttackPanel {
    private CopyAttackPanel() { }
    static Rect card(int width,int height) {
        int h=(int)(height*.65),w=Math.min(width-24,Math.round(h*63f/88f));
        return new Rect((width-w)/2,(int)(height*.27),w,h);
    }
    static Rect row(int width,int height,int scale,int index) {
        int w=Math.min(width-24*scale,Math.max(170*scale,(int)(height*.56)));
        int h=Math.max(32*scale,(int)(height*.09));
        int y=Math.min((int)(height*.67),height-34*scale-2*h-5*scale);
        return new Rect((width-w)/2,y+index*(h+5*scale),w,h);
    }
    static Rect heading(int width,int height,int scale) {
        int w=Math.min(width-24*scale,Math.max(190*scale,(int)(height*.64)));
        return new Rect((width-w)/2,(int)(height*.045),w,33*scale);
    }
    static final class AttackButton extends ButtonWidget {
        private final String detail;
        private final int tint,scale;
        private final DoubleSupplier progress;
        AttackButton(Rect r,String title,String detail,int tint,int scale,DoubleSupplier progress,PressAction submit) {
            super(r.x(),r.y(),r.width(),r.height(),Text.literal(title),submit,DEFAULT_NARRATION_SUPPLIER);
            this.detail=detail;this.tint=tint;this.scale=scale;this.progress=progress;
            setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(title+"\n"+detail)));
        }
        @Override protected void renderWidget(DrawContext c,int mx,int my,float delta) {
            double p=progress.getAsDouble();if(p<=0)return;
            var font=MinecraftClient.getInstance().textRenderer;int k=scale,x=getX(),y=getY(),w=getWidth(),h=getHeight();
            c.getMatrices().push();c.getMatrices().translate(0,(1-p)*12*k,3);
            BattleUi.round(c,x-5*k,y-5*k,w+10*k,h+10*k,9*k,fade(isSelected()?0x8865FFF0:0x4865FFF0,p));
            BattleUi.round(c,x-2*k,y-2*k,w+4*k,h+4*k,7*k,fade(0xFF8BFFF0,p));
            BattleUi.round(c,x,y,w,h,5*k,fade(active?tint:0xFFCED6DF,p));
            BattleUi.round(c,x+2*k,y+2*k,w-4*k,h/2,4*k,fade(0x40FFFFFF,p));
            int ink=fade(0xFF182C42,p);
            BattleUi.text(c,font,font.trimToWidth(getMessage().getString(),Math.max(1,w/k-18)),x+9*k,y+6*k,ink,false,k);
            BattleUi.text(c,font,font.trimToWidth(detail,Math.max(1,w/k-18)),x+9*k,y+h-13*k,ink,false,k);
            c.getMatrices().pop();
        }
        private static int fade(int argb,double p){return ((int)((argb>>>24)*Math.clamp(p,0,1))<<24)|(argb&0xFFFFFF);}
    }
}
