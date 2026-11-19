package dev.hua.ptcglive.spectator;

import dev.hua.ptcglive.engine.field.BattlefieldState.PokemonView;
import java.util.*;

/** Shared read-only card hit map and world-render layout, in a 1000 x 600 canvas. */
public final class ScreenLayout {
    public static final double WIDTH=1000,HEIGHT=600;
    public record Card(double x,double y,double width,double height,String id,String label,PokemonView pokemon){
        public boolean contains(double px,double py){return px>=x&&px<x+width&&py>=y&&py<y+height;}
    }
    public record Fit(double scale,double left,double top){
        public double x(double blocks){return (blocks-left)/scale;}
        public double y(double blocks){return (blocks-top)/scale;}
    }
    public static Fit fit(int width,int height){double scale=Math.min((width-.12)/WIDTH,(height-.12)/HEIGHT);return new Fit(scale,(width-WIDTH*scale)/2,(height-HEIGHT*scale)/2);}
    public static List<Card> cards(ScreenMatch view){
        if(view==null)return List.of();var cards=new ArrayList<Card>();
        for(int index=0;index<view.players().size();index++){
            var p=view.players().get(index);boolean top=index==1;double benchY=top?42:454,activeY=top?171:318;
            if(p.active()!=null)addPokemon(cards,p.active(),438,activeY,80,112,"战斗位");
            int count=p.bench().size();double step=Math.min(96,600.0/Math.max(1,count));
            for(int i=0;i<count;i++)addPokemon(cards,p.bench().get(i),200+i*step,benchY,62,87,"备战");
            double piles=top?47:460;
            if(!p.discard().isEmpty())cards.add(new Card(852,piles,56,78,p.discard().getLast().definitionId(),"弃牌 "+p.discard().size(),null));
            if(!p.lostZone().isEmpty())cards.add(new Card(923,piles,50,70,p.lostZone().getLast().definitionId(),"放逐 "+p.lostZone().size(),null));
            for(var prize:p.faceUpPrizes())cards.add(new Card(25+prize.slot()*22,top?135:551,19,27,prize.cardId(),"公开奖赏",null));
        }
        if(view.stadium()!=null)cards.add(new Card(278,254,60,84,view.stadium(),"场地",null));
        for(int i=0;i<Math.min(3,view.resolvingCards().size());i++)cards.add(new Card(700+i*66,255,60,84,view.resolvingCards().get(i),"结算中",null));
        return List.copyOf(cards);
    }
    private static void addPokemon(List<Card> cards,PokemonView p,double x,double y,double w,double h,String label){
        cards.add(new Card(x,y,w,h,p.topCardId(),label,p));
        int i=0;for(var energy:p.attachedEnergyCardIds())cards.add(new Card(x+(i++%8)*9,y+h-2+(i/9)*6,14,20,energy,"附着能量",null));
        i=0;for(var tool:p.attachedTools())cards.add(new Card(x+w-14-(i++*15),y+h-2,14,20,tool.definitionId(),"附着道具",null));
    }
    public static String hit(ScreenMatch match,double x,double y){
        var cards=cards(match);for(int i=cards.size()-1;i>=0;i--)if(cards.get(i).contains(x,y))return cards.get(i).id();return null;
    }
    private ScreenLayout(){}
}
