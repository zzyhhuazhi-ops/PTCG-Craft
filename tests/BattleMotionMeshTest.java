package dev.hua.ptcglive.client;

import java.util.ArrayList;
import java.util.List;

/** Render-independent checks for bounded work, translucent edges and frame-rate-independent motion. */
public final class BattleMotionMeshTest {
    static void knockout(){
        near(KnockoutAnimation.sample(-1).cardAlpha(),1,"queued departing card must remain visible");
        near(KnockoutAnimation.sample(850).cardAlpha(),0,"departed card remains visible");
        near(KnockoutAnimation.sample(850).ring(),0,"expired knockout glow remains");
        require(KnockoutAnimation.sample(200).flash()>.9,"knockout card disappears without flash cover");
        for(var quality:BattleAnimationEngine.Quality.values())for(int fps:new int[]{30,60,120})for(int frame=0;frame<=fps;frame++){
            double ms=frame*1000.0/fps;var p=KnockoutAnimation.sample(ms);
            require(p.cardAlpha()>=0&&p.cardAlpha()<=1&&p.ring()>=0&&p.ring()<=1,"invalid knockout alpha");
            var vs=new ArrayList<Vertex>();
            KnockoutAnimation.fragments((x,y,c)->vs.add(new Vertex(x,y,c)),0,0,100,p.fragments(),quality);
            require(vs.size()<=896&&vs.size()%4==0,"knockout fragment allocation/work is unbounded");
            for(var v:vs)require(Double.isFinite(v.x)&&Double.isFinite(v.y)&&Math.abs(v.x)<180&&Math.abs(v.y)<180,"unbounded knockout fragment");
            for(int i=0;i<vs.size();i+=4){double area=0;
                for(int j=0;j<4;j++){var a=vs.get(i+j);var b=vs.get(i+(j+1)%4);area+=a.x*b.y-b.x*a.y;}
                require(area<=0,"knockout fragment is back-facing");
            }
            if(quality==BattleAnimationEngine.Quality.OFF||ms>=850)require(vs.isEmpty(),"disabled/expired effect still emits fragments");
            KnockoutAnimation.sample(9999);require(p.equals(KnockoutAnimation.sample(ms)),"seek depends on previous frame history");
        }
    }
    record Vertex(double x,double y,int color) {}
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void near(double a,double b,String why){require(Math.abs(a-b)<.00001,why+": "+a+" / "+b);}
    static void mesh(List<Vertex> vertices,double extent){
        require(!vertices.isEmpty()&&vertices.size()%4==0,"GUI output must contain complete quads");
        require(vertices.size()<=1024,"one primitive exceeds bounded vertex budget");
        for(var v:vertices)require(Double.isFinite(v.x)&&Double.isFinite(v.y)&&Math.abs(v.x)<=extent&&Math.abs(v.y)<=extent,"invalid or unbounded mesh coordinate");
        require(vertices.stream().anyMatch(v->v.color>>>24==0),"outer edge must fade to transparent");
        require(vertices.stream().anyMatch(v->v.color>>>24>0),"primitive has no visible core");
        for(int i=0;i<vertices.size();i+=4){
            double area=0;
            for(int j=0;j<4;j++){var a=vertices.get(i+j);var b=vertices.get(i+(j+1)%4);area+=a.x*b.y-b.x*a.y;}
            require(area<=.000001,"GUI back-face culling removes an effect quad");
        }
    }
    public static void main(String[] args){
        knockout();
        var vertices=new ArrayList<Vertex>();
        BattleEffectMesh.Sink sink=(x,y,c)->vertices.add(new Vertex(x,y,c));
        BattleEffectMesh.glow(sink,0,0,32,0x80FFFFFF);mesh(vertices,32);
        int small=vertices.size();vertices.clear();
        BattleEffectMesh.glow(sink,0,0,512,0x80FFFFFF);mesh(vertices,512);
        require(vertices.size()==small,"large effects increase CPU geometry work");
        require(vertices.stream().allMatch(v->v.color>>>24<=128),"alpha multiplication made a translucent glow opaque");
        vertices.clear();BattleEffectMesh.ring(sink,0,0,40,.6,1.5,0xFFFFFFFF);mesh(vertices,42);
        require(vertices.stream().allMatch(v->Math.hypot(v.x,v.y/.6)>=38.2),"ring fills its hollow center");
        vertices.clear();BattleEffectMesh.ellipse(sink,0,0,30,9,0xFF000000);mesh(vertices,30);
        require(vertices.stream().allMatch(v->Math.abs(v.y)<=9),"shadow ellipse exceeds vertical bounds");
        vertices.clear();BattleEffectMesh.line(sink,0,0,40,30,2,0xFFEEEEFF);mesh(vertices,42);
        vertices.clear();BattleEffectMesh.ribbon(sink,0,0,180,30,0xFF00D6DF,0xFF3D8AF4);mesh(vertices,190);
        int ribbonVertices=vertices.size();vertices.clear();
        BattleEffectMesh.ribbon(sink,0,0,1000,120,0xFFFFAD72,0xFFFF5267);mesh(vertices,1020);
        require(vertices.size()==ribbonVertices,"GUI resize changes turn ribbon work");
        vertices.clear();BattleEffectMesh.ribbon(sink,0,0,180,30,0xFF00D6DF,0xFF3D8AF4,true);mesh(vertices,190);
        require(vertices.size()==ribbonVertices,"mirrored turn ribbon changes fixed geometry budget");
        require(vertices.get(0).x<vertices.get(vertices.size()-12).x,"own turn must round the bottom-left instead of the top-left corner");
        for(boolean flame:new boolean[]{false,true}){
            vertices.clear();
            if(flame)BattleEffectMesh.flame(sink,12,30,.2,0xFFFFFFFF);else BattleEffectMesh.crystal(sink,8,15,0xFFFFFFFF);
            require(vertices.size()%4==0&&vertices.size()<40,"particle geometry is unbounded");
            for(int i=0;i<vertices.size();i+=4){double area=0;
                for(int j=0;j<4;j++){var a=vertices.get(i+j);var b=vertices.get(i+(j+1)%4);area+=a.x*b.y-b.x*a.y;}
                require(area<=0,"particle is back-facing");
            }
        }
        vertices.clear();BattleEffectMesh.line(sink,0,0,0,0,2,0xFFFFFFFF);
        BattleEffectMesh.glow(sink,0,0,0,0xFFFFFFFF);BattleEffectMesh.ellipse(sink,0,0,5,5,0);
        require(vertices.isEmpty(),"invisible geometry still rendered");
        near(BattleMotion.attack(.65),1,"attack must reach the confirmed impact");
        for(double contact:new double[]{.65,1.2,2.1}){
            near(BattleMotion.attack(contact,contact),1,"PvP/AI attack arrives before damage");
            near(BattleMotion.attack(contact+.38,contact),0,"aligned attack fails to return");
            require(Math.abs(BattleMotion.attack(contact-.000001,contact)-BattleMotion.attack(contact+.000001,contact))<.0001,"aligned contact pose jumps");
        }
        near(BattleMotion.attack(2),0,"stalled frame must recover the resting pose");
        near(BattleMotion.recoil(2),0,"recoil never settles");
        near(BattleMotion.hitScale(2),1,"hit scale never settles");
        near(BattleMotion.turnSlide(0,1.2),1,"turn ribbon must enter from outside");
        near(BattleMotion.turnSlide(.5,1.2),0,"turn ribbon must hold still while readable");
        near(BattleMotion.turnSlide(2,1.2),-1,"skipped frame must finish turn ribbon exit");
        near(BattleMotion.turnTitleOpacity(.05,1.2),0,"turn title appears before ribbon arrival");
        near(BattleMotion.turnTitleOpacity(.5,1.2),1,"turn title unreadable during hold");
        near(BattleMotion.turnTitleOpacity(1.05,1.2),0,"turn title travels away instead of fading before exit");
        for(double boundary:new double[]{.16,.60,.68,1.02})
            require(Math.abs(BattleMotion.attack(boundary-.000001)-BattleMotion.attack(boundary+.000001))<.0001,"attack pose jumps at a phase boundary");
        for(int fps:new int[]{30,60,144}){
            // Every rate and a skipped-frame seek arrive at exactly the same time-based pose.
            near(BattleMotion.attack((fps/2)/(double)fps),BattleMotion.attack(.5),"frame-dependent attack");
            near(BattleMotion.flight(fps/(double)fps,.72),1,"flight failed to settle after a skipped frame");
            for(double duration:new double[]{.58,1.1,1.6,2.4}){
                for(int frame=0;frame<duration*fps+2;frame++){
                    double alpha=BattleMotion.envelope(frame/(double)fps,duration,.08,.28);
                    require(Double.isFinite(alpha)&&alpha>=0&&alpha<=1,"scene opacity outside range");
                }
                near(BattleMotion.envelope(duration+.01,duration,.08,.28),0,"skipped scene frame leaves an overlay behind");
                near(BattleMotion.envelope(-1,duration,.08,.28),0,"future scene appeared early");
                near(BattleMotion.envelope(.5,duration,.08,.28),BattleMotion.envelope((fps/2)/(double)fps,duration,.08,.28),"frame-dependent scene opacity");
            }
        }
        require(BattleMotion.stagger(60,59)+.45<1.2,"large draw outlasts its presentation window");
        require(BattleMotion.numberOpacity(-1)==0&&BattleMotion.numberOpacity(1.1)==0,"damage label survives outside its cue");
        for(int i=0;i<=200;i++){
            double time=i/100.0,p=BattleMotion.flight(time,.72);
            require(p>=0&&p<=1,"flight overshoots the destination");
        }
        System.out.println("BattleMotionMeshTest passed: bounded geometry, transparent edges, impact continuity, 30/60/144 Hz poses, skipped frames and large draws");
    }
}
