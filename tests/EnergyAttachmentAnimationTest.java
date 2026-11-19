package dev.hua.ptcglive.client;

import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;

public final class EnergyAttachmentAnimationTest {
    public static void main(String[] args){
        var target=new Rect(210,100,60,84);var source=new Rect(20,30,1,1);
        for(var quality:BattleAnimationEngine.Quality.values())for(int fps:new int[]{30,60,120})for(boolean known:new boolean[]{false,true})for(int i=-1;i<=fps*2;i++){
            double ms=i*1000./fps;int[] vertices={0};
            EnergyAttachmentAnimation.mesh((x,y,c)->{
                if(!Double.isFinite(x)||!Double.isFinite(y)||x<0||x>400||y<0||y>250)throw new AssertionError("invalid energy geometry");vertices[0]++;
            },known?source:null,target,1,0,ms,0x9e9e9e,quality);
            if((ms<0||ms>=850||quality==BattleAnimationEngine.Quality.OFF)&&vertices[0]!=0)throw new AssertionError("expired or disabled energy remains");
            if(vertices[0]>12000)throw new AssertionError("unbounded energy geometry");
        }
        if(EnergyAttachmentAnimation.contactMs(false)!=0||EnergyAttachmentAnimation.contactMs(true)<=0)throw new AssertionError("hidden source invented a flight");
        for(int ui:new int[]{1,2,3})for(int width:new int[]{30,60,120}){
            var rect=new Rect(200,100,width,width*7/5);
            for(int slot:new int[]{-1,0,1,100}){
                var icon=EnergyAttachmentAnimation.icon(rect,ui,slot);
                if(icon.x()<rect.x()||icon.y()<rect.y()||icon.x()+icon.width()>rect.x()+rect.width()||icon.y()+icon.height()>rect.y()+rect.height())throw new AssertionError("energy icon outside its card");
            }
        }
        var icon=EnergyAttachmentAnimation.icon(target,1,0);
        double[] extent={Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY};
        EnergyAttachmentAnimation.mesh((x,y,c)->{extent[0]=Math.min(extent[0],x);extent[1]=Math.max(extent[1],x);extent[2]=Math.min(extent[2],y);extent[3]=Math.max(extent[3],y);},source,target,1,0,EnergyAttachmentAnimation.contactMs(true),0x9e9e9e,BattleAnimationEngine.Quality.HIGH);
        if(Math.abs((extent[0]+extent[1])/2-icon.x()-icon.width()/2.)>.001||Math.abs((extent[2]+extent[3])/2-icon.y()-icon.height()/2.)>.001)throw new AssertionError("attachment landed away from its persistent icon");
        if(extent[1]-extent[0]>icon.width()*2)throw new AssertionError("attachment became a central explosion");
        System.out.println("EnergyAttachmentAnimationTest passed: finite bounded geometry, correct icon contact, scaled slot bounds, unknown origin, quality OFF, 30/60/120Hz and expiry");
    }
}
