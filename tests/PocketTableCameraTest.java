package dev.hua.ptcglive.client;

import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;
import static dev.hua.ptcglive.client.PocketTableCamera.*;

/** Projection/picking invariants independent of Minecraft's camera and frame cadence. */
public final class PocketTableCameraTest {
    private static void require(boolean value,String detail){if(!value)throw new AssertionError(detail);}
    public static void main(String[] args){
        int cases=0;
        for(int[] size:new int[][]{{640,360},{960,540},{1280,720},{400,800},{1024,768},{1920,1080}}){
            var l=PtcgLiveTableLayout.forScreen(size[0],size[1]);
            for(double pitch:new double[]{30,50,80})for(double fov:new double[]{20,40,65})for(double aspect:new double[]{1.3,1.8,2.3}){
                var s=Settings.defaults().pitch(pitch).fov(fov).aspect(aspect);
                var c=new PocketTableCamera(size[0],size[1],new Rect(8,8,l.rightColumnX()-16,l.handHintY()-16),s);
                require(Math.abs(c.x(l.activeX()+l.activeWidth()/2.,l))<1e-8,"active column is off camera center");
                for(boolean far:new boolean[]{true,false}){
                    var slots=new java.util.ArrayList<Rect>();
                    for(int i=0;i<5;i++)slots.add(new Rect(l.benchX(i),l.benchY(far),l.benchWidth(),l.benchHeight()));
                    for(int x:new int[]{l.deckX(),l.discardX(),l.lostX(),l.prizeX()})slots.add(new Rect(x,l.benchY(far),l.benchWidth(),l.benchHeight()));
                    slots.add(new Rect(l.activeX(),l.activeY(far),l.activeWidth(),l.activeHeight()));
                    for(var slot:slots){var pose=c.card(slot,l);
                        for(int i=0;i<4;i++){var v=pose.vertex(i<2?0:1,i%2);require(Math.abs(v.x())<=TABLE_WIDTH/2&&Math.abs(v.z())<=c.tableDepth/2,"card outside physical mat");}
                    }
                }
                for(double x:new double[]{-TABLE_WIDTH/2,TABLE_WIDTH/2})for(double z:new double[]{-c.tableDepth/2,c.tableDepth/2}){
                    var p=c.project(new Vec(x,0,z));require(c.viewport.contains(p.x(),p.y()),"table corner cropped "+size[0]+"/"+pitch+"/"+fov);
                }
                var near=new Pose(0,.025,2,1,1.4,0,0);var far=new Pose(0,.025,-2,1,1.4,0,0);
                require(c.project(near.vertex(1,.5)).x()-c.project(near.vertex(0,.5)).x()>c.project(far.vertex(1,.5)).x()-c.project(far.vertex(0,.5)).x(),"near card isn't larger");
                require(c.project(near).y()>c.project(far).y(),"player must be below opponent");
                for(double tilt:new double[]{0,8,35})for(double yaw:new double[]{-15,0,20}){
                    var pose=near.angles(tilt,yaw);
                    for(double u:new double[]{.02,.5,.98})for(double v:new double[]{.02,.5,.98}){
                        var screen=c.project(pose.vertex(u,v));double hit=pose.intersect(c.ray(screen.x(),screen.y()));
                        require(Double.isFinite(hit),"projected interior missed by ray");
                    }
                    var outside=c.project(pose.vertex(1.04,.5));require(!Double.isFinite(pose.intersect(c.ray(outside.x(),outside.y()))),"outside card accepted");
                    var center=c.project(pose);var b=c.bounds(pose);require(Math.abs(b.x()+b.width()/2.-center.x())<=.51&&Math.abs(b.y()+b.height()/2.-center.y())<=.51,"FX anchor not true projected center");
                }
                cases++;
            }
        }
        int presets=0;
        require(BattleTablePreset.values().length==3&&BattleTablePreset.D.next()==BattleTablePreset.B,"only B/C/D may cycle");
        for(int[] size:new int[][]{{320,240},{640,360},{960,540},{1280,720},{320,480},{1024,768},{1920,1080}}){
            var l=PtcgLiveTableLayout.forScreen(size[0],size[1]);
            for(var preset:BattleTablePreset.values()){
                var c=new PocketTableCamera(size[0],size[1],preset.viewport(l),preset.settings(),preset.fill);
                for(boolean far:new boolean[]{true,false}){
                    var slots=new java.util.ArrayList<Rect>();
                    for(int i=0;i<5;i++)slots.add(new Rect(l.benchX(i),l.benchY(far),l.benchWidth(),l.benchHeight()));
                    for(int x:new int[]{l.deckX(),l.discardX(),l.lostX(),l.prizeX()})slots.add(new Rect(x,l.benchY(far),l.benchWidth(),l.benchHeight()));
                    slots.add(new Rect(l.activeX(),l.activeY(far),l.activeWidth(),l.activeHeight()));
                    for(var slot:slots){var pose=c.card(slot,l);
                        for(double u:new double[]{0,.5,1})for(double v:new double[]{0,.5,1}){
                            var p=c.project(pose.vertex(u,v));
                            require(p.x()>=0&&p.x()<size[0]&&p.y()>=0&&p.y()<l.handHintY(),"preset clips legal card "+preset+" "+size[0]+"x"+size[1]+" "+p);
                        }
                        var center=c.project(pose);require(Double.isFinite(pose.intersect(c.ray(center.x(),center.y()))),"preset ray misses card");
                        require(!c.bounds(pose).overlaps(l.endTurn()),"HUD covers card "+preset);
                        var bounds=c.bounds(pose);require(Math.abs(bounds.x()+bounds.width()/2.-center.x())<=.51&&Math.abs(bounds.y()+bounds.height()/2.-center.y())<=.51,"preset FX anchor drift");
                    }
                    var active=c.card(new Rect(l.activeX(),l.activeY(far),l.activeWidth(),l.activeHeight()),l);
                    var middleBench=c.card(new Rect(l.benchX(2),l.benchY(far),l.benchWidth(),l.benchHeight()),l);
                    require(Math.abs(active.z()-middleBench.z())>(active.height()+middleBench.height())/2,"active overlaps bench "+preset+" "+size[0]+"x"+size[1]);
                    require(Math.abs(active.z())>active.height()/2,"opposing active cards overlap "+preset+" "+size[0]+"x"+size[1]);
                    if(c.portrait){
                        for(int sideX:new int[]{l.deckX(),l.lostX()}){
                            var pile=c.card(new Rect(sideX,l.benchY(far),l.benchWidth(),l.benchHeight()),l);
                            require(Math.abs(pile.z()-middleBench.z())>(pile.height()+middleBench.height())/2,"portrait pile overlaps bench");
                        }
                        var deck=c.card(new Rect(l.deckX(),l.benchY(far),l.benchWidth(),l.benchHeight()),l);
                        var discard=c.card(new Rect(l.discardX(),l.benchY(far),l.benchWidth(),l.benchHeight()),l);
                        require(Math.abs(deck.z()-discard.z())>(deck.height()+discard.height())/2,"portrait piles overlap");
                    }
                }
                presets++;
            }
        }
        System.out.println("B/C/D: "+presets+" viewport/preset combinations; all legal regions visible, HUD clearance, ray picks and FX anchors passed");
        var s=Settings.defaults().height(20).distance(10).pitch(60);
        require(Math.abs(s.pitch()-Math.toDegrees(Math.atan2(s.height(),s.distance())))<.001,"camera controls lost look-at invariant");
        for(int[] size:new int[][]{{320,240},{640,360},{320,692},{960,640},{1280,720}})for(int count:new int[]{0,1,4,12}){
            var panel=BattleCardPanelLayout.centered(size[0],size[1],count,0);
            require(!panel.card().overlaps(panel.panel()),"centered card overlaps actions");
            for(var r:new Rect[]{panel.card(),panel.panel()})require(r.x()>=0&&r.y()>=0&&r.right()<=size[0]&&r.bottom()<=size[1]&&r.width()>0&&r.height()>0,"inspection leaves window");
            require(Math.abs(panel.card().x()+panel.card().width()/2.-size[0]/2.)<1,"inspection face is not horizontally centered");
        }
        System.out.println("Pocket camera: "+cases+" viewport/settings combinations; perspective size, full table, ray edges/tilt and hit anchors passed");
    }
}
