package dev.hua.ptcglive.client;

public final class CardRevealAnimationTest {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        for(int[] size:new int[][]{{640,360},{320,480},{320,240},{1280,720}}){
            var r=CardRevealAnimation.area(size[0],size[1],1);
            check(r.x()>=0&&r.y()>=0&&r.right()<=size[0]&&r.bottom()<=size[1]&&r.height()>0,"reveal exceeds viewport");
        }
        for(int fps:new int[]{30,60,120})for(int index=0;index<5;index++){
            double last=0;
            for(int f=0;f<=fps;f++){
                double p=CardRevealAnimation.progress(f*1000./fps,index);
                check(Double.isFinite(p)&&p>=last&&p<=1,"invalid entry motion");last=p;
            }
            check(last==1,"card not settled before reading gate ends");
        }
        check(CardRevealAnimation.progress(0,0)==0,"entry starts at wrong pose");
        check(CardRevealAnimation.progress(580,4)==1,"last page card never settles");
        System.out.println("CardRevealAnimationTest passed: bounded layout, staggered entry and 30/60/120 sampling");
    }
}
