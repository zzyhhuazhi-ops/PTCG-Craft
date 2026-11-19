package dev.hua.ptcglive.client;

import dev.hua.ptcglive.engine.field.EnergyType;

public final class BattleTableEnvironmentTest {
    static void check(boolean v,String s){if(!v)throw new AssertionError(s);}
    public static void main(String[] args){
        for(int fps:new int[]{30,60,120})for(var type:EnergyType.values()){
            var state=new BattleTableEnvironment();long start=2_000_000_000L;
            state.update(type,EnergyType.WATER,start);
            for(int i=0;i<=fps;i++){
                state.update(type,EnergyType.WATER,start+(long)(i*1e9/fps));float sum=0;
                for(float w:state.own.weights){check(w>=0&&w<=1,"invalid opacity");sum+=w;}
                check(Math.abs(sum-1)<1e-5,"transition loses/duplicates energy");
            }
            check(state.own.weights[type.ordinal()]==1,"wrong final theme");
            check(state.opponent.weights[EnergyType.WATER.ordinal()]==1,"opponent theme overwritten by own type");
        }
        var state=new BattleTableEnvironment();long now=3_000_000_000L;
        state.update(EnergyType.FIRE,EnergyType.GRASS,now);state.update(EnergyType.FIRE,EnergyType.GRASS,now+300_000_000L);
        var before=state.own.weights.clone();state.update(EnergyType.WATER,EnergyType.GRASS,now+300_000_000L);
        for(int i=0;i<before.length;i++)check(before[i]==state.own.weights[i],"rapid switch causes visible jump");
        state.update(null,null,now+1_000_000_000L);state.update(null,null,now+2_000_000_000L);
        check(state.own.weights[EnergyType.COLORLESS.ordinal()]==1,"empty active slot must return to neutral");
        System.out.println("Environment: all 11 public types, 30/60/120 Hz, independent sides, interruption continuity and empty-active fallback passed");
    }
}
