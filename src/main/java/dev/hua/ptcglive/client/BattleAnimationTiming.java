package dev.hua.ptcglive.client;

import dev.hua.ptcglive.view.MatchPresentation.Cue;
import dev.hua.ptcglive.view.MatchPresentation.Kind;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;

/** Visual staging only. Server settlement is immediate; local input waits for presentation. */
final class BattleAnimationTiming {
    private BattleAnimationTiming() { }
    static final long COIN_STEP=700_000_000L;
    static final long HIT_STEP=320_000_000L;
    static long coinSequenceDuration(long count) { return CoinAnimation.duration(Math.toIntExact(count)); }
    static long duration(Cue cue) {
        return switch (cue.kind()) {
            case COIN -> CoinAnimation.duration(1);
            case SHUFFLE -> ShuffleAnimation.DURATION_NS;
            case DISCARD -> DiscardAnimation.DURATION_NS;
            case LOST -> LostZoneAnimation.DURATION_NS;
            case DRAW -> 1_200_000_000L;
            case PRIZE -> 1_800_000_000L;
            case PLAY -> trainer(cue)?(long)(TrainerPlayAnimation.timeline(cue.cardId()).durationMs*1e6):1_300_000_000L;
            case STADIUM -> 1_300_000_000L;
            case TOOL -> cue.amount()<0&&cue.text().startsWith("工具弃置")?1_600_000_000L:1_100_000_000L;
            case EVOLVE -> (long)(BattleAnimationEngine.evolution().durationMs*1_000_000);
            case DEVOLVE -> DevolutionAnimation.DURATION_NS;
            case ATTACK -> 1_650_000_000L;
            case ABILITY -> AbilityAnimation.DURATION_NS;
            case RESULT -> 2_400_000_000L;
            case TURN, CHECKUP -> 1_200_000_000L;
            default -> 1_100_000_000L;
        };
    }
    /** A spread impact ends at a repeated target or any intervening public event. */
    static int spreadEnd(List<Cue> cues,int start){
        var first=cues.get(start);
        int end=start+1;
        if(first.kind()!=Kind.DAMAGE||first.pokemon()==null||!first.text().startsWith("招式伤害·")||end==cues.size())return end;
        var targets=new HashSet<UUID>();
        targets.add(first.pokemon());
        while(end<cues.size()){
            var next=cues.get(end);
            if(next.kind()!=Kind.DAMAGE||next.pokemon()==null||!first.text().equals(next.text())
                ||!Objects.equals(first.seat(),next.seat())||!targets.add(next.pokemon()))break;
            end++;
        }
        return end;
    }
    /** Old snapshots carry a single contact time. Distinct targets share it, repeated hits get separate beats. */
    static long[] legacyHitOffsets(List<Cue> batch){
        var offsets=new long[batch.size()];
        var counts=new HashMap<UUID,Integer>();
        long tail=0;
        for(int i=0;i<batch.size();i++){
            var cue=batch.get(i);
            if(cue.kind()!=Kind.DAMAGE||cue.pokemon()==null||!cue.text().startsWith("招式伤害·"))continue;
            int index=counts.merge(cue.pokemon(),1,Integer::sum)-1;
            offsets[i]=index*HIT_STEP;
            tail=Math.max(tail,offsets[i]);
        }
        if(tail>0)for(int i=0;i<batch.size();i++)switch(batch.get(i).kind()){
            case DEVOLVE, LEAVE, KNOCKOUT, PRIZE, RESULT, TURN, CHECKUP -> offsets[i]+=tail;
            default -> {}
        }
        return offsets;
    }
    static long offset(Cue cue, List<Cue> batch) {
        boolean intro = batch.stream().anyMatch(c -> c.kind()==Kind.PLAY || c.kind()==Kind.EVOLVE || c.kind()==Kind.STADIUM);
        boolean devolve = batch.stream().anyMatch(c -> c.kind()==Kind.DEVOLVE);
        boolean ability = batch.stream().anyMatch(c -> c.kind()==Kind.ABILITY);
        boolean attack = batch.stream().anyMatch(c -> c.kind()==Kind.ATTACK);
        boolean coins = batch.stream().anyMatch(c -> c.kind()==Kind.COIN);
        boolean departure = batch.stream().anyMatch(c -> c.kind()==Kind.LEAVE || c.kind()==Kind.KNOCKOUT);
        boolean gate = batch.stream().anyMatch(c -> c.kind()==Kind.COIN
            && (c.text().contains("前判定") || c.text().contains("混乱") || c.text().contains("复制招式")));
        long coinDuration=coinSequenceDuration(batch.stream().filter(c->c.kind()==Kind.COIN).count());
        long introduction = gate && coins ? coinDuration : 0;
        long introDuration=batch.stream().anyMatch(c -> c.kind()==Kind.EVOLVE) ? (long)(BattleAnimationEngine.evolution().durationMs*1_000_000)
            :batch.stream().filter(BattleAnimationTiming::trainer).mapToLong(BattleAnimationTiming::duration).max().orElse(850_000_000L);
        long coin = !gate && intro ? introDuration : 0;
        long action = Math.max(intro ? introduction + introDuration : 0, coins ? coin + coinDuration : 0);
        long impact = action + (attack ? 650_000_000L : ability ? AbilityAnimation.DURATION_NS : 0);
        long devolution = impact + (attack ? 450_000_000L : 0);
        long leaving = devolution + (devolve ? DevolutionAnimation.DURATION_NS : 0);
        return switch(cue.kind()) {
            case PLAY, EVOLVE, STADIUM -> introduction;
            case DEVOLVE -> devolution;
            case COIN -> coin;
            case ATTACK, ABILITY -> action;
            case DAMAGE, HEAL, STATUS, ENERGY, TOOL, DISCARD, LOST, SHUFFLE, MOVE -> impact;
            case LEAVE, KNOCKOUT -> leaving;
            case PRIZE -> departure ? leaving + 800_000_000L : impact;
            case RESULT -> Math.max(Math.max(leaving + (departure ? 850_000_000L : 0), action),
                batch.stream().anyMatch(c->c.kind()==Kind.PRIZE)?(departure?leaving+800_000_000L:impact)+1_800_000_000L:0);
            case TURN, CHECKUP -> attack || departure ? leaving + 900_000_000L : 0;
            case DRAW -> impact + Math.max(batch.stream().anyMatch(c -> c.kind()==Kind.SHUFFLE)?ShuffleAnimation.DURATION_NS:0,
                Math.max(batch.stream().anyMatch(c -> c.kind()==Kind.DISCARD)?DiscardAnimation.DURATION_NS:0,
                    batch.stream().anyMatch(c -> c.kind()==Kind.LOST)?LostZoneAnimation.DURATION_NS:0));
        };
    }
    private static boolean trainer(Cue cue){return cue.kind()==Kind.PLAY&&cue.pokemon()==null&&cue.cardId()!=null;}
}
