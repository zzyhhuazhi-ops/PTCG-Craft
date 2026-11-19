package dev.hua.ptcglive.rental;

/** Pure, bounded production calculation. Time is the persistent server-active clock, in milliseconds. */
public final class RentalProduction {
    public static final long MINUTE = 60_000;
    public record Result(long total, long since) { }
    public record Boosted(long total,long since,int half,int stock,int cycleMultiplier) { }
    public record Conditioned(long total,long since,int eighth,int stock,int cycleMultiplier) { }
    /** Exact per-minute eighth-coins, shared by production and collection previews. */
    public static int incomeEighthsPerMinute(int rate,int percent,int multiplier){return rate*multiplier*percent/25;}
    /** Exact eighth-coins cover 25% condition steps combined with 1.5x fuel. */
    public static Conditioned settleConditioned(long total,long paid,long since,long now,int rate,int percent,int eighth,
                                                int stock,int fuelMultiplier,int cycleMultiplier){
        return settleConditioned(total,paid,since,now,rate,percent,eighth,stock,fuelMultiplier,cycleMultiplier,1);
    }
    public static Conditioned settleConditioned(long total,long paid,long since,long now,int rate,int percent,int eighth,
                                                int stock,int fuelMultiplier,int cycleMultiplier,int capacityMultiplier){
        if(capacityMultiplier!=1&&capacityMultiplier!=3&&capacityMultiplier!=9)throw new IllegalArgumentException("租赁机等级无效");
        if(total<0||paid<0||since<0||now<0||rate<1||rate>20||eighth<0||eighth>7||stock<0||stock>64
            ||fuelMultiplier<2||fuelMultiplier>4||cycleMultiplier<2||cycleMultiplier>4
            ||(stock>0&&fuelMultiplier==2)||!java.util.Set.of(50,75,100,125,150,200).contains(percent))throw new IllegalArgumentException("租赁品质数据无效");
        if(total<paid)return new Conditioned(paid,now,eighth,stock,cycleMultiplier);
        long cap=rate*60L*percent*capacityMultiplier/100;
        if(total-paid>=cap)return new Conditioned(total,now,0,stock,cycleMultiplier);
        if(now<since)return new Conditioned(total,now,eighth,stock,cycleMultiplier);
        while(now-since>=MINUTE&&total-paid<cap){
            int units=incomeEighthsPerMinute(rate,percent,cycleMultiplier)+eighth;
            long add=Math.min(cap-(total-paid),units/8);add=Math.min(add,Long.MAX_VALUE-total);
            total+=add;eighth=units%8;since+=MINUTE;cycleMultiplier=2;
            if(total-paid>=cap||total==Long.MAX_VALUE){since=now;eighth=0;break;}
            if(stock>0){stock--;cycleMultiplier=fuelMultiplier;}
        }
        return new Conditioned(total,since,eighth,stock,cycleMultiplier);
    }
    private RentalProduction() { }
    public static int rate(int rank, int levels) {
        if (levels < 2 || rank < 0 || rank >= levels) throw new IllegalArgumentException("品质档位无效");
        return 1 + (int)Math.round(19.0 * rank / (levels - 1));
    }
    public static Result settle(long total, long paid, long since, long now, int rate) {
        if (total < 0 || paid < 0 || since < 0 || now < 0 || rate < 1 || rate > 20)
            throw new IllegalArgumentException("租赁数据无效");
        // A stale chunk must not also replay the TIME that produced already credited coins.
        // Conservatively restart its clock; the wallet's permanent receipt is authoritative.
        if(total<paid)return new Result(paid,now);
        long cap = rate * 60L, held = total - paid;
        if (held >= cap) return new Result(total, now);
        if (now < since) return new Result(total, now);
        long minutes = (now - since) / MINUTE;
        long add = Math.min(cap - held, Math.min(minutes, 60) * rate);
        add = Math.min(add, Long.MAX_VALUE - total);
        long next = total + add;
        return new Result(next, next - paid >= cap ? now : since + minutes * MINUTE);
    }

    /** Multipliers use half units: 2=normal, 3=redstone, 4=lapis. The active cycle has one reserved block. */
    public static Boosted settleBoosted(long total,long paid,long since,long now,int rate,int half,
                                       int stock,int fuelMultiplier,int cycleMultiplier) {
        if(total<0||paid<0||since<0||now<0||rate<1||rate>20||half<0||half>1||stock<0||stock>64
            ||fuelMultiplier<2||fuelMultiplier>4||cycleMultiplier<2||cycleMultiplier>4
            ||(stock>0&&fuelMultiplier==2))throw new IllegalArgumentException("租赁加速数据无效");
        if(total<paid)return new Boosted(paid,now,half,stock,cycleMultiplier);
        long cap=rate*60L;
        if(total-paid>=cap)return new Boosted(total,now,0,stock,cycleMultiplier);
        if(now<since)return new Boosted(total,now,half,stock,cycleMultiplier);
        // At least one whole coin per completed minute: the 60-minute capacity bounds this loop.
        while(now-since>=MINUTE&&total-paid<cap){
            int units=rate*cycleMultiplier+half;
            long add=Math.min(cap-(total-paid),units/2);
            add=Math.min(add,Long.MAX_VALUE-total);total+=add;half=units%2;since+=MINUTE;
            cycleMultiplier=2; // The completed cycle consumes its reserved block exactly once.
            if(total-paid>=cap||total==Long.MAX_VALUE){since=now;half=0;break;}
            if(stock>0){stock--;cycleMultiplier=fuelMultiplier;}
        }
        return new Boosted(total,since,half,stock,cycleMultiplier);
    }
}
