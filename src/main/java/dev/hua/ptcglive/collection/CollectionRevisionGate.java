package dev.hua.ptcglive.collection;
/** Single-use revision claim, separate from ownership. Stale/replayed requests never mutate cards. */
public final class CollectionRevisionGate {
    private int revision;
    public int revision(){return revision;}
    public boolean claim(int expected){if(expected!=revision)return false;revision++;return true;}
}
