package dev.hua.ptcglive.client;

import dev.hua.ptcglive.view.MatchPresentation.Cue;
import dev.hua.ptcglive.view.MatchPresentation.Kind;
import java.util.*;

public final class BattleAnimationTimingTest {
    static Cue cue(Kind k) { return new Cue(k,null,null,1,"",null); }
    static void require(boolean value,String why) { if(!value)throw new AssertionError(why); }
    public static void main(String[] args) {
        var coins=cue(Kind.COIN);var attack=cue(Kind.ATTACK);var damage=cue(Kind.DAMAGE);
        var leave=cue(Kind.LEAVE);var prize=cue(Kind.PRIZE);var result=cue(Kind.RESULT);
        var first=new Cue(Kind.DAMAGE,null,UUID.randomUUID(),30,"招式伤害·DRAGON",null);
        var bench=new Cue(Kind.DAMAGE,null,UUID.randomUUID(),10,"招式伤害·DRAGON",null);
        var orderedHits=List.of(first,bench,first,leave,bench,coins,first);
        require(BattleAnimationTiming.spreadEnd(orderedHits,0)==2,"spread must stop before the same target is hit again");
        require(BattleAnimationTiming.spreadEnd(orderedHits,2)==3,"KO must separate confirmed impacts");
        require(BattleAnimationTiming.spreadEnd(orderedHits,4)==5,"coin must keep its position between hits");
        var counter=new Cue(Kind.DAMAGE,null,UUID.randomUUID(),20,"中毒伤害",null);
        require(BattleAnimationTiming.spreadEnd(List.of(first,counter),0)==1,"checkup damage must not join the attack");
        var spread=BattleAnimationTiming.legacyHitOffsets(List.of(attack,first,bench,first,leave,prize,result));
        require(spread[1]==spread[2]&&spread[3]>spread[1],"independent targets can share a beat, repeated hits cannot");
        require(spread[4]==spread[3]&&spread[5]==spread[3]&&spread[6]==spread[3],"settlement follows the last repeated hit");
        var batch=List.of(coins,attack,damage,leave,prize,result);
        require(BattleAnimationTiming.offset(attack,batch)>=BattleAnimationTiming.duration(coins),"coin result must settle before attack");
        var repeated=List.of(coins,coins,coins,attack,damage);
        require(BattleAnimationTiming.offset(attack,repeated)>=BattleAnimationTiming.coinSequenceDuration(3),"all sequential coins must settle before attack");
        require(BattleAnimationTiming.offset(damage,batch)>BattleAnimationTiming.offset(attack,batch),"impact follows attack launch");
        require(BattleAnimationTiming.offset(leave,batch)>BattleAnimationTiming.offset(damage,batch),"KO follows impact");
        require(BattleAnimationTiming.offset(prize,batch)>BattleAnimationTiming.offset(leave,batch),"prize follows KO");
        require(BattleAnimationTiming.duration(prize)>=1_650_000_000L,"six prize flights must finish");
        require(BattleAnimationTiming.offset(result,batch)>=BattleAnimationTiming.offset(prize,batch)+BattleAnimationTiming.duration(prize),"last prize must enter hand before victory overlay");
        require(BattleAnimationTiming.offset(result,List.of(prize,result))>=BattleAnimationTiming.duration(prize),"standalone last prize must settle before victory overlay");
        var gate=new Cue(Kind.COIN,null,null,0,"硬币·训练家使用前判定：正面",null);
        var play=cue(Kind.PLAY);
        require(BattleAnimationTiming.offset(play,List.of(gate,play))>=BattleAnimationTiming.duration(gate),"attempt gate before trainer reveal");
        var evolve=cue(Kind.EVOLVE);
        var devolve=cue(Kind.DEVOLVE);var devolutionBatch=List.of(attack,damage,devolve,leave,prize);
        require(BattleAnimationTiming.offset(devolve,devolutionBatch)>BattleAnimationTiming.offset(damage,devolutionBatch),"devolution follows attack impact");
        require(BattleAnimationTiming.offset(leave,devolutionBatch)>=BattleAnimationTiming.offset(devolve,devolutionBatch)+BattleAnimationTiming.duration(devolve),"devolution settles before departure");
        require(BattleAnimationTiming.offset(devolve,List.of(devolve))==0,"standalone devolution starts immediately");
        var toolPlay=new Cue(Kind.PLAY,null,null,0,"公开使用训练家","sv1_en_193");
        var toolAttach=new Cue(Kind.TOOL,null,null,1,"工具附着","sv1_en_193");
        var toolRemove=new Cue(Kind.TOOL,null,null,-1,"工具弃置","sv1_en_193");
        require(BattleAnimationTiming.offset(toolAttach,List.of(toolPlay,toolAttach))==BattleAnimationTiming.duration(toolPlay),"legacy tool attachment must follow its own reveal timeline");
        require(BattleAnimationTiming.duration(toolRemove)>ToolAttachmentAnimation.discardEnd()*1e6,"discard landing cut short");
        require(BattleAnimationTiming.offset(coins,List.of(evolve,coins))>=1_600_000_000L,"evolution settles before effect coin");
        var all=Arrays.stream(Kind.values()).map(BattleAnimationTimingTest::cue).toList();
        for(var c:all) {
            long offset=BattleAnimationTiming.offset(c,all),duration=BattleAnimationTiming.duration(c);
            require(offset>=0&&duration>0&&offset+duration<15_000_000_000L,"bounded sequence: "+c.kind());
            require(BattleAnimationTiming.offset(c,List.of(c))>=0,"standalone cue: "+c.kind());
        }
        for(var c:all) {
            long millis=dev.hua.ptcglive.view.AiPresentationTiming.millis(c);
            require(millis>=700&&millis<=1300,"AI cue outside requested range: "+c.kind());
            require(dev.hua.ptcglive.view.AiPresentationTiming.ticks(List.of(c))*50==millis,"server/client cadence differs");
        }
        long total=batch.stream().mapToLong(dev.hua.ptcglive.view.AiPresentationTiming::millis).sum();
        require(dev.hua.ptcglive.view.AiPresentationTiming.ticks(batch)*50==total,"AI steps overlap their preceding effect batch");
        require(dev.hua.ptcglive.view.AiPresentationTiming.ticks(List.of())==14,"silent choice needs bounded readable pacing");
        System.out.println("BattleAnimationTimingTest passed: server coin order, attack/impact/KO/prize staging, six prizes, bounded durations");
    }
}
