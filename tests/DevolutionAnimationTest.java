package dev.hua.ptcglive.client;

import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;

public final class DevolutionAnimationTest {
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        var card=new Rect(100,200,90,125);
        check(DevolutionAnimation.previous(639)&&!DevolutionAnimation.previous(640),"single covered replacement");
        check(DevolutionAnimation.veil(640)>.99,"replacement hidden by blue cover");
        check(DevolutionAnimation.lifted(card,0).equals(card)&&DevolutionAnimation.lifted(card,1400).equals(card),"exact original placement at endpoints");
        for(boolean low:new boolean[]{false,true})for(int fps:new int[]{30,60,120})for(int i=0;i<=fps*3;i++){
            double ms=i*1000./fps;int[] count={0};
            DevolutionAnimation.draw((x,y,c)->{
                check(Double.isFinite(x)&&Double.isFinite(y),"finite geometry");
                check(x>0&&x<350&&y>0&&y<500,"local effect bounds");count[0]++;
            },card,ms,low);
            check(count[0]<=2000,"bounded mesh work");
            if(ms>=DevolutionAnimation.LAND_MS)check(count[0]==0,"no residual effect after landing");
            check(DevolutionAnimation.lift(ms)>=0&&DevolutionAnimation.lift(ms)<=1,"bounded real-time pose");
        }
        check(DevolutionAnimation.DURATION_MS>DevolutionAnimation.LAND_MS,"settle/HP hold remains locked");
        System.out.println("DevolutionAnimationTest passed: covered identity change, stable endpoints, bounded mesh, 30/60/120 sampling, no residual effect");
    }
}
