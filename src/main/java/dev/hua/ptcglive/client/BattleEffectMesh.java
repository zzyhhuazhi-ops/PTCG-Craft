package dev.hua.ptcglive.client;

/** Fixed-size, antialiased GUI meshes. Quad output shares Minecraft's existing GUI batch. */
final class BattleEffectMesh {
    static final int SEGMENTS=48;
    private static final double[] X=new double[SEGMENTS+1],Y=new double[SEGMENTS+1];
    private static final double[] FLAME_X={0,-.5,-.3,0,.42,.28},FLAME_Y={.24,0,-.42,-1,-.36,.1};
    static { for(int i=0;i<=SEGMENTS;i++){double a=i*Math.PI*2/SEGMENTS;X[i]=Math.cos(a);Y[i]=Math.sin(a);} }
    @FunctionalInterface interface Sink { void vertex(double x,double y,int color); }
    private BattleEffectMesh(){}
    private static int opacity(int color,double a){return ((int)Math.round((color>>>24)*Math.clamp(a,0,1))<<24)|(color&0xFFFFFF);}
    private static void band(Sink out,double x,double y,double rx,double ry,double inner,double outer,int inside,int outside){
        for(int i=0;i<SEGMENTS;i++){
            out.vertex(x+X[i]*rx*inner,y+Y[i]*ry*inner,inside);
            out.vertex(x+X[i+1]*rx*inner,y+Y[i+1]*ry*inner,inside);
            out.vertex(x+X[i+1]*rx*outer,y+Y[i+1]*ry*outer,outside);
            out.vertex(x+X[i]*rx*outer,y+Y[i]*ry*outer,outside);
        }
    }
    static void ellipse(Sink out,double x,double y,double rx,double ry,int color){
        if(rx<=0||ry<=0||(color>>>24)==0)return;
        double core=Math.max(0,1-1/Math.min(rx,ry));
        band(out,x,y,rx,ry,0,core,color,color);
        band(out,x,y,rx,ry,core,1,color,opacity(color,0));
    }
    static void glow(Sink out,double x,double y,double radius,int color){
        glow(out,x,y,radius,radius,color);
    }
    static void glow(Sink out,double x,double y,double rx,double ry,int color){
        if(rx<=0||ry<=0||(color>>>24)==0)return;
        band(out,x,y,rx,ry,0,.22,color,opacity(color,.72));
        band(out,x,y,rx,ry,.22,.5,opacity(color,.72),opacity(color,.27));
        band(out,x,y,rx,ry,.5,.78,opacity(color,.27),opacity(color,.06));
        band(out,x,y,rx,ry,.78,1,opacity(color,.06),opacity(color,0));
    }
    static void crystal(Sink out,double width,double height,int color){
        // Two shaded facets, clockwise in GUI coordinates, share a bright center seam.
        int pale=(color&0xFF000000)|0xEEFFFF;
        out.vertex(0,-height,pale);out.vertex(-width,0,color);out.vertex(0,height,pale);out.vertex(0,0,pale);
        out.vertex(0,-height,pale);out.vertex(0,0,pale);out.vertex(0,height,pale);out.vertex(width,0,opacity(color,.55));
    }
    static void flame(Sink out,double width,double height,double bend,int color){
        int center=(color&0xFF000000)|0xFFF4AA;
        for(int i=0;i<FLAME_X.length;i++){
            int next=(i+1)%FLAME_X.length;
            out.vertex(0,-height*.12,center);
            out.vertex((next==3?bend:FLAME_X[next])*width,FLAME_Y[next]*height,opacity(color,.7));
            out.vertex((i==3?bend:FLAME_X[i])*width,FLAME_Y[i]*height,opacity(color,.7));
            out.vertex(0,-height*.12,center);
        }
    }
    static void ring(Sink out,double x,double y,double radius,double squash,double thickness,int color){
        if(radius<=0||squash<=0||thickness<=0||(color>>>24)==0)return;
        double outer=radius+thickness/2+1;
        double a=Math.max(0,(radius-thickness/2-1)/outer),b=Math.max(0,(radius-thickness/2)/outer),d=(outer-1)/outer;
        band(out,x,y,outer,outer*squash,a,b,opacity(color,0),color);
        band(out,x,y,outer,outer*squash,b,d,color,color);
        band(out,x,y,outer,outer*squash,d,1,color,opacity(color,0));
    }
    static void line(Sink out,double x,double y,double tx,double ty,double width,int color){
        double length=Math.hypot(tx-x,ty-y);
        if(length<.01||width<=0||(color>>>24)==0)return;
        double nx=-(ty-y)/length,ny=(tx-x)/length,h=width/2;
        strip(out,x,y,tx,ty,nx,ny,-h,h,color,color);
        strip(out,x,y,tx,ty,nx,ny,-h-.7,-h,opacity(color,0),color);
        strip(out,x,y,tx,ty,nx,ny,h,h+.7,color,opacity(color,0));
    }
    /** Slanted banner with opposite rounded corners and soft side edges. Fixed work at any GUI scale. */
    static void ribbon(Sink out,double x,double y,double width,double height,int left,int right){
        ribbon(out,x,y,width,height,left,right,false);
    }
    static void ribbon(Sink out,double x,double y,double width,double height,int left,int right,boolean reverse){
        if(width<=0||height<=0||((left|right)>>>24)==0)return;
        double r=height*.28;
        for(int i=0;i<48;i++){
            double a=height*i/48,b=height*(i+1)/48;
            double direction=reverse?-1:1;
            double la=x+(height*.5-a)*.22*direction+cornerInset(reverse?height-a:a,r);
            double lb=x+(height*.5-b)*.22*direction+cornerInset(reverse?height-b:b,r);
            double ra=x+width+(height*.5-a)*.22*direction-cornerInset(reverse?a:height-a,r);
            double rb=x+width+(height*.5-b)*.22*direction-cornerInset(reverse?b:height-b,r);
            out.vertex(la,y+a,left);out.vertex(lb,y+b,left);out.vertex(rb,y+b,right);out.vertex(ra,y+a,right);
            out.vertex(la-1,y+a,opacity(left,0));out.vertex(lb-1,y+b,opacity(left,0));out.vertex(lb,y+b,left);out.vertex(la,y+a,left);
            out.vertex(ra,y+a,right);out.vertex(rb,y+b,right);out.vertex(rb+1,y+b,opacity(right,0));out.vertex(ra+1,y+a,opacity(right,0));
        }
    }
    private static double cornerInset(double distance,double radius){
        return distance>=radius?0:radius-Math.sqrt(Math.max(0,radius*radius-(radius-distance)*(radius-distance)));
    }
    private static void strip(Sink out,double x,double y,double tx,double ty,double nx,double ny,double a,double b,int ca,int cb){
        // GUI projection flips Y. Match DrawContext.fill's front-facing winding.
        out.vertex(x+nx*a,y+ny*a,ca);out.vertex(x+nx*b,y+ny*b,cb);
        out.vertex(tx+nx*b,ty+ny*b,cb);out.vertex(tx+nx*a,ty+ny*a,ca);
    }
}
