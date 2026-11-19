package dev.hua.ptcglive.client;

import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Independent perspective camera. World X runs across the table, +Z toward the player, +Y up. */
final class PocketTableCamera {
    static final double TABLE_WIDTH=18, CARD_WIDTH=1.30, CARD_ASPECT=63.0/88;
    record Settings(double pitch,double fov,double height,double distance,double aspect,double spacing){
        Settings {
            if(!Double.isFinite(pitch+fov+height+distance+aspect+spacing)||pitch<30||pitch>80||fov<20||fov>65
                ||height<=0||distance<=0||aspect<1.3||aspect>2.3||spacing<.7||spacing>1.1)
                throw new IllegalArgumentException("Camera parameters outside supported range");
            if(Math.abs(Math.toDegrees(Math.atan2(height,distance))-pitch)>.001)
                throw new IllegalArgumentException("A camera aimed at table center needs consistent height, distance and pitch");
        }
        static Settings defaults(){return new Settings(62,34,10,10/Math.tan(Math.toRadians(62)),1.8,1);}
        Settings pitch(double value){double p=Math.clamp(value,30,80),r=Math.hypot(height,distance);return new Settings(p,fov,r*Math.sin(Math.toRadians(p)),r*Math.cos(Math.toRadians(p)),aspect,spacing);}
        Settings fov(double value){return new Settings(pitch,Math.clamp(value,20,65),height,distance,aspect,spacing);}
        Settings height(double value){double h=Math.clamp(value,distance*Math.tan(Math.toRadians(30)),distance*Math.tan(Math.toRadians(80)));return new Settings(Math.toDegrees(Math.atan2(h,distance)),fov,h,distance,aspect,spacing);}
        Settings distance(double value){double d=Math.clamp(value,height/Math.tan(Math.toRadians(80)),height/Math.tan(Math.toRadians(30)));return new Settings(Math.toDegrees(Math.atan2(height,d)),fov,height,d,aspect,spacing);}
        Settings aspect(double value){return new Settings(pitch,fov,height,distance,Math.clamp(value,1.3,2.3),spacing);}
        Settings spacing(double value){return new Settings(pitch,fov,height,distance,aspect,Math.clamp(value,.7,1.1));}
    }
    record Point(double x,double y,double depth){}
    record Vec(double x,double y,double z){}
    record Ray(Vec origin,Vec direction){}
    record Pose(double x,double y,double z,double width,double height,double tilt,double yaw){
        Pose move(double dx,double dy,double dz){return new Pose(x+dx,y+dy,z+dz,width,height,tilt,yaw);}
        Pose scale(double factor){return new Pose(x,y,z,width*factor,height*factor,tilt,yaw);}
        Pose angles(double pitch,double rotation){return new Pose(x,y,z,width,height,pitch,rotation);}
        Vec vertex(double u,double v){
            double a=(u-.5)*width,b=(v-.5)*height,cx=Math.cos(Math.toRadians(tilt)),sx=Math.sin(Math.toRadians(tilt)),cy=Math.cos(Math.toRadians(yaw)),sy=Math.sin(Math.toRadians(yaw));
            return new Vec(x+a*cy+b*cx*sy,y-b*sx,z-a*sy+b*cx*cy);
        }
        private Vec local(Vec p,boolean direction){
            double dx=p.x-(direction?0:x),dy=p.y-(direction?0:y),dz=p.z-(direction?0:z);
            double cx=Math.cos(Math.toRadians(tilt)),sx=Math.sin(Math.toRadians(tilt)),cy=Math.cos(Math.toRadians(yaw)),sy=Math.sin(Math.toRadians(yaw));
            double rz=dx*sy+dz*cy;
            return new Vec(dx*cy-dz*sy,dy*cx+rz*sx,-dy*sx+rz*cx);
        }
        double intersect(Ray ray){
            var o=local(ray.origin,false);var d=local(ray.direction,true);
            if(Math.abs(d.y)<1e-9)return Double.POSITIVE_INFINITY;
            double t=-o.y/d.y,px=o.x+d.x*t,pz=o.z+d.z*t;
            return t>=0&&Math.abs(px)<=width/2+1e-6&&Math.abs(pz)<=height/2+1e-6?t:Double.POSITIVE_INFINITY;
        }
    }
    final int screenWidth,screenHeight;
    final Rect viewport;
    final Settings settings;
    final boolean portrait;
    final double cardWidth;
    final double tableDepth,actualHeight,actualDistance;
    final Matrix4f view=new Matrix4f(),projection=new Matrix4f(),clip=new Matrix4f(),inverse=new Matrix4f();
    PocketTableCamera(int width,int height,Rect viewport,Settings settings){
        this(width,height,viewport,settings,0);
    }
    PocketTableCamera(int width,int height,Rect viewport,Settings settings,double fill){
        if(width<=0||height<=0||viewport.width()<=0||viewport.height()<=0)throw new IllegalArgumentException("Empty viewport");
        screenWidth=width;screenHeight=height;this.viewport=viewport;this.settings=settings;
        portrait=viewport.height()>viewport.width()*1.3;cardWidth=portrait?3.0:CARD_WIDTH;
        // Portrait columns need room for four full card rows, including the larger Active cards.
        double aspect=portrait?Math.min(.76,settings.aspect*Math.max(.28,viewport.width()/(viewport.height()*1.4))):settings.aspect;
        tableDepth=TABLE_WIDTH/aspect;
        double pitch=Math.toRadians(settings.pitch),fy=1/Math.tan(Math.toRadians(settings.fov)/2),fx=fy*viewport.height()/viewport.width();
        // Initial centered camera; presets then frame the actual asymmetric projected mat bounds.
        double r=Math.hypot(settings.height,settings.distance);
        for(double z:new double[]{-tableDepth/2,tableDepth/2}){
            r=Math.max(r,z*Math.cos(pitch)+TABLE_WIDTH/2*fx/.92);
            r=Math.max(r,z*Math.cos(pitch)+Math.abs(z*Math.sin(pitch))*fy/.92);
        }
        actualHeight=r*Math.sin(pitch);actualDistance=r*Math.cos(pitch);
        view.lookAt(0,(float)actualHeight,(float)actualDistance,0,0,0,0,1,0);
        // A GUI-only scene has known depth bounds. A world-sized far plane caused z-fighting on narrow windows.
        projection.perspective((float)Math.toRadians(settings.fov),(float)viewport.width()/viewport.height(),(float)(r*.2),(float)(r+tableDepth+TABLE_WIDTH+20));
        float sx=(float)viewport.width()/width,sy=(float)viewport.height()/height;
        float tx=(2f*viewport.x()+viewport.width())/width-1,ty=1-(2f*viewport.y()+viewport.height())/height;
        clip.translation(tx,ty,0).scale(sx,sy,1).mul(projection).mul(view);
        if(fill>0){
            double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=-minX,maxY=-minX;
            for(double x:new double[]{-TABLE_WIDTH/2,TABLE_WIDTH/2})for(double z:new double[]{-tableDepth/2,tableDepth/2}){
                var p=project(new Vec(x,0,z));minX=Math.min(minX,p.x);maxX=Math.max(maxX,p.x);minY=Math.min(minY,p.y);maxY=Math.max(maxY,p.y);
            }
            double scale=Math.min(viewport.width()/(maxX-minX),viewport.height()/(maxY-minY))*fill;
            double cx=(minX+maxX)/2,cy=(minY+maxY)/2,targetX=viewport.x()+viewport.width()/2.,targetY=viewport.y()+viewport.height()/2.;
            // Homogeneous focal zoom + lens shift: cards, mat, FX anchors and inverse rays share this matrix.
            var framing=new Matrix4f().translation((float)((2*targetX/width-1)-scale*(2*cx/width-1)),
                (float)((1-2*targetY/height)-scale*(1-2*cy/height)),0).scale((float)scale,(float)scale,1);
            clip.set(framing.mul(clip));
        }
        inverse.set(clip).invert();
    }
    Point project(Vec world){
        var p=new Vector4f((float)world.x,(float)world.y,(float)world.z,1).mul(clip);
        return new Point((p.x/p.w+1)*screenWidth/2.,(1-p.y/p.w)*screenHeight/2.,p.w);
    }
    Point project(Pose pose){return project(new Vec(pose.x,pose.y,pose.z));}
    Ray ray(double x,double y){
        float nx=(float)(2*x/screenWidth-1),ny=(float)(1-2*y/screenHeight);
        var a=new Vector4f(nx,ny,-1,1).mul(inverse);a.div(a.w);
        var b=new Vector4f(nx,ny,1,1).mul(inverse);b.div(b.w);
        double dx=b.x-a.x,dy=b.y-a.y,dz=b.z-a.z,n=Math.sqrt(dx*dx+dy*dy+dz*dz);
        return new Ray(new Vec(a.x,a.y,a.z),new Vec(dx/n,dy/n,dz/n));
    }
    /** Symmetric bounds keep screen-facing hit FX centered on the actual projected world center. */
    Rect bounds(Pose pose){
        var center=project(pose);double rx=0,ry=0;
        for(int i=0;i<4;i++){var p=project(pose.vertex(i<2?0:1,i==0||i==3?0:1));rx=Math.max(rx,Math.abs(p.x-center.x));ry=Math.max(ry,Math.abs(p.y-center.y));}
        int w=Math.max(2,(int)Math.ceil(rx)*2),h=Math.max(2,(int)Math.ceil(ry)*2);
        return new Rect((int)Math.round(center.x)-w/2,(int)Math.round(center.y)-h/2,w,h);
    }
    double x(double logicalX,PtcgLiveTableLayout l){
        double first=l.lostX()+l.benchWidth()/2.,last=l.discardX()+l.benchWidth()/2.;
        double center=l.activeX()+l.activeWidth()/2.;
        if(portrait)return (logicalX-center)/(l.benchWidth()+l.slotGap())*cardWidth*1.06;
        return (logicalX-center)/Math.max(center-first,last-center)*(TABLE_WIDTH-2.4)/2;
    }
    double z(double logicalY,PtcgLiveTableLayout l){
        double farBench=l.opponentBenchY()+l.benchHeight()/2.,farActive=l.opponentActiveY()+l.activeHeight()/2.;
        double nearActive=l.ownActiveY()+l.activeHeight()/2.,nearBench=l.ownBenchY()+l.benchHeight()/2.;
        double b=Math.min((tableDepth/2-(portrait?2.6:1.3))*settings.spacing,tableDepth/2-cardWidth/CARD_ASPECT/2-.12),a=b*.36;
        if(logicalY<=farActive)return interpolate(logicalY,farBench,farActive,-b,-a);
        if(logicalY<=nearActive)return interpolate(logicalY,farActive,nearActive,-a,a);
        return interpolate(logicalY,nearActive,nearBench,a,b);
    }
    private static double interpolate(double value,double start,double end,double a,double b){return a+(b-a)*(value-start)/Math.max(.001,end-start);}
    Pose card(Rect logical,PtcgLiveTableLayout layout){
        double cx=logical.x()+logical.width()/2.,cy=logical.y()+logical.height()/2.;
        double px=x(cx,layout),pz=z(cy,layout);
        boolean sidePile=false;
        if(portrait){
            boolean far=cy<layout.centerY();
            double activeZ=z(layout.activeY(far)+layout.activeHeight()/2.,layout),half=layout.benchWidth()/2.;
            double benchZ=z(layout.benchY(far)+layout.benchHeight()/2.,layout);
            double sign=far?-1:1,pileRow=sign*(Math.abs(benchZ)-cardWidth/CARD_ASPECT*.84-.25);
            double innerRow=pileRow-sign*(cardWidth/CARD_ASPECT*.68+.3);
            if(Math.abs(cx-(layout.deckX()+half))<half*.6){px=7.25;pz=pileRow;sidePile=true;}
            else if(Math.abs(cx-(layout.discardX()+half))<half*.6){px=7.25;pz=innerRow;sidePile=true;}
            else if(Math.abs(cx-(layout.lostX()+half))<half*.6){px=-7.25;pz=pileRow;sidePile=true;}
            else if(cx>=layout.prizeX()+half-1&&cx<layout.trayX()){
                px=-7.25+(cx-layout.prizeX()-half)/Math.max(1,layout.prizeFan())*.13;pz=innerRow;sidePile=true;
            }else if(Math.abs(cy-layout.centerY())<layout.benchHeight()*.2&&cx<layout.activeX()){px=-4.2;pz=0;}
        }
        double scale=sidePile?.68:portrait&&Math.abs(cx-(layout.activeX()+layout.activeWidth()/2.))<1&&logical.height()==layout.activeHeight()?1.2:1;
        return new Pose(px,.025,pz,logical.width()/(double)layout.benchWidth()*cardWidth*scale,
            logical.height()/(double)layout.benchHeight()*cardWidth/CARD_ASPECT*scale,0,0);
    }
    /** Logical regions use the same ray, while card boundaries use their own lifted plane. */
    boolean contains(Rect logical,PtcgLiveTableLayout layout,double mouseX,double mouseY){
        Ray ray=ray(mouseX,mouseY);if(Math.abs(ray.direction.y)<1e-9)return false;
        double t=-ray.origin.y/ray.direction.y;if(t<0)return false;
        double px=ray.origin.x+ray.direction.x*t,pz=ray.origin.z+ray.direction.z*t;
        return px>=x(logical.x(),layout)&&px<=x(logical.right(),layout)&&pz>=z(logical.y(),layout)&&pz<=z(logical.bottom(),layout);
    }
}
