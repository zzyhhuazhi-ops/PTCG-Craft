package dev.hua.ptcglive.engine;

import java.security.SecureRandom;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.ArrayList;

/** All chance comes from the server. Tests inject a seeded instance; production uses SecureRandom. */
public final class ServerRandom {
    private final Random random;
    private final List<RandomAuditEvent> audit = new ArrayList<>();
    private long sequence;
    public ServerRandom() { this(new SecureRandom()); }
    public ServerRandom(long seed) { this(new Random(seed)); }
    private ServerRandom(Random random) { this.random = Objects.requireNonNull(random); }
    public synchronized int nextInt(String operation, int bound) {
        if (bound <= 0) throw new IllegalArgumentException("随机范围必须为正数");
        int result = random.nextInt(bound); audit.add(new RandomAuditEvent(++sequence, operation, bound, result)); return result;
    }
    public synchronized List<RandomAuditEvent> transcript() { return List.copyOf(audit); }
    /** Consume the ordinary coin draw, but record the result required by a public rules effect. */
    public synchronized boolean coinHeads(String operation,boolean forceTails) {
        return coinHeads(operation,forceTails,null);
    }
    /** A selected side still consumes one draw; an all-tails rule has precedence. */
    public synchronized boolean coinHeads(String operation,boolean forceTails,Boolean selectedHeads) {
        int raw=random.nextInt(2),result=forceTails?1:selectedHeads==null?raw:selectedHeads?0:1;
        audit.add(new RandomAuditEvent(++sequence,operation,2,result));return result==0;
    }
}
