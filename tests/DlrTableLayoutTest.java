package dev.hua.ptcglive.client;
public final class DlrTableLayoutTest {
    public static void main(String[] args){
        for(int[] size:new int[][]{{320,240},{320,480},{640,360},{1280,720}}){
            var l=PtcgLiveTableLayout.forScreen(size[0],size[1]);
            if(!StadiumAnimation.partSlot(l,1,0).equals(StadiumAnimation.slot(l)))throw new AssertionError("single Stadium layout changed");
            var a=StadiumAnimation.partSlot(l,2,0);var b=StadiumAnimation.partSlot(l,2,1);
            if(a.right()>b.x()||a.x()<0||b.right()>size[0]||a.y()<0||a.bottom()>size[1])throw new AssertionError("overlapping/outside Stadium halves");
            if(b.right()>l.activeX())throw new AssertionError("Stadium half overlaps Active Pokemon");
        }
        System.out.println("DLR table layout passed: two distinct card rectangles, no Active overlap, unchanged single Stadium");
    }
}
