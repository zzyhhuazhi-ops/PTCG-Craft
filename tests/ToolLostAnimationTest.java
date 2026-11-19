package dev.hua.ptcglive.client;

import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;

public final class ToolLostAnimationTest {
    public static void main(String[] args){
        var source=new Rect(500,300,90,126);var destination=new Rect(30,600,65,91);
        for(int fps:new int[]{30,60,120})for(var quality:BattleAnimationEngine.Quality.values()){
            for(double ms=-20;ms<=1200;ms+=1000.0/fps){
                int[] vertices={0};
                ToolLostAnimation.mesh((x,y,color)->{
                    if(!Double.isFinite(x)||!Double.isFinite(y)||Math.abs(x)>2000||Math.abs(y)>2000)throw new AssertionError("invalid portal geometry");
                    vertices[0]++;
                },destination,ms,quality);
                if(vertices[0]>14000)throw new AssertionError("unbounded portal mesh");
                if((ms<ToolLostAnimation.START_MS||ms>=ToolLostAnimation.END_MS||quality==BattleAnimationEngine.Quality.OFF)&&vertices[0]!=0)
                    throw new AssertionError("expired or disabled portal emitted vertices");
                var pose=ToolLostAnimation.pose(source,destination,ms);
                if(!Double.isFinite(pose.x())||!Double.isFinite(pose.y())||pose.width()<=0||pose.height()<=0)throw new AssertionError("invalid flight");
            }
        }
        var end=ToolLostAnimation.pose(source,destination,ToolLostAnimation.LAND_MS);
        if(Math.abs(end.x()-(destination.x()+destination.width()*.5))>.001||Math.abs(end.y()-(destination.y()+destination.height()*.5))>.001)
            throw new AssertionError("flight misses pile at landing");
        var first=ToolLostAnimation.pose(source,destination,500);
        ToolLostAnimation.pose(source,destination,1000);
        if(!first.equals(ToolLostAnimation.pose(source,destination,500)))throw new AssertionError("seeking changes flight");
        System.out.println("ToolLostAnimationTest passed: 30/60/120 FPS sampling, finite bounded mesh, OFF/expiry, exact landing, deterministic seeking");
    }
}
