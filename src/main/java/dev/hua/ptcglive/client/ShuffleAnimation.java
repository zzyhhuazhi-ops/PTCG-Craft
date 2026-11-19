package dev.hua.ptcglive.client;

/** Four short packet lifts, then a flush deck. No hidden card identities are required. */
final class ShuffleAnimation {
    static final long DURATION_NS=650_000_000L;
    static final int LAYERS=4;
    record Sample(double lift,double edge,double turn){}
    static Sample sample(double ms){
        if(ms<0||ms>=650)return new Sample(0,0,0);
        double lift=ms<40?0:ms<480?Math.pow(Math.sin(Math.PI*((ms-40)%110)/110),.7):0;
        double edge=Math.min(1,ms/65)*Math.min(1,(650-ms)/170);
        return new Sample(lift,edge,Math.sin(ms*Math.PI/110)*3*edge);
    }
    static PocketTableCamera.Pose pose(PocketTableCamera.Pose deck,double ms,int layer){
        var s=sample(ms);double moving=layer==LAYERS-1?s.lift():0;
        return deck.move(deck.width()*(moving*.12+layer*.008*s.edge()),
            deck.height()*(moving*.72+layer*.012*s.edge()),
            -deck.height()*(moving*.22+layer*.038*s.edge()))
            .angles(moving*8,layer==LAYERS-1?s.turn():0);
    }
    static double flatLift(double ms,int layer){var s=sample(ms);return (layer==LAYERS-1?s.lift()*.9:0)+layer*.035*s.edge();}
}
