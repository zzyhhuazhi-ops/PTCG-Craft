package dev.hua.ptcglive.client;

import java.util.*;
import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;

public final class EnergyTransferAnimationTest {
    public static void main(String[] args){
        UUID source=new UUID(0,1),a=new UUID(0,2),b=new UUID(0,3),c=new UUID(0,4);
        var claimed=new HashSet<UUID>();var before=List.of(a,b,c);var cards=List.of("water","fire","water");
        var first=EnergyTransferAnimation.link(source,before,cards,List.of(b),List.of(c,a),"water",claimed,null);
        var second=EnergyTransferAnimation.link(source,before,cards,List.of(b),List.of(c,a),"water",claimed,null);
        if(first.sourceIndex()!=0||first.targetIndex()!=1||second.sourceIndex()!=2||second.targetIndex()!=0)throw new AssertionError("transfer ignored instance order");
        if(EnergyTransferAnimation.link(source,before,cards,List.of(b),List.of(c,a),"water",claimed,null)!=null)throw new AssertionError("same energy animated twice");
        if(EnergyTransferAnimation.link(source,before,cards,List.of(a,b,c),List.of(a),"water",new HashSet<>(),null)!=null)throw new AssertionError("unmoved source invented a transfer");
        var start=new Rect(170,120,10,10);var end=new Rect(210,265,10,10);
        for(var q:BattleAnimationEngine.Quality.values())for(int fps:new int[]{30,60,120})for(int i=-1;i<=fps;i++){
            double ms=i*1000./fps;int[] vertices={0};
            EnergyTransferAnimation.mesh((x,y,color)->{
                if(!Double.isFinite(x)||!Double.isFinite(y)||x<100||x>300||y<50||y>350)throw new AssertionError("transfer geometry escaped local bounds");
                vertices[0]++;
            },start,end,70,ms,q);
            if((ms<0||ms>=750||q==BattleAnimationEngine.Quality.OFF)&&vertices[0]!=0)throw new AssertionError("expired/disabled transfer still draws");
            if(vertices[0]>12000)throw new AssertionError("unbounded transfer geometry");
        }
        if(Math.abs(EnergyTransferAnimation.contactMs()-366.666667)>.01)throw new AssertionError("contact timing lost reference alignment");
        System.out.println("EnergyTransferAnimationTest passed: unique public instances, reordered destination slots, no invented transfer, bounded geometry, 30/60/120Hz, OFF and expiry");
    }
}
