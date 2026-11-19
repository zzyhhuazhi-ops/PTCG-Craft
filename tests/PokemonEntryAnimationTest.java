package dev.hua.ptcglive.client;

import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;
import dev.hua.ptcglive.view.MatchPresentation.*;
import java.util.UUID;

public final class PokemonEntryAnimationTest {
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        var id=new UUID(0,1);
        var entry=new Cue(Kind.PLAY,id,id,0,"从手牌上场","sv1_en_1");
        check(PokemonEntryAnimation.applies(entry),"field entry");
        check(!PokemonEntryAnimation.applies(new Cue(Kind.PLAY,id,id,0,"能量附着","energy")),"energy excluded");
        check(!PokemonEntryAnimation.applies(new Cue(Kind.PLAY,id,null,0,"训练家出牌","trainer")),"trainer excluded");
        check(!PokemonEntryAnimation.reveal(entry,false)&&PokemonEntryAnimation.reveal(entry,true),"own placement/opponent reveal");
        check(!PokemonEntryAnimation.reveal(new Cue(Kind.PLAY,id,id,0,"宝可梦登场","sv1_en_1"),true),"unknown zone must not invent a hand reveal");
        var hand=new Rect(500,650,80,110);var stage=new Rect(400,160,210,300);var target=new Rect(200,180,70,100);
        var lifted=PokemonEntryAnimation.lifted(target);
        check(!PokemonEntryAnimation.revealPose(hand,stage,lifted,0).front(),"opponent starts hidden");
        for(int fps:new int[]{30,60,120}){
            for(int i=0;i<fps*2;i++){
                double ms=i*1000./fps;var p=PokemonEntryAnimation.revealPose(hand,stage,lifted,ms);
                check(Double.isFinite(p.x())&&Double.isFinite(p.y())&&p.width()>0&&p.height()>0,"finite bounded geometry");
                if(ms>=300)check(p.front(),"confirmed public face remains visible");
                if(ms>=300&&ms<=1000)check(Math.abs(p.x()-505)<1e-7&&Math.abs(p.height()-300)<1e-7,"readable central hold");
            }
        }
        var toss=PokemonEntryAnimation.revealPose(hand,stage,lifted,1200);
        var start=PokemonEntryAnimation.flatPose(lifted,target,true,1200);
        check(Math.abs(toss.x()-start.x())<1e-7&&Math.abs(toss.y()-start.y())<1e-7&&toss.width()==start.width(),"continuous toss/landing join");
        for(boolean reveal:new boolean[]{false,true}){
            var p=PokemonEntryAnimation.flatPose(reveal?lifted:hand,target,reveal,PokemonEntryAnimation.contact(reveal));
            check(p.x()==235&&p.y()==230&&p.width()==70&&p.height()==100,"exact landing position");
            check(PokemonEntryAnimation.duration(reveal)/1e6==PokemonEntryAnimation.contact(reveal)+200,"queue includes complete landing tail");
        }
        System.out.println("PokemonEntryAnimationTest passed: public-only reveal, zone distinction, phase joins, 30/60/120 sampling, exact contact, tail gate");
    }
}
