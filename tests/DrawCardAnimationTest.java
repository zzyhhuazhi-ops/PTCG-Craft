package dev.hua.ptcglive.client;

import com.google.gson.*;
import dev.hua.ptcglive.engine.MatchClientView;
import dev.hua.ptcglive.view.MatchPresentation.*;
import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;
import java.util.*;

public final class DrawCardAnimationTest {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        var gson=new Gson();
        // Minimal private DTO fixture; the test must not depend on exported preview files.
        var before=JsonParser.parseString("""
            {"revision":0,"game":{
              "matchId":"00000000-0000-0000-0000-000000000001",
              "viewingSeat":"00000000-0000-0000-0000-000000000002",
              "prizeSlots":[],"setupPlayers":[],"mulliganReveals":[],
              "players":[{"id":"00000000-0000-0000-0000-000000000002","handSize":1,"deckSize":49,
                "bench":[],"discard":[],"lostZone":[],"faceUpPrizes":[]}],
              "hand":[{"instanceId":"00000000-0000-0000-0000-000000000003",
                "ownerId":"00000000-0000-0000-0000-000000000002","definitionId":"sv1_en_1"}]
            }}
            """).getAsJsonObject();
        var after=before.deepCopy();var game=after.getAsJsonObject("game");
        var seat=UUID.fromString(game.get("viewingSeat").getAsString());
        var card=game.getAsJsonArray("hand").get(0).deepCopy().getAsJsonObject();
        card.addProperty("instanceId",new UUID(0,0xd0a1).toString());game.getAsJsonArray("hand").add(card);
        for(var raw:game.getAsJsonArray("players"))if(raw.getAsJsonObject().get("id").getAsString().equals(seat.toString())){
            var p=raw.getAsJsonObject();p.addProperty("handSize",p.get("handSize").getAsInt()+1);p.addProperty("deckSize",p.get("deckSize").getAsInt()-1);
        }
        var b=gson.fromJson(before.getAsJsonObject("game"),MatchClientView.class);var a=gson.fromJson(game,MatchClientView.class);
        var cue=new Cue(Kind.DRAW,seat,null,1,"抽牌 1 张",null);
        check(DrawCardAnimation.revealedCard(b,a,List.of(cue)).instanceId().equals(new UUID(0,0xd0a1)),"same definition uses physical UUID");
        var multi=game.deepCopy();var second=card.deepCopy();second.addProperty("instanceId",new UUID(0,0xd0a2).toString());multi.getAsJsonArray("hand").add(second);
        var multiPlayer=multi.getAsJsonArray("players").get(0).getAsJsonObject();multiPlayer.addProperty("handSize",3);multiPlayer.addProperty("deckSize",47);
        var two=new Cue(Kind.DRAW,seat,null,2,"抽牌 2 张",null);
        var twoCards=DrawCardAnimation.revealedCards(b,gson.fromJson(multi,MatchClientView.class),List.of(two));
        var setup=before.getAsJsonObject("game").deepCopy();
        setup.addProperty("phase","TURN_ORDER");setup.add("hand",new JsonArray());
        var setupPlayer=setup.getAsJsonArray("players").get(0).getAsJsonObject();
        setupPlayer.addProperty("handSize",0);setupPlayer.addProperty("deckSize",50);
        var dealt=game.deepCopy();dealt.addProperty("phase","OPENING_POKEMON");
        var otherDraw=new Cue(Kind.DRAW,new UUID(0,99),null,2,"开局发牌",null);
        var ownDeal=new Cue(Kind.DRAW,seat,null,2,"开局发牌",null);
        check(DrawCardAnimation.revealedCards(gson.fromJson(setup,MatchClientView.class),
            gson.fromJson(dealt,MatchClientView.class),List.of(otherDraw,ownDeal)).equals(a.hand()),
            "simultaneous opening binds exactly the viewing seat's hand");
        var seven=new Cue(Kind.DRAW,seat,null,7,"开局发牌",null);
        check(DrawCardAnimation.arrival(seven,true,6)==960_000_000L
            && DrawCardAnimation.arrival(seven,false,6)==960_000_000L,"both opening hands finish together");
        check(twoCards.size()==2&&twoCards.get(0).instanceId().equals(new UUID(0,0xd0a1))&&twoCards.get(1).instanceId().equals(new UUID(0,0xd0a2)),"multi physical IDs in confirmed hand order");
        multi.getAsJsonArray("hand").remove(0);multiPlayer.addProperty("handSize",2);
        check(DrawCardAnimation.revealedCards(b,gson.fromJson(multi,MatchClientView.class),List.of(two)).isEmpty(),"unexplained removed hand must not bind");
        var played=new Cue(Kind.PLAY,seat,null,0,"训练家",b.hand().getFirst().definitionId());
        check(DrawCardAnimation.revealedCards(b,gson.fromJson(multi,MatchClientView.class),List.of(played,two)).size()==2,"public played source can leave before drawn cards arrive");
        check(DrawCardAnimation.revealedCards(b,gson.fromJson(multi,MatchClientView.class),List.of(played,two,new Cue(Kind.SHUFFLE,seat,null,0,"",null))).isEmpty(),"reset/shuffle batch stays conservative");
        check(DrawCardAnimation.revealedCard(a,a,List.of(cue))==null,"duplicate snapshot must not bind");
        check(DrawCardAnimation.revealedCard(b,a,List.of(cue,new Cue(Kind.MOVE,seat,null,1,"return",null)))==null,"mixed hand gains ambiguous");
        check(DrawCardAnimation.revealedCard(b,a,List.of(new Cue(Kind.DRAW,UUID.randomUUID(),null,1,"",null)))==null,"opponent draw private");
        check(DrawCardAnimation.revealedCard(b,a,List.of(new Cue(Kind.DRAW,seat,null,2,"",null)))==null,"multi draw cannot reveal guessed card");
        game.add("viewingSeat",JsonNull.INSTANCE);check(DrawCardAnimation.revealedCard(b,gson.fromJson(game,MatchClientView.class),List.of(cue))==null,"spectator private");
        game.addProperty("viewingSeat",UUID.randomUUID().toString());check(DrawCardAnimation.revealedCard(b,gson.fromJson(game,MatchClientView.class),List.of(cue))==null,"seat switch private");
        var deck=new Rect(800,300,80,112);var stage=new Rect(400,200,200,280);var hand=new Rect(600,600,100,140);
        for(int fps:new int[]{30,60,120})for(int frame=0;frame<=fps*2;frame++){
            double ms=frame*1000./fps;var pose=DrawCardAnimation.pose(deck,stage,hand,false,ms);
            check(!pose.front(),"opponent never reveals");check(Double.isFinite(pose.x()+pose.y()+pose.width()),"finite pose");
        }
        var hold=DrawCardAnimation.pose(deck,stage,hand,true,700);
        check(hold.front()&&hold.x()==500&&hold.y()==340&&hold.height()==280,"center readable hold");
        var end=DrawCardAnimation.pose(deck,stage,hand,true,1400);
        check(end.x()==650&&end.y()==670&&end.width()==100&&end.height()==140,"exact hand landing");
        check(DrawCardAnimation.duration(true)==1400000000L&&DrawCardAnimation.duration(false)==450000000L,"real time endpoints");
        check(DrawCardAnimation.duration(true,2)==2400000000L&&DrawCardAnimation.duration(false,2)==600000000L,"two-card sequence");
        for(int count=1;count<=60;count++){
            check(DrawCardAnimation.duration(true,count)<=3800000000L&&DrawCardAnimation.duration(false,count)<=1650000000L,"bounded bulk duration");
            for(int i=0;i<count;i++)check(DrawCardAnimation.offsetMs(true,count,i)+1400<=DrawCardAnimation.duration(true,count)/1e6+.001,"last card must finish before unlock");
        }
        System.out.println("DrawCardAnimationTest passed: private UUID binding, public trainer source, duplicate/mixed/multi/spectator/seat-switch safety, 30/60/120 sampling, exact landing, 1..60 bounded sequence");
    }
}
