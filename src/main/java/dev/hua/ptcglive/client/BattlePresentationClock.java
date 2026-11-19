package dev.hua.ptcglive.client;

/** Local animation time only. Server deadlines and intent timestamps never use this clock. */
final class BattlePresentationClock {
    private final java.util.function.LongSupplier source;
    private long real,shown;
    private int speed=1;
    BattlePresentationClock(){this(System::nanoTime);}
    BattlePresentationClock(java.util.function.LongSupplier source){this.source=source;real=source.getAsLong();shown=real;}
    long now(){long next=source.getAsLong();shown+=Math.max(0,next-real)*speed;real=next;return shown;}
    boolean fast(){return speed>1;}
    int speed(){return speed;}
    void toggle(){now();speed=speed==1?2:speed==2?4:1;}
}
