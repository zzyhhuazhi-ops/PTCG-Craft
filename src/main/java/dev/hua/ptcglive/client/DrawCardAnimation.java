package dev.hua.ptcglive.client;

import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;
import dev.hua.ptcglive.engine.MatchClientView;
import dev.hua.ptcglive.rules.CardInstance;
import dev.hua.ptcglive.view.MatchPresentation;
import java.util.*;

/** Presentation only. The public draw event never contains a private card identity. */
final class DrawCardAnimation {
    static final double REVEAL_MS=1400, HIDDEN_MS=450;
    static boolean single(MatchPresentation.Cue cue){return cue.kind()==MatchPresentation.Kind.DRAW&&cue.amount()==1;}
    static boolean draw(MatchPresentation.Cue cue){return cue.kind()==MatchPresentation.Kind.DRAW&&cue.amount()>0&&cue.amount()<=60;}
    static long duration(boolean own){return (long)((own?REVEAL_MS:HIDDEN_MS)*1e6);}
    static double offsetMs(boolean own,int count,int index){
        return count<=1?0:index*Math.min(own?1000:150,(own?2400.:1200.)/(count-1));
    }
    static long duration(boolean own,int count){return duration(own)+(long)(offsetMs(own,count,count-1)*1e6);}

    static boolean firstDeal(MatchClientView before,MatchClientView after){
        return before.phase()==dev.hua.ptcglive.engine.MatchPhase.TURN_ORDER && after.turnNumber()==0
            && before.phase()!=after.phase() && before.hand().isEmpty();
    }
    static boolean deal(MatchPresentation.Cue cue){return draw(cue)&&"开局发牌".equals(cue.text());}
    static double launchMs(MatchPresentation.Cue cue,boolean own,int index){
        return deal(cue)?index*85.:offsetMs(own,cue.amount(),index);
    }
    static long arrival(MatchPresentation.Cue cue,boolean own,int index){
        return (deal(cue)?duration(false):duration(own))+(long)(launchMs(cue,own,index)*1e6);
    }

    /** Only an unambiguous draw in the same private view can reveal faces. */
    static CardInstance revealedCard(MatchClientView before,MatchClientView after,List<MatchPresentation.Cue> cues){
        var cards=revealedCards(before,after,cues);return cards.size()==1?cards.getFirst():null;
    }
    static List<CardInstance> revealedCards(MatchClientView before,MatchClientView after,List<MatchPresentation.Cue> cues){
        var seat=before.viewingSeat();
        if(seat==null||!seat.equals(after.viewingSeat())||!before.matchId().equals(after.matchId()))return List.of();
        boolean first=firstDeal(before,after);
        var draws=cues.stream().filter(c->c.kind()==MatchPresentation.Kind.DRAW&&(!first||seat.equals(c.seat()))).toList();
        if(draws.size()!=1||!draw(draws.getFirst())||!seat.equals(draws.getFirst().seat()))return List.of();
        int count=draws.getFirst().amount();
        // Mixed effect batches may also return cards to hand. Never guess which new card was drawn.
        var played=new HashMap<String,Integer>();
        for(var cue:cues)if(cue.kind()==MatchPresentation.Kind.PLAY&&cue.pokemon()==null&&seat.equals(cue.seat())&&cue.cardId()!=null)
            played.merge(cue.cardId(),1,Integer::sum);
        if(cues.stream().anyMatch(c->switch(c.kind()){
            case DRAW,TURN,CHECKUP->false;
            case PLAY->c.pokemon()!=null||!seat.equals(c.seat())||c.cardId()==null;
            case DISCARD->!seat.equals(c.seat())||!played.containsKey(c.cardId());
            default->true;
        }))return List.of();
        var oldPlayer=before.players().stream().filter(p->seat.equals(p.id())).findFirst().orElse(null);
        var newPlayer=after.players().stream().filter(p->seat.equals(p.id())).findFirst().orElse(null);
        if(oldPlayer==null||newPlayer==null||oldPlayer.deckSize()-newPlayer.deckSize()!=count
            ||newPlayer.handSize()-oldPlayer.handSize()!=after.hand().size()-before.hand().size())return List.of();
        var oldIds=new HashSet<UUID>();for(var card:before.hand())oldIds.add(card.instanceId());
        var newIds=new HashSet<UUID>();for(var card:after.hand())newIds.add(card.instanceId());
        if(oldIds.size()!=before.hand().size()||newIds.size()!=after.hand().size())return List.of();
        for(var removed:before.hand())if(!newIds.contains(removed.instanceId())){
            int available=played.getOrDefault(removed.definitionId(),0);if(available==0)return List.of();
            played.put(removed.definitionId(),available-1);
        }
        var added=after.hand().stream().filter(c->!oldIds.contains(c.instanceId())).toList();
        return added.size()==count?added:List.of();
    }

    static EvolutionAnimation.Pose pose(Rect deck,Rect stage,Rect hand,boolean reveal,double ms){
        if(!reveal){
            double p=smooth(ms/HIDDEN_MS);
            return new EvolutionAnimation.Pose(mix(cx(deck),cx(hand),p),mix(cy(deck),cy(hand),p)-Math.sin(p*Math.PI)*deck.height()*.4,
                mix(deck.width(),hand.width(),p),mix(deck.height(),hand.height(),p),0,Math.sin(p*Math.PI)*-12,0,0);
        }
        double skim=smooth(ms/200),launch=out((ms-200)/200),settle=smooth((ms-1100)/300),flip=smooth((ms-100)/300);
        double x=mix(cx(deck),cx(stage),skim),y=mix(cy(deck),cy(hand),skim);
        double w=mix(deck.width(),stage.width()*.75,skim),h=mix(deck.height(),stage.height()*.75,skim);
        return new EvolutionAnimation.Pose(mix(mix(x,cx(stage),launch),cx(hand),settle),
            mix(mix(y,cy(stage),launch),cy(hand),settle),
            mix(mix(w,stage.width(),launch),hand.width(),settle),
            mix(mix(h,stage.height(),launch),hand.height(),settle),flip,
            (1-smooth(ms/520))*-18+Math.sin(settle*Math.PI)*7,(1-smooth(ms/650))*-14,0);
    }
    private static double cx(Rect r){return r.x()+r.width()/2.;}
    private static double cy(Rect r){return r.y()+r.height()/2.;}
    private static double mix(double a,double b,double p){return a+(b-a)*p;}
    private static double unit(double p){return Math.clamp(p,0,1);}
    private static double smooth(double p){p=unit(p);return p*p*(3-2*p);}
    private static double out(double p){return 1-Math.pow(1-unit(p),3);}
}
