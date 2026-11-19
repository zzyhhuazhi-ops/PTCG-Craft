package dev.hua.ptcglive.client;

import dev.hua.ptcglive.engine.field.EnergyType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.math.RotationAxis;

/** Bounded, deterministic screen-space geometry; no world particles or gameplay randomness. */
final class BattleEffects {
    // Silvally / Brave Buddies: first visible flash to first visible numeral, in the sampled clip.
    static final int COLORLESS_POPUP_MS = 133;
    private BattleEffects() { }
    static int alpha(int color,double opacity) {
        return ((int)(Math.clamp(opacity,0,1)*255)<<24)|(color&0xFFFFFF);
    }
    static int color(EnergyType type) {
        if(type==null)return 0xFFFFD66C;
        return switch(type) {
            case GRASS -> 0xFF83DF70; case FIRE -> 0xFFFF8C4E; case WATER -> 0xFF73CEFF;
            case LIGHTNING -> 0xFFFFE352; case PSYCHIC -> 0xFFD9A4FF; case FIGHTING -> 0xFFDF9F63;
            case DARKNESS -> 0xFF9B76DB; case METAL -> 0xFFCEE6EB; case DRAGON -> 0xFFDCD573;
            case FAIRY -> 0xFFFFACE4; case COLORLESS -> 0xFFF5F4EA;
        };
    }
    static BattleEffectMesh.Sink mesh(DrawContext c){
        var matrix=c.getMatrices().peek().getPositionMatrix();
        var vertices=c.getVertexConsumers().getBuffer(RenderLayer.getGui());
        return (x,y,color)->vertices.vertex(matrix,(float)x,(float)y,0).color(color);
    }
    static void line(DrawContext c,double x,double y,double toX,double toY,double width,int color) {
        BattleEffectMesh.line(mesh(c),x,y,toX,toY,width,color);
    }
    static void disc(DrawContext c,int x,int y,int radius,int color) {
        BattleEffectMesh.ellipse(mesh(c),x,y,Math.clamp(radius,1,240),Math.clamp(radius,1,240),color);
    }
    static void ring(DrawContext c,int x,int y,double radius,double squash,int color) {
        BattleEffectMesh.ring(mesh(c),x,y,radius,squash,1.5,color);
    }
    static void glow(DrawContext c,int x,int y,double radius,int color,double strength) {
        BattleEffectMesh.glow(mesh(c),x,y,Math.min(512,radius),alpha(color,strength*.65));
    }
    static void shadow(DrawContext c,int x,int y,int width,int height,double strength){
        BattleEffectMesh.ellipse(mesh(c),x,y,Math.max(1,width*.62),Math.max(1,height*.075),alpha(0xFF15243B,strength*.22));
    }
    static void star(DrawContext c,int x,int y,double radius,int color) {
        line(c,x-radius,y,x+radius,y,2,color);line(c,x,y-radius,x,y+radius,2,color);
        line(c,x-radius*.45,y-radius*.45,x+radius*.45,y+radius*.45,1,color);
        line(c,x-radius*.45,y+radius*.45,x+radius*.45,y-radius*.45,1,color);
    }
    static void impact(DrawContext c,int x,int y,int size,double time,int color,boolean knockout) {
        double p=Math.clamp(time/(knockout?.85:.65),0,1);if(p>=1)return;
        double r=size*(.20+p*(knockout?1.35:.75));
        glow(c,x,y,r,color,(1-p)*(knockout?.9:.6));
        ring(c,x,y,r,knockout?.62:.8,alpha(color,(1-p)*.8));
        for(int i=0;i<12;i++) {
            double a=i*Math.PI/6+.2,inner=r*(.35+.45*p),outer=r*(1+(i%3)*.18);
            line(c,x+Math.cos(a)*inner,y+Math.sin(a)*inner,
                x+Math.cos(a)*outer,y+Math.sin(a)*outer,Math.max(1,size*.045*(1-p)),alpha(i%2==0?0xFFFFFFFF:color,1-p));
            if(knockout&&i%2==0)star(c,(int)(x+Math.cos(a)*r),(int)(y+Math.sin(a)*r),size*.07*(1-p),alpha(0xFFFFE766,1-p));
        }
    }
    /** Original soft gradients and typed impact shapes; no downloaded game textures. */
    static void attackImpact(DrawContext c,int x,int y,int size,double time,EnergyType type,BattleAnimationEngine.Quality quality) {
        if(type==EnergyType.DRAGON||type==EnergyType.FIGHTING) {
            if(time>=0&&time<.86)heavyImpact(mesh(c),x,y,size,time,type==EnergyType.FIGHTING,quality);
            return;
        }
        double p=Math.clamp(time/(type==EnergyType.PSYCHIC||type==EnergyType.FAIRY?1.05:.65),0,1);if(p>=1||time<0)return;
        if(type==EnergyType.FIRE) {
            double bloom=Math.sin(Math.PI*Math.min(1,p*2.4));
            glow(c,x,y,size*(.40+.55*BattleMotion.out(p)),0xFFFF9B37,(1-p)*1.5);
            glow(c,x,y,size*.35,0xFFFFF2BA,bloom*1.7);
            ring(c,x,y,size*(.12+.63*BattleMotion.out(p)),.70,alpha(0xFFFFE3A0,(1-p)*.65));
            for(int i=0;i<8;i++){
                double a=i*Math.PI/4,r=size*(.10+.42*BattleMotion.out(p));
                double px=x+Math.cos(a)*r,py=y+Math.sin(a)*r*.65-size*p*.28;
                BattleEffectMesh.glow(mesh(c),px,py,size*.22,size*.32,alpha(0xFFFF8B29,(1-p)*.6));
                var m=c.getMatrices();m.push();m.translate(px,py,0);
                m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float)(Math.cos(a)*24)));
                BattleEffectMesh.flame(mesh(c),size*.22*(1-p),size*(.28+.14*(i%3))*(1-p),Math.sin(i+p*5)*.3,alpha(i%2==0?0xFFFFAD37:0xFFFF762F,(1-p)*.85));
                m.pop();
            }
            for(int i=0;i<7;i++){
                double a=i*Math.PI*2/7,r=size*(.15+p*.68);
                disc(c,x+(int)(Math.cos(a)*r),y+(int)(Math.sin(a)*r*.7-size*p*.18),Math.max(1,(int)(size*.025*(1-p))),alpha(0xFFFFED9C,1-p));
            }
        } else if(type==EnergyType.GRASS) {
            grassImpact(mesh(c),x,y,size,time,quality);
        } else if(type==EnergyType.WATER) {
            // Blue-white shell and outward facets, based on the official Pocket battle trailer.
            double r=size*(.26+.39*BattleMotion.out(p));
            glow(c,x,y,r*1.5,0xFF65DFFF,(1-p)*1.5);
            glow(c,x,y,r*.8,0xFFE5FFFF,(1-p)*1.4);
            ring(c,x,y,r,.86,alpha(0xFFBBF7FF,(1-p)*.75));
            for(int i=0;i<9;i++) {
                double a=i*Math.PI*2/9+p*.3,px=x+Math.cos(a)*r,py=y+Math.sin(a)*r*.86;
                BattleEffectMesh.glow(mesh(c),px,py,size*.28,size*.22,alpha(0xFF80E5FF,(1-p)*.65));
                var m=c.getMatrices();m.push();m.translate(px,py,0);
                m.multiply(RotationAxis.POSITIVE_Z.rotation((float)(a+Math.PI/2)));
                BattleEffectMesh.crystal(mesh(c),size*.045*(1-p),size*(.13+(i%3)*.035)*(1-p),alpha(0xFF78D9FF,1-p));m.pop();
                if(i%3==0)star(c,(int)px,(int)py,size*.045*(1-p),alpha(0xFFFFFFFF,1-p));
            }
        } else if(type==EnergyType.PSYCHIC||type==EnergyType.FAIRY) {
            psychicImpact(mesh(c),x,y,size,time,quality,type==EnergyType.FAIRY);
        } else if(type==EnergyType.METAL) {
            metalImpact(mesh(c),x,y,size,time,quality);
        } else if(type==EnergyType.LIGHTNING) {
            glow(c,x,y,size*.6,0xFFFFDF48,(1-p)*.8);
            for(int arm=0;arm<5;arm++) {
                double a=arm*Math.PI*2/5+p*.3,px=x,py=y;
                for(int j=1;j<=5;j++) {
                    double r=size*(.12+j*.12)*(1+p*.4),bend=(j%2==0?1:-1)*.17;
                    double nx=x+Math.cos(a+bend)*r,ny=y+Math.sin(a+bend)*r;
                    line(c,px,py,nx,ny,Math.max(1,size*.04*(1-p)),alpha(0xFFFFF5B0,1-p));px=nx;py=ny;
                }
            }
        } else if(type==EnergyType.DARKNESS) {
            darknessImpact(mesh(c),x,y,size,time,quality);
        } else if(type==EnergyType.COLORLESS) {
            colorlessImpact(mesh(c),x,y,size,time,quality);
        } else impact(c,x,y,size,time,0xFFFFCF55,false);
    }

    private static void grassImpact(BattleEffectMesh.Sink out,int x,int y,int size,double time,BattleAnimationEngine.Quality quality){
        double flash=BattleMotion.envelope(time,.20,.03,.12),spread=BattleMotion.out(time/.16);
        BattleEffectMesh.glow(out,x,y,size*.78,size*.92,alpha(0xFFC9FF56,flash));
        BattleEffectMesh.glow(out,x,y,size*.35,alpha(0xFFFFFFD7,flash));
        int count=6+quality.branches;
        for(int i=0;i<count;i++){
            double angle=i*2*Math.PI/count+.17*Math.sin(i*3);
            double dx=Math.cos(angle),dy=Math.sin(angle);
            spike(out,x,y,dx,dy,size*.28*spread,size*(.56+.18*(i%3))*spread,size*.09*flash,
                alpha(0xFFEEFF91,flash),alpha(0xFF7DDD2C,flash));
        }
        double leaves=BattleMotion.envelope(time,.61,.11,.31);
        for(int i=0;i<quality.branches+2;i++){
            double a=i*2.4+.3*time,r=size*(.23+.63*BattleMotion.out(time/.45));
            double px=x+r*Math.cos(a),py=y+r*Math.sin(a)*.83+size*.33*time*time;
            double heading=a+time*(i%2==0?6:-5);
            spike(out,px,py,Math.cos(heading),Math.sin(heading),size*.044,size*.13,size*.034*leaves,
                alpha(0xFF78C63B,leaves),alpha(0xFFC9F170,leaves));
        }
    }

    private static void psychicImpact(BattleEffectMesh.Sink out,int x,int y,int size,double time,BattleAnimationEngine.Quality quality,boolean fairy){
        double flash=BattleMotion.envelope(time,.18,.028,.095);
        double shell=BattleMotion.envelope(time,1.02,.15,.38),spread=BattleMotion.out(time/.22);
        double r=size*(.12+.53*spread);
        // Pocket Florges uses a violet shell; the physical TCG Fairy palette tints this variant.
        BattleEffectMesh.glow(out,x,y,r*1.55,alpha(fairy?0xFFE978CF:0xFFD831FF,shell));
        BattleEffectMesh.ellipse(out,x,y,r*.66,r*.64,alpha(fairy?0xFF9F467D:0xFF8A288D,shell*.83));
        for(int i=0;i<2;i++){
            double radius=r*(i==0?.67:.94);
            BattleEffectMesh.ring(out,x,y,radius,.96,size*.042*shell,alpha(i==0?0xFFFFDAFF:0xFFE896FF,shell));
        }
        int count=6+quality.branches;
        double flare=BattleMotion.envelope(time,.54,.10,.26);
        for(int i=0;i<count;i++){
            double angle=i*2*Math.PI/count+.18*time,dx=Math.cos(angle),dy=Math.sin(angle);
            spike(out,x+dx*r*.76,y+dy*r*.76,dx,dy,r*.19,r*(.40+.13*(i%3)),size*.062*flare,
                alpha(0xFFFFCAFF,flare),alpha(fairy?0xFFFFA1D7:0xFFE35AFB,flare));
        }
        BattleEffectMesh.glow(out,x,y,r*1.23,alpha(0xFFFFFFFF,flash));
        BattleEffectMesh.ellipse(out,x,y,r*.54,r*.52,alpha(0xFFFFFFFF,flash));
    }

    private static void metalImpact(BattleEffectMesh.Sink out,int x,int y,int size,double time,BattleAnimationEngine.Quality quality){
        double contact=BattleMotion.envelope(time,.16,.024,.08);
        double extent=size*BattleMotion.out(time/.065);
        BattleEffectMesh.glow(out,x,y,extent*.84,alpha(0xFFE7FFFF,contact));
        spike(out,x,y,.95,-.31,extent*.27,extent*1.18,extent*.095*contact,alpha(0xFFFFFFFF,contact),alpha(0xFFE3F8FF,contact));
        spike(out,x,y,-.95,.31,extent*.27,extent*1.18,extent*.095*contact,alpha(0xFFFFFFFF,contact),alpha(0xFFE3F8FF,contact));
        double age=time-.064;
        if(age<=0)return;
        double spread=BattleMotion.out(age/.23);
        double shell=BattleMotion.envelope(age,.47,.064,.23);
        int count=8+quality.branches;
        for(int i=0;i<count;i++){
            double angle=i*2*Math.PI/count+.10*Math.sin(i*5);
            double dx=Math.cos(angle),dy=Math.sin(angle);
            double radius=size*(.26+.32*spread);
            double px=x+dx*radius,py=y+dy*radius;
            double half=size*(.085+.018*(i%3))*shell;
            double tip=size*(.25+.055*(i%4))*spread;
            int ridge=alpha(0xFFF2FFFF,shell);
            double lx=px-dy*half,ly=py+dx*half,rx=px+dy*half,ry=py-dx*half;
            double tx=px+dx*tip,ty=py+dy*tip;
            out.vertex(x,y,ridge);out.vertex(lx,ly,alpha(0xFF8C9B9F,shell));out.vertex(tx,ty,ridge);out.vertex(px,py,ridge);
            out.vertex(x,y,ridge);out.vertex(px,py,ridge);out.vertex(tx,ty,ridge);out.vertex(rx,ry,alpha(0xFF516069,shell));
        }
        BattleEffectMesh.glow(out,x,y,size*(.26+.31*spread),alpha(0xFF657078,
            BattleMotion.envelope(age,.58,.17,.30)*.8));
    }

    private static void heavyImpact(BattleEffectMesh.Sink out,int x,int y,int size,double time,boolean fighting,BattleAnimationEngine.Quality quality) {
        double flash=BattleMotion.envelope(time,.17,.028,.09);
        double radius=size*(.14+.80*BattleMotion.out(time/.14));
        BattleEffectMesh.glow(out,x,y,radius,radius*.88,alpha(0xFFFFCC56,flash));
        BattleEffectMesh.ellipse(out,x,y,radius*.65,radius*.64,alpha(0xFFFFF8CE,flash));
        int rays=6+quality.branches;
        for(int i=0;i<rays;i++){
            double angle=i*2*Math.PI/rays+.21;
            spike(out,x,y,Math.cos(angle),Math.sin(angle),radius*.27,radius*(.83+.16*(i%3)),
                size*.085*flash,alpha(0xFFFFFFFF,flash),alpha(0xFFFFEBAC,flash*.78));
        }
        double shellStart=fighting?.17:.08;
        double age=time-shellStart;
        if(age<=0)return;
        double expansion=BattleMotion.out(age/.30);
        double shell=BattleMotion.envelope(age,.86-shellStart,.08,.32);
        int rim=fighting?0xFFFF9829:0xFFEAC74E;
        double r=size*(.30+.42*expansion);
        BattleEffectMesh.glow(out,x,y,r*1.18,alpha(rim,shell*.75));
        for(int i=0;i<(fighting?2:3);i++){
            double tilt=fighting?.18*Math.sin(i*3+age*8):i*Math.PI/3+age*1.2;
            orbit(out,x,y,r*(1-i*.075),fighting?.78:.54,tilt,size*.034*shell,alpha(rim,shell),alpha(0xFFFFF1A7,shell));
        }
        if(fighting)for(int i=0;i<quality.branches+2;i++){
            double angle=i*2.40+.31,dx=Math.cos(angle),dy=Math.sin(angle);
            double dist=size*(.32+.56*expansion);
            double px=x+dx*dist,py=y+dy*dist*.77+size*.20*age*age;
            spike(out,px,py,Math.cos(angle+age*4),Math.sin(angle+age*4),size*.055,size*.14,
                size*.044*shell,alpha(0xFF805231,shell),alpha(0xFF4A3830,shell));
        }
    }

    private static void orbit(BattleEffectMesh.Sink out,double x,double y,double r,double squash,double tilt,double half,int edge,int light) {
        double cos=Math.cos(tilt),sin=Math.sin(tilt),px=r*cos,py=r*sin;
        double inner=1-half/r,outer=1+half/r;
        for(int i=1;i<=32;i++){
            double angle=i*Math.PI/16,u=r*Math.cos(angle),v=r*squash*Math.sin(angle);
            double nx=u*cos-v*sin,ny=u*sin+v*cos;
            out.vertex(x+px*inner,y+py*inner,light);out.vertex(x+nx*inner,y+ny*inner,light);
            out.vertex(x+nx*outer,y+ny*outer,edge);out.vertex(x+px*outer,y+py*outer,edge);
            px=nx;py=ny;
        }
    }

    private static void darknessImpact(BattleEffectMesh.Sink out,int x,int y,int size,double time,BattleAnimationEngine.Quality quality) {
        double charge=BattleMotion.envelope(time,.13,.025,.045);
        double shade=BattleMotion.envelope(time,.47,.045,.23);
        BattleEffectMesh.glow(out,x,y,size*.82,size*.95,alpha(0xFF102623,shade));
        BattleEffectMesh.glow(out,x,y,size*(.34-.16*BattleMotion.out(time/.10)),alpha(0xFFFFB6EC,charge));
        double age=time-.085;
        if(age<0)return;
        double spread=BattleMotion.out(age/.105),core=BattleMotion.envelope(age,.17,.023,.11);
        double ink=BattleMotion.envelope(age,.43,.046,.23);
        int count=6+quality.branches;
        for(int i=0;i<count;i++){
            double angle=i*2*Math.PI/count+.11*Math.sin(7*i);
            double dx=Math.cos(angle),dy=Math.sin(angle);
            double reach=size*(.54+.09*(i%4))*spread;
            double base=size*(.16+.10*BattleMotion.out(age/.31));
            spike(out,x,y,dx,dy,base,reach,size*.085*ink,alpha(0xFF102827,ink),alpha(0xFF27423B,ink*.8));
            spike(out,x,y,dx,dy,base*.63,reach*(i%3==0?.96:.51),size*.065*core,
                alpha(0xFFF5FFFF,core),alpha(0xFFB2EFE3,core));
        }
        BattleEffectMesh.glow(out,x,y,size*.38,alpha(0xFFDBFFF9,core));
    }

    private static void colorlessImpact(BattleEffectMesh.Sink out,int x,int y,int size,double time,BattleAnimationEngine.Quality quality) {
        double flash=BattleMotion.envelope(time,.15,.022,.08);
        BattleEffectMesh.glow(out,x,y,size*.68,size*.76,alpha(0xFFFFED93,flash));
        double age=time-.045;
        if(age<0)return;
        double spread=BattleMotion.out(age/.18);
        double white=BattleMotion.envelope(age,.32,.042,.18);
        BattleEffectMesh.glow(out,x,y,size*(.22+.55*spread),size*.38,alpha(0xFFFFFFFF,white));
        int pairs=quality==BattleAnimationEngine.Quality.LOW?3:6;
        for(int i=0;i<pairs*2;i++){
            int side=i%2==0?-1:1,row=i/2;
            double angle=(row/(pairs-1.0)-.5)*1.10;
            double dx=side*Math.cos(angle),dy=Math.sin(angle);
            double length=size*(.48+.17*((row*2+i)%3))*spread;
            double base=size*.16*spread,half=size*(.04+.008*row)*white;
            int root=alpha(0xFFFFFFFF,white),tip=alpha(0xFFE8F7FF,white*.70);
            spike(out,x,y,dx,dy,base,length,half,root,tip);
        }
        double debris=BattleMotion.envelope(age,.54,.12,.29);
        for(int i=0;i<8;i++){
            double angle=i*Math.PI/4+.23,distance=size*(.32+.50*BattleMotion.out(age/.50));
            double dx=Math.cos(angle),dy=Math.sin(angle);
            double px=x+dx*distance,py=y+dy*distance*.70;
            BattleEffectMesh.line(out,px,py,px+dx*size*.057,py+dy*size*.057,
                size*.021,alpha(0xFFF3F6FC,debris));
        }
    }

    private static void spike(BattleEffectMesh.Sink out,double x,double y,double dx,double dy,
                              double base,double length,double half,int root,int tip) {
        if(half<=0||length<=0)return;
        double bx=x+dx*base,by=y+dy*base;
        out.vertex(x,y,root);
        out.vertex(bx-dy*half,by+dx*half,root);
        out.vertex(x+dx*length,y+dy*length,tip);
        out.vertex(bx+dy*half,by-dx*half,root);
    }

    static void counterImpact(DrawContext c,int x,int y,int size,double time) {
        double p=Math.clamp(time/.7,0,1);if(time<0||p>=1)return;
        int r=Math.max(2,(int)(size*.13*(1+p*.3)));
        disc(c,x,y,r,alpha(0xFFC37D23,1-p));
        ring(c,x,y,r,1,alpha(0xFFFFDB79,1-p));
        for(int i=0;i<3;i++) {
            double a=i*Math.PI*2/3+p;
            star(c,x+(int)(Math.cos(a)*size*p*.5),y+(int)(Math.sin(a)*size*p*.5),size*.04*(1-p),alpha(0xFFFFD76A,1-p));
        }
    }
}
