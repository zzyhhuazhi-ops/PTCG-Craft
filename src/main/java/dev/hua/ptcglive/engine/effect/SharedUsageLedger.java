package dev.hua.ptcglive.engine.effect;

import java.util.*;

/** Match-owned, server-only usage. No reset on switching, evolution, leave-play or reconnect. */
public final class SharedUsageLedger {
    private record Key(UUID player,String group) { }
    private record Used(SharedUsageLimit.Period period,long turn) { }
    private final Map<Key,Used> used=new HashMap<>();
    public synchronized boolean exhausted(UUID player,SharedUsageLimit limit,long turn) {
        Objects.requireNonNull(player);if(turn<0)throw new IllegalArgumentException("回合序号不能为负数");
        if(limit==null)return false;
        var previous=used.get(new Key(player,limit.group()));if(previous==null)return false;
        if(previous.period()!=limit.period())throw new IllegalArgumentException("同组共享资格周期不能在对局中变更");
        if(turn<previous.turn())throw new IllegalArgumentException("共享资格不能回退至旧回合");
        return limit.period()==SharedUsageLimit.Period.GAME || previous.turn()==turn;
    }
    public synchronized void consume(UUID player,SharedUsageLimit limit,long turn) {
        if(exhausted(player,limit,turn))throw new IllegalStateException(limit.exhaustedZh());
        if(limit!=null)used.put(new Key(player,limit.group()),new Used(limit.period(),turn));
    }
    public boolean markerUsed(UUID player,String group,long turn) {
        if(!Set.of(SharedUsageLimit.GX,SharedUsageLimit.VSTAR).contains(group))throw new IllegalArgumentException("未知公开力量标记");
        return exhausted(player,new SharedUsageLimit(group,SharedUsageLimit.Period.GAME),turn);
    }
}
