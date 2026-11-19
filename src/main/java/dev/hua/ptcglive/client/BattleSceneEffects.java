package dev.hua.ptcglive.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.RotationAxis;
import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;
import static dev.hua.ptcglive.client.BattleEffects.*;

/** Public-event presentation only. All particles have fixed counts and analytic lifetimes. */
final class BattleSceneEffects {
    static final int CYAN=0xFF83E9F4, WHITE=0xFFF5FFFF, GOLD=0xFFFFDEA0;
    private static final net.minecraft.util.Identifier TITLE_FONT=net.minecraft.util.Identifier.of("ptcglive_mc","battle_title");
    private static final net.minecraft.util.Identifier OWN_TITLE=net.minecraft.util.Identifier.of("ptcglive_mc","textures/gui/battle/turn_own_title.png");
    private static final net.minecraft.util.Identifier OPPONENT_TITLE=net.minecraft.util.Identifier.of("ptcglive_mc","textures/gui/battle/turn_opponent_title.png");
    private BattleSceneEffects(){}
    private static double fade(double t,double duration){return BattleMotion.envelope(t,duration,.08,.28);}
    static void glint(DrawContext c,double x,double y,double radius,int color){
        var m=c.getMatrices();m.push();m.translate(x,y,0);
        BattleEffectMesh.crystal(mesh(c),radius*.16,radius,color);
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(90));
        BattleEffectMesh.crystal(mesh(c),radius*.16,radius*.72,color);m.pop();
    }
    static void focus(DrawContext c,int x,int y,int w,int h,int color,double seconds){
        double a=.65+.12*Math.sin(seconds*2.4);
        for(int i=4;i>=1;i--){
            int tint=alpha(color,a*(5-i)*.035);
            c.drawBorder(x-i,y-i,w+2*i,h+2*i,tint);
        }
        c.drawBorder(x-2,y-2,w+4,h+4,alpha(0xFF19525B,.78));
        c.drawBorder(x-1,y-1,w+2,h+2,alpha(color,.9));
        line(c,x,y,x+w,y,1,alpha(WHITE,a));
    }
    static void turnEdge(DrawContext c,Rect bounds,boolean bottom,double seconds){
        int h=Math.max(3,bounds.height()/36),y=bottom?bounds.bottom()-h:bounds.y();
        int color=alpha(CYAN,.13+.025*Math.sin(seconds*1.8));
        c.fillGradient(bounds.x()+3,y,bounds.right()-3,y+h,bottom?0:color,bottom?color:0);
    }
    static void turnBanner(DrawContext c,net.minecraft.client.font.TextRenderer font,Rect arena,int cy,
                           String label,int turn,boolean own,double seconds,double duration){
        if(seconds<0||seconds>=duration)return;
        double w=Math.min(arena.width()*.70,arena.height()*1.375),h=w*.16;
        double direction=own?-1:1;
        double slide=(BattleMotion.turnSlide(seconds,duration,own)*(arena.width()+w)*.55
            +BattleMotion.turnDrift(seconds,duration)*w)*direction;
        c.draw();c.enableScissor(arena.x(),arena.y(),arena.right(),arena.bottom());
        var m=c.getMatrices();m.push();m.translate(arena.x()+arena.width()*.5+slide,cy,0);
        BattleEffectMesh.ribbon(mesh(c),-w*.5+w*(own?.18:.13)*direction,-h*.5-h*.25*direction,w*(own?1.11:1.06),h,own?0xFF4D86F6:0xFFFF744F,own?0xFF7478F9:0xFFFF3996,own);
        BattleEffectMesh.ribbon(mesh(c),-w*.5,-h*.5,w,h,own?0xFF00E8FF:0xFFFEA26C,own?0xFF3E84FE:0xFFFE4057,own);
        c.draw();m.push();m.translate(0,0,1);
        double titleOpacity=BattleMotion.turnTitleOpacity(seconds,duration);
        if(titleOpacity>=4/255.0){
            if(label.equals(own?"你的回合":"对手的回合")){
                var texture=own?OWN_TITLE:OPPONENT_TITLE;int tw=own?512:640,th=128;
                int dh=(int)Math.round(h*.62),dw=(int)Math.round(dh*tw/(double)th);
                net.minecraft.client.MinecraftClient.getInstance().getTextureManager().getTexture(texture).setFilter(true,false);
                com.mojang.blaze3d.systems.RenderSystem.enableBlend();com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
                c.setShaderColor(1,1,1,(float)titleOpacity);
                try{c.drawTexture(texture,-dw/2,-dh/2,dw,dh,0,0,tw,th,tw,th);}
                finally{c.setShaderColor(1,1,1,1);com.mojang.blaze3d.systems.RenderSystem.disableBlend();}
            }else{
                m.scale((float)(h/18),(float)(h/18),1);
                var text=net.minecraft.text.Text.literal(label).styled(s->s.withFont(TITLE_FONT));
                c.drawText(font,text,-font.getWidth(text)/2,-5,alpha(WHITE,titleOpacity),false);
            }
        }
        m.pop();m.pop();
        double subtitleOpacity=BattleMotion.turnSubtitleOpacity(seconds,duration);
        int sw=(int)(w*.60),sh=(int)(h*.43),x=arena.x()+arena.width()/2,subY=cy+(int)(h*.85);
        BattleUi.round(c,x-sw/2,subY,sw,sh,sh/2,alpha(0xFF3C4A60,subtitleOpacity*.88));
        c.draw();m.push();m.translate(x,subY+sh*.5,1);m.scale((float)(h/32),(float)(h/32),1);
        var subtitle=BattleUi.label("当前回合： ").copy().append(net.minecraft.text.Text.literal(Integer.toString(turn))
            .styled(s->s.withFont(net.minecraft.util.Identifier.of("ptcglive_mc","battle_numbers"))));
        if(subtitleOpacity>=4/255.0)c.drawText(font,subtitle,-font.getWidth(subtitle)/2,-5,alpha(WHITE,subtitleOpacity),false);m.pop();
        c.draw();c.disableScissor();
    }
    static void sweep(DrawContext c,Rect bounds,double seconds,double duration,int color){
        double a=fade(seconds,duration);if(a==0)return;
        int cy=bounds.y()+bounds.height()/2,h=Math.max(10,bounds.height()/10);
        c.fillGradient(bounds.x(),cy-h,bounds.right(),cy,alpha(color,0),alpha(color,a*.13));
        c.fillGradient(bounds.x(),cy,bounds.right(),cy+h,alpha(color,a*.13),alpha(color,0));
        double p=BattleMotion.out(seconds/Math.min(.65,duration));
        int x=bounds.x()+(int)(p*bounds.width());
        glow(c,x,cy,h*1.2,color,a*.35);
        line(c,bounds.x(),cy,bounds.x()+bounds.width()*p,cy,1,alpha(WHITE,a*.45));
    }
    static void shine(DrawContext c,Rect card,double seconds,double duration){
        double a=fade(seconds,duration);if(a==0)return;
        int y=card.bottom()-(int)(BattleMotion.smooth(seconds/duration)*card.height());
        int h=Math.max(3,card.height()/8);
        c.enableScissor(card.x(),card.y(),card.right(),card.bottom());
        c.fillGradient(card.x(),y-h,card.right(),y,0,alpha(WHITE,a*.42));
        c.fillGradient(card.x(),y,card.right(),y+h,alpha(WHITE,a*.42),0);
        c.disableScissor();
    }
    static void landing(DrawContext c,Rect card,double seconds,int color){
        double p=BattleMotion.unit(seconds/.58),a=fade(seconds,.58);if(a==0)return;
        int x=card.x()+card.width()/2,y=card.bottom();
        glow(c,x,y,card.width()*(.3+.25*p),color,a*.6);
        ring(c,x,y,card.width()*(.24+.38*BattleMotion.out(p)),.22,alpha(WHITE,a*.65));
        for(int i=0;i<4;i++)glint(c,x+(i-1.5)*card.width()*.28,y-p*card.height()*.3-(i%2)*5,
            card.width()*.065*(1-p),alpha(WHITE,a));
    }
    static void evolution(DrawContext c,Rect card,double seconds,boolean reverse){
        double a=fade(seconds,1.6),p=BattleMotion.unit(seconds/1.6);if(a==0)return;
        int x=card.x()+card.width()/2,y=card.y()+card.height()/2;
        BattleEffectMesh.glow(mesh(c),x,y,card.width()*.85,card.height()*.95,alpha(CYAN,a*.35));
        for(int i=0;i<9;i++){
            double q=(p*1.6+i/9.0)%1,angle=i*2.4+p*7;
            double px=x+Math.cos(angle)*card.width()*.65,py=card.bottom()-q*card.height()*1.15;
            if(reverse)py=card.y()+q*card.height()*1.15;
            glint(c,px,py,card.width()*.075*Math.sin(q*Math.PI),alpha(WHITE,a*.85));
        }
        for(int i=0;i<2;i++){
            double q=BattleMotion.unit((seconds-i*.18)/1.05);
            int cy=card.bottom()-(int)(q*card.height());
            ring(c,x,reverse?card.y()+card.bottom()-cy:cy,card.width()*(.38+.18*Math.sin(Math.PI*q)),.24,alpha(WHITE,a*Math.sin(Math.PI*q)*.9));
        }
        shine(c,card,seconds-.25,.75);
    }
    static void ability(DrawContext c,Rect card,double seconds){
        double a=fade(seconds,1.1);if(a==0)return;
        int x=card.x()+card.width()/2,y=card.y()+card.height()/2;
        glow(c,x,y,card.width()*.85,CYAN,a*.6);
        ring(c,x,y,card.width()*(.2+.5*BattleMotion.out(seconds/.65)),.8,alpha(WHITE,a*.7));
        shine(c,card,seconds,.75);
        for(int i=0;i<4;i++)glint(c,card.x()+(i%2)*card.width(),card.y()+(i/2)*card.height(),card.width()*.10*a,alpha(WHITE,a));
    }
    static void heal(DrawContext c,int x,int y,int size,double seconds){
        double a=fade(seconds,1.1),p=BattleMotion.unit(seconds/1.1);if(a==0)return;
        var out=mesh(c);
        double bloom=BattleMotion.envelope(seconds-.18,.82,.24,.30);
        double r=size*(.24+.46*BattleMotion.out((seconds-.18)/.68));
        BattleEffectMesh.glow(out,x,y,r*1.22,alpha(0xFFECFFAA,bloom));
        BattleEffectMesh.ring(out,x,y,r,.96,size*.035,alpha(0xFFFFFFDE,bloom));
        for(int i=0;i<5;i++){
            double q=BattleMotion.unit((p-i*.08)/.57);
            double px=x+Math.cos(i*2.4)*size*(.28+.24*q),py=y+size*(.35-.96*q);
            glint(c,px,py,size*.075*Math.sin(Math.PI*q),alpha(i%2==0?WHITE:0xFFB6FFE2,a));
        }
    }
    static void status(DrawContext c,Rect card,String condition,boolean clear,double seconds){
        double a=fade(seconds,1.0),p=BattleMotion.unit(seconds);if(a==0)return;
        int x=card.x()+card.width()/2,y=card.y()+card.height()/2,s=card.width();
        if(clear){
            double progress=BattleMotion.out(seconds/.42);
            double flash=BattleMotion.envelope(seconds,.50,.055,.32);
            int cx=x+(int)(s*.37),cy=y-(int)(card.height()*.17);
            glow(c,cx,cy,s*(.13+.25*progress),CYAN,flash*.82);
            glint(c,cx,cy,s*.18*flash,alpha(WHITE,flash));
            return;
        }
        switch(condition){
            case "中毒" -> {
                for(int i=0;i<6;i++){double q=(p+i*.14)%1;int px=x+(int)(Math.sin(i*2.4)*s*.4),py=y+s/3-(int)(q*s);
                    glow(c,px,py,s*.2,0xFFB68BEB,a*.5);ring(c,px,py,s*.07*(1-q*.4),1,alpha(0xFFDBBBF9,a));}
            }
            case "灼伤" -> {
                for(int i=0;i<5;i++){double q=(p+i*.17)%1;var m=c.getMatrices();m.push();m.translate(x+(i-2)*s*.18,y+s*.35-q*s*.5,0);
                    BattleEffectMesh.flame(mesh(c),s*.18*(1-q),s*.35*(1-q),.2,alpha(0xFFFFA063,a));m.pop();}
            }
            case "麻痹" -> {
                var out=mesh(c);
                double pulse=.81+.19*Math.cos(p*18);
                BattleEffectMesh.glow(out,x,y,s*.72,card.height()*.61,alpha(0xFFFFD54A,a*pulse));
                double cx=x+s*.43,cy=y-card.height()*.20,r=s*.15*BattleMotion.out(p/.17);
                BattleEffectMesh.ellipse(out,cx,cy,r,r,alpha(0xFFFFDA58,a));
                BattleEffectMesh.ring(out,cx,cy,r,.98,s*.026,alpha(0xFFFFF4B4,a));
                BattleEffectMesh.line(out,x-s*.36,y+s*.35,x+s*.38,y-s*.29,
                    s*.041,alpha(0xFFFFF3A1,BattleMotion.envelope(p,.35,.08,.14)));
            }
            case "睡眠" -> {
                var out=mesh(c);
                BattleEffectMesh.glow(out,x,y,s*.69,card.height()*.58,alpha(0xFF6DADDD,a*.83));
                for(int i=0;i<3;i++){
                    double q=BattleMotion.unit((p-i*.10)/.78);
                    double px=x+(i-1)*s*.27+s*q*.05,py=y+s*(.34-i*.12-.32*q);
                    double r=s*(.054+i*.014),stroke=s*.024;
                    int tint=alpha(0xFFE5F7FF,a*Math.sin(Math.PI*q));
                    BattleEffectMesh.line(out,px-r,py-r,px+r,py-r,stroke,tint);
                    BattleEffectMesh.line(out,px+r,py-r,px-r,py+r,stroke,tint);
                    BattleEffectMesh.line(out,px-r,py+r,px+r,py+r,stroke,tint);
                }
            }
            case "混乱" -> {
                var out=mesh(c);
                double enter=BattleMotion.out(p/.20);
                double cx=x+s*(.18+.24*enter),cy=y-card.height()*.22;
                double radius=s*.19*enter;
                BattleEffectMesh.glow(out,x,y,s*.60,card.height()*.54,alpha(0xFF8AFE88,a*.62));
                BattleEffectMesh.ellipse(out,cx,cy,radius,radius,alpha(0xFF85DA88,a));
                double px=cx,py=cy;
                for(int i=1;i<=32;i++){
                    double fraction=i/32.0,angle=fraction*Math.PI*4+p*2.2;
                    double nx=cx+Math.cos(angle)*radius*.83*fraction;
                    double ny=cy+Math.sin(angle)*radius*.83*fraction;
                    BattleEffectMesh.line(out,px,py,nx,ny,radius*.14,alpha(WHITE,a));
                    px=nx;py=ny;
                }
            }
        }
    }
    static void trail(DrawContext c,Rect from,Rect to,double seconds,double duration,int color){
        double a=fade(seconds,duration),p=BattleMotion.unit(seconds/duration);if(a==0)return;
        for(int i=1;i<=5;i++){
            double q=BattleMotion.smooth(Math.max(0,p-i*.035));
            double x=from.x()+from.width()/2.0+(to.x()+to.width()/2.0-from.x()-from.width()/2.0)*q;
            double y=from.y()+from.height()/2.0+(to.y()+to.height()/2.0-from.y()-from.height()/2.0)*q;
            glint(c,x,y-Math.sin(q*Math.PI)*8,Math.max(1,from.width()*.045)*(1-i*.12),alpha(color,a*(1-i*.13)));
        }
    }
    static void result(DrawContext c,Rect bounds,double seconds,boolean won){
        double a=fade(seconds,2.4);if(a==0)return;
        int x=bounds.x()+bounds.width()/2,y=bounds.y()+bounds.height()/2;
        glow(c,x,y,bounds.width()*.46,won?GOLD:CYAN,a*(won?.5:.25));
        if(won)for(int i=0;i<18;i++){
            double q=BattleMotion.unit((seconds-i*.015)/2.4),side=i%2==0?-1:1;
            glint(c,x+side*bounds.width()*(.12+q*.35),y+Math.sin(i*2.4)*bounds.height()*.22-q*bounds.height()*.18,
                Math.max(2,bounds.width()*.012)*(1-q),alpha(i%3==0?GOLD:WHITE,a));
        }
    }
}
