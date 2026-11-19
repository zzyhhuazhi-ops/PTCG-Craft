package dev.hua.ptcglive.client;

import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;

public final class PrizeCardAnimationTest {
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args){
        for(int[] size:new int[][]{{320,180},{640,360},{1280,720},{854,480},{3440,1440}}){
            for(int i=0;i<6;i++){
                var r=PrizeCardAnimation.choice(size[0],size[1],i);
                check(r.x()>=0&&r.y()>=0&&r.right()<=size[0]&&r.bottom()<size[1]-20,"choice outside viewport");
                for(int j=0;j<i;j++){
                    var s=PrizeCardAnimation.choice(size[0],size[1],j);
                    check(r.right()<=s.x()||s.right()<=r.x()||r.bottom()<=s.y()||s.bottom()<=r.y(),"overlapping choices");
                }
                var center=PrizeCardAnimation.reveal(size[0],size[1]);var hand=new Rect(size[0]/2,size[1]-40,24,34);
                for(int fps:new int[]{30,60,120})for(int f=0;f<=fps*2;f++){
                    double ms=f*1000./fps;var p=PrizeCardAnimation.pose(r,center,hand,ms);
                    check(Double.isFinite(p.x())&&Double.isFinite(p.y())&&p.width()>0&&p.height()>0,"invalid pose");
                    if(ms<200)check(!p.front(),"face visible before confirmed reveal phase");
                    if(ms>=100&&ms<200)check(p.drawnWidth()<=Math.max(1,p.width()*.03),"card must pause edge-on during the purple preflash");
                    if(ms>=PrizeCardAnimation.END_MS)check(Math.abs(p.x()-(hand.x()+hand.width()/2.))<.001&&PrizeCardAnimation.dim(ms)==0,"failed to settle into hand");
                }
            }
        }
        for(var quality:BattleAnimationEngine.Quality.values())for(double ms:new double[]{-1,0,100,200,500,700,1100}){
            int[] count={0};double[] xs=new double[4],ys=new double[4];
            PrizeCardAnimation.mesh((x,y,c)->{
                check(Double.isFinite(x)&&Double.isFinite(y),"invalid sparkle");int i=count[0]++%4;xs[i]=x;ys[i]=y;
                if(i==3){double area=0;for(int j=0;j<4;j++)area+=xs[j]*ys[(j+1)%4]-ys[j]*xs[(j+1)%4];check(area<=.00001,"GUI culling hides a backwards-facing light polygon");}
            },new Rect(200,100,120,168),ms,quality);
            check(count[0]<60000,"unbounded geometry");
            if(quality==BattleAnimationEngine.Quality.OFF||ms<0||ms>=PrizeCardAnimation.HOLD_MS)check(count[0]==0,"disabled/expired light remains");
        }
        System.out.println("PrizeCardAnimationTest passed: six distinct slots, viewport bounds, flip privacy, 30/60/120Hz and expiry");
    }
}
