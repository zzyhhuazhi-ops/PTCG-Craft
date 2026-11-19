package dev.hua.ptcglive.client;

import java.util.*;
import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;

public final class EnergyDiscardAnimationTest {
    public static void main(String[] args){
        UUID a=new UUID(0,1),b=new UUID(0,2),c=new UUID(0,3),d=new UUID(0,4);
        var claimed=new HashSet<UUID>();var before=List.of(a,b,c,d);var cards=List.of("psychic","fire","psychic","psychic");
        var first=EnergyDiscardAnimation.removed(before,cards,List.of(a,b),"psychic",claimed);
        var second=EnergyDiscardAnimation.removed(before,cards,List.of(a,b),"psychic",claimed);
        if(first.index()!=2||second.index()!=3||first.sourceCount()!=4)throw new AssertionError("wrong old slots");
        if(EnergyDiscardAnimation.removed(before,cards,List.of(a,b),"psychic",claimed)!=null)throw new AssertionError("duplicate discard");
        if(EnergyDiscardAnimation.removed(before,cards,List.of(a,b),"fire",claimed)!=null)throw new AssertionError("retained energy discarded visually");
        for(var q:BattleAnimationEngine.Quality.values())for(int fps:new int[]{30,60,120})for(int i=-1;i<=fps;i++){
            double ms=i*1000./fps;int[] vertices={0};
            EnergyDiscardAnimation.mesh((x,y,color)->{
                if(!Double.isFinite(x)||!Double.isFinite(y)||x<40||x>160||y<40||y>160)throw new AssertionError("nonlocal geometry");
                vertices[0]++;
            },new Rect(95,95,10,10),ms,1234,q);
            if((ms<0||ms>=750||q==BattleAnimationEngine.Quality.OFF)&&vertices[0]!=0)throw new AssertionError("disabled or expired discard draws");
            if(vertices[0]>15000)throw new AssertionError("unbounded discard mesh");
        }
        System.out.println("EnergyDiscardAnimationTest passed: distinct removed instances, retained energy, 30/60/120Hz, local bounded geometry, OFF and cleanup");
    }
}
