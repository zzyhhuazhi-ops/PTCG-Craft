import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch45Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final List<String> IDS=List.of("sm12_en_188","swsh10-5_en_068","swsh10-5_en_067","swsh10-5_en_088","sc_csm2bc_184_sr");
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static CardInstance card(ServerMatch m,UUID p,String id){return NonPokemonBatch37Test.card(m,p,id);}
    static ServerMatch aiGame(String id){return NonPokemonBatch37Test.game(id);}
    // Move existing physical cards only. Fixture preparation is independent of recovery permissions.
    static CardInstance top(ServerMatch m,UUID p,String id){var c=card(m,p,id);check(DeckDiscardTriggeredAbilityTest.zone(m,p,"hand").remove(c),"Missing fixture card");DeckDiscardTriggeredAbilityTest.zone(m,p,"deck").add(c);return c;}
    static void play(ServerMatch m){NonPokemonBatch37Test.play(m);}
    static void settle(ServerMatch m){NonPokemonBatch2Test.settle(m);}
    public static void main(String[] args){mill();lure();empty();System.out.println("NonPokemonBatch45Test passed: exact current discard batch, Item versus Tool, recovery prohibition, partial/empty decks, per-player Stadium use, public snapshots before mandatory all-Pokemon transfer and both owners' remainder shuffle");}
    static void mill(){for(String id:List.of(IDS.get(0),IDS.get(1),IDS.get(4)))for(boolean blocked:List.of(false,true))for(boolean shortDeck:List.of(false,true)){
        var lock=BundledCombatData.ability(JsonParser.parseString("{\"id\":\"colony\",\"nameZh\":\"黏菌聚落\",\"kind\":\"PASSIVE\",\"effectSupport\":\"SUPPORTED\",\"effects\":[{\"type\":\"unrecoverable_from_discard\",\"target\":\"ALL_OPPONENT_POKEMON\",\"sources\":\"ABILITY|TRAINER\",\"destination\":\"HAND\"}]}").getAsJsonObject(),EffectRegistry.standard());
        var m=NonPokemonBatch37Test.game(id,blocked?List.of(lock):List.of());boolean stadium=id.equals("swsh10-5_en_068");
        if(stadium)play(m);
        for(UUID p:stadium?List.of(A,B):List.of(A)){
            if(p.equals(B))m.endTurn(A);
            var old=card(m,p,"item");m.discardHandCardsByEffect(p,p,List.of(old.instanceId()));
            if(shortDeck)m.drawCardsByEffect(p,p,m.privateView(p).deck().size());
            var expected=new ArrayList<CardInstance>();
            if(!shortDeck)for(String kind:stadium?List.of("tool","energy"):List.of("tool","supporter","energy","basic","basic","item"))expected.add(top(m,p,kind));
            expected.add(top(m,p,"item"));
            int before=m.privateView(p).deck().size();var hand=new HashSet<>(m.privateView(p).hand());
            if(stadium)NonPokemonBatch42Test.activate(m,p);else play(m);settle(m);
            check(m.privateView(p).deck().size()==before-expected.size(),"Wrong mill window");
            check(m.privateView(p).discard().contains(old),"Recovered an Item from an older discard batch");
            for(var c:expected){boolean recover=c.definitionId().equals("item")&&!blocked;check(m.privateView(p).hand().contains(c)==recover&&m.privateView(p).discard().contains(c)!=recover,"Recovery kind, current batch or Colony protection: "+c);}
            check(m.privateView(p).hand().stream().filter(c->!hand.contains(c)).count()==(blocked?0:expected.stream().filter(c->c.definitionId().equals("item")).count()),"Unexpected draw or duplicate");
            if(stadium)check(!m.canActivateStadium(p),"Pokestop reused in same player's turn");
        }
    }}
    static void lure(){for(String id:IDS.subList(2,4))for(int mode=0;mode<4;mode++){
        var m=aiGame(id);var windows=new HashMap<UUID,List<CardInstance>>();var hands=new HashMap<UUID,List<CardInstance>>();var decks=new HashMap<UUID,List<CardInstance>>();
        for(UUID p:List.of(A,B)){
            if(mode==3)m.drawCardsByEffect(A,p,m.privateView(p).deck().size());
            var window=new ArrayList<CardInstance>();
            for(String kind:switch(mode){case 0->List.of("basic","energy","basic");case 1->List.of("item","tool","supporter");case 2->List.of("basic","basic","basic");default->p.equals(A)?List.of("basic"):List.<String>of();})window.add(top(m,p,kind));
            Collections.reverse(window);windows.put(p,window);hands.put(p,List.copyOf(m.privateView(p).hand()));decks.put(p,List.copyOf(m.privateView(p).deck()));
        }
        int random=m.randomTranscript().size();play(m);
        for(UUID p:List.of(A,B)){
            if(windows.get(p).isEmpty())continue;
            var reveal=m.effectCardReveal();check(reveal!=null&&reveal.actor().equals(p)&&!reveal.confirmingPlayer().equals(p)&&reveal.cardIds().equals(windows.get(p).stream().map(CardInstance::definitionId).toList()),"Public reveal concealed non-Pokemon, selected wrong owner/order or wrong window");
            check(m.privateView(p).deck().equals(decks.get(p)),"Moved cards before public confirmation");
            m.acknowledgeEffectReveal(reveal.confirmingPlayer(),reveal.id());
        }
        settle(m);check(m.effectCardReveal()==null,"Unresolved public reveal");
        for(UUID p:List.of(A,B)){
            var taken=windows.get(p).stream().filter(c->c.definitionId().equals("basic")).toList();
            check(m.privateView(p).hand().containsAll(taken)&&m.privateView(p).deck().size()==decks.get(p).size()-taken.size(),"Did not take all Pokemon or wrong owner's hand");
            var remaining=new HashSet<>(decks.get(p));remaining.removeAll(taken);check(new HashSet<>(m.privateView(p).deck()).equals(remaining),"Lure remainder lost or moved cards");
            check(m.privateView(p).hand().stream().filter(c->!hands.get(p).contains(c)).count()==taken.size(),"Lure took non-Pokemon");
        }
        if(mode!=3)check(m.randomTranscript().size()>random,"Lure did not shuffle remainder");
    }}
    static void empty(){for(String id:IDS){var m=aiGame(id);if(id.equals("swsh10-5_en_068"))play(m);m.drawCardsByEffect(A,A,m.privateView(A).deck().size());m.drawCardsByEffect(A,B,m.privateView(B).deck().size());if(id.equals("swsh10-5_en_068"))check(!m.canActivateStadium(A),"Empty Pokestop activated");else check(!m.canPlayTrainer(A,card(m,A,"trainer").instanceId(),null),"Empty deck-only Trainer played");}}
}
