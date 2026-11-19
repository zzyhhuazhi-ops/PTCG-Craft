package dev.hua.ptcglive.client;

import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;
import dev.hua.ptcglive.view.MatchPresentation.*;
import java.util.List;

public final class TrainerPlayAnimationTest {
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    public static void main(String[] args){
        var t=BattleAnimationEngine.trainer();var hand=new Rect(320,630,1,1);var center=new Rect(220,180,200,280);
        check(TrainerPlayAnimation.color("sv1_en_185")==0xFF10F8FF,"item style");
        check(TrainerPlayAnimation.color("sv1_en_189")==0xFFFFD132,"supporter style");
        check(TrainerPlayAnimation.color("sv1_en_193")==0xFFD395FF,"tool style");
        check(TrainerPlayAnimation.color("sv1_en_167")==0xFF72F838,"stadium green style");
        check(!TrainerPlayAnimation.calibratedReveal(null),"missing public card remains a safe fallback");
        check(TrainerPlayAnimation.flashTime("sv1_en_167",1100,true)==1100,"stadium must not inherit item advance");
        for(int width:new int[]{640,1280,1920}){
            var layout=PtcgLiveTableLayout.forScreen(width,720);var slot=StadiumAnimation.slot(layout);
            check(slot.x()==layout.stadiumX()&&slot.y()==layout.stadiumY()&&slot.width()==layout.benchWidth()&&slot.height()==layout.benchHeight(),"stadium must land on card bounds, not padded slot");
        }
        check(TrainerPlayAnimation.color("unknown-public-card")==0xFF10F8FF,"unknown card fallback");
        check(!TrainerPlayAnimation.item(null)&&!TrainerPlayAnimation.item("unknown-public-card"),"missing identity must not create an item reveal");
        check(TrainerPlayAnimation.item("sv1_en_185")&&!TrainerPlayAnimation.item("bw4_en_090"),"stadium must not receive item timing or sound");
        var cardA=new dev.hua.ptcglive.rules.CardInstance(new java.util.UUID(0,1),new java.util.UUID(0,3),"tool");
        var cardB=new dev.hua.ptcglive.rules.CardInstance(new java.util.UUID(0,2),new java.util.UUID(0,3),"tool");
        var claimed=new java.util.HashSet<java.util.UUID>();
        check(ToolAttachmentAnimation.slot(List.of(),List.of(cardA,cardB),"tool",true,claimed)==0
            &&ToolAttachmentAnimation.slot(List.of(),List.of(cardA,cardB),"tool",true,claimed)==1,"same-name tools need distinct public slots");
        check(ToolAttachmentAnimation.slot(List.of(cardA,cardB),List.of(cardB),"tool",false,new java.util.HashSet<>())==0,"removal must use the removed instance's old slot");
        double[] quad=new double[8];int[] cursor={0};
        for(boolean remove:new boolean[]{false,true})ToolAttachmentAnimation.mesh((x,y,color)->{
            quad[cursor[0]++]=x;quad[cursor[0]++]=y;
            if(cursor[0]==8){
                double area=0;for(int j=0;j<4;j++){int next=(j+1)%4;area+=quad[j*2]*quad[next*2+1]-quad[next*2]*quad[j*2+1];}
                check(area<=1e-8,"tool burst has back-facing GUI triangles; culling hides the impact rays");cursor[0]=0;
            }
        },ToolAttachmentAnimation.icon(center,0),67,remove,BattleAnimationEngine.Quality.HIGH);
        for(boolean opponent:new boolean[]{false,true})for(int fps:new int[]{30,60,120}){
            var pile=new PocketTableCamera.Pose(1,0,2,1,1.4,0,opponent?180:0);
            var corner=pile.vertex(0,0);
            check(Math.abs(corner.x()-(opponent?1.5:.5))<1e-9 && Math.abs(corner.z()-(opponent?2.7:1.3))<1e-9,"opponent discard must face its owner");
            double previous=0;
            for(int i=0;i<=fps*2;i++){
                double ms=i*1000./fps,p=ToolAttachmentAnimation.discardProgress(ms);
                check(p>=previous&&p<=1,"discard landing reversed or overshot");previous=p;
                if(ms<ToolAttachmentAnimation.discardStart())check(p==0,"discard appeared before source interval");
                if(ms>=ToolAttachmentAnimation.discardEnd())check(ToolAttachmentAnimation.discardPose(pile,ms).equals(pile),"discard did not settle into the exact table pose");
            }
        }
        for(boolean remove:new boolean[]{false,true})for(int fps:new int[]{30,60,120})for(int i=0;i<=fps;i++){
            int[] vertices={0};
            ToolAttachmentAnimation.mesh((x,y,color)->{check(Double.isFinite(x)&&Double.isFinite(y),"nonfinite tool light");vertices[0]++;},
                ToolAttachmentAnimation.icon(center,0),i*1000./fps,remove,BattleAnimationEngine.Quality.HIGH);
            check(vertices[0]<2400,"unbounded tool effect mesh");
            if(i*1000./fps>=900)check(vertices[0]==0,"expired tool light retained vertices");
        }
        for(int fps:new int[]{30,60,120}){
            double prior=0;
            for(int i=0;i<=fps;i++){
                double ms=i*1000./fps,p=ToolAttachmentAnimation.returnProgress(ms);
                check(p>=prior&&p<=1,"return card reverses or overshoots");prior=p;
                if(ms<ToolAttachmentAnimation.returnStart())check(p==0,"returned card revealed before hand entry");
                if(ms>=ToolAttachmentAnimation.returnEnd())check(p==1,"returned card does not settle into confirmed hand");
                int[] vertices={0};
                ToolAttachmentAnimation.returnMesh((x,y,color)->{check(Double.isFinite(x)&&Double.isFinite(y),"invalid return flash");vertices[0]++;},center,ms,BattleAnimationEngine.Quality.HIGH);
                check(vertices[0]<2400,"return flash unbounded");
                if(ms>=383)check(vertices[0]==0,"return flash survives observed end");
            }
        }
        for(String id:List.of("sv1_en_185","sv1_en_189","sv1_en_193"))for(boolean opponent:new boolean[]{false,true})for(int fps:new int[]{30,60,120}){
            t=TrainerPlayAnimation.timeline(id);
            double previous=-1,previousOpening=0;
            for(int i=0;i<=fps*2;i++){
                double ms=i*1000./fps,flash=TrainerPlayAnimation.flashTime(id,ms,opponent);
                check(flash>=previous,"subtype clock went backwards");previous=flash;
                if(ms<=900&&!TrainerPlayAnimation.calibratedReveal(id))check(flash==ms,"flash profile changed the card flight");
                double opening=t.sample(TrainerPlayAnimation.supporter(id)?"core.open.supporter":"core.open",flash,0);
                check(opening>=previousOpening&&opening>=0&&opening<=1,"center opening reverses or escapes bounds");previousOpening=opening;
                if(flash<=(TrainerPlayAnimation.tool(id)?983.333333:1100))check(opening==0,"center opens before card concealment");
                for(String width:List.of("frame.width","echo.width"))check(t.sample(width,flash,0)>0&&Double.isFinite(t.sample(width,flash,0)),"invalid light width");
                if(t.sample("card.visible",flash,0)<=.5&&flash<1120)check(t.sample(TrainerPlayAnimation.item(id)?"flash.item":TrainerPlayAnimation.supporter(id)?"flash.supporter":"flash",flash,0)>.9,"subtype exposes disappearing card");
                if(ms>=t.durationMs)for(String track:List.of("card.visible","flash","frame.alpha","echo.alpha","sparks"))check(t.sample(track,flash,0)==0,"subtype layer survives expiry");
            }
        }
        for(String id:List.of("sv1_en_185","sv1_en_189","sv1_en_193"))for(boolean opponent:new boolean[]{false,true})for(int fps:new int[]{30,60,120})for(int i=0;i<=fps*2;i++){
            t=TrainerPlayAnimation.timeline(id);
            double ms=i*1000./fps;var pose=TrainerPlayAnimation.pose(hand,center,opponent,id,ms);
            check(Double.isFinite(pose.x())&&Double.isFinite(pose.y())&&pose.width()>0&&pose.height()>0,"invalid pose");
            check(pose.x()>=219&&pose.x()<=321&&pose.y()>=(TrainerPlayAnimation.calibratedReveal(id)&&opponent?180:319)&&pose.y()<=631,"card escaped flight bounds");
            if(ms>=430)check(pose.front()&&Math.abs(pose.x()-320)<.001&&Math.abs(pose.y()-320)<.001,"hold pose not centered");
            double settleAngle=TrainerPlayAnimation.calibratedReveal(id)&&opponent?(pose.flip()+1)*180:pose.yaw();
            if(ms>=467&&ms<(TrainerPlayAnimation.tool(id)?534:TrainerPlayAnimation.calibratedReveal(id)?600:700))check(Math.abs(settleAngle)>1,"card stops turning before the observed settling interval");
            if(ms>=900)check(pose.yaw()==0&&pose.tilt()==0&&pose.front()&&Math.abs(Math.sin(pose.flip()*Math.PI))<1e-8,"card keeps moving during the reading hold");
            if(ms>=400){
                double[] bounds={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
                EvolutionCardRenderer.mesh(EvolutionCardRenderer.Projection.trainer(pose,TrainerPlayAnimation.focal(id,center,pose,ms)),(x,y,u,v)->{
                    check(Double.isFinite(x)&&Double.isFinite(y),"non-finite projected card");
                    bounds[0]=Math.min(bounds[0],x);bounds[1]=Math.min(bounds[1],y);bounds[2]=Math.max(bounds[2],x);bounds[3]=Math.max(bounds[3],y);
                });
                check(bounds[2]-bounds[0]>center.width()*.85&&bounds[2]-bounds[0]<center.width()*1.05,"settling card collapses or overscales");
                check(bounds[3]-bounds[1]<center.height()*1.10,"settling projection exceeds its effect coverage");
            }
            if(t.sample("card.visible",ms,0)<=.5&&ms<1120)check(t.sample("flash",ms,0)>.9,"card vanishes before flash covers it");
            if(ms>=t.durationMs)for(String track:List.of("card.visible","flash","frame.alpha","echo.alpha","sparks"))check(t.sample(track,ms,0)==0,"expired layer remains: "+track);
        }
        check(!TrainerPlayAnimation.pose(hand,center,true,"sv1_en_189",100).front(),"opponent card face exposed before flip");
        check(TrainerPlayAnimation.pose(hand,center,false,"sv1_en_185",100).front(),"own public card unexpectedly concealed");
        var early=TrainerPlayAnimation.pose(hand,center,false,"sv1_en_185",0);
        check(early.height()>center.height()*.5&&early.height()<center.height()*.56,"confirmed item reveal must start near half size");
        check(early.y()>=center.y()+center.height()*.5&&early.y()<center.y()+center.height()*.6,"item reveal wrongly repeats the manual hand drag");
        var projected=EvolutionCardRenderer.Projection.trainer(early,center.height()*2.85);
        check(Math.abs(projected.sy())>.1,"trainer yaw suppressed by evolution's two-turn gate");
        check(EvolutionCardRenderer.Projection.of(early).sy()==0,"trainer correction altered accepted evolution projection");
        TrainerPlayAnimation.flash(null,null,"sv1_en_185",900,false,BattleAnimationEngine.Quality.OFF);
        TrainerPlayAnimation.flash(null,null,"sv1_en_185",t.durationMs,true,BattleAnimationEngine.Quality.HIGH);
        check(!TrainerPlayAnimation.pose(hand,center,true,"sv1_en_185",200).front(),"opponent item reveals its front before the narrow sleeve phase");
        check(TrainerPlayAnimation.pose(hand,center,true,"sv1_en_185",233).front(),"opponent item remains concealed after the observed flip");
        for(boolean opponent:new boolean[]{false,true}){
            var sounds=TrainerPlayAnimation.sounds(opponent);
            check(sounds.size()==1&&sounds.getFirst().name().equals("trainer_item"),"item must use one original shimmer, not duplicate the generic play cue");
            check(sounds.getFirst().offsetMs()>=800&&sounds.getFirst().offsetMs()<950,"item shimmer must accompany the flash rather than card entry");
        }
        check(!TrainerPlayAnimation.pose(hand,center,true,"sv1_en_189",200).front()&&TrainerPlayAnimation.pose(hand,center,true,"sv1_en_189",250).front(),"supporter public face appears outside the observed turn interval");
        check(TrainerPlayAnimation.pose(hand,center,false,"sv1_en_189",0).height()>center.height()*.5,"supporter repeated the manual hand drag instead of confirmed reveal");
        var play=new Cue(Kind.PLAY,null,null,0,"公开使用训练家","sv1_en_185");var draw=new Cue(Kind.DRAW,null,null,1,"抽牌",null);
        check(BattleAnimationTiming.offset(draw,List.of(play,draw))>=BattleAnimationTiming.duration(play),"trainer resolution overlaps its reveal");
        System.out.println("Trainer: 3 public styles, opponent flip, 30/60/120 Hz, flash cover, expiry and resolution ordering passed");
    }
}
