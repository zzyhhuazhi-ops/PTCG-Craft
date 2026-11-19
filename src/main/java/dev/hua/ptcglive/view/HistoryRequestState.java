package dev.hua.ptcglive.view;

/** Tick-based bounded retry, independent of rendering and network implementation. */
public final class HistoryRequestState {
    private int ticks;
    private boolean received,unsupported;
    public boolean tick(){if(received||unsupported||timedOut())return false;ticks++;return ticks==20||ticks==60;}
    public boolean timedOut(){return !received&&(unsupported||ticks>=120);}
    public boolean received(){return received;}
    public void receive(){received=true;}
    public void unsupported(){unsupported=true;}
    public void retry(){ticks=0;received=false;unsupported=false;}
}
